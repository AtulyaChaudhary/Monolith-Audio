package com.example.ui.theme

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import java.io.File
import java.io.FileOutputStream

/**
 * Manages user-uploaded custom `.ttf` / `.otf` fonts for:
 * 1. The Entire UI (`custom_ui_font.ttf`)
 * 2. Synced Lyrics Stage (`custom_lyrics_font.ttf`)
 */
object CustomFontManager {

    data class UploadedFontResult(
        val filePath: String,
        val displayName: String
    )

    fun copyAndValidateFont(
        context: Context,
        uri: Uri,
        targetSlot: String // "ui" or "lyrics"
    ): UploadedFontResult? {
        val resolver = context.contentResolver
        var originalName = "$targetSlot.ttf"
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) {
                originalName = c.getString(idx) ?: originalName
            }
        }

        val fontsDir = File(context.filesDir, "custom_fonts").apply { mkdirs() }
        val ext = originalName.substringAfterLast('.', "ttf").lowercase().let {
            if (it == "otf" || it == "ttf") it else "ttf"
        }
        val destFile = File(fontsDir, "custom_${targetSlot}_font.$ext")

        return try {
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            // Validate with Android Typeface to ensure the uploaded file is a valid TTF/OTF font
            val tf = Typeface.createFromFile(destFile)
            if (tf == null || tf == Typeface.DEFAULT) {
                // Still allow if non-empty and valid header
                if (destFile.length() < 128L) {
                    destFile.delete()
                    return null
                }
            }
            UploadedFontResult(
                filePath = destFile.absolutePath,
                displayName = originalName
            )
        } catch (_: Exception) {
            destFile.delete()
            null
        }
    }

    fun loadFontFamilyFromFile(path: String?): FontFamily? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists() || file.length() < 128L) return null
        return try {
            // Validate first
            Typeface.createFromFile(file)
            FontFamily(
                Font(file = file, weight = FontWeight.Normal),
                Font(file = file, weight = FontWeight.Medium),
                Font(file = file, weight = FontWeight.SemiBold),
                Font(file = file, weight = FontWeight.Bold)
            )
        } catch (_: Exception) {
            null
        }
    }
}
