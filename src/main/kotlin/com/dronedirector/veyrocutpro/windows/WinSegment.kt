package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.graphics.Bitmap
import kotlin.math.exp

/** People in a photo with DeepLab v3 (its "person" class), on Windows. Own code. */
object WinSegment {
    private const val N = 257
    private const val PERSON = 15
    private var session: ai.onnxruntime.OrtSession? = null
    private val lock = Any()

    private fun session(c: Context): ai.onnxruntime.OrtSession? = synchronized(lock) {
        session ?: run {
            val f = ModelStore.file(c, "deeplab.onnx")
            if (!f.exists() || java.io.File(f.path + ".part").exists()) return null
            val so = ai.onnxruntime.OrtSession.SessionOptions().apply { setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(1, 8)) }
            ai.onnxruntime.OrtEnvironment.getEnvironment().createSession(f.path, so).also { session = it }
        }
    }

    /** How likely each cell of an N × N grid is a person (0..1), and N; null without the model. */
    fun person(c: Context, b: Bitmap): Pair<FloatArray, Int>? {
        val s = session(c) ?: return null
        val small = Bitmap.createScaledBitmap(b, N, N, true)
        val px = IntArray(N * N); small.getPixels(px, 0, N, 0, 0, N, N)
        val inp = FloatArray(N * N * 3)
        for (i in px.indices) {
            val v = px[i]
            inp[i * 3] = (((v shr 16) and 0xFF) - 127.5f) / 127.5f
            inp[i * 3 + 1] = (((v shr 8) and 0xFF) - 127.5f) / 127.5f
            inp[i * 3 + 2] = ((v and 0xFF) - 127.5f) / 127.5f
        }
        val env = ai.onnxruntime.OrtEnvironment.getEnvironment()
        val o = synchronized(lock) {
            ai.onnxruntime.OnnxTensor.createTensor(env, java.nio.FloatBuffer.wrap(inp), longArrayOf(1, N.toLong(), N.toLong(), 3)).use { t ->
                s.run(mapOf(s.inputNames.first() to t)).use { r ->
                    val fb = (r.get(0) as ai.onnxruntime.OnnxTensor).floatBuffer; FloatArray(fb.remaining()).also { fb.get(it) }
                }
            }
        }
        val k = o.size / (N * N)
        if (k <= PERSON) return null
        return FloatArray(N * N) { i ->
            var mx = -1e9f
            for (j in 0 until k) mx = maxOf(mx, o[i * k + j])
            var sum = 0f
            for (j in 0 until k) sum += exp(o[i * k + j] - mx)
            exp(o[i * k + PERSON] - mx) / sum
        } to N
    }
}
