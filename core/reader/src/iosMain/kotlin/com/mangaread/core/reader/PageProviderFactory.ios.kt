package com.mangaread.core.reader

import com.mangaread.core.domain.Chapter
import com.mangaread.core.source.MangaSource

actual fun pageProviderFor(chapter: Chapter, source: MangaSource): PageProvider =
    TODO("iOS PageProvider — pending Mac bring-up (PLAN.md §12)")
