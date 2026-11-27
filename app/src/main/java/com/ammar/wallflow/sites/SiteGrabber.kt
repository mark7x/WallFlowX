package com.ammar.wallflow.sites

import kotlinx.serialization.Serializable
import android.webkit.CookieManager
import com.ammar.wallflow.sources.booru.BOORU_USER_AGENT
import com.ammar.wallflow.sources.pacing.RequestPacer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

@Serializable
data class SiteItem(val postUrl: String?, val thumbUrl: String?)

/**
 * Fetches a taught site's pages. It reuses the cookies from the in-app browser, so a
 * login done there carries over. Requests go through the same per-site pacing as booru.
 */
@Singleton
class SiteGrabber @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val pacer: RequestPacer,
    private val siteLog: SiteLog,
) {
    suspend fun search(profile: SiteProfile, tags: String, page: Int): Result<List<SiteItem>> =
        runCatching {
            val address = if (page > 1 && profile.nextPageSelector != null) {
                addressOfPage(profile, tags, page) ?: run {
                    siteLog.log("grab", "${profile.name}: page $page has no address, stopping")
                    return@runCatching emptyList()
                }
            } else {
                profile.searchAddress(tags, page)
            }
            siteLog.log("grab", "${profile.name}: reading page $page at $address")
            val doc = fetchDocument(address)
            nextLinkOf(profile, doc)?.let { nextPages[pageKey(profile, tags, page + 1)] = it }
            val items = itemsIn(profile, doc)
            siteLog.log("grab", "${profile.name}: page $page gave ${items.size} items")
            items
        }.onFailure { siteLog.log("grab", "${profile.name}: page $page failed: ${it.message}") }

    /** The page's learned "next page" link. Only links to the same site are followed, so ad links are ignored. */
    private fun nextLinkOf(profile: SiteProfile, doc: Document): String? {
        val selector = profile.nextPageSelector ?: run {
            siteLog.log("next", "${profile.name}: no next-page pick learned")
            return null
        }
        val next = doc.selectFirst(selector)?.absUrl(profile.nextPageAttr)?.takeIf { it.isNotBlank() }
        if (next == null) {
            siteLog.log("next", "${profile.name}: next-page selector matched nothing on ${doc.location()}")
            return null
        }
        val siteHost = runCatching { java.net.URI(profile.searchAddress("", 1)).host }.getOrNull()
        val nextHost = runCatching { java.net.URI(next).host }.getOrNull()
        if (siteHost == null || siteHost != nextHost) {
            siteLog.log("next", "${profile.name}: next link ignored, other host: $next")
            return null
        }
        siteLog.log("next", "${profile.name}: next link is $next")
        return next
    }

    /**
     * Address of a later page. The links are only known once the page before has been read,
     * so the first page is read again, following links, the first time a later page is asked for.
     */
    private suspend fun addressOfPage(profile: SiteProfile, tags: String, page: Int): String? {
        nextPages[pageKey(profile, tags, page)]?.let { return it }
        var current = profile.searchAddress(tags, 1)
        siteLog.log("next", "${profile.name}: walking pages 1 to ${page - 1} to find page $page")
        for (p in 1 until page) {
            current = nextLinkOf(profile, fetchDocument(current)) ?: return null
            nextPages[pageKey(profile, tags, p + 1)] = current
        }
        return current
    }

    private val nextPages = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun pageKey(profile: SiteProfile, tags: String, page: Int) = "${profile.id}|$tags|$page"

    /** Items from a page the browser has already rendered. Works for JavaScript-driven sites. */
    fun renderedItemsIn(profile: SiteProfile, html: String, pageUrl: String): List<SiteItem> =
        runCatching { itemsIn(profile, Jsoup.parse(html, pageUrl)) }.getOrDefault(emptyList())

    private fun itemsIn(profile: SiteProfile, doc: Document): List<SiteItem> {
        val itemSelector = profile.itemSelector
            ?: error("This site has no grid item learned yet.")
        return doc.select(itemSelector).mapNotNull { element ->
            val link = if (element.tagName() == "a") {
                element.absUrl("href")
            } else {
                element.selectFirst("a[href]")?.absUrl("href")
            }?.takeIf { it.isNotBlank() }
            val image = if (element.tagName() == "img") element else element.selectFirst("img")
            val thumb = image?.let { img ->
                listOf(profile.thumbAttr, "data-src", "src")
                    .map { img.absUrl(it) }
                    .firstOrNull { it.isNotBlank() }
            }
            if (link == null && thumb == null) null else SiteItem(postUrl = link, thumbUrl = thumb)
        }
    }

    /**
     * Full-size image address on a post page. Uses the learned full image pick when there is one,
     * otherwise the page's og:image tag, which most sites set to the full image.
     */
    suspend fun fullImageUrl(profile: SiteProfile, postUrl: String): String? = runCatching {
        siteLog.log("post", "${profile.name}: full image for $postUrl")
        val doc = fetchDocument(postUrl)
        val picked = profile.postImageSelector?.let { selector ->
            doc.selectFirst(selector)?.let { el ->
                listOfNotNull(profile.postImageAttr, "src", "href")
                    .map { el.absUrl(it) }
                    .firstOrNull { it.isNotBlank() }
            }
        }
        val full = picked ?: doc.selectFirst("meta[property=og:image]")?.absUrl("content")?.takeIf { it.isNotBlank() }
        siteLog.log("post", "${profile.name}: full image ${full ?: "not found"}")
        full
    }.getOrNull()

    /** Tags on a page, read from its HTML with the learned tag selector. */
    fun tagsIn(profile: SiteProfile, html: String): List<String> {
        val selector = profile.tagSelector ?: return emptyList()
        return runCatching {
            Jsoup.parse(html).select(selector)
                .map { it.text().trim().lowercase().replace(' ', '_') }
                .filter { it.isNotBlank() }
                .distinct()
        }.getOrDefault(emptyList())
    }

    private suspend fun fetchDocument(url: String): Document = withContext(Dispatchers.IO) {
        pacer.await(url)
        val cookies = CookieManager.getInstance().getCookie(url).orEmpty()
        siteLog.log("http", "GET $url (cookies sent: ${cookies.isNotBlank()})")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", BOORU_USER_AGENT)
            .apply { if (cookies.isNotBlank()) header("Cookie", cookies) }
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (response.code == 429) {
                siteLog.log("http", "429 rate limited at $url, Retry-After ${response.header("Retry-After")}")
                pacer.rateLimited(url, response.header("Retry-After"))
            }
            if (!response.isSuccessful) {
                siteLog.log("http", "HTTP ${response.code} at $url")
                error("HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            siteLog.log("http", "${response.code} ${body.length} chars from $url")
            Jsoup.parse(body, url)
        }
    }
}
