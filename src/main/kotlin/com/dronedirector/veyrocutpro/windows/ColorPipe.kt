package com.dronedirector.veyrocutpro.camera

import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow

/**
 * The live picture's colour on Windows: filter (colour matrix + film look), log profile and the
 * monitoring LUT, the same maths as the phone's live shader, baked into a 128-step colour table so
 * every picture is coloured with one lookup per pixel. Own code.
 */
class ColorPipe(filter: Filter, curve: Int, lut: com.dronedirector.veyrocutpro.editor.CubeLut?) {
    private val n = 128
    /** null = nothing to do (the picture stays as the camera gives it). */
    val table: IntArray? = if (filter == Filter.NONE && curve == 0 && lut == null) null else build(filter, curve, lut)

    private fun build(filter: Filter, curve: Int, lut: com.dronedirector.veyrocutpro.editor.CubeLut?): IntArray {
        val m = filter.matrix
        val look = filter.look
        val t = IntArray(n * n * n)
        val rgb = FloatArray(3)
        fun flatc(x: Float) = (ln(1f + 6f * x) / ln(7f)) * 0.9f + 0.04f
        fun dlog(x: Float) = if (x <= 0.0078f) 6.025f * x + 0.0929f else (log10(x * 0.9892f + 0.0108f)) * 0.256663f + 0.584555f
        fun dlogm(x: Float) = 0.09f + 0.75f * (ln(1f + 15f * x) / ln(2f)) / 4f
        for (ri in 0 until n) for (gi in 0 until n) for (bi in 0 until n) {
            val r = ri / (n - 1f); val g = gi / (n - 1f); val b = bi / (n - 1f)
            rgb[0] = (m[0] * r + m[1] * g + m[2] * b + m[4] / 255f).coerceIn(0f, 1f)
            rgb[1] = (m[5] * r + m[6] * g + m[7] * b + m[9] / 255f).coerceIn(0f, 1f)
            rgb[2] = (m[10] * r + m[11] * g + m[12] * b + m[14] / 255f).coerceIn(0f, 1f)
            look?.apply(rgb)
            if (curve != 0) for (k in 0..2) {
                val lin = rgb[k].coerceAtLeast(0f).pow(2.2f)
                rgb[k] = when (curve) { 1 -> flatc(lin); 2 -> dlog(lin); 3 -> dlogm(lin); else -> rgb[k] }.coerceIn(0f, 1f)
            }
            lut?.apply(rgb)
            t[(ri * n + gi) * n + bi] = (0xFF shl 24) or ((rgb[0].coerceIn(0f, 1f) * 255f + 0.5f).toInt() shl 16) or
                ((rgb[1].coerceIn(0f, 1f) * 255f + 0.5f).toInt() shl 8) or (rgb[2].coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        }
        return t
    }

    /** Colours [src] into [dst] (may be the same array), using all processor cores. */
    fun apply(src: IntArray, dst: IntArray, count: Int) {
        val t = table ?: run { if (src !== dst) System.arraycopy(src, 0, dst, 0, count); return }
        parallel(count) { a, b ->
            for (i in a until b) {
                val c = src[i]
                dst[i] = t[((((c shr 17) and 0x7F) shl 7 or ((c shr 9) and 0x7F)) shl 7) or ((c shr 1) and 0x7F)]
            }
        }
    }
}

/** Splits 0 until [count] over the processor cores. */
internal inline fun parallel(count: Int, crossinline f: (Int, Int) -> Unit) {
    val cores = Runtime.getRuntime().availableProcessors().coerceIn(1, 16)
    if (count < 200_000 || cores == 1) { f(0, count); return }
    val step = (count + cores - 1) / cores
    java.util.stream.IntStream.range(0, cores).parallel().forEach { k -> val a = k * step; val b = minOf(count, a + step); if (a < b) f(a, b) }
}
