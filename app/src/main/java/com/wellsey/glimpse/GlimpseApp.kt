package com.wellsey.glimpse

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.ImageDecoderDecoder
import com.wellsey.glimpse.data.ThumbFetcher
import com.wellsey.glimpse.data.ThumbKeyer
import org.osmdroid.config.Configuration
import java.io.File

class GlimpseApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        // OpenStreetMap asks every app to identify itself; tiles are cached in app storage.
        val osm = Configuration.getInstance()
        osm.setUserAgentValue(packageName)
        osm.setOsmdroidBasePath(File(cacheDir, "osmdroid"))
        osm.setOsmdroidTileCache(File(cacheDir, "osmdroid/tiles"))
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                add(ThumbFetcher.Factory())
                add(ThumbKeyer())
                add(ImageDecoderDecoder.Factory()) // animated GIF / WebP / HEIF
            }
            .crossfade(150)
            .build()
}
