package com.ammar.wallflow.ui.sites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ammar.wallflow.sites.SiteProfile
import com.ammar.wallflow.sites.SiteProfileStore
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class SitesViewModel @Inject constructor(
    private val store: SiteProfileStore,
) : ViewModel() {
    private val _profiles = MutableStateFlow<List<SiteProfile>>(emptyList())
    val profiles: StateFlow<List<SiteProfile>> = _profiles.asStateFlow()

    fun refresh() {
        viewModelScope.launch { _profiles.value = store.all() }
    }

    fun add(name: String, searchUrl: String) {
        viewModelScope.launch {
            store.save(
                SiteProfile(
                    id = UUID.randomUUID().toString(),
                    name = name.trim(),
                    searchUrl = searchUrl.trim(),
                ),
            )
            _profiles.value = store.all()
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            store.delete(id)
            _profiles.value = store.all()
        }
    }
}
