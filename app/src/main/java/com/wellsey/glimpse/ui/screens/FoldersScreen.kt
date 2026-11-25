package com.wellsey.glimpse.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.wellsey.glimpse.data.MediaThumb
import com.wellsey.glimpse.ui.GalleryViewModel

/**
 * Choose which folders appear in Photos, Search, Map and Videos.
 * Hidden folders still appear in Albums, so nothing is ever out of reach.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersScreen(vm: GalleryViewModel, onBack: () -> Unit) {
    val albums by vm.albums.collectAsStateWithLifecycle()
    val hidden by vm.settings.hiddenFolders.collectAsStateWithLifecycle()
    val lock by vm.settings.lockHidden.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    val shownCount = albums.count { it.bucketId !in hidden }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = {
                    Column0(
                        title = "Folders in Photos",
                        subtitle = "$shownCount of ${albums.size} shown",
                    )
                },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Show all") },
                            onClick = { menu = false; vm.settings.setHiddenFolders(emptySet()) },
                        )
                        DropdownMenuItem(
                            text = { Text("Only Camera") },
                            onClick = {
                                menu = false
                                vm.settings.setHiddenFolders(
                                    albums.filterNot { it.relativePath.startsWith("DCIM/Camera", ignoreCase = true) }
                                        .map { it.bucketId }.toSet()
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Hide all") },
                            onClick = { menu = false; vm.settings.setHiddenFolders(albums.map { it.bucketId }.toSet()) },
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp)) {
            item {
                Text(
                    "Turn off folders you don't want in your Photos timeline, such as WhatsApp images or screenshots. " +
                        "They also stay out of Search, Map and Videos, but you can still open them from Albums. " +
                        "Nothing is moved or deleted.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.settings.setLockHidden(!lock) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Column0(
                        title = "Lock hidden folders",
                        subtitle = "Ask for your fingerprint, face or screen lock before opening them",
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = lock, onCheckedChange = { vm.settings.setLockHidden(it) })
                }
                HorizontalDivider()
            }
            items(albums, key = { it.bucketId }) { album ->
                val shown = album.bucketId !in hidden
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.settings.setFolderShown(album.bucketId, !shown) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    AsyncImage(
                        model = MediaThumb(album.cover.uri, album.cover.dateModified, 160),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .alpha(if (shown) 1f else 0.4f),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column0(
                        title = album.name,
                        subtitle = "${album.count} · ${album.relativePath.trimEnd('/')}",
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = shown, onCheckedChange = { vm.settings.setFolderShown(album.bucketId, it) })
                }
            }
        }
    }
}

@Composable
private fun Column0(title: String, subtitle: String, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Column(modifier) {
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
