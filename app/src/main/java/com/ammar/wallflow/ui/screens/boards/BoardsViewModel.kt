package com.ammar.wallflow.ui.screens.boards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.ammar.wallflow.sources.booru.BoardsStore
import com.ammar.wallflow.sources.booru.BooruDetection
import com.ammar.wallflow.sources.booru.BooruPost
import com.ammar.wallflow.sources.booru.BooruPostsClient
import com.ammar.wallflow.sources.booru.BooruStyleDetector
import com.ammar.wallflow.sources.booru.SavedBoard
import com.ammar.wallflow.utils.CrashLog
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BoardPostsState(
    val loading: Boolean = false,
    val posts: List<BooruPost> = emptyList(),
    val error: String? = null,
)

data class AddBoardState(
    val running: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class BoardsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val boardsStore: BoardsStore,
    private val booruPostsClient: BooruPostsClient,
    private val booruStyleDetector: BooruStyleDetector,
) : ViewModel() {
    val boards: StateFlow<List<SavedBoard>> = boardsStore.boards

    private val _lastCrash = MutableStateFlow(CrashLog.read(context))
    val lastCrash: StateFlow<String?> = _lastCrash.asStateFlow()

    fun clearCrash() {
        CrashLog.clear(context)
        _lastCrash.value = null
    }

    private val _selected = MutableStateFlow<SavedBoard?>(null)
    val selected: StateFlow<SavedBoard?> = _selected.asStateFlow()

    private val _posts = MutableStateFlow(BoardPostsState())
    val posts: StateFlow<BoardPostsState> = _posts.asStateFlow()

    private val _addState = MutableStateFlow(AddBoardState())
    val addState: StateFlow<AddBoardState> = _addState.asStateFlow()

    /** Detects the booru style at [address] and saves it as a board if it is recognised. */
    fun addBoard(address: String, userId: String = "", apiKey: String = "") {
        val url = address.trim()
        if (url.isEmpty() || _addState.value.running) return
        val user = userId.trim().ifBlank { null }
        val key = apiKey.trim().ifBlank { null }
        viewModelScope.launch {
            _addState.value = AddBoardState(running = true)
            when (val detection = booruStyleDetector.detect(url, user, key)) {
                is BooruDetection.Detected -> {
                    boardsStore.add(
                        SavedBoard(baseUrl = url, style = detection.style, userId = user, apiKey = key),
                    )
                    _addState.value = AddBoardState()
                }
                is BooruDetection.Unknown -> {
                    val reasons = detection.reasons.joinToString("; ")
                    val needsKeys = reasons.contains("Missing authentication", ignoreCase = true)
                    _addState.value = AddBoardState(
                        error = when {
                            needsKeys && (user == null || key == null) ->
                                "This site needs a user ID and API key. Fill in both fields and add again."
                            needsKeys -> "The site rejected these keys. Check the user ID and API key."
                            else -> reasons.ifBlank { "Not a supported booru site" }
                        },
                    )
                }
            }
        }
    }

    fun select(board: SavedBoard) {
        _selected.value = board
        _posts.value = BoardPostsState(loading = true)
        viewModelScope.launch {
            booruPostsClient.fetchPosts(board.style, board.baseUrl, limit = 30).fold(
                onSuccess = { list -> _posts.value = BoardPostsState(posts = list) },
                onFailure = { e ->
                    _posts.value = BoardPostsState(error = e.message ?: e.javaClass.simpleName)
                },
            )
        }
    }

    fun clearSelection() {
        _selected.value = null
        _posts.value = BoardPostsState()
    }

    fun remove(board: SavedBoard) {
        boardsStore.remove(board.baseUrl)
    }
}
