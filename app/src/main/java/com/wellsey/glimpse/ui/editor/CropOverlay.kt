package com.wellsey.glimpse.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val MIN_SIZE = 0.08f

private enum class Handle { TopLeft, TopRight, BottomLeft, BottomRight, Move, None }

/** Where a [imageSize] image sits inside a [box] when drawn with ContentScale.Fit. */
fun fitRect(box: IntSize, imageSize: IntSize): Rect {
    if (box.width == 0 || box.height == 0 || imageSize.width == 0 || imageSize.height == 0) return Rect.Zero
    val s = min(box.width.toFloat() / imageSize.width, box.height.toFloat() / imageSize.height)
    val w = imageSize.width * s
    val h = imageSize.height * s
    val left = (box.width - w) / 2f
    val top = (box.height - h) / 2f
    return Rect(left, top, left + w, top + h)
}

/** Largest centred crop with the given width/height [ratio] (in pixels). */
fun cropForAspect(ratio: Float?, imageSize: IntSize): Rect {
    if (ratio == null || imageSize.width == 0 || imageSize.height == 0) return FULL_RECT
    val rn = ratio * imageSize.height / imageSize.width // ratio in normalised units
    var w = 1f
    var h = 1f / rn
    if (h > 1f) {
        h = 1f
        w = rn
    }
    val l = (1f - w) / 2f
    val t = (1f - h) / 2f
    return Rect(l, t, l + w, t + h)
}

/**
 * Draggable crop frame. Drag a corner to resize, drag inside to move.
 * [aspect] (width / height in pixels) locks the shape when set.
 */
@Composable
fun CropOverlay(
    imageSize: IntSize,
    crop: Rect,
    aspect: Float?,
    onCropChange: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentCrop by rememberUpdatedState(crop)
    val currentAspect by rememberUpdatedState(aspect)
    val onChange by rememberUpdatedState(onCropChange)

    Canvas(
        modifier
            .fillMaxSize()
            .pointerInput(imageSize) {
                var handle = Handle.None
                detectDragGestures(
                    onDragStart = { pos ->
                        val img = fitRect(size, imageSize)
                        val c = toScreen(currentCrop, img)
                        val touch = 32.dp.toPx()
                        handle = when {
                            near(pos, c.topLeft, touch) -> Handle.TopLeft
                            near(pos, c.topRight, touch) -> Handle.TopRight
                            near(pos, c.bottomLeft, touch) -> Handle.BottomLeft
                            near(pos, c.bottomRight, touch) -> Handle.BottomRight
                            c.contains(pos) -> Handle.Move
                            else -> Handle.None
                        }
                    },
                    onDragEnd = { handle = Handle.None },
                    onDrag = { change, drag ->
                        if (handle == Handle.None) return@detectDragGestures
                        change.consume()
                        val img = fitRect(size, imageSize)
                        if (img.width <= 0f || img.height <= 0f) return@detectDragGestures
                        val dx = drag.x / img.width
                        val dy = drag.y / img.height
                        val ratio = currentAspect?.let { it * imageSize.height / imageSize.width }
                        onChange(
                            if (handle == Handle.Move) moveRect(currentCrop, dx, dy)
                            else resizeRect(currentCrop, handle, dx, dy, ratio)
                        )
                    },
                )
            }
    ) {
        val img = fitRect(IntSize(size.width.toInt(), size.height.toInt()), imageSize)
        if (img == Rect.Zero) return@Canvas
        val c = toScreen(crop, img)

        // Dim everything outside the crop.
        val hole = Path().apply { addRect(c) }
        clipPath(hole, clipOp = ClipOp.Difference) {
            drawRect(Color(0x99000000), topLeft = img.topLeft, size = img.size)
        }
        // Frame and rule-of-thirds guides.
        drawRect(Color.White, topLeft = c.topLeft, size = c.size, style = Stroke(1.5.dp.toPx()))
        val guide = Color.White.copy(alpha = 0.45f)
        for (i in 1..2) {
            val x = c.left + c.width * i / 3f
            val y = c.top + c.height * i / 3f
            drawLine(guide, Offset(x, c.top), Offset(x, c.bottom), 1.dp.toPx())
            drawLine(guide, Offset(c.left, y), Offset(c.right, y), 1.dp.toPx())
        }
        // Corner handles.
        val len = 18.dp.toPx()
        val w = 4.dp.toPx()
        for ((corner, dirX, dirY) in listOf(
            Triple(c.topLeft, 1f, 1f), Triple(c.topRight, -1f, 1f),
            Triple(c.bottomLeft, 1f, -1f), Triple(c.bottomRight, -1f, -1f),
        )) {
            drawLine(Color.White, corner, corner + Offset(len * dirX, 0f), w)
            drawLine(Color.White, corner, corner + Offset(0f, len * dirY), w)
        }
    }
}

private fun toScreen(n: Rect, img: Rect) = Rect(
    img.left + n.left * img.width,
    img.top + n.top * img.height,
    img.left + n.right * img.width,
    img.top + n.bottom * img.height,
)

private fun near(a: Offset, b: Offset, d: Float) = abs(a.x - b.x) < d && abs(a.y - b.y) < d

private fun moveRect(c: Rect, dx: Float, dy: Float): Rect {
    val nx = (c.left + dx).coerceIn(0f, 1f - c.width)
    val ny = (c.top + dy).coerceIn(0f, 1f - c.height)
    return Rect(Offset(nx, ny), Size(c.width, c.height))
}

private fun resizeRect(c: Rect, h: Handle, dx: Float, dy: Float, ratio: Float?): Rect {
    val leftSide = h == Handle.TopLeft || h == Handle.BottomLeft
    val topSide = h == Handle.TopLeft || h == Handle.TopRight

    if (ratio == null) {
        var l = c.left
        var t = c.top
        var r = c.right
        var b = c.bottom
        if (leftSide) l = (l + dx).coerceIn(0f, r - MIN_SIZE) else r = (r + dx).coerceIn(l + MIN_SIZE, 1f)
        if (topSide) t = (t + dy).coerceIn(0f, b - MIN_SIZE) else b = (b + dy).coerceIn(t + MIN_SIZE, 1f)
        return Rect(l, t, r, b)
    }

    // Aspect locked: the opposite corner stays put, the width follows the drag.
    val ax = if (leftSide) c.right else c.left
    val ay = if (topSide) c.bottom else c.top
    val maxW = if (leftSide) ax else 1f - ax
    val maxH = if (topSide) ay else 1f - ay
    val drive = if (abs(dx) >= abs(dy * ratio)) (if (leftSide) -dx else dx) else (if (topSide) -dy else dy) * ratio
    var w = (c.width + drive).coerceAtLeast(MIN_SIZE)
    var hgt = w / ratio
    if (w > maxW) { w = maxW; hgt = w / ratio }
    if (hgt > maxH) { hgt = maxH; w = hgt * ratio }
    val l = if (leftSide) ax - w else ax
    val t = if (topSide) ay - hgt else ay
    return Rect(l, t, l + w, t + hgt)
}

@Suppress("unused")
private fun clampUnit(v: Float) = max(0f, min(1f, v))
