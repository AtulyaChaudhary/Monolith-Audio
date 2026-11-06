package com.example.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalGlassTheme
import com.example.ui.theme.MonoPureBlack
import com.example.ui.theme.VisualThemeMode
import com.example.ui.theme.clampMaxLuminanceForLyricsBackdrop
import com.example.ui.theme.toMonochrome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val RemoteBitmapCache = object : LruCache<String, ImageBitmap>(80) {}
private val LocalCoverArtBitmapCache = object : LruCache<String, ImageBitmap>(180) {}

/**
 * Loads and caches remote images (such as Spicy Lyrics contributor circular PFPs
 * and search result album art URLs) off the main thread.
 */
@Composable
fun RemoteUrlImage(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallback: @Composable BoxScope.() -> Unit = {}
) {
    var bitmap by remember(imageUrl) {
        mutableStateOf(imageUrl?.let { RemoteBitmapCache.get(it) })
    }

    LaunchedEffect(imageUrl) {
        val url = imageUrl?.trim()
        if (url.isNullOrEmpty() || bitmap != null) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) {
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    setRequestProperty("User-Agent", "MonolithAudio/1.0")
                }
                if (conn.responseCode in 200..299) {
                    conn.inputStream.use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                } else null
            } catch (_: Exception) {
                null
            }
        }
        if (loaded != null) {
            RemoteBitmapCache.put(url, loaded)
            bitmap = loaded
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            fallback()
        }
    }
}

/**
 * Single Universal Animated 9-Orb Album Art Background:
 * - Uses ONE universal blur layer across the entire screen background (never per song row).
 * - Renders 9 rich color orbs sampled from the active album art.
 * - On the Lyrics Screen (`isLyricsScreenActive == true`), applies:
 *   1. Luminance clamping on overly white/bright swatches (`clampMaxLuminanceForLyricsBackdrop`)
 *   2. A dedicated **protective Black Tint overlay** (`lyricsBlackTintAlpha`, default 34%) over the blur
 *      so white and dimmed lyrics remain 100% readable even when the album cover is mostly white!
 */
