package com.cepatolyesi.app

import android.content.Context
import android.net.ConnectivityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

/**
 * Ağdaki etiket yazıcılarını bulur: telefonun bağlı olduğu ağdaki adreslerde yazıcı portunun (9100)
 * açık olup olmadığına bakar. Yazıcıya hiçbir komut göndermez (bazı yazıcılar gelen yazıyı basar).
 */
object PrinterScan {
    data class Found(val ip: String, val port: Int)

    /** Testlerde gerçek ağ yerine kullanılacak adres listesi */
    @Volatile var hostsOverride: List<String>? = null

    /** Telefonun ağdaki IPv4 adresi ve ağ öneki (ör. 192.168.1.34, 24). Önce Wi-Fi/aktif ağ. */
    fun localAddress(ctx: Context): Pair<String, Int>? {
        runCatching {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.getLinkProperties(cm.activeNetwork)?.linkAddresses
                ?.firstOrNull { it.address is Inet4Address && !it.address.isLoopbackAddress }
                ?.let { return it.address.hostAddress!! to it.prefixLength }
        }
        return runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .sortedBy { if (it.name.startsWith("wlan")) 0 else 1 }
                .flatMap { ni -> ni.interfaceAddresses.map { it } }
                .firstOrNull { it.address is Inet4Address && !it.address.isLoopbackAddress }
                ?.let { it.address.hostAddress!! to it.networkPrefixLength.toInt() }
        }.getOrNull()
    }

    /** Taranacak adresler: kendi /24 ağı (daha geniş ağlarda da yalnızca kendi /24'ü), kendisi hariç */
    fun hosts(ip: String, prefix: Int): List<String> {
        val p = ip.split(".").mapNotNull { it.toIntOrNull() }
        if (p.size != 4) return emptyList()
        val n = ((p[0] shl 24) or (p[1] shl 16) or (p[2] shl 8) or p[3])
        val pre = prefix.coerceIn(24, 30)
        val mask = -1 shl (32 - pre)
        val net = n and mask
        val count = (1 shl (32 - pre)) - 2
        return (1..count).map { net + it }.filter { it != n }.map {
            "${(it ushr 24) and 255}.${(it ushr 16) and 255}.${(it ushr 8) and 255}.${it and 255}"
        }
    }

    /** Adresleri aynı anda (en fazla parallel) dener; açık portları döner. onProgress(bitti, toplam) */
    suspend fun scan(
        hosts: List<String>, ports: List<Int> = listOf(9100), timeoutMs: Int = 400, parallel: Int = 48,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): List<Found> = withContext(Dispatchers.IO) {
        val sem = Semaphore(parallel)
        val done = AtomicInteger(0)
        val total = hosts.size * ports.size
        coroutineScope {
            hosts.flatMap { h -> ports.map { p -> h to p } }.map { (h, p) ->
                async {
                    sem.withPermit {
                        val ok = runCatching { Socket().use { it.connect(InetSocketAddress(h, p), timeoutMs) }; true }.getOrDefault(false)
                        onProgress(done.incrementAndGet(), total)
                        if (ok) Found(h, p) else null
                    }
                }
            }.awaitAll().filterNotNull()
        }.sortedBy { f -> f.ip.split(".").map { it.toInt() }.fold(0L) { a, b -> a * 256 + b } }
    }

    /** Ayarlar'daki "Ağda yazıcı ara": dönen (bulunanlar, açıklama) */
    suspend fun findPrinters(ctx: Context, onProgress: (Int, Int) -> Unit): Pair<List<Found>, String> {
        val list = hostsOverride ?: run {
            val (ip, prefix) = localAddress(ctx) ?: return emptyList<Found>() to "Telefon bir ağa bağlı görünmüyor. Yazıcıyla aynı Wi-Fi ağına bağlanıp tekrar deneyin."
            hosts(ip, prefix)
        }
        val found = scan(list, onProgress = onProgress)
        val net = list.firstOrNull()?.substringBeforeLast(".")?.let { "$it.x" } ?: ""
        return found to if (found.isEmpty())
            "$net ağında yazıcı bulunamadı. Yazıcının açık ve telefonla aynı Wi-Fi ağında olduğunu kontrol edin. Kablolu (Ethernet) yazıcıysa modemle aynı ağa bağlı olmalı."
        else "$net ağında ${found.size} cihaz bulundu. Yazıcınızı seçin."
    }
}
