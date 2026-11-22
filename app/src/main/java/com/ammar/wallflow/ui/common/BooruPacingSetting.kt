package com.ammar.wallflow.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ammar.wallflow.R
import com.ammar.wallflow.sources.booru.BooruPacing

/** Slider for the pause between requests to the same booru site. Saves when the finger lifts. */
@Composable
fun BooruPacingSetting(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var delayMs by remember { mutableStateOf(BooruPacing.delayMs(context)) }
    val steps = ((BooruPacing.MAX_MS - BooruPacing.MIN_MS) / BooruPacing.STEP_MS).toInt() - 1
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.booru_delay_label, delayMs / 1000f),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.booru_delay_desc),
            style = MaterialTheme.typography.bodySmall,
        )
        Slider(
            value = delayMs.toFloat(),
            onValueChange = {
                delayMs = it.toLong() / BooruPacing.STEP_MS * BooruPacing.STEP_MS
            },
            onValueChangeFinished = { BooruPacing.setDelayMs(context, delayMs) },
            valueRange = BooruPacing.MIN_MS.toFloat()..BooruPacing.MAX_MS.toFloat(),
            steps = steps,
        )
    }
}
