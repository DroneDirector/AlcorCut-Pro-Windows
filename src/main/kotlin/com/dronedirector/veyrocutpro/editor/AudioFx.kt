package com.dronedirector.veyrocutpro.editor

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

enum class AudioChannels(val label: String) { STEREO("Stereo"), MONO("Mono"), LEFT("Left only"), RIGHT("Right only"), SWAP("Swap L/R") }

/** Per-clip (and music) audio processing: EQ, dynamics, channels, fades and ducking. */
data class AudioFx(
    val low: Float = 0f,          // dB, shelf at 120 Hz
    val mid: Float = 0f,          // dB, peak at 1 kHz
    val high: Float = 0f,         // dB, shelf at 8 kHz
    val lowCut: Boolean = false,  // high-pass 80 Hz (wind, rumble, handling noise)
    val voice: Boolean = false,   // speech clarity: 100 Hz high-pass + presence lift
    val compressor: Float = 0f,   // 0..1
    val gate: Float = 0f,         // 0..1 noise gate
    val pan: Float = 0f,          // -1 left .. +1 right
    val channels: AudioChannels = AudioChannels.STEREO,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L,
    val duckMusic: Boolean = false,
    val limiter: Boolean = true,
    /** Voice noise reduction on export: 0 off, 1 light, 2 strong, 3 voice isolation (AI), 4 studio voice (AI). */
    val denoise: Int = 0,
    /** Repair on export: remove clicks / crackle, and take room echo down (0..1). */
    val declick: Boolean = false,
    val dereverb: Float = 0f,
    /** Dialogue leveller (0..1): rides the level so quiet and loud speech come out even. */
    val leveler: Float = 0f,
    /** Parametric EQ (on top of the three quick bands); null = off. */
    val bands: List<EqBand>? = null,
    /** De-esser (0..1): tames sharp "s" and "sh" sounds. */
    val deess: Float = 0f,
    /** Pitch in semitones (−12..12), without changing the speed. */
    val pitch: Float = 0f,
    /** Voice effect: 0 none, 1 robot, 2 radio, 3 phone, 4 megaphone. */
    val voiceFx: Int = 0,
) {
    val isNeutral get() = this == AudioFx() || this == AudioFx(limiter = false)
}

/** One parametric EQ band. type: 0 bell, 1 low shelf, 2 high shelf, 3 low cut, 4 high cut. */
data class EqBand(val type: Int = 0, val freq: Float = 1000f, val gain: Float = 0f, val q: Float = 1f, val on: Boolean = true) {
    companion object { val TYPES = listOf("Bell", "Low shelf", "High shelf", "Low cut", "High cut") }
}

