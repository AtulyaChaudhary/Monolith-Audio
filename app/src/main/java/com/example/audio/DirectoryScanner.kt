package com.example.audio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.example.data.TrackEntity
import java.io.File
import java.io.FileOutputStream

/**
 * Full Directory & File System Scanner:
 * 1. Recursively walks Android Storage Access Framework directory trees (`OpenDocumentTree`),
 *    preserving the exact relative folder hierarchy (`/Folder/Subfolder`) and parent directory URI.
 * 2. Also supports multi-file fallback selection (`OpenMultipleDocuments`), grouping audio files
 *    and `.ttml` files selected together by base filename.
 * 3. Extracts embedded ID3/Vorbis/MP4 metadata (Title, Artist, Album, Track Number, Duration,
 *    and Cover Art) + samples a 5-color vibrant palette from the embedded cover art for the
 *    Frosted Glass animated background.
 * 4. Automatically discovers co-located `<SongBaseName>.ttml` files in the exact same directory!
 */
object DirectoryScanner {

    private val SUPPORTED_AUDIO_EXTENSIONS = setOf("flac", "mp3", "wav", "m4a", "ogg", "opus", "aac")

    private val FALLBACK_PALETTES = listOf(
        "#6366F1,#EC4899,#06B6D4,#8B5CF6,#1E1B4B",
        "#FF3B5C,#FF7A00,#9B51E0,#1F1037,#FF0055",
        "#00F5D4,#7B2CBF,#3A86FF,#0F172A,#FF007F",
        "#10B981,#3B82F6,#6366F1,#064E3B,#1E1B4B",
        "#F59E0B,#EF4444,#8B5CF6,#1F2937,#EC4899"
    )

    private data class DiscoveredDoc(
        val uri: Uri,
        val displayName: String,
        val baseName: String,
        val extension: String,
        val folderPath: String,
        val folderName: String,
        val parentTreeUri: String
    )

    fun scanDirectoryTree(context: Context, treeUri: Uri): List<TrackEntity> {
        val resolver = context.contentResolver
        try {
            val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            resolver.takePersistableUriPermission(treeUri, takeFlags)
        } catch (_: Exception) {
        }

        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootFolderName = rootDocId.substringAfterLast(':').substringAfterLast('/').ifBlank { "Music" }
        val discoveredDocs = mutableListOf<DiscoveredDoc>()

        fun walkFolder(docId: String, currentFolderPath: String, currentFolderName: String) {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            )
            resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)

