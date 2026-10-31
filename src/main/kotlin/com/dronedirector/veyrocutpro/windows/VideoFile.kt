package com.dronedirector.veyrocutpro.windows

import java.io.DataInputStream
import java.io.File

/** What a video or sound file holds. */
class MediaInfo(
    val durationUs: Long, val width: Int, val height: Int, val fps: Float, val rotation: Int,
    val hasVideo: Boolean, val hasAudio: Boolean,
)

/**
 * Reads a video file's pictures with Windows' own media engine (any format Windows plays: H.264, HEVC
 * with the HEVC extension, AV1, VP9, MPEG-2, ProRes with its codec installed…). Pictures come as
 * ARGB, scaled to fit [maxW] x [maxH] (0 = full size). One reader is for one place in the file at a
 * time; use several for several places. Own code.
 */
class VideoFile(val file: File, maxW: Int = 0, maxH: Int = 0) : AutoCloseable {
    private var h = 0L
    private var test: TestReader? = null
    val info: MediaInfo
    var width = 0; private set
    var height = 0; private set
    private var px = IntArray(0)

    init {
        if (MfNative.ok) {
            h = MfNative.fileOpen(file.absolutePath, 0, maxW, maxH)
            if (h == 0L) throw java.io.IOException(MfNative.error().ifBlank { "Couldn't open ${file.name}" })
            val v = MfNative.fileInfo(h)!!
            info = MediaInfo(v[0] / 10, v[1].toInt(), v[2].toInt(), if (v[4] > 0) v[3].toFloat() / v[4] else 30f, v[5].toInt(), v[6] != 0L, v[7] != 0L)
            width = v[8].toInt(); height = v[9].toInt()
        } else {
            if (DevTools.windows) throw java.io.IOException(MfNative.error().ifBlank { "Windows' media engine couldn't start — reinstall AlcorCut Pro" })
            val t = TestReader(file, maxW, maxH); test = t
            info = t.info; width = t.w; height = t.h
        }
    }

    /** A picture and its time in the file (microseconds). [px] is reused by the next read. */
    class Frame(val px: IntArray, val width: Int, val height: Int, val timeUs: Long)

    /** Goes to [us]; the next picture is the one at or before it (the nearest key frame). */
    fun seek(us: Long) { if (h != 0L) MfNative.fileSeek(h, us * 10) else test?.seek(us) }

    /** The next picture, or null at the end. */
    fun next(): Frame? {
        test?.let { return it.next() }
        if (px.size < width * height) px = IntArray(width * height)
        var t = MfNative.fileVideo(h, px)
        val v = MfNative.fileInfo(h)!!
        if (v[8].toInt() != width || v[9].toInt() != height) {
            width = v[8].toInt(); height = v[9].toInt()
            px = IntArray(width * height)
            if (t == -3L) t = MfNative.fileVideo(h, px)
        }
        return if (t < 0) null else Frame(px, width, height, t / 10)
    }

    /** The picture showing at [us] (seeks back to the key frame and reads forward). */
    fun frameAt(us: Long): Frame? {
        seek(us)
        val half = (500_000f / info.fps.coerceAtLeast(1f)).toLong()
        var f = next() ?: return null
        var last = f.px.copyOf(f.width * f.height); var lw = f.width; var lh = f.height; var lt = f.timeUs
        var n = 0
        while (f.timeUs + half < us && n++ < 600) {
            f = next() ?: break
            if (last.size != f.width * f.height) last = IntArray(f.width * f.height)
            System.arraycopy(f.px, 0, last, 0, f.width * f.height); lw = f.width; lh = f.height; lt = f.timeUs
        }
        return Frame(last, lw, lh, lt)
    }

    override fun close() {
        if (h != 0L) { MfNative.fileClose(h); h = 0L }
        test?.close(); test = null
    }

