package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.MusicAudioEngine
import com.example.data.TrackEntity
import com.example.lyrics.ElementVocalState
import com.example.lyrics.SyllableLineModel
import com.example.lyrics.SyllableWordModel
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalGlassTheme
import com.example.ui.theme.MonoSilver
import com.example.viewmodel.PlayerSettingsState

/**
 * Full-Screen & Side-by-Side Synced `.ttml` Lyrics Stage matching the reference layout:
 * - Preserves full-color Album Art and blurred background aura even in Ultra Monochrome Mode.
 * - Renders Main lyrics (left, full size), Background lyrics (smaller sub-line below main line),
 *   and Duo / Secondary vocalist lyrics (right-aligned).
 * - Guarantees NotSung words have ZERO premature letter highlight (only lighting up when Active/Sung).
 * - Applies custom uploaded Lyrics FontFamily if configured.
 */
@Composable
fun LyricsStageView(
    track: TrackEntity?,
    lines: List<SyllableLineModel>,
    activeLineIndex: Int,
    currentPositionMs: Double,
    durationMs: Long,
    isPlaying: Boolean,
    playbackSpeed: Float,
    playbackPitch: Float,
    coLocatedTtmlFileName: String?,
    settings: PlayerSettingsState,
    onCollapse: () -> Unit,
    onToggleTheatreMode: (Boolean) -> Unit,
    onSeekToMs: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit = {},
    onSkipNext: () -> Unit = {},
    onOpenSpeedPitchSheet: () -> Unit,
    onOpenGlassCustomizer: () -> Unit,
    onAdjustTrackOffset: (Int) -> Unit,
    onLoadManualTtmlClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val glass = LocalGlassTheme.current
    val lyricsFontFamily = glass.effectiveLyricsFontFamily
    val listState = rememberLazyListState()
    val isTheatre = settings.theatreFullscreenMode

    LaunchedEffect(activeLineIndex, settings.autoScrollLyrics) {
        if (settings.autoScrollLyrics && activeLineIndex in lines.indices) {
            listState.animateScrollToItem(
                index = activeLineIndex,
                scrollOffset = -200
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("lyrics_stage_view")
    ) {
        val isLandscapeOrWide = maxWidth >= 600.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // Subtle Top Control Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("collapse_lyrics_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse Lyrics View",
                        tint = Color.White
                    )
                }

                if (!isTheatre) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val effectiveOffset = settings.globalLyricsOffsetMs + (track?.lyricsOffsetMs ?: 0)
                        MiniPillButton(
                            text = "-100ms",
                            onClick = { onAdjustTrackOffset(-100) },
                            tag = "lyrics_offset_minus_button"
                        )
                        Text(
                            text = "${if (effectiveOffset >= 0) "+" else ""}${effectiveOffset}ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        MiniPillButton(
                            text = "+100ms",
                            onClick = { onAdjustTrackOffset(100) },
                            tag = "lyrics_offset_plus_button"
                        )
                        MiniPillButton(
                            text = "${MusicAudioEngine.formatMultiplier(playbackSpeed)} / ${MusicAudioEngine.formatMultiplier(playbackPitch)}",
                            onClick = onOpenSpeedPitchSheet,
                            tag = "lyrics_open_speed_pitch_button"
                        )
                        MiniPillButton(
                            text = ".ttml",
                            onClick = onLoadManualTtmlClick,
                            tag = "lyrics_header_load_ttml_button"
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isTheatre) {
                        IconButton(
                            onClick = onOpenGlassCustomizer,
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("lyrics_glass_customizer_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Customize Blur & Glass",
                                tint = Color.White
                            )
                        }
                    }
                    IconButton(
                        onClick = { onToggleTheatreMode(!isTheatre) },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("toggle_theatre_mode_button")
                    ) {
                        Icon(
                            imageVector = if (isTheatre) Icons.Default.CloseFullscreen else Icons.Default.Fullscreen,
                            contentDescription = "Toggle Theatre Mode",
                            tint = Color.White
                        )
                    }
                }
            }

            if (isLandscapeOrWide) {
                // TWO-COLUMN REFERENCE SCREENSHOT LAYOUT:
                // Left: Full-Color Album Art + Slim Progress Bar (0:50 --- 2:48) + Bold Title + Artist
                // Right: Multi-Vocalist Scrolling Lyrics (Main, Smaller Background, Right-Side Duo)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    ArtworkAndScrubberColumn(
                        track = track,
                        currentPositionMs = currentPositionMs,
                        durationMs = durationMs,
                        isPlaying = isPlaying,
                        onSeekToMs = onSeekToMs,
                        onTogglePlayPause = onTogglePlayPause,
                        onSkipPrevious = onSkipPrevious,
                        onSkipNext = onSkipNext,
                        compactForPortrait = false,
                        modifier = Modifier
                            .weight(0.42f)
                            .fillMaxHeight()
                    )

                    LyricsScrollViewport(
                        track = track,
                        lines = lines,
                        activeLineIndex = activeLineIndex,
                        currentPositionMs = currentPositionMs,
                        baseFontSizeSp = settings.lyricsFontSizeSp,
                        lyricsFontFamily = lyricsFontFamily,
                        listState = listState,
                        onSeekToMs = onSeekToMs,
                        onLoadManualTtmlClick = onLoadManualTtmlClick,
                        modifier = Modifier
                            .weight(0.58f)
                            .fillMaxHeight()
                    )
                }
            } else {
                // PORTRAIT LAYOUT:
                // Top: Crisp Full-Color Album Art + Progress Bar + Centered Title & Artist
                // Bottom: Multi-Vocalist Scrolling Lyrics
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    ArtworkAndScrubberColumn(
                        track = track,
                        currentPositionMs = currentPositionMs,
                        durationMs = durationMs,
                        isPlaying = isPlaying,
                        onSeekToMs = onSeekToMs,
                        onTogglePlayPause = onTogglePlayPause,
                        onSkipPrevious = onSkipPrevious,
                        onSkipNext = onSkipNext,
                        compactForPortrait = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LyricsScrollViewport(
                        track = track,
                        lines = lines,
                        activeLineIndex = activeLineIndex,
                        currentPositionMs = currentPositionMs,
                        baseFontSizeSp = settings.lyricsFontSizeSp,
                        lyricsFontFamily = lyricsFontFamily,
                        listState = listState,
                        onSeekToMs = onSeekToMs,
                        onLoadManualTtmlClick = onLoadManualTtmlClick,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * Renders the Album Art, slim pill scrubber (`0:50 ─── 2:48`), bold centered track title,
 * and artist name matching the uploaded reference image (`round and round` / `bbno$`).
 */
@Composable
private fun ArtworkAndScrubberColumn(
    track: TrackEntity?,
    currentPositionMs: Double,
    durationMs: Long,
    isPlaying: Boolean,
    onSeekToMs: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    compactForPortrait: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
    ) {
        val artSize = if (compactForPortrait) 156.dp else 260.dp

        CrispAlbumArtCover(
            title = track?.album ?: "No Track",
            subtitle = track?.artist ?: "",
            palette = track?.parsePalette() ?: listOf(
                Color(0xFFD97706),
                Color(0xFFDC2626),
                Color(0xFF2563EB),
                Color(0xFF1E1B4B)
            ),
            coverArtPath = track?.coverArtPath,
            sizeDp = artSize,
            cornerRadiusDp = 16.dp,
            forceFullColor = true, // Always full color on Lyrics Screen even in Ultra Monochrome mode
            modifier = Modifier
                .size(artSize)
                .clickable { onTogglePlayPause() }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Slim pill progress bar with timestamps on left & right (`0:50 [====------] 2:48`)
        val progress = (currentPositionMs.toFloat() / durationMs.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .widthIn(max = 310.dp)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = formatDurationMs(currentPositionMs.toLong()),
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.78f)
            )

            var barWidthPx by remember { mutableFloatStateOf(1f) }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.22f))
                    .onSizeChanged { barWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                    .pointerInput(durationMs) {
                        detectTapGestures { offset ->
                            val frac = (offset.x / barWidthPx).coerceIn(0f, 1f)
                            onSeekToMs((frac * durationMs).toLong())
                        }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White)
                )
            }

            Text(
                text = formatDurationMs(durationMs),
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.78f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = track?.title ?: "Select a Track",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = if (compactForPortrait) 20.sp else 24.sp
            ),
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = track?.artist ?: "Connect a local folder to begin",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.68f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (!compactForPortrait) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onSkipPrevious) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White)
                }
                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier.testTag("lyrics_play_pause_button")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
                IconButton(onClick = onSkipNext) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun LyricsScrollViewport(
    track: TrackEntity?,
    lines: List<SyllableLineModel>,
    activeLineIndex: Int,
    currentPositionMs: Double,
    baseFontSizeSp: Float,
    lyricsFontFamily: FontFamily,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onSeekToMs: (Long) -> Unit,
    onLoadManualTtmlClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                // Smooth top & bottom alpha mask like the reference screenshot
                drawRect(
                    brush = Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.08f to Color.Black,
                        0.90f to Color.Black,
                        1.0f to Color.Transparent
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
    ) {
        if (lines.isEmpty()) {
            EmptyTtmlFallbackState(
                track = track,
                onLoadManualTtmlClick = onLoadManualTtmlClick
            )
        } else {
            val frameKey = (currentPositionMs / 16.0).toLong()
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = 56.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("lyrics_lazy_column")
            ) {
                itemsIndexed(
                    items = lines,
                    key = { idx, line -> "${line.index}_${idx}" }
                ) { index, line ->
                    @Suppress("UNUSED_VARIABLE")
                    val tick = frameKey

                    SyllableLineRow(
                        line = line,
                        isActiveLine = index == activeLineIndex || line.state == ElementVocalState.Active,
                        baseFontSizeSp = baseFontSizeSp,
                        lyricsFontFamily = lyricsFontFamily,
                        onLineClick = { onSeekToMs(line.startTimeMs.toLong()) }
                    )
                }
            }
        }
    }
}

