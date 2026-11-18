package com.ammar.wallflow.sources

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject

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

class WallhavenSourceDescriptor @Inject constructor() : WallpaperSource {
    override val id = "wallhaven"
    override val displayName = "Wallhaven"
    override val capabilities = setOf(
        SourceCapability.SEARCH,
        SourceCapability.TAGS,
        SourceCapability.PURITY_FILTER,
        SourceCapability.LOGIN,
    )
}

class RedditSourceDescriptor @Inject constructor() : WallpaperSource {
    override val id = "reddit"
    override val displayName = "Reddit"
    override val capabilities = setOf(
        SourceCapability.SEARCH,
        SourceCapability.LOGIN,
    )
}
