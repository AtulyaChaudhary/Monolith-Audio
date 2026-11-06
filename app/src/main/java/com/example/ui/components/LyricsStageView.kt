package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.MusicAudioEngine
import com.example.data.TrackEntity
import com.example.lyrics.ElementVocalState
import com.example.lyrics.LyricsAttributionInfo
import com.example.lyrics.SyllableLineModel
import com.example.lyrics.SyllableWordModel
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalGlassTheme
import com.example.ui.theme.MonoSilver
import com.example.viewmodel.PlayerSettingsState
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Full-Screen & Side-by-Side Synced Lyrics Stage:
 * - Supports ALL 4 lyrics modes:
 *   1. Syllable Sync (`.ttml` / Spicy Lyrics `Syllable`)
 *   2. Word Sync (`Word` / Enhanced LRC)
 *   3. Line Sync (`.lrc` / Spicy Lyrics `Line`)
 *   4. Plain Text (`.txt` / Unsynced static lyrics)
 * - Eliminates line-switch jitter by using smooth `animateScrollBy` viewport centering and
 *   hardware-accelerated `graphicsLayer` transforms (zero conditional `Modifier.blur` node recreation).
 * - Smooth word-to-word glow transitions using a single persistent `Text` composable per word.
 * - Restores full Lyrics Background Controls (Blur Radius, Protective Black Tint, Orb Speed, Word Glow)
 *   in the Lyrics Settings Pop-Out Sheet.
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
    attribution: LyricsAttributionInfo?,
    isFetchingSpicyLyrics: Boolean,
    settings: PlayerSettingsState,
    onCollapse: () -> Unit,
    onToggleTheatreMode: (Boolean) -> Unit,
    onSeekToMs: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit = {},
    onSkipNext: () -> Unit = {},
    onOpenSpeedPitchSheet: () -> Unit,
    onOpenGlassCustomizer: () -> Unit,
    onOpenFullSettings: () -> Unit = {},
    onOpenSpicySearchModal: () -> Unit = {},
    onAdjustTrackOffset: (Int) -> Unit,
    onUpdateSettings: ((PlayerSettingsState) -> PlayerSettingsState) -> Unit = {},
    onClearCurrentTrackLyricsCache: () -> Unit = {},
    onLoadManualTtmlClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val glass = LocalGlassTheme.current
    val lyricsFontFamily = glass.effectiveLyricsFontFamily
    val listState = rememberLazyListState()
    val isTheatre = settings.theatreFullscreenMode
    val isPlainTextMode = remember(lines) { lines.isNotEmpty() && lines.all { it.isPlainTextUnsynced } }

    // Controls are hidden by default; revealed only on screen tap or finger drag
    var controlsVisible by remember { mutableStateOf(false) }
    var interactionNonce by remember { mutableIntStateOf(0) }
    var isLyricsPopoutSettingsOpen by remember { mutableStateOf(false) }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            controlsVisible = true
            interactionNonce++
        }
    }

    LaunchedEffect(controlsVisible, interactionNonce, listState.isScrollInProgress) {
        if (controlsVisible && !listState.isScrollInProgress && !isLyricsPopoutSettingsOpen) {
            delay(3800)
            controlsVisible = false
        }
    }

    // Jitter-free smooth scroll centering when activeLineIndex advances!
    LaunchedEffect(activeLineIndex, settings.autoScrollLyrics, isPlainTextMode) {
        if (!isPlainTextMode && settings.autoScrollLyrics && activeLineIndex in lines.indices && !listState.isScrollInProgress) {
            val layoutInfo = listState.layoutInfo
            val targetItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == activeLineIndex }
            val viewportHeight = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).coerceAtLeast(1)
            val desiredAnchorPx = (viewportHeight * 0.30f).toInt()

            if (targetItem != null) {
                val itemCenter = targetItem.offset + (targetItem.size / 2)
                val deltaPx = (itemCenter - desiredAnchorPx).toFloat()
                if (kotlin.math.abs(deltaPx) > 6f) {
                    listState.animateScrollBy(
                        value = deltaPx,
                        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing)
                    )
                }
            } else {
                listState.animateScrollToItem(
                    index = activeLineIndex,
                    scrollOffset = -desiredAnchorPx
                )
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.any { it.pressed }) {
                            controlsVisible = true
                            interactionNonce++
                        }
                    }
                }
            }
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("lyrics_stage_view")
    ) {
        val isLandscapeOrWide = maxWidth >= 600.dp

        Column(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(260))
            ) {
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
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.52f))
                            .testTag("collapse_lyrics_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Collapse Lyrics View",
                            tint = Color.White
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!coLocatedTtmlFileName.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.Black.copy(alpha = 0.50f))
                                    .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(50))
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = coLocatedTtmlFileName,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.90f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        MiniPillButton(
                            text = "Search Lyrics",
                            onClick = onOpenSpicySearchModal,
                            tag = "lyrics_open_spicy_search_button"
                        )

                        IconButton(
                            onClick = { isLyricsPopoutSettingsOpen = true },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.52f))
                                .testTag("lyrics_exclusive_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Open Lyrics & Background Settings",
                                tint = Color.White
                            )
                        }

                        IconButton(
                            onClick = { onToggleTheatreMode(!isTheatre) },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.52f))
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
            }

            if (isLandscapeOrWide) {
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
                        controlsVisible = controlsVisible,
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
                        attribution = attribution,
                        isFetchingSpicyLyrics = isFetchingSpicyLyrics,
                        listState = listState,
                        onSeekToMs = onSeekToMs,
                        onLoadManualTtmlClick = onLoadManualTtmlClick,
                        onOpenSpicySearchModal = onOpenSpicySearchModal,
                        modifier = Modifier
                            .weight(0.58f)
                            .fillMaxHeight()
                    )
                }
            } else {
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
                        controlsVisible = controlsVisible,
                        onSeekToMs = onSeekToMs,
                        onTogglePlayPause = onTogglePlayPause,
                        onSkipPrevious = onSkipPrevious,
                        onSkipNext = onSkipNext,
                        compactForPortrait = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    LyricsScrollViewport(
                        track = track,
                        lines = lines,
                        activeLineIndex = activeLineIndex,
                        currentPositionMs = currentPositionMs,
                        baseFontSizeSp = settings.lyricsFontSizeSp,
                        lyricsFontFamily = lyricsFontFamily,
                        attribution = attribution,
                        isFetchingSpicyLyrics = isFetchingSpicyLyrics,
                        listState = listState,
                        onSeekToMs = onSeekToMs,
                        onLoadManualTtmlClick = onLoadManualTtmlClick,
                        onOpenSpicySearchModal = onOpenSpicySearchModal,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                }
            }
        }

        // Exclusive Pop-Out Settings Sheet when the user taps the Settings button on the Lyrics screen
        if (isLyricsPopoutSettingsOpen) {
            LyricsQuickSettingsPopoutSheet(
                track = track,
                playbackSpeed = playbackSpeed,
                playbackPitch = playbackPitch,
                settings = settings,
                coLocatedTtmlFileName = coLocatedTtmlFileName,
                onAdjustTrackOffset = onAdjustTrackOffset,
                onUpdateSettings = onUpdateSettings,
                onClearCurrentTrackLyricsCache = {
                    onClearCurrentTrackLyricsCache()
                    isLyricsPopoutSettingsOpen = false
                },
                onOpenSpeedPitchSheet = {
                    isLyricsPopoutSettingsOpen = false
                    onOpenSpeedPitchSheet()
                },
                onLoadManualTtmlClick = {
                    isLyricsPopoutSettingsOpen = false
                    onLoadManualTtmlClick()
                },
                onOpenSpicySearchModal = {
                    isLyricsPopoutSettingsOpen = false
                    onOpenSpicySearchModal()
                },
                onOpenFullSettings = {
                    isLyricsPopoutSettingsOpen = false
                    onOpenFullSettings()
                },
                onDismiss = { isLyricsPopoutSettingsOpen = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LyricsQuickSettingsPopoutSheet(
    track: TrackEntity?,
    playbackSpeed: Float,
    playbackPitch: Float,
    settings: PlayerSettingsState,
    coLocatedTtmlFileName: String?,
    onAdjustTrackOffset: (Int) -> Unit,
    onUpdateSettings: ((PlayerSettingsState) -> PlayerSettingsState) -> Unit,
    onClearCurrentTrackLyricsCache: () -> Unit,
    onOpenSpeedPitchSheet: () -> Unit,
    onLoadManualTtmlClick: () -> Unit,
    onOpenSpicySearchModal: () -> Unit,
    onOpenFullSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val effectiveOffset = settings.globalLyricsOffsetMs + (track?.lyricsOffsetMs ?: 0)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        scrimColor = Color.Black.copy(alpha = 0.82f),
        dragHandle = null
    ) {
        FrostedGlassSurface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            elevated = true,
            accentHighlight = true,
            opaqueModal = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp)
                .testTag("lyrics_popout_settings_sheet")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "LYRICS & BACKGROUND SETTINGS",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Text(
                            text = coLocatedTtmlFileName ?: "Syllable • Word • Line (LRC) • Plain Text Engine",
                            style = MaterialTheme.typography.bodySmall,
                            color = MonoSilver
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                // 1. LYRICS SCREEN BACKGROUND BLUR, BLACK TINT & ORB CONTROLS
                FrostedGlassSurface(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Background Blur Radius: ${settings.glassBlurRadiusDp.roundToInt()}dp",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                        Slider(
                            value = settings.glassBlurRadiusDp,
                            onValueChange = { v -> onUpdateSettings { it.copy(glassBlurRadiusDp = v) } },
                            valueRange = 0f..120f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                            ),
                            modifier = Modifier.testTag("lyrics_blur_radius_slider")
                        )

                        Text(
                            text = "Protective Black Tint on Blur: ${(settings.lyricsBlackTintAlpha * 100).roundToInt()}% (Keeps white lyrics readable on white covers)",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                        Slider(
                            value = settings.lyricsBlackTintAlpha,
                            onValueChange = { v -> onUpdateSettings { it.copy(lyricsBlackTintAlpha = v) } },
                            valueRange = 0.15f..0.80f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                            ),
                            modifier = Modifier.testTag("lyrics_black_tint_slider")
                        )

                        Text(
                            text = "Background 9-Orb Speed: ${String.format(java.util.Locale.US, "%.1fx", settings.bgAnimationSpeed)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                        Slider(
                            value = settings.bgAnimationSpeed,
                            onValueChange = { v -> onUpdateSettings { it.copy(bgAnimationSpeed = v) } },
                            valueRange = 0f..3.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                            ),
                            modifier = Modifier.testTag("lyrics_orb_speed_slider")
                        )

                        Text(
                            text = "Word Glow Intensity: ${String.format(java.util.Locale.US, "%.1fx", settings.lyricsGlowIntensity)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                        Slider(
                            value = settings.lyricsGlowIntensity,
                            onValueChange = { v -> onUpdateSettings { it.copy(lyricsGlowIntensity = v) } },
                            valueRange = 0f..2.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                            ),
                            modifier = Modifier.testTag("lyrics_glow_intensity_slider")
                        )

                        Text(
                            text = "Lyrics Font Size: ${settings.lyricsFontSizeSp.roundToInt()}sp",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                        Slider(
                            value = settings.lyricsFontSizeSp,
                            onValueChange = { v -> onUpdateSettings { it.copy(lyricsFontSizeSp = v) } },
                            valueRange = 16f..42f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                            )
                        )
                    }
                }

                // 2. Track Timing Offset
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sync Offset: ${if (effectiveOffset >= 0) "+" else ""}${effectiveOffset}ms",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiniPillButton("-100ms", { onAdjustTrackOffset(-100) }, "lyrics_offset_minus_button")
                        MiniPillButton("+100ms", { onAdjustTrackOffset(100) }, "lyrics_offset_plus_button")
                    }
                }

                // 3. Speed & Pitch Quick Access
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Speed & Pitch (0.1x–5.0x)",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White
                    )
                    MiniPillButton(
                        text = "${MusicAudioEngine.formatMultiplier(playbackSpeed)} / ${MusicAudioEngine.formatMultiplier(playbackPitch)}",
                        onClick = onOpenSpeedPitchSheet,
                        tag = "lyrics_open_speed_pitch_button"
                    )
                }

                // 4. Actions Row: Search Spicy Lyrics, Load Local (.ttml / .lrc / .txt)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onOpenSpicySearchModal,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("popout_search_spicy_lyrics_button")
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Search Lyrics")
                    }

                    OutlinedButton(
                        onClick = onLoadManualTtmlClick,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("lyrics_header_load_ttml_button")
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Load File")
                    }
                }

                // 5. Clear Current Song Lyrics Cache button
                OutlinedButton(
                    onClick = onClearCurrentTrackLyricsCache,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("popout_clear_current_song_lyrics_cache_button")
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear Current Song Lyrics Cache")
                }

                OutlinedButton(
                    onClick = onOpenFullSettings,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("popout_open_full_settings_button")
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("All App Settings, Themes & Secret Key")
                }
            }
        }
    }
}

