package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.view.Surface
import androidx.annotation.OptIn
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.UnstableApi
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Optical-flow retiming, on the phone (AlcorCut Pro's own motion engine, as in the morph cut):
 *
 *  - Smooth slow motion: [factor]× more frames — new in-between frames drawn from the motion
 *    between each pair of real frames (optical-flow retiming). The new file keeps the clip's length and sound, so the clip's
 *    own speed (or ramp) then plays it back slowly without stutter.
 *  - Motion blur: each frame smeared along its own motion, like a real camera shutter
 *    ([shutter] = fraction of the frame time, 0.5 = 180°).
 *
 * The result is a new video file (H.264, high bitrate) of the clip's used range with its sound.
 */
@OptIn(UnstableApi::class)
object FlowRetime {
    class Stopped : RuntimeException("stopped")

    /** Size the motion is worked out at (long side): enough for smooth motion, fast on a phone. */
    private const val WORK = 512

    private fun key(c: Clip, factor: Int, shutter: Float) =
        listOf(c.uri.toString(), c.startMs, c.endMs, factor, (shutter * 100).roundToInt(), 2).hashCode().toUInt().toString(16)

    fun outFile(context: Context, c: Clip, factor: Int, shutter: Float) =
        File(File(context.filesDir, "edits").apply { mkdirs() }, "flow_${key(c, factor, shutter)}.mp4")

    /**
     * Makes the retimed / motion-blurred copy of [c]'s used range (blocking; call off the main thread).
     * Returns the file, or null when it couldn't be made.
     */
    fun make(context: Context, c: Clip, factor: Int, shutter: Float, maxSide: Int, onProgress: (Int) -> Unit, stop: () -> Boolean): File? {
        if (!MorphNative.available) return null
        val out = outFile(context, c, factor, shutter)
        if (out.exists() && out.length() > 4096) return out
        val tmp = File(out.path + ".part"); tmp.delete()
        val srcFps = sourceFps(context, c)
        val gapMs = 1000.0 / srcFps
        var enc: Writer? = null
        var prev: Pair<Long, Bitmap>? = null
        val total = (c.endMs - c.startMs).coerceAtLeast(1)
        var ok = false
        try {
            FrameReader.read(context, c.uri, c.startMs, c.endMs, maxSide, 0) { t, raw ->
                if (stop()) throw Stopped()
                val bmp = even(raw)
                val e = enc ?: Writer(context, c, tmp, bmp.width, bmp.height, (srcFps * factor).coerceAtMost(240.0)).also { enc = it }
                if (bmp.width != e.w || bmp.height != e.h) return@read true
                val p = prev
                if (p == null) e.begin(bmp) else e.pair(p.first, t, bmp, factor, shutter, gapMs)
                prev = t to bmp
                onProgress(((t - c.startMs) * 100 / total).toInt().coerceIn(0, 99))
                true
            }
            val e = enc
            val p = prev
            if (e != null && p != null && !stop()) {
                e.last(p.first, shutter)
                e.finish()
                ok = e.frames > 1
            }
        } catch (_: Stopped) {
        } catch (t: Throwable) {
            android.util.Log.w("FlowRetime", "failed: ${t.message}", t)
        } finally {
            try { enc?.release() } catch (_: Throwable) {}
        }
        if (!ok || stop()) { tmp.delete(); return null }
        if (!tmp.renameTo(out)) { tmp.delete(); return null }
        onProgress(100)
        return out
    }

    private fun even(b: Bitmap): Bitmap {
        val w = b.width / 2 * 2; val h = b.height / 2 * 2
        val c = if (w == b.width && h == b.height) b else Bitmap.createScaledBitmap(b, w, h, true)
        return if (c.config == Bitmap.Config.ARGB_8888) c else c.copy(Bitmap.Config.ARGB_8888, false)
    }

    private fun sourceFps(context: Context, c: Clip): Double {
        val ex = MediaExtractor()
        return try {
            ex.setDataSource(context, c.uri, null)
            val f = (0 until ex.trackCount).map { ex.getTrackFormat(it) }.firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
            val r = if (f != null && f.containsKey(MediaFormat.KEY_FRAME_RATE)) try { f.getInteger(MediaFormat.KEY_FRAME_RATE).toDouble() } catch (_: Throwable) { f.getFloat(MediaFormat.KEY_FRAME_RATE).toDouble() } else 30.0
            r.coerceIn(10.0, 240.0)
        } catch (_: Throwable) { 30.0 } finally { ex.release() }
    }

    /** True when two frames are a camera cut (no motion to follow): tiny pictures compared. */
    private fun isCut(a: Bitmap, b: Bitmap): Boolean {
        val sa = Bitmap.createScaledBitmap(a, 32, 18, true); val sb = Bitmap.createScaledBitmap(b, 32, 18, true)
        val pa = IntArray(32 * 18); val pb = IntArray(32 * 18)
        sa.getPixels(pa, 0, 32, 0, 0, 32, 18); sb.getPixels(pb, 0, 32, 0, 0, 32, 18)
        var d = 0f
        for (i in pa.indices) d += abs(((pa[i] shr 8) and 255) - ((pb[i] shr 8) and 255)) / 255f
        return d / pa.size > 0.3f
    }

    /** Encoder + GPU drawing + muxer for the new file. */
    /** Encoder + GPU drawing + muxer for a new file of [c]'s used range (with its sound). Also used by object removal. */
    internal class Writer(val context: Context, val c: Clip, val file: File, val w: Int, val h: Int, rate: Double) {
        private val codec: MediaCodec
        private val input: Surface
        private val dpy: EGLDisplay
        private val ctx: EGLContext
        private val surf: EGLSurface
        private val muxer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        private var videoTrack = -1
        private var audioTrack = -1
        private var audioFormat: MediaFormat? = null
        private var audioIndex = -1
        private var started = false
        private val info = MediaCodec.BufferInfo()
        var frames = 0; private set
        private var lastPts = -1L

        private val copy: GlProgram
        private val morph: GlProgram
        private val blur: GlProgram
        private var texA = 0
        private var motBuf: ByteBuffer? = null
        private var motTex = 0; private var occTex = 0; private var workW = 0; private var workH = 0

        init {
            // Encoder: H.264 if it takes this size, else HEVC.
            val fr = rate.roundToInt().coerceIn(24, 240)
            fun fmt(mime: String, r: Int) = MediaFormat.createVideoFormat(mime, w, h).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, (w.toLong() * h * fr * 0.14).toLong().coerceIn(6_000_000L, 120_000_000L).toInt())
                setInteger(MediaFormat.KEY_FRAME_RATE, r)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            var made: MediaCodec? = null
            loop@ for (mime in listOf(MediaFormat.MIMETYPE_VIDEO_AVC, MediaFormat.MIMETYPE_VIDEO_HEVC)) for (r in listOf(fr, 60, 30)) {
                val f = fmt(mime, r)
                val name = list.findEncoderForFormat(MediaFormat.createVideoFormat(mime, w, h)) ?: continue
                try { val m = MediaCodec.createByCodecName(name); m.configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); made = m; break@loop } catch (_: Throwable) {}
            }
            codec = made ?: throw IllegalStateException("No video encoder for ${w}x$h")
            input = codec.createInputSurface()
            codec.start()
            // EGL on the encoder's surface.
            dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val ver = IntArray(2); EGL14.eglInitialize(dpy, ver, 0, ver, 1)
            val attrs = intArrayOf(EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, 0x3142 /* EGL_RECORDABLE_ANDROID */, 1, EGL14.EGL_NONE)
            val cfg = arrayOfNulls<EGLConfig>(1); val n = IntArray(1)
            EGL14.eglChooseConfig(dpy, attrs, 0, cfg, 0, 1, n, 0)
            ctx = EGL14.eglCreateContext(dpy, cfg[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
            surf = EGL14.eglCreateWindowSurface(dpy, cfg[0], input, intArrayOf(EGL14.EGL_NONE), 0)
            EGL14.eglMakeCurrent(dpy, surf, surf, ctx)
            GLES20.glViewport(0, 0, w, h)
            copy = GlProgram(VERT, COPY_FRAG); morph = GlProgram(VERT, MORPH_FRAG); blur = GlProgram(VERT, BLUR_FRAG)
            for (p in listOf(copy, morph, blur)) p.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
            // The clip's sound, copied as it is.
            val ex = MediaExtractor()
            try {
                ex.setDataSource(context, c.uri, null)
                for (i in 0 until ex.trackCount) { val f = ex.getTrackFormat(i); if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { audioFormat = f; audioIndex = i; break } }
            } catch (_: Throwable) {} finally { ex.release() }
        }

        private fun upload(b: Bitmap): Int {
            val t = IntArray(1); GLES20.glGenTextures(1, t, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t[0])
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, b, 0)
            params(GLES20.GL_LINEAR)
            return t[0]
        }
        private fun params(filter: Int) {
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, filter)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, filter)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        }
        private fun raw(tw: Int, th: Int, data: ByteBuffer?, filter: Int, reuse: Int = 0): Int {
            val id = if (reuse != 0) reuse else IntArray(1).also { GLES20.glGenTextures(1, it, 0) }[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
            GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, tw, th, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, data)
            params(filter)
            return id
        }
        private fun del(t: Int) { if (t != 0) GLES20.glDeleteTextures(1, intArrayOf(t), 0) }

        /** Loads the motion (and occlusion) of an analysed pair at time [t] into textures. */
        private fun motion(d: MorphData, t: Float, withOcc: Boolean) {
            if (d.workW != workW || d.workH != workH) {
                del(motTex); del(occTex); motTex = 0; occTex = 0
                workW = d.workW; workH = d.workH
                motBuf = ByteBuffer.allocateDirect(workW * workH * 4).order(ByteOrder.nativeOrder())
            }
            val mb = motBuf!!
            if (withOcc) { mb.clear(); MorphNative.occlusion(d.handle, mb); mb.rewind(); occTex = raw(workW, workH, mb, GLES20.GL_LINEAR, occTex) }
            mb.clear(); MorphNative.motion(d.handle, t, mb); mb.rewind()
            motTex = raw(workW, workH, mb, GLES20.GL_NEAREST, motTex)
        }

        private fun present(ptsUs: Long) {
            // Strictly increasing times (the encoder drops anything else).
            val p = if (ptsUs <= lastPts) lastPts + 1 else ptsUs
            lastPts = p
            EGLExt.eglPresentationTimeANDROID(dpy, surf, p * 1000)
            EGL14.eglSwapBuffers(dpy, surf)
            frames++
            drain(false)
        }
        private fun pts(srcMs: Double) = ((srcMs - c.startMs) * 1000.0).toLong().coerceAtLeast(0L)

        private fun drawCopy(tex: Int) {
            copy.use(); copy.setSamplerTexIdUniform("uA", tex, 0); copy.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }
        private fun drawBlur(tex: Int, len: Float) {
            blur.use()
            blur.setSamplerTexIdUniform("uA", tex, 0); blur.setSamplerTexIdUniform("uMot", motTex, 1)
            blur.setFloatsUniform("uWork", floatArrayOf(workW.toFloat(), workH.toFloat())); blur.setFloatUniform("uLen", len)
            blur.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }
        private fun drawMorph(a: Int, b: Int, t: Float) {
            morph.use()
            morph.setSamplerTexIdUniform("uA", a, 0); morph.setSamplerTexIdUniform("uB", b, 1)
            morph.setSamplerTexIdUniform("uMot", motTex, 2); morph.setSamplerTexIdUniform("uOcc", occTex, 3)
            morph.setFloatsUniform("uWork", floatArrayOf(workW.toFloat(), workH.toFloat())); morph.setFloatUniform("uT", t)
            morph.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }

        /** One finished picture shown at source time [tMs]. */
        fun still(b: Bitmap, tMs: Long) {
            val t = upload(b); drawCopy(t); present(pts(tMs.toDouble())); del(t)
        }

        private var firstMs = -1L
        private var pending: Bitmap? = null
        fun begin(b: Bitmap) { texA = upload(b); pending = b }

        /** The real frame A (shown at [t0]) and the new frames between it and B (at [t1]). */
        fun pair(t0: Long, t1: Long, b: Bitmap, factor: Int, shutter: Float, gapMs: Double) {
            val a = pending ?: return
            if (firstMs < 0) firstMs = t0
            val texB = upload(b)
            val cut = isCut(a, b)
            val d = if (!cut && (factor > 1 || shutter > 0f)) MorphData.analyze(a, b, WORK) else null
            try {
                // Frame A (smeared along its motion for motion blur).
                if (d != null && shutter > 0f) { motion(d, 0f, false); drawBlur(texA, shutter) } else drawCopy(texA)
                present(pts(t0.toDouble()))
                if (factor > 1) {
                    val n = max(1, ((t1 - t0) / (gapMs / factor)).roundToInt())
                    for (k in 1 until n) {
                        val t = k.toFloat() / n
                        if (d != null) { motion(d, t, k == 1); drawMorph(texA, texB, t) } else drawCopy(if (t < 0.5f) texA else texB)
                        present(pts(t0 + (t1 - t0) * t.toDouble()))
                    }
                }
                // Motion blur needs A's motion for the last frame too: keep this pair's motion.
                lastMotion = if (shutter > 0f && d != null) true else lastMotion
            } finally { d?.release() }
            del(texA); texA = texB; pending = b
        }
        private var lastMotion = false

        fun last(t: Long, shutter: Float) {
            if (pending == null) return
            // The last frame has no next frame to follow: its blur reuses the last motion we had.
            if (shutter > 0f && lastMotion && motTex != 0) drawBlur(texA, shutter) else drawCopy(texA)
            present(pts(t.toDouble()))
        }

        private fun drain(end: Boolean) {
            if (end) codec.signalEndOfInputStream()
            while (true) {
                val i = codec.dequeueOutputBuffer(info, if (end) 10_000 else 0)
                if (i == MediaCodec.INFO_TRY_AGAIN_LATER) { if (!end) return else continue }
                if (i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    videoTrack = muxer.addTrack(codec.outputFormat)
                    audioFormat?.let { f -> audioTrack = try { muxer.addTrack(f) } catch (_: Throwable) { -1 } }
                    muxer.start(); started = true
                    continue
                }
                if (i < 0) continue
                val buf = codec.getOutputBuffer(i)
                if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                if (info.size > 0 && started && buf != null) { buf.position(info.offset); buf.limit(info.offset + info.size); muxer.writeSampleData(videoTrack, buf, info) }
                codec.releaseOutputBuffer(i, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
            }
        }

        fun finish() {
            drain(true)
            if (started && audioTrack >= 0 && audioIndex >= 0) {
                val ex = MediaExtractor()
                try {
                    ex.setDataSource(context, c.uri, null)
                    ex.selectTrack(audioIndex)
                    ex.seekTo(c.startMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                    val buf = ByteBuffer.allocateDirect(1 shl 20)
                    val ai = MediaCodec.BufferInfo()
                    while (true) {
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0) break
                        val tUs = ex.sampleTime
                        if (tUs > c.endMs * 1000) break
                        if (tUs >= c.startMs * 1000) {
                            ai.set(0, n, tUs - c.startMs * 1000, if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                            muxer.writeSampleData(audioTrack, buf, ai)
                        }
                        ex.advance()
                    }
                } catch (_: Throwable) {} finally { ex.release() }
            }
            if (started) muxer.stop()
        }

        fun release() {
            try { del(texA); del(motTex); del(occTex) } catch (_: Throwable) {}
            try { copy.delete(); morph.delete(); blur.delete() } catch (_: Throwable) {}
            EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(dpy, surf)
            EGL14.eglDestroyContext(dpy, ctx)
            try { input.release() } catch (_: Throwable) {}
            try { codec.stop() } catch (_: Throwable) {}
            try { codec.release() } catch (_: Throwable) {}
            try { muxer.release() } catch (_: Throwable) {}
        }
    }

    private const val VERT = """
        attribute vec4 aFramePosition;
        varying vec2 vUv;
        void main() { gl_Position = aFramePosition; vUv = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5); }
    """
    // Frames are uploaded as pictures (top row first), so every lookup flips y.
    private const val COPY_FRAG = """
        precision mediump float;
        uniform sampler2D uA;
        varying vec2 vUv;
        void main() { gl_FragColor = vec4(texture2D(uA, vec2(vUv.x, 1.0 - vUv.y)).rgb, 1.0); }
    """
    private const val MOT = """
        uniform sampler2D uMot;
        uniform vec2 uWork;
        vec2 dec(vec4 c) {
            vec4 b = floor(c * 255.0 + 0.5);
            return vec2((b.r * 256.0 + b.g - 32768.0) / 32.0, (b.b * 256.0 + b.a - 32768.0) / 32.0);
        }
        vec2 mot(vec2 p) {
            vec2 q = p - 0.5;
            vec2 f = fract(q);
            vec2 i0 = clamp(floor(q), vec2(0.0), uWork - 1.0);
            vec2 i1 = clamp(floor(q) + 1.0, vec2(0.0), uWork - 1.0);
            vec2 a = dec(texture2D(uMot, (vec2(i0.x, i0.y) + 0.5) / uWork));
            vec2 b = dec(texture2D(uMot, (vec2(i1.x, i0.y) + 0.5) / uWork));
            vec2 c = dec(texture2D(uMot, (vec2(i0.x, i1.y) + 0.5) / uWork));
            vec2 d = dec(texture2D(uMot, (vec2(i1.x, i1.y) + 0.5) / uWork));
            return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
        }
    """
    private const val HEAD = """
        #ifdef GL_FRAGMENT_PRECISION_HIGH
        precision highp float;
        #else
        precision mediump float;
        #endif
        varying vec2 vUv;
    """
    private const val MORPH_FRAG = HEAD + MOT + """
        uniform sampler2D uA;
        uniform sampler2D uB;
        uniform sampler2D uOcc;
        uniform float uT;
        float inside(vec2 p) { return step(0.0, p.x) * step(p.x, 1.0) * step(0.0, p.y) * step(p.y, 1.0); }
        void main() {
            vec2 iuv = vec2(vUv.x, 1.0 - vUv.y);
            vec2 m = mot(iuv * uWork) / uWork;
            vec2 pa = iuv - uT * m;
            vec2 pb = iuv + (1.0 - uT) * m;
            vec4 ca = texture2D(uA, clamp(pa, 0.0, 1.0));
            vec4 cb = texture2D(uB, clamp(pb, 0.0, 1.0));
            float oa = texture2D(uOcc, clamp(pa, 0.0, 1.0)).r;
            float ob = texture2D(uOcc, clamp(pb, 0.0, 1.0)).g;
            float wa = (1.0 - uT) * (1.0 - ob) * inside(pa);
            float wb = uT * (1.0 - oa) * inside(pb);
            if (wa + wb < 0.0001) { wa = 1.0 - uT; wb = uT; }
            gl_FragColor = vec4((ca.rgb * wa + cb.rgb * wb) / (wa + wb), 1.0);
        }
    """
    /** Motion blur: the frame averaged along its own motion, centred on the frame time (a shutter). */
    private const val BLUR_FRAG = HEAD + MOT + """
        uniform sampler2D uA;
        uniform float uLen;
        void main() {
            vec2 iuv = vec2(vUv.x, 1.0 - vUv.y);
            vec2 m = mot(iuv * uWork) / uWork * uLen;
            vec3 s = vec3(0.0);
            for (int i = 0; i < 24; i++) {
                float f = float(i) / 23.0 - 0.5;
                s += texture2D(uA, clamp(iuv + m * f, 0.0, 1.0)).rgb;
            }
            gl_FragColor = vec4(s / 24.0, 1.0);
        }
    """
}