@Composable
fun AnimatedAlbumArtBackdrop(
    isPlaying: Boolean,
    playbackSpeed: Float,
    modifier: Modifier = Modifier
) {
    val glass = LocalGlassTheme.current
    val useFullColorOrbs = (glass.isLyricsScreenActive && (!glass.isMonochromeUiMode || glass.monoKeepLyricsColored)) ||
        (!glass.isMonochromeUiMode && (glass.bleedAlbumArtIntoSongList || glass.themeMode == VisualThemeMode.FROSTED_GLASS))

    val rawPalette = if (useFullColorOrbs) {
        glass.activePalette
    } else {
        glass.effectivePalette
    }

    fun prepareOrbColor(c: Color): Color {
        val saturated = if (glass.isLyricsScreenActive && glass.lyricsOrbSaturation != 1.0f) {
            val gray = c.toMonochrome()
            lerp(gray, c, glass.lyricsOrbSaturation.coerceIn(0f, 1.4f))
        } else {
            c
        }
        return if (glass.isLyricsScreenActive) {
            saturated.clampMaxLuminanceForLyricsBackdrop(maxLuma = 0.66f)
        } else {
            saturated
        }
    }

    val p0 = prepareOrbColor(rawPalette.getOrElse(0) { Color(0xFFD97706) })
    val p1 = prepareOrbColor(rawPalette.getOrElse(1) { Color(0xFFDC2626) })
    val p2 = prepareOrbColor(rawPalette.getOrElse(2) { Color(0xFF2563EB) })
    val p3 = prepareOrbColor(rawPalette.getOrElse(3) { Color(0xFF7C3AED) })
    val p4 = prepareOrbColor(rawPalette.getOrElse(4) { lerp(p0, p1, 0.5f) })
    val p5 = prepareOrbColor(rawPalette.getOrElse(5) { lerp(p1, p2, 0.5f) })
    val p6 = prepareOrbColor(rawPalette.getOrElse(6) { lerp(p2, p3, 0.5f) })
    val p7 = prepareOrbColor(rawPalette.getOrElse(7) { lerp(p0, p2, 0.45f) })
    val p8 = prepareOrbColor(rawPalette.getOrElse(8) { Color(0xFF121212) })

    val c0 by animateColorAsState(p0, tween(850), label = "c0")
    val c1 by animateColorAsState(p1, tween(850), label = "c1")
    val c2 by animateColorAsState(p2, tween(850), label = "c2")
    val c3 by animateColorAsState(p3, tween(850), label = "c3")
    val c4 by animateColorAsState(p4, tween(850), label = "c4")
    val c5 by animateColorAsState(p5, tween(850), label = "c5")
    val c6 by animateColorAsState(p6, tween(850), label = "c6")
    val c7 by animateColorAsState(p7, tween(850), label = "c7")
    val c8 by animateColorAsState(p8, tween(850), label = "c8")

    val shouldAnimateOrbs = glass.isLyricsScreenActive ||
        glass.bleedAlbumArtIntoSongList ||
        glass.themeMode == VisualThemeMode.FROSTED_GLASS ||
        glass.themeMode == VisualThemeMode.MONOCHROME_GLASS

    val baseBg = if (glass.isMonochromeUiMode || glass.oledPitchBlack) {
        MonoPureBlack
    } else {
        MaterialTheme.colorScheme.background
    }

    if (!shouldAnimateOrbs) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(baseBg)
        )
        return
    }

    var phaseSeconds by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(glass.animationSpeed, isPlaying, playbackSpeed) {
        if (glass.animationSpeed <= 0.01f) return@LaunchedEffect
        var lastNanos = 0L
        while (true) {
            withFrameNanos { nanos ->
                if (lastNanos != 0L) {
                    val dt = ((nanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
                    val playBoost = if (isPlaying) (0.65f + 0.35f * playbackSpeed.coerceIn(0.2f, 2.5f)) else 0.30f
                    phaseSeconds += dt * glass.animationSpeed * playBoost
                }
                lastNanos = nanos
            }
        }
    }

    val blurDp = glass.blurRadiusDp.coerceIn(0f, 120f)
    val singleUniversalBlur = if (blurDp > 1f) {
        Modifier.blur(
            radius = blurDp.dp,
            edgeTreatment = BlurredEdgeTreatment.Unbounded
        )
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
                .then(singleUniversalBlur)
        ) {
            val w = size.width
            val h = size.height
            val minDim = min(w, h)
            val t = phaseSeconds

            val alphaStrength = when {
                glass.isLyricsScreenActive -> 0.84f
                glass.themeMode == VisualThemeMode.FROSTED_GLASS -> 0.56f
                glass.bleedAlbumArtIntoSongList -> 0.44f
                else -> 0.32f
            }

            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        c8.copy(alpha = alphaStrength * 0.88f),
                        baseBg
                    )
                )
            )

            fun drawOrb(cxFrac: Float, cyFrac: Float, color: Color, radiusScale: Float) {
                val center = Offset(w * cxFrac, h * cyFrac)
                val r = (minDim * radiusScale).coerceAtLeast(40f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            color.copy(alpha = alphaStrength),
                            color.copy(alpha = alphaStrength * 0.55f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = r
                    ),
                    radius = r,
                    center = center
                )
            }

            // 9 Multi-Color Album Art Orbs moving along independent harmonic trajectories
            drawOrb(
                cxFrac = 0.20f + 0.22f * cos(t * 0.52f),
                cyFrac = 0.18f + 0.16f * sin(t * 0.41f),
                color = c0,
                radiusScale = 0.68f
            )
            drawOrb(
                cxFrac = 0.78f + 0.18f * sin(t * 0.47f + 1.1f),
                cyFrac = 0.22f + 0.20f * cos(t * 0.36f + 0.7f),
                color = c1,
                radiusScale = 0.64f
            )
            drawOrb(
                cxFrac = 0.50f + 0.25f * sin(t * 0.38f + 2.0f),
                cyFrac = 0.36f + 0.18f * cos(t * 0.49f + 1.4f),
                color = c2,
                radiusScale = 0.66f
            )
            drawOrb(
                cxFrac = 0.24f + 0.20f * cos(t * 0.44f + 2.8f),
                cyFrac = 0.54f + 0.22f * sin(t * 0.33f + 2.1f),
                color = c3,
                radiusScale = 0.62f
            )
            drawOrb(
                cxFrac = 0.76f + 0.21f * cos(t * 0.59f + 3.5f),
                cyFrac = 0.58f + 0.19f * sin(t * 0.42f + 2.9f),
                color = c4,
                radiusScale = 0.60f
            )
            drawOrb(
                cxFrac = 0.32f + 0.24f * sin(t * 0.35f + 4.2f),
                cyFrac = 0.80f + 0.15f * cos(t * 0.53f + 3.4f),
                color = c5,
                radiusScale = 0.65f
            )
            drawOrb(
                cxFrac = 0.70f + 0.19f * sin(t * 0.62f + 5.0f),
                cyFrac = 0.84f + 0.14f * cos(t * 0.46f + 4.1f),
                color = c6,
                radiusScale = 0.58f
            )
            drawOrb(
                cxFrac = 0.15f + 0.18f * sin(t * 0.49f + 5.7f),
                cyFrac = 0.40f + 0.24f * cos(t * 0.39f + 4.8f),
                color = c7,
                radiusScale = 0.56f
            )
            drawOrb(
                cxFrac = 0.55f + 0.22f * cos(t * 0.31f + 0.4f),
                cyFrac = 0.68f + 0.20f * sin(t * 0.55f + 1.6f),
                color = c8,
                radiusScale = 0.70f
            )
        }

        // Protective Black Tint over the blurred background on the Lyrics page
        // so white & dimmed lyrics remain 100% visible even when the album art has lots of white!
        if (glass.isLyricsScreenActive) {
            val tintAlpha = glass.lyricsBlackTintAlpha.coerceIn(0.15f, 0.85f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = tintAlpha))
            )
        }
    }
}

