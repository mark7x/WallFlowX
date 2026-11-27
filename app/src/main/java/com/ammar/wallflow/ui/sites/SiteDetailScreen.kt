package com.ammar.wallflow.ui.sites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ammar.wallflow.R
import com.ammar.wallflow.destinations.SiteWebScreenDestination
import com.ammar.wallflow.navigation.AppNavGraphs.RootNavGraph
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootNavGraph>
@Composable
fun SiteDetailScreen(
    navigator: DestinationsNavigator,
    profileId: String,
    viewModel: SiteDetailViewModel = hiltViewModel(),
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, profileId) {
        // ON_RESUME also fires right away, so this loads the profile on entry and after learning.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.load(profileId)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = profile?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = { navigator.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = {
                        profile?.let { current ->
                            navigator.navigate(
                                SiteWebScreenDestination(
                                    profileId = current.id,
                                    url = current.searchAddress("", 1),
                                ),
                            )
                        }
                    }) {
                        Text(
                            text = stringResource(
                                if (profile?.canBrowse == true) R.string.site_relearn else R.string.site_learn,
                            ),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            profile?.takeIf { it.canBrowse }?.let { learned ->
                Text(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    text = stringResource(R.string.site_ready, learned.tagCounts.size),
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                )
            }
            profile?.takeIf { it.canBrowse && it.nextPageSelector != null }?.let { learned ->
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = stringResource(R.string.site_hidden_load_more),
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    )
                    androidx.compose.material3.Switch(
                        checked = learned.hiddenLoadMore,
                        onCheckedChange = { viewModel.setHiddenLoadMore(it) },
                    )
                }
                Text(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    text = stringResource(R.string.site_hidden_help),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    viewModel.onQueryChange(it)
                },
                label = { Text(text = stringResource(R.string.site_tags_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                state.suggestions.forEach { suggestion ->
                    AssistChip(
                        onClick = {
                            text = text.substringBeforeLast(' ', "").let {
                                if (it.isEmpty()) suggestion else "$it $suggestion"
                            } + " "
                            viewModel.onQueryChange(text)
                        },
                        label = { Text(text = suggestion) },
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
            }
            Button(
                onClick = { viewModel.search(text) },
                enabled = profile?.canBrowse == true && !state.loading,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Text(text = stringResource(R.string.site_search))
            }
            if (state.loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(16.dp))
            }
            state.error?.let {
                Text(
                    text = it,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (state.items.isEmpty() && !state.loading) {
                Text(
                    text = stringResource(R.string.site_no_items),
                    modifier = Modifier.padding(16.dp),
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(120.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.items) { item ->
                    AsyncImage(
                        model = item.thumbUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clickable(enabled = item.postUrl != null) {
                                item.postUrl?.let { url ->
                                    navigator.navigate(SiteWebScreenDestination(profileId, url))
                                }
                            },
                    )
                }
                if (state.items.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        TextButton(
                            onClick = viewModel::loadMore,
                            enabled = !state.loading,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = stringResource(R.string.site_load_more))
                        }
                    }
                }
            }
        }
    }
}
