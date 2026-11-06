package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily

data class GlassThemeConfig(
    val themeMode: VisualThemeMode = VisualThemeMode.MATERIAL_YOU,
    // Material You Theme Customizations
    val bleedAlbumArtIntoSongList: Boolean = false,
    val materialYouAccentBoost: Float = 0.65f,       // 0.2f..1.0f
    val materialYouSurfaceRichness: Float = 0.12f,   // 0.02f..0.30f
    // True Monochrome Theme Customizations
    val oledPitchBlack: Boolean = true,              // True #000000 pitch black surfaces
    val monoBorderContrast: Float = 0.38f,           // 0.15f..0.85f
    val monoKeepLyricsColored: Boolean = true,       // Keep Lyrics screen full-color in Monochrome
    // Frosted Glass & Lyrics Screen Background Customizations
    val blurRadiusDp: Float = 68f,                   // Universal & Lyrics background blur radius (0dp..120dp)
    val lyricsBlackTintAlpha: Float = 0.34f,         // Protective black tint over Lyrics blur (0.15f..0.80f)
    val lyricsOrbSaturation: Float = 1.0f,           // Lyrics orb color richness (0.0f..1.4f)
    val frostOpacity: Float = 0.86f,                 // Frosted glass card opacity
    val specularIntensity: Float = 0.38f,            // Glass rim highlight
    val animationSpeed: Float = 1.0f,                // 9-Orb motion speed
    val theatreMode: Boolean = false,
    val isLyricsScreenActive: Boolean = false,
    val customUiFontFamily: FontFamily? = null,
    val customLyricsFontFamily: FontFamily? = null,
    val activePalette: List<Color> = listOf(
        Color(0xFF6366F1),
        Color(0xFFEC4899),
        Color(0xFF06B6D4),
        Color(0xFF8B5CF6),
        Color(0xFFF59E0B),
        Color(0xFF10B981),
        Color(0xFFEF4444),
        Color(0xFF3B82F6),
        Color(0xFF1E1B4B)
    )
) {
    /**
     * True Monochrome lock:
     * - When `themeMode` is `PURE_MONOCHROME` or `MONOCHROME_GLASS`, the UI is strictly pitch-black & white.
     * - On the Lyrics screen (`isLyricsScreenActive`), album art colors remain active when `monoKeepLyricsColored` is true.
     * - Album Art covers themselves are NEVER desaturated in any mode!
     */
    val isMonochromeUiMode: Boolean
        get() = themeMode == VisualThemeMode.PURE_MONOCHROME || themeMode == VisualThemeMode.MONOCHROME_GLASS

    val isMonochromeLocked: Boolean
        get() = isMonochromeUiMode && (!isLyricsScreenActive || !monoKeepLyricsColored)

    val effectivePalette: List<Color>
        get() = if (isMonochromeLocked) {
            listOf(
                Color(0xFFFFFFFF),
                Color(0xFFD4D4D4),
                Color(0xFF9E9E9E),
                Color(0xFFE5E5E5),
                Color(0xFF737373),
                Color(0xFF404040),
                Color(0xFFC4C4C4),
                Color(0xFF525252),
                Color(0xFF0A0A0A)
            )
        } else {
            activePalette
        }

    val effectiveLyricsFontFamily: FontFamily
        get() = customLyricsFontFamily ?: customUiFontFamily ?: OutfitThickFontFamily
}

val LocalGlassTheme = staticCompositionLocalOf { GlassThemeConfig() }

@Composable
fun MyApplicationTheme(
    glassConfig: GlassThemeConfig = GlassThemeConfig(),
    content: @Composable () -> Unit
) {
    val isMonoUi = glassConfig.isMonochromeUiMode

    val primarySeed = glassConfig.activePalette.getOrElse(0) { Color(0xFF6366F1) }
    val secondarySeed = glassConfig.activePalette.getOrElse(1) { Color(0xFFEC4899) }
    val tertiarySeed = glassConfig.activePalette.getOrElse(2) { Color(0xFF06B6D4) }

    val accentWhiteMix = (1f - glassConfig.materialYouAccentBoost.coerceIn(0.2f, 1.0f) * 0.65f).coerceIn(0.15f, 0.70f)
    val surfaceColorMix = (1f - glassConfig.materialYouSurfaceRichness.coerceIn(0.02f, 0.30f)).coerceIn(0.70f, 0.98f)

    // In True Monochrome Mode, ALL colors are strictly #000000, pure neutral greys, and #FFFFFF (zero blue tint!)
    val m3Primary = if (isMonoUi) MonoPureWhite else lerp(primarySeed, Color.White, accentWhiteMix)
    val m3PrimaryContainer = if (isMonoUi) Color(0xFF1A1A1A) else lerp(primarySeed, Color(0xFF0D0D0D), 0.70f)
    val m3Secondary = if (isMonoUi) MonoLightGrey else lerp(secondarySeed, Color.White, 0.35f)
    val m3SecondaryContainer = if (isMonoUi) Color(0xFF141414) else lerp(secondarySeed, Color(0xFF101010), 0.76f)

    val m3Surface = if (isMonoUi) {
        if (glassConfig.oledPitchBlack) Color(0xFF050505) else Color(0xFF0E0E0E)
    } else {
        lerp(primarySeed, Color(0xFF0E0E0E), surfaceColorMix)
    }

    val m3SurfaceVariant = if (isMonoUi) {
        if (glassConfig.oledPitchBlack) Color(0xFF0D0D0D) else Color(0xFF171717)
    } else {
        lerp(primarySeed, Color(0xFF171717), (surfaceColorMix - 0.05f).coerceAtLeast(0.68f))
    }

    val bgColor = if (isMonoUi || glassConfig.oledPitchBlack) {
        MonoPureBlack
    } else {
        lerp(primarySeed, MonoPureBlack, 0.92f)
    }

    val monoOutline = Color.White.copy(alpha = glassConfig.monoBorderContrast.coerceIn(0.12f, 0.85f))

    val scheme = darkColorScheme(
        primary = m3Primary,
        onPrimary = MonoPureBlack,
        primaryContainer = m3PrimaryContainer,
        onPrimaryContainer = MonoPureWhite,
        secondary = m3Secondary,
        onSecondary = MonoPureBlack,
        secondaryContainer = m3SecondaryContainer,
        onSecondaryContainer = MonoPureWhite,
        tertiary = if (isMonoUi) MonoPureWhite else lerp(tertiarySeed, Color.White, 0.32f),
        onTertiary = MonoPureBlack,
        background = bgColor,
        onBackground = MonoPureWhite,
        surface = m3Surface,
        onSurface = MonoPureWhite,
        surfaceVariant = m3SurfaceVariant,
        onSurfaceVariant = if (isMonoUi) MonoLightGrey else MonoSilver,
        outline = if (isMonoUi) monoOutline else lerp(primarySeed, MonoBorder, 0.75f),
        outlineVariant = if (isMonoUi) Color.White.copy(alpha = 0.45f) else MonoBorderBright
    )

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
