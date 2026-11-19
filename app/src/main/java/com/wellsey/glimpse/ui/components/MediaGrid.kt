package com.wellsey.glimpse.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.wellsey.glimpse.data.MediaItem
import com.wellsey.glimpse.data.MediaThumb
import com.wellsey.glimpse.util.dayLabel
import com.wellsey.glimpse.util.formatDuration
import com.wellsey.glimpse.util.localDate

private data class Section(val label: String, val entries: List<IndexedValue<MediaItem>>)

/**
 * The photo grid used everywhere. When [grouped] is true, items get date headers
 * (tap a header to select that whole day). Pinch with two fingers to change the column count.
 */
@Composable
fun MediaGrid(
    items: List<MediaItem>,
    columns: Int,
    onColumnsChange: (Int) -> Unit,
    selected: Set<Long>,
    favorites: Set<Long>,
    onClick: (index: Int) -> Unit,
    onLongClick: (index: Int) -> Unit,
    onSelectGroup: (List<MediaItem>) -> Unit,
    modifier: Modifier = Modifier,
    grouped: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(),
    state: LazyGridState = rememberLazyGridState(),
    badge: ((MediaItem) -> String?)? = null,
    header: (LazyGridScope.() -> Unit)? = null,
) {
    val sections = remember(items, grouped) {
        if (!grouped) {
            listOf(Section("", items.withIndex().toList()))
        } else {
            items.withIndex()
                .groupBy { localDate(it.value.dateTaken) }
                .map { (date, entries) -> Section(dayLabel(date), entries) }
        }
    }
    val selectionMode = selected.isNotEmpty()

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = state,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .fillMaxSize()
            .pinchToChangeColumns(columns, onColumnsChange),
    ) {
        header?.invoke(this)
        for (section in sections) {
            if (section.label.isNotEmpty()) {
                item(
                    key = "h_${section.label}_${section.entries.first().index}",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "header",
                ) {
                    val allSelected = selectionMode && section.entries.all { it.value.id in selected }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = selectionMode) {
                                onSelectGroup(section.entries.map { it.value })
                            }
                            .padding(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 8.dp),
                    ) {
                        Text(
                            section.label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.weight(1f))
                        if (selectionMode) {
                            Icon(
                                if (allSelected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                                contentDescription = "Select day",
                                tint = if (allSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
            items(
                count = section.entries.size,
                key = { section.entries[it].value.id },
                contentType = { "media" },
            ) { i ->
                val (index, item) = section.entries[i]
                MediaCell(
                    item = item,
                    isSelected = item.id in selected,
                    selectionMode = selectionMode,
                    isFavorite = item.id in favorites,
                    badge = badge?.invoke(item),
                    onClick = { onClick(index) },
                    onLongClick = { onLongClick(index) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaCell(
    item: MediaItem,
    isSelected: Boolean,
    selectionMode: Boolean,
    isFavorite: Boolean,
    badge: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val inset by animateDpAsState(if (isSelected) 10.dp else 0.dp, label = "inset")
    val corner by animateDpAsState(if (isSelected) 12.dp else 0.dp, label = "corner")
    Box(
        Modifier
            .aspectRatio(1f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        AsyncImage(
            model = MediaThumb(item.uri, item.dateModified),
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .padding(inset)
                .clip(RoundedCornerShape(corner))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        if (item.isVideo || isFavorite || badge != null) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(inset)
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000)))
                    )
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                if (isFavorite) {
                    Icon(
                        Icons.Filled.Favorite, contentDescription = "Favourite",
                        tint = Color.White, modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(14.dp)
                    )
                }
                val label = badge ?: if (item.isVideo) formatDuration(item.durationMs) else null
                if (label != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    ) {
                        if (item.isVideo && badge == null) {
                            Icon(
                                Icons.Filled.PlayArrow, null, tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(2.dp))
                        }
                        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        if (selectionMode) {
            Icon(
                if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                contentDescription = if (isSelected) "Selected" else "Not selected",
                tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(22.dp)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.surface else Color(0x33000000),
                        CircleShape
                    ),
            )
        }
    }
}

/** Two-finger pinch on the grid: spread to show bigger thumbnails, pinch to show more. */
fun Modifier.pinchToChangeColumns(columns: Int, onChange: (Int) -> Unit): Modifier =
    this.pointerInput(columns) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var zoom = 1f
            var handled = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } >= 2) {
                    zoom *= event.calculateZoom()
                    event.changes.forEach { it.consume() }
                    if (!handled) {
                        if (zoom > 1.3f) {
                            onChange(columns - 1); handled = true
                        } else if (zoom < 0.77f) {
                            onChange(columns + 1); handled = true
                        }
                    }
                }
            } while (event.changes.any { it.pressed })
        }
    }

/** Keeps the latest value for use inside long-lived gesture lambdas. */
@Composable
fun <T> latest(value: T) = rememberUpdatedState(value)
