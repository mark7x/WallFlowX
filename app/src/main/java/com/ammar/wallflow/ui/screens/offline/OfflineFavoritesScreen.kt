package com.ammar.wallflow.ui.screens.offline

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import android.text.format.Formatter
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ammar.wallflow.R
import com.ammar.wallflow.navigation.AppNavGraphs.RootNavGraph
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootNavGraph>
@Composable
fun OfflineFavoritesScreen(
    viewModel: OfflineFavoritesViewModel = hiltViewModel(),
    navigator: DestinationsNavigator,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.offline_favorites)) },
                navigationIcon = {
                    IconButton(onClick = { navigator.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.offline_favorites_desc),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = viewModel::start,
                enabled = !state.running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.offline_download_all))
            }
            if (state.running) {
                Spacer(modifier = Modifier.height(16.dp))
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
                TextButton(onClick = viewModel::cancel) {
                    Text(text = stringResource(R.string.offline_cancel))
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(
                    R.string.offline_storage,
                    state.files,
                    Formatter.formatShortFileSize(LocalContext.current, state.bytes),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(
                onClick = viewModel::deleteAll,
                enabled = !state.running && state.files > 0,
            ) {
                Text(text = stringResource(R.string.offline_delete_all))
            }
            state.summary?.let { summary ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(
                        R.string.offline_done,
                        summary.saved,
                        summary.skipped,
                        summary.failed,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
