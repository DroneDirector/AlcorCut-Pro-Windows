package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Keeps the photo editor's viewer from ever going black when you move between photos.
 *
 * Layers, best first:
 *  1. the exact picture the editor last rendered for that photo and edit (memory, then disk),
 *  2. for an edited photo never rendered at full size here, its saved project thumbnail (right look, softer),
 *  3. for a RAW, the camera's embedded preview, colour-matched to how this editor develops that
 *     camera's files (learned from every RAW opened), so the swap to the real render is seamless.
 * Meanwhile the photos next to the current one in the filmstrip are decoded and rendered in the
 * background, so stepping through them usually shows the finished picture straight away.
 */
object PhotoViewer {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** A decoded RAW at preview size and its neutral develop: what the editor needs to start editing at once. */
    class RawEntry(val meta: RawDng.Meta, val lin: RawDng.Linear, val base: Bitmap)

    private val bigHeap get() = Runtime.getRuntime().maxMemory() >= 400L * 1024 * 1024
    private val raws = LinkedHashMap<String, RawEntry>(8, 0.75f, true)
    private val shown = LinkedHashMap<String, Pair<Int, Bitmap>>(8, 0.75f, true)

    fun raw(uri: String): RawEntry? = synchronized(raws) { raws[uri] }
    fun putRaw(uri: String, e: RawEntry) = synchronized(raws) {
        raws[uri] = e
        val keep = if (bigHeap) 3 else 2
        while (raws.size > keep) raws.remove(raws.keys.first())
    }

    private val inflight = HashMap<String, Deferred<RawEntry?>>()

    /**
     * Decodes a DNG at preview size and develops it neutrally, shared between the editor and the
     * read-ahead (a photo already being read ahead is simply awaited). Null for files that aren't DNGs.
     * Throws [RawDng.UnsupportedRaw] / [OutOfMemoryError] like the decoder.
     */
    /**
     * Size of the photo editor's viewer picture: the screen's own resolution (long side), so the photo
     * shows at full screen quality — never the small preview. RAWs a little smaller (their data is 3×
     * bigger in memory).
     */
    fun viewerPx(context: Context, raw: Boolean = false): Int {
        val dm = context.resources.displayMetrics
        val side = maxOf(dm.widthPixels, dm.heightPixels)
        return if (raw) side.coerceIn(1600, 2600) else side.coerceIn(1800, 3200)
    }

    fun loadRaw(context: Context, uri: Uri): Deferred<RawEntry?> {
        val k = uri.toString()
        val app = context.applicationContext
        synchronized(inflight) {
            raw(k)?.let { return CompletableDeferred(it) }
            inflight[k]?.let { return it }
            val d = scope.async(Dispatchers.IO) {
                try {
                    if (!RawDng.isRaw(app, uri)) return@async null
                    val m = RawDng.open(app, uri)
                    val lin = RawDng.preview(app, m, viewerPx(app, raw = true))
                    val base = RawDng.develop(lin, m, RawDng.Dev(), 0f, 0f, 0f, 0f, 0f, 0f, 0f)
                    RawEntry(m, lin, base).also { putRaw(k, it); learn(app, uri, base) }
                } finally { synchronized(inflight) { inflight.remove(k) } }
            }
            inflight[k] = d
            return d
        }
    }

    fun hasRendered(context: Context, key: String, sig: Int): Boolean = rendered(key, sig) != null || file(context, key, sig).exists()

    /** Fingerprint of an edit: the cached picture is used only while the edit is unchanged. */
    fun signature(edit: Any): Int = try { ProjectStore.gson.toJson(edit).hashCode() } catch (_: Throwable) { 0 }

    fun rendered(key: String, sig: Int): Bitmap? = synchronized(shown) { shown[key]?.takeIf { it.first == sig }?.second }

    private fun dir(context: Context) = File(context.cacheDir, "viewer").apply { mkdirs() }
    private fun stem(key: String) = Integer.toHexString(key.hashCode()) + "_" + Integer.toHexString(key.length)
    private fun file(context: Context, key: String, sig: Int) = File(dir(context), stem(key) + "_" + Integer.toHexString(sig) + ".jpg")

    /** Remembers the finished render of [key] with edit [sig] (memory now, disk in the background). */
    fun store(context: Context, key: String, sig: Int, bmp: Bitmap) {
        synchronized(shown) {
            if (shown[key]?.let { it.first == sig && it.second === bmp } == true) return
            shown[key] = sig to bmp
            while (shown.size > (if (bigHeap) 6 else 3)) shown.remove(shown.keys.first())
        }
        val app = context.applicationContext
        scope.launch(Dispatchers.IO) {
            try {
                val d = dir(app); val stem = stem(key)
                val f = file(app, key, sig)
                val tmp = File(d, f.name + ".tmp")
                tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                d.listFiles()?.forEach { if (it.name.startsWith(stem + "_") && it.name != f.name && !it.name.endsWith(".tmp")) it.delete() }
                tmp.renameTo(f)
                // Keep the cache to about 250 MB, dropping the oldest.
                val all = d.listFiles()?.filter { it.name.endsWith(".jpg") }?.sortedByDescending { it.lastModified() } ?: return@launch
                var total = 0L
                for (x in all) { total += x.length(); if (total > 250L * 1024 * 1024) x.delete() }
            } catch (_: Throwable) {}
        }
    }

