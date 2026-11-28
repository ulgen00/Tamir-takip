package com.cepatolyesi.app

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class PrinterScanTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @After fun clear() { PrinterScan.hostsOverride = null }

    /** 127.0.0.1 üzerinde dinleyen sahte yazıcı; bağlantıları kabul edip kapatır */
    private fun listener(port: Int = 0): ServerSocket {
        val ss = ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"))
        thread(isDaemon = true) { runCatching { while (true) ss.accept().close() } }
        return ss
    }

    @Test fun subnetHosts() {
        val h = PrinterScan.hosts("192.168.1.34", 24)
        assertEquals(253, h.size)                       // .1-.254, kendisi hariç
        assertEquals("192.168.1.1", h.first()); assertEquals("192.168.1.254", h.last())
        assertTrue("192.168.1.34" !in h)
        assertEquals(253, PrinterScan.hosts("10.0.5.20", 16).size)   // geniş ağda yalnızca kendi /24'ü
        assertEquals(listOf("192.168.1.65", "192.168.1.66"), PrinterScan.hosts("192.168.1.67", 30) - "192.168.1.67")
    }

    @Test fun scanFindsOpenPort() = runBlocking {
        val ss = listener()
        val found = PrinterScan.scan(listOf("127.0.0.3", "127.0.0.1", "127.0.0.2"), ports = listOf(ss.localPort))
        assertEquals(listOf(PrinterScan.Found("127.0.0.1", ss.localPort)), found)
        // 254 adres: yanıt vermeyen ağda bile birkaç saniye
        val t0 = System.currentTimeMillis()
        PrinterScan.scan(PrinterScan.hosts("10.254.254.10", 24))
        val ms = System.currentTimeMillis() - t0
        println("254 adres tarama süresi: $ms ms")
        assertTrue("süre $ms ms", ms < 6000)
        ss.close()
    }

    @Test fun findAndSelectInSettings() {
        val ss = listener(9100)
        PrinterScan.hostsOverride = listOf("127.0.0.2", "127.0.0.1", "127.0.0.3")
        val vm = androidx.lifecycle.ViewModelProvider(rule.activity)[AppViewModel::class.java]
        rule.runOnUiThread { vm.updateNav(NavMode.DRAWER); vm.updatePrinter(PrinterCfg()) }
        rule.onNodeWithContentDescription("Seçenekler").performClick(); rule.onNodeWithText("Ayarlar").performClick()
        rule.onNodeWithText("Ağda yazıcı ara").performScrollTo().performClick()
        rule.waitUntil(10_000) { rule.onAllNodes(hasText("127.0.0.1")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Seç").performScrollTo(); rule.waitForIdle()
        captureScreenRoboImage("build/ekranlar/40-yazici-bulundu.png")
        rule.onNodeWithText("Seç").performClick(); rule.waitForIdle()
        assertEquals("127.0.0.1", vm.printer.ip); assertEquals(9100, vm.printer.port)
        assertEquals(2, rule.onAllNodes(hasText("Seçili")).fetchSemanticsNodes().size)   // menü seçeneği + seçilen yazıcı
        captureScreenRoboImage("build/ekranlar/41-yazici-secildi.png")
        ss.close()
    }
}
