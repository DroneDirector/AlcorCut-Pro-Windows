package android.media

import android.content.Context
import android.net.Uri
import com.dronedirector.veyrocutpro.windows.AudioFile
import com.dronedirector.veyrocutpro.windows.MediaFiles
import com.dronedirector.veyrocutpro.windows.VideoFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The phone's media codec interface on Windows' media engine. Files are read already decoded: the
 * reader ([MediaExtractor]) gives pictures (YUV 4:2:0) and sound (16-bit PCM, 48 kHz stereo), a
 * "decoder" passes them through unchanged, an "encoder" passes its input through too, and [MediaMuxer]
 * does the real encoding with Windows' encoders when it writes the file. So the shared code's
 * read → decode → process → encode → write loops work as they are. Own code.
 */
class MediaFormat {
    private val m = LinkedHashMap<String, Any>()
    fun containsKey(k: String) = k in m
    fun getInteger(k: String): Int = (m[k] as? Number)?.toInt() ?: throw NullPointerException(k)
    fun getInteger(k: String, d: Int): Int = (m[k] as? Number)?.toInt() ?: d
    fun getLong(k: String): Long = (m[k] as? Number)?.toLong() ?: throw NullPointerException(k)
    fun getLong(k: String, d: Long): Long = (m[k] as? Number)?.toLong() ?: d
    fun getFloat(k: String): Float = (m[k] as? Number)?.toFloat() ?: throw NullPointerException(k)
    fun getFloat(k: String, d: Float): Float = (m[k] as? Number)?.toFloat() ?: d
    fun getString(k: String): String? = m[k] as? String
    fun getString(k: String, d: String): String = (m[k] as? String) ?: d
    fun getByteBuffer(k: String): ByteBuffer? = m[k] as? ByteBuffer
    fun getNumber(k: String): Number? = m[k] as? Number
    fun setInteger(k: String, v: Int) { m[k] = v }
    fun setLong(k: String, v: Long) { m[k] = v }
    fun setFloat(k: String, v: Float) { m[k] = v }
    fun setString(k: String, v: String?) { if (v == null) m.remove(k) else m[k] = v }
    fun setByteBuffer(k: String, v: ByteBuffer?) { if (v == null) m.remove(k) else m[k] = v }
    fun removeKey(k: String) { m.remove(k) }
    val keys: Set<String> get() = m.keys
    internal fun copy(): MediaFormat = MediaFormat().also { it.m.putAll(m) }
    override fun toString() = m.toString()

    companion object {
        const val KEY_MIME = "mime"; const val KEY_WIDTH = "width"; const val KEY_HEIGHT = "height"
        const val KEY_SAMPLE_RATE = "sample-rate"; const val KEY_CHANNEL_COUNT = "channel-count"
        const val KEY_FRAME_RATE = "frame-rate"; const val KEY_COLOR_FORMAT = "color-format"
        const val KEY_BIT_RATE = "bitrate"; const val KEY_I_FRAME_INTERVAL = "i-frame-interval"
        const val KEY_DURATION = "durationUs"; const val KEY_PCM_ENCODING = "pcm-encoding"
        const val KEY_COLOR_TRANSFER = "color-transfer"; const val KEY_COLOR_STANDARD = "color-standard"; const val KEY_COLOR_RANGE = "color-range"
        const val KEY_PROFILE = "profile"; const val KEY_LEVEL = "level"; const val KEY_BITRATE_MODE = "bitrate-mode"; const val KEY_QUALITY = "quality"
        const val KEY_MAX_INPUT_SIZE = "max-input-size"; const val KEY_ROTATION = "rotation-degrees"; const val KEY_STRIDE = "stride"; const val KEY_SLICE_HEIGHT = "slice-height"
        const val KEY_LANGUAGE = "language"; const val KEY_AAC_PROFILE = "aac-profile"; const val KEY_OPERATING_RATE = "operating-rate"; const val KEY_PRIORITY = "priority"
        const val KEY_CAPTURE_RATE = "capture-rate"; const val KEY_TRACK_ID = "track-id"; const val KEY_HDR_STATIC_INFO = "hdr-static-info"
        const val MIMETYPE_VIDEO_AVC = "video/avc"; const val MIMETYPE_VIDEO_HEVC = "video/hevc"; const val MIMETYPE_VIDEO_AV1 = "video/av01"
        const val MIMETYPE_VIDEO_VP9 = "video/x-vnd.on2.vp9"; const val MIMETYPE_VIDEO_DOLBY_VISION = "video/dolby-vision"; const val MIMETYPE_VIDEO_RAW = "video/raw"
        const val MIMETYPE_AUDIO_AAC = "audio/mp4a-latm"; const val MIMETYPE_AUDIO_OPUS = "audio/opus"; const val MIMETYPE_AUDIO_RAW = "audio/raw"
        const val MIMETYPE_AUDIO_MPEG = "audio/mpeg"; const val MIMETYPE_AUDIO_FLAC = "audio/flac"; const val MIMETYPE_IMAGE_AVIF = "image/avif"
        const val COLOR_TRANSFER_SDR_VIDEO = 3; const val COLOR_TRANSFER_ST2084 = 6; const val COLOR_TRANSFER_HLG = 7; const val COLOR_TRANSFER_LINEAR = 1
        const val COLOR_STANDARD_BT709 = 1; const val COLOR_STANDARD_BT601_PAL = 2; const val COLOR_STANDARD_BT601_NTSC = 4; const val COLOR_STANDARD_BT2020 = 6
        const val COLOR_RANGE_FULL = 1; const val COLOR_RANGE_LIMITED = 2
        fun createVideoFormat(mime: String, w: Int, h: Int) = MediaFormat().apply { setString(KEY_MIME, mime); setInteger(KEY_WIDTH, w); setInteger(KEY_HEIGHT, h) }
        fun createAudioFormat(mime: String, rate: Int, ch: Int) = MediaFormat().apply { setString(KEY_MIME, mime); setInteger(KEY_SAMPLE_RATE, rate); setInteger(KEY_CHANNEL_COUNT, ch) }
    }
}

