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
import com.wellsey.glimpse.ui.editor.decodeBitmap
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import coil.imageLoader
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
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

    /** Everything except the folders you've chosen to hide. Used by Photos, Search, Map and Videos. */
    val visibleMedia: StateFlow<List<MediaItem>> = combine(media, settings.hiddenFolders) { m, hidden ->
        if (hidden.isEmpty()) m else m.filter { it.bucketId !in hidden }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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

    // --- Editor preview, decoded ahead of time so Edit opens instantly ---
    private var previewUri: Uri? = null
    private var previewJob: Deferred<Bitmap?>? = null

    /** Starts (or reuses) decoding the editor's working copy of [item]. */
    fun editPreview(item: MediaItem): Deferred<Bitmap?> {
        val existing = previewJob
        if (existing != null && previewUri == item.uri && !existing.isCancelled) return existing
        existing?.cancel()
        previewUri = item.uri
        val job = viewModelScope.async(Dispatchers.IO) {
            runCatching { decodeBitmap(getApplication(), item.uri, EDIT_PREVIEW_MAX) }.getOrNull()
        }
        previewJob = job
        return job
    }

    /** The decoded preview for [uri] if it's already finished, else null. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun readyPreview(uri: Uri): Bitmap? {
        val job = previewJob ?: return null
        if (previewUri != uri || !job.isCompleted || job.isCancelled) return null
        return runCatching { job.getCompleted() }.getOrNull()
    }

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
            // Keep the original's date, camera details and location on the copy.
            if (uri != null && !source.isExternal) repo.copyExif(source.uri, uri, bitmap.width, bitmap.height)
            message(if (uri != null) "Saved a copy" else "Couldn't save the edit")
            refresh()
            onSaved(uri)
        }
    }

    /** Whether "Replace original" is possible: Glimpse can re-save JPEG, PNG and WebP photos. */
    fun canReplace(item: MediaItem): Boolean =
        !item.isExternal && item.mimeType in setOf("image/jpeg", "image/png", "image/webp")

    /** Saves over the original photo. Android asks you to confirm for photos made by other apps. */
    fun replaceOriginal(source: MediaItem, bitmap: Bitmap, onDone: (Boolean) -> Unit) {
        request(repo.writeRequest(listOf(source.uri))) { ok ->
            if (!ok) {
                onDone(false); return@request
            }
            viewModelScope.launch {
                val saved = repo.overwrite(source, bitmap)
                if (saved) {
                    repo.rescan(source.uri)
                    // Forget cached pictures of the old version so the new one shows everywhere.
                    getApplication<Application>().imageLoader.memoryCache?.clear()
                    previewJob?.cancel(); previewJob = null; previewUri = null
                    val now = System.currentTimeMillis() / 1000
                    val fresh = repo.loadMedia().map {
                        // If Android hasn't updated the date yet, mark it changed ourselves.
                        if (it.id == source.id && it.dateModified <= source.dateModified) it.copy(dateModified = now) else it
                    }
                    _media.value = fresh
                    viewerItems = viewerItems.map { v -> fresh.firstOrNull { it.id == v.id } ?: v }
                    message("Saved over the original")
                } else {
                    message("Couldn't save over the original. Try Save as copy.")
                }
                onDone(saved)
            }
        }
    }

    companion object {
        const val EDIT_PREVIEW_MAX = 1440

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