/** Response of a band set at [freq] (dB), for drawing the EQ curve. Own RBJ cookbook maths. */
object EqMath {
    private fun coeffs(b: EqBand, rate: Double): DoubleArray? {
        val w = 2 * PI * b.freq.toDouble().coerceIn(10.0, rate * 0.45) / rate; val c = cos(w); val sn = sin(w)
        val q = b.q.toDouble().coerceIn(0.1, 18.0); val a = 10.0.pow(b.gain / 40.0)
        return when (b.type) {
            0 -> { if (abs(b.gain) < 0.05) return null; val al = sn / (2 * q); doubleArrayOf(1 + al * a, -2 * c, 1 - al * a, 1 + al / a, -2 * c, 1 - al / a) }
            1, 2 -> {
                if (abs(b.gain) < 0.05) return null
                val al = sn / 2 * sqrt((a + 1 / a) * (1 / q.coerceIn(0.5, 1.0) - 1) + 2); val sa = 2 * sqrt(a) * al
                if (b.type == 1) doubleArrayOf(a * ((a + 1) - (a - 1) * c + sa), 2 * a * ((a - 1) - (a + 1) * c), a * ((a + 1) - (a - 1) * c - sa), (a + 1) + (a - 1) * c + sa, -2 * ((a - 1) + (a + 1) * c), (a + 1) + (a - 1) * c - sa)
                else doubleArrayOf(a * ((a + 1) + (a - 1) * c + sa), -2 * a * ((a - 1) + (a + 1) * c), a * ((a + 1) + (a - 1) * c - sa), (a + 1) - (a - 1) * c + sa, 2 * ((a - 1) - (a + 1) * c), (a + 1) - (a - 1) * c - sa)
            }
            3 -> { val al = sn / (2 * q); doubleArrayOf((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + al, -2 * c, 1 - al) }
            else -> { val al = sn / (2 * q); doubleArrayOf((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + al, -2 * c, 1 - al) }
        }
    }
    internal fun normalized(b: EqBand, rate: Double): DoubleArray? = coeffs(b, rate)?.let { k -> doubleArrayOf(k[0] / k[3], k[1] / k[3], k[2] / k[3], k[4] / k[3], k[5] / k[3]) }
    fun responseDb(bands: List<EqBand>, freq: Float, rate: Double = 48000.0): Float {
        var db = 0.0
        val w = 2 * PI * freq / rate
        for (b in bands) {
            if (!b.on) continue
            val k = normalized(b, rate) ?: continue
            // |H(e^jw)|
            val cr = cos(w); val ci = -sin(w); val c2r = cos(2 * w); val c2i = -sin(2 * w)
            val nr = k[0] + k[1] * cr + k[2] * c2r; val ni = k[1] * ci + k[2] * c2i
            val dr = 1 + k[3] * cr + k[4] * c2r; val di = k[3] * ci + k[4] * c2i
            db += 10 * kotlin.math.log10(((nr * nr + ni * ni) / (dr * dr + di * di)).coerceAtLeast(1e-12))
        }
        return db.toFloat()
    }
}

/** The last moments of the preview's sound (mono), for the EQ's live spectrum. */
object AudioSpectrum {
    const val N = 2048
    val buf = FloatArray(N)
    @Volatile var pos = 0
    @Volatile var rate = 48000
    fun push(v: Float) { buf[pos] = v; pos = (pos + 1) % N }
    /** Magnitudes (dB) of the last N samples in [bins] log-spaced bands from 20 Hz to 20 kHz. Own FFT. */
    fun bands(bins: Int): FloatArray {
        val re = DoubleArray(N); val im = DoubleArray(N)
        val p = pos
        for (i in 0 until N) { val w = 0.5 - 0.5 * cos(2 * PI * i / (N - 1)); re[i] = buf[(p + i) % N] * w }
        // In-place radix-2 FFT.
        var j = 0
        for (i in 1 until N) { var bit = N shr 1; while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }; j = j xor bit; if (i < j) { val t = re[i]; re[i] = re[j]; re[j] = t } }
        var len = 2
        while (len <= N) {
            val ang = -2 * PI / len
            for (i in 0 until N step len) for (k in 0 until len / 2) {
                val wr = cos(ang * k); val wi = sin(ang * k)
                val ur = re[i + k]; val ui = im[i + k]
                val vr = re[i + k + len / 2] * wr - im[i + k + len / 2] * wi; val vi = re[i + k + len / 2] * wi + im[i + k + len / 2] * wr
                re[i + k] = ur + vr; im[i + k] = ui + vi; re[i + k + len / 2] = ur - vr; im[i + k + len / 2] = ui - vi
            }
            len = len shl 1
        }
        val out = FloatArray(bins)
        for (b in 0 until bins) {
            val f0 = 20.0 * 1000.0.pow(b / bins.toDouble()); val f1 = 20.0 * 1000.0.pow((b + 1) / bins.toDouble())
            val i0 = (f0 / rate * N).toInt().coerceIn(1, N / 2 - 1); val i1 = (f1 / rate * N).toInt().coerceIn(i0, N / 2 - 1)
            var m = 0.0
            for (i in i0..i1) m = maxOf(m, re[i] * re[i] + im[i] * im[i])
            out[b] = (10 * kotlin.math.log10(m.coerceAtLeast(1e-12)) - 40).toFloat()
        }
        return out
    }
}

enum class AudioPreset(val label: String, val fx: AudioFx) {
    FLAT("Flat", AudioFx()),
    VOICE("Voice", AudioFx(low = -2f, mid = 1f, high = 2f, voice = true, compressor = 0.45f, gate = 0.2f)),
    INTERVIEW("Interview", AudioFx(low = -3f, mid = 1.5f, high = 1.5f, voice = true, compressor = 0.6f, gate = 0.35f, channels = AudioChannels.MONO)),
    WIND("Wind cut", AudioFx(low = -6f, lowCut = true, gate = 0.1f)),
    WARM("Warm", AudioFx(low = 3f, mid = 0.5f, high = -2f)),
    BRIGHT("Bright", AudioFx(low = -1f, high = 4f)),
    BASS("Bass boost", AudioFx(low = 7f, mid = -1f)),
    RADIO("Radio", AudioFx(low = -12f, mid = 6f, high = -10f, lowCut = true, compressor = 0.7f, channels = AudioChannels.MONO)),
    LOUD("Loud & punchy", AudioFx(low = 2f, high = 1.5f, compressor = 0.85f)),
}

/** Live peak levels (0..1) from the preview, for the meter, and loudness (LUFS, ITU-R BS.1770). */
object AudioMeter {
    @Volatile var left = 0f
    @Volatile var right = 0f
    /** Momentary (400 ms) and short-term (3 s) loudness, LUFS. */
    @Volatile var momentary = -70f
    @Volatile var shortTerm = -70f
    /** Integrated loudness of what has played since [resetIntegrated] (gated as in EBU R128). */
    @Volatile var integrated = -70f
    private val blocks = java.util.ArrayList<Double>()
    @Volatile private var resetAsked = false
    fun resetIntegrated() { resetAsked = true }

