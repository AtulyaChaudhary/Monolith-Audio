package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

data class GlassThemeConfig(
    val themeMode: VisualThemeMode = VisualThemeMode.FROSTED_GLASS,
    val blurRadiusDp: Float = 52f, // 0f..120f customizable blur slider
    val frostOpacity: Float = 0.22f, // 0.05f..0.85f frosted glass opacity
    val specularIntensity: Float = 0.45f, // 0f..1f rim highlight strength
    val animationSpeed: Float = 1.0f, // 0f..3.0f background motion speed
    val oledPitchBlack: Boolean = false,
    val theatreMode: Boolean = false,
    val isLyricsScreenActive: Boolean = false,
    val customUiFontFamily: FontFamily? = null,
    val customLyricsFontFamily: FontFamily? = null,
    val activePalette: List<Color> = listOf(
        Color(0xFF6366F1),
        Color(0xFFEC4899),
        Color(0xFF06B6D4),
        Color(0xFF8B5CF6),
        Color(0xFF1E1B4B)
    )
) {
    val isGlassActive: Boolean
        get() = !theatreMode && (themeMode == VisualThemeMode.FROSTED_GLASS || themeMode == VisualThemeMode.MONOCHROME_GLASS)

    /**
     * When `isLyricsScreenActive` is true, colors on the lyrics screen are ALWAYS preserved
     * even when the app is in Pure Monochrome Studio or Monochrome Frosted Glass mode.
     */
    val isMonochromeLocked: Boolean
        get() = !isLyricsScreenActive && (themeMode == VisualThemeMode.PURE_MONOCHROME || themeMode == VisualThemeMode.MONOCHROME_GLASS)

    val effectivePalette: List<Color>
        get() = if (isMonochromeLocked) {
            listOf(
                Color(0xFFE5E5E5),
                Color(0xFF8A8A8A),
                Color(0xFF404040),
                Color(0xFFB5B5B5),
                Color(0xFF171717)
            )
        } else {
            activePalette
        }

    val effectiveLyricsFontFamily: FontFamily
        get() = customLyricsFontFamily ?: customUiFontFamily ?: SpaceGroteskFontFamily
}

val LocalGlassTheme = staticCompositionLocalOf { GlassThemeConfig() }

private val MonolithColorScheme = darkColorScheme(
    primary = MonoPureWhite,
    onPrimary = MonoPureBlack,
    primaryContainer = MonoSurfaceHigh,
    onPrimaryContainer = MonoPureWhite,
    secondary = MonoSilver,
    onSecondary = MonoPureBlack,
    secondaryContainer = MonoSurfaceElevated,
    onSecondaryContainer = MonoLightGrey,
    tertiary = MonoLightGrey,
    onTertiary = MonoPureBlack,
    background = MonoPureBlack,
    onBackground = MonoPureWhite,
    surface = MonoSurface,
    onSurface = MonoPureWhite,
    surfaceVariant = MonoSurfaceElevated,
    onSurfaceVariant = MonoSilver,
    outline = MonoBorder,
    outlineVariant = MonoBorderBright
)

@Composable
fun MyApplicationTheme(
    glassConfig: GlassThemeConfig = GlassThemeConfig(),
    content: @Composable () -> Unit
) {
    val bgColor = if (!glassConfig.isLyricsScreenActive && (glassConfig.oledPitchBlack || glassConfig.themeMode == VisualThemeMode.PURE_MONOCHROME)) {
        MonoPureBlack
    } else {
        MonoCarbon
    }
    val scheme = MonolithColorScheme.copy(background = bgColor)
    val dynamicTypography = remember(glassConfig.customUiFontFamily) {
        buildAppTypography(glassConfig.customUiFontFamily)
    }

    CompositionLocalProvider(LocalGlassTheme provides glassConfig) {
        MaterialTheme(
            colorScheme = scheme,
            typography = dynamicTypography,
            content = content
        )
    }
}
