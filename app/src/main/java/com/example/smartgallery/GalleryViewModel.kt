package com.example.smartgallery

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

enum class MediaFilter(val label: String) {
    ALL("All"), PHOTOS("Photos"), VIDEOS("Videos"), LIVE("Live");

    fun accepts(i: GalleryItem) = when (this) {
        ALL -> true
        PHOTOS -> !i.isVideo
        VIDEOS -> i.isVideo
        LIVE -> i.isLive
    }
}

data class GalleryState(
    val items: List<GalleryItem> = emptyList(),
    val filter: MediaFilter = MediaFilter.ALL,
    val loading: Boolean = true,
)

class GalleryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = MediaRepository(app)
    private val motionCache = ConcurrentHashMap<String, Live>()
    private val _state = MutableStateFlow(GalleryState())
    val state: StateFlow<GalleryState> = _state
    private var job: Job? = null

    fun setFilter(f: MediaFilter) = _state.update { it.copy(filter = f) }

    fun refresh() {
        job?.cancel()
        job = viewModelScope.launch {
            // 1) fast: show everything + iPhone Live Photos immediately
            val base = repo.load().map { i ->
                motionCache[i.key]?.takeIf { it !is Live.None }?.let { i.copy(live = it) } ?: i
            }
            _state.update { it.copy(items = base, loading = false) }

            // 2) background: find Android Motion Photos, update the grid as batches finish
            val gate = Semaphore(4)
            base.filter { !it.isVideo && !it.isLive && it.mime == "image/jpeg" && it.key !in motionCache }
                .chunked(40)
                .forEach { chunk ->
                    val found = chunk.map { i ->
                        async(Dispatchers.IO) {
                            gate.withPermit { i.key to repo.detectMotion(i).also { motionCache[i.key] = it } }
                        }
                    }.awaitAll().filter { it.second !is Live.None }.toMap()
                    if (found.isNotEmpty()) _state.update { s ->
                        s.copy(items = s.items.map { i -> found[i.key]?.let { i.copy(live = it) } ?: i })
                    }
                }
        }
    }

    /** The playable motion clip for a Live item (MOV uri, or extracted MP4 file). */
    suspend fun liveSource(item: GalleryItem): Uri? = when (val l = item.live) {
        is Live.Paired -> l.video
        is Live.Embedded -> runCatching {
            Uri.fromFile(repo.extractMotionVideo(item, l.videoLength))
        }.getOrNull()
        Live.None -> null
    }
}
