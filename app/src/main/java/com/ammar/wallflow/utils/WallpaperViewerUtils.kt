package com.ammar.wallflow.utils

import com.ammar.wallflow.extensions.toast
import com.ammar.wallflow.R
import com.ammar.wallflow.data.rating.ratingKey
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.ammar.wallflow.data.rating.wallpaperRatingStore
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ammar.wallflow.MIME_TYPE_JPEG
import com.ammar.wallflow.activities.setwallpaper.SetWallpaperActivity
import com.ammar.wallflow.extensions.getFileNameFromUrl
import com.ammar.wallflow.extensions.getUriForFile
import com.ammar.wallflow.extensions.parseMimeType
import com.ammar.wallflow.extensions.share
import com.ammar.wallflow.model.DownloadableWallpaper
import com.ammar.wallflow.model.Wallpaper
import com.ammar.wallflow.model.local.LocalWallpaper
import com.ammar.wallflow.model.reddit.RedditWallpaper
import com.ammar.wallflow.model.reddit.withRedditDomainPrefix
import com.ammar.wallflow.model.wallhaven.WallhavenWallpaper
import com.ammar.wallflow.ui.wallpaperviewer.WallpaperViewerViewModel

fun shareWallpaperUrl(
    context: Context,
    wallpaper: Wallpaper,
) {
    val url = when (wallpaper) {
        is WallhavenWallpaper -> wallpaper.url
        is RedditWallpaper -> wallpaper.postUrl.withRedditDomainPrefix()
        else -> return
    }
    context.share(url)
}

fun shareWallpaper(
    context: Context,
    viewModel: WallpaperViewerViewModel,
    wallpaper: Wallpaper,
) {
    val type = when (wallpaper) {
        is WallhavenWallpaper -> wallpaper.mimeType.ifBlank {
            parseMimeType(wallpaper.data)
        }
        is RedditWallpaper -> parseMimeType(wallpaper.data)
        is LocalWallpaper -> wallpaper.mimeType ?: MIME_TYPE_JPEG
        else -> return
    }
    val title = when (wallpaper) {
        is DownloadableWallpaper -> wallpaper.data.getFileNameFromUrl()
        is LocalWallpaper -> wallpaper.name
        else -> return
    }
    if (wallpaper is LocalWallpaper) {
        context.share(
            uri = wallpaper.data,
            type = type,
            title = title,
            grantTempPermission = true,
        )
        return
    }
    viewModel.downloadForSharing(wallpaper) {
        if (it == null) return@downloadForSharing
        context.share(
            uri = context.getUriForFile(it),
            type = type,
            title = title,
            grantTempPermission = true,
        )
    }
}

fun applyWallpaper(
    context: Context,
    viewModel: WallpaperViewerViewModel,
    wallpaper: Wallpaper,
) {
    viewModel.viewModelScope.launch { wallpaperRatingStore(context).recordSet(wallpaper) }
    val cropKey = ratingKey(wallpaper.source, wallpaper.id)
    fun startActivity(uri: Uri, cropKey: String) {
        context.startActivity(
            Intent().apply {
                setClass(context, SetWallpaperActivity::class.java)
                putExtra(
                    SetWallpaperActivity.EXTRA_URI,
                    uri,
                )
                putExtra(SetWallpaperActivity.EXTRA_CROP_KEY, cropKey)
            },
        )
    }
    if (wallpaper is LocalWallpaper) {
        startActivity(wallpaper.data, cropKey)
        return
    }
    viewModel.downloadForSharing(wallpaper) {
        val file = it
        if (file == null) {
            context.toast(context.getString(R.string.apply_download_failed))
            return@downloadForSharing
        }
        val uri = context.getUriForFile(file)
        startActivity(uri, cropKey)
    }
}
