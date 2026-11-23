package com.example.smartgallery

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MotionPhotosOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import java.time.format.DateTimeFormatter

private val DAY_FMT = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(vm: GalleryViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    val shown = remember(state.items, state.filter) { state.items.filter(state.filter::accepts) }
    val groups = remember(shown) { shown.groupBy { it.day } }
    val indexByKey = remember(shown) { shown.withIndex().associate { it.value.key to it.index } }
    val gridState = rememberLazyGridState()
    var viewer by remember { mutableStateOf<Int?>(null) }

    val open = viewer
    if (open != null) {
        ViewerScreen(shown, open, vm) { viewer = null }
        return
    }

    Scaffold(topBar = {
        Column {
            TopAppBar(title = { Text("Smart Gallery") })
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MediaFilter.entries.forEach { f ->
                    FilterChip(selected = f == state.filter, onClick = { vm.setFilter(f) }, label = { Text(f.label) })
                }
            }
        }
    }) { pad ->
        Box(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(110.dp),
                contentPadding = pad,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                groups.forEach { (day, list) ->
                    item(key = "h$day", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            day.format(DAY_FMT),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(12.dp, 16.dp, 12.dp, 6.dp),
                        )
                    }
                    items(list, key = { it.key }) { item ->
                        Thumb(item) { viewer = indexByKey[item.key] }
                    }
                }
            }
            if (state.loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
            else if (shown.isEmpty()) Text("No media found", Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun Thumb(item: GalleryItem, onClick: () -> Unit) {
    Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(4.dp)).clickable(onClick = onClick)) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (item.isVideo) MediaBadge(item.durationLabel(), Icons.Filled.PlayArrow, Modifier.align(Alignment.BottomStart))
        if (item.isLive) MediaBadge("LIVE", Icons.Filled.MotionPhotosOn, Modifier.align(Alignment.TopStart))
    }
}

@Composable
internal fun MediaBadge(text: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Row(
        modifier
            .padding(4.dp)
            .background(Color.Black.copy(alpha = .55f), RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(3.dp))
        Text(text, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}
