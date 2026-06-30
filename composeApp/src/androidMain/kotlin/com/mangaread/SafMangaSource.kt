package com.mangaread

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.mangaread.core.domain.SourceCapability
import com.mangaread.core.domain.ioDispatcher
import com.mangaread.core.source.ChangeEvent
import com.mangaread.core.source.ChangeSet
import com.mangaread.core.source.MangaSource
import com.mangaread.core.source.SourceEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext
import okio.Source

/**
 * Android Storage Access Framework source (PLAN.md §6, §12). Locators are tree-document
 * URI strings; the granted root comes from ACTION_OPEN_DOCUMENT_TREE + a persisted permission.
 * `open`/`changesSince`/`watch` arrive in later phases — Phase 1 only needs `list`.
 */
class SafMangaSource(private val context: Context) : MangaSource {

    override val id: String = "local"
    override val capabilities: Set<SourceCapability> = setOf(SourceCapability.RANDOM_ACCESS)

    override suspend fun list(path: String): List<SourceEntry> = withContext(ioDispatcher) {
        val dir = DocumentFile.fromTreeUri(context, Uri.parse(path)) ?: return@withContext emptyList()
        dir.listFiles().map { f ->
            SourceEntry(
                locator = f.uri.toString(),
                name = f.name ?: "",
                isDirectory = f.isDirectory,
                size = if (f.isFile) f.length() else null,
                changeToken = f.lastModified().toString(),
            )
        }
    }

    override suspend fun open(locator: String): Source =
        TODO("ReadHandle for IMAGE_DIR/CBZ pages — Phase 2 reader")

    override suspend fun changesSince(token: String?): ChangeSet = ChangeSet(emptyList(), null)

    override fun watch(path: String): Flow<ChangeEvent> = emptyFlow()
}
