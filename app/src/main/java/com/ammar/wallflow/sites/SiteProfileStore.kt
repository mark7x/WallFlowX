package com.ammar.wallflow.sites

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val _profiles = MutableStateFlow<List<SiteProfile>>(emptyList())

    /** Taught sites, kept current after every save. Call [refresh] once at startup. */
    val profiles: StateFlow<List<SiteProfile>> = _profiles.asStateFlow()

    suspend fun refresh() {
        _profiles.value = all()
    }

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

    /** Merges taught sites from a backup. Backup values win for the same site. */
    suspend fun importAll(profiles: List<SiteProfile>) = update { current ->
        current.filterNot { old -> profiles.any { it.id == old.id } } + profiles
    }

    private suspend fun update(block: (List<SiteProfile>) -> List<SiteProfile>) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val next = block(read())
                file.writeText(json.encodeToString(listSerializer, next))
                _profiles.value = next
            }
        }
    }

    private fun read(): List<SiteProfile> = runCatching {
        if (file.exists()) json.decodeFromString(listSerializer, file.readText()) else emptyList()
    }.getOrDefault(emptyList())
}
