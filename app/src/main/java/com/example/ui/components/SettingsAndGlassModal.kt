package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TrackEntity
import com.example.lyrics.SpicyLyricsClient
import com.example.lyrics.SpicyTrackSearchResult
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MonoSilver
import com.example.ui.theme.VisualThemeMode
import com.example.viewmodel.PlayerSettingsState
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Theme Studio, Per-Theme Customizer & Lyrics Background Sheet.
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
        scrimColor = Color.Black.copy(alpha = 0.88f),
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
                            text = "THEME STUDIO & LYRICS BACKDROP",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Text(
                            text = "Switch themes to unlock dedicated per-theme controls & customize the Lyrics blur",
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

                // Theme Mode Selector Cards + Dynamic Per-Theme Customization Panel
                ThemeSelectorAndCustomizationsSection(
                    settings = settings,
                    onUpdateSettings = onUpdateSettings
                )

                // Restored Lyrics Screen Background Blur, Black Tint & 9-Orb Settings
                LyricsBackdropSettingsCard(
                    settings = settings,
                    onUpdateSettings = onUpdateSettings
                )

                // CUSTOM FONT UPLOADERS (UI FONT & LYRICS FONT)
                CustomFontUploaderCard(
                    uiFontName = settings.customUiFontName,
                    lyricsFontName = settings.customLyricsFontName,
                    onUploadUiFontClick = onUploadUiFontClick,
                    onResetUiFontClick = onResetUiFontClick,
                    onUploadLyricsFontClick = onUploadLyricsFontClick,
                    onResetLyricsFontClick = onResetLyricsFontClick
                )
            }
        }
    }
}

