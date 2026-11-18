package com.ammar.wallflow.data.network.coil

import coil.intercept.Interceptor
import coil.request.ImageResult
import com.ammar.wallflow.sources.booru.BOORU_USER_AGENT
import java.net.URI

/** Sends the booru User-Agent on image loads from every host except Wallhaven. */
class BooruUserAgentInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val data = chain.request.data
        val host = (data as? String)?.let { runCatching { URI(it).host }.getOrNull() }
        if (host == null || host.endsWith("wallhaven.cc")) {
            return chain.proceed(chain.request)
        }
        val request = chain.request.newBuilder()
            .setHeader("User-Agent", BOORU_USER_AGENT)
            .build()
        return chain.proceed(request)
    }
}
