package com.ammar.wallflow.data.network.retrofit.reddit

import com.ammar.wallflow.BuildConfig
import com.ammar.wallflow.data.repository.AppPreferencesRepository
import com.ammar.wallflow.data.repository.GlobalErrorsRepository
import com.ammar.wallflow.data.repository.GlobalErrorsRepository.GlobalErrorType
import com.ammar.wallflow.data.repository.GlobalErrorsRepository.RateLimitError
import com.ammar.wallflow.data.repository.GlobalErrorsRepository.RedditUnauthorisedError
import com.ammar.wallflow.model.Source
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * Reddit throttles or blocks clients that don't send a unique, descriptive User-Agent,
 * and rejects the default OkHttp one. See https://github.com/reddit-archive/reddit/wiki/API.
 *
 * If the user logged in via [com.ammar.wallflow.ui.screens.redditauth.RedditAuthScreen],
 * the stored session cookie is sent with every Reddit request.
 */
@Singleton
class RedditInterceptor @Inject constructor(
    private val appPreferencesRepository: AppPreferencesRepository,
    private val globalErrorsRepository: GlobalErrorsRepository,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.isRedditApiHost()) {
            return chain.proceed(request)
        }
        val newRequest = runBlocking { appendHeaders(request) }
        val response = chain.proceed(newRequest)
        checkForErrors(response)
        return response
    }

    private suspend fun appendHeaders(request: Request): Request {
        val builder = request.newBuilder()
            .header("User-Agent", USER_AGENT)
        val cookie = appPreferencesRepository.getRedditCookie()
        if (cookie.isNotBlank()) {
            builder.removeHeader("Cookie").header("Cookie", cookie)
        }
        return builder.build()
    }

    private fun checkForErrors(response: Response) {
        when {
            response.code == HTTP_TOO_MANY_REQUESTS -> globalErrorsRepository.addError(
                RateLimitError(Source.REDDIT),
                replace = true,
            )
            response.code == HTTP_UNAUTHORIZED || response.code == HTTP_FORBIDDEN -> {
                globalErrorsRepository.addError(RedditUnauthorisedError(), replace = true)
            }
            response.isSuccessful -> {
                globalErrorsRepository.removeRateLimitError(Source.REDDIT)
                globalErrorsRepository.removeErrorByType(GlobalErrorType.REDDIT_UNAUTHORISED)
            }
        }
    }

    private fun HttpUrl.isRedditApiHost() =
        host == "reddit.com" || host == "www.reddit.com"

    companion object {
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private val USER_AGENT = "android:com.ammar.wallflow:${BuildConfig.VERSION_NAME}"
    }
}
