package com.cepatolyesi.app

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.time.YearMonth

enum class ThemeMode { AUTO, LIGHT, DARK }
enum class NavMode { DRAWER, BOTTOM }

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val store = Store(File(app.filesDir, "tamir-defteri.json")).also { it.load() }
    val tacDb = TacDb(app)
    private val prefs = app.getSharedPreferences("ayarlar", Context.MODE_PRIVATE)

    var theme by mutableStateOf(runCatching { ThemeMode.valueOf(prefs.getString("tema", "AUTO")!!) }.getOrDefault(ThemeMode.AUTO))
        private set
    var nav by mutableStateOf(runCatching { NavMode.valueOf(prefs.getString("gezinme", "DRAWER")!!) }.getOrDefault(NavMode.DRAWER))
        private set
    /** Renk paleti (Ayarlar > Renk paleti): hazır palet kimliği ya da "ozel"; ozel için ton (0-360) */
    var palette by mutableStateOf(prefs.getString("palet", "kahve") ?: "kahve")
        private set
    var customHue by mutableStateOf(prefs.getFloat("palet_ton", 200f))
        private set
    fun updatePalette(id: String, hue: Float = customHue) {
        palette = id; customHue = hue
        prefs.edit().putString("palet", id).putFloat("palet_ton", hue).apply()
    }

    /** Sol üstte ve etiketlerde görünen dükkan adı (Ayarlar'dan değiştirilir) */
    var shopName by mutableStateOf(prefs.getString("dukkan", DEFAULT_SHOP) ?: DEFAULT_SHOP)
        private set
    fun updateShopName(n: String) { shopName = n; prefs.edit().putString("dukkan", n).apply() }
    /** Boş bırakılırsa varsayılan ad */
    val shopTitle: String get() = shopName.trim().ifEmpty { DEFAULT_SHOP }

    /** Kasa ve Özet'te gösterilen ay */
    var month by mutableStateOf(YearMonth.now())

    /** Ağdaki etiket yazıcısı */
    var printer by mutableStateOf(
        PrinterCfg(
            ip = prefs.getString("yz_ip", "") ?: "", port = prefs.getInt("yz_port", 9100),
            widthMm = prefs.getInt("yz_gen", 60), heightMm = prefs.getInt("yz_yuk", 30), gapMm = prefs.getInt("yz_bosluk", 2),
            rotate = prefs.getBoolean("yz_cevir", false), invert = prefs.getBoolean("yz_ters", false), autoPrint = prefs.getBoolean("yz_oto", false),
            lang = prefs.getString("yz_dil", "TSPL") ?: "TSPL",
        )
    )
        private set

    fun updatePrinter(c: PrinterCfg) {
        printer = c
        prefs.edit().putString("yz_ip", c.ip.trim()).putInt("yz_port", c.port).putInt("yz_gen", c.widthMm).putInt("yz_yuk", c.heightMm)
            .putInt("yz_bosluk", c.gapMm).putBoolean("yz_cevir", c.rotate).putBoolean("yz_ters", c.invert).putBoolean("yz_oto", c.autoPrint).putString("yz_dil", c.lang).apply()
    }

    /** Kullanıcının etiket tasarımı; null ise hazır tasarım (etiket boyuna göre) */
    var labelTemplate by mutableStateOf(prefs.getString("etiket_sablon", null)?.let { runCatching { LabelTemplate.fromJson(it) }.getOrNull() })
        private set
    fun updateTemplate(t: LabelTemplate?) {
        labelTemplate = t
        prefs.edit().apply { if (t == null) remove("etiket_sablon") else putString("etiket_sablon", t.toJson()) }.apply()
    }
    /** Düzenlenecek/basılacak şablon */
    fun currentTemplate(): LabelTemplate = labelTemplate ?: LabelTemplate.default(printer.widthMm, printer.heightMm)

    /** Etiketi yazıcıya gönderir; sonuç mesajı ana iş parçacığında döner. Form kapansa da devam eder. */
    fun printLabel(r: Repair, onDone: (String) -> Unit) {
        val cfg = printer
        val tpl = currentTemplate(); val shop = shopTitle
        viewModelScope.launch { onDone(LabelPrinter.print(r, cfg, tpl, shop) ?: "Etiket yazıcıya gönderildi.") }
    }

    fun updateTheme(t: ThemeMode) { theme = t; prefs.edit().putString("tema", t.name).apply() }
    fun updateNav(n: NavMode) { nav = n; prefs.edit().putString("gezinme", n.name).apply() }
}

/**
 * IMEI kutusunun arkasındaki mantık (web sürümündeki fillTac + edvQuery):
 * 8 hane olunca marka/model aranır (önce dükkanın kendi kayıtları, sonra uygulama içindeki veritabanı),
 * 15 hane ve geçerli olunca e-Devlet kaydı sorgulanır.
 *
 * solo=true: IMEI Sorgula sayfası (model alanı yok, model mesajda gösterilir).
 * onModel: bulunan modeli forma yazmayı dener; yazdıysa true döner.
 */
