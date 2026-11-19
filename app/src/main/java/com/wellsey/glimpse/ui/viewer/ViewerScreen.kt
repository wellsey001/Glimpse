package com.wellsey.glimpse.ui.viewer

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wellsey.glimpse.data.MediaItem
import com.wellsey.glimpse.ui.GalleryViewModel
import com.wellsey.glimpse.ui.ViewerMode
import com.wellsey.glimpse.ui.components.shareItems
import com.wellsey.glimpse.util.formatDateTime

@Composable
fun ViewerScreen(
    vm: GalleryViewModel,
    onBack: () -> Unit,
    onEdit: (MediaItem) -> Unit,
) {
    val items = vm.viewerItems
    val mode = vm.viewerMode
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val context = LocalContext.current
    val favorites by vm.favorites.ids.collectAsStateWithLifecycle()
    val pager = rememberPagerState(initialPage = vm.viewerStart.coerceIn(0, items.lastIndex)) { items.size }
    var chrome by remember { mutableStateOf(true) }
    var showInfo by remember { mutableStateOf(false) }
    val current = items.getOrNull(pager.currentPage.coerceAtMost(items.lastIndex))

    // Hide the status and navigation bars along with our own controls.
    val view = LocalView.current
    LaunchedEffect(chrome) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        if (chrome) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            val window = (view.context as? Activity)?.window ?: return@onDispose
            WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.systemBars())
        }
    }

    fun removeFromViewer(gone: List<MediaItem>) {
        val ids = gone.map { it.id }.toSet()
        val next = vm.viewerItems.filterNot { it.id in ids }
        vm.viewerStart = pager.currentPage.coerceAtMost((next.size - 1).coerceAtLeast(0))
        vm.viewerItems = next
        if (next.isEmpty()) onBack()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pager,
            key = { index -> items.getOrNull(index)?.let { "${it.id}:${it.uri}" } ?: index },
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val item = items[page]
            val isCurrent = page == pager.settledPage
            if (item.isVideo) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .padding(bottom = if (chrome) 72.dp else 0.dp)
                ) {
                    VideoPage(
                        item = item,
                        isCurrent = isCurrent,
                        onChromeChange = { chrome = it },
                        onTapPoster = { chrome = !chrome },
                    )
                }
            } else {
                ZoomableImage(
                    item = item,
                    isCurrent = isCurrent,
                    onTap = { chrome = !chrome },
                    onZoomChange = {},
                )
            }
        }

        // Top bar
        AnimatedVisibility(
            visible = chrome,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xAA000000), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    if (current != null) {
                        Text(
                            if (current.isExternal) current.name else formatDateTime(current.dateTaken),
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (!current.isExternal && current.bucketName.isNotBlank()) {
                            Text(
                                current.bucketName,
                                color = Color.White.copy(alpha = 0.75f),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                            )
                        }
                    }
                }
                IconButton(onClick = { showInfo = true }) {
                    Icon(Icons.Outlined.Info, "Details", tint = Color.White)
                }
            }
        }

        // Bottom actions
        AnimatedVisibility(
            visible = chrome && current != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val item = current ?: return@AnimatedVisibility
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xAA000000))))
                    .navigationBarsPadding()
                    .padding(vertical = 8.dp),
            ) {
                when (mode) {
                    ViewerMode.Trash -> {
                        BarAction(Icons.Filled.Restore, "Restore") {
                            vm.restore(listOf(item)) { removeFromViewer(listOf(item)) }
                        }
                        BarAction(Icons.Filled.DeleteForever, "Delete") {
                            vm.deleteForever(listOf(item)) { removeFromViewer(listOf(item)) }
                        }
                    }
                    ViewerMode.External -> {
                        BarAction(Icons.Filled.Share, "Share") { shareItems(context, listOf(item)) }
                        if (!item.isVideo) BarAction(Icons.Filled.Edit, "Edit") { onEdit(item) }
                    }
                    ViewerMode.Normal -> {
                        val fav = item.id in favorites
                        BarAction(Icons.Filled.Share, "Share") { shareItems(context, listOf(item)) }
                        BarAction(
                            if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            if (fav) "Favourited" else "Favourite",
                        ) { vm.toggleFavorite(item) }
                        if (!item.isVideo) BarAction(Icons.Filled.Edit, "Edit") { onEdit(item) }
                        BarAction(Icons.Filled.Delete, "Delete") {
                            vm.moveToTrash(listOf(item)) { removeFromViewer(listOf(item)) }
                        }
                    }
                }
            }
        }
    }

    if (showInfo && current != null) {
        InfoSheet(current, onDismiss = { showInfo = false })
    }
}

@Composable
private fun BarAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) {
            Icon(icon, label, tint = Color.White)
        }
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}
