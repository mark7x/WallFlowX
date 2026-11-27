package com.ammar.wallflow.ui.sites

import com.ammar.wallflow.sites.SiteRenderedCache
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ammar.wallflow.sites.SiteItem
import com.ammar.wallflow.sites.SiteGrabber
import com.ammar.wallflow.sites.SiteProfile
import com.ammar.wallflow.sites.SiteProfileStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BrowseState(
    val items: List<SiteItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val suggestions: List<String> = emptyList(),
)

@HiltViewModel
class SiteDetailViewModel @Inject constructor(
    private val store: SiteProfileStore,
    private val grabber: SiteGrabber,
    private val renderedCache: SiteRenderedCache,
) : ViewModel() {
    private val _profile = MutableStateFlow<SiteProfile?>(null)
    val profile: StateFlow<SiteProfile?> = _profile.asStateFlow()

    private val _state = MutableStateFlow(BrowseState())
    val state: StateFlow<BrowseState> = _state.asStateFlow()

    private var query = ""
    private var page = 1
    private var job: Job? = null

    fun setBlockThirdParty(enabled: Boolean) {
        viewModelScope.launch {
            val current = _profile.value ?: return@launch
            val updated = current.copy(blockThirdParty = enabled)
            store.save(updated)
            _profile.value = updated
        }
    }

    fun setHiddenLoadMore(enabled: Boolean) {
        viewModelScope.launch {
            val current = _profile.value ?: return@launch
            val updated = current.copy(hiddenLoadMore = enabled)
            store.save(updated)
            _profile.value = updated
        }
    }

    fun load(id: String) {
        viewModelScope.launch {
            _profile.value = store.get(id)
            _state.update { s ->
                if (s.items.isEmpty()) s.copy(items = renderedCache.get(id)) else s
            }
        }
    }

    fun onQueryChange(text: String) {
        query = text
        val lastWord = text.substringAfterLast(' ')
        _state.update { it.copy(suggestions = _profile.value?.suggestTags(lastWord).orEmpty()) }
    }

    fun search(text: String) {
        query = text.trim()
        page = 1
        fetch(replace = true)
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || current.items.isEmpty()) return
        page++
        fetch(replace = false)
    }

    private fun fetch(replace: Boolean) {
        val current = _profile.value ?: return
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val result = grabber.search(current, query, page)
            _state.update { s ->
                result.fold(
                    onSuccess = { items ->
                        s.copy(
                            loading = false,
                            items = if (replace) items else s.items + items,
                            suggestions = emptyList(),
                        )
                    },
                    onFailure = { error ->
                        s.copy(
                            loading = false,
                            error = error.message,
                            items = if (replace) emptyList() else s.items,
                        )
                    },
                )
            }
        }
    }
}