/**
 * Renders one line of `.ttml` synced lyrics supporting:
 * - **Main Lyrics** (`!line.isBgLine && !line.isDuoLine`): Large bold text on the left.
 * - **Smaller Background Lyrics** (`line.isBgLine`): Smaller font (`0.68x` size) tucked right underneath the main vocal line.
 * - **Right-Aligned Duo Lyrics** (`line.isDuoLine`): Aligned/indented to the right side of the viewport.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SyllableLineRow(
    line: SyllableLineModel,
    isActiveLine: Boolean,
    baseFontSizeSp: Float,
    lyricsFontFamily: FontFamily,
    onLineClick: () -> Unit
) {
    val density = LocalDensity.current
    val fontSizeSp = if (line.isBgLine) (baseFontSizeSp * 0.66f) else baseFontSizeSp
    val lineFontPx = with(density) { fontSizeSp.sp.toPx() }

    val lineAlpha by animateFloatAsState(
        targetValue = when {
            line.preHidden -> 0f
            line.state == ElementVocalState.Active -> 1f
            line.state == ElementVocalState.Sung && line.sungAfterActive -> 0.65f
            line.state == ElementVocalState.Sung -> 0.42f
            else -> 0.45f
        },
        animationSpec = tween(220),
        label = "lineAlpha"
    )

    val blurDp = with(density) { line.blurAmountPx.toDp() }
    val blurMod = if (!isActiveLine && blurDp > 0.6.dp) {
        Modifier.blur(radius = blurDp.coerceAtMost(6.dp), edgeTreatment = BlurredEdgeTreatment.Unbounded)
    } else {
        Modifier
    }

    val startPad = if (line.isDuoLine) 48.dp else 4.dp
    val endPad = if (line.isDuoLine) 4.dp else 32.dp
    val topPad = if (line.isBgLine) 0.dp else 6.dp
    val bottomPad = if (line.isBgLine) 6.dp else 2.dp

    val horizontalArrangement = if (line.isDuoLine) {
        Arrangement.spacedBy(8.dp, Alignment.End)
    } else {
        Arrangement.spacedBy(8.dp, Alignment.Start)
    }

    Box(
        contentAlignment = if (line.isDuoLine) Alignment.CenterEnd else Alignment.CenterStart,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onLineClick)
            .padding(start = startPad, end = endPad, top = topPad, bottom = bottomPad)
            .alpha(lineAlpha)
            .then(blurMod)
    ) {
        if (line.isDotLine) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.graphicsLayer {
                    scaleX = line.dotGroupBreathScale
                    scaleY = line.dotGroupBreathScale
                }
            ) {
                for (dot in line.words) {
                    Text(
                        text = "●",
                        fontSize = (fontSizeSp * 0.85f).sp,
                        color = Color.White.copy(alpha = dot.currentDotOpacity.coerceIn(0.15f, 1f)),
                        style = TextStyle(
                            shadow = Shadow(
                                color = Color.White.copy(alpha = dot.currentGlowOpacity),
                                offset = Offset.Zero,
                                blurRadius = dot.currentGlowRadiusPx.coerceAtLeast(1f)
                            )
                        ),
                        modifier = Modifier.graphicsLayer {
                            scaleX = dot.currentScale
                            scaleY = dot.currentScale
                            translationY = dot.currentYOffsetEm * lineFontPx
                        }
                    )
                }
            }
        } else {
            FlowRow(
                horizontalArrangement = horizontalArrangement,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                for (word in line.words) {
                    SyllableWordView(
                        word = word,
                        fontSizeSp = fontSizeSp,
                        lineFontPx = lineFontPx,
                        isBgLine = line.isBgLine,
                        lyricsFontFamily = lyricsFontFamily
                    )
                }
            }
        }
    }
}

/**
 * Renders an individual word or split-letter group with strict vocal state gating:
 * - `ElementVocalState.NotSung`: 100% uniform dim resting color (`0.42f` alpha). ZERO white letters!
 * - `ElementVocalState.Sung`: 100% solid lit white (`1.0f` alpha) during active line, or soft resting sung alpha.
 * - `ElementVocalState.Active`: Smooth horizontal sweep across the word's true measured pixel width!
 */
