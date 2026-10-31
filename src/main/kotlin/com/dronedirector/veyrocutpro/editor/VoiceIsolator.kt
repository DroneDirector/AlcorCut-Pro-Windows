package com.dronedirector.veyrocutpro.editor

/**
 * AI voice isolation (Audio → Voice noise reduction → "Voice isolation (AI)"): keeps speech and
 * removes wind, traffic, hum, crowd and music, using RNNoise (BSD 3-clause, bundled as native code).
 *
 * RNNoise works on 48 kHz mono frames of 480 samples; other sample rates are resampled to 48 kHz
 * and back. Interleaved samples in (−1..1) → the same number of samples out, [delay] frames late.
 */
class VoiceIsolator(private val channels: Int, private val rate: Int, private val amount: Float = 1f, private val studio: Boolean = false) {
    companion object {
        private const val FRAME = 480
        /** DeepFilterNet3's look-ahead at 48 kHz (two frames); with its analysis frame the output is 1440 samples late (measured). */
        private const val DF_DELAY = 480 * 2
        val available: Boolean by lazy {
            try { System.loadLibrary("veyrocut_native"); true } catch (_: Throwable) { false }
        }
        /** Where the DeepFilterNet3 model file is (copied out of the app's assets by [prepare]). */
        @Volatile var dfModel: String? = null
        /** Studio voice (DeepFilterNet3) can run on this phone. */
        val studioAvailable: Boolean get() = dfModel != null && dfOk
        /** Call before export: picks up the model if it has arrived since start-up. */
        fun studioReady(context: android.content.Context): Boolean { prepare(context); return studioAvailable }
        private val dfOk: Boolean by lazy { available && try { nativeDfAvailable() } catch (_: Throwable) { false } }

        /** Finds the DeepFilterNet3 model (downloaded on first launch). */
        fun prepare(context: android.content.Context) {
            if (dfModel != null) return
            if (AppData.has(context, "DeepFilterNet3_onnx.tar.gz")) dfModel = AppData.file(context, "DeepFilterNet3_onnx.tar.gz").path
        }
        @JvmStatic private external fun nativeCreate(): Long
        @JvmStatic private external fun nativeDestroy(h: Long)
        @JvmStatic private external fun nativeProcess(h: Long, input: FloatArray, output: FloatArray): Float
        @JvmStatic private external fun nativeDfAvailable(): Boolean
        @JvmStatic private external fun nativeDfCreate(model: String, atten: Float): Long
        @JvmStatic private external fun nativeDfFrame(h: Long): Int
        @JvmStatic private external fun nativeDfDestroy(h: Long)
        @JvmStatic private external fun nativeDfProcess(h: Long, input: FloatArray, output: FloatArray): Float
    }

    /** Frames of delay (at the input rate) before the first cleaned sample. */
    val delay: Int = ((FRAME + (if (studio) DF_DELAY else 0)).toLong() * rate / 48000).toInt() + 4

    private inner class Chan {
        val h = if (studio) nativeDfCreate(dfModel ?: "", 100f) else nativeCreate()
        // The dry signal, held back by the model's own delay so dry and clean line up when mixed.
        val dryDelay = ArrayDeque<Float>().also { q -> if (studio) repeat(FRAME + DF_DELAY) { q.addLast(0f) } }
        // Up-sampling (input rate → 48 kHz) state.
        var upPos = 0.0; var upPrev = 0f; var upHave = false
        // 48 kHz frame being filled, its dry copy, and cleaned 48 kHz output waiting to be down-sampled.
        val inBuf = FloatArray(FRAME); val outBuf = FloatArray(FRAME); var fill = 0
        val clean48 = ArrayDeque<Float>()
        val dry48 = ArrayDeque<Float>()
        // Down-sampling (48 kHz → input rate) state.
        var downPos = 0.0; var downPrev = 0f; var downPrevDry = 0f; var downHave = false
        val outQ = ArrayDeque<Float>()

        fun push48(v: Float) {
            if (studio) {
                inBuf[fill++] = v
                if (fill == FRAME) {
                    nativeDfProcess(h, inBuf, outBuf)
                    for (i in 0 until FRAME) { clean48.addLast(outBuf[i]); dryDelay.addLast(inBuf[i]); dry48.addLast(dryDelay.removeFirst()) }
                    fill = 0
                    drain()
                }
                return
            }
            inBuf[fill++] = v * 32768f
            if (fill == FRAME) {
                nativeProcess(h, inBuf, outBuf)
                for (i in 0 until FRAME) { clean48.addLast(outBuf[i] / 32768f); dry48.addLast(inBuf[i] / 32768f) }
                fill = 0
                drain()
            }
        }

        /** Down-sample whatever is ready back to the input rate. */
        fun drain() {
            val step = 48000.0 / rate
            while (clean48.isNotEmpty()) {
                val c = clean48.removeFirst(); val d = dry48.removeFirst()
                if (!downHave) { downPrev = c; downPrevDry = d; downHave = true; outQ.addLast(mix(c, d)); downPos = step; continue }
                // Emit every output sample that falls between the previous and this 48 kHz sample.
                while (downPos <= 1.0) {
                    val t = downPos.toFloat()
                    outQ.addLast(mix(downPrev + (c - downPrev) * t, downPrevDry + (d - downPrevDry) * t))
                    downPos += step
                }
                downPos -= 1.0
                downPrev = c; downPrevDry = d
            }
        }

        private fun mix(clean: Float, dry: Float) = if (amount >= 0.999f) clean else dry + (clean - dry) * amount

        fun push(v: Float) {
            if (rate == 48000) { push48(v); return }
            val step = rate / 48000.0
            if (!upHave) { upPrev = v; upHave = true; push48(v); upPos = step; return }
            while (upPos <= 1.0) { push48(upPrev + (v - upPrev) * upPos.toFloat()); upPos += step }
            upPos -= 1.0
            upPrev = v
        }
    }

    private val chans = Array(channels) { Chan() }
    private var primed = 0

    fun process(input: FloatArray): FloatArray {
        val frames = input.size / channels
        val out = FloatArray(frames * channels)
        for (f in 0 until frames) {
            for (c in 0 until channels) chans[c].push(input[f * channels + c])
            // The first [delay] frames come out silent; after that, one cleaned frame per input frame.
            if (primed < delay) { primed++; continue }
            for (c in 0 until channels) out[f * channels + c] = chans[c].outQ.removeFirstOrNull() ?: 0f
        }
        return out
    }

    fun release() { chans.forEach { try { if (studio) nativeDfDestroy(it.h) else nativeDestroy(it.h) } catch (_: Throwable) {} } }
}
