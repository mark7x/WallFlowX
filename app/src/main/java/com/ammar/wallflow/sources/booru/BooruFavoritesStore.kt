package com.ammar.wallflow.sources.booru

import androidx.compose.ui.unit.IntSize
import com.ammar.wallflow.model.Purity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import android.content.Context

@Serializable
data class StoredBooruPost(
    val fileUrl: String,
    val previewUrl: String?,
    val boardUrl: String,
    val purity: String,
)

/**
 * Keeps favorited booru posts in app storage. The favorites table stores only IDs, so the
 * URLs needed to show or download a booru post are saved here.
 */
@Singleton
class BooruFavoritesStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val file = File(context.filesDir, FILE_NAME)
    private val posts: MutableMap<String, StoredBooruPost> = load().toMutableMap()

    @Synchronized
    fun put(wallpaper: BooruWallpaper) {
        posts[wallpaper.id] = StoredBooruPost(
            fileUrl = wallpaper.data,
            previewUrl = wallpaper.thumbData,
            boardUrl = wallpaper.boardUrl,
            purity = wallpaper.purity.purityName,
        )
        save()
    }

    @Synchronized
    fun get(id: String): BooruWallpaper? = posts[id]?.let {
        BooruWallpaper(
            id = id,
            data = it.fileUrl,
            thumbData = it.previewUrl,
            resolution = IntSize.Zero,
            purity = Purity.fromName(it.purity),
            boardUrl = it.boardUrl,
        )
    }

    /** Every saved post, for backups. */
    @Synchronized
    fun exportAll(): Map<String, StoredBooruPost> = posts.toMap()

    /** Puts posts from a backup back. Existing posts with the same id are replaced. */
    @Synchronized
    fun importAll(entries: Map<String, StoredBooruPost>) {
        posts.putAll(entries)
        save()
    }

    private fun save() {
        file.writeText(Json.encodeToString(MapSerializer(String.serializer(), StoredBooruPost.serializer()), posts))
    }

    private fun load(): Map<String, StoredBooruPost> = try {
        if (file.exists()) {
            Json.decodeFromString(MapSerializer(String.serializer(), StoredBooruPost.serializer()), file.readText())
        } else {
            emptyMap()
        }
    } catch (e: Exception) {
        emptyMap()
    }

    companion object {
        private const val FILE_NAME = "booru_favorites.json"
    }
}
