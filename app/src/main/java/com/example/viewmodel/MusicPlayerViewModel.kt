package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.audio.DirectoryScanner
import com.example.audio.MusicAudioEngine
import com.example.data.AlbumSummary
import com.example.data.AppDatabase
import com.example.data.ArtistSummary
import com.example.data.CategorySubTab
import com.example.data.FolderSummary
import com.example.data.MainNavTab
import com.example.data.PlaylistEntity
import com.example.data.QueueItem
import com.example.data.SortOption
import com.example.data.TrackEntity
import com.example.lyrics.LyricsAnimatorEngine
import com.example.lyrics.LyricsAttributionInfo
import com.example.lyrics.SpicyLyricsClient
import com.example.lyrics.SpicyTrackSearchResult
import com.example.lyrics.SyllableLineModel
import com.example.lyrics.TtmlParser
import com.example.ui.theme.CustomFontManager
import com.example.ui.theme.VisualThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.math.abs

data class PlayerSettingsState(
    // Theme Mode & Per-Theme Customizations
    val themeMode: VisualThemeMode = VisualThemeMode.MATERIAL_YOU,
    val bleedAlbumArtIntoSongList: Boolean = false,
    // Material You Dynamic Theme Customizations
    val materialYouAccentBoost: Float = 0.65f,
    val materialYouSurfaceRichness: Float = 0.12f,
    // True Pitch-Black Monochrome Customizations
    val oledPitchBlack: Boolean = true,
    val monoBorderContrast: Float = 0.38f,
    val monoKeepLyricsColored: Boolean = true,
    // Frosted Glass Aura & Lyrics Screen Background Settings
    val glassBlurRadiusDp: Float = 68f,        // 0dp to 120dp
    val glassFrostOpacity: Float = 0.88f,      // 0.05 to 0.98
    val glassSpecularIntensity: Float = 0.42f, // 0.0 to 1.0
    val bgAnimationSpeed: Float = 1.0f,        // 0.0x to 3.0x
    val lyricsBlackTintAlpha: Float = 0.34f,   // 0.15 to 0.80 protective black tint on Lyrics blur
    val lyricsOrbSaturation: Float = 1.0f,     // 0.5 to 1.5
    val theatreFullscreenMode: Boolean = false,
    // Spicy Lyrics Secret Key ($SPICY_LYRICS_SECRET_KEY)
    val spicyLyricsSecretKey: String = SpicyLyricsClient.DEFAULT_SECRET_KEY_PLACEHOLDER,
    // Custom Uploaded Fonts (.ttf / .otf) for UI and Lyrics
    val customUiFontPath: String? = null,
    val customUiFontName: String? = null,
    val customLyricsFontPath: String? = null,
    val customLyricsFontName: String? = null,
    // Lyrics & Animator Settings
    val globalLyricsOffsetMs: Int = 0,         // -5000ms to +5000ms
    val lyricsFontSizeSp: Float = 26f,         // 16sp to 42sp
    val lyricsGlowIntensity: Float = 1.0f,     // 0.0f to 2.0f
    val simpleLyricsMode: Boolean = false,
    val refreshedSimpleLyrics: Boolean = true,
    val autoScrollLyrics: Boolean = true,
    // Playback Behavior Settings
    val resetSpeedPitchOnTrackChange: Boolean = false,
    val gaplessPlayback: Boolean = true,
    val crossfadeDurationSec: Int = 2,         // 0s to 12s
    val replayGainEnabled: Boolean = true
)

data class MusicUiState(
    val allTracks: List<TrackEntity> = emptyList(),
    val playlists: List<PlaylistEntity> = emptyList(),
    val mainTab: MainNavTab = MainNavTab.ALL_SONGS,
    val categorySubTab: CategorySubTab = CategorySubTab.ALBUMS,
    val searchQuery: String = "",
    val sortOption: SortOption = SortOption.TITLE,
    val selectedAlbumTitle: String? = null,
    val selectedFolderPath: String? = null,
    val selectedArtistName: String? = null,
    val selectedPlaylistId: String? = null,
    // Queue State Machine
    val queue: List<QueueItem> = emptyList(),
    val currentQueueIndex: Int = -1,
    val queueContextLabel: String = "All Songs",
    // Playback & Granular Speed/Pitch (0.1x - 5.0x)
    val isPlaying: Boolean = false,
    val currentPositionMs: Double = 0.0,
    val durationMs: Long = 1L,
    val playbackSpeed: Float = 1.0f,
    val playbackPitch: Float = 1.0f,
    // Synced Lyrics, Attribution & Spicy Lyrics Search
    val parsedLyricsLines: List<SyllableLineModel> = emptyList(),
    val activeLyricsLineIndex: Int = -1,
    val hasCoLocatedTtml: Boolean = false,
    val coLocatedTtmlFileName: String? = null,
    val lyricsAttribution: LyricsAttributionInfo? = null,
    val isFetchingSpicyLyrics: Boolean = false,
    val isSpicySearchModalOpen: Boolean = false,
    val spicySearchResults: List<SpicyTrackSearchResult> = emptyList(),
    val isSpicySearching: Boolean = false,
    val spicySearchError: String? = null,
    // Modals / Drawers
    val isLyricsExpanded: Boolean = false,
    val isQueueDrawerOpen: Boolean = false,
    val isSpeedPitchSheetOpen: Boolean = false,
    val isSettingsOpen: Boolean = false,
    val isGlassCustomizerOpen: Boolean = false,
    val statusBannerMessage: String? = null,
    val settings: PlayerSettingsState = PlayerSettingsState()
) {
    val currentTrack: TrackEntity?
        get() = queue.getOrNull(currentQueueIndex)?.track ?: allTracks.firstOrNull()

    val filteredAndSortedTracks: List<TrackEntity>
        get() {
            val q = searchQuery.trim().lowercase()
            val filtered = if (q.isEmpty()) {
                allTracks
            } else {
                allTracks.filter {
                    it.title.lowercase().contains(q) ||
                        it.artist.lowercase().contains(q) ||
                        it.album.lowercase().contains(q) ||
                        it.folderPath.lowercase().contains(q)
                }
            }
            return when (sortOption) {
                SortOption.TITLE -> filtered.sortedBy { it.title.lowercase() }
                SortOption.ARTIST -> filtered.sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
                SortOption.ALBUM -> filtered.sortedWith(compareBy({ it.album.lowercase() }, { it.trackNumber }))
                SortOption.TRACK_NUMBER -> filtered.sortedBy { it.trackNumber }
                SortOption.DURATION -> filtered.sortedByDescending { it.durationMs }
                SortOption.FOLDER -> filtered.sortedWith(compareBy({ it.folderPath.lowercase() }, { it.trackNumber }))
            }
        }

    val albums: List<AlbumSummary>
        get() = allTracks.groupBy { it.album }
            .map { (albumTitle, tracks) ->
                val sorted = tracks.sortedBy { it.trackNumber }
                val first = sorted.first()
                AlbumSummary(
                    albumTitle = albumTitle,
                    artist = first.artist,
                    tracks = sorted,
                    palette = first.parsePalette(),
                    coverArtPath = first.coverArtPath
                )
            }
            .sortedBy { it.albumTitle.lowercase() }

    val folders: List<FolderSummary>
        get() = allTracks.groupBy { it.folderPath }
            .map { (path, tracks) ->
                val sorted = tracks.sortedBy { it.trackNumber }
                val first = sorted.first()
                FolderSummary(
                    folderPath = path,
                    folderName = first.folderName,
                    parentTreeUri = first.parentTreeUri,
                    tracks = sorted,
                    ttmlCount = sorted.count { it.hasAnySyncedLyrics }
                )
            }
            .sortedBy { it.folderPath.lowercase() }

    val artists: List<ArtistSummary>
        get() = allTracks.groupBy { it.artist }
            .map { (artistName, tracks) ->
                val sorted = tracks.sortedWith(compareBy({ it.album.lowercase() }, { it.trackNumber }))
                ArtistSummary(
                    artistName = artistName,
                    tracks = sorted,
                    albumCount = sorted.map { it.album }.distinct().size,
                    palette = sorted.first().parsePalette()
                )
            }
            .sortedBy { it.artistName.lowercase() }
}

class MusicPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val dao = db.musicDao()
    private val prefs = application.getSharedPreferences("monolith_prefs", Context.MODE_PRIVATE)
    private val audioEngine = MusicAudioEngine(application)
    val lyricsAnimator = LyricsAnimatorEngine()

    private var autoLyricsFetchJob: Job? = null

    private val embeddedLyricsCacheDir: File by lazy {
        File(application.filesDir, "embedded_lyrics_cache").apply { mkdirs() }
    }

    private val _uiState = MutableStateFlow(MusicUiState(settings = loadSettingsFromPrefs()))
    val uiState: StateFlow<MusicUiState> = combine(
        _uiState,
        dao.observeAllTracks(),
        dao.observePlaylists()
    ) { state, tracks, playlists ->
        val trackById = tracks.associateBy { it.id }
        val syncedQueue = state.queue.mapNotNull { item ->
            val updated = trackById[item.track.id]
            if (updated != null) item.copy(track = updated) else if (!item.track.isBuiltInStudioTrack) item else null
        }
        state.copy(
            allTracks = tracks,
            playlists = playlists,
            queue = syncedQueue
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = _uiState.value
    )

    init {
        audioEngine.setOnTrackEndedListener {
            viewModelScope.launch {
                skipToNext(autoAdvance = true)
            }
        }
        purgeBuiltInTracksAndInitializeUserLibrary()
    }

    private fun purgeBuiltInTracksAndInitializeUserLibrary() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteBuiltInStudioTracks()
            dao.deletePlaylist("playlist_favorites")
            try {
                File(getApplication<Application>().filesDir, "LocalMusic").deleteRecursively()
            } catch (_: Exception) {
            }

            var userTracks = dao.getAllTracksOnce()
            if (userTracks.isEmpty()) {
                val savedUris = prefs.getStringSet("saved_saf_tree_uris", emptySet()) ?: emptySet()
                val rescanned = DirectoryScanner.rescanAllSourcesStreaming(
                    context = getApplication(),
                    savedTreeUris = savedUris,
                    onBatchReady = { batch, _, _ ->
                        dao.insertTracks(batch)
                    }
                )
                if (rescanned.isNotEmpty()) {
                    userTracks = dao.getAllTracksOnce()
                }
            }

            withContext(Dispatchers.Main) {
                if (_uiState.value.queue.isEmpty() && userTracks.isNotEmpty()) {
                    val sorted = userTracks.sortedBy { it.title.lowercase() }
                    val queueItems = sorted.map { QueueItem(UUID.randomUUID().toString(), it) }
                    val first = sorted.first()
                    val parsedLyrics = loadLocalOrCachedLyricsForTrack(first)
                    _uiState.update {
                        it.copy(
                            queue = queueItems,
                            currentQueueIndex = 0,
                            queueContextLabel = "All Songs",
                            durationMs = first.durationMs,
                            parsedLyricsLines = parsedLyrics.lines,
                            hasCoLocatedTtml = parsedLyrics.hasCoLocated,
                            coLocatedTtmlFileName = parsedLyrics.fileName,
                            lyricsAttribution = parsedLyrics.attribution
                        )
                    }
                    audioEngine.loadTrack(
                        track = first,
                        playImmediately = false,
                        speed = _uiState.value.playbackSpeed,
                        pitch = _uiState.value.playbackPitch,
                        replayGainEnabled = _uiState.value.settings.replayGainEnabled
                    )
                    if (!parsedLyrics.hasCoLocated) {
                        triggerAutoSpicyLyricsIfNeeded(first)
                    }
                }
            }
        }
    }

    // ========================================================================
    // 1. STRICT QUEUE STATE MACHINE (Play Now Hard Reset vs Add to Queue @ currentIndex + 1)
    // ========================================================================

    fun playSongFromContext(
        contextTracks: List<TrackEntity>,
        clickedTrack: TrackEntity,
        contextLabel: String
    ) {
        if (contextTracks.isEmpty()) return
        val newQueue = contextTracks.map { QueueItem(UUID.randomUUID().toString(), it) }
        val startIdx = contextTracks.indexOfFirst { it.id == clickedTrack.id }.coerceAtLeast(0)
        val activeTrack = newQueue[startIdx].track

        val resetRate = _uiState.value.settings.resetSpeedPitchOnTrackChange
        val nextSpeed = if (resetRate) 1.0f else _uiState.value.playbackSpeed
        val nextPitch = if (resetRate) 1.0f else _uiState.value.playbackPitch

        val parsedLyrics = loadLocalOrCachedLyricsForTrack(activeTrack)

        _uiState.update {
            it.copy(
                queue = newQueue,
                currentQueueIndex = startIdx,
                queueContextLabel = contextLabel,
                isPlaying = true,
                currentPositionMs = 0.0,
                durationMs = activeTrack.durationMs,
                playbackSpeed = nextSpeed,
                playbackPitch = nextPitch,
                parsedLyricsLines = parsedLyrics.lines,
                activeLyricsLineIndex = -1,
                hasCoLocatedTtml = parsedLyrics.hasCoLocated,
                coLocatedTtmlFileName = parsedLyrics.fileName,
                lyricsAttribution = parsedLyrics.attribution,
                statusBannerMessage = "Playing from $contextLabel (${newQueue.size} tracks)"
            )
        }

        audioEngine.loadTrack(
            track = activeTrack,
            playImmediately = true,
            speed = nextSpeed,
            pitch = nextPitch,
            replayGainEnabled = _uiState.value.settings.replayGainEnabled
        )

        if (!parsedLyrics.hasCoLocated) {
            triggerAutoSpicyLyricsIfNeeded(activeTrack)
        }
    }

    fun addToQueueNext(track: TrackEntity) {
        _uiState.update { state ->
            val currentList = state.queue.toMutableList()
            val newItem = QueueItem(UUID.randomUUID().toString(), track)
            if (currentList.isEmpty() || state.currentQueueIndex < 0) {
                val parsed = loadLocalOrCachedLyricsForTrack(track)
                audioEngine.loadTrack(
                    track = track,
                    playImmediately = false,
                    speed = state.playbackSpeed,
                    pitch = state.playbackPitch,
                    replayGainEnabled = state.settings.replayGainEnabled
                )
                if (!parsed.hasCoLocated) {
                    triggerAutoSpicyLyricsIfNeeded(track)
                }
                state.copy(
                    queue = listOf(newItem),
                    currentQueueIndex = 0,
                    durationMs = track.durationMs,
                    parsedLyricsLines = parsed.lines,
                    hasCoLocatedTtml = parsed.hasCoLocated,
                    coLocatedTtmlFileName = parsed.fileName,
                    lyricsAttribution = parsed.attribution,
                    statusBannerMessage = "Added \"${track.title}\" to Queue (#1)"
                )
            } else {
                val insertIndex = (state.currentQueueIndex + 1).coerceIn(0, currentList.size)
                currentList.add(insertIndex, newItem)
                state.copy(
                    queue = currentList,
                    statusBannerMessage = "Queued \"${track.title}\" next at Position #${insertIndex + 1}"
                )
            }
        }
    }

    fun jumpToQueueIndex(index: Int) {
        val state = _uiState.value
        val item = state.queue.getOrNull(index) ?: return
        val resetRate = state.settings.resetSpeedPitchOnTrackChange
        val nextSpeed = if (resetRate) 1.0f else state.playbackSpeed
        val nextPitch = if (resetRate) 1.0f else state.playbackPitch
        val parsed = loadLocalOrCachedLyricsForTrack(item.track)

        _uiState.update {
            it.copy(
                currentQueueIndex = index,
                isPlaying = true,
                currentPositionMs = 0.0,
                durationMs = item.track.durationMs,
                playbackSpeed = nextSpeed,
                playbackPitch = nextPitch,
                parsedLyricsLines = parsed.lines,
                activeLyricsLineIndex = -1,
                hasCoLocatedTtml = parsed.hasCoLocated,
                coLocatedTtmlFileName = parsed.fileName,
                lyricsAttribution = parsed.attribution
            )
        }
        audioEngine.loadTrack(
            track = item.track,
            playImmediately = true,
            speed = nextSpeed,
            pitch = nextPitch,
            replayGainEnabled = state.settings.replayGainEnabled
        )
        if (!parsed.hasCoLocated) {
            triggerAutoSpicyLyricsIfNeeded(item.track)
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        _uiState.update { state ->
            val list = state.queue.toMutableList()
            if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) {
                return@update state
            }
            val moved = list.removeAt(fromIndex)
            list.add(toIndex, moved)
            val newActiveIndex = when {
                state.currentQueueIndex == fromIndex -> toIndex
                fromIndex < state.currentQueueIndex && toIndex >= state.currentQueueIndex -> state.currentQueueIndex - 1
                fromIndex > state.currentQueueIndex && toIndex <= state.currentQueueIndex -> state.currentQueueIndex + 1
                else -> state.currentQueueIndex
            }
            state.copy(queue = list, currentQueueIndex = newActiveIndex)
        }
    }

    fun removeQueueItem(index: Int) {
        _uiState.update { state ->
            val list = state.queue.toMutableList()
            if (index !in list.indices) return@update state
            list.removeAt(index)
            if (list.isEmpty()) {
                audioEngine.pause()
                return@update state.copy(queue = emptyList(), currentQueueIndex = -1, isPlaying = false)
            }
            val newIndex = when {
                index < state.currentQueueIndex -> state.currentQueueIndex - 1
                index == state.currentQueueIndex -> index.coerceAtMost(list.lastIndex)
                else -> state.currentQueueIndex
            }
            if (index == state.currentQueueIndex) {
                val nextTrack = list[newIndex].track
                val parsed = loadLocalOrCachedLyricsForTrack(nextTrack)
                audioEngine.loadTrack(
                    track = nextTrack,
                    playImmediately = state.isPlaying,
                    speed = state.playbackSpeed,
                    pitch = state.playbackPitch,
                    replayGainEnabled = state.settings.replayGainEnabled
                )
                if (!parsed.hasCoLocated) {
                    triggerAutoSpicyLyricsIfNeeded(nextTrack)
                }
                state.copy(
                    queue = list,
                    currentQueueIndex = newIndex,
                    currentPositionMs = 0.0,
                    durationMs = nextTrack.durationMs,
                    parsedLyricsLines = parsed.lines,
                    hasCoLocatedTtml = parsed.hasCoLocated,
                    coLocatedTtmlFileName = parsed.fileName,
                    lyricsAttribution = parsed.attribution
                )
            } else {
                state.copy(queue = list, currentQueueIndex = newIndex)
            }
        }
    }

    fun clearUpcomingQueue() {
        _uiState.update { state ->
            if (state.currentQueueIndex < 0 || state.queue.isEmpty()) return@update state
            val kept = state.queue.take(state.currentQueueIndex + 1)
            state.copy(
                queue = kept,
                statusBannerMessage = "Cleared upcoming tracks"
            )
        }
    }

    // ========================================================================
    // 2. GRANULAR 0.1x - 5.0x SPEED & PITCH CONTROLS
    // ========================================================================

    fun setPlaybackSpeed(newSpeed: Float) {
        val quantized = MusicAudioEngine.quantizeOneDecimal(newSpeed)
        _uiState.update { it.copy(playbackSpeed = quantized) }
        audioEngine.setSpeedAndPitch(quantized, _uiState.value.playbackPitch)
    }

    fun stepPlaybackSpeed(deltaSteps: Int) {
        val next = MusicAudioEngine.stepOneDecimal(_uiState.value.playbackSpeed, deltaSteps)
        setPlaybackSpeed(next)
    }

    fun resetPlaybackSpeed() {
        setPlaybackSpeed(1.0f)
    }

    fun setPlaybackPitch(newPitch: Float) {
        val quantized = MusicAudioEngine.quantizeOneDecimal(newPitch)
        _uiState.update { it.copy(playbackPitch = quantized) }
        audioEngine.setSpeedAndPitch(_uiState.value.playbackSpeed, quantized)
    }

    fun stepPlaybackPitch(deltaSteps: Int) {
        val next = MusicAudioEngine.stepOneDecimal(_uiState.value.playbackPitch, deltaSteps)
        setPlaybackPitch(next)
    }

    fun resetPlaybackPitch() {
        setPlaybackPitch(1.0f)
    }

    // ========================================================================
    // 3. PLAYBACK TRANSPORT & ZERO-LAG FRAME CLOCK TICK FOR LYRICS ANIMATOR
    // ========================================================================

    fun togglePlayPause() {
        val playing = _uiState.value.isPlaying
        if (playing) {
            audioEngine.pause()
            _uiState.update { it.copy(isPlaying = false) }
        } else {
            audioEngine.play()
            _uiState.update { it.copy(isPlaying = true) }
        }
    }

    fun seekTo(positionMs: Long) {
        audioEngine.seekTo(positionMs)
        _uiState.update { it.copy(currentPositionMs = positionMs.toDouble()) }
        tickFrameClock(System.nanoTime(), forceUiUpdate = true)
    }

    fun skipToNext(autoAdvance: Boolean = false) {
        val state = _uiState.value
        if (state.queue.isEmpty()) return
        val nextIdx = if (state.currentQueueIndex + 1 < state.queue.size) {
            state.currentQueueIndex + 1
        } else {
            0
        }
        if (autoAdvance && nextIdx == 0 && state.queue.size == 1 && !state.settings.gaplessPlayback) {
            audioEngine.pause()
            _uiState.update { it.copy(isPlaying = false, currentPositionMs = 0.0) }
            return
        }
        jumpToQueueIndex(nextIdx)
    }

    fun skipToPrevious() {
        val state = _uiState.value
        if (state.currentPositionMs > 3000.0) {
            seekTo(0L)
            return
        }
        if (state.queue.isEmpty()) return
        val prevIdx = if (state.currentQueueIndex - 1 >= 0) {
            state.currentQueueIndex - 1
        } else {
            state.queue.lastIndex
        }
        jumpToQueueIndex(prevIdx)
    }

    fun tickFrameClock(frameNanos: Long, forceUiUpdate: Boolean = false) {
        val pos = audioEngine.getCurrentPositionMs()
        val dur = audioEngine.getDurationMs().coerceAtLeast(1L)
        val playing = audioEngine.isPlaying()
        val state = _uiState.value

        if (!state.isLyricsExpanded && !forceUiUpdate) {
            // When viewing the Song List or Categories, throttle progress bar updates to ~4Hz (every 240ms)
            // so the library list never suffers 60fps root state churn while scrolling!
            val posDelta = abs(pos - state.currentPositionMs)
            if (posDelta < 240.0 && playing == state.isPlaying && dur == state.durationMs) {
                return
            }
            _uiState.update {
                it.copy(
                    currentPositionMs = pos,
                    durationMs = dur,
                    isPlaying = playing
                )
            }
            return
        }

        val trackOffset = state.currentTrack?.lyricsOffsetMs ?: 0
        val totalOffset = (state.settings.globalLyricsOffsetMs + trackOffset).toDouble()

        val activeLine = if (state.parsedLyricsLines.isNotEmpty()) {
            lyricsAnimator.animate(
                lines = state.parsedLyricsLines,
                rawPositionMs = pos,
                timeOffsetMs = totalOffset,
                simpleLyricsMode = state.settings.simpleLyricsMode,
                refreshedSimpleLyrics = state.settings.refreshedSimpleLyrics,
                glowMultiplierUser = state.settings.lyricsGlowIntensity,
                frameNanos = frameNanos
            )
        } else {
            -1
        }

        _uiState.update {
            it.copy(
                currentPositionMs = pos,
                durationMs = dur,
                isPlaying = playing,
                activeLyricsLineIndex = activeLine
            )
        }
    }

    // ========================================================================
    // 4. PERSISTENT EMBEDDED STORAGE LYRICS CACHE + .TTML / .LRC / .TXT SUPPORT
    // ========================================================================

    private data class LoadedLyricsResult(
        val lines: List<SyllableLineModel>,
        val hasCoLocated: Boolean,
        val fileName: String?,
        val attribution: LyricsAttributionInfo?
    )

    private fun embeddedCacheFileForTrack(trackId: String): File {
        val safeName = trackId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(embeddedLyricsCacheDir, "${safeName.takeLast(90)}_${trackId.hashCode()}.json")
    }

    private fun saveToEmbeddedStorageCache(
        trackId: String,
        formatType: String,
        payload: String,
        fileNameLabel: String?,
        attribution: LyricsAttributionInfo?
    ) {
        try {
            val file = embeddedCacheFileForTrack(trackId)
            val obj = JSONObject()
            obj.put("trackId", trackId)
            obj.put("formatType", formatType)
            obj.put("payload", payload)
            obj.put("fileNameLabel", fileNameLabel ?: "")
            obj.put("attributionJson", attribution?.toJsonString() ?: "")
            file.writeText(obj.toString())
        } catch (_: Exception) {
        }
    }

    private fun readFromEmbeddedStorageCache(track: TrackEntity): LoadedLyricsResult? {
        try {
            val file = embeddedCacheFileForTrack(track.id)
            if (!file.exists()) return null
            val obj = JSONObject(file.readText())
            val formatType = obj.optString("formatType", "")
            val payload = obj.optString("payload", "")
            if (payload.isBlank()) return null
            val fileNameLabel = obj.optString("fileNameLabel", "").takeIf { it.isNotBlank() }
            val attrJson = obj.optString("attributionJson", "").takeIf { it.isNotBlank() }
            val savedAttr = LyricsAttributionInfo.fromJsonString(attrJson)

            if (formatType == "spicy_json") {
                val parsed = SpicyLyricsClient.parseLyricsResponseJson(payload, track.id)
                if (parsed != null && parsed.lines.isNotEmpty()) {
                    val attr = savedAttr ?: parsed.attribution
                    return LoadedLyricsResult(
                        lines = parsed.lines,
                        hasCoLocated = true,
                        fileName = fileNameLabel ?: "Cached • ${attr.syncPriority.label}",
                        attribution = attr
                    )
                }
            } else {
                val (parsedLines, syncPriority) = TtmlParser.parseAnyLyricsFormat(payload, track.durationMs)
                if (parsedLines.isNotEmpty()) {
                    val attr = savedAttr
                        ?: TtmlParser.extractAttributionFromTtml(payload)?.copy(syncPriority = syncPriority)
                        ?: LyricsAttributionInfo(syncPriority = syncPriority)
                    return LoadedLyricsResult(
                        lines = parsedLines,
                        hasCoLocated = true,
                        fileName = fileNameLabel ?: "Embedded Cache (${track.baseFileName})",
                        attribution = attr
                    )
                }
            }
        } catch (_: Exception) {
        }
        return null
    }

    private fun loadLocalOrCachedLyricsForTrack(track: TrackEntity): LoadedLyricsResult {
        // 1. Check Persistent Embedded Storage Cache first
        val diskCached = readFromEmbeddedStorageCache(track)
        if (diskCached != null && diskCached.lines.isNotEmpty()) {
            return diskCached
        }

        // 2. Check cached Spicy Lyrics JSON in Room DB
        if (!track.spicyLyricsJson.isNullOrBlank()) {
            val cachedSpicy = SpicyLyricsClient.parseLyricsResponseJson(track.spicyLyricsJson, track.id)
            if (cachedSpicy != null && cachedSpicy.lines.isNotEmpty()) {
                val attr = LyricsAttributionInfo.fromJsonString(track.lyricsAttributionJson)
                    ?: cachedSpicy.attribution
                val label = "Spicy Lyrics • ${attr.syncPriority.label}"
                saveToEmbeddedStorageCache(
                    trackId = track.id,
                    formatType = "spicy_json",
                    payload = track.spicyLyricsJson,
                    fileNameLabel = label,
                    attribution = attr
                )
                return LoadedLyricsResult(
                    lines = cachedSpicy.lines,
                    hasCoLocated = true,
                    fileName = label,
                    attribution = attr
                )
            }
        }

        // 3. Check manual or co-located lyrics file (.ttml, .lrc, or .txt) in the same directory
        var rawLyricsContent: String? = track.manualTtmlContent?.takeIf { it.isNotBlank() }
        var resolvedFileName = "${track.baseFileName}.ttml"

        if (rawLyricsContent == null && track.coLocatedTtmlUri != null) {
            val uriOrPath = track.coLocatedTtmlUri
            resolvedFileName = uriOrPath.substringAfterLast('/')
            rawLyricsContent = if (uriOrPath.startsWith("content://")) {
                DirectoryScanner.readTextFromUri(getApplication(), Uri.parse(uriOrPath))
            } else {
                val f = File(uriOrPath)
                if (f.exists()) f.readText() else null
            }
        }

        if (rawLyricsContent == null && !track.parentTreeUri.startsWith("content://")) {
            val folderDir = File(track.parentTreeUri)
            if (folderDir.exists() && folderDir.isDirectory) {
                val files = folderDir.listFiles().orEmpty()
                // Strict same-song base filename priority: .ttml > .lrc > .txt
                val candidates = listOf(
                    "${track.baseFileName}.ttml",
                    "${track.baseFileName}.lrc",
                    "${track.baseFileName}.txt"
                )
                for (expected in candidates) {
                    val match = files.firstOrNull { it.name.equals(expected, ignoreCase = true) }
                    if (match != null) {
                        rawLyricsContent = match.readText()
                        resolvedFileName = match.name
                        break
                    }
                }
            }
        }

        if (rawLyricsContent == null) {
            rawLyricsContent = track.embeddedTtmlContent
        }

        if (rawLyricsContent.isNullOrBlank()) {
            return LoadedLyricsResult(emptyList(), false, null, null)
        }

        val (parsedLines, syncPriority) = TtmlParser.parseAnyLyricsFormat(rawLyricsContent, track.durationMs)
        val lyricsAttr = LyricsAttributionInfo.fromJsonString(track.lyricsAttributionJson)
            ?: TtmlParser.extractAttributionFromTtml(rawLyricsContent)?.copy(syncPriority = syncPriority)
            ?: LyricsAttributionInfo(syncPriority = syncPriority)
        val label = "${track.folderPath}/$resolvedFileName"

        if (parsedLines.isNotEmpty()) {
            saveToEmbeddedStorageCache(
                trackId = track.id,
                formatType = "raw_lyrics",
                payload = rawLyricsContent,
                fileNameLabel = label,
                attribution = lyricsAttr
            )
        }

        return LoadedLyricsResult(
            lines = parsedLines,
            hasCoLocated = parsedLines.isNotEmpty(),
            fileName = label,
            attribution = lyricsAttr
        )
    }

    /**
     * Clears the persistent embedded lyrics cache for the currently active track only.
     */
    fun clearCurrentTrackLyricsCache() {
        val current = _uiState.value.currentTrack ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                embeddedCacheFileForTrack(current.id).delete()
            } catch (_: Exception) {
            }
            dao.clearTrackLyricsCache(current.id)
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        parsedLyricsLines = emptyList(),
                        activeLyricsLineIndex = -1,
                        hasCoLocatedTtml = false,
                        coLocatedTtmlFileName = null,
                        lyricsAttribution = null,
                        statusBannerMessage = "Cleared cached lyrics for \"${current.title}\""
                    )
                }
            }
        }
    }

    /**
     * Clears the persistent embedded lyrics cache for ALL songs in storage and Room DB.
     */
    fun clearAllLyricsCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                embeddedLyricsCacheDir.listFiles()?.forEach { it.delete() }
            } catch (_: Exception) {
            }
            dao.clearAllLyricsCache()
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        parsedLyricsLines = emptyList(),
                        activeLyricsLineIndex = -1,
                        hasCoLocatedTtml = false,
                        coLocatedTtmlFileName = null,
                        lyricsAttribution = null,
                        statusBannerMessage = "Cleared all cached lyrics from storage"
                    )
                }
            }
        }
    }

    /**
     * Automatically attempts to fetch synced lyrics from `api.spicylyrics.org` using the song's
     * own Title, Artist, Album, and Duration without requiring manual search.
     * Enforces strict same-song matching so another song's TTML never overrides the actual song's LRC/Line or Plain Text.
     */
    private fun triggerAutoSpicyLyricsIfNeeded(track: TrackEntity) {
        val secretKey = _uiState.value.settings.spicyLyricsSecretKey
        if (!SpicyLyricsClient.isSecretKeyConfigured(secretKey)) return

        autoLyricsFetchJob?.cancel()
        autoLyricsFetchJob = viewModelScope.launch {
            _uiState.update { it.copy(isFetchingSpicyLyrics = true) }
            val result = SpicyLyricsClient.autoFetchBestLyricsForTrack(
                secretKey = secretKey,
                rawTitle = track.title,
                rawArtist = track.artist,
                rawAlbum = track.album,
                durationMs = track.durationMs
            )

            result.onSuccess { fetched ->
                val attrJson = fetched.attribution.toJsonString()
                val label = "Spicy Lyrics • ${fetched.attribution.syncPriority.label}"
                withContext(Dispatchers.IO) {
                    dao.updateSpicyLyrics(track.id, fetched.rawJsonPayload, attrJson)
                    saveToEmbeddedStorageCache(
                        trackId = track.id,
                        formatType = "spicy_json",
                        payload = fetched.rawJsonPayload,
                        fileNameLabel = label,
                        attribution = fetched.attribution
                    )
                }
                val currentId = _uiState.value.currentTrack?.id
                if (currentId == track.id) {
                    _uiState.update {
                        it.copy(
                            parsedLyricsLines = fetched.lines,
                            hasCoLocatedTtml = fetched.lines.isNotEmpty(),
                            coLocatedTtmlFileName = label,
                            lyricsAttribution = fetched.attribution,
                            isFetchingSpicyLyrics = false,
                            statusBannerMessage = "Cached ${fetched.attribution.syncPriority.label} for \"${track.title}\""
                        )
                    }
                } else {
                    _uiState.update { it.copy(isFetchingSpicyLyrics = false) }
                }
            }.onFailure {
                _uiState.update { it.copy(isFetchingSpicyLyrics = false) }
            }
        }
    }

    fun saveSpicySecretKey(newKey: String) {
        val cleaned = newKey.trim()
        val effective = if (cleaned.isEmpty()) {
            SpicyLyricsClient.DEFAULT_SECRET_KEY_PLACEHOLDER
        } else {
            cleaned
        }
        updateSettings { it.copy(spicyLyricsSecretKey = effective) }
        _uiState.update {
            it.copy(statusBannerMessage = "Spicy Lyrics Secret Key applied to Node.js fetch engine")
        }
        val active = _uiState.value.currentTrack
        if (active != null && !_uiState.value.hasCoLocatedTtml) {
            triggerAutoSpicyLyricsIfNeeded(active)
        }
    }

    fun revokeSpicySecretKey() {
        updateSettings {
            it.copy(spicyLyricsSecretKey = SpicyLyricsClient.DEFAULT_SECRET_KEY_PLACEHOLDER)
        }
        _uiState.update {
            it.copy(
                statusBannerMessage = "Revoked Secret Key • Reset to ${SpicyLyricsClient.DEFAULT_SECRET_KEY_PLACEHOLDER}"
            )
        }
    }

    fun setSpicySearchModalOpen(open: Boolean) {
        _uiState.update { it.copy(isSpicySearchModalOpen = open, spicySearchError = null) }
        if (open) {
            val current = _uiState.value.currentTrack
            val secretKey = _uiState.value.settings.spicyLyricsSecretKey
            if (current != null && SpicyLyricsClient.isSecretKeyConfigured(secretKey) && _uiState.value.spicySearchResults.isEmpty()) {
                executeSpicySearch(
                    title = current.title,
                    artist = current.artist.takeUnless { it.equals("Unknown Artist", ignoreCase = true) } ?: "",
                    freeQuery = ""
                )
            }
        }
    }

    fun executeSpicySearch(title: String, artist: String, freeQuery: String) {
        val secretKey = _uiState.value.settings.spicyLyricsSecretKey
        val durationSec = _uiState.value.currentTrack?.durationMs?.takeIf { it > 1000L }?.let { it / 1000.0 }
        viewModelScope.launch {
            _uiState.update { it.copy(isSpicySearching = true, spicySearchError = null) }
            val res = SpicyLyricsClient.searchTracks(
                secretKey = secretKey,
                title = title.takeIf { it.isNotBlank() },
                artist = artist.takeIf { it.isNotBlank() },
                durationSeconds = durationSec,
                freeQuery = freeQuery.takeIf { it.isNotBlank() }
            )
            res.onSuccess { list ->
                _uiState.update {
                    it.copy(
                        spicySearchResults = list,
                        isSpicySearching = false,
                        spicySearchError = if (list.isEmpty()) "No matching tracks found on Spicy Lyrics." else null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSpicySearching = false,
                        spicySearchError = err.message ?: "Search failed"
                    )
                }
            }
        }
    }

    fun selectSpicySearchResult(result: SpicyTrackSearchResult) {
        val secretKey = _uiState.value.settings.spicyLyricsSecretKey
        val activeTrack = _uiState.value.currentTrack
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSpicySearching = true,
                    spicySearchError = null,
                    statusBannerMessage = "Checking ${result.allCandidateIds().size} IDs (Spotify, Apple Music, ISRC)..."
                )
            }
            val bestRes = SpicyLyricsClient.fetchBestLyricsAcrossIds(
                secretKey = secretKey,
                candidateIds = result.allCandidateIds()
            )
            bestRes.onSuccess { fetched ->
                val label = "Spicy Lyrics • ${fetched.attribution.syncPriority.label} (${fetched.trackId})"
                if (activeTrack != null) {
                    withContext(Dispatchers.IO) {
                        dao.updateSpicyLyrics(
                            trackId = activeTrack.id,
                            spicyJson = fetched.rawJsonPayload,
                            attributionJson = fetched.attribution.toJsonString()
                        )
                        saveToEmbeddedStorageCache(
                            trackId = activeTrack.id,
                            formatType = "spicy_json",
                            payload = fetched.rawJsonPayload,
                            fileNameLabel = label,
                            attribution = fetched.attribution
                        )
                    }
                }
                _uiState.update {
                    it.copy(
                        parsedLyricsLines = fetched.lines,
                        hasCoLocatedTtml = fetched.lines.isNotEmpty(),
                        coLocatedTtmlFileName = label,
                        lyricsAttribution = fetched.attribution,
                        isSpicySearching = false,
                        isSpicySearchModalOpen = false,
                        isLyricsExpanded = true,
                        statusBannerMessage = "Cached ${fetched.attribution.syncPriority.label} for \"${result.title}\""
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSpicySearching = false,
                        spicySearchError = err.message ?: "No synced lyrics found across this track's IDs."
                    )
                }
            }
        }
    }

    fun fetchSpicyLyricsByDirectId(trackId: String) {
        val cleanId = trackId.trim()
        if (cleanId.isEmpty()) return
        val secretKey = _uiState.value.settings.spicyLyricsSecretKey
        val activeTrack = _uiState.value.currentTrack
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isFetchingSpicyLyrics = true,
                    statusBannerMessage = "Fetching Spicy Lyrics for ID: $cleanId..."
                )
            }
            val res = SpicyLyricsClient.fetchBestLyricsAcrossIds(
                secretKey = secretKey,
                candidateIds = listOf(cleanId)
            )
            res.onSuccess { fetched ->
                val label = "Spicy Lyrics • ${fetched.attribution.syncPriority.label} ($cleanId)"
                if (activeTrack != null) {
                    withContext(Dispatchers.IO) {
                        dao.updateSpicyLyrics(
                            trackId = activeTrack.id,
                            spicyJson = fetched.rawJsonPayload,
                            attributionJson = fetched.attribution.toJsonString()
                        )
                        saveToEmbeddedStorageCache(
                            trackId = activeTrack.id,
                            formatType = "spicy_json",
                            payload = fetched.rawJsonPayload,
                            fileNameLabel = label,
                            attribution = fetched.attribution
                        )
                    }
                }
                _uiState.update {
                    it.copy(
                        parsedLyricsLines = fetched.lines,
                        hasCoLocatedTtml = fetched.lines.isNotEmpty(),
                        coLocatedTtmlFileName = label,
                        lyricsAttribution = fetched.attribution,
                        isFetchingSpicyLyrics = false,
                        isSettingsOpen = false,
                        isLyricsExpanded = true,
                        statusBannerMessage = "Cached ${fetched.attribution.syncPriority.label} ($cleanId)"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isFetchingSpicyLyrics = false,
                        statusBannerMessage = err.message ?: "Failed to fetch ID $cleanId"
                    )
                }
            }
        }
    }

    fun loadManualTtmlUriForCurrentTrack(uri: Uri) {
        val current = _uiState.value.currentTrack ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val text = DirectoryScanner.readTextFromUri(getApplication(), uri)
            if (!text.isNullOrBlank()) {
                val hintName = uri.lastPathSegment ?: "${current.baseFileName}.ttml"
                val (parsedLines, syncPriority) = TtmlParser.parseAnyLyricsFormat(text, current.durationMs)
                val attr = TtmlParser.extractAttributionFromTtml(text)?.copy(syncPriority = syncPriority)
                    ?: LyricsAttributionInfo(syncPriority = syncPriority)
                val label = "Manual: ${hintName.substringAfterLast('/')}"
                dao.updateManualTtml(current.id, text, attr.toJsonString())
                saveToEmbeddedStorageCache(
                    trackId = current.id,
                    formatType = "raw_lyrics",
                    payload = text,
                    fileNameLabel = label,
                    attribution = attr
                )
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            parsedLyricsLines = parsedLines,
                            hasCoLocatedTtml = parsedLines.isNotEmpty(),
                            coLocatedTtmlFileName = label,
                            lyricsAttribution = attr,
                            statusBannerMessage = "Cached manual lyrics (${parsedLines.size} lines)"
                        )
                    }
                }
            }
        }
    }

    // ========================================================================
    // 5. CUSTOM FONT UPLOAD (.TTF / .OTF) FOR BOTH UI AND LYRICS
    // ========================================================================

    fun uploadCustomUiFont(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = CustomFontManager.copyAndValidateFont(getApplication(), uri, "ui")
            withContext(Dispatchers.Main) {
                if (result != null) {
                    updateSettings {
                        it.copy(
                            customUiFontPath = result.filePath,
                            customUiFontName = result.displayName
                        )
                    }
                    _uiState.update {
                        it.copy(statusBannerMessage = "Applied custom UI font: ${result.displayName}")
                    }
                } else {
                    _uiState.update {
                        it.copy(statusBannerMessage = "Invalid font file. Please select a valid .ttf or .otf file.")
                    }
                }
            }
        }
    }

    fun resetCustomUiFont() {
        updateSettings {
            it.copy(customUiFontPath = null, customUiFontName = null)
        }
        _uiState.update { it.copy(statusBannerMessage = "Reset UI font to default") }
    }

    fun uploadCustomLyricsFont(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = CustomFontManager.copyAndValidateFont(getApplication(), uri, "lyrics")
            withContext(Dispatchers.Main) {
                if (result != null) {
                    updateSettings {
                        it.copy(
                            customLyricsFontPath = result.filePath,
                            customLyricsFontName = result.displayName
                        )
                    }
                    _uiState.update {
                        it.copy(statusBannerMessage = "Applied custom Lyrics font: ${result.displayName}")
                    }
                } else {
                    _uiState.update {
                        it.copy(statusBannerMessage = "Invalid font file. Please select a valid .ttf or .otf file.")
                    }
                }
            }
        }
    }

    fun resetCustomLyricsFont() {
        updateSettings {
            it.copy(customLyricsFontPath = null, customLyricsFontName = null)
        }
        _uiState.update { it.copy(statusBannerMessage = "Reset Lyrics font to default") }
    }

    // ========================================================================
    // 6. FAST STREAMING DIRECTORY SCANNING, RESCAN & PLAYLIST MANAGEMENT
    // ========================================================================

    fun scanSafDirectoryTree(treeUri: Uri) {
        val currentSaved = (prefs.getStringSet("saved_saf_tree_uris", emptySet()) ?: emptySet()).toMutableSet()
        currentSaved.add(treeUri.toString())
        prefs.edit().putStringSet("saved_saf_tree_uris", currentSaved).apply()

        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(statusBannerMessage = "Scanning folder...") }
            }
            val scanned = DirectoryScanner.scanDirectoryTreeStreaming(
                context = getApplication(),
                treeUri = treeUri,
                onBatchReady = { batch, completed, total ->
                    dao.insertTracks(batch)
                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(statusBannerMessage = "Loaded $completed / $total tracks...")
                        }
                    }
                }
            )
            withContext(Dispatchers.Main) {
                if (scanned.isNotEmpty()) {
                    if (_uiState.value.queue.isEmpty()) {
                        playSongFromContext(scanned, scanned.first(), "Imported Folder")
                    } else {
                        _uiState.update {
                            it.copy(statusBannerMessage = "Indexed ${scanned.size} tracks across folders")
                        }
                    }
                } else {
                    _uiState.update {
                        it.copy(statusBannerMessage = "No supported audio files found in selected directory")
                    }
                }
            }
        }
    }

    fun rescanLibrary() {
        val savedUris = prefs.getStringSet("saved_saf_tree_uris", emptySet()) ?: emptySet()
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(statusBannerMessage = "Rescanning music folders...") }
            }
            val scanned = DirectoryScanner.rescanAllSourcesStreaming(
                context = getApplication(),
                savedTreeUris = savedUris,
                onBatchReady = { batch, completed, total ->
                    dao.insertTracks(batch)
                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(statusBannerMessage = "Rescanning... $completed / $total tracks")
                        }
                    }
                }
            )
            withContext(Dispatchers.Main) {
                if (scanned.isNotEmpty()) {
                    if (_uiState.value.queue.isEmpty()) {
                        playSongFromContext(scanned, scanned.first(), "All Songs")
                    } else {
                        _uiState.update {
                            it.copy(statusBannerMessage = "Rescan complete: ${scanned.size} tracks ready")
                        }
                    }
                } else {
                    val existing = dao.getAllTracksOnce()
                    _uiState.update {
                        it.copy(
                            statusBannerMessage = if (existing.isNotEmpty()) {
                                "Rescan complete: ${existing.size} tracks in library"
                            } else {
                                "No connected folders found yet. Tap the Folder icon to connect your music directory."
                            }
                        )
                    }
                }
            }
        }
    }

    fun importMultipleFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val scanned = DirectoryScanner.scanMultipleDocuments(
                context = getApplication(),
                uris = uris,
                onBatchReady = { batch, completed, total ->
                    dao.insertTracks(batch)
                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(statusBannerMessage = "Importing $completed / $total files...")
                        }
                    }
                }
            )
            if (scanned.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    if (_uiState.value.queue.isEmpty()) {
                        playSongFromContext(scanned, scanned.first(), "Imported Files")
                    } else {
                        _uiState.update {
                            it.copy(statusBannerMessage = "Imported ${scanned.size} tracks")
                        }
                    }
                }
            }
        }
    }

    fun clearAllLibraryTracks() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.clearAllTracks()
            withContext(Dispatchers.Main) {
                audioEngine.pause()
                _uiState.update {
                    it.copy(
                        queue = emptyList(),
                        currentQueueIndex = -1,
                        isPlaying = false,
                        parsedLyricsLines = emptyList(),
                        statusBannerMessage = "Cleared library tracks"
                    )
                }
            }
        }
    }

    fun createPlaylist(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertPlaylist(
                PlaylistEntity(
                    id = "pl_${UUID.randomUUID()}",
                    name = clean,
                    createdAt = System.currentTimeMillis(),
                    trackIdsCsv = ""
                )
            )
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(statusBannerMessage = "Created playlist \"$clean\"") }
            }
        }
    }

    fun renamePlaylist(playlist: PlaylistEntity, newName: String) {
        val clean = newName.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertPlaylist(playlist.copy(name = clean))
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deletePlaylist(playlistId)
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        selectedPlaylistId = if (it.selectedPlaylistId == playlistId) null else it.selectedPlaylistId,
                        statusBannerMessage = "Deleted playlist"
                    )
                }
            }
        }
    }

    fun addTrackToPlaylist(playlist: PlaylistEntity, trackId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val updatedIds = (playlist.trackIds + trackId).distinct()
            dao.insertPlaylist(playlist.copy(trackIdsCsv = updatedIds.joinToString(",")))
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(statusBannerMessage = "Added track to \"${playlist.name}\"") }
            }
        }
    }

    fun removeTrackFromPlaylist(playlist: PlaylistEntity, trackId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val updatedIds = playlist.trackIds.filterNot { it == trackId }
            dao.insertPlaylist(playlist.copy(trackIdsCsv = updatedIds.joinToString(",")))
        }
    }

    fun exportPlaylistsJson(): String {
        val arr = JSONArray()
        for (pl in _uiState.value.playlists) {
            val obj = JSONObject()
            obj.put("id", pl.id)
            obj.put("name", pl.name)
            obj.put("createdAt", pl.createdAt)
            obj.put("trackIdsCsv", pl.trackIdsCsv)
            arr.put(obj)
        }
        return arr.toString(2)
    }

    fun importPlaylistsJson(jsonString: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val arr = JSONArray(jsonString)
                val list = mutableListOf<PlaylistEntity>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        PlaylistEntity(
                            id = obj.optString("id", "pl_${UUID.randomUUID()}"),
                            name = obj.optString("name", "Imported Playlist"),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                            trackIdsCsv = obj.optString("trackIdsCsv", "")
                        )
                    )
                }
                if (list.isNotEmpty()) {
                    dao.insertPlaylists(list)
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(statusBannerMessage = "Imported ${list.size} playlists") }
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(statusBannerMessage = "Invalid playlist JSON format") }
                }
            }
        }
    }

    // ========================================================================
    // 7. NAVIGATION, OFFSETS, SETTINGS & PER-THEME CUSTOMIZATION
    // ========================================================================

    fun selectMainTab(tab: MainNavTab) {
        _uiState.update {
            it.copy(
                mainTab = tab,
                selectedAlbumTitle = null,
                selectedFolderPath = null,
                selectedArtistName = null,
                selectedPlaylistId = null
            )
        }
    }

    fun selectCategorySubTab(subTab: CategorySubTab) {
        _uiState.update {
            it.copy(
                mainTab = MainNavTab.CATEGORIES,
                categorySubTab = subTab,
                selectedAlbumTitle = null,
                selectedFolderPath = null,
                selectedArtistName = null,
                selectedPlaylistId = null
            )
        }
    }

    fun openAlbumDetail(albumTitle: String?) = _uiState.update { it.copy(selectedAlbumTitle = albumTitle) }
    fun openFolderDetail(folderPath: String?) = _uiState.update { it.copy(selectedFolderPath = folderPath) }
    fun openArtistDetail(artistName: String?) = _uiState.update { it.copy(selectedArtistName = artistName) }
    fun openPlaylistDetail(playlistId: String?) = _uiState.update { it.copy(selectedPlaylistId = playlistId) }

    fun setSearchQuery(query: String) = _uiState.update { it.copy(searchQuery = query) }
    fun setSortOption(option: SortOption) = _uiState.update { it.copy(sortOption = option) }

    fun setLyricsExpanded(expanded: Boolean) = _uiState.update {
        it.copy(
            isLyricsExpanded = expanded,
            settings = if (!expanded) it.settings.copy(theatreFullscreenMode = false) else it.settings
        )
    }

    fun setTheatreFullscreenMode(enabled: Boolean) {
        updateSettings { it.copy(theatreFullscreenMode = enabled) }
        if (enabled) {
            _uiState.update { it.copy(isLyricsExpanded = true) }
        }
    }

    fun setQueueDrawerOpen(open: Boolean) = _uiState.update { it.copy(isQueueDrawerOpen = open) }
    fun setSpeedPitchSheetOpen(open: Boolean) = _uiState.update { it.copy(isSpeedPitchSheetOpen = open) }
    fun setSettingsOpen(open: Boolean) = _uiState.update { it.copy(isSettingsOpen = open) }
    fun setGlassCustomizerOpen(open: Boolean) = _uiState.update { it.copy(isGlassCustomizerOpen = open) }
    fun clearStatusBanner() = _uiState.update { it.copy(statusBannerMessage = null) }

    fun adjustGlobalLyricsOffset(deltaMs: Int) {
        updateSettings {
            val next = (it.globalLyricsOffsetMs + deltaMs).coerceIn(-5000, 5000)
            it.copy(globalLyricsOffsetMs = next)
        }
    }

    fun setGlobalLyricsOffset(offsetMs: Int) {
        updateSettings { it.copy(globalLyricsOffsetMs = offsetMs.coerceIn(-5000, 5000)) }
    }

    fun adjustCurrentTrackLyricsOffset(deltaMs: Int) {
        val current = _uiState.value.currentTrack ?: return
        val next = (current.lyricsOffsetMs + deltaMs).coerceIn(-5000, 5000)
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateTrackLyricsOffset(current.id, next)
        }
    }

    fun updateSettings(transform: (PlayerSettingsState) -> PlayerSettingsState) {
        _uiState.update { state ->
            val updated = transform(state.settings)
            saveSettingsToPrefs(updated)
            state.copy(settings = updated)
        }
    }

    private fun loadSettingsFromPrefs(): PlayerSettingsState {
        val modeName = prefs.getString("themeMode", VisualThemeMode.MATERIAL_YOU.name)
        val mode = try {
            VisualThemeMode.valueOf(modeName ?: VisualThemeMode.MATERIAL_YOU.name)
        } catch (_: Exception) {
            VisualThemeMode.MATERIAL_YOU
        }
        val envSecret = try {
            BuildConfig.SPICY_LYRICS_SECRET_KEY
        } catch (_: Exception) {
            SpicyLyricsClient.DEFAULT_SECRET_KEY_PLACEHOLDER
        }
        val defaultSecret = if (SpicyLyricsClient.isSecretKeyConfigured(envSecret)) {
            envSecret
        } else {
            SpicyLyricsClient.DEFAULT_SECRET_KEY_PLACEHOLDER
        }
        val savedSecret = prefs.getString("spicyLyricsSecretKey", defaultSecret) ?: defaultSecret

        return PlayerSettingsState(
            themeMode = mode,
            bleedAlbumArtIntoSongList = prefs.getBoolean("bleedAlbumArtIntoSongList", false),
            materialYouAccentBoost = prefs.getFloat("materialYouAccentBoost", 0.65f).coerceIn(0.15f, 1.0f),
            materialYouSurfaceRichness = prefs.getFloat("materialYouSurfaceRichness", 0.12f).coerceIn(0.02f, 0.35f),
            oledPitchBlack = prefs.getBoolean("oledPitchBlack", true),
            monoBorderContrast = prefs.getFloat("monoBorderContrast", 0.38f).coerceIn(0.12f, 0.90f),
            monoKeepLyricsColored = prefs.getBoolean("monoKeepLyricsColored", true),
            glassBlurRadiusDp = prefs.getFloat("glassBlurRadiusDp", 68f).coerceIn(0f, 120f),
            glassFrostOpacity = prefs.getFloat("glassFrostOpacity", 0.88f).coerceIn(0.05f, 0.98f),
            glassSpecularIntensity = prefs.getFloat("glassSpecularIntensity", 0.42f).coerceIn(0f, 1f),
            bgAnimationSpeed = prefs.getFloat("bgAnimationSpeed", 1.0f).coerceIn(0f, 3.0f),
            lyricsBlackTintAlpha = prefs.getFloat("lyricsBlackTintAlpha", 0.34f).coerceIn(0.15f, 0.80f),
            lyricsOrbSaturation = prefs.getFloat("lyricsOrbSaturation", 1.0f).coerceIn(0.5f, 1.5f),
            theatreFullscreenMode = false,
            spicyLyricsSecretKey = savedSecret,
            customUiFontPath = prefs.getString("customUiFontPath", null),
            customUiFontName = prefs.getString("customUiFontName", null),
            customLyricsFontPath = prefs.getString("customLyricsFontPath", null),
            customLyricsFontName = prefs.getString("customLyricsFontName", null),
            globalLyricsOffsetMs = prefs.getInt("globalLyricsOffsetMs", 0).coerceIn(-5000, 5000),
            lyricsFontSizeSp = prefs.getFloat("lyricsFontSizeSp", 26f).coerceIn(16f, 42f),
            lyricsGlowIntensity = prefs.getFloat("lyricsGlowIntensity", 1.0f).coerceIn(0f, 2f),
            simpleLyricsMode = prefs.getBoolean("simpleLyricsMode", false),
            refreshedSimpleLyrics = prefs.getBoolean("refreshedSimpleLyrics", true),
            autoScrollLyrics = prefs.getBoolean("autoScrollLyrics", true),
            resetSpeedPitchOnTrackChange = prefs.getBoolean("resetSpeedPitchOnTrackChange", false),
            gaplessPlayback = prefs.getBoolean("gaplessPlayback", true),
            crossfadeDurationSec = prefs.getInt("crossfadeDurationSec", 2).coerceIn(0, 12),
            replayGainEnabled = prefs.getBoolean("replayGainEnabled", true)
        )
    }

    private fun saveSettingsToPrefs(s: PlayerSettingsState) {
        prefs.edit()
            .putString("themeMode", s.themeMode.name)
            .putBoolean("bleedAlbumArtIntoSongList", s.bleedAlbumArtIntoSongList)
            .putFloat("materialYouAccentBoost", s.materialYouAccentBoost)
            .putFloat("materialYouSurfaceRichness", s.materialYouSurfaceRichness)
            .putBoolean("oledPitchBlack", s.oledPitchBlack)
            .putFloat("monoBorderContrast", s.monoBorderContrast)
            .putBoolean("monoKeepLyricsColored", s.monoKeepLyricsColored)
            .putFloat("glassBlurRadiusDp", s.glassBlurRadiusDp)
            .putFloat("glassFrostOpacity", s.glassFrostOpacity)
            .putFloat("glassSpecularIntensity", s.glassSpecularIntensity)
            .putFloat("bgAnimationSpeed", s.bgAnimationSpeed)
            .putFloat("lyricsBlackTintAlpha", s.lyricsBlackTintAlpha)
            .putFloat("lyricsOrbSaturation", s.lyricsOrbSaturation)
            .putString("spicyLyricsSecretKey", s.spicyLyricsSecretKey)
            .putString("customUiFontPath", s.customUiFontPath)
            .putString("customUiFontName", s.customUiFontName)
            .putString("customLyricsFontPath", s.customLyricsFontPath)
            .putString("customLyricsFontName", s.customLyricsFontName)
            .putInt("globalLyricsOffsetMs", s.globalLyricsOffsetMs)
            .putFloat("lyricsFontSizeSp", s.lyricsFontSizeSp)
            .putFloat("lyricsGlowIntensity", s.lyricsGlowIntensity)
            .putBoolean("simpleLyricsMode", s.simpleLyricsMode)
            .putBoolean("refreshedSimpleLyrics", s.refreshedSimpleLyrics)
            .putBoolean("autoScrollLyrics", s.autoScrollLyrics)
            .putBoolean("resetSpeedPitchOnTrackChange", s.resetSpeedPitchOnTrackChange)
            .putBoolean("gaplessPlayback", s.gaplessPlayback)
            .putInt("crossfadeDurationSec", s.crossfadeDurationSec)
            .putBoolean("replayGainEnabled", s.replayGainEnabled)
            .apply()
    }

    override fun onCleared() {
        super.onCleared()
        audioEngine.releasePlayer()
    }
}
