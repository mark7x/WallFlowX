package com.ammar.wallflow.data.offline

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.graphics.BitmapFactory
import androidx.compose.ui.unit.IntSize
import com.ammar.wallflow.model.DownloadableWallpaper
import com.ammar.wallflow.model.Source
import com.ammar.wallflow.model.local.LocalWallpaper
import com.ammar.wallflow.sources.booru.BOORU_USER_AGENT
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
private const val SIDECAR_EXT = ".src"
private const val RATE_LIMIT_PAUSE_MS = 30_000L

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

    /** Every saved copy, newest first. Ignores the .nomedia marker. */
    fun files(context: Context): List<File> =
        directory(context).listFiles()
            ?.filter { it.isFile && it.name != ".nomedia" && !it.name.endsWith(SIDECAR_EXT) }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

    /** Saves one wallpaper for offline use. Returns true when the copy exists afterwards. */
    suspend fun saveOne(context: Context, wallpaper: DownloadableWallpaper): Boolean =
        withContext(Dispatchers.IO) {
            val target = File(directory(context), fileName(wallpaper))
            if (target.exists() && target.length() > 0) return@withContext true
            val ok = download(wallpaper.data, target)
            if (ok) writeSource(target, wallpaper.source, wallpaper.id)
            ok
        }

    /** Removes one offline copy. Returns true when a file was deleted. */
    fun removeOne(context: Context, wallpaper: DownloadableWallpaper): Boolean {
        val file = existingFile(context, wallpaper) ?: return false
        sidecarOf(file).delete()
        return file.delete()
    }

    /** Deletes one copy and its source record. */
    fun deleteCopy(file: File) {
        sidecarOf(file).delete()
        file.delete()
    }

    /** Records which favorite an offline copy belongs to, so ratings can follow it. */
    fun writeSource(target: File, source: Source, id: String) {
        sidecarOf(target).writeText("${source.name}\n$id")
    }

    /** The favorite an offline copy belongs to, or null when it was saved before this was tracked. */
    fun originalOf(file: File): Pair<Source, String>? {
        val lines = sidecarOf(file).takeIf { it.exists() }?.readLines() ?: return null
        if (lines.size < 2) return null
        val source = runCatching { Source.valueOf(lines[0]) }.getOrNull() ?: return null
        return source to lines[1]
    }

    /** Removes the offline copy of a favorite, when one exists. */
    fun removeByOriginal(context: Context, source: Source, id: String): Boolean {
        val copies = directory(context).listFiles().orEmpty()
            .filter { it.isFile && !it.name.endsWith(SIDECAR_EXT) && originalOf(it) == (source to id) }
        copies.forEach { deleteCopy(it) }
        return copies.isNotEmpty()
    }

    private fun sidecarOf(file: File) = File(file.parentFile, file.name + SIDECAR_EXT)

    /** Deletes every saved copy and returns how many files were removed. */
    fun deleteAll(context: Context): Int =
        directory(context).listFiles()
            ?.filter { it.isFile && it.name != ".nomedia" }
            ?.count { it.delete() }
            ?: 0

    /**
     * Copies every saved file into the folder the user picked with the system folder picker.
     * Files exported this way are visible to gallery apps, so the screen warns first.
     * Names already in that folder are skipped. Returns how many files were copied.
     */
    suspend fun exportTo(context: Context, treeUri: Uri): Int = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        val existing = existingNames(context, treeUri)
        val files = directory(context).listFiles()
            ?.filter { it.isFile && it.name != ".nomedia" && it.extension != "part" }
            .orEmpty()
        var copied = 0
        for (file in files) {
            if (file.name in existing) continue
            val target = DocumentsContract.createDocument(
                resolver,
                parentUri,
                mimeFor(file),
                file.name,
            ) ?: continue
            resolver.openOutputStream(target)?.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
            }
            copied++
        }
        copied
    }

    private fun existingNames(context: Context, treeUri: Uri): Set<String> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        val names = mutableSetOf<String>()
        context.contentResolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                names += cursor.getString(0) ?: continue
            }
        }
        return names
    }

    private fun mimeFor(file: File) = when (file.extension.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "application/octet-stream"
    }

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
        val pauseMs = OfflineDownloadDelay.get(context)
        wallpapers.forEachIndexed { index, wallpaper ->
            ensureActive()
            val target = File(dir, fileName(wallpaper))
            if (target.exists() && target.length() > 0) {
                skipped++
            } else {
                if (download(wallpaper.data, target)) {
                    saved++
                    writeSource(target, wallpaper.source, wallpaper.id)
                } else {
                    failed++
                }
                // Pause after each real download, so sites don't block the burst.
                if (index < wallpapers.lastIndex) delay(pauseMs)
            }
            onProgress(index + 1, wallpapers.size)
        }
        DownloadSummary(saved = saved, skipped = skipped, failed = failed)
    }

    private suspend fun download(url: String, target: File): Boolean {
        // One retry after a rate-limit response, following a longer pause.
        repeat(2) { attempt ->
            val outcome = attemptDownload(url, target)
            if (outcome != HttpOutcome.RATE_LIMITED) return outcome == HttpOutcome.OK
            if (attempt == 0) delay(RATE_LIMIT_PAUSE_MS)
        }
        return false
    }

    private enum class HttpOutcome { OK, FAILED, RATE_LIMITED }

    private fun attemptDownload(url: String, target: File): HttpOutcome {
        val temp = File(target.parentFile, target.name + ".part")
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", BOORU_USER_AGENT)
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.code == 429) return HttpOutcome.RATE_LIMITED
                if (!response.isSuccessful) return HttpOutcome.FAILED
                val body = response.body ?: return HttpOutcome.FAILED
                temp.outputStream().use { out -> body.byteStream().copyTo(out) }
            }
            if (temp.renameTo(target)) HttpOutcome.OK else HttpOutcome.FAILED
        } catch (e: IOException) {
            temp.delete()
            HttpOutcome.FAILED
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

/**
 * Shows an offline copy in the Local and Offline tabs. Reads the image size from the file
 * directly, so no document lookup is needed.
 */
fun File.toOfflineLocalWallpaper(): LocalWallpaper {
    val uri = android.net.Uri.fromFile(this)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(absolutePath, bounds)
    return LocalWallpaper(
        source = Source.LOCAL,
        id = uri.toString(),
        data = uri,
        fileSize = length(),
        name = name,
        resolution = IntSize(bounds.outWidth.coerceAtLeast(0), bounds.outHeight.coerceAtLeast(0)),
        mimeType = when (extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> null
        },
    )
}
