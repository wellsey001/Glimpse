package com.wellsey.glimpse.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wellsey.glimpse.data.Geo
import com.wellsey.glimpse.data.MediaItem
import com.wellsey.glimpse.ui.GalleryViewModel
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt

private class Cluster(val center: GeoPoint, val items: List<MediaItem>)

/** Groups nearby photos so the map shows one bubble per place at the current zoom. */
private fun cluster(points: List<Pair<MediaItem, Geo>>, zoom: Int): List<Cluster> {
    val cellDeg = 360.0 / (256.0 * 2.0.pow(zoom)) * 64.0 // ~64px cells on screen
    return points.groupBy { (_, g) ->
        floor(g.lat / cellDeg).toLong() * 1_000_003L + floor(g.lon / cellDeg).toLong()
    }.values.map { group ->
        val lat = group.sumOf { it.second.lat } / group.size
        val lon = group.sumOf { it.second.lon } / group.size
        Cluster(GeoPoint(lat, lon), group.map { it.first })
    }
}

private fun bubble(context: Context, count: Int, fill: Int, text: Int): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val label = if (count > 999) "999+" else count.toString()
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = text
        textSize = 13f * density
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    val w = maxOf(34f * density, textPaint.measureText(label) + 20f * density)
    val h = 34f * density
    val bmp = Bitmap.createBitmap(w.roundToInt(), h.roundToInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill }
    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    val r = h / 2f
    canvas.drawRoundRect(1f * density, 1f * density, w - density, h - density, r, r, bg)
    canvas.drawRoundRect(1f * density, 1f * density, w - density, h - density, r, r, border)
    val y = h / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(label, w / 2f, y, textPaint)
    return BitmapDrawable(context.resources, bmp)
}

@Composable
fun MapScreen(
    vm: GalleryViewModel,
    onOpenPlace: (title: String, items: List<MediaItem>) -> Unit,
    onOpenSingle: (MediaItem) -> Unit,
) {
    val context = LocalContext.current
    val media by vm.media.collectAsStateWithLifecycle()
    val geo by vm.geo.collectAsStateWithLifecycle()
    val progress by vm.geoProgress.collectAsStateWithLifecycle()
    val dark = isSystemInDarkTheme()
    val fill = MaterialTheme.colorScheme.primary.toArgb()
    val onFill = MaterialTheme.colorScheme.onPrimary.toArgb()

    LaunchedEffect(media) { vm.indexLocations() }

    val points = remember(media, geo) {
        media.mapNotNull { m -> geo[m.id]?.let { m to it } }
    }
    var zoom by remember { mutableIntStateOf(3) }
    var fitted by remember { mutableStateOf(false) }
    val openPlace by rememberUpdatedState(onOpenPlace)
    val openSingle by rememberUpdatedState(onOpenSingle)

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            minZoomLevel = 2.0
            maxZoomLevel = 19.0
            controller.setZoom(3.0)
            isTilesScaledToDpi = true
            addMapListener(DelayedMapListener(object : MapListener {
                override fun onScroll(event: ScrollEvent?) = false
                override fun onZoom(event: ZoomEvent?): Boolean {
                    event?.let { zoom = it.zoomLevel.roundToInt() }
                    return false
                }
            }, 200))
        }
    }

    // Keep osmdroid in step with the screen's lifecycle.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onDetach()
        }
    }

    LaunchedEffect(dark) {
        mapView.overlayManager.tilesOverlay.setColorFilter(if (dark) TilesOverlay.INVERTED_COLORS else null)
        mapView.invalidate()
    }

    // Rebuild the bubbles whenever photos or zoom change.
    LaunchedEffect(points, zoom, fill) {
        mapView.overlays.removeAll { it is Marker }
        for (c in cluster(points, zoom)) {
            val marker = Marker(mapView).apply {
                position = c.center
                icon = bubble(context, c.items.size, fill, onFill)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setOnMarkerClickListener { _, _ ->
                    if (c.items.size == 1) openSingle(c.items.first())
                    else openPlace("${c.items.size} photos here", c.items)
                    true
                }
            }
            mapView.overlays.add(marker)
        }
        mapView.invalidate()

        if (!fitted && points.isNotEmpty() && progress == null) {
            fitted = true
            mapView.post {
                if (points.size == 1) {
                    mapView.controller.setZoom(12.0)
                    mapView.controller.setCenter(GeoPoint(points[0].second.lat, points[0].second.lon))
                } else {
                    val box = BoundingBox.fromGeoPoints(points.map { GeoPoint(it.second.lat, it.second.lon) })
                    mapView.zoomToBoundingBox(box, false, 96)
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 12.dp),
        ) {
            val p = progress
            val label = when {
                p != null -> "Finding photo locations… ${p.first} / ${p.second}"
                points.isEmpty() && geo.isEmpty() && media.isNotEmpty() ->
                    "No photos with a location yet"
                else -> "${points.size} photos with a location"
            }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shadowElevation = 3.dp,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    if (p != null) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        Text(
            "© OpenStreetMap contributors",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp),
        )
    }
}
