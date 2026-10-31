package androidx.media3.transformer

import android.content.Context
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.common.audio.AudioProcessor

/**
 * Media3's exporter on Windows: the same description of the edit (sequences of clips with effects),
 * rendered by the app's Windows exporter ([com.dronedirector.veyrocutpro.windows.WinExport]). Own code.
 */
class Effects(@JvmField val audioProcessors: List<AudioProcessor>, @JvmField val videoEffects: List<Effect>) {
    companion object { @JvmField val EMPTY = Effects(emptyList(), emptyList()) }
}

class EditedMediaItem private constructor(
    @JvmField val mediaItem: MediaItem, @JvmField val removeAudio: Boolean, @JvmField val removeVideo: Boolean,
    @JvmField val effects: Effects, @JvmField val durationUs: Long, @JvmField val frameRate: Int,
    @JvmField val speedProvider: androidx.media3.common.audio.SpeedProvider?, @JvmField val flattenForSlowMotion: Boolean,
) {
    class Builder(private val item: MediaItem) {
        private var ra = false; private var rv = false; private var fx = Effects.EMPTY; private var dur = androidx.media3.common.C.TIME_UNSET; private var fps = -1
        private var sp: androidx.media3.common.audio.SpeedProvider? = null; private var flat = false
        fun setRemoveAudio(b: Boolean) = apply { ra = b }
        fun setRemoveVideo(b: Boolean) = apply { rv = b }
        fun setEffects(e: Effects) = apply { fx = e }
        fun setDurationUs(us: Long) = apply { dur = us }
        fun setFrameRate(f: Int) = apply { fps = f }
        fun setSpeed(p: androidx.media3.common.audio.SpeedProvider) = apply { sp = p }
        fun setFlattenForSlowMotion(b: Boolean) = apply { flat = b }
        fun build() = EditedMediaItem(item, ra, rv, fx, dur, fps, sp, flat)
    }
    fun buildUpon() = Builder(mediaItem).setRemoveAudio(removeAudio).setRemoveVideo(removeVideo).setEffects(effects).setDurationUs(durationUs).setFrameRate(frameRate)
}

/** Clips (and gaps) played one after another. A gap is an item with no media. */
class EditedMediaItemSequence private constructor(@JvmField val editedMediaItems: List<EditedMediaItem>, @JvmField val isLooping: Boolean, internal val gapsUs: Map<Int, Long>, internal val forceAudio: Boolean, internal val forceVideo: Boolean) {
    constructor(items: List<EditedMediaItem>) : this(items, false, emptyMap(), false, false)
    constructor(vararg items: EditedMediaItem) : this(items.toList())
    constructor(items: List<EditedMediaItem>, looping: Boolean) : this(items, looping, emptyMap(), false, false)
    class Builder() {
        private val items = ArrayList<EditedMediaItem>(); private val gaps = HashMap<Int, Long>(); private var loop = false; private var fa = false; private var fv = false
        constructor(items: List<EditedMediaItem>) : this() { this.items += items }
        constructor(vararg items: EditedMediaItem) : this(items.toList())
        constructor(trackTypes: Set<Int>) : this()
        fun addItem(i: EditedMediaItem) = apply { items += i }
        fun addItems(vararg i: EditedMediaItem) = apply { items += i }
        fun addItems(i: List<EditedMediaItem>) = apply { items += i }
        /** A stretch of nothing (black / silence) of [durationUs]. */
        fun addGap(durationUs: Long) = apply { gaps[items.size] = (gaps[items.size] ?: 0L) + durationUs; items += EditedMediaItem.Builder(MediaItem.EMPTY).setDurationUs(durationUs).build() }
        fun setIsLooping(b: Boolean) = apply { loop = b }
        fun experimentalSetForceAudioTrack(b: Boolean) = apply { fa = b }
        fun experimentalSetForceVideoTrack(b: Boolean) = apply { fv = b }
        fun build() = EditedMediaItemSequence(items.toList(), loop, gaps.toMap(), fa, fv)
    }
}

