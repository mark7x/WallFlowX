package com.ammar.wallflow.ui.sites

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ammar.wallflow.R
import com.ammar.wallflow.sites.SiteGrabber
import com.ammar.wallflow.sites.SiteProfile
import com.ammar.wallflow.sites.SiteProfileStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Applies one tap from the learning browser to the profile. Null when the tap cannot be used. */
internal fun applyPick(profile: SiteProfile, pick: JSONObject): SiteProfile? {
    val target = pick.optJSONObject("target") ?: return null
    val attr = pick.optString("attr", "src")
    return when (pick.optString("mode")) {
        "item" -> {
            val link = pick.optJSONObject("link")
            val selector = link?.nonBlank("classSelector")
                ?: link?.nonBlank("selector")
                ?: target.nonBlank("classSelector")
                ?: target.nonBlank("selector")
                ?: return null
            profile.copy(itemSelector = selector, thumbAttr = attr)
        }
        "image" -> {
            val selector = target.nonBlank("selector") ?: return null
            profile.copy(postImageSelector = selector, postImageAttr = attr)
        }
        "tag" -> {
            val selector = target.nonBlank("classSelector")
                ?: target.nonBlank("selector")
                ?: return null
            profile.copy(tagSelector = selector)
        }
        else -> null
    }
}

private fun JSONObject.nonBlank(key: String): String? =
    optString(key).takeIf { it.isNotBlank() && it != "null" }

@HiltViewModel
class SiteWebViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: SiteProfileStore,
    private val grabber: SiteGrabber,
) : ViewModel() {
    private val _profile = MutableStateFlow<SiteProfile?>(null)
    val profile: StateFlow<SiteProfile?> = _profile.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun load(id: String) {
        viewModelScope.launch { _profile.value = store.get(id) }
    }

    fun onPick(json: String) {
        viewModelScope.launch {
            val current = _profile.value ?: return@launch
            val pick = runCatching { JSONObject(json) }.getOrNull()
            val updated = pick?.let { applyPick(current, it) }
            if (pick == null || updated == null) {
                _message.value = context.getString(R.string.site_pick_error)
                return@launch
            }
            store.save(updated)
            _profile.value = updated
            val label = when (pick.optString("mode")) {
                "item" -> R.string.site_label_item
                "image" -> R.string.site_label_image
                else -> R.string.site_label_tag
            }
            _message.value = context.getString(R.string.site_picked, context.getString(label))
        }
    }

    fun harvestTags(html: String) {
        viewModelScope.launch {
            val current = _profile.value ?: return@launch
            val tags = grabber.tagsIn(current, html)
            if (tags.isEmpty()) {
                _message.value = context.getString(R.string.site_no_tags)
                return@launch
            }
            store.addTags(current.id, tags)
            _profile.value = store.get(current.id)
            _message.value = context.getString(R.string.site_tags_read, tags.size)
        }
    }
}