/** A picture in YUV 4:2:0 (three planes) as decoders give it. */
open class Image internal constructor(val width: Int, val height: Int, internal val data: ByteArray, var timestamp: Long = 0) : AutoCloseable {
    open val format: Int get() = android.graphics.ImageFormat.YUV_420_888
    var cropRect: android.graphics.Rect = android.graphics.Rect(0, 0, width, height)
    class Plane(val buffer: ByteBuffer, val rowStride: Int, val pixelStride: Int) {
    }
    open val planes: Array<Plane> by lazy {
        val y = width * height; val cw = (width + 1) / 2; val ch = (height + 1) / 2
        // Direct memory, as decoders give it on the phone (native code reads the planes in place).
        val all = ByteBuffer.allocateDirect(data.size).put(data).also { it.position(0) }
        fun part(at: Int, n: Int) = all.duplicate().apply { position(at); limit(at + n) }.slice()
        arrayOf(Plane(part(0, y), width, 1), Plane(part(y, cw * ch), cw, 1), Plane(part(y + cw * ch, cw * ch), cw, 1))
    }
    override fun close() {}
}


/** ARGB ↔ YUV 4:2:0 (planar, BT.601 video range, as the phone's encoders take it). */
internal object Yuv {
    fun size(w: Int, h: Int) = w * h + 2 * ((w + 1) / 2) * ((h + 1) / 2)
    fun fromArgb(px: IntArray, w: Int, h: Int, out: ByteArray = ByteArray(size(w, h))): ByteArray {
        val cw = (w + 1) / 2; val ch = (h + 1) / 2; val uo = w * h; val vo = uo + cw * ch
        for (y in 0 until h) for (x in 0 until w) {
            val c = px[y * w + x]; val r = c shr 16 and 0xFF; val g = c shr 8 and 0xFF; val b = c and 0xFF
            out[y * w + x] = (16 + ((66 * r + 129 * g + 25 * b + 128) shr 8)).toByte()
            if (x and 1 == 0 && y and 1 == 0) {
                val i = (y / 2) * cw + x / 2
                out[uo + i] = (128 + ((-38 * r - 74 * g + 112 * b + 128) shr 8)).coerceIn(0, 255).toByte()
                out[vo + i] = (128 + ((112 * r - 94 * g - 18 * b + 128) shr 8)).coerceIn(0, 255).toByte()
            }
        }
        return out
    }
    fun toArgb(d: ByteArray, w: Int, h: Int, out: IntArray = IntArray(w * h), uvInterleaved: Boolean = false): IntArray {
        val cw = (w + 1) / 2; val ch = (h + 1) / 2; val uo = w * h; val vo = uo + cw * ch
        for (y in 0 until h) for (x in 0 until w) {
            val yy = ((d[y * w + x].toInt() and 0xFF) - 16) * 298
            val u: Int; val v: Int
            if (uvInterleaved) { val i = uo + (y / 2) * w + (x / 2) * 2; u = (d[i].toInt() and 0xFF) - 128; v = (d[i + 1].toInt() and 0xFF) - 128 }
            else { val i = (y / 2) * cw + x / 2; u = (d[uo + i].toInt() and 0xFF) - 128; v = (d[vo + i].toInt() and 0xFF) - 128 }
            val r = ((yy + 409 * v + 128) shr 8).coerceIn(0, 255)
            val g = ((yy - 100 * u - 208 * v + 128) shr 8).coerceIn(0, 255)
            val b = ((yy + 516 * u + 128) shr 8).coerceIn(0, 255)
            out[y * w + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return out
    }
}

/** Reads a file's pictures and sound (already decoded; see [MediaFormat]). */
open class MediaExtractor {
    private var file: java.io.File? = null
    private val formats = ArrayList<MediaFormat>()
    private var kinds = IntArray(0)              // 0 = video, 1 = audio
    private val selected = HashSet<Int>()
    private var video: VideoFile? = null
    private var audio: AudioFile? = null
    private var vNext: VideoFile.Frame? = null
    private var aNext: AudioFile.Block? = null
    private var vBuf: ByteArray? = null
    private var vEnd = false; private var aEnd = false
    private var fps = 30f

    fun setDataSource(path: String) { open(java.io.File(path)) }
    fun setDataSource(path: String, headers: Map<String, String>?) { open(java.io.File(path)) }
    fun setDataSource(c: Context, uri: Uri, headers: Map<String, String>?) { open(MediaFiles.local(uri) ?: throw java.io.IOException("Can't read $uri")) }
    fun setDataSource(fd: java.io.FileDescriptor) { throw java.io.IOException("File descriptors aren't readable here") }
    fun setDataSource(fd: java.io.FileDescriptor, offset: Long, length: Long) { throw java.io.IOException("File descriptors aren't readable here") }
    fun setDataSource(afd: android.content.res.AssetFileDescriptor) { open(afd.file) }

    private fun open(f: java.io.File) {
        if (!f.exists()) throw java.io.IOException("Can't read ${f.path}")
        file = f
        val i = VideoFile.info(f) ?: throw java.io.IOException("Can't read ${f.name}")
        fps = i.fps
        val k = ArrayList<Int>()
        if (i.hasVideo) {
            val rot = i.rotation
            formats += MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, i.width, i.height).apply {
                setLong(MediaFormat.KEY_DURATION, i.durationUs); setInteger(MediaFormat.KEY_FRAME_RATE, Math.round(i.fps)); setFloat("frame-rate-exact", i.fps)
                if (rot != 0) setInteger(MediaFormat.KEY_ROTATION, rot)
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, Yuv.size(i.width, i.height))
            }
            k += 0
        }
        if (i.hasAudio) {
            formats += MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 48000, 2).apply {
                setLong(MediaFormat.KEY_DURATION, i.durationUs); setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT); setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1 shl 18)
            }
            k += 1
        }
        kinds = k.toIntArray()
    }

    val trackCount: Int get() = formats.size
    fun getTrackFormat(i: Int): MediaFormat = formats[i]
    fun selectTrack(i: Int) {
        selected += i
        if (kinds[i] == 0 && video == null) { video = VideoFile(file!!); vNext = null; vEnd = false }
        if (kinds[i] == 1 && audio == null) { audio = AudioFile(file!!); aNext = null; aEnd = false }
    }
    fun unselectTrack(i: Int) {
        selected -= i
        if (kinds[i] == 0) { video?.close(); video = null }
        if (kinds[i] == 1) { audio?.close(); audio = null }
    }

    fun seekTo(us: Long, mode: Int) {
        video?.let { it.seek(us); vNext = null; vEnd = false }
        audio?.let { it.seek(us); aNext = null; aEnd = false }
        if (mode == SEEK_TO_CLOSEST_SYNC || mode == SEEK_TO_PREVIOUS_SYNC) return
    }

    private fun fill() {
        if (video != null && vNext == null && !vEnd) {
            val f = video!!.next()
            if (f == null) vEnd = true else {
                val y = Yuv.fromArgb(f.px, f.width, f.height, vBuf?.takeIf { it.size == Yuv.size(f.width, f.height) } ?: ByteArray(Yuv.size(f.width, f.height)).also { vBuf = it })
                vNext = VideoFile.Frame(IntArray(0), f.width, f.height, f.timeUs); vData = y
            }
        }
        if (audio != null && aNext == null && !aEnd) { aNext = audio!!.next(); if (aNext == null) aEnd = true }
    }
    private var vData: ByteArray? = null

    /** Which track the next sample belongs to (the earlier one first), or −1 at the end. */
    val sampleTrackIndex: Int get() {
        fill()
        val v = vNext?.timeUs; val a = aNext?.timeUs
        val vi = kinds.indexOf(0); val ai = kinds.indexOf(1)
        return when {
            v != null && (a == null || v <= a) -> vi
            a != null -> ai
            else -> -1
        }
    }
    val sampleTime: Long get() = when (sampleTrackIndex) { -1 -> -1L; kinds.indexOf(0) -> vNext!!.timeUs; else -> aNext!!.timeUs }
    val sampleFlags: Int get() = if (sampleTrackIndex == kinds.indexOf(0)) SAMPLE_FLAG_SYNC else SAMPLE_FLAG_SYNC
    val sampleSize: Long get() = when (sampleTrackIndex) { -1 -> -1L; kinds.indexOf(0) -> vData!!.size.toLong(); else -> aNext!!.n * 4L }
    /** Picture size of the next video sample (decoded). */
    internal val nextVideoSize: Pair<Int, Int>? get() = vNext?.let { it.width to it.height }

    fun readSampleData(buf: ByteBuffer, offset: Int): Int {
        val t = sampleTrackIndex
        if (t < 0) return -1
        buf.clear(); buf.position(offset)
        if (kinds[t] == 0) {
            val d = vData!!
            if (buf.remaining() < d.size) return -1
            buf.put(d)
            buf.position(offset); buf.limit(offset + d.size)
            lastVideoSize = vNext!!.width to vNext!!.height
            return d.size
        }
        val b = aNext!!
        val n = minOf(b.n * 4, buf.remaining())
        val bb = buf.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until n / 2) bb.putShort(b.pcm[i])
        buf.position(offset); buf.limit(offset + n)
        return n
    }
    internal var lastVideoSize: Pair<Int, Int>? = null

    fun advance(): Boolean {
        val t = sampleTrackIndex
        if (t < 0) return false
        if (kinds[t] == 0) vNext = null else aNext = null
        return sampleTrackIndex >= 0
    }

    fun getCachedDuration(): Long = -1
    fun release() { video?.close(); audio?.close(); video = null; audio = null }

    companion object {
        const val SEEK_TO_PREVIOUS_SYNC = 0; const val SEEK_TO_NEXT_SYNC = 1; const val SEEK_TO_CLOSEST_SYNC = 2
        const val SAMPLE_FLAG_SYNC = 1; const val SAMPLE_FLAG_ENCRYPTED = 2; const val SAMPLE_FLAG_PARTIAL_FRAME = 4
    }
}

