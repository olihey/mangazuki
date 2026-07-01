package com.mangaread

import com.mangaread.core.data.ChapterCard
import com.mangaread.core.data.LibraryRepository
import com.mangaread.core.domain.Series as DomainSeries
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SeriesViewModel(
    private val repository: LibraryRepository,
    val seriesId: String,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    val series: StateFlow<DomainSeries?> =
        repository.observeSeries(seriesId).stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    /** Ascending (Chapter 1 first), grouped by volume in the UI (PLAN.md §7.3). */
    val chapters: StateFlow<List<ChapterCard>> =
        repository.observeChapters(seriesId).stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleRead(chapter: ChapterCard) {
        scope.launch {
            val nowCompleted = !chapter.completed
            val lastPage = if (nowCompleted) (chapter.pageCount ?: 1) - 1 else 0
            repository.markProgress(chapter.id, lastPage.coerceAtLeast(0), nowCompleted)
        }
    }
}
