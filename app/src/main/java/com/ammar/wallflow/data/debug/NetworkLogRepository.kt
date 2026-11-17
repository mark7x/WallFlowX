package com.ammar.wallflow.data.debug

import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class NetworkLogEntry(
    val id: Long,
    val timeMillis: Long,
    val method: String,
    val url: String,
    val code: Int?,
    val durationMs: Long,
    val error: String?,
) {
    /** 2xx and 3xx responses count as successful fetches. */
    val isSuccess: Boolean get() = error == null && code != null && code in 200..399
}

/** Keeps the most recent network fetches (API calls, image loads, downloads) in memory. */
@Singleton
class NetworkLogRepository @Inject constructor() {
    private val nextId = AtomicLong(0)
    private val _entries = MutableStateFlow<List<NetworkLogEntry>>(emptyList())
    val entries: StateFlow<List<NetworkLogEntry>> = _entries

    fun add(
        method: String,
        url: String,
        code: Int?,
        durationMs: Long,
        error: String?,
    ) {
        val entry = NetworkLogEntry(
            id = nextId.incrementAndGet(),
            timeMillis = System.currentTimeMillis(),
            method = method,
            url = url,
            code = code,
            durationMs = durationMs,
            error = error,
        )
        _entries.update { (listOf(entry) + it).take(MAX_ENTRIES) }
    }

    fun clear() {
        _entries.update { emptyList() }
    }

    private companion object {
        const val MAX_ENTRIES = 500
    }
}
