package com.ammar.wallflow.sites

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * A log of everything the taught-site code does: learning browser events, page requests,
 * paging decisions and feed loads. It is written to a file so it survives restarts.
 * The debug panel shows the newest lines, and Copy takes the whole file.
 */
@Singleton
class SiteLog @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val file = File(context.filesDir, "site_logs/activity.log")
    private val lock = Any()

    private val _lines = MutableStateFlow(
        runCatching { file.takeIf { it.exists() }?.readLines()?.takeLast(SHOWN_LINES).orEmpty() }
            .getOrDefault(emptyList()),
    )

    /** The newest lines, for the debug panel. */
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    fun log(category: String, message: String) {
        val time = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date())
        val line = "$time [$category] $message"
        synchronized(lock) {
            runCatching {
                file.parentFile?.mkdirs()
                file.appendText(line + "\n")
                if (file.length() > MAX_BYTES) {
                    val kept = file.readLines().takeLast(KEPT_LINES)
                    file.writeText(kept.joinToString(separator = "\n", postfix = "\n"))
                }
            }
            _lines.update { (it + line).takeLast(SHOWN_LINES) }
        }
    }

    /** The whole saved log, for copying. */
    fun fullText(): String = synchronized(lock) {
        runCatching { file.takeIf { it.exists() }?.readText().orEmpty() }.getOrDefault("")
    }

    fun clear() {
        synchronized(lock) {
            runCatching { file.delete() }
            _lines.value = emptyList()
        }
    }

    private companion object {
        const val SHOWN_LINES = 400
        const val KEPT_LINES = 3000
        const val MAX_BYTES = 1_000_000L
    }
}