    /** Called with each 100 ms of K-weighted mean square (all channels summed). */
    @Synchronized internal fun push(ms100: Double, history: ArrayDeque<Double>) {
        if (resetAsked) { blocks.clear(); resetAsked = false }
        history.addLast(ms100); while (history.size > 30) history.removeFirst()
        fun l(z: Double) = (-0.691 + 10.0 * log10(z.coerceAtLeast(1e-12))).toFloat()
        if (history.size >= 4) {
            val m = history.takeLast(4).average()
            momentary = l(m)
            if (m > 1e-7) blocks += m
        }
        shortTerm = l(history.takeLast(30).average())
        if (blocks.isNotEmpty()) {
            val abs = blocks.filter { l(it) > -70f }
            if (abs.isNotEmpty()) {
                val rel = l(abs.average()) - 10f
                val g = abs.filter { l(it) > rel }
                if (g.isNotEmpty()) integrated = l(g.average())
            }
        }
    }
}

private class Biquad {
    var b0 = 1.0; var b1 = 0.0; var b2 = 0.0; var a1 = 0.0; var a2 = 0.0
    private var z1 = DoubleArray(8); private var z2 = DoubleArray(8)
    var on = false

    fun process(x: Double, ch: Int): Double {
        val y = b0 * x + z1[ch]
        z1[ch] = b1 * x - a1 * y + z2[ch]
        z2[ch] = b2 * x - a2 * y
        return y
    }

    fun reset() { z1 = DoubleArray(8); z2 = DoubleArray(8) }
    fun setNormalized(k: DoubleArray?) { if (k == null) { on = false; return }; b0 = k[0]; b1 = k[1]; b2 = k[2]; a1 = k[3]; a2 = k[4]; on = true }

    private fun set(nb0: Double, nb1: Double, nb2: Double, na0: Double, na1: Double, na2: Double) {
        b0 = nb0 / na0; b1 = nb1 / na0; b2 = nb2 / na0; a1 = na1 / na0; a2 = na2 / na0
    }

