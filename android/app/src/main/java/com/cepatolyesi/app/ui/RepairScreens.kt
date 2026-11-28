package com.cepatolyesi.app.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.cepatolyesi.app.AppViewModel
import com.cepatolyesi.app.ImeiState
import com.cepatolyesi.app.Repair
import com.cepatolyesi.app.STATUSES
import com.cepatolyesi.app.amountText
import com.cepatolyesi.app.dfmt
import com.cepatolyesi.app.fmtPhone
import com.cepatolyesi.app.money
import com.cepatolyesi.app.parseAmount
import com.cepatolyesi.app.phoneDigits
import com.cepatolyesi.app.phoneEdit
import com.cepatolyesi.app.today
import com.cepatolyesi.app.uid

@Composable
fun RepairsScreen(vm: AppViewModel, padding: PaddingValues, onOpen: (String?) -> Unit) {
    val st = vm.store
    var flt by rememberSaveable { mutableStateOf(-1) }
    val list = st.repairs.filter { flt == -1 || it.st == flt }.sortedByDescending { it.date }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = padding.calculateTopPadding() + 6.dp, bottom = padding.calculateBottomPadding() + 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Tamire gelen cihazlar", style = androidx.compose.material3.MaterialTheme.typography.titleLarge) }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { FChip("Hepsi ${st.repairs.size}", flt == -1) { flt = -1 } }
                items(STATUSES.size) { i -> FChip("${STATUSES[i]} ${st.repairs.count { it.st == i }}", flt == i) { flt = i } }
            }
        }
        if (list.isEmpty()) item {
            Text("Henüz cihaz yok. Sağ alttaki + ile ilk cihazı kaydedin.", style = mutStyle, modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        items(list, key = { it.id }) { r -> RepairCard(r) { onOpen(r.id) } }
    }
}

@Composable
fun FChip(text: String, on: Boolean, onClick: () -> Unit) {
    val c = LocalColors.current
    FilterChip(selected = on, onClick = onClick, label = { Text(text) },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.acc, selectedLabelColor = c.onAcc, containerColor = c.surf),
        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = on, borderColor = c.line, selectedBorderColor = c.acc))
}

@Composable
private fun RepairCard(r: Repair, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(r.model, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); StatusPill(r.st)
        }
        Text(r.name + if (r.phone.isNotBlank()) " · " + fmtPhone(r.phone) else "", style = mutStyle)
        if (r.issue.isNotBlank()) Text(r.issue, style = mutStyle)
        if (r.imei.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
            Text("IMEI ${r.imei}", style = mutStyle); r.edv?.let { Spacer(Modifier.padding(start = 6.dp)); EdvPill(it) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(dfmt(r.date), style = mutStyle); Text(money(r.price), fontWeight = FontWeight.Bold)
        }
    }
}

