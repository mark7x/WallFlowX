package com.ammar.wallflow.ui.screens.offlinetab

import android.content.Context
import androidx.core.net.toUri
import com.ammar.wallflow.data.offline.toOfflineLocalWallpaper
import com.ammar.wallflow.data.repository.FavoritesRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ammar.wallflow.data.offline.OfflineFavoritesStore
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

data class OfflineTabState(
    val items: List<Wallpaper> = emptyList(),
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
) : ViewModel() {
    private val _state = MutableStateFlow(OfflineTabState())
    val state: StateFlow<OfflineTabState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val state = withContext(Dispatchers.IO) {
                val files = offlineFavoritesStore.files(context)
                OfflineTabState(
                    items = files.map { it.toOfflineLocalWallpaper() },
                    bytes = files.sumOf { it.length() },
                )
            }
            _state.value = _state.value.copy(items = state.items, bytes = state.bytes)
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

    /** Removes the offline copy only. The favorite stays in Collections. */
    fun delete(wallpaper: Wallpaper) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                wallpaper.id.toUri().path?.let { File(it).delete() }
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
