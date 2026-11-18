package com.ammar.wallflow.sources

/**
 * Describes one image source (a board or site) that the app can read wallpapers from.
 *
 * Stage 1 only carries metadata. Searching and paging stay in each source's existing
 * repository until they are moved behind this interface in a later stage.
 */
interface WallpaperSource {
    /** Stable ID, stored with saved searches and favorites. Must never change once released. */
    val id: String

    /** Name shown in the UI. */
    val displayName: String

    /** What the source supports, so the UI only shows controls that work for it. */
    val capabilities: Set<SourceCapability>
}

enum class SourceCapability {
    SEARCH,
    TAGS,
    PURITY_FILTER,
    LOGIN,
}
