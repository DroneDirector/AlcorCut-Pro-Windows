package com.dronedirector.veyrocutpro.camera

import com.dronedirector.veyrocutpro.windows.MfNative
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Records an MP4 on Windows: the camera's pictures (H.264 or HEVC with the PC's encoder) and the
 * microphone (AAC, with the input gain and soft limiter). Time-lapse and slow motion write their
 * pictures at a fixed 30 fps (no sound). Own code.
 *
 * [timing]: 0 = real time, 1 = one picture per 1/30 s of video (time-lapse), 2 = slowed down by fps / 30.
 */
class WinRecorder(
    val file: File, val width: Int, val height: Int, private val fps: Float, bitrate: Int, hevc: Boolean,
    private val withAudio: Boolean, private val gainDb: () -> Int, private val limiter: () -> Boolean,
    private val onLevel: (Float, Float, Float) -> Unit, private val timing: Int = 0, outFps: Float = 30f,
) {
    private val fpsNum = if (timing == 0) Math.round(fps * 1000) else Math.round(outFps * 1000)
    private val h: Long
    private val rate = 48_000
    private val queue = ArrayBlockingQueue<Pair<IntArray, Long>>(6)
    private val pool = ArrayBlockingQueue<IntArray>(8)
    @Volatile private var running = true
    @Volatile var paused = false
        set(v) { if (v && !field) pauseAt = System.nanoTime() else if (!v && field) pausedNs += System.nanoTime() - pauseAt; field = v }
    private var pauseAt = 0L
    @Volatile private var pausedNs = 0L
    private val t0 = System.nanoTime()
    @Volatile var frames = 0; private set
    @Volatile var dropped = 0; private set
    private var writer: Thread? = null
    private var audio: Thread? = null
    @Volatile var error: String? = null; private set

    init {
        h = MfNative.recOpen(file.path, width, height, fpsNum, 1000, bitrate, hevc, if (withAudio) rate else 0, 2)
        if (h == 0L) throw IllegalStateException(MfNative.error().ifBlank { "The video encoder couldn't start" })
        writer = Thread({
            val frameDur = (10_000_000.0 * 1000 / fpsNum).toLong()
            var lastTs = -1L
            while (running || queue.isNotEmpty()) {
                val (px, ts0) = queue.poll(100, TimeUnit.MILLISECONDS) ?: continue
                val ts = when (timing) { 0 -> maxOf(ts0, lastTs + 1); else -> frames * frameDur }
                if (!MfNative.recVideo(h, px, ts, frameDur)) error = MfNative.error()
                lastTs = ts
                frames++
                pool.offer(px)
            }
        }, "rec-video").apply { isDaemon = true; start() }
        if (withAudio && timing == 0) audio = Thread({ recordAudio() }, "rec-audio").apply { isDaemon = true; start() }
    }

    /** Time since the start, without pauses, in 100 ns units. */
    private fun now100ns(): Long = (System.nanoTime() - t0 - pausedNs) / 100

    /** A picture for the clip (copied). Skipped while paused or when the encoder falls behind. */
    fun frame(px: IntArray) {
        if (!running || paused) return
        val buf = pool.poll() ?: IntArray(width * height)
        System.arraycopy(px, 0, buf, 0, width * height)
        if (!queue.offer(buf to now100ns())) { dropped++; pool.offer(buf) }
    }

    /** A picture for the clip, waiting for the encoder instead of skipping (for made-up videos). */
    fun frameWait(px: IntArray) {
        val buf = pool.poll() ?: IntArray(width * height)
        System.arraycopy(px, 0, buf, 0, width * height)
        queue.put(buf to now100ns())
    }

    private fun recordAudio() {
        val rec = android.media.AudioRecord(android.media.MediaRecorder.AudioSource.CAMCORDER, rate, android.media.AudioFormat.CHANNEL_IN_STEREO, android.media.AudioFormat.ENCODING_PCM_16BIT, 1 shl 15)
        if (rec.state != android.media.AudioRecord.STATE_INITIALIZED) { error = "No microphone found — recording without sound"; return }
        val ch = rec.channelCount
        val n = rate / 50 * ch                          // 20 ms
        val raw = ShortArray(n)
        val out = ByteArray(rate / 50 * 4)
        var samples = 0L
        var started = false
        try {
            rec.startRecording()
            while (running) {
                val got = rec.read(raw, 0, n)
                if (got <= 0) continue
                if (paused) continue
                if (!started) { started = true; samples = now100ns() * rate / 10_000_000 }
                val frames = got / ch
                val g = 10.0.pow(gainDb() / 20.0).toFloat(); val lim = limiter()
                var accL = 0.0; var accR = 0.0
                for (i in 0 until frames) {
                    for (c in 0..1) {
                        var x = raw[i * ch + (if (ch == 2) c else 0)] / 32768f * g
                        if (lim) { val a = abs(x); if (a > 0.8f) x = (0.8f + 0.2f * tanh((a - 0.8f) / 0.2f)) * if (x < 0) -1f else 1f }
                        x = x.coerceIn(-1f, 0.99997f)
                        if (c == 0) accL += (x * x).toDouble() else accR += (x * x).toDouble()
                        val v = (x * 32767f).toInt()
                        out[i * 4 + c * 2] = v.toByte(); out[i * 4 + c * 2 + 1] = (v shr 8).toByte()
                    }
                }
                val ts = samples * 10_000_000 / rate
                val dur = frames.toLong() * 10_000_000 / rate
                MfNative.recAudio(h, out, frames * 4, ts, dur)
                samples += frames
                val l = sqrt(accL / frames).toFloat(); val r = sqrt(accR / frames).toFloat()
                onLevel((l + r) / 2f, l, r)
            }
        } catch (_: Throwable) {
        } finally {
            try { rec.stop() } catch (_: Throwable) {}
            rec.release()
        }
    }

    /** Finishes the file. Returns it, or null when nothing usable was written. */
    fun stop(): File? {
        running = false
        audio?.join(2000)
        writer?.join(30_000)
        val ok = MfNative.recClose(h)
        return if (ok && frames > 0 && file.length() > 0) file else { if (frames == 0) file.delete(); null }
    }

    val seconds: Long get() = when (timing) { 0 -> (System.nanoTime() - t0 - pausedNs) / 1_000_000_000L; else -> (System.nanoTime() - t0) / 1_000_000_000L }
}
