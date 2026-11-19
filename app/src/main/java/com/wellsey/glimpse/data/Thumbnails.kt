package com.wellsey.glimpse.data

import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.key.Keyer
import coil.request.Options

/**
 * Model for grid thumbnails. Uses the system's own thumbnail cache
 * (ContentResolver.loadThumbnail), which is fast and works for both photos and videos.
 */
data class MediaThumb(val uri: Uri, val dateModified: Long, val size: Int = 384)

class ThumbFetcher(
    private val data: MediaThumb,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val bitmap = options.context.contentResolver.loadThumbnail(
            data.uri, Size(data.size, data.size), null
        )
        return DrawableResult(
            drawable = BitmapDrawable(options.context.resources, bitmap),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<MediaThumb> {
        override fun create(data: MediaThumb, options: Options, imageLoader: ImageLoader): Fetcher =
            ThumbFetcher(data, options)
    }
}

class ThumbKeyer : Keyer<MediaThumb> {
    override fun key(data: MediaThumb, options: Options): String =
        "thumb:${data.uri}#${data.dateModified}#${data.size}"
}
