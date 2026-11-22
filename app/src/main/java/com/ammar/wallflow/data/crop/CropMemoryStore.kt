package com.ammar.wallflow.data.crop

import android.content.Context
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** A crop region as fractions (0..1) of the image, so it fits any resolution. */
@Serializable
data class NormalizedCrop(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** Converts a crop rect in image pixels to fractions. Null when the image has no size yet. */
fun Rect.toNormalizedCrop(imageSize: Size): NormalizedCrop? {
    if (imageSize.width <= 0f || imageSize.height <= 0f) return null
    return NormalizedCrop(
        left = left / imageSize.width,
        top = top / imageSize.height,
        right = right / imageSize.width,
        bottom = bottom / imageSize.height,
    )
}

/** Converts fractions back to a crop rect in pixels of an image of [imageSize]. */
fun NormalizedCrop.toCropRect(imageSize: Size): Rect = Rect(
    left = left * imageSize.width,
    top = top * imageSize.height,
    right = right * imageSize.width,
    bottom = bottom * imageSize.height,
)

/** Remembers the crop chosen for each image, keyed by wallpaper ("SOURCE:id"). */
@Singleton
class CropMemoryStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val file = File(context.filesDir, "crop_memory.json")
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val mapSerializer = MapSerializer(String.serializer(), NormalizedCrop.serializer())

    suspend fun get(key: String): NormalizedCrop? = withContext(Dispatchers.IO) {
        mutex.withLock { read()[key] }
    }

    suspend fun put(key: String, crop: NormalizedCrop) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = read() + (key to crop)
            file.writeText(json.encodeToString(mapSerializer, updated))
        }
    }

    /** Every saved crop, for backups. */
    suspend fun exportAll(): Map<String, NormalizedCrop> = withContext(Dispatchers.IO) {
        mutex.withLock { read() }
    }

    /** Merges crops from a backup. Backup values win for the same image. */
    suspend fun importAll(entries: Map<String, NormalizedCrop>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            file.writeText(json.encodeToString(mapSerializer, read() + entries))
        }
    }

    private fun read(): Map<String, NormalizedCrop> = runCatching {
        if (file.exists()) json.decodeFromString(mapSerializer, file.readText()) else emptyMap()
    }.getOrDefault(emptyMap())
}
