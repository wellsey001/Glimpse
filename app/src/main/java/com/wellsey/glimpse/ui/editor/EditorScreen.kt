package com.wellsey.glimpse.ui.editor

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.wellsey.glimpse.data.MediaItem
import com.wellsey.glimpse.ui.GalleryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private enum class Tool(val label: String) { Crop("Crop"), Adjust("Adjust"), Filters("Filters") }

private data class AspectOption(val label: String, val ratio: Float?, val original: Boolean = false)

private val aspectOptions = listOf(
    AspectOption("Free", null),
    AspectOption("Original", null, original = true),
    AspectOption("1:1", 1f),
    AspectOption("4:3", 4f / 3f),
    AspectOption("3:4", 3f / 4f),
    AspectOption("16:9", 16f / 9f),
    AspectOption("9:16", 9f / 16f),
)

private const val PREVIEW_MAX = 1600
private const val SAVE_MAX = 4096

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: GalleryViewModel, item: MediaItem, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val source by produceState<Bitmap?>(null, item.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching { decodeBitmap(context, item.uri, PREVIEW_MAX) }.getOrNull()
        }
    }

    var tool by remember { mutableStateOf(Tool.Crop) }
    var rotation by remember { mutableStateOf(0) }
    var flip by remember { mutableStateOf(false) }
    var cropRect by remember { mutableStateOf(FULL_RECT) }
    var aspect by remember { mutableStateOf(aspectOptions[0]) }
    var adjust by remember { mutableStateOf(Adjustments()) }
    var adjustKind by remember { mutableStateOf(AdjustKind.Brightness) }
    var filter by remember { mutableStateOf(PhotoFilter.None) }
    var showOriginal by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    val edited = rotation != 0 || flip || cropRect != FULL_RECT || !adjust.isDefault || filter != PhotoFilter.None

    val transformed = remember(source, rotation, flip) { source?.let { transform(it, rotation, flip) } }
    val imageSize = transformed?.let { IntSize(it.width, it.height) } ?: IntSize.Zero
    val displayed = remember(transformed, cropRect, tool) {
        transformed?.let { if (tool == Tool.Crop) it else crop(it, cropRect) }
    }
    val matrix = remember(filter, adjust) { colorMatrix(filter, adjust) }
    val colorFilter = remember(matrix) { ColorFilter.colorMatrix(ColorMatrix(matrix)) }

    fun ratioFor(option: AspectOption): Float? =
        if (option.original) source?.let { it.width.toFloat() / it.height }?.let {
            if (rotation % 180 != 0) 1f / it else it
        } else option.ratio

    fun tryClose() {
        if (edited && !saving) confirmDiscard = true else onClose()
    }

    BackHandler { tryClose() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { tryClose() }) { Icon(Icons.Filled.Close, "Close") }
                },
                title = { Text("Edit") },
                actions = {
                    TextButton(
                        enabled = edited && !saving && source != null,
                        onClick = {
                            saving = true
                            scope.launch {
                                val result = withContext(Dispatchers.Default) {
                                    runCatching {
                                        val full = decodeBitmap(context, item.uri, SAVE_MAX)
                                        val t = transform(full, rotation, flip)
                                        val c = crop(t, cropRect)
                                        applyColor(c, matrix)
                                    }.getOrNull()
                                }
                                if (result == null) {
                                    saving = false
                                    vm.message("Couldn't edit this photo")
                                } else {
                                    vm.saveEdited(item, result) { uri ->
                                        saving = false
                                        if (uri != null) onClose()
                                    }
                                }
                            }
                        },
                    ) { Text("Save copy") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .navigationBarsPadding()
        ) {
            // --- Preview ---
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                val bmp = displayed
                if (bmp == null) {
                    CircularProgressIndicator()
                } else {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Preview",
                        contentScale = ContentScale.Fit,
                        colorFilter = if (showOriginal) null else colorFilter,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(tool) {
                                if (tool != Tool.Crop) {
                                    // Press and hold to compare with the original.
                                    detectTapGestures(onPress = {
                                        showOriginal = true
                                        tryAwaitRelease()
                                        showOriginal = false
                                    })
                                }
                            },
                    )
                    if (tool == Tool.Crop) {
                        CropOverlay(
                            imageSize = imageSize,
                            crop = cropRect,
                            aspect = ratioFor(aspect),
                            onCropChange = { cropRect = it },
                        )
                    }
                }
                if (saving) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                }
            }

            // --- Tool panel ---
            Box(Modifier.height(132.dp).fillMaxWidth()) {
                when (tool) {
                    Tool.Crop -> Column(verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(aspectOptions) { option ->
                                FilterChip(
                                    selected = aspect == option,
                                    onClick = {
                                        aspect = option
                                        cropRect = cropForAspect(ratioFor(option), imageSize)
                                    },
                                    label = { Text(option.label) },
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) {
                            ToolButton(Icons.Filled.RotateLeft, "Left") {
                                rotation = (rotation + 270) % 360
                                cropRect = cropForAspect(ratioFor(aspect), IntSize(imageSize.height, imageSize.width))
                            }
                            ToolButton(Icons.Filled.RotateRight, "Right") {
                                rotation = (rotation + 90) % 360
                                cropRect = cropForAspect(ratioFor(aspect), IntSize(imageSize.height, imageSize.width))
                            }
                            ToolButton(Icons.Filled.Flip, "Flip") {
                                flip = !flip
                                cropRect = Rect(1f - cropRect.right, cropRect.top, 1f - cropRect.left, cropRect.bottom)
                            }
                            ToolButton(Icons.Filled.Restore, "Reset") {
                                rotation = 0; flip = false; cropRect = FULL_RECT; aspect = aspectOptions[0]
                            }
                        }
                    }

                    Tool.Adjust -> Column(verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(AdjustKind.entries) { kind ->
                                FilterChip(
                                    selected = adjustKind == kind,
                                    onClick = { adjustKind = kind },
                                    label = {
                                        val v = (adjust.get(kind) * 100).roundToInt()
                                        Text(if (v == 0) kind.label else "${kind.label} ${if (v > 0) "+" else ""}$v")
                                    },
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        ) {
                            Slider(
                                value = adjust.get(adjustKind),
                                onValueChange = { adjust = adjust.with(adjustKind, it) },
                                valueRange = -1f..1f,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { adjust = adjust.with(adjustKind, 0f) }) { Text("Reset") }
                        }
                    }

                    Tool.Filters -> FilterStrip(
                        source = source,
                        selected = filter,
                        adjust = adjust,
                        onSelect = { filter = it },
                    )
                }
            }

            PrimaryTabRow(selectedTabIndex = tool.ordinal) {
                Tool.entries.forEach { t ->
                    Tab(selected = tool == t, onClick = { tool = t }, text = { Text(t.label) })
                }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard edits?") },
            text = { Text("Your changes haven't been saved.") },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; onClose() }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") }
            },
        )
    }
}

@Composable
private fun ToolButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) { Icon(icon, label) }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun FilterStrip(
    source: Bitmap?,
    selected: PhotoFilter,
    adjust: Adjustments,
    onSelect: (PhotoFilter) -> Unit,
) {
    // A small copy of the photo for the filter swatches.
    val thumb = remember(source) {
        source?.let {
            val s = 160f / maxOf(it.width, it.height)
            Bitmap.createScaledBitmap(it, (it.width * s).roundToInt().coerceAtLeast(1), (it.height * s).roundToInt().coerceAtLeast(1), true)
        }
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(PhotoFilter.entries) { f ->
            val cf = remember(f, adjust) { ColorFilter.colorMatrix(ColorMatrix(colorMatrix(f, adjust))) }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onSelect(f) },
            ) {
                Box(
                    Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .then(
                            if (f == selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                            else Modifier
                        )
                ) {
                    if (thumb != null) {
                        Image(
                            bitmap = thumb.asImageBitmap(),
                            contentDescription = f.label,
                            contentScale = ContentScale.Crop,
                            colorFilter = cf,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    f.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (f == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
