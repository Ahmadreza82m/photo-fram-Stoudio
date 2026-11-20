package com.photoframe.studio.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile

class SafImageRepository(private val context: Context) {
    private val extensions = setOf("jpg", "jpeg", "png", "webp", "bmp")

    fun imageInfo(uri: Uri): SelectedImage {
        var name = "image"
        var size = 0L
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val si = c.getColumnIndex(OpenableColumns.SIZE)
                if (ni >= 0) name = c.getString(ni) ?: name
                if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
            }
        }
        return SelectedImage(uri, name, size)
    }

    fun scanRecursively(tree: Uri): List<SelectedImage> {
        val root = DocumentFile.fromTreeUri(context, tree) ?: return emptyList()
        val result = mutableListOf<SelectedImage>()
        val pending = ArrayDeque<DocumentFile>()
        pending.add(root)
        while (pending.isNotEmpty()) {
            pending.removeLast().listFiles().forEach { file ->
                when {
                    file.isDirectory -> pending.add(file)
                    file.isFile && file.name?.substringAfterLast('.', "")?.lowercase() in extensions ->
                        result.add(SelectedImage(file.uri, file.name ?: "image", file.length()))
                }
            }
        }
        return result
    }

    fun deleteOutput(uri: Uri) { DocumentFile.fromSingleUri(context, uri)?.delete() }

    fun createUniqueOutput(folder: Uri, sourceName: String, format: ExportFormat): Uri {
        val directory = DocumentFile.fromTreeUri(context, folder) ?: error("پوشه خروجی در دسترس نیست")
        val base = sourceName.substringBeforeLast('.', sourceName).replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "image" }
        val ext = if (format == ExportFormat.PNG) "png" else "jpg"
        var name = "$base.$ext"
        var n = 1
        while (directory.findFile(name)?.exists() == true) {
            name = "$base ($n).$ext"
            n++
        }
        return (directory.createFile(if (ext == "png") "image/png" else "image/jpeg", name)
            ?: error("ساخت فایل خروجی ممکن نشد")).uri
    }
}
