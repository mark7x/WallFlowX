package com.ammar.wallflow.sites

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
 * Loads more posts of a taught site in a hidden browser, the same way the learning page does:
 * one page stays open, the learned Next button is clicked, and the posts the page then shows
 * are saved. The page stays open between loads, so each load is one click. It is closed after
 * a few idle minutes, when the site changes, or when it grows too large.
 */
@Singleton
class SiteHiddenBrowser @Inject constructor(
    @ApplicationContext private val context: Context,
    private val grabber: SiteGrabber,
    private val renderedCache: SiteRenderedCache,
    private val siteLog: SiteLog,
) {
    private val mutex = Mutex()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var openFor: String? = null
    private var pageLoaded: CompletableDeferred<Boolean>? = null
    private val idleRelease = Runnable { release("idle for ${IDLE_MS / 60_000} minutes") }

    /** Closes the hidden page, so the next load starts from a fresh page. */
    suspend fun close(reason: String) = mutex.withLock {
        withContext(Dispatchers.Main) { release(reason) }
    }

    /**
     * Saves more posts under [key]. With [click] it clicks the learned Next button first;
     * without it, it only reads what the page shows (for a first page HTTP could not read).
     * Returns how many new posts were saved.
     */
    suspend fun loadNextPage(profile: SiteProfile, query: String = "", key: String = profile.id, click: Boolean = true): Int = mutex.withLock {
        val nextSelector = profile.nextPageSelector
        val itemSelector = profile.itemSelector
        if (itemSelector == null || (click && nextSelector == null)) {
            log(profile, "cannot load more: next button learned=${nextSelector != null}, grid item learned=${itemSelector != null}")
            return@withLock 0
        }
        val before = renderedCache.get(key).size
        val started = System.currentTimeMillis()
        val page = withContext(Dispatchers.Main) {
            mainHandler.removeCallbacks(idleRelease)
            try {
                step(profile, query, key, itemSelector, if (click) nextSelector else null)
            } finally {
                mainHandler.postDelayed(idleRelease, IDLE_MS)
            }
        } ?: return@withLock 0
        val items = grabber.renderedItemsIn(profile, page.html, page.url)
        renderedCache.add(key, items, page.url)
        val added = renderedCache.get(key).size - before
        log(profile, "read ${items.size} posts from ${page.url}, $added new saved, took ${System.currentTimeMillis() - started} ms")
        added
    }

    private class Page(val html: String, val url: String)

    /** Runs on the main thread, where WebView must be used. */
    private suspend fun step(profile: SiteProfile, query: String, key: String, itemSelector: String, nextSelector: String?): Page? {
        var view = ensureOpen(profile, query, key) ?: return null
        var countBefore = waitForCount(view, itemSelector, atLeast = 1, timeoutMs = RENDER_TIMEOUT_MS)
        if (countBefore > MAX_PAGE_ITEMS) {
            val here = view.url
            log(profile, "hidden page has $countBefore posts, reopening at $here to keep memory low")
            release("page too large")
            if (here != null) renderedCache.add(key, emptyList(), here)
            view = ensureOpen(profile, query, key) ?: return null
            countBefore = waitForCount(view, itemSelector, atLeast = 1, timeoutMs = RENDER_TIMEOUT_MS)
        }
        log(profile, "posts on hidden page before click: $countBefore")
        if (countBefore <= 0) {
            log(profile, "no posts appeared: ${pageState(view, itemSelector, nextSelector ?: itemSelector)}")
            return null
        }
        if (nextSelector == null) {
            log(profile, "reading the page without clicking")
            val raw = view.evaluate("document.documentElement.outerHTML") ?: return null
            val html = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull() ?: return null
            return Page(html = html, url = view.url.orEmpty())
        }
        if (!waitForElement(view, nextSelector, BUTTON_TIMEOUT_MS)) {
            log(profile, "next button not found: ${pageState(view, itemSelector, nextSelector)}")
            return null
        }
        log(profile, "click result: ${view.evaluate(clickScript(nextSelector))}")
        val waitStart = System.currentTimeMillis()
        var countAfter = countBefore
        while (System.currentTimeMillis() - waitStart < GROW_TIMEOUT_MS) {
            delay(POLL_MS)
            countAfter = countOf(view, itemSelector)
            if (countAfter > countBefore || countAfter < 0) break
        }
        log(profile, "posts after click: $countAfter (waited ${System.currentTimeMillis() - waitStart} ms)")
        if (countAfter < 0) return null
        if (countAfter <= countBefore) {
            log(profile, "page did not grow: ${pageState(view, itemSelector, nextSelector)}")
        }
        val raw = view.evaluate("document.documentElement.outerHTML") ?: return null
        val html = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull() ?: return null
        return Page(html = html, url = view.url.orEmpty())
    }

    private suspend fun ensureOpen(profile: SiteProfile, query: String, key: String): WebView? {
        webView?.takeIf { openFor == key }?.let { return it }
        if (webView != null) release("switching to ${profile.name} '${query}'")
        val start = renderedCache.resumeUrl(key) ?: profile.searchAddress(query, 1)
        log(profile, "opening hidden page $start (ad blocking ${if (profile.blockThirdParty) "on" else "off"})")
        val loaded = CompletableDeferred<Boolean>()
        pageLoaded = loaded
        val view = createView(profile, start)
        webView = view
        openFor = key
        view.loadUrl(start)
        val ok = withTimeoutOrNull(LOAD_TIMEOUT_MS) { loaded.await() }
        if (ok != true) {
            log(profile, if (ok == null) "hidden page did not finish loading in ${LOAD_TIMEOUT_MS / 1000} s" else "hidden page crashed while loading")
            release("load failed")
            return null
        }
        return view
    }

    private fun createView(profile: SiteProfile, start: String): WebView {
        val siteHost = Uri.parse(start).host.orEmpty()
        val view = WebView(context)
        // A WebView that is never shown has no size, so the page would lay out as zero by zero.
        view.measure(
            View.MeasureSpec.makeMeasureSpec(VIEW_WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(VIEW_HEIGHT, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, VIEW_WIDTH, VIEW_HEIGHT)
        view.settings.javaScriptEnabled = true
        view.settings.domStorageEnabled = true
        view.settings.javaScriptCanOpenWindowsAutomatically = false
        view.settings.setSupportMultipleWindows(false)
        val blockedHosts = java.util.Collections.synchronizedSet(mutableSetOf<String>())
        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url ?: return true
                val allowed = (url.scheme == "http" || url.scheme == "https") &&
                    SiteRequestFilter.isSameSite(url.host.orEmpty(), siteHost)
                if (!allowed) log(profile, "hidden page navigation blocked: $url")
                return !allowed
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                if (!profile.blockThirdParty || request == null || request.isForMainFrame) return null
                val host = request.url?.host.orEmpty()
                if (host.isEmpty() || SiteRequestFilter.isSameSite(host, siteHost)) return null
                if (SiteRequestFilter.allowReason(request) != null) return null
                if (blockedHosts.size < 40 && blockedHosts.add(host)) log(profile, "hidden page blocked other host: $host")
                return SiteRequestFilter.emptyResponse()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                pageLoaded?.complete(true)
            }

            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                log(profile, "hidden page process ended: didCrash=${detail?.didCrash()}")
                pageLoaded?.complete(false)
                // Destroy outside the callback; the view is dead either way.
                mainHandler.post { if (webView === view) release("renderer ended") }
                return true
            }
        }
        return view
    }

    private fun release(reason: String) {
        val view = webView ?: return
        siteLog.log("hidden", "closing hidden page: $reason")
        webView = null
        openFor = null
        pageLoaded = null
        mainHandler.removeCallbacks(idleRelease)
        runCatching {
            view.stopLoading()
            view.destroy()
        }
    }

    private suspend fun countOf(view: WebView, selector: String): Int =
        view.evaluate("document.querySelectorAll(${JSONObject.quote(selector)}).length")
            ?.trim('"')?.toIntOrNull() ?: -1

    private suspend fun waitForCount(view: WebView, selector: String, atLeast: Int, timeoutMs: Long): Int {
        val start = System.currentTimeMillis()
        var count = countOf(view, selector)
        while (count in 0 until atLeast && System.currentTimeMillis() - start < timeoutMs) {
            delay(POLL_MS)
            count = countOf(view, selector)
        }
        return count
    }

    private suspend fun waitForElement(view: WebView, selector: String, timeoutMs: Long): Boolean {
        val start = System.currentTimeMillis()
        val script = "document.querySelector(${JSONObject.quote(selector)})!=null"
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (view.evaluate(script) == "true") return true
            delay(POLL_MS)
        }
        return false
    }

    /** One line describing the page, for the log when something goes wrong. */
    private suspend fun pageState(view: WebView, itemSelector: String, nextSelector: String): String {
        val script = "(function(){return JSON.stringify({ready:document.readyState,url:location.href," +
            "title:document.title,posts:document.querySelectorAll(${JSONObject.quote(itemSelector)}).length," +
            "next:document.querySelectorAll(${JSONObject.quote(nextSelector)}).length," +
            "html:document.documentElement.outerHTML.length,hidden:document.hidden," +
            "w:window.innerWidth,h:window.innerHeight});})()"
        val raw = view.evaluate(script) ?: return "page did not answer"
        return runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull() ?: raw
    }

    private suspend fun WebView.evaluate(script: String): String? = withTimeoutOrNull(EVAL_TIMEOUT_MS) {
        suspendCancellableCoroutine { cont ->
            evaluateJavascript(script) { result -> if (cont.isActive) cont.resume(result) }
        }
    }

    private fun clickScript(selector: String) =
        "(function(){var el=document.querySelector(${JSONObject.quote(selector)});" +
            "if(!el)return 'no element';el.scrollIntoView({block:'center'});" +
            "var t=el.closest('button,a')||el;t.click();" +
            "return 'clicked '+t.tagName+' '+(t.innerText||'').trim().slice(0,30);})()"

    private fun log(profile: SiteProfile, message: String) = siteLog.log("hidden", "${profile.name}: $message")

    private companion object {
        const val LOAD_TIMEOUT_MS = 30_000L
        const val RENDER_TIMEOUT_MS = 20_000L
        const val BUTTON_TIMEOUT_MS = 15_000L
        const val GROW_TIMEOUT_MS = 20_000L
        const val EVAL_TIMEOUT_MS = 5_000L
        const val POLL_MS = 500L
        const val IDLE_MS = 3 * 60_000L
        const val MAX_PAGE_ITEMS = 600
        const val VIEW_WIDTH = 1080
        const val VIEW_HEIGHT = 2400
    }
}
