package com.wellsey.glimpse.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wellsey.glimpse.data.MediaItem
import com.wellsey.glimpse.ui.GalleryViewModel
import com.wellsey.glimpse.ui.components.MediaGrid
import kotlin.math.ceil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    vm: GalleryViewModel,
    onBack: () -> Unit,
    onOpen: (List<MediaItem>, Int) -> Unit,
) {
    LaunchedEffect(Unit) { vm.loadTrash() }
    val items by vm.trash.collectAsStateWithLifecycle()
    val columns by vm.settings.columns.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var confirmEmpty by remember { mutableStateOf(false) }
    val chosen = items.filter { it.id in selected }

    BackHandler(enabled = selected.isNotEmpty()) { selected = emptySet() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selected.isEmpty()) "Trash" else "${selected.size} selected") },
                navigationIcon = {
                    if (selected.isEmpty()) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    } else {
                        IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Filled.Close, "Clear") }
                    }
                },
                actions = {
                    if (selected.isNotEmpty()) {
                        IconButton(onClick = { vm.restore(chosen) { selected = emptySet() } }) {
                            Icon(Icons.Filled.Restore, "Restore")
                        }
                        IconButton(onClick = { vm.deleteForever(chosen) { selected = emptySet() } }) {
                            Icon(Icons.Filled.DeleteForever, "Delete forever")
                        }
                    } else if (items.isNotEmpty()) {
                        TextButton(onClick = { confirmEmpty = true }) { Text("Empty") }
                    }
                },
            )
        },
    ) { padding ->
        if (items.isEmpty()) {
            EmptyState("Trash is empty", Modifier.padding(padding))
        } else {
            MediaGrid(
                items = items,
                columns = columns,
                onColumnsChange = vm.settings::setColumns,
                selected = selected,
                favorites = emptySet(),
                grouped = false,
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 8.dp,
                ),
                badge = { daysLeft(it) },
                onClick = { i ->
                    val id = items[i].id
                    if (selected.isNotEmpty()) {
                        selected = if (id in selected) selected - id else selected + id
                    } else {
                        onOpen(items, i)
                    }
                },
                onLongClick = { i -> selected = selected + items[i].id },
                onSelectGroup = {},
                header = {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "Items are deleted for good after 30 days. Long-press to select.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                },
            )
        }
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("Empty trash?") },
            text = { Text("All ${items.size} items will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmEmpty = false
                    vm.deleteForever(items)
                }) { Text("Delete all") }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmpty = false }) { Text("Cancel") }
            },
        )
    }
}

private fun daysLeft(item: MediaItem): String? {
    if (item.dateExpires <= 0) return null
    val secs = item.dateExpires - System.currentTimeMillis() / 1000
    val days = ceil(secs / 86_400.0).toInt().coerceAtLeast(0)
    return if (days == 1) "1 day" else "$days days"
}
