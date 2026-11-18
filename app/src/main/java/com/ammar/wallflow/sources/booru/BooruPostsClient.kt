package com.ammar.wallflow.sources.booru

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/** One post, mapped from any booru style into the same shape. */
data class BooruPost(
    val id: String,
    val fileUrl: String,
    val previewUrl: String?,
    val tags: List<String>,
    val rating: String?,
)

/** Fetches posts from a board whose style is already known. Read-only. */
@Singleton
class BooruPostsClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    suspend fun fetchPosts(
        style: BooruStyle,
        baseUrl: String,
        limit: Int = 20,
        page: Int = 1,
    ): Result<List<BooruPost>> = withContext(Dispatchers.IO) {
        runCatching {
            val base = baseUrl.trim().trimEnd('/')
            val request = Request.Builder().url(postsUrl(style, base, limit, page)).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                parsePosts(style, response.body?.string().orEmpty())
            }
        }
    }

    companion object {
        internal fun postsUrl(style: BooruStyle, base: String, limit: Int, page: Int): String =
            when (style) {
                BooruStyle.DANBOORU -> "$base/posts.json?limit=$limit&page=$page"
                BooruStyle.MOEBOORU -> "$base/post.json?limit=$limit&page=$page"
                // Gelbooru's pid is zero-based.
                BooruStyle.GELBOORU ->
                    "$base/index.php?page=dapi&s=post&q=index&json=1&limit=$limit&pid=${page - 1}"
            }

        internal fun parsePosts(style: BooruStyle, body: String): List<BooruPost> {
            val root: JsonElement = Json.parseToJsonElement(body)
            val items: JsonArray = when (root) {
                is JsonArray -> root
                is JsonObject -> root["post"] as? JsonArray ?: error("Unexpected JSON shape")
                else -> error("Unexpected JSON shape")
            }
            return items.mapNotNull { element ->
                val post = element as? JsonObject ?: return@mapNotNull null
                val fileUrl = post.text("file_url") ?: return@mapNotNull null
                val id = post.text("id") ?: return@mapNotNull null
                val previewKey = if (style == BooruStyle.DANBOORU) "preview_file_url" else "preview_url"
                val tagText = post.text("tag_string") ?: post.text("tags").orEmpty()
                BooruPost(
                    id = id,
                    fileUrl = fileUrl,
                    previewUrl = post.text(previewKey),
                    tags = tagText.split(' ').filter { it.isNotBlank() },
                    rating = post.text("rating"),
                )
            }
        }

        private fun JsonObject.text(key: String): String? =
            (this[key] as? JsonPrimitive)
                ?.takeIf { it !is JsonNull }
                ?.content
                ?.takeIf { it.isNotBlank() }
    }
}
