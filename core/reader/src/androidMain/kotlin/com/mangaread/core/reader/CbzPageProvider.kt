package com.mangaread.core.reader

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.mangaread.core.source.MangaSource
import kotlinx.coroutines.runBlocking
import okio.buffer
import java.io.ByteArrayInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

private fun String.isImageName(): Boolean =
    substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "avif", "bmp")

/**
 * CBZ = sorted ZIP of images (PLAN.md §11). The archive's central directory sits at the
 * end, so random access needs a seekable local handle; on a real cloud source (Phase 4)
 * this provider would need a local-temp copy first. Fine for LocalFileSource today.
 */
class CbzPageProvider(
    private val cbzLocator: String,
    private val source: MangaSource,
) : PageProvider {

    private val entryNames: List<String> = runBlocking { listImageEntries() }

    override val pageCount: Int get() = entryNames.size

    override suspend fun loadPage(index: Int, target: PageTarget): ImageBitmap {
        val bytes = readEntry(entryNames[index])
        return decodeSampled(bytes, target.maxWidthPx, target.maxHeightPx).asImageBitmap()
    }

    override suspend fun pageSize(index: Int): Size {
        val bytes = readEntry(entryNames[index])
        return decodeBoundsSize(bytes)
    }

    override fun close() {}

    private suspend fun listImageEntries(): List<String> {
        val names = mutableListOf<String>()
        withZip { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.isImageName()) names += entry.name
                entry = zis.nextEntry
            }
        }
        return names.sorted()
    }

    /** Re-scans the archive each call — ZipInputStream is forward-only; chapters are small. */
    private suspend fun readEntry(name: String): ByteArray {
        var result: ByteArray? = null
        withZip { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                if (entry.name == name) {
                    result = zis.readBytes()
                    break
                }
                entry = zis.nextEntry
            }
        }
        return result ?: error("entry $name not found in $cbzLocator")
    }

    private suspend fun withZip(block: (ZipInputStream) -> Unit) {
        val bytes = source.open(cbzLocator).buffer().use { it.readByteArray() }
        ZipInputStream(ByteArrayInputStream(bytes)).use(block)
    }
}
