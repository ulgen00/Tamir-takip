package com.cepatolyesi.app.ui

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.cepatolyesi.app.ImeiState
import com.cepatolyesi.app.imeiFromAll
import kotlinx.coroutines.launch

/** IMEI kutusu + Yapıştır / Barkod tara / Galeriden seç + model mesajı + e-Devlet kartı */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImeiBlock(s: ImeiState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf(false) }

    val camPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) scanning = true else s.msg = "Kamera izni verilmedi. IMEI’yi yazın, yapıştırın ya da galeriden fotoğraf seçin."
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        s.msg = "Fotoğraftaki barkod okunuyor…"
        scope.launch {
            val values = scanPhoto(ctx, uri)
            val imei = values?.let { imeiFromAll(it) }
            when {
                values == null -> s.msg = "Fotoğraf açılamadı. Başka bir fotoğraf seçin ya da IMEI’yi elle yazın."
                imei != null -> s.setFromScan(imei, "fotoğraftan okundu")
                values.isNotEmpty() -> s.msg = "Fotoğrafta IMEI barkodu bulunamadı (başka barkodlar var). IMEI barkodunun net göründüğü bir fotoğraf seçin."
                else -> s.msg = "Fotoğrafta barkod bulunamadı. Barkodu daha yakından ve net çekip tekrar deneyin ya da IMEI’yi elle yazın."
            }
        }
    }

    OutlinedTextField(
        value = s.imei, onValueChange = { s.input(it) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("15 haneli IMEI") }, colors = fieldColors(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
    )
    FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallBtn("Yapıştır", Icons.Filled.ContentPaste) {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val t = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
            val imei = com.cepatolyesi.app.imeiFrom(t)
            if (imei != null) s.setFromScan(imei, "yapıştırıldı") else s.msg = "Panoda 15 haneli IMEI bulunamadı. Önce IMEI’yi kopyalayın."
        }
        SmallBtn("Barkod tara", Icons.Filled.QrCodeScanner) {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) scanning = true
            else camPerm.launch(Manifest.permission.CAMERA)
        }
        SmallBtn("Galeriden seç", Icons.Filled.PhotoLibrary) {
            gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }
    Text(s.msg, style = mutStyle, modifier = Modifier.padding(top = 8.dp))
    EdvArea(s)

    if (scanning) ScannerDialog(onImei = { scanning = false; s.setFromScan(it, "okundu") }, onClose = { scanning = false })
}

@Composable
private fun SmallBtn(text: String, icon: ImageVector, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, border = androidx.compose.foundation.BorderStroke(1.dp, LocalColors.current.line)) {
        Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(text)
    }
}

@Composable
private fun EdvArea(s: ImeiState) {
    val c = LocalColors.current
    val e = s.edv
    val cap = s.captcha
    when {
        s.edvBusy -> Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("e-Devlet’te sorgulanıyor…", style = mutStyle)
        }
        e != null -> AppCard(Modifier.padding(top = 8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("e-Devlet IMEI kaydı", fontWeight = FontWeight.Bold); EdvPill(e)
            }
            Text(e.durum, style = mutStyle)
            if (e.kaynak.isNotBlank()) Text("Kaynak: ${e.kaynak}", style = mutStyle)
            if (e.model.isNotBlank()) Text(e.model, style = mutStyle)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Sorgu tarihi: ${e.tarih}", style = mutStyle)
                OutlinedButton(onClick = { s.refreshEdv() }) { Text("Yenile") }
            }
        }
        cap != null -> {
            var code by remember(cap) { mutableStateOf("") }
            AppCard(Modifier.padding(top = 8.dp)) {
                Text("e-Devlet doğrulama kodu istiyor", fontWeight = FontWeight.Bold)
                if (cap.error.isNotBlank()) Text(cap.error, color = c.out, fontSize = 14.sp)
                cap.image?.let {
                    Image(it, "Doğrulama kodu resmi", Modifier.padding(vertical = 8.dp).height(60.dp).background(Color.White, RoundedCornerShape(8.dp)))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(code, { code = it }, Modifier.weight(1f), singleLine = true, placeholder = { Text("Resimdeki kodu yazın") }, colors = fieldColors(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { if (code.isNotBlank()) s.queryEdv(code = code.trim()) }))
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { if (code.isNotBlank()) s.queryEdv(code = code.trim()) }) { Text("Gönder") }
                }
            }
        }
        s.edvErr != null -> Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(s.edvErr!!, style = mutStyle, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { s.queryEdv() }) { Text("Tekrar dene") }
        }
    }
}
