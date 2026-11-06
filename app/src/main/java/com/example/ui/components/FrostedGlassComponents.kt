package com.example.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalGlassTheme
import com.example.ui.theme.MonoBorder
import com.example.ui.theme.MonoCarbon
import com.example.ui.theme.MonoPureBlack
import com.example.ui.theme.MonoPureWhite
import com.example.ui.theme.MonoSurface
import com.example.ui.theme.MonoSurfaceElevated
import com.example.ui.theme.VisualThemeMode
import com.example.ui.theme.toMonochrome
import java.io.File
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Layer 0: Dynamic Animated Album Art & Color-Palette Fluid Canvas.
 *
 * Importantly, when `glass.isLyricsScreenActive` is true, the full-color album-art aura
 * is ALWAYS rendered even if the user is in Pure Monochrome Studio or Monochrome Glass mode!
 */
@Composable
fun AnimatedAlbumArtBackdrop(
    isPlaying: Boolean,
    playbackSpeed: Float,
    modifier: Modifier = Modifier
) {
    val glass = LocalGlassTheme.current
    val palette = glass.effectivePalette

    val c0 by animateColorAsState(palette.getOrElse(0) { Color(0xFFD97706) }, tween(900), label = "c0")
    val c1 by animateColorAsState(palette.getOrElse(1) { Color(0xFFDC2626) }, tween(900), label = "c1")
    val c2 by animateColorAsState(palette.getOrElse(2) { Color(0xFF2563EB) }, tween(900), label = "c2")
    val c3 by animateColorAsState(palette.getOrElse(3) { Color(0xFF7C3AED) }, tween(900), label = "c3")
    val c4 by animateColorAsState(palette.getOrElse(4) { Color(0xFF1E1B4B) }, tween(900), label = "c4")

    var phaseSeconds by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(glass.animationSpeed, isPlaying, playbackSpeed) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { nanos ->
                if (lastNanos != 0L) {
                    val dt = ((nanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
                    val playBoost = if (isPlaying) (0.65f + 0.35f * playbackSpeed.coerceIn(0.2f, 2.5f)) else 0.35f
                    phaseSeconds += dt * glass.animationSpeed * playBoost
                }
                lastNanos = nanos
            }
        }
    }

    val blurDp = glass.blurRadiusDp.coerceIn(0f, 120f)
    val isStrictMonoNonLyrics = !glass.isLyricsScreenActive && glass.themeMode == VisualThemeMode.PURE_MONOCHROME
    val baseBg = if (!glass.isLyricsScreenActive && (glass.oledPitchBlack || isStrictMonoNonLyrics)) {
        MonoPureBlack
    } else {
        MonoCarbon
    }

    if (isStrictMonoNonLyrics && glass.oledPitchBlack) {
        Box(modifier = modifier.fillMaxSize().background(MonoPureBlack))
        return
    }

    val blurModifier = if (blurDp > 0.5f) {
        Modifier.blur(radius = blurDp.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseBg)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(blurModifier)
        ) {
            val w = size.width
            val h = size.height
            val minDim = min(w, h)
            val t = phaseSeconds

            val normBlur = (blurDp / 120f).coerceIn(0f, 1f)
            val radiusScale = 0.38f + normBlur * 0.85f
            // On the Lyrics Screen, always render full rich album-art colors even in Ultra Monochrome mode!
            val alphaStrength = if (glass.isLyricsScreenActive) {
                0.84f
            } else if (glass.themeMode == VisualThemeMode.PURE_MONOCHROME) {
                0.14f
            } else {
                0.78f
            }

            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        c4.copy(alpha = alphaStrength * 0.88f),
                        baseBg
                    )
                )
            )

            val orb1Center = Offset(
                x = w * (0.28f + 0.24f * cos(t * 0.55f)),
                y = h * (0.25f + 0.20f * sin(t * 0.42f))
            )
            val orb2Center = Offset(
                x = w * (0.74f + 0.22f * sin(t * 0.48f + 1.2f)),
                y = h * (0.38f + 0.25f * cos(t * 0.37f + 0.8f))
            )
            val orb3Center = Offset(
                x = w * (0.35f + 0.26f * sin(t * 0.31f + 2.5f)),
                y = h * (0.72f + 0.18f * cos(t * 0.51f + 1.9f))
            )
            val orb4Center = Offset(
                x = w * (0.68f + 0.20f * cos(t * 0.63f + 3.8f)),
                y = h * (0.78f + 0.16f * sin(t * 0.44f + 2.2f))
            )

            fun drawDynamicAlbumOrb(center: Offset, color: Color, baseR: Float, rotDeg: Float) {
                val r = (baseR * radiusScale).coerceAtLeast(24f)
                if (normBlur < 0.25f) {
                    val sharpWeight = (1f - normBlur / 0.25f).coerceIn(0f, 1f)
                    rotate(degrees = rotDeg, pivot = center) {
                        drawRoundRect(
                            color = color.copy(alpha = alphaStrength * 0.65f * sharpWeight),
                            topLeft = Offset(center.x - r * 0.55f, center.y - r * 0.55f),
                            size = Size(r * 1.1f, r * 1.1f),
                            cornerRadius = CornerRadius(r * 0.18f, r * 0.18f)
                        )
                    }
                }
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            color.copy(alpha = alphaStrength),
                            color.copy(alpha = alphaStrength * (0.48f + 0.25f * normBlur)),
                            Color.Transparent
                        ),
                        center = center,
                        radius = r * 1.4f
                    ),
                    radius = r * 1.4f,
                    center = center
                )
            }

            drawDynamicAlbumOrb(orb1Center, c0, minDim * 0.62f, t * 22f)
            drawDynamicAlbumOrb(orb2Center, c1, minDim * 0.58f, -t * 18f)
            drawDynamicAlbumOrb(orb3Center, c2, minDim * 0.64f, t * 15f)
            drawDynamicAlbumOrb(orb4Center, c3, minDim * 0.54f, -t * 26f)
        }
    }
}

