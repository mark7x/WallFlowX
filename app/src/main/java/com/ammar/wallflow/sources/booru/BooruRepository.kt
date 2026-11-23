package com.ammar.wallflow.sources.booru

import com.ammar.wallflow.sites.SiteGrabber
import com.ammar.wallflow.sites.SiteProfileStore
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
) {
    fun wallpapersPager(search: BooruSearch): Flow<PagingData<Wallpaper>> = Pager(
        config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
        pagingSourceFactory = { BooruPagingSource(booruPostsClient, siteProfileStore, siteGrabber, search) },
    ).flow

    companion object {
        const val PAGE_SIZE = 30
    }
}

private class BooruPagingSource(
    private val booruPostsClient: BooruPostsClient,
    private val siteProfileStore: SiteProfileStore,
    private val siteGrabber: SiteGrabber,
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
        val items = siteGrabber.search(profile, search.query, page).getOrElse {
            return LoadResult.Error(it)
        }
        val allowed = search.filters.allowedPurities()
        val wallpapers: List<Wallpaper> = items.mapNotNull { item ->
            val postUrl = item.postUrl ?: return@mapNotNull null
            val fullUrl = siteGrabber.fullImageUrl(profile, postUrl) ?: return@mapNotNull null
            siteWallpaper(siteId, postUrl, fullUrl, item.thumbUrl)
        }.filter { it.purity in allowed }.filter { seenIds.add(it.id) }
        BooruPostRegistry.remember(wallpapers)
        return LoadResult.Page(
            data = wallpapers,
            prevKey = if (page > 1) page - 1 else null,
            nextKey = if (items.isEmpty()) null else page + 1,
        )
    }

    override fun getRefreshKey(state: PagingState<Int, Wallpaper>): Int? = null
}
