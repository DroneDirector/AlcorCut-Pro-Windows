package com.dronedirector.veyrocutpro.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tanh

/** One snapshot of the audio graph: recent levels (0..1, oldest first) plus current and peak dBFS. */
class AudioLevels(val history: FloatArray, val db: Float, val peakDb: Float, val live: Boolean, val leftDb: Float = db, val rightDb: Float = db, val stereo: Boolean = false)

/**
 * Real-time microphone levels for the video modes.
 * While idle it listens with its own [AudioRecord]; while recording, the recorder owns the
 * microphone and feeds levels in through [pushAmplitude].
 */
class AudioMeter(private val context: Context) {

    private val size = 64
    private val ring = FloatArray(size)
    private var head = 0
    private var peakDb = MIN_DB
    private var peakHoldFrames = 0

    private val _levels = MutableStateFlow(AudioLevels(FloatArray(size), MIN_DB, MIN_DB, false))
    val levels: StateFlow<AudioLevels> = _levels.asStateFlow()

    @Volatile private var running = false
    /** Input gain (dB) also applied to the monitoring meter so it matches what will be recorded. */
    @Volatile var gainDb: Int = 0
    /** Play the microphone to the headphones (only switched on when headphones are connected). */
    @Volatile var headphones: Boolean = false
    /** Headphone volume 0..1. */
    @Volatile var monitorVolume: Float = 0.8f
    @Volatile var limiter: Boolean = true
    /** True while the microphone delivers nothing but silence (e.g. Android hands it to the recorder). */
    @Volatile var silenced: Boolean = false
        private set
    val isRunning: Boolean get() = running
    private var thread: Thread? = null

    fun hasPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun startMonitoring() {
        if (running || !hasPermission()) return
        running = true
        thread = Thread({
            val rate = 44_100
            // Stereo when the phone has two microphones for video (left / right meters), else mono.
            fun open(stereo: Boolean): AudioRecord? {
                val mask = if (stereo) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
                val minBuf = AudioRecord.getMinBufferSize(rate, mask, AudioFormat.ENCODING_PCM_16BIT)
                if (minBuf <= 0) return null
                return try {
                    AudioRecord(MediaRecorder.AudioSource.CAMCORDER, rate, mask, AudioFormat.ENCODING_PCM_16BIT, max(minBuf, 8192))
                        .takeIf { it.state == AudioRecord.STATE_INITIALIZED && (!stereo || it.channelCount == 2) }
                        ?.also { com.dronedirector.veyrocutpro.ui.AudioHardware.applyTo(it) }
                } catch (_: Throwable) { null }
            }
            val record = open(true) ?: open(false)
            if (record == null) { running = false; return@Thread }
            val ch = record.channelCount.coerceIn(1, 2)
            // Small reads keep headphone latency low; the meter is updated every ~40 ms.
            val raw = ShortArray(rate / 100 * ch)
            val buf = ShortArray(rate / 100)
            val out = ShortArray(buf.size)
            var accL = 0.0; var accR = 0.0
            var track: AudioTrack? = null
            var acc = 0.0
            var accN = 0
            var zeroRun = 0
            try {
                record.startRecording()
                while (running) {
                    val got = record.read(raw, 0, raw.size)
                    if (got <= 0) continue
                    val n = got / ch
                    val g0 = 10.0.pow(gainDb / 20.0)
                    if (ch == 2) {
                        for (i in 0 until n) {
                            val l = raw[2 * i]; val r = raw[2 * i + 1]
                            val lf = l / 32768.0 * g0; val rf = r / 32768.0 * g0
                            accL += lf * lf; accR += rf * rf
                            buf[i] = ((l + r) / 2).toShort()
                        }
                    } else {
                        for (i in 0 until n) { buf[i] = raw[i]; val x = raw[i] / 32768.0 * g0; accL += x * x }
                        accR = accL
                    }
                    val g = 10.0.pow(gainDb / 20.0).toFloat()
                    val lim = limiter
                    var allZero = true
                    for (i in 0 until n) {
                        if (buf[i].toInt() != 0) allZero = false
                        var x = buf[i] / 32768f * g
                        if (lim) {
                            val a = abs(x)
                            if (a > 0.8f) x = (0.8f + 0.2f * tanh((a - 0.8f) / 0.2f)) * if (x < 0) -1f else 1f
                        }
                        x = x.coerceIn(-1f, 0.99997f)
                        acc += (x * x).toDouble()
                        out[i] = (x * 32767f).toInt().toShort()
                    }
                    accN += n
                    zeroRun = if (allZero) zeroRun + n else 0
                    silenced = zeroRun > rate // a full second of digital silence
                    if (accN >= rate / 25) {
                        pushRms(sqrt(acc / accN).toFloat(), sqrt(accL / accN).toFloat(), sqrt(accR / accN).toFloat(), ch == 2)
                        acc = 0.0; accN = 0; accL = 0.0; accR = 0.0
                    }
                    if (headphones) {
                        val t = track ?: createTrack(rate).also { track = it }
                        if (t != null) {
                            val vol = monitorVolume
                            if (vol < 0.999f) for (i in 0 until n) out[i] = (out[i] * vol).toInt().toShort()
                            t.write(out, 0, n)
                        }
                    } else if (track != null) {
                        try { track?.stop() } catch (_: Throwable) {}
                        track?.release()
                        track = null
                    }
                }
            } catch (_: Throwable) {
            } finally {
                try { record.stop() } catch (_: Throwable) {}
                record.release()
                try { track?.stop() } catch (_: Throwable) {}
                track?.release()
                silenced = false
            }
        }, "ProCamAudioMeter").apply { start() }
    }

    private fun createTrack(rate: Int): AudioTrack? = try {
        val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(max(min, 2048))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
            .also { it.play() }
    } catch (_: Throwable) {
        null
    }

    fun stopMonitoring() {
        running = false
        thread?.join(300)
        thread = null
    }

    /** Amplitude 0..1 from CameraX recording stats. */
    fun pushAmplitude(amplitude: Float) = pushRms(amplitude * 0.7071f)

    /** Level of the processed (gain-adjusted) recording audio. */
    fun pushProcessed(rms: Float) = pushRms(rms)

    fun clear() {
        ring.fill(0f)
        peakDb = MIN_DB
        _levels.value = AudioLevels(FloatArray(size), MIN_DB, MIN_DB, false)
    }

    private fun toDb(rms: Float) = if (rms <= 0.00001f) MIN_DB else (20f * log10(rms)).coerceIn(MIN_DB, 0f)

    @Synchronized
    private fun pushRms(rms: Float, l: Float = rms, r: Float = rms, stereo: Boolean = false) {
        val db = toDb(rms)
        ring[head] = (db - MIN_DB) / -MIN_DB
        head = (head + 1) % size
        if (db >= peakDb) {
            peakDb = db
            peakHoldFrames = 40
        } else if (--peakHoldFrames <= 0) {
            peakDb = max(db, peakDb - 1.5f)
        }
        val ordered = FloatArray(size) { ring[(head + it) % size] }
        _levels.value = AudioLevels(ordered, db, peakDb, true, toDb(l), toDb(r), stereo)
    }

    companion object {
        const val MIN_DB = -60f
    }
}