class ImeiState(
    private val store: Store,
    private val tacDb: TacDb,
    private val scope: CoroutineScope,
    private val solo: Boolean,
    initImei: String = "",
    initEdv: Edv? = null,
    private val onModel: (model: String) -> Boolean = { false },
) {
    var imei by mutableStateOf(initImei); private set
    var msg by mutableStateOf(if (solo) HINT_SOLO else HINT_FORM)
    var edv by mutableStateOf(initEdv); private set
    var edvBusy by mutableStateOf(false); private set
    var edvErr by mutableStateOf<String?>(null); private set
    var captcha by mutableStateOf<EdvResult.Captcha?>(null); private set

    private var lastTac = ""
    private var edvFor = if (initEdv != null) initImei else ""
    private var tacJob: Job? = null
    private var edvJob: Job? = null

    /** Kutuya yazılan/yapıştırılan metin. Metinden IMEI ayıklanır, en fazla 15 rakam. */
    fun input(v: String) {
        val d = imeiFrom(v) ?: v.filter { it.isDigit() }.take(15)
        imei = d
        if (d.length >= 8 && (d.take(8) != lastTac || d.length == 15)) { lastTac = d.take(8); fillModel(d) }
        else if (d.length < 8) { lastTac = ""; tacJob?.cancel(); if (solo) msg = HINT_SOLO }
        if (edv != null && edv!!.imei != d || edvFor.isNotEmpty() && edvFor != d) {
            edv = null; edvFor = ""; captcha = null; edvErr = null; edvJob?.cancel(); edvBusy = false
        }
        if (d.length == 15 && luhn(d) && d != edvFor) queryEdv(d)
    }

    /** Barkoddan/fotoğraftan/panodan gelen IMEI */
    fun setFromScan(v: String, source: String) {
        input(v)
        if (!solo && imei.length == 15) msg = if (luhn(imei)) "IMEI $source." else "IMEI $source ancak doğrulama hanesi uyuşmuyor, lütfen kontrol edin."
    }

    private fun fillModel(d: String) {
        tacJob?.cancel()
        val tac = d.take(8)
        tacJob = scope.launch {
            var m = store.tac[tac]
            var spec = ""
            if (m == null) {
                msg = "IMEI’den marka/model aranıyor…"
                val s = tacDb.lookup(tac)
                if (imei.take(8) != tac) return@launch
                if (s == null) {
                    msg = if (solo) "Bu IMEI model veritabanında bulunamadı." else "Bu IMEI model veritabanında bulunamadı. Marka/modeli elle yazın; bir sonraki sefer otomatik dolar."
                    return@launch
                }
                spec = s; m = prettyModel(s)
            }
            val src = if (spec.isNotEmpty()) " ($spec)" else " (önceki kayıtlarınızdan)"
            msg = when {
                solo -> "Marka/model: $m$src."
                onModel(m) -> "Marka/model otomatik dolduruldu: $m$src."
                else -> "Bu IMEI için bulunan model: $m${if (spec.isNotEmpty()) " ($spec)" else ""}. Alan dolu olduğu için değiştirilmedi."
            }
            if (d.length == 15 && !luhn(d)) msg += " Uyarı: IMEI doğrulama hanesi uyuşmuyor, kontrol edin."
        }
    }

    fun queryEdv(d: String = imei, code: String? = null) {
        if (d.length != 15) { edvErr = "e-Devlet sorgusu için 15 haneli IMEI gerekli."; return }
        edvJob?.cancel()
        edvFor = d; edvBusy = true; edvErr = null
        val state = captcha?.state
        if (code == null) captcha = null
        edvJob = scope.launch {
            val r = Edevlet.query(d, if (code != null) state else null, code)
            if (edvFor != d) return@launch
            edvBusy = false
            when (r) {
                is EdvResult.Ok -> { edv = r.edv.copy(imei = d); captcha = null }
                is EdvResult.Captcha -> captcha = r
                is EdvResult.Err -> { edvErr = r.message; captcha = null }
            }
        }
    }

    fun refreshEdv() { edv = null; queryEdv(imei) }

    /** Formdaki IMEI değiştiyse eski e-Devlet sonucu kaydedilmez */
    fun edvFor(imeiNow: String): Edv? = edv?.takeIf { it.imei == imeiNow }

    companion object {
        const val HINT_FORM = "IMEI’yi öğrenmek için telefondan *#06# tuşlayın."
        const val HINT_SOLO = "IMEI’yi yazın, yapıştırın, barkodunu tarayın ya da galeriden fotoğraf seçin. Telefondan *#06# tuşlayarak öğrenebilirsiniz."
    }
}
