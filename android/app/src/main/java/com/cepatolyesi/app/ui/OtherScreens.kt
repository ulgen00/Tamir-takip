package com.cepatolyesi.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cepatolyesi.app.AppViewModel
import com.cepatolyesi.app.BuildConfigInfo
import com.cepatolyesi.app.CAT_IN
import com.cepatolyesi.app.CAT_OUT
import com.cepatolyesi.app.ImeiState
import com.cepatolyesi.app.NavMode
import com.cepatolyesi.app.STATUSES
import com.cepatolyesi.app.Tx
import com.cepatolyesi.app.amountText
import com.cepatolyesi.app.dfmt
import com.cepatolyesi.app.money
import com.cepatolyesi.app.parseAmount
import com.cepatolyesi.app.today
import com.cepatolyesi.app.uid
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

private fun pad(p: PaddingValues, bottomExtra: Int = 24) =
    PaddingValues(start = 14.dp, end = 14.dp, top = p.calculateTopPadding() + 6.dp, bottom = p.calculateBottomPadding() + bottomExtra.dp)

private fun inMonth(d: String, m: YearMonth) = d.startsWith(m.toString())

// ---------------- Kasa ----------------

@Composable
fun CashScreen(vm: AppViewModel, padding: PaddingValues, onOpen: (String?) -> Unit) {
    val c = LocalColors.current
    val list = vm.store.tx.filter { inMonth(it.date, vm.month) }.sortedByDescending { it.date }
    val inc = list.filter { it.type == "in" }.sumOf { it.amt }
    val exp = list.filter { it.type == "out" }.sumOf { it.amt }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad(padding, 96), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Kasa", style = MaterialTheme.typography.titleLarge) }
        item { MonthBar(vm.month) { vm.month = it } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppCard(Modifier.weight(1f)) { Text("Gelir", style = mutStyle); Text(money(inc), color = c.inc, fontWeight = FontWeight.Bold) }
                AppCard(Modifier.weight(1f)) { Text("Gider", style = mutStyle); Text(money(exp), color = c.out, fontWeight = FontWeight.Bold) }
            }
        }
        if (list.isEmpty()) item {
            Text("Bu ay kayıt yok. + ile gelir veya gider ekleyin.", style = mutStyle, modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        items(list, key = { it.id }) { t ->
            AppCard(onClick = { onOpen(t.id) }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(t.cat, fontWeight = FontWeight.Bold)
                    Text((if (t.type == "in") "+" else "−") + money(t.amt), color = if (t.type == "in") c.inc else c.out, fontWeight = FontWeight.Bold)
                }
                Text(dfmt(t.date) + if (t.note.isNotBlank()) " · " + t.note else "", style = mutStyle)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxSheet(vm: AppViewModel, id: String?, onClose: () -> Unit) {
    val st = vm.store
    val old = remember(id) { st.tx.firstOrNull { it.id == id } }
    var type by remember { mutableStateOf(old?.type ?: "in") }
    var amt by remember { mutableStateOf(amountText(old?.amt ?: 0.0)) }
    var cat by remember { mutableStateOf(old?.cat ?: CAT_IN[0]) }
    var date by remember { mutableStateOf(old?.date ?: today()) }
    var note by remember { mutableStateOf(old?.note ?: "") }
    var ask by remember { mutableStateOf<Triple<String, () -> Unit, (() -> Unit)?>?>(null) }
    var pickDate by remember { mutableStateOf(false) }
    val cats = if (type == "in") CAT_IN else CAT_OUT
    if (cat !in cats) cat = cats[0]

    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = LocalColors.current.bg) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).imePadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (old == null) "Yeni kayıt" else "Kaydı düzenle", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Kapat") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FChip("Gelir", type == "in") { type = "in" }
                FChip("Gider", type == "out") { type = "out" }
            }
            Label("Tutar (₺)", true)
            OutlinedTextField(amt, { amt = it }, Modifier.fillMaxWidth(), singleLine = true, colors = fieldColors(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            Label("Kategori")
            var open by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
                OutlinedTextField(cat, {}, Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable), readOnly = true, colors = fieldColors(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) })
                ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    cats.forEach { k -> DropdownMenuItem(text = { Text(k) }, onClick = { cat = k; open = false }) }
                }
            }
            Label("Tarih")
            OutlinedButton(onClick = { pickDate = true }, Modifier.fillMaxWidth()) { Text(dfmt(date)) }
            Label("Not")
            OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), singleLine = true, colors = fieldColors())
            Row(Modifier.padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val a = parseAmount(amt)
                    if (a <= 0) ask = Triple("Geçerli bir tutar girin.", { ask = null }, null)
                    else {
                        st.upsertTx(Tx(old?.id ?: uid(), type, a, cat, date, note.trim(), old?.rid))
                        vm.month = YearMonth.parse(date.take(7)); onClose()
                    }
                }) { Text("Kaydet", fontWeight = FontWeight.Bold) }
                if (old != null) OutlinedButton(onClick = {
                    ask = Triple("Bu kayıt silinsin mi?", { ask = null; st.deleteTx(old.id); onClose() }, { ask = null })
                }) { Text("Sil", color = LocalColors.current.out) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    if (pickDate) {
        val ds = rememberDatePickerState(initialSelectedDateMillis = LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(onDismissRequest = { pickDate = false },
            confirmButton = { TextButton(onClick = {
                ds.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }; pickDate = false
            }) { Text("Tamam") } },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Vazgeç") } }) { DatePicker(ds) }
    }
    ask?.let { (t, yes, no) -> AskDialog(t, yes, no) }
}

// ---------------- Özet ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SummaryScreen(vm: AppViewModel, padding: PaddingValues) {
    val c = LocalColors.current
    val tx = vm.store.tx.filter { inMonth(it.date, vm.month) }
    val inc = tx.filter { it.type == "in" }.sumOf { it.amt }
    val exp = tx.filter { it.type == "out" }.sumOf { it.amt }
    val open = vm.store.repairs.filter { it.st < 3 }
    val cats = tx.filter { it.type == "out" }.groupBy { it.cat }.mapValues { e -> e.value.sumOf { it.amt } }.toList().sortedByDescending { it.second }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad(padding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Özet", style = MaterialTheme.typography.titleLarge) }
        item { MonthBar(vm.month) { vm.month = it } }
        item {
            AppCard {
                Text("Net kâr", style = mutStyle)
                Text(money(inc - exp), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = if (inc - exp >= 0) c.inc else c.out)
                Text("Gelir ${money(inc)} − Gider ${money(exp)}", style = mutStyle)
            }
        }
        item {
            AppCard {
                Text("Açık işler", style = mutStyle)
                Text("${open.size} cihaz", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("Teslimde beklenen tahsilat: ${money(open.sumOf { it.price })}", style = mutStyle)
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in 0..2) { val (fg, bg) = c.st[i]; Pill("${STATUSES[i]}: ${vm.store.repairs.count { it.st == i }}", fg, bg) }
                }
            }
        }
        item {
            AppCard {
                Text("Gider dağılımı", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                if (cats.isEmpty()) Text("Bu ay gider yok.", style = mutStyle)
                cats.forEach { (k, v) -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(k); Text(money(v), color = c.out) } }
            }
        }
    }
}

