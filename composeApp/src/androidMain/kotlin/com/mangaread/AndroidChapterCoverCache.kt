package com.mangaread

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.mangaread.core.domain.Chapter
import com.mangaread.core.domain.ChapterFormat
import com.mangaread.core.domain.ioDispatcher
import com.mangaread.core.reader.decodeSampled
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Extracts and caches one chapter's first-page cover to app-internal storage (PLAN.md §9),
 * keyed by chapter id so it survives app restarts and is never written under a user-granted
 * root (which can disappear). Called once per chapter right after it's persisted by a scan.
 */
class AndroidChapterCoverCache(
    private val context: Context,
    private val source: SafMangaSource,
) : ChapterCoverCache {

    private val dir by lazy { File(context.filesDir, "chapter_covers").apply { mkdirs() } }

    override suspend fun ensureCover(chapter: Chapter): String? = withContext(ioDispatcher) {
        val file = File(dir, "${chapter.id}.jpg")
        if (file.exists() && file.length() > 0L) return@withContext file.absolutePath
        val bytes = firstPageBytes(chapter) ?: return@withContext null
        try {
            val bitmap = decodeSampled(bytes, 480, 720)
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out) }
            file.absolutePath
        } catch (t: Throwable) {
            android.util.Log.w("ChapterCoverCache", "cover generation failed for ${chapter.id}", t)
            null
        }
    }

    private suspend fun firstPageBytes(chapter: Chapter): ByteArray? = when (chapter.format) {
        ChapterFormat.IMAGE_DIR -> firstFolderImageBytes(chapter.locator)
        ChapterFormat.CBZ -> firstCbzImageBytes(chapter.locator)
    }

    private suspend fun firstFolderImageBytes(dirLocator: String): ByteArray? {
        val first = source.list(dirLocator)
            .filter { !it.isDirectory && it.name.isImageName() }
            .minByOrNull { it.name }
            ?: return null
        return context.contentResolver.openInputStream(Uri.parse(first.locator))?.use { it.readBytes() }
    }

    private fun firstCbzImageBytes(cbzLocator: String): ByteArray? {
        val input = context.contentResolver.openInputStream(Uri.parse(cbzLocator)) ?: return null
        ZipInputStream(input.buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.isImageName()) return zis.readBytes()
                entry = zis.nextEntry
            }
        }
        return null
    }
}

private fun String.isImageName(): Boolean =
    substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "avif", "bmp")
