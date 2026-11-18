package com.ammar.wallflow.ui.screens.boards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ammar.wallflow.model.Purity
import com.ammar.wallflow.sources.booru.BoardsStore
import com.ammar.wallflow.sources.booru.BooruPost
import com.ammar.wallflow.sources.booru.BooruPostsClient
import com.ammar.wallflow.sources.booru.SavedBoard
import com.ammar.wallflow.sources.booru.purity
import dagger.hilt.android.lifecycle.HiltViewModel
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

@HiltViewModel
class BoardsViewModel @Inject constructor(
    private val boardsStore: BoardsStore,
    private val booruPostsClient: BooruPostsClient,
) : ViewModel() {
    val boards: StateFlow<List<SavedBoard>> = boardsStore.boards

    private val _selected = MutableStateFlow<SavedBoard?>(null)
    val selected: StateFlow<SavedBoard?> = _selected.asStateFlow()

    private val _posts = MutableStateFlow(BoardPostsState())
    val posts: StateFlow<BoardPostsState> = _posts.asStateFlow()

    fun select(board: SavedBoard) {
        _selected.value = board
        _posts.value = BoardPostsState(loading = true)
        viewModelScope.launch {
            booruPostsClient.fetchPosts(board.style, board.baseUrl, limit = 30).fold(
                onSuccess = { list ->
                    // Only SFW posts are shown. Sketchy, explicit and unrated posts are hidden.
                    _posts.value = BoardPostsState(
                        posts = list.filter { it.purity() == Purity.SFW },
                    )
                },
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
