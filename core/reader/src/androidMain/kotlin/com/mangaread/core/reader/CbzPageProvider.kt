package com.mangaread.core.reader

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.mangaread.core.source.MangaSource
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
 *
 * The entry list is resolved by [create] (suspend) rather than the constructor, so building a
 * provider never blocks a calling thread — a plain blocking constructor here previously froze
 * the caller (e.g. the series screen counting pages for many chapters at once) since opening
 * and scanning the archive is real I/O.
 */
class CbzPageProvider private constructor(
    private val entryNames: List<String>,
    private val cbzLocator: String,
    private val source: MangaSource,
) : PageProvider {

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

    /** Re-scans the archive each call — ZipInputStream is forward-only; chapters are small. */
    private suspend fun readEntry(name: String): ByteArray {
        var result: ByteArray? = null
        withZip(cbzLocator, source) { zis ->
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

    companion object {
        suspend fun create(cbzLocator: String, source: MangaSource): CbzPageProvider {
            val names = mutableListOf<String>()
            withZip(cbzLocator, source) { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.isImageName()) names += entry.name
                    entry = zis.nextEntry
                }
            }
            return CbzPageProvider(names.sorted(), cbzLocator, source)
        }

        private suspend fun withZip(cbzLocator: String, source: MangaSource, block: (ZipInputStream) -> Unit) {
            val bytes = source.open(cbzLocator).buffer().use { it.readByteArray() }
            ZipInputStream(ByteArrayInputStream(bytes)).use(block)
        }
    }
}
