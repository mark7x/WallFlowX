package com.ammar.wallflow.sources.booru

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The delay between requests to the same booru site, stored in SharedPreferences. */
object BooruPacing {
    const val MIN_MS = 0L
    const val MAX_MS = 10_000L
    const val STEP_MS = 250L
    const val DEFAULT_MS = 1_000L
    private const val PREFS = "booru_pacing"
    private const val KEY_DELAY = "delay_ms"

    fun delayMs(context: Context): Long = context
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getLong(KEY_DELAY, DEFAULT_MS)

    fun setDelayMs(context: Context, ms: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_DELAY, ms.coerceIn(MIN_MS, MAX_MS))
            .apply()
    }
}

/**
 * Spaces out requests per site. Every request reserves its turn for its host, so
 * concurrent calls queue up instead of arriving together. After an HTTP 429 the host
 * is paused for the time the site asked for in Retry-After (30 seconds if none).
 */
@Singleton
class BooruPacer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()
    private val nextAllowedAt = mutableMapOf<String, Long>()

    /** Waits for this site's turn, then reserves the next one. */
    suspend fun await(baseUrl: String) {
        val host = hostOf(baseUrl)
        val wait = mutex.withLock {
            val now = System.currentTimeMillis()
            val turn = maxOf(nextAllowedAt[host] ?: 0L, now)
            nextAllowedAt[host] = turn + BooruPacing.delayMs(context)
            turn - now
        }
        if (wait > 0) delay(wait)
    }

    /** Called after a 429. Blocks further requests to the site for the Retry-After time. */
    suspend fun rateLimited(baseUrl: String, retryAfter: String?) {
        val seconds = retryAfter?.trim()?.toLongOrNull() ?: DEFAULT_BACKOFF_SECONDS
        mutex.withLock {
            nextAllowedAt[hostOf(baseUrl)] = System.currentTimeMillis() + seconds * 1000
        }
    }

    private fun hostOf(baseUrl: String): String =
        runCatching { java.net.URI(baseUrl.trim()).host }.getOrNull() ?: baseUrl

    private companion object {
        const val DEFAULT_BACKOFF_SECONDS = 30L
    }
}
