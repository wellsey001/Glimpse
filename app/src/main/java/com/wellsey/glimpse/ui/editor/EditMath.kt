package com.wellsey.glimpse.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.compose.ui.geometry.Rect
import kotlin.math.max
import kotlin.math.roundToInt

/** Slider values, each from -1 to 1 (0 = unchanged). */
data class Adjustments(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
) {
    val isDefault get() = this == Adjustments()
}

enum class AdjustKind(val label: String) {
    Brightness("Brightness"), Contrast("Contrast"), Saturation("Saturation"), Warmth("Warmth")
}

fun Adjustments.get(kind: AdjustKind) = when (kind) {
    AdjustKind.Brightness -> brightness
    AdjustKind.Contrast -> contrast
    AdjustKind.Saturation -> saturation
    AdjustKind.Warmth -> warmth
}

fun Adjustments.with(kind: AdjustKind, v: Float) = when (kind) {
    AdjustKind.Brightness -> copy(brightness = v)
    AdjustKind.Contrast -> copy(contrast = v)
    AdjustKind.Saturation -> copy(saturation = v)
    AdjustKind.Warmth -> copy(warmth = v)
}

enum class PhotoFilter(val label: String) {
    None("Original"), Vivid("Vivid"), Warm("Warm"), Cool("Cool"),
    Fade("Fade"), Mono("Mono"), Noir("Noir"), Sepia("Sepia");

    fun matrix(): ColorMatrix = when (this) {
        None -> ColorMatrix()
        Vivid -> saturation(1.35f).also { it.postConcat(contrast(1.08f)) }
        Warm -> scale(1.08f, 1.0f, 0.88f).also { it.postConcat(offset(8f, 4f, 0f)) }
        Cool -> scale(0.92f, 1.0f, 1.1f).also { it.postConcat(offset(0f, 2f, 8f)) }
        Fade -> saturation(0.75f).also {
            it.postConcat(contrast(0.82f))
            it.postConcat(offset(18f, 18f, 18f))
        }
        Mono -> saturation(0f)
        Noir -> saturation(0f).also { it.postConcat(contrast(1.45f)) }
        Sepia -> saturation(0f).also {
            it.postConcat(ColorMatrix(floatArrayOf(
                1.07f, 0f, 0f, 0f, 20f,
                0f, 0.95f, 0f, 0f, 8f,
                0f, 0f, 0.78f, 0f, -12f,
                0f, 0f, 0f, 1f, 0f,
            )))
        }
    }
}

private fun saturation(s: Float) = ColorMatrix().apply { setSaturation(s) }

private fun contrast(c: Float): ColorMatrix {
    val t = 128f * (1f - c)
    return ColorMatrix(floatArrayOf(
        c, 0f, 0f, 0f, t,
        0f, c, 0f, 0f, t,
        0f, 0f, c, 0f, t,
        0f, 0f, 0f, 1f, 0f,
    ))
}

private fun scale(r: Float, g: Float, b: Float) = ColorMatrix().apply { setScale(r, g, b, 1f) }

private fun offset(r: Float, g: Float, b: Float) = ColorMatrix(floatArrayOf(
    1f, 0f, 0f, 0f, r,
    0f, 1f, 0f, 0f, g,
    0f, 0f, 1f, 0f, b,
    0f, 0f, 0f, 1f, 0f,
))

/** The combined colour transform: filter first, then the sliders. */
fun colorMatrix(filter: PhotoFilter, adj: Adjustments): FloatArray {
    val m = filter.matrix()
    m.postConcat(saturation(1f + adj.saturation))
    m.postConcat(contrast(1f + adj.contrast * 0.6f))
    val b = adj.brightness * 70f
    m.postConcat(offset(b, b, b))
    val w = adj.warmth * 28f
    m.postConcat(offset(w, w * 0.25f, -w))
    return m.array.copyOf()
}

/** Decodes a photo (respecting EXIF rotation) so its longest side is at most [maxSide]. */
fun decodeBitmap(context: Context, uri: Uri, maxSide: Int): Bitmap {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.isMutableRequired = false
        val w = info.size.width
        val h = info.size.height
        val longest = max(w, h)
        if (longest > maxSide) {
            val f = maxSide.toFloat() / longest
            decoder.setTargetSize((w * f).roundToInt().coerceAtLeast(1), (h * f).roundToInt().coerceAtLeast(1))
        }
    }
}

/** Rotates (in 90° steps) and optionally mirrors a bitmap. */
fun transform(src: Bitmap, rotation: Int, flip: Boolean): Bitmap {
    if (rotation % 360 == 0 && !flip) return src
    val m = Matrix().apply {
        postRotate(rotation.toFloat())
        if (flip) postScale(-1f, 1f)
    }
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
}

/** Crops using a rectangle in 0..1 coordinates. */
fun crop(src: Bitmap, rect: Rect): Bitmap {
    if (rect == FULL_RECT) return src
    val x = (rect.left * src.width).roundToInt().coerceIn(0, src.width - 1)
    val y = (rect.top * src.height).roundToInt().coerceIn(0, src.height - 1)
    val w = (rect.width * src.width).roundToInt().coerceIn(1, src.width - x)
    val h = (rect.height * src.height).roundToInt().coerceIn(1, src.height - y)
    return Bitmap.createBitmap(src, x, y, w, h)
}

fun applyColor(src: Bitmap, matrix: FloatArray): Bitmap {
    val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(matrix)
    }
    Canvas(out).drawBitmap(src, 0f, 0f, paint)
    return out
}

val FULL_RECT = Rect(0f, 0f, 1f, 1f)
