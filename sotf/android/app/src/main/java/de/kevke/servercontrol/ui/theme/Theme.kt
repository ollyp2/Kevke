package de.kevke.servercontrol.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Palette presets. Deliberately muted — no indigo/violet/cyan gradients,
 * nothing that reads as stock-AI.
 */
enum class AccentPreset(val label: String, val light: Color, val dark: Color) {
    Sage("Salbei", Color(0xFF5E7361), Color(0xFF8FA894)),
    Clay("Ton", Color(0xFF9C6244), Color(0xFFC08A66)),
    Slate("Schiefer", Color(0xFF4A5A6A), Color(0xFF8CA3B8)),
    Rust("Rost", Color(0xFF8C4A3C), Color(0xFFBE7A69)),
    Moss("Moos", Color(0xFF4F6B3A), Color(0xFF8FAE74)),
    Sand("Sand", Color(0xFF8A7A55), Color(0xFFC4B28A)),
}

enum class SurfacePreset(val label: String, val isDark: Boolean, val bg: Color) {
    Ink("Tinte", true, Color(0xFF121212)),
    Charcoal("Kohle", true, Color(0xFF1A1C1B)),
    Bark("Rinde", true, Color(0xFF1C1917)),
    Paper("Papier", false, Color(0xFFFAFAF8)),
    Linen("Leinen", false, Color(0xFFF3F1EC)),
}

data class AppColors(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val onBackground: Color,
    val onMuted: Color,
    val accent: Color,
    val outline: Color,
    val danger: Color,
    val ok: Color,
    val warn: Color,
)

val LocalAppColors = compositionLocalOf {
    buildColors(SurfacePreset.Ink, AccentPreset.Sage)
}

fun buildColors(surface: SurfacePreset, accent: AccentPreset): AppColors {
    val dark = surface.isDark
    val base = surface.bg
    return AppColors(
        background = base,
        surface = base.shift(if (dark) 0.04f else -0.02f),
        surfaceRaised = base.shift(if (dark) 0.08f else -0.05f),
        onBackground = if (dark) Color(0xFFE8E6E3) else Color(0xFF1A1917),
        onMuted = if (dark) Color(0xFF9B9894) else Color(0xFF6E6B67),
        accent = if (dark) accent.dark else accent.light,
        outline = if (dark) Color(0x22FFFFFF) else Color(0x1A000000),
        danger = if (dark) Color(0xFFC77A72) else Color(0xFFA84A40),
        ok = if (dark) Color(0xFF7FA882) else Color(0xFF4E7A52),
        warn = if (dark) Color(0xFFC9A96B) else Color(0xFF9A7A3C),
    )
}

/** Nudge a colour toward white (positive) or black (negative). */
private fun Color.shift(amount: Float): Color {
    val target = if (amount > 0) 1f else 0f
    val t = kotlin.math.abs(amount)
    return Color(
        red = red + (target - red) * t,
        green = green + (target - green) * t,
        blue = blue + (target - blue) * t,
        alpha = alpha,
    )
}

val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Light,
        fontSize = 40.sp, letterSpacing = (-0.5).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, letterSpacing = 0.6.sp,
    ),
)

val CornerRadius = 12.dp

@Composable
fun ServerControlTheme(
    surface: SurfacePreset = if (isSystemInDarkTheme()) SurfacePreset.Ink else SurfacePreset.Paper,
    accent: AccentPreset = AccentPreset.Sage,
    content: @Composable () -> Unit,
) {
    val colors = buildColors(surface, accent)
    val scheme = if (surface.isDark) {
        darkColorScheme(
            primary = colors.accent,
            background = colors.background,
            surface = colors.surface,
            onBackground = colors.onBackground,
            onSurface = colors.onBackground,
            error = colors.danger,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            background = colors.background,
            surface = colors.surface,
            onBackground = colors.onBackground,
            onSurface = colors.onBackground,
            error = colors.danger,
        )
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}
