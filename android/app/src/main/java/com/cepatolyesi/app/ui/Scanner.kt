package com.cepatolyesi.app.ui

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.cepatolyesi.app.imeiFromAll
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import kotlin.coroutines.resume

private fun newScanner(): BarcodeScanner = BarcodeScanning.getClient(
    BarcodeScannerOptions.Builder().setBarcodeFormats(
        Barcode.FORMAT_CODE_128, Barcode.FORMAT_CODE_39, Barcode.FORMAT_EAN_13, Barcode.FORMAT_ITF,
        Barcode.FORMAT_QR_CODE, Barcode.FORMAT_DATA_MATRIX,
    ).build()
)

/**
 * Fotoğraftaki tüm barkodları okur. Dönen: okunan değerler (boşsa barkod bulunamadı), null ise fotoğraf açılamadı.
 */
suspend fun scanPhoto(ctx: Context, uri: Uri): List<String>? = suspendCancellableCoroutine { cont ->
    val img = runCatching { InputImage.fromFilePath(ctx, uri) }.getOrNull()
    if (img == null) { cont.resume(null); return@suspendCancellableCoroutine }
    val sc = newScanner()
    sc.process(img)
        .addOnSuccessListener { list -> cont.resume(list.mapNotNull { it.rawValue }) }
        .addOnFailureListener { cont.resume(emptyList()) }
        .addOnCompleteListener { sc.close() }
}

/** Tam ekran kamera: IMEI barkodu görülünce onImei çağrılır ve kapanır. */
@Composable
fun ScannerDialog(onImei: (String) -> Unit, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val ctx = LocalContext.current
        val owner = LocalLifecycleOwner.current
        var hint by remember { mutableStateOf("IMEI barkodunu çerçeveye getirin") }
        val previewView = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

        DisposableEffect(Unit) {
            val scanner = newScanner()
            val exec = Executors.newSingleThreadExecutor()
            var done = false
            var provider: ProcessCameraProvider? = null
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                val p = runCatching { future.get() }.getOrNull() ?: run { hint = "Kamera açılamadı."; return@addListener }
                provider = p
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(exec, @OptIn(ExperimentalGetImage::class) { proxy ->
                    val media = proxy.image
                    if (media == null || done) { proxy.close(); return@setAnalyzer }
                    scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                        .addOnSuccessListener { list ->
                            val imei = imeiFromAll(list.mapNotNull { it.rawValue })
                            if (imei != null && !done) { done = true; onImei(imei) }
                            else if (list.isNotEmpty()) hint = "Barkod görüldü ama IMEI değil. IMEI barkodunu çerçeveye getirin."
                        }
                        .addOnCompleteListener { proxy.close() }
                })
                runCatching {
                    p.unbindAll()
                    p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                }.onFailure { hint = "Kamera açılamadı." }
            }, ContextCompat.getMainExecutor(ctx))
            onDispose {
                done = true
                provider?.unbindAll()
                scanner.close()
                exec.shutdown()
            }
        }

        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.weight(1f))
                Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f).border(2.dp, Color.White.copy(alpha = .6f), RoundedCornerShape(12.dp))) {
                    AndroidView({ previewView }, Modifier.fillMaxSize().padding(2.dp))
                }
                Text(hint, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp))
                Spacer(Modifier.weight(1f))
                Button(onClick = onClose) { Text("Kapat") }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
