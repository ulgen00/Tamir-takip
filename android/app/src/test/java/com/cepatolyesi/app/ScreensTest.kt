package com.cepatolyesi.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Uygulamayı bilgisayarda Android taklidiyle açıp gerçek bir kullanım akışını dener ve
 * her adımın ekran görüntüsünü app/build/ekranlar/ klasörüne kaydeder.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class ScreensTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun shot(name: String) { rule.waitForIdle(); captureScreenRoboImage("build/ekranlar/$name.png") }
    private fun field(i: Int) = rule.onAllNodes(hasSetTextAction())[i]

    @Test fun repairFlow() {
        shot("01-tamir-bos")
        rule.onNodeWithContentDescription("Yeni kayıt ekle").performClick()
        shot("02-yeni-cihaz")

        // Zorunlu alanlar boşken kaydetme
        rule.onNodeWithText("Kaydet").performScrollTo().performClick()
        rule.onNodeWithText("Müşteri adını yazın.").assertExists()
        shot("03-zorunlu-alan-uyarisi")
        rule.onNodeWithText("Tamam").performClick()

        field(0).performTextInput("Ayşe Yılmaz")
        field(1).performTextInput("5321234567")
        rule.onNode(hasText("0532 123 45 67")).assertExists()   // başa 0 + boşluklu biçim
        field(3).performTextInput("35601506")                    // IMEI ilk 8 hane -> model
        rule.waitUntil(10_000) { rule.onAllNodes(hasText("Samsung Galaxy Alpha")).fetchSemanticsNodes().isNotEmpty() }
        shot("04-imei-model-doldu")

        rule.onNodeWithText("Kaydet").performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Ayşe Yılmaz · 0532 123 45 67").fetchSemanticsNodes().isNotEmpty() }
        shot("05-liste")
        val vm = rule.activity.let { androidx.lifecycle.ViewModelProvider(it)[AppViewModel::class.java] }
        assertEquals(1, vm.store.repairs.size)
        assertEquals("Samsung Galaxy Alpha", vm.store.repairs[0].model)

        // Teslim: kasaya gelir yazılır
        rule.onNodeWithText("Samsung Galaxy Alpha").performClick()
        shot("06-cihaz-detayi")
        rule.onNodeWithText("Teslim et ve tahsil et").performScrollTo().performClick()
        shot("07-teslim-onayi")
        rule.onNodeWithText("Evet").performClick()
        rule.waitForIdle()
        assertEquals(3, vm.store.repairs[0].st)
    }

    @Test fun tabsAndSettings() {
        rule.onNodeWithContentDescription("Seçenekler").performClick()
        shot("10-menu")
        rule.onNodeWithText("Kasa").performClick(); shot("11-kasa")
        rule.onNodeWithContentDescription("Yeni kayıt ekle").performClick(); shot("12-kasa-yeni-kayit")
        field(0).performTextInput("1500")
        rule.onNodeWithText("Kaydet").performClick(); rule.waitForIdle(); shot("13-kasa-kayitli")

        rule.onNodeWithContentDescription("Seçenekler").performClick(); rule.onNodeWithText("Özet").performClick(); shot("14-ozet")
        rule.onNodeWithContentDescription("Seçenekler").performClick(); rule.onNodeWithText("IMEI Sorgula").performClick(); shot("15-imei-sorgula")
        rule.onNodeWithContentDescription("Seçenekler").performClick(); rule.onNodeWithText("Ayarlar").performClick(); shot("16-ayarlar")

        rule.onNodeWithText("Alt çubuk").performClick(); shot("17-alt-cubuk")
        rule.onNodeWithContentDescription("Koyu tema").performClick(); shot("18-koyu-tema")
    }

    @Test fun imeiEdevlet() {
        // Ayarlar testler arasında kalabildiği için soldan menüye al
        rule.runOnUiThread { androidx.lifecycle.ViewModelProvider(rule.activity)[AppViewModel::class.java].updateNav(NavMode.DRAWER) }
        rule.onNodeWithContentDescription("Seçenekler").performClick(); rule.onNodeWithText("IMEI Sorgula").performClick()
        field(0).performTextInput("356015060194519")
        // Gerçek e-Devlet sorgusu (Worker üzerinden); internet yoksa hata mesajı görünür
        rule.waitUntil(40_000) {
            rule.onAllNodes(hasText("e-Devlet IMEI kaydı")).fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodes(hasText("Tekrar dene")).fetchSemanticsNodes().isNotEmpty()
        }
        shot("20-imei-edevlet")
        rule.onNodeWithText("Kapalı").assertExists()
    }
}