class Composition private constructor(
    @JvmField val sequences: List<EditedMediaItemSequence>, @JvmField val effects: Effects,
    @JvmField val videoCompositorSettings: VideoCompositorSettings, @JvmField val hdrMode: Int,
    @JvmField val transmuxAudio: Boolean, @JvmField val transmuxVideo: Boolean, @JvmField val forceAudioTrack: Boolean,
) {
    class Builder(private val seqs: List<EditedMediaItemSequence>) {
        constructor(vararg s: EditedMediaItemSequence) : this(s.toList())
        private var fx = Effects.EMPTY; private var vcs = VideoCompositorSettings.DEFAULT; private var hdr = HDR_MODE_KEEP_HDR
        private var ta = false; private var tv = false; private var fa = false
        fun setEffects(e: Effects) = apply { fx = e }
        fun setVideoCompositorSettings(s: VideoCompositorSettings) = apply { vcs = s }
        fun setHdrMode(m: Int) = apply { hdr = m }
        fun setTransmuxAudio(b: Boolean) = apply { ta = b }
        fun setTransmuxVideo(b: Boolean) = apply { tv = b }
        fun experimentalSetForceAudioTrack(b: Boolean) = apply { fa = b }
        fun build() = Composition(seqs, fx, vcs, hdr, ta, tv, fa)
    }
    companion object {
        const val HDR_MODE_KEEP_HDR = 0; const val HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC = 1
        const val HDR_MODE_EXPERIMENTAL_FORCE_INTERPRET_HDR_AS_SDR = 2; const val HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL = 3
    }
}

class ExportResult internal constructor(
    @JvmField val durationMs: Long, @JvmField val fileSizeBytes: Long, @JvmField val averageVideoBitrate: Int, @JvmField val averageAudioBitrate: Int,
    @JvmField val width: Int, @JvmField val height: Int, @JvmField val videoFrameCount: Int, @JvmField val videoEncoderName: String?, @JvmField val audioEncoderName: String?,
    @JvmField val exportException: ExportException?,
) {
    @JvmField val optimizationResult = 0
    @JvmField val processedInputs: List<Any> = emptyList()
    @JvmField val colorInfo: Any? = null
    @JvmField val channelCount = 2
    @JvmField val sampleRate = 48000
}

class ExportException(message: String, cause: Throwable?, @JvmField val errorCode: Int) : Exception(message, cause) {
    val errorCodeName: String get() = getErrorCodeName(errorCode)
    @JvmField val codecInfo: Any? = null
    companion object {
        const val ERROR_CODE_UNSPECIFIED = 1000; const val ERROR_CODE_FAILED_RUNTIME_CHECK = 1001; const val ERROR_CODE_IO_UNSPECIFIED = 2000
        const val ERROR_CODE_IO_FILE_NOT_FOUND = 2005; const val ERROR_CODE_DECODER_INIT_FAILED = 3001; const val ERROR_CODE_DECODING_FAILED = 3002
        const val ERROR_CODE_DECODING_FORMAT_UNSUPPORTED = 3003; const val ERROR_CODE_ENCODER_INIT_FAILED = 4001; const val ERROR_CODE_ENCODING_FAILED = 4002
        const val ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED = 4003; const val ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED = 5001; const val ERROR_CODE_AUDIO_PROCESSING_FAILED = 6001
        const val ERROR_CODE_MUXING_FAILED = 7001
        @JvmStatic fun getErrorCodeName(c: Int) = when (c) {
            2005 -> "ERROR_CODE_IO_FILE_NOT_FOUND"; 3001 -> "ERROR_CODE_DECODER_INIT_FAILED"; 3002 -> "ERROR_CODE_DECODING_FAILED"; 3003 -> "ERROR_CODE_DECODING_FORMAT_UNSUPPORTED"
            4001 -> "ERROR_CODE_ENCODER_INIT_FAILED"; 4002 -> "ERROR_CODE_ENCODING_FAILED"; 5001 -> "ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED"; 6001 -> "ERROR_CODE_AUDIO_PROCESSING_FAILED"
            7001 -> "ERROR_CODE_MUXING_FAILED"; else -> "ERROR_CODE_UNSPECIFIED"
        }
    }
}

class ProgressHolder { @JvmField var progress = 0 }

class VideoEncoderSettings private constructor(@JvmField val bitrate: Int, @JvmField val bitrateMode: Int, @JvmField val profile: Int, @JvmField val level: Int, @JvmField val iFrameIntervalSeconds: Float) {
    class Builder {
        private var br = -1; private var mode = -1; private var p = -1; private var l = -1; private var i = 1f
        fun setBitrate(b: Int) = apply { br = b }
        fun setBitrateMode(m: Int) = apply { mode = m }
        fun setEncodingProfileLevel(profile: Int, level: Int) = apply { p = profile; l = level }
        fun setiFrameIntervalSeconds(s: Float) = apply { i = s }
        fun build() = VideoEncoderSettings(br, mode, p, l, i)
    }
    fun buildUpon() = Builder().setBitrate(bitrate)
    companion object { @JvmField val DEFAULT = Builder().build(); const val NO_VALUE = -1 }
}