/** A "codec" on Windows: decoders and encoders pass their data through (see [MediaFormat]). */
class MediaCodec private constructor(val name: String, private val encoder: Boolean) {
    class BufferInfo {
        @JvmField var offset = 0; @JvmField var size = 0; @JvmField var presentationTimeUs = 0L; @JvmField var flags = 0
        fun set(offset: Int, size: Int, pts: Long, flags: Int) { this.offset = offset; this.size = size; this.presentationTimeUs = pts; this.flags = flags }
    }
    class CodecException(msg: String) : IllegalStateException(msg) { val isTransient = false; val isRecoverable = false; val diagnosticInfo = msg }
    class CryptoException(msg: String) : RuntimeException(msg)
    abstract class Callback {
        abstract fun onInputBufferAvailable(codec: MediaCodec, index: Int)
        abstract fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: BufferInfo)
        abstract fun onError(codec: MediaCodec, e: CodecException)
        abstract fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat)
    }

    private var inFormat: MediaFormat? = null
    var outputFormat: MediaFormat = MediaFormat(); private set
    private var video = false
    private var w = 0; private var h = 0
    private var inputs = Array(4) { ByteBuffer.allocate(0) }
    private val freeIn = java.util.ArrayDeque<Int>()
    private class Out(val data: ByteBuffer, val pts: Long, val flags: Int, val size: Int)
    private val outs = HashMap<Int, Out>()
    private val ready = java.util.ArrayDeque<Int>()
    private var nextOut = 0
    private var formatSent = false
    private var surface: android.view.Surface? = null
    private var surfaceFrames = java.util.concurrent.LinkedBlockingQueue<Triple<IntArray, Pair<Int, Int>, Long>>()
    private var eos = false
    private var started = false
    var codecInfo: MediaCodecInfo = MediaCodecInfo(name, encoder, emptyArray()); private set

    fun configure(format: MediaFormat, surface: android.view.Surface?, crypto: Any?, flags: Int) {
        inFormat = format
        val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
        video = mime.startsWith("video") || mime.startsWith("image")
        w = format.getInteger(MediaFormat.KEY_WIDTH, 0); h = format.getInteger(MediaFormat.KEY_HEIGHT, 0)
        val cap = if (video) Yuv.size(w.coerceAtLeast(2), h.coerceAtLeast(2)) else 1 shl 18
        inputs = Array(4) { ByteBuffer.allocateDirect(maxOf(cap, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0))).order(ByteOrder.nativeOrder()) }
        freeIn.clear(); for (i in inputs.indices) freeIn += i
        outputFormat = format.copy().apply {
            if (!encoder) {
                if (video) { setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible); setInteger(MediaFormat.KEY_STRIDE, w); setInteger(MediaFormat.KEY_SLICE_HEIGHT, h) }
                else { setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_RAW); setInteger(MediaFormat.KEY_SAMPLE_RATE, 48000); setInteger(MediaFormat.KEY_CHANNEL_COUNT, 2); setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT) }
            }
        }
        this.surface = surface
    }
    fun setCallback(cb: Callback?) { throw CodecException("Callback mode isn't available on Windows") }
    fun setCallback(cb: Callback?, handler: android.os.Handler?) = setCallback(cb)
    fun start() { started = true }
    fun stop() { started = false }
    fun flush() { outs.clear(); ready.clear(); freeIn.clear(); for (i in inputs.indices) freeIn += i; eos = false }
    fun release() { started = false; inputSurface?.release() }
    fun reset() { flush(); formatSent = false }
    fun setParameters(b: android.os.Bundle?) {}
    fun setVideoScalingMode(mode: Int) {}
    fun setOutputSurface(s: android.view.Surface) { surface = s }

    fun dequeueInputBuffer(timeoutUs: Long): Int = freeIn.pollFirst() ?: INFO_TRY_AGAIN_LATER
    fun getInputBuffer(i: Int): ByteBuffer? = inputs.getOrNull(i)?.also { it.clear() }
    @Deprecated("old style") fun getInputBuffers(): Array<ByteBuffer> = inputs
    @Deprecated("old style") fun getOutputBuffers(): Array<ByteBuffer> = Array(64) { outs[it]?.data ?: ByteBuffer.allocate(0) }

    /** An input picture to fill (encoders taking pictures as YUV planes). */
    fun getInputImage(i: Int): Image? {
        val b = inputs.getOrNull(i) ?: return null
        val arr = ByteArray(Yuv.size(w, h))
        return object : Image(w, h, arr) {
            override fun close() { b.clear(); b.put(arr); b.flip() }
        }.also { pendingImage[i] = arr }
    }
    private val pendingImage = HashMap<Int, ByteArray>()

    fun queueInputBuffer(i: Int, offset: Int, size: Int, pts: Long, flags: Int) {
        val b = inputs[i]
        val img = pendingImage.remove(i)
        val data: ByteBuffer
        val n: Int
        if (img != null) { data = ByteBuffer.wrap(img); n = img.size }
        else { val c = ByteArray(size); val d = b.duplicate(); d.position(offset); d.get(c, 0, size); data = ByteBuffer.wrap(c); n = size }
        freeIn += i
        if (n > 0 || flags and BUFFER_FLAG_END_OF_STREAM == 0) addOut(data, pts, flags and BUFFER_FLAG_END_OF_STREAM.inv() or (if (video) BUFFER_FLAG_KEY_FRAME else 0), n)
        if (flags and BUFFER_FLAG_END_OF_STREAM != 0) { addOut(ByteBuffer.allocate(0), pts, BUFFER_FLAG_END_OF_STREAM, 0); eos = true }
    }
    fun queueSecureInputBuffer(i: Int, offset: Int, info: Any?, pts: Long, flags: Int) = queueInputBuffer(i, offset, 0, pts, flags)

    private fun addOut(d: ByteBuffer, pts: Long, flags: Int, size: Int) {
        val k = nextOut++ and 0xFFFF
        outs[k] = Out(d, pts, flags, size); ready += k
    }

    fun dequeueOutputBuffer(info: BufferInfo, timeoutUs: Long): Int {
        if (!formatSent && (ready.isNotEmpty() || inputSurface != null)) {
            if (video && w == 0) inFormat?.let { }
            formatSent = true
            return INFO_OUTPUT_FORMAT_CHANGED
        }
        // Pictures drawn to an encoder's input surface.
        inputSurface?.let { s ->
            val f = surfaceFrames.poll(if (timeoutUs > 0) timeoutUs / 1000 else 0, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (f != null) {
                val (px, size, ns) = f
                val y = Yuv.fromArgb(px, size.first, size.second)
                addOut(ByteBuffer.wrap(y), ns / 1000, BUFFER_FLAG_KEY_FRAME, y.size)
                lastSize = size
            } else if (surfaceEos) { surfaceEos = false; addOut(ByteBuffer.allocate(0), 0, BUFFER_FLAG_END_OF_STREAM, 0) }
        }
        val k = ready.pollFirst() ?: run { if (timeoutUs > 0 && inputSurface == null) Thread.sleep(minOf(timeoutUs / 1000, 5)); return INFO_TRY_AGAIN_LATER }
        val o = outs[k]!!
        info.offset = 0; info.size = o.size; info.presentationTimeUs = o.pts; info.flags = o.flags
        return k
    }
    private var lastSize: Pair<Int, Int>? = null
    fun getOutputBuffer(i: Int): ByteBuffer? = outs[i]?.data?.let { it.duplicate().also { d -> d.position(0); d.limit(outs[i]!!.size) } }
    fun getOutputImage(i: Int): Image? {
        val o = outs[i] ?: return null
        if (!video || o.size == 0) return null
        val arr = ByteArray(o.size); o.data.duplicate().apply { position(0) }.get(arr)
        return Image(w, h, arr, o.pts * 1000)
    }
    fun getOutputFormat(i: Int): MediaFormat = outputFormat
    fun getInputFormat(): MediaFormat = inFormat ?: MediaFormat()
    fun releaseOutputBuffer(i: Int, render: Boolean) {
        val o = outs.remove(i) ?: return
        if (render && video && o.size > 0) surface?.sink?.invoke(Yuv.toArgb(o.data.array(), w, h), w, h, o.pts * 1000)
    }
    fun releaseOutputBuffer(i: Int, renderTimeNs: Long) = releaseOutputBuffer(i, true)

    // ---- encoders fed by drawing (an input surface)
    private var inputSurface: InputSurface? = null
    @Volatile private var surfaceEos = false
    internal inner class InputSurface : android.view.Surface({ px, sw, sh, ns -> surfaceFrames.put(Triple(px.copyOf(sw * sh), sw to sh, ns)) }) { @Volatile var ptsUs = -1L }
    fun createInputSurface(): android.view.Surface = InputSurface().also { it.defaultWidth = w; it.defaultHeight = h; inputSurface = it }
    fun signalEndOfInputStream() { surfaceEos = true }

    companion object {
        const val BUFFER_FLAG_KEY_FRAME = 1; const val BUFFER_FLAG_SYNC_FRAME = 1; const val BUFFER_FLAG_CODEC_CONFIG = 2; const val BUFFER_FLAG_END_OF_STREAM = 4; const val BUFFER_FLAG_PARTIAL_FRAME = 8
        const val INFO_TRY_AGAIN_LATER = -1; const val INFO_OUTPUT_FORMAT_CHANGED = -2; const val INFO_OUTPUT_BUFFERS_CHANGED = -3
        const val CONFIGURE_FLAG_ENCODE = 1
        const val VIDEO_SCALING_MODE_SCALE_TO_FIT = 1
        const val PARAMETER_KEY_REQUEST_SYNC_FRAME = "request-sync"; const val PARAMETER_KEY_VIDEO_BITRATE = "video-bitrate"
        fun createDecoderByType(mime: String) = MediaCodec("win.decoder." + mime, false)
        fun createEncoderByType(mime: String): MediaCodec {
            if (mime == MediaFormat.MIMETYPE_AUDIO_OPUS || mime == MediaFormat.MIMETYPE_IMAGE_AVIF) throw java.io.IOException("No $mime encoder on Windows")
            return MediaCodec("win.encoder." + mime, true)
        }
        fun createByCodecName(name: String) = MediaCodec(name, name.contains("encoder"))
        /** Pictures drawn to an encoder's input surface get this time (EGL presentation time). */
        fun setSurfaceTime(s: android.view.Surface, us: Long) { (s as? MediaCodec.InputSurface)?.ptsUs = us }
    }
}

