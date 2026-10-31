package android.media

import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine
import javax.sound.sampled.AudioFormat as JFormat

/** Microphone input on Windows (Java Sound), with the microphone chosen in Audio hardware. Own code. */
class AudioRecord(source: Int, private val rate: Int, channelMask: Int, encoding: Int, bufferSize: Int) {
    val channelCount: Int = if (channelMask == AudioFormat.CHANNEL_IN_STEREO) 2 else 1
    val sampleRate: Int get() = rate
    private var line: TargetDataLine? = null
    var state: Int = STATE_UNINITIALIZED; private set
    private var mixer: String? = preferredInput

    init { open() }

    private fun open() {
        val f = JFormat(rate.toFloat(), 16, channelCount, true, false)
        val info = DataLine.Info(TargetDataLine::class.java, f)
        line = try {
            val m = mixer?.let { name -> AudioSystem.getMixerInfo().firstOrNull { it.name == name } }
            ((if (m != null) AudioSystem.getMixer(m).getLine(info) else AudioSystem.getLine(info)) as TargetDataLine).also { it.open(f, maxOf(bufferSize(), 4096)) }
        } catch (_: Throwable) { null }
        state = if (line != null) STATE_INITIALIZED else STATE_UNINITIALIZED
    }
    private fun bufferSize() = rate / 10 * channelCount * 2

    /** Audio hardware: a named Windows input. */
    fun setPreferredDevice(d: AudioDeviceInfo?): Boolean {
        if (d?.mixerName == mixer) return true
        mixer = d?.mixerName
        val was = recording; try { line?.close() } catch (_: Throwable) {}
        open(); if (was) startRecording()
        return true
    }

    private var recording = false
    fun startRecording() { line?.start(); recording = true }
    fun stop() { recording = false; try { line?.stop() } catch (_: Throwable) {} }
    fun release() { stop(); try { line?.close() } catch (_: Throwable) {}; line = null; state = STATE_UNINITIALIZED }
    val recordingState: Int get() = if (recording) RECORDSTATE_RECORDING else RECORDSTATE_STOPPED

    private var bytes = ByteArray(0)
    fun read(dst: ShortArray, off: Int, n: Int): Int {
        val l = line ?: return ERROR_INVALID_OPERATION
        if (bytes.size < n * 2) bytes = ByteArray(n * 2)
        val got = l.read(bytes, 0, n * 2)
        if (got <= 0) return 0
        for (i in 0 until got / 2) dst[off + i] = ((bytes[i * 2].toInt() and 0xFF) or (bytes[i * 2 + 1].toInt() shl 8)).toShort()
        return got / 2
    }
    fun read(dst: ByteArray, off: Int, n: Int): Int = line?.read(dst, off, n - n % (2 * channelCount)) ?: ERROR_INVALID_OPERATION
    fun read(dst: java.nio.ByteBuffer, n: Int): Int {
        val b = ByteArray(n); val got = read(b, 0, n); if (got > 0) dst.put(b, 0, got); return got
    }
    fun getTimestamp(t: AudioTimestamp, base: Int): Int = ERROR

    companion object {
        const val STATE_UNINITIALIZED = 0; const val STATE_INITIALIZED = 1
        const val RECORDSTATE_STOPPED = 1; const val RECORDSTATE_RECORDING = 3
        const val ERROR = -1; const val ERROR_BAD_VALUE = -2; const val ERROR_INVALID_OPERATION = -3
        fun getMinBufferSize(rate: Int, mask: Int, enc: Int): Int = rate / 25 * (if (mask == AudioFormat.CHANNEL_IN_STEREO) 4 else 2)
        /** The chosen microphone (mixer name), or null for Windows' default. */
        @Volatile var preferredInput: String? = null
    }
}

class AudioTimestamp { var framePosition = 0L; var nanoTime = 0L; companion object { const val TIMEBASE_MONOTONIC = 0 } }

/**
 * Sound recording to a file (voiceover): the microphone (Audio hardware's choice) to AAC in an .m4a
 * with Windows' encoder. Own code.
 */
