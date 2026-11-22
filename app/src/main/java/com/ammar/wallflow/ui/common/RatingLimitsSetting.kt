package com.ammar.wallflow.ui.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ammar.wallflow.R
import com.ammar.wallflow.data.rating.DEFAULT_LIMITS
import com.ammar.wallflow.data.rating.wallpaperRatingStore
import kotlinx.coroutines.launch

/** Sets how many sets a favorite of each star rating survives. Used in Settings > Downloads. */
@Composable
fun RatingLimitsSetting(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { wallpaperRatingStore(context) }
    val scope = rememberCoroutineScope()
    var limits by remember { mutableStateOf(DEFAULT_LIMITS) }
    LaunchedEffect(Unit) {
        limits = store.limits()
    }
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.rating_limits_title),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.rating_help),
            style = MaterialTheme.typography.bodySmall,
        )
        listOf(1, 2, 3, 4).forEach { stars ->
            Column(modifier = Modifier.padding(vertical = 2.dp)) {
                Text(
                    text = stringResource(R.string.remove_after_label, stars),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf(1, 2, 3, 5, 10).forEach { sets ->
                        FilterChip(
                            selected = limits[stars] == sets,
                            onClick = {
                                scope.launch {
                                    store.setLimit(stars, sets)
                                    limits = store.limits()
                                }
                            },
                            label = { Text(text = sets.toString()) },
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                }
            }
        }
    }
}
