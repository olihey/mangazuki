package com.mangaread

import com.mangaread.core.data.ChapterCard
import com.mangaread.core.data.LibraryRepository
import com.mangaread.core.domain.Chapter as DomainChapter
import com.mangaread.core.domain.ChapterFormat
import com.mangaread.core.domain.Series as DomainSeries
import com.mangaread.core.reader.pageProviderFor
import com.mangaread.core.source.MangaSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class SeriesViewModel(
    private val repository: LibraryRepository,
    private val source: MangaSource,
    val seriesId: String,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    val series: StateFlow<DomainSeries?> =
        repository.observeSeries(seriesId).stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    /** Ascending (Chapter 1 first); volume/number order, flat grid regardless of volume (PLAN.md §7.3). */
    val chapters: StateFlow<List<ChapterCard>> =
        repository.observeChapters(seriesId).stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Multi-select chapters for bulk read/unread (PLAN.md §7.5). */
    val selectionMode = MutableStateFlow(false)
    val selectedIds = MutableStateFlow<Set<String>>(emptySet())

    /** Chapter ids already counted or currently being counted, so [chapters] re-emitting after a
     * write-back doesn't re-trigger the same chapter (PLAN.md §9: covers/counts are on-demand). */
    private val pageCountAttempted = mutableSetOf<String>()

    /** Caps how many chapters get counted at once — counting a CBZ means reading the whole
     * archive, and a series can have hundreds of chapters missing a count after a fresh scan. */
    private val pageCountLimiter = Semaphore(4)

    init {
        scope.launch {
            chapters.collect { list ->
                list.filter { it.pageCount == null && it.id !in pageCountAttempted }.forEach { chapter ->
                    pageCountAttempted += chapter.id
                    scope.launch { countPages(chapter) }
                }
            }
        }
    }

    fun toggleRead(chapter: ChapterCard) {
        scope.launch {
            val nowCompleted = !chapter.completed
            val lastPage = if (nowCompleted) (chapter.pageCount ?: 1) - 1 else 0
            repository.markProgress(chapter.id, lastPage.coerceAtLeast(0), nowCompleted)
        }
    }

    fun enterSelectionMode(chapterId: String) {
        selectionMode.value = true
        selectedIds.value = setOf(chapterId)
    }

    fun toggleSelected(chapterId: String) {
        selectedIds.value = if (chapterId in selectedIds.value) selectedIds.value - chapterId else selectedIds.value + chapterId
        if (selectedIds.value.isEmpty()) selectionMode.value = false
    }

    fun selectAll() { selectedIds.value = chapters.value.map { it.id }.toSet() }
    fun selectNone() { selectedIds.value = emptySet() }

    fun exitSelectionMode() {
        selectionMode.value = false
        selectedIds.value = emptySet()
    }

    fun markSelectedRead(completed: Boolean) {
        val entries = chapters.value.filter { it.id in selectedIds.value }.map { it.id to it.pageCount }
        scope.launch {
            repository.markChaptersProgress(entries, completed)
            exitSelectionMode()
        }
    }

    /** Same PageProvider the reader uses, just to read `.pageCount` — cheap (entry names/dir
     * listing only, no image bytes) and reuses the existing CBZ/image-dir abstraction directly. */
    private suspend fun countPages(chapter: ChapterCard) {
        val domainChapter = DomainChapter(
            id = chapter.id,
            seriesId = chapter.seriesId,
            sourceId = chapter.sourceId,
            locator = chapter.locator,
            format = ChapterFormat.valueOf(chapter.format),
            displayName = chapter.displayName,
            volume = chapter.volume,
            number = chapter.number,
            pageCount = null,
            dateAdded = 0L,
        )
        try {
            pageCountLimiter.withPermit {
                val provider = pageProviderFor(domainChapter, source)
                val count = provider.pageCount
                provider.close()
                if (count > 0) repository.setChapterPageCount(chapter.id, count)
            }
        } catch (t: Throwable) {
            // Best-effort — leave pageCount null; the read-percentage overlay just won't show.
        }
    }
}
