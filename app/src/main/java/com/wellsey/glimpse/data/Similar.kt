package com.wellsey.glimpse.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/** A set of near-identical photos (bursts, retakes), with the sharpest one marked as best. */
data class SimilarGroup(val items: List<MediaItem>, val best: MediaItem)

/**
 * Finds photos that look almost the same and were taken close together.
 *
 * Each photo gets a tiny "fingerprint" of its shapes and light (a difference hash) plus a
 * sharpness score, both worked out from Android's small thumbnail and cached on disk.
 * Photos taken within two minutes of each other with nearly matching fingerprints are grouped.
 */
class SimilarFinder(context: Context) {
    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, "similar_index.txt")
    private val cache = HashMap<Long, Triple<Long, Long, Float>>() // id -> (modified, hash, sharpness)
    private var loaded = false
    private val mutex = Mutex()

    suspend fun find(
        media: List<MediaItem>,
        onProgress: (done: Int, total: Int) -> Unit,
    ): List<SimilarGroup> = mutex.withLock {
        withContext(Dispatchers.IO) {
            load()
            val photos = media.filter { !it.isVideo }.sortedBy { it.dateTaken }
            val todo = photos.filter { cache[it.id]?.first != it.dateModified }
            todo.forEachIndexed { i, item ->
                coroutineContext.ensureActive()
                fingerprint(item)?.let { (h, s) -> cache[item.id] = Triple(item.dateModified, h, s) }
                if (i % 25 == 0) onProgress(i, todo.size)
                if (i > 0 && i % 400 == 0) save()
            }
            if (todo.isNotEmpty()) save()
            onProgress(todo.size, todo.size)

            val groups = mutableListOf<List<MediaItem>>()
            var current = mutableListOf<MediaItem>()
            for (p in photos) {
                val last = current.lastOrNull()
                val similar = last != null &&
                    p.dateTaken - last.dateTaken <= WINDOW_MS &&
                    distance(cache[p.id]?.second, cache[last.id]?.second) <= MAX_DISTANCE
                if (similar) {
                    current.add(p)
                } else {
                    if (current.size > 1) groups += current
                    current = mutableListOf(p)
                }
            }
            if (current.size > 1) groups += current

            groups.map { g ->
                val best = g.maxByOrNull { (cache[it.id]?.third ?: 0f) * 1_000 + it.size / 1_000_000f } ?: g.first()
                SimilarGroup(g, best)
            }.sortedByDescending { it.items.first().dateTaken }
        }
    }

    /** A 64-bit difference hash and a sharpness score from the photo's thumbnail. */
    private fun fingerprint(item: MediaItem): Pair<Long, Float>? = try {
        val thumb = appContext.contentResolver.loadThumbnail(item.uri, Size(160, 160), null)
        val hash = dHash(thumb)
        val sharp = sharpness(thumb)
        thumb.recycle()
        hash to sharp
    } catch (e: Exception) {
        null
    }

    private fun luma(c: Int): Int = ((c shr 16 and 0xFF) * 299 + (c shr 8 and 0xFF) * 587 + (c and 0xFF) * 114) / 1000

    private fun dHash(bmp: Bitmap): Long {
        val small = Bitmap.createScaledBitmap(bmp, 9, 8, true)
        var hash = 0L
        var bit = 0
        for (y in 0 until 8) for (x in 0 until 8) {
            if (luma(small.getPixel(x, y)) > luma(small.getPixel(x + 1, y))) hash = hash or (1L shl bit)
            bit++
        }
        small.recycle()
        return hash
    }

    /** Variance of the Laplacian: higher means more fine detail, i.e. sharper. */
    private fun sharpness(bmp: Bitmap): Float {
        val w = bmp.width
        val h = bmp.height
        if (w < 3 || h < 3) return 0f
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val g = IntArray(w * h) { luma(px[it]) }
        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val lap = (4 * g[i] - g[i - 1] - g[i + 1] - g[i - w] - g[i + w]).toDouble()
            sum += lap; sumSq += lap * lap; n++
        }
        val mean = sum / n
        return (sumSq / n - mean * mean).toFloat()
    }

    private fun distance(a: Long?, b: Long?): Int =
        if (a == null || b == null) Int.MAX_VALUE else java.lang.Long.bitCount(a xor b)

    private fun load() {
        if (loaded) return
        loaded = true
        if (!file.exists()) return
        runCatching {
            file.forEachLine { line ->
                val p = line.split(',')
                if (p.size == 4) {
                    val id = p[0].toLongOrNull() ?: return@forEachLine
                    cache[id] = Triple(p[1].toLongOrNull() ?: 0, p[2].toLongOrNull() ?: 0, p[3].toFloatOrNull() ?: 0f)
                }
            }
        }
    }

    private fun save() {
        runCatching {
            file.bufferedWriter().use { w ->
                for ((id, t) in cache) w.write("$id,${t.first},${t.second},${t.third}\n")
            }
        }
    }

    private companion object {
        const val WINDOW_MS = 2 * 60 * 1000L
        const val MAX_DISTANCE = 10
    }
}
