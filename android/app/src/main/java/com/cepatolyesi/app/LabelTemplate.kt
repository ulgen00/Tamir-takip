package com.cepatolyesi.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.oned.Code128Writer
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

const val DEFAULT_SHOP = "Cep Atölyesi"

enum class ItemKind(val title: String) { TEXT("Yazı"), BARCODE("Barkod"), QR("QR kod"), LINE("Çizgi"), BOX("Kutu") }

/**
 * Etiketteki bir öğe. Tüm ölçüler milimetre; (x, y) sol üst köşe.
 * text: yazı / barkod / QR içeriği; {ad}, {telefon} gibi alanlar kayıttaki bilgiyle doldurulur.
 * size: yazı boyu (mm). align: 0 sol, 1 orta, 2 sağ. LINE/BOX için h kalınlık/yükseklik.
 */
data class LabelItem(
    val id: String = uid(),
    val kind: ItemKind,
    val text: String = "",
    val x: Float, val y: Float, val w: Float, val h: Float = 0f,
    val size: Float = 3f, val bold: Boolean = false, val align: Int = 0,
) {
    /** Ekranda seçim ve sürükleme için kaplanan yükseklik (mm) */
    fun boxH(): Float = if (kind == ItemKind.TEXT) size * 1.2f else max(h, 0.3f)
    fun boxW(): Float = if (kind == ItemKind.QR) min(w, h) else w
}

data class LabelTemplate(val items: List<LabelItem>) {
    fun toJson(): String = JSONObject().put("items", JSONArray().apply {
        items.forEach { i ->
            put(JSONObject().put("id", i.id).put("kind", i.kind.name).put("text", i.text).put("x", i.x.toDouble()).put("y", i.y.toDouble())
                .put("w", i.w.toDouble()).put("h", i.h.toDouble()).put("size", i.size.toDouble()).put("bold", i.bold).put("align", i.align))
        }
    }).toString()