/**
 * Clean Material You / Pitch-Black Monochrome / Frosted Glass Surface Container.
 * - Zero per-item blur overhead (eliminating scroll lag on song lists).
 * - In True Monochrome Mode (`PURE_MONOCHROME`), uses strict `#000000` / `#080808` and pure white borders
 *   with zero blue tint.
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
    val scheme = MaterialTheme.colorScheme
    val isMono = glass.isMonochromeUiMode

    if (opaqueModal) {
        val modalColor = if (isMono) {
            if (glass.oledPitchBlack) Color(0xFF040404) else Color(0xFF0C0C0C)
        } else {
            lerp(scheme.surface, Color(0xFF080808), 0.42f)
        }
        val borderColor = if (isMono) {
            Color.White.copy(alpha = if (accentHighlight) 0.70f else glass.monoBorderContrast.coerceIn(0.18f, 0.85f))
        } else if (accentHighlight) {
            scheme.primary.copy(alpha = 0.48f)
        } else {
            scheme.outline.copy(alpha = 0.55f)
        }
        Box(
            modifier = modifier
                .clip(shape)
                .background(modalColor)
                .border(BorderStroke(1.dp, borderColor), shape),
            content = content
        )
        return
    }

    if (isMono) {
        val monoAlpha = if (glass.themeMode == VisualThemeMode.MONOCHROME_GLASS) {
            glass.frostOpacity.coerceIn(0.55f, 0.98f)
        } else {
            1.0f
        }
        val monoFill = when {
            accentHighlight -> Color(0xFF1C1C1C).copy(alpha = monoAlpha)
            glass.oledPitchBlack && !elevated -> MonoPureBlack.copy(alpha = monoAlpha)
            glass.oledPitchBlack && elevated -> Color(0xFF0A0A0A).copy(alpha = monoAlpha)
            elevated -> Color(0xFF141414).copy(alpha = monoAlpha)
            else -> Color(0xFF0A0A0A).copy(alpha = monoAlpha)
        }
        val monoBorder = when {
            accentHighlight -> Color.White.copy(alpha = 0.90f)
            elevated -> Color.White.copy(alpha = glass.monoBorderContrast.coerceIn(0.15f, 0.85f))
            else -> Color.White.copy(alpha = (glass.monoBorderContrast * 0.65f).coerceIn(0.10f, 0.65f))
        }
        Box(
            modifier = modifier
                .clip(shape)
                .background(monoFill)
                .border(BorderStroke(if (accentHighlight) 1.5.dp else 1.dp, monoBorder), shape),
            content = content
        )
        return
    }

    val isFrostedMode = glass.themeMode == VisualThemeMode.FROSTED_GLASS
    val allowBleed = !glass.isLyricsScreenActive && (glass.bleedAlbumArtIntoSongList || isFrostedMode)
    val surfaceAlpha = when {
        isFrostedMode -> glass.frostOpacity.coerceIn(0.40f, 0.96f)
        allowBleed -> 0.82f
        else -> 0.98f
    }

    val baseFill = when {
        accentHighlight -> scheme.primaryContainer.copy(alpha = if (allowBleed) 0.90f else 1.0f)
        elevated -> scheme.surfaceVariant.copy(alpha = surfaceAlpha)
        else -> scheme.surface.copy(alpha = surfaceAlpha)
    }

    val borderColor = when {
        accentHighlight -> scheme.primary.copy(alpha = 0.72f)
        isFrostedMode -> Color.White.copy(alpha = glass.specularIntensity.coerceIn(0.12f, 0.75f))
        elevated -> scheme.outline.copy(alpha = 0.50f)
        else -> scheme.outline.copy(alpha = 0.32f)
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(baseFill)
            .border(
                BorderStroke(if (accentHighlight) 1.5.dp else 1.dp, borderColor),
                shape
            ),
        content = content
    )
}

/**
 * Unblurred, crystal-clear Full-Color Album Art renderer.
 * - Uses an off-thread `LruCache<String, ImageBitmap>` (`LocalCoverArtBitmapCache`) loaded on `Dispatchers.IO`
 *   so scrolling through hundreds of songs in `AllSongsListView` NEVER blocks the main UI thread!
 * - Album arts are ALWAYS rendered in full color (never monochrome/grayscale).
 */
