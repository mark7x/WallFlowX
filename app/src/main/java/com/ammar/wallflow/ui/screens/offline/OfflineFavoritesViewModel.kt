package com.ammar.wallflow.ui.screens.offline

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ammar.wallflow.data.offline.DownloadSummary
import com.ammar.wallflow.data.offline.OfflineFavoritesStore
import com.ammar.wallflow.data.repository.FavoritesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class OfflineFavoritesState(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val summary: DownloadSummary? = null,
)

@HiltViewModel
class OfflineFavoritesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val favoritesRepository: FavoritesRepository,
    private val offlineFavoritesStore: OfflineFavoritesStore,
) : ViewModel() {
    private val _state = MutableStateFlow(OfflineFavoritesState())
    val state: StateFlow<OfflineFavoritesState> = _state.asStateFlow()

    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            _state.value = OfflineFavoritesState(running = true)
            val wallpapers = favoritesRepository.getDownloadableWallpapers(context)
            _state.value = _state.value.copy(total = wallpapers.size)
            val summary = offlineFavoritesStore.downloadAll(context, wallpapers) { done, total ->
                _state.value = _state.value.copy(done = done, total = total)
            }
            _state.value = _state.value.copy(running = false, summary = summary)
        }
    }

    fun cancel() {
        job?.cancel()
        _state.value = _state.value.copy(running = false)
    }
}