    companion object {
        /** A file's details without reading it. */
        fun info(file: File): MediaInfo? = try {
            if (MfNative.ok) {
                val h = MfNative.fileOpen(file.absolutePath, 2, 0, 0)
                if (h == 0L) null else try {
                    val v = MfNative.fileInfo(h)!!
                    MediaInfo(v[0] / 10, v[1].toInt(), v[2].toInt(), if (v[4] > 0) v[3].toFloat() / v[4] else 30f, v[5].toInt(), v[6] != 0L, v[7] != 0L)
                } finally { MfNative.fileClose(h) }
            } else TestReader.probe(file)
        } catch (_: Throwable) { null }
    }
}

/**
 * A file's sound as 16-bit stereo at 48 kHz, read in blocks from any position. Own code.
 */
class AudioFile(val file: File) : AutoCloseable {
    private var h = 0L
    private var test: TestAudio? = null
    private val buf = ByteArray(1 shl 18)
    private val time = LongArray(1)

    init {
        if (MfNative.ok) {
            h = MfNative.fileOpen(file.absolutePath, 1, 0, 0)
            if (h == 0L) throw java.io.IOException(MfNative.error().ifBlank { "Couldn't read the sound of ${file.name}" })
        } else {
            if (DevTools.windows) throw java.io.IOException("Windows' media engine couldn't start — reinstall AlcorCut Pro")
            test = TestAudio(file)
        }
    }

    /** A block of sound: [n] stereo frames starting at [timeUs]. */
    class Block(val pcm: ShortArray, val n: Int, val timeUs: Long)

    fun seek(us: Long) { if (h != 0L) MfNative.fileSeek(h, us * 10) else test?.seek(us) }

    /** The next block, or null at the end. */
    fun next(): Block? {
        test?.let { return it.next() }
        while (true) {
            val n = MfNative.fileAudio(h, buf, time)
            if (n < 0) return null
            if (n == 0) continue
            val frames = n / 4
            val out = ShortArray(frames * 2)
            java.nio.ByteBuffer.wrap(buf, 0, n).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(out)
            return Block(out, frames, time[0] / 10)
        }
    }

    override fun close() {
        if (h != 0L) { MfNative.fileClose(h); h = 0L }
        test?.close(); test = null
    }
}

// ---------------------------------------------------------------- development readers (not Windows)

/** Reads pictures through a separate ffmpeg program where Windows' media engine isn't there (development machines). */
internal class TestReader(private val file: File, private val maxW: Int, private val maxH: Int) : AutoCloseable {
    companion object {
        fun probe(f: File): MediaInfo? = try {
            val p = ProcessBuilder("ffprobe", "-v", "error", "-show_entries", "stream=codec_type,width,height,r_frame_rate:stream_side_data=rotation:format=duration", "-of", "default=nw=1", f.absolutePath).start()
            val out = p.inputStream.bufferedReader().readText(); p.waitFor()
            val m = out.lines().filter { '=' in it }.groupBy({ it.substringBefore('=') }, { it.substringAfter('=') })
            val fr = m["r_frame_rate"]?.firstOrNull { it != "0/0" }?.split('/')
            MediaInfo(((m["duration"]?.firstOrNull()?.toDoubleOrNull() ?: 0.0) * 1e6).toLong(),
                m["width"]?.firstOrNull()?.toIntOrNull() ?: 0, m["height"]?.firstOrNull()?.toIntOrNull() ?: 0,
                if (fr != null && fr.size == 2) (fr[0].toFloat() / fr[1].toFloat().coerceAtLeast(1f)) else 30f,
                ((m["rotation"]?.firstOrNull()?.toIntOrNull() ?: 0) + 360) % 360,
                m["codec_type"]?.contains("video") == true, m["codec_type"]?.contains("audio") == true)
        } catch (_: Throwable) { null }
    }
    val info = probe(file) ?: MediaInfo(0, 0, 0, 30f, 0, false, false)
    val w: Int; val h: Int
    init {
        var ww = info.width; var hh = info.height
        if (maxW > 0 && maxH > 0 && (ww > maxW || hh > maxH)) { val k = minOf(maxW.toDouble() / ww, maxH.toDouble() / hh); ww = (ww * k).toInt(); hh = (hh * k).toInt() }
        w = ww and 1.inv(); h = hh and 1.inv()
    }
    private var proc: Process? = null
    private var input: DataInputStream? = null
    private var pos = 0L
    private var n = 0L
    private val raw = ByteArray(w * h * 4)
    private val px = IntArray(w * h)

