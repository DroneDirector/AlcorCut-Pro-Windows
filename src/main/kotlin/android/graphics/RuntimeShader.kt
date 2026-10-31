package android.graphics

import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

/**
 * GPU picture effects in the phone's shader language (AGSL), run by Skia's runtime effects (SkSL, the
 * language AGSL is based on). Used by the video editor's live previews. Own code.
 */
class RuntimeShader(val source: String) : Shader() {
    internal val effect: RuntimeEffect = compiled.getOrPut(source) {
        try { RuntimeEffect.makeForShader(source) } catch (e: Throwable) {
            // Skia's shader language takes no shader-typed function parameters: such functions are
            // copied once per shader they're called with (same result).
            val alt = specialise(source)
            if (alt == source) throw e
            RuntimeEffect.makeForShader(alt)
        }
    }
    internal val builder = RuntimeShaderBuilder(effect)
    /** Child shaders set by name (the view's own picture goes in as the effect's input). */
    internal val children = HashMap<String, Shader>()
    fun setFloatUniform(name: String, v: Float) = setFloats(name, floatArrayOf(v))
    fun setFloatUniform(name: String, a: Float, b: Float) = setFloats(name, floatArrayOf(a, b))
    fun setFloatUniform(name: String, a: Float, b: Float, c: Float) = setFloats(name, floatArrayOf(a, b, c))
    fun setFloatUniform(name: String, a: Float, b: Float, c: Float, d: Float) = setFloats(name, floatArrayOf(a, b, c, d))
    fun setFloatUniform(name: String, v: FloatArray) = setFloats(name, v)
    private fun setFloats(name: String, v: FloatArray) {
        try { when (v.size) { 1 -> builder.uniform(name, v[0]); 2 -> builder.uniform(name, v[0], v[1]); 3 -> builder.uniform(name, v[0], v[1], v[2]); 4 -> builder.uniform(name, v[0], v[1], v[2], v[3]); else -> builder.uniform(name, v) } }
        catch (e: Throwable) { throw IllegalArgumentException("Uniform $name: ${e.message}") }
    }
    fun setIntUniform(name: String, vararg v: Int) {
        try { when (v.size) { 1 -> builder.uniform(name, v[0]); 2 -> builder.uniform(name, v[0], v[1]); 3 -> builder.uniform(name, v[0], v[1], v[2]); else -> builder.uniform(name, v[0], v[1], v[2], v[3]) } }
        catch (e: Throwable) { throw IllegalArgumentException("Uniform $name: ${e.message}") }
    }
    fun setColorUniform(name: String, c: Int) = setFloats(name, floatArrayOf((c shr 16 and 0xFF) / 255f, (c shr 8 and 0xFF) / 255f, (c and 0xFF) / 255f, (c ushr 24) / 255f))
    fun setInputShader(name: String, s: Shader) { children[name] = s }
    fun setInputBuffer(name: String, s: BitmapShader) { children[name] = s }

    companion object {
        private val compiled = java.util.concurrent.ConcurrentHashMap<String, RuntimeEffect>()

        internal fun specialise(src: String): String {
            val head = Regex("""(\w+)\s+(\w+)\s*\(\s*shader\s+(\w+)\s*(,([^)]*))?\)\s*\{""")
            var text = src
            while (true) {
                val m = head.find(text) ?: break
                val ret = m.groupValues[1]; val name = m.groupValues[2]; val param = m.groupValues[3]; val rest = m.groupValues[5]
                // The function's body (to its matching brace).
                var depth = 0; var end = m.range.last
                for (i in m.range.last until text.length) { if (text[i] == '{') depth++; if (text[i] == '}') { depth--; if (depth == 0) { end = i; break } } }
                val body = text.substring(m.range.last, end + 1)
                val before = text.substring(0, m.range.first); val after = text.substring(end + 1)
                val call = Regex("""\b$name\s*\(\s*(\w+)\s*,""")
                val used = call.findAll(after).map { it.groupValues[1] }.toSet()
                val copies = used.joinToString("\n") { sh -> "$ret ${name}_$sh($rest) " + body.replace(Regex("\\b$param\\b"), sh) }
                text = before + copies + call.replace(after) { "${name}_${it.groupValues[1]}(" }
            }
            return text
        }
    }
}

/** A picture used as a shader (tiled or clamped), e.g. a colour table handed to a runtime shader. */
class BitmapShader(val bitmap: Bitmap, val tileX: TileMode, val tileY: TileMode) : Shader() {
    var filterMode = FILTER_MODE_DEFAULT
    private var local: Matrix? = null
    fun setLocalMatrix(m: Matrix?) { local = m?.let { Matrix(it) } }
    internal val localMatrix: Matrix? get() = local
    companion object { const val FILTER_MODE_DEFAULT = 0; const val FILTER_MODE_NEAREST = 1; const val FILTER_MODE_LINEAR = 2 }
}

class SweepGradient(val cx: Float, val cy: Float, val colors: IntArray, val positions: FloatArray?) : Shader() {
    constructor(cx: Float, cy: Float, c0: Int, c1: Int) : this(cx, cy, intArrayOf(c0, c1), null)
    private var local: Matrix? = null
    fun setLocalMatrix(m: Matrix?) { local = m?.let { Matrix(it) } }
    internal val localMatrix: Matrix? get() = local
}

/** What a view's picture goes through before it's shown (the phone's RenderEffect). */
class RenderEffect private constructor(internal val kind: Int, internal val shader: RuntimeShader? = null, internal val input: String? = null,
                                       internal val blurX: Float = 0f, internal val blurY: Float = 0f,
                                       internal val outer: RenderEffect? = null, internal val inner: RenderEffect? = null, internal val colorFilter: ColorFilter? = null) {
    companion object {
        @JvmStatic fun createRuntimeShaderEffect(shader: RuntimeShader, uniformShaderName: String) = RenderEffect(0, shader, uniformShaderName)
        @JvmStatic fun createBlurEffect(rx: Float, ry: Float, tile: Shader.TileMode) = RenderEffect(1, blurX = rx, blurY = ry)
        @JvmStatic fun createChainEffect(outer: RenderEffect, inner: RenderEffect) = RenderEffect(2, outer = outer, inner = inner)
        @JvmStatic fun createColorFilterEffect(f: ColorFilter) = RenderEffect(3, colorFilter = f)
        @JvmStatic fun createOffsetEffect(x: Float, y: Float) = RenderEffect(4, blurX = x, blurY = y)
    }
}
