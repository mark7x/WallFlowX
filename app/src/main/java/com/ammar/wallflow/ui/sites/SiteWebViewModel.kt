package com.ammar.wallflow.ui.sites

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.ammar.wallflow.sites.SiteLog
import com.ammar.wallflow.sites.SiteRenderedCache
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
        "next" -> {
            val link = pick.optJSONObject("link")
            val selector = link?.nonBlank("classSelector")
                ?: link?.nonBlank("selector")
                ?: target.nonBlank("classSelector")
                ?: target.nonBlank("selector")
                ?: return null
            profile.copy(nextPageSelector = selector, nextPageAttr = "href")
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
    private val renderedCache: SiteRenderedCache,
    private val siteLog: SiteLog,
) : ViewModel() {
    private val _profile = MutableStateFlow<SiteProfile?>(null)
    val profile: StateFlow<SiteProfile?> = _profile.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Debug mode: the newest lines of the site log (picks, page loads, scans, feed loads). */
    val debugLog: StateFlow<List<String>> = siteLog.lines

    fun debug(message: String) {
        siteLog.log("learn", message)
    }

    fun clearDebug() {
        siteLog.clear()
    }

    /** The whole saved log, for copying. */
    fun fullLogText(): String = siteLog.fullText()

    fun load(id: String) {
        viewModelScope.launch { _profile.value = store.get(id) }
    }

    fun onPick(json: String) {
        viewModelScope.launch {
            val current = _profile.value ?: return@launch
            val pick = runCatching { JSONObject(json) }.getOrNull()
            debug("pick: mode=${pick?.optString("mode") ?: "unreadable"}, selector=${pick?.optJSONObject("target")?.optString("selector")}")
            val updated = pick?.let { applyPick(current, it) }
            debug("pick applied: ${updated != null}")
            if (updated != null && pick?.optString("mode") == "next") {
                debug("saved next-page selector: ${updated.nextPageSelector}")
            }
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

    /** Keeps the items this page shows, so the site's browse grid can show them. */
    /**
     * Learns the site's search address from a search done on the site: the word is found in the
     * page address and replaced with {tags}.
     */
    fun saveSearch(pageUrl: String, word: String) {
        viewModelScope.launch {
            val current = _profile.value ?: return@launch
            val term = word.trim()
            if (term.isEmpty() || pageUrl.isBlank()) return@launch
            val forms = listOf(
                term,
                java.net.URLEncoder.encode(term, "UTF-8"),
                java.net.URLEncoder.encode(term, "UTF-8").replace("+", "%20"),
                term.replace(' ', '_'),
                term.replace(' ', '-'),
            ).distinct()
            val form = forms.firstOrNull { pageUrl.contains(it, ignoreCase = true) }
            if (form == null) {
                debug("save search: '$term' not found in $pageUrl")
                _message.value = context.getString(R.string.site_search_word_missing, term)
                return@launch
            }
            val at = pageUrl.indexOf(form, ignoreCase = true)
            val template = pageUrl.substring(0, at) + "{tags}" + pageUrl.substring(at + form.length)
            val updated = current.copy(searchUrl = template)
            store.save(updated)
            _profile.value = updated
            debug("save search: address is now $template")
            _message.value = context.getString(R.string.site_search_saved, template)
        }
    }

    fun scanPage(html: String, pageUrl: String) {
        viewModelScope.launch {
            val current = _profile.value ?: return@launch
            val found = withContext(Dispatchers.Default) {
                grabber.renderedItemsIn(current, html, pageUrl)
            }
            debug("scan $pageUrl: html ${html.length} chars, ${found.size} items, item selector=${current.itemSelector}")
            if (found.isEmpty()) {
                _message.value = context.getString(R.string.site_scan_none)
                return@launch
            }
            renderedCache.add(current.id, found, pageUrl)
            _message.value = context.getString(R.string.site_scanned, found.size)
        }
    }

    fun harvestTags(html: String) {
        viewModelScope.launch {
            val current = _profile.value ?: return@launch
            val tags = withContext(Dispatchers.Default) { grabber.tagsIn(current, html) }
            debug("read tags: ${tags.size} found, tag selector=${current.tagSelector}")
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