@Composable
private fun ArtworkAndScrubberColumn(
    track: TrackEntity?,
    currentPositionMs: Double,
    durationMs: Long,
    isPlaying: Boolean,
    controlsVisible: Boolean,
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
        val artSize = if (compactForPortrait) 148.dp else 256.dp

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
            forceFullColor = true,
            modifier = Modifier
                .size(artSize)
                .clickable { onTogglePlayPause() }
        )

        Spacer(modifier = Modifier.height(12.dp))

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
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.82f)
            )

            var barWidthPx by remember { mutableFloatStateOf(1f) }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.24f))
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
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.82f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = track?.title ?: "Select a Track",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.ExtraBold,
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
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White.copy(alpha = 0.76f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(240))
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 6.dp)
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
    attribution: LyricsAttributionInfo?,
    isFetchingSpicyLyrics: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onSeekToMs: (Long) -> Unit,
    onLoadManualTtmlClick: () -> Unit,
    onOpenSpicySearchModal: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.06f to Color.Black,
                        0.92f to Color.Black,
                        1.0f to Color.Transparent
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
    ) {
        if (lines.isEmpty()) {
            EmptyTtmlFallbackState(
                track = track,
                isFetchingSpicyLyrics = isFetchingSpicyLyrics,
                onLoadManualTtmlClick = onLoadManualTtmlClick,
                onOpenSpicySearchModal = onOpenSpicySearchModal
            )
        } else {
            val frameTick = (currentPositionMs / 16.0).toLong()
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = 64.dp, bottom = 160.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("lyrics_lazy_column")
            ) {
                itemsIndexed(
                    items = lines,
                    key = { idx, line -> "${line.index}_${idx}" }
                ) { index, line ->
                    val isNearActive = line.isPlainTextUnsynced || kotlin.math.abs(index - activeLineIndex) <= 2
                    val lineTick = if (isNearActive) frameTick else 0L

                    SyllableLineRow(
                        line = line,
                        isActiveLine = line.isPlainTextUnsynced || index == activeLineIndex || line.state == ElementVocalState.Active,
                        distanceFromActive = if (line.isPlainTextUnsynced || activeLineIndex < 0) 0 else kotlin.math.abs(index - activeLineIndex),
                        frameTick = lineTick,
                        baseFontSizeSp = baseFontSizeSp,
                        lyricsFontFamily = lyricsFontFamily,
                        onLineClick = {
                            if (!line.isPlainTextUnsynced) {
                                onSeekToMs(line.startTimeMs.toLong())
                            }
                        }
                    )
                }

                item(key = "lyrics_attribution_footer") {
                    LyricsAttributionFooter(
                        attribution = attribution,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 18.dp, bottom = 24.dp, start = 4.dp, end = 16.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LyricsAttributionFooter(
    attribution: LyricsAttributionInfo?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val writers = attribution?.songWriters.orEmpty()
    val maker = attribution?.maker
    val uploader = attribution?.uploader
    val source = attribution?.source.orEmpty().lowercase()

    Column(
        modifier = modifier.testTag("lyrics_attribution_footer"),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (writers.isNotEmpty()) {
            Text(
                text = "Written by ${writers.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                ),
                color = Color.White.copy(alpha = 0.66f)
            )
        }

        if (maker != null || uploader != null || source == "spicy_lyrics") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Community lyrics",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    ),
                    color = Color.White.copy(alpha = 0.54f),
                    modifier = Modifier.align(Alignment.CenterVertically)
                )

                val primaryContributor = maker ?: uploader
                if (primaryContributor != null) {
                    val roleLabel = if (maker != null) "· Made by" else "· Uploaded by"
                    Text(
                        text = roleLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        ),
                        color = Color.White.copy(alpha = 0.54f),
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )

                    ContributorBadgeInline(
                        username = primaryContributor.username,
                        avatarUrl = primaryContributor.avatarUrl,
                        profileUrl = primaryContributor.profileUrl,
                        onOpenUrl = { url ->
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            } catch (_: Exception) {
                            }
                        },
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }

                if (maker != null && uploader != null && !uploader.username.equals(maker.username, ignoreCase = true)) {
                    Text(
                        text = "· Uploaded by",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        ),
                        color = Color.White.copy(alpha = 0.54f),
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )

                    ContributorBadgeInline(
                        username = uploader.username,
                        avatarUrl = uploader.avatarUrl,
                        profileUrl = uploader.profileUrl,
                        onOpenUrl = { url ->
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            } catch (_: Exception) {
                            }
                        },
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
            }
        } else {
            val providerText = when {
                source.contains("spotify") -> "Provided by Spotify"
                source.contains("lrc") -> "Synced LRC Lyrics"
                source.contains("txt") || source.contains("plain") -> "Plain Text Lyrics"
                else -> "Provided by Apple Music"
            }
            Text(
                text = providerText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                ),
                color = Color.White.copy(alpha = 0.54f)
            )
        }
    }
}

