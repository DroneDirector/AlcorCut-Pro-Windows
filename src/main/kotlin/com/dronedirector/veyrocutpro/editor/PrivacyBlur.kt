package com.dronedirector.veyrocutpro.editor

import androidx.compose.foundation.layout.size
import kotlin.math.min

/** One blurred thing through time: [t] source ms, [box] centre x, centre y, width, height (0..1 of the frame) per time. */
data class BlurTrack(val face: Boolean, val t: List<Long>, val box: List<Float>)

/** Privacy blur on a clip: faces found automatically and spots you tap, each followed as it moves. */
data class PrivacyBlur(
    val tracks: List<BlurTrack> = emptyList(),
    /** false = blur, true = pixelate. */
    val pixelate: Boolean = false,
    val strength: Float = 0.7f,
    /** Extra room round each face / spot (0..1). */
    val pad: Float = 0.25f,
) {
    val active: Boolean get() = tracks.isNotEmpty()

    /** The boxes showing at source time [src] (up to [MAX]). */
    fun boxesAt(src: Long): List<FloatArray> {
        val out = ArrayList<FloatArray>()
        for (tr in tracks) {
            val n = tr.t.size
            if (n == 0) continue
            if (src < tr.t[0] - 120 || src > tr.t[n - 1] + 120) continue
            var i = tr.t.binarySearch(src).let { if (it >= 0) it else (-it - 2) }.coerceIn(0, n - 1)
            val j = min(i + 1, n - 1)
            val k = if (j == i || tr.t[j] == tr.t[i]) 0f else ((src - tr.t[i]).toFloat() / (tr.t[j] - tr.t[i])).coerceIn(0f, 1f)
            val b = FloatArray(4) { q -> tr.box[i * 4 + q] + (tr.box[j * 4 + q] - tr.box[i * 4 + q]) * k }
            val grow = 1f + pad
            b[2] *= grow; b[3] *= grow
            out += b
            if (out.size >= MAX) break
        }
        return out
    }

    companion object { const val MAX = 8 }
}