/**
 * Universal Frosted Glass Container.
 *
 * When `opaqueModal = true` (used for Settings, Speed/Pitch, Queue, and Glass Customizer modals),
 * it enforces a high-density `#0C0C12` (`96%` opacity) surface so background text is NEVER
 * visible through the Settings or modal pages!
 */
@Composable
fun FrostedGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    elevated: Boolean = false,
    accentHighlight: Boolean = false,
    opaqueModal: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val glass = LocalGlassTheme.current

    if (opaqueModal) {
        val modalFillBrush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF171720).copy(alpha = 0.97f),
                Color(0xFF0B0B10).copy(alpha = 0.98f)
            )
        )
        val borderBrush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.42f),
                Color.White.copy(alpha = 0.14f)
            )
        )
        Box(
            modifier = modifier
                .clip(shape)
                .background(brush = modalFillBrush)
                .border(BorderStroke(1.dp, borderBrush), shape),
            content = content
        )
        return
    }

    if (glass.themeMode == VisualThemeMode.PURE_MONOCHROME) {
        val fill = when {
            accentHighlight -> Color(0xFF262626)
            elevated -> MonoSurfaceElevated
            else -> MonoSurface
        }
        val borderColor = if (accentHighlight) MonoPureWhite else MonoBorder
        Box(
            modifier = modifier
                .clip(shape)
                .background(fill)
                .border(1.dp, borderColor, shape),
            content = content
        )
        return
    }

    val baseOpacity = if (glass.theatreMode) {
        (glass.frostOpacity * 0.35f).coerceIn(0.04f, 0.25f)
    } else {
        val boost = if (elevated) 0.08f else 0f
        (glass.frostOpacity + boost).coerceIn(0.05f, 0.88f)
    }

    val topSheenAlpha = (baseOpacity * 0.85f + 0.06f).coerceIn(0.05f, 0.90f)
    val bottomTintAlpha = (baseOpacity * 1.15f).coerceIn(0.06f, 0.92f)

    val glassFillBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = topSheenAlpha * 0.52f),
            Color(0xFF141418).copy(alpha = bottomTintAlpha),
            Color.White.copy(alpha = topSheenAlpha * 0.28f)
        ),
        start = Offset(0f, 0f),
        end = Offset(900f, 900f)
    )

    val rimAlpha = if (accentHighlight) {
        (glass.specularIntensity + 0.35f).coerceIn(0.25f, 0.95f)
    } else {
        glass.specularIntensity.coerceIn(0.08f, 0.85f)
    }

    val specularBorderBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = rimAlpha),
            Color.White.copy(alpha = rimAlpha * 0.22f),
            Color.White.copy(alpha = rimAlpha * 0.55f)
        ),
        start = Offset(0f, 0f),
        end = Offset(600f, 600f)
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(brush = glassFillBrush)
            .border(
                BorderStroke(if (accentHighlight) 1.5.dp else 1.dp, specularBorderBrush),
                shape
            ),
        content = content
    )
}

