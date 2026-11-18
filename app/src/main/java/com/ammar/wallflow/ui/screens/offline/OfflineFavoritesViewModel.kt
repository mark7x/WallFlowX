package com.ammar.wallflow.ui.screens.offline

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ammar.wallflow.data.db.entity.search.toSavedSearch
import com.ammar.wallflow.data.db.entity.wallpaper.toWallpaper
import com.ammar.wallflow.data.network.RedditNetworkDataSource
import com.ammar.wallflow.data.network.WallhavenNetworkDataSource
import com.ammar.wallflow.data.network.model.reddit.toWallpaperEntities
import com.ammar.wallflow.data.network.model.wallhaven.toWallhavenWallpaper
import com.ammar.wallflow.data.offline.DownloadSummary
import com.ammar.wallflow.data.offline.OfflineFavoritesStore
import com.ammar.wallflow.data.repository.FavoritesRepository
import com.ammar.wallflow.data.repository.SavedSearchRepository
import com.ammar.wallflow.model.DownloadableWallpaper
import com.ammar.wallflow.model.search.RedditSearch
import com.ammar.wallflow.model.search.SavedSearch
import com.ammar.wallflow.model.search.Search
import com.ammar.wallflow.model.search.WallhavenSearch
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How many result pages a saved search downloads. Each page is up to a full result set. */
private const val MAX_SEARCH_PAGES = 2

data class OfflineFavoritesState(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val summary: DownloadSummary? = null,
    val files: Int = 0,
    val bytes: Long = 0L,
    val exporting: Boolean = false,
    val exported: Int? = null,
    val searches: List<SavedSearch> = emptyList(),
    val searchRunningId: Long? = null,
    val searchMessage: String? = null,
)

@HiltViewModel
class OfflineFavoritesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val favoritesRepository: FavoritesRepository,
    private val offlineFavoritesStore: OfflineFavoritesStore,
    private val savedSearchRepository: SavedSearchRepository,
    private val wallhavenNetwork: WallhavenNetworkDataSource,
    private val redditNetwork: RedditNetworkDataSource,
) : ViewModel() {
    private val _state = MutableStateFlow(OfflineFavoritesState())
    val state: StateFlow<OfflineFavoritesState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        refreshStats()
        viewModelScope.launch {
            savedSearchRepository.observeAll().map { entities ->
                entities.map { it.toSavedSearch() }
            }.collect { searches ->
                _state.value = _state.value.copy(searches = searches)
            }
        }
    }

    fun refreshStats() {
        viewModelScope.launch {
            val (count, size) = withContext(Dispatchers.IO) { offlineFavoritesStore.stats(context) }
            _state.value = _state.value.copy(files = count, bytes = size)
        }
    }

    fun start() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            _state.value = OfflineFavoritesState(running = true, searches = _state.value.searches)
            val wallpapers = favoritesRepository.getDownloadableWallpapers(context)
            _state.value = _state.value.copy(total = wallpapers.size)
            val summary = offlineFavoritesStore.downloadAll(context, wallpapers) { done, total ->
                _state.value = _state.value.copy(done = done, total = total)
            }
            _state.value = _state.value.copy(running = false, summary = summary)
            refreshStats()
        }
    }

    /** Downloads the first few result pages of a saved search into the offline folder. */
    fun downloadSearch(saved: SavedSearch) {
        if (_state.value.running || _state.value.searchRunningId != null) return
        viewModelScope.launch {
            _state.value = _state.value.copy(searchRunningId = saved.id, searchMessage = null)
            val wallpapers = fetchSearchWallpapers(saved.search)
            val summary = offlineFavoritesStore.downloadAll(context, wallpapers) { _, _ -> }
            _state.value = _state.value.copy(
                searchRunningId = null,
                searchMessage = "${saved.name}: saved ${summary.saved}, " +
                    "already saved ${summary.skipped}, failed ${summary.failed}",
            )
            refreshStats()
        }
    }

    private suspend fun fetchSearchWallpapers(search: Search): List<DownloadableWallpaper> =
        withContext(Dispatchers.IO) {
            val result = mutableListOf<DownloadableWallpaper>()
            runCatching {
                when (search) {
                    is WallhavenSearch -> {
                        for (page in 1..MAX_SEARCH_PAGES) {
                            val response = wallhavenNetwork.search(search, page)
                            result += response.data.map { it.toWallhavenWallpaper() }
                            val lastPage = response.meta?.last_page ?: 1
                            if (page >= lastPage) break
                        }
                    }
                    is RedditSearch -> {
                        if (search.filters.subreddits.isNotEmpty()) {
                            var after: String? = null
                            for (page in 1..MAX_SEARCH_PAGES) {
                                val response = redditNetwork.search(search, after)
                                result += response.data.children
                                    .flatMap { it.data.toWallpaperEntities() }
                                    .map { it.toWallpaper() }
                                    .filterIsInstance<DownloadableWallpaper>()
                                after = response.data.after ?: break
                            }
                        }
                    }
                    else -> Unit
                }
            }
            result
        }

    fun cancel() {
        job?.cancel()
        _state.value = _state.value.copy(running = false)
    }

    fun exportTo(treeUri: Uri) {
        if (_state.value.running || _state.value.exporting) return
        viewModelScope.launch {
            _state.value = _state.value.copy(exporting = true, exported = null)
            val copied = offlineFavoritesStore.exportTo(context, treeUri)
            _state.value = _state.value.copy(exporting = false, exported = copied)
        }
    }

    fun deleteAll() {
        if (_state.value.running) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { offlineFavoritesStore.deleteAll(context) }
            _state.value = _state.value.copy(summary = null)
            refreshStats()
        }
    }
}
