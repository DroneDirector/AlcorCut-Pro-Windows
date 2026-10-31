package com.dronedirector.veyrocutpro.editor

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Sound effects that can be put on a cut (Transitions → Audio transitions → SOUND EFFECTS).
 * [hitMs] is the moment in the sound that lands on the cut: 0 for impacts (they start on the cut),
 * the full length for risers (they build up to the cut).
 */
enum class SfxGroup(val label: String) { HITS("Hits & impacts"), WHOOSH("Whooshes & air"), BUILD("Builds & risers"), OTHER("Stylized & sci-fi") }

enum class TransitionSfx(val label: String, val detail: String, val lengthMs: Int, val hitMs: Int, val group: SfxGroup) {
    IMPACT("Cinematic impact", "Deep thump with a sharp crack and a rumbling tail", 2500, 0, SfxGroup.HITS),
    BOOM("Cinematic boom", "Huge low boom that rolls out", 3500, 0, SfxGroup.HITS),
    TRAILER_HIT("Trailer hit", "Boom with a metallic ring, like a movie trailer", 3000, 0, SfxGroup.HITS),
    BRAAM("Braam", "Huge brassy horn blast, blockbuster-trailer style", 3000, 0, SfxGroup.HITS),
    DRUM_HIT("Drum hit", "Big cinematic drum with room sound", 1800, 0, SfxGroup.HITS),
    PUNCH("Punch", "Quick swish that ends in a hard hit", 650, 400, SfxGroup.HITS),
    THUNDER("Thunder", "Crack of thunder rolling away", 4000, 0, SfxGroup.HITS),
    HEARTBEAT("Heartbeat", "Two low thumps — lub-dub", 1200, 0, SfxGroup.HITS),
    SUB_DROP("Sub drop", "Low tone that falls away under the cut", 1800, 0, SfxGroup.HITS),

    WHOOSH("Whoosh", "Air rushing past, left to right", 1000, 500, SfxGroup.WHOOSH),
    SWOOSH("Fast swoosh", "Short, quick swish", 450, 220, SfxGroup.WHOOSH),
    DEEP_WHOOSH("Deep whoosh", "Heavy, low pass-by", 1400, 700, SfxGroup.WHOOSH),
    SWIRL("Swirl", "Spinning, circling air that lands on the cut", 1500, 1000, SfxGroup.WHOOSH),
    WIND_GUST("Wind gust", "A gust of wind that swells and fades", 2200, 1100, SfxGroup.WHOOSH),

    RISER("Riser", "Tension that builds up into the cut", 2500, 2500, SfxGroup.BUILD),
    REVERSE("Reverse cymbal", "Swelling shimmer that sucks into the cut", 2000, 2000, SfxGroup.BUILD),
    TICK_RISER("Clock tick build", "Ticking that speeds up into the cut", 3000, 3000, SfxGroup.BUILD),
    TAPE_STOP("Tape stop", "Music slowing to a halt into the cut", 1500, 1500, SfxGroup.BUILD),

    GLITCH("Glitch", "Digital stutters and crackles", 700, 80, SfxGroup.OTHER),
    SCRATCH("Record scratch", "Quick DJ scratch back and forth", 600, 300, SfxGroup.OTHER),
    LASER("Laser zap", "Sci-fi zap", 500, 0, SfxGroup.OTHER),
    SHIMMER("Magic shimmer", "Sparkling bell tones", 2000, 150, SfxGroup.OTHER),
    SHUTTER("Camera shutter", "Mechanical shutter click", 400, 0, SfxGroup.OTHER),
    STATIC("TV static", "Burst of analogue noise", 900, 450, SfxGroup.OTHER),
}

/** Makes the sound effects on the phone (no downloaded audio, no licences) and caches them as WAV files. */
object SfxSynth {
    private const val SR = 44100
    private const val VERSION = 1

    /** The WAV file for [s]; made the first time it's needed. Call off the main thread. */
    @Synchronized
    fun file(context: Context, s: TransitionSfx): File {
        val f = File(File(context.cacheDir, "sfx").apply { mkdirs() }, "${s.name.lowercase()}_v$VERSION.wav")
        if (!f.exists() || f.length() < 100) {
            val (l, r) = render(s)
            val tmp = File(f.path + ".tmp")
            writeWav(tmp, l, r)
            tmp.renameTo(f)
        }
        return f
    }

