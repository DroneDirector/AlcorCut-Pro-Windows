package com.dronedirector.veyrocutpro.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlin.math.abs

/** Settings the analyzer reads on every frame (written from the UI thread). */
data class AnalysisConfig(
    val histogram: Boolean = true,
    val zebras: Boolean = false,
    val zebraLevel: Int = 95,
    val peaking: Boolean = false,
    /** Exposure shown as colour bands (purple/blue = crushed, green = mid grey, pink = skin, yellow/red = clipping). */
    val falseColor: Boolean = false,
    val mirror: Boolean = false,
    /** Track sideways motion between frames (for panorama). */
    val motion: Boolean = false,
    /** Measure the average brightness (for the app's own shutter / ISO priority). */
    val meter: Boolean = false,
)

class AnalysisResult(
    /** 64 normalized luma bins (0..1), or null when the histogram is off. */
    val histogram: FloatArray?,
    /** Zebra / focus-peaking overlay, already rotated to display orientation. */
    val overlay: Bitmap?,
    /** Share of pixels clipped at pure white / black, for the clipping warning. */
    val clipHigh: Float,
    val clipLow: Float,
    /** Camera movement since the previous frame, as a share of the screen width (+ = turning right). */
    val motionX: Float = 0f,
    /** Average brightness 0..1 when metering, else -1. */
    val meanLuma: Float = -1f,
)

/**
 * Works on the luma (Y) plane only, at a subsampled resolution, so it stays cheap enough
 * to run alongside preview and capture on mid-range tablets.
 */
