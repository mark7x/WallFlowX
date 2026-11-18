package com.ammar.wallflow.sources

import com.ammar.wallflow.data.repository.wallhaven.WallhavenRepository
import com.ammar.wallflow.model.search.Search
import com.ammar.wallflow.model.search.WallhavenSearch
import androidx.paging.PagingData
import com.ammar.wallflow.model.Wallpaper
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

@Module
@InstallIn(SingletonComponent::class)
abstract class SourceModule {
    @Binds
    @IntoSet
    abstract fun bindWallhaven(source: WallhavenSourceDescriptor): WallpaperSource

    @Binds
    @IntoSet
    abstract fun bindReddit(source: RedditSourceDescriptor): WallpaperSource
}

class WallhavenSourceDescriptor @Inject constructor(
    private val wallhavenRepository: WallhavenRepository,
) : WallpaperSource {
    override val id = "wallhaven"
    override val displayName = "Wallhaven"
    override val capabilities = setOf(
        SourceCapability.SEARCH,
        SourceCapability.TAGS,
        SourceCapability.PURITY_FILTER,
        SourceCapability.LOGIN,
    )

    override fun wallpapersPager(search: Search): Flow<PagingData<Wallpaper>>? =
        (search as? WallhavenSearch)?.let { wallhavenRepository.wallpapersPager(it) }
}

class RedditSourceDescriptor @Inject constructor() : WallpaperSource {
    override val id = "reddit"
    override val displayName = "Reddit"
    override val capabilities = setOf(
        SourceCapability.SEARCH,
        SourceCapability.LOGIN,
    )
}