    fun seek(us: Long) { close(); pos = us; n = 0 }

    fun next(): VideoFile.Frame? {
        if (proc == null) {
            proc = ProcessBuilder("ffmpeg", "-v", "error", "-ss", "%.6f".format(java.util.Locale.US, pos / 1e6), "-i", file.absolutePath, "-an", "-vf", "scale=$w:$h", "-pix_fmt", "bgra", "-f", "rawvideo", "-").start()
            input = DataInputStream(proc!!.inputStream.buffered(1 shl 20))
        }
        try { input!!.readFully(raw) } catch (_: Throwable) { return null }
        for (i in px.indices) px[i] = (raw[i * 4 + 2].toInt() and 0xFF shl 16) or (raw[i * 4 + 1].toInt() and 0xFF shl 8) or (raw[i * 4].toInt() and 0xFF) or (0xFF shl 24)
        val t = pos + (n++ * 1_000_000L / info.fps.coerceAtLeast(1f)).toLong()
        return VideoFile.Frame(px, w, h, t)
    }

    override fun close() { proc?.destroy(); proc = null; input = null }
}

internal class TestAudio(private val file: File) : AutoCloseable {
    private var proc: Process? = null
    private var pos = 0L
    private var read = 0L
    private val buf = ByteArray(4096 * 4)
    fun seek(us: Long) { close(); pos = us; read = 0 }
    fun next(): AudioFile.Block? {
        if (proc == null) proc = ProcessBuilder("ffmpeg", "-v", "error", "-ss", "%.6f".format(java.util.Locale.US, pos / 1e6), "-i", file.absolutePath, "-vn", "-ac", "2", "-ar", "48000", "-f", "s16le", "-").start()
        val n = try { proc!!.inputStream.readNBytes(buf, 0, buf.size) } catch (_: Throwable) { 0 }
        if (n <= 0) return null
        val frames = n / 4
        val out = ShortArray(frames * 2)
        java.nio.ByteBuffer.wrap(buf, 0, frames * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(out)
        val t = pos + read * 1_000_000L / 48000
        read += frames
        return AudioFile.Block(out, frames, t)
    }
    override fun close() { proc?.destroy(); proc = null }
}

/** Media files as local files for Windows' media engine (web addresses are downloaded once to a cache). */
object MediaFiles {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, File>()
    val videoExt = setOf("mp4", "mov", "m4v", "mkv", "webm", "avi", "wmv", "mts", "m2ts", "3gp", "mpg", "mpeg", "ts", "mxf")
    val audioExt = setOf("mp3", "wav", "m4a", "aac", "flac", "ogg", "opus", "wma", "aif", "aiff")

    fun local(u: android.net.Uri): File? {
        u.toFile()?.let { return it.takeIf { f -> f.exists() } }
        val key = u.toString()
        cache[key]?.takeIf { it.exists() }?.let { return it }
        val ins = android.content.Context.app.contentResolver.openInputStream(u) ?: return null
        val ext = key.substringAfterLast('.', "mp4").substringBefore('?').take(5)
        val f = File(File(System.getProperty("java.io.tmpdir"), "alcorcut-media").apply { mkdirs() }, "m${key.hashCode().toUInt()}.$ext")
        ins.use { i -> f.outputStream().use { i.copyTo(it) } }
        cache[key] = f
        return f
    }
}

/** The development readers and writer use a separate program on Linux test machines only; never on Windows. */
internal object DevTools {
    val windows: Boolean = System.getProperty("os.name").lowercase().contains("win")
}
