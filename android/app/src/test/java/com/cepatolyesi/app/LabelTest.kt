package com.cepatolyesi.app

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.net.ServerSocket
import kotlin.concurrent.thread

/** Etiket yazdırma: çizim, TSPL komutu, ağdan gönderme ve formdaki düğme. */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class LabelTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val sample = Repair("M3K9ABQ7XZ", "2026-11-28", "Şükrü Ağaoğlu", "05321234567", "Samsung Galaxy A54", "Ekran değişimi, şarj soketi gevşek", "356015060194519", "", 1500.0, 0.0, 0, null)

    /** Sahte yazıcı: bir bağlantı kabul eder, gelen tüm baytları döner */
    private fun fakePrinter(): Pair<Int, () -> ByteArray> {
        val ss = ServerSocket(0)
        var got = ByteArray(0)
        val t = thread { ss.accept().use { got = it.getInputStream().readBytes() }; ss.close() }
        return ss.localPort to { t.join(10000); got }
    }

    /** TSPL'deki BITMAP verisini yazıcının basacağı görüntüye geri çevirir */
    private fun decodeTspl(b: ByteArray): Bitmap {
        val text = String(b, Charsets.ISO_8859_1)
        val m = Regex("BITMAP 0,0,(\\d+),(\\d+),0,").find(text)!!
        val wb = m.groupValues[1].toInt(); val h = m.groupValues[2].toInt()
        val start = m.range.last + 1
        val bmp = Bitmap.createBitmap(wb * 8, h, Bitmap.Config.ARGB_8888)
        for (y in 0 until h) for (bx in 0 until wb) {
            val v = b[start + y * wb + bx].toInt() and 0xFF
            for (bit in 0 until 8) bmp.setPixel(bx * 8 + bit, y, if (v and (0x80 shr bit) == 0) Color.BLACK else Color.WHITE)
        }
        return bmp
    }

    private fun readBarcode(bmp: Bitmap): String? {
        val px = IntArray(bmp.width * bmp.height); bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        return runCatching { MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bmp.width, bmp.height, px)))).text }.getOrNull()
    }

    private fun savePng(bmp: Bitmap, name: String) {
        File("build/ekranlar").mkdirs()
        // 2 kat büyüt ve gri çerçeve: etiket kâğıdı gibi görünsün
        val big = Bitmap.createScaledBitmap(bmp, bmp.width * 2, bmp.height * 2, false)
        File("build/ekranlar/$name.png").outputStream().use { big.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun renderAndSend() = runBlocking {
        val cfg = PrinterCfg(ip = "127.0.0.1", widthMm = 60, heightMm = 30)
        val (port, result) = fakePrinter()
        assertNull(LabelPrinter.print(sample, cfg.copy(port = port)))
        val bytes = result()
        val text = String(bytes, Charsets.ISO_8859_1)
        assertTrue(text.startsWith("SIZE 60 mm,30 mm\r\nGAP 2 mm,0 mm\r\nDIRECTION 1\r\nREFERENCE 0,0\r\nCLS\r\nBITMAP 0,0,60,240,0,"))
        assertTrue(text.endsWith("\r\nPRINT 1,1\r\n"))
        val printed = decodeTspl(bytes)
        assertEquals(480, printed.width); assertEquals(240, printed.height)
        savePng(printed, "30-etiket-yazicinin-basacagi")
        // Etiketteki barkod kayıt numarasını veriyor
        assertEquals(LabelPrinter.jobNo(sample.id), readBarcode(printed))
        assertEquals("ABQ7XZ", LabelPrinter.jobNo(sample.id))

        // Ters renk ve 180° seçenekleri komuta yansıyor
        val inv = LabelPrinter.tspl(LabelPrinter.render(sample, cfg), cfg.copy(invert = true, rotate = true))
        assertTrue(String(inv, Charsets.ISO_8859_1).contains("DIRECTION 0\r\n"))
        val p2 = decodeTspl(inv)
        assertEquals(Color.BLACK, p2.getPixel(2, 2)); assertEquals(Color.WHITE, printed.getPixel(2, 2))

        // Başka boy: 40x30 mm
        val small = decodeTspl(LabelPrinter.tspl(LabelPrinter.render(sample, cfg.copy(widthMm = 40)), cfg.copy(widthMm = 40)))
        savePng(small, "31-etiket-40x30")

        // Yazıcı yoksa anlaşılır hata
        val err = LabelPrinter.print(sample, cfg.copy(port = 1))
        assertTrue(err!!.startsWith("Yazıcıya bağlanılamadı (127.0.0.1:1)"))
        assertTrue(LabelPrinter.send(PrinterCfg(), ByteArray(0))!!.contains("IP adresini girin"))
    }

    @Test fun printFromForm() {
        val (port, result) = fakePrinter()
        val vm = androidx.lifecycle.ViewModelProvider(rule.activity)[AppViewModel::class.java]
        rule.runOnUiThread { vm.updateNav(NavMode.DRAWER); vm.updatePrinter(PrinterCfg(ip = "127.0.0.1", port = port)) }
        rule.onNodeWithContentDescription("Yeni kayıt ekle").performClick()
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("Ayşe Yılmaz")
        rule.onAllNodes(hasSetTextAction())[2].performTextInput("iPhone 13")
        rule.onNodeWithText("Etiket yazdır").performScrollTo().performClick()
        rule.waitUntil(10_000) { rule.onAllNodes(hasText("Etiket yazıcıya gönderildi.")).fetchSemanticsNodes().isNotEmpty() }
        val bytes = result()
        assertTrue(String(bytes, Charsets.ISO_8859_1).contains("BITMAP 0,0,60,240,0,"))
        savePng(decodeTspl(bytes), "32-formdan-basilan-etiket")
        rule.onNodeWithText("Tamam").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Etiket yazıcıya gönderildi.")).fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithContentDescription("Kapat").performClick()
        // × formu kapatıyor mu
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Yeni cihaz")).fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithContentDescription("Seçenekler").performClick(); rule.onNodeWithText("Ayarlar").performClick()
        rule.waitForIdle(); captureScreenRoboImage("build/ekranlar/33-ayarlar-yazici.png")
        rule.onNodeWithText("Deneme etiketi yazdır").performScrollTo(); rule.waitForIdle()
        captureScreenRoboImage("build/ekranlar/34-ayarlar-yazici-alt.png")
    }
}
