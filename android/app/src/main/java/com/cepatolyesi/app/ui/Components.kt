package com.cepatolyesi.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cepatolyesi.app.Edv
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalColors.current
    val colors = CardDefaults.cardColors(containerColor = c.surf, contentColor = c.ink)
    val border = BorderStroke(1.dp, c.line)
    val shape = RoundedCornerShape(12.dp)
    if (onClick != null) Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, colors = colors, border = border) { Column(Modifier.padding(12.dp), content = content) }
    else Card(modifier = modifier.fillMaxWidth(), shape = shape, colors = colors, border = border) { Column(Modifier.padding(12.dp), content = content) }
}

/** Renkli küçük etiket (durum, Kayıtlı/Kapalı vb.) */
@Composable
fun Pill(text: String, fg: androidx.compose.ui.graphics.Color, bg: androidx.compose.ui.graphics.Color) {
    Text(text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.background(bg, RoundedCornerShape(99.dp)).padding(horizontal = 9.dp, vertical = 2.dp))
}

@Composable
fun StatusPill(st: Int) { val (fg, bg) = LocalColors.current.st[st.coerceIn(0, 3)]; Pill(com.cepatolyesi.app.STATUSES[st.coerceIn(0, 3)], fg, bg) }

@Composable
fun EdvPill(e: Edv) {
    val c = LocalColors.current
    when (e.sinif) {
        "kayitli" -> Pill("Kayıtlı", c.st[2].first, c.st[2].second)
        "kayitsiz" -> Pill("Kayıtsız", c.out, c.badBg)
        "kapali" -> Pill("Kapalı", c.out, c.badBg)
        else -> Pill("Durum", c.st[3].first, c.st[3].second)
    }
}

/** ‹ Kasım 2026 › */
@Composable
fun MonthBar(month: YearMonth, onChange: (YearMonth) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        OutlinedIconButton(onClick = { onChange(month.minusMonths(1)) }, border = BorderStroke(1.dp, LocalColors.current.line)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Önceki ay")
        }
        val tr = Locale("tr", "TR")
        Text(month.month.getDisplayName(TextStyle.FULL_STANDALONE, tr).replaceFirstChar { it.titlecase(tr) } + " " + month.year, fontWeight = FontWeight.Bold)
        OutlinedIconButton(onClick = { onChange(month.plusMonths(1)) }, border = BorderStroke(1.dp, LocalColors.current.line)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Sonraki ay")
        }
    }
}

/** Ortada açılan onay/uyarı kutusu. onNo null ise sadece "Tamam" düğmesi. */
@Composable
fun AskDialog(text: String, onYes: () -> Unit, onNo: (() -> Unit)?, yes: String = if (onNo == null) "Tamam" else "Evet") {
    AlertDialog(
        onDismissRequest = { (onNo ?: onYes)() },
        text = { Text(text, fontSize = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { Button(onClick = onYes) { Text(yes) } },
        dismissButton = onNo?.let { { TextButton(onClick = it) { Text("Vazgeç") } } },
        containerColor = LocalColors.current.bg,
    )
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = LocalColors.current.surf, unfocusedContainerColor = LocalColors.current.surf,
    unfocusedBorderColor = LocalColors.current.line,
)

@Composable
fun Label(text: String, required: Boolean = false) {
    Row(Modifier.padding(top = 10.dp, bottom = 4.dp)) {
        Text(text, style = mutStyle)
        if (required) Text(" *", color = LocalColors.current.out, fontSize = 14.sp)
    }
}

