package androidx.media3.common

import android.net.Uri

/**
 * The phone's media library interfaces on Windows (Media3, Apache 2.0, by Google): the same names and
 * shapes, with playback and export done by the app's own Windows engine. Own code.
 */
object C {
    const val TIME_UNSET = Long.MIN_VALUE + 1
    const val TIME_END_OF_SOURCE = Long.MIN_VALUE
    const val INDEX_UNSET = -1; const val LENGTH_UNSET = -1
    const val TRACK_TYPE_UNKNOWN = -1; const val TRACK_TYPE_DEFAULT = 0; const val TRACK_TYPE_AUDIO = 1; const val TRACK_TYPE_VIDEO = 2; const val TRACK_TYPE_TEXT = 3; const val TRACK_TYPE_IMAGE = 4
    const val ENCODING_INVALID = 0; const val ENCODING_PCM_16BIT = 2; const val ENCODING_PCM_8BIT = 3; const val ENCODING_PCM_FLOAT = 4; const val ENCODING_PCM_24BIT = 0x15; const val ENCODING_PCM_32BIT = 0x16
    const val COLOR_TRANSFER_SDR = 3; const val COLOR_TRANSFER_HLG = 7; const val COLOR_TRANSFER_ST2084 = 6
    const val VIDEO_SCALING_MODE_SCALE_TO_FIT = 1
    const val WAKE_MODE_NONE = 0; const val WAKE_MODE_LOCAL = 1
    const val MICROS_PER_SECOND = 1_000_000L
    const val USAGE_MEDIA = 1; const val AUDIO_CONTENT_TYPE_MOVIE = 3
    @JvmStatic fun usToMs(us: Long) = if (us == TIME_UNSET) TIME_UNSET else us / 1000
    @JvmStatic fun msToUs(ms: Long) = if (ms == TIME_UNSET) TIME_UNSET else ms * 1000
}

object MimeTypes {
    const val VIDEO_H264 = "video/avc"; const val VIDEO_H265 = "video/hevc"; const val VIDEO_AV1 = "video/av01"; const val VIDEO_VP9 = "video/x-vnd.on2.vp9"
    const val VIDEO_MP4 = "video/mp4"; const val VIDEO_DOLBY_VISION = "video/dolby-vision"; const val VIDEO_RAW = "video/raw"
    const val AUDIO_AAC = "audio/mp4a-latm"; const val AUDIO_OPUS = "audio/opus"; const val AUDIO_RAW = "audio/raw"; const val AUDIO_MPEG = "audio/mpeg"; const val AUDIO_FLAC = "audio/flac"; const val AUDIO_AMR_NB = "audio/3gpp"
    const val IMAGE_JPEG = "image/jpeg"; const val IMAGE_PNG = "image/png"; const val APPLICATION_M3U8 = "application/x-mpegURL"
    @JvmStatic fun isVideo(m: String?) = m?.startsWith("video/") == true
    @JvmStatic fun isAudio(m: String?) = m?.startsWith("audio/") == true
    @JvmStatic fun isImage(m: String?) = m?.startsWith("image/") == true
}

/** Something that changes the picture or the sound (see androidx.media3.effect). */
interface Effect

