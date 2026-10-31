package com.dronedirector.veyrocutpro.windows

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.GlUtil
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.Transformer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.lwjgl.opengles.GLES20 as G

/**
 * Renders an edit ([Composition]: sequences of clips with their effects, composited, with the
 * project's global effects) to an MP4 file on Windows, frame by frame: pictures are decoded with
 * Windows' media engine, the phone's effect shaders run on the graphics engine ([GlEnv]), the sound
 * of every sequence goes through its sound steps and is mixed, and Windows' encoders write H.264 /
 * HEVC + AAC. Own code.
 */
class WinExport(
    private val context: Context, private val transformer: Transformer, private val composition: Composition,
    private val path: String, private val listeners: List<Transformer.Listener>,
) {
    @Volatile var progress = 0; private set
    @Volatile var done = false; private set
    @Volatile private var cancelled = false
    private var thread: Thread? = null

    fun start() { thread = Thread({ run() }, "export").apply { isDaemon = true; start() } }
    fun cancel() { cancelled = true }

    private fun main(block: () -> Unit) = javax.swing.SwingUtilities.invokeLater(block)

    // ---------------------------------------------------------------- the plan

    /** One item of a sequence placed on the output timeline. */
    private inner class Part(val item: EditedMediaItem, val outStartUs: Long, val outDurUs: Long, val srcStartUs: Long, val srcEndUs: Long) {
        val file: File? = item.mediaItem.localConfiguration?.uri?.let { try { MediaFiles.local(it) } catch (_: Throwable) { null } }
        val info: MediaInfo? = file?.let { VideoFile.info(it) }
        val isImage = item.mediaItem.imageDurationMs != C.TIME_UNSET || (file != null && info?.hasVideo != true && info?.hasAudio != true && file.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp", "heic", "bmp", "gif", "tif", "tiff"))
        val isGap = file == null
        val speed = item.speedProvider
        /** Source time (µs from the clip's start) for an output offset, along the speed curve. */
        private val map: LongArray by lazy {
            val sp = speed ?: return@lazy LongArray(0)
            // Output offset → source offset, in 10 ms steps of output time.
            val out = ArrayList<Long>(); var src = 0.0; var o = 0L
            while (o <= outDurUs + 10_000) { out += src.toLong(); src += 10_000.0 * sp.getSpeed(src.toLong()).coerceIn(0.01f, 100f); o += 10_000 }
            out.toLongArray()
        }
        fun srcAt(offUs: Long): Long {
            if (speed == null) return srcStartUs + offUs
            val m = map; if (m.isEmpty()) return srcStartUs + offUs
            val i = (offUs / 10_000).toInt().coerceIn(0, m.size - 1)
            return srcStartUs + m[i]
        }
        fun speedAt(offUs: Long): Float = speed?.getSpeed(srcAt(offUs) - srcStartUs)?.coerceIn(0.01f, 100f) ?: 1f
    }

    private fun outDuration(it: EditedMediaItem, srcDurUs: Long): Long {
        val sp = it.speedProvider ?: return srcDurUs
        var src = 0.0; var out = 0L
        while (src < srcDurUs) { src += 10_000.0 * sp.getSpeed(src.toLong()).coerceIn(0.01f, 100f); out += 10_000 }
        return out
    }

    private fun plan(): List<List<Part>> = composition.sequences.map { seq ->
        var t = 0L
        seq.editedMediaItems.map { it ->
            val mi = it.mediaItem
            val clip = mi.clippingConfiguration
            val f = mi.localConfiguration?.uri?.let { u -> try { MediaFiles.local(u) } catch (_: Throwable) { null } }
            val info = f?.let { VideoFile.info(it) }
            val srcStart = clip.startPositionMs * 1000
            val fileDur = info?.durationUs ?: 0L
            val srcEnd = when {
                mi.imageDurationMs != C.TIME_UNSET -> srcStart + mi.imageDurationMs * 1000
                f == null -> srcStart + (if (it.durationUs != C.TIME_UNSET) it.durationUs else 0L)
                clip.endPositionMs == C.TIME_END_OF_SOURCE -> if (fileDur > 0) fileDur else srcStart + (if (it.durationUs != C.TIME_UNSET) it.durationUs else 5_000_000L)
                else -> if (fileDur > 0) minOf(clip.endPositionMs * 1000, fileDur) else clip.endPositionMs * 1000
            }
            val outDur = outDuration(it, (srcEnd - srcStart).coerceAtLeast(0))
            Part(it, t, outDur, srcStart, srcEnd).also { t += outDur }
        }
    }

    // ---------------------------------------------------------------- run

    private fun run() {
        val started = System.currentTimeMillis()
        var rec: Encoder? = null
        val parts = plan()
        try {
            val totalUs = parts.maxOfOrNull { s -> s.lastOrNull()?.let { it.outStartUs + it.outDurUs } ?: 0L } ?: 0L
            if (totalUs <= 0L) throw ExportException("Nothing to export", null, ExportException.ERROR_CODE_FAILED_RUNTIME_CHECK)
            val anyVideo = !transformer.removeVideo && parts.flatten().any { !it.item.removeVideo && !it.isGap && (it.isImage || it.info?.hasVideo == true) }
            val anyAudio = !transformer.removeAudio && parts.flatten().any { !it.item.removeAudio && it.info?.hasAudio == true } || composition.forceAudioTrack
            val fps = (composition.effects.videoEffects.filterIsInstance<FrameDropEffect>().firstOrNull()?.targetFps
                ?: parts.flatten().firstOrNull { it.info?.hasVideo == true }?.info?.fps ?: 30f).coerceIn(1f, 240f)
            if (anyVideo && !GlEnv.ok) throw ExportException(driverNote(GlEnv.problem), null, ExportException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED)
            val video = if (anyVideo) GlEnv.run { VideoPipe(parts) } else null
            val (w, h) = video?.size ?: (0 to 0)
            val hevc = transformer.videoMime == androidx.media3.common.MimeTypes.VIDEO_H265
            val br = transformer.encoder?.settings?.bitrate?.takeIf { it > 0 } ?: (w.toLong() * h * fps / 5).toInt().coerceIn(2_000_000, 120_000_000)
            File(path).parentFile?.mkdirs()
            rec = Encoder.open(path, w, h, fps, br, hevc, anyAudio)
            val audio = if (anyAudio) AudioMix(parts) else null
            val frameUs = 1_000_000.0 / fps
            var n = 0L
            var frames = 0
            var audioUs = 0L
            while (!cancelled) {
                val t = (n * frameUs).toLong()
                if (t >= totalUs) break
                if (video != null) {
                    val px = GlEnv.run { video.render(t) }
                    rec.video(px, t, frameUs.toLong())
                    frames++
                }
                // Sound up to the end of this frame.
                if (audio != null) {
                    val until = minOf(totalUs, (t + frameUs).toLong())
                    while (audioUs < until) {
                        val chunkUs = minOf(until - audioUs, 100_000L)
                        val pcm = audio.render(audioUs, chunkUs)
                        rec.audio(pcm, audioUs, chunkUs)
                        audioUs += chunkUs
                    }
                }
                n++
                progress = (t * 100 / totalUs).toInt().coerceIn(0, 99)
                if (video == null && audio != null) { while (audioUs < totalUs && !cancelled) { val c = minOf(totalUs - audioUs, 100_000L); val pcm = audio.render(audioUs, c); rec.audio(pcm, audioUs, c); audioUs += c; progress = (audioUs * 100 / totalUs).toInt().coerceIn(0, 99) }; break }
            }
            video?.let { v -> GlEnv.run { v.release() } }
            audio?.release()
            val r0 = rec; rec = null
            r0.close()
            if (cancelled) { File(path).delete(); done = true; return }
            progress = 100; done = true
            val r = Transformer.result(totalUs / 1000, File(path).length(), w, h, frames, null)
            main { listeners.forEach { it.onCompleted(composition, r) } }
        } catch (t: Throwable) {
            try { rec?.close() } catch (_: Throwable) {}
            done = true
            if (cancelled) return
            val e = t as? ExportException ?: ExportException(t.message ?: t.toString(), t, ExportException.ERROR_CODE_UNSPECIFIED)
            com.dronedirector.veyrocutpro.ui.AppLog.w("Export failed: ${e.message}")
            main { listeners.forEach { it.onError(composition, Transformer.result(0, 0, 0, 0, 0, e), e) } }
        } finally {
            com.dronedirector.veyrocutpro.ui.AppLog.w("Export took ${(System.currentTimeMillis() - started) / 1000} s")
        }
    }

    /** The "update your graphics driver" note, only for a graphics engine that didn't start. */
    private fun driverNote(p: String?) = "The graphics engine couldn't start on this PC (${p ?: "unknown reason"}). Updating the graphics driver usually fixes this."

    // ---------------------------------------------------------------- pictures

    private inner class Source(val part: Part) {
        private var reader: VideoFile? = null
        private var cur: VideoFile.Frame? = null
        private var next: VideoFile.Frame? = null
        private var image: Triple<IntArray, Int, Int>? = null
        var programs: List<GlShaderProgram>? = null

        /** The picture at source time [us] (ARGB, top row first). */
        fun frameAt(us: Long, maxSide: Int): Triple<IntArray, Int, Int>? {
            if (part.isImage) {
                image?.let { return it }
                val b = android.graphics.BitmapFactory.decodeFile(part.file?.path ?: return null) ?: return null
                val k = minOf(1f, maxSide.toFloat() / maxOf(b.width, b.height))
                val s = if (k < 1f) android.graphics.Bitmap.createScaledBitmap(b, (b.width * k).toInt().coerceAtLeast(2), (b.height * k).toInt().coerceAtLeast(2), true) else b
                val px = IntArray(s.width * s.height); s.getPixels(px, 0, s.width, 0, 0, s.width, s.height)
                return Triple(px, s.width, s.height).also { image = it }
            }
            val f = part.file ?: return null
            if (part.info?.hasVideo != true) return null
            val r = reader ?: VideoFile(f, maxSide, maxSide).also { reader = it; it.seek(us); cur = null; next = null }
            // Going back (or far ahead): start again from the key frame before it.
            val c0 = cur
            if (c0 != null && (us < c0.timeUs - 1000 || us > c0.timeUs + 2_000_000)) { r.seek(us); cur = null; next = null }
            while (true) {
                val nx = next ?: r.next()?.let { fr -> VideoFile.Frame(fr.px.copyOf(fr.width * fr.height), fr.width, fr.height, fr.timeUs) }
                if (nx == null) break
                if (cur == null || nx.timeUs <= us + 500) { cur = nx; next = null } else { next = nx; break }
            }
            val c = cur ?: return null
            val rot = part.info.rotation
            return if (rot == 0) Triple(c.px, c.width, c.height) else rotate(c.px, c.width, c.height, rot)
        }
        fun release() { reader?.close(); reader = null; programs?.forEach { try { it.release() } catch (_: Throwable) {} }; programs = null }
    }

    private fun rotate(px: IntArray, w: Int, h: Int, deg: Int): Triple<IntArray, Int, Int> {
        val out = IntArray(w * h)
        return when (deg) {
            90 -> { for (y in 0 until h) for (x in 0 until w) out[x * h + (h - 1 - y)] = px[y * w + x]; Triple(out, h, w) }
            270 -> { for (y in 0 until h) for (x in 0 until w) out[(w - 1 - x) * h + y] = px[y * w + x]; Triple(out, h, w) }
            else -> { for (i in px.indices) out[px.size - 1 - i] = px[i]; Triple(out, w, h) }
        }
    }

    /** All picture work, on the GL thread. */
    private inner class VideoPipe(val parts: List<List<Part>>) {
        val globalPrograms: List<GlShaderProgram>
        val size: Pair<Int, Int>
        private val sources = HashMap<Part, Source>()
        private val upTex = IntArray(parts.size)
        private var compTex = 0; private var compFbo = 0; private var cw = 0; private var ch = 0
        private val drawProg = androidx.media3.common.util.GlProgram(
            "attribute vec4 aFramePosition; uniform mat4 uMatrix; varying vec2 vUv; void main() { gl_Position = uMatrix * aFramePosition; vUv = aFramePosition.xy * 0.5 + 0.5; }",
            "precision highp float; uniform sampler2D uTex; uniform float uA; varying vec2 vUv; void main() { gl_FragColor = texture2D(uTex, vUv) * uA; }",
        ).apply { setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), 4) }
        private var readW = 0; private var readH = 0
        private var readBuf: ByteBuffer? = null
        private var outPx = IntArray(0)
        /** The size the source pictures are decoded at (never more than the output needs). */
        private val maxSide: Int

        init {
            val pres = composition.effects.videoEffects.filterIsInstance<Presentation>().lastOrNull()
                ?: parts.flatten().flatMap { it.item.effects.videoEffects }.filterIsInstance<Presentation>().lastOrNull()
            val first = parts.flatten().firstOrNull { it.info?.hasVideo == true }?.info
            val fw0 = first?.let { if (it.rotation == 90 || it.rotation == 270) it.height else it.width } ?: 1920
            val fh0 = first?.let { if (it.rotation == 90 || it.rotation == 270) it.width else it.height } ?: 1080
            var w = if (pres != null && pres.width > 0) pres.width else if (pres != null && pres.height > 0) Math.round(pres.height * fw0.toFloat() / fh0) else fw0
            var h = if (pres != null && pres.height > 0) pres.height else fh0
            w = w and 1.inv(); h = h and 1.inv()
            size = w.coerceAtLeast(2) to h.coerceAtLeast(2)
            maxSide = (maxOf(size.first, size.second) * 1.25f).toInt().coerceAtLeast(320)
            globalPrograms = composition.effects.videoEffects.filterIsInstance<GlEffect>().filter { !it.isNoOp(size.first, size.second) }.map { it.toGlShaderProgram(context, false) }
            for (i in upTex.indices) upTex[i] = G.glGenTextures()
        }

        private val bgra = (G.glGetString(G.GL_EXTENSIONS) ?: "").let { "GL_EXT_texture_format_BGRA8888" in it && "GL_EXT_read_format_bgra" in it }
        private val stage = IntArray(parts.size); private val upFbo = IntArray(parts.size); private val upW = IntArray(parts.size); private val upH = IntArray(parts.size)
        private var upBuf: java.nio.IntBuffer? = null
        private val flipProg = androidx.media3.common.util.GlProgram(
            "attribute vec4 aFramePosition; varying vec2 vUv; void main() { gl_Position = aFramePosition; vUv = vec2(aFramePosition.x * 0.5 + 0.5, 0.5 - aFramePosition.y * 0.5); }",
            "precision highp float; uniform sampler2D uTex; varying vec2 vUv; void main() { gl_FragColor = vec4(texture2D(uTex, vUv).rgb, 1.0); }",
        ).apply { setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), 4) }

        private fun upload(i: Int, px: IntArray, w: Int, h: Int): Int {
            if (bgra) {
                // The picture as it is (ARGB = BGRA bytes), then turned the right way up on the graphics card.
                if (upW[i] != w || upH[i] != h || upFbo[i] == 0) {
                    GlUtil.deleteFbo(upFbo[i]); GlUtil.deleteTexture(upTex[i]); GlUtil.deleteTexture(stage[i])
                    upTex[i] = GlUtil.createTexture(w, h, false); upFbo[i] = GlUtil.createFboForTexture(upTex[i])
                    stage[i] = G.glGenTextures(); upW[i] = w; upH[i] = h
                }
                val n = w * h
                val b = upBuf?.takeIf { it.capacity() >= n } ?: ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder()).asIntBuffer().also { upBuf = it }
                b.clear(); b.put(px, 0, n); b.flip()
                GlUtil.bindTexture(G.GL_TEXTURE_2D, stage[i])
                G.glPixelStorei(G.GL_UNPACK_ALIGNMENT, 4)
                G.glTexImage2D(G.GL_TEXTURE_2D, 0, 0x80E1, w, h, 0, 0x80E1, G.GL_UNSIGNED_BYTE, b)
                GlUtil.focusFramebufferUsingCurrentContext(upFbo[i], w, h)
                flipProg.use(); flipProg.setSamplerTexIdUniform("uTex", stage[i], 0); flipProg.bindAttributesAndUniforms()
                G.glDrawArrays(G.GL_TRIANGLE_STRIP, 0, 4)
                return upTex[i]
            }
            // Bottom row first (GL's way), RGBA.
            val b = ByteBuffer.allocateDirect(w * h * 4)
            for (y in h - 1 downTo 0) { val row = y * w; for (x in 0 until w) { val c = px[row + x]; b.put((c shr 16).toByte()); b.put((c shr 8).toByte()); b.put(c.toByte()); b.put(-1) } }
            b.flip()
            GlUtil.bindTexture(G.GL_TEXTURE_2D, upTex[i])
            G.glPixelStorei(G.GL_UNPACK_ALIGNMENT, 1)
            G.glTexImage2D(G.GL_TEXTURE_2D, 0, G.GL_RGBA, w, h, 0, G.GL_RGBA, G.GL_UNSIGNED_BYTE, b)
            return upTex[i]
        }

        /** One sequence's picture at output time [t] after its clip's effects, or null when it shows nothing. */
        private fun sequenceFrame(i: Int, t: Long): Triple<Int, Int, Int>? {
            val part = parts[i].firstOrNull { t >= it.outStartUs && t < it.outStartUs + it.outDurUs } ?: return null
            if (part.isGap || part.item.removeVideo) return null
            // Clips that are over: their readers and shader programs are let go.
            for (p in parts[i]) if (p !== part && p.outStartUs + p.outDurUs <= t) sources.remove(p)?.release()
            val s = sources.getOrPut(part) { Source(part) }
            val off = t - part.outStartUs
            val (px, w, h) = s.frameAt(part.srcAt(off), maxSide) ?: return null
            var cur = Triple(upload(i, px, w, h), w, h)
            val progs = s.programs ?: part.item.effects.videoEffects.filterIsInstance<GlEffect>().filter { !it.isNoOp(w, h) }
                .map { it.toGlShaderProgram(context, false) }.also { s.programs = it }
            for (p in progs) cur = p.process(cur.first, cur.second, cur.third, t)
            return cur
        }

        fun render(t: Long): IntArray {
            val frames = parts.indices.map { sequenceFrame(it, t) }
            var cur: Triple<Int, Int, Int>
            if (parts.size == 1) cur = frames[0] ?: blank()
            else {
                val sizes = frames.mapIndexed { k, f -> androidx.media3.common.util.Size(f?.second ?: size.first, f?.third ?: size.second) }
                val os = composition.videoCompositorSettings.getOutputSize(sizes)
                ensureComp(os.width, os.height)
                GlUtil.focusFramebufferUsingCurrentContext(compFbo, cw, ch)
                G.glClearColor(0f, 0f, 0f, 1f); G.glClear(G.GL_COLOR_BUFFER_BIT)
                G.glEnable(G.GL_BLEND); G.glBlendFunc(G.GL_ONE, G.GL_ONE_MINUS_SRC_ALPHA)
                // The first input is on top: drawn last.
                for (k in frames.indices.reversed()) {
                    val f = frames[k] ?: continue
                    val s = composition.videoCompositorSettings.getOverlaySettings(k, t)
                    if (s.alphaScale <= 0f) continue
                    drawProg.use()
                    drawProg.setSamplerTexIdUniform("uTex", f.first, 0)
                    drawProg.setFloatUniform("uA", s.alphaScale)
                    drawProg.setFloatsUniform("uMatrix", overlay(s, f.second.toFloat() / cw, f.third.toFloat() / ch))
                    drawProg.bindAttributesAndUniforms()
                    G.glDrawArrays(G.GL_TRIANGLE_STRIP, 0, 4)
                }
                G.glDisable(G.GL_BLEND)
                cur = Triple(compTex, cw, ch)
            }
            for (p in globalPrograms) cur = p.process(cur.first, cur.second, cur.third, t)
            return read(cur)
        }

        private fun overlay(s: androidx.media3.common.OverlaySettings, fw: Float, fh: Float): FloatArray {
            val sx = fw * s.scale.first; val sy = fh * s.scale.second
            val tx = s.backgroundFrameAnchor.first - s.overlayFrameAnchor.first * sx; val ty = s.backgroundFrameAnchor.second - s.overlayFrameAnchor.second * sy
            val r = Math.toRadians(s.rotationDegrees.toDouble()); val c = Math.cos(r).toFloat(); val sn = Math.sin(r).toFloat()
            return floatArrayOf(sx * c, sx * sn, 0f, 0f, -sy * sn, sy * c, 0f, 0f, 0f, 0f, 1f, 0f, tx, ty, 0f, 1f)
        }

        private var blankTex = 0
        private fun blank(): Triple<Int, Int, Int> {
            if (blankTex == 0) { blankTex = GlUtil.createTexture(2, 2, false); val f = GlUtil.createFboForTexture(blankTex); G.glClearColor(0f, 0f, 0f, 1f); G.glClear(G.GL_COLOR_BUFFER_BIT); GlUtil.deleteFbo(f) }
            return Triple(blankTex, 2, 2)
        }

        private fun ensureComp(w: Int, h: Int) {
            if (w == cw && h == ch && compTex != 0) return
            GlUtil.deleteFbo(compFbo); GlUtil.deleteTexture(compTex)
            cw = w; ch = h; compTex = GlUtil.createTexture(w, h, false); compFbo = GlUtil.createFboForTexture(compTex)
        }

        /** The final picture at the output size, as ARGB (top row first). */
        private fun read(cur: Triple<Int, Int, Int>): IntArray {
            var (tex, w, h) = cur
            val (ow, oh) = size
            if (w != ow || h != oh) {
                // To the output size (fit) when the last step didn't already.
                ensureComp(ow, oh)
                GlUtil.focusFramebufferUsingCurrentContext(compFbo, ow, oh)
                G.glClearColor(0f, 0f, 0f, 1f); G.glClear(G.GL_COLOR_BUFFER_BIT)
                val a = w.toFloat() / h; val oa = ow.toFloat() / oh
                val m = GlUtil.create4x4IdentityMatrix(); if (a > oa) m[5] = oa / a else m[0] = a / oa
                drawProg.use(); drawProg.setSamplerTexIdUniform("uTex", tex, 0); drawProg.setFloatUniform("uA", 1f); drawProg.setFloatsUniform("uMatrix", m)
                drawProg.bindAttributesAndUniforms(); G.glDrawArrays(G.GL_TRIANGLE_STRIP, 0, 4)
                tex = compTex; w = ow; h = oh
            } else {
                val fbo = GlUtil.createFboForTexture(tex)
                GlUtil.focusFramebufferUsingCurrentContext(fbo, w, h)
                readPixels(w, h)
                GlUtil.deleteFbo(fbo)
                return outPx
            }
            readPixels(w, h)
            return outPx
        }
        private fun readPixels(w: Int, h: Int) {
            if (readW != w || readH != h) { readBuf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder()); outPx = IntArray(w * h); readW = w; readH = h }
            val b = readBuf!!; b.clear()
            if (bgra) {
                G.glReadPixels(0, 0, w, h, 0x80E1, G.GL_UNSIGNED_BYTE, b)
                val ib = b.asIntBuffer()
                for (y in 0 until h) { ib.position((h - 1 - y) * w); ib.get(outPx, y * w, w) }
                return
            }
            G.glReadPixels(0, 0, w, h, G.GL_RGBA, G.GL_UNSIGNED_BYTE, b)
            for (y in 0 until h) {
                val src = (h - 1 - y) * w * 4; val dst = y * w
                for (x in 0 until w) { val i = src + x * 4; outPx[dst + x] = (0xFF shl 24) or ((b.get(i).toInt() and 0xFF) shl 16) or ((b.get(i + 1).toInt() and 0xFF) shl 8) or (b.get(i + 2).toInt() and 0xFF) }
            }
        }

        fun release() {
            sources.values.forEach { it.release() }; sources.clear()
            globalPrograms.forEach { try { it.release() } catch (_: Throwable) {} }
            G.glDeleteTextures(upTex); G.glDeleteTextures(stage); upFbo.forEach { GlUtil.deleteFbo(it) }; flipProg.delete(); GlUtil.deleteFbo(compFbo); GlUtil.deleteTexture(compTex); GlUtil.deleteTexture(blankTex)
            drawProg.delete()
        }
    }

    // ---------------------------------------------------------------- sound

    /** Every sequence's sound through its steps, mixed and through the project's steps (48 kHz stereo, 16-bit). */
    private inner class AudioMix(val parts: List<List<Part>>) {
        private inner class Track(val seq: List<Part>) {
            var part: Part? = null
            var reader: AudioFile? = null
            var chain: List<AudioProcessor> = emptyList()
            var pending = ShortArray(0); var at = 0; var srcPos = 0.0
            var srcUs = 0L

            /** [n] stereo frames of this sequence's sound starting at output time [t]. */
            fun fill(t: Long, n: Int, out: IntArray) {
                var i = 0
                while (i < n) {
                    val tt = t + i * 1_000_000L / 48000
                    val p = seq.firstOrNull { tt >= it.outStartUs && tt < it.outStartUs + it.outDurUs }
                    if (p == null || p.isGap || p.item.removeAudio || transformer.removeAudio || p.info?.hasAudio != true) {
                        if (p !== part) open(null)
                        // Silence until this part ends (or the next one starts).
                        val endUs = p?.let { it.outStartUs + it.outDurUs } ?: seq.firstOrNull { it.outStartUs > tt }?.outStartUs ?: Long.MAX_VALUE
                        val k = (((endUs - tt) * 48000 + 999_999) / 1_000_000).coerceIn(1, (n - i).toLong()).toInt()
                        i += k; continue
                    }
                    if (p !== part) open(p)
                    val endUs = p.outStartUs + p.outDurUs
                    val k = ((((endUs - tt) * 48000) + 999_999) / 1_000_000).coerceIn(1, (n - i).toLong()).toInt()
                    val sp = p.speedAt(tt - p.outStartUs).toDouble()
                    var j = 0
                    while (j < k) {
                        if (at >= pending.size / 2) { if (!refill()) { j = k; break } ; continue }
                        out[(i + j) * 2] += pending[at * 2].toInt(); out[(i + j) * 2 + 1] += pending[at * 2 + 1].toInt()
                        j++
                        srcPos += sp
                        while (srcPos >= 1.0) { srcPos -= 1.0; at++ }
                    }
                    i += k
                }
            }

            private fun open(p: Part?) {
                reader?.close(); reader = null; part = p; pending = ShortArray(0); at = 0; srcPos = 0.0
                chain.forEach { try { it.reset() } catch (_: Throwable) {} }
                chain = emptyList()
                if (p == null) return
                reader = try { AudioFile(p.file!!).also { it.seek(p.srcStartUs) } } catch (_: Throwable) { null }
                srcUs = p.srcStartUs
                chain = p.item.effects.audioProcessors.filter { pr -> try { pr.configure(AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)); pr.flush(); pr.isActive() } catch (_: Throwable) { false } }
            }

            private fun refill(): Boolean {
                val r = reader ?: return false
                val p = part ?: return false
                while (true) {
                    val b = r.next() ?: return false
                    if (b.timeUs >= p.srcEndUs) return false
                    val skip = ((p.srcStartUs - b.timeUs) * 48000 / 1_000_000L).toInt().coerceIn(0, b.n)
                    if (skip >= b.n) continue
                    val endFrames = (((p.srcEndUs - b.timeUs) * 48000) / 1_000_000L).toInt().coerceIn(skip, b.n)
                    var pcm = b.pcm.copyOfRange(skip * 2, endFrames * 2)
                    if (chain.isNotEmpty()) pcm = runChain(chain, pcm)
                    pending = pcm; at = 0
                    if (pcm.isNotEmpty()) return true
                }
            }
            fun release() { open(null) }
        }

        private val tracks = parts.map { Track(it) }
        private val master = composition.effects.audioProcessors.filter { pr -> try { pr.configure(AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)); pr.flush(); pr.isActive() } catch (_: Throwable) { false } }

        fun render(t: Long, durUs: Long): ByteArray {
            val n = (durUs * 48000 / 1_000_000).toInt().coerceAtLeast(1)
            val mix = IntArray(n * 2)
            for (tr in tracks) tr.fill(t, n, mix)
            var pcm = ShortArray(n * 2) { mix[it].coerceIn(-32768, 32767).toShort() }
            if (master.isNotEmpty()) pcm = runChain(master, pcm)
            // Exactly n frames out (steps like resampling may give a few more or less).
            val out = ByteArray(n * 4)
            for (i in 0 until minOf(n * 2, pcm.size)) { out[i * 2] = pcm[i].toInt().toByte(); out[i * 2 + 1] = (pcm[i].toInt() shr 8).toByte() }
            return out
        }

        fun release() {
            tracks.forEach { it.release() }
            master.forEach { try { it.queueEndOfStream(); it.getOutput(); it.reset() } catch (_: Throwable) {} }
        }
    }

    private fun runChain(chain: List<AudioProcessor>, pcm: ShortArray): ShortArray {
        var buf = ByteBuffer.allocateDirect(pcm.size * 2).order(ByteOrder.nativeOrder()); buf.asShortBuffer().put(pcm); buf.limit(pcm.size * 2)
        for (p in chain) {
            p.queueInput(buf)
            val o = p.getOutput()
            buf = if (o.hasRemaining()) o else if (buf.hasRemaining()) buf else o
        }
        val out = ShortArray(buf.remaining() / 2)
        buf.order(ByteOrder.nativeOrder()).asShortBuffer().get(out)
        return out
    }
}