@Composable
private fun SyllableWordView(
    word: SyllableWordModel,
    fontSizeSp: Float,
    lineFontPx: Float,
    isBgLine: Boolean,
    lyricsFontFamily: FontFamily
) {
    val dimAlpha = if (isBgLine) 0.36f else 0.42f
    val litAlpha = if (isBgLine) 0.88f else 1.0f
    val fontWeight = if (isBgLine) FontWeight.SemiBold else FontWeight.Bold

    if (word.letterGroup && word.letters.isNotEmpty()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.graphicsLayer {
                scaleX = word.currentScale
                scaleY = word.currentScale
                translationY = word.currentYOffsetEm * lineFontPx
            }
        ) {
            for (letter in word.letters) {
                val letterAlpha = when (letter.state) {
                    ElementVocalState.NotSung -> dimAlpha
                    ElementVocalState.Sung -> litAlpha
                    ElementVocalState.Active -> {
                        val f = letter.fillFraction.coerceIn(0f, 1f)
                        dimAlpha + (litAlpha - dimAlpha) * f
                    }
                }
                Text(
                    text = letter.charText,
                    fontFamily = lyricsFontFamily,
                    fontWeight = fontWeight,
                    fontSize = fontSizeSp.sp,
                    color = Color.White.copy(alpha = letterAlpha),
                    style = TextStyle(
                        shadow = if (letter.state == ElementVocalState.Active && letter.currentGlowOpacity > 0.02f) {
                            Shadow(
                                color = Color.White.copy(alpha = letter.currentGlowOpacity.coerceIn(0f, 1f)),
                                offset = Offset.Zero,
                                blurRadius = letter.currentGlowRadiusPx.coerceAtLeast(1f)
                            )
                        } else null
                    ),
                    modifier = Modifier.graphicsLayer {
                        scaleX = letter.currentScale
                        scaleY = letter.currentScale
                        translationX = letter.currentTranslateXEm * lineFontPx
                        translationY = letter.currentTranslateYEm * lineFontPx
                    }
                )
            }
        }
    } else {
        var measuredWidthPx by remember { mutableFloatStateOf(0f) }
        val fill = word.fillFraction.coerceIn(0f, 1f)

        // Strict gating: when NotSung (fill <= 0.005f), render solid uniform dim color so
        // the first 1.5 letters are NEVER visible before it's this word's turn!
        if (word.state == ElementVocalState.NotSung || fill <= 0.005f) {
            Text(
                text = word.text,
                fontFamily = lyricsFontFamily,
                fontWeight = fontWeight,
                fontSize = fontSizeSp.sp,
                color = Color.White.copy(alpha = dimAlpha),
                modifier = Modifier
                    .onSizeChanged { measuredWidthPx = it.width.toFloat() }
                    .graphicsLayer {
                        scaleX = word.currentScale
                        scaleY = word.currentScale
                        translationY = word.currentYOffsetEm * lineFontPx
                    }
            )
        } else if (word.state == ElementVocalState.Sung || fill >= 0.995f) {
            Text(
                text = word.text,
                fontFamily = lyricsFontFamily,
                fontWeight = fontWeight,
                fontSize = fontSizeSp.sp,
                color = Color.White.copy(alpha = litAlpha),
                modifier = Modifier
                    .onSizeChanged { measuredWidthPx = it.width.toFloat() }
                    .graphicsLayer {
                        scaleX = word.currentScale
                        scaleY = word.currentScale
                        translationY = word.currentYOffsetEm * lineFontPx
                    }
            )
        } else {
            // Active state: sweep across actual measured width from 0% to 100%
            val widthPx = if (measuredWidthPx > 4f) measuredWidthPx else (word.text.length * lineFontPx * 0.58f).coerceAtLeast(20f)
            val softBand = 0.08f
            val stopLit = (fill - softBand).coerceIn(0f, 0.98f)
            val stopDim = (fill + softBand).coerceIn(stopLit + 0.01f, 1f)

            val sweepBrush = Brush.horizontalGradient(
                0.0f to Color.White.copy(alpha = litAlpha),
                stopLit to Color.White.copy(alpha = litAlpha),
                stopDim to Color.White.copy(alpha = dimAlpha),
                1.0f to Color.White.copy(alpha = dimAlpha),
                startX = 0f,
                endX = widthPx
            )

            Text(
                text = word.text,
                fontFamily = lyricsFontFamily,
                fontWeight = fontWeight,
                fontSize = fontSizeSp.sp,
                style = TextStyle(
                    brush = sweepBrush,
                    shadow = if (word.currentGlowOpacity > 0.02f) {
                        Shadow(
                            color = Color.White.copy(alpha = word.currentGlowOpacity.coerceIn(0f, 1f)),
                            offset = Offset.Zero,
                            blurRadius = word.currentGlowRadiusPx.coerceAtLeast(1f)
                        )
                    } else null
                ),
                modifier = Modifier
                    .onSizeChanged { measuredWidthPx = it.width.toFloat() }
                    .graphicsLayer {
                        scaleX = word.currentScale
                        scaleY = word.currentScale
                        translationY = word.currentYOffsetEm * lineFontPx
                    }
            )
        }
    }
}

@Composable
private fun EmptyTtmlFallbackState(
    track: TrackEntity?,
    onLoadManualTtmlClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        FrostedGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            elevated = true
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(28.dp)
            ) {
                Text(
                    text = "NO CO-LOCATED .TTML LYRICS FOUND",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (track != null) {
                        "Place ${track.baseFileName}.ttml in ${track.folderPath} or load a .ttml file manually below."
                    } else {
                        "Import a track with a matching .ttml file in the same folder, or load a .ttml file below."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MonoSilver,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onLoadManualTtmlClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.testTag("empty_load_ttml_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Load .ttml File")
                }
            }
        }
    }
}

@Composable
fun MiniPillButton(
    text: String,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier
) {
    FrostedGlassSurface(
        shape = RoundedCornerShape(50),
        modifier = modifier
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Text(
            text = text,
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

fun formatDurationMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return String.format(java.util.Locale.US, "%d:%02d", mins, secs)
}
