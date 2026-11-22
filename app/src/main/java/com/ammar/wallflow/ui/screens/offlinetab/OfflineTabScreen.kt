package com.ammar.wallflow.ui.screens.offlinetab

import android.text.format.Formatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ammar.wallflow.R
import com.ammar.wallflow.data.rating.ratingKey
import com.ammar.wallflow.destinations.WallpaperScreenDestination
import com.ammar.wallflow.model.Source
import com.ammar.wallflow.navigation.AppNavGraphs.OfflineTabNavGraph
import com.ammar.wallflow.ui.common.LocalSystemController
import com.ammar.wallflow.ui.common.RatingDialog
import com.ammar.wallflow.ui.common.bottomWindowInsets
import com.ammar.wallflow.ui.common.bottombar.LocalBottomBarController
import com.ammar.wallflow.ui.common.topWindowInsets
import com.ammar.wallflow.ui.screens.main.RootNavControllerWrapper
import com.ammar.wallflow.utils.getStartBottomPadding
import com.ramcosta.composedestinations.annotation.Destination

/** What the rating dialog is for: a grid item (may also be removed) or a recent entry. */
private data class RatingTarget(
    val source: Source,
    val id: String,
    val stars: Int,
    val item: OfflineItem?,
)

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
    var pendingDelete by remember { mutableStateOf<OfflineItem?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var rating by remember { mutableStateOf<RatingTarget?>(null) }

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
        // One scrolling grid: the header and Recent row span the full width, so a tall header
        // can never squeeze the tiles out of view.
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
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
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
                    Text(
                        text = stringResource(R.string.offline_settings_note),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
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
            }

            if (state.recent.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        Text(
                            text = stringResource(R.string.recent_title),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(state.recent, key = { it.source + ":" + it.id }) { entry ->
                                val source = Source.values().firstOrNull { it.name == entry.source }
                                if (source != null) {
                                    val stars = state.ratings[ratingKey(source, entry.id)] ?: 0
                                    Box(
                                        modifier = Modifier
                                            .size(84.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                rating = RatingTarget(source, entry.id, stars, item = null)
                                            },
                                    ) {
                                        AsyncImage(
                                            model = entry.preview,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                        StarBadge(
                                            stars = stars,
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(4.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (state.items.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.offline_tab_empty),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            } else {
                items(state.items, key = { it.wallpaper.id }) { item ->
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .combinedClickable(
                                onClick = {
                                    rootNavController.navigate(
                                        WallpaperScreenDestination(
                                            source = Source.LOCAL,
                                            wallpaperId = item.wallpaper.id,
                                            thumbData = item.wallpaper.thumbData,
                                        ).route,
                                    )
                                },
                                onLongClick = {
                                    rating = RatingTarget(item.source, item.originalId, item.stars, item)
                                },
                            ),
                    ) {
                        AsyncImage(
                            model = item.wallpaper.data,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        StarBadge(
                            stars = item.stars,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(4.dp),
                        )
                    }
                }
            }
        }
    }

    rating?.let { target ->
        RatingDialog(
            current = target.stars,
            onRate = { stars -> viewModel.rate(target.source, target.id, stars) },
            onDismiss = { rating = null },
            onDelete = target.item?.let { item -> { pendingDelete = item } },
        )
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(text = stringResource(R.string.offline_tab_delete_title)) },
            text = { Text(text = stringResource(R.string.offline_tab_delete_text)) },
            confirmButton = {
                Button(onClick = {
                    viewModel.delete(item)
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

@Composable
private fun StarBadge(stars: Int, modifier: Modifier = Modifier) {
    if (stars <= 0) return
    Text(
        text = "★$stars",
        style = MaterialTheme.typography.labelMedium,
        color = Color.White,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}
