package com.dronedirector.veyrocutpro.editor

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Fast thumbnails for the video editor (timeline and media strip). Own code.
 *  - A cover appears at once: the phone's own ready-made video thumbnail (the gallery keeps one for
 *    every video), else one quick frame.
 *  - Filmstrips fill in coarse to fine (whole clip first, then the gaps), several clips at a time,
 *    each clip with its own reader, the lighter proxy when there is one.
 *  - Every frame is kept small on disk in the video catalog, so a project opens with its thumbnails
 *    already there.
 */
@OptIn(ExperimentalCoroutinesApi::class)
object VideoThumbs {
    /** Several clips at once (each with its own reader). */
    val io = Dispatchers.IO.limitedParallelism(4)

    private val mem = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    private fun dir(context: Context) = MediaCatalog.sub(context, "thumbs")
    private fun file(context: Context, uri: Uri, name: String): File =
        File(dir(context), uri.toString().hashCode().toUInt().toString(16) + "_" + name + ".jpg").also { MediaCatalog.claim(context, uri, it) }

    fun cached(uri: Uri, name: String): Bitmap? = mem.get("$uri|$name")

    private fun load(context: Context, uri: Uri, name: String): Bitmap? {
        mem.get("$uri|$name")?.let { return it }
        val f = file(context, uri, name)
        if (!f.exists()) return null
        return try { BitmapFactory.decodeFile(f.path)?.also { mem.put("$uri|$name", it) } } catch (_: Throwable) { null }
    }

    private fun keep(context: Context, uri: Uri, name: String, b: Bitmap) {
        mem.put("$uri|$name", b)
        try { file(context, uri, name).outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 80, it) } } catch (_: Throwable) {}
    }

    /** The clip's cover (long side about [px]): memory, disk, the phone's ready thumbnail, or one quick frame. */
    fun cover(context: Context, uri: Uri, atMs: Long, px: Int = 240): Bitmap? {
        val name = "c${atMs / 1000}_$px"
        load(context, uri, name)?.let { return it }
        var b: Bitmap? = null
        if (atMs < 1500 && uri.scheme == ContentResolver.SCHEME_CONTENT && Build.VERSION.SDK_INT >= 29) {
            b = try { context.contentResolver.loadThumbnail(uri, Size(px, px), null) } catch (_: Throwable) { null }
        }
        if (b == null) b = try {
            val m = MediaMetadataRetriever()
            try { m.setDataSource(context, uri); grab(m, atMs * 1000, px) } finally { m.release() }
        } catch (_: Throwable) { null }
        return b?.also { keep(context, uri, name, it) }
    }

    private fun grab(m: MediaMetadataRetriever, us: Long, px: Int): Bitmap? =
        if (Build.VERSION.SDK_INT >= 27) m.getScaledFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, px, px)
        else m.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { f ->
            val s = px.toFloat() / maxOf(f.width, f.height)
            Bitmap.createScaledBitmap(f, (f.width * s).toInt().coerceAtLeast(1), (f.height * s).toInt().coerceAtLeast(1), true).also { if (it !== f) f.recycle() }
        }

    /** The order to fill [n] slots: ends and middle first, then halving the gaps. */
    private fun order(n: Int): List<Int> {
        val out = LinkedHashSet<Int>()
        out += 0; if (n > 1) out += n - 1
        var step = n
        while (step > 1 && out.size < n) {
            step = (step + 1) / 2
            var i = step / 2
            while (i < n) { out += i; i += step }
        }
        for (i in 0 until n) out += i
        return out.toList()
    }

    /**
     * Fills a filmstrip of [n] frames for [uri] (from [read], e.g. its proxy). [publish] gets the
     * whole strip whenever more frames are in: first the cover in every slot, then the real frames.
     */
    suspend fun strip(context: Context, uri: Uri, read: Uri, durationMs: Long, n: Int, h: Int, w: Int, publish: (List<Bitmap>) -> Unit) {
        val slots = arrayOfNulls<Bitmap>(n)
        var missing = false
        for (i in 0 until n) { slots[i] = load(context, uri, "s${n}_${h}_$i"); if (slots[i] == null) missing = true }
        val first = withContext(io) { cover(context, uri, 0, maxOf(w, h)) }
        fun snapshot() = slots.map { it ?: first }.filterNotNull()
        snapshot().takeIf { it.size == n }?.let { withContext(Dispatchers.Main) { publish(it) } }
        if (!missing) return
        withContext(io) {
            val m = MediaMetadataRetriever()
            try {
                try { m.setDataSource(context, read) } catch (t: Throwable) { if (read != uri) m.setDataSource(context, uri) else throw t }
                var sinceShown = 0
                for (i in order(n)) {
                    if (slots[i] != null) continue
                    val t = (durationMs * (i + 0.5) / n * 1000).toLong()
                    val f = (if (Build.VERSION.SDK_INT >= 27) m.getScaledFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, w, h)
                        else m.getFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { Bitmap.createScaledBitmap(it, w, h, true) }) ?: continue
                    slots[i] = f
                    keep(context, uri, "s${n}_${h}_$i", f)
                    // Show progress often at first (the coarse frames), then in small batches.
                    if (++sinceShown >= (if (i < 4) 1 else 3)) {
                        sinceShown = 0
                        val s = snapshot()
                        if (s.size == n) withContext(Dispatchers.Main) { publish(s) }
                    }
                }
            } catch (_: Throwable) {} finally { try { m.release() } catch (_: Throwable) {} }
        }
        snapshot().takeIf { it.size == n }?.let { withContext(Dispatchers.Main) { publish(it) } }
    }
}
