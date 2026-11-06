package com.example.audio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.example.data.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.File
import java.io.FileOutputStream

/**
 * High-Speed Parallel Directory & File System Scanner:
 * 1. Recursively walks Android Storage Access Framework directory trees (`OpenDocumentTree`),
 *    preserving the exact relative folder hierarchy (`/Folder/Subfolder`) and parent directory URI.
 * 2. Supports Rescan of all persisted SAF folder permissions (`contentResolver.persistedUriPermissions`)
 *    plus accessible local `/Music` and `/Download` directories.
 * 3. Streams discovered tracks in fast parallel batches with downsampled cover-art decoding
 *    (`inSampleSize = 4`) so tracks load into the library immediately with zero lag.
 * 4. Samples 9 vibrant colors across each album cover for the multi-orb animated background.
 * 5. Automatically pairs co-located `<SongBaseName>.ttml` files in the same directory.
 */
object DirectoryScanner {

    private val SUPPORTED_AUDIO_EXTENSIONS = setOf("flac", "mp3", "wav", "m4a", "ogg", "opus", "aac")
    private val SUPPORTED_LYRICS_EXTENSIONS = listOf("ttml", "lrc", "txt")

    private val FALLBACK_PALETTES = listOf(
        "#6366F1,#EC4899,#06B6D4,#8B5CF6,#F59E0B,#10B981,#EF4444,#3B82F6,#1E1B4B",
        "#FF3B5C,#FF7A00,#9B51E0,#00F5D4,#FF0055,#3A86FF,#FBBF24,#8B5CF6,#1F1037",
        "#00F5D4,#7B2CBF,#3A86FF,#FF007F,#10B981,#F59E0B,#6366F1,#EC4899,#0F172A",
        "#10B981,#3B82F6,#6366F1,#EC4899,#F59E0B,#06B6D4,#8B5CF6,#14B8A6,#064E3B",
        "#F59E0B,#EF4444,#8B5CF6,#EC4899,#3B82F6,#10B981,#06B6D4,#6366F1,#1F2937"
    )

    private data class DiscoveredDoc(
        val uri: Uri,
        val displayName: String,
        val baseName: String,
        val extension: String,
        val folderPath: String,
        val folderName: String,
        val parentTreeUri: String,
        val directFile: File? = null
    )

    suspend fun scanDirectoryTreeStreaming(
        context: Context,
        treeUri: Uri,
        onBatchReady: suspend (batch: List<TrackEntity>, completedCount: Int, totalCount: Int) -> Unit = { _, _, _ -> }
    ): List<TrackEntity> {
        val resolver = context.contentResolver
        try {
            val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            resolver.takePersistableUriPermission(treeUri, takeFlags)
        } catch (_: Exception) {
        }

        val rootDocId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (_: Exception) {
            return emptyList()
        }
        val rootFolderName = rootDocId.substringAfterLast(':').substringAfterLast('/').ifBlank { "Music" }
        val discoveredDocs = mutableListOf<DiscoveredDoc>()

        fun walkFolder(docId: String, currentFolderPath: String, currentFolderName: String) {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            )
            try {
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
                            if (ext in SUPPORTED_AUDIO_EXTENSIONS || ext in SUPPORTED_LYRICS_EXTENSIONS) {
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
            } catch (_: Exception) {
            }
        }

        walkFolder(rootDocId, "/$rootFolderName", rootFolderName)
        return pairAndBuildTracksParallel(context, discoveredDocs, onBatchReady)
    }

    suspend fun scanDirectoryTree(context: Context, treeUri: Uri): List<TrackEntity> {
        return scanDirectoryTreeStreaming(context, treeUri)
    }

