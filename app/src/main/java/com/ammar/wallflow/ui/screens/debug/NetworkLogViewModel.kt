package com.ammar.wallflow.ui.screens.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ammar.wallflow.data.debug.NetworkLogRepository
import com.ammar.wallflow.data.repository.AppPreferencesRepository
import com.ammar.wallflow.sources.booru.BooruDetection
import com.ammar.wallflow.sources.booru.BooruPostsClient
import com.ammar.wallflow.sources.booru.BooruStyle
import com.ammar.wallflow.sources.booru.BoardsStore
import com.ammar.wallflow.sources.booru.BooruStyleDetector
import com.ammar.wallflow.sources.booru.SavedBoard
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

@HiltViewModel
class NetworkLogViewModel @Inject constructor(
    private val networkLogRepository: NetworkLogRepository,
    private val appPreferencesRepository: AppPreferencesRepository,
    private val okHttpClient: OkHttpClient,
    private val booruStyleDetector: BooruStyleDetector,
    private val booruPostsClient: BooruPostsClient,
    private val boardsStore: BoardsStore,
) : ViewModel() {
    val entries = networkLogRepository.entries

    private val _redditTest = MutableStateFlow<RedditTestResult?>(null)
    val redditTest: StateFlow<RedditTestResult?> = _redditTest.asStateFlow()

    private val _redditTestRunning = MutableStateFlow(false)
    val redditTestRunning: StateFlow<Boolean> = _redditTestRunning.asStateFlow()

    private val _booruTest = MutableStateFlow<BooruTestResult?>(null)
    val booruTest: StateFlow<BooruTestResult?> = _booruTest.asStateFlow()

    private val _booruTestRunning = MutableStateFlow(false)

    private val _lastBoard = MutableStateFlow<SavedBoard?>(null)
    val lastBoard: StateFlow<SavedBoard?> = _lastBoard.asStateFlow()

    private val _boardSaved = MutableStateFlow(false)
    val boardSaved: StateFlow<Boolean> = _boardSaved.asStateFlow()

    fun saveLastBoard() {
        _lastBoard.value?.let { boardsStore.add(it) }
        _boardSaved.value = true
    }
    val booruTestRunning: StateFlow<Boolean> = _booruTestRunning.asStateFlow()

    /** Detects the board's style, then fetches three posts to confirm reading works. */
    fun runBooruTest(url: String) {
        if (_booruTestRunning.value) return
        viewModelScope.launch {
            _booruTestRunning.value = true
            _lastBoard.value = null
            _boardSaved.value = false
            val result = when (val detection = booruStyleDetector.detect(url)) {
                is BooruDetection.Unknown -> BooruTestResult(
                    style = null,
                    postCount = null,
                    firstFileUrl = null,
                    error = detection.reasons.joinToString("; "),
                )
                is BooruDetection.Detected -> {
                    _lastBoard.value = SavedBoard(
                        baseUrl = url.trim().trimEnd('/'),
                        style = detection.style,
                    )
                    booruPostsClient.fetchPosts(detection.style, url, limit = 3).fold(
                        onSuccess = { posts ->
                            BooruTestResult(
                                style = detection.style,
                                postCount = posts.size,
                                firstFileUrl = posts.firstOrNull()?.fileUrl,
                                error = null,
                            )
                        },
                        onFailure = { e ->
                            BooruTestResult(
                                style = detection.style,
                                postCount = null,
                                firstFileUrl = null,
                                error = e.message ?: e.javaClass.simpleName,
                            )
                        },
                    )
                }
            }
            _booruTest.value = result
            _booruTestRunning.value = false
        }
    }

    val redditCookie: StateFlow<String> = appPreferencesRepository.appPreferencesFlow
        .map { it.redditCookie }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun clear() = networkLogRepository.clear()

    /**
     * Sends one Reddit search through the app's own OkHttp client, so the same User-Agent,
     * saved cookie and logging apply as in real requests. Only cookie names are reported.
     */
    fun runRedditTest() {
        if (_redditTestRunning.value) return
        viewModelScope.launch {
            _redditTestRunning.value = true
            val cookie = appPreferencesRepository.getRedditCookie()
            val names = cookie.split(";")
                .map { it.trim().substringBefore("=") }
                .filter { it.isNotEmpty() }
            val result = withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder().url(REDDIT_TEST_URL).build()
                    okHttpClient.newCall(request).execute().use { response ->
                        val body = response.body?.string().orEmpty().trim()
                        RedditTestResult(
                            cookieNames = names,
                            hasSessionCookie = SESSION_COOKIE in names,
                            code = response.code,
                            error = null,
                            bodyPreview = when {
                                body.startsWith("<") -> null
                                else -> body.take(BODY_PREVIEW_LENGTH)
                            },
                            isHtml = body.startsWith("<"),
                        )
                    }
                } catch (e: IOException) {
                    RedditTestResult(
                        cookieNames = names,
                        hasSessionCookie = SESSION_COOKIE in names,
                        code = null,
                        error = e.message ?: e.javaClass.simpleName,
                        bodyPreview = null,
                        isHtml = false,
                    )
                }
            }
            _redditTest.value = result
            _redditTestRunning.value = false
        }
    }

    companion object {
        const val REDDIT_TEST_URL = "https://www.reddit.com/search.json?q=wallpaper&limit=1&raw_json=1"
        private const val SESSION_COOKIE = "reddit_session"
        private const val BODY_PREVIEW_LENGTH = 200
    }
}

data class RedditTestResult(
    val cookieNames: List<String>,
    val hasSessionCookie: Boolean,
    val code: Int?,
    val error: String?,
    val bodyPreview: String?,
    val isHtml: Boolean,
)

data class BooruTestResult(
    val style: BooruStyle?,
    val postCount: Int?,
    val firstFileUrl: String?,
    val error: String?,
)
