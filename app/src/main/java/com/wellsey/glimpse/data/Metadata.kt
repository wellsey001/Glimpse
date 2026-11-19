package com.wellsey.glimpse.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.wellsey.glimpse.util.formatDateTime
import com.wellsey.glimpse.util.formatDuration
import com.wellsey.glimpse.util.formatSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** Opens EXIF for a photo. Asks for the un-redacted original so GPS data is included. */
private fun openExif(resolver: ContentResolver, uri: Uri): ExifInterface? {
    val candidates = listOf(
        runCatching { MediaStore.setRequireOriginal(uri) }.getOrNull(),
        uri,
    ).filterNotNull()
    for (candidate in candidates) {
        try {
            resolver.openFileDescriptor(candidate, "r")?.use { pfd ->
                return ExifInterface(pfd.fileDescriptor)
            }
        } catch (e: Exception) {
            // SecurityException without ACCESS_MEDIA_LOCATION, or unsupported format — try next.
        }
    }
    return null
}

private fun ExifInterface.geo(): Geo? {
    val ll = latLong ?: return null
    if (ll[0] == 0.0 && ll[1] == 0.0) return null
    return Geo(ll[0], ll[1])
}

data class MediaDetails(val rows: List<Pair<String, String>>, val geo: Geo?)

suspend fun readDetails(context: Context, item: MediaItem): MediaDetails = withContext(Dispatchers.IO) {
    val rows = mutableListOf<Pair<String, String>>()
    rows += "Name" to item.name
    if (!item.isExternal) rows += "Date" to formatDateTime(item.dateTaken)
    if (item.size > 0) rows += "Size" to formatSize(item.size)
    if (item.width > 0 && item.height > 0) {
        val mp = item.width.toLong() * item.height / 1_000_000.0
        val mpText = if (!item.isVideo && mp >= 0.1) "  ·  ${"%.1f".format(mp)} MP" else ""
        rows += "Resolution" to "${item.width} × ${item.height}$mpText"
    }
    if (item.isVideo && item.durationMs > 0) rows += "Length" to formatDuration(item.durationMs)
    rows += "Type" to item.mimeType
    if (item.relativePath.isNotBlank()) rows += "Folder" to item.relativePath.trimEnd('/')

    var geo: Geo? = null
    if (!item.isVideo) {
        openExif(context.contentResolver, item.uri)?.let { exif ->
            val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim().orEmpty()
            val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim().orEmpty()
            val camera = if (model.startsWith(make, ignoreCase = true)) model else "$make $model".trim()
            if (camera.isNotBlank()) rows += "Camera" to camera

            val parts = mutableListOf<String>()
            exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0).takeIf { it > 0 }
                ?.let { parts += "f/${"%.1f".format(it)}" }
            exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0).takeIf { it > 0 }
                ?.let { parts += if (it < 1) "1/${(1 / it).roundToInt()}s" else "${"%.1f".format(it)}s" }
            exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0).takeIf { it > 0 }
                ?.let { parts += "ISO $it" }
            exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0).takeIf { it > 0 }
                ?.let { parts += "${"%.1f".format(it)}mm" }
            if (parts.isNotEmpty()) rows += "Exposure" to parts.joinToString("  ·  ")

            geo = exif.geo()
            geo?.let { rows += "Location" to "%.5f, %.5f".format(it.lat, it.lon) }
        }
    }
    MediaDetails(rows, geo)
}

/**
 * Finds GPS coordinates for photos, for the map. Reading EXIF is slow, so results are
 * cached on disk (keyed by id + modified date) and only new or changed photos are scanned.
 */
class LocationIndex(context: Context) {
    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, "geo_index.txt")
    private val cache = HashMap<Long, Pair<Long, Geo?>>()
    private var loaded = false
    private val mutex = Mutex()

    suspend fun index(
        items: List<MediaItem>,
        onProgress: (done: Int, total: Int) -> Unit,
    ): Map<Long, Geo> = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (!loaded) {
                load()
                loaded = true
            }
            val photos = items.filter { !it.isVideo }
            val todo = photos.filter { cache[it.id]?.first != it.dateModified }
            todo.forEachIndexed { i, item ->
                val geo = openExif(appContext.contentResolver, item.uri)?.geo()
                cache[item.id] = item.dateModified to geo
                if (i % 50 == 0) onProgress(i, todo.size)
                if (i > 0 && i % 500 == 0) save()
            }
            if (todo.isNotEmpty()) save()
            onProgress(todo.size, todo.size)
            val result = HashMap<Long, Geo>()
            for (p in photos) cache[p.id]?.second?.let { result[p.id] = it }
            result
        }
    }

    private fun load() {
        if (!file.exists()) return
        runCatching {
            file.forEachLine { line ->
                val parts = line.split(',')
                if (parts.size == 4) {
                    val id = parts[0].toLongOrNull() ?: return@forEachLine
                    val mod = parts[1].toLongOrNull() ?: return@forEachLine
                    val lat = parts[2].toDoubleOrNull()
                    val lon = parts[3].toDoubleOrNull()
                    cache[id] = mod to if (lat != null && lon != null) Geo(lat, lon) else null
                }
            }
        }
    }

    private fun save() {
        runCatching {
            file.bufferedWriter().use { w ->
                for ((id, entry) in cache) {
                    val geo = entry.second
                    w.write("$id,${entry.first},${geo?.lat ?: "-"},${geo?.lon ?: "-"}\n")
                }
            }
        }
    }
}