    // RBJ cookbook filters.
    fun highPass(f: Double, rate: Double, q: Double = 0.707) {
        val w = 2 * PI * f / rate; val al = sin(w) / (2 * q); val c = cos(w)
        set((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + al, -2 * c, 1 - al); on = true
    }

    fun peak(f: Double, rate: Double, db: Double, q: Double = 0.9) {
        if (abs(db) < 0.05) { on = false; return }
        val a = 10.0.pow(db / 40); val w = 2 * PI * f / rate; val al = sin(w) / (2 * q); val c = cos(w)
        set(1 + al * a, -2 * c, 1 - al * a, 1 + al / a, -2 * c, 1 - al / a); on = true
    }

    fun lowShelf(f: Double, rate: Double, db: Double) {
        if (abs(db) < 0.05) { on = false; return }
        val a = 10.0.pow(db / 40); val w = 2 * PI * f / rate; val c = cos(w); val al = sin(w) / 2 * sqrt(2.0); val sa = 2 * sqrt(a) * al
        set(a * ((a + 1) - (a - 1) * c + sa), 2 * a * ((a - 1) - (a + 1) * c), a * ((a + 1) - (a - 1) * c - sa),
            (a + 1) + (a - 1) * c + sa, -2 * ((a - 1) + (a + 1) * c), (a + 1) + (a - 1) * c - sa); on = true
    }

    fun highShelf(f: Double, rate: Double, db: Double) {
        if (abs(db) < 0.05) { on = false; return }
        val a = 10.0.pow(db / 40); val w = 2 * PI * f / rate; val c = cos(w); val al = sin(w) / 2 * sqrt(2.0); val sa = 2 * sqrt(a) * al
        set(a * ((a + 1) + (a - 1) * c + sa), -2 * a * ((a - 1) + (a + 1) * c), a * ((a + 1) + (a - 1) * c - sa),
            (a + 1) - (a - 1) * c + sa, 2 * ((a - 1) - (a + 1) * c), (a + 1) - (a - 1) * c - sa); on = true
    }
}

/**
 * Pitch shifter (own code): two read heads glide through a short delay line at the new speed and cross-fade,
 * so the pitch changes but the length doesn't. Good for voices; [ratio] 0.5..2.
 */
private class PitchShift(private val ch: Int, rate: Int) {
    private val size = (rate * 0.05).toInt().coerceAtLeast(256)       // 50 ms window
    private val buf = Array(ch) { DoubleArray(size * 2) }
    private var w = 0
    private var phase = 0.0
    fun process(frame: DoubleArray, ratio: Double) {
        val n = buf[0].size
        for (c in 0 until ch) buf[c][w] = frame[c]
        phase = (phase + (1.0 - ratio) / size).let { it - kotlin.math.floor(it) }
        val p2 = (phase + 0.5) % 1.0
        val g1 = sin(PI * phase).let { it * it }; val g2 = sin(PI * p2).let { it * it }
        val d1 = phase * size + 1; val d2 = p2 * size + 1
        for (c in 0 until ch) {
            fun tap(d: Double): Double {
                val pos = (w - d + n * 4) % n; val i = pos.toInt(); val f = pos - i
                return buf[c][i] * (1 - f) + buf[c][(i + 1) % n] * f
            }
            frame[c] = tap(d1) * g1 + tap(d2) * g2
        }
        w = (w + 1) % n
    }
}

/**
 * 16-bit PCM processor used both for the export (fixed settings, with fades and ducking)
 * and for the live preview (settings can change while playing; set [fx]).
 */
@UnstableApi
class AudioFxProcessor(
    initial: AudioFx = AudioFx(),
    private val gain: Float = 1f,
    private val totalMs: Long = 0L,
    /** Ranges (ms) where the signal is lowered by [duckGain] — used for music under dialogue. */
    private val duckRanges: List<LongRange> = emptyList(),
    private val duckGain: Float = 1f,
    private val meter: Boolean = false,
    private val live: Boolean = false,
    /** Volume keyframes over the clip (export). */
    private val envelope: List<VolKey>? = null,
    /** Audio transition into the next clip (export). */
    private val xOut: FadeOut? = null,
    /** Audio transition from the previous clip (export). */
    private val xIn: FadeIn? = null,
) : BaseAudioProcessor() {
    @Volatile var fx: AudioFx = initial
    /** Mixer (preview): track × master gain and track pan, set from the mixer page. */
    @Volatile var liveGain = 1f
    @Volatile var livePan = 0f
    /** This processor's own last peak level (0..1), for the mixer's track meters. */
    @Volatile var peak = 0f
    private var lev = 1.0; private var levRms = 0.0
    // Loudness meter (K-weighting, two stages per channel) and its 100 ms accumulator.
    private val kShelf = Biquad(); private val kHp = Biquad()
    private var kSum = 0.0; private var kN = 0
    private val kHist = ArrayDeque<Double>()
    private var built: AudioFx? = null
    private var builtRate = 0
    private val hp = Biquad(); private val ls = Biquad(); private val pk = Biquad(); private val hs = Biquad(); private val pres = Biquad()
    private val peq = Array(8) { Biquad() }
    // De-esser: high band split (4.5 kHz high-pass) and its level vs. the whole signal.
    private val dsHp = Biquad(); private var dsEnv = 0.0; private var dsAll = 0.0
    private var frames = 0L
    private var env = 0.0; private var gateGain = 1.0; private var duck = 1.0
    private var pitchShift: PitchShift? = null
    private val vHp = Biquad(); private val vLp = Biquad(); private val vPk = Biquad()
    private var ringPhase = 0.0
    /** Audio-transition effects: in the preview set from the editor (type and strength), in the export from [xOut] / [xIn]. */
    @Volatile var liveXfx: AudioXfx = AudioXfx.NONE
    @Volatile var liveXAmt = 0f
    private var xDsp: XfxDsp? = null

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) return AudioProcessor.AudioFormat.NOT_SET
        if (!live && fx.isNeutral && fx.denoise == 0 && !fx.declick && fx.dereverb <= 0f && gain == 1f && duckRanges.isEmpty() && !meter && envelope.isNullOrEmpty() && xOut == null && xIn == null) return AudioProcessor.AudioFormat.NOT_SET
        return inputAudioFormat
    }

