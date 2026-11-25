package com.wellsey.glimpse.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wellsey.glimpse.data.MediaItem
import com.wellsey.glimpse.data.SettingsStore
import com.wellsey.glimpse.ui.GalleryViewModel

enum class HomeTab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Photos("Photos", Icons.Outlined.Photo, Icons.Filled.Photo),
    Albums("Albums", Icons.Outlined.PhotoAlbum, Icons.Filled.PhotoAlbum),
    Map("Map", Icons.Outlined.Map, Icons.Filled.Map),
    Search("Search", Icons.Outlined.Search, Icons.Filled.Search),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: GalleryViewModel,
    access: Access,
    onRequestMoreAccess: () -> Unit,
    onOpen: (List<MediaItem>, Int) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenFavourites: () -> Unit,
    onOpenVideos: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenPlace: (String, List<MediaItem>) -> Unit,
    onChooseFolders: () -> Unit,
    onOpenRecent: () -> Unit = {},
    onOpenOldScreenshots: () -> Unit = {},
    onOpenSimilar: () -> Unit = {},
) {
    var tab by rememberSaveable { mutableStateOf(HomeTab.Photos) }
    val photosGrid = rememberLazyGridState()

    Scaffold(
        bottomBar = {
            NavigationBar {
                HomeTab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(if (tab == t) t.selectedIcon else t.icon, null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding())
        ) {
            when (tab) {
                HomeTab.Photos -> PhotosTab(vm, access, onRequestMoreAccess, onOpen, photosGrid, onChooseFolders)
                HomeTab.Albums -> AlbumsScreen(
                    vm, onOpenAlbum, onOpenFavourites, onOpenVideos, onOpenTrash, onChooseFolders,
                    onOpenRecent, onOpenOldScreenshots, onOpenSimilar,
                )
                HomeTab.Map -> MapScreen(
                    vm,
                    onOpenPlace = onOpenPlace,
                    onOpenSingle = { item -> onOpen(listOf(item), 0) },
                )
                HomeTab.Search -> SearchScreen(vm, onOpen)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotosTab(
    vm: GalleryViewModel,
    access: Access,
    onRequestMoreAccess: () -> Unit,
    onOpen: (List<MediaItem>, Int) -> Unit,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    onChooseFolders: () -> Unit,
) {
    val context = LocalContext.current
    val media by vm.visibleMedia.collectAsStateWithLifecycle()
    val hidden by vm.settings.hiddenFolders.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val columns by vm.settings.columns.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    val canAskManageMedia = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        !MediaStore.canManageMedia(context)

    val bannerHeader: LazyGridScope.() -> Unit = {
        item(span = { GridItemSpan(maxLineSpan) }) {
            PartialAccessBanner(onRequestMoreAccess)
        }
    }

    CollectionScreen(
        vm = vm,
        title = "Photos",
        items = media,
        onOpen = onOpen,
        loading = !loaded,
        emptyText = if (hidden.isNotEmpty()) "No photos in the folders you've chosen. Tap ⋮ → Choose folders." else "No photos or videos found",
        windowInsets = WindowInsets(0.dp),
        gridState = gridState,
        actions = {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(if (hidden.isEmpty()) "Choose folders…" else "Choose folders… (${hidden.size} hidden)") },
                    onClick = { menu = false; onChooseFolders() },
                )
                DropdownMenuItem(
                    text = { Text("Bigger thumbnails") },
                    enabled = columns > SettingsStore.MIN_COLUMNS,
                    onClick = { vm.settings.setColumns(columns - 1) },
                )
                DropdownMenuItem(
                    text = { Text("Smaller thumbnails") },
                    enabled = columns < SettingsStore.MAX_COLUMNS,
                    onClick = { vm.settings.setColumns(columns + 1) },
                )
                DropdownMenuItem(
                    text = { Text("Refresh") },
                    onClick = { menu = false; vm.refresh() },
                )
                if (canAskManageMedia) {
                    DropdownMenuItem(
                        text = { Text("Skip confirm prompts…") },
                        onClick = {
                            menu = false
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_REQUEST_MANAGE_MEDIA,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                )
                            }.onFailure { vm.message("Your phone doesn't offer this setting") }
                        },
                    )
                }
            }
        },
        header = if (access == Access.Partial) bannerHeader else null,
    )
}

@Composable
private fun PartialAccessBanner(onManage: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Icon(Icons.Outlined.Info, null)
            Spacer(Modifier.width(10.dp))
            Text(
                "Glimpse can only see the photos you picked.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onManage) { Text("Manage") }
        }
    }
}
