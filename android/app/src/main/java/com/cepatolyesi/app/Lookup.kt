package com.cepatolyesi.app

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * IMEI'nin ilk 8 hanesinden (TAC) marka/model bulur. Veri uygulamanın içinde (assets/tac),
 * TAC'in ilk 4 hanesine göre parçalara bölünmüş; sadece gereken parça okunur.
 */
class TacDb(private val ctx: Context) {
    private var prefixLen = 4
    private var prefixes: Set<String>? = null
    private val shards = HashMap<String, Pair<List<String>, JSONObject>>()

    /** Ham kayıt ("SAMSUNG, SAMSUNG GALAXY ALPHA") ya da bulunamazsa null. */
    suspend fun lookup(tac8: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val set = prefixes ?: JSONObject(ctx.assets.open("tac/index.json").bufferedReader().readText()).let { ix ->
                prefixLen = ix.optInt("p", 4)
                val a = ix.getJSONArray("s"); (0 until a.length()).map { a.getString(it) }.toSet().also { prefixes = it }
            }
            val p = tac8.take(prefixLen)
            if (p !in set) return@runCatching null
            val (models, map) = shards.getOrPut(p) {
                val o = JSONObject(ctx.assets.open("tac/$p.json").bufferedReader().readText())
                val m = o.getJSONArray("m"); (0 until m.length()).map { m.getString(it) } to o.getJSONObject("t")
            }
            val i = map.optInt(tac8.drop(prefixLen), -1)
            if (i < 0) null else models[i]
        }.getOrNull()
    }
}

sealed class EdvResult {
    data class Ok(val edv: Edv) : EdvResult()
    /** e-Devlet doğrulama kodu istedi: resmi göster, kullanıcının yazdığı kodla state'i geri gönder */
    data class Captcha(val image: ImageBitmap?, val state: String, val error: String) : EdvResult()
    data class Err(val message: String) : EdvResult()
}

/**
 * e-Devlet IMEI kayıt sorgusu. Sorgu, worker/imei-worker.js ile kurulan Cloudflare Worker
 * üzerinden yapılır (e-Devlet sayfasını doldurup sonucu JSON olarak döner).
 */
object Edevlet {
    const val API = "https://cep-atolyesi-imei.hvt8b9y5zt.workers.dev/sorgu"

    suspend fun query(imei: String, state: String? = null, code: String? = null): EdvResult = withContext(Dispatchers.IO) {
        try {
            val c = (URL(API).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true; connectTimeout = 15000; readTimeout = 60000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("X-Cep-Atolyesi", "android")
            }
            val body = JSONObject().put("imei", imei).apply { if (state != null) { put("state", state); put("code", code ?: "") } }
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val text = (if (c.responseCode < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: ""
            parse(text)
        } catch (e: Exception) {
            EdvResult.Err("Sorgu sunucusuna ulaşılamadı. İnternet bağlantısını kontrol edin.")
        }
    }

    fun parse(text: String): EdvResult {
        val j = runCatching { JSONObject(text) }.getOrElse { return EdvResult.Err("e-Devlet sonucu okunamadı.") }
        if (j.optBoolean("ok")) return EdvResult.Ok(Edv(j.optString("imei"), j.optString("durum"), j.optString("sinif", "bilinmiyor"), j.optString("kaynak"), j.optString("tarih"), j.optString("model")))
        j.optJSONObject("captcha")?.let { c ->
            val img = runCatching {
                val b = Base64.decode(c.optString("img").substringAfter(","), Base64.DEFAULT)
                BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap()
            }.getOrNull()
            return EdvResult.Captcha(img, c.optString("state"), c.optString("error"))
        }
        return EdvResult.Err(j.optString("error").ifEmpty { "Sorgu yapılamadı." })
    }
}
