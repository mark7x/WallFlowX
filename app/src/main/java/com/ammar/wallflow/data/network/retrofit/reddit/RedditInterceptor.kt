package com.ammar.wallflow.data.network.retrofit.reddit

import com.ammar.wallflow.BuildConfig
import com.ammar.wallflow.data.repository.GlobalErrorsRepository
import com.ammar.wallflow.data.repository.GlobalErrorsRepository.RateLimitError
import com.ammar.wallflow.model.Source
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Reddit throttles or blocks clients that don't send a unique, descriptive User-Agent,
 * and rejects the default OkHttp one. See https://github.com/reddit-archive/reddit/wiki/API.
 */
@Singleton
class RedditInterceptor @Inject constructor(
    private val globalErrorsRepository: GlobalErrorsRepository,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.isRedditApiHost()) {
            return chain.proceed(request)
        }
        val newRequest = request.newBuilder()
            .header("User-Agent", USER_AGENT)
            .build()
        val response = chain.proceed(newRequest)
        checkForErrors(response)
        return response
    }

    private fun checkForErrors(response: Response) {
        if (response.code == HTTP_TOO_MANY_REQUESTS) {
            globalErrorsRepository.addError(
                RateLimitError(Source.REDDIT),
                replace = true,
            )
        } else if (response.isSuccessful) {
            globalErrorsRepository.removeRateLimitError(Source.REDDIT)
        }
    }

    private fun HttpUrl.isRedditApiHost() =
        host == "reddit.com" || host == "www.reddit.com"

    companion object {
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private val USER_AGENT = "android:com.ammar.wallflow:${BuildConfig.VERSION_NAME}"
    }
}
