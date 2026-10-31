package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Automatic test (emulator): the visualizer's music analysis and its pictures. Results go to the log ("VisTest"). */
object VisTest {
    /** The made-up test song ([seconds] long) as a file. */
    fun song(context: Context, seconds: Int): Uri { val f = File(context.cacheDir, "testsong_$seconds.wav"); if (!f.exists()) wav(f, seconds); return Uri.fromFile(f) }

    private fun wav(f: File, seconds: Int, rate: Int = 44100) {
        val n = seconds * rate
        val pcm = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN)
        var seed = 1
        for (i in 0 until n) {
            val t = i.toDouble() / rate
            val beat = t % 0.5
            val kick = sin(2 * PI * (50 + 80 * exp(-beat * 30)) * beat) * exp(-beat * 8) * 0.8
            val chord = if (((t / 2).toInt() % 2) == 0) 220.0 else 330.0
            val tone = (sin(2 * PI * chord * t) + sin(2 * PI * chord * 1.5 * t)) * 0.12 * (0.6 + 0.4 * sin(2 * PI * 0.25 * t))
            seed = seed * 1103515245 + 12345
            val hat = (((seed ushr 8) and 0xFFFF) / 32768.0 - 1.0) * exp(-((t + 0.25) % 0.5) * 40) * 0.15
            pcm.putShort(((kick + tone + hat).coerceIn(-1.0, 1.0) * 30000).toInt().toShort())
        }
        FileOutputStream(f).use { o ->
            val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            h.put("RIFF".toByteArray()); h.putInt(36 + n * 2); h.put("WAVE".toByteArray()); h.put("fmt ".toByteArray())
            h.putInt(16); h.putShort(1); h.putShort(1); h.putInt(rate); h.putInt(rate * 2); h.putShort(2); h.putShort(16)
            h.put("data".toByteArray()); h.putInt(n * 2)
            o.write(h.array()); o.write(pcm.array())
        }
    }

    fun run(context: Context) {
        val tag = "VisTest"
        try {
            val f = File(context.cacheDir, "vistest.wav")
            wav(f, 12)
            val uri = Uri.fromFile(f)
            val t0 = System.currentTimeMillis()
            val data = Spectrum.load(context, uri)
            android.util.Log.i(tag, "analysis: ${if (data == null) "FAILED" else "${data.size / Spectrum.STRIDE} frames"} in ${System.currentTimeMillis() - t0} ms")
            if (data == null) return
            // How much the bands move between frames, and the beats found.
            var move = 0.0; var beats = 0; val nF = data.size / Spectrum.STRIDE
            for (i in 1 until nF) {
                for (b in 0 until Spectrum.BANDS) move += kotlin.math.abs(data[i * Spectrum.STRIDE + b] - data[(i - 1) * Spectrum.STRIDE + b])
                if (data[i * Spectrum.STRIDE + Spectrum.BANDS] > 0.5f && data[(i - 1) * Spectrum.STRIDE + Spectrum.BANDS] <= 0.5f) beats++
            }
            android.util.Log.i(tag, "band movement per frame %.3f, beats found $beats (expected about ${nF * Spectrum.HOP_MS / 500})".format(move / (nF - 1)))
            for (style in 0 until FxLayers.VIS_STYLES.size) {
                val l = Layer(kind = LayerKind.FX, fx = FxLayers.VIS, imageUri = uri, fxD = 0f, fxB = style.toFloat(), size = 0.25f, startMs = 0, endMs = 12_000)
                val t1 = System.currentTimeMillis()
                val a = FxLayers.render(l, 1280, 720, 2000, context)
                val ms = System.currentTimeMillis() - t1
                val b = FxLayers.render(l, 1280, 720, 2240, context)
                var diff = 0L
                if (a != null && b != null && a.width == b.width && a.height == b.height) {
                    val pa = IntArray(a.width * a.height); val pb = IntArray(b.width * b.height)
                    a.getPixels(pa, 0, a.width, 0, 0, a.width, a.height); b.getPixels(pb, 0, b.width, 0, 0, b.width, b.height)
                    for (i in pa.indices) if (pa[i] != pb[i]) diff++
                }
                android.util.Log.i(tag, "style $style: render ${ms} ms, ${if (a == null) "NO PICTURE" else "${a.width}x${a.height}"}, pixels changed between 2.00 s and 2.24 s: $diff")
            }
            // Countdown styles: render time each (the preview redraws them every frame).
            for (style in 0 until FxLayers.TIMER_STYLES.size) {
                val l = Layer(kind = LayerKind.FX, fx = FxLayers.TIMER, fxB = style.toFloat(), size = 0.18f, startMs = 0, endMs = 10_000)
                val t1 = System.currentTimeMillis()
                val a = FxLayers.render(l, 1280, 720, 3000, context)
                android.util.Log.i(tag, "timer $style: render ${System.currentTimeMillis() - t1} ms, ${if (a == null) "NO PICTURE" else "ok"}")
            }
            for (style in RetroFx.STYLES.indices) {
                val l = Layer(kind = LayerKind.FX, fx = FxLayers.RETRO, fxA = style.toFloat(), fxX = 10_500f, fxY = 600f, startMs = 0, endMs = 10_000)
                val t1 = System.currentTimeMillis()
                val a = FxLayers.render(l, 1920, 1080, 3000, context)
                android.util.Log.i(tag, "retro $style: render ${System.currentTimeMillis() - t1} ms, ${if (a == null) "NO PICTURE" else "ok"}")
            }
        } catch (t: Throwable) { android.util.Log.e(tag, "failed", t) }
    }

    /** Slideshow: three drawn photos made into clips (runs on the main thread — the video maker needs it). */
    suspend fun slideshow(context: Context) {
        try {
            val uris = (0 until 3).map { k ->
                val b = android.graphics.Bitmap.createBitmap(1200, 800, android.graphics.Bitmap.Config.ARGB_8888)
                b.eraseColor(listOf(0xFFC0392B, 0xFF27AE60, 0xFF2980B9)[k].toInt())
                val f = java.io.File(context.cacheDir, "slidetest_$k.jpg")
                f.outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
                android.net.Uri.fromFile(f)
            }
            val t0 = System.currentTimeMillis()
            val clips = Slideshow.build(context, Slideshow.Request(uris, 3, TransitionType.CROSSFADE, true)) { _, _ -> }
            android.util.Log.i("VisTest", "slideshow: ${clips.size} of 3 clips in ${System.currentTimeMillis() - t0} ms; " +
                clips.joinToString { "${it.durationMs} ms ${it.width}x${it.height} zoom ${it.reframe.end.zoom} tr ${it.transition.type}" })
        } catch (t: Throwable) { android.util.Log.e("VisTest", "slideshow failed", t) }
    }

    /** Text styles: every font, effect, lower third and text type drawn once; slow or failing ones are logged. */
    fun text(context: Context) {
        try {
            var bad = 0; var slow = ""
            fun check(name: String, l: Layer) {
                val t0 = System.currentTimeMillis()
                val b = try { LayerRenderer.render(context, l, 1920, 1080) } catch (t: Throwable) { android.util.Log.e("VisTest", "text $name failed", t); null }
                val ms = System.currentTimeMillis() - t0
                if (b == null || b.width < 4) bad++
                if (ms > 120) slow += "$name ${ms}ms; "
            }
            var noFace = 0
            for (f in TextFonts.ALL) { if (TextFonts.typeface(f.id) == null) noFace++; check("font ${f.id}", Layer(kind = LayerKind.TEXT, text = "Æøå Ðý Hello", face = f.id, size = 0.1f)) }
            for (i in 1 until TextFx.NAMES.size) check("fx ${TextFx.NAMES[i]}", Layer(kind = LayerKind.TEXT, text = "EFFECT", textFx = i, face = "anton", size = 0.12f))
            for (i in LowerThirds.NAMES.indices) check("lower third ${LowerThirds.NAMES[i]}", lowerThirdLayer(i, 0L, 1000L))
            for (t in TextTypes.ALL) check("type ${t.name}", t.layer(0L, 1000L))
            android.util.Log.i("VisTest", "text: ${TextFonts.ALL.size} fonts ($noFace not loaded), ${TextFx.NAMES.size - 1} effects, ${LowerThirds.NAMES.size} lower thirds, ${TextTypes.ALL.size} types; failed $bad; slow: ${slow.ifEmpty { "none" }}")
        } catch (t: Throwable) { android.util.Log.e("VisTest", "text test failed", t) }
    }

    /** Transitions: every preview shader built once; failures logged with the compiler's message. */
    fun transitions() {
        if (android.os.Build.VERSION.SDK_INT < 33) { android.util.Log.i("VisTest", "transitions: API < 33, preview shaders not used"); return }
        var bad = 0
        for (t in TransitionType.entries) {
            if (t == TransitionType.NONE) continue
            try { android.graphics.RuntimeShader(TransitionMath.agsl(t)) }
            catch (e: Throwable) { bad++; if (bad <= 3) android.util.Log.e("VisTest", "transition ${t.name} shader failed: ${e.message?.take(1500)}") }
        }
        android.util.Log.i("VisTest", "transitions: ${TransitionType.entries.size - 1} preview shaders, $bad failed")
    }
}
