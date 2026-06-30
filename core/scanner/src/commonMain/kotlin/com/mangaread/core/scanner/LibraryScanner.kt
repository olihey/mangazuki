package com.mangaread.core.scanner

import com.mangaread.core.domain.Chapter
import com.mangaread.core.domain.ChapterFormat
import com.mangaread.core.domain.Series
import com.mangaread.core.domain.deterministicId
import com.mangaread.core.domain.normalizeSortTitle
import com.mangaread.core.source.MangaSource
import com.mangaread.core.source.SourceEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** One scanned series and its chapters, emitted as the scan progresses. */
data class ScannedSeries(val series: Series, val chapters: List<Chapter>)

/**
 * Walks a library root via [MangaSource.list] and EMITS one [ScannedSeries] per top-level
 * folder as it goes (PLAN.md §5), so a large library can be persisted incrementally and the
 * UI fills in live instead of waiting for the whole tree. Deterministic IDs make re-scans
 * reconcile. Layout convention:
 *   <root>/<Series>/<Chapter dir of images>   → IMAGE_DIR chapter
 *   <root>/<Series>/<Chapter>.cbz             → CBZ chapter
 *   <root>/<Series>/<images directly>         → one IMAGE_DIR chapter (the series folder)
 */
class LibraryScanner(private val source: MangaSource) {

    fun scan(rootLocator: String, now: Long): Flow<ScannedSeries> = flow {
        for (dir in source.list(rootLocator).filter { it.isDirectory }) {
            val seriesId = deterministicId(source.id, dir.locator)
            val series = Series(
                id = seriesId,
                title = dir.name,
                sortTitle = normalizeSortTitle(dir.name),
                dateAdded = now,
                lastScanned = now,
            )

            val children = source.list(dir.locator)
            val chapterEntries = children.filter { it.isDirectory || it.name.isCbz() }
            val directImages = children.count { !it.isDirectory && it.name.isImage() }

            val chapters = when {
                chapterEntries.isEmpty() && directImages > 0 ->
                    listOf(imageDirChapter(seriesId, dir, dir.name, directImages, now))

                else -> chapterEntries.map { entry ->
                    if (entry.isDirectory) {
                        val pages = source.list(entry.locator).count { it.name.isImage() }
                        imageDirChapter(seriesId, entry, entry.name, pages, now)
                    } else {
                        cbzChapter(seriesId, entry, now)
                    }
                }
            }
            emit(ScannedSeries(series, chapters))
        }
    }

    private fun imageDirChapter(seriesId: String, e: SourceEntry, name: String, pages: Int, now: Long): Chapter {
        val parsed = FilenameParser.parse(name)
        return Chapter(
            id = deterministicId(source.id, e.locator),
            seriesId = seriesId,
            sourceId = source.id,
            locator = e.locator,
            format = ChapterFormat.IMAGE_DIR,
            displayName = name,
            volume = parsed.volume,
            number = parsed.number,
            pageCount = pages,
            size = e.size,
            changeToken = e.changeToken,
            dateAdded = now,
        )
    }

    private fun cbzChapter(seriesId: String, e: SourceEntry, now: Long): Chapter {
        val parsed = FilenameParser.parse(e.name)
        return Chapter(
            id = deterministicId(source.id, e.locator),
            seriesId = seriesId,
            sourceId = source.id,
            locator = e.locator,
            format = ChapterFormat.CBZ,
            displayName = e.name,
            volume = parsed.volume,
            number = parsed.number,
            pageCount = null,           // counted when the CBZ provider is built (Phase 2)
            size = e.size,
            changeToken = e.changeToken,
            dateAdded = now,
        )
    }
}

private fun String.ext() = substringAfterLast('.', "").lowercase()
private fun String.isCbz() = ext() == "cbz"
private fun String.isImage() = ext() in setOf("jpg", "jpeg", "png", "webp", "gif", "avif", "bmp")
