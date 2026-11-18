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
import com.ammar.wallflow.data.offline.OfflineDownloadDelay

/** Slider for the pause between offline downloads. Saves when the finger lifts. */
@Composable
fun OfflineDelaySetting(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var delayMs by remember {
        mutableStateOf(OfflineDownloadDelay.get(context))
    }
    val steps = ((OfflineDownloadDelay.MAX_MS - OfflineDownloadDelay.MIN_MS) /
        OfflineDownloadDelay.STEP_MS).toInt() - 1
    Column(modifier = modifier) {
        Text(
            text = stringResource(
                R.string.offline_delay_label,
                delayMs / 1000f,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.offline_delay_desc),
            style = MaterialTheme.typography.bodySmall,
        )
        Slider(
            value = delayMs.toFloat(),
            onValueChange = { delayMs = it.toLong() / OfflineDownloadDelay.STEP_MS * OfflineDownloadDelay.STEP_MS },
            onValueChangeFinished = { OfflineDownloadDelay.set(context, delayMs) },
            valueRange = OfflineDownloadDelay.MIN_MS.toFloat()..OfflineDownloadDelay.MAX_MS.toFloat(),
            steps = steps,
        )
    }
}
