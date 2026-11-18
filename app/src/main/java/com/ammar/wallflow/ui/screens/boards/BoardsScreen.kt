package com.ammar.wallflow.ui.screens.boards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ammar.wallflow.R
import com.ammar.wallflow.navigation.AppNavGraphs.RootNavGraph
import com.ammar.wallflow.sources.booru.SavedBoard
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootNavGraph>
@Composable
fun BoardsScreen(
    viewModel: BoardsViewModel = hiltViewModel(),
    navigator: DestinationsNavigator,
) {
    val boards by viewModel.boards.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val postsState by viewModel.posts.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.boards)) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (selected != null) viewModel.clearSelection() else navigator.navigateUp()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        val current = selected
        if (current == null) {
            BoardList(
                boards = boards,
                modifier = Modifier.padding(padding),
                onOpen = viewModel::select,
                onRemove = viewModel::remove,
            )
        } else {
            BoardPosts(
                board = current,
                state = postsState,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun BoardList(
    boards: List<SavedBoard>,
    modifier: Modifier = Modifier,
    onOpen: (SavedBoard) -> Unit,
    onRemove: (SavedBoard) -> Unit,
) {
    if (boards.isEmpty()) {
        Column(modifier = modifier.padding(16.dp)) {
            Text(text = stringResource(R.string.boards_empty), style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(boards, key = { it.baseUrl }) { board ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(board) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(text = board.baseUrl, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = stringResource(R.string.boards_style, board.style.name.lowercase()),
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { onRemove(board) }) {
                    Text(text = stringResource(R.string.boards_remove))
                }
            }
        }
    }
}

@Composable
private fun BoardPosts(
    board: SavedBoard,
    state: BoardPostsState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = board.baseUrl,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(16.dp),
        )
        when {
            state.loading -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            state.error != null -> Text(
                text = stringResource(R.string.boards_error, state.error),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp),
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                contentPadding = PaddingValues(4.dp),
            ) {
                items(state.posts, key = { it.id }) { post ->
                    AsyncImage(
                        model = post.previewUrl ?: post.fileUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .padding(2.dp)
                            .aspectRatio(1f),
                    )
                }
            }
        }
    }
}
