package com.ammar.wallflow.sources.booru

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A booru board the user saved after a successful detection. */
@Serializable
data class SavedBoard(
    val baseUrl: String,
    val style: BooruStyle,
)

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

    fun remove(baseUrl: String) {
        save(_boards.value.filterNot { it.baseUrl == baseUrl })
    }

    private fun save(list: List<SavedBoard>) {
        file.writeText(Json.encodeToString(list))
        _boards.value = list
    }

    private fun load(): List<SavedBoard> = try {
        if (file.exists()) Json.decodeFromString(file.readText()) else emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    companion object {
        private const val FILE_NAME = "boards.json"
    }
}
