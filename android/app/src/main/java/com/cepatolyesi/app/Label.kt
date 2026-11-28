package com.cepatolyesi.app

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.roundToInt

/**
 * Ağdaki termal etiket yazıcısı ayarları (Xprinter, Gprinter, HPRT vb. — TSPL dili, ham TCP).
 * rotate: etiketi 180° çevir (yazıcı ters basıyorsa). invert: siyah/beyazı ters çevir (yazıcı negatif basıyorsa).
 */
data class PrinterCfg(
    val ip: String = "",
    val port: Int = 9100,
    val widthMm: Int = 60,
    val heightMm: Int = 30,
    val gapMm: Int = 2,
    val dpi: Int = 203,
    val rotate: Boolean = false,
    val invert: Boolean = false,
    val autoPrint: Boolean = false,
    /** Yazıcının komut dili: TSPL (Xprinter, Gprinter...), ZPL (Zebra ve uyumlular), EPL (Zebra/Eltron eski, bazı HPRT) */
    val lang: String = "TSPL",
)

object LabelPrinter {
    /** Kayda ait kısa numara (etikette ve barkodda) */
    fun jobNo(id: String): String = id.uppercase().filter { it.isLetterOrDigit() }.takeLast(6)

    /** Etiketi şablona göre resim olarak çizer (Türkçe karakterler yazıcının yazı tipinden bağımsız çıkar). */
    fun render(r: Repair, cfg: PrinterCfg, tpl: LabelTemplate? = null): Bitmap =
        LabelTemplate.render(tpl ?: LabelTemplate.default(cfg.widthMm, cfg.heightMm), r, cfg)

