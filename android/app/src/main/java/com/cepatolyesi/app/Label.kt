package com.cepatolyesi.app

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket

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
)

object LabelPrinter {
    /** Kayda ait kısa numara (etikette ve barkodda) */
    fun jobNo(id: String): String = id.uppercase().filter { it.isLetterOrDigit() }.takeLast(6)

    /** Etiketi şablona göre resim olarak çizer (Türkçe karakterler yazıcının yazı tipinden bağımsız çıkar). */
    fun render(r: Repair, cfg: PrinterCfg, tpl: LabelTemplate? = null): Bitmap =
        LabelTemplate.render(tpl ?: LabelTemplate.default(cfg.widthMm, cfg.heightMm), r, cfg)

    /** TSPL komutu: etiket boyu, boşluk, yön ve 1 bitlik resim (BITMAP), ardından PRINT. */
    fun tspl(bmp: Bitmap, cfg: PrinterCfg, copies: Int = 1): ByteArray {
        val w = bmp.width; val h = bmp.height; val wb = (w + 7) / 8
        val data = ByteArray(wb * h)
        val px = IntArray(w)
        for (y in 0 until h) {
            bmp.getPixels(px, 0, w, 0, y, w, 1)
            for (bx in 0 until wb) {
                var b = 0xFF // TSPL'de 1 = boş (beyaz), 0 = basılan nokta (siyah)
                for (bit in 0 until 8) {
                    val x = bx * 8 + bit
                    if (x < w) {
                        val p = px[x]
                        val lum = (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
                        if ((lum < 128) != cfg.invert) b = b and (0x80 shr bit).inv()
                    }
                }
                data[y * wb + bx] = b.toByte()
            }
        }
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

    /** Yazıcıya gönderir. Başarısızsa kullanıcıya gösterilecek açıklamayı döner, başarılıysa null. */
    suspend fun send(cfg: PrinterCfg, bytes: ByteArray): String? = withContext(Dispatchers.IO) {
        if (cfg.ip.isBlank()) return@withContext "Önce Ayarlar > Etiket yazıcısı bölümünde yazıcının IP adresini girin."
        try {
            Socket().use { sock ->
                sock.connect(InetSocketAddress(cfg.ip.trim(), cfg.port), 5000)
                sock.soTimeout = 10000
                sock.getOutputStream().apply { write(bytes); flush() }
            }
            null
        } catch (e: Exception) {
            "Yazıcıya bağlanılamadı (${cfg.ip.trim()}:${cfg.port}). Yazıcının açık olduğunu ve telefonla aynı Wi-Fi ağında olduğunu kontrol edin."
        }
    }

    suspend fun print(r: Repair, cfg: PrinterCfg, tpl: LabelTemplate? = null, copies: Int = 1): String? = send(cfg, tspl(render(r, cfg, tpl), cfg, copies))
}
