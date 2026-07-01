package com.mangaread

/**
 * Coil model for one reader page. `model` is the chapter's scheme-tagged locator
 * ("cbz:<uri>" / "imgdir:<uri>", same scheme as [MangaCover]); `index` selects the page
 * within it. A dedicated data class — not a String — so Coil's built-in String→Uri mapper
 * doesn't intercept it before the custom fetcher runs (see MangaCover).
 */
data class MangaPage(val model: String, val index: Int)
