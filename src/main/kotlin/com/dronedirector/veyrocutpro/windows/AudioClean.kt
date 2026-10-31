package com.dronedirector.veyrocutpro.camera


/**
 * NTSC rates: the sound stretched by 1001 / 1000 so it stays with the picture at 23.976 / 29.97 /
 * 59.94 fps (0.1 % slower, like the phone does after recording). Stereo, interleaved. Own code.
 */
class NtscStretch {
    private var pos = 0.0
    private var lastL = 0f; private var lastR = 0f
    /** [n] stereo frames of [x] in, the stretched frames out. */
    fun run(x: FloatArray, n: Int): FloatArray {
        val out = ArrayList<Float>(n * 2 + 8)
        val step = 1000.0 / 1001.0
        while (pos < n) {
            val i = pos.toInt(); val f = (pos - i).toFloat()
            val l0 = if (i == 0) lastL else x[(i - 1) * 2]; val r0 = if (i == 0) lastR else x[(i - 1) * 2 + 1]
            val l1 = x[i * 2]; val r1 = x[i * 2 + 1]
            out += l0 + (l1 - l0) * f; out += r0 + (r1 - r0) * f
            pos += step
        }
        pos -= n
        if (n > 0) { lastL = x[(n - 1) * 2]; lastR = x[(n - 1) * 2 + 1] }
        return out.toFloatArray()
    }
}
