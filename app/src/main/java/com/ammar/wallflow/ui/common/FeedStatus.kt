package com.ammar.wallflow.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import com.ammar.wallflow.R

/**
 * Adds a full-width status item to a wallpaper grid when there is nothing to show:
 * a retry prompt when the initial load failed, or a "no results" message when the
 * query returned nothing. Adds nothing while loading or when there are results.
 */
internal fun LazyStaggeredGridScope.feedStatusItem(
    refresh: LoadState,
    onRetry: () -> Unit,
) {
    when (refresh) {
        is LoadState.Error -> item(
            span = StaggeredGridItemSpan.FullLine,
            contentType = "feed-status",
        ) {
            FeedStatus(
                iconRes = R.drawable.baseline_error_outline_24,
                title = stringResource(R.string.load_error_title),
                message = stringResource(R.string.load_error_message),
                actionText = stringResource(R.string.retry),
                onAction = onRetry,
            )
        }
        is LoadState.NotLoading -> item(
            span = StaggeredGridItemSpan.FullLine,
            contentType = "feed-status",
        ) {
            FeedStatus(
                iconRes = R.drawable.outline_image_24,
                title = stringResource(R.string.no_results_title),
                message = stringResource(R.string.no_results_message),
            )
        }
        else -> Unit
    }
}

/** Adds a retry row at the end of the feed when loading the next page fails. */
internal fun LazyStaggeredGridScope.feedAppendErrorItem(
    onRetry: () -> Unit,
) {
    item(
        span = StaggeredGridItemSpan.FullLine,
        contentType = "feed-status",
    ) {
        FeedStatus(
            iconRes = R.drawable.baseline_error_outline_24,
            title = stringResource(R.string.load_error_title),
            message = stringResource(R.string.load_error_message),
            actionText = stringResource(R.string.retry),
            onAction = onRetry,
        )
    }
}

@Composable
private fun FeedStatus(
    iconRes: Int,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            modifier = Modifier.size(48.dp),
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionText != null && onAction != null) {
            OutlinedButton(onClick = onAction) {
                Text(text = actionText)
            }
        }
    }
}