    /** State-variable filter: low / band / high outputs with a cutoff that can change every sample. */
    private class Svf {
        var low = 0f; var band = 0f; var high = 0f
        fun step(x: Float, fc: Float, q: Float = 0.8f) {
            val f = (2.0 * sin(PI * fc.coerceIn(20f, 5200f) / SR)).toFloat()
            high = x - low - band / q
            band += f * high
            low += f * band
        }
    }

    private class Noise(seed: Long) {
        private val r = java.util.Random(seed)
        fun next() = r.nextFloat() * 2f - 1f
    }

    private fun render(s: TransitionSfx): Pair<FloatArray, FloatArray> {
        val n = s.lengthMs * SR / 1000
        val l = FloatArray(n); val r = FloatArray(n)
        val nl = Noise(11L + s.ordinal); val nr = Noise(97L + s.ordinal)
        val fl = Svf(); val fr = Svf(); val gl = Svf(); val gr = Svf()
        val len = s.lengthMs / 1000f
        var ph = 0.0; var ph2 = 0.0
        when (s) {
            TransitionSfx.IMPACT, TransitionSfx.TRAILER_HIT -> {
                val partials = floatArrayOf(87f, 143.5f, 231f, 377f, 611f)
                val pp = DoubleArray(partials.size)
                for (i in 0 until n) {
                    val t = i.toFloat() / SR
                    val f = 35f + 45f * exp(-t * 12f)
                    ph += 2 * PI * f / SR
                    val att = (t / 0.004f).coerceAtMost(1f)
                    val thump = sin(ph).toFloat() * exp(-t * 3.2f) * att
                    val a = nl.next(); val b = nr.next()
                    fl.step(a, 2500f); fr.step(b, 2500f)
                    gl.step(a, 380f); gr.step(b, 380f)
                    val crack = exp(-t * 35f) * 0.7f
                    val tail = exp(-t * 2.2f) * 0.9f
                    var metal = 0f
                    if (s == TransitionSfx.TRAILER_HIT) for (k in partials.indices) {
                        pp[k] += 2 * PI * partials[k] / SR
                        metal += sin(pp[k]).toFloat() * exp(-t * (1.3f + k * 0.5f)) * 0.22f * att
                    }
                    val body = tanh(1.6f * thump)
                    l[i] = body + fl.high * crack + gl.low * tail + metal
                    r[i] = body + fr.high * crack + gr.low * tail + metal * 0.9f
                }
            }
            TransitionSfx.BOOM -> for (i in 0 until n) {
                val t = i.toFloat() / SR
                ph += 2 * PI * (28f + 30f * exp(-t * 4f)) / SR
                val att = (t / 0.006f).coerceAtMost(1f)
                val sub = tanh(1.8f * sin(ph).toFloat()) * exp(-t * 1.4f) * att
                val a = nl.next(); val b = nr.next()
                fl.step(a, 150f); fr.step(b, 150f)
                gl.step(a, 1200f); gr.step(b, 1200f)
                val rumble = exp(-t * 1.0f) * 1.6f
                val hit = exp(-t * 20f) * 0.6f
                l[i] = sub + fl.low * rumble + gl.low * hit
                r[i] = sub + fr.low * rumble + gr.low * hit
            }
            TransitionSfx.SUB_DROP -> for (i in 0 until n) {
                val t = i.toFloat() / SR
                ph += 2 * PI * (30f + 90f * exp(-t * 2.5f)) / SR
                val att = (t / 0.005f).coerceAtMost(1f)
                val v = tanh(1.5f * sin(ph).toFloat()) * exp(-t * 1.2f) * att
                l[i] = v; r[i] = v
            }
            TransitionSfx.WHOOSH, TransitionSfx.SWOOSH -> {
                val (f0, f1) = if (s == TransitionSfx.WHOOSH) 250f to 3200f else 600f to 4800f
                for (i in 0 until n) {
                    val t = i.toFloat() / SR
                    val p = t / len
                    // Bell shape that peaks a little before the middle, then a quicker tail.
                    val env = sin(PI.toFloat() * p.toDouble().pow(0.85).toFloat()).let { it * it }
                    val fc = f0 + (f1 - f0) * p
                    fl.step(nl.next(), fc, 1.4f); fr.step(nr.next(), fc * 1.03f, 1.4f)
                    val pan = p * (PI.toFloat() / 2)
                    l[i] = fl.band * env * cos(pan) * 2.2f
                    r[i] = fr.band * env * sin(pan) * 2.2f
                }
            }
            TransitionSfx.SWIRL -> {
                val hit = s.hitMs / 1000f
                for (i in 0 until n) {
                    val t = i.toFloat() / SR
                    val p = (t / hit).coerceAtMost(1f)
                    var env = p.toDouble().pow(1.5).toFloat()
                    if (t > hit) env *= exp(-(t - hit) * 8f)
                    val fc = 400f + 1800f * p + 600f * sin(2 * PI * 7 * t).toFloat()
                    fl.step(nl.next(), fc, 2.2f); fr.step(nr.next(), fc, 2.2f)
                    val pan = (0.5f + 0.5f * sin(2 * PI * 3 * t).toFloat()) * (PI.toFloat() / 2)
                    l[i] = fl.band * env * cos(pan) * 2.6f
                    r[i] = fr.band * env * sin(pan) * 2.6f
                }
            }
            TransitionSfx.RISER -> for (i in 0 until n) {
                val t = i.toFloat() / SR
                val p = t / len
                val f = 180f * 2f.pow(p * 2.5f) * (1f + 0.006f * sin(2 * PI * 5.5 * t).toFloat())
                ph += 2 * PI * f / SR
                ph2 += 2 * PI * f * 1.005 / SR
                var tone = 0f
                for (k in 1..4) tone += (sin(ph * k) + sin(ph2 * k)).toFloat() / (2f * k)
                fl.step(nl.next(), 800f + 4200f * p); fr.step(nr.next(), 800f + 4200f * p)
                val env = p * p
                val end = ((len - t) / 0.015f).coerceIn(0f, 1f)
                l[i] = (tone * 0.35f + fl.high * 0.5f) * env * end
                r[i] = (tone * 0.35f + fr.high * 0.5f) * env * end
            }
            TransitionSfx.REVERSE -> for (i in 0 until n) {
                val t = i.toFloat() / SR
                val p = t / len
                val env = exp((t - len) * 3.5f) * p
                val end = ((len - t) / 0.01f).coerceIn(0f, 1f)
                fl.step(nl.next(), 3000f); fr.step(nr.next(), 3000f)
                gl.step(nl.next(), 6500f, 3f); gr.step(nr.next(), 6500f, 3f)
                l[i] = (fl.high * 0.7f + gl.band * 0.6f) * env * end
                r[i] = (fr.high * 0.7f + gr.band * 0.6f) * env * end
            }
            TransitionSfx.BRAAM -> {
                val notes = floatArrayOf(55f, 82.4f, 110f, 164.8f)
                val phL = DoubleArray(notes.size * 8); val phR = DoubleArray(notes.size * 8)
                for (i in 0 until n) {
                    val t = i.toFloat() / SR
                    var a = 0f; var b = 0f
                    for (k in notes.indices) for (h in 1..8) {
                        val idx = k * 8 + h - 1
                        phL[idx] += 2 * PI * notes[k] * h * 0.998 / SR
                        phR[idx] += 2 * PI * notes[k] * h * 1.002 / SR
                        a += sin(phL[idx]).toFloat() / h; b += sin(phR[idx]).toFloat() / h
                    }
                    val env = (t / 0.06f).coerceAtMost(1f) * exp(-t * 0.9f)
                    val fc = 400f + 2100f * exp(-t * 1.5f)
                    fl.step(a * 0.25f, fc); fr.step(b * 0.25f, fc)
                    l[i] = tanh(2f * fl.low) * env; r[i] = tanh(2f * fr.low) * env
                }
            }
            TransitionSfx.DRUM_HIT -> for (i in 0 until n) {
                val t = i.toFloat() / SR
                ph += 2 * PI * (48f + 40f * exp(-t * 18f)) / SR
                val att = (t / 0.003f).coerceAtMost(1f)
                val body = tanh(1.8f * sin(ph).toFloat()) * exp(-t * 5f) * att
                val a = nl.next(); val b = nr.next()
                fl.step(a, 900f); fr.step(b, 900f); gl.step(a, 600f); gr.step(b, 600f)
                val skin = exp(-t * 18f) * 0.7f; val room = exp(-t * 3f) * 0.35f
                l[i] = body + fl.low * skin + gl.low * room
                r[i] = body + fr.low * skin + gr.low * room
            }
            TransitionSfx.PUNCH -> {
                val hit = s.hitMs / 1000f
                for (i in 0 until n) {
                    val t = i.toFloat() / SR
                    val a = nl.next(); val b = nr.next()
                    if (t < hit) {
                        val p = t / hit
                        val fc = 800f + 2700f * p
                        fl.step(a, fc, 1.4f); fr.step(b, fc, 1.4f)
                        l[i] = fl.band * p * p * 1.8f; r[i] = fr.band * p * p * 1.8f
                    } else {
                        val d = t - hit
                        ph += 2 * PI * (50f + 70f * exp(-d * 25f)) / SR
                        val thump = tanh(2f * sin(ph).toFloat()) * exp(-d * 14f) * (d / 0.002f).coerceAtMost(1f)
                        gl.step(a, 2000f); gr.step(b, 2000f)
                        val burst = exp(-d * 40f)
                        l[i] = thump + gl.low * burst; r[i] = thump + gr.low * burst
                    }
                }
            }
            TransitionSfx.THUNDER -> for (i in 0 until n) {
                val t = i.toFloat() / SR
                val a = nl.next(); val b = nr.next()
                fl.step(a, 1500f); fr.step(b, 1500f)
                val fc = 120f + 80f * sin(2 * PI * 0.7 * t).toFloat()
                gl.step(a, fc); gr.step(b, fc)
                val crack = exp(-t * 12f) * 0.9f
                val roll = (t / 0.15f).coerceAtMost(1f) * exp(-t * 0.8f) * (1f + 0.5f * sin(2 * PI * 1.3 * t).toFloat()) * 2.4f
                l[i] = fl.high * crack + gl.low * roll
                r[i] = fr.high * crack + gr.low * roll
            }
            TransitionSfx.HEARTBEAT -> {
                var phB = 0.0
                for (i in 0 until n) {
                    val t = i.toFloat() / SR
                    ph += 2 * PI * 55 / SR; phB += 2 * PI * 50 / SR
                    val d2 = t - 0.28f
                    val one = sin(ph).toFloat() * exp(-t * 14f) * (t / 0.004f).coerceAtMost(1f)
                    val two = if (d2 >= 0f) sin(phB).toFloat() * exp(-d2 * 14f) * (d2 / 0.004f).coerceAtMost(1f) * 0.8f else 0f
                    val v = tanh(1.5f * (one + two))
                    l[i] = v; r[i] = v
                }
            }
            TransitionSfx.DEEP_WHOOSH -> for (i in 0 until n) {
                val t = i.toFloat() / SR
                val p = t / len
                val env = sin(PI.toFloat() * p).let { it * it }
                val fc = 80f + 820f * p
                fl.step(nl.next(), fc, 1.0f); fr.step(nr.next(), fc * 1.05f, 1.0f)
                ph += 2 * PI * (40f + 30f * p) / SR
                val sub = sin(ph).toFloat() * env * 0.35f
                val pan = p * (PI.toFloat() / 2)
                l[i] = fl.band * env * cos(pan) * 2.8f + sub
                r[i] = fr.band * env * sin(pan) * 2.8f + sub
            }
            TransitionSfx.WIND_GUST -> for (i in 0 until n) {
                val t = i.toFloat() / SR
                val p = t / len
                val env = sin(PI.toFloat() * p).let { it * it } * (0.8f + 0.2f * sin(2 * PI * 2.3 * t).toFloat())
                val fc = 300f + 500f * env + 120f * sin(2 * PI * 0.9 * t).toFloat()
                fl.step(nl.next(), fc, 0.9f); fr.step(nr.next(), fc * 1.1f, 0.9f)
                l[i] = fl.band * env * 2.2f; r[i] = fr.band * env * 2.2f
            }
            TransitionSfx.TICK_RISER -> {
                val ticks = mutableListOf<Float>()
                var tt = 0f; var dt = 0.35f
                while (tt < len - 0.01f) { ticks += tt; tt += dt; dt = maxOf(0.06f, dt * 0.9f) }
                var next = 0
                var last = -1f; var lastIdx = 0
                for (i in 0 until n) {
                    val t = i.toFloat() / SR
                    while (next < ticks.size && ticks[next] <= t) { last = ticks[next]; lastIdx = next; next++ }
                    val a = nl.next()
                    fl.step(a, 3000f)
                    var v = 0f
                    if (last >= 0f) {
                        val d = t - last
                        v = fl.high * exp(-d * 300f) * 0.8f + sin(2 * PI * (if (lastIdx % 2 == 0) 1800 else 1500) * d).toFloat() * exp(-d * 200f) * 0.5f
                    }
                    val grow = 0.4f + 0.6f * (t / len)
                    l[i] = v * grow * (if (lastIdx % 2 == 0) 1f else 0.7f)
                    r[i] = v * grow * (if (lastIdx % 2 == 0) 0.7f else 1f)
                }
            }
            TransitionSfx.TAPE_STOP -> {
                val notes = floatArrayOf(220f, 277.2f, 330f, 440f)
                val phs = DoubleArray(notes.size * 3)
                for (i in 0 until n) {
                    val t = i.toFloat() / SR
                    val p = t / len
                    val rate = (1f - p).toDouble().pow(1.2).toFloat()
                    var v = 0f
                    for (k in notes.indices) for (h in 1..3) {
                        val idx = k * 3 + h - 1
                        phs[idx] += 2 * PI * notes[k] * h * rate / SR
                        v += sin(phs[idx]).toFloat() / (h * 2f)
                    }
                    fl.step(v, 300f + 3000f * rate)
                    val end = ((len - t) / 0.015f).coerceIn(0f, 1f)
                    val out = fl.low * (1f - p * 0.3f) * end
                    l[i] = out; r[i] = out
                }
            }
            TransitionSfx.SCRATCH -> for (i in 0 until n) {
                val t = i.toFloat() / SR
                val sw = sin(2 * PI * 6 * t).toFloat()
                val fc = 1200f + 1000f * sw
                fl.step(nl.next(), fc, 3f)
                ph += 2 * PI * (300f + 250f * sw) / SR
                val gate = abs(cos(2 * PI * 6 * t).toFloat())
                val v = (fl.band * 1.5f + sin(ph).toFloat() * 0.3f) * gate * exp(-t * 1.5f)
                l[i] = v; r[i] = v * 0.9f
            }
            TransitionSfx.LASER -> for (i in 0 until n) {
                val t = i.toFloat() / SR
                ph += 2 * PI * (150f + 3000f * exp(-t * 9f)) / SR
                val v = tanh(4f * sin(ph).toFloat()) * exp(-t * 6f) * (t / 0.002f).coerceAtMost(1f)
                l[i] = v; r[i] = v
            }
            TransitionSfx.SHIMMER -> {
                val rnd = java.util.Random(777)
                val scale = floatArrayOf(1046.5f, 1174.7f, 1318.5f, 1568f, 1760f, 2093f, 2349.3f, 2637f, 3136f)
                val times = FloatArray(18) { rnd.nextFloat() * 0.6f }
                val freqs = FloatArray(18) { scale[rnd.nextInt(scale.size)] }
                val pans = FloatArray(18) { rnd.nextFloat() }
                for (i in 0 until n) {
                    val t = i.toFloat() / SR
                    var a = 0f; var b = 0f
                    for (k in 0 until 18) {
                        val d = t - times[k]
                        if (d < 0f) continue
                        val env = exp(-d * 4f) * (d / 0.002f).coerceAtMost(1f)
                        val v = (sin(2 * PI * freqs[k] * d).toFloat() + 0.4f * sin(2 * PI * freqs[k] * 2.76 * d).toFloat()) * env
                        a += v * (1f - pans[k]); b += v * pans[k]
                    }
                    l[i] = a; r[i] = b
                }
            }
            TransitionSfx.SHUTTER -> for (i in 0 until n) {
                val t = i.toFloat() / SR
                val a = nl.next()
                fl.step(a, 2500f)
                var v = 0f
                for (c0 in floatArrayOf(0f, 0.09f)) {
                    val d = t - c0
                    if (d >= 0f) v += fl.high * exp(-d * 120f) + sin(2 * PI * 2200 * d).toFloat() * exp(-d * 60f) * 0.3f
                }
                l[i] = v; r[i] = v
            }
            TransitionSfx.STATIC -> {
                val rnd = java.util.Random(31)
                var drop = 1f
                for (i in 0 until n) {
                    val t = i.toFloat() / SR
                    if (i % 900 == 0) drop = if (rnd.nextFloat() < 0.15f) 0.15f else 1f
                    fl.step(nl.next(), 4500f); fr.step(nr.next(), 4500f)
                    val env = (t / 0.03f).coerceAtMost(1f) * ((len - t) / 0.1f).coerceIn(0f, 1f)
                    val hum = sin(2 * PI * 50 * t).toFloat() * 0.15f
                    l[i] = (fl.low * 0.9f * drop + hum) * env
                    r[i] = (fr.low * 0.9f * drop + hum) * env
                }
            }
            TransitionSfx.GLITCH -> {
                val rnd = java.util.Random(4242)
                var i = 0
                while (i < n) {
                    val seg = (SR * (0.02f + rnd.nextFloat() * 0.04f)).toInt()
                    val kind = rnd.nextInt(4)
                    val freq = 200f + rnd.nextFloat() * 1800f
                    val hold = 1 + rnd.nextInt(24)
                    val panL = 0.3f + rnd.nextFloat() * 0.7f; val panR = 1.3f - panL
                    var held = 0f
                    for (j in 0 until seg) {
                        if (i >= n) break
                        val t = i.toFloat() / SR
                        val env = exp(-t * 2.5f) * (if (j < 40) j / 40f else 1f) * (if (seg - j < 40) (seg - j) / 40f else 1f)
                        val v = when (kind) {
                            0 -> if (sin(2 * PI * freq * t) > 0) 0.5f else -0.5f
                            1 -> { if (j % hold == 0) held = nl.next(); held * 0.8f }
                            2 -> nl.next() * 0.6f
                            else -> 0f
                        }
                        l[i] = v * env * panL; r[i] = v * env * panR
                        i++
                    }
                }
            }
        }
        // Normalise to a safe peak.
        var peak = 0f
        for (i in 0 until n) peak = maxOf(peak, abs(l[i]), abs(r[i]))
        if (peak > 0f) { val k = 0.89f / peak; for (i in 0 until n) { l[i] *= k; r[i] *= k } }
        return l to r
    }

