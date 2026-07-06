package com.oliver.heyme.mangazuki.core.scanner

import com.oliver.heyme.mangazuki.core.domain.Chapter
import com.oliver.heyme.mangazuki.core.domain.ChapterFormat
import com.oliver.heyme.mangazuki.core.domain.Series
import com.oliver.heyme.mangazuki.core.domain.deterministicId
import com.oliver.heyme.mangazuki.core.domain.normalizeSortTitle
import com.oliver.heyme.mangazuki.core.source.MangaSource
import com.oliver.heyme.mangazuki.core.source.SourceEntry
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
 *   <root>/<Chapter>.cbz                      → CBZ chapter grouped by ComicInfo.xml <Series>,
 *                                                falling back to the file's own name (see below)
 */
class LibraryScanner(private val source: MangaSource) {

    fun scan(rootLocator: String, now: Long): Flow<ScannedSeries> = flow {
        val rootEntries = source.list(rootLocator).filterNot { it.name.startsWith(".") }

        // Skip hidden folders (e.g. Resilio's ".sync", ".thumbnails").
        for (dir in rootEntries.filter { it.isDirectory }) {
            val seriesId = deterministicId(source.id, dir.locator)

            val children = source.list(dir.locator)
            val chapterEntries = children.filter { it.isDirectory || it.name.isCbz() }
            val directImages = children.count { !it.isDirectory && it.name.isImage() }

            val chapters = when {
                chapterEntries.isEmpty() && directImages > 0 ->
                    listOf(imageDirChapter(seriesId, dir, dir.name, directImages, now))

                else -> chapterEntries.mapNotNull { entry ->
                    if (entry.isDirectory) {
                        val pages = source.list(entry.locator).count { it.name.isImage() }
                        // A subfolder with no images isn't a chapter (e.g. an "Archive" folder).
                        if (pages == 0) null else imageDirChapter(seriesId, entry, entry.name, pages, now)
                    } else {
                        cbzChapter(seriesId, entry, now)
                    }
                }
            }

            // Don't add a series with no chapters/volumes (empty or non-manga folders).
            if (chapters.isEmpty()) continue

            emit(
                ScannedSeries(
                    Series(
                        id = seriesId,
                        title = dir.name,
                        sortTitle = normalizeSortTitle(dir.name),
                        dateAdded = now,
                        lastScanned = now,
                    ),
                    chapters,
                ),
            )
        }

        // Files sitting directly in the configured root, with no containing series folder, still
        // count -- grouped by their own ComicInfo.xml <Series> title when present (so several
        // loose chapter files of the same series land together, not one series per file), falling
        // back to each file's own name (extension stripped) only when there's no usable metadata.
        // Unlike a series folder's first-CBZ-only sniff (comicInfoSeriesTitle's own contract),
        // every root-level file is checked -- there's no folder name to fall back to instead.
        val chaptersByRootTitle = mutableMapOf<String, MutableList<Chapter>>()
        for (entry in rootEntries.filter { !it.isDirectory && it.name.isCbz() }) {
            val title = comicInfoSeriesTitle(entry.locator)?.takeIf { it.isNotBlank() } ?: stripCbzExtension(entry.name)
            val seriesId = deterministicId(source.id, normalizeSortTitle(title))
            chaptersByRootTitle.getOrPut(title) { mutableListOf() } += cbzChapter(seriesId, entry, now)
        }
        for ((title, chapters) in chaptersByRootTitle) {
            emit(
                ScannedSeries(
                    Series(
                        id = deterministicId(source.id, normalizeSortTitle(title)),
                        title = title,
                        sortTitle = normalizeSortTitle(title),
                        dateAdded = now,
                        lastScanned = now,
                    ),
                    chapters,
                ),
            )
        }
    }

    /**
     * Best-effort series name from [cbzLocator]'s `ComicInfo.xml` sidecar. [LibrarySyncer] calls
     * this only when a series is discovered for the very first time, against only that series'
     * first CBZ chapter -- never on a later rescan, and never checking a second CBZ if the first
     * has no usable `ComicInfo.xml`, so an established title can never flip-flop or cost a scan
     * more than one extra file read. Null if there's no `ComicInfo.xml`, no `<Series>` element,
     * or the file can't be read at all.
     */
    suspend fun comicInfoSeriesTitle(cbzLocator: String): String? =
        readComicInfoXml(source, cbzLocator)?.let(::parseComicInfoSeriesTitle)

    private fun imageDirChapter(seriesId: String, e: SourceEntry, name: String, pages: Int, now: Long): Chapter {
        val parsed = FilenameParser.parse(name)
        return Chapter(
            id = deterministicId(source.id, e.locator),
            seriesId = seriesId,
            sourceId = source.id,
            locator = e.locator,
            format = ChapterFormat.IMAGE_DIR,
            displayName = cleanDisplayName(name),
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
            displayName = cleanDisplayName(e.name),
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

/** A root-level CBZ's fallback series title (only used when it has no usable `ComicInfo.xml`):
 * its own filename, extension stripped -- a stray "." inside a name like "Vol. 01.cbz" survives
 * since only the trailing `.cbz` is removed. */
private fun stripCbzExtension(rawName: String): String =
    if (rawName.isCbz()) rawName.substringBeforeLast('.') else rawName

/**
 * Chapter card/reader-title text (PLAN.md §7.3) — a raw filename like "chaper_18.5.cbz" reads
 * poorly as a title. Strips the archive extension (only when actually one — an IMAGE_DIR
 * chapter's folder name never has one, and a stray "." inside a folder name like "Vol. 01"
 * must survive), turns underscores into spaces, and capitalizes the first letter. Deliberately
 * separate from [FilenameParser]: that parser's job is extracting volume/chapter *numbers* (and
 * its `seriesTitle` strips the chapter token/number entirely, which would leave this blank).
 */
internal fun cleanDisplayName(rawName: String): String {
    val noExt = stripCbzExtension(rawName)
    val spaced = noExt.replace('_', ' ').trim()
    return if (spaced.isEmpty()) spaced else spaced[0].uppercase() + spaced.substring(1)
}
