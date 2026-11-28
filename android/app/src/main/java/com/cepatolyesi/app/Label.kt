package com.cepatolyesi.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.google.zxing.BarcodeFormat
import com.google.zxing.oned.Code128Writer
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
)

object LabelPrinter {
    /** Kayda ait kısa numara (etikette ve barkodda) */
    fun jobNo(id: String): String = id.uppercase().filter { it.isLetterOrDigit() }.takeLast(6)

    /**
     * Etiketi resim olarak çizer (Türkçe karakterler yazıcının yazı tipinden bağımsız çıkar).
     * Ölçüler 60x30 mm / 203 dpi (480x240 nokta) için tasarlandı, başka boylarda orantılı büyür/küçülür.
     */
    fun render(r: Repair, cfg: PrinterCfg): Bitmap {
        val dpmm = cfg.dpi / 25.4f
        val w = (cfg.widthMm * dpmm).roundToInt()
        val h = (cfg.heightMm * dpmm).roundToInt()
        val s = h / 240f
        val pad = 12 * s
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        fun paint(size: Float, bold: Boolean = false) = Paint().apply {
            color = Color.BLACK; textSize = size * s; isAntiAlias = false
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        fun fit(text: String, p: Paint, maxW: Float): String {
            if (p.measureText(text) <= maxW) return text
            val n = p.breakText(text, true, maxW - p.measureText("…"), null)
            return text.take(n).trimEnd() + "…"
        }
        val inner = w - 2 * pad

        // Üst satır: dükkan adı ve tarih
        val head = paint(20f, true); val date = paint(20f)
        c.drawText("CEP ATÖLYESİ", pad, 26 * s, head)
        val d = dfmt(r.date)
        c.drawText(d, w - pad - date.measureText(d), 26 * s, date)
        c.drawRect(pad, 33 * s, w - pad, 35 * s, Paint().apply { color = Color.BLACK })

        // Müşteri, telefon, cihaz, arıza
        val name = paint(32f, true)
        c.drawText(fit(r.name.ifBlank { "-" }, name, inner), pad, 68 * s, name)
        val phone = paint(26f)
        c.drawText(fit(fmtPhone(r.phone), phone, inner), pad, 98 * s, phone)
        val model = paint(26f, true)
        c.drawText(fit(r.model, model, inner), pad, 128 * s, model)
        if (r.issue.isNotBlank()) { val issue = paint(22f); c.drawText(fit(r.issue.replace('\n', ' '), issue, inner), pad, 154 * s, issue) }

        // Alt: kayıt numarası barkodu ve numara
        val no = jobNo(r.id)
        val top = 166 * s; val bh = 62 * s
        val m = Code128Writer().encode(no, BarcodeFormat.CODE_128, 0, 0)
        val mod = maxOf(1, (w * 0.55f / m.width).toInt())
        val black = Paint().apply { color = Color.BLACK }
        for (x in 0 until m.width) if (m.get(x, 0)) c.drawRect(pad + x * mod, top, pad + (x + 1) * mod, top + bh, black)
        val small = paint(18f); val big = paint(34f, true)
        c.drawText("Kayıt no", w - pad - small.measureText("Kayıt no"), 186 * s, small)
        c.drawText(no, w - pad - big.measureText(no), 224 * s, big)
        return bmp
    }

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

    suspend fun print(r: Repair, cfg: PrinterCfg, copies: Int = 1): String? = send(cfg, tspl(render(r, cfg), cfg, copies))
}
