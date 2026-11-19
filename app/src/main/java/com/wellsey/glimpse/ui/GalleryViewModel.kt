package com.wellsey.glimpse.ui

import android.app.Application
import android.content.IntentSender
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wellsey.glimpse.data.Album
import com.wellsey.glimpse.data.FavoritesStore
import com.wellsey.glimpse.data.Geo
import com.wellsey.glimpse.data.LocationIndex
import com.wellsey.glimpse.data.MediaItem
import com.wellsey.glimpse.data.MediaRepository
import com.wellsey.glimpse.data.SettingsStore
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A system confirm dialog the UI needs to show (trash, delete, edit someone else's file). */
class SystemRequest(val sender: IntentSender, val onResult: (Boolean) -> Unit)

enum class ViewerMode { Normal, Trash, External }

@OptIn(FlowPreview::class)
class GalleryViewModel(app: Application) : AndroidViewModel(app) {

    val repo = MediaRepository(app)
    val favorites = FavoritesStore(app)
    val settings = SettingsStore(app)
    private val locationIndex = LocationIndex(app)

    private val _media = MutableStateFlow<List<MediaItem>>(emptyList())
    val media: StateFlow<List<MediaItem>> = _media.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _trash = MutableStateFlow<List<MediaItem>>(emptyList())
    val trash: StateFlow<List<MediaItem>> = _trash.asStateFlow()

    val albums: StateFlow<List<Album>> = media.map(::buildAlbums)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val favoriteItems: StateFlow<List<MediaItem>> = combine(media, favorites.ids) { m, ids ->
        m.filter { it.id in ids }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // --- Map ---
    private val _geo = MutableStateFlow<Map<Long, Geo>>(emptyMap())
    val geo: StateFlow<Map<Long, Geo>> = _geo.asStateFlow()
    private val _geoProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val geoProgress: StateFlow<Pair<Int, Int>?> = _geoProgress.asStateFlow()
    private var geoJob: Job? = null

    // --- Viewer / editor / ad-hoc collections (set right before navigating) ---
    var viewerItems by mutableStateOf<List<MediaItem>>(emptyList())
    var viewerStart by mutableStateOf(0)
    var viewerMode by mutableStateOf(ViewerMode.Normal)
    var editorItem by mutableStateOf<MediaItem?>(null)
    var collectionTitle by mutableStateOf("")
    var collectionIds by mutableStateOf<Set<Long>>(emptySet())

    // --- One-off events for the UI ---
    private val _requests = Channel<SystemRequest>(Channel.BUFFERED)
    val requests = _requests.receiveAsFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    private var started = false

    /** Call once media permission is granted. Loads the library and watches for changes. */
    fun start() {
        if (started) return
        started = true
        refresh()
        viewModelScope.launch {
            repo.changes().debounce(400).collect {
                refresh()
                if (_trash.value.isNotEmpty()) loadTrash()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _media.value = repo.loadMedia()
            _loaded.value = true
        }
    }

    fun loadTrash() {
        viewModelScope.launch { _trash.value = repo.loadMedia(trashed = true) }
    }

    fun indexLocations() {
        if (geoJob?.isActive == true) return
        geoJob = viewModelScope.launch {
            val items = media.value
            if (items.isEmpty()) return@launch
            _geo.value = locationIndex.index(items) { done, total ->
                _geoProgress.value = if (done >= total) null else done to total
            }
            _geoProgress.value = null
        }
    }

    fun openViewer(items: List<MediaItem>, index: Int, mode: ViewerMode = ViewerMode.Normal) {
        viewerItems = items
        viewerStart = index
        viewerMode = mode
    }

    fun openCollection(title: String, items: List<MediaItem>) {
        collectionTitle = title
        collectionIds = items.map { it.id }.toSet()
    }

    fun message(text: String) {
        _messages.tryEmit(text)
    }

    private fun request(sender: IntentSender, onResult: (Boolean) -> Unit) {
        _requests.trySend(SystemRequest(sender, onResult))
    }

    // --- Actions ---

    fun setFavorite(items: List<MediaItem>, favorite: Boolean) {
        favorites.set(items.filterNot { it.isExternal }.map { it.id }, favorite)
    }

    fun toggleFavorite(item: MediaItem) {
        if (!item.isExternal) favorites.toggle(item.id)
    }

    fun moveToTrash(items: List<MediaItem>, onDone: () -> Unit = {}) {
        val targets = items.filterNot { it.isExternal }
        if (targets.isEmpty()) return
        request(repo.trashRequest(targets.map { it.uri }, trash = true)) { ok ->
            if (ok) {
                message(plural(targets.size, "item") + " moved to trash")
                _media.value = _media.value.filterNot { m -> targets.any { it.id == m.id } }
                refresh()
                onDone()
            }
        }
    }

    fun restore(items: List<MediaItem>, onDone: () -> Unit = {}) {
        if (items.isEmpty()) return
        request(repo.trashRequest(items.map { it.uri }, trash = false)) { ok ->
            if (ok) {
                message(plural(items.size, "item") + " restored")
                refresh()
                loadTrash()
                onDone()
            }
        }
    }

    fun deleteForever(items: List<MediaItem>, onDone: () -> Unit = {}) {
        if (items.isEmpty()) return
        request(repo.deleteRequest(items.map { it.uri })) { ok ->
            if (ok) {
                message(plural(items.size, "item") + " deleted")
                favorites.set(items.map { it.id }, false)
                loadTrash()
                onDone()
            }
        }
    }

    fun move(items: List<MediaItem>, relativePath: String) {
        val targets = items.filterNot { it.isExternal || it.relativePath == relativePath }
        if (targets.isEmpty()) return
        request(repo.writeRequest(targets.map { it.uri })) { ok ->
            if (!ok) return@request
            viewModelScope.launch {
                val moved = repo.move(targets, relativePath)
                val skipped = targets.size - moved
                message(
                    plural(moved, "item") + " moved to ${folderName(relativePath)}" +
                        if (skipped > 0) " ($skipped couldn't be moved)" else ""
                )
                refresh()
            }
        }
    }

    fun copy(items: List<MediaItem>, relativePath: String) {
        val targets = items.filterNot { it.isExternal }
        if (targets.isEmpty()) return
        viewModelScope.launch {
            val copied = repo.copy(targets, relativePath)
            message(plural(copied, "item") + " copied to ${folderName(relativePath)}")
            refresh()
        }
    }

    fun saveEdited(source: MediaItem, bitmap: Bitmap, onSaved: (Uri?) -> Unit) {
        viewModelScope.launch {
            val base = source.name.substringBeforeLast('.').ifBlank { "photo" }
            val path = source.relativePath.ifBlank { "Pictures/Glimpse/" }
            var uri = repo.saveJpeg(bitmap, "${base}_edited.jpg", path, System.currentTimeMillis())
            if (uri == null && path != "Pictures/Glimpse/") {
                // Some folders don't accept new images; fall back to our own album.
                uri = repo.saveJpeg(bitmap, "${base}_edited.jpg", "Pictures/Glimpse/", System.currentTimeMillis())
            }
            message(if (uri != null) "Saved a copy" else "Couldn't save the edit")
            refresh()
            onSaved(uri)
        }
    }

    companion object {
        fun plural(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"

        fun folderName(relativePath: String) =
            relativePath.trimEnd('/').substringAfterLast('/').ifBlank { relativePath }

        fun buildAlbums(media: List<MediaItem>): List<Album> {
            val albums = media.groupBy { it.bucketId }.map { (id, list) ->
                val first = list.first() // list is newest-first
                Album(
                    bucketId = id,
                    name = first.bucketName.ifBlank { "Internal storage" },
                    relativePath = first.relativePath,
                    count = list.size,
                    cover = first,
                )
            }
            fun rank(a: Album) = when {
                a.relativePath.startsWith("DCIM/Camera", ignoreCase = true) -> 0
                a.name.equals("Screenshots", ignoreCase = true) -> 1
                else -> 2
            }
            return albums.sortedWith(compareBy<Album> { rank(it) }.thenByDescending { it.cover.dateTaken })
        }
    }
}
