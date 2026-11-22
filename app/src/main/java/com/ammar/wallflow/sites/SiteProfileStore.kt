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

/** Stores taught sites in one JSON file in the app's private storage. */
@Singleton
class SiteProfileStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val file = File(context.filesDir, "site_profiles.json")
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(SiteProfile.serializer())

    suspend fun all(): List<SiteProfile> = withContext(Dispatchers.IO) {
        mutex.withLock { read() }
    }

    suspend fun get(id: String): SiteProfile? = all().firstOrNull { it.id == id }

    suspend fun save(profile: SiteProfile) = update { list ->
        list.filterNot { it.id == profile.id } + profile
    }

    suspend fun delete(id: String) = update { list -> list.filterNot { it.id == id } }

    suspend fun addTags(id: String, tags: Collection<String>) = update { list ->
        list.map { profile ->
            if (profile.id != id) {
                profile
            } else {
                val counts = profile.tagCounts.toMutableMap()
                tags.forEach { counts[it] = (counts[it] ?: 0) + 1 }
                profile.copy(tagCounts = counts)
            }
        }
    }

    private suspend fun update(block: (List<SiteProfile>) -> List<SiteProfile>) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                file.writeText(json.encodeToString(listSerializer, block(read())))
            }
        }
    }

    private fun read(): List<SiteProfile> = runCatching {
        if (file.exists()) json.decodeFromString(listSerializer, file.readText()) else emptyList()
    }.getOrDefault(emptyList())
}
