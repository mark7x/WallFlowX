package com.ammar.wallflow.ui.sites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ammar.wallflow.R
import com.ammar.wallflow.destinations.SiteDetailScreenDestination
import com.ammar.wallflow.navigation.AppNavGraphs.RootNavGraph
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootNavGraph>
@Composable
fun SitesScreen(
    navigator: DestinationsNavigator,
    viewModel: SitesViewModel = hiltViewModel(),
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.sites_title)) },
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
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = stringResource(R.string.site_add_button))
            }
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            if (profiles.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.sites_empty),
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(profiles, key = { it.id }) { profile ->
                ListItem(
                    modifier = Modifier.clickable {
                        navigator.navigate(SiteDetailScreenDestination(profile.id))
                    },
                    headlineContent = { Text(text = profile.name) },
                    supportingContent = {
                        Text(
                            text = if (profile.canBrowse) {
                                stringResource(R.string.site_ready, profile.tagCounts.size)
                            } else {
                                stringResource(R.string.site_not_learned)
                            },
                        )
                    },
                    trailingContent = {
                        IconButton(onClick = { viewModel.delete(profile.id) }) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.site_delete),
                            )
                        }
                    },
                )
            }
        }
    }
    if (showAdd) {
        AddSiteDialog(
            onDismiss = { showAdd = false },
            onAdd = { name, url ->
                viewModel.add(name, url)
                showAdd = false
            },
        )
    }
}

@Composable
private fun AddSiteDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, searchUrl: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    val valid = name.isNotBlank() &&
        (url.trim().startsWith("https://") || url.trim().startsWith("http://"))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.site_add_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(text = stringResource(R.string.site_name_label)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(text = stringResource(R.string.site_url_label)) },
                    placeholder = { Text(text = stringResource(R.string.site_url_hint)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(name, url) },
                enabled = valid,
            ) {
                Text(text = stringResource(R.string.site_add_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.site_cancel))
            }
        },
    )
}
