package com.mangaread.core.scanner

import com.mangaread.core.domain.Chapter
import com.mangaread.core.domain.ChapterFormat
import com.mangaread.core.domain.Series
import com.mangaread.core.domain.deterministicId
import com.mangaread.core.domain.normalizeSortTitle
import com.mangaread.core.source.MangaSource
import com.mangaread.core.source.SourceEntry

data class ScanResult(val series: List<Series>, val chapters: List<Chapter>)

/**
 * Walks a library root via [MangaSource.list] and produces domain Series + Chapter rows
 * with deterministic IDs (PLAN.md §5). Pure orchestration — platform file access lives behind
 * the source. Layout convention:
 *   <root>/<Series>/<Chapter dir of images>      → IMAGE_DIR chapter
 *   <root>/<Series>/<Chapter>.cbz                 → CBZ chapter
 *   <root>/<Series>/<images directly>             → one IMAGE_DIR chapter (the series folder)
 */
class LibraryScanner(private val source: MangaSource) {

    suspend fun scan(rootLocator: String, now: Long): ScanResult {
        val series = mutableListOf<Series>()
        val chapters = mutableListOf<Chapter>()

        for (dir in source.list(rootLocator).filter { it.isDirectory }) {
            val seriesId = deterministicId(source.id, dir.locator)
            series += Series(
                id = seriesId,
                title = dir.name,
                sortTitle = normalizeSortTitle(dir.name),
                dateAdded = now,
                lastScanned = now,
            )

            val children = source.list(dir.locator)
            val chapterEntries = children.filter { it.isDirectory || it.name.isCbz() }
            val directImages = children.count { !it.isDirectory && it.name.isImage() }

            if (chapterEntries.isEmpty() && directImages > 0) {
                // The series folder itself is a single image chapter.
                chapters += imageDirChapter(seriesId, dir, dir.name, directImages, now)
            } else {
                for (entry in chapterEntries) {
                    chapters += if (entry.isDirectory) {
                        val pages = source.list(entry.locator).count { it.name.isImage() }
                        imageDirChapter(seriesId, entry, entry.name, pages, now)
                    } else {
                        cbzChapter(seriesId, entry, now)
                    }
                }
            }
        }
        return ScanResult(series, chapters)
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
