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
        siteLog.log("feed", "${profile.name}: page $page, query '${search.query}'")
        // Unfiltered feed: pages come from the saved posts. When they run out, the hidden browser
        // clicks the learned Next button and saves more, the same way the learning page does.
        if (search.query.isBlank()) {
            val needed = page * SITE_PAGE_SIZE
            var scanned = siteRenderedCache.get(siteId)
            val canClick = profile.hiddenLoadMore && profile.nextPageSelector != null && profile.itemSelector != null
            if (page == 1 && scanned.isEmpty()) {
                // Nothing saved yet: read the first page over HTTP and save it, so later pages can follow on.
                val first = siteGrabber.search(profile, "", 1).getOrNull().orEmpty()
                siteLog.log("feed", "${profile.name}: nothing saved, first page over HTTP gave ${first.size} posts")
                if (first.isNotEmpty()) siteRenderedCache.add(siteId, first)
                scanned = siteRenderedCache.get(siteId)
            }
            var attempts = 0
            while (canClick && scanned.size < needed && attempts < MAX_HIDDEN_ATTEMPTS) {
                attempts++
                siteLog.log("feed", "${profile.name}: page $page needs $needed posts, ${scanned.size} saved, hidden load #$attempts")
                val added = siteHiddenBrowser.loadNextPage(profile)
                scanned = siteRenderedCache.get(siteId)
                if (added <= 0) break
            }
            val offset = (page - 1) * SITE_PAGE_SIZE
            if (scanned.size > offset) {
                val allowed = search.filters.allowedPurities()
                val pageItems = scanned.drop(offset).take(SITE_PAGE_SIZE)
                val wallpapers: List<Wallpaper> = pageItems.mapNotNull { item ->
                    val postUrl = item.postUrl ?: return@mapNotNull null
                    val thumb = item.thumbUrl ?: return@mapNotNull null
                    siteWallpaper(siteId, postUrl, null, thumb)
                }.filter { it.purity in allowed }.filter { seenIds.add(it.id) }
                val more = canClick || scanned.size > offset + SITE_PAGE_SIZE
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
            if (page > 1) {
                siteLog.log(
                    "feed",
                    "${profile.name}: page $page stopping, ${scanned.size} saved posts and " +
                        if (canClick) "the hidden browser added none (see [hidden] lines)" else "no way to load more",
                )
                return LoadResult.Page(data = emptyList(), prevKey = page - 1, nextKey = null)
            }
        }
        // Without a learned Next button or a {page} address, page 2 would just reload page 1. Stop instead.
        if (page > 1 && profile.nextPageSelector == null && !profile.searchUrl.contains("{page}")) {
            siteLog.log("feed", "${profile.name}: page $page needs a learned Next button, none learned, stopping")
            return LoadResult.Page(data = emptyList(), prevKey = page - 1, nextKey = null)
        }
        val result = siteGrabber.search(profile, search.query, page)
        // Sites that build their list with JavaScript return nothing over plain HTTP.
        // Show what the learning center has scanned for them, on the first page of an unfiltered feed.
        val fromScan = page == 1 && search.query.isBlank()
        val items = result.getOrNull().orEmpty().ifEmpty {
            if (fromScan) siteRenderedCache.get(siteId) else emptyList()
        }
        if (result.isFailure && items.isEmpty()) {
            siteLog.log("feed", "${profile.name}: page $page failed, stopping")
            return LoadResult.Error(result.exceptionOrNull() ?: IllegalStateException("Site search failed."))
        }
        val allowed = search.filters.allowedPurities()
        val wallpapers: List<Wallpaper> = items.mapNotNull { item ->
            val postUrl = item.postUrl ?: return@mapNotNull null
            val thumb = item.thumbUrl ?: return@mapNotNull null
            siteWallpaper(siteId, postUrl, null, thumb)
        }.filter { it.purity in allowed }.filter { seenIds.add(it.id) }
        siteLog.log(
            "feed",
            "${profile.name}: page $page gave ${items.size} items, ${wallpapers.size} new to the list" +
                if (wallpapers.isEmpty()) ", next page will not be asked" else "",
        )
        BooruPostRegistry.remember(wallpapers)
        return LoadResult.Page(
            data = wallpapers,
            prevKey = if (page > 1) page - 1 else null,
            // Stop when a page adds nothing new, otherwise Paging keeps asking for pages forever.
            nextKey = if (items.isEmpty() || wallpapers.isEmpty()) null else page + 1,
        )
    }

    override fun getRefreshKey(state: PagingState<Int, Wallpaper>): Int? = null
}

private const val SITE_PAGE_SIZE = 30
private const val MAX_HIDDEN_ATTEMPTS = 3
