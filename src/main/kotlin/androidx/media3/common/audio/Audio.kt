package androidx.media3.common.audio

import androidx.media3.common.C
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A step in the sound chain (PCM in, PCM out). Same contract as the phone's. */
interface AudioProcessor {
    class AudioFormat(@JvmField val sampleRate: Int, @JvmField val channelCount: Int, @JvmField val encoding: Int) {
        @JvmField val bytesPerFrame: Int = if (encoding == C.ENCODING_PCM_FLOAT || encoding == C.ENCODING_PCM_32BIT) 4 * channelCount else if (encoding == C.ENCODING_PCM_24BIT) 3 * channelCount else 2 * channelCount
        override fun toString() = "AudioFormat[$sampleRate Hz, $channelCount ch, $encoding]"
        override fun equals(other: Any?) = other is AudioFormat && other.sampleRate == sampleRate && other.channelCount == channelCount && other.encoding == encoding
        override fun hashCode() = sampleRate * 31 + channelCount * 7 + encoding
        companion object { @JvmField val NOT_SET = AudioFormat(-1, -1, -1) }
    }
    class UnhandledAudioFormatException(message: String, val inputAudioFormat: AudioFormat) : Exception(message) {
        constructor(f: AudioFormat) : this("Unhandled input format: $f", f)
    }
    fun configure(inputAudioFormat: AudioFormat): AudioFormat
    fun isActive(): Boolean
    fun queueInput(inputBuffer: ByteBuffer)
    fun queueEndOfStream()
    fun getOutput(): ByteBuffer
    fun isEnded(): Boolean
    fun flush()
    fun reset()
    companion object { @JvmField val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder()) }
}

abstract class BaseAudioProcessor : AudioProcessor {
    protected var inputAudioFormat: AudioProcessor.AudioFormat = AudioProcessor.AudioFormat.NOT_SET; private set
    protected var outputAudioFormat: AudioProcessor.AudioFormat = AudioProcessor.AudioFormat.NOT_SET; private set
    private var pendingIn = AudioProcessor.AudioFormat.NOT_SET
    private var pendingOut = AudioProcessor.AudioFormat.NOT_SET
    private var buffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false

    final override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        pendingIn = inputAudioFormat
        pendingOut = onConfigure(inputAudioFormat)
        return if (isActive()) pendingOut else AudioProcessor.AudioFormat.NOT_SET
    }
    override fun isActive() = pendingOut != AudioProcessor.AudioFormat.NOT_SET
    override fun getOutput(): ByteBuffer { val b = outputBuffer; outputBuffer = AudioProcessor.EMPTY_BUFFER; return b }
    final override fun queueEndOfStream() { inputEnded = true; onQueueEndOfStream() }
    override fun isEnded() = inputEnded && outputBuffer === AudioProcessor.EMPTY_BUFFER
    final override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER; inputEnded = false
        inputAudioFormat = pendingIn; outputAudioFormat = pendingOut
        onFlush()
    }
    final override fun reset() {
        flush(); buffer = AudioProcessor.EMPTY_BUFFER
        pendingIn = AudioProcessor.AudioFormat.NOT_SET; pendingOut = AudioProcessor.AudioFormat.NOT_SET
        inputAudioFormat = AudioProcessor.AudioFormat.NOT_SET; outputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
        onReset()
    }
    protected fun replaceOutputBuffer(size: Int): ByteBuffer {
        if (buffer.capacity() < size) buffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder()) else buffer.clear()
        outputBuffer = buffer
        return buffer
    }
    protected fun hasPendingOutput() = outputBuffer.hasRemaining()
    protected open fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat = throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
    protected open fun onQueueEndOfStream() {}
    protected open fun onFlush() {}
    protected open fun onReset() {}
}

/** Changing speed over time (speed ramps). */
interface SpeedProvider {
    fun getSpeed(timeUs: Long): Float
    fun getNextSpeedChangeTimeUs(timeUs: Long): Long
}

