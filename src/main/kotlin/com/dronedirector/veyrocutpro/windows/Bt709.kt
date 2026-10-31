package com.dronedirector.veyrocutpro.windows

/**
 * The PC's decoded pictures are BT.601 YUV; HD files (like the CineForm .mov) are BT.709. Converts a
 * picture's planes in place so the colours stay exactly as in the edit. Own code.
 */
object Bt709 {
    fun planes(img: android.media.Image): Array<android.media.Image.Plane> {
        val p = img.planes
        if (img.height < 720) return p
        val y = p[0].buffer; val u = p[1].buffer; val v = p[2].buffer
        val w = img.width; val h = img.height; val cw = p[1].rowStride; val ch = (h + 1) / 2
        val du = IntArray(cw * ch); val dv = IntArray(cw * ch)
        for (i in 0 until cw * ch) {
            val uu = (u.get(i).toInt() and 0xFF) - 128; val vv = (v.get(i).toInt() and 0xFF) - 128
            du[i] = uu; dv[i] = vv
            u.put(i, (128 + ((66757 * uu + 7512 * vv) shr 16)).coerceIn(0, 255).toByte())
            v.put(i, (128 + ((4918 * uu + 67196 * vv) shr 16)).coerceIn(0, 255).toByte())
        }
        val yRow = p[0].rowStride
        for (r in 0 until h) {
            val cr = (r shr 1) * cw
            for (c in 0 until w) {
                val k = cr + (c shr 1)
                val at = r * yRow + c
                val yy = (y.get(at).toInt() and 0xFF) + ((-7573 * du[k] - 13628 * dv[k]) shr 16)
                y.put(at, yy.coerceIn(0, 255).toByte())
            }
        }
        return p
    }
}
