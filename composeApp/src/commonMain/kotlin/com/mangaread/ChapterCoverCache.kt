package com.mangaread

import com.mangaread.core.domain.Chapter

/**
 * Generates and caches a chapter's first-page cover during scan (PLAN.md §9). Implementations
 * must be idempotent: if the cached file already exists, return its path without regenerating
 * so a re-scan doesn't redo the work.
 */
interface ChapterCoverCache {
    /** Returns the cached cover path (generating it first if missing), or null on failure. */
    suspend fun ensureCover(chapter: Chapter): String?
}
