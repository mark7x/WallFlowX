package com.ammar.wallflow.ui.screens.offlinetab

import android.text.format.Formatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ammar.wallflow.R
import com.ammar.wallflow.destinations.WallpaperScreenDestination
import com.ammar.wallflow.model.Source
import com.ammar.wallflow.model.Wallpaper
import com.ammar.wallflow.navigation.AppNavGraphs.OfflineTabNavGraph
import com.ammar.wallflow.ui.common.LocalSystemController
import com.ammar.wallflow.ui.common.OfflineDelaySetting
import com.ammar.wallflow.ui.common.bottomWindowInsets
import com.ammar.wallflow.ui.common.bottombar.LocalBottomBarController
import com.ammar.wallflow.ui.common.topWindowInsets
import com.ammar.wallflow.ui.screens.main.RootNavControllerWrapper
import com.ammar.wallflow.utils.getStartBottomPadding
import com.ramcosta.composedestinations.annotation.Destination

@OptIn(ExperimentalFoundationApi::class)
@Destination<OfflineTabNavGraph>(
    start = true,
)
@Composable
fun OfflineTabScreen(
    rootNavControllerWrapper: RootNavControllerWrapper,
    viewModel: OfflineTabViewModel = hiltViewModel(),
) {
    val rootNavController = rootNavControllerWrapper.navController
    val state by viewModel.state.collectAsStateWithLifecycle()
    val bottomBarController = LocalBottomBarController.current
    val systemController = LocalSystemController.current
    val density = LocalDensity.current
    val context = LocalContext.current
    val bottomPadding = getStartBottomPadding(
        density,
        bottomBarController,
        bottomWindowInsets,
        WindowInsets.navigationBars,
    )
    var pendingDelete by remember { mutableStateOf<Wallpaper?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        systemController.resetBarsState()
        bottomBarController.update { it.copy(visible = true) }
        viewModel.refresh()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(topWindowInsets),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(R.string.offline_favorites),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(
                    R.string.offline_tab_summary,
                    state.items.size,
                    Formatter.formatShortFileSize(context, state.bytes),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            OfflineDelaySetting(modifier = Modifier.padding(vertical = 4.dp))
            Button(
                onClick = viewModel::downloadFavorites,
                enabled = !state.running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.offline_download_all))
            }
            if (state.running) {
                LinearProgressIndicator(
                    progress = {
                        if (state.total == 0) 0f else state.done.toFloat() / state.total
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.offline_progress, state.done, state.total),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            state.message?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall)
            }
            if (state.items.isNotEmpty()) {
                TextButton(onClick = { confirmDeleteAll = true }) {
                    Text(text = stringResource(R.string.offline_delete_all))
                }
            }
        }
        if (state.items.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.offline_tab_empty),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(120.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 8.dp,
                    end = 8.dp,
                    top = 8.dp,
                    bottom = bottomPadding + 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.items, key = { it.id }) { wallpaper ->
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .combinedClickable(
                                onClick = {
                                    rootNavController.navigate(
                                        WallpaperScreenDestination(
                                            source = Source.LOCAL,
                                            wallpaperId = wallpaper.id,
                                            thumbData = wallpaper.thumbData,
                                        ).route,
                                    )
                                },
                                onLongClick = { pendingDelete = wallpaper },
                            ),
                    ) {
                        AsyncImage(
                            model = wallpaper.data,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { wallpaper ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(text = stringResource(R.string.offline_tab_delete_title)) },
            text = { Text(text = stringResource(R.string.offline_tab_delete_text)) },
            confirmButton = {
                Button(onClick = {
                    viewModel.delete(wallpaper)
                    pendingDelete = null
                }) {
                    Text(text = stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(text = stringResource(R.string.cancel))
                }
            },
        )
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text(text = stringResource(R.string.offline_delete_all)) },
            text = { Text(text = stringResource(R.string.offline_tab_delete_all_text)) },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteAll()
                    confirmDeleteAll = false
                }) {
                    Text(text = stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            },
        )
    }
}
