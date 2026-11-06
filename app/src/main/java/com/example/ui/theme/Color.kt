package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Pure Neutral Monochrome Tokens (Zero Blue Tint — strictly #000000 to #FFFFFF)
val MonoPureBlack = Color(0xFF000000)
val MonoCarbon = Color(0xFF060606)
val MonoSurface = Color(0xFF0D0D0D)
val MonoSurfaceElevated = Color(0xFF161616)
val MonoSurfaceHigh = Color(0xFF222222)
val MonoBorder = Color(0xFF2C2C2C)
val MonoBorderBright = Color(0xFF545454)
val MonoMuted = Color(0xFF787878)
val MonoSilver = Color(0xFFA8A8A8)
val MonoLightGrey = Color(0xFFE0E0E0)
val MonoPureWhite = Color(0xFFFFFFFF)

enum class VisualThemeMode(val label: String, val subtitle: String) {
    MATERIAL_YOU(
        "Material You (Dynamic M3)",
        "Dynamic Material 3 tonal surfaces extracted from your active album art"
    ),
    PURE_MONOCHROME(
        "True Pitch-Black Monochrome",
        "Strict #000000 pitch black & #FFFFFF pure white contrast (zero blue tint)"
    ),
    FROSTED_GLASS(
        "Frosted Glass Aura",
        "Translucent glass surfaces over the animated 9-orb album art backdrop"
    ),
    MONOCHROME_GLASS(
        "Monochrome Tonal Glass",
        "Neutral grayscale glass surfaces with subtle depth"
    )
}

/**
 * Converts any color to strict monochrome luminance (shades of white, grey, and black).
 */
fun Color.toMonochrome(): Color {
    val luma = (0.299f * red + 0.587f * green + 0.114f * blue).coerceIn(0f, 1f)
    return Color(luma, luma, luma, alpha)
}

/**
 * Clamps overly bright or near-white colors so background orbs on the Lyrics screen
 * never wash out white lyric text even when an album cover is mostly white.
 */
fun Color.clampMaxLuminanceForLyricsBackdrop(maxLuma: Float = 0.68f): Color {
    val luma = 0.299f * red + 0.587f * green + 0.114f * blue
    if (luma <= maxLuma || luma <= 0.001f) return this
    val scale = (maxLuma / luma).coerceIn(0.25f, 1f)
    return Color(
        red = (red * scale).coerceIn(0f, 1f),
        green = (green * scale).coerceIn(0f, 1f),
        blue = (blue * scale).coerceIn(0f, 1f),
        alpha = alpha
    )
}
