package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Pure Monochrome Studio Palette (Strictly White, Grey, and Black)
val MonoPureBlack = Color(0xFF000000)
val MonoCarbon = Color(0xFF0A0A0A)
val MonoSurface = Color(0xFF121212)
val MonoSurfaceElevated = Color(0xFF1A1A1A)
val MonoSurfaceHigh = Color(0xFF242424)
val MonoBorder = Color(0xFF2E2E2E)
val MonoBorderBright = Color(0xFF525252)
val MonoMuted = Color(0xFF666666)
val MonoSilver = Color(0xFFA3A3A3)
val MonoLightGrey = Color(0xFFD4D4D4)
val MonoPureWhite = Color(0xFFFFFFFF)

enum class VisualThemeMode(val label: String, val subtitle: String) {
    FROSTED_GLASS(
        "Dynamic Frosted Glass",
        "Animated album-art colors behind customizable frosted glass UI"
    ),
    MONOCHROME_GLASS(
        "Monochrome Frosted Glass",
        "Shades of white, grey & black with animated blurred glass depth"
    ),
    PURE_MONOCHROME(
        "Pure Monochrome Studio",
        "Strict matte black, architectural greys & crisp white contrast"
    )
}

/**
 * Converts any color to strict monochrome luminance (shades of white, grey, and black).
 */
fun Color.toMonochrome(): Color {
    val luma = (0.299f * red + 0.587f * green + 0.114f * blue).coerceIn(0f, 1f)
    return Color(luma, luma, luma, alpha)
}
