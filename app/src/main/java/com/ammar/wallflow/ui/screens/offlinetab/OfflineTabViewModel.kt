package com.ammar.wallflow.ui.screens.offlinetab

import android.content.Context
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ammar.wallflow.data.offline.OfflineFavoritesStore
import com.ammar.wallflow.data.offline.toOfflineLocalWallpaper
import com.ammar.wallflow.data.rating.RecentSet
import com.ammar.wallflow.data.rating.WallpaperRatingStore
import com.ammar.wallflow.data.rating.ratingKey
import com.ammar.wallflow.data.repository.FavoritesRepository
import com.ammar.wallflow.model.Source
import com.ammar.wallflow.model.Wallpaper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One offline copy, with the favorite it belongs to and its rating. */
data class OfflineItem(
    val wallpaper: Wallpaper,
    val source: Source,
    val originalId: String,
    val stars: Int,
)

data class OfflineTabState(
    val items: List<OfflineItem> = emptyList(),
    val recent: List<RecentSet> = emptyList(),
    val ratings: Map<String, Int> = emptyMap(),
    val removeAfterSets: Int = 2,
    val bytes: Long = 0L,
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val message: String? = null,
)

@HiltViewModel
class OfflineTabViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val offlineFavoritesStore: OfflineFavoritesStore,
    private val favoritesRepository: FavoritesRepository,
    private val ratingStore: WallpaperRatingStore,
) : ViewModel() {
    private val _state = MutableStateFlow(OfflineTabState())
    val state: StateFlow<OfflineTabState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val built = withContext(Dispatchers.IO) {
                val files = offlineFavoritesStore.files(context)
                val ratings = ratingStore.allRatings()
                OfflineTabState(
                    items = files.map { file ->
                        val wallpaper = file.toOfflineLocalWallpaper()
                        val original = offlineFavoritesStore.originalOf(file)
                        val source = original?.first ?: Source.LOCAL
                        val id = original?.second ?: wallpaper.id
                        OfflineItem(
                            wallpaper = wallpaper,
                            source = source,
                            originalId = id,
                            stars = ratings[ratingKey(source, id)] ?: 0,
                        )
                    },
                    recent = ratingStore.recent(),
                    ratings = ratings,
                    removeAfterSets = ratingStore.removeAfterSets(),
                    bytes = files.sumOf { it.length() },
                )
            }
            _state.update { current ->
                built.copy(
                    running = current.running,
                    done = current.done,
                    total = current.total,
                    message = current.message,
                )
            }
        }
    }

    fun rate(source: Source, id: String, stars: Int) {
        viewModelScope.launch {
            ratingStore.setStars(source, id, stars)
            refresh()
        }
    }

    fun setRemoveAfter(sets: Int) {
        viewModelScope.launch {
            ratingStore.setRemoveAfterSets(sets)
            refresh()
        }
    }

    /** Removes the offline copy only. The favorite stays in Collections. */
    fun delete(item: OfflineItem) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                item.wallpaper.id.toUri().path?.let { offlineFavoritesStore.deleteCopy(File(it)) }
            }
            refresh()
        }
    }

    /** Saves every favorite, booru included, that isn't already on this phone. */
    fun downloadFavorites() {
        if (_state.value.running) return
        viewModelScope.launch {
            _state.update { it.copy(running = true, done = 0, total = 0, message = null) }
            val wallpapers = withContext(Dispatchers.IO) {
                favoritesRepository.getDownloadableWallpapers(context)
            }
            _state.update { it.copy(total = wallpapers.size) }
            val summary = offlineFavoritesStore.downloadAll(context, wallpapers) { done, total ->
                _state.update { it.copy(done = done, total = total) }
            }
            _state.update {
                it.copy(
                    running = false,
                    message = "Saved ${summary.saved}, already saved ${summary.skipped}, " +
                        "failed ${summary.failed}",
                )
            }
            refresh()
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { offlineFavoritesStore.deleteAll(context) }
            refresh()
        }
    }
}
