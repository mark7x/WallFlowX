package com.ammar.wallflow.ui.common

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ammar.wallflow.R
import kotlin.math.roundToInt

@Composable
fun ProgressIndicator(
    modifier: Modifier = Modifier,
    progress: Float = -1F,
    strokeCap: StrokeCap = StrokeCap.Round,
    circular: Boolean = true,
) {
    val progressDescription = stringResource(
        R.string.progress_percent,
        (progress * 100).roundToInt(),
    )
    if (progress <= -1F) {
        if (circular) {
            CircularProgressIndicator(
                modifier = modifier.testTag("circular-progress"),
                strokeCap = strokeCap,
            )
        } else {
            LinearProgressIndicator(
                modifier = modifier.testTag("linear-progress"),
                strokeCap = strokeCap,
            )
        }
    } else {
        if (circular) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = modifier
                    .testTag("circular-progress")
                    .semantics { contentDescription = progressDescription },
                strokeCap = strokeCap,
            )
        } else {
            LinearProgressIndicator(
                progress = { progress },
                modifier = modifier
                    .testTag("linear-progress")
                    .semantics { contentDescription = progressDescription },
                strokeCap = strokeCap,
            )
        }
    }
}
