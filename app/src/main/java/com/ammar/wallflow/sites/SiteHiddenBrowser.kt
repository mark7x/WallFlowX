package com.ammar.wallflow.sites

import android.content.Context
import android.net.Uri
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Loads the next page of a taught site in a hidden browser. It opens the last scanned page,
 * clicks the learned Next button, and saves what the new page shows. The home feed uses it
 * when it runs out of scanned posts. Only one hidden load runs at a time.
 */
@Singleton
class SiteHiddenBrowser @Inject constructor(
    @ApplicationContext private val context: Context,
    private val grabber: SiteGrabber,
    private val renderedCache: SiteRenderedCache,
    private val siteLog: SiteLog,
) {
    private val mutex = Mutex()

    /** Returns how many new posts were saved. */
    suspend fun loadNextPage(profile: SiteProfile): Int = mutex.withLock {
        val selector = profile.nextPageSelector ?: return@withLock 0
        val start = renderedCache.resumeUrl(profile.id) ?: profile.searchAddress("", 1)
        val before = renderedCache.get(profile.id).size
        siteLog.log("hidden", "${profile.name}: opening $start and clicking the learned Next button")
        val page = withContext(Dispatchers.Main) { openAndClick(start, selector) }
        if (page == null) {
            siteLog.log("hidden", "${profile.name}: hidden load failed or timed out")
            return@withLock 0
        }
        siteLog.log("hidden", "${profile.name}: click result ${page.click}, now at ${page.url}")
        val items = grabber.renderedItemsIn(profile, page.html, page.url)
        renderedCache.add(profile.id, items, page.url)
        val added = renderedCache.get(profile.id).size - before
        siteLog.log("hidden", "${profile.name}: ${items.size} items read, $added new saved")
        added
    }

    private class Page(val html: String, val url: String, val click: String?)

    /** Runs on the main thread, where WebView must be used. */
    private suspend fun openAndClick(start: String, selector: String): Page? {
        val webView = WebView(context)
        try {
            // A WebView that is never shown has no size, so the site's "Show more" logic never sees the
            // page and adds nothing. Give it a real size, as if it were on screen.
            webView.measure(
                View.MeasureSpec.makeMeasureSpec(VIEW_WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(VIEW_HEIGHT, View.MeasureSpec.EXACTLY),
            )
            webView.layout(0, 0, VIEW_WIDTH, VIEW_HEIGHT)
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            webView.settings.javaScriptCanOpenWindowsAutomatically = false
            val siteHost = Uri.parse(start).host.orEmpty()
            val loaded = CompletableDeferred<Boolean>()
            webView.webViewClient = object : WebViewClient() {
                // Same rule as the learning browser: only pages on the taught site may load.
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url ?: return true
                    val host = url.host.orEmpty()
                    val allowed = (url.scheme == "http" || url.scheme == "https") &&
                        (host == siteHost || host.endsWith(".$siteHost"))
                    return !allowed
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    loaded.complete(true)
                }

                override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                    loaded.complete(false)
                    return true
                }
            }
            webView.loadUrl(start)
            if (withTimeoutOrNull(LOAD_TIMEOUT_MS) { loaded.await() } != true) return null
            val click = webView.evaluate(clickScript(selector))
            delay(CLICK_WAIT_MS)
            val raw = webView.evaluate("document.documentElement.outerHTML") ?: return null
            val html = JSONTokener(raw).nextValue() as? String ?: return null
            return Page(html = html, url = webView.url ?: start, click = click)
        } finally {
            webView.stopLoading()
            webView.destroy()
        }
    }

    private suspend fun WebView.evaluate(script: String): String? = suspendCancellableCoroutine { cont ->
        evaluateJavascript(script) { result -> if (cont.isActive) cont.resume(result) }
    }

    private fun clickScript(selector: String) =
        "(function(){var el=document.querySelector(${JSONObject.quote(selector)});" +
            "if(!el)return 'no element';el.click();return 'clicked';})()"

    private companion object {
        const val LOAD_TIMEOUT_MS = 30_000L
        const val CLICK_WAIT_MS = 3_000L
        const val VIEW_WIDTH = 1080
        const val VIEW_HEIGHT = 2400
    }
}
