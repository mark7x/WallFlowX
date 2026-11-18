package com.ammar.wallflow.data.offline

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** Lets composables reach the offline store without passing it through every screen. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface OfflineFavoritesEntryPoint {
    fun offlineFavoritesStore(): OfflineFavoritesStore
}

fun offlineFavoritesStore(context: Context): OfflineFavoritesStore =
    EntryPointAccessors.fromApplication(
        context.applicationContext,
        OfflineFavoritesEntryPoint::class.java,
    ).offlineFavoritesStore()
