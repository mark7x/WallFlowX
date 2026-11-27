package com.ammar.wallflow.sites

import java.net.URLEncoder
import kotlinx.serialization.Serializable

/**
 * A site the app has been taught. Each part is learned from the page itself:
 * the grid item, the full image, and the tags. Nothing is hard-coded per site.
 */
@Serializable
data class SiteProfile(
    val id: String,
    val name: String,
    /** Search page address. {tags} and {page} are filled in when browsing. */
    val searchUrl: String,
    /** CSS selector for one grid item on a search page. Null until learned. */
    val itemSelector: String? = null,
    /** Attribute on the item's image that holds the thumbnail address. */
    val thumbAttr: String = "src",
    /** CSS selector for the full image on a post page. Null until learned. */
    val postImageSelector: String? = null,
    val postImageAttr: String = "src",
    /** CSS selector for tag links on a post page. Null until learned. */
    val tagSelector: String? = null,
    /** How often each tag has been seen. Feeds tag suggestions. */
    val tagCounts: Map<String, Int> = emptyMap(),
    /** The learned "next page" link. When set, pages after the first follow it. */
    val nextPageSelector: String? = null,
    val nextPageAttr: String = "href",
) {
    val canBrowse: Boolean get() = itemSelector != null

    fun searchAddress(tags: String, page: Int): String = searchUrl
        .replace("{tags}", URLEncoder.encode(tags, "UTF-8"))
        .replace("{page}", page.toString())

    /** Most-seen learned tags that start with [prefix]. */
    fun suggestTags(prefix: String, limit: Int = 10): List<String> {
        val p = prefix.trim().lowercase()
        if (p.isEmpty()) return emptyList()
        return tagCounts.entries
            .filter { it.key.startsWith(p) }
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key }
    }
}