/**
 * Comprehensive Settings Modal with:
 * 1. Spicy Lyrics API Secret Key (`$SPICY_LYRICS_SECRET_KEY`) + Revoke button + Node.js fetch snippet
 * 2. Theme Selector & Per-Theme Customizations (Material You, True Pitch-Black Monochrome, Frosted Glass)
 * 3. Restored Lyrics Screen Background Blur, Black Tint & 9-Orb Controls
 * 4. Persistent Embedded Lyrics Cache Management ("Clear Current Song Lyrics Cache" & "Clear All Lyrics Cache")
 * 5. Custom Font Uploaders (.ttf / .otf) for UI & Lyrics
 * 6. Lyrics Timing Offset & Playback Settings
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsModalSheet(
    settings: PlayerSettingsState,
    currentTrack: TrackEntity?,
    totalTracksCount: Int,
    totalFoldersCount: Int,
    onUpdateSettings: ((PlayerSettingsState) -> PlayerSettingsState) -> Unit,
    onSaveSpicySecretKey: (String) -> Unit,
    onRevokeSpicySecretKey: () -> Unit,
    onOpenSpicySearchModal: () -> Unit,
    onFetchDirectSpicyId: (String) -> Unit,
    onAdjustGlobalOffset: (Int) -> Unit,
    onSetGlobalOffset: (Int) -> Unit,
    onAdjustTrackOffset: (Int) -> Unit,
    onUploadUiFontClick: () -> Unit,
    onResetUiFontClick: () -> Unit,
    onUploadLyricsFontClick: () -> Unit,
    onResetLyricsFontClick: () -> Unit,
    onClearCurrentTrackLyricsCache: () -> Unit = {},
    onClearAllLyricsCache: () -> Unit = {},
    onRescanLibraryClick: () -> Unit,
    onClearAllTracks: () -> Unit,
    onExportPlaylistsJson: () -> String,
    onImportPlaylistsJson: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var playlistJsonField by remember { mutableStateOf("") }
    var secretKeyInput by remember(settings.spicyLyricsSecretKey) {
        mutableStateOf(
            if (SpicyLyricsClient.isSecretKeyConfigured(settings.spicyLyricsSecretKey)) {
                settings.spicyLyricsSecretKey
            } else {
                ""
            }
        )
    }
    var directTrackIdInput by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        scrimColor = Color.Black.copy(alpha = 0.88f),
        dragHandle = null
    ) {
        FrostedGlassSurface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            elevated = true,
            accentHighlight = true,
            opaqueModal = true,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.93f)
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "STUDIO & SPICY LYRICS SETTINGS",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Text(
                            text = "Spicy Lyrics Secret Key, Theme Customizer, Lyrics Blur & Embedded Cache",
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

                // SECTION 1: SPICY LYRICS SECRET KEY ($SPICY_LYRICS_SECRET_KEY) & NODE.JS ENGINE
                FrostedGlassSurface(
                    shape = RoundedCornerShape(18.dp),
                    elevated = true,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "1. SPICY LYRICS SECRET KEY (NODE.JS FETCH)",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                        }

                        val isKeyActive = SpicyLyricsClient.isSecretKeyConfigured(settings.spicyLyricsSecretKey)
                        Text(
                            text = if (isKeyActive) {
                                "Active Key: ${settings.spicyLyricsSecretKey.take(10)}... (Auto-fetches exact song match on play)"
                            } else {
                                "Active Variable: ${SpicyLyricsClient.DEFAULT_SECRET_KEY_PLACEHOLDER} (Enter your sl_sk_... key below)"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isKeyActive) MaterialTheme.colorScheme.primary else MonoSilver,
                            modifier = Modifier.testTag("spicy_key_status_text")
                        )

                        OutlinedTextField(
                            value = secretKeyInput,
                            onValueChange = { secretKeyInput = it },
                            placeholder = {
                                Text(
                                    "sl_sk_... (Replaces \$SPICY_LYRICS_SECRET_KEY)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MonoSilver
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("spicy_secret_key_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (secretKeyInput.isNotBlank()) {
                                        onSaveSpicySecretKey(secretKeyInput.trim())
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("save_spicy_secret_key_button")
                            ) {
                                Text("Apply Secret Key")
                            }

                            OutlinedButton(
                                onClick = {
                                    secretKeyInput = ""
                                    onRevokeSpicySecretKey()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("revoke_spicy_secret_key_button")
                            ) {
                                Text("Revoke Secret Key")
                            }
                        }

                        // Live Node.js fetch() snippet showing the substituted secret key or $SPICY_LYRICS_SECRET_KEY
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.72f))
                                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = SpicyLyricsClient.buildNodeJsFetchSnippet(
                                    secretKey = settings.spicyLyricsSecretKey,
                                    trackId = directTrackIdInput.ifBlank { "TRACK_ID" }
                                ),
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                                color = Color.White.copy(alpha = 0.88f),
                                modifier = Modifier.testTag("nodejs_fetch_snippet_text")
                            )
                        }

                        // Direct Track ID input (Spotify ID, Apple Music ID, or ISRC) + Search Song Modal button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = directTrackIdInput,
                                onValueChange = { directTrackIdInput = it },
                                placeholder = {
                                    Text(
                                        "Spotify ID, Apple Music ID, or ISRC",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MonoSilver
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("direct_spicy_track_id_input")
                            )

                            Button(
                                onClick = {
                                    if (directTrackIdInput.isNotBlank()) {
                                        onFetchDirectSpicyId(directTrackIdInput.trim())
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Black
                                ),
                                modifier = Modifier.testTag("fetch_direct_spicy_id_button")
                            ) {
                                Text("Fetch ID")
                            }
                        }

                        Button(
                            onClick = {
                                onDismiss()
                                onOpenSpicySearchModal()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_open_spicy_search_button")
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Search Songs on Spicy Lyrics (Fallback / Override)")
                        }
                    }
                }

                // SECTION 2: THEME SELECTOR & PER-THEME CUSTOMIZATIONS
                ThemeSelectorAndCustomizationsSection(
                    settings = settings,
                    onUpdateSettings = onUpdateSettings
                )

                // SECTION 3: LYRICS SCREEN BACKGROUND BLUR, BLACK TINT & 9-ORB CONTROLS
                LyricsBackdropSettingsCard(
                    settings = settings,
                    onUpdateSettings = onUpdateSettings
                )

                // SECTION 4: PERSISTENT EMBEDDED LYRICS CACHE MANAGEMENT
                FrostedGlassSurface(
                    shape = RoundedCornerShape(18.dp),
                    elevated = true,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "4. EMBEDDED LYRICS STORAGE CACHE",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                        }
                        Text(
                            text = "Resolved lyrics (.ttml, .lrc, .txt & Spicy Lyrics JSON) stay embedded in storage across sessions until explicitly cleared below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MonoSilver
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onClearCurrentTrackLyricsCache,
                                enabled = currentTrack != null,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("clear_current_song_lyrics_cache_button")
                            ) {
                                Text("Clear Current Song Lyrics Cache")
                            }

                            Button(
                                onClick = onClearAllLyricsCache,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Black
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("clear_all_lyrics_cache_button")
                            ) {
                                Text("Clear All Lyrics Cache")
                            }
                        }
                    }
                }

                // SECTION 5: CUSTOM FONTS FOR LYRICS & ENTIRE UI (.TTF / .OTF)
                CustomFontUploaderCard(
                    uiFontName = settings.customUiFontName,
                    lyricsFontName = settings.customLyricsFontName,
                    onUploadUiFontClick = onUploadUiFontClick,
                    onResetUiFontClick = onResetUiFontClick,
                    onUploadLyricsFontClick = onUploadLyricsFontClick,
                    onResetLyricsFontClick = onResetLyricsFontClick
                )

                // SECTION 6: LYRICS TIMING OFFSET (-5000ms to +5000ms)
                FrostedGlassSurface(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("6. LYRICS TIMING OFFSET", style = MaterialTheme.typography.titleMedium, color = Color.White)
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

                // SECTION 7: LYRICS APPEARANCE & PLAYBACK
                FrostedGlassSurface(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("7. LYRICS & PLAYBACK ENGINE", style = MaterialTheme.typography.titleMedium, color = Color.White)

                        Text(
                            text = "Lyrics Font Size: ${settings.lyricsFontSizeSp.roundToInt()}sp (Background vocals scale to 68%)",
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

                        SettingToggleRow(
                            title = "Auto-Scroll Lock to Active Vocal Line",
                            subtitle = "Smoothly centers the active lyric line without layout jitter",
                            checked = settings.autoScrollLyrics,
                            onCheckedChange = { v -> onUpdateSettings { it.copy(autoScrollLyrics = v) } },
                            tag = "toggle_autoscroll_lyrics"
                        )

                        SettingToggleRow(
                            title = "Reset Speed & Pitch to 1.0x on Track Change",
                            subtitle = if (settings.resetSpeedPitchOnTrackChange) "Resets to 1.0x when switching songs" else "Locks active Speed & Pitch across tracks",
                            checked = settings.resetSpeedPitchOnTrackChange,
                            onCheckedChange = { v -> onUpdateSettings { it.copy(resetSpeedPitchOnTrackChange = v) } },
                            tag = "toggle_reset_speed_pitch"
                        )
                    }
                }

                // SECTION 8: LIBRARY & STORAGE MANAGEMENT
                FrostedGlassSurface(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("8. LIBRARY & STORAGE MANAGEMENT", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Text(
                            text = "Indexed: $totalTracksCount Tracks across $totalFoldersCount Folders",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MonoSilver
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = onRescanLibraryClick,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier.testTag("settings_rescan_library_button")
                            ) {
                                Text("Rescan Folders")
                            }

                            OutlinedButton(
                                onClick = onClearAllTracks,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                modifier = Modifier.testTag("clear_library_button")
                            ) {
                                Text("Clear Library")
                            }
                        }

                        OutlinedButton(
                            onClick = { playlistJsonField = onExportPlaylistsJson() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            modifier = Modifier.testTag("export_playlists_json_button")
                        ) {
                            Text("Export Playlists JSON")
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

/**
 * Theme Mode Selector Cards + Dedicated Per-Theme Customization Panel that dynamically adapts to the active theme.
 */
