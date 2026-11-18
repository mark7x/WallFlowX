package com.ammar.wallflow.data.offline

import android.content.Context

/**
 * Pause between two offline downloads, so sites such as Wallhaven don't block a burst of
 * requests. The user sets it on the Offline screens. Stored on this device only.
 */
object OfflineDownloadDelay {
    const val DEFAULT_MS = 2000L
    const val MIN_MS = 0L
    const val MAX_MS = 10_000L
    const val STEP_MS = 500L

    private const val PREFS = "offline_download"
    private const val KEY = "delay_ms"

    fun get(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY, DEFAULT_MS)
            .coerceIn(MIN_MS, MAX_MS)

    fun set(context: Context, millis: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY, millis.coerceIn(MIN_MS, MAX_MS))
            .apply()
    }
}
