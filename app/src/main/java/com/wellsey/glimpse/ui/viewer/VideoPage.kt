package com.wellsey.glimpse.ui.viewer

import android.view.View
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.wellsey.glimpse.data.MediaItem
import com.wellsey.glimpse.data.MediaThumb

/**
 * A video page in the viewer. Only the page on screen gets a real player;
 * neighbouring pages show a poster frame so swiping stays smooth.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPage(
    item: MediaItem,
    isCurrent: Boolean,
    onChromeChange: (Boolean) -> Unit,
    onTapPoster: () -> Unit,
) {
    if (!isCurrent) {
        Box(Modifier.fillMaxSize().clickable(onClick = onTapPoster), contentAlignment = Alignment.Center) {
            AsyncImage(
                model = MediaThumb(item.uri, item.dateModified, 1024),
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            Icon(
                Icons.Filled.PlayArrow, null, tint = Color.White,
                modifier = Modifier
                    .size(64.dp)
                    .background(Color(0x66000000), CircleShape),
            )
        }
        return
    }

    val context = LocalContext.current
    val chrome by rememberUpdatedState(onChromeChange)
    val player = remember(item.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoMediaItem.fromUri(item.uri))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { player.pause() }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                setShowNextButton(false)
                setShowPreviousButton(false)
                setShowFastForwardButton(false)
                setShowRewindButton(false)
                controllerAutoShow = true
                setControllerVisibilityListener(
                    PlayerView.ControllerVisibilityListener { visibility ->
                        chrome(visibility == View.VISIBLE)
                    }
                )
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}
