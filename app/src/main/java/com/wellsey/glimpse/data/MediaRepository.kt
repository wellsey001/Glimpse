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
