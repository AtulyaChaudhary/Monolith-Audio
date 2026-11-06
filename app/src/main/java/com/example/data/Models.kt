package com.example.data

import androidx.compose.ui.graphics.Color
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val trackNumber: Int,
    val durationMs: Long,
    val audioUri: String,
    val folderPath: String,        // Full relative directory path, e.g. "/Music/Rock"
    val folderName: String,        // Immediate directory name, e.g. "Rock"
    val parentTreeUri: String,     // Directory reference (FileSystemDirectoryHandle equivalent)
    val baseFileName: String,      // Base filename without extension, e.g. "SongName"
    val extension: String,         // e.g. "flac", "mp3", "wav"
    val coLocatedTtmlUri: String?, // Same-folder SongName.ttml / .lrc / .txt URI or path if found
    val embeddedTtmlContent: String? = null,
    val manualTtmlContent: String? = null,
    val spicyLyricsJson: String? = null,       // Cached Spicy Lyrics JSON payload
    val lyricsAttributionJson: String? = null, // Cached SongWriters + Maker/Uploader PFP attribution
    val coverColorsCsv: String,    // Up to 9 hex colors extracted from Album Art for multi-orb background
    val coverArtPath: String? = null,
    val replayGainDb: Float = 0f,
    val lyricsOffsetMs: Int = 0,   // Per-track lyrics offset (-5000ms to +5000ms)
    val isBuiltInStudioTrack: Boolean = false
) {
    @Ignore
    private var memoizedPalette: List<Color>? = null

    fun parsePalette(): List<Color> {
        memoizedPalette?.let { return it }
        val parts = coverColorsCsv.split(",").mapNotNull { hex ->
            try {
                Color(android.graphics.Color.parseColor(hex.trim()))
            } catch (_: Exception) {
                null
            }
        }
        val computed = if (parts.size >= 3) parts else listOf(
            Color(0xFF6366F1),
            Color(0xFFEC4899),
            Color(0xFF06B6D4),
            Color(0xFF8B5CF6),
            Color(0xFFF59E0B),
            Color(0xFF10B981),
            Color(0xFFEF4444),
            Color(0xFF3B82F6),
            Color(0xFF1E1B4B)
        )
        memoizedPalette = computed
        return computed
    }

    val effectiveTtmlContent: String?
        get() = manualTtmlContent?.takeIf { it.isNotBlank() }
            ?: embeddedTtmlContent?.takeIf { it.isNotBlank() }

    val hasAnySyncedLyrics: Boolean
        get() = coLocatedTtmlUri != null ||
            effectiveTtmlContent != null ||
            !spicyLyricsJson.isNullOrBlank()
}

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val trackIdsCsv: String = ""
) {
    val trackIds: List<String>
        get() = if (trackIdsCsv.isBlank()) emptyList() else trackIdsCsv.split(",").filter { it.isNotBlank() }
}

data class AlbumSummary(
    val albumTitle: String,
    val artist: String,
    val tracks: List<TrackEntity>,
    val palette: List<Color>,
    val coverArtPath: String?
)

data class FolderSummary(
    val folderPath: String,
    val folderName: String,
    val parentTreeUri: String,
    val tracks: List<TrackEntity>,
    val ttmlCount: Int
)

data class ArtistSummary(
    val artistName: String,
    val tracks: List<TrackEntity>,
    val albumCount: Int,
    val palette: List<Color>
)

data class QueueItem(
    val queueEntryId: String,
    val track: TrackEntity
)

enum class MainNavTab {
    ALL_SONGS,
    CATEGORIES
}

enum class CategorySubTab(val title: String) {
    ALBUMS("Albums"),
    FOLDERS("Folders"),
    ARTISTS("Artists"),
    PLAYLISTS("Playlists")
}

enum class SortOption(val label: String) {
    TITLE("Title (A–Z)"),
    ARTIST("Artist"),
    ALBUM("Album"),
    TRACK_NUMBER("Track #"),
    DURATION("Duration"),
    FOLDER("Folder Path")
}
