package com.ammar.wallflow.sites

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Items taken from a page as the in-app browser rendered it. Used for sites whose
 * listings load with JavaScript, which a plain HTTP request cannot read.
 * Each site keeps its own file, so scanned items survive an app restart.
 */
@Singleton
class SiteRenderedCache @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val resumePrefs = context.getSharedPreferences("site_resume", Context.MODE_PRIVATE)

    /** Address of the last page scanned for this site, where the hidden browser picks up. */
    fun resumeUrl(profileId: String): String? = resumePrefs.getString(profileId, null)
    private val listSerializer = ListSerializer(SiteItem.serializer())

    private fun fileFor(profileId: String): File {
        val dir = File(context.filesDir, "site_scans").apply { mkdirs() }
        return File(dir, "$profileId.json")
    }

    suspend fun put(profileId: String, found: List<SiteItem>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            fileFor(profileId).writeText(json.encodeToString(listSerializer, found))
        }
    }

    suspend fun get(profileId: String): List<SiteItem> = withContext(Dispatchers.IO) {
        mutex.withLock { readFile(profileId) }
    }

    /** Adds scanned items to the ones already saved for this site, without repeats. */
    suspend fun add(profileId: String, found: List<SiteItem>, pageUrl: String? = null) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val merged = (readFile(profileId) + found).distinctBy { it.postUrl ?: it.thumbUrl }
            fileFor(profileId).writeText(json.encodeToString(listSerializer, merged))
            if (pageUrl != null) resumePrefs.edit().putString(profileId, pageUrl).apply()
        }
    }

    private fun readFile(profileId: String): List<SiteItem> {
        val file = fileFor(profileId)
        return runCatching {
            if (file.exists()) json.decodeFromString(listSerializer, file.readText()) else emptyList()
        }.getOrDefault(emptyList())
    }
}
