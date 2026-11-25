package com.wellsey.glimpse.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wellsey.glimpse.data.MediaItem
import com.wellsey.glimpse.ui.GalleryViewModel
import com.wellsey.glimpse.ui.components.MediaGrid
import com.wellsey.glimpse.ui.components.SelectionTopBar

/**
 * A titled grid of photos with multi-select. Used for the main timeline, albums,
 * favourites, videos and map places.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    vm: GalleryViewModel,
    title: String,
    items: List<MediaItem>,
    onOpen: (List<MediaItem>, Int) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    grouped: Boolean = true,
    emptyText: String = "Nothing here yet",
    loading: Boolean = false,
    windowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    gridState: LazyGridState = rememberLazyGridState(),
    showTopBarWhenIdle: Boolean = true,
    /** Start with everything selected (used by "Old screenshots"). */
    preselectAll: Boolean = false,
    topBarWindowInsets: WindowInsets? = null,
    actions: @Composable RowScope.() -> Unit = {},
    header: (LazyGridScope.() -> Unit)? = null,
) {
    val favorites by vm.favorites.ids.collectAsStateWithLifecycle()
    val columns by vm.settings.columns.collectAsStateWithLifecycle()
    val albums by vm.albums.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var preselected by remember { mutableStateOf(false) }
    LaunchedEffect(items) {
        if (preselectAll && !preselected && items.isNotEmpty()) {
            selected = items.map { it.id }.toSet()
            preselected = true
        }
    }

    // Drop selections for items that disappeared (deleted, moved out of this album).
    val visibleSelection = remember(selected, items) {
        if (selected.isEmpty()) selected else {
            val ids = items.mapTo(HashSet()) { it.id }
            selected.filterTo(HashSet()) { it in ids }
        }
    }

    BackHandler(enabled = visibleSelection.isNotEmpty()) { selected = emptySet() }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = windowInsets,
        topBar = {
            if (visibleSelection.isNotEmpty()) {
                SelectionTopBar(
                    vm = vm,
                    allItems = items,
                    selected = visibleSelection,
                    favorites = favorites,
                    albums = albums,
                    onSelectionChange = { selected = it },
                    windowInsets = topBarWindowInsets,
                )
            } else if (showTopBarWhenIdle) {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                            }
                        }
                    },
                    actions = actions,
                )
            }
        },
    ) { padding ->
        when {
            loading && items.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            items.isEmpty() && header == null -> EmptyState(emptyText, Modifier.padding(padding))
            else -> MediaGrid(
                items = items,
                columns = columns,
                onColumnsChange = vm.settings::setColumns,
                selected = visibleSelection,
                favorites = favorites,
                grouped = grouped,
                state = gridState,
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 8.dp,
                ),
                onClick = { index ->
                    if (visibleSelection.isNotEmpty()) {
                        val id = items[index].id
                        selected = if (id in visibleSelection) visibleSelection - id else visibleSelection + id
                    } else {
                        onOpen(items, index)
                    }
                },
                onLongClick = { index -> selected = visibleSelection + items[index].id },
                onSelectGroup = { group ->
                    val ids = group.map { it.id }
                    selected = if (ids.all { it in visibleSelection }) visibleSelection - ids.toSet()
                    else visibleSelection + ids
                },
                header = header,
            )
        }
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
