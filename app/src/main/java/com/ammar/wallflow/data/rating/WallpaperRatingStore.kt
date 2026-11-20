package com.ammar.wallflow.data.rating

import android.content.Context
import com.ammar.wallflow.data.offline.OfflineFavoritesStore
import com.ammar.wallflow.data.repository.FavoritesRepository
import com.ammar.wallflow.model.Source
import com.ammar.wallflow.model.Wallpaper
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val MAX_STARS = 5
const val RECENT_LIMIT = 10
/** Sets before a rated favorite is removed. 5 stars have no limit. */
val DEFAULT_LIMITS: Map<Int, Int> = mapOf(1 to 2, 2 to 3, 3 to 5, 4 to 10)

fun ratingKey(source: Source, id: String) = "${source.name}:$id"

@Serializable
data class RatingEntry(val stars: Int = 0, val sets: Int = 0)

@Serializable
data class RecentSet(
    val source: String,
    val id: String,
    val preview: String? = null,
    val setAt: Long = 0L,
)

@Serializable
data class RatingFile(
    val ratings: Map<String, RatingEntry> = emptyMap(),
    val recent: List<RecentSet> = emptyList(),
    val limits: Map<Int, Int> = DEFAULT_LIMITS,
)

/**
 * Star ratings from 1 to 5 and the last [RECENT_LIMIT] set wallpapers. A 1-star favorite is
 * removed from favorites and from its offline copy once it has been set enough times for its stars
 * (see [RatingFile.limits]). 5 stars are never removed.
 */
@Singleton
class WallpaperRatingStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val favoritesRepository: FavoritesRepository,
    private val offlineFavoritesStore: OfflineFavoritesStore,
) {
    private val file = File(context.filesDir, "wallpaper_ratings.json")
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    private fun read(): RatingFile = runCatching {
        json.decodeFromString<RatingFile>(file.readText())
    }.getOrDefault(RatingFile())

    private suspend fun <T> locked(block: (RatingFile) -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock { block(read()) }
    }

    private suspend fun update(block: (RatingFile) -> RatingFile) {
        withContext(Dispatchers.IO) {
            mutex.withLock { file.writeText(json.encodeToString(RatingFile.serializer(), block(read()))) }
        }
    }

    suspend fun allRatings(): Map<String, Int> =
        locked { data -> data.ratings.mapValues { it.value.stars } }

    suspend fun recent(): List<RecentSet> = locked { it.recent }

    suspend fun limits(): Map<Int, Int> = locked { it.limits }

    /** Everything rated, for backups. */
    suspend fun export(): RatingFile = locked { it }

    /** Merges a backup into the current ratings. Backup values win for the same wallpaper. */
    suspend fun importData(data: RatingFile) {
        update { current ->
            current.copy(
                ratings = current.ratings + data.ratings,
                limits = current.limits + data.limits,
                recent = (data.recent + current.recent)
                    .distinctBy { it.source to it.id }
                    .sortedByDescending { it.setAt }
                    .take(RECENT_LIMIT),
            )
        }
    }

    suspend fun setLimit(stars: Int, sets: Int) {
        if (stars !in 1..4) return
        update { it.copy(limits = it.limits + (stars to sets.coerceAtLeast(1))) }
    }

    suspend fun setStars(source: Source, id: String, stars: Int) {
        val clamped = stars.coerceIn(0, MAX_STARS)
        update { data ->
            val key = ratingKey(source, id)
            val entry = data.ratings[key] ?: RatingEntry()
            data.copy(ratings = data.ratings + (key to entry.copy(stars = clamped)))
        }
        pruneIfNeeded(source, id)
    }

    /** Call when a wallpaper gets set. Counts the set and keeps the last [RECENT_LIMIT]. */
    suspend fun recordSet(wallpaper: Wallpaper) {
        val source = wallpaper.source
        update { data ->
            val key = ratingKey(source, wallpaper.id)
            val entry = data.ratings[key] ?: RatingEntry()
            val set = RecentSet(
                source = source.name,
                id = wallpaper.id,
                preview = wallpaper.thumbData?.toString(),
                setAt = System.currentTimeMillis(),
            )
            val recent = (
                listOf(set) +
                    data.recent.filterNot { it.source == source.name && it.id == wallpaper.id }
                ).take(RECENT_LIMIT)
            data.copy(
                ratings = data.ratings + (key to entry.copy(sets = entry.sets + 1)),
                recent = recent,
            )
        }
        pruneIfNeeded(source, wallpaper.id)
    }

    private suspend fun pruneIfNeeded(source: Source, id: String) {
        val (sets, limit) = locked { data ->
            val entry = data.ratings[ratingKey(source, id)] ?: RatingEntry()
            entry.sets to data.limits[entry.stars]
        }
        if (limit == null || sets < limit) return
        favoritesRepository.removeFavorite(sourceId = id, source = source)
        withContext(Dispatchers.IO) { offlineFavoritesStore.removeByOriginal(context, source, id) }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WallpaperRatingEntryPoint {
    fun ratingStore(): WallpaperRatingStore
}

fun wallpaperRatingStore(context: Context): WallpaperRatingStore =
    EntryPointAccessors.fromApplication(
        context.applicationContext,
        WallpaperRatingEntryPoint::class.java,
    ).ratingStore()
