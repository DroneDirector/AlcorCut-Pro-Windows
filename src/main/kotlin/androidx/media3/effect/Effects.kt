package androidx.media3.effect

import android.content.Context
import android.graphics.Bitmap
import androidx.media3.common.Effect
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import org.lwjgl.opengles.GLES20 as G

/**
 * Media3's picture effects on the app's graphics engine: each effect becomes a shader program that
 * draws its input texture into its own output texture ([GlShaderProgram.process]). Own code.
 */
interface GlEffect : Effect {
    fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram
    fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = false
}

interface GlShaderProgram {
    /** Draws [inputTex] (w×h) at [presentationTimeUs]; returns the output texture and its size. */
    fun process(inputTex: Int, w: Int, h: Int, presentationTimeUs: Long): Triple<Int, Int, Int>
    fun release()
}

/** A shader program with one output texture; subclasses size it ([configure]) and draw into it ([drawFrame]). */
abstract class BaseGlShaderProgram(protected val useHighPrecisionColorComponents: Boolean, texturePoolCapacity: Int) : GlShaderProgram {
    private var outTex = 0; private var fbo = 0; private var ow = 0; private var oh = 0
    private var inW = -1; private var inH = -1
    abstract fun configure(inputWidth: Int, inputHeight: Int): Size
    abstract fun drawFrame(inputTexId: Int, presentationTimeUs: Long)
    open fun isNoOp(): Boolean = false

    override fun process(inputTex: Int, w: Int, h: Int, presentationTimeUs: Long): Triple<Int, Int, Int> {
        if (w != inW || h != inH) {
            val s = configure(w, h); inW = w; inH = h
            if (s.width != ow || s.height != oh || outTex == 0) {
                GlUtil.deleteFbo(fbo); GlUtil.deleteTexture(outTex)
                ow = s.width; oh = s.height
                outTex = GlUtil.createTexture(ow, oh, useHighPrecisionColorComponents); fbo = GlUtil.createFboForTexture(outTex)
            }
        }
        GlUtil.focusFramebufferUsingCurrentContext(fbo, ow, oh)
        GlUtil.clearFocusedBuffers()
        drawFrame(inputTex, presentationTimeUs)
        return Triple(outTex, ow, oh)
    }
    override fun release() { GlUtil.deleteFbo(fbo); GlUtil.deleteTexture(outTex); fbo = 0; outTex = 0 }
}

/** Draws a texture with a 4×4 matrix and an optional extra fragment step (shared by the built-in effects below). */
internal abstract class SimpleProgram(private val fragmentBody: String = "gl_FragColor = texture2D(uTexSampler, vTexSamplingCoord);", extraUniforms: String = "") : BaseGlShaderProgram(false, 1) {
    protected val program = androidx.media3.common.util.GlProgram(
        """
        attribute vec4 aFramePosition;
        uniform mat4 uMatrix;
        varying vec2 vTexSamplingCoord;
        void main() { gl_Position = uMatrix * aFramePosition; vTexSamplingCoord = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5); }
        """,
        "precision highp float;\nuniform sampler2D uTexSampler;\nvarying vec2 vTexSamplingCoord;\n$extraUniforms\nvoid main() { $fragmentBody }",
    ).apply { setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), 4) }
    protected open fun matrix(ptsUs: Long): FloatArray = GlUtil.create4x4IdentityMatrix()
    protected open fun uniforms(ptsUs: Long) {}
    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        program.use()
        program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
        program.setFloatsUniform("uMatrix", matrix(presentationTimeUs))
        uniforms(presentationTimeUs)
        program.bindAttributesAndUniforms()
        G.glDrawArrays(G.GL_TRIANGLE_STRIP, 0, 4)
    }
    override fun release() { super.release(); program.delete() }
}

