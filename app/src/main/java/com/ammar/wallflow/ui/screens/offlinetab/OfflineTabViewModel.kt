package com.ammar.wallflow.ui.screens.offlinetab

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ammar.wallflow.data.offline.OfflineFavoritesStore
import com.ammar.wallflow.extensions.toLocalWallpaper
import com.ammar.wallflow.model.Wallpaper
import com.lazygeniouz.dfc.file.DocumentFileCompat
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OfflineTabState(
    val items: List<Wallpaper> = emptyList(),
    val bytes: Long = 0L,
)

@HiltViewModel
class OfflineTabViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val offlineFavoritesStore: OfflineFavoritesStore,
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
                    items = files.mapNotNull { file ->
                        DocumentFileCompat.fromSingleUri(context, Uri.fromFile(file))
                            ?.toLocalWallpaper(context)
                    },
                    bytes = files.sumOf { it.length() },
                )
            }
            _state.value = state
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