@Composable
private fun ThemeSelectorAndCustomizationsSection(
    settings: PlayerSettingsState,
    onUpdateSettings: ((PlayerSettingsState) -> PlayerSettingsState) -> Unit
) {
    FrostedGlassSurface(
        shape = RoundedCornerShape(18.dp),
        elevated = true,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "2. THEME MODES & PER-THEME CUSTOMIZATION",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )

            listOf(
                VisualThemeMode.MATERIAL_YOU,
                VisualThemeMode.PURE_MONOCHROME,
                VisualThemeMode.FROSTED_GLASS
            ).forEach { mode ->
                val selected = settings.themeMode == mode
                FrostedGlassSurface(
                    shape = RoundedCornerShape(14.dp),
                    elevated = selected,
                    accentHighlight = selected,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onUpdateSettings { it.copy(themeMode = mode) } }
                        .testTag("theme_mode_${mode.name.lowercase()}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold
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

            // Dynamic Per-Theme Customization Controls for the currently selected theme
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.04f))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
                    .padding(14.dp)
                    .testTag("per_theme_customization_panel")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (settings.themeMode) {
                        VisualThemeMode.MATERIAL_YOU -> {
                            Text(
                                text = "MATERIAL YOU DYNAMIC CUSTOMIZATION",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                                color = Color.White
                            )

                            SettingToggleRow(
                                title = "Bleed Album Art Colors into Song List",
                                subtitle = "Lets the single universal 9-orb blurred background gently tint the song list page",
                                checked = settings.bleedAlbumArtIntoSongList,
                                onCheckedChange = { v ->
                                    onUpdateSettings { it.copy(bleedAlbumArtIntoSongList = v) }
                                },
                                tag = "settings_toggle_song_list_bleed"
                            )

                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Dynamic Accent Vibrancy", style = MaterialTheme.typography.labelMedium, color = Color.White)
                                    Text("${(settings.materialYouAccentBoost * 100).roundToInt()}%", fontFamily = JetBrainsMonoFontFamily, fontSize = 12.sp, color = MonoSilver)
                                }
                                Slider(
                                    value = settings.materialYouAccentBoost.coerceIn(0.15f, 1.0f),
                                    onValueChange = { v -> onUpdateSettings { it.copy(materialYouAccentBoost = v) } },
                                    valueRange = 0.15f..1.0f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = Color.White,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                                    ),
                                    modifier = Modifier.testTag("slider_material_you_accent_boost")
                                )
                            }

                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Surface Tonal Richness", style = MaterialTheme.typography.labelMedium, color = Color.White)
                                    Text("${(settings.materialYouSurfaceRichness * 100).roundToInt()}%", fontFamily = JetBrainsMonoFontFamily, fontSize = 12.sp, color = MonoSilver)
                                }
                                Slider(
                                    value = settings.materialYouSurfaceRichness.coerceIn(0.02f, 0.35f),
                                    onValueChange = { v -> onUpdateSettings { it.copy(materialYouSurfaceRichness = v) } },
                                    valueRange = 0.02f..0.35f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = Color.White,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                                    ),
                                    modifier = Modifier.testTag("slider_material_you_surface_richness")
                                )
                            }
                        }

                        VisualThemeMode.PURE_MONOCHROME, VisualThemeMode.MONOCHROME_GLASS -> {
                            Text(
                                text = "TRUE PITCH-BLACK (#000000) & WHITE (#FFFFFF) CUSTOMIZATION",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                                color = Color.White
                            )

                            SettingToggleRow(
                                title = "OLED Pitch-Black (#000000) Canvas",
                                subtitle = "Pure #000000 background and cards with zero blue tint",
                                checked = settings.oledPitchBlack,
                                onCheckedChange = { v -> onUpdateSettings { it.copy(oledPitchBlack = v) } },
                                tag = "toggle_oled_pitch_black"
                            )

                            SettingToggleRow(
                                title = "Full-Color Album Art & Lyrics Stage",
                                subtitle = "Keeps album covers and the Lyrics 9-orb stage vivid even in Monochrome mode",
                                checked = settings.monoKeepLyricsColored,
                                onCheckedChange = { v -> onUpdateSettings { it.copy(monoKeepLyricsColored = v) } },
                                tag = "toggle_mono_keep_lyrics_colored"
                            )

                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Pure White Border Contrast", style = MaterialTheme.typography.labelMedium, color = Color.White)
                                    Text("${(settings.monoBorderContrast * 100).roundToInt()}%", fontFamily = JetBrainsMonoFontFamily, fontSize = 12.sp, color = MonoSilver)
                                }
                                Slider(
                                    value = settings.monoBorderContrast.coerceIn(0.12f, 0.90f),
                                    onValueChange = { v -> onUpdateSettings { it.copy(monoBorderContrast = v) } },
                                    valueRange = 0.12f..0.90f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = Color.White,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                                    ),
                                    modifier = Modifier.testTag("slider_mono_border_contrast")
                                )
                            }
                        }

                        VisualThemeMode.FROSTED_GLASS -> {
                            Text(
                                text = "FROSTED GLASS AURA CUSTOMIZATION",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                                color = Color.White
                            )

                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Glass Frost Opacity", style = MaterialTheme.typography.labelMedium, color = Color.White)
                                    Text("${(settings.glassFrostOpacity * 100).roundToInt()}%", fontFamily = JetBrainsMonoFontFamily, fontSize = 12.sp, color = MonoSilver)
                                }
                                Slider(
                                    value = settings.glassFrostOpacity.coerceIn(0.05f, 0.98f),
                                    onValueChange = { v -> onUpdateSettings { it.copy(glassFrostOpacity = v) } },
                                    valueRange = 0.05f..0.98f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = Color.White,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                                    ),
                                    modifier = Modifier.testTag("slider_glass_frost_opacity")
                                )
                            }

                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Specular Rim Highlight", style = MaterialTheme.typography.labelMedium, color = Color.White)
                                    Text("${(settings.glassSpecularIntensity * 100).roundToInt()}%", fontFamily = JetBrainsMonoFontFamily, fontSize = 12.sp, color = MonoSilver)
                                }
                                Slider(
                                    value = settings.glassSpecularIntensity.coerceIn(0f, 1f),
                                    onValueChange = { v -> onUpdateSettings { it.copy(glassSpecularIntensity = v) } },
                                    valueRange = 0f..1f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = Color.White,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                                    ),
                                    modifier = Modifier.testTag("slider_glass_specular_intensity")
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Restored Lyrics Screen Background Settings Card:
 * - Background Blur Radius (0dp–120dp)
 * - Protective Black Tint on Blur (15%–80%) so lyrics stay readable even over bright white album art
 * - 9-Orb Background Animation Speed (0.0x–3.0x)
 * - Word Glow Intensity (0.0x–2.0x)
 */