/** Writes the MP4: Windows' encoders (or, on a development machine, a separate ffmpeg). */
internal abstract class Encoder {
    abstract fun video(px: IntArray, tUs: Long, durUs: Long)
    abstract fun audio(pcm: ByteArray, tUs: Long, durUs: Long)
    abstract fun close()
    companion object {
        fun open(path: String, w: Int, h: Int, fps: Float, bitrate: Int, hevc: Boolean, audio: Boolean): Encoder {
            if (!MfNative.ok) {
                if (DevTools.windows) throw ExportException("Windows' media engine couldn't start — reinstall AlcorCut Pro", null, ExportException.ERROR_CODE_ENCODER_INIT_FAILED)
                return DevEncoder(path, w, h, fps, audio)
            }
            val rec = MfNative.recOpen(path, w, h, Math.round(fps * 1000), 1000, bitrate, hevc, if (audio) 48000 else 0, if (audio) 2 else 0)
            if (rec == 0L) throw ExportException(MfNative.error().ifBlank { "The encoder couldn't start" } + if (hevc) " (HEVC needs Windows' HEVC Video Extensions)" else "", null, ExportException.ERROR_CODE_ENCODER_INIT_FAILED)
            return object : Encoder() {
                override fun video(px: IntArray, tUs: Long, durUs: Long) { if (!MfNative.recVideo(rec, px, tUs * 10, durUs * 10)) throw ExportException("Encoding failed: " + MfNative.error(), null, ExportException.ERROR_CODE_ENCODING_FAILED) }
                override fun audio(pcm: ByteArray, tUs: Long, durUs: Long) { MfNative.recAudio(rec, pcm, pcm.size, tUs * 10, durUs * 10) }
                override fun close() { if (!MfNative.recClose(rec)) throw ExportException("Finishing the file failed: " + MfNative.error(), null, ExportException.ERROR_CODE_MUXING_FAILED) }
            }
        }
    }
}