class ChannelMixingMatrix(val inputChannelCount: Int, val outputChannelCount: Int, private val coefficients: FloatArray) {
    fun getMixingCoefficient(input: Int, output: Int) = coefficients[input * outputChannelCount + output]
    companion object {
        @JvmStatic fun create(inputChannelCount: Int, outputChannelCount: Int): ChannelMixingMatrix {
            val c = FloatArray(inputChannelCount * outputChannelCount)
            when {
                inputChannelCount == outputChannelCount -> for (i in 0 until inputChannelCount) c[i * outputChannelCount + i] = 1f
                inputChannelCount == 1 -> for (o in 0 until outputChannelCount) c[o] = 1f
                outputChannelCount == 1 -> for (i in 0 until inputChannelCount) c[i] = 1f / inputChannelCount
                else -> for (i in 0 until inputChannelCount) c[i * outputChannelCount + (i % outputChannelCount)] = 1f
            }
            return ChannelMixingMatrix(inputChannelCount, outputChannelCount, c)
        }
    }
}

/** Mixes channels (e.g. stereo to mono) with a matrix per channel count. 16-bit PCM. */
class ChannelMixingAudioProcessor : BaseAudioProcessor() {
    private val matrices = HashMap<Int, ChannelMixingMatrix>()
    fun putChannelMixingMatrix(m: ChannelMixingMatrix) { matrices[m.inputChannelCount] = m }
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        val m = matrices[inputAudioFormat.channelCount] ?: return AudioProcessor.AudioFormat.NOT_SET
        return AudioProcessor.AudioFormat(inputAudioFormat.sampleRate, m.outputChannelCount, C.ENCODING_PCM_16BIT)
    }
    override fun queueInput(inputBuffer: ByteBuffer) {
        val m = matrices[inputAudioFormat.channelCount] ?: return
        val inCh = m.inputChannelCount; val outCh = m.outputChannelCount
        val frames = inputBuffer.remaining() / (2 * inCh)
        val out = replaceOutputBuffer(frames * 2 * outCh)
        val ib = inputBuffer.order(ByteOrder.nativeOrder())
        val s = FloatArray(inCh)
        repeat(frames) {
            for (i in 0 until inCh) s[i] = ib.getShort().toFloat()
            for (o in 0 until outCh) { var v = 0f; for (i in 0 until inCh) v += s[i] * m.getMixingCoefficient(i, o); out.putShort(v.toInt().coerceIn(-32768, 32767).toShort()) }
        }
        out.flip()
    }
}

/** Changes the sample rate (linear resampling) or the speed without changing pitch. 16-bit PCM. */
class SonicAudioProcessor : BaseAudioProcessor() {
    private var outRate = -1
    private var speed = 1f
    private var pos = 0.0
    private var last = ShortArray(0)
    fun setOutputSampleRateHz(hz: Int) { outRate = hz }
    fun setSpeed(s: Float) { speed = s }
    fun setPitch(p: Float) {}
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        val r = if (outRate > 0) outRate else inputAudioFormat.sampleRate
        return if (r == inputAudioFormat.sampleRate && speed == 1f) AudioProcessor.AudioFormat.NOT_SET else AudioProcessor.AudioFormat(r, inputAudioFormat.channelCount, C.ENCODING_PCM_16BIT)
    }
    override fun queueInput(inputBuffer: ByteBuffer) {
        val ch = inputAudioFormat.channelCount
        val ib = inputBuffer.order(ByteOrder.nativeOrder())
        val n = ib.remaining() / (2 * ch)
        val x = ShortArray(n * ch); ib.asShortBuffer().get(x); ib.position(ib.limit())
        if (last.size != ch) last = ShortArray(ch)
        val step = inputAudioFormat.sampleRate.toDouble() * speed / outputAudioFormat.sampleRate
        val outN = ((n - pos) / step).toInt().coerceAtLeast(0) + 1
        val out = replaceOutputBuffer(outN * 2 * ch)
        while (pos < n) {
            val i = pos.toInt(); val f = (pos - i).toFloat()
            for (c in 0 until ch) {
                val a = if (i == 0) last[c] else x[(i - 1) * ch + c]; val b = x[i * ch + c]
                out.putShort((a + (b - a) * f).toInt().toShort())
            }
            pos += step
        }
        pos -= n
        if (n > 0) for (c in 0 until ch) last[c] = x[(n - 1) * ch + c]
        out.flip()
    }
    override fun onFlush() { pos = 0.0 }
}