// ---------------- IMEI Sorgula ----------------

@Composable
fun ImeiScreen(vm: AppViewModel, padding: PaddingValues) {
    val scope = rememberCoroutineScope()
    val s = remember { ImeiState(vm.store, vm.tacDb, scope, solo = true) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pad(padding)).imePadding()) {
        Text("IMEI sorgulama", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
        AppCard {
            Text("IMEI", style = mutStyle, modifier = Modifier.padding(bottom = 4.dp))
            ImeiBlock(s)
        }
    }
}

// ---------------- Ayarlar ----------------

@Composable
fun SettingsScreen(vm: AppViewModel, padding: PaddingValues) {
    val ctx = LocalContext.current
    var ask by remember { mutableStateOf<Triple<String, () -> Unit, (() -> Unit)?>?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { it.write(vm.store.toJson().toByteArray()) } }.isSuccess
        ask = Triple(if (ok) "Yedek kaydedildi." else "Yedek kaydedilemedi.", { ask = null }, null)
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching { ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() } }.getOrNull()
        val probe = com.cepatolyesi.app.Store(null)
        val valid = text != null && runCatching { probe.fromJson(text) }.isSuccess
        ask = if (!valid) Triple("Bu dosya bir Cep Atölyesi yedeği değil.", { ask = null }, null)
        else Triple("Yedekte ${probe.repairs.size} cihaz ve ${probe.tx.size} kasa kaydı var. Telefondaki kayıtların yerine konsun mu? Şu anki kayıtlar silinir.", {
            ask = null; vm.store.fromJson(text!!); vm.store.save()
        }, { ask = null })
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pad(padding)), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Ayarlar", style = MaterialTheme.typography.titleLarge)
        Text("Sekmelerin yeri", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
        Option("Soldan menü", "Sol üstteki ☰ düğmesiyle ya da ekranın sol kenarından sağa kaydırarak açılır.", vm.nav == NavMode.DRAWER) { vm.updateNav(NavMode.DRAWER) }
        Option("Alt çubuk", "Sekmeler ekranın altında, her zaman görünür.", vm.nav == NavMode.BOTTOM) { vm.updateNav(NavMode.BOTTOM) }

        Text("Yedekleme", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
        Text("Kayıtlar yalnızca bu telefonda durur. Telefon değiştirirken ya da kaybolmaya karşı yedek alın. Yedek dosyası web sürümüyle de uyumludur.", style = mutStyle)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { export.launch("cep-atolyesi-yedek-${today()}.json") }) { Text("Yedeği kaydet") }
            OutlinedButton(onClick = { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) { Text("Yedekten yükle") }
        }
        Text("Sürüm ${BuildConfigInfo.versionName(ctx)}", style = mutStyle, modifier = Modifier.padding(top = 16.dp))
    }
    ask?.let { (t, yes, no) -> AskDialog(t, yes, no) }
}

@Composable
private fun Option(title: String, desc: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalColors.current
    AppCard(onClick = onClick) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontWeight = FontWeight.Bold)
            if (selected) Pill("Seçili", c.st[2].first, c.st[2].second)
        }
        Text(desc, style = mutStyle)
    }
}
