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
    /** Also save the sound uncompressed as a WAV next to the clip (24-bit when the microphone gives it). */
    private val wav: Boolean = false,
    /** Cut the low rumble of wind and handling noise (high-pass at 80 Hz). */
    private val windFilter: Boolean = false,
    /** Voice noise reduction: 0 off, 1 light, 2 strong. */
    private val noiseReduce: Int = 0,
    /** NTSC rates: 24 / 30 / 60 fps become 23.976 / 29.97 / 59.94 (picture and sound 0.1 % slower). */
    private val ntsc: Boolean = false,
    /** Pre-record: the seconds before the press. */
    private val pre: PreRoll.Take? = null,
) {
    @Volatile var wavFile: File? = null; private set
    private val ntscOn = ntsc && timing == 0 && Math.round(fps) in setOf(24, 30, 60)
    private val fpsNum = if (timing == 0) (if (ntscOn) Math.round(fps) * 1_000_000 / 1001 else Math.round(fps * 1000)) else Math.round(outFps * 1000)
    private val h: Long
    private val rate = 48_000
    private val queue = ArrayBlockingQueue<Pair<IntArray, Long>>(if (pre != null) 30 else 6)
    private val pool = ArrayBlockingQueue<IntArray>(8)
    @Volatile private var running = true
    @Volatile var paused = false
        set(v) { if (v && !field) pauseAt = System.nanoTime() else if (!v && field) pausedNs += System.nanoTime() - pauseAt; field = v }
    private var pauseAt = 0L
    @Volatile private var pausedNs = 0L
    private val t0 = pre?.startNs ?: System.nanoTime()
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
            // The seconds before the press first.
            pre?.frames?.forEach { (jpg, t) ->
                val img = try { javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(jpg)) } catch (_: Throwable) { null } ?: return@forEach
                if (img.width != width || img.height != height) return@forEach
                val px = img.getRGB(0, 0, width, height, null, 0, width)
                val ts = maxOf((t - t0) / 100, lastTs + 1).let { if (ntscOn) it * 1001 / 1000 else it }
                if (!MfNative.recVideo(h, px, ts, frameDur)) error = MfNative.error()
                lastTs = ts; frames++
            }
            while (running || queue.isNotEmpty()) {
                val (px, ts1) = queue.poll(100, TimeUnit.MILLISECONDS) ?: continue
                val ts0 = if (ntscOn) ts1 * 1001 / 1000 else ts1
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
        // 24-bit straight from the microphone when the WAV is wanted and Windows offers it; else 16-bit.
        var line24: javax.sound.sampled.TargetDataLine? = null
        if (wav) line24 = try {
            val f = javax.sound.sampled.AudioFormat(rate.toFloat(), 24, 2, true, false)
            val info = javax.sound.sampled.DataLine.Info(javax.sound.sampled.TargetDataLine::class.java, f)
            val m = android.media.AudioRecord.preferredInput?.let { n -> javax.sound.sampled.AudioSystem.getMixerInfo().firstOrNull { it.name == n } }
            ((if (m != null) javax.sound.sampled.AudioSystem.getMixer(m).getLine(info) else javax.sound.sampled.AudioSystem.getLine(info)) as javax.sound.sampled.TargetDataLine).also { it.open(f, rate / 5 * 6) }
        } catch (_: Throwable) { null }
        val rec = if (line24 == null) android.media.AudioRecord(android.media.MediaRecorder.AudioSource.CAMCORDER, rate, android.media.AudioFormat.CHANNEL_IN_STEREO, android.media.AudioFormat.ENCODING_PCM_16BIT, 1 shl 15) else null
        if (line24 == null && rec?.state != android.media.AudioRecord.STATE_INITIALIZED) { error = "No microphone found — recording without sound"; return }
        val ch = if (line24 != null) 2 else rec!!.channelCount
        val bits = if (line24 != null) 24 else 16
        val frames20 = rate / 50
        val raw16 = ShortArray(frames20 * ch)
        val raw24 = ByteArray(frames20 * 6)
        val x = FloatArray(frames20 * 2)
        val out = ByteArray(frames20 * 4)
        var wavOut: java.io.RandomAccessFile? = null
        var wavBytes = 0L
        if (wav) try {
            val wf = File(file.path.substringBeforeLast('.') + ".wav"); wavFile = wf
            wavOut = java.io.RandomAccessFile(wf, "rw").also { it.setLength(0); it.write(ByteArray(44)) }
        } catch (_: Throwable) { wavOut = null }
        // High-pass filter state (per channel).
        val hpA = Math.exp(-2.0 * Math.PI * 80.0 / rate).toFloat()
        val hpX = FloatArray(2); val hpY = FloatArray(2)
        val nr = if (noiseReduce > 0) arrayOf(NoiseReducer(noiseReduce), NoiseReducer(noiseReduce)) else null
        val stretch = if (ntscOn) NtscStretch() else null
        var samples = 0L
        var started = false
        /** Gain, limiter, filters → AAC (and WAV); [x] holds [n] stereo frames. */
        fun write(x: FloatArray, n0: Int) {
            var n = n0
            var buf = x
            if (windFilter || nr != null) for (c in 0..1) {
                if (windFilter) for (i in 0 until n) { val v = buf[i * 2 + c]; val y = hpA * (hpY[c] + v - hpX[c]); hpX[c] = v; hpY[c] = y; buf[i * 2 + c] = y }
                nr?.get(c)?.process(buf, c, n, 2)
            }
            if (stretch != null) { buf = stretch.run(buf, n); n = buf.size / 2 }
            val g = 10.0.pow(gainDb() / 20.0).toFloat(); val lim = limiter()
            var accL = 0.0; var accR = 0.0
            val outB = if (out.size >= n * 4) out else ByteArray(n * 4)
            for (i in 0 until n) for (c in 0..1) {
                var v = buf[i * 2 + c] * g
                if (lim) { val a = abs(v); if (a > 0.8f) v = (0.8f + 0.2f * tanh((a - 0.8f) / 0.2f)) * if (v < 0) -1f else 1f }
                v = v.coerceIn(-1f, 0.99997f)
                buf[i * 2 + c] = v
                if (c == 0) accL += (v * v).toDouble() else accR += (v * v).toDouble()
                val s16 = (v * 32767f).toInt()
                outB[i * 4 + c * 2] = s16.toByte(); outB[i * 4 + c * 2 + 1] = (s16 shr 8).toByte()
            }
            wavOut?.let { w ->
                val bpsm = bits / 8
                val wb = ByteArray(n * 2 * bpsm)
                for (i in 0 until n * 2) { val sv = if (bits == 24) (buf[i] * 8388607f).toInt() else (buf[i] * 32767f).toInt(); for (k in 0 until bpsm) wb[i * bpsm + k] = (sv shr (8 * k)).toByte() }
                w.write(wb); wavBytes += wb.size
            }
            if (n > 0) {
                MfNative.recAudio(h, outB, n * 4, samples * 10_000_000 / rate, n.toLong() * 10_000_000 / rate)
                samples += n
                onLevel(sqrt((accL + accR) / (2 * n)).toFloat(), sqrt(accL / n).toFloat(), sqrt(accR / n).toFloat())
            }
        }
        try {
            line24?.start(); rec?.startRecording()
            while (running) {
                val n: Int
                if (line24 != null) {
                    val got = line24.read(raw24, 0, raw24.size); if (got <= 0) continue
                    n = got / 6
                    for (i in 0 until n * 2) { val o = i * 3; x[i] = (((raw24[o].toInt() and 0xFF) or ((raw24[o + 1].toInt() and 0xFF) shl 8) or (raw24[o + 2].toInt() shl 16))) / 8388608f }
                } else {
                    val got = rec!!.read(raw16, 0, raw16.size); if (got <= 0) continue
                    n = got / ch
                    for (i in 0 until n) for (c in 0..1) x[i * 2 + c] = raw16[i * ch + (if (ch == 2) c else 0)] / 32768f
                }
                if (paused) continue
                if (!started) {
                    started = true
                    val p = pre
                    if (p != null && p.pcm.size >= 2) {
                        samples = ((p.pcmStartNs - t0).coerceAtLeast(0) * rate / 1_000_000_000L).let { if (ntscOn) it * 1001 / 1000 else it }
                        var i = 0
                        while (i < p.pcm.size / 2) { val m = minOf(960, p.pcm.size / 2 - i); write(p.pcm.copyOfRange(i * 2, (i + m) * 2), m); i += m }
                    } else samples = now100ns() * rate / 10_000_000
                }
                write(x.copyOf(n * 2), n)
            }
        } catch (_: Throwable) {
        } finally {
            try { line24?.stop(); line24?.close() } catch (_: Throwable) {}
            try { rec?.stop() } catch (_: Throwable) {}
            rec?.release()
            wavOut?.let { w ->
                try {
                    val hdr = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    hdr.put("RIFF".toByteArray()).putInt((36 + wavBytes).toInt()).put("WAVE".toByteArray())
                    hdr.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(2).putInt(rate).putInt(rate * 2 * bits / 8).putShort((2 * bits / 8).toShort()).putShort(bits.toShort())
                    hdr.put("data".toByteArray()).putInt(wavBytes.toInt())
                    w.seek(0); w.write(hdr.array()); w.close()
                } catch (_: Throwable) {}
            }
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
