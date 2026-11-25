package com.wellsey.glimpse

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wellsey.glimpse.data.MediaItem
import com.wellsey.glimpse.ui.GalleryViewModel
import com.wellsey.glimpse.ui.SystemRequest
import com.wellsey.glimpse.ui.ViewerMode
import com.wellsey.glimpse.ui.editor.EditorScreen
import com.wellsey.glimpse.ui.screens.Access
import com.wellsey.glimpse.ui.screens.CollectionScreen
import com.wellsey.glimpse.ui.screens.FoldersScreen
import com.wellsey.glimpse.ui.screens.SimilarScreen
import com.wellsey.glimpse.ui.viewer.SlideshowScreen
import com.wellsey.glimpse.ui.screens.HomeScreen
import com.wellsey.glimpse.ui.screens.PermissionScreen
import com.wellsey.glimpse.ui.screens.TrashScreen
import com.wellsey.glimpse.ui.screens.mediaAccess
import com.wellsey.glimpse.ui.screens.mediaPermissions
import com.wellsey.glimpse.ui.theme.GlimpseTheme
import com.wellsey.glimpse.ui.viewer.ViewerScreen

class MainActivity : FragmentActivity() {

    private val vm: GalleryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Opened from another app ("Open with Glimpse") — show just that file.
        val external: MediaItem? = intent?.takeIf {
            it.action == Intent.ACTION_VIEW || it.action == MediaStore.ACTION_REVIEW
        }?.data?.let { vm.repo.externalItem(it, intent.type) }
        if (external != null && savedInstanceState == null) {
            vm.openViewer(listOf(external), 0, ViewerMode.External)
        }

        setContent {
            GlimpseTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    GlimpseRoot(vm, external)
                }
            }
        }
    }
}

private object Routes {
    const val HOME = "home"
    const val ALBUM = "album/{id}"
    const val FAVOURITES = "favourites"
    const val VIDEOS = "videos"
    const val TRASH = "trash"
    const val PLACE = "place"
    const val VIEWER = "viewer"
    const val EDITOR = "editor"
    const val FOLDERS = "folders"
    const val RECENT = "recent"
    const val OLD_SCREENSHOTS = "old-screenshots"
    const val SIMILAR = "similar"
    const val SLIDESHOW = "slideshow"
}

@Composable
private fun GlimpseRoot(vm: GalleryViewModel, external: MediaItem?) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    // --- System confirm dialogs (trash, delete, edit others' files) ---
    val pending = remember { mutableStateOf<SystemRequest?>(null) }
    val senderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        pending.value?.onResult?.invoke(result.resultCode == Activity.RESULT_OK)
        pending.value = null
    }
    LaunchedEffect(Unit) {
        vm.requests.collect { req ->
            pending.value = req
            senderLauncher.launch(IntentSenderRequest.Builder(req.sender).build())
        }
    }
    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    Box(Modifier.fillMaxSize()) {
        if (external != null) {
            ExternalViewer(vm, external)
        } else {
            Gallery(vm)
        }
        SnackbarHost(
            snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 80.dp),
        )
    }
}

@Composable
private fun ExternalViewer(vm: GalleryViewModel, item: MediaItem) {
    val activity = LocalContext.current as? Activity
    var editing by rememberSaveable { mutableStateOf(false) }
    if (editing) {
        EditorScreen(vm, item, onClose = { editing = false })
    } else {
        ViewerScreen(
            vm = vm,
            onBack = { activity?.finish() },
            onEdit = { vm.editorItem = it; editing = true },
        )
    }
}

