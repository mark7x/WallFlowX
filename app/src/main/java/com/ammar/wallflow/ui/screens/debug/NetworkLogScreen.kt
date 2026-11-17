package com.ammar.wallflow.ui.screens.debug

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ammar.wallflow.R
import com.ammar.wallflow.data.debug.NetworkLogEntry
import com.ammar.wallflow.navigation.AppNavGraphs.RootNavGraph
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Destination<RootNavGraph>
@Composable
fun NetworkLogScreen(
    viewModel: NetworkLogViewModel = hiltViewModel(),
    navigator: DestinationsNavigator,
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val successes = entries.filter { it.isSuccess }
    val failures = entries.filterNot { it.isSuccess }
    val shown = if (selectedTab == 0) successes else failures

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.network_log)) },
                navigationIcon = {
                    IconButton(onClick = { navigator.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::clear) {
                        Text(text = stringResource(R.string.network_log_clear))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = stringResource(R.string.network_log_success, successes.size),
                        )
                    },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = stringResource(R.string.network_log_failures, failures.size),
                        )
                    },
                )
            }
            if (shown.isEmpty()) {
                Text(
                    modifier = Modifier.padding(16.dp),
                    text = stringResource(R.string.network_log_empty),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            LazyColumn {
                items(shown, key = { it.id }) { entry ->
                    NetworkLogRow(entry = entry)
                }
            }
        }
    }
}

@Composable
private fun NetworkLogRow(entry: NetworkLogEntry) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = "${entry.method} · ${entry.code ?: "no response"} · " +
                "${entry.durationMs} ms · ${TIME_FORMAT.format(Date(entry.timeMillis))}",
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = entry.url,
            style = MaterialTheme.typography.bodySmall,
        )
        entry.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private val TIME_FORMAT = SimpleDateFormat("HH:mm:ss", Locale.US)
