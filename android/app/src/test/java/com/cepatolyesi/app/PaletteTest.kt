package com.cepatolyesi.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import com.cepatolyesi.app.ui.AppColors
import com.cepatolyesi.app.ui.PALETTES
import com.cepatolyesi.app.ui.colorsFor
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class PaletteTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    /** WCAG kontrast oranı */
    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05; val lb = b.luminance() + 0.05
        return (maxOf(la, lb) / minOf(la, lb)).toDouble()
    }

    private fun check(name: String, c: AppColors) {
        fun need(what: String, x: Color, y: Color, min: Double) {
            val r = contrast(x, y)
            assertTrue("$name: $what kontrastı ${"%.2f".format(r)} < $min", r >= min)
        }
        need("yazı/zemin", c.ink, c.bg, 7.0)
        need("yazı/kart", c.ink, c.surf, 7.0)
        need("soluk yazı/zemin", c.mut, c.bg, 4.5)
        need("soluk yazı/kart", c.mut, c.surf, 4.5)
        need("vurgu/zemin", c.acc, c.bg, 4.5)
        need("vurgu/kart", c.acc, c.surf, 4.5)
        need("düğme yazısı", c.onAcc, c.acc, 4.5)
        need("seçili sekme", c.st[1].first, c.st[1].second, 4.5)
    }

    @Test fun allPalettesReadable() {
        for (dark in listOf(false, true)) {
            val mode = if (dark) "koyu" else "açık"
            PALETTES.forEach { p -> check("${p.name} ($mode)", colorsFor(p.id, 0f, dark)) }
            for (h in 0 until 360 step 15) check("özel $h° ($mode)", colorsFor("ozel", h.toFloat(), dark))
        }
    }

    @Test fun choosePalette() {
        val vm = androidx.lifecycle.ViewModelProvider(rule.activity)[AppViewModel::class.java]
        rule.runOnUiThread { vm.updateNav(NavMode.DRAWER); vm.updateTheme(ThemeMode.LIGHT); vm.updatePalette("kahve") }
        rule.onNodeWithContentDescription("Seçenekler").performClick(); rule.onNodeWithText("Ayarlar").performClick()
        // Kapalıyken paletler görünmez, satırda seçili palet yazar
        rule.onNodeWithText("Okyanus").assertDoesNotExist()
        rule.onNodeWithText("Kahve").assertExists()
        captureScreenRoboImage("build/ekranlar/49-ayarlar-kapali.png")
        rule.onNodeWithText("Renk paleti").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithText("Okyanus").performScrollTo().performClick(); rule.waitForIdle()
        assertEquals("okyanus", vm.palette)
        captureScreenRoboImage("build/ekranlar/50-palet-okyanus.png")
        rule.onNodeWithContentDescription("Koyu tema").performClick(); rule.waitForIdle()
        captureScreenRoboImage("build/ekranlar/51-palet-okyanus-koyu.png")
        rule.onNodeWithText("Özel").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithTag("palet-ton").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(320f) }
        rule.waitForIdle()
        assertEquals("ozel", vm.palette); assertEquals(320f, vm.customHue, 1f)
        captureScreenRoboImage("build/ekranlar/52-palet-ozel-koyu.png")
        rule.onNodeWithContentDescription("Açık tema").performClick(); rule.onNodeWithText("Orman").performScrollTo().performClick(); rule.waitForIdle()
        captureScreenRoboImage("build/ekranlar/53-palet-orman.png")
        rule.onNodeWithText("Lavanta").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithContentDescription("Seçenekler").performClick(); rule.onNodeWithText("Tamir").performClick(); rule.waitForIdle()
        captureScreenRoboImage("build/ekranlar/54-palet-lavanta-tamir.png")
        rule.runOnUiThread { vm.updatePalette("kahve") }
    }
}