@Composable
private fun LyricsBackdropSettingsCard(
    settings: PlayerSettingsState,
    onUpdateSettings: ((PlayerSettingsState) -> PlayerSettingsState) -> Unit
) {
    FrostedGlassSurface(
        shape = RoundedCornerShape(18.dp),
        elevated = true,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "3. LYRICS SCREEN BLUR, BLACK TINT & ORB BACKDROP",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )

            // 1. Background Blur Radius (0dp - 120dp)
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Background Blur Radius", style = MaterialTheme.typography.labelMedium, color = Color.White)
                    Text("${settings.glassBlurRadiusDp.roundToInt()}dp", fontFamily = JetBrainsMonoFontFamily, fontSize = 12.sp, color = MonoSilver)
                }
                Slider(
                    value = settings.glassBlurRadiusDp.coerceIn(0f, 120f),
                    onValueChange = { v -> onUpdateSettings { it.copy(glassBlurRadiusDp = v) } },
                    valueRange = 0f..120f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                    ),
                    modifier = Modifier.testTag("settings_slider_blur_radius")
                )
            }

            // 2. Protective Black Tint on Lyrics Blur (15% - 80%)
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Protective Black Tint on Blur (White Album Art Contrast)",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                    Text(
                        "${(settings.lyricsBlackTintAlpha * 100).roundToInt()}%",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 12.sp,
                        color = MonoSilver
                    )
                }
                Slider(
                    value = settings.lyricsBlackTintAlpha.coerceIn(0.15f, 0.80f),
                    onValueChange = { v -> onUpdateSettings { it.copy(lyricsBlackTintAlpha = v) } },
                    valueRange = 0.15f..0.80f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                    ),
                    modifier = Modifier.testTag("settings_slider_lyrics_black_tint")
                )
            }

            // 3. 9-Orb Background Animation Speed (0.0x - 3.0x)
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("9-Orb Background Drift Speed", style = MaterialTheme.typography.labelMedium, color = Color.White)
                    Text(
                        String.format(Locale.US, "%.1fx", settings.bgAnimationSpeed),
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 12.sp,
                        color = MonoSilver
                    )
                }
                Slider(
                    value = settings.bgAnimationSpeed.coerceIn(0f, 3f),
                    onValueChange = { v -> onUpdateSettings { it.copy(bgAnimationSpeed = v) } },
                    valueRange = 0f..3f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                    ),
                    modifier = Modifier.testTag("settings_slider_orb_speed")
                )
            }

            // 4. Word Glow Intensity (0.0x - 2.0x)
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Active Word Glow Intensity", style = MaterialTheme.typography.labelMedium, color = Color.White)
                    Text(
                        String.format(Locale.US, "%.1fx", settings.lyricsGlowIntensity),
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 12.sp,
                        color = MonoSilver
                    )
                }
                Slider(
                    value = settings.lyricsGlowIntensity.coerceIn(0f, 2f),
                    onValueChange = { v -> onUpdateSettings { it.copy(lyricsGlowIntensity = v) } },
                    valueRange = 0f..2f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                    ),
                    modifier = Modifier.testTag("settings_slider_word_glow")
                )
            }
        }
    }
}

