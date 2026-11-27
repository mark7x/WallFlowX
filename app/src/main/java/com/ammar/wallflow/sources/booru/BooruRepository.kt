package com.ammar.wallflow.sources.booru

import com.ammar.wallflow.sites.SiteGrabber
import com.ammar.wallflow.sites.SiteProfileStore
import com.ammar.wallflow.sites.SiteHiddenBrowser
import com.ammar.wallflow.sites.SiteLog
import com.ammar.wallflow.sites.SiteRenderedCache
import androidx.paging.PagingSource.LoadParams
import androidx.paging.PagingSource.LoadResult
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.ammar.wallflow.model.Wallpaper
import com.ammar.wallflow.model.search.BooruSearch
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Loads feed pages for booru boards. Each page is one request to the board. */
@Singleton
class BooruRepository @Inject constructor(
    private val booruPostsClient: BooruPostsClient,
    private val siteProfileStore: SiteProfileStore,
    private val siteGrabber: SiteGrabber,
    private val siteRenderedCache: SiteRenderedCache,
    private val siteLog: SiteLog,
    private val siteHiddenBrowser: SiteHiddenBrowser,
) {
    fun wallpapersPager(search: BooruSearch): Flow<PagingData<Wallpaper>> = Pager(
        config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
        pagingSourceFactory = {
            BooruPagingSource(
                booruPostsClient, siteProfileStore, siteGrabber, siteRenderedCache, siteLog, siteHiddenBrowser, search,
            )
        },
    ).flow

    companion object {
        const val PAGE_SIZE = 30
    }
}

private class BooruPagingSource(
    private val booruPostsClient: BooruPostsClient,
    private val siteProfileStore: SiteProfileStore,
    private val siteGrabber: SiteGrabber,
    private val siteRenderedCache: SiteRenderedCache,
    private val siteLog: SiteLog,
    private val siteHiddenBrowser: SiteHiddenBrowser,
    private val search: BooruSearch,
) : PagingSource<Int, Wallpaper>() {
    // Ids already shown in this list. A post can come back on a later page when new posts
    // shift the results, and the grid needs each id only once.
    private val seenIds = mutableSetOf<String>()

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Wallpaper> {
        val page = params.key ?: 1
        search.filters.siteId?.let { return loadSite(it, page) }
        val boardUrl = search.filters.boardUrl
        val allowed = search.filters.allowedPurities()
        return booruPostsClient.fetchPosts(
            style = search.filters.style,
            baseUrl = boardUrl,
            limit = params.loadSize,
            page = page,
            tags = search.query,
        ).fold(
            onSuccess = { posts ->
                val wallpapers: List<Wallpaper> = posts
                    .map { it.toWallpaper(boardUrl) }
                    .filter { it.purity in allowed }
                    .filter { seenIds.add(it.id) }
                BooruPostRegistry.remember(wallpapers)
                LoadResult.Page(
                    data = wallpapers,
                    prevKey = if (page > 1) page - 1 else null,
                    nextKey = if (posts.isEmpty()) null else page + 1,
                )
            },
            onFailure = { LoadResult.Error(it) },
        )
    }

    private suspend fun loadSite(siteId: String, page: Int): LoadResult<Int, Wallpaper> {
        val profile = siteProfileStore.get(siteId)
            ?: return LoadResult.Error(IllegalStateException("This site was removed."))
        val query = search.query.trim()
        siteLog.log("feed", "${profile.name}: page $page, query '$query'")
        if (query.isNotEmpty() && !profile.searchUrl.contains("{tags}")) {
            siteLog.log("feed", "${profile.name}: search not learned, address ${profile.searchUrl} has no {tags}")
            return LoadResult.Error(
                IllegalStateException(
                    "Search isn't learned for ${profile.name}. Open it in Sites, search on the site, then tap Save search.",
                ),
            )
        }
        // The plain listing keeps what the learning page scanned. Each search keeps its own posts,
        // and starts fresh every time it is run.
        val key = siteCacheKey(siteId, query)
        if (query.isNotEmpty() && page == 1) {
            siteRenderedCache.clear(key)
            siteHiddenBrowser.close("new search '$query'")
        }
        val needed = page * SITE_PAGE_SIZE
        var saved = siteRenderedCache.get(key)
        val canClick = profile.hiddenLoadMore && profile.nextPageSelector != null && profile.itemSelector != null
        if (page == 1 && saved.isEmpty()) {
            val first = siteGrabber.search(profile, query, 1)
            val items = first.getOrNull().orEmpty()
            siteLog.log(
                "feed",
                "${profile.name}: first page over HTTP gave ${items.size} posts" +
                    (first.exceptionOrNull()?.let { ", error: ${it.message}" } ?: ""),
            )
            if (items.isNotEmpty()) {
                siteRenderedCache.add(key, items)
            } else if (profile.hiddenLoadMore && profile.itemSelector != null) {
                // Sites that draw their list with JavaScript: read the first page in the hidden browser.
                siteHiddenBrowser.loadNextPage(profile, query, key, click = false)
            }
            saved = siteRenderedCache.get(key)
            if (saved.isEmpty() && first.isFailure) {
                return LoadResult.Error(first.exceptionOrNull() ?: IllegalStateException("Site search failed."))
            }
        }
        var attempts = 0
        while (canClick && saved.size < needed && attempts < MAX_HIDDEN_ATTEMPTS) {
            attempts++
            siteLog.log("feed", "${profile.name}: page $page needs $needed posts, ${saved.size} saved, hidden load #$attempts")
            val added = siteHiddenBrowser.loadNextPage(profile, query, key)
            saved = siteRenderedCache.get(key)
            if (added <= 0) break
        }
        val offset = (page - 1) * SITE_PAGE_SIZE
        if (saved.size <= offset) {
            siteLog.log(
                "feed",
                "${profile.name}: page $page stopping, ${saved.size} saved posts and " +
                    if (canClick) "the hidden browser added none (see [hidden] lines)" else "no way to load more",
            )
            return LoadResult.Page(data = emptyList(), prevKey = if (page > 1) page - 1 else null, nextKey = null)
        }
        val allowed = search.filters.allowedPurities()
        val pageItems = saved.drop(offset).take(SITE_PAGE_SIZE)
        val wallpapers: List<Wallpaper> = pageItems.mapNotNull { item ->
            val postUrl = item.postUrl ?: return@mapNotNull null
            val thumb = item.thumbUrl ?: return@mapNotNull null
            siteWallpaper(siteId, postUrl, null, thumb)
        }.filter { it.purity in allowed }.filter { seenIds.add(it.id) }
        val more = canClick || saved.size > offset + SITE_PAGE_SIZE
        siteLog.log(
            "feed",
            "${profile.name}: page $page served ${wallpapers.size} of ${pageItems.size} saved posts" +
                if (more) "" else ", no more pages (no Next button learned or hidden loading off)",
        )
        BooruPostRegistry.remember(wallpapers)
        return LoadResult.Page(
            data = wallpapers,
            prevKey = if (page > 1) page - 1 else null,
            nextKey = if (more) page + 1 else null,
        )
    }

    override fun getRefreshKey(state: PagingState<Int, Wallpaper>): Int? = null
}

private const val SITE_PAGE_SIZE = 30
private const val MAX_HIDDEN_ATTEMPTS = 3

/** Saved posts of the plain listing use the site id; each search gets its own key. */
private fun siteCacheKey(siteId: String, query: String): String =
    if (query.isEmpty()) siteId else "${siteId}_q${query.lowercase().hashCode().toUInt()}"
