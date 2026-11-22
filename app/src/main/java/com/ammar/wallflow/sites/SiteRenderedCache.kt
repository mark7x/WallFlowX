package com.ammar.wallflow.sites

import java.util.concurrent.ConcurrentHashMap

/**
 * Items taken from a page as the in-app browser rendered it. Used for sites whose
 * listings load with JavaScript, which a plain HTTP request cannot read.
 * Kept in memory, so it is refilled by scanning again after the app restarts.
 */
object SiteRenderedCache {
    private val items = ConcurrentHashMap<String, List<SiteItem>>()

    fun put(profileId: String, found: List<SiteItem>) {
        items[profileId] = found
    }

    fun get(profileId: String): List<SiteItem> = items[profileId].orEmpty()
}