/** Output size and fitting (scale to fit, crop to fill, or stretch). */
class Presentation private constructor(val width: Int, val height: Int, val aspectRatio: Float, val layout: Int) : GlEffect {
    companion object {
        const val LAYOUT_SCALE_TO_FIT = 0; const val LAYOUT_SCALE_TO_FIT_WITH_CROP = 1; const val LAYOUT_STRETCH_TO_FIT = 2
        @JvmStatic fun createForHeight(h: Int) = Presentation(-1, h, -1f, LAYOUT_SCALE_TO_FIT)
        @JvmStatic fun createForWidthAndHeight(w: Int, h: Int, layout: Int) = Presentation(w, h, -1f, layout)
        @JvmStatic fun createForAspectRatio(a: Float, layout: Int) = Presentation(-1, -1, a, layout)
        @JvmStatic fun createForShortSide(s: Int) = Presentation(-2, s, -1f, LAYOUT_SCALE_TO_FIT)
    }
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = object : SimpleProgram() {
        private var m = GlUtil.create4x4IdentityMatrix()
        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            val inA = inputWidth.toFloat() / inputHeight
            var ow: Int; var oh: Int
            when {
                width == -2 -> { val k = height.toFloat() / minOf(inputWidth, inputHeight); ow = (inputWidth * k).toInt() and 1.inv(); oh = (inputHeight * k).toInt() and 1.inv() }
                width == -1 && height > 0 -> { oh = height; ow = Math.round(height * inA) }
                aspectRatio > 0 -> { if (inA > aspectRatio) { oh = inputHeight; ow = Math.round(inputHeight * aspectRatio) } else { ow = inputWidth; oh = Math.round(inputWidth / aspectRatio) } }
                else -> { ow = width; oh = height }
            }
            ow = ow.coerceAtLeast(2); oh = oh.coerceAtLeast(2)
            val outA = ow.toFloat() / oh
            m = GlUtil.create4x4IdentityMatrix()
            if (width != -1 && width != -2) when (layout) {
                LAYOUT_SCALE_TO_FIT -> if (inA > outA) m[5] = outA / inA else m[0] = inA / outA
                LAYOUT_SCALE_TO_FIT_WITH_CROP -> if (inA > outA) m[0] = inA / outA else m[5] = outA / inA
            }
            return Size(ow, oh)
        }
        override fun matrix(ptsUs: Long) = m
    }
}

/** A changing 2D transform (an android.graphics.Matrix in normalised device coordinates). */
fun interface MatrixTransformation : GlEffect {
    fun getMatrix(presentationTimeUs: Long): android.graphics.Matrix
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = matrixProgram { getMatrix(it) }
}

internal fun matrixProgram(get: (Long) -> android.graphics.Matrix): GlShaderProgram = object : SimpleProgram() {
    override fun configure(inputWidth: Int, inputHeight: Int) = Size(inputWidth, inputHeight)
    override fun matrix(ptsUs: Long): FloatArray {
        val v = FloatArray(9); get(ptsUs).getValues(v)
        // 3×3 affine → column-major 4×4.
        return floatArrayOf(v[0], v[3], 0f, v[6], v[1], v[4], 0f, v[7], 0f, 0f, 1f, 0f, v[2], v[5], 0f, v[8])
    }
}

class ScaleAndRotateTransformation private constructor(private val sx: Float, private val sy: Float, private val deg: Float) : MatrixTransformation {
    override fun getMatrix(presentationTimeUs: Long) = android.graphics.Matrix().apply { postScale(sx, sy); postRotate(-deg) }
    class Builder {
        private var sx = 1f; private var sy = 1f; private var r = 0f
        fun setScale(x: Float, y: Float) = apply { sx = x; sy = y }
        fun setRotationDegrees(d: Float) = apply { r = d }
        fun build() = ScaleAndRotateTransformation(sx, sy, r)
    }
}

/** Keeps the picture at [targetFps] by dropping frames (done by the exporter's clock). */
class FrameDropEffect private constructor(val targetFps: Float) : Effect {
    companion object {
        @JvmStatic fun createDefaultFrameDropEffect(targetFrameRate: Float) = FrameDropEffect(targetFrameRate)
        @JvmStatic fun createSimpleFrameDropEffect(expected: Float, target: Float) = FrameDropEffect(target)
    }
}

