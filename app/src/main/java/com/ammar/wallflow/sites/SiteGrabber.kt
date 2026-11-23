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
) {
    suspend fun search(profile: SiteProfile, tags: String, page: Int): Result<List<SiteItem>> =
        runCatching {
            itemsIn(profile, fetchDocument(profile.searchAddress(tags, page)))
        }

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

    /** Full-size image address on a post page, read with the learned full image pick. */
    suspend fun fullImageUrl(profile: SiteProfile, postUrl: String): String? = runCatching {
        val selector = profile.postImageSelector ?: return null
        val doc = fetchDocument(postUrl)
        doc.selectFirst(selector)?.let { el ->
            listOfNotNull(profile.postImageAttr, "src", "href")
                .map { el.absUrl(it) }
                .firstOrNull { it.isNotBlank() }
        }
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
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", BOORU_USER_AGENT)
            .apply { if (cookies.isNotBlank()) header("Cookie", cookies) }
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (response.code == 429) pacer.rateLimited(url, response.header("Retry-After"))
            if (!response.isSuccessful) error("HTTP ${response.code}")
            Jsoup.parse(response.body?.string().orEmpty(), url)
        }
    }
}
