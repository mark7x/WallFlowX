package com.ammar.wallflow.ui.common

import android.widget.Toast
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ammar.wallflow.R
import com.ammar.wallflow.data.offline.offlineFavoritesStore
import com.ammar.wallflow.model.DownloadableWallpaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Tracks whether [wallpaper] has an offline copy, and saves or removes it on toggle. */
class OfflineSaveHandle(
    val saved: Boolean,
    val saving: Boolean,
    val toggle: () -> Unit,
)

@Composable
fun rememberOfflineSave(wallpaper: DownloadableWallpaper): OfflineSaveHandle {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { offlineFavoritesStore(context) }
    val onDisk by produceState(initialValue = false, key1 = wallpaper.id) {
        value = withContext(Dispatchers.IO) { store.existingFile(context, wallpaper) != null }
    }
    var savedOverride by remember(wallpaper.id) { mutableStateOf<Boolean?>(null) }
    var saving by remember(wallpaper.id) { mutableStateOf(false) }
    val saved = savedOverride ?: onDisk
    val failedText = stringResource(R.string.offline_save_failed)
    return OfflineSaveHandle(
        saved = saved,
        saving = saving,
        toggle = {
            if (!saving) {
                scope.launch {
                    if (saved) {
                        withContext(Dispatchers.IO) { store.removeOne(context, wallpaper) }
                        savedOverride = false
                    } else {
                        saving = true
                        val ok = withContext(Dispatchers.IO) { store.saveOne(context, wallpaper) }
                        saving = false
                        if (ok) {
                            savedOverride = true
                        } else {
                            Toast.makeText(context, failedText, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        },
    )
}

/** Small round button for the bottom-left corner of a grid card. */
@Composable
fun CardOfflineSaveButton(
    wallpaper: DownloadableWallpaper,
    modifier: Modifier = Modifier,
) {
    val handle = rememberOfflineSave(wallpaper)
    FilledIconButton(
        modifier = modifier,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = Color.Black.copy(alpha = 0.5f),
            contentColor = if (handle.saved) Color.Green else Color.White,
        ),
        onClick = handle.toggle,
    ) {
        if (handle.saving) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = Color.White,
            )
        } else {
            Icon(
                modifier = Modifier.size(24.dp),
                painter = painterResource(
                    if (handle.saved) {
                        R.drawable.outline_file_download_done_24
                    } else {
                        R.drawable.outline_file_download_24
                    },
                ),
                contentDescription = stringResource(R.string.offline_save),
            )
        }
    }
}

/** Toolbar button for the full image viewer. */
@Composable
fun ViewerOfflineSaveButton(
    wallpaper: DownloadableWallpaper,
) {
    val handle = rememberOfflineSave(wallpaper)
    IconButton(onClick = handle.toggle) {
        if (handle.saving) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                painter = painterResource(
                    if (handle.saved) {
                        R.drawable.outline_file_download_done_24
                    } else {
                        R.drawable.outline_file_download_24
                    },
                ),
                contentDescription = stringResource(R.string.offline_save),
            )
        }
    }
}