    private fun rebuild(f: AudioFx, rate: Int) {
        val r = rate.toDouble()
        if (f.voice) hp.highPass(100.0, r) else if (f.lowCut) hp.highPass(80.0, r) else hp.on = false
        ls.lowShelf(120.0, r, f.low.toDouble())
        pk.peak(1000.0, r, f.mid.toDouble())
        hs.highShelf(8000.0, r, f.high.toDouble())
        pres.peak(3200.0, r, if (f.voice) 4.0 else 0.0, 1.2)
        kShelf.highShelf(1500.0, r, 4.0); kHp.highPass(38.0, r, 0.5)
        val bs = f.bands.orEmpty()
        for (i in peq.indices) { val b = bs.getOrNull(i); if (b == null || !b.on) peq[i].on = false else { peq[i].setNormalized(EqMath.normalized(b, r)) } }
        dsHp.highPass(4500.0, r, 0.707)
        // Voice effects: a band of the voice (radio, phone, megaphone).
        val band = when (f.voiceFx) { 2 -> Triple(300.0, 3400.0, 3.0); 3 -> Triple(450.0, 2800.0, 0.0); 4 -> Triple(600.0, 4500.0, 6.0); else -> null }
        if (band == null) { vHp.on = false; vLp.on = false; vPk.on = false } else {
            vHp.highPass(band.first, r, 0.8)
            vLp.setNormalized(EqMath.normalized(EqBand(type = 4, freq = band.second.toFloat(), q = 0.8f), r))
            vPk.peak(1800.0, r, band.third, 1.0)
        }
        built = f; builtRate = rate
    }

    private var dc: DeClicker? = null
    private var dr: DeReverb? = null
    private var nr: com.dronedirector.veyrocutpro.camera.NoiseReducer? = null
    private var vi: VoiceIsolator? = null
    private var nrDrop = 0
    /** Samples still inside the repair / noise-reduction processors (flushed at the end, so the tail isn't lost). */
    private var held = 0
    private var flushing = false