/** Development machines only (not Windows): ffmpeg encodes. */
internal class DevEncoder(private val path: String, private val w: Int, private val h: Int, private val fps: Float, private val withAudio: Boolean) : Encoder() {
    private val vtmp = File("$path.v.mp4"); private val atmp = File("$path.pcm")
    private val proc = if (w > 0) ProcessBuilder("ffmpeg", "-v", "error", "-y", "-f", "rawvideo", "-pix_fmt", "bgra", "-s", "${w}x$h", "-r", fps.toString(), "-i", "-", "-c:v", "libx264", "-pix_fmt", "yuv420p", vtmp.path).start() else null
    private val vout = proc?.outputStream?.buffered(1 shl 20)
    private val aout = if (withAudio) atmp.outputStream().buffered(1 shl 16) else null
    private val bytes = ByteArray(w * h * 4)
    override fun video(px: IntArray, tUs: Long, durUs: Long) { ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(px); vout!!.write(bytes) }
    override fun audio(pcm: ByteArray, tUs: Long, durUs: Long) { aout?.write(pcm) }
    override fun close() {
        vout?.close(); proc?.waitFor(); aout?.close()
        val args = arrayListOf("ffmpeg", "-v", "error", "-y")
        if (w > 0) { args += "-i"; args += vtmp.path }
        if (withAudio) { args += listOf("-f", "s16le", "-ar", "48000", "-ac", "2", "-i", atmp.path) }
        args += listOf("-c:v", "copy", "-c:a", "aac", path)
        ProcessBuilder(args).inheritIO().start().waitFor()
        vtmp.delete(); atmp.delete()
    }
}
