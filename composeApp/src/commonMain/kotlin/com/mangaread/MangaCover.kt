package com.mangaread

/**
 * Dedicated Coil model for series covers. Using a custom type (not a String) bypasses Coil's
 * built-in String→Uri mapper, so the platform CoverFetcher.Factory actually receives it.
 * [model] is the scheme-tagged locator, e.g. "cbz:content://…" or "imgdir:content://…".
 */
data class MangaCover(val model: String)