@Composable
private fun Gallery(vm: GalleryViewModel) {
    val context = LocalContext.current
    var access by remember { mutableStateOf(mediaAccess(context)) }
    var askedBefore by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        askedBefore = true
        access = mediaAccess(context)
        if (access != Access.None) vm.refresh()
    }

    // Re-check when returning to the app: the user may have changed access in Settings.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val now = mediaAccess(context)
        if (now != access) access = now
        if (now != Access.None && vm.loaded.value) vm.refresh()
    }
    LaunchedEffect(access) {
        if (access != Access.None) vm.start()
    }

    if (access == Access.None) {
        PermissionScreen(askedBefore) { permissionLauncher.launch(mediaPermissions()) }
        return
    }

    val nav = rememberNavController()
    val openViewer: (List<MediaItem>, Int) -> Unit = { items, index ->
        vm.openViewer(items, index)
        nav.navigate(Routes.VIEWER)
    }

    /** A "Slideshow" button for a list of photos' top bar. */
    fun slideshowAction(items: List<MediaItem>): @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        if (items.any { !it.isVideo }) {
            IconButton(onClick = { vm.openSlideshow(items); nav.navigate(Routes.SLIDESHOW) }) {
                Icon(Icons.Filled.Slideshow, "Slideshow")
            }
        }
    }

    // Hidden folders lock again whenever Glimpse goes into the background.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.setUnlocked(false) }

    /** Opens a hidden, locked folder only after a fingerprint, face or screen-lock check. */
    fun unlockThen(bucketId: Long, action: () -> Unit) {
        if (!vm.isLocked(bucketId)) {
            action(); return
        }
        val activity = context as? FragmentActivity ?: return
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    vm.setUnlocked(true)
                    action()
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock hidden folders")
                .setSubtitle("Hidden folders are locked in Glimpse")
                .setAllowedAuthenticators(Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL)
                .build()
        )
    }

    NavHost(
        navController = nav,
        startDestination = Routes.HOME,
        enterTransition = { fadeIn(tween(160)) },
        exitTransition = { fadeOut(tween(160)) },
        popEnterTransition = { fadeIn(tween(160)) },
        popExitTransition = { fadeOut(tween(160)) },
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                vm = vm,
                access = access,
                onRequestMoreAccess = { permissionLauncher.launch(mediaPermissions()) },
                onOpen = openViewer,
                onOpenAlbum = { id -> unlockThen(id) { nav.navigate("album/$id") } },
                onOpenFavourites = { nav.navigate(Routes.FAVOURITES) },
                onOpenVideos = { nav.navigate(Routes.VIDEOS) },
                onOpenTrash = { nav.navigate(Routes.TRASH) },
                onOpenPlace = { title, items ->
                    vm.openCollection(title, items)
                    nav.navigate(Routes.PLACE)
                },
                onChooseFolders = { nav.navigate(Routes.FOLDERS) },
                onOpenRecent = { nav.navigate(Routes.RECENT) },
                onOpenOldScreenshots = { nav.navigate(Routes.OLD_SCREENSHOTS) },
                onOpenSimilar = { nav.navigate(Routes.SIMILAR) },
            )
        }
        composable(Routes.RECENT) {
            val items by vm.recentlyAdded.collectAsStateWithLifecycle()
            CollectionScreen(
                vm = vm,
                title = "Recently added",
                items = items,
                onOpen = openViewer,
                onBack = { nav.popBackStack() },
                emptyText = "Nothing new in the last 7 days",
                actions = slideshowAction(items),
            )
        }
        composable(Routes.OLD_SCREENSHOTS) {
            val items by vm.oldScreenshots.collectAsStateWithLifecycle()
            CollectionScreen(
                vm = vm,
                title = "Old screenshots",
                items = items,
                onOpen = openViewer,
                onBack = { nav.popBackStack() },
                emptyText = "No screenshots older than 30 days",
                preselectAll = true,
            )
        }
        composable(Routes.SIMILAR) {
            SimilarScreen(vm, onBack = { nav.popBackStack() }, onOpen = openViewer)
        }
        composable(Routes.SLIDESHOW) {
            SlideshowScreen(vm, onClose = { nav.popBackStack() })
        }
        composable(Routes.FOLDERS) {
            FoldersScreen(vm, onBack = { nav.popBackStack() })
        }
        composable(
            Routes.ALBUM,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            val media by vm.media.collectAsStateWithLifecycle()
            val items = remember(media, id) { media.filter { it.bucketId == id } }
            val albums by vm.albums.collectAsStateWithLifecycle()
            val name = albums.firstOrNull { it.bucketId == id }?.name ?: "Album"
            CollectionScreen(
                vm = vm,
                title = name,
                items = items,
                onOpen = openViewer,
                onBack = { nav.popBackStack() },
                emptyText = "This album is empty",
                actions = slideshowAction(items),
            )
        }
        composable(Routes.FAVOURITES) {
            val items by vm.favoriteItems.collectAsStateWithLifecycle()
            CollectionScreen(
                vm = vm,
                title = "Favourites",
                items = items,
                onOpen = openViewer,
                onBack = { nav.popBackStack() },
                emptyText = "Tap the heart on any photo to add it here",
                actions = slideshowAction(items),
            )
        }
        composable(Routes.VIDEOS) {
            val media by vm.visibleMedia.collectAsStateWithLifecycle()
            val items = remember(media) { media.filter { it.isVideo } }
            CollectionScreen(
                vm = vm,
                title = "Videos",
                items = items,
                onOpen = openViewer,
                onBack = { nav.popBackStack() },
                emptyText = "No videos",
            )
        }
        composable(Routes.PLACE) {
            val media by vm.media.collectAsStateWithLifecycle()
            val ids = vm.collectionIds
            val items = remember(media, ids) { media.filter { it.id in ids } }
            CollectionScreen(
                vm = vm,
                title = vm.collectionTitle,
                items = items,
                onOpen = openViewer,
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.TRASH) {
            TrashScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onOpen = { items, index ->
                    vm.openViewer(items, index, ViewerMode.Trash)
                    nav.navigate(Routes.VIEWER)
                },
            )
        }
        composable(Routes.VIEWER) {
            ViewerScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onSlideshow = { nav.navigate(Routes.SLIDESHOW) },
                onEdit = { item ->
                    vm.editorItem = item
                    nav.navigate(Routes.EDITOR)
                },
            )
        }
        composable(Routes.EDITOR) {
            val item = vm.editorItem
            if (item == null) {
                LaunchedEffect(Unit) { nav.popBackStack() }
            } else {
                EditorScreen(vm, item, onClose = { nav.popBackStack() })
            }
        }
    }
}
