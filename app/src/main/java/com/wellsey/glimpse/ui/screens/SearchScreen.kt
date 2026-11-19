package com.wellsey.glimpse.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wellsey.glimpse.data.MediaItem
import com.wellsey.glimpse.ui.GalleryViewModel
import com.wellsey.glimpse.util.localDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.format.TextStyle
import java.util.Locale

/**
 * Search by file name, album, month, year, or type. Every word must match, e.g.
 * "camera june 2024", "screenshots", "videos holiday", "favourites 2023".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(vm: GalleryViewModel, onOpen: (List<MediaItem>, Int) -> Unit) {
    val media by vm.media.collectAsStateWithLifecycle()
    val favorites by vm.favorites.ids.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }

    val results by produceState(initialValue = emptyList<MediaItem>(), query, media, favorites) {
        if (query.isBlank()) {
            value = emptyList()
        } else {
            delay(150) // wait for typing to pause
            value = withContext(Dispatchers.Default) { search(media, favorites, query) }
        }
    }
    val years = remember(media) {
        media.map { localDate(it.dateTaken).year }.distinct().sortedDescending()
    }
    val suggestions = remember(years) {
        listOf("Videos", "Favourites", "Screenshots", "Camera") + years.map { it.toString() }
    }

    Column(Modifier.statusBarsPadding()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search by name, album, month, year…") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Clear") }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(suggestions) { s ->
                AssistChip(onClick = { query = s }, label = { Text(s) })
            }
        }
        when {
            query.isBlank() -> EmptyState("Try a month like \"June\", a year, an album name, or \"videos\".")
            results.isEmpty() -> EmptyState("No matches for \"$query\"")
            else -> {
                Text(
                    "${results.size} results",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                CollectionScreen(
                    vm = vm,
                    title = "Results",
                    items = results,
                    onOpen = onOpen,
                    windowInsets = WindowInsets(0.dp),
                    topBarWindowInsets = WindowInsets(0.dp),
                    showTopBarWhenIdle = false,
                )
            }
        }
    }
}

private val monthNames: List<Pair<Int, List<String>>> = (1..12).map { m ->
    val month = java.time.Month.of(m)
    m to listOf(
        month.getDisplayName(TextStyle.FULL, Locale.getDefault()).lowercase(),
        month.getDisplayName(TextStyle.SHORT, Locale.getDefault()).lowercase().trimEnd('.'),
        month.getDisplayName(TextStyle.FULL, Locale.ENGLISH).lowercase(),
        month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).lowercase(),
    ).distinct()
}

private fun search(media: List<MediaItem>, favorites: Set<Long>, query: String): List<MediaItem> {
    val tokens = query.lowercase().split(' ', ',').map { it.trim() }.filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return emptyList()

    val matchers: List<(MediaItem) -> Boolean> = tokens.map { token ->
        val month = monthNames.firstOrNull { (_, names) -> names.any { it == token } }?.first
        val year = token.toIntOrNull()?.takeIf { it in 1900..2100 }
        val matcher: (MediaItem) -> Boolean = when {
            token in listOf("video", "videos") -> { m -> m.isVideo }
            token in listOf("photo", "photos", "picture", "pictures", "image", "images") -> { m -> !m.isVideo }
            token in listOf("favourite", "favourites", "favorite", "favorites", "fav", "favs") ->
                { m -> m.id in favorites }
            token in listOf("screenshot", "screenshots") ->
                { m -> m.bucketName.contains("screenshot", true) || m.name.contains("screenshot", true) }
            token == "camera" -> { m -> m.relativePath.startsWith("DCIM/Camera", true) }
            month != null -> { m -> localDate(m.dateTaken).monthValue == month }
            year != null -> { m -> localDate(m.dateTaken).year == year }
            else -> { m ->
                m.name.contains(token, true) ||
                    m.bucketName.contains(token, true) ||
                    m.relativePath.contains(token, true)
            }
        }
        matcher
    }
    return media.filter { m -> matchers.all { it(m) } }
}
