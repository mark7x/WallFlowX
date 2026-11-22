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
import com.ammar.wallflow.sources.pacing.RequestPacing

/** Slider for the pause between requests to the same booru site. Saves when the finger lifts. */
@Composable
fun RequestPacingSetting(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var delayMs by remember { mutableStateOf(RequestPacing.delayMs(context)) }
    val steps = ((RequestPacing.MAX_MS - RequestPacing.MIN_MS) / RequestPacing.STEP_MS).toInt() - 1
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
                delayMs = it.toLong() / RequestPacing.STEP_MS * RequestPacing.STEP_MS
            },
            onValueChangeFinished = { RequestPacing.setDelayMs(context, delayMs) },
            valueRange = RequestPacing.MIN_MS.toFloat()..RequestPacing.MAX_MS.toFloat(),
            steps = steps,
        )
    }
}
