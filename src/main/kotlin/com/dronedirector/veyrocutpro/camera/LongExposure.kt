package com.dronedirector.veyrocutpro.camera

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.pow

/** Long-exposure looks made by stacking many viewfinder frames. */
enum class LongKind(val label: String, val short: String, val hint: String) {
    TRAILS("Light trails", "TRAILS", "Car lights, fireworks, light painting · tripod"),
    WATER("Silky water", "WATER", "Waterfalls, waves, clouds, crowds vanish · tripod"),
    STARS("Night sky", "STARS", "Stars and dark scenes · less noise, brighter · tripod"),
}

/**
 * Builds one picture from many frames:
 *  - TRAILS keeps the brightest value each pixel ever had (moving lights draw lines).
 *  - WATER averages all frames (moving things go smooth, still things stay sharp).
 *  - STARS averages (noise drops with every frame) and then brightens with a gentle stretch.
 */
class LongStacker(val width: Int, val height: Int, private val kind: LongKind) {
    private val n = width * height
    private val sum = IntArray(n * 3)
    private val peak = IntArray(n)
    private val row = IntArray(n)
    var frames = 0; private set

    fun add(frame: Bitmap) {
        val src = if (frame.width == width && frame.height == height) frame
            else Bitmap.createScaledBitmap(frame, width, height, true)
        src.getPixels(row, 0, width, 0, 0, width, height)
        if (src !== frame) src.recycle()
        if (kind == LongKind.TRAILS) {
            for (i in 0 until n) {
                val p = row[i]
                val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
                val q = peak[i]
                peak[i] = (max((q shr 16) and 0xFF, r) shl 16) or (max((q shr 8) and 0xFF, g) shl 8) or max(q and 0xFF, b)
                // A little of the average too, so the still parts don't get noisy-bright.
                val j = i * 3
                sum[j] += r; sum[j + 1] += g; sum[j + 2] += b
            }
        } else {
            for (i in 0 until n) {
                val p = row[i]
                val j = i * 3
                sum[j] += (p shr 16) and 0xFF; sum[j + 1] += (p shr 8) and 0xFF; sum[j + 2] += p and 0xFF
            }
        }
        frames++
    }

    /** The picture so far. [step] > 1 makes a smaller, quicker preview. */
    fun result(step: Int = 1): Bitmap? {
        if (frames == 0) return null
        val w = (width + step - 1) / step
        val h = (height + step - 1) / step
        val out = IntArray(w * h)
        val f = frames.toFloat()
        // Night sky: find a black and white point from the averaged picture, then stretch.
        var black = 0f; var gain = 1f
        if (kind == LongKind.STARS) {
            val hist = IntArray(256)
            var k = 0
            while (k < n) { val j = k * 3; hist[((sum[j] + sum[j + 1] + sum[j + 2]) / (3f * f)).toInt().coerceIn(0, 255)]++; k += 7 }
            val total = hist.sum().coerceAtLeast(1)
            var acc = 0; var lo = 0; var hi = 255
            for (v in 0..255) { acc += hist[v]; if (acc >= total * 0.01f) { lo = v; break } }
            acc = 0
            for (v in 255 downTo 0) { acc += hist[v]; if (acc >= total * 0.002f) { hi = v; break } }
            black = lo * 0.85f
            gain = (255f / (hi - black).coerceAtLeast(24f)).coerceIn(1f, 6f)
        }
        var o = 0
        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                val i = y * width + x
                val j = i * 3
                var r = sum[j] / f; var g = sum[j + 1] / f; var b = sum[j + 2] / f
                when (kind) {
                    LongKind.TRAILS -> {
                        // Lighten blend: the brightest moment of each pixel, over the average.
                        val q = peak[i]
                        r = max(r, ((q shr 16) and 0xFF).toFloat())
                        g = max(g, ((q shr 8) and 0xFF).toFloat())
                        b = max(b, (q and 0xFF).toFloat())
                    }
                    LongKind.STARS -> {
                        r = stretch(r, black, gain); g = stretch(g, black, gain); b = stretch(b, black, gain)
                    }
                    LongKind.WATER -> {}
                }
                out[o++] = (0xFF shl 24) or (r.toInt().coerceIn(0, 255) shl 16) or (g.toInt().coerceIn(0, 255) shl 8) or b.toInt().coerceIn(0, 255)
            }
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun stretch(v: Float, black: Float, gain: Float): Float {
        val t = ((v - black) * gain / 255f).coerceIn(0f, 1f)
        // Soft shoulder so bright stars and city glow don't clip hard.
        return 255f * t.pow(0.92f)
    }
}
