package com.ammar.wallflow.sources.booru

import androidx.compose.ui.unit.IntSize
import com.ammar.wallflow.model.DownloadableWallpaper
import com.ammar.wallflow.model.Purity
import com.ammar.wallflow.model.Source

/** A booru post shown in the feed. [data] is the full file URL. */
data class BooruWallpaper(
    override val id: String,
    override val data: String,
    override val thumbData: String?,
    override val resolution: IntSize,
    override val purity: Purity,
    val boardUrl: String,
) : DownloadableWallpaper() {
    override val source: Source = Source.BOORU
    override val fileSize: Long = 0L
    override val mimeType: String? = null
}

/** A post from a taught site. Sites have no rating, so it is treated as unrated (NSFW). */
fun siteWallpaper(siteId: String, postUrl: String, fullUrl: String, thumbUrl: String?) = BooruWallpaper(
    id = "site_${siteId}_$postUrl",
    data = fullUrl,
    thumbData = thumbUrl,
    resolution = IntSize.Zero,
    purity = Purity.NSFW,
    boardUrl = "site:$siteId",
)

/**
 * Maps a booru rating to [Purity]. Danbooru uses g/s/q/e, Gelbooru uses
 * general/sensitive/questionable/explicit, and Moebooru uses safe/questionable/explicit.
 * Unknown ratings map to NSFW.
 */
fun ratingToPurity(rating: String?): Purity = when (rating?.lowercase()) {
    "g", "general", "safe" -> Purity.SFW
    "s", "sensitive", "q", "questionable" -> Purity.SKETCHY
    "e", "explicit" -> Purity.NSFW
    else -> Purity.NSFW
}

fun BooruPost.toWallpaper(boardUrl: String) = BooruWallpaper(
    id = "${boardUrl}_$id",
    data = fileUrl,
    thumbData = previewUrl,
    resolution = IntSize.Zero,
    purity = ratingToPurity(rating),
    boardUrl = boardUrl,
)
