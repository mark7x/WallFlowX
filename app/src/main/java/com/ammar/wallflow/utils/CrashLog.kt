package com.ammar.wallflow.utils

import android.content.Context
import java.io.File
import java.util.Date

/**
 * Saves the last uncaught crash to app storage, so it can be read after the app closes.
 * The app shows it on the Boards screen.
 */
object CrashLog {
    private const val FILE_NAME = "last_crash.txt"

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val text = "${Date()} thread=${thread.name}\n${error.stackTraceToString()}"
                File(context.filesDir, FILE_NAME).writeText(text.take(MAX_CHARS))
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? =
        File(context.filesDir, FILE_NAME).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
    }

    private const val MAX_CHARS = 8000
}
