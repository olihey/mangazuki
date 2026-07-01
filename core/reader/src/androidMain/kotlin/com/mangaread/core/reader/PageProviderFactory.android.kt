package com.mangaread.core.reader

import com.mangaread.core.domain.Chapter
import com.mangaread.core.domain.ChapterFormat
import com.mangaread.core.source.MangaSource

actual fun pageProviderFor(chapter: Chapter, source: MangaSource): PageProvider = when (chapter.format) {
    ChapterFormat.IMAGE_DIR -> ImageDirPageProvider(chapter.locator, source)
    ChapterFormat.CBZ -> CbzPageProvider(chapter.locator, source)
}
