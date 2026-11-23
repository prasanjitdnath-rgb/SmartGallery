package com.example.smartgallery

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MotionPhotosOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage

@Composable
fun ViewerScreen(items: List<GalleryItem>, startIndex: Int, vm: GalleryViewModel, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val start = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    val pager = rememberPagerState(start) { items.size }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(pager, Modifier.fillMaxSize(), key = { items[it].key }) { page ->
            val item = items[page]
            val current = pager.currentPage == page
            when {
                item.isVideo -> if (current) VideoPlayer(item) else Still(item)
                else -> PhotoPage(item, current, vm)
            }
        }
        IconButton(onClick = onClose, modifier = Modifier.statusBarsPadding()) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
        }
    }
}

@Composable
private fun Still(item: GalleryItem) = AsyncImage(
    model = item.uri,
    contentDescription = item.name,
    contentScale = ContentScale.Fit,
    modifier = Modifier.fillMaxSize(),
)

/** Photo page. For Live items: press-and-hold the photo (or tap the LIVE chip) to play the motion clip. */
@Composable
private fun PhotoPage(item: GalleryItem, current: Boolean, vm: GalleryViewModel) {
    var playing by remember(item.key) { mutableStateOf(false) }
    val source by produceState<Uri?>(null, item.key, current) {
        value = if (current && item.isLive) vm.liveSource(item) else null
    }
    LaunchedEffect(current) { if (!current) playing = false }

    Box(
        Modifier.fillMaxSize().pointerInput(source) {
            detectTapGestures(
                onLongPress = { if (source != null) playing = true },
                onPress = { tryAwaitRelease(); playing = false },
            )
        }
    ) {
        Still(item)
        if (playing) source?.let { LivePlayer(it) { playing = false } }
        if (item.isLive) {
            MediaBadge(
                if (playing) "PLAYING" else "LIVE · hold",
                Icons.Filled.MotionPhotosOn,
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)
                    .clickable { if (source != null) playing = true },
            )
        }
    }
}

/** Transparent, controller-less player layered over the still so playback "wakes" the photo. */
@OptIn(UnstableApi::class)
@Composable
private fun LivePlayer(source: Uri, onEnded: () -> Unit) {
    val ctx = LocalContext.current
    val ended by rememberUpdatedState(onEnded)
    val player = remember(source) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(source))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) ended()
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l); player.release() }
    }
    AndroidView(
        factory = {
            PlayerView(it).apply {
                useController = false
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        update = { it.player = player },
        modifier = Modifier.fillMaxSize(),
    )
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(item: GalleryItem) {
    val ctx = LocalContext.current
    val player = remember(item.key) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(item.uri))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = { PlayerView(it) },
        update = { it.player = player },
        modifier = Modifier.fillMaxSize(),
    )
}
