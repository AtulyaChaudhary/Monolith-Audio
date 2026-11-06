package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.TrackEntity
import com.example.ui.theme.MonoSilver
import com.example.ui.theme.VisualThemeMode
import com.example.viewmodel.PlayerSettingsState
import kotlin.math.roundToInt

/**
 * Live Customizer for the Dynamic Frosted Glass & Pure Monochrome Theme Engine.
 * Uses `opaqueModal = true` + dark scrim so background text never bleeds through.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassCustomizerSheet(
    settings: PlayerSettingsState,
    onUpdateSettings: ((PlayerSettingsState) -> PlayerSettingsState) -> Unit,
    onUploadUiFontClick: () -> Unit,
    onResetUiFontClick: () -> Unit,
    onUploadLyricsFontClick: () -> Unit,
    onResetLyricsFontClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        scrimColor = Color.Black.copy(alpha = 0.84f),
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
                .testTag("glass_customizer_sheet")
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
                            text = "FROSTED GLASS, FONTS & MONOCHROME",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Text(
                            text = "Lyrics screen always preserves full album-art colors even in Monochrome Mode",
                            style = MaterialTheme.typography.bodySmall,
                            color = MonoSilver
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Customizer",
                            tint = Color.White
                        )
                    }
                }

                // Theme Mode Selector Cards
                VisualThemeMode.entries.forEach { mode ->
                    val selected = settings.themeMode == mode
                    FrostedGlassSurface(
                        shape = RoundedCornerShape(16.dp),
                        elevated = selected,
                        accentHighlight = selected,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onUpdateSettings { it.copy(themeMode = mode) } }
                            .testTag("theme_mode_${mode.name.lowercase()}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = Color.White
                                )
                                Text(
                                    text = mode.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MonoSilver
                                )
                            }
                            Text(
                                text = if (selected) "ACTIVE" else "SELECT",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) Color.White else MonoSilver
                            )
                        }
                    }
                }

                // CUSTOM FONT UPLOADERS (UI FONT & LYRICS FONT)
                CustomFontUploaderCard(
                    uiFontName = settings.customUiFontName,
                    lyricsFontName = settings.customLyricsFontName,
                    onUploadUiFontClick = onUploadUiFontClick,
                    onResetUiFontClick = onResetUiFontClick,
                    onUploadLyricsFontClick = onUploadLyricsFontClick,
                    onResetLyricsFontClick = onResetLyricsFontClick
                )

                // 1. REAL-TIME BACKGROUND BLUR SLIDER (0dp - 120dp)
                FrostedGlassSurface(
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Background Blur Radius",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White
                            )
                            Text(
                                text = "${settings.glassBlurRadiusDp.roundToInt()} dp",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                                modifier = Modifier.testTag("blur_slider_value_text")
                            )
                        }
                        Slider(
                            value = settings.glassBlurRadiusDp,
                            onValueChange = { r -> onUpdateSettings { it.copy(glassBlurRadiusDp = r) } },
                            valueRange = 0f..120f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("background_blur_slider")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("0dp (Crisp Shapes)", style = MaterialTheme.typography.labelSmall, color = MonoSilver)
                            Text("60dp (Frosted)", style = MaterialTheme.typography.labelSmall, color = MonoSilver)
                            Text("120dp (Deep Aura)", style = MaterialTheme.typography.labelSmall, color = MonoSilver)
                        }
                    }
                }

                // 2. FROSTED GLASS SURFACE OPACITY SLIDER (5% - 85%)
                FrostedGlassSurface(
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Frosted Glass Panel Opacity",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White
                            )
                            Text(
                                text = "${(settings.glassFrostOpacity * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                        }
                        Slider(
                            value = settings.glassFrostOpacity,
                            onValueChange = { op -> onUpdateSettings { it.copy(glassFrostOpacity = op) } },
                            valueRange = 0.05f..0.85f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("glass_opacity_slider")
                        )
                    }
                }

                // 3. BACKGROUND ANIMATION SPEED SLIDER (0.0x - 3.0x)
                FrostedGlassSurface(
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Album Art Fluid Animation Speed",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White
                            )
                            Text(
                                text = String.format(java.util.Locale.US, "%.1fx", settings.bgAnimationSpeed),
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                        }
                        Slider(
                            value = settings.bgAnimationSpeed,
                            onValueChange = { sp -> onUpdateSettings { it.copy(bgAnimationSpeed = sp) } },
                            valueRange = 0f..3.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("bg_animation_speed_slider")
                        )
                    }
                }
            }
        }
    }
}

/**
 * Comprehensive Settings View / Modal with opaque backdrop (`opaqueModal = true` + `0.86f` scrim)
 * so background text is never visible while in Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsModalSheet(
    settings: PlayerSettingsState,
    currentTrack: TrackEntity?,
    totalTracksCount: Int,
    totalFoldersCount: Int,
    onUpdateSettings: ((PlayerSettingsState) -> PlayerSettingsState) -> Unit,
    onAdjustGlobalOffset: (Int) -> Unit,
    onSetGlobalOffset: (Int) -> Unit,
    onAdjustTrackOffset: (Int) -> Unit,
    onUploadUiFontClick: () -> Unit,
    onResetUiFontClick: () -> Unit,
    onUploadLyricsFontClick: () -> Unit,
    onResetLyricsFontClick: () -> Unit,
    onClearAllTracks: () -> Unit,
    onExportPlaylistsJson: () -> String,
    onImportPlaylistsJson: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var playlistJsonField by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        scrimColor = Color.Black.copy(alpha = 0.86f),
        dragHandle = null
    ) {
        FrostedGlassSurface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            elevated = true,
            accentHighlight = true,
            opaqueModal = true,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 6.dp)
                .testTag("settings_modal_sheet")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "STUDIO & ENGINE SETTINGS",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Text(
                            text = "Custom Fonts, TTML sync, Playback & Frosted Glass settings",
                            style = MaterialTheme.typography.bodySmall,
                            color = MonoSilver
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Settings",
                            tint = Color.White
                        )
                    }
                }

                // SECTION 1: CUSTOM FONTS FOR LYRICS & ENTIRE UI (.TTF / .OTF)
                CustomFontUploaderCard(
                    uiFontName = settings.customUiFontName,
                    lyricsFontName = settings.customLyricsFontName,
                    onUploadUiFontClick = onUploadUiFontClick,
                    onResetUiFontClick = onResetUiFontClick,
                    onUploadLyricsFontClick = onUploadLyricsFontClick,
                    onResetLyricsFontClick = onResetLyricsFontClick
                )

                // SECTION 2: LYRICS TIMING OFFSET (-5000ms to +5000ms)
                FrostedGlassSurface(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("2. LYRICS TIMING OFFSET (.TTML SYNC)", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Text(
                            text = "Global Offset: ${if (settings.globalLyricsOffsetMs >= 0) "+" else ""}${settings.globalLyricsOffsetMs}ms",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            modifier = Modifier.testTag("global_offset_value_text")
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MiniPillButton("-500ms", { onAdjustGlobalOffset(-500) }, "global_offset_minus_500")
                            MiniPillButton("-100ms", { onAdjustGlobalOffset(-100) }, "global_offset_minus_100")
                            MiniPillButton("0ms", { onSetGlobalOffset(0) }, "global_offset_reset")
                            MiniPillButton("+100ms", { onAdjustGlobalOffset(100) }, "global_offset_plus_100")
                            MiniPillButton("+500ms", { onAdjustGlobalOffset(500) }, "global_offset_plus_500")
                        }

                        Slider(
                            value = settings.globalLyricsOffsetMs.toFloat(),
                            onValueChange = { onSetGlobalOffset(it.roundToInt()) },
                            valueRange = -5000f..5000f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                            )
                        )

                        if (currentTrack != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Track Offset (${currentTrack.title}): ${currentTrack.lyricsOffsetMs}ms",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MonoSilver,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    MiniPillButton("-100ms", { onAdjustTrackOffset(-100) }, "track_offset_minus_100")
                                    MiniPillButton("+100ms", { onAdjustTrackOffset(100) }, "track_offset_plus_100")
                                }
                            }
                        }
                    }
                }

                // SECTION 3: PLAYBACK BEHAVIOR
                FrostedGlassSurface(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("3. PLAYBACK BEHAVIOR", style = MaterialTheme.typography.titleMedium, color = Color.White)

                        SettingToggleRow(
                            title = "Reset Speed & Pitch to 1.0x on Track Change",
                            subtitle = if (settings.resetSpeedPitchOnTrackChange) "Resets to 1.0x when switching songs" else "Locks active Speed & Pitch across tracks",
                            checked = settings.resetSpeedPitchOnTrackChange,
                            onCheckedChange = { v -> onUpdateSettings { it.copy(resetSpeedPitchOnTrackChange = v) } },
                            tag = "toggle_reset_speed_pitch"
                        )

                        SettingToggleRow(
                            title = "Gapless Playback",
                            subtitle = "Zero-gap buffer transition across queued tracks",
                            checked = settings.gaplessPlayback,
                            onCheckedChange = { v -> onUpdateSettings { it.copy(gaplessPlayback = v) } },
                            tag = "toggle_gapless"
                        )

                        SettingToggleRow(
                            title = "ReplayGain / Volume Normalization",
                            subtitle = "Applies track loudness normalization in real time",
                            checked = settings.replayGainEnabled,
                            onCheckedChange = { v -> onUpdateSettings { it.copy(replayGainEnabled = v) } },
                            tag = "toggle_replaygain"
                        )

                        Text(
                            text = "Crossfade Duration: ${settings.crossfadeDurationSec}s",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                        Slider(
                            value = settings.crossfadeDurationSec.toFloat(),
                            onValueChange = { s -> onUpdateSettings { it.copy(crossfadeDurationSec = s.roundToInt()) } },
                            valueRange = 0f..12f,
                            steps = 11,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                            )
                        )
                    }
                }

                // SECTION 4: LYRICS APPEARANCE & LYRICSANIMATOR PHYSICS
                FrostedGlassSurface(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("4. LYRICS APPEARANCE & ANIMATOR ENGINE", style = MaterialTheme.typography.titleMedium, color = Color.White)

                        Text(
                            text = "Lyrics Font Size: ${settings.lyricsFontSizeSp.roundToInt()}sp (Background vocals scale to 66%)",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                        Slider(
                            value = settings.lyricsFontSizeSp,
                            onValueChange = { sz -> onUpdateSettings { it.copy(lyricsFontSizeSp = sz) } },
                            valueRange = 16f..42f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                            )
                        )

                        Text(
                            text = "Word-by-Word Glow Intensity: ${(settings.lyricsGlowIntensity * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                        Slider(
                            value = settings.lyricsGlowIntensity,
                            onValueChange = { g -> onUpdateSettings { it.copy(lyricsGlowIntensity = g) } },
                            valueRange = 0f..2.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                            )
                        )

                        SettingToggleRow(
                            title = "Auto-Scroll Lock to Active Vocal Line",
                            subtitle = "Automatically centers the active .ttml lyric line",
                            checked = settings.autoScrollLyrics,
                            onCheckedChange = { v -> onUpdateSettings { it.copy(autoScrollLyrics = v) } },
                            tag = "toggle_autoscroll_lyrics"
                        )

                        SettingToggleRow(
                            title = "Simple Lyrics Mode (Refreshed Wave)",
                            subtitle = "Uses Refreshed Simple Lyrics LineSweep & Letter Wave",
                            checked = settings.simpleLyricsMode,
                            onCheckedChange = { v -> onUpdateSettings { it.copy(simpleLyricsMode = v) } },
                            tag = "toggle_simple_lyrics_mode"
                        )
                    }
                }

                // SECTION 5: LIBRARY & STORAGE MANAGEMENT
                FrostedGlassSurface(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("5. LIBRARY & STORAGE MANAGEMENT", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Text(
                            text = "Indexed: $totalTracksCount Tracks across $totalFoldersCount Folders",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MonoSilver
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = onClearAllTracks,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                modifier = Modifier.testTag("clear_library_button")
                            ) {
                                Text("Clear Library Cache")
                            }

                            OutlinedButton(
                                onClick = { playlistJsonField = onExportPlaylistsJson() },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                modifier = Modifier.testTag("export_playlists_json_button")
                            ) {
                                Text("Export Playlists JSON")
                            }
                        }

                        if (playlistJsonField.isNotBlank()) {
                            OutlinedTextField(
                                value = playlistJsonField,
                                onValueChange = { playlistJsonField = it },
                                label = { Text("Playlists JSON (Edit or Paste to Import)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = { onImportPlaylistsJson(playlistJsonField) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                            ) {
                                Text("Import Playlists JSON")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun CustomFontUploaderCard(
    uiFontName: String?,
    lyricsFontName: String?,
    onUploadUiFontClick: () -> Unit,
    onResetUiFontClick: () -> Unit,
    onUploadLyricsFontClick: () -> Unit,
    onResetLyricsFontClick: () -> Unit
) {
    FrostedGlassSurface(
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "CUSTOM TYPOGRAPHY (.TTF / .OTF UPLOAD)",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )

            // 1. Custom Entire UI Font
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Entire UI Font",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White
                    )
                    Text(
                        text = uiFontName ?: "Default (Space Grotesk / Inter)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MonoSilver
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MiniPillButton(
                        text = "Upload .ttf/.otf",
                        onClick = onUploadUiFontClick,
                        tag = "upload_ui_font_button"
                    )
                    if (uiFontName != null) {
                        MiniPillButton(
                            text = "Reset",
                            onClick = onResetUiFontClick,
                            tag = "reset_ui_font_button"
                        )
                    }
                }
            }

            // 2. Custom Lyrics Stage Font
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Synced Lyrics Font",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White
                    )
                    Text(
                        text = lyricsFontName ?: (uiFontName ?: "Default (Space Grotesk Bold)"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MonoSilver
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MiniPillButton(
                        text = "Upload .ttf/.otf",
                        onClick = onUploadLyricsFontClick,
                        tag = "upload_lyrics_font_button"
                    )
                    if (lyricsFontName != null) {
                        MiniPillButton(
                            text = "Reset",
                            onClick = onResetLyricsFontClick,
                            tag = "reset_lyrics_font_button"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MonoSilver)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = Color.White
            ),
            modifier = Modifier.testTag(tag)
        )
    }
}