    /** TSPL komutu: etiket boyu, boşluk, yön ve 1 bitlik resim (BITMAP), ardından PRINT. */
    /** Resmi 1 bitlik satırlara çevirir; black1=true ise 1 = siyah nokta (ZPL), değilse 0 = siyah (TSPL, EPL) */
    private fun mono(bmp: Bitmap, invert: Boolean, black1: Boolean): Triple<ByteArray, Int, Int> {
        val w = bmp.width; val h = bmp.height; val wb = (w + 7) / 8
        val data = ByteArray(wb * h)
        val px = IntArray(w)
        for (y in 0 until h) {
            bmp.getPixels(px, 0, w, 0, y, w, 1)
            for (bx in 0 until wb) {
                var b = if (black1) 0 else 0xFF
                for (bit in 0 until 8) {
                    val x = bx * 8 + bit
                    if (x < w) {
                        val p = px[x]
                        val lum = (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
                        if ((lum < 128) != invert) b = if (black1) b or (0x80 shr bit) else b and (0x80 shr bit).inv()
                    }
                }
                data[y * wb + bx] = b.toByte()
            }
        }
        return Triple(data, wb, h)
    }

    /** Seçili dile göre yazıcı komutu */
    fun encode(bmp: Bitmap, cfg: PrinterCfg, copies: Int = 1): ByteArray = when (cfg.lang) {
        "ZPL" -> zpl(bmp, cfg, copies)
        "EPL" -> epl(bmp, cfg, copies)
        else -> tspl(bmp, cfg, copies)
    }

    /** TSPL: etiket boyu, boşluk, yön ve 1 bitlik resim (BITMAP; 0 = siyah), ardından PRINT. */
    fun tspl(bmp: Bitmap, cfg: PrinterCfg, copies: Int = 1): ByteArray {
        val (data, wb, h) = mono(bmp, cfg.invert, black1 = false)
        val out = ByteArrayOutputStream()
        fun cmd(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
        cmd("SIZE ${cfg.widthMm} mm,${cfg.heightMm} mm\r\n")
        cmd("GAP ${cfg.gapMm} mm,0 mm\r\n")
        cmd("DIRECTION ${if (cfg.rotate) 0 else 1}\r\n")
        cmd("REFERENCE 0,0\r\n")
        cmd("CLS\r\n")
        cmd("BITMAP 0,0,$wb,$h,0,")
        out.write(data)
        cmd("\r\nPRINT ${copies.coerceIn(1, 20)},1\r\n")
        return out.toByteArray()
    }

    /** ZPL: ^GFA grafik alanı (onaltılık, 1 = siyah). ^POI etiketi 180° çevirir. */
    fun zpl(bmp: Bitmap, cfg: PrinterCfg, copies: Int = 1): ByteArray {
        val (data, wb, h) = mono(bmp, cfg.invert, black1 = true)
        val hex = StringBuilder(data.size * 2)
        for (b in data) { val v = b.toInt() and 0xFF; hex.append(HEX[v shr 4]).append(HEX[v and 15]) }
        return ("^XA^PW${bmp.width}^LL$h^LH0,0^PO${if (cfg.rotate) "I" else "N"}" +
            "^FO0,0^GFA,${data.size},${data.size},$wb,$hex^FS^PQ${copies.coerceIn(1, 20)}^XZ\r\n").toByteArray(Charsets.US_ASCII)
    }
    private val HEX = "0123456789ABCDEF".toCharArray()

    /** EPL2: q/Q etiket boyu, GW ham resim (0 = siyah), ZT/ZB yön, P adet. */
    fun epl(bmp: Bitmap, cfg: PrinterCfg, copies: Int = 1): ByteArray {
        val (data, wb, h) = mono(bmp, cfg.invert, black1 = false)
        val gap = (cfg.gapMm * cfg.dpi / 25.4f).roundToInt()
        val out = ByteArrayOutputStream()
        fun cmd(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
        cmd("\r\nN\r\nq${bmp.width}\r\nQ$h,$gap\r\n${if (cfg.rotate) "ZB" else "ZT"}\r\n")
        cmd("GW0,0,$wb,$h,")
        out.write(data)
        cmd("\r\nP${copies.coerceIn(1, 20)}\r\n")
        return out.toByteArray()
    }

    /** Dil denemesi: resim yerine yazıcının kendi yazı tipiyle, üzerinde dilin adı yazan basit etiket */
    fun langTest(lang: String, cfg: PrinterCfg): ByteArray = when (lang) {
        "ZPL" -> "^XA^PW${(cfg.widthMm * cfg.dpi / 25.4f).roundToInt()}^FO20,20^A0N,50,50^FDZPL CALISIYOR^FS^XZ\r\n"
        "EPL" -> "\r\nN\r\nq${(cfg.widthMm * cfg.dpi / 25.4f).roundToInt()}\r\nA20,20,0,4,2,2,N,\"EPL CALISIYOR\"\r\nP1\r\n"
        else -> "SIZE ${cfg.widthMm} mm,${cfg.heightMm} mm\r\nGAP ${cfg.gapMm} mm,0 mm\r\nCLS\r\nTEXT 20,20,\"3\",0,2,2,\"TSPL CALISIYOR\"\r\nPRINT 1,1\r\n"
    }.toByteArray(Charsets.US_ASCII)

    /** Yazıcıya gönderir. Başarısızsa kullanıcıya gösterilecek açıklamayı döner, başarılıysa null. */
    suspend fun send(cfg: PrinterCfg, bytes: ByteArray): String? = withContext(Dispatchers.IO) {
        if (cfg.ip.isBlank()) return@withContext "Önce Ayarlar > Etiket yazıcısı bölümünde yazıcının IP adresini girin."
        try {
            Socket().use { sock ->
                sock.connect(InetSocketAddress(cfg.ip.trim(), cfg.port), 5000)
                sock.soTimeout = 10000
                sock.getOutputStream().apply { write(bytes); flush() }
                // Bazı yazıcılar bağlantı hemen kapanınca gelen veriyi işlemeden atıyor: biraz bekle, sonra kapat
                Thread.sleep(600)
                runCatching { sock.shutdownOutput() }
            }
            null
        } catch (e: Exception) {
            "Yazıcıya bağlanılamadı (${cfg.ip.trim()}:${cfg.port}). Yazıcının açık olduğunu ve telefonla aynı ağda olduğunu kontrol edin."
        }
    }

    suspend fun print(r: Repair, cfg: PrinterCfg, tpl: LabelTemplate? = null, copies: Int = 1): String? = send(cfg, encode(render(r, cfg, tpl), cfg, copies))
}
