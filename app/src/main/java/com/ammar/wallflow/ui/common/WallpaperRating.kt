package com.ammar.wallflow.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ammar.wallflow.R
import com.ammar.wallflow.data.rating.DEFAULT_REMOVE_AFTER_SETS
import com.ammar.wallflow.data.rating.MAX_STARS
import com.ammar.wallflow.data.rating.ratingKey
import com.ammar.wallflow.data.rating.wallpaperRatingStore
import com.ammar.wallflow.model.Wallpaper
import kotlinx.coroutines.launch

/** Rates 1 to 5 stars. [onDelete] adds a Remove button, used for offline items. */
@Composable
fun RatingDialog(
    current: Int,
    removeAfterSets: Int,
    onRate: (Int) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.rate_wallpaper)) },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    (1..MAX_STARS).forEach { stars ->
                        TextButton(onClick = {
                            onRate(stars)
                            onDismiss()
                        }) {
                            Text(
                                text = if (stars <= current) "★" else "☆",
                                style = MaterialTheme.typography.headlineMedium,
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.rating_help, removeAfterSets),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.cancel)) }
        },
        dismissButton = if (onDelete != null) {
            {
                TextButton(onClick = {
                    onDelete()
                    onDismiss()
                }) {
                    Text(text = stringResource(R.string.delete))
                }
            }
        } else {
            null
        },
    )
}

/** Toolbar button in the full image viewer. Shows the current stars and opens the rating dialog. */
@Composable
fun ViewerRatingButton(wallpaper: Wallpaper) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { wallpaperRatingStore(context) }
    var stars by remember { mutableStateOf(0) }
    var open by remember { mutableStateOf(false) }
    val removeAfter by produceState(initialValue = DEFAULT_REMOVE_AFTER_SETS) {
        value = store.removeAfterSets()
    }
    val key = ratingKey(wallpaper.source, wallpaper.id)
    LaunchedEffect(key) {
        stars = store.allRatings()[key] ?: 0
    }
    IconButton(onClick = { open = true }) {
        Text(
            text = if (stars > 0) "★$stars" else "☆",
            style = MaterialTheme.typography.titleMedium,
        )
    }
    if (open) {
        RatingDialog(
            current = stars,
            removeAfterSets = removeAfter,
            onRate = { n ->
                stars = n
                scope.launch { store.setStars(wallpaper.source, wallpaper.id, n) }
            },
            onDismiss = { open = false },
        )
    }
}
