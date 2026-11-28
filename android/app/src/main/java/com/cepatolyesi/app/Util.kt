package com.cepatolyesi.app

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

// ---- IMEI ----

/** IMEI doğrulama hanesi (Luhn) kontrolü; 15 haneli IMEI için. */
fun luhn(s: String): Boolean {
    if (s.length != 15 || !s.all { it.isDigit() }) return false
    var t = 0
    for (i in 0 until 15) {
        var n = s[14 - i] - '0'
        if (i % 2 == 1) { n *= 2; if (n > 9) n -= 9 }
        t += n
    }
    return t % 10 == 0
}

private val IMEI_RUN = Regex("\\d{15}")

/**
 * Metindeki IMEI'yi bulur ("IMEI1: 35...", boşluklu yazım, çift SIM'de iki IMEI).
 * Önce doğrulama hanesi tutan 15 haneli diziyi, yoksa ilk 15 haneli diziyi döner.
 */
fun imeiFrom(text: String): String? {
    for (src in listOf(text, text.filter { it.isDigit() })) {
        val runs = IMEI_RUN.findAll(src).map { it.value }.toList() +
            // bitişik iki IMEI (30 hane) gibi durumlar için 15'lik pencereler
            src.windowed(15, 15).filter { it.length == 15 && it.all(Char::isDigit) }
        runs.firstOrNull { luhn(it) }?.let { return it }
        runs.firstOrNull()?.let { return it }
    }
    return null
}

/** Barkoddan okunan değerlerden IMEI'yi seçer (doğrulama hanesi tutan önce). */
fun imeiFromAll(values: List<String>): String? =
    values.mapNotNull { imeiFrom(it) }.let { all -> all.firstOrNull { luhn(it) } ?: all.firstOrNull() }

// ---- Telefon numarası: 0532 123 45 67 ----

fun fmtPhone(v: String): String {
    val d = v.filter { it.isDigit() }
    if (v.trim().startsWith("+")) {
        if (!d.startsWith("90")) return v
        val r = d.drop(2).take(10)
        return "+90" + listOf(r.take(3), r.drop(3).take(3), r.drop(6).take(2), r.drop(8).take(2))
            .filter { it.isNotEmpty() }.joinToString("") { " $it" }
    }
    val x = d.take(11)
    return listOf(x.take(4), x.drop(4).take(3), x.drop(7).take(2), x.drop(9).take(2))
        .filter { it.isNotEmpty() }.joinToString(" ")
}

/**
 * Telefon kutusuna yazılırken çağrılır: başa 0 ekler, çift sıfırı teke indirir, boşluklarla
 * biçimler ve imlecin yerini korur. Dönen: (yeni metin, imleç konumu).
 */
fun phoneEdit(old: String, new: String, cursor: Int): Pair<String, Int> {
    var v = new
    var pos = cursor.coerceIn(0, v.length)
    // Sadece bir boşluk silindiyse önündeki rakamı da sil (yoksa silme tuşu takılı kalır)
    if (v.length < old.length && v.filter { it.isDigit() } == old.filter { it.isDigit() } && pos > 0) {
        v = v.removeRange(pos - 1, pos); pos--
    }
    if (v.isNotEmpty() && v[0] != '0' && v[0] != '+') { v = "0$v"; pos++ }
    if (v.startsWith("00")) { val n = v.takeWhile { it == '0' }.length - 1; v = v.substring(n); pos = max(1, pos - n) }
    val keep = v.substring(0, min(pos, v.length)).count { it != ' ' }
    val f = fmtPhone(v)
    var i = 0; var n = 0
    while (i < f.length && n < keep) { if (f[i] != ' ') n++; i++ }
    return f to i
}

fun phoneDigits(v: String) = v.filter { it.isDigit() }.length

// ---- Model adı: "SAMSUNG, SAMSUNG GALAXY ALPHA" -> "Samsung Galaxy Alpha" ----

private val UPW = setOf("LG", "HTC", "ZTE", "TCL", "HMD", "OPPO", "BLU", "POCO", "SE", "XR", "XS", "FE", "GT", "TV", "5G", "4G", "LTE", "II", "III")
private val SPW = mapOf("IPHONE" to "iPhone", "IPAD" to "iPad", "IPOD" to "iPod", "IQOO" to "iQOO", "ONEPLUS" to "OnePlus", "REALME" to "realme", "VIVO" to "vivo", "HONOR" to "HONOR")
private val TR = Locale("tr", "TR")

fun prettyModel(spec: String): String {
    val p = spec.split(",").map { it.trim() }.filter { it.isNotEmpty() && it != "N/A" }
    var n = p.firstOrNull() ?: spec.trim()
    if (n.split(Regex("\\s+")).size < 2 && p.size > 1 && !Regex("^\\d{4}$").matches(p[1]))
        n = if (p[1].uppercase(Locale.ROOT).startsWith(n.uppercase(Locale.ROOT))) p[1] else "$n ${p[1]}"
    return n.split(Regex("\\s+")).joinToString(" ") { w ->
        val u = w.uppercase(Locale.ROOT)
        SPW[u] ?: if (u in UPW || w.any { it.isDigit() }) u else u.take(1) + u.drop(1).lowercase(Locale.ROOT)
    }
}

// ---- Para ve tarih ----

private val NF = NumberFormat.getNumberInstance(TR).apply { maximumFractionDigits = 2 }
fun money(d: Double): String = NF.format(d) + " ₺"
/** "2026-11-28" -> "28.11.2026" */
fun dfmt(d: String): String = if (d.isEmpty()) "" else d.split("-").reversed().joinToString(".")
/** "1.250,50" / "1250.5" / "1250" -> sayı */
fun parseAmount(s: String): Double {
    val t = s.trim().replace(" ", "")
    if (t.isEmpty()) return 0.0
    val norm = if (t.contains(',')) t.replace(".", "").replace(',', '.') else t
    return norm.toDoubleOrNull() ?: 0.0
}
fun amountText(d: Double): String = if (d == 0.0) "" else if (d % 1.0 == 0.0) d.toLong().toString() else d.toString().replace('.', ',')
