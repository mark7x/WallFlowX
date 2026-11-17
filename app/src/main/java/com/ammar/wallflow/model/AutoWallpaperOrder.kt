package com.ammar.wallflow.model

/**
 * How auto wallpaper picks the next image.
 * Favorites use the time they were favorited. Saved searches use the search's own order,
 * and RANDOM shuffles each page of results.
 */
enum class AutoWallpaperOrder {
    RANDOM,
    NEWEST_FIRST,
    OLDEST_FIRST,
}
