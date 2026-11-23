package com.example.smartgallery

import android.content.ContentUris
import android.content.Context
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

class MediaRepository(private val context: Context) {
    private val resolver = context.contentResolver

    private data class Row(val item: GalleryItem, val folder: String)

    /** Fast pass: MediaStore query + iPhone Live Photo pairing. */
    suspend fun load(): List<GalleryItem> = withContext(Dispatchers.IO) {
        pair(query(video = false), query(video = true))
    }

    private fun query(video: Boolean): List<Row> {
        val base = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val cols = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )
        val rows = ArrayList<Row>()
        resolver.query(base, cols, null, null, null)?.use { c ->
            val iId = c.getColumnIndexOrThrow(cols[0])
            val iName = c.getColumnIndexOrThrow(cols[1])
            val iMime = c.getColumnIndexOrThrow(cols[2])
            val iTaken = c.getColumnIndexOrThrow(cols[3])
            val iAdded = c.getColumnIndexOrThrow(cols[4])
            val iDur = c.getColumnIndexOrThrow(cols[5])
            val iPath = c.getColumnIndexOrThrow(cols[6])
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val taken = c.getLong(iTaken).takeIf { it > 0 } ?: (c.getLong(iAdded) * 1000)
                rows += Row(
                    GalleryItem(
                        id = id,
                        uri = ContentUris.withAppendedId(base, id),
                        name = c.getString(iName).orEmpty(),
                        mime = c.getString(iMime).orEmpty(),
                        isVideo = video,
                        takenMs = taken,
                        durationMs = c.getLong(iDur),
                    ),
                    folder = c.getString(iPath).orEmpty(),
                )
            }
        }
        return rows
    }

    /**
     * iPhone Live Photos arrive as IMG_1234.HEIC + IMG_1234.MOV (~1.5–3 s).
     * Match by folder + base name + short duration, attach the MOV to the still,
     * and hide the MOV from the grid.
     */
    private fun pair(images: List<Row>, videos: List<Row>): List<GalleryItem> {
        fun key(r: Row) = r.folder + r.item.name.substringBeforeLast('.').lowercase()
        val clips = videos.filter { it.item.durationMs in 1..6_000 }.associateBy(::key)
        val used = HashSet<Long>()
        val out = ArrayList<GalleryItem>(images.size + videos.size)
        for (img in images) {
            val clip = if (img.item.mime in APPLE_STILLS) clips[key(img)] else null
            if (clip != null) {
                used += clip.item.id
                out += img.item.copy(live = Live.Paired(clip.item.uri))
            } else out += img.item
        }
        for (v in videos) if (v.item.id !in used) out += v.item
        out.sortByDescending { it.takenMs }
        return out
    }

    /**
     * Slow pass (blocking, call off the main thread): detects Google/Samsung Motion Photos by reading
     * the XMP header of a JPEG, then sanity-checks that an MP4 ("ftyp") really sits at the tail.
     */
    fun detectMotion(item: GalleryItem): Live {
        if (item.mime != "image/jpeg") return Live.None
        return runCatching {
            val pfd = resolver.openFileDescriptor(item.uri, "r") ?: return Live.None
            ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
                val ch = input.channel
                val size = ch.size()
                val head = ByteBuffer.allocate(minOf(size, 96L * 1024).toInt())
                while (head.hasRemaining() && ch.read(head) > 0) { /* fill */ }
                val xmp = String(head.array(), 0, head.position(), Charsets.ISO_8859_1)

                val len = MOTION_ITEM.find(xmp)?.value
                    ?.let { LENGTH.find(it)?.groupValues?.get(1)?.toLongOrNull() }
                    ?: MICRO_OFFSET.find(xmp)?.groupValues?.get(1)?.toLongOrNull()
                if (len == null || len <= 0 || len >= size) return@use Live.None

                val sig = ByteBuffer.allocate(8)
                ch.position(size - len)
                while (sig.hasRemaining() && ch.read(sig) > 0) { /* fill */ }
                val tag = String(sig.array(), 4, 4, Charsets.ISO_8859_1)
                if (tag == "ftyp") Live.Embedded(len) else Live.None
            }
        }.getOrDefault(Live.None)
    }

    /** Copies the embedded MP4 tail of a Motion Photo into cache so ExoPlayer can play it. */
    suspend fun extractMotionVideo(item: GalleryItem, length: Long): File = withContext(Dispatchers.IO) {
        val out = File(context.cacheDir, "motion_${item.id}_$length.mp4")
        if (out.length() == length) return@withContext out
        val pfd = resolver.openFileDescriptor(item.uri, "r") ?: error("Cannot open ${item.uri}")
        ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
            input.channel.position(input.channel.size() - length)
            out.outputStream().use { input.copyTo(it) }
        }
        out
    }

    private companion object {
        val APPLE_STILLS = setOf("image/heic", "image/heif", "image/jpeg")
        val MOTION_ITEM = Regex("<[^>]*Semantic=\"MotionPhoto\"[^>]*>")
        val LENGTH = Regex("Item:Length=\"(\\d+)\"")
        val MICRO_OFFSET = Regex("MicroVideoOffset=\"(\\d+)\"")
    }
}
