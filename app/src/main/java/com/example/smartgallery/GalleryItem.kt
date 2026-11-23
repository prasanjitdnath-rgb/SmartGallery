package com.example.smartgallery

import android.net.Uri
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** How a photo's motion clip is stored. */
sealed interface Live {
    data object None : Live

    /** iPhone Live Photo: a still (HEIC/JPG) plus a separate short MOV with the same name. */
    data class Paired(val video: Uri) : Live

    /** Android Motion Photo: an MP4 appended to the end of the JPEG (last [videoLength] bytes). */
    data class Embedded(val videoLength: Long) : Live
}

data class GalleryItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val mime: String,
    val isVideo: Boolean,
    val takenMs: Long,
    val durationMs: Long,
    val live: Live = Live.None,
) {
    /** Image and video MediaStore ids can collide, so prefix them. */
    val key: String get() = (if (isVideo) "v" else "i") + id
    val isLive: Boolean get() = live !is Live.None
    val day: LocalDate get() = Instant.ofEpochMilli(takenMs).atZone(ZoneId.systemDefault()).toLocalDate()
}

fun GalleryItem.durationLabel(): String {
    val s = durationMs / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
