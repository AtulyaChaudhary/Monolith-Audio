package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.MusicAudioEngine
import com.example.data.MainNavTab
import com.example.data.QueueItem
import com.example.data.TrackEntity
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MonoCarbon
import com.example.ui.theme.MonoLightGrey
import com.example.ui.theme.MonoSilver

@Composable
fun BottomPlayerAndNavColumn(
    currentTrack: TrackEntity?,
    isPlaying: Boolean,
    currentPositionMs: Double,
    durationMs: Long,
    playbackSpeed: Float,
    playbackPitch: Float,
    queueSize: Int,
    currentQueueIndex: Int,
    hasCoLocatedTtml: Boolean,
    mainTab: MainNavTab,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onExpandLyrics: () -> Unit,
    onOpenSpeedPitch: () -> Unit,
    onOpenQueueDrawer: () -> Unit,
    onSelectMainTab: (MainNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Persistent Frosted Glass Mini-Player / Studio Deck
        if (currentTrack != null) {
            FrostedGlassSurface(
                shape = RoundedCornerShape(22.dp),
                elevated = true,
                accentHighlight = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandLyrics() }
                    .testTag("mini_player_bar")
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    val progress = (currentPositionMs.toFloat() / durationMs.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.18f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Crisp Unblurred Album Art
                        CrispAlbumArtCover(
                            title = currentTrack.album,
                            subtitle = currentTrack.artist,
                            palette = currentTrack.parsePalette(),
                            coverArtPath = currentTrack.coverArtPath,
                            sizeDp = 46.dp,
                            cornerRadiusDp = 12.dp,
                            modifier = Modifier.size(46.dp)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentTrack.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${currentTrack.artist} • ${currentTrack.folderName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MonoSilver,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Transport Buttons
                        IconButton(
                            onClick = onSkipPrevious,
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("player_prev_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous Track",
                                tint = Color.White
                            )
                        }

                        IconButton(
                            onClick = onTogglePlayPause,
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("player_play_pause_button")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        IconButton(
                            onClick = onSkipNext,
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("player_next_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next Track",
                                tint = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Studio Quick Pills Row: Speed/Pitch (0.1x-5.0x), Synced TTML Lyrics, Queue Drawer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MiniPillButton(
                            text = "SPD ${MusicAudioEngine.formatMultiplier(playbackSpeed)} • PIT ${MusicAudioEngine.formatMultiplier(playbackPitch)}",
                            onClick = onOpenSpeedPitch,
                            tag = "open_speed_pitch_button"
                        )

                        MiniPillButton(
                            text = if (hasCoLocatedTtml) "TTML Lyrics ●" else "TTML Lyrics",
                            onClick = onExpandLyrics,
                            tag = "open_lyrics_button"
                        )

                        MiniPillButton(
                            text = "Queue (${(currentQueueIndex + 1).coerceAtLeast(0)}/$queueSize)",
                            onClick = onOpenQueueDrawer,
                            tag = "open_queue_drawer_button"
                        )
                    }
                }
            }
        }

        // Bottom Navigation Switcher: All Songs vs Categories
        FrostedGlassSurface(
            shape = RoundedCornerShape(22.dp),
            elevated = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("bottom_navigation_switcher")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isAllSongs = mainTab == MainNavTab.ALL_SONGS
                val isCategories = mainTab == MainNavTab.CATEGORIES

                FrostedGlassSurface(
                    shape = RoundedCornerShape(16.dp),
                    elevated = isAllSongs,
                    accentHighlight = isAllSongs,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelectMainTab(MainNavTab.ALL_SONGS) }
                        .testTag("nav_tab_all_songs")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LibraryMusic,
                            contentDescription = "All Songs",
                            tint = if (isAllSongs) Color.White else MonoSilver,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "All Songs",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = if (isAllSongs) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isAllSongs) Color.White else MonoSilver
                        )
                    }
                }

                FrostedGlassSurface(
                    shape = RoundedCornerShape(16.dp),
                    elevated = isCategories,
                    accentHighlight = isCategories,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelectMainTab(MainNavTab.CATEGORIES) }
                        .testTag("nav_tab_categories")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = "Categories",
                            tint = if (isCategories) Color.White else MonoSilver,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Categories",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = if (isCategories) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isCategories) Color.White else MonoSilver
                        )
                    }
                }
            }
        }
    }
}