interface Codec { interface EncoderFactory; interface DecoderFactory }

class DefaultEncoderFactory private constructor(val settings: VideoEncoderSettings) : Codec.EncoderFactory {
    class Builder(context: Context) {
        private var s = VideoEncoderSettings.DEFAULT
        fun setRequestedVideoEncoderSettings(v: VideoEncoderSettings) = apply { s = v }
        fun setRequestedAudioEncoderSettings(v: Any?) = this
        fun setEnableFallback(b: Boolean) = this
        fun build() = DefaultEncoderFactory(s)
    }
}
class DefaultDecoderFactory private constructor() : Codec.DecoderFactory {
    class Builder(context: Context) {
        fun setMediaCodecSelector(s: androidx.media3.exoplayer.mediacodec.MediaCodecSelector) = this
        fun setEnableDecoderFallback(b: Boolean) = this
        fun setShouldConfigureOperatingRate(b: Boolean) = this
        fun build() = DefaultDecoderFactory()
    }
}

interface AssetLoader { interface Factory }
class DefaultAssetLoaderFactory(context: Context, decoderFactory: Codec.DecoderFactory, clock: androidx.media3.common.util.Clock, mediaSourceFactory: Any? = null) : AssetLoader.Factory {
    constructor(context: Context) : this(context, DefaultDecoderFactory.Builder(context).build(), androidx.media3.common.util.Clock.DEFAULT)
}

class Transformer private constructor(
    private val context: Context, internal val videoMime: String, internal val audioMime: String, internal val encoder: DefaultEncoderFactory?,
    private val listeners: List<Listener>, internal val removeAudio: Boolean, internal val removeVideo: Boolean,
) {
    interface Listener {
        fun onCompleted(composition: Composition, exportResult: ExportResult) {}
        fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {}
        fun onFallbackApplied(composition: Composition, originalTransformationRequest: Any?, fallbackTransformationRequest: Any?) {}
    }
    class Builder(private val context: Context) {
        private var v = androidx.media3.common.MimeTypes.VIDEO_H264; private var a = androidx.media3.common.MimeTypes.AUDIO_AAC
        private var enc: DefaultEncoderFactory? = null; private val ls = ArrayList<Listener>(); private var ra = false; private var rv = false
        fun setVideoMimeType(m: String) = apply { v = m }
        fun setAudioMimeType(m: String) = apply { a = m }
        fun setEncoderFactory(f: Codec.EncoderFactory) = apply { enc = f as? DefaultEncoderFactory }
        fun setAssetLoaderFactory(f: AssetLoader.Factory) = this
        fun addListener(l: Listener) = apply { ls += l }
        fun setRemoveAudio(b: Boolean) = apply { ra = b }
        fun setRemoveVideo(b: Boolean) = apply { rv = b }
        fun setLooper(l: android.os.Looper?) = this
        fun experimentalSetTrimOptimizationEnabled(b: Boolean) = this
        fun setPortraitEncodingEnabled(b: Boolean) = this
        fun setMuxerFactory(f: Any?) = this
        fun build() = Transformer(context, v, a, enc, ls.toList(), ra, rv)
    }

    private var job: com.dronedirector.veyrocutpro.windows.WinExport? = null

    fun start(composition: Composition, path: String) {
        job = com.dronedirector.veyrocutpro.windows.WinExport(context, this, composition, path, listeners).also { it.start() }
    }
    fun start(item: EditedMediaItem, path: String) = start(Composition.Builder(EditedMediaItemSequence(item)).build(), path)
    fun start(item: MediaItem, path: String) = start(EditedMediaItem.Builder(item).build(), path)
    fun cancel() { job?.cancel() }
    fun getProgress(h: ProgressHolder): Int { val j = job ?: return PROGRESS_STATE_NOT_STARTED; h.progress = j.progress; return if (j.done) PROGRESS_STATE_NOT_STARTED else PROGRESS_STATE_AVAILABLE }

    companion object {
        const val PROGRESS_STATE_NOT_STARTED = 0; const val PROGRESS_STATE_WAITING_FOR_AVAILABILITY = 1; const val PROGRESS_STATE_AVAILABLE = 2; const val PROGRESS_STATE_UNAVAILABLE = 3
        internal fun result(ms: Long, bytes: Long, w: Int, h: Int, frames: Int, e: ExportException?) = ExportResult(ms, bytes, 0, 0, w, h, frames, "Windows", "Windows", e)
    }
}
