package com.oliver.heyme.mangazuki.core.reader

import com.oliver.heyme.mangazuki.core.domain.Chapter
import com.oliver.heyme.mangazuki.core.source.MangaSource

actual suspend fun pageProviderFor(
    chapter: Chapter,
    source: MangaSource,
    pdfCacheDir: String?,
    onPdfMaterializeProgress: (bytesCopied: Long, totalBytes: Long?) -> Unit,
): PageProvider =
    TODO("iOS PageProvider — pending Mac bring-up (PLAN.md §12); PDF via PDFKit or Pdfium-cinterop (§16)")
