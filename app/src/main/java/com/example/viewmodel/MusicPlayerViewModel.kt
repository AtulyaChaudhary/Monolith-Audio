package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import com.example.lyrics.SyllableLineModel
import com.example.lyrics.TtmlParser
import com.example.ui.theme.CustomFontManager
import com.example.ui.theme.VisualThemeMode
import kotlinx.coroutines.Dispatchers
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

data class PlayerSettingsState(
    // Frosted Glass & Theme Customization
    val themeMode: VisualThemeMode = VisualThemeMode.FROSTED_GLASS,
    val glassBlurRadiusDp: Float = 52f,      // 0dp to 120dp customizable blur slider
    val glassFrostOpacity: Float = 0.22f,    // 0.05f to 0.85f frost opacity slider
    val glassSpecularIntensity: Float = 0.50f,
    val bgAnimationSpeed: Float = 1.0f,      // 0x to 3.0x dynamic animation speed
    val oledPitchBlack: Boolean = false,
    val theatreFullscreenMode: Boolean = false,
    // Custom Uploaded Fonts (.ttf / .otf) for UI and Lyrics
    val customUiFontPath: String? = null,
    val customUiFontName: String? = null,
    val customLyricsFontPath: String? = null,
    val customLyricsFontName: String? = null,
    // Lyrics & Animator Settings
    val globalLyricsOffsetMs: Int = 0,       // -5000ms to +5000ms
    val lyricsFontSizeSp: Float = 26f,       // 16sp to 42sp
    val lyricsGlowIntensity: Float = 1.0f,   // 0.0f to 2.0f
    val simpleLyricsMode: Boolean = false,
    val refreshedSimpleLyrics: Boolean = true,
    val autoScrollLyrics: Boolean = true,
    // Playback Behavior Settings
    val resetSpeedPitchOnTrackChange: Boolean = false,
    val gaplessPlayback: Boolean = true,
    val crossfadeDurationSec: Int = 2,       // 0s to 12s
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
    // Synced TTML Lyrics & Animator
    val parsedLyricsLines: List<SyllableLineModel> = emptyList(),
    val activeLyricsLineIndex: Int = -1,
    val hasCoLocatedTtml: Boolean = false,
    val coLocatedTtmlFileName: String? = null,
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
                    ttmlCount = sorted.count { it.coLocatedTtmlUri != null || it.effectiveTtmlContent != null }
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

    /**
     * Purges any previously seeded built-in example tracks ("Tomato", "Potato", "Carrot",
     * "Glass Horizon", etc.) from the database and disk so only the user's real local files remain.
     */
    private fun purgeBuiltInTracksAndInitializeUserLibrary() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteBuiltInStudioTracks()
            dao.deletePlaylist("playlist_favorites")
            try {
                File(getApplication<Application>().filesDir, "LocalMusic").deleteRecursively()
            } catch (_: Exception) {
            }

            val userTracks = dao.getAllTracksOnce()
            withContext(Dispatchers.Main) {
                if (_uiState.value.queue.isEmpty() && userTracks.isNotEmpty()) {
                    val sorted = userTracks.sortedBy { it.title.lowercase() }
                    val queueItems = sorted.map { QueueItem(UUID.randomUUID().toString(), it) }
                    val first = sorted.first()
                    val parsedLyrics = loadCoLocatedTtmlForTrack(first)
                    _uiState.update {
                        it.copy(
                            queue = queueItems,
                            currentQueueIndex = 0,
                            queueContextLabel = "All Songs",
                            durationMs = first.durationMs,
                            parsedLyricsLines = parsedLyrics.lines,
                            hasCoLocatedTtml = parsedLyrics.hasCoLocated,
                            coLocatedTtmlFileName = parsedLyrics.fileName
                        )
                    }
                    audioEngine.loadTrack(
                        track = first,
                        playImmediately = false,
                        speed = _uiState.value.playbackSpeed,
                        pitch = _uiState.value.playbackPitch,
                        replayGainEnabled = _uiState.value.settings.replayGainEnabled
                    )
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

        val parsedLyrics = loadCoLocatedTtmlForTrack(activeTrack)

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
    }

    fun addToQueueNext(track: TrackEntity) {
        _uiState.update { state ->
            val currentList = state.queue.toMutableList()
            val newItem = QueueItem(UUID.randomUUID().toString(), track)
            if (currentList.isEmpty() || state.currentQueueIndex < 0) {
                val parsed = loadCoLocatedTtmlForTrack(track)
                audioEngine.loadTrack(
                    track = track,
                    playImmediately = false,
                    speed = state.playbackSpeed,
                    pitch = state.playbackPitch,
                    replayGainEnabled = state.settings.replayGainEnabled
                )
                state.copy(
                    queue = listOf(newItem),
                    currentQueueIndex = 0,
                    durationMs = track.durationMs,
                    parsedLyricsLines = parsed.lines,
                    hasCoLocatedTtml = parsed.hasCoLocated,
                    coLocatedTtmlFileName = parsed.fileName,
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
        val parsed = loadCoLocatedTtmlForTrack(item.track)

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
                coLocatedTtmlFileName = parsed.fileName
            )
        }
        audioEngine.loadTrack(
            track = item.track,
            playImmediately = true,
            speed = nextSpeed,
            pitch = nextPitch,
            replayGainEnabled = state.settings.replayGainEnabled
        )
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
                val parsed = loadCoLocatedTtmlForTrack(nextTrack)
                audioEngine.loadTrack(
                    track = nextTrack,
                    playImmediately = state.isPlaying,
                    speed = state.playbackSpeed,
                    pitch = state.playbackPitch,
                    replayGainEnabled = state.settings.replayGainEnabled
                )
                state.copy(
                    queue = list,
                    currentQueueIndex = newIndex,
                    currentPositionMs = 0.0,
                    durationMs = nextTrack.durationMs,
                    parsedLyricsLines = parsed.lines,
                    hasCoLocatedTtml = parsed.hasCoLocated,
                    coLocatedTtmlFileName = parsed.fileName
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
    // 3. PLAYBACK TRANSPORT & FRAME CLOCK TICK FOR LYRICS ANIMATOR
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
        tickFrameClock(System.nanoTime())
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

    fun tickFrameClock(frameNanos: Long) {
        val pos = audioEngine.getCurrentPositionMs()
        val dur = audioEngine.getDurationMs().coerceAtLeast(1L)
        val playing = audioEngine.isPlaying()
        val state = _uiState.value
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
    // 4. CO-LOCATED .TTML DISCOVERY & MANUAL .TTML LOADING
    // ========================================================================

    private data class LoadedLyricsResult(
        val lines: List<SyllableLineModel>,
        val hasCoLocated: Boolean,
        val fileName: String?
    )

    private fun loadCoLocatedTtmlForTrack(track: TrackEntity): LoadedLyricsResult {
        val expectedFileName = "${track.baseFileName}.ttml"
        var xmlContent: String? = track.manualTtmlContent?.takeIf { it.isNotBlank() }

        if (xmlContent == null && track.coLocatedTtmlUri != null) {
            val uriOrPath = track.coLocatedTtmlUri
            xmlContent = if (uriOrPath.startsWith("content://")) {
                DirectoryScanner.readTextFromUri(getApplication(), Uri.parse(uriOrPath))
            } else {
                val f = File(uriOrPath)
                if (f.exists()) f.readText() else null
            }
        }

        if (xmlContent == null && !track.parentTreeUri.startsWith("content://")) {
            val folderDir = File(track.parentTreeUri)
            if (folderDir.exists() && folderDir.isDirectory) {
                val sibling = folderDir.listFiles()?.firstOrNull {
                    it.name.equals(expectedFileName, ignoreCase = true)
                }
                if (sibling != null) {
                    xmlContent = sibling.readText()
                }
            }
        }

        if (xmlContent == null) {
            xmlContent = track.embeddedTtmlContent
        }

        if (xmlContent.isNullOrBlank()) {
            return LoadedLyricsResult(emptyList(), false, null)
        }

        val parsed = TtmlParser.parseTtml(xmlContent)
        return LoadedLyricsResult(
            lines = parsed,
            hasCoLocated = true,
            fileName = "${track.folderPath}/$expectedFileName"
        )
    }

    fun loadManualTtmlUriForCurrentTrack(uri: Uri) {
        val current = _uiState.value.currentTrack ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val text = DirectoryScanner.readTextFromUri(getApplication(), uri)
            if (!text.isNullOrBlank()) {
                dao.updateManualTtml(current.id, text)
                val parsed = TtmlParser.parseTtml(text)
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            parsedLyricsLines = parsed,
                            hasCoLocatedTtml = parsed.isNotEmpty(),
                            coLocatedTtmlFileName = "Manual: ${current.baseFileName}.ttml",
                            statusBannerMessage = "Loaded synced .ttml lyrics (${parsed.size} lines)"
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
    // 6. DIRECTORY SCANNING & PLAYLIST MANAGEMENT
    // ========================================================================

    fun scanSafDirectoryTree(treeUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val scanned = DirectoryScanner.scanDirectoryTree(getApplication(), treeUri)
            if (scanned.isNotEmpty()) {
                dao.insertTracks(scanned)
                withContext(Dispatchers.Main) {
                    if (_uiState.value.queue.isEmpty()) {
                        playSongFromContext(scanned, scanned.first(), "Imported Folder")
                    } else {
                        _uiState.update {
                            it.copy(statusBannerMessage = "Indexed ${scanned.size} tracks across folders with .ttml pairing")
                        }
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(statusBannerMessage = "No supported audio files found in selected directory")
                    }
                }
            }
        }
    }

    fun importMultipleFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val scanned = DirectoryScanner.scanMultipleDocuments(getApplication(), uris)
            if (scanned.isNotEmpty()) {
                dao.insertTracks(scanned)
                withContext(Dispatchers.Main) {
                    if (_uiState.value.queue.isEmpty()) {
                        playSongFromContext(scanned, scanned.first(), "Imported Files")
                    } else {
                        _uiState.update {
                            it.copy(statusBannerMessage = "Imported ${scanned.size} tracks with co-located .ttml check")
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
    // 7. NAVIGATION, OFFSETS, SETTINGS & FROSTED GLASS CUSTOMIZATION
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
        val modeName = prefs.getString("themeMode", VisualThemeMode.FROSTED_GLASS.name)
        val mode = try {
            VisualThemeMode.valueOf(modeName ?: VisualThemeMode.FROSTED_GLASS.name)
        } catch (_: Exception) {
            VisualThemeMode.FROSTED_GLASS
        }
        return PlayerSettingsState(
            themeMode = mode,
            glassBlurRadiusDp = prefs.getFloat("glassBlurRadiusDp", 52f).coerceIn(0f, 120f),
            glassFrostOpacity = prefs.getFloat("glassFrostOpacity", 0.22f).coerceIn(0.05f, 0.85f),
            glassSpecularIntensity = prefs.getFloat("glassSpecularIntensity", 0.50f).coerceIn(0f, 1f),
            bgAnimationSpeed = prefs.getFloat("bgAnimationSpeed", 1.0f).coerceIn(0f, 3.0f),
            oledPitchBlack = prefs.getBoolean("oledPitchBlack", false),
            theatreFullscreenMode = false,
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
            .putFloat("glassBlurRadiusDp", s.glassBlurRadiusDp)
            .putFloat("glassFrostOpacity", s.glassFrostOpacity)
            .putFloat("glassSpecularIntensity", s.glassSpecularIntensity)
            .putFloat("bgAnimationSpeed", s.bgAnimationSpeed)
            .putBoolean("oledPitchBlack", s.oledPitchBlack)
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
