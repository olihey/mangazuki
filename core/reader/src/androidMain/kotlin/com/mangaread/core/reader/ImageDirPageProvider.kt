package com.mangaread.core.reader

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.mangaread.core.source.MangaSource
import com.mangaread.core.source.SourceEntry
import okio.buffer

private fun String.isImageName(): Boolean =
    substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "avif", "bmp")

/**
 * One chapter = a folder of naturally-sorted images (PLAN.md §11). The page list is resolved by
 * [create] (suspend) rather than the constructor, so building a provider never blocks a calling
 * thread — a plain blocking constructor here previously froze the caller (e.g. the series screen
 * counting pages for many chapters at once) since directory listing is real I/O.
 */
class ImageDirPageProvider private constructor(
    private val pages: List<SourceEntry>,
    private val source: MangaSource,
) : PageProvider {

    override val pageCount: Int get() = pages.size

    override suspend fun loadPage(index: Int, target: PageTarget): ImageBitmap {
        val bytes = source.open(pages[index].locator).buffer().use { it.readByteArray() }
        return decodeSampled(bytes, target.maxWidthPx, target.maxHeightPx).asImageBitmap()
    }

    override suspend fun pageSize(index: Int): Size {
        val bytes = source.open(pages[index].locator).buffer().use { it.readByteArray() }
        return decodeBoundsSize(bytes)
    }

    override fun close() {}

    companion object {
        suspend fun create(dirLocator: String, source: MangaSource): ImageDirPageProvider {
            val pages = source.list(dirLocator).filter { !it.isDirectory && it.name.isImageName() }.sortedBy { it.name }
            return ImageDirPageProvider(pages, source)
        }
    }
}