    private fun writeWav(f: File, l: FloatArray, r: FloatArray) {
        val n = l.size
        val data = n * 4
        val b = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + data).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(2).putInt(SR).putInt(SR * 4).putShort(4).putShort(16)
        b.put("data".toByteArray()).putInt(data)
        for (i in 0 until n) {
            b.putShort((l[i] * 32767f).toInt().coerceIn(-32768, 32767).toShort())
            b.putShort((r[i] * 32767f).toInt().coerceIn(-32768, 32767).toShort())
        }
        FileOutputStream(f).use { it.write(b.array()) }
    }
}

/** Where (timeline ms) the sound effect on the cut after clip [k] starts; can be negative near the start. */
fun sfxStartMs(clips: List<Clip>, starts: LongArray, k: Int): Long? {
    val fx = clips.getOrNull(k)?.transition?.sfx ?: return null
    if (k + 1 > clips.lastIndex) return null
    return starts[k + 1] + overlapMs(clips, k) / 2 - fx.hitMs
}

/** Plays a sound effect once (preview and the ▶ buttons). */
object SfxPlayer {
    fun play(context: Context, s: TransitionSfx, volume: Float, skipMs: Int = 0) {
        val app = context.applicationContext
        Thread {
            try {
                val f = SfxSynth.file(app, s)
                val mp = android.media.MediaPlayer()
                mp.setDataSource(f.path)
                mp.setOnCompletionListener { it.release() }
                mp.prepare()
                val v = volume.coerceIn(0f, 1f)
                mp.setVolume(v, v)
                if (skipMs > 0) mp.seekTo(skipMs)
                mp.start()
            } catch (_: Throwable) {}
        }.start()
    }
}