/**
 * Unblurred, crystal-clear Album Art renderer.
 * When `forceFullColor = true` (or on the Lyrics Screen), always renders full album-art colors
 * even when Ultra Monochrome mode is active!
 */
@Composable
fun CrispAlbumArtCover(
    title: String,
    subtitle: String,
    palette: List<Color>,
    coverArtPath: String?,
    sizeDp: Dp,
    cornerRadiusDp: Dp = 14.dp,
    forceFullColor: Boolean = false,
    modifier: Modifier = Modifier
) {
    val glass = LocalGlassTheme.current
    val useMonochrome = !forceFullColor && glass.isMonochromeLocked

    val effectiveColors = remember(palette, useMonochrome) {
        if (useMonochrome) {
            palette.map { it.toMonochrome() }
        } else {
            palette
        }
    }

    val bitmap = remember(coverArtPath) {
        coverArtPath?.let { path ->
            val f = File(path)
            if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
        }
    }

    val shape = RoundedCornerShape(cornerRadiusDp)

    Box(
        modifier = modifier
            .clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.22f), shape),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            val colorFilter = if (useMonochrome) {
                ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
            } else null

            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "$title album art",
                contentScale = ContentScale.Crop,
                colorFilter = colorFilter,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val c0 = effectiveColors.getOrElse(0) { Color(0xFF6366F1) }
            val c1 = effectiveColors.getOrElse(1) { Color(0xFFEC4899) }
            val c2 = effectiveColors.getOrElse(2) { Color(0xFF06B6D4) }
            val c3 = effectiveColors.getOrElse(3) { Color(0xFF1E1B4B) }

            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(c0, c1, c3),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height)
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(c2.copy(alpha = 0.85f), Color.Transparent),
                        center = Offset(size.width * 0.75f, size.height * 0.28f),
                        radius = size.minDimension * 0.65f
                    ),
                    radius = size.minDimension * 0.65f,
                    center = Offset(size.width * 0.75f, size.height * 0.28f)
                )
                val center = Offset(size.width / 2f, size.height / 2f)
                drawCircle(
                    color = Color.Black.copy(alpha = 0.28f),
                    radius = size.minDimension * 0.34f,
                    center = center
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.35f),
                    radius = size.minDimension * 0.34f,
                    center = center,
                    style = Stroke(width = 1.5f)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    radius = size.minDimension * 0.07f,
                    center = center
                )
            }

            if (sizeDp >= 72.dp && title.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Text(
                        text = title.take(2).uppercase(),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = if (sizeDp > 120.dp) 18.sp else 11.sp
                        ),
                        color = Color.White.copy(alpha = 0.92f)
                    )
                }
            }
        }
    }
}