    /**
     * Re-scans all persisted SAF folder trees + accessible standard Music/Download folders
     * when the user taps the Rescan button.
     */
    suspend fun rescanAllSourcesStreaming(
        context: Context,
        savedTreeUris: Set<String>,
        onBatchReady: suspend (batch: List<TrackEntity>, completedCount: Int, totalCount: Int) -> Unit = { _, _, _ -> }
    ): List<TrackEntity> {
        val allResults = mutableListOf<TrackEntity>()
        val persistedUris = mutableSetOf<Uri>()

        try {
            context.contentResolver.persistedUriPermissions.forEach { perm ->
                if (perm.isReadPermission && DocumentsContract.isTreeUri(perm.uri)) {
                    persistedUris.add(perm.uri)
                }
            }
        } catch (_: Exception) {
        }

        savedTreeUris.forEach { raw ->
            try {
                val u = Uri.parse(raw)
                if (DocumentsContract.isTreeUri(u)) {
                    persistedUris.add(u)
                }
            } catch (_: Exception) {
            }
        }

        for (treeUri in persistedUris) {
            val scanned = scanDirectoryTreeStreaming(context, treeUri, onBatchReady)
            allResults.addAll(scanned)
        }

        // Also scan accessible standard device Music / Download directories if readable
        val standardDirs = listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        ).filter { it.exists() && it.canRead() }

        if (standardDirs.isNotEmpty()) {
            val fsDocs = mutableListOf<DiscoveredDoc>()
            for (rootDir in standardDirs) {
                walkFileSystemDir(rootDir, "/${rootDir.name}", fsDocs)
            }
            if (fsDocs.isNotEmpty()) {
                val scannedFs = pairAndBuildTracksParallel(context, fsDocs, onBatchReady)
                allResults.addAll(scannedFs)
            }
        }