class MediaCodecInfo internal constructor(private val nm: String, private val enc: Boolean, private val types: Array<String>) {
    val name: String get() = nm
    val isEncoder: Boolean get() = enc
    val isHardwareAccelerated: Boolean get() = true
    val isSoftwareOnly: Boolean get() = false
    val isVendor: Boolean get() = true
    val canonicalName: String get() = nm
    val supportedTypes: Array<String> get() = types
    fun getCapabilitiesForType(type: String): CodecCapabilities = CodecCapabilities(type)

    class CodecCapabilities(val mimeType: String = "") {
        @JvmField val colorFormats = intArrayOf(COLOR_FormatYUV420Flexible, COLOR_FormatSurface)
        @JvmField val profileLevels: Array<CodecProfileLevel> = arrayOf(CodecProfileLevel().apply { profile = CodecProfileLevel.AVCProfileHigh; level = 0x10000 })
        val videoCapabilities: VideoCapabilities get() = VideoCapabilities()
        val encoderCapabilities: EncoderCapabilities get() = EncoderCapabilities()
        val audioCapabilities: Any? get() = null
        val maxSupportedInstances: Int get() = 16
        fun isFeatureSupported(name: String) = false
        companion object {
            const val COLOR_FormatYUV420Flexible = 0x7F420888; const val COLOR_FormatSurface = 0x7F000789
            const val COLOR_FormatYUV420Planar = 19; const val COLOR_FormatYUV420SemiPlanar = 21; const val COLOR_FormatYUVP010 = 54
            const val COLOR_Format32bitABGR8888 = 0x7F00A000; const val COLOR_FormatYUV422Flexible = 0x7F422888; const val COLOR_FormatYUV444Flexible = 0x7F444888
            const val FEATURE_HdrEditing = "hdr-editing"
        }
    }
    class VideoCapabilities {
        fun isSizeSupported(w: Int, h: Int) = w in 2..8192 && h in 2..8192
        fun areSizeAndRateSupported(w: Int, h: Int, fps: Double) = isSizeSupported(w, h) && fps <= 240
        fun getSupportedFrameRatesFor(w: Int, h: Int) = android.util.Range(1.0, 240.0)
        val supportedWidths get() = android.util.Range(2, 8192)
        val supportedHeights get() = android.util.Range(2, 8192)
        val bitrateRange get() = android.util.Range(1, 400_000_000)
        val widthAlignment get() = 2
        val heightAlignment get() = 2
        val supportedFrameRates get() = android.util.Range(1, 240)
    }
    class EncoderCapabilities {
        fun isBitrateModeSupported(mode: Int) = mode != BITRATE_MODE_CQ
        val qualityRange get() = android.util.Range(0, 100)
        val complexityRange get() = android.util.Range(0, 0)
        companion object { const val BITRATE_MODE_CQ = 0; const val BITRATE_MODE_VBR = 1; const val BITRATE_MODE_CBR = 2 }
    }
    class CodecProfileLevel {
        @JvmField var profile = 0; @JvmField var level = 0
        companion object {
            const val AVCProfileBaseline = 1; const val AVCProfileMain = 2; const val AVCProfileHigh = 8; const val AVCLevel31 = 0x200; const val AVCLevel4 = 0x800; const val AVCLevel41 = 0x1000; const val AVCLevel51 = 0x4000
            const val HEVCProfileMain = 1; const val HEVCProfileMain10 = 2; const val HEVCProfileMain10HDR10 = 0x1000; const val HEVCProfileMain10HDR10Plus = 0x2000
            const val AACObjectLC = 2; const val AV1ProfileMain8 = 1; const val AV1ProfileMain10 = 2; const val DolbyVisionProfileDvheSt = 0x100
        }
    }
}