/** A 3D colour table (n×n×n cube of ARGB colours) applied to every pixel. */
class SingleColorLut private constructor(private val cube: Array<Array<IntArray>>) : GlEffect {
    companion object {
        @JvmStatic fun createFromCube(cube: Array<Array<IntArray>>) = SingleColorLut(cube)
        @JvmStatic fun createFromBitmap(b: Bitmap): SingleColorLut {
            val n = b.height
            return SingleColorLut(Array(n) { r -> Array(n) { g -> IntArray(n) { bl -> b.getPixel(r * n + bl, g) } } })
        }
    }
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        val n = cube.size
        return object : SimpleProgram(
            """
            vec4 c = texture2D(uTexSampler, vTexSamplingCoord);
            vec3 p = clamp(c.rgb, 0.0, 1.0) * (uN - 1.0);
            float b0 = floor(p.b); float b1 = min(b0 + 1.0, uN - 1.0); float f = p.b - b0;
            vec2 uv0 = vec2((b0 * uN + p.r + 0.5) / (uN * uN), (p.g + 0.5) / uN);
            vec2 uv1 = vec2((b1 * uN + p.r + 0.5) / (uN * uN), (p.g + 0.5) / uN);
            gl_FragColor = vec4(mix(texture2D(uLut, uv0).rgb, texture2D(uLut, uv1).rgb, f), c.a);
            """, "uniform sampler2D uLut; uniform float uN;",
        ) {
            private var lut = 0
            override fun configure(inputWidth: Int, inputHeight: Int): Size {
                if (lut == 0) {
                    // Laid out n*n wide (blue slices side by side, red across each), n high (green).
                    val px = IntArray(n * n * n)
                    for (r in 0 until n) for (g in 0 until n) for (b in 0 until n) px[g * n * n + b * n + r] = cube[r][g][b]
                    lut = G.glGenTextures(); GlUtil.bindTexture(G.GL_TEXTURE_2D, lut)
                    G.glTexImage2D(G.GL_TEXTURE_2D, 0, G.GL_RGBA, n * n, n, 0, G.GL_RGBA, G.GL_UNSIGNED_BYTE, android.opengl.GLUtils.rgba(px))
                }
                return Size(inputWidth, inputHeight)
            }
            override fun uniforms(ptsUs: Long) { program.setSamplerTexIdUniform("uLut", lut, 1); program.setFloatUniform("uN", n.toFloat()) }
            override fun release() { super.release(); GlUtil.deleteTexture(lut) }
        }
    }
}

class AlphaScale(private val alpha: Float) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = object : SimpleProgram("vec4 c = texture2D(uTexSampler, vTexSamplingCoord); gl_FragColor = c * uA;", "uniform float uA;") {
        override fun configure(inputWidth: Int, inputHeight: Int) = Size(inputWidth, inputHeight)
        override fun uniforms(ptsUs: Long) = program.setFloatUniform("uA", alpha)
    }
}

class Contrast(private val contrast: Float) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = object : SimpleProgram("vec4 c = texture2D(uTexSampler, vTexSamplingCoord); gl_FragColor = vec4(clamp((c.rgb - 0.5) * uK + 0.5, 0.0, 1.0), c.a);", "uniform float uK;") {
        override fun configure(inputWidth: Int, inputHeight: Int) = Size(inputWidth, inputHeight)
        override fun uniforms(ptsUs: Long) = program.setFloatUniform("uK", (1f + contrast) / (1.0001f - contrast))
    }
    override fun isNoOp(inputWidth: Int, inputHeight: Int) = contrast == 0f
}

/** Overlay placement: anchors in −1…1 (x right, y up), scale, opacity and rotation. */
class StaticOverlaySettings private constructor(
    override val alphaScale: Float, override val backgroundFrameAnchor: Pair<Float, Float>, override val overlayFrameAnchor: Pair<Float, Float>,
    override val scale: Pair<Float, Float>, override val rotationDegrees: Float,
) : OverlaySettings {
    class Builder {
        private var a = 1f; private var bg = 0f to 0f; private var ov = 0f to 0f; private var s = 1f to 1f; private var r = 0f
        fun setAlphaScale(v: Float) = apply { a = v }
        fun setBackgroundFrameAnchor(x: Float, y: Float) = apply { bg = x to y }
        fun setOverlayFrameAnchor(x: Float, y: Float) = apply { ov = x to y }
        fun setScale(x: Float, y: Float) = apply { s = x to y }
        fun setRotationDegrees(d: Float) = apply { r = d }
        fun setHdrLuminanceMultiplier(m: Float) = this
        fun build() = StaticOverlaySettings(a, bg, ov, s, r)
    }
}

/** A picture drawn over the video (layers, titles, timecode). */
abstract class TextureOverlay {
    open fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = StaticOverlaySettings.Builder().build()
    open fun configure(videoSize: Size) {}
    open fun release() {}
    /** The overlay's texture and size at a time (GL thread). */
    abstract fun getTextureId(presentationTimeUs: Long): Int
    abstract fun getTextureSize(presentationTimeUs: Long): Size
}