                while (cursor.moveToNext()) {
                    val childId = if (idCol >= 0) cursor.getString(idCol) else continue
                    val displayName = if (nameCol >= 0) cursor.getString(nameCol) ?: "Unknown" else "Unknown"
                    val mimeType = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "" else ""

                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        val nextPath = "$currentFolderPath/$displayName"
                        walkFolder(childId, nextPath, displayName)
                    } else {
                        val ext = displayName.substringAfterLast('.', "").lowercase()
                        val base = displayName.substringBeforeLast('.', displayName)
                        if (ext in SUPPORTED_AUDIO_EXTENSIONS || ext == "ttml") {
                            val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                            discoveredDocs.add(
                                DiscoveredDoc(
                                    uri = fileUri,
                                    displayName = displayName,
                                    baseName = base,
                                    extension = ext,
                                    folderPath = currentFolderPath,
                                    folderName = currentFolderName,
                                    parentTreeUri = "$treeUri#$currentFolderPath"
                                )
                            )
                        }
                    }
                }
            }
        }

        walkFolder(rootDocId, "/$rootFolderName", rootFolderName)
        return pairAndBuildTracks(context, discoveredDocs)
    }

    fun scanMultipleDocuments(context: Context, uris: List<Uri>): List<TrackEntity> {
        val resolver = context.contentResolver
        val docs = mutableListOf<DiscoveredDoc>()

        for (uri in uris) {
            try {
                resolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }

            var displayName = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "Track"
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) {
                    displayName = c.getString(idx) ?: displayName
                }
            }

            val rawPath = uri.path?.substringBeforeLast('/')?.substringAfterLast(':') ?: "Imported"
            val folderPath = if (rawPath.startsWith("/")) rawPath else "/$rawPath"
            val folderName = folderPath.substringAfterLast('/').ifBlank { "Imported" }
            val ext = displayName.substringAfterLast('.', "").lowercase()
            val base = displayName.substringBeforeLast('.', displayName)

            if (ext in SUPPORTED_AUDIO_EXTENSIONS || ext == "ttml") {
                docs.add(
                    DiscoveredDoc(
                        uri = uri,
                        displayName = displayName,
                        baseName = base,
                        extension = ext,
                        folderPath = folderPath,
                        folderName = folderName,
                        parentTreeUri = "imported://$folderPath"
                    )
                )
            }
        }

        return pairAndBuildTracks(context, docs)
    }

    private fun pairAndBuildTracks(
        context: Context,
        docs: List<DiscoveredDoc>
    ): List<TrackEntity> {
        // Index .ttml files by (folderPath.lowercase(), baseName.lowercase()) for strict co-located discovery
        val ttmlByFolderAndBase = docs
            .filter { it.extension == "ttml" }
            .associateBy { "${it.folderPath.lowercase()}::${it.baseName.lowercase()}" }

        val audioDocs = docs.filter { it.extension in SUPPORTED_AUDIO_EXTENSIONS }
        val results = mutableListOf<TrackEntity>()

        audioDocs.forEachIndexed { idx, doc ->
            val key = "${doc.folderPath.lowercase()}::${doc.baseName.lowercase()}"
            val matchingTtmlDoc = ttmlByFolderAndBase[key]
            val ttmlContent = matchingTtmlDoc?.let { readTextFromUri(context, it.uri) }

            val meta = extractAudioMetadata(context, doc.uri, doc.baseName, doc.folderName, idx)
            results.add(
                TrackEntity(
                    id = "saf_${(doc.folderPath + "/" + doc.displayName).hashCode()}",
                    title = meta.title,
                    artist = meta.artist,
                    album = meta.album,
                    trackNumber = meta.trackNumber,
                    durationMs = meta.durationMs,
                    audioUri = doc.uri.toString(),
                    folderPath = doc.folderPath,
                    folderName = doc.folderName,
                    parentTreeUri = doc.parentTreeUri,
                    baseFileName = doc.baseName,
                    extension = doc.extension,
                    coLocatedTtmlUri = matchingTtmlDoc?.uri?.toString(),
                    embeddedTtmlContent = ttmlContent,
                    coverColorsCsv = meta.paletteCsv,
                    coverArtPath = meta.savedCoverPath,
                    isBuiltInStudioTrack = false
                )
            )
        }
        return results
    }

    fun readTextFromUri(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }

    private data class ExtractedMeta(
        val title: String,
        val artist: String,
        val album: String,
        val trackNumber: Int,
        val durationMs: Long,
        val paletteCsv: String,
        val savedCoverPath: String?
    )

    private fun extractAudioMetadata(
        context: Context,
        uri: Uri,
        fallbackTitle: String,
        fallbackFolder: String,
        indexSeed: Int
    ): ExtractedMeta {
        val retriever = MediaMetadataRetriever()
        var title = fallbackTitle.replace('_', ' ')
        var artist = "Unknown Artist"
        var album = fallbackFolder.replace('_', ' ')
        var trackNum = indexSeed + 1
        var durationMs = 180_000L
        var paletteCsv = FALLBACK_PALETTES[indexSeed % FALLBACK_PALETTES.size]
        var savedCoverPath: String? = null

        try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }?.let { title = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() }?.let { artist = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.takeIf { it.isNotBlank() }?.let { album = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                ?.substringBefore('/')?.trim()?.toIntOrNull()?.let { trackNum = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.takeIf { it > 0 }?.let { durationMs = it }

            val artBytes = retriever.embeddedPicture
            if (artBytes != null && artBytes.isNotEmpty()) {
                val bmp = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                if (bmp != null) {
                    paletteCsv = samplePaletteFromBitmap(bmp)
                    val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
                    val coverFile = File(coversDir, "cover_${uri.toString().hashCode()}.jpg")
                    FileOutputStream(coverFile).use { fos ->
                        bmp.compress(Bitmap.CompressFormat.JPEG, 85, fos)
                    }
                    savedCoverPath = coverFile.absolutePath
                }
            }
        } catch (_: Exception) {
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }

        return ExtractedMeta(
            title = title,
            artist = artist,
            album = album,
            trackNumber = trackNum,
            durationMs = durationMs,
            paletteCsv = paletteCsv,
            savedCoverPath = savedCoverPath
        )
    }

    private fun samplePaletteFromBitmap(bitmap: Bitmap): String {
        val samplePoints = listOf(
            0.2f to 0.2f,
            0.8f to 0.25f,
            0.5f to 0.5f,
            0.25f to 0.8f,
            0.75f to 0.75f
        )
        val hexes = samplePoints.map { (fx, fy) ->
            val x = (bitmap.width * fx).toInt().coerceIn(0, (bitmap.width - 1).coerceAtLeast(0))
            val y = (bitmap.height * fy).toInt().coerceIn(0, (bitmap.height - 1).coerceAtLeast(0))
            val pixel = bitmap.getPixel(x, y)
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            String.format(java.util.Locale.US, "#%02X%02X%02X", r, g, b)
        }
        return hexes.joinToString(",")
    }
}
