package androidx.media3.exoplayer.mediacodec

/** Decoder choice (Windows picks its own decoders). */
class MediaCodecInfo(@JvmField val name: String, @JvmField val mimeType: String)

fun interface MediaCodecSelector {
    fun getDecoderInfos(mimeType: String, requiresSecureDecoder: Boolean, requiresTunnelingDecoder: Boolean): List<MediaCodecInfo>
    companion object { @JvmField val DEFAULT = MediaCodecSelector { m, _, _ -> listOf(MediaCodecInfo("windows", m)) } }
}
