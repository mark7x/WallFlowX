package com.ammar.wallflow.sites

import com.ammar.wallflow.sources.booru.BooruWallpaper
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Feed tiles of taught sites carry the thumbnail only, so the feed loads as fast as the learning center.
 * When a post is opened, its full-size image is read from the post page.
 */
@Singleton
class SitePostResolver @Inject constructor(
    private val siteProfileStore: SiteProfileStore,
    private val siteGrabber: SiteGrabber,
) {
    private fun isPlaceholder(post: BooruWallpaper) = post.boardUrl.startsWith("site:") && post.data == post.thumbData

    suspend fun resolve(post: BooruWallpaper): BooruWallpaper {
        if (!isPlaceholder(post)) return post
        val siteId = post.boardUrl.removePrefix("site:")
        val postUrl = post.id.removePrefix("site_${siteId}_")
        val profile = siteProfileStore.get(siteId) ?: return post
        val full = siteGrabber.fullImageUrl(profile, postUrl) ?: return post
        return post.copy(data = full)
    }
}