class FrameAnalyzer(
    private val config: () -> AnalysisConfig,
    private val onResult: (AnalysisResult) -> Unit,
) : ImageAnalysis.Analyzer {

    private var buffer = ByteArray(0)
    private var frame = 0L
    private var lastOverlay: Bitmap? = null
    private var lastHist: FloatArray? = null
    private var lastClipHigh = 0f
    private var lastClipLow = 0f
    private var prevCols: FloatArray? = null
    private var prevRows: FloatArray? = null

    override fun analyze(image: ImageProxy) {
        try {
            process(image)
        } catch (_: Throwable) {
            // A bad frame is never worth crashing the viewfinder for.
        } finally {
            image.close()
        }
    }

    /**
     * Fallback when the phone can't run a separate analysis stream (common while recording video):
     * analyses a small copy of what is on screen. It is already upright and mirrored as shown,
     * so the overlay lines up with the viewfinder as-is.
     */
    fun analyzeBitmap(bmp: Bitmap) {
        try {
            val w = bmp.width
            val h = bmp.height
            val px = IntArray(w * h)
            bmp.getPixels(px, 0, w, 0, 0, w, h)
            if (buffer.size < w * h) buffer = ByteArray(w * h)
            for (i in 0 until w * h) {
                val c = px[i]
                val l = (((c shr 16) and 0xFF) * 77 + ((c shr 8) and 0xFF) * 150 + (c and 0xFF) * 29) shr 8
                buffer[i] = l.toByte()
            }
            processLuma(config().copy(mirror = false, motion = false), w, h, w, 1, 0)
        } catch (_: Throwable) {
        }
    }

    private fun process(image: ImageProxy) {
        if (CaptureExtras.faceBlur) FaceBlur.submit(image)
        val cfg0 = config()
        val wantAny = cfg0.histogram || cfg0.zebras || cfg0.peaking || cfg0.falseColor || cfg0.motion || cfg0.meter
        if (!wantAny && lastOverlay == null) return
        val plane = image.planes[0]
        val buf = plane.buffer
        buf.rewind()
        val size = buf.remaining()
        if (buffer.size < size) buffer = ByteArray(size)
        buf.get(buffer, 0, size)
        processLuma(cfg0, image.width, image.height, plane.rowStride, plane.pixelStride, image.imageInfo.rotationDegrees)
    }

    private fun processLuma(cfg: AnalysisConfig, w: Int, h: Int, rowStride: Int, pixStride: Int, rotationDegrees: Int) {
        frame++
        val wantOverlay = cfg.zebras || cfg.peaking || cfg.falseColor
        // Histogram ~10 fps, overlays ~15 fps.
        val doHist = (cfg.histogram || cfg.meter) && frame % 3 == 0L
        var mean = -1f
        val doOverlay = wantOverlay && frame % 2 == 0L
        if (!cfg.motion) { prevCols = null; prevRows = null }
        if (!doHist && !doOverlay && !cfg.motion) {
            if (!wantOverlay && lastOverlay != null) {
                lastOverlay = null
                onResult(AnalysisResult(null, null, 0f, 0f))
            }
            return
        }

        var hist: FloatArray? = lastHist
        var clipHigh = lastClipHigh
        var clipLow = lastClipLow
        if (doHist) {
            val bins = IntArray(64)
            var hi = 0
            var lo = 0
            var n = 0
            var sum = 0L
            var y = 0
            while (y < h) {
                val row = y * rowStride
                var x = 0
                while (x < w) {
                    val l = buffer[row + x * pixStride].toInt() and 0xFF
                    bins[l shr 2]++
                    sum += l
                    if (l >= 250) hi++
                    if (l <= 5) lo++
                    n++
                    x += 4
                }
                y += 4
            }
            val max = (bins.maxOrNull() ?: 1).coerceAtLeast(1).toFloat()
            hist = FloatArray(64) { bins[it] / max }
            if (n > 0) {
                mean = sum.toFloat() / n / 255f
                clipHigh = hi.toFloat() / n
                clipLow = lo.toFloat() / n
            }
            lastHist = hist
            lastClipHigh = clipHigh
            lastClipLow = clipLow
        }

        var overlay: Bitmap? = lastOverlay
        if (doOverlay) {
            overlay = buildOverlay(cfg, w, h, rowStride, pixStride, rotationDegrees)
            lastOverlay = overlay
        } else if (!wantOverlay) {
            overlay = null
            lastOverlay = null
        }

        val motion = if (cfg.motion) estimateMotion(w, h, rowStride, pixStride, rotationDegrees) else 0f
        onResult(AnalysisResult(if (cfg.histogram) hist else null, overlay, clipHigh, clipLow, motion, if (cfg.meter) mean else -1f))
    }

    /** Matches brightness profiles of this frame against the previous one. */
    private fun estimateMotion(w: Int, h: Int, rowStride: Int, pixStride: Int, rotation: Int): Float {
        val step = 4
        val cw = w / step
        val ch = h / step
        val cols = FloatArray(cw)
        val rows = FloatArray(ch)
        for (yi in 0 until ch) {
            val row = yi * step * rowStride
            for (xi in 0 until cw) {
                val l = (buffer[row + xi * step * pixStride].toInt() and 0xFF).toFloat()
                cols[xi] += l
                rows[yi] += l
            }
        }
        for (i in cols.indices) cols[i] /= ch
        for (i in rows.indices) rows[i] /= cw
        val pc = prevCols
        val pr = prevRows
        prevCols = cols
        prevRows = rows
        if (pc == null || pr == null || pc.size != cw || pr.size != ch) return 0f
        val sx = bestShift(pc, cols)
        val sy = bestShift(pr, rows)
        return when (rotation) {
            90 -> -sy / ch.toFloat()
            180 -> -sx / cw.toFloat()
            270 -> sy / ch.toFloat()
            else -> sx / cw.toFloat()
        }
    }

    /** Shift s minimising |prev[x + s] - cur[x]| over gradients (s > 0: content moved toward lower x). */
    private fun bestShift(prev: FloatArray, cur: FloatArray): Float {
        val n = prev.size
        val max = n / 4
        var best = 0
        var bestErr = Float.MAX_VALUE
        for (s in -max..max) {
            var err = 0f
            var cnt = 0
            for (x in 1 until n) {
                val px = x + s
                if (px < 1 || px >= n) continue
                err += abs((prev[px] - prev[px - 1]) - (cur[x] - cur[x - 1]))
                cnt++
            }
            if (cnt < n / 2) continue
            err /= cnt
            if (err < bestErr) { bestErr = err; best = s }
        }
        return best.toFloat()
    }

    /** IRE-style false colour bands on the 0..255 luma value; in-between tones show as grey. */
    private fun falseColor(l: Int): Int {
        val pct = l * 100 / 255
        return when {
            pct < 3 -> 0xFF6A1B9A.toInt()       // crushed blacks: purple
            pct < 10 -> 0xFF1E5BFF.toInt()      // near black: blue
            pct in 41..48 -> 0xFF2EBD4F.toInt() // middle grey: green
            pct in 56..64 -> 0xFFFF7FAA.toInt() // skin tones: pink
            pct in 90..96 -> 0xFFFFE03B.toInt() // near clipping: yellow
            pct >= 97 -> 0xFFFF2A2A.toInt()     // clipped: red
            else -> { val g = (l * 0.85f).toInt(); (0xFF shl 24) or (g shl 16) or (g shl 8) or g }
        }
    }

    private fun buildOverlay(
        cfg: AnalysisConfig, w: Int, h: Int, rowStride: Int, pixStride: Int, rotation: Int,
    ): Bitmap {
        val step = 2
        val ow = w / step
        val oh = h / step
        val px = IntArray(ow * oh)
        val zebraThreshold = (cfg.zebraLevel * 255) / 100
        val peakThreshold = 48
        val peakColor = 0xFFFF2D55.toInt()
        val zebraDark = 0xC0000000.toInt()
        val zebraLight = 0x90FFFFFF.toInt()

        fun lum(x: Int, y: Int): Int = buffer[y * rowStride + x * pixStride].toInt() and 0xFF

        for (oy in 1 until oh - 1) {
            val sy = oy * step
            for (ox in 1 until ow - 1) {
                val sx = ox * step
                val l = lum(sx, sy)
                var c = 0
                if (cfg.peaking) {
                    val gx = abs(lum(sx + step, sy) - lum(sx - step, sy))
                    val gy = abs(lum(sx, sy + step) - lum(sx, sy - step))
                    if (gx + gy > peakThreshold) c = peakColor
                }
                if (c == 0 && cfg.falseColor) {
                    c = falseColor(l)
                } else if (c == 0 && cfg.zebras && l >= zebraThreshold) {
                    c = if (((ox + oy) / 4) % 2 == 0) zebraDark else zebraLight
                }
                px[oy * ow + ox] = c
            }
        }
        val bmp = Bitmap.createBitmap(px, ow, oh, Bitmap.Config.ARGB_8888)
        if (rotation == 0 && !cfg.mirror) return bmp
        val m = Matrix()
        m.postRotate(rotation.toFloat())
        if (cfg.mirror) m.postScale(-1f, 1f)
        return Bitmap.createBitmap(bmp, 0, 0, ow, oh, m, false)
    }
}