/** Yeni cihaz / cihaz detayı: alttan açılır, aşağı çekince ya da × ile kapanır. id == null: yeni kayıt */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RepairSheet(vm: AppViewModel, id: String?, onClose: () -> Unit) {
    val st = vm.store
    val ctx = LocalContext.current
    val old = remember(id) { st.repairs.firstOrNull { it.id == id } }
    var name by remember { mutableStateOf(old?.name ?: "") }
    var phone by remember { mutableStateOf(TextFieldValue(old?.phone?.let { fmtPhone(it) } ?: "")) }
    var model by remember { mutableStateOf(old?.model ?: "") }
    var autoModel by remember { mutableStateOf("") }
    var issue by remember { mutableStateOf(old?.issue ?: "") }
    var price by remember { mutableStateOf(amountText(old?.price ?: 0.0)) }
    var cost by remember { mutableStateOf(amountText(old?.cost ?: 0.0)) }
    var sup by remember { mutableStateOf(old?.sup ?: "") }
    var status by remember { mutableStateOf(old?.st ?: 0) }
    var ask by remember { mutableStateOf<Triple<String, () -> Unit, (() -> Unit)?>?>(null) }
    val scope = rememberCoroutineScope()
    val imei = remember {
        ImeiState(st, vm.tacDb, scope, solo = false, initImei = old?.imei ?: "", initEdv = old?.edv) { m ->
            if (model.isBlank() || model == autoModel) { model = m; autoModel = m; true } else false
        }
    }
    val fName = remember { FocusRequester() }; val fPhone = remember { FocusRequester() }; val fModel = remember { FocusRequester() }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val recId = remember { old?.id ?: uid() }  // etiketteki kayıt no kaydetmeden önce de sabit kalsın
    var printing by remember { mutableStateOf(false) }

    fun build(): Repair = Repair(
        id = recId, date = old?.date ?: today(), name = name.trim(), phone = phone.text.trim(), model = model.trim(),
        issue = issue.trim(), imei = imei.imei, sup = sup.trim(), price = parseAmount(price), cost = parseAmount(cost),
        st = status, edv = imei.edvFor(imei.imei) ?: old?.edv?.takeIf { it.imei == imei.imei },
    )
    fun validate(): Boolean {
        val bad = when {
            name.isBlank() -> "Müşteri adını yazın." to fName
            phoneDigits(phone.text) < 10 -> "Müşterinin telefon numarasını yazın (ör. 0532 123 45 67)." to fPhone
            model.isBlank() -> "Cihaz modelini yazın." to fModel
            else -> null
        } ?: return true
        ask = Triple(bad.first, { ask = null; runCatching { bad.second.requestFocus() } }, null)
        return false
    }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheet, containerColor = LocalColors.current.bg) {
        // Başlık ve × sabit; form altında kayar
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (old == null) "Yeni cihaz" else "Cihaz detayı", style = androidx.compose.material3.MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Kapat") }
            }
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
            Label("Müşteri adı", true)
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth().focusRequester(fName), singleLine = true, colors = fieldColors(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
            Label("Telefon numarası", true)
            OutlinedTextField(
                value = phone,
                onValueChange = { v -> val (t, c) = phoneEdit(phone.text, v.text, v.selection.end); phone = TextFieldValue(t, TextRange(c)) },
                modifier = Modifier.fillMaxWidth().focusRequester(fPhone).onFocusChanged { f ->
                    if (f.isFocused && phone.text.isEmpty()) phone = TextFieldValue("0", TextRange(1))
                    if (!f.isFocused && phone.text.trim() == "0") phone = TextFieldValue("")
                },
                singleLine = true, colors = fieldColors(), placeholder = { Text("0532 123 45 67") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )
            Label("Cihaz (marka / model)", true)
            OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth().focusRequester(fModel), singleLine = true, colors = fieldColors(), placeholder = { Text("Örn. Samsung A54") })
            Label("IMEI")
            ImeiBlock(imei)
            Label("Arıza / yapılacak iş")
            OutlinedTextField(issue, { issue = it }, Modifier.fillMaxWidth(), minLines = 2, colors = fieldColors(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Label("Ücret (₺)")
                    OutlinedTextField(price, { price = it }, singleLine = true, colors = fieldColors(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                Column(Modifier.weight(1f)) {
                    Label("Parça maliyeti (₺)")
                    OutlinedTextField(cost, { cost = it }, singleLine = true, colors = fieldColors(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
            }
            Label("Parçanın alındığı toptancı")
            SupplierField(sup, st.repairs.map { it.sup }.filter { it.isNotBlank() }.distinct()) { sup = it }
            Label("Durum")
            StatusField(status) { status = it }

            FlowRow(Modifier.padding(top = 16.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (validate()) {
                        val r = build(); st.upsertRepair(r)
                        // Yeni kayıtta otomatik etiket (Ayarlar'dan açılır); form kapansa da arka planda gönderilir
                        if (old == null && vm.printer.autoPrint) vm.printLabel(r) { msg -> Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show() }
                        onClose()
                    }
                }) { Text("Kaydet", fontWeight = FontWeight.Bold) }
                OutlinedButton(enabled = !printing, onClick = {
                    if (name.isBlank() && model.isBlank()) { ask = Triple("Etiket için en azından müşteri adını ve cihaz modelini yazın.", { ask = null }, null); return@OutlinedButton }
                    printing = true
                    vm.printLabel(build()) { msg -> printing = false; ask = Triple(msg, { ask = null }, null) }
                }) {
                    Icon(Icons.Filled.Print, null, Modifier.padding(end = 6.dp)); Text(if (printing) "Gönderiliyor…" else "Etiket yazdır")
                }
                if (old != null && old.st < 3) OutlinedButton(onClick = {
                    val r = build()
                    ask = Triple("${money(r.price)} tahsil edilip kasaya gelir olarak eklensin mi?", {
                        ask = null; st.deliver(r); onClose()
                    }, { ask = null })
                }) { Text("Teslim et ve tahsil et") }
                if (old != null && phone.text.isNotBlank()) OutlinedButton(onClick = {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone.text.filter { it.isDigit() || it == '+' }))) }
                }) { Text("Ara") }
                if (old != null) OutlinedButton(onClick = {
                    ask = Triple("Bu cihaz kaydı silinsin mi?", { ask = null; st.deleteRepair(old.id); onClose() }, { ask = null })
                }) { Text("Sil", color = LocalColors.current.out) }
            }
            Spacer(Modifier.height(8.dp))
            }
        }
    }
    ask?.let { (t, yes, no) -> AskDialog(t, yes, no) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupplierField(value: String, all: List<String>, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val matches = all.filter { it.contains(value, ignoreCase = true) && it != value }
    ExposedDropdownMenuBox(expanded = open && matches.isNotEmpty(), onExpandedChange = { open = it }) {
        OutlinedTextField(value, { onChange(it); open = true }, Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable), singleLine = true,
            colors = fieldColors(), placeholder = { Text("Örn. Ahmet Telekom") })
        ExposedDropdownMenu(expanded = open && matches.isNotEmpty(), onDismissRequest = { open = false }) {
            matches.forEach { m -> DropdownMenuItem(text = { Text(m) }, onClick = { onChange(m); open = false }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusField(value: Int, onChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(STATUSES[value], {}, Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable), readOnly = true, colors = fieldColors(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) })
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            STATUSES.forEachIndexed { i, s -> DropdownMenuItem(text = { Text(s) }, onClick = { onChange(i); open = false }) }
        }
    }
}

