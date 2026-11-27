package com.ammar.wallflow.sites

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/**
 * Ad blocking for the site browsers. Requests to the taught site always load. Requests to
 * other hosts load only when they are images, video, fonts or styles (CDNs serve those).
 * Scripts and frames from other hosts are blocked: those are the ads, pop-ups and trackers.
 */
object SiteRequestFilter {
    private val mediaExtensions = setOf(
        "jpg", "jpeg", "png", "gif", "webp", "avif", "svg", "bmp", "ico",
        "mp4", "webm", "m4v", "mov",
        "woff", "woff2", "ttf", "otf", "css",
    )

    fun baseHost(host: String): String = host.lowercase().removePrefix("www.")

    fun isSameSite(host: String, siteHost: String): Boolean {
        val h = baseHost(host)
        val s = baseHost(siteHost)
        return h == s || h.endsWith(".$s") || s.endsWith(".$h")
    }

    /** Why a request from another host is allowed, or null when it should be blocked. */
    fun allowReason(request: WebResourceRequest): String? {
        val ext = request.url?.path.orEmpty().substringAfterLast('.', "").lowercase()
        if (ext in mediaExtensions) return ext
        val accept = request.requestHeaders?.entries
            ?.firstOrNull { it.key.equals("Accept", ignoreCase = true) }?.value.orEmpty()
        return when {
            accept.startsWith("image/") -> "image"
            accept.startsWith("video/") -> "video"
            accept.contains("text/css") -> "style"
            accept.contains("font") -> "font"
            else -> null
        }
    }

    /** An empty response that stands in for a blocked request. */
    fun emptyResponse() = WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
}
