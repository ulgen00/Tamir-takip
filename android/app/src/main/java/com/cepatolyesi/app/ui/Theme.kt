package com.cepatolyesi.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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

/**
 * Renk paleti. hue: ana renk tonu (0-360), sat: doygunluk çarpanı (gri tonlu paletler için küçük).
 * "kahve" uygulamanın özgün renkleridir, diğerleri tondan üretilir.
 */
data class Palette(val id: String, val name: String, val hue: Float, val sat: Float = 1f)

val PALETTES = listOf(
    Palette("kahve", "Kahve", 24f),
    Palette("okyanus", "Okyanus", 205f),
    Palette("orman", "Orman", 150f),
    Palette("lavanta", "Lavanta", 265f),
    Palette("bordo", "Bordo", 345f),
    Palette("gece", "Gece", 215f, sat = 0.18f),
)

private fun hsl(h: Float, s: Float, l: Float) = Color.hsl(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))

/** WCAG kontrast oranı */
fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f; val lb = b.luminance() + 0.05f
    return maxOf(la, lb) / minOf(la, lb)
}

/** Açıklığı adım adım değiştirerek istenen kontrasta ulaşan rengi bulur (yeşil/sarı gibi parlak tonlar için) */
private fun tuned(h: Float, s: Float, startL: Float, step: Float, ok: (Color) -> Boolean): Color {
    var l = startL
    repeat(60) { val c = hsl(h, s, l); if (ok(c)) return c; l = (l + step).coerceIn(0f, 1f) }
    return hsl(h, s, l)
}

/** Verilen tondan açık ya da koyu tema renkleri üretir; yazı ve vurgu renkleri okunabilir kontrasta ayarlanır */
fun paletteColors(hue: Float, sat: Float, dark: Boolean): AppColors {
    val f = sat
    val base = if (dark) DarkColors else LightColors
    fun on(c: Color, bgs: List<Color>, min: Float) = bgs.all { contrastRatio(c, it) >= min }
    return if (!dark) {
        val bg = hsl(hue, 0.32f * f, 0.935f); val surf = hsl(hue, 0.40f * f, 0.975f); val stBg = hsl(hue, 0.36f * f, 0.87f)
        val onAcc = hsl(hue, 0.60f * f, 0.985f)
        val ink = tuned(hue, 0.30f * f, 0.16f, -0.01f) { on(it, listOf(bg, surf), 7.5f) }
        val mut = tuned(hue, 0.14f * f, 0.38f, -0.01f) { on(it, listOf(bg, surf, stBg), 4.8f) }
        val acc = tuned(hue, 0.50f * f, 0.32f, -0.01f) { on(it, listOf(bg, surf, stBg, onAcc), 4.8f) }
        base.copy(
            bg = bg, surf = surf, ink = ink, mut = mut, line = hsl(hue, 0.24f * f, 0.84f), acc = acc, onAcc = onAcc,
            st = listOf(base.st[0], acc to stBg, base.st[2], mut to hsl(hue, 0.18f * f, 0.88f)),
        )
    } else {
        val bg = hsl(hue, 0.24f * f, 0.085f); val surf = hsl(hue, 0.20f * f, 0.13f); val stBg = hsl(hue, 0.30f * f, 0.20f)
        val onAcc = hsl(hue, 0.50f * f, 0.10f)
        val ink = tuned(hue, 0.35f * f, 0.91f, 0.01f) { on(it, listOf(bg, surf), 7.5f) }
        val mut = tuned(hue, 0.16f * f, 0.66f, 0.01f) { on(it, listOf(bg, surf, stBg), 4.8f) }
        val acc = tuned(hue, 0.55f * f, 0.68f, 0.01f) { on(it, listOf(bg, surf, stBg, onAcc), 4.8f) }
        base.copy(
            bg = bg, surf = surf, ink = ink, mut = mut, line = hsl(hue, 0.18f * f, 0.21f), acc = acc, onAcc = onAcc,
            st = listOf(base.st[0], acc to stBg, base.st[2], mut to hsl(hue, 0.16f * f, 0.18f)),
        )
    }
}

/** Seçili palete göre renkler. id "ozel" ise customHue kullanılır. */
fun colorsFor(id: String, customHue: Float, dark: Boolean): AppColors = when (id) {
    "kahve" -> if (dark) DarkColors else LightColors
    "ozel" -> paletteColors(customHue, 1f, dark)
    else -> PALETTES.firstOrNull { it.id == id }?.let { paletteColors(it.hue, it.sat, dark) } ?: if (dark) DarkColors else LightColors
}

val LocalColors = staticCompositionLocalOf { LightColors }

@Composable
fun AppTheme(dark: Boolean, colors: AppColors = if (dark) DarkColors else LightColors, content: @Composable () -> Unit) {
    val c = colors
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
