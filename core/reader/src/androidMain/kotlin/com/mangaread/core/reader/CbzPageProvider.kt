package com.mangaread.core.reader

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.mangaread.core.source.MangaSource
import okio.buffer
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

private fun String.isImageName(): Boolean =
    substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "avif", "bmp")

/**
 * CBZ = sorted ZIP of images (PLAN.md §11). The archive's central directory sits at the
 * end, so random access needs a seekable local handle; on a real cloud source (Phase 4)
 * this provider would need a local-temp copy first. Fine for LocalFileSource today.
 *
 * [create] (suspend) reads the whole archive into memory ONCE — bounded, since only one
 * chapter is ever open in the reader at a time (PLAN.md §13's 50MB worst-case CBZ) — and
 * records each wanted entry's byte offset while doing the single forward pass needed to list
 * them anyway. Every later [loadPage]/[pageSize] then seeks straight to that offset in the
 * in-memory buffer instead of re-scanning from byte zero. Re-scanning per call used to make
 * opening a big chapter O(n²) (`ReaderViewModel` probes `pageSize` for every page up front to
 * build the spread-pairing list) — don't reintroduce that.
 */
class CbzPageProvider private constructor(
    private val bytes: ByteArray,
    private val entries: List<IndexedEntry>,
) : PageProvider {

    private data class IndexedEntry(val name: String, val offset: Int)

    override val pageCount: Int get() = entries.size

    override suspend fun loadPage(index: Int, target: PageTarget): ImageBitmap {
        val data = readEntryAt(entries[index].offset)
        return decodeSampled(data, target.maxWidthPx, target.maxHeightPx).asImageBitmap()
    }

    override suspend fun pageSize(index: Int): Size {
        val data = readEntryAt(entries[index].offset)
        return decodeBoundsSize(data)
    }

    override fun close() {}

    /** O(1): the offset already points at that entry's local file header. */
    private fun readEntryAt(offset: Int): ByteArray {
        ZipInputStream(ByteArrayInputStream(bytes, offset, bytes.size - offset)).use { zis ->
            zis.nextEntry ?: error("no zip entry at offset $offset")
            return zis.readBytes()
        }
    }

    companion object {
        suspend fun create(cbzLocator: String, source: MangaSource): CbzPageProvider {
            val bytes = source.open(cbzLocator).buffer().use { it.readByteArray() }
            val entries = mutableListOf<IndexedEntry>()
            val counted = CountingInputStream(ByteArrayInputStream(bytes))
            ZipInputStream(counted).use { zis ->
                while (true) {
                    val offset = counted.count
                    val entry: ZipEntry = zis.nextEntry ?: break
                    if (!entry.isDirectory && entry.name.isImageName()) entries += IndexedEntry(entry.name, offset)
                }
            }
            return CbzPageProvider(bytes, entries.sortedBy { it.name })
        }
    }
}

/** Tracks bytes consumed so far, so the offset just before each `nextEntry()` call is that
 * entry's local file header position in [bytes] — `getNextEntry()` skips any unread tail of
 * the previous entry first, so this lands exactly right. */
private class CountingInputStream(private val delegate: InputStream) : InputStream() {
    var count = 0
        private set

    override fun read(): Int {
        val b = delegate.read()
        if (b != -1) count++
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = delegate.read(b, off, len)
        if (n > 0) count += n
        return n
    }

    override fun close() = delegate.close()
}
