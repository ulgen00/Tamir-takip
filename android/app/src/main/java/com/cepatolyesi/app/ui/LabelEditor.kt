package com.cepatolyesi.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cepatolyesi.app.AppViewModel
import com.cepatolyesi.app.ItemKind
import com.cepatolyesi.app.LabelItem
import com.cepatolyesi.app.LabelPrinter
import com.cepatolyesi.app.LabelTemplate
import com.cepatolyesi.app.Repair
import com.cepatolyesi.app.today
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Önizlemede kullanılan örnek kayıt */
val SAMPLE_REPAIR = Repair("K7M2Q9", today(), "Ayşe Yılmaz", "05321234567", "Samsung Galaxy A54", "Ekran değişimi", "356015060194519", "Ahmet Telekom", 1500.0, 400.0, 1, null)

/**
 * Etiket tasarım ekranı: önizlemede öğeye dokunarak seç, sürükleyerek taşı; altta seçili öğenin ayarları.
 * Her değişiklik hemen kaydedilir.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LabelEditor(vm: AppViewModel, onClose: () -> Unit) {
    val c = LocalColors.current
    val cfg = vm.printer
    var tpl by remember { mutableStateOf(vm.currentTemplate()) }
    var selId by remember { mutableStateOf<String?>(null) }
    var ask by remember { mutableStateOf<Triple<String, () -> Unit, (() -> Unit)?>?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val sel = tpl.items.firstOrNull { it.id == selId }
    fun commit(t: LabelTemplate) { tpl = t; vm.updateTemplate(t) }
    fun update(i: LabelItem) = commit(tpl.copy(items = tpl.items.map { if (it.id == i.id) i else it }))
    fun add(i: LabelItem) { commit(tpl.copy(items = tpl.items + i)); selId = i.id }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(c.bg).safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Etiket tasarımı", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Kapat") }
            }
            Text("${cfg.widthMm} x ${cfg.heightMm} mm · Öğeyi seçmek için dokunun, taşımak için sürükleyin. Değişiklikler kendiliğinden kaydedilir.",
                style = mutStyle, modifier = Modifier.padding(horizontal = 14.dp))

            Preview(tpl, cfg.widthMm, cfg.heightMm, vm, sel, onSelect = { selId = it }, onMove = { id, dx, dy ->
                tpl.items.firstOrNull { it.id == id }?.let { i ->
                    update(i.copy(x = (i.x + dx).coerceIn(-i.boxW() + 1f, cfg.widthMm - 1f), y = (i.y + dy).coerceIn(-i.boxH() + 0.5f, cfg.heightMm - 0.5f)))
                }
            })

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
                Text("Ekle", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val cx = cfg.widthMm / 2f
                    AssistChip(onClick = { add(LabelItem(kind = ItemKind.TEXT, text = "Yeni yazı", x = 2f, y = cfg.heightMm / 2f - 2f, w = cfg.widthMm - 4f, size = 3f)) }, label = { Text("+ Yazı") })
                    AssistChip(onClick = { add(LabelItem(kind = ItemKind.BARCODE, text = "{kayitno}", x = 2f, y = cfg.heightMm / 2f - 4f, w = cx, h = 8f)) }, label = { Text("+ Barkod") })
                    AssistChip(onClick = { add(LabelItem(kind = ItemKind.QR, text = "{telefon}", x = cfg.widthMm - 14f, y = cfg.heightMm / 2f - 6f, w = 12f, h = 12f)) }, label = { Text("+ QR kod") })
                    AssistChip(onClick = { add(LabelItem(kind = ItemKind.LINE, x = 2f, y = cfg.heightMm / 2f, w = cfg.widthMm - 4f, h = 0.3f)) }, label = { Text("+ Çizgi") })
                    AssistChip(onClick = { add(LabelItem(kind = ItemKind.BOX, x = 1f, y = 1f, w = cfg.widthMm - 2f, h = cfg.heightMm - 2f)) }, label = { Text("+ Kutu") })
                }

                if (sel == null) {
                    Text("Düzenlemek için önizlemedeki bir öğeye dokunun.", style = mutStyle, modifier = Modifier.padding(vertical = 12.dp))
                } else Props(sel, cfg.widthMm, cfg.heightMm, ::update,
                    onDelete = { commit(tpl.copy(items = tpl.items.filterNot { it.id == sel.id })); selId = null },
                    onCopy = { add(sel.copy(id = com.cepatolyesi.app.uid(), x = sel.x + 2f, y = sel.y + 2f)) })

                Row(Modifier.padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !busy, onClick = {
                        busy = true
                        scope.launch { val e = LabelPrinter.print(SAMPLE_REPAIR, vm.printer, tpl); busy = false; ask = Triple(e ?: "Deneme etiketi yazıcıya gönderildi.", { ask = null }, null) }
                    }) { Text(if (busy) "Gönderiliyor…" else "Deneme yazdır") }
                    OutlinedButton(onClick = {
                        ask = Triple("Etiket hazır tasarıma dönsün mü? Yaptığınız değişiklikler silinir.", {
                            ask = null; vm.updateTemplate(null); tpl = vm.currentTemplate(); selId = null
                        }, { ask = null })
                    }) { Text("Varsayılana dön") }
                }
            }
        }
        ask?.let { (t, yes, no) -> AskDialog(t, yes, no) }
    }
}

/** Etiketin yazıcıdaki haliyle önizlemesi; seçili öğe çerçeveli */
@Composable
private fun Preview(
    tpl: LabelTemplate, wMm: Int, hMm: Int, vm: AppViewModel, sel: LabelItem?,
    onSelect: (String?) -> Unit, onMove: (String, Float, Float) -> Unit,
) {
    val c = LocalColors.current
    val bmp = remember(tpl, vm.printer) { LabelPrinter.render(SAMPLE_REPAIR, vm.printer, tpl).asImageBitmap() }
    val items by rememberUpdatedState(tpl.items)
    val selected by rememberUpdatedState(sel)
    Box(Modifier.fillMaxWidth().background(c.surf).padding(14.dp), contentAlignment = Alignment.Center) {
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(wMm.toFloat() / hMm).border(1.dp, c.line).background(Color.White).clipToBounds().testTag("etiket-onizleme")) {
            val pxPerMm = constraints.maxWidth.toFloat() / wMm
            fun hit(p: Offset): LabelItem? = items.lastOrNull { i ->
                val mx = p.x / pxPerMm; val my = p.y / pxPerMm
                mx >= i.x - 1f && mx <= i.x + i.boxW() + 1f && my >= i.y - 1f && my <= i.y + i.boxH() + 1f
            }
            var dragId by remember { mutableStateOf<String?>(null) }
            Image(bmp, "Etiket önizlemesi", Modifier.fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { p -> onSelect(hit(p)?.id) } }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { p -> val h = hit(p) ?: selected; dragId = h?.id; onSelect(h?.id) },
                        onDragEnd = { dragId = null }, onDragCancel = { dragId = null },
                        onDrag = { ch, d -> ch.consume(); dragId?.let { onMove(it, d.x / pxPerMm, d.y / pxPerMm) } },
                    )
                },
                contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.None)
            sel?.let { s ->
                val density = LocalDensity.current
                with(density) {
                    Box(Modifier.offset { IntOffset((s.x * pxPerMm).roundToInt(), (s.y * pxPerMm).roundToInt()) }
                        .size((s.boxW() * pxPerMm).toDp(), (s.boxH() * pxPerMm).toDp())
                        .border(BorderStroke(2.dp, c.acc)))
                }
            }
        }
    }
}