class MediaRecorder() {
    constructor(context: android.content.Context) : this()
    object AudioSource { const val DEFAULT = 0; const val MIC = 1; const val CAMCORDER = 5; const val VOICE_RECOGNITION = 6; const val UNPROCESSED = 9 }
    object OutputFormat { const val DEFAULT = 0; const val THREE_GPP = 1; const val MPEG_4 = 2; const val AAC_ADTS = 6; const val OGG = 11 }
    object AudioEncoder { const val DEFAULT = 0; const val AMR_NB = 1; const val AAC = 3; const val HE_AAC = 4; const val OPUS = 7 }
    private var path: String? = null
    private var rate = 48000
    private var bitrate = 160_000
    @Volatile private var running = false
    private var thread: Thread? = null
    @Volatile private var peak = 0
    fun setAudioSource(s: Int) {}
    fun setOutputFormat(f: Int) {}
    fun setAudioEncoder(e: Int) {}
    fun setAudioEncodingBitRate(b: Int) { bitrate = b }
    fun setAudioSamplingRate(r: Int) { rate = r }
    fun setAudioChannels(c: Int) {}
    fun setOutputFile(p: String) { path = p }
    fun setOutputFile(f: java.io.File) { path = f.path }
    fun prepare() { if (path == null) throw IllegalStateException("No output file") }
    val maxAmplitude: Int get() { val p = peak; peak = 0; return p }
    fun start() {
        val rec = AudioRecord(AudioSource.MIC, 48000, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT, 1 shl 15)
        if (rec.state != AudioRecord.STATE_INITIALIZED) throw IllegalStateException("No microphone")
        val nat = com.dronedirector.veyrocutpro.windows.MfNative
        val h = if (nat.ok) nat.recOpen(path!!, 0, 0, 30, 1, 0, false, 48000, 2) else 0L
        if (nat.ok && h == 0L) { rec.release(); throw IllegalStateException(nat.error()) }
        val wav = if (h == 0L) java.io.RandomAccessFile(path!!, "rw").also { it.setLength(0); it.write(ByteArray(44)) } else null
        running = true
        rec.startRecording()
        thread = Thread({
            val ch = rec.channelCount
            val raw = ShortArray(1024 * ch); val pcm = ByteArray(1024 * 4); var frames = 0L
            try {
                while (running) {
                    val got = rec.read(raw, 0, raw.size); if (got <= 0) continue
                    val n = got / ch
                    for (i in 0 until n) for (c in 0..1) {
                        val v = raw[i * ch + (if (ch == 2) c else 0)].toInt()
                        if (kotlin.math.abs(v) > peak) peak = kotlin.math.abs(v)
                        pcm[i * 4 + c * 2] = v.toByte(); pcm[i * 4 + c * 2 + 1] = (v shr 8).toByte()
                    }
                    if (h != 0L) nat.recAudio(h, pcm, n * 4, frames * 10_000_000 / 48000, n * 10_000_000L / 48000) else wav!!.write(pcm, 0, n * 4)
                    frames += n
                }
            } finally {
                rec.stop(); rec.release()
                if (h != 0L) nat.recClose(h)
                wav?.let { w -> val len = w.length() - 44; w.seek(0); w.write(wavHeader(len)); w.close() }
            }
        }, "voice-rec").apply { isDaemon = true; start() }
    }
    private fun wavHeader(len: Long): ByteArray = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray()); putInt((len + 36).toInt()); put("WAVEfmt ".toByteArray()); putInt(16); putShort(1); putShort(2); putInt(48000); putInt(48000 * 4); putShort(4); putShort(16)
        put("data".toByteArray()); putInt(len.toInt())
    }.array()
    fun stop() { running = false; thread?.join(3000); thread = null }
    fun pause() {}
    fun resume() {}
    fun reset() { stop() }
    fun release() { if (running) stop() }
}

/** A Windows sound device (Java Sound mixer). */
class AudioDeviceInfo(val mixerName: String, val isSource: Boolean) {
    val productName: CharSequence get() = mixerName
    val type: Int get() = TYPE_UNKNOWN
    val address: String get() = mixerName
    val id: Int get() = mixerName.hashCode()
    companion object {
        const val TYPE_UNKNOWN = 0; const val TYPE_BUILTIN_EARPIECE = 1; const val TYPE_BUILTIN_SPEAKER = 2; const val TYPE_WIRED_HEADSET = 3
        const val TYPE_WIRED_HEADPHONES = 4; const val TYPE_LINE_ANALOG = 5; const val TYPE_LINE_DIGITAL = 6; const val TYPE_BLUETOOTH_SCO = 7
        const val TYPE_BLUETOOTH_A2DP = 8; const val TYPE_HDMI = 9; const val TYPE_DOCK = 13; const val TYPE_USB_ACCESSORY = 12
        const val TYPE_USB_DEVICE = 11; const val TYPE_BUILTIN_MIC = 15; const val TYPE_USB_HEADSET = 22; const val TYPE_AUX_LINE = 19
    }
}

/** Sound devices on this PC: microphones (inputs) and speakers / headphones (outputs). */
class AudioManager {
    fun getDevices(flags: Int): Array<AudioDeviceInfo> = list(flags == GET_DEVICES_INPUTS).toTypedArray()
    val isWiredHeadsetOn: Boolean get() = false
    fun registerAudioDeviceCallback(cb: AudioDeviceCallback, h: android.os.Handler?) {}
    fun unregisterAudioDeviceCallback(cb: AudioDeviceCallback) {}
    companion object {
        const val GET_DEVICES_INPUTS = 1; const val GET_DEVICES_OUTPUTS = 2; const val GET_DEVICES_ALL = 3
        fun list(inputs: Boolean): List<AudioDeviceInfo> = try {
            val want = if (inputs) TargetDataLine::class.java else javax.sound.sampled.SourceDataLine::class.java
            AudioSystem.getMixerInfo().filter { mi ->
                !mi.name.startsWith("Port ") && !mi.name.contains("Primary Sound") &&
                    AudioSystem.getMixer(mi).targetLineInfo.let { if (inputs) it else AudioSystem.getMixer(mi).sourceLineInfo }.any { it.lineClass == want }
            }.map { AudioDeviceInfo(it.name, !inputs) }.distinctBy { it.mixerName }
        } catch (_: Throwable) { emptyList() }
    }
}
abstract class AudioDeviceCallback {
    open fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) {}
    open fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) {}
}
