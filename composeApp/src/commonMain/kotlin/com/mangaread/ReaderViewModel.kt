package com.mangaread

import com.mangaread.core.data.ChapterCard
import com.mangaread.core.data.LibraryRepository
import com.mangaread.core.domain.Chapter as DomainChapter
import com.mangaread.core.domain.ChapterFormat
import com.mangaread.core.reader.pageProviderFor
import com.mangaread.core.source.MangaSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** The pager only knows "page N of a chapter" (PLAN.md §8); loading a bitmap per page is
 * delegated to Coil ([MangaPage]/`PageFetcher`) for its caching/prefetch, while [PageProvider]
 * supplies the authoritative page count. */
class ReaderViewModel(
    private val repository: LibraryRepository,
    source: MangaSource,
    val chapter: ChapterCard,
    val readingDirectionRtl: Boolean,
    private val prefs: ReaderPreferences,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    val pageModel: String = if (chapter.format == "CBZ") "cbz:${chapter.locator}" else "imgdir:${chapter.locator}"

    private val _pageCount = MutableStateFlow(chapter.pageCount ?: 0)
    val pageCount: StateFlow<Int> = _pageCount

    val currentPage = MutableStateFlow(chapter.lastPageIndex.coerceAtLeast(0))

    /** One-time overlay explaining tap zones (PLAN.md §8.1); dismiss persists so it shows once. */
    private val _showGestureHelp = MutableStateFlow(!prefs.hasSeenGestureHelp)
    val showGestureHelp: StateFlow<Boolean> = _showGestureHelp

    fun dismissGestureHelp() {
        prefs.hasSeenGestureHelp = true
        _showGestureHelp.value = false
    }

    init {
        scope.launch {
            val domainChapter = DomainChapter(
                id = chapter.id,
                seriesId = chapter.seriesId,
                sourceId = chapter.sourceId,
                locator = chapter.locator,
                format = ChapterFormat.valueOf(chapter.format),
                displayName = chapter.displayName,
                volume = chapter.volume,
                number = chapter.number,
                pageCount = chapter.pageCount,
                dateAdded = 0L,
            )
            val provider = pageProviderFor(domainChapter, source)
            _pageCount.value = provider.pageCount
            provider.close()
        }
    }

    fun onPageChanged(index: Int) {
        currentPage.value = index
        val count = _pageCount.value
        val completed = count > 0 && index >= count - 1
        scope.launch { repository.markProgress(chapter.id, index, completed) }
    }
}
