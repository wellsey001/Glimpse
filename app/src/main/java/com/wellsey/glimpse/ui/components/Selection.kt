package com.wellsey.glimpse.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.wellsey.glimpse.data.Album
import com.wellsey.glimpse.data.MediaItem
import com.wellsey.glimpse.data.MediaThumb
import com.wellsey.glimpse.ui.GalleryViewModel

/** Opens the Android share sheet for one or more items. */
fun shareItems(context: Context, items: List<MediaItem>) {
    if (items.isEmpty()) return
    val uris = ArrayList<Uri>(items.map { it.uri })
    val type = when {
        items.all { !it.isVideo } -> "image/*"
        items.all { it.isVideo } -> "video/*"
        else -> "*/*"
    }
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    }.apply {
        this.type = type
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, null))
}

private enum class FolderAction { Move, Copy }

/**
 * Top bar shown while items are selected: share, favourite, move/copy, trash, select all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(
    vm: GalleryViewModel,
    allItems: List<MediaItem>,
    selected: Set<Long>,
    favorites: Set<Long>,
    albums: List<Album>,
    onSelectionChange: (Set<Long>) -> Unit,
    windowInsets: WindowInsets? = null,
) {
    val context = LocalContext.current
    val chosen = allItems.filter { it.id in selected }
    val allFav = chosen.isNotEmpty() && chosen.all { it.id in favorites }
    var menu by remember { mutableStateOf(false) }
    var folderAction by remember { mutableStateOf<FolderAction?>(null) }

    TopAppBar(
        windowInsets = windowInsets ?: TopAppBarDefaults.windowInsets,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        navigationIcon = {
            IconButton(onClick = { onSelectionChange(emptySet()) }) {
                Icon(Icons.Filled.Close, "Clear selection")
            }
        },
        title = { Text("${selected.size} selected") },
        actions = {
            IconButton(onClick = { shareItems(context, chosen) }) {
                Icon(Icons.Filled.Share, "Share")
            }
            IconButton(onClick = { vm.setFavorite(chosen, !allFav) }) {
                Icon(
                    if (allFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    if (allFav) "Remove from favourites" else "Add to favourites"
                )
            }
            IconButton(onClick = {
                vm.moveToTrash(chosen) { onSelectionChange(emptySet()) }
            }) {
                Icon(Icons.Filled.Delete, "Move to trash")
            }
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, "More")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Select all") },
                    leadingIcon = { Icon(Icons.Filled.SelectAll, null) },
                    onClick = {
                        menu = false
                        onSelectionChange(allItems.map { it.id }.toSet())
                    },
                )
                DropdownMenuItem(
                    text = { Text("Move to album…") },
                    leadingIcon = { Icon(Icons.Filled.Folder, null) },
                    onClick = { menu = false; folderAction = FolderAction.Move },
                )
                DropdownMenuItem(
                    text = { Text("Copy to album…") },
                    leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                    onClick = { menu = false; folderAction = FolderAction.Copy },
                )
            }
        },
    )

    folderAction?.let { action ->
        AlbumPickerDialog(
            title = if (action == FolderAction.Move) "Move to" else "Copy to",
            albums = albums,
            onDismiss = { folderAction = null },
            onPick = { path ->
                folderAction = null
                if (action == FolderAction.Move) vm.move(chosen, path) else vm.copy(chosen, path)
                onSelectionChange(emptySet())
            },
        )
    }
}

/** Pick an existing album, or type a name for a new one (created under Pictures/). */
@Composable
fun AlbumPickerDialog(
    title: String,
    albums: List<Album>,
    onDismiss: () -> Unit,
    onPick: (relativePath: String) -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    val valid = newName.isNotBlank() && newName.none { it in "/\\:*?\"<>|" }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (creating) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New album name") },
                    singleLine = true,
                    supportingText = { Text("Saved in Pictures/") },
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { creating = true }
                                .padding(vertical = 10.dp),
                        ) {
                            Icon(
                                Icons.Filled.CreateNewFolder, null,
                                modifier = Modifier.size(44.dp).padding(8.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text("New album", style = MaterialTheme.typography.bodyLarge)
                        }
                        HorizontalDivider()
                    }
                    items(albums.filter { it.relativePath.isNotBlank() }, key = { it.bucketId }) { album ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(album.relativePath) }
                                .padding(vertical = 6.dp),
                        ) {
                            AsyncImage(
                                model = MediaThumb(album.cover.uri, album.cover.dateModified, 160),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(album.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    album.relativePath.trimEnd('/'),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(enabled = valid, onClick = { onPick("Pictures/${newName.trim()}/") }) {
                    Text("OK")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (creating) creating = false else onDismiss() }) {
                Text(if (creating) "Back" else "Cancel")
            }
        },
    )
}
