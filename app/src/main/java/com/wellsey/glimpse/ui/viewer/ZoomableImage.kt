package com.wellsey.glimpse.ui.viewer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wellsey.glimpse.data.MediaItem
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val MAX_SCALE = 8f
private const val DOUBLE_TAP_SCALE = 2.5f

/**
 * Full-screen photo with pinch-zoom, pan and double-tap zoom.
 * When not zoomed, horizontal swipes pass through to the pager so you can flick between photos.
 */
@Composable
fun ZoomableImage(
    item: MediaItem,
    isCurrent: Boolean,
    onTap: () -> Unit,
    onZoomChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()

    fun clamp(o: Offset, s: Float): Offset {
        val maxX = size.width * (s - 1f) / 2f
        val maxY = size.height * (s - 1f) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    fun animateTo(targetScale: Float, targetOffset: Offset) {
        val startScale = scale
        val startOffset = offset
        scope.launch {
            val anim = Animatable(0f)
            anim.animateTo(1f, tween(220)) {
                val t = value
                scale = startScale + (targetScale - startScale) * t
                offset = startOffset + (targetOffset - startOffset) * t
            }
            onZoomChange(targetScale > 1f)
        }
    }

    LaunchedEffect(isCurrent) {
        if (!isCurrent && scale != 1f) {
            scale = 1f
            offset = Offset.Zero
            onZoomChange(false)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(item.id) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tap ->
                        if (scale > 1.05f) {
                            animateTo(1f, Offset.Zero)
                        } else {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val target = (tap - center) * (1f - DOUBLE_TAP_SCALE)
                            animateTo(DOUBLE_TAP_SCALE, clamp(target, DOUBLE_TAP_SCALE))
                        }
                    },
                )
            }
            .pointerInput(item.id) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        if (fingers >= 2 || scale > 1.01f) {
                            val newScale = (scale * zoom).coerceIn(1f, MAX_SCALE)
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val centroid = event.calculateCentroid(useCurrent = true)
                            // Keep the point under the fingers fixed while zooming.
                            var newOffset = if (centroid == Offset.Unspecified) offset else {
                                val rel = centroid - center
                                rel - (rel - offset) * (newScale / scale)
                            }
                            newOffset += pan
                            val clamped = clamp(newOffset, newScale)

                            // At the left/right edge with one finger: let the pager take the swipe.
                            val atEdge = fingers == 1 && abs(clamped.x - newOffset.x) > 0.5f &&
                                abs(pan.x) > abs(pan.y)
                            if (!atEdge) {
                                scale = newScale
                                offset = if (newScale <= 1.001f) Offset.Zero else clamped
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                    if (scale < 1.01f) {
                        scale = 1f
                        offset = Offset.Zero
                    }
                    onZoomChange(scale > 1f)
                }
            }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(item.uri)
                .memoryCacheKey("${item.uri}#${item.dateModified}")
                .size(2560)
                .build(),
            contentDescription = item.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}
