package com.ammar.wallflow.sources.booru

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request

enum class BooruStyle { DANBOORU, MOEBOORU, GELBOORU }

sealed interface BooruDetection {
    /** The site answered like [style]. [reasons] is empty. */
    data class Detected(val style: BooruStyle) : BooruDetection

    /** No known style matched. [reasons] holds one line per style probed, for debugging. */
    data class Unknown(val reasons: List<String>) : BooruDetection
}

/**
 * Asks a site for one sample post per known booru API style and checks the response shape.
 * It only reads: it sends one GET per style and never logs in or writes anything.
 */
@Singleton
class BooruStyleDetector @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    suspend fun detect(
        baseUrl: String,
        userId: String? = null,
        apiKey: String? = null,
    ): BooruDetection = withContext(Dispatchers.IO) {
        val base = baseUrl.trim().trimEnd('/')
        if (!base.startsWith("https://") && !base.startsWith("http://")) {
            return@withContext BooruDetection.Unknown(
                listOf("Enter a full address starting with https:// or http://"),
            )
        }
        val results = BooruStyle.entries.map { style -> style to probe(style, base, userId, apiKey) }
        val match = results.firstOrNull { it.second is ProbeResult.Match }
        if (match != null) {
            BooruDetection.Detected(match.first)
        } else {
            BooruDetection.Unknown(
                results.map { (style, result) ->
                    val miss = (result as ProbeResult.Miss).reason
                    "${style.name.lowercase()}: $miss"
                },
            )
        }
    }

    private fun probe(style: BooruStyle, base: String, userId: String?, apiKey: String?): ProbeResult {
        return try {
            val request = Request.Builder()
                .url(probeUrl(style, base, userId, apiKey))
                .header("User-Agent", BOORU_USER_AGENT)
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return ProbeResult.Miss("HTTP ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                val detected = classify(body)
                when (detected) {
                    style -> ProbeResult.Match(style)
                    null -> ProbeResult.Miss(
                        "no sample post in a recognised shape (site said: ${snippet(body)})",
                    )
                    else -> ProbeResult.Miss("answers like ${detected.name.lowercase()}")
                }
            }
        } catch (e: Exception) {
            ProbeResult.Miss(e.message ?: e.javaClass.simpleName)
        }
    }

    private sealed interface ProbeResult {
        data class Match(val style: BooruStyle) : ProbeResult
        data class Miss(val reason: String) : ProbeResult
    }

    companion object {
        /** Public read-only endpoints, asking for a single post. */
        internal fun probeUrl(
            style: BooruStyle,
            base: String,
            userId: String? = null,
            apiKey: String? = null,
        ): String = when (style) {
            BooruStyle.DANBOORU -> "$base/posts.json?limit=1"
            BooruStyle.MOEBOORU -> "$base/post.json?limit=1"
            BooruStyle.GELBOORU ->
                "$base/index.php?page=dapi&s=post&q=index&json=1&limit=1" + booruAuthQuery(BooruStyle.GELBOORU, userId, apiKey)
        }

        /** The first 80 characters of a reply, on one line, so a failed probe shows what the site said. */
        internal fun snippet(body: String): String =
            body.replace(Regex("\\s+"), " ").trim().take(80).ifEmpty { "empty reply" }

        /**
         * Works out which style a JSON body came from, using fields that only that style has.
         * Returns null when the body isn't JSON or has no recognisable post.
         */
        internal fun classify(body: String): BooruStyle? {
            val root = try {
                Json.parseToJsonElement(body)
            } catch (e: Exception) {
                return null
            }
            val posts: JsonArray = when (root) {
                is JsonArray -> root
                is JsonObject -> root["post"] as? JsonArray ?: return null
                else -> return null
            }
            val post = posts.firstOrNull() as? JsonObject ?: return null
            val hasFileField = post.containsKey("file_url") || post.containsKey("image")
            if (!hasFileField) return null
            return when {
                post.containsKey("tag_string") -> BooruStyle.DANBOORU
                post.containsKey("file_ext") -> BooruStyle.MOEBOORU
                post.containsKey("directory") || post.containsKey("owner") || post.containsKey("image") ->
                    BooruStyle.GELBOORU
                else -> null
            }
        }
    }
}
