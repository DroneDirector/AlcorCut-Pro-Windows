package com.dronedirector.veyrocutpro.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.size
import kotlin.math.min

/** Waveform, RGB parade and vectorscope, measured from the viewfinder picture a few times a second. */
object ScopeMath {
    /** [mode] 1 waveform, 2 parade, 3 vectorscope. Returns a W×H image. */
    fun render(src: Bitmap, mode: Int): Bitmap {
        val w = src.width; val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        return when (mode) {
            3 -> vector(px)
            else -> wave(px, w, h, mode == 2)
        }
    }

    private fun wave(px: IntArray, w: Int, h: Int, parade: Boolean): Bitmap {
        val outW = if (parade) w * 3 else w
        val outH = 100
        val acc = Array(if (parade) 3 else 1) { IntArray(w * outH) }
        for (y in 0 until h) for (x in 0 until w) {
            val c = px[y * w + x]
            val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
            if (parade) {
                acc[0][x * outH + r * (outH - 1) / 255]++
                acc[1][x * outH + g * (outH - 1) / 255]++
                acc[2][x * outH + b * (outH - 1) / 255]++
            } else {
                val l = (r * 54 + g * 183 + b * 19) shr 8
                acc[0][x * outH + l * (outH - 1) / 255]++
            }
        }
        val out = IntArray(outW * outH)
        val k = 255f * 6f / h
        for (ch in acc.indices) for (x in 0 until w) for (v in 0 until outH) {
            val n = acc[ch][x * outH + v]
            if (n == 0) continue
            val a = min(255f, 40f + n * k).toInt()
            val col = if (!parade) (a shl 24) or 0x00E6FFE6 else when (ch) { 0 -> (a shl 24) or 0x00FF5050; 1 -> (a shl 24) or 0x0050FF70; else -> (a shl 24) or 0x005A8CFF }
            val ox = if (parade) ch * w + x else x
            out[(outH - 1 - v) * outW + ox] = col
        }
        return Bitmap.createBitmap(out, outW, outH, Bitmap.Config.ARGB_8888)
    }

    private fun vector(px: IntArray): Bitmap {
        val s = 120
        val acc = IntArray(s * s)
        for (c in px) {
            val r = ((c shr 16) and 0xFF) / 255f; val g = ((c shr 8) and 0xFF) / 255f; val b = (c and 0xFF) / 255f
            val cb = -0.1687f * r - 0.3313f * g + 0.5f * b
            val cr = 0.5f * r - 0.4187f * g - 0.0813f * b
            val x = ((cb + 0.5f) * (s - 1)).toInt().coerceIn(0, s - 1)
            val y = ((0.5f - cr) * (s - 1)).toInt().coerceIn(0, s - 1)
            acc[y * s + x]++
        }
        val out = IntArray(s * s)
        val k = 255f * 60f / px.size.coerceAtLeast(1)
        for (i in acc.indices) {
            val n = acc[i]
            if (n == 0) continue
            val a = min(255f, 60f + n * k * 40f).toInt()
            val x = i % s; val y = i / s
            // Tint each dot with its own hue so the scope reads like a colour wheel.
            val cb = x / (s - 1f) - 0.5f; val cr = 0.5f - y / (s - 1f)
            val r = (0.6f + 1.4f * cr).coerceIn(0f, 1f); val b = (0.6f + 1.7f * cb).coerceIn(0f, 1f); val g = (0.6f - 0.34f * cb - 0.71f * cr).coerceIn(0f, 1f)
            out[i] = (a shl 24) or ((r * 255).toInt() shl 16) or ((g * 255).toInt() shl 8) or (b * 255).toInt()
        }
        return Bitmap.createBitmap(out, s, s, Bitmap.Config.ARGB_8888)
    }
}
