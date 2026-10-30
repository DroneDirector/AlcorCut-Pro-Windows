package com.dronedirector.veyrocutpro.editor

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max

/**
 * Focus mask: the sharpest areas of the picture tinted green (strong fine detail = in focus). The level is
 * set from the picture itself, so a sharp photo shows its in-focus parts and a blurry one shows little.
 */
object FocusMask {
    fun overlay(src: Bitmap): Bitmap {
        val w = src.width; val h = src.height
        val px = IntArray(w * h); src.getPixels(px, 0, w, 0, 0, w, h)
        val y = IntArray(w * h) { i -> val c = px[i]; (((c shr 16) and 255) * 77 + ((c shr 8) and 255) * 150 + (c and 255) * 29) shr 8 }
        val e = IntArray(w * h)
        val hist = IntArray(1021)
        for (yy in 1 until h - 1) for (xx in 1 until w - 1) {
            val i = yy * w + xx
            val v = abs(4 * y[i] - y[i - 1] - y[i + 1] - y[i - w] - y[i + w]).coerceAtMost(1020)
            e[i] = v; hist[v]++
        }
        // Threshold: the top 6 % of edge strength, but never below a real edge.
        var cnt = 0; var thr = 1020; val want = (w * h) * 0.06
        while (thr > 0 && cnt < want) { cnt += hist[thr]; thr-- }
        thr = max(thr, 28)
        val out = IntArray(w * h)
        for (i in out.indices) {
            if (e[i] > thr) out[i] = 0xC000FF40.toInt()
            else if (i % w > 0 && e[i - 1] > thr) out[i] = 0x8000FF40.toInt()
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }
}