    fun renderedFromDisk(context: Context, key: String, sig: Int): Bitmap? = try {
        file(context, key, sig).takeIf { it.exists() }?.let { f -> f.setLastModified(System.currentTimeMillis()); BitmapFactory.decodeFile(f.path) }
    } catch (_: Throwable) { null }

    // ---------------------------------------------------------------- camera preview colour match

    /** Per camera: 3 × 256 tone curves taking its embedded JPEG to this editor's neutral develop. */
    private var luts: MutableMap<String, Pair<Int, IntArray>>? = null
    private fun lutFile(context: Context) = File(context.filesDir, "viewer_match.txt")

    private fun loadLuts(context: Context): MutableMap<String, Pair<Int, IntArray>> = synchronized(this) {
        luts ?: HashMap<String, Pair<Int, IntArray>>().also { m ->
            try {
                lutFile(context).takeIf { it.exists() }?.readLines()?.forEach { line ->
                    val parts = line.split('\t')
                    if (parts.size == 3) {
                        val v = parts[2].split(',').mapNotNull { it.toIntOrNull() }
                        if (v.size == 768) m[parts[0]] = (parts[1].toIntOrNull() ?: 1) to v.toIntArray()
                    }
                }
            } catch (_: Throwable) {}
            luts = m
        }
    }

    /** The camera's embedded preview, recoloured to look like this editor's develop of it. */
    fun matched(context: Context, e: RawDng.Embedded): Bitmap {
        val lut = loadLuts(context)[e.camera]?.second ?: return e.bitmap
        val b = e.bitmap
        val px = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
        for (i in px.indices) {
            val c = px[i]
            px[i] = (0xFF shl 24) or (lut[(c shr 16) and 0xFF] shl 16) or (lut[256 + ((c shr 8) and 0xFF)] shl 8) or lut[512 + (c and 0xFF)]
        }
        return Bitmap.createBitmap(px, b.width, b.height, Bitmap.Config.ARGB_8888)
    }

    /** Learns (a little more each time) how this camera's embedded previews differ from our neutral develop. */
    fun learn(context: Context, uri: Uri, neutral: Bitmap) {
        val app = context.applicationContext
        scope.launch {
            try {
                val e = RawDng.embedded(app, uri, 320) ?: return@launch
                val map = loadLuts(app)
                val n = synchronized(this@PhotoViewer) { map[e.camera]?.first ?: 0 }
                if (n >= 40) return@launch   // well learned
                fun cdf(b0: Bitmap): Array<DoubleArray> {
                    val k = 320f / max(b0.width, b0.height)
                    val b = if (k < 1f) Bitmap.createScaledBitmap(b0, max(1, (b0.width * k).roundToInt()), max(1, (b0.height * k).roundToInt()), true) else b0
                    val px = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
                    val h = Array(3) { DoubleArray(256) }
                    for (c in px) { h[0][(c shr 16) and 0xFF]++; h[1][(c shr 8) and 0xFF]++; h[2][c and 0xFF]++ }
                    for (ch in h) { var s = 0.0; for (i in 0 until 256) { s += ch[i]; ch[i] = s / px.size } }
                    return h
                }
                val a = cdf(e.bitmap); val t = cdf(neutral)
                val fresh = IntArray(768)
                for (ch in 0 until 3) {
                    var u = 0
                    for (v in 0 until 256) {
                        while (u < 255 && t[ch][u] < a[ch][v]) u++
                        fresh[ch * 256 + v] = u
                    }
                }
                synchronized(this@PhotoViewer) {
                    val old = map[e.camera]
                    val w = old?.first ?: 0
                    val merged = if (old == null) fresh else IntArray(768) { i -> ((old.second[i] * w + fresh[i]).toDouble() / (w + 1)).roundToInt().coerceIn(0, 255) }
                    // Keep each curve rising (no tone reversals).
                    for (ch in 0 until 3) for (v in 1 until 256) if (merged[ch * 256 + v] < merged[ch * 256 + v - 1]) merged[ch * 256 + v] = merged[ch * 256 + v - 1]
                    map[e.camera] = (w + 1) to merged
                    lutFile(app).writeText(map.entries.joinToString("\n") { (k, v) -> k.replace('\t', ' ').replace('\n', ' ') + "\t" + v.first + "\t" + v.second.joinToString(",") })
                }
            } catch (_: Throwable) {}
        }
    }

    // ---------------------------------------------------------------- neighbours

    private var prefetchJob: Job? = null

    /** Runs [work] in the background, replacing any earlier read-ahead still going. */
    fun prefetch(work: suspend CoroutineScope.() -> Unit) {
        prefetchJob?.cancel()
        prefetchJob = scope.launch(Dispatchers.Default) { try { work() } catch (_: OutOfMemoryError) {} catch (_: Throwable) {} }
    }

    fun stopPrefetch() { prefetchJob?.cancel() }
}