/**
 * Interactive Spicy Lyrics Song Search Modal (`/v1/tracks/search`):
 * - Pre-fills with the current song's Title & Artist so you can search in 1 tap when automatic lookup needs manual override.
 * - Formats search results as rich song cards with Album Art (`artworkUrl`), Full Title, Artist, Album, Duration, and Match Tier.
 * - Clicking a result fetches across all linked IDs (`spotify`, `apple_music`, `isrc`, `variants`) and applies the best sync (`Syllable > Word > Line > No Sync`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpicyLyricsSearchModalSheet(
    currentTrack: TrackEntity?,
    secretKey: String,
    searchResults: List<SpicyTrackSearchResult>,
    isSearching: Boolean,
    searchErrorMessage: String?,
    onExecuteSearch: (title: String, artist: String, freeQuery: String) -> Unit,
    onSelectSearchResult: (SpicyTrackSearchResult) -> Unit,
    onOpenSettingsForKey: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var titleField by remember(currentTrack?.id) { mutableStateOf(currentTrack?.title ?: "") }
    var artistField by remember(currentTrack?.id) {
        mutableStateOf(
            currentTrack?.artist?.takeUnless { it.equals("Unknown Artist", ignoreCase = true) } ?: ""
        )
    }
    var freeQueryField by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        scrimColor = Color.Black.copy(alpha = 0.88f),
        dragHandle = null
    ) {
        FrostedGlassSurface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            elevated = true,
            accentHighlight = true,
            opaqueModal = true,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
                .padding(horizontal = 6.dp)
                .testTag("spicy_lyrics_search_modal")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SEARCH SPICY LYRICS",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Text(
                            text = "Priority: Syllable > Word > Line > No Sync across Spotify, Apple Music & ISRC",
                            style = MaterialTheme.typography.bodySmall,
                            color = MonoSilver
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                if (!SpicyLyricsClient.isSecretKeyConfigured(secretKey)) {
                    FrostedGlassSurface(
                        shape = RoundedCornerShape(14.dp),
                        accentHighlight = true,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Secret Key Required (\$SPICY_LYRICS_SECRET_KEY)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color.White
                                )
                                Text(
                                    text = "Enter your sl_sk_... key in Settings to enable Spicy Lyrics search & auto-fetch.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MonoSilver
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    onDismiss()
                                    onOpenSettingsForKey()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Black
                                ),
                                modifier = Modifier.testTag("search_modal_open_key_settings_button")
                            ) {
                                Text("Enter Key")
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = titleField,
                        onValueChange = { titleField = it },
                        label = { Text("Song Title") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("spicy_search_title_input")
                    )

                    OutlinedTextField(
                        value = artistField,
                        onValueChange = { artistField = it },
                        label = { Text("Artist") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("spicy_search_artist_input")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = freeQueryField,
                        onValueChange = { freeQueryField = it },
                        placeholder = {
                            Text(
                                "Or free-text search (?q=...)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MonoSilver
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("spicy_search_free_query_input")
                    )

                    Button(
                        onClick = { onExecuteSearch(titleField, artistField, freeQueryField) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.testTag("spicy_execute_search_button")
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Search")
                    }
                }

                if (searchErrorMessage != null) {
                    Text(
                        text = searchErrorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFF87171)
                    )
                }

                if (isSearching) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.White)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag("spicy_search_results_list")
                    ) {
                        items(searchResults, key = { it.id }) { item ->
                            SpicySearchResultRowCard(
                                result = item,
                                onClick = { onSelectSearchResult(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpicySearchResultRowCard(
    result: SpicyTrackSearchResult,
    onClick: () -> Unit
) {
    val candidateIds = result.allCandidateIds()
    val servicesSummary = buildList {
        if (!result.ids.spotify.isNullOrBlank()) add("Spotify")
        if (!result.ids.appleMusic.isNullOrBlank()) add("Apple Music")
        if (!result.ids.isrc.isNullOrBlank()) add("ISRC")
        if (result.variants.isNotEmpty()) add("+${result.variants.size} variants")
    }.joinToString(" • ")

    FrostedGlassSurface(
        shape = RoundedCornerShape(16.dp),
        elevated = true,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("spicy_search_result_${result.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            RemoteUrlImage(
                imageUrl = result.artworkUrl,
                contentDescription = "${result.title} album art",
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.10f))
            ) {
                Icon(
                    imageVector = Icons.Default.Album,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = result.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.16f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = result.tier.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }

                Text(
                    text = "${result.artistDisplay} • ${result.album}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MonoSilver,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "${formatDurationMs((result.durationSeconds * 1000).toLong())} • Checks ${candidateIds.size} IDs ($servicesSummary)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
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
                text = "5. CUSTOM TYPOGRAPHY (.TTF / .OTF UPLOAD)",
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
                        text = uiFontName ?: "Default (Outfit / Plus Jakarta Sans Bold)",
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
                        text = lyricsFontName ?: (uiFontName ?: "Default (Outfit ExtraBold)"),
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
