package com.ammar.wallflow.ui.sites

import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.runtime.rememberCoroutineScope
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import androidx.compose.runtime.key
import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ammar.wallflow.R
import com.ammar.wallflow.sites.SiteScripts
import com.ammar.wallflow.navigation.AppNavGraphs.RootNavGraph
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONTokener

/** Receives taps from the page's script. Taps arrive on a background thread, so they are posted back. */
internal class SiteBridge(private val onPick: (String) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun report(json: String) {
        handler.post { onPick(json) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Destination<RootNavGraph>
@Composable
fun SiteWebScreen(
    navigator: DestinationsNavigator,
    profileId: String,
    url: String,
    viewModel: SiteWebViewModel = hiltViewModel(),
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var mode by remember { mutableStateOf<String?>(null) }
    val modeRef = remember { AtomicReference<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    val scrollScope = rememberCoroutineScope()

    LaunchedEffect(profileId) { viewModel.load(profileId) }
    LaunchedEffect(mode, webView) {
        modeRef.set(mode)
        webView?.evaluateJavascript(SiteScripts.modeScript(mode), null)
    }
    DisposableEffect(Unit) {
        onDispose { webView?.destroy() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = profile?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = { navigator.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            Column {
                message?.let {
                    Text(
                        text = it,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                Text(
                    text = stringResource(
                        when (mode) {
                            "item" -> R.string.site_help_item
                            "image" -> R.string.site_help_image
                            "tag" -> R.string.site_help_tag
                            "next" -> R.string.site_help_next
                            else -> R.string.site_help_browse
                        },
                    ),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    FilterChip(
                        selected = mode == "item",
                        onClick = { mode = if (mode == "item") null else "item" },
                        label = { Text(text = stringResource(R.string.site_pick_item)) },
                        modifier = Modifier.padding(end = 6.dp),
                    )
                    FilterChip(
                        selected = mode == "image",
                        onClick = { mode = if (mode == "image") null else "image" },
                        label = { Text(text = stringResource(R.string.site_pick_image)) },
                        modifier = Modifier.padding(end = 6.dp),
                    )
                    FilterChip(
                        selected = mode == "tag",
                        onClick = { mode = if (mode == "tag") null else "tag" },
                        label = { Text(text = stringResource(R.string.site_pick_tag)) },
                        modifier = Modifier.padding(end = 6.dp),
                    )
                    FilterChip(
                        selected = mode == "next",
                        onClick = { mode = if (mode == "next") null else "next" },
                        label = { Text(text = stringResource(R.string.site_pick_next)) },
                        modifier = Modifier.padding(end = 6.dp),
                    )
                    TextButton(onClick = {
                        webView?.evaluateJavascript("document.documentElement.outerHTML") { raw ->
                            val html = runCatching { JSONTokener(raw).nextValue() as? String }
                                .getOrNull()
                            viewModel.harvestTags(html.orEmpty())
                        }
                    }) {
                        Text(text = stringResource(R.string.site_read_tags))
                    }
                    TextButton(onClick = {
                        webView?.evaluateJavascript("document.documentElement.outerHTML") { raw ->
                            val html = runCatching { JSONTokener(raw).nextValue() as? String }
                                .getOrNull()
                            viewModel.scanPage(html.orEmpty(), webView?.url.orEmpty())
                        }
                    }) {
                        Text(text = stringResource(R.string.site_scan))
                    }
                    TextButton(onClick = {
                        // Infinite-scroll sites load more posts as the page scrolls; scroll, wait, then scan all of it.
                        scrollScope.launch {
                            repeat(8) {
                                webView?.evaluateJavascript("window.scrollTo(0, document.body.scrollHeight);", null)
                                delay(1500L)
                            }
                            webView?.evaluateJavascript("document.documentElement.outerHTML") { raw ->
                                val html = runCatching { JSONTokener(raw).nextValue() as? String }
                                    .getOrNull()
                                viewModel.scanPage(html.orEmpty(), webView?.url.orEmpty())
                            }
                        }
                    }) {
                        Text(text = stringResource(R.string.site_scroll_scan))
                    }
                }
            }
        },
    ) { padding ->
        key(reloadKey) {
        AndroidView(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    CookieManager.getInstance().setAcceptCookie(true)
                    addJavascriptInterface(SiteBridge { json -> viewModel.onPick(json) }, "WallFlowPick")
                    webViewClient = object : WebViewClient() {
                        // Ads often open app links (intent://, market://). Only web pages load here.
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean {
                            val scheme = request?.url?.scheme.orEmpty()
                            return scheme != "http" && scheme != "https"
                        }

                        // If the page's process dies (common with heavy ad pages), a WebView that does
                        // not handle this takes the whole app down. Replace the view instead.
                        override fun onRenderProcessGone(
                            view: WebView?,
                            detail: RenderProcessGoneDetail?,
                        ): Boolean {
                            view?.destroy()
                            reloadKey++
                            return true
                        }

                        override fun onPageFinished(view: WebView, pageUrl: String?) {
                            view.evaluateJavascript(SiteScripts.PICKER, null)
                            view.evaluateJavascript(SiteScripts.modeScript(modeRef.get()), null)
                        }
                    }
                    loadUrl(url)
                    webView = this
                }
            },
        )
        }
    }
}
