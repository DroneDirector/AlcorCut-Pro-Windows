package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * AI Remove for photos, on the phone: everything painted or tapped is filled in by LaMa
 * (Apache 2.0) at the photo's full size. Each separate area gets its own square around it, so a
 * small thing is painted at (nearly) full detail and only the removed part changes — the rest of
 * the photo keeps every original pixel.
 */
object PhotoErase {
    /** Is the AI model there (else the simpler fill is used)? */
    fun ready(context: Context) = ObjectRemoval.ready(context)

    /**
     * Fills [hole] (one weight per pixel of [src], > 0.25 = remove) and returns the new photo, or
     * null if the AI couldn't run. [onProgress] gets 0..100.
     */
    fun fill(context: Context, src: Bitmap, hole: FloatArray, onProgress: (Int) -> Unit = {}): Bitmap? {
        if (!ObjectRemoval.ready(context)) return null
        val w = src.width; val h = src.height
        if (hole.size != w * h) return null
        // Grow the area a little: AI selections and brush strokes sit tight on the object, and its
        // soft edge or shadow halo must go too.
        val grow = max(2, min(w, h) / 260)
        val raw = BooleanArray(w * h) { hole[it] > 0.25f }
        if (raw.none { it }) return src
        val m = dilate(raw, w, h, grow)

        // Separate areas, found on a coarse grid (areas close together are done as one).
        val cell = 16
        val gw = (w + cell - 1) / cell; val gh = (h + cell - 1) / cell
        val g = BooleanArray(gw * gh)
        for (y in 0 until h) { val gy = y / cell; for (x in 0 until w) if (m[y * w + x]) g[gy * gw + x / cell] = true }
        val gd = dilate(g, gw, gh, 3)
        val label = IntArray(gw * gh) { -1 }
        val boxes = ArrayList<IntArray>()   // x0, y0, x1, y1 in px
        val stack = IntArray(gw * gh)
        for (start in g.indices) {
            if (!gd[start] || label[start] >= 0) continue
            val id = boxes.size
            var sp = 0; stack[sp++] = start; label[start] = id
            var bx0 = Int.MAX_VALUE; var by0 = Int.MAX_VALUE; var bx1 = -1; var by1 = -1
            while (sp > 0) {
                val c = stack[--sp]; val cx = c % gw; val cy = c / gw
                if (g[c]) { bx0 = min(bx0, cx); by0 = min(by0, cy); bx1 = max(bx1, cx); by1 = max(by1, cy) }
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = cx + dx; val ny = cy + dy
                    if (nx !in 0 until gw || ny !in 0 until gh) continue
                    val n = ny * gw + nx
                    if (gd[n] && label[n] < 0) { label[n] = id; stack[sp++] = n }
                }
            }
            if (bx1 >= 0) boxes += intArrayOf(bx0 * cell, by0 * cell, min(w, (bx1 + 1) * cell), min(h, (by1 + 1) * cell))
            else boxes += intArrayOf(0, 0, 0, 0)
        }
        val out = src.copy(Bitmap.Config.ARGB_8888, true) ?: return null
        val todo = boxes.filter { it[2] > it[0] && it[3] > it[1] }
        todo.forEachIndexed { k, b ->
            onProgress(k * 100 / todo.size)
            if (!paintBox(out, m, w, h, b, grow)) return null
        }
        onProgress(100)
        return out
    }

    /** One area: a square of the photo round it (at least 512 px, so small things keep their detail) through LaMa. */
    private fun paintBox(out: Bitmap, m: BooleanArray, w: Int, h: Int, b: IntArray, grow: Int): Boolean {
        val bw = b[2] - b[0]; val bh = b[3] - b[1]
        val side = max((max(bw, bh) * 2.2f).roundToInt(), 512)
        val sw = min(side, w); val sh = min(side, h)
        val cx = (b[0] + b[2]) / 2; val cy = (b[1] + b[3]) / 2
        val sx0 = (cx - sw / 2).coerceIn(0, w - sw); val sy0 = (cy - sh / 2).coerceIn(0, h - sh)
        val crop = Bitmap.createBitmap(out, sx0, sy0, sw, sh)
        val small = Bitmap.createScaledBitmap(crop, 512, 512, true)
        val n = 512 * 512
        val sp = IntArray(n); small.getPixels(sp, 0, 512, 0, 0, 512, 512)
        if (small !== crop) small.recycle()
        val img = FloatArray(3 * n); val msk = FloatArray(n)
        for (i in 0 until n) { val c = sp[i]; img[i] = ((c shr 16) and 255) / 255f; img[n + i] = ((c shr 8) and 255) / 255f; img[2 * n + i] = (c and 255) / 255f }
        // A model pixel is painted if any photo pixel under it is.
        for (y in 0 until sh) { val my = y * 512 / sh; val row = (sy0 + y) * w + sx0; for (x in 0 until sw) if (m[row + x]) msk[my * 512 + x * 512 / sw] = 1f }
        val res = ObjectRemoval.lama512(img, msk) ?: return false
        val rp = IntArray(n) { i ->
            val r = res[i].roundToInt().coerceIn(0, 255); val gg = res[n + i].roundToInt().coerceIn(0, 255); val bb = res[2 * n + i].roundToInt().coerceIn(0, 255)
            (0xFF shl 24) or (r shl 16) or (gg shl 8) or bb
        }
        val big = Bitmap.createScaledBitmap(Bitmap.createBitmap(rp, 512, 512, Bitmap.Config.ARGB_8888), sw, sh, true)
        val bp = IntArray(sw * sh); big.getPixels(bp, 0, sw, 0, 0, sw, sh); big.recycle()
        val op = IntArray(sw * sh); crop.getPixels(op, 0, sw, 0, 0, sw, sh)
        if (crop !== out) crop.recycle()
        // Soft edge: the painted area fades into the original over a few pixels outside it.
        val a = FloatArray(sw * sh); for (y in 0 until sh) for (x in 0 until sw) if (m[(sy0 + y) * w + sx0 + x]) a[y * sw + x] = 1f
        val soft = boxBlur(a, sw, sh, max(1, grow / 2 + 1))
        for (i in op.indices) {
            val k = if (a[i] >= 1f) 1f else (soft[i] * 2f).coerceIn(0f, 1f)
            if (k <= 0f) continue
            val o = op[i]; val p = bp[i]
            fun mix(s: Int) = (((o shr s) and 255) * (1 - k) + ((p shr s) and 255) * k).roundToInt()
            op[i] = (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
        }
        out.setPixels(op, 0, sw, sx0, sy0, sw, sh)
        return true
    }

    /** Grows true areas by [r] (square). */
    private fun dilate(src: BooleanArray, w: Int, h: Int, r: Int): BooleanArray {
        val tmp = BooleanArray(w * h); val out = BooleanArray(w * h)
        for (y in 0 until h) {
            var last = -1_000_000
            for (x in 0 until w) { if (src[y * w + x]) last = x; if (x - last <= r) tmp[y * w + x] = true }
            last = 1_000_000
            for (x in w - 1 downTo 0) { if (src[y * w + x]) last = x; if (last - x <= r) tmp[y * w + x] = true }
        }
        for (x in 0 until w) {
            var last = -1_000_000
            for (y in 0 until h) { if (tmp[y * w + x]) last = y; if (y - last <= r) out[y * w + x] = true }
            last = 1_000_000
            for (y in h - 1 downTo 0) { if (tmp[y * w + x]) last = y; if (last - y <= r) out[y * w + x] = true }
        }
        return out
    }

    private fun boxBlur(src: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        val tmp = FloatArray(w * h); val out = FloatArray(w * h); val d = 2f * r + 1
        for (y in 0 until h) {
            var s = 0f
            for (x in -r..r) s += src[y * w + x.coerceIn(0, w - 1)]
            for (x in 0 until w) { tmp[y * w + x] = s / d; s += src[y * w + (x + r + 1).coerceAtMost(w - 1)] - src[y * w + (x - r).coerceAtLeast(0)] }
        }
        for (x in 0 until w) {
            var s = 0f
            for (y in -r..r) s += tmp[y.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) { out[y * w + x] = s / d; s += tmp[(y + r + 1).coerceAtMost(h - 1) * w + x] - tmp[(y - r).coerceAtLeast(0) * w + x] }
        }
        return out
    }
}
