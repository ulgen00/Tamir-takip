package com.cepatolyesi.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Cep Atölyesi renkleri (web sürümüyle aynı). st: durum etiketleri (yazı, zemin) */
data class AppColors(
    val bg: Color, val surf: Color, val ink: Color, val mut: Color, val line: Color,
    val acc: Color, val onAcc: Color, val inc: Color, val out: Color, val badBg: Color,
    val st: List<Pair<Color, Color>>,
)

val LightColors = AppColors(
    bg = Color(0xFFF3EBDD), surf = Color(0xFFFBF6EC), ink = Color(0xFF3B2A1E), mut = Color(0xFF7A6555), line = Color(0xFFE0D3BF),
    acc = Color(0xFF7A4A2A), onAcc = Color(0xFFFFF8EE), inc = Color(0xFF3F7A3A), out = Color(0xFFA8392B), badBg = Color(0xFFF6DCD7),
    st = listOf(Color(0xFF8A5A00) to Color(0xFFF4E2C0), Color(0xFF7A4A2A) to Color(0xFFEBDCC8), Color(0xFF3F7A3A) to Color(0xFFDDEBD3), Color(0xFF7A6555) to Color(0xFFE8DFD2)),
)
val DarkColors = AppColors(
    bg = Color(0xFF1E1712), surf = Color(0xFF2A211A), ink = Color(0xFFF1E6D6), mut = Color(0xFFB5A28E), line = Color(0xFF3D3127),
    acc = Color(0xFFD9A877), onAcc = Color(0xFF24180E), inc = Color(0xFF8FCB86), out = Color(0xFFF09080), badBg = Color(0xFF3D1F1B),
    st = listOf(Color(0xFFF0C36B) to Color(0xFF3A2C12), Color(0xFFD9A877) to Color(0xFF3D2C1C), Color(0xFF8FCB86) to Color(0xFF22351F), Color(0xFFB5A28E) to Color(0xFF332A22)),
)

val LocalColors = staticCompositionLocalOf { LightColors }

@Composable
fun AppTheme(dark: Boolean, content: @Composable () -> Unit) {
    val c = if (dark) DarkColors else LightColors
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = c.acc, onPrimary = c.onAcc, primaryContainer = c.st[1].second, onPrimaryContainer = c.acc,
        secondary = c.acc, onSecondary = c.onAcc, secondaryContainer = c.st[1].second, onSecondaryContainer = c.ink,
        background = c.bg, onBackground = c.ink, surface = c.bg, onSurface = c.ink,
        surfaceVariant = c.surf, onSurfaceVariant = c.mut, outline = c.line, outlineVariant = c.line,
        surfaceContainerLowest = c.bg, surfaceContainerLow = c.surf, surfaceContainer = c.surf,
        surfaceContainerHigh = c.surf, surfaceContainerHighest = c.surf, surfaceBright = c.surf, surfaceDim = c.bg,
        error = c.out, inverseSurface = c.ink, inverseOnSurface = c.bg,
    )
    val t = Typography()
    val typo = t.copy(
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
        bodyLarge = t.bodyLarge.copy(fontSize = 17.sp),
    )
    CompositionLocalProvider(LocalColors provides c) { MaterialTheme(colorScheme = scheme, typography = typo, content = content) }
}

val mutStyle @Composable get() = TextStyle(color = LocalColors.current.mut, fontSize = 14.sp)
