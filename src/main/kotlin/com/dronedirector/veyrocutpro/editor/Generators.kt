package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.opengl.GLES20
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import java.io.File

/** Generated clips (Media → GENERATORS): drawn by AlcorCut Pro and saved as short videos. */
enum class GenKind(val label: String) {
    BLACK("Black"), SOLID("Solid colour"), GRADIENT("Gradient"), NOISE("Noise"), BARS("Colour bars"), COUNTDOWN("Countdown leader"),
}

data class GenSpec(val kind: GenKind, val color1: Int = 0xFF101010.toInt(), val color2: Int = 0xFF2A4C8C.toInt(), val seconds: Int = 5, val width: Int = 1920, val height: Int = 1080, val fps: Int = 30)

@UnstableApi
object Generators {
    private fun even(v: Int) = (v / 2 * 2).coerceIn(16, 3840)

    /** The still picture behind the generator (the countdown's digit [digit], or null). */
    private fun still(s: GenSpec, digit: Int? = null): Bitmap {
        val w = even(s.width); val h = even(s.height)
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        when (s.kind) {
            GenKind.BLACK, GenKind.NOISE -> cv.drawColor(0xFF000000.toInt())
            GenKind.SOLID -> cv.drawColor(s.color1)
            GenKind.GRADIENT -> {
                p.shader = LinearGradient(0f, 0f, w * 0.35f, h.toFloat(), s.color1, s.color2, Shader.TileMode.CLAMP)
                cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
                p.shader = RadialGradient(w * 0.5f, h * 0.45f, w * 0.75f, 0x00000000, 0x55000000, Shader.TileMode.CLAMP)
                cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            }
            GenKind.BARS -> {
                // Generic 75 % colour bars with a darker strip below.
                val cols = listOf(0xFFBFBFBF, 0xFFBFBF00, 0xFF00BFBF, 0xFF00BF00, 0xFFBF00BF, 0xFFBF0000, 0xFF0000BF).map { it.toInt() }
                val bw = w / 7f
                cols.forEachIndexed { i, c -> p.color = c; cv.drawRect(i * bw, 0f, (i + 1) * bw, h * 0.72f, p) }
                val low = listOf(0xFF0000BF, 0xFF131313, 0xFFBF00BF, 0xFF131313, 0xFF00BFBF, 0xFF131313, 0xFFBFBFBF).map { it.toInt() }
                low.forEachIndexed { i, c -> p.color = c; cv.drawRect(i * bw, h * 0.72f, (i + 1) * bw, h * 0.8f, p) }
                val bot = listOf(0xFF00214C, 0xFFFFFFFF, 0xFF32006A, 0xFF131313, 0xFF090909, 0xFF131313, 0xFF1D1D1D).map { it.toInt() }
                bot.forEachIndexed { i, c -> p.color = c; cv.drawRect(i * bw, h * 0.8f, (i + 1) * bw, h.toFloat(), p) }
            }
            GenKind.COUNTDOWN -> {
                if (digit == null || digit <= 1) { cv.drawColor(0xFF000000.toInt()); return b }
                cv.drawColor(0xFF8A8A8A.toInt())
                val cx = w / 2f; val cy = h / 2f; val r = h * 0.42f
                p.style = Paint.Style.STROKE; p.color = 0xFFEDEDED.toInt(); p.strokeWidth = h * 0.012f
                cv.drawCircle(cx, cy, r, p); cv.drawCircle(cx, cy, r * 0.82f, p)
                p.strokeWidth = h * 0.006f; p.color = 0xFF2A2A2A.toInt()
                cv.drawLine(0f, cy, w.toFloat(), cy, p); cv.drawLine(cx, 0f, cx, h.toFloat(), p)
                p.style = Paint.Style.FILL; p.color = 0xFF111111.toInt()
                p.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                p.textSize = h * 0.62f; p.textAlign = Paint.Align.CENTER
                val fm = p.fontMetrics
                cv.drawText(digit.toString(), cx, cy - (fm.ascent + fm.descent) / 2f, p)
            }
        }
        return b
    }

    private fun save(context: Context, b: Bitmap, name: String): Uri {
        val f = File(context.cacheDir, "gen_$name.png")
        f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return Uri.fromFile(f)
    }