/** A Bitmap overlay: uploaded when the bitmap object changes. */
abstract class BitmapOverlay : TextureOverlay() {
    private var tex = 0; private var last: Bitmap? = null; private var lw = 1; private var lh = 1
    abstract fun getBitmap(presentationTimeUs: Long): Bitmap
    override fun getTextureId(presentationTimeUs: Long): Int {
        val b = getBitmap(presentationTimeUs)
        if (b !== last || tex == 0) {
            if (tex == 0) tex = G.glGenTextures()
            GlUtil.bindTexture(G.GL_TEXTURE_2D, tex)
            android.opengl.GLUtils.texImage2D(G.GL_TEXTURE_2D, 0, b, 0)
            last = b; lw = b.width; lh = b.height
        }
        return tex
    }
    override fun getTextureSize(presentationTimeUs: Long) = Size(lw, lh)
    override fun release() { GlUtil.deleteTexture(tex); tex = 0 }
    companion object {
        @JvmStatic fun createStaticBitmapOverlay(b: Bitmap): BitmapOverlay = object : BitmapOverlay() { override fun getBitmap(presentationTimeUs: Long) = b }
        @JvmStatic fun createStaticBitmapOverlay(b: Bitmap, s: OverlaySettings): BitmapOverlay = object : BitmapOverlay() {
            override fun getBitmap(presentationTimeUs: Long) = b
            override fun getOverlaySettings(presentationTimeUs: Long) = s
        }
    }
}

/** Draws [overlays] over the picture, in order. */
class OverlayEffect(val overlays: List<TextureOverlay>) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = object : BaseGlShaderProgram(false, 1) {
        private val copy = matrixProgram { android.graphics.Matrix() } as BaseGlShaderProgram
        private val prog = androidx.media3.common.util.GlProgram(
            "attribute vec4 aFramePosition; uniform mat4 uMatrix; varying vec2 vUv; void main() { gl_Position = uMatrix * aFramePosition; vUv = vec2(aFramePosition.x * 0.5 + 0.5, 0.5 - aFramePosition.y * 0.5); }",
            "precision highp float; uniform sampler2D uTex; uniform float uA; varying vec2 vUv; void main() { gl_FragColor = texture2D(uTex, vUv) * uA; }",
        ).apply { setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), 4) }
        private var w = 1; private var h = 1
        override fun configure(inputWidth: Int, inputHeight: Int): Size { w = inputWidth; h = inputHeight; overlays.forEach { it.configure(Size(w, h)) }; return Size(w, h) }
        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            // The picture itself.
            val p = androidx.media3.common.util.GlProgram(
                "attribute vec4 aFramePosition; varying vec2 vUv; void main() { gl_Position = aFramePosition; vUv = aFramePosition.xy * 0.5 + 0.5; }",
                "precision highp float; uniform sampler2D uTex; varying vec2 vUv; void main() { gl_FragColor = texture2D(uTex, vUv); }")
            p.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), 4)
            p.use(); p.setSamplerTexIdUniform("uTex", inputTexId, 0); p.bindAttributesAndUniforms(); G.glDrawArrays(G.GL_TRIANGLE_STRIP, 0, 4); p.delete()
            G.glEnable(G.GL_BLEND); G.glBlendFunc(G.GL_ONE, G.GL_ONE_MINUS_SRC_ALPHA)
            for (o in overlays) {
                val tex = o.getTextureId(presentationTimeUs); val sz = o.getTextureSize(presentationTimeUs)
                val s = o.getOverlaySettings(presentationTimeUs)
                if (s.alphaScale <= 0f) continue
                prog.use()
                prog.setSamplerTexIdUniform("uTex", tex, 0)
                prog.setFloatUniform("uA", s.alphaScale)
                prog.setFloatsUniform("uMatrix", overlayMatrix(s, sz.width.toFloat() / w, sz.height.toFloat() / h))
                prog.bindAttributesAndUniforms()
                G.glDrawArrays(G.GL_TRIANGLE_STRIP, 0, 4)
            }
            G.glDisable(G.GL_BLEND)
        }
        override fun release() { super.release(); prog.delete(); overlays.forEach { it.release() } }
    }
}

/**
 * Where an overlay goes (column-major 4×4): its own anchor point lands on the background anchor;
 * [fw] / [fh] are its size as a share of the frame.
 */
internal fun overlayMatrix(s: OverlaySettings, fw: Float, fh: Float): FloatArray {
    val sx = fw * s.scale.first; val sy = fh * s.scale.second
    val ax = s.overlayFrameAnchor.first; val ay = s.overlayFrameAnchor.second
    val tx = s.backgroundFrameAnchor.first - ax * sx; val ty = s.backgroundFrameAnchor.second - ay * sy
    val r = Math.toRadians(s.rotationDegrees.toDouble()); val c = Math.cos(r).toFloat(); val sn = Math.sin(r).toFloat()
    return floatArrayOf(sx * c, sx * sn, 0f, 0f, -sy * sn, sy * c, 0f, 0f, 0f, 0f, 1f, 0f, tx, ty, 0f, 1f)
}