@Composable
private fun ContributorBadgeInline(
    username: String,
    avatarUrl: String?,
    profileUrl: String?,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .clickable(enabled = !profileUrl.isNullOrBlank()) {
                if (!profileUrl.isNullOrBlank()) onOpenUrl(profileUrl)
            }
    ) {
        RemoteUrlImage(
            imageUrl = avatarUrl,
            contentDescription = "$username avatar",
            modifier = Modifier
                .size(15.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f))
                .border(0.5.dp, Color.White.copy(alpha = 0.35f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.size(11.dp)
            )
        }

        Text(
            text = "@$username",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                textDecoration = TextDecoration.Underline
            ),
            color = Color.White.copy(alpha = 0.70f)
        )
    }
}

/**
 * Jitter-Free Line Row:
 * - Uses ONLY `Modifier.graphicsLayer` for opacity and subtle scale (never attaches/detaches `Modifier.blur()`
 *   on line transitions, completely eliminating RenderNode recreation hitches!).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SyllableLineRow(
    line: SyllableLineModel,
    isActiveLine: Boolean,
    distanceFromActive: Int,
    frameTick: Long,
    baseFontSizeSp: Float,
    lyricsFontFamily: FontFamily,
    onLineClick: () -> Unit
) {
    @Suppress("UNUSED_VARIABLE")
    val tick = frameTick

    val density = LocalDensity.current
    val fontSizeSp = if (line.isBgLine) (baseFontSizeSp * 0.68f) else baseFontSizeSp
    val lineFontPx = with(density) { fontSizeSp.sp.toPx() }

    val targetLineAlpha = when {
        line.isPlainTextUnsynced -> 0.94f
        line.preHidden -> 0f
        isActiveLine -> 1f
        distanceFromActive == 1 -> 0.56f
        distanceFromActive == 2 -> 0.42f
        else -> 0.32f
    }

    val lineAlpha by animateFloatAsState(
        targetValue = targetLineAlpha,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "lineAlpha"
    )

    val lineScale by animateFloatAsState(
        targetValue = if (isActiveLine && !line.isPlainTextUnsynced && !line.isBgLine) 1.0f else 0.975f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
        label = "lineScale"
    )

    val startPad = if (line.isDuoLine) 48.dp else 4.dp
    val endPad = if (line.isDuoLine) 4.dp else 28.dp
    val topPad = if (line.isBgLine) 0.dp else 6.dp
    val bottomPad = if (line.isBgLine) 6.dp else 4.dp

    val horizontalArrangement = if (line.isDuoLine) {
        Arrangement.spacedBy(8.dp, Alignment.End)
    } else {
        Arrangement.spacedBy(8.dp, Alignment.Start)
    }

    Box(
        contentAlignment = if (line.isDuoLine) Alignment.CenterEnd else Alignment.CenterStart,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = lineAlpha
                scaleX = lineScale
                scaleY = lineScale
                transformOrigin = TransformOrigin(if (line.isDuoLine) 1f else 0f, 0.5f)
            }
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !line.isPlainTextUnsynced, onClick = onLineClick)
            .padding(start = startPad, end = endPad, top = topPad, bottom = bottomPad)
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
        } else if (line.isLineSyncedOnly || line.isPlainTextUnsynced) {
            // Line-Synced (LRC) or Plain Text (Unsynced): Render the whole line as a single smooth phrase!
            val fullLineText = remember(line.words) { line.words.joinToString(" ") { it.text } }
            val isLit = line.isPlainTextUnsynced || isActiveLine || line.state == ElementVocalState.Active
            val textAlpha by animateFloatAsState(
                targetValue = if (isLit) 1.0f else 0.48f,
                animationSpec = tween(220),
                label = "lrcLineTextAlpha"
            )
            val glowAlpha by animateFloatAsState(
                targetValue = if (isActiveLine && !line.isPlainTextUnsynced) {
                    (line.words.firstOrNull()?.currentGlowOpacity ?: 0.28f)
                } else 0f,
                animationSpec = tween(240),
                label = "lrcGlowAlpha"
            )

            Text(
                text = fullLineText,
                fontFamily = lyricsFontFamily,
                fontWeight = if (line.isBgLine) FontWeight.Bold else FontWeight.ExtraBold,
                fontSize = fontSizeSp.sp,
                lineHeight = (fontSizeSp * 1.24f).sp,
                textAlign = if (line.isDuoLine) TextAlign.End else TextAlign.Start,
                color = Color.White.copy(alpha = textAlpha),
                style = TextStyle(
                    shadow = if (glowAlpha > 0.01f) {
                        Shadow(
                            color = Color.White.copy(alpha = glowAlpha),
                            offset = Offset.Zero,
                            blurRadius = 12f
                        )
                    } else null
                ),
                modifier = Modifier.fillMaxWidth()
            )
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
 * Single-Text Smooth Word & Syllable Renderer:
 * - Always renders ONE persistent `Text` composable (never branching between multiple `Text` calls)
 *   so `MultiParagraph` layout cache is never invalidated when words change state!
 * - Smoothly interpolates `fillFraction` and `glowOpacity` so the highlight and glow glide
 *   seamlessly from word to word without lag or snapping!
 */
