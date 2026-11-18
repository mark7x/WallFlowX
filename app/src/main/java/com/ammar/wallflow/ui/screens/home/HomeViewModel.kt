package com.ammar.wallflow.ui.screens.home

import androidx.compose.runtime.Stable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.ammar.wallflow.data.db.entity.FavoriteEntity
import com.ammar.wallflow.data.db.entity.LightDarkEntity
import com.ammar.wallflow.data.db.entity.ViewedEntity
import com.ammar.wallflow.data.db.entity.search.SavedSearchEntity
import com.ammar.wallflow.data.db.entity.search.toSavedSearch
import com.ammar.wallflow.data.db.entity.toFavorite
import com.ammar.wallflow.data.db.entity.toLightDark
import com.ammar.wallflow.data.db.entity.toViewed
import com.ammar.wallflow.data.preferences.LayoutPreferences
import com.ammar.wallflow.data.preferences.ViewedWallpapersLook
import com.ammar.wallflow.data.repository.AppPreferencesRepository
import com.ammar.wallflow.data.repository.FavoritesRepository
import com.ammar.wallflow.data.repository.LightDarkRepository
import com.ammar.wallflow.data.repository.SavedSearchRepository
import com.ammar.wallflow.data.repository.ViewedRepository
import com.ammar.wallflow.data.repository.reddit.RedditRepository
import com.ammar.wallflow.data.repository.utils.Resource
import com.ammar.wallflow.data.repository.utils.successOr
import com.ammar.wallflow.data.repository.wallhaven.WallhavenRepository
import com.ammar.wallflow.sources.WallpaperSourceRegistry
import com.ammar.wallflow.model.Favorite
import com.ammar.wallflow.model.LightDark
import com.ammar.wallflow.model.OnlineSource
import com.ammar.wallflow.model.Purity
import com.ammar.wallflow.model.Viewed
import com.ammar.wallflow.model.Wallpaper
import com.ammar.wallflow.model.search.RedditFilters
import com.ammar.wallflow.model.search.RedditSearch
import com.ammar.wallflow.sources.booru.BooruRepository
import com.ammar.wallflow.model.search.BooruFilters
import com.ammar.wallflow.model.search.BooruSearch
import com.ammar.wallflow.sources.booru.BoardsStore
import com.ammar.wallflow.model.search.RedditSort
import com.ammar.wallflow.model.search.RedditSubredditFilter
import com.ammar.wallflow.model.search.RedditTimeRange
import com.ammar.wallflow.model.search.SavedSearch
import com.ammar.wallflow.model.search.Search
import com.ammar.wallflow.model.search.WallhavenFilters
import com.ammar.wallflow.model.reddit.RedditWallpaper
import com.ammar.wallflow.model.search.WallhavenSearch
import com.ammar.wallflow.model.search.WallhavenSorting
import com.ammar.wallflow.model.search.WallhavenTopRange
import com.ammar.wallflow.model.wallhaven.WallhavenTag
import com.ammar.wallflow.navArgs
import com.github.materiiapps.partial.Partialize
import com.github.materiiapps.partial.getOrElse
import com.github.materiiapps.partial.partial
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.ammar.wallflow.sources.booru.BooruDetection
import com.ammar.wallflow.sources.booru.BooruStyleDetector
import com.ammar.wallflow.sources.booru.SavedBoard
import com.ammar.wallflow.utils.CrashLog

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val wallhavenRepository: WallhavenRepository,
    private val wallpaperSourceRegistry: WallpaperSourceRegistry,
    private val redditRepository: RedditRepository,
    private val booruRepository: BooruRepository,
    private val boardsStore: BoardsStore,
    private val booruStyleDetector: BooruStyleDetector,
    @ApplicationContext private val context: Context,
    private val appPreferencesRepository: AppPreferencesRepository,
    private val savedSearchRepository: SavedSearchRepository,
    private val favoritesRepository: FavoritesRepository,
    viewedRepository: ViewedRepository,
    lightDarkRepository: LightDarkRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val mainSearch = savedStateHandle.navArgs<HomeScreenNavArgs>().search
    private val popularTags = wallhavenRepository.popularTags()
    private val localUiState = MutableStateFlow(HomeUiStatePartial())
    private val homeSearchFlow = combine(
        localUiState,
        appPreferencesRepository.appPreferencesFlow,
        boardsStore.boards,
    ) { local, appPreferences, boards ->
        val source = getCorrectSelectedSource(
            local = local,
            homeSource = appPreferences.homeSources,
            homeRedditSearch = appPreferences.homeRedditSearch,
            hasBoards = boards.isNotEmpty(),
        )
        when (source) {
            OnlineSource.WALLHAVEN -> appPreferences.homeWallhavenSearch
            OnlineSource.REDDIT -> appPreferences.homeRedditSearch
            // The website chosen in the feed, else the first saved website.
            OnlineSource.BOORU -> {
                val website = boards.firstOrNull { it.baseUrl == local.selectedBoardUrl.getOrElse { null } }
                    ?: boards.firstOrNull()
                website?.let { BooruSearch(filters = BooruFilters(boardUrl = it.baseUrl, style = it.style)) }
                    ?: appPreferences.homeWallhavenSearch
            }
        }
    }.filterNotNull().distinctUntilChanged()

    /** True when at least one website is saved. */
    fun hasBoards(): Boolean = boardsStore.boards.value.isNotEmpty()

    fun selectWebsite(url: String) {
        localUiState.update {
            it.copy(
                selectedSource = partial(OnlineSource.BOORU),
                selectedBoardUrl = partial<String?>(url),
            )
        }
    }

    fun addWebsite(address: String, userId: String = "", apiKey: String = "") {
        val url = address.trim()
        if (url.isEmpty() || uiState.value.websiteAddRunning) return
        val user = userId.trim().ifBlank { null }
        val key = apiKey.trim().ifBlank { null }
        viewModelScope.launch {
            localUiState.update {
                it.copy(websiteAddRunning = partial(true), websiteAddError = partial<String?>(null))
            }
            when (val detection = booruStyleDetector.detect(url, user, key)) {
                is BooruDetection.Detected -> {
                    boardsStore.add(
                        SavedBoard(baseUrl = url, style = detection.style, userId = user, apiKey = key),
                    )
                    localUiState.update { it.copy(websiteAddRunning = partial(false)) }
                }
                is BooruDetection.Unknown -> {
                    val reasons = detection.reasons.joinToString("; ")
                    val needsKeys = reasons.contains("Missing authentication", ignoreCase = true)
                    val message = when {
                        needsKeys && (user == null || key == null) ->
                            "This site needs a user ID and API key. Fill in both fields and add again."
                        needsKeys -> "The site rejected these keys. Check the user ID and API key."
                        else -> reasons.ifBlank { "Not a supported booru site" }
                    }
                    localUiState.update {
                        it.copy(websiteAddRunning = partial(false), websiteAddError = partial<String?>(message))
                    }
                }
            }
        }
    }

    fun removeWebsite(website: SavedBoard) {
        boardsStore.remove(website.baseUrl)
    }

    fun clearCrash() {
        CrashLog.clear(context)
        localUiState.update { it.copy(lastCrash = partial<String?>(null)) }
    }

    private fun getCorrectSelectedSource(
        local: HomeUiStatePartial,
        homeSource: Map<OnlineSource, Boolean>,
        homeRedditSearch: RedditSearch?,
        hasBoards: Boolean,
    ): OnlineSource {
        var source = local.selectedSource.getOrElse { OnlineSource.WALLHAVEN }
        // Booru is enabled by having at least one website, not by the toggle.
        val sourceEnabled = if (source == OnlineSource.BOORU) hasBoards else homeSource[source] ?: false
        if (!sourceEnabled) {
            source = OnlineSource.entries.first { it != source }
        }
        if (source == OnlineSource.REDDIT &&
            (homeRedditSearch == null || homeRedditSearch.filters.subreddits.isEmpty())
        ) {
            // fallback to wallhaven
            source = OnlineSource.WALLHAVEN
        }
        return source
    }

    val wallpapers = if (mainSearch != null) {
        when (mainSearch) {
            is WallhavenSearch -> wallpaperSourceRegistry.pagerFor(mainSearch)!!
            is RedditSearch -> redditRepository.wallpapersPager(mainSearch)
            is BooruSearch -> booruRepository.wallpapersPager(mainSearch)
        }
    } else {
        combine(
            homeSearchFlow,
            appPreferencesRepository.appPreferencesFlow,
        ) { search, prefs ->
            search to prefs
        }.flatMapLatest { (search, prefs) ->
            val showCarousel = prefs.lookAndFeelPreferences.layoutPreferences.showCarousel
            val subFilter = prefs.redditSubredditFilter
            when (search) {
                is WallhavenSearch -> wallpaperSourceRegistry.pagerFor(search)!!
                is BooruSearch -> booruRepository.wallpapersPager(search)
                is RedditSearch -> {
                    // apply subreddit filter at query level (does NOT touch saved settings)
                    val effectiveSearch = applySubredditFilter(search, subFilter)
                    if (showCarousel) {
                        redditRepository.wallpapersPagerGrouped(effectiveSearch)
                    } else {
                        redditRepository.wallpapersPager(effectiveSearch)
                    }
                }
            }
        }
    }.cachedIn(viewModelScope)

    init {
        localUiState.update { it.copy(lastCrash = partial(CrashLog.read(context))) }
        if (mainSearch != null) {
            // save search to app prefs
            viewModelScope.launch {
                appPreferencesRepository.updateMainSearch(mainSearch)
            }
        }
        viewModelScope.launch {
            val appPreferences = appPreferencesRepository.appPreferencesFlow.first()
            val selectedSource = getCorrectSelectedSource(
                local = localUiState.value,
                homeSource = appPreferences.homeSources,
                homeRedditSearch = appPreferences.homeRedditSearch,
                hasBoards = boardsStore.boards.value.isNotEmpty(),
            )

            localUiState.update {
                it.copy(
                    selectedSource = partial(selectedSource),
                )
            }
        }
    }

    val uiState = com.ammar.wallflow.utils.combine(
        popularTags,
        homeSearchFlow,
        appPreferencesRepository.appPreferencesFlow,
        localUiState,
        savedSearchRepository.observeAll(),
        favoritesRepository.observeAll(),
        viewedRepository.observeAll(),
        lightDarkRepository.observeAll(),
        boardsStore.boards,
    ) {
            tags,
            homeSearch,
            appPreferences,
            local,
            savedSearchEntities,
            favorites,
            viewedList,
            lightDarkList,
            boards,
        ->
        local.merge(
            HomeUiState(
                wallhaven = WallhavenState(
                    wallhavenTags = (
                        if (tags is Resource.Loading) {
                            tempWallhavenTags
                        } else {
                            tags.successOr(emptyList())
                        }
                        ).toImmutableList(),
                    areTagsLoading = tags is Resource.Loading,
                ),
                reddit = RedditState(
                    subreddits = appPreferences.homeRedditSearch?.filters?.subreddits
                        ?.toImmutableSet()
                        ?: persistentSetOf(),
                ),
                mainSearch = mainSearch,
                homeSearch = homeSearch,
                savedSearches = savedSearchEntities.map(
                    SavedSearchEntity::toSavedSearch,
                ),
                blurSketchy = appPreferences.blurSketchy,
                blurNsfw = appPreferences.blurNsfw,
                showNSFW = appPreferences.wallhavenApiKey.isNotBlank(),
                layoutPreferences = appPreferences.lookAndFeelPreferences.layoutPreferences,
                favorites = favorites.map(FavoriteEntity::toFavorite).toImmutableList(),
                viewedList = viewedList.map(ViewedEntity::toViewed).toImmutableList(),
                viewedWallpapersLook = appPreferences.viewedWallpapersPreferences.look,
                lightDarkList = lightDarkList.map(LightDarkEntity::toLightDark).toImmutableList(),
                sources = appPreferences.homeSources.toImmutableMap(),
                websites = boards.toImmutableList(),
                prevMainWallhavenSearch = appPreferences.mainWallhavenSearch,
                prevMainRedditSearch = appPreferences.mainRedditSearch,
                redditSubredditFilter = appPreferences.redditSubredditFilter,
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(),
    )

    fun refresh() {
        viewModelScope.launch {
            wallhavenRepository.refreshPopularTags()
        }
    }

    fun updateHomeSearch(search: Search) {
        viewModelScope.launch {
            when (search) {
                is WallhavenSearch -> appPreferencesRepository.updateHomeWallhavenSearch(search)
                is RedditSearch -> appPreferencesRepository.updateHomeRedditSearch(search)
                is BooruSearch -> Unit
            }
        }
    }

    fun showFilters(show: Boolean) = localUiState.update {
        it.copy(showFilters = partial(show))
    }

    fun setSelectedWallpaper(wallpaper: Wallpaper) = localUiState.update {
        it.copy(selectedWallpaper = partial(wallpaper))
    }

    fun showSaveSearchAsDialog(search: Search? = null) = localUiState.update {
        it.copy(
            saveSearchAsSearch = partial(search),
        )
    }

    fun saveSearchAs(name: String, search: Search) = viewModelScope.launch {
        savedSearchRepository.upsert(
            SavedSearch(
                name = name,
                search = search,
            ),
        )
    }

    fun showSavedSearches(
        show: Boolean = true,
        isFromSearchBar: Boolean = false,
    ) = localUiState.update {
        it.copy(
            showSavedSearchesDialog = partial(show),
            showSavedSearchesForSearchBar = partial(isFromSearchBar),
        )
    }

    fun setQuickActionsWallpaper(wallpaper: Wallpaper?) = localUiState.update {
        it.copy(quickActionsWallpaper = partial(wallpaper))
    }

    fun toggleFavorite(wallpaper: Wallpaper) = viewModelScope.launch {
        if (wallpaper is RedditWallpaper && wallpaper.galleryPosition != null) {
            val gallery = redditRepository.galleryWallpapers(wallpaper.postId)
            if (gallery.size > 1) {
                val isFav = favoritesRepository.isFavorite(wallpaper.id, wallpaper.source)
                gallery.forEach { gWp ->
                    if (isFav) {
                        favoritesRepository.removeFavorite(gWp.id, gWp.source)
                    } else {
                        favoritesRepository.addFavorite(gWp.id, gWp.source)
                    }
                }
                return@launch
            }
        }
        favoritesRepository.toggleFavorite(
            sourceId = wallpaper.id,
            source = wallpaper.source,
        )
    }

    fun showManageSourcesDialog(show: Boolean) = localUiState.update {
        it.copy(
            manageSourcesState = partial(
                ManageSourcesState(
                    showDialog = show,
                    currentSources = if (show) {
                        uiState.value.sources
                    } else {
                        uiState.value.manageSourcesState.currentSources
                    },
                ),
            ),
        )
    }

    fun addSource(source: OnlineSource) {
        if (source == OnlineSource.BOORU) {
            viewModelScope.launch {
                appPreferencesRepository.updateHomeSources(
                    uiState.value.sources + (OnlineSource.BOORU to true),
                )
            }
            showManageSourcesDialog(false)
            return
        }
        localUiState.update {
            when (source) {
                OnlineSource.WALLHAVEN -> it // will never be called
                OnlineSource.BOORU -> it // boards are opened from the Boards screen
                OnlineSource.REDDIT -> it.copy(
                    showRedditInitDialog = partial(true),
                )
            }
        }
        showManageSourcesDialog(false)
    }

    fun showRedditInitDialog(show: Boolean) = localUiState.update {
        it.copy(showRedditInitDialog = partial(show))
    }

    fun updateRedditConfigAndCloseDialog(subreddits: Set<String>) = viewModelScope.launch {
        appPreferencesRepository.updateHomeRedditSearch(
            RedditSearch(
                query = "",
                filters = RedditFilters(
                    subreddits = subreddits,
                    includeNsfw = false,
                    sort = RedditSort.TOP,
                    timeRange = RedditTimeRange.WEEK,
                ),
            ),
        )
        localUiState.update {
            it.copy(showRedditInitDialog = partial(false))
        }
    }

    fun changeSource(source: OnlineSource) {
        if (uiState.value.selectedSource == source) {
            return
        }
        localUiState.update {
            it.copy(selectedSource = partial(source))
        }
    }

    fun updateManageSourcesDialogSources(
        sources: Map<OnlineSource, Boolean>,
    ) = localUiState.update {
        val existingSources = uiState.value.sources
        val updatedSources = sources.toPersistentMap()
        val saveEnabled = existingSources != updatedSources
        it.copy(
            manageSourcesState = partial(
                uiState.value.manageSourcesState.copy(
                    currentSources = updatedSources,
                    saveEnabled = saveEnabled,
                ),
            ),
        )
    }

    fun saveManageSources() = viewModelScope.launch {
        val manageSourcesState = uiState.value.manageSourcesState
        val currentSources = manageSourcesState.currentSources
        val allDisabled = currentSources.all { !it.value }
        if (allDisabled) {
            return@launch
        }
        appPreferencesRepository.updateHomeSources(currentSources)

        val prefs = appPreferencesRepository.appPreferencesFlow.first()
        val selectedSource = getCorrectSelectedSource(
            local = localUiState.value,
            homeSource = currentSources,
            homeRedditSearch = prefs.homeRedditSearch,
            hasBoards = boardsStore.boards.value.isNotEmpty(),
        )

        localUiState.update {
            it.copy(
                selectedSource = partial(selectedSource),
            )
        }
        showManageSourcesDialog(false)
    }

    suspend fun checkSavedSearchNameExists(name: String) = savedSearchRepository.exists(name)

    fun updateRedditSubredditFilter(filter: RedditSubredditFilter) = viewModelScope.launch {
        appPreferencesRepository.updateRedditSubredditFilter(filter)
    }
}

private val tempWallhavenTags = List(3) {
    WallhavenTag(
        id = it + 1L,
        name = "Loading...", // no need for string resource as "Loading..." won't be visible
        alias = emptyList(),
        categoryId = 0,
        category = "",
        purity = Purity.SFW,
        createdAt = Clock.System.now(),
    )
}

/**
 * Derives an effective [RedditSearch] that applies [filter] on top of the configured subreddits
 * without mutating any persisted setting.
 */
private fun applySubredditFilter(search: RedditSearch, filter: RedditSubredditFilter): RedditSearch {
    if (!filter.isActive) return search
    val configuredSubreddits = search.filters.subreddits
    val effective = filter.apply(configuredSubreddits)
    // Don't apply the filter if it would eliminate every subreddit — that would crash the
    // network layer and produce a confusing empty state. Show all instead.
    if (effective.isEmpty() || effective == configuredSubreddits) return search
    return search.copy(filters = search.filters.copy(subreddits = effective))
}

@Stable
@Partialize
data class HomeUiState(
    val sources: ImmutableMap<OnlineSource, Boolean> = persistentMapOf(),
    val mainSearch: Search? = null,
    val selectedSource: OnlineSource = OnlineSource.WALLHAVEN,
    val homeSearch: Search = WallhavenSearch(
        filters = WallhavenFilters(
            sorting = WallhavenSorting.TOPLIST,
            topRange = WallhavenTopRange.ONE_DAY,
        ),
    ),
    val saveSearchAsSearch: Search? = null,
    val savedSearches: List<SavedSearch> = emptyList(),
    val wallhaven: WallhavenState = WallhavenState(),
    val reddit: RedditState = RedditState(),
    val showFilters: Boolean = false,
    val blurSketchy: Boolean = false,
    val blurNsfw: Boolean = false,
    val showNSFW: Boolean = false,
    val selectedWallpaper: Wallpaper? = null,
    val showSavedSearchesDialog: Boolean = false,
    val showSavedSearchesForSearchBar: Boolean = false,
    val layoutPreferences: LayoutPreferences = LayoutPreferences(),
    val favorites: ImmutableList<Favorite> = persistentListOf(),
    val viewedList: ImmutableList<Viewed> = persistentListOf(),
    val viewedWallpapersLook: ViewedWallpapersLook = ViewedWallpapersLook.DIM_WITH_LABEL,
    val lightDarkList: ImmutableList<LightDark> = persistentListOf(),
    val showRedditInitDialog: Boolean = false,
    val manageSourcesState: ManageSourcesState = ManageSourcesState(),
    val prevMainWallhavenSearch: WallhavenSearch? = null,
    val prevMainRedditSearch: RedditSearch? = null,
    val quickActionsWallpaper: Wallpaper? = null,
    val redditSubredditFilter: RedditSubredditFilter = RedditSubredditFilter(),
    val websites: ImmutableList<SavedBoard> = persistentListOf(),
    val selectedBoardUrl: String? = null,
    val websiteAddRunning: Boolean = false,
    val websiteAddError: String? = null,
    val lastCrash: String? = null,
) {
    val isHome = mainSearch == null
    val showSaveAsDialog = saveSearchAsSearch != null
}

data class WallhavenState(
    val wallhavenTags: ImmutableList<WallhavenTag> = persistentListOf(),
    val areTagsLoading: Boolean = true,
)

data class RedditState(
    val subreddits: ImmutableSet<String> = persistentSetOf(),
)

data class ManageSourcesState(
    val showDialog: Boolean = false,
    val currentSources: ImmutableMap<OnlineSource, Boolean> = persistentMapOf(),
    val saveEnabled: Boolean = false,
)
