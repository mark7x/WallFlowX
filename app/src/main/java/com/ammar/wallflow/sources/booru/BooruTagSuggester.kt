package com.ammar.wallflow.sources.booru

import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Suggests tag names from a board while the user types. Danbooru and Moebooru have a tag
 * list that supports prefix search. Gelbooru has no suggestion endpoint we can confirm, so it
 * returns nothing.
 */
@Singleton
class BooruTagSuggester @Inject constructor(
    private val pacer: BooruPacer,
    private val okHttpClient: OkHttpClient,
    private val boardsStore: BoardsStore,
) {
    suspend fun suggest(style: BooruStyle, baseUrl: String, prefix: String): List<String> =
        withContext(Dispatchers.IO) {
            val term = prefix.trim()
            if (term.length < MIN_PREFIX) return@withContext emptyList()
            val base = baseUrl.trim().trimEnd('/')
            val board = boardsStore.find(base)
            val url = suggestUrl(style, base, term, board?.userId, board?.apiKey) ?: return@withContext emptyList()
            runCatching {
                val request = Request.Builder().url(url).header("User-Agent", BOORU_USER_AGENT).build()
                pacer.await(base)
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.code == 429) pacer.rateLimited(base, response.header("Retry-After"))
                    if (!response.isSuccessful) return@use emptyList()
                    parseNames(response.body?.string().orEmpty())
                }
            }.getOrDefault(emptyList())
        }

    companion object {
        private const val MIN_PREFIX = 2
        private const val LIMIT = 10

        internal fun suggestUrl(
            style: BooruStyle,
            base: String,
            term: String,
            userId: String?,
            apiKey: String?,
        ): String? {
            val encoded = URLEncoder.encode("$term*", "UTF-8")
            return when (style) {
                BooruStyle.DANBOORU ->
                    "$base/tags.json?search[name_matches]=$encoded&search[order]=count&limit=$LIMIT" +
                        booruAuthQuery(style, userId, apiKey)
                BooruStyle.MOEBOORU -> "$base/tag.json?name=$encoded&order=count&limit=$LIMIT"
                BooruStyle.GELBOORU -> null
            }
        }

        /** Reads the "name" of each tag in a JSON array. Anything else gives an empty list. */
        internal fun parseNames(body: String): List<String> {
            val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonArray
                ?: return emptyList()
            return root.mapNotNull { element ->
                ((element as? JsonObject)?.get("name") as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
            }
        }
    }
}