/** What Windows can encode (H.264 / HEVC pictures and AAC sound through [MediaMuxer]). */
class MediaCodecList(kind: Int) {
    val codecInfos: Array<MediaCodecInfo> = arrayOf(
        MediaCodecInfo("win.encoder.video/avc", true, arrayOf(MediaFormat.MIMETYPE_VIDEO_AVC)),
        MediaCodecInfo("win.encoder.video/hevc", true, arrayOf(MediaFormat.MIMETYPE_VIDEO_HEVC)),
        MediaCodecInfo("win.encoder.audio/mp4a-latm", true, arrayOf(MediaFormat.MIMETYPE_AUDIO_AAC)),
        MediaCodecInfo("win.decoder.video/avc", false, arrayOf(MediaFormat.MIMETYPE_VIDEO_AVC, MediaFormat.MIMETYPE_VIDEO_HEVC, MediaFormat.MIMETYPE_VIDEO_AV1, MediaFormat.MIMETYPE_VIDEO_VP9)),
        MediaCodecInfo("win.decoder.audio/mp4a-latm", false, arrayOf(MediaFormat.MIMETYPE_AUDIO_AAC, MediaFormat.MIMETYPE_AUDIO_MPEG, MediaFormat.MIMETYPE_AUDIO_OPUS, MediaFormat.MIMETYPE_AUDIO_FLAC)),
    )
    fun findEncoderForFormat(f: MediaFormat): String? = codecInfos.firstOrNull { it.isEncoder && f.getString(MediaFormat.KEY_MIME) in it.supportedTypes }?.name
    fun findDecoderForFormat(f: MediaFormat): String? = codecInfos.firstOrNull { !it.isEncoder }?.name
    companion object {
        const val REGULAR_CODECS = 0; const val ALL_CODECS = 1
        @JvmStatic fun getCodecCount() = 5
        @JvmStatic fun getCodecInfoAt(i: Int) = MediaCodecList(0).codecInfos[i]
    }
}

