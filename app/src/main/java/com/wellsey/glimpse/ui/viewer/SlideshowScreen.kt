package com.wellsey.glimpse.ui.viewer

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wellsey.glimpse.ui.GalleryViewModel
import com.wellsey.glimpse.util.formatDateTime
import kotlinx.coroutines.delay

/**
 * Full-screen slideshow of photos (videos are skipped). Photos fade from one to the next;
 * tap to show the controls for pausing, skipping and changing the speed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlideshowScreen(vm: GalleryViewModel, onClose: () -> Unit) {
    val items = vm.slideshowItems
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val context = LocalContext.current
    val seconds by vm.settings.slideSeconds.collectAsStateWithLifecycle()
    var index by remember { mutableIntStateOf(vm.slideshowStart.coerceIn(0, items.lastIndex)) }
    var playing by remember { mutableStateOf(true) }
    var controls by remember { mutableStateOf(false) }
    BackHandler(onBack = onClose)

    // Full screen, and keep the screen on while it plays.
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            val c = WindowCompat.getInsetsController(window, view)
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            if (window != null) {
                WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.systemBars())
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    LaunchedEffect(index, playing, seconds) {
        if (!playing) return@LaunchedEffect
        delay(seconds * 1000L)
        index = (index + 1) % items.size
    }
    // Hide the controls again after a few seconds.
    LaunchedEffect(controls) {
        if (controls) {
            delay(4000); controls = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures(onTap = { controls = !controls }) }
    ) {
        Crossfade(targetState = index, animationSpec = tween(700), label = "slide") { i ->
            val item = items[i]
            AsyncImage(
                model = ImageRequest.Builder(context).data(item.uri).size(2560).build(),
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Load the next photo early so the change is smooth.
        val next = items[(index + 1) % items.size]
        AsyncImage(
            model = ImageRequest.Builder(context).data(next.uri).size(2560).build(),
            contentDescription = null,
            modifier = Modifier.padding(0.dp).fillMaxSize().background(Color.Transparent),
            alpha = 0f,
        )

        AnimatedVisibility(controls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x99000000))
                    .statusBarsPadding()
                    .padding(4.dp),
            ) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(formatDateTime(items[index].dateTaken), color = Color.White, style = MaterialTheme.typography.titleSmall)
                    Text("${index + 1} of ${items.size}", color = Color(0xCCFFFFFF), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        AnimatedVisibility(controls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x99000000))
                    .navigationBarsPadding()
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { index = (index - 1 + items.size) % items.size; controls = true }) {
                        Icon(Icons.Filled.SkipPrevious, "Previous", tint = Color.White)
                    }
                    IconButton(onClick = { playing = !playing; controls = true }) {
                        Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pause" else "Play", tint = Color.White)
                    }
                    IconButton(onClick = { index = (index + 1) % items.size; controls = true }) {
                        Icon(Icons.Filled.SkipNext, "Next", tint = Color.White)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 4, 8, 15).forEach { s ->
                        FilterChip(
                            selected = seconds == s,
                            onClick = { vm.settings.setSlideSeconds(s); controls = true },
                            label = { Text("${s}s") },
                        )
                    }
                }
            }
        }
    }
}
