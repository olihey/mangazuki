package com.mangaread

import com.russhwolf.settings.Settings

/** Persisted reader/viewer settings (PLAN.md §8.1 — tap zones, volume keys, gesture help). */
class ReaderPreferences(private val settings: Settings) {

    var volumeKeyPaging: Boolean
        get() = settings.getBoolean(KEY_VOLUME_KEYS, true)
        set(value) = settings.putBoolean(KEY_VOLUME_KEYS, value)

    /** One-time gesture-help overlay; set true after the user dismisses it once. */
    var hasSeenGestureHelp: Boolean
        get() = settings.getBoolean(KEY_SEEN_HELP, false)
        set(value) = settings.putBoolean(KEY_SEEN_HELP, value)

    private companion object {
        const val KEY_VOLUME_KEYS = "reader.volumeKeyPaging"
        const val KEY_SEEN_HELP = "reader.hasSeenGestureHelp"
    }
}
