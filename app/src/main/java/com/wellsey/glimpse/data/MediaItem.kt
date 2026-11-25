package com.wellsey.glimpse.data

import android.net.Uri

/** One photo or video, as MediaStore describes it. */
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val mimeType: String,
    /** Milliseconds since epoch. Falls back to the date added when the file has no capture date. */
    val dateTaken: Long,
    /** Seconds since epoch. Used to invalidate cached thumbnails when a file changes. */
    val dateModified: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val bucketId: Long,
    val bucketName: String,
    val relativePath: String,
    val isVideo: Boolean,
    /** Seconds since epoch when a trashed item will be deleted for good (0 if not trashed). */
    val dateExpires: Long = 0,
    /** True when the file came from another app (opened via "Open with") rather than MediaStore. */
    val isExternal: Boolean = false,
    /** Milliseconds since epoch when the file arrived on the phone (used by "Recently added"). */
    val dateAdded: Long = 0,
)

data class Album(
    val bucketId: Long,
    val name: String,
    val relativePath: String,
    val count: Int,
    val cover: MediaItem,
)

data class Geo(val lat: Double, val lon: Double)