class MediaItem private constructor(
    val mediaId: String,
    val localConfiguration: LocalConfiguration?,
    val clippingConfiguration: ClippingConfiguration,
    val mediaMetadata: MediaMetadata,
    /** Still pictures shown as video (image duration in ms), as set on the builder. */
    val imageDurationMs: Long,
    val frameRate: Float,
) {
    class LocalConfiguration(val uri: Uri, val mimeType: String?, val tag: Any?)
    class MediaMetadata(val title: CharSequence? = null) {
        class Builder { private var t: CharSequence? = null; fun setTitle(s: CharSequence?) = apply { t = s }; fun build() = MediaMetadata(t) }
        companion object { val EMPTY = MediaMetadata() }
    }
    class ClippingConfiguration internal constructor(val startPositionMs: Long, val endPositionMs: Long) {
        val startPositionUs: Long get() = startPositionMs * 1000
        val endPositionUs: Long get() = if (endPositionMs == C.TIME_END_OF_SOURCE) C.TIME_END_OF_SOURCE else endPositionMs * 1000
        class Builder {
            private var s = 0L; private var e = C.TIME_END_OF_SOURCE
            fun setStartPositionMs(v: Long) = apply { s = v }
            fun setEndPositionMs(v: Long) = apply { e = v }
            fun setStartPositionUs(v: Long) = apply { s = v / 1000 }
            fun setEndPositionUs(v: Long) = apply { e = if (v == C.TIME_END_OF_SOURCE) v else v / 1000 }
            fun setRelativeToDefaultPosition(b: Boolean) = this
            fun setStartsAtKeyFrame(b: Boolean) = this
            fun setRelativeToLiveWindow(b: Boolean) = this
            fun build() = ClippingConfiguration(s, e)
        }
        fun buildUpon() = Builder().setStartPositionMs(startPositionMs).setEndPositionMs(endPositionMs)
        companion object { @JvmField val UNSET = ClippingConfiguration(0, C.TIME_END_OF_SOURCE) }
    }
    class Builder {
        private var uri: Uri? = null; private var mime: String? = null; private var id = ""
        private var clip = ClippingConfiguration.UNSET; private var tag: Any? = null; private var meta = MediaMetadata.EMPTY
        private var img = C.TIME_UNSET; private var fps = C.TIME_UNSET.toFloat()
        fun setUri(u: Uri?) = apply { uri = u }
        fun setUri(s: String?) = apply { uri = s?.let { Uri.parse(it) } }
        fun setMimeType(m: String?) = apply { mime = m }
        fun setMediaId(s: String) = apply { id = s }
        fun setTag(t: Any?) = apply { tag = t }
        fun setClippingConfiguration(c: ClippingConfiguration) = apply { clip = c }
        fun setMediaMetadata(m: MediaMetadata) = apply { meta = m }
        fun setImageDurationMs(ms: Long) = apply { img = ms }
        fun build() = MediaItem(id, uri?.let { LocalConfiguration(it, mime, tag) }, clip, meta, img, fps)
    }
    fun buildUpon(): Builder = Builder().also { b -> localConfiguration?.let { b.setUri(it.uri).setMimeType(it.mimeType).setTag(it.tag) }; b.setMediaId(mediaId).setClippingConfiguration(clippingConfiguration).setMediaMetadata(mediaMetadata).setImageDurationMs(imageDurationMs) }
    companion object {
        @JvmStatic fun fromUri(u: Uri) = Builder().setUri(u).build()
        @JvmStatic fun fromUri(s: String) = Builder().setUri(s).build()
        @JvmField val EMPTY = Builder().build()
    }
}

class PlaybackParameters @JvmOverloads constructor(@JvmField val speed: Float, @JvmField val pitch: Float = 1f) {
    fun withSpeed(s: Float) = PlaybackParameters(s, pitch)
    companion object { @JvmField val DEFAULT = PlaybackParameters(1f) }
}

class VideoSize @JvmOverloads constructor(@JvmField val width: Int, @JvmField val height: Int, @JvmField val unappliedRotationDegrees: Int = 0, @JvmField val pixelWidthHeightRatio: Float = 1f) {
    companion object { @JvmField val UNKNOWN = VideoSize(0, 0) }
}

open class PlaybackException(message: String?, cause: Throwable?, val errorCode: Int) : Exception(message, cause) {
    val errorCodeName: String get() = getErrorCodeName(errorCode)
    companion object {
        const val ERROR_CODE_UNSPECIFIED = 1000; const val ERROR_CODE_IO_FILE_NOT_FOUND = 2005; const val ERROR_CODE_DECODING_FAILED = 4001
        const val ERROR_CODE_DECODER_INIT_FAILED = 4001; const val ERROR_CODE_DECODING_FORMAT_UNSUPPORTED = 4005; const val ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES = 4004
        @JvmStatic fun getErrorCodeName(c: Int) = when (c) { 2005 -> "ERROR_CODE_IO_FILE_NOT_FOUND"; 4001 -> "ERROR_CODE_DECODING_FAILED"; 4005 -> "ERROR_CODE_DECODING_FORMAT_UNSUPPORTED"; 4004 -> "ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES"; else -> "ERROR_CODE_UNSPECIFIED" }
    }
}

class VideoFrameProcessingException : Exception {
    constructor(message: String) : super(message)
    constructor(cause: Throwable) : super(cause)
    constructor(message: String, cause: Throwable) : super(message, cause)
}

/** Where and how a picture is placed over another (see StaticOverlaySettings). */
interface OverlaySettings {
    val alphaScale: Float get() = 1f
    val backgroundFrameAnchor: Pair<Float, Float> get() = 0f to 0f
    val overlayFrameAnchor: Pair<Float, Float> get() = 0f to 0f
    val scale: Pair<Float, Float> get() = 1f to 1f
    val rotationDegrees: Float get() = 0f
}

/** How several pictures are put together (export with layers and A/B transitions). */
interface VideoCompositorSettings {
    fun getOutputSize(inputSizes: List<androidx.media3.common.util.Size>): androidx.media3.common.util.Size
    fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings
    companion object {
        @JvmField val DEFAULT = object : VideoCompositorSettings {
            override fun getOutputSize(inputSizes: List<androidx.media3.common.util.Size>) = inputSizes[0]
            override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings = object : OverlaySettings {}
        }
    }
}
