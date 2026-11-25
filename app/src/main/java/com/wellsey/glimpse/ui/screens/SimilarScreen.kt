package com.wellsey.glimpse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wellsey.glimpse.data.MediaItem
import com.wellsey.glimpse.ui.GalleryViewModel
import com.wellsey.glimpse.ui.components.MediaCell
import com.wellsey.glimpse.util.formatDateTime
import com.wellsey.glimpse.util.formatSize

/**
 * Groups of near-identical photos (bursts and retakes). The sharpest photo in each group is
 * marked "Best"; the rest start selected so you can clear them out in one go.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimilarScreen(vm: GalleryViewModel, onBack: () -> Unit, onOpen: (List<MediaItem>, Int) -> Unit) {
    val groups by vm.similar.collectAsStateWithLifecycle()
    val progress by vm.similarProgress.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var initialised by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { if (vm.similar.value == null) vm.findSimilar() }
    LaunchedEffect(groups) {
        val g = groups ?: return@LaunchedEffect
        if (!initialised) {
            selected = g.flatMap { grp -> grp.items.filter { it.id != grp.best.id } }.map { it.id }.toSet()
            initialised = true
        }
    }
    val all = groups.orEmpty().flatMap { it.items }
    val chosen = all.filter { it.id in selected }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text("Similar photos") },
                actions = {
                    IconButton(enabled = progress == null, onClick = { initialised = false; vm.findSimilar() }) {
                        Icon(Icons.Filled.Refresh, "Check again")
                    }
                },
            )
        },
        bottomBar = {
            if (chosen.isNotEmpty()) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = {
                            vm.moveToTrash(chosen) {
                                vm.removeFromSimilar(chosen.map { it.id }.toSet())
                                selected = emptySet()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Icon(Icons.Filled.Delete, null)
                        Text("  Move ${chosen.size} to trash · ${formatSize(chosen.sumOf { it.size })}")
                    }
                }
            }
        },
    ) { padding ->
        val list = groups
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                list == null || progress != null -> Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val p = progress
                    Text(
                        if (p == null || p.second == 0) "Looking for similar photos…"
                        else "Comparing photos… ${p.first} of ${p.second}. The first time takes a while; after that it's quick.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (p != null && p.second > 0) LinearProgressIndicator(progress = { p.first.toFloat() / p.second }, modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                list.isEmpty() -> Text(
                    "No similar photos found. Bursts and retakes taken within a couple of minutes of each other appear here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 16.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "The sharpest photo in each group is marked Best and kept. The others are selected; tap to change.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                    list.forEach { group ->
                        item(span = { GridItemSpan(maxLineSpan) }, key = "g${group.items.first().id}") {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp)) {
                                Text(formatDateTime(group.items.first().dateTaken), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text("${group.items.size} photos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton(onClick = {
                                    val ids = group.items.map { it.id }
                                    selected = if (ids.any { it in selected }) selected - ids.toSet() else selected + ids.filter { it != group.best.id }
                                }) { Text(if (group.items.any { it.id in selected }) "Keep all" else "Select extras") }
                            }
                        }
                        items(group.items, key = { it.id }) { item ->
                            Box {
                                MediaCell(
                                    item = item,
                                    isSelected = item.id in selected,
                                    selectionMode = true,
                                    isFavorite = false,
                                    badge = null,
                                    onClick = { selected = if (item.id in selected) selected - item.id else selected + item.id },
                                    onLongClick = { onOpen(group.items, group.items.indexOf(item)) },
                                )
                                if (item.id == group.best.id) {
                                    Text(
                                        "Best",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
