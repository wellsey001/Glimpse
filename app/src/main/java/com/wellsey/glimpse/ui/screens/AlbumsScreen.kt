package com.wellsey.glimpse.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.wellsey.glimpse.data.MediaThumb
import com.wellsey.glimpse.ui.GalleryViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AlbumsScreen(
    vm: GalleryViewModel,
    onOpenAlbum: (Long) -> Unit,
    onOpenFavourites: () -> Unit,
    onOpenVideos: () -> Unit,
    onOpenTrash: () -> Unit,
    onChooseFolders: () -> Unit,
    onOpenRecent: () -> Unit,
    onOpenOldScreenshots: () -> Unit,
    onOpenSimilar: () -> Unit,
) {
    val hidden by vm.settings.hiddenFolders.collectAsStateWithLifecycle()
    val sort by vm.settings.albumSort.collectAsStateWithLifecycle()
    val pinned by vm.settings.pinnedAlbums.collectAsStateWithLifecycle()
    val lock by vm.settings.lockHidden.collectAsStateWithLifecycle()
    val unlocked by vm.unlocked.collectAsStateWithLifecycle()
    val recent by vm.recentlyAdded.collectAsStateWithLifecycle()
    val oldShots by vm.oldScreenshots.collectAsStateWithLifecycle()
    var sortMenu by remember { mutableStateOf(false) }
    val albums by vm.albums.collectAsStateWithLifecycle()
    val favs by vm.favoriteItems.collectAsStateWithLifecycle()
    val media by vm.media.collectAsStateWithLifecycle()
    val videoCount = media.count { it.isVideo }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text("Albums") },
                actions = {
                    IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sort albums") }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        listOf("recent" to "Most recent", "name" to "Name", "size" to "Most photos").forEach { (key, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                leadingIcon = { RadioButton(selected = sort == key, onClick = null) },
                                onClick = { sortMenu = false; vm.settings.setAlbumSort(key) },
                            )
                        }
                        HorizontalDivider()
                        Text(
                            "Long-press an album to pin it to the top.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    TextButton(onClick = onChooseFolders) {
                        Icon(Icons.Filled.Tune, null)
                        Text(" Folders")
                    }
                },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(
                start = 12.dp, end = 12.dp,
                top = padding.calculateTopPadding() + 4.dp, bottom = 16.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickTile(Icons.Filled.Favorite, "Favourites", favs.size, onOpenFavourites, Modifier.weight(1f))
                    QuickTile(Icons.Filled.Videocam, "Videos", videoCount, onOpenVideos, Modifier.weight(1f))
                    QuickTile(Icons.Filled.Delete, "Trash", null, onOpenTrash, Modifier.weight(1f))
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickTile(Icons.Filled.NewReleases, "Recently added", recent.size, onOpenRecent, Modifier.weight(1f))
                    QuickTile(Icons.Filled.Screenshot, "Old screenshots", oldShots.size, onOpenOldScreenshots, Modifier.weight(1f))
                    QuickTile(Icons.Filled.Collections, "Similar", null, onOpenSimilar, Modifier.weight(1f))
                }
            }
            items(albums, key = { it.bucketId }) { album ->
                val isPinned = album.bucketId in pinned
                val isLocked = lock && !unlocked && album.bucketId in hidden
                Column(
                    Modifier.combinedClickable(
                        onClick = { onOpenAlbum(album.bucketId) },
                        onLongClick = {
                            vm.settings.setPinned(album.bucketId, !isPinned)
                            vm.message(if (isPinned) "Unpinned ${album.name}" else "Pinned ${album.name} to the top")
                        },
                    )
                ) {
                    Box {
                        if (isLocked) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Filled.Lock, "Locked", modifier = Modifier.size(36.dp)) }
                        } else AsyncImage(
                            model = MediaThumb(album.cover.uri, album.cover.dateModified, 512),
                            contentDescription = album.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(16.dp)),
                        )
                        if (isPinned) {
                            Icon(
                                Icons.Filled.PushPin, "Pinned", tint = Color.White,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .background(Color(0xCC000000), RoundedCornerShape(10.dp))
                                    .padding(5.dp)
                                    .size(16.dp),
                            )
                        }
                        if (album.bucketId in hidden) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(8.dp)
                                    .background(Color(0xCC000000), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Icon(Icons.Filled.VisibilityOff, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Text(" Hidden from Photos", color = Color.White, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        album.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (isLocked) "Locked" else "${album.count}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickTile(
    icon: ImageVector,
    label: String,
    count: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            Icon(icon, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                if (count != null) {
                    Text("$count", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
