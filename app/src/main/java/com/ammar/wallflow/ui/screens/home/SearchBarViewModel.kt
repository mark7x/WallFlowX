package com.ammar.wallflow.ui.screens.home

import com.ammar.wallflow.model.search.BooruSearch

import android.app.Application
import androidx.compose.runtime.Stable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ammar.wallflow.data.db.entity.search.toSearch
import com.ammar.wallflow.data.repository.SearchHistoryRepository
import com.ammar.wallflow.extensions.trimAll
import com.ammar.wallflow.model.OnlineSource
import com.ammar.wallflow.model.search.RedditSearch
import com.ammar.wallflow.model.search.Search
import com.ammar.wallflow.model.search.WallhavenSearch
import com.ammar.wallflow.model.search.getSupportingText
import com.ammar.wallflow.ui.common.Suggestion
import com.ammar.wallflow.ui.common.mainsearch.MainSearchBar.Defaults
import com.github.materiiapps.partial.Partialize
import com.github.materiiapps.partial.getOrElse
import com.github.materiiapps.partial.getOrNull
import com.github.materiiapps.partial.partial
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.ammar.wallflow.sources.booru.BooruTagSuggester
import kotlinx.coroutines.Job

@HiltViewModel
class SearchBarViewModel @Inject constructor(
    private val application: Application,
    private val searchHistoryRepository: SearchHistoryRepository,
    private val booruTagSuggester: BooruTagSuggester,
) : AndroidViewModel(
    application = application,
) {
    private val localUiState = MutableStateFlow(SearchBarUiStatePartial())
    private val booruSuggestions = MutableStateFlow<List<Suggestion<Search>>>(emptyList())
    private var booruSuggestionJob: Job? = null

    val uiState = combine(
        localUiState,
        searchHistoryRepository.getAll(),
        booruSuggestions,
    ) { local, searchHistory, booruTags ->
        val localQuery = local.search.getOrNull()?.query?.trimAll()?.lowercase() ?: ""
        local.merge(
            SearchBarUiState(
                suggestions = if (local.source.getOrNull() == OnlineSource.BOORU) booruTags else searchHistory
                    .filter {
                        localQuery.isBlank() || it.query.trimAll().lowercase().contains(localQuery)
                    }
                    .map { it.toSearch() }
                    .filter {
                        when (local.source.getOrNull()) {
                            OnlineSource.WALLHAVEN -> it is WallhavenSearch
                            OnlineSource.REDDIT -> it is RedditSearch
                            OnlineSource.BOORU -> false
                            null -> false
                        }
                    }
                    .map {
                        when (it) {
                            is BooruSearch -> Suggestion(
                                value = it,
                                source = OnlineSource.BOORU,
                                headline = it.query,
                                supportingText = null,
                            )
                            is WallhavenSearch -> Suggestion(
                                value = it,
                                source = OnlineSource.WALLHAVEN,
                                headline = it.query,
                                supportingText = it.getSupportingText(application),
                            )
                            is RedditSearch -> Suggestion(
                                value = it,
                                source = OnlineSource.REDDIT,
                                headline = it.query,
                                supportingText = null,
                            )
                        }
                    },

            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SearchBarUiState(),
    )

    fun setActive(active: Boolean) = localUiState.update {
        it.copy(active = partial(active))
    }

    fun setSource(source: OnlineSource) = localUiState.update {
        it.copy(source = partial(source))
    }

    fun setQuery(query: String) {
        localUiState.update { updateQuery(it, query) }
        val search = localUiState.value.search.getOrNull()
        if (search is BooruSearch) {
            requestBooruSuggestions(search)
        } else {
            booruSuggestionJob?.cancel()
            booruSuggestions.value = emptyList()
        }
    }

    private fun updateQuery(it: SearchBarUiStatePartial, query: String): SearchBarUiStatePartial {
        val updated = when (
            val currentSearch =
                it.search.getOrElse { Defaults.wallhavenSearch }
        ) {
            is RedditSearch -> currentSearch.copy(
                query = query,
            )
            is BooruSearch -> currentSearch.copy(
                query = query,
            )
            is WallhavenSearch -> currentSearch.copy(
                query = query,
            )
        }
        return it.copy(search = partial(updated))
    }

    /**
     * Suggests tags for the last word typed. Each suggestion keeps the earlier words, so
     * choosing a tag completes the search rather than replacing it.
     */
    private fun requestBooruSuggestions(search: BooruSearch) {
        booruSuggestionJob?.cancel()
        val query = search.query
        val lastSpace = query.lastIndexOf(' ')
        val head = if (lastSpace >= 0) query.substring(0, lastSpace + 1) else ""
        val prefix = query.substring(lastSpace + 1)
        booruSuggestionJob = viewModelScope.launch {
            delay(250)
            val tags = booruTagSuggester.suggest(search.filters.style, search.filters.boardUrl, prefix)
            booruSuggestions.value = tags.map { tag ->
                Suggestion(
                    value = search.copy(query = "$head$tag "),
                    source = OnlineSource.BOORU,
                    headline = tag,
                    supportingText = null,
                )
            }
        }
    }

    fun setSearch(search: Search) = localUiState.update {
        it.copy(search = partial(search))
    }

    fun onSearch(search: Search) {
        localUiState.update { it.copy(search = partial(search)) }
        viewModelScope.launch {
            delay(1000) // delay for better ux
            searchHistoryRepository.addSearch(search)
        }
    }

    fun setSearchToDelete(search: Search?) = localUiState.update {
        it.copy(searchToDelete = partial(search))
    }

    fun onConfirmDeleteSearch() = viewModelScope.launch {
        val searchToDelete = localUiState.value.searchToDelete.getOrNull() ?: return@launch
        searchHistoryRepository.deleteSearch(searchToDelete)
        localUiState.update { it.copy(searchToDelete = partial(null)) }
    }

    fun onCancelDeleteSearch() = viewModelScope.launch {
        localUiState.update { it.copy(searchToDelete = partial(null)) }
    }
}

@Stable
@Partialize
data class SearchBarUiState(
    val source: OnlineSource = OnlineSource.WALLHAVEN,
    val search: Search = Defaults.wallhavenSearch,
    val active: Boolean = false,
    val suggestions: List<Suggestion<Search>> = emptyList(),
    val searchToDelete: Search? = null,
    val showSavedSearchesDialog: Boolean = false,
)
