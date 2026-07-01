package com.mangaread

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mangaread.core.data.DatabaseDriverFactory
import com.mangaread.core.data.LibraryRepository
import com.mangaread.core.data.createMangaDatabase
import com.mangaread.core.scanner.LibraryScanner

/**
 * Background library scan (PLAN.md §15) so a large scan survives leaving the screen and the
 * library stays fresh. Builds its own object graph from the app context (no DI framework yet)
 * and reuses [LibrarySyncer]. No-ops if no root is saved or the SAF grant was revoked.
 */
class ScanWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val source = SafMangaSource(applicationContext)
        val database = createMangaDatabase(DatabaseDriverFactory(applicationContext).create())
        val repository = LibraryRepository(database)

        val root = repository.savedLocalRoot() ?: return Result.success()
        if (!source.canAccess(root)) return Result.success() // grant lost; UI will prompt re-grant

        return try {
            val coverCache = AndroidChapterCoverCache(applicationContext, source)
            val syncer = LibrarySyncer(repository, LibraryScanner(source), coverCache)
            syncer.sync(root)
            // Already running in the background, so there's no UI to unblock — finish the
            // deferred per-chapter covers here instead of leaving them for next time.
            syncer.backfillChapterCovers()
            Result.success()
        } catch (t: Throwable) {
            android.util.Log.w("ScanWorker", "background scan failed", t)
            Result.retry()
        }
    }
}