@Composable
private fun SyllableWordView(
    word: SyllableWordModel,
    fontSizeSp: Float,
    lineFontPx: Float,
    isBgLine: Boolean,
    lyricsFontFamily: FontFamily
) {
    val dimAlpha = if (isBgLine) 0.38f else 0.42f
    val litAlpha = if (isBgLine) 0.92f else 1.0f
    val fontWeight = if (isBgLine) FontWeight.Bold else FontWeight.ExtraBold

    var measuredWidthPx by remember { mutableFloatStateOf(0f) }

    val targetFill = when (word.state) {
        ElementVocalState.NotSung -> 0f
        ElementVocalState.Sung -> 1f
        ElementVocalState.Active -> {
            if (word.letterGroup && word.letters.isNotEmpty()) {
                val totalLetters = word.letters.size.toFloat().coerceAtLeast(1f)
                var litCount = 0f
                for (letter in word.letters) {
                    litCount += when (letter.state) {
                        ElementVocalState.Sung -> 1f
                        ElementVocalState.Active -> letter.fillFraction.coerceIn(0f, 1f)
                        ElementVocalState.NotSung -> 0f
                    }
                }
                (litCount / totalLetters).coerceIn(0f, 1f)
            } else {
                word.fillFraction.coerceIn(0f, 1f)
            }
        }
    }

    val rawGlowOpacity = if (word.letterGroup && word.letters.isNotEmpty()) {
        maxOf(word.currentGlowOpacity, word.letters.maxOfOrNull { it.currentGlowOpacity } ?: 0f)
    } else {
        word.currentGlowOpacity
    }
    val rawGlowRadius = if (word.letterGroup && word.letters.isNotEmpty()) {
        maxOf(word.currentGlowRadiusPx, word.letters.maxOfOrNull { it.currentGlowRadiusPx } ?: 6f)
    } else {
        word.currentGlowRadiusPx
    }

    // Smooth glow interpolation so word-to-word glow transitions never snap!
    val smoothGlowAlpha by animateFloatAsState(
        targetValue = rawGlowOpacity.coerceIn(0f, 0.85f),
        animationSpec = tween(durationMillis = 110, easing = FastOutSlowInEasing),
        label = "wordGlowAlpha"
    )

    val smoothScale = word.currentScale.coerceIn(0.985f, 1.045f)
    val smoothTranslateY = (word.currentYOffsetEm * lineFontPx).coerceIn(-5f, 3f)

    val widthPx = if (measuredWidthPx > 4f) {
        measuredWidthPx
    } else {
        (word.text.length * lineFontPx * 0.58f).coerceAtLeast(24f)
    }

    val sweepBrush = if (targetFill <= 0.004f) {
        Brush.horizontalGradient(
            colors = listOf(Color.White.copy(alpha = dimAlpha), Color.White.copy(alpha = dimAlpha)),
            startX = 0f,
            endX = widthPx
        )
    } else if (targetFill >= 0.996f) {
        Brush.horizontalGradient(
            colors = listOf(Color.White.copy(alpha = litAlpha), Color.White.copy(alpha = litAlpha)),
            startX = 0f,
            endX = widthPx
        )
    } else {
        val softEdge = 0.08f
        val stopLit = (targetFill - softEdge).coerceIn(0f, 0.98f)
        val stopDim = (targetFill + softEdge).coerceIn(stopLit + 0.01f, 1f)
        Brush.horizontalGradient(
            0.0f to Color.White.copy(alpha = litAlpha),
            stopLit to Color.White.copy(alpha = litAlpha),
            stopDim to Color.White.copy(alpha = dimAlpha),
            1.0f to Color.White.copy(alpha = dimAlpha),
            startX = 0f,
            endX = widthPx
        )
    }

    Text(
        text = word.text,
        fontFamily = lyricsFontFamily,
        fontWeight = fontWeight,
        fontSize = fontSizeSp.sp,
        style = TextStyle(
            brush = sweepBrush,
            shadow = if (smoothGlowAlpha > 0.015f) {
                Shadow(
                    color = Color.White.copy(alpha = smoothGlowAlpha),
                    offset = Offset.Zero,
                    blurRadius = rawGlowRadius.coerceIn(2f, 16f)
                )
            } else null
        ),
        modifier = Modifier
            .onSizeChanged { measuredWidthPx = it.width.toFloat() }
            .graphicsLayer {
                transformOrigin = TransformOrigin(0.5f, 0.5f)
                scaleX = smoothScale
                scaleY = smoothScale
                translationY = smoothTranslateY
            }
    )
}

