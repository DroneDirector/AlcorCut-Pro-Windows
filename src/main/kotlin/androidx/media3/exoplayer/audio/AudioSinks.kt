package androidx.media3.exoplayer.audio

import android.content.Context
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer

interface AudioSink

class DefaultAudioSink internal constructor(val processors: List<AudioProcessor>) : AudioSink {
    class Builder(context: Context) {
        private var p: List<AudioProcessor> = emptyList()
        fun setAudioProcessors(a: Array<AudioProcessor>) = apply { p = a.toList() }
        fun setEnableAudioTrackPlaybackParams(b: Boolean) = this
        fun setEnableFloatOutput(b: Boolean) = this
        fun build() = DefaultAudioSink(p)
    }
}

/** Passes the sound on unchanged and hands a copy to [sink]. */
class TeeAudioProcessor(private val sink: AudioBufferSink) : BaseAudioProcessor() {
    interface AudioBufferSink {
        fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int)
        fun handleBuffer(buffer: ByteBuffer)
    }
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat = inputAudioFormat
    override fun onFlush() { if (isActive()) sink.flush(inputAudioFormat.sampleRate, inputAudioFormat.channelCount, inputAudioFormat.encoding) }
    override fun queueInput(inputBuffer: ByteBuffer) {
        val n = inputBuffer.remaining()
        if (n == 0) return
        sink.handleBuffer(inputBuffer.asReadOnlyBuffer())
        replaceOutputBuffer(n).put(inputBuffer).flip()
    }
}
