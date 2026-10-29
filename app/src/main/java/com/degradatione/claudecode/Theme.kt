package com.degradatione.claudecode

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── Бренд-шрифты Claude (Anthropic Sans / Anthropic Serif), распакованы из
//    официального deb-пакета claude-desktop и инстанцированы по весам ────────
val ClaudeSans = FontFamily(
    Font(R.font.anthropic_sans_regular, FontWeight.Normal),
    Font(R.font.anthropic_sans_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.anthropic_sans_medium, FontWeight.Medium),
    Font(R.font.anthropic_sans_semibold, FontWeight.SemiBold),
    Font(R.font.anthropic_sans_bold, FontWeight.Bold),
)

val ClaudeSerif = FontFamily(
    Font(R.font.anthropic_serif_regular, FontWeight.Normal),
    Font(R.font.anthropic_serif_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.anthropic_serif_medium, FontWeight.Medium),
    Font(R.font.anthropic_serif_semibold, FontWeight.SemiBold),
)

val MonoFamily = FontFamily.Monospace

@Immutable
data class ClaudePalette(
    val page: Color,
    val sidebar: Color,
    val card: Color,
    val cardAlt: Color,
    val border: Color,
    val borderStrong: Color,
    val text: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val accentPressed: Color,
    val onAccent: Color,
    val success: Color,
    val danger: Color,
    val warn: Color,
    val codeBg: Color,
    val diffAddBg: Color,
    val diffDelBg: Color,
    val diffAddText: Color,
    val diffDelText: Color,
    val isDark: Boolean,
)

val ClaudeDark = ClaudePalette(
    page = Color(0xFF1F1E1D),
    sidebar = Color(0xFF262624),
    card = Color(0xFF2C2B29),
    cardAlt = Color(0xFF30302D),
    border = Color(0xFF3B3A36),
    borderStrong = Color(0xFF4A4842),
    text = Color(0xFFF5F4EF),
    textSecondary = Color(0xFFA6A39B),
    textTertiary = Color(0xFF7B786F),
    accent = Color(0xFFD97757),
    accentPressed = Color(0xFFC6613F),
    onAccent = Color(0xFF1F1E1D),
    success = Color(0xFF6FBF8B),
    danger = Color(0xFFE5796F),
    warn = Color(0xFFE8B45C),
    codeBg = Color(0xFF191918),
    diffAddBg = Color(0xFF1E3226),
    diffDelBg = Color(0xFF3A2523),
    diffAddText = Color(0xFF9AD3A9),
    diffDelText = Color(0xFFE7A19A),
    isDark = true,
)

val ClaudeLight = ClaudePalette(
    page = Color(0xFFFAF9F5),
    sidebar = Color(0xFFF0EEE6),
    card = Color(0xFFF5F3EC),
    cardAlt = Color(0xFFFFFFFF),
    border = Color(0xFFE2DFD3),
    borderStrong = Color(0xFFCFCBBB),
    text = Color(0xFF1F1E1D),
    textSecondary = Color(0xFF6B6860),
    textTertiary = Color(0xFF8C887E),
    accent = Color(0xFFC6613F),
    accentPressed = Color(0xFFA94F31),
    onAccent = Color(0xFFFFFDF9),
    success = Color(0xFF2F7D50),
    danger = Color(0xFFB4342A),
    warn = Color(0xFF9A6B12),
    codeBg = Color(0xFFF0EEE6),
    diffAddBg = Color(0xFFE1F0E4),
    diffDelBg = Color(0xFFFBE3E0),
    diffAddText = Color(0xFF2F7D50),
    diffDelText = Color(0xFFB4342A),
    isDark = false,
)

val LocalClaude = staticCompositionLocalOf { ClaudeDark }

@Composable
fun ClaudeTheme(dark: Boolean, content: @Composable () -> Unit) {
    val p = if (dark) ClaudeDark else ClaudeLight
    val scheme = if (dark) {
        darkColorScheme(
            primary = p.accent,
            onPrimary = p.onAccent,
            background = p.page,
            onBackground = p.text,
            surface = p.card,
            onSurface = p.text,
            surfaceVariant = p.cardAlt,
            onSurfaceVariant = p.textSecondary,
            outline = p.border,
            error = p.danger,
        )
    } else {
        lightColorScheme(
            primary = p.accent,
            onPrimary = p.onAccent,
            background = p.page,
            onBackground = p.text,
            surface = p.card,
            onSurface = p.text,
            surfaceVariant = p.cardAlt,
            onSurfaceVariant = p.textSecondary,
            outline = p.border,
            error = p.danger,
        )
    }
    val base = Typography()
    val typo = Typography(
        displayLarge = base.displayLarge.copy(fontFamily = ClaudeSerif),
        displayMedium = base.displayMedium.copy(fontFamily = ClaudeSerif),
        displaySmall = base.displaySmall.copy(fontFamily = ClaudeSerif),
        headlineLarge = base.headlineLarge.copy(fontFamily = ClaudeSerif, fontWeight = FontWeight.Medium),
        headlineMedium = base.headlineMedium.copy(fontFamily = ClaudeSerif, fontWeight = FontWeight.Medium),
        headlineSmall = base.headlineSmall.copy(fontFamily = ClaudeSerif, fontWeight = FontWeight.Medium),
        titleLarge = TextStyle(fontFamily = ClaudeSerif, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
        titleMedium = TextStyle(fontFamily = ClaudeSans, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
        titleSmall = TextStyle(fontFamily = ClaudeSans, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        bodyLarge = TextStyle(fontFamily = ClaudeSans, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontFamily = ClaudeSans, fontSize = 14.5.sp, lineHeight = 21.sp),
        bodySmall = TextStyle(fontFamily = ClaudeSans, fontSize = 12.5.sp, lineHeight = 17.sp),
        labelLarge = TextStyle(fontFamily = ClaudeSans, fontSize = 14.sp, fontWeight = FontWeight.Medium),
        labelMedium = TextStyle(fontFamily = ClaudeSans, fontSize = 12.sp, fontWeight = FontWeight.Medium),
        labelSmall = TextStyle(fontFamily = ClaudeSans, fontSize = 11.sp, fontWeight = FontWeight.Medium),
    )
    CompositionLocalProvider(LocalClaude provides p) {
        MaterialTheme(colorScheme = scheme, typography = typo, content = content)
    }
}