    override fun queueInput(inputBuffer0: ByteBuffer) {
        val fmt = inputAudioFormat
        val ch = fmt.channelCount.coerceIn(1, 8)
        val rate = fmt.sampleRate.coerceAtLeast(1)
        val f = fx
        // Voice noise reduction (export only): cleaned first, then EQ and dynamics as usual.
        var inputBuffer = inputBuffer0
        if (!live && (f.denoise > 0 || f.declick || f.dereverb > 0f)) {
            val sb = inputBuffer0.order(java.nio.ByteOrder.nativeOrder()).asShortBuffer()
            var inF = FloatArray(sb.remaining()) { sb.get() / 32768f }
            inputBuffer0.position(inputBuffer0.limit())
            // Repair first (clicks, then room echo), each with its own delay taken off the front.
            if (f.declick) { val d = dc ?: DeClicker(ch, rate).also { dc = it; nrDrop += it.delay * ch; held += it.delay * ch }; inF = d.process(inF) }
            if (f.dereverb > 0f) { val d = dr ?: DeReverb(ch, rate, f.dereverb).also { dr = it; nrDrop += it.delay * ch; held += it.delay * ch }; inF = d.process(inF) }
            // 3 = AI voice isolation (RNNoise), 4 = studio voice (DeepFilterNet3); falls back if the native part can't load.
            var outF = if (f.denoise == 0) inF else if (f.denoise >= 3 && VoiceIsolator.available) {
                val v = vi ?: VoiceIsolator(ch, rate, studio = f.denoise >= 4 && VoiceIsolator.studioAvailable).also { vi = it; nrDrop += it.delay * ch; held += it.delay * ch }
                v.process(inF)
            } else {
                val r = nr ?: com.dronedirector.veyrocutpro.camera.NoiseReducer(ch, f.denoise.coerceAtMost(2)).also { nr = it; nrDrop += it.delay * ch; held += it.delay * ch }
                r.process(inF)
            }
            if (nrDrop > 0) { val d = minOf(nrDrop, outF.size); outF = outF.copyOfRange(d, outF.size); nrDrop -= d }
            val bb = ByteBuffer.allocate(outF.size * 2).order(java.nio.ByteOrder.nativeOrder())
            for (v in outF) bb.putShort((v.coerceIn(-1f, 0.99997f) * 32768f).toInt().toShort())
            bb.flip()
            inputBuffer = bb
        }
        if (f != built || rate != builtRate) rebuild(f, rate)
        val out = replaceOutputBuffer(inputBuffer.remaining())
        val frame = DoubleArray(ch)
        // Dynamics constants
        val atk = exp(-1.0 / (0.005 * rate)); val rel = exp(-1.0 / (0.12 * rate))
        val compThr = 10.0.pow((-10.0 - f.compressor * 22.0) / 20.0)
        val ratio = 1.0 + f.compressor * 5.0
        val makeup = 10.0.pow(f.compressor * 7.0 / 20.0)
        val gateThr = 10.0.pow((-62.0 + f.gate * 30.0) / 20.0)
        val gateStep = 1.0 / (0.01 * rate)
        val duckStep = 1.0 / (0.25 * rate)
        val pan = (f.pan + livePan).coerceIn(-1f, 1f)
        val panL = if (pan > 0) 1.0 - pan else 1.0
        val panR = if (pan < 0) 1.0 + pan else 1.0
        val levOn = f.leveler > 0f
        val levTarget = 10.0.pow(-20.0 / 20.0)            // speech at about −20 dBFS RMS
        val levMax = 10.0.pow(12.0 * f.leveler / 20.0)    // up to +12 dB for quiet speech
        val levMin = 10.0.pow(-9.0 * f.leveler / 20.0)    // down to −9 dB for loud parts
        val rmsK = exp(-1.0 / (0.4 * rate))
        val levK = exp(-1.0 / (0.6 * rate))
        val block = (rate / 10).coerceAtLeast(1)
        var peakL = 0.0; var peakR = 0.0
        while (inputBuffer.remaining() >= 2 * ch) {
            for (c in 0 until ch) frame[c] = inputBuffer.short / 32768.0
            // Channels
            if (ch >= 2) {
                val l = frame[0]; val r = frame[1]
                when (f.channels) {
                    AudioChannels.STEREO -> {}
                    AudioChannels.MONO -> { val m = (l + r) * 0.5; frame[0] = m; frame[1] = m }
                    AudioChannels.LEFT -> { frame[0] = l; frame[1] = l }
                    AudioChannels.RIGHT -> { frame[0] = r; frame[1] = r }
                    AudioChannels.SWAP -> { frame[0] = r; frame[1] = l }
                }
                frame[0] *= panL; frame[1] *= panR
            } else if (livePan != 0f && ch == 1) { /* mono: pan has no effect */ }
            // Pitch (same length) and voice effects.
            if (f.pitch != 0f) {
                val ps = pitchShift ?: PitchShift(ch, rate).also { pitchShift = it }
                ps.process(frame, 2.0.pow(f.pitch / 12.0))
            }
            if (f.voiceFx == 1) {
                // Robot: ring modulation at 55 Hz with a little of the voice kept.
                ringPhase += 2 * PI * 55.0 / rate; if (ringPhase > 2 * PI) ringPhase -= 2 * PI
                val m = sin(ringPhase)
                for (c in 0 until ch) frame[c] = frame[c] * (0.25 + 0.75 * m) * 1.3
            } else if (f.voiceFx >= 2) {
                val drive = when (f.voiceFx) { 2 -> 2.2; 3 -> 1.6; else -> 3.2 }
                for (c in 0 until ch) {
                    var x = frame[c]
                    if (vHp.on) x = vHp.process(x, c)
                    if (vLp.on) x = vLp.process(x, c)
                    if (vPk.on) x = vPk.process(x, c)
                    frame[c] = tanh(x * drive) / tanh(drive) * 0.9
                }
            }
            // EQ
            for (c in 0 until ch) {
                var x = frame[c]
                if (hp.on) x = hp.process(x, c)
                if (ls.on) x = ls.process(x, c)
                if (pk.on) x = pk.process(x, c)
                if (hs.on) x = hs.process(x, c)
                if (pres.on) x = pres.process(x, c)
                for (b in peq) if (b.on) x = b.process(x, c)
                frame[c] = x
            }
            // De-esser: when the sharp high band gets loud next to the whole sound, turn just that band down.
            if (f.deess > 0f) {
                var hiPk = 0.0; var allPk = 0.0
                val hi = DoubleArray(ch)
                for (c in 0 until ch) { hi[c] = dsHp.process(frame[c], c); hiPk = maxOf(hiPk, abs(hi[c])); allPk = maxOf(allPk, abs(frame[c])) }
                dsEnv = if (hiPk > dsEnv) atk * dsEnv + (1 - atk) * hiPk else rel * dsEnv + (1 - rel) * hiPk
                dsAll = if (allPk > dsAll) atk * dsAll + (1 - atk) * allPk else rel * dsAll + (1 - rel) * allPk
                val k = 0.55 - 0.4 * f.deess
                if (dsEnv > 0.01 && dsEnv > k * dsAll) {
                    val gr = ((k * dsAll) / dsEnv).coerceIn(0.15, 1.0)
                    for (c in 0 until ch) frame[c] -= hi[c] * (1 - gr)
                }
            }
            // Audio transition effect (muffle, echo, tape stop…) on this side of the cut.
            run {
                val tx = frames * 1000L / rate
                var type = AudioXfx.NONE; var amt = 0.0
                if (live) { type = liveXfx; amt = liveXAmt.toDouble() }
                else {
                    val o = xOut; val i = xIn
                    if (o != null && o.fx != AudioXfx.NONE && totalMs > 0) { val pr = o.progress(totalMs - tx); if (pr > 0f) { type = o.fx; amt = pr.toDouble() } }
                    if (type == AudioXfx.NONE && i != null && i.fx != AudioXfx.NONE) { val l = i.left(tx); if (l > 0f) { type = i.fx; amt = l.toDouble() } }
                }
                if (type != AudioXfx.NONE) (xDsp ?: XfxDsp(ch, rate).also { xDsp = it }).process(frame, type, amt)
            }
            if (meter) { var m = 0.0; for (c in 0 until ch) m += frame[c]; AudioSpectrum.push((m / ch).toFloat()); AudioSpectrum.rate = rate }
            // Envelope (peak of all channels)
            var lvl = 0.0
            for (c in 0 until ch) lvl = maxOf(lvl, abs(frame[c]))
            env = if (lvl > env) atk * env + (1 - atk) * lvl else rel * env + (1 - rel) * lvl
            var g = gain.toDouble() * liveGain
            // Dialogue leveller: slow RMS of the speech, gain towards the target (silence is left alone).
            if (levOn) {
                var sq = 0.0; for (c in 0 until ch) sq += frame[c] * frame[c]
                levRms = rmsK * levRms + (1 - rmsK) * (sq / ch)
                val rms = sqrt(levRms)
                val want = if (rms < 0.003) 1.0 else (levTarget / rms).coerceIn(levMin, levMax)
                lev = levK * lev + (1 - levK) * want
                g *= lev
            }
            if (f.gate > 0f) {
                gateGain = if (env < gateThr) (gateGain - gateStep).coerceAtLeast(0.0) else (gateGain + gateStep * 4).coerceAtMost(1.0)
                g *= gateGain
            }
            if (f.compressor > 0f && env > compThr) g *= (compThr * (env / compThr).pow(1.0 / ratio)) / env
            if (f.compressor > 0f) g *= makeup
            val tMs = frames * 1000L / rate
            if (!live) {
                if (f.fadeInMs > 0 && tMs < f.fadeInMs) g *= tMs.toDouble() / f.fadeInMs
                if (f.fadeOutMs > 0 && totalMs > 0 && tMs > totalMs - f.fadeOutMs) g *= ((totalMs - tMs).toDouble() / f.fadeOutMs).coerceIn(0.0, 1.0)
                if (xIn != null) g *= xIn.gain(tMs)
                if (xOut != null && totalMs > 0) g *= xOut.gain(totalMs - tMs)
            }
            if (!envelope.isNullOrEmpty()) g *= volAt(envelope, tMs)
            if (duckRanges.isNotEmpty()) {
                val target = if (duckRanges.any { tMs in it }) duckGain.toDouble() else 1.0
                duck = if (duck > target) (duck - duckStep).coerceAtLeast(target) else (duck + duckStep).coerceAtMost(target)
                g *= duck
            }
            for (c in 0 until ch) {
                var y = frame[c] * g
                if (f.limiter && abs(y) > 0.9) y = (if (y > 0) 1 else -1) * (0.9 + 0.1 * tanh((abs(y) - 0.9) / 0.1))
                if (c == 0) peakL = maxOf(peakL, abs(y)) else if (c == 1) peakR = maxOf(peakR, abs(y))
                if (meter && c < 2) { val k = kHp.process(kShelf.process(y, c), c); kSum += k * k }
                out.putShort((y * 32767.0).toInt().coerceIn(-32768, 32767).toShort())
            }
            if (meter) { kN++; if (kN >= block) { AudioMeter.push(kSum / kN, kHist); kSum = 0.0; kN = 0 } }
            frames++
        }
        while (inputBuffer.hasRemaining()) out.put(inputBuffer.get())
        out.flip()
        peak = maxOf(peakL, peakR).toFloat().coerceAtMost(1f).let { maxOf(it, peak * 0.85f) }
        if (meter) {
            AudioMeter.left = maxOf(peakL.toFloat(), AudioMeter.left * 0.85f)
            AudioMeter.right = maxOf((if (ch >= 2) peakR else peakL).toFloat(), AudioMeter.right * 0.85f)
        }
    }

