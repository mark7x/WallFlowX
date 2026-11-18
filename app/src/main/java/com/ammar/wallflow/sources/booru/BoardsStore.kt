package com.ammar.wallflow.sources.booru

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val boardsSerializer = ListSerializer(SavedBoard.serializer())

/**
 * A booru board the user saved after a successful detection.
 * [userId] and [apiKey] are only set for sites that require them (rule34.xxx).
 */
@Serializable
data class SavedBoard(
    val baseUrl: String,
    val style: BooruStyle,
    val userId: String? = null,
    val apiKey: String? = null,
)

/** Query text for a tag search. Empty when there is no text. */
internal fun booruTagsQuery(tags: String): String =
    tags.trim().takeIf { it.isNotEmpty() }
        ?.let { "&tags=${URLEncoder.encode(it, \"UTF-8\")}" }
        .orEmpty()

/**
 * Query text that adds the account keys. Danbooru uses login and api_key;
 * Gelbooru uses user_id and api_key. Empty unless both values are set.
 */
internal fun booruAuthQuery(style: BooruStyle, userId: String?, apiKey: String?): String {
    if (userId.isNullOrBlank() || apiKey.isNullOrBlank()) return ""
    val user = URLEncoder.encode(userId, \"UTF-8\")
    val key = URLEncoder.encode(apiKey, \"UTF-8\")
    return when (style) {
        BooruStyle.DANBOORU -> "&login=$user&api_key=$key"
        BooruStyle.GELBOORU -> "&user_id=$user&api_key=$key"
        BooruStyle.MOEBOORU -> ""
    }
}

/** Keeps saved boards in a JSON file inside the app's private storage. */
@Singleton
class BoardsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val file = File(context.filesDir, FILE_NAME)
    private val _boards = MutableStateFlow(load())
    val boards: StateFlow<List<SavedBoard>> = _boards.asStateFlow()

    fun add(board: SavedBoard) {
        val current = _boards.value
        if (current.any { it.baseUrl == board.baseUrl }) return
        save(current + board)
    }

    /** The saved board at [baseUrl], ignoring a trailing slash. */
    fun find(baseUrl: String): SavedBoard? {
        val wanted = baseUrl.trim().trimEnd('/')
        return _boards.value.firstOrNull { it.baseUrl.trimEnd('/') == wanted }
    }

    fun remove(baseUrl: String) {
        save(_boards.value.filterNot { it.baseUrl == baseUrl })
    }

    private fun save(list: List<SavedBoard>) {
        file.writeText(Json.encodeToString(boardsSerializer, list))
        _boards.value = list
    }

    private fun load(): List<SavedBoard> = try {
        if (file.exists()) Json.decodeFromString(boardsSerializer, file.readText()) else emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    companion object {
        private const val FILE_NAME = "boards.json"
    }
}
