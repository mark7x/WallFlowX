package com.ammar.wallflow.ui.screens.offline

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
            Text(
                text = stringResource(R.string.offline_settings_note),
                style = MaterialTheme.typography.bodySmall,
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
            var showExportWarning by rememberSaveable { mutableStateOf(false) }
            val folderPicker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocumentTree(),
            ) { uri ->
                if (uri != null) viewModel.exportTo(uri)
            }
            TextButton(
                onClick = { showExportWarning = true },
                enabled = !state.running && !state.exporting && state.files > 0,
            ) {
                Text(text = stringResource(R.string.offline_export_button))
            }
            if (state.exporting) {
                Text(
                    text = stringResource(R.string.offline_exporting),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            state.exported?.let { copied ->
                Text(
                    text = stringResource(R.string.offline_export_done, copied),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (showExportWarning) {
                AlertDialog(
                    onDismissRequest = { showExportWarning = false },
                    title = { Text(text = stringResource(R.string.offline_export_warning_title)) },
                    text = { Text(text = stringResource(R.string.offline_export_warning)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showExportWarning = false
                                folderPicker.launch(null)
                            },
                        ) {
                            Text(text = stringResource(R.string.offline_export_continue))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showExportWarning = false }) {
                            Text(text = stringResource(R.string.offline_cancel))
                        }
                    },
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.offline_saved_searches),
                style = MaterialTheme.typography.titleMedium,
            )
            state.searches.forEach { saved ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = saved.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(
                        onClick = { viewModel.downloadSearch(saved) },
                        enabled = !state.running && !state.exporting && state.searchRunningId == null,
                    ) {
                        Text(text = stringResource(R.string.offline_search_download))
                    }
                }
            }
            if (state.searchRunningId != null) {
                Text(
                    text = stringResource(R.string.offline_search_running),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            state.searchMessage?.let { message ->
                Text(text = message, style = MaterialTheme.typography.bodySmall)
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
