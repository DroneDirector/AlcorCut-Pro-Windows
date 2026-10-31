package androidx.media3.exoplayer

import android.content.Context
import androidx.media3.common.Effect
import androidx.media3.common.Player

/** Media3's player on the Windows engine ([com.dronedirector.veyrocutpro.windows.WinPlayer]). */
interface ExoPlayer : Player {
    fun setVideoTextureView(v: android.view.TextureView?)
    fun clearVideoTextureView(v: android.view.TextureView?)
    fun setVideoSurfaceView(v: android.view.SurfaceView?) {}
    fun setVideoEffects(effects: List<Effect>)
    fun setSeekParameters(p: SeekParameters?)
    fun setVideoScalingMode(mode: Int) {}
    fun setHandleAudioBecomingNoisy(b: Boolean) {}
    fun setWakeMode(m: Int) {}
    val audioSessionId: Int get() = 0
    class Builder(private val context: Context, private val renderers: DefaultRenderersFactory? = null) {
        fun setSeekBackIncrementMs(ms: Long) = this
        fun setSeekForwardIncrementMs(ms: Long) = this
        fun setLooper(l: android.os.Looper?) = this
        fun setUseLazyPreparation(b: Boolean) = this
        fun setPauseAtEndOfMediaItems(b: Boolean) = this
        fun setRenderersFactory(r: DefaultRenderersFactory) = ExoPlayer.Builder(context, r)
        fun setHandleAudioBecomingNoisy(b: Boolean) = this
        fun setAudioAttributes(a: Any?, focus: Boolean) = this
        fun build(): ExoPlayer = com.dronedirector.veyrocutpro.windows.WinPlayer(context, renderers?.audioProcessors(context) ?: emptyList())
    }
}

class SeekParameters private constructor(val toleranceBeforeUs: Long, val toleranceAfterUs: Long) {
    companion object {
        @JvmField val EXACT = SeekParameters(0, 0)
        @JvmField val CLOSEST_SYNC = SeekParameters(Long.MAX_VALUE, Long.MAX_VALUE)
        @JvmField val PREVIOUS_SYNC = SeekParameters(Long.MAX_VALUE, 0)
        @JvmField val NEXT_SYNC = SeekParameters(0, Long.MAX_VALUE)
        @JvmField val DEFAULT = EXACT
    }
}

/** Where the sound goes: the shared code adds its own sound steps here (see DefaultAudioSink.Builder). */
open class DefaultRenderersFactory(val context: Context) {
    open fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): androidx.media3.exoplayer.audio.AudioSink? = null
    fun setMediaCodecSelector(s: androidx.media3.exoplayer.mediacodec.MediaCodecSelector) = this
    fun setEnableDecoderFallback(b: Boolean) = this
    fun setExtensionRendererMode(m: Int) = this
    fun setEnableAudioTrackPlaybackParams(b: Boolean) = this
    internal fun audioProcessors(c: Context): List<androidx.media3.common.audio.AudioProcessor> =
        (buildAudioSink(c, false, true) as? androidx.media3.exoplayer.audio.DefaultAudioSink)?.processors ?: emptyList()
    companion object { const val EXTENSION_RENDERER_MODE_OFF = 0; const val EXTENSION_RENDERER_MODE_ON = 1; const val EXTENSION_RENDERER_MODE_PREFER = 2 }
}