@Composable
fun CrispAlbumArtCover(
    title: String,
    subtitle: String,
    palette: List<Color>,
    coverArtPath: String?,
    sizeDp: Dp,
    cornerRadiusDp: Dp = 14.dp,
    forceFullColor: Boolean = true,
    modifier: Modifier = Modifier
) {
    val cacheKey = remember(coverArtPath, sizeDp) {
        if (coverArtPath.isNullOrBlank()) null else "$coverArtPath::${if (sizeDp <= 64.dp) "thumb" else "full"}"
    }

    var cachedBitmap by remember(cacheKey) {
        mutableStateOf(cacheKey?.let { LocalCoverArtBitmapCache.get(it) })
    }

    LaunchedEffect(cacheKey, coverArtPath) {
        if (cacheKey == null || coverArtPath.isNullOrBlank() || cachedBitmap != null) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) {
            try {
                val f = File(coverArtPath)
                if (!f.exists()) return@withContext null
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                    inSampleSize = if (sizeDp <= 64.dp) 2 else 1
                }
                BitmapFactory.decodeFile(f.absolutePath, opts)?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
        if (loaded != null) {
            LocalCoverArtBitmapCache.put(cacheKey, loaded)
            cachedBitmap = loaded
        }
    }

    val shape = RoundedCornerShape(cornerRadiusDp)

    Box(
        modifier = modifier
            .clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.18f), shape),
        contentAlignment = Alignment.Center
    ) {
        val bmp = cachedBitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = "$title album art",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val c0 = palette.getOrElse(0) { Color(0xFF6366F1) }
            val c1 = palette.getOrElse(1) { Color(0xFFEC4899) }
            val c2 = palette.getOrElse(2) { Color(0xFF06B6D4) }
            val c3 = palette.getOrElse(3) { Color(0xFF1E1B4B) }

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
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = if (sizeDp > 120.dp) 18.sp else 11.sp
                        ),
                        color = Color.White.copy(alpha = 0.92f)
                    )
                }
            }
        }
    }
}
