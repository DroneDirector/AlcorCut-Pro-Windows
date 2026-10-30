package android.media

import javax.sound.sampled.AudioFormat as JFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip

/** Short sounds (shutter clicks, beeps) through the PC's speakers, like the phone's static AudioTrack. Own code. */
class AudioTrack private constructor(private val rate: Int, private val channels: Int) {
    private var pcm = ByteArray(0)
    private var clip: Clip? = null

    fun write(data: ShortArray, off: Int, n: Int): Int {
        val b = ByteArray(n * 2)
        for (i in 0 until n) { val v = data[off + i].toInt(); b[i * 2] = v.toByte(); b[i * 2 + 1] = (v shr 8).toByte() }
        pcm = b; clip?.close(); clip = null
        return n
    }
    fun play() {
        try {
            val c = clip ?: AudioSystem.getClip().also { it.open(JFormat(rate.toFloat(), 16, channels, true, false), pcm, 0, pcm.size); clip = it }
            c.framePosition = 0; c.start()
        } catch (_: Throwable) {}
    }
    fun stop() { try { clip?.stop() } catch (_: Throwable) {} }
    fun reloadStaticData(): Int = 0
    fun release() { try { clip?.close() } catch (_: Throwable) {}; clip = null }

    class Builder {
        private var rate = 44100; private var ch = 1
        fun setAudioAttributes(a: AudioAttributes) = this
        fun setAudioFormat(f: AudioFormat) = apply { rate = f.sampleRate; ch = if (f.channelMask == AudioFormat.CHANNEL_OUT_STEREO) 2 else 1 }
        fun setTransferMode(m: Int) = this
        fun setBufferSizeInBytes(n: Int) = this
        fun build() = AudioTrack(rate, ch)
    }
    companion object { const val MODE_STATIC = 0; const val MODE_STREAM = 1 }
}

class AudioAttributes private constructor() {
    class Builder {
        fun setUsage(u: Int) = this
        fun setContentType(t: Int) = this
        fun build() = AudioAttributes()
    }
    companion object {
        const val USAGE_MEDIA = 1; const val USAGE_ASSISTANCE_SONIFICATION = 13
        const val CONTENT_TYPE_MUSIC = 2; const val CONTENT_TYPE_SONIFICATION = 4
    }
}

class AudioFormat private constructor(val sampleRate: Int, val channelMask: Int, val encoding: Int) {
    class Builder {
        private var rate = 44100; private var mask = CHANNEL_OUT_MONO; private var enc = ENCODING_PCM_16BIT
        fun setEncoding(e: Int) = apply { enc = e }
        fun setSampleRate(r: Int) = apply { rate = r }
        fun setChannelMask(m: Int) = apply { mask = m }
        fun build() = AudioFormat(rate, mask, enc)
    }
    companion object {
        const val ENCODING_PCM_16BIT = 2; const val ENCODING_PCM_FLOAT = 4
        const val CHANNEL_OUT_MONO = 4; const val CHANNEL_OUT_STEREO = 12
        const val CHANNEL_IN_MONO = 16; const val CHANNEL_IN_STEREO = 12
    }
}
