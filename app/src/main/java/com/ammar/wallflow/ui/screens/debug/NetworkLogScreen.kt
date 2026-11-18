package com.ammar.wallflow.ui.screens.debug

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ammar.wallflow.R
import com.ammar.wallflow.data.debug.NetworkLogEntry
import com.ammar.wallflow.navigation.AppNavGraphs.RootNavGraph
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootNavGraph>
@Composable
fun NetworkLogScreen(
    viewModel: NetworkLogViewModel = hiltViewModel(),
    navigator: DestinationsNavigator,
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val successes = entries.filter { it.isSuccess }
    val failures = entries.filterNot { it.isSuccess }
    val shown = if (selectedTab == 0) successes else failures
    val redditTest by viewModel.redditTest.collectAsStateWithLifecycle()
    val redditTestRunning by viewModel.redditTestRunning.collectAsStateWithLifecycle()
    val redditCookie by viewModel.redditCookie.collectAsStateWithLifecycle()
    var webTestActive by remember { mutableStateOf(false) }
    var webTestText by remember { mutableStateOf<String?>(null) }
    val booruTest by viewModel.booruTest.collectAsStateWithLifecycle()
    val booruTestRunning by viewModel.booruTestRunning.collectAsStateWithLifecycle()
    var booruUrl by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.network_log)) },
                navigationIcon = {
                    IconButton(onClick = { navigator.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::clear) {
                        Text(text = stringResource(R.string.network_log_clear))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Button(
                onClick = viewModel::runRedditTest,
                enabled = !redditTestRunning,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(
                    text = stringResource(
                        if (redditTestRunning) R.string.reddit_test_running else R.string.network_log_reddit_test,
                    ),
                )
            }
            redditTest?.let { RedditTestSummary(result = it) }
            Button(
                onClick = {
                    webTestText = null
                    webTestActive = true
                },
                enabled = !webTestActive,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Text(text = stringResource(R.string.network_log_webview_test))
            }
            if (webTestActive) {
                RedditWebTest(
                    cookie = redditCookie,
                    onResult = {
                        webTestText = it
                        webTestActive = false
                    },
                )
            }
            webTestText?.let {
                Text(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    text = stringResource(R.string.reddit_webview_result, it),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedTextField(
                value = booruUrl,
                onValueChange = { booruUrl = it },
                label = { Text(text = stringResource(R.string.booru_test_url_label)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            Button(
                onClick = { viewModel.runBooruTest(booruUrl) },
                enabled = !booruTestRunning && booruUrl.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(
                    text = stringResource(
                        if (booruTestRunning) R.string.booru_test_running else R.string.booru_test_button,
                    ),
                )
            }
            booruTest?.let { BooruTestSummary(result = it) }
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = stringResource(R.string.network_log_success, successes.size),
                        )
                    },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = stringResource(R.string.network_log_failures, failures.size),
                        )
                    },
                )
            }
            if (shown.isEmpty()) {
                Text(
                    modifier = Modifier.padding(16.dp),
                    text = stringResource(R.string.network_log_empty),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            LazyColumn {
                items(shown, key = { it.id }) { entry ->
                    NetworkLogRow(entry = entry)
                }
            }
        }
    }
}

@Composable
private fun NetworkLogRow(entry: NetworkLogEntry) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = "${entry.method} · ${entry.code ?: "no response"} · " +
                "${entry.durationMs} ms · ${TIME_FORMAT.format(Date(entry.timeMillis))}",
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = entry.url,
            style = MaterialTheme.typography.bodySmall,
        )
        entry.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun RedditTestSummary(result: RedditTestResult) {
    val noneText = stringResource(R.string.reddit_test_none)
    val names = if (result.cookieNames.isEmpty()) noneText else result.cookieNames.joinToString()
    val sessionText = stringResource(
        if (result.hasSessionCookie) R.string.reddit_test_yes else R.string.reddit_test_no,
    )
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = stringResource(R.string.reddit_test_cookies, names),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = stringResource(R.string.reddit_test_session, sessionText),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = stringResource(
                R.string.reddit_test_http,
                result.code?.toString() ?: stringResource(R.string.reddit_test_no_response),
            ),
            style = MaterialTheme.typography.bodySmall,
        )
        result.error?.let {
            Text(
                text = stringResource(R.string.reddit_test_error, it),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (result.isHtml) {
            Text(
                text = stringResource(R.string.reddit_test_html),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        result.bodyPreview?.let {
            Text(
                text = stringResource(R.string.reddit_test_body, it),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun RedditWebTest(cookie: String, onResult: (String) -> Unit) {
    val timeoutText = stringResource(R.string.reddit_webview_timeout)
    var reported = false
    LaunchedEffect(Unit) {
        delay(WEBVIEW_TEST_TIMEOUT_MS)
        if (!reported) {
            reported = true
            onResult(timeoutText)
        }
    }
    AndroidView(
        modifier = Modifier.size(1.dp),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookie.split(";")
                    .map { it.trim() }
                    .filter { it.contains("=") }
                    .forEach { cookieManager.setCookie("https://www.reddit.com", it) }
                cookieManager.flush()
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (reported) return
                        view?.evaluateJavascript(
                            "(function(){return document.body ? document.body.innerText.slice(0,200) : ''})()",
                        ) { value ->
                            if (!reported) {
                                reported = true
                                onResult(value.orEmpty())
                            }
                        }
                    }
                }
                loadUrl(NetworkLogViewModel.REDDIT_TEST_URL)
            }
        },
    )
}

private const val WEBVIEW_TEST_TIMEOUT_MS = 20_000L

@Composable
private fun BooruTestSummary(result: BooruTestResult) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        result.style?.let {
            Text(
                text = stringResource(R.string.booru_test_style, it.name.lowercase()),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        result.postCount?.let {
            Text(
                text = stringResource(R.string.booru_test_posts, it),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        result.firstFileUrl?.let {
            Text(
                text = stringResource(R.string.booru_test_first, it),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        result.error?.let {
            Text(
                text = stringResource(R.string.booru_test_error, it),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private val TIME_FORMAT = SimpleDateFormat("HH:mm:ss", Locale.US)