@Composable
private fun EmptyTtmlFallbackState(
    track: TrackEntity?,
    isFetchingSpicyLyrics: Boolean,
    onLoadManualTtmlClick: () -> Unit,
    onOpenSpicySearchModal: () -> Unit
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
            elevated = true,
            opaqueModal = true
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                if (isFetchingSpicyLyrics) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "FETCHING SPICY LYRICS AUTOMATICALLY...",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Matching \"${track?.title ?: ""}\" by ${track?.artist ?: ""} (Syllable > Word > LRC > Plain Text)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MonoSilver,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        text = "NO LYRICS FOUND YET",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (track != null) {
                            "Auto-lookup by Title & Artist did not find lyrics for \"${track.title}\". Search Spicy Lyrics below or load a local .ttml, .lrc, or .txt file."
                        } else {
                            "Select a song to automatically fetch lyrics or load a .ttml / .lrc / .txt file."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MonoSilver,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onOpenSpicySearchModal,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier.testTag("empty_search_spicy_lyrics_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Search Spicy Lyrics")
                        }

                        OutlinedButton(
                            onClick = onLoadManualTtmlClick,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            modifier = Modifier.testTag("empty_load_ttml_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Load .ttml/.lrc/.txt")
                        }
                    }
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
        elevated = true,
        modifier = modifier
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Text(
            text = text,
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
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