        return allResults.distinctBy { it.id }
    }

    private fun walkFileSystemDir(
        dir: File,
        currentFolderPath: String,
        outDocs: MutableList<DiscoveredDoc>
    ) {
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (child.isDirectory) {
                if (!child.name.startsWith(".")) {
                    walkFileSystemDir(child, "$currentFolderPath/${child.name}", outDocs)
                }
            } else {
                val ext = child.extension.lowercase()
                val base = child.nameWithoutExtension
                if (ext in SUPPORTED_AUDIO_EXTENSIONS || ext in SUPPORTED_LYRICS_EXTENSIONS) {
                    outDocs.add(
                        DiscoveredDoc(
                            uri = Uri.fromFile(child),
                            displayName = child.name,
                            baseName = base,
                            extension = ext,
                            folderPath = currentFolderPath,
                            folderName = dir.name,
                            parentTreeUri = dir.absolutePath,
                            directFile = child
                        )
                    )
                }
            }
        }
    }

    suspend fun scanMultipleDocuments(
        context: Context,
        uris: List<Uri>,
        onBatchReady: suspend (batch: List<TrackEntity>, completedCount: Int, totalCount: Int) -> Unit = { _, _, _ -> }
    ): List<TrackEntity> {
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
            try {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && c.moveToFirst()) {
                        displayName = c.getString(idx) ?: displayName
                    }
                }
            } catch (_: Exception) {
            }

            val rawPath = uri.path?.substringBeforeLast('/')?.substringAfterLast(':') ?: "Imported"
            val folderPath = if (rawPath.startsWith("/")) rawPath else "/$rawPath"
            val folderName = folderPath.substringAfterLast('/').ifBlank { "Imported" }
            val ext = displayName.substringAfterLast('.', "").lowercase()
            val base = displayName.substringBeforeLast('.', displayName)

            if (ext in SUPPORTED_AUDIO_EXTENSIONS || ext in SUPPORTED_LYRICS_EXTENSIONS) {
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

        return pairAndBuildTracksParallel(context, docs, onBatchReady)
    }

    private suspend fun pairAndBuildTracksParallel(
        context: Context,
        docs: List<DiscoveredDoc>,
        onBatchReady: suspend (batch: List<TrackEntity>, completedCount: Int, totalCount: Int) -> Unit
    ): List<TrackEntity> = coroutineScope {
        // Group co-located lyrics strictly by exact folderPath + exact baseFileName!
        // Within the exact same song, prefer .ttml > .lrc > .txt.
        // NEVER pair another song's .ttml with a song that has its own .lrc or .txt!
        val lyricsByFolderAndBase = docs
            .filter { it.extension in SUPPORTED_LYRICS_EXTENSIONS }
            .groupBy { "${it.folderPath.lowercase()}::${it.baseName.lowercase()}" }
            .mapValues { (_, candidates) ->
                candidates.minByOrNull { SUPPORTED_LYRICS_EXTENSIONS.indexOf(it.extension).coerceAtLeast(0) }!!
            }

        val audioDocs = docs.filter { it.extension in SUPPORTED_AUDIO_EXTENSIONS }
        if (audioDocs.isEmpty()) return@coroutineScope emptyList()

        val allTracks = mutableListOf<TrackEntity>()
        val total = audioDocs.size
        val indexedChunks = audioDocs.withIndex().chunked(8)

        for (chunk in indexedChunks) {
            val batchTracks = chunk.map { (idx, doc) ->
                async(Dispatchers.IO) {
                    val key = "${doc.folderPath.lowercase()}::${doc.baseName.lowercase()}"
                    val matchingLyricsDoc = lyricsByFolderAndBase[key]
                    val lyricsContent = matchingLyricsDoc?.let { readTextFromUri(context, it.uri) }

                    val meta = extractAudioMetadataFast(
                        context = context,
                        uri = doc.uri,
                        directFile = doc.directFile,
                        fallbackTitle = doc.baseName,
                        fallbackFolder = doc.folderName,
                        indexSeed = idx
                    )

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
                        coLocatedTtmlUri = matchingLyricsDoc?.uri?.toString(),
                        embeddedTtmlContent = lyricsContent,
                        coverColorsCsv = meta.paletteCsv,
                        coverArtPath = meta.savedCoverPath,
                        isBuiltInStudioTrack = false
                    )
                }
            }.awaitAll()

            allTracks.addAll(batchTracks)
            onBatchReady(batchTracks, allTracks.size, total)
        }

        allTracks
    }

    fun readTextFromUri(context: Context, uri: Uri): String? {
        return try {
            if (uri.scheme == "file") {
                File(uri.path ?: return null).takeIf { it.exists() }?.readText()
            } else {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }
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

    private fun extractAudioMetadataFast(
        context: Context,
        uri: Uri,
        directFile: File?,
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
            if (directFile != null && directFile.exists()) {
                retriever.setDataSource(directFile.absolutePath)
            } else {
                retriever.setDataSource(context, uri)
            }
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

            val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
            val coverFile = File(coversDir, "cover_${uri.toString().hashCode()}.jpg")

            val artBytes = retriever.embeddedPicture
            if (artBytes != null && artBytes.isNotEmpty()) {
                // Fast downsampled decode (avoids 3000x3000 bitmap allocation lag!)
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size, boundsOpts)
                var sampleSize = 1
                while (boundsOpts.outWidth / sampleSize > 512 || boundsOpts.outHeight / sampleSize > 512) {
                    sampleSize *= 2
                }
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val bmp = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size, decodeOpts)
                if (bmp != null) {
                    paletteCsv = sampleNineColorPaletteFromBitmap(bmp)
                    if (!coverFile.exists()) {
                        FileOutputStream(coverFile).use { fos ->
                            bmp.compress(Bitmap.CompressFormat.JPEG, 82, fos)
                        }
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

    /**
     * Samples 9 distinct regions across the album cover (corners, mid-edges, center)
     * so the animated background has 9 lush, multi-colored album-art orbs!
     */
    private fun sampleNineColorPaletteFromBitmap(bitmap: Bitmap): String {
        val samplePoints = listOf(
            0.18f to 0.18f,
            0.50f to 0.16f,
            0.82f to 0.20f,
            0.20f to 0.50f,
            0.50f to 0.50f,
            0.80f to 0.52f,
            0.22f to 0.82f,
            0.52f to 0.84f,
            0.82f to 0.80f
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