/**
 * Granular 0.1x–5.0x Speed & Pitch Studio Popover / Bottom Sheet:
 * - Independent Speed and Pitch sliders from 0.1x to 5.0x formatted strictly to 1 decimal place.
 * - `-` button on the LEFT side of each slider bar (-0.1x per click).
 * - `+` button on the RIGHT side of each slider bar (+0.1x per click).
 * - Dedicated `Reset (1.0x)` button for each control.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedAndPitchModalSheet(
    speed: Float,
    pitch: Float,
    onSpeedChange: (Float) -> Unit,
    onStepSpeed: (Int) -> Unit,
    onResetSpeed: () -> Unit,
    onPitchChange: (Float) -> Unit,
    onStepPitch: (Int) -> Unit,
    onResetPitch: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                .padding(horizontal = 8.dp)
                .testTag("speed_pitch_modal")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "GRANULAR SPEED & PITCH ENGINE",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Text(
                            text = "Independent 0.1x – 5.0x real-time DSP manipulation",
                            style = MaterialTheme.typography.bodySmall,
                            color = MonoSilver
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }

                // 1. PLAYBACK SPEED CONTROL (0.1x - 5.0x)
                GranularRateControlCard(
                    label = "Playback Speed",
                    formattedValue = MusicAudioEngine.formatMultiplier(speed),
                    rawValue = speed,
                    onSliderChange = onSpeedChange,
                    onDecrement = { onStepSpeed(-1) },
                    onIncrement = { onStepSpeed(1) },
                    onReset = onResetSpeed,
                    minusTag = "speed_minus_button",
                    sliderTag = "speed_slider",
                    plusTag = "speed_plus_button",
                    resetTag = "speed_reset_button",
                    valueTag = "speed_value_text"
                )

                // 2. PITCH CONTROL (0.1x - 5.0x)
                GranularRateControlCard(
                    label = "Audio Pitch",
                    formattedValue = MusicAudioEngine.formatMultiplier(pitch),
                    rawValue = pitch,
                    onSliderChange = onPitchChange,
                    onDecrement = { onStepPitch(-1) },
                    onIncrement = { onStepPitch(1) },
                    onReset = onResetPitch,
                    minusTag = "pitch_minus_button",
                    sliderTag = "pitch_slider",
                    plusTag = "pitch_plus_button",
                    resetTag = "pitch_reset_button",
                    valueTag = "pitch_value_text"
                )

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun GranularRateControlCard(
    label: String,
    formattedValue: String,
    rawValue: Float,
    onSliderChange: (Float) -> Unit,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    onReset: () -> Unit,
    minusTag: String,
    sliderTag: String,
    plusTag: String,
    resetTag: String,
    valueTag: String
) {
    FrostedGlassSurface(
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Text(
                        text = formattedValue,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White,
                        modifier = Modifier.testTag(valueTag)
                    )
                }

                MiniPillButton(
                    text = "Reset (1.0x)",
                    onClick = onReset,
                    tag = resetTag
                )
            }

            // Horizontal Slider Bar flanked by `-` on the LEFT and `+` on the RIGHT
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // LEFT `-` Button (-0.1x per click)
                FrostedGlassSurface(
                    shape = CircleShape,
                    accentHighlight = true,
                    modifier = Modifier
                        .size(48.dp)
                        .clickable(onClick = onDecrement)
                        .testTag(minusTag)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxHeight().fillMaxWidth()) {
                        Text(
                            text = "−",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = Color.White
                        )
                    }
                }

                // Horizontal Slider (0.1x to 5.0x)
                Slider(
                    value = rawValue.coerceIn(0.1f, 5.0f),
                    onValueChange = onSliderChange,
                    valueRange = 0.1f..5.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag(sliderTag)
                )

                // RIGHT `+` Button (+0.1x per click)
                FrostedGlassSurface(
                    shape = CircleShape,
                    accentHighlight = true,
                    modifier = Modifier
                        .size(48.dp)
                        .clickable(onClick = onIncrement)
                        .testTag(plusTag)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxHeight().fillMaxWidth()) {
                        Text(
                            text = "+",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = Color.White
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("0.1x", style = MaterialTheme.typography.labelSmall, color = MonoSilver)
                Text("1.0x", style = MaterialTheme.typography.labelSmall, color = MonoSilver)
                Text("2.5x", style = MaterialTheme.typography.labelSmall, color = MonoSilver)
                Text("5.0x", style = MaterialTheme.typography.labelSmall, color = MonoSilver)
            }
        }
    }
}

/**
 * Interactive Queue Drawer / Modal enforcing the Strict Queue State Machine:
 * - Shows active track and upcoming tracks in exact playback order.
 * - Up/Down reorder buttons to move songs up or down.
 * - Remove individual song button.
 * - Clear upcoming tracks button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueDrawerSheet(
    queue: List<QueueItem>,
    currentQueueIndex: Int,
    queueContextLabel: String,
    onJumpToIndex: (Int) -> Unit,
    onMoveItem: (Int, Int) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onClearUpcoming: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                .fillMaxHeight(0.84f)
                .padding(horizontal = 6.dp)
                .testTag("queue_drawer_sheet")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PLAYBACK QUEUE (${queue.size})",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Text(
                            text = "Context: $queueContextLabel • \"Add to Queue\" inserts at #${(currentQueueIndex + 2).coerceAtLeast(1)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MonoSilver
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MiniPillButton(
                            text = "Clear Upcoming",
                            onClick = onClearUpcoming,
                            tag = "clear_upcoming_queue_button"
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Queue",
                                tint = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("queue_items_lazy_column")
                ) {
                    itemsIndexed(
                        items = queue,
                        key = { _, item -> item.queueEntryId }
                    ) { index, item ->
                        val isCurrent = index == currentQueueIndex
                        FrostedGlassSurface(
                            shape = RoundedCornerShape(16.dp),
                            elevated = isCurrent,
                            accentHighlight = isCurrent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onJumpToIndex(index) }
                                .testTag("queue_item_pos_${index + 1}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = "#${index + 1}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (isCurrent) Color.White else MonoSilver,
                                    modifier = Modifier.width(34.dp)
                                )

                                CrispAlbumArtCover(
                                    title = item.track.album,
                                    subtitle = item.track.artist,
                                    palette = item.track.parsePalette(),
                                    coverArtPath = item.track.coverArtPath,
                                    sizeDp = 42.dp,
                                    cornerRadiusDp = 10.dp,
                                    modifier = Modifier.size(42.dp)
                                )

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.track.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (isCurrent) "NOW PLAYING • ${item.track.artist}" else "${item.track.artist} • ${item.track.folderName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isCurrent) MonoLightGrey else MonoSilver,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Move Up Button
                                IconButton(
                                    onClick = { if (index > 0) onMoveItem(index, index - 1) },
                                    enabled = index > 0,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .testTag("queue_move_up_${index + 1}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowUp,
                                        contentDescription = "Move Up",
                                        tint = if (index > 0) Color.White else MonoSilver.copy(alpha = 0.3f)
                                    )
                                }

                                // Move Down Button
                                IconButton(
                                    onClick = { if (index < queue.lastIndex) onMoveItem(index, index + 1) },
                                    enabled = index < queue.lastIndex,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .testTag("queue_move_down_${index + 1}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Move Down",
                                        tint = if (index < queue.lastIndex) Color.White else MonoSilver.copy(alpha = 0.3f)
                                    )
                                }

                                // Remove Button
                                IconButton(
                                    onClick = { onRemoveItem(index) },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .testTag("queue_remove_${index + 1}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Remove from Queue",
                                        tint = MonoSilver
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