/** Where an imported sound sits on the cut. */
enum class SfxAnchor(val label: String) { START("Starts on the cut"), MIDDLE("Centred on the cut"), END("Ends on the cut") }

/** A sound the user imported for transitions (copied into the app, so it keeps working). */
data class UserSfx(val id: String, val name: String, val file: String, val lengthMs: Long, val anchor: SfxAnchor? = null) {
    val anchorOrStart get() = anchor ?: SfxAnchor.START
    val hitMs: Long get() = when (anchorOrStart) { SfxAnchor.START -> 0L; SfxAnchor.MIDDLE -> lengthMs / 2; SfxAnchor.END -> lengthMs }
}

/** The user's own transition sounds (Transitions → Audio transitions → MY SOUNDS). */
object UserSfxLibrary {
    private const val PREFS = "acluxpro_user_sfx"
    private val gson = com.google.gson.Gson()
    val items = androidx.compose.runtime.mutableStateListOf<UserSfx>()
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("list", null) ?: return
        try {
            gson.fromJson(json, Array<UserSfx>::class.java)?.filter { File(it.file).exists() }?.let { items.clear(); items.addAll(it) }
        } catch (_: Throwable) {}
    }

    private fun save(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("list", gson.toJson(items.toTypedArray())).apply()
    }

    fun find(context: Context, id: String?): UserSfx? { if (id == null) return null; load(context); return items.firstOrNull { it.id == id } }

    /** Copies the picked sound into the app. Call off the main thread. Returns null if it can't be read. */
    fun import(context: Context, uri: android.net.Uri): UserSfx? {
        load(context)
        val cr = context.contentResolver
        var name = "Sound"
        try {
            cr.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) name = c.getString(0) ?: name
            }
        } catch (_: Throwable) {}
        val ext = name.substringAfterLast('.', "").lowercase().takeIf { it.length in 2..4 } ?: "audio"
        val id = java.util.UUID.randomUUID().toString()
        val dir = File(context.filesDir, "user_sfx").apply { mkdirs() }
        val f = File(dir, "$id.$ext")
        try {
            cr.openInputStream(uri)?.use { i -> f.outputStream().use { i.copyTo(it) } } ?: return null
        } catch (_: Throwable) { f.delete(); return null }
        val len = try {
            val m = android.media.MediaMetadataRetriever()
            m.setDataSource(f.path)
            val d = m.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            m.release(); d
        } catch (_: Throwable) { 0L }
        if (len < 50) { f.delete(); return null }
        val s = UserSfx(id, name.substringBeforeLast('.'), f.path, len, SfxAnchor.START)
        items.add(s); save(context)
        return s
    }

    fun setAnchor(context: Context, id: String, a: SfxAnchor) {
        val i = items.indexOfFirst { it.id == id }
        if (i >= 0) { items[i] = items[i].copy(anchor = a); save(context) }
    }

    fun remove(context: Context, id: String) {
        items.firstOrNull { it.id == id }?.let { File(it.file).delete() }
        items.removeAll { it.id == id }
        save(context)
    }

    fun play(context: Context, s: UserSfx, volume: Float) {
        Thread {
            try {
                val mp = android.media.MediaPlayer()
                mp.setDataSource(s.file)
                mp.setOnCompletionListener { it.release() }
                mp.prepare()
                val v = volume.coerceIn(0f, 1f); mp.setVolume(v, v)
                mp.start()
            } catch (_: Throwable) {}
        }.start()
    }
}

/** The sound on the cut after clip [k], built-in or imported: file, length and the moment that lands on the cut. */
data class CutSound(val file: File, val lengthMs: Long, val hitMs: Long, val volume: Float)

/** Call off the main thread (a built-in effect may need to be made first). */
fun cutSound(context: Context, clips: List<Clip>, k: Int): CutSound? {
    val tr = clips.getOrNull(k)?.transition ?: return null
    if (k + 1 > clips.lastIndex) return null
    UserSfxLibrary.find(context, tr.userSfx)?.let { u ->
        val f = File(u.file)
        if (f.exists()) return CutSound(f, u.lengthMs, u.hitMs, tr.sfxVolume)
    }
    val fx = tr.sfx ?: return null
    return CutSound(SfxSynth.file(context, fx), fx.lengthMs.toLong(), fx.hitMs.toLong(), tr.sfxVolume)
}

/** Timeline ms where the sound on the cut after clip [k] starts (may be negative near the start). */
fun cutSoundStart(clips: List<Clip>, starts: LongArray, k: Int, hitMs: Long): Long =
    starts[k + 1] + overlapMs(clips, k) / 2 - hitMs