    companion object {
        /** Kayıttan doldurulabilen alanlar: (etiket, yer tutucu) */
        val FIELDS = listOf(
            "Dükkan" to "{dukkan}", "Müşteri" to "{ad}", "Telefon" to "{telefon}", "Model" to "{model}", "Arıza" to "{ariza}", "IMEI" to "{imei}",
            "Tarih" to "{tarih}", "Kayıt no" to "{kayitno}", "Ücret" to "{ucret}", "Durum" to "{durum}", "Toptancı" to "{toptanci}",
        )

        fun fromJson(s: String): LabelTemplate {
            val a = JSONObject(s).getJSONArray("items")
            return LabelTemplate((0 until a.length()).map { k ->
                val o = a.getJSONObject(k)
                LabelItem(
                    id = o.optString("id").ifEmpty { uid() },
                    kind = runCatching { ItemKind.valueOf(o.getString("kind")) }.getOrDefault(ItemKind.TEXT),
                    text = o.optString("text"), x = o.optDouble("x").toFloat(), y = o.optDouble("y").toFloat(),
                    w = o.optDouble("w", 20.0).toFloat(), h = o.optDouble("h", 0.0).toFloat(), size = o.optDouble("size", 3.0).toFloat(),
                    bold = o.optBoolean("bold"), align = o.optInt("align").coerceIn(0, 2),
                )
            })
        }

        /** Hazır tasarım (60x30 mm için çizildi, başka boylara orantılı uyarlanır) */
        fun default(widthMm: Int = 60, heightMm: Int = 30): LabelTemplate {
            val sx = widthMm / 60f; val sy = heightMm / 30f; val sz = min(sx, sy)
            fun t(text: String, x: Float, y: Float, w: Float, size: Float, bold: Boolean = false, align: Int = 0) =
                LabelItem(kind = ItemKind.TEXT, text = text, x = x * sx, y = y * sy, w = w * sx, size = size * sz, bold = bold, align = align)
            return LabelTemplate(listOf(
                t("{dukkan}", 1.5f, 0.9f, 34f, 2.5f, bold = true),
                t("{tarih}", 25f, 0.9f, 33.5f, 2.5f, align = 2),
                LabelItem(kind = ItemKind.LINE, x = 1.5f * sx, y = 4.1f * sy, w = 57f * sx, h = 0.25f),
                t("{ad}", 1.5f, 4.8f, 57f, 4f, bold = true),
                t("{telefon}", 1.5f, 9.3f, 57f, 3.25f),
                t("{model}", 1.5f, 13f, 57f, 3.25f, bold = true),
                t("{ariza}", 1.5f, 16.8f, 57f, 2.75f),
                LabelItem(kind = ItemKind.BARCODE, text = "{kayitno}", x = 1.5f * sx, y = 20.75f * sy, w = 33f * sx, h = 7.75f * sy),
                t("Kayıt no", 36f, 20.6f, 22.5f, 2.25f, align = 2),
                t("{kayitno}", 36f, 23.6f, 22.5f, 4.25f, bold = true, align = 2),
            ))
        }

        /** {alan} yer tutucularını kayıttaki bilgiyle doldurur */
        /** shop: Ayarlar'daki dükkan adı */
        fun fill(text: String, r: Repair, shop: String = DEFAULT_SHOP): String {
            val v = mapOf(
                "{dukkan}" to shop,
                "{ad}" to r.name, "{telefon}" to fmtPhone(r.phone), "{model}" to r.model, "{ariza}" to r.issue.replace('\n', ' '),
                "{imei}" to r.imei, "{tarih}" to dfmt(r.date), "{kayitno}" to LabelPrinter.jobNo(r.id),
                "{ucret}" to if (r.price > 0) money(r.price) else "", "{durum}" to STATUSES[r.st.coerceIn(0, 3)], "{toptanci}" to r.sup,
            )
            var s = text
            v.forEach { (k, x) -> s = s.replace(k, x) }
            return s.trim()
        }

        /** Şablonu verilen kayıtla resim olarak çizer (siyah-beyaz, yazıcının çözünürlüğünde) */
        fun render(t: LabelTemplate, r: Repair, cfg: PrinterCfg, shop: String = DEFAULT_SHOP): Bitmap {
            val d = cfg.dpi / 25.4f
            val bmp = Bitmap.createBitmap(max(8, (cfg.widthMm * d).roundToInt()), max(8, (cfg.heightMm * d).roundToInt()), Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawColor(Color.WHITE)
            val black = Paint().apply { color = Color.BLACK; isAntiAlias = false }
            for (it in t.items) {
                val x = it.x * d; val y = it.y * d; val w = it.w * d; val h = it.h * d
                when (it.kind) {
                    ItemKind.TEXT -> {
                        val s = fill(it.text, r, shop); if (s.isEmpty()) continue
                        val p = Paint().apply {
                            color = Color.BLACK; textSize = it.size * d; isAntiAlias = false
                            typeface = if (it.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                        }
                        val txt = fit(s, p, w)
                        val tw = p.measureText(txt)
                        val tx = when (it.align) { 1 -> x + (w - tw) / 2; 2 -> x + w - tw; else -> x }
                        c.drawText(txt, tx, y - p.ascent(), p)
                    }
                    ItemKind.BARCODE -> {
                        val s = fill(it.text, r, shop); if (s.isEmpty()) continue
                        val m = runCatching { Code128Writer().encode(s, BarcodeFormat.CODE_128, 0, 0) }.getOrNull() ?: continue
                        val mod = max(1, (w / m.width).toInt())
                        for (k in 0 until m.width) if (m.get(k, 0)) c.drawRect(x + k * mod, y, x + (k + 1) * mod, y + h, black)
                    }
                    ItemKind.QR -> {
                        val s = fill(it.text, r, shop); if (s.isEmpty()) continue
                        val side = min(w, h).toInt(); if (side < 8) continue
                        val m = runCatching {
                            QRCodeWriter().encode(s, BarcodeFormat.QR_CODE, side, side, mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.CHARACTER_SET to "UTF-8"))
                        }.getOrNull() ?: continue
                        for (yy in 0 until m.height) for (xx in 0 until m.width) if (m.get(xx, yy)) c.drawRect(x + xx, y + yy, x + xx + 1, y + yy + 1, black)
                    }
                    ItemKind.LINE -> c.drawRect(x, y, x + w, y + max(1f, h), black)
                    ItemKind.BOX -> {
                        val th = max(1f, 0.25f * d)
                        c.drawRect(x, y, x + w, y + th, black); c.drawRect(x, y + h - th, x + w, y + h, black)
                        c.drawRect(x, y, x + th, y + h, black); c.drawRect(x + w - th, y, x + w, y + h, black)
                    }
                }
            }
            return bmp
        }

        private fun fit(text: String, p: Paint, maxW: Float): String {
            if (maxW <= 0 || p.measureText(text) <= maxW) return text
            val n = p.breakText(text, true, maxW - p.measureText("…"), null)
            return text.take(max(0, n)).trimEnd() + "…"
        }
    }
}
