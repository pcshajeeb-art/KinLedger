package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

@Immutable
data class LedgerSemanticColors(
    val isDark: Boolean,
    val income: Color,
    val incomeContainer: Color,
    val incomeGlow: Color,
    val expense: Color,
    val expenseContainer: Color,
    val debt: Color,
    val debtContainer: Color,
    val debtGlow: Color,
    val transfer: Color,
    val transferContainer: Color,
    val border: Color,
    val mutedText: Color,
    val cardBackground: Color,
    val glassFillTop: Color,
    val glassFillBottom: Color,
    val glassRimTopLeft: Color,
    val glassRimBottomRight: Color,
    val glassInnerHighlight: Color,
    val glassInnerShadow: Color,
    val glassDropShadow: Color,
    val glassSheetBackground: Color,
    val glassNavBackgroundTop: Color,
    val glassNavBackgroundBottom: Color,
    val backdropTop: Color,
    val backdropMid: Color,
    val backdropBottom: Color,
    val ambientOrbPrimary: Color,
    val ambientOrbSecondary: Color,
    val ambientOrbWatermark: Color
)

private val LightSemanticColors = LedgerSemanticColors(
    isDark = false,
    income = DeepTeal,
    incomeContainer = TealContainerLight,
    incomeGlow = Color(0xFF14B8A6),
    expense = CoralRose,
    expenseContainer = CoralContainerLight,
    debt = WarmAmber,
    debtContainer = AmberContainerLight,
    debtGlow = Color(0xFFF59E0B),
    transfer = OceanBlue,
    transferContainer = OceanContainerLight,
    border = Color(0xAAFFFFFF),
    mutedText = SlateTextMuted,
    cardBackground = Color(0xA6FFFFFF),
    glassFillTop = Color(0xD6FFFFFF),
    glassFillBottom = Color(0x7FFFFFFF),
    glassRimTopLeft = Color(0xF5FFFFFF),
    glassRimBottomRight = Color(0x4094A3B8),
    glassInnerHighlight = Color(0xEEFFFFFF),
    glassInnerShadow = Color(0x160F172A),
    glassDropShadow = Color(0x280F172A),
    glassSheetBackground = Color(0xF0F1F5FA),
    glassNavBackgroundTop = Color(0xD8FFFFFF),
    glassNavBackgroundBottom = Color(0xB8E6EDF7),
    backdropTop = Color(0xFFDFE9F5),
    backdropMid = Color(0xFFECF2FA),
    backdropBottom = Color(0xFFDCE6F4),
    ambientOrbPrimary = Color(0x380D9488),
    ambientOrbSecondary = Color(0x303B82F6),
    ambientOrbWatermark = Color(0x1AE11D48)
)

private val DarkSemanticColors = LedgerSemanticColors(
    isDark = true,
    income = EmeraldVibrant,
    incomeContainer = TealContainerDark,
    incomeGlow = CyanGlow,
    expense = CoralBright,
    expenseContainer = CoralContainerDark,
    debt = AmberBright,
    debtContainer = AmberContainerDark,
    debtGlow = CoralBright,
    transfer = SkyBright,
    transferContainer = SkyContainerDark,
    border = Color(0x4D94A3B8),
    mutedText = DarkTextMuted,
    cardBackground = Color(0x33475569),
    glassFillTop = Color(0x3894A3B8),
    glassFillBottom = Color(0x24475569),
    glassRimTopLeft = Color(0xA6FFFFFF),
    glassRimBottomRight = Color(0x4094A3B8),
    glassInnerHighlight = Color(0x52FFFFFF),
    glassInnerShadow = Color(0x66000000),
    glassDropShadow = Color(0xB8000000),
    glassSheetBackground = Color(0xF50B111C),
    glassNavBackgroundTop = Color(0xEB0C1320),
    glassNavBackgroundBottom = Color(0xFA06090F),
    backdropTop = DarkBackground,
    backdropMid = DarkBackgroundMid,
    backdropBottom = DarkBackgroundDeep,
    ambientOrbPrimary = Color(0x1C10B981),
    ambientOrbSecondary = Color(0x183B82F6),
    ambientOrbWatermark = Color(0x1CF43F5E)
)

val LocalLedgerColors = staticCompositionLocalOf { DarkSemanticColors }

private val LightColorScheme = lightColorScheme(
    primary = DeepTeal,
    onPrimary = CrispWhite,
    primaryContainer = TealContainerLight,
    onPrimaryContainer = DeepTealDark,
    secondary = WarmAmber,
    onSecondary = CrispWhite,
    secondaryContainer = AmberContainerLight,
    onSecondaryContainer = Color(0xFF92400E),
    tertiary = CoralRose,
    onTertiary = CrispWhite,
    tertiaryContainer = CoralContainerLight,
    onTertiaryContainer = Color(0xFF9F1239),
    background = SlateBackground,
    onBackground = SlateTextPrimary,
    surface = Color(0xCCFFFFFF),
    onSurface = SlateTextPrimary,
    surfaceVariant = Color(0x99E2E8F0),
    onSurfaceVariant = SlateTextSecondary,
    outline = Color(0x8094A3B8),
    outlineVariant = Color(0x4D94A3B8),
    error = CoralRose,
    onError = CrispWhite,
    errorContainer = CoralContainerLight,
    onErrorContainer = Color(0xFF9F1239)
)

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldVibrant,
    onPrimary = Color(0xFF031F17),
    primaryContainer = TealContainerDark,
    onPrimaryContainer = TealContainerLight,
    secondary = AmberBright,
    onSecondary = Color(0xFF3B1A02),
    secondaryContainer = AmberContainerDark,
    onSecondaryContainer = AmberContainerLight,
    tertiary = CoralBright,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = CoralContainerDark,
    onTertiaryContainer = CoralContainerLight,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = Color(0x59CBD5E1),
    outlineVariant = Color(0x3394A3B8),
    error = CoralBright,
    onError = Color(0xFFFFFFFF),
    errorContainer = CoralContainerDark,
    onErrorContainer = CoralContainerLight
)

val KinLedgerShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

enum class ThemePreference {
    SYSTEM, LIGHT, DARK
}

@Composable
fun KinLedgerTheme(
    themePreference: ThemePreference = ThemePreference.DARK,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themePreference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    val colorScheme = if (useDarkTheme) DarkColorScheme else LightColorScheme
    val semanticColors = if (useDarkTheme) DarkSemanticColors else LightSemanticColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity
            if (activity != null) {
                val controller = WindowCompat.getInsetsController(activity.window, view)
                controller.isAppearanceLightStatusBars = !useDarkTheme
                controller.isAppearanceLightNavigationBars = !useDarkTheme
            }
        }
    }

    CompositionLocalProvider(LocalLedgerColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = KinLedgerTypography,
            shapes = KinLedgerShapes,
            content = content
        )
    }
}
