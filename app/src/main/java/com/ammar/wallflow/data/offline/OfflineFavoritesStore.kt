package com.ammar.wallflow.data.offline

import android.content.Context
import com.ammar.wallflow.model.DownloadableWallpaper
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class DownloadSummary(
    val saved: Int,
    val skipped: Int,
    val failed: Int,
)

/**
 * Saves favorite wallpapers into the app's private storage (context.filesDir).
 * That folder is not visible to the gallery, Google Photos, or other apps, and it
 * isn't written to MediaStore. A `.nomedia` file is also added as a second guard.
 */
@Singleton
class OfflineFavoritesStore @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    fun directory(context: Context): File {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        val nomedia = File(dir, ".nomedia")
        if (!nomedia.exists()) nomedia.createNewFile()
        return dir
    }

    /** Returns the saved copy of [wallpaper] if it exists and isn't empty. */
    fun existingFile(context: Context, wallpaper: DownloadableWallpaper): File? {
        val file = File(directory(context), fileName(wallpaper))
        return file.takeIf { it.exists() && it.length() > 0 }
    }

    /** Number of saved copies and their total size in bytes. Ignores the .nomedia marker. */
    fun stats(context: Context): Pair<Int, Long> {
        val files = directory(context).listFiles()
            ?.filter { it.isFile && it.name != ".nomedia" }
            .orEmpty()
        return files.size to files.sumOf { it.length() }
    }

    /** Deletes every saved copy and returns how many files were removed. */
    fun deleteAll(context: Context): Int =
        directory(context).listFiles()
            ?.filter { it.isFile && it.name != ".nomedia" }
            ?.count { it.delete() }
            ?: 0

    /** Downloads every wallpaper not already saved. [onProgress] gets (done, total). */
    suspend fun downloadAll(
        context: Context,
        wallpapers: List<DownloadableWallpaper>,
        onProgress: (done: Int, total: Int) -> Unit,
    ): DownloadSummary = withContext(Dispatchers.IO) {
        val dir = directory(context)
        var saved = 0
        var skipped = 0
        var failed = 0
        wallpapers.forEachIndexed { index, wallpaper ->
            ensureActive()
            val target = File(dir, fileName(wallpaper))
            if (target.exists() && target.length() > 0) {
                skipped++
            } else if (download(wallpaper.data, target)) {
                saved++
            } else {
                failed++
            }
            onProgress(index + 1, wallpapers.size)
        }
        DownloadSummary(saved = saved, skipped = skipped, failed = failed)
    }

    private fun download(url: String, target: File): Boolean {
        val temp = File(target.parentFile, target.name + ".part")
        return try {
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                val body = response.body ?: return false
                temp.outputStream().use { out -> body.byteStream().copyTo(out) }
            }
            temp.renameTo(target)
        } catch (e: IOException) {
            temp.delete()
            false
        }
    }

    internal fun fileName(wallpaper: DownloadableWallpaper): String {
        val safeId = wallpaper.id.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val ext = wallpaper.mimeType
            ?.substringAfter('/')
            ?.takeIf { it.length in 2..4 && it.all { c -> c.isLetterOrDigit() } }
            ?: "jpg"
        return "${wallpaper.source.name.lowercase()}_$safeId.$ext"
    }

    companion object {
        private const val DIR_NAME = "offline_favorites"
    }
}