/** Seçili öğenin ayarları */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Props(i: LabelItem, wMm: Int, hMm: Int, onChange: (LabelItem) -> Unit, onDelete: () -> Unit, onCopy: () -> Unit) {
    Text("Seçili: ${i.kind.title}", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
    if (i.kind == ItemKind.TEXT || i.kind == ItemKind.BARCODE || i.kind == ItemKind.QR) {
        Label(if (i.kind == ItemKind.TEXT) "Yazı" else "İçerik")
        OutlinedTextField(i.text, { onChange(i.copy(text = it)) }, Modifier.fillMaxWidth(), colors = fieldColors(), singleLine = i.kind != ItemKind.QR)
        Text("Kayıttan bilgi eklemek için dokunun:", style = mutStyle, modifier = Modifier.padding(top = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LabelTemplate.FIELDS.forEach { (name, ph) ->
                AssistChip(onClick = { onChange(i.copy(text = if (i.kind == ItemKind.TEXT) (i.text + " " + ph).trim() else ph)) }, label = { Text(name) })
            }
        }
        if (i.kind == ItemKind.BARCODE) Text("Barkod yalnızca Türkçe karakter içermeyen yazıları basabilir (rakam ve İngilizce harfler).", style = mutStyle)
    }
    if (i.kind == ItemKind.TEXT) {
        SliderRow("Yazı boyu", i.size, 1.5f, 12f, "mm") { onChange(i.copy(size = it)) }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Kalın", Modifier.weight(1f)); Switch(i.bold, { onChange(i.copy(bold = it)) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
            listOf("Sola", "Ortaya", "Sağa").forEachIndexed { k, t -> FChip(t, i.align == k) { onChange(i.copy(align = k)) } }
        }
    }
    SliderRow(if (i.kind == ItemKind.TEXT) "Genişlik (sığmayan yazı … ile kısalır)" else "Genişlik", i.w, 2f, wMm.toFloat(), "mm") { onChange(i.copy(w = it)) }
    when (i.kind) {
        ItemKind.LINE -> SliderRow("Kalınlık", i.h, 0.15f, 2f, "mm") { onChange(i.copy(h = it)) }
        ItemKind.BARCODE, ItemKind.QR, ItemKind.BOX -> SliderRow("Yükseklik", i.h, 2f, hMm.toFloat(), "mm") { onChange(i.copy(h = it)) }
        else -> {}
    }
    SliderRow("Soldan uzaklık", i.x, 0f, wMm.toFloat(), "mm") { onChange(i.copy(x = it)) }
    SliderRow("Üstten uzaklık", i.y, 0f, hMm.toFloat(), "mm") { onChange(i.copy(y = it)) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        OutlinedButton(onClick = onCopy) { Text("Kopyala") }
        OutlinedButton(onClick = onDelete) { Text("Sil", color = LocalColors.current.out) }
    }
}

@Composable
private fun SliderRow(title: String, value: Float, min: Float, max: Float, unit: String, onChange: (Float) -> Unit) {
    Column(Modifier.padding(top = 6.dp)) {
        Row { Text(title, style = mutStyle, modifier = Modifier.weight(1f)); Text("${"%.1f".format(value)} $unit", style = mutStyle) }
        Slider(value.coerceIn(min, max), { onChange((it * 10).roundToInt() / 10f) }, valueRange = min..max)
    }
}
