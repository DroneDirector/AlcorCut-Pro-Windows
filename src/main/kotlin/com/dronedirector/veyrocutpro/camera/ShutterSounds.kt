package com.dronedirector.veyrocutpro.camera

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Shutter sounds made by the app itself (synthesised, no recordings): a few camera styles to pick
 * from in Capture settings. Where the law requires the phone's own shutter sound, that one is
 * always used instead.
 */
object ShutterSounds {
    val NAMES = listOf("Phone default", "SLR", "Rangefinder", "Film camera", "Digital")
    private const val RATE = 44100
    private val cache = HashMap<Int, AudioTrack>()

    fun style(c: Context) = c.getSharedPreferences("procam", Context.MODE_PRIVATE).getInt("shutter_style", 0).coerceIn(0, NAMES.lastIndex)
    fun setStyle(c: Context, s: Int) = c.getSharedPreferences("procam", Context.MODE_PRIVATE).edit().putInt("shutter_style", s).apply()

    /** Plays style [s] (1…4). */
    @Synchronized fun play(s: Int) {
        try {
            val t = cache.getOrPut(s) { track(render(s)) }
            t.stop(); t.reloadStaticData(); t.play()
        } catch (_: Throwable) {}
    }

    private fun track(pcm: ShortArray): AudioTrack {
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size * 2).build()
        t.write(pcm, 0, pcm.size)
        return t
    }

    private fun render(s: Int): ShortArray {
        val len = when (s) { 3 -> 0.42f; 1 -> 0.16f; else -> 0.1f }
        val n = (RATE * len).toInt()
        val out = FloatArray(n)
        var seed = 12345 + s * 977
        fun noise(): Float { seed = seed xor (seed shl 13); seed = seed xor (seed ushr 17); seed = seed xor (seed shl 5); return (seed and 0xFFFF) / 32768f - 1f }
        // A click: filtered noise with a fast decay, plus a short ring at [ring] Hz.
        fun click(at: Float, gain: Float, decayMs: Float, ring: Float, bright: Float) {
            val start = (at * RATE).toInt()
            var lp = 0f
            for (i in 0 until (RATE * decayMs * 6 / 1000).toInt()) {
                val k = start + i; if (k >= n) break
                val tt = i.toFloat() / RATE
                val env = exp(-tt * 1000f / decayMs)
                lp += (noise() - lp) * bright
                out[k] += gain * env * (lp * 0.8f + 0.5f * sin(2 * PI.toFloat() * ring * tt))
            }
        }
        fun thump(at: Float, gain: Float, hz: Float, decayMs: Float) {
            val start = (at * RATE).toInt()
            for (i in 0 until (RATE * decayMs * 5 / 1000).toInt()) {
                val k = start + i; if (k >= n) break
                val tt = i.toFloat() / RATE
                out[k] += gain * exp(-tt * 1000f / decayMs) * sin(2 * PI.toFloat() * hz * tt)
            }
        }
        when (s) {
            1 -> { // SLR: mirror up, shutter, mirror down — a solid "ka-chunk".
                click(0f, 0.9f, 4f, 2200f, 0.55f); thump(0f, 0.7f, 95f, 18f)
                click(0.065f, 1f, 5f, 1800f, 0.5f); thump(0.065f, 0.8f, 80f, 24f)
            }
            2 -> { // Rangefinder: one quiet, soft tick.
                click(0f, 0.75f, 2.5f, 3200f, 0.35f); thump(0f, 0.25f, 140f, 8f)
            }
            3 -> { // Film camera: shutter, then the winder.
                click(0f, 0.9f, 4f, 2000f, 0.5f); thump(0f, 0.6f, 90f, 16f)
                val start = (0.12f * RATE).toInt(); val dur = (0.26f * RATE).toInt()
                for (i in 0 until dur) {
                    val k = start + i; if (k >= n) break
                    val tt = i.toFloat() / RATE
                    // Ratchet: quick little clicks under a gear whirr.
                    val ratchet = if ((tt * 55f) % 1f < 0.12f) 1f else 0.25f
                    val env = (if (tt < 0.02f) tt / 0.02f else 1f) * (if (tt > 0.22f) (0.26f - tt) / 0.04f else 1f)
                    out[k] += 0.35f * env * ratchet * noise() + 0.12f * env * sin(2 * PI.toFloat() * 310f * tt)
                }
            }
            4 -> { // Digital: a short, clean double beep.
                for (b in 0..1) {
                    val start = (b * 0.05f * RATE).toInt()
                    for (i in 0 until (0.035f * RATE).toInt()) {
                        val k = start + i; if (k >= n) break
                        val tt = i.toFloat() / RATE
                        val env = (if (tt < 0.003f) tt / 0.003f else 1f) * exp(-tt * 40f)
                        out[k] += 0.45f * env * sin(2 * PI.toFloat() * (if (b == 0) 2100f else 2800f) * tt)
                    }
                }
            }
        }
        var peak = 0.001f
        for (v in out) peak = maxOf(peak, kotlin.math.abs(v))
        val k = 0.85f / peak
        return ShortArray(n) { (out[it] * k * 32767f).toInt().coerceIn(-32768, 32767).toShort() }
    }
}

/**
 * What the phone's side (volume) buttons do in Capture: nothing (normal volume), the shutter —
 * take a photo or start / stop recording, whichever mode is on — or zoom in / out.
 */
object SideKeys {
    val NAMES = listOf("Off (volume)", "Shutter / record", "Zoom")
    fun get(c: Context): Int = c.getSharedPreferences("procam", Context.MODE_PRIVATE).getInt("side_keys", 1).coerceIn(0, NAMES.lastIndex)
    fun set(c: Context, v: Int) = c.getSharedPreferences("procam", Context.MODE_PRIVATE).edit().putInt("side_keys", v).apply()
}

