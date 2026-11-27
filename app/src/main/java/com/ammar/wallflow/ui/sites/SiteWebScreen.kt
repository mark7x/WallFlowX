package com.ammar.wallflow.ui.sites

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.collectAsState
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
    var debugOn by remember { mutableStateOf(false) }
    val debugLines by viewModel.debugLog.collectAsState()
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

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
                actions = {
                    TextButton(onClick = { debugOn = !debugOn }) {
                        Text(
                            text = stringResource(
                                if (debugOn) R.string.site_debug_hide else R.string.site_debug_show,
                            ),
                        )
                    }
                },
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
                if (debugOn) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = if (debugLines.isEmpty()) "No events yet." else debugLines.joinToString("\n"),
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        )
                        Row {
                            TextButton(onClick = { viewModel.clearDebug() }) {
                                Text(text = stringResource(R.string.site_debug_clear))
                            }
                            TextButton(onClick = {
                                clipboard.setText(androidx.compose.ui.text.AnnotatedString(debugLines.joinToString("\n")))
                            }) {
                                Text(text = stringResource(R.string.site_debug_copy))
                            }
                        }
                    }
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
                        viewModel.debug("scroll and scan started")
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
                val siteHost = android.net.Uri.parse(url).host.orEmpty()
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // Scripts may not open pop-up windows or tabs on their own (ad popups).
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.setSupportMultipleWindows(false)
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onCreateWindow(
                            view: WebView?,
                            isDialog: Boolean,
                            isUserGesture: Boolean,
                            resultMsg: android.os.Message?,
                        ): Boolean = false
                    }
                    CookieManager.getInstance().setAcceptCookie(true)
                    addJavascriptInterface(SiteBridge { json -> viewModel.onPick(json) }, "WallFlowPick")
                    webViewClient = object : WebViewClient() {
                        // Ads often open app links (intent://, market://). Only web pages load here.
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean {
                            request ?: return false
                            val scheme = request.url?.scheme.orEmpty()
                            if (scheme != "http" && scheme != "https") return true
                            // Infinite-scroll pages often navigate by script when you scroll, which
                            // reloads the page under you. Only real taps may change the main page.
                            // Ads that open a new tab or take over the page land on another host.
                            // Only taps that stay on the taught site may change the main page.
                            if (request.isForMainFrame) {
                                val host = request.url?.host.orEmpty()
                                val sameSite = host == siteHost || host.endsWith(".$siteHost")
                                if (!request.hasGesture() || !sameSite) {
                                    viewModel.debug("blocked navigation: ${request.url}")
                                    return true
                                }
                            }
                            return false
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
