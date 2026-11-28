package com.cepatolyesi.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UtilTest {
    @Test fun luhnCheck() {
        assertTrue(luhn("356015060194519"))
        assertFalse(luhn("356015060194510"))
        assertFalse(luhn("35601506019451"))
    }

    @Test fun imeiFromText() {
        assertEquals("356015060194519", imeiFrom("IMEI1: 356015060194519\nIMEI2: 356015060194527"))
        assertEquals("356015060194519", imeiFrom("35 601506 019451 9"))
        assertEquals("356015060194519", imeiFrom("356015060194519356015060194527"))
        assertNull(imeiFrom("3560"))
        assertEquals("356015060194519", imeiFromAll(listOf("R58M41ABCDE", "356015060194519")))
    }

    @Test fun phoneFormat() {
        assertEquals("0532 123 45 67", fmtPhone("05321234567"))
        assertEquals("+90 532 123 45 67", fmtPhone("+905321234567"))
        assertEquals("0532 12", fmtPhone("053212"))
    }

    @Test fun phoneEditing() {
        assertEquals("0532 123 45 67" to 14, phoneEdit("", "5321234567", 10))       // başa 0
        assertEquals("0532" to 4, phoneEdit("0", "00532", 5))                       // çift sıfır
        assertEquals("0532 12" to 7, phoneEdit("0532 123", "0532 12", 7))           // silme
        assertEquals("0531 23" to 3, phoneEdit("0532 123", "0532123", 4))          // boşluk silinince önündeki rakam (2) da silinir
        assertEquals("0593 212 3" to 3, phoneEdit("0532 123", "05932 123", 3))     // ortaya ekleme
        assertEquals("+90 532 1" to 9, phoneEdit("", "+905321", 7))
    }

    @Test fun modelNames() {
        assertEquals("Samsung Galaxy Alpha", prettyModel("SAMSUNG, SAMSUNG GALAXY ALPHA"))
        assertEquals("Apple iPhone 15 Pro Max", prettyModel("APPLE IPHONE 15 PRO MAX, Apple iPhone 15 Pro Max, A3108, 2023"))
        assertEquals("Xiaomi Redmi Note 14S", prettyModel("XIAOMI REDMI NOTE 14S, Xiaomi 2502FRA65G, Global Model, 2025"))
        assertEquals("iQOO Neo 7 SE", prettyModel("IQOO, IQOO NEO 7 SE"))
    }

    @Test fun amounts() {
        assertEquals(1250.5, parseAmount("1.250,50"), 0.001)
        assertEquals(1250.5, parseAmount("1250.5"), 0.001)
        assertEquals(0.0, parseAmount(""), 0.001)
        assertEquals("28.11.2026", dfmt("2026-11-28"))
    }

    @Test fun storeJsonRoundTripWebFormat() {
        // Web sürümünün localStorage biçimi
        val web = """{"repairs":[{"id":"a1","date":"2026-11-20","name":"Ali","phone":"0532 123 45 67","model":"Asus Zenfone 5","issue":"Ekran","imei":"356015060194519","sup":"","price":1500,"cost":400,"st":1,
            "edv":{"imei":"356015060194519","durum":"kapatılmış","sinif":"kapali","kaynak":"","tarih":"28/11/2026","model":"x"}}],
            "tx":[{"id":"t1","type":"out","amt":200,"cat":"Kira","date":"2026-11-02","note":""}],"tac":{"35601506":"Samsung Galaxy Alpha"}}"""
        val s = Store(null); s.fromJson(web)
        assertEquals(1, s.repairs.size); assertEquals("kapali", s.repairs[0].edv!!.sinif); assertEquals(1500.0, s.repairs[0].price, 0.0)
        assertEquals("out", s.tx[0].type); assertEquals("Samsung Galaxy Alpha", s.tac["35601506"])
        val s2 = Store(null); s2.fromJson(s.toJson())
        assertEquals(s.repairs.toList(), s2.repairs.toList()); assertEquals(s.tx.toList(), s2.tx.toList())
        s2.deliver(s2.repairs[0])
        assertEquals(3, s2.repairs[0].st)
        assertEquals(listOf("out" to 200.0, "in" to 1500.0, "out" to 400.0), s2.tx.map { it.type to it.amt })
    }
}
