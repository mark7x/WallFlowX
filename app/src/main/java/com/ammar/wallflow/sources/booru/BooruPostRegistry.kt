package com.ammar.wallflow.sources.booru

import com.ammar.wallflow.model.Wallpaper
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps the booru posts the feed has loaded, so the viewer can open one with the same
 * actions as Wallhaven images (download, set as wallpaper). Cleared when it gets large.
 */
object BooruPostRegistry {
    private const val MAX_POSTS = 2000
    private val posts = ConcurrentHashMap<String, Wallpaper>()

    fun remember(wallpapers: List<Wallpaper>) {
        if (posts.size + wallpapers.size > MAX_POSTS) posts.clear()
        wallpapers.forEach { posts[it.id] = it }
    }

    fun get(id: String): Wallpaper? = posts[id]
}
