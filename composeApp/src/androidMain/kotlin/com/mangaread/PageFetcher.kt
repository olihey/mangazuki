package com.mangaread

import android.content.Context
import android.net.Uri
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import okio.Buffer
import okio.BufferedSource
import okio.FileSystem
import okio.buffer
import okio.source
import java.util.zip.ZipInputStream

/** Resolves one page of a chapter (image dir or CBZ) into image bytes for the reader pager. */
class PageFetcher(
    private val page: MangaPage,
    private val context: Context,
    private val source: SafMangaSource,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val locator = page.model.substringAfter(':')
        val bytes: BufferedSource = when {
            page.model.startsWith("cbz:") -> cbzImageAt(locator, page.index)
            page.model.startsWith("imgdir:") -> folderImageAt(locator, page.index)
            else -> error("unsupported page model: ${page.model}")
        }
        return SourceFetchResult(
            source = ImageSource(bytes, FileSystem.SYSTEM),
            mimeType = null,
            dataSource = DataSource.DISK,
        )
    }

    private suspend fun folderImageAt(dirLocator: String, index: Int): BufferedSource {
        val entry = source.list(dirLocator)
            .filter { !it.isDirectory && it.name.isImageName() }
            .sortedBy { it.name }
            .getOrNull(index)
            ?: error("no page $index in $dirLocator")
        val stream = context.contentResolver.openInputStream(Uri.parse(entry.locator))
            ?: error("cannot open ${entry.locator}")
        return stream.source().buffer()
    }

    private fun cbzImageAt(cbzLocator: String, index: Int): BufferedSource {
        val names = mutableListOf<String>()
        context.contentResolver.openInputStream(Uri.parse(cbzLocator))?.use { input ->
            ZipInputStream(input.buffered()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.isImageName()) names += entry.name
                    entry = zis.nextEntry
                }
            }
        } ?: error("cannot open $cbzLocator")
        val target = names.sorted().getOrNull(index) ?: error("no page $index in $cbzLocator")

        val input = context.contentResolver.openInputStream(Uri.parse(cbzLocator))
            ?: error("cannot open $cbzLocator")
        ZipInputStream(input.buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == target) return Buffer().apply { write(zis.readBytes()) }
                entry = zis.nextEntry
            }
        }
        error("no page $index in $cbzLocator")
    }

    class Factory(
        private val context: Context,
        private val source: SafMangaSource,
    ) : Fetcher.Factory<MangaPage> {
        override fun create(data: MangaPage, options: Options, imageLoader: ImageLoader): Fetcher =
            PageFetcher(data, context, source)
    }
}

private fun String.isImageName(): Boolean =
    substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "avif", "bmp")
