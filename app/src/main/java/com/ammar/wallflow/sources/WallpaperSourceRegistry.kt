package com.ammar.wallflow.sources

import com.ammar.wallflow.model.OnlineSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Collects every [WallpaperSource] bound through Hilt. Add a new source by binding it in
 * [SourceModule]; nothing else in the registry needs to change.
 */
@Singleton
class WallpaperSourceRegistry @Inject constructor(
    sources: Set<@JvmSuppressWildcards WallpaperSource>,
) {
    private val byId: Map<String, WallpaperSource>

    init {
        val all = sources.toList()
        require(all.map { it.id }.distinct().size == all.size) {
            "Duplicate wallpaper source IDs: ${all.map { it.id }}"
        }
        byId = all.associateBy { it.id }
    }

    fun all(): List<WallpaperSource> = byId.values.toList()

    fun find(id: String): WallpaperSource? = byId[id]

    /** Maps the existing [OnlineSource] enum to its source, while the enum is still in use. */
    fun forOnlineSource(source: OnlineSource): WallpaperSource? = byId[source.name.lowercase()]
}