    /**
     * Renders [s] to an .mp4 in the app's files (with a silent sound track, so it joins any edit) and
     * calls [onDone] on the main thread with the file, or an error. Returns a cancel function.
     */
    fun make(context: Context, s: GenSpec, onProgress: (Int) -> Unit, onDone: (Uri?, String?) -> Unit): () -> Unit {
        val main = Handler(Looper.getMainLooper())
        val dir = File(context.filesDir, "generated").apply { mkdirs() }
        val out = File(dir, "${s.kind.name.lowercase()}_${System.currentTimeMillis()}.mp4")
        val w = even(s.width); val h = even(s.height)
        fun item(uri: Uri, ms: Long, mode: Int): EditedMediaItem =
            EditedMediaItem.Builder(MediaItem.Builder().setUri(uri).setImageDurationMs(ms).build())
                .setFrameRate(s.fps)
                .setEffects(Effects(emptyList(), buildList {
                    add(Presentation.createForWidthAndHeight(w, h, Presentation.LAYOUT_STRETCH_TO_FIT))
                    if (mode != 0) add(GenEffect(mode))
                }))
                .build()
        val items = try {
            if (s.kind == GenKind.COUNTDOWN) {
                val n = s.seconds.coerceIn(3, 10)
                (n downTo 1).map { d -> item(save(context, still(s, d), "cd$d"), 1000L, if (d > 1) 2 else 0) }
            } else {
                listOf(item(save(context, still(s), s.kind.name), s.seconds.coerceIn(1, 600) * 1000L, if (s.kind == GenKind.NOISE) 1 else 0))
            }
        } catch (t: Throwable) { onDone(null, "Couldn't draw the generator: ${t.message}"); return {} }
        val seq = EditedMediaItemSequence.Builder(items).experimentalSetForceAudioTrack(true).build()
        val composition = Composition.Builder(seq).build()
        var done = false
        val tr = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) { done = true; onDone(Uri.fromFile(out), null) }
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    done = true; out.delete(); onDone(null, "Couldn't make the clip: ${exportException.errorCodeName}")
                }
            })
            .build()
        tr.start(composition, out.path)
        val holder = ProgressHolder()
        val tick = object : Runnable {
            override fun run() {
                if (done) return
                if (tr.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress)
                main.postDelayed(this, 200)
            }
        }
        main.postDelayed(tick, 200)
        return { if (!done) { done = true; try { tr.cancel() } catch (_: Throwable) {}; out.delete() } }
    }

    /**
     * A photo as a video clip of [seconds] (its own shape, long side at most 3840), so it can sit in
     * the media list and on the timeline like any clip. Saved in the app's files. Calls [onDone] on the
     * main thread with the clip, or an error.
     */
    fun photoClip(context: Context, photo: Uri, seconds: Int, onDone: (Uri?, String?) -> Unit) {
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try { context.contentResolver.openInputStream(photo)?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) } } catch (_: Throwable) {}
        var pw = opts.outWidth; var ph = opts.outHeight
        if (pw <= 0 || ph <= 0) { onDone(null, "Couldn't read the photo"); return }
        val rot = try {
            context.contentResolver.openInputStream(photo)?.use { androidx.exifinterface.media.ExifInterface(it).rotationDegrees } ?: 0
        } catch (_: Throwable) { 0 }
        if (rot == 90 || rot == 270) { val t = pw; pw = ph; ph = t }
        val k = minOf(1f, 3840f / maxOf(pw, ph))
        val w = even((pw * k).toInt()); val h = even((ph * k).toInt())
        val dir = File(context.filesDir, "generated").apply { mkdirs() }
        val out = File(dir, "AC_PHOTO_" + java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date()) + ".mp4")
        val item = EditedMediaItem.Builder(MediaItem.Builder().setUri(photo).setImageDurationMs(seconds.coerceIn(1, 60) * 1000L).build())
            .setFrameRate(30)
            .setEffects(Effects(emptyList(), listOf(Presentation.createForWidthAndHeight(w, h, Presentation.LAYOUT_SCALE_TO_FIT))))
            .build()
        val seq = EditedMediaItemSequence.Builder(listOf(item)).experimentalSetForceAudioTrack(true).build()
        Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) { onDone(Uri.fromFile(out), null) }
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    out.delete(); onDone(null, "Couldn't make a clip from the photo: ${exportException.errorCodeName}")
                }
            })
            .build()
            .start(Composition.Builder(seq).build(), out.path)
    }
}

/** Mode 1: animated TV noise. Mode 2: the countdown's clock sweep over the still. */
@UnstableApi
private class GenEffect(private val mode: Int) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = GenProgram(mode, useHdr)
}

@UnstableApi
private class GenProgram(private val mode: Int, useHdr: Boolean) : BaseGlShaderProgram(useHdr, 1) {
    private val program = try { GlProgram(VERTEX, FRAGMENT) } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    private var w = 1; private var h = 1
    init { program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE) }
    override fun configure(inputWidth: Int, inputHeight: Int): Size { w = inputWidth; h = inputHeight; return Size(inputWidth, inputHeight) }
    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            program.use()
            program.setSamplerTexIdUniform("uTex", inputTexId, 0)
            program.setFloatUniform("uMode", mode.toFloat())
            program.setFloatUniform("uTime", (presentationTimeUs % 1_000_000_000L) / 1e6f)
            program.setFloatsUniform("uRes", floatArrayOf(w.toFloat(), h.toFloat()))
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    }
    override fun release() { super.release(); try { program.delete() } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) } }

    companion object {
        private const val VERTEX = """
            attribute vec4 aFramePosition;
            varying vec2 vUv;
            void main() { gl_Position = aFramePosition; vUv = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5); }
        """
        private const val FRAGMENT = """
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            uniform sampler2D uTex;
            uniform float uMode;
            uniform float uTime;
            uniform vec2 uRes;
            varying vec2 vUv;
            float h21(vec2 p) { p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
            void main() {
                vec4 c = texture2D(uTex, vUv);
                if (uMode < 1.5) {
                    float fr = floor(uTime * 30.0);
                    vec2 q = floor(vUv * uRes / 2.0);
                    float n = h21(q + vec2(fr * 17.0, fr * 31.0));
                    float roll = 0.85 + 0.15 * sin(vUv.y * 8.0 + uTime * 9.0);
                    gl_FragColor = vec4(vec3(n * roll), 1.0);
                    return;
                }
                // Countdown sweep: the part of the dial the hand has passed is darker.
                vec2 p = (vUv - 0.5) * vec2(uRes.x / uRes.y, 1.0);
                float ang = atan(p.x, p.y);
                if (ang < 0.0) ang += 6.28318;
                float sweep = fract(uTime) * 6.28318;
                float shade = ang < sweep ? 0.62 : 1.0;
                float edge = 1.0 - smoothstep(0.0, 0.012, abs(ang - sweep) * length(p));
                vec3 col = c.rgb * shade;
                col = mix(col, vec3(0.05), edge * 0.9);
                gl_FragColor = vec4(col, 1.0);
            }
        """
    }
}
