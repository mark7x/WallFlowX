package com.ammar.wallflow.ui.screens.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ammar.wallflow.data.debug.NetworkLogRepository
import com.ammar.wallflow.data.repository.AppPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
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
) : ViewModel() {
    val entries = networkLogRepository.entries

    private val _redditTest = MutableStateFlow<RedditTestResult?>(null)
    val redditTest: StateFlow<RedditTestResult?> = _redditTest.asStateFlow()

    private val _redditTestRunning = MutableStateFlow(false)
    val redditTestRunning: StateFlow<Boolean> = _redditTestRunning.asStateFlow()

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
        private const val REDDIT_TEST_URL = "https://www.reddit.com/search.json?q=wallpaper&limit=1&raw_json=1"
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
