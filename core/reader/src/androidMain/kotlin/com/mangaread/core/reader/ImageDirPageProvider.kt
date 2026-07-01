package com.mangaread.core.reader

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.mangaread.core.source.MangaSource
import com.mangaread.core.source.SourceEntry
import kotlinx.coroutines.runBlocking
import okio.buffer

private fun String.isImageName(): Boolean =
    substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "avif", "bmp")

/** One chapter = a folder of naturally-sorted images (PLAN.md §11). */
class ImageDirPageProvider(
    private val dirLocator: String,
    private val source: MangaSource,
) : PageProvider {

    private val pages: List<SourceEntry> = runBlocking {
        source.list(dirLocator).filter { !it.isDirectory && it.name.isImageName() }.sortedBy { it.name }
    }

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
}
