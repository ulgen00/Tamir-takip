package com.cepatolyesi.app

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

val STATUSES = listOf("Bekliyor", "Tamirde", "Hazır", "Teslim edildi")
val CAT_IN = listOf("Tamir", "Aksesuar satışı", "Diğer gelir")
val CAT_OUT = listOf("Parça", "Kira", "Fatura", "Maaş", "Diğer gider")

/** e-Devlet IMEI kaydı sonucu. sinif: kayitli / kayitsiz / kapali / bilinmiyor */
data class Edv(val imei: String, val durum: String, val sinif: String, val kaynak: String, val tarih: String, val model: String)

data class Repair(
    val id: String, val date: String, val name: String, val phone: String, val model: String,
    val issue: String, val imei: String, val sup: String, val price: Double, val cost: Double,
    val st: Int, val edv: Edv?,
)

data class Tx(val id: String, val type: String, val amt: Double, val cat: String, val date: String, val note: String, val rid: String?)

fun uid(): String = java.lang.Long.toString(System.currentTimeMillis(), 36) + java.lang.Long.toString((Math.random() * 1679616).toLong(), 36)
fun today(): String = LocalDate.now().toString()

/**
 * Kayıtlar: telefonda tek bir JSON dosyası. Biçim web sürümündeki
 * "tamir-defteri-v1" ile aynıdır; yedek dosyaları iki yönde de kullanılabilir.
 */
class Store(private val file: File?) {
    val repairs = mutableStateListOf<Repair>()
    val tx = mutableStateListOf<Tx>()
    /** IMEI'nin ilk 8 hanesi -> bu dükkanda kaydedilen model adı */
    val tac = mutableStateMapOf<String, String>()

    fun load() {
        val f = file ?: return
        if (f.exists()) runCatching { fromJson(f.readText()) }
    }

    fun save() {
        val f = file ?: return
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(toJson())
        tmp.renameTo(f)
    }

    fun toJson(): String = JSONObject().apply {
        put("repairs", JSONArray().apply { repairs.forEach { put(it.toJson()) } })
        put("tx", JSONArray().apply { tx.forEach { put(it.toJson()) } })
        put("tac", JSONObject().apply { tac.forEach { (k, v) -> put(k, v) } })
    }.toString()

    /** Yedeği/kaydı okur ve mevcut kayıtların yerine koyar. Biçim hatalıysa istisna atar, hiçbir şey değişmez. */
    fun fromJson(s: String) {
        val o = JSONObject(s)
        val r = o.optJSONArray("repairs") ?: JSONArray()
        val t = o.optJSONArray("tx") ?: JSONArray()
        val newR = (0 until r.length()).map { repairOf(r.getJSONObject(it)) }
        val newT = (0 until t.length()).map { txOf(t.getJSONObject(it)) }
        val tc = o.optJSONObject("tac")
        repairs.clear(); repairs.addAll(newR)
        tx.clear(); tx.addAll(newT)
        tac.clear(); tc?.keys()?.forEach { k -> tac[k] = tc.optString(k) }
    }

    fun upsertRepair(r: Repair) {
        val i = repairs.indexOfFirst { it.id == r.id }
        if (i >= 0) repairs[i] = r else repairs.add(r)
        if (r.imei.length >= 8 && r.model.isNotBlank()) tac[r.imei.take(8)] = r.model
        save()
    }
    fun deleteRepair(id: String) { repairs.removeAll { it.id == id }; save() }
    fun upsertTx(t: Tx) { val i = tx.indexOfFirst { it.id == t.id }; if (i >= 0) tx[i] = t else tx.add(t); save() }
    fun deleteTx(id: String) { tx.removeAll { it.id == id }; save() }

    /** Teslim: durumu "Teslim edildi" yapar; ücreti gelir, parça maliyetini gider olarak kasaya yazar. */
    fun deliver(r: Repair) {
        val d = r.copy(st = 3)
        val i = repairs.indexOfFirst { it.id == r.id }
        if (i >= 0) repairs[i] = d else repairs.add(d)
        if (r.price > 0) tx.add(Tx(uid(), "in", r.price, "Tamir", today(), r.model + " - " + r.name, r.id))
        if (r.cost > 0) tx.add(Tx(uid(), "out", r.cost, "Parça", today(), r.model + if (r.sup.isNotBlank()) " - " + r.sup else "", r.id))
        save()
    }

    companion object {
        fun Repair.toJson() = JSONObject().apply {
            put("id", id); put("date", date); put("name", name); put("phone", phone); put("model", model)
            put("issue", issue); put("imei", imei); put("sup", sup); put("price", price); put("cost", cost); put("st", st)
            edv?.let { e -> put("edv", JSONObject().apply {
                put("imei", e.imei); put("durum", e.durum); put("sinif", e.sinif); put("kaynak", e.kaynak); put("tarih", e.tarih); put("model", e.model)
            }) }
        }
        fun Tx.toJson() = JSONObject().apply {
            put("id", id); put("type", type); put("amt", amt); put("cat", cat); put("date", date); put("note", note); rid?.let { put("rid", it) }
        }
        fun repairOf(o: JSONObject) = Repair(
            id = o.optString("id").ifEmpty { uid() }, date = o.optString("date").ifEmpty { today() },
            name = o.optString("name"), phone = o.optString("phone"), model = o.optString("model"),
            issue = o.optString("issue"), imei = o.optString("imei"), sup = o.optString("sup"),
            price = o.optDouble("price", 0.0).let { if (it.isNaN()) 0.0 else it },
            cost = o.optDouble("cost", 0.0).let { if (it.isNaN()) 0.0 else it },
            st = o.optInt("st", 0).coerceIn(0, 3),
            edv = o.optJSONObject("edv")?.let { e ->
                Edv(e.optString("imei"), e.optString("durum"), e.optString("sinif", "bilinmiyor"), e.optString("kaynak"), e.optString("tarih"), e.optString("model"))
            },
        )
        fun txOf(o: JSONObject) = Tx(
            id = o.optString("id").ifEmpty { uid() }, type = if (o.optString("type") == "out") "out" else "in",
            amt = o.optDouble("amt", 0.0).let { if (it.isNaN()) 0.0 else it }, cat = o.optString("cat"),
            date = o.optString("date").ifEmpty { today() }, note = o.optString("note"),
            rid = if (o.has("rid")) o.optString("rid") else null,
        )
    }
}
