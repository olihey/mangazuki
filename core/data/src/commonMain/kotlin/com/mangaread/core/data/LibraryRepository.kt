package com.mangaread.core.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.mangaread.core.data.db.MangaDatabase
import com.mangaread.core.domain.Chapter as DomainChapter
import com.mangaread.core.domain.ReadingDirection
import com.mangaread.core.domain.Series as DomainSeries
import com.mangaread.core.domain.ioDispatcher
import com.mangaread.core.data.db.Series as SeriesRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class LibraryRepository(db: MangaDatabase) {

    private val q = db.schemaQueries

    fun observeSeries(): Flow<List<DomainSeries>> =
        q.selectAllSeries().asFlow().mapToList(ioDispatcher).map { rows -> rows.map(::toDomain) }

    /** Idempotent: upserts on deterministic IDs, so re-scans reconcile (PLAN.md §5). */
    suspend fun persistScan(series: List<DomainSeries>, chapters: List<DomainChapter>) =
        withContext(ioDispatcher) {
            q.transaction {
                series.forEach { s ->
                    q.upsertSeries(
                        id = s.id,
                        title = s.title,
                        sort_title = s.sortTitle,
                        author = s.author,
                        description = s.description,
                        cover_path = s.coverPath,
                        start_year = s.startYear?.toLong(),
                        reading_direction = s.readingDirection?.name,
                        external_id = s.externalId,
                        date_added = s.dateAdded,
                        last_scanned = s.lastScanned,
                    )
                }
                chapters.forEach { c ->
                    q.upsertChapter(
                        id = c.id,
                        series_id = c.seriesId,
                        source_id = c.sourceId,
                        locator = c.locator,
                        format = c.format.name,
                        display_name = c.displayName,
                        volume = c.volume,
                        number = c.number,
                        page_count = c.pageCount?.toLong(),
                        size = c.size,
                        change_token = c.changeToken,
                        date_added = c.dateAdded,
                    )
                }
            }
        }

    private fun toDomain(r: SeriesRow) = DomainSeries(
        id = r.id,
        title = r.title,
        sortTitle = r.sort_title,
        author = r.author,
        description = r.description,
        coverPath = r.cover_path,
        startYear = r.start_year?.toInt(),
        readingDirection = r.reading_direction?.let { ReadingDirection.valueOf(it) },
        externalId = r.external_id,
        dateAdded = r.date_added,
        lastScanned = r.last_scanned,
    )
}