/**
 * Writes MP4 files: pictures (YUV 4:2:0 from the pass-through encoders) are encoded to H.264 or HEVC
 * and sound (16-bit PCM) to AAC with Windows' encoders, as the samples arrive.
 */
class MediaMuxer(private val path: String, private val format: Int) {
    private val tracks = ArrayList<MediaFormat>()
    private var enc: com.dronedirector.veyrocutpro.windows.Encoder? = null
    private var started = false
    private var px = IntArray(0)
    private var lastVideoUs = -1L
    private var audioUs = -1L
    private var pendingAudio = java.io.ByteArrayOutputStream()
    private var orientation = 0

    constructor(fd: java.io.FileDescriptor, format: Int) : this("", format) { throw java.io.IOException("File descriptors aren't writable here") }

    fun addTrack(f: MediaFormat): Int { tracks += f; return tracks.size - 1 }
    fun setOrientationHint(deg: Int) { orientation = deg }
    fun setLocation(lat: Float, lon: Float) {}

    fun start() {
        if (format == OutputFormat.MUXER_OUTPUT_OGG || format == OutputFormat.MUXER_OUTPUT_WEBM) throw IllegalStateException("This file type can't be written on Windows")
        val v = tracks.firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("video") == true }
        val a = tracks.firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio") == true }
        val w = v?.getInteger(MediaFormat.KEY_WIDTH, 0) ?: 0; val hh = v?.getInteger(MediaFormat.KEY_HEIGHT, 0) ?: 0
        val fps = v?.getInteger(MediaFormat.KEY_FRAME_RATE, 30) ?: 30
        val br = v?.getInteger(MediaFormat.KEY_BIT_RATE, 0)?.takeIf { it > 0 } ?: (w * hh * fps / 6).coerceAtLeast(2_000_000)
        val hevc = v?.getString(MediaFormat.KEY_MIME) == MediaFormat.MIMETYPE_VIDEO_HEVC
        enc = com.dronedirector.veyrocutpro.windows.Encoder.open(path, w, hh, fps.toFloat(), br, hevc, a != null)
        started = true
    }

    fun writeSampleData(track: Int, buf: ByteBuffer, info: MediaCodec.BufferInfo) {
        if (!started || info.size <= 0) return
        val f = tracks[track]
        val e = enc ?: return
        if (f.getString(MediaFormat.KEY_MIME)?.startsWith("video") == true) {
            val w = f.getInteger(MediaFormat.KEY_WIDTH); val hh = f.getInteger(MediaFormat.KEY_HEIGHT)
            if (px.size != w * hh) px = IntArray(w * hh)
            val d = ByteArray(info.size); buf.duplicate().apply { position(info.offset) }.get(d)
            if (d.size < Yuv.size(w, hh)) return
            Yuv.toArgb(d, w, hh, px)
            val fps = f.getInteger(MediaFormat.KEY_FRAME_RATE, 30)
            val dur = if (lastVideoUs >= 0) (info.presentationTimeUs - lastVideoUs).coerceAtLeast(1) else 1_000_000L / fps
            lastVideoUs = info.presentationTimeUs
            e.video(px, info.presentationTimeUs, dur)
        } else {
            val d = ByteArray(info.size); buf.duplicate().apply { position(info.offset) }.get(d)
            val frames = d.size / 4
            e.audio(d, info.presentationTimeUs, frames * 1_000_000L / 48000)
        }
    }

    fun stop() { if (started) { started = false; enc?.close(); enc = null } }
    fun release() { if (started) stop() }

    object OutputFormat {
        const val MUXER_OUTPUT_MPEG_4 = 0; const val MUXER_OUTPUT_WEBM = 1; const val MUXER_OUTPUT_3GPP = 2; const val MUXER_OUTPUT_HEIF = 3; const val MUXER_OUTPUT_OGG = 4
    }
}

/** Telling the gallery about new files: Windows sees new files by itself. */
object MediaScannerConnection {
    fun interface OnScanCompletedListener { fun onScanCompleted(path: String?, uri: Uri?) }
    @JvmStatic fun scanFile(c: Context?, paths: Array<String>?, mimes: Array<String>?, cb: OnScanCompletedListener?) { paths?.forEach { cb?.onScanCompleted(it, Uri.fromFile(java.io.File(it))) } }
}
