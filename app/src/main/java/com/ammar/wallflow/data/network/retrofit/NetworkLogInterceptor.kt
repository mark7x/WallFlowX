package com.ammar.wallflow.data.network.retrofit

import com.ammar.wallflow.data.debug.NetworkLogRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Records every request made through the app's OkHttp client, including Coil image loads
 * and downloads, as a success or failure for the in-app network log.
 */
@Singleton
class NetworkLogInterceptor @Inject constructor(
    private val repository: NetworkLogRepository,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.redacted()
        val start = System.nanoTime()
        val response = try {
            chain.proceed(request)
        } catch (e: IOException) {
            repository.add(
                method = request.method,
                url = url,
                code = null,
                durationMs = elapsedMs(start),
                error = "${e.javaClass.simpleName}: ${e.message ?: "no message"}",
            )
            throw e
        }
        repository.add(
            method = request.method,
            url = url,
            code = response.code,
            durationMs = elapsedMs(start),
            error = if (response.code in 200..399) {
                null
            } else {
                "HTTP ${response.code} ${response.message}".trim()
            },
        )
        return response
    }

    private fun elapsedMs(startNanos: Long) = (System.nanoTime() - startNanos) / 1_000_000

    /** Hides the Wallhaven API key, which is sent as a query parameter. */
    private fun HttpUrl.redacted(): String {
        val builder = newBuilder()
        if (queryParameter("apikey") != null) {
            builder.setQueryParameter("apikey", "REDACTED")
        }
        return builder.build().toString()
    }
}