    private fun clear() { pitchShift = null; ringPhase = 0.0; xDsp = null; vHp.reset(); vLp.reset(); vPk.reset(); lev = 1.0; levRms = 0.0; kSum = 0.0; kN = 0; kHist.clear(); kShelf.reset(); kHp.reset(); frames = 0; env = 0.0; gateGain = 1.0; duck = 1.0; nr = null; vi?.release(); vi = null; dc = null; dr = null; nrDrop = 0; held = 0; flushing = false; listOf(hp, ls, pk, hs, pres, dsHp).forEach { it.reset() }; peq.forEach { it.reset() }; dsEnv = 0.0; dsAll = 0.0 }
    override fun onQueueEndOfStream() {
        // Push silence through so the last moment of sound comes out of the processors' delay.
        if (held > 0 && !flushing) {
            val ch = inputAudioFormat.channelCount.coerceIn(1, 8)
            val n = held - held % ch
            held = 0; flushing = true
            if (n > 0) queueInput(ByteBuffer.allocateDirect(n * 2).order(java.nio.ByteOrder.nativeOrder()))
        }
    }

    override fun onFlush() = clear()
    override fun onReset() { clear(); built = null }
}

fun dbfs(v: Float): Float = if (v <= 0.00001f) -60f else (20 * log10(v)).coerceAtLeast(-60f)
