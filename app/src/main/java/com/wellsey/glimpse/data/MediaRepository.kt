package com.wellsey.glimpse.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.database.ContentObserver
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.media.MediaScannerConnection
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/**
 * Everything that talks to MediaStore: reading the library, and the write operations
 * (trash, restore, delete, move, copy, save edited copies).
 *
 * Changing files that another app created needs the user's consent on Android 11+,
 * so trash/restore/delete/move return an [IntentSender] for the system confirm dialog.
 */
class MediaRepository(context: Context) {

    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver
    private val filesUri: Uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)

    private val projection = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.MEDIA_TYPE,
        MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.MIME_TYPE,
        MediaStore.MediaColumns.DATE_TAKEN,
        MediaStore.MediaColumns.DATE_ADDED,
        MediaStore.MediaColumns.DATE_MODIFIED,
        MediaStore.MediaColumns.SIZE,
        MediaStore.MediaColumns.WIDTH,
        MediaStore.MediaColumns.HEIGHT,
        MediaStore.MediaColumns.ORIENTATION,
        MediaStore.MediaColumns.DURATION,
        MediaStore.MediaColumns.BUCKET_ID,
        MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
        MediaStore.MediaColumns.RELATIVE_PATH,
        MediaStore.MediaColumns.DATE_EXPIRES,
    )

    /** Loads every photo and video, newest first. Pass [trashed] = true to load the trash instead. */
    suspend fun loadMedia(trashed: Boolean = false): List<MediaItem> = withContext(Dispatchers.IO) {
        val args = Bundle().apply {
            putString(
                ContentResolver.QUERY_ARG_SQL_SELECTION,
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)"
            )
            putStringArray(
                ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                arrayOf(
                    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
                )
            )
            if (trashed) putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
        }
        val result = ArrayList<MediaItem>()
        try {
            resolver.query(filesUri, projection, args, null)?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val typeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val takenCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
                val addedCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val modCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val wCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
                val hCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
                val oriCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.ORIENTATION)
                val durCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
                val bucketCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
                val bucketNameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val pathCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                val expCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_EXPIRES)

                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val isVideo = c.getInt(typeCol) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                    val taken = c.getLong(takenCol)
                    val added = c.getLong(addedCol) * 1000L
                    val rotated = c.getInt(oriCol).let { it == 90 || it == 270 }
                    val w = c.getInt(wCol)
                    val h = c.getInt(hCol)
                    result += MediaItem(
                        id = id,
                        uri = contentUri(id, isVideo),
                        name = c.getString(nameCol) ?: "",
                        mimeType = c.getString(mimeCol) ?: if (isVideo) "video/*" else "image/*",
                        dateTaken = if (taken > 0) taken else added,
                        dateAdded = added,
                        dateModified = c.getLong(modCol),
                        size = c.getLong(sizeCol),
                        width = if (rotated) h else w,
                        height = if (rotated) w else h,
                        durationMs = c.getLong(durCol),
                        bucketId = c.getLong(bucketCol),
                        bucketName = c.getString(bucketNameCol) ?: "",
                        relativePath = c.getString(pathCol) ?: "",
                        isVideo = isVideo,
                        dateExpires = c.getLong(expCol),
                    )
                }
            }
        } catch (e: SecurityException) {
            // Permission revoked while running — return what we have (nothing).
        }
        result.sortByDescending { it.dateTaken }
        result
    }

    /** Emits whenever anything in the media library changes. */
    fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(filesUri, true, observer)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }

    fun trashRequest(uris: List<Uri>, trash: Boolean): IntentSender =
        MediaStore.createTrashRequest(resolver, uris, trash).intentSender

    fun deleteRequest(uris: List<Uri>): IntentSender =
        MediaStore.createDeleteRequest(resolver, uris).intentSender

    fun writeRequest(uris: List<Uri>): IntentSender =
        MediaStore.createWriteRequest(resolver, uris).intentSender

    /** Moves items to another folder by changing their RELATIVE_PATH. Returns how many moved. */
    suspend fun move(items: List<MediaItem>, relativePath: String): Int = withContext(Dispatchers.IO) {
        var moved = 0
        for (item in items) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                }
                if (resolver.update(item.uri, values, null, null) > 0) moved++
            } catch (e: Exception) {
                // Not allowed for this item (e.g. a video into an image-only folder). Skip it.
            }
        }
        moved
    }

    /** Copies items into another folder. Returns how many were copied. */
    suspend fun copy(items: List<MediaItem>, relativePath: String): Int = withContext(Dispatchers.IO) {
        var copied = 0
        for (item in items) {
            val target = if (item.isVideo) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, item.name)
                put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.DATE_TAKEN, item.dateTaken)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            var newUri: Uri? = null
            try {
                val created = resolver.insert(target, values) ?: continue
                newUri = created
                resolver.openInputStream(item.uri)?.use { input ->
                    resolver.openOutputStream(created)?.use { output -> input.copyTo(output) }
                } ?: throw IllegalStateException("Could not open source")
                resolver.update(created, ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }, null, null)
                copied++
            } catch (e: Exception) {
                newUri?.let { runCatching { resolver.delete(it, null, null) } }
            }
        }
        copied
    }

    /** Saves an edited photo as a new JPEG. Returns the new item's Uri, or null on failure. */
    suspend fun saveJpeg(
        bitmap: Bitmap,
        displayName: String,
        relativePath: String,
        dateTaken: Long,
    ): Uri? = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.DATE_TAKEN, dateTaken)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        var uri: Uri? = null
        try {
            val created = resolver.insert(collection, values) ?: return@withContext null
            uri = created
            resolver.openOutputStream(created)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            } ?: throw IllegalStateException("Could not open output")
            resolver.update(created, ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }, null, null)
            created
        } catch (e: Exception) {
            uri?.let { runCatching { resolver.delete(it, null, null) } }
            null
        }
    }

    // ------------------------------------------------------------ saving over the original

    /** Photo details worth keeping when a photo is re-saved (date, camera, location…). */
    private val keptExifTags = listOf(
        ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_OFFSET_TIME, ExifInterface.TAG_OFFSET_TIME_ORIGINAL, ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
        ExifInterface.TAG_SUBSEC_TIME_ORIGINAL, ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL,
        ExifInterface.TAG_LENS_MAKE, ExifInterface.TAG_LENS_MODEL, ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, ExifInterface.TAG_FLASH, ExifInterface.TAG_WHITE_BALANCE,
        ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF, ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF, ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP, ExifInterface.TAG_IMAGE_DESCRIPTION,
        ExifInterface.TAG_ARTIST, ExifInterface.TAG_COPYRIGHT,
    )

    /** Reads the photo details from [uri], including location when Android allows it. */
    fun readExif(uri: Uri): Map<String, String> {
        val candidates = listOfNotNull(runCatching { MediaStore.setRequireOriginal(uri) }.getOrNull(), uri)
        for (c in candidates) {
            try {
                resolver.openFileDescriptor(c, "r")?.use { pfd ->
                    val exif = ExifInterface(pfd.fileDescriptor)
                    return keptExifTags.mapNotNull { tag -> exif.getAttribute(tag)?.let { tag to it } }.toMap()
                }
            } catch (e: Exception) {
                // try the next way of opening it
            }
        }
        return emptyMap()
    }

    /** Writes [values] into a JPEG, with the picture now the right way up and the new size. */
    private fun writeExif(uri: Uri, values: Map<String, String>, width: Int, height: Int) {
        runCatching {
            resolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)
                values.forEach { (k, v) -> exif.setAttribute(k, v) }
                exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                exif.setAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION, width.toString())
                exif.setAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION, height.toString())
                exif.saveAttributes()
            }
        }
    }

    /** Copies the original's details (date, camera, location) onto a saved copy. */
    suspend fun copyExif(from: Uri, to: Uri, width: Int, height: Int) = withContext(Dispatchers.IO) {
        val values = readExif(from)
        if (values.isNotEmpty()) writeExif(to, values, width, height)
    }

    /**
     * Replaces the original photo with [bitmap], in the same format, keeping its date,
     * camera details and location. The caller must already have write permission.
     */
    suspend fun overwrite(item: MediaItem, bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
        val format = when (item.mimeType) {
            "image/png" -> Bitmap.CompressFormat.PNG
            "image/webp" -> Bitmap.CompressFormat.WEBP_LOSSY
            else -> Bitmap.CompressFormat.JPEG
        }
        val exif = if (format == Bitmap.CompressFormat.JPEG) readExif(item.uri) else emptyMap()
        // Encode fully in memory first, so a failure can never leave a half-written photo.
        val bytes = java.io.ByteArrayOutputStream().use { out ->
            if (!bitmap.compress(format, 95, out)) return@withContext false
            out.toByteArray()
        }
        try {
            resolver.openOutputStream(item.uri, "wt")?.use { it.write(bytes) } ?: return@withContext false
        } catch (e: Exception) {
            return@withContext false
        }
        if (format == Bitmap.CompressFormat.JPEG) writeExif(item.uri, exif, bitmap.width, bitmap.height)
        true
    }

    /**
     * Asks Android to re-read a changed file now, so its size, date and thumbnail update
     * in Glimpse and in every other app. Waits up to a few seconds for it to finish.
     */
    suspend fun rescan(uri: Uri) {
        val path = withContext(Dispatchers.IO) {
            runCatching {
                @Suppress("DEPRECATION")
                resolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }.getOrNull()
        } ?: return
        withTimeoutOrNull(5000) {
            suspendCancellableCoroutine { cont ->
                MediaScannerConnection.scanFile(appContext, arrayOf(path), null) { _, _ ->
                    if (cont.isActive) cont.resume(Unit)
                }
            }
        }
    }

    // ------------------------------------------------------------ small actions

    /** Renames a photo, keeping its extension. The caller must already have write permission. */
    suspend fun rename(item: MediaItem, newBaseName: String): Boolean = withContext(Dispatchers.IO) {
        val ext = item.name.substringAfterLast('.', "")
        val name = if (ext.isBlank()) newBaseName else "$newBaseName.$ext"
        runCatching {
            resolver.update(item.uri, ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, name) }, null, null) > 0
        }.getOrDefault(false)
    }

    /** Sets a photo as the wallpaper. [which] is WallpaperManager.FLAG_SYSTEM, FLAG_LOCK, or both. */
    suspend fun setWallpaper(item: MediaItem, which: Int): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val wm = android.app.WallpaperManager.getInstance(appContext)
            resolver.openInputStream(item.uri)?.use { wm.setStream(it, null, true, which) } != null
        }.getOrDefault(false)
    }

    /**
     * Makes a shareable copy for the clipboard. Other apps can only paste from a link Glimpse
     * can hand out, so the photo is copied into Glimpse's own space first.
     */
    suspend fun clipboardCopy(item: MediaItem): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = java.io.File(appContext.cacheDir, "clip").apply { deleteRecursively(); mkdirs() }
            val f = java.io.File(dir, item.name.ifBlank { "photo.jpg" })
            resolver.openInputStream(item.uri)?.use { input -> f.outputStream().use { input.copyTo(it) } }
                ?: return@runCatching null
            androidx.core.content.FileProvider.getUriForFile(appContext, "${appContext.packageName}.files", f)
        }.getOrNull()
    }

    /** Builds a [MediaItem] for a file another app asked us to open. */
    fun externalItem(uri: Uri, mimeHint: String?): MediaItem {
        var name = uri.lastPathSegment ?: "File"
        var size = 0L
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val s = c.getColumnIndex(OpenableColumns.SIZE)
                        if (n >= 0) c.getString(n)?.let { name = it }
                        if (s >= 0) size = c.getLong(s)
                    }
                }
        }
        val mime = mimeHint ?: resolver.getType(uri) ?: "image/*"
        return MediaItem(
            id = -1L,
            uri = uri,
            name = name,
            mimeType = mime,
            dateTaken = System.currentTimeMillis(),
            dateModified = 0,
            size = size,
            width = 0,
            height = 0,
            durationMs = 0,
            bucketId = 0,
            bucketName = "",
            relativePath = "",
            isVideo = mime.startsWith("video"),
            isExternal = true,
        )
    }

    private fun contentUri(id: Long, isVideo: Boolean): Uri {
        val base = if (isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        }
        return ContentUris.withAppendedId(base, id)
    }
}
