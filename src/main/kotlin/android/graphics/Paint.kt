package android.graphics

/** Drawing style for [Canvas]. Own code. */
open class Paint(flags: Int = 0) {
    enum class Style { FILL, STROKE, FILL_AND_STROKE }
    enum class Cap { BUTT, ROUND, SQUARE }
    enum class Join { MITER, ROUND, BEVEL }
    enum class Align { LEFT, CENTER, RIGHT }

    companion object {
        const val ANTI_ALIAS_FLAG = 1
        const val FILTER_BITMAP_FLAG = 2
        const val DITHER_FLAG = 4
    }

    constructor(p: Paint) : this(p.flags) { set(p) }

    var flags: Int = flags
    var isAntiAlias: Boolean
        get() = flags and ANTI_ALIAS_FLAG != 0
        set(v) { flags = if (v) flags or ANTI_ALIAS_FLAG else flags and ANTI_ALIAS_FLAG.inv() }
    var isFilterBitmap: Boolean
        get() = flags and FILTER_BITMAP_FLAG != 0
        set(v) { flags = if (v) flags or FILTER_BITMAP_FLAG else flags and FILTER_BITMAP_FLAG.inv() }
    var isDither = false
    var color: Int = 0xFF000000.toInt()
    var alpha: Int
        get() = (color ushr 24) and 0xFF
        set(v) { color = (color and 0x00FFFFFF) or (v.coerceIn(0, 255) shl 24) }
    var style = Style.FILL
    var strokeWidth = 0f
    var strokeCap = Cap.BUTT
    var strokeJoin = Join.MITER
    var textSize = 12f
    var textAlign = Align.LEFT
    var isFakeBoldText = false
    var maskFilter: MaskFilter? = null
    var xfermode: Xfermode? = null
    var colorFilter: ColorFilter? = null
    var shader: Shader? = null
    var typeface: Typeface? = null

    fun set(p: Paint) {
        flags = p.flags; isDither = p.isDither; color = p.color; style = p.style; strokeWidth = p.strokeWidth
        strokeCap = p.strokeCap; strokeJoin = p.strokeJoin; textSize = p.textSize; textAlign = p.textAlign
        maskFilter = p.maskFilter; xfermode = p.xfermode; colorFilter = p.colorFilter; shader = p.shader; typeface = p.typeface
        isFakeBoldText = p.isFakeBoldText
    }

    fun setARGB(a: Int, r: Int, g: Int, b: Int) { color = Color.argb(a, r, g, b) }

    fun measureText(text: String): Float = Canvas.font(this).let { f ->
        java.awt.Canvas().getFontMetrics(f).stringWidth(text).toFloat()
    }
    fun measureText(text: CharSequence, start: Int, end: Int): Float = measureText(text.subSequence(start, end).toString())
    fun measureText(text: CharArray, index: Int, count: Int): Float = measureText(String(text, index, count))

    /** Font ascent/descent like the phone (ascent negative). */
    class FontMetrics { @JvmField var ascent = 0f; @JvmField var descent = 0f; @JvmField var top = 0f; @JvmField var bottom = 0f; @JvmField var leading = 0f }
    val fontMetrics: FontMetrics get() {
        val fm = java.awt.Canvas().getFontMetrics(Canvas.font(this))
        return FontMetrics().apply { ascent = -fm.ascent.toFloat(); descent = fm.descent.toFloat(); top = -fm.maxAscent.toFloat(); bottom = fm.maxDescent.toFloat(); leading = fm.leading.toFloat() }
    }
    fun getFontMetrics(out: FontMetrics?): Float { val m = fontMetrics; out?.let { it.ascent = m.ascent; it.descent = m.descent; it.top = m.top; it.bottom = m.bottom; it.leading = m.leading }; return m.descent - m.ascent + m.leading }
    fun getTextBounds(text: String, start: Int, end: Int, bounds: Rect) {
        val f = Canvas.font(this)
        val r = f.getStringBounds(text.substring(start, end), java.awt.font.FontRenderContext(null, true, true))
        bounds.set(r.x.toInt(), r.y.toInt(), (r.x + r.width).toInt(), (r.y + r.height).toInt())
    }
    var letterSpacing = 0f
    fun setShadowLayer(radius: Float, dx: Float, dy: Float, color: Int) {}
    fun clearShadowLayer() {}
}

open class MaskFilter
class BlurMaskFilter(val radius: Float, val style: Blur) : MaskFilter() {
    enum class Blur { NORMAL, SOLID, OUTER, INNER }
}

open class Xfermode
class PorterDuffXfermode(val mode: PorterDuff.Mode) : Xfermode()

object PorterDuff {
    enum class Mode { CLEAR, SRC, DST, SRC_OVER, DST_OVER, SRC_IN, DST_IN, SRC_OUT, DST_OUT, SRC_ATOP, DST_ATOP, XOR, DARKEN, LIGHTEN, MULTIPLY, SCREEN, ADD, OVERLAY }
}

open class ColorFilter
class ColorMatrixColorFilter(matrix: FloatArray) : ColorFilter() {
    constructor(m: ColorMatrix) : this(m.array)
    val m: FloatArray = matrix.copyOf(20)
}
class PorterDuffColorFilter(val color: Int, val mode: PorterDuff.Mode) : ColorFilter()

open class Shader {
    enum class TileMode { CLAMP, REPEAT, MIRROR }
}
class LinearGradient(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val colors: IntArray, val positions: FloatArray?, val tile: Shader.TileMode) : Shader() {
    constructor(x0: Float, y0: Float, x1: Float, y1: Float, c0: Int, c1: Int, tile: Shader.TileMode) : this(x0, y0, x1, y1, intArrayOf(c0, c1), null, tile)
}
class RadialGradient(val cx: Float, val cy: Float, val radius: Float, val colors: IntArray, val positions: FloatArray?, val tile: Shader.TileMode) : Shader() {
    constructor(cx: Float, cy: Float, radius: Float, c0: Int, c1: Int, tile: Shader.TileMode) : this(cx, cy, radius, intArrayOf(c0, c1), null, tile)
}

class Typeface private constructor(val family: String, val style: Int) {
    companion object {
        const val NORMAL = 0; const val BOLD = 1; const val ITALIC = 2; const val BOLD_ITALIC = 3
        val DEFAULT = Typeface("SansSerif", NORMAL)
        val DEFAULT_BOLD = Typeface("SansSerif", BOLD)
        val SANS_SERIF = Typeface("SansSerif", NORMAL)
        val SERIF = Typeface("Serif", NORMAL)
        val MONOSPACE = Typeface("Monospaced", NORMAL)
        fun create(family: Typeface?, style: Int) = Typeface(family?.family ?: "SansSerif", style)
        fun create(family: String?, style: Int) = Typeface(family ?: "SansSerif", style)
    }
}

/** 4×5 colour matrix (offsets 0..255), as on the phone. */
class ColorMatrix() {
    val array = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
    constructor(src: FloatArray) : this() { src.copyInto(array, 0, 0, 20) }
    constructor(src: ColorMatrix) : this(src.array)

    fun reset() { ColorMatrix().array.copyInto(array) }
    fun set(src: FloatArray) { src.copyInto(array, 0, 0, 20) }
    fun set(src: ColorMatrix) = set(src.array)

    fun setSaturation(sat: Float) {
        reset()
        val inv = 1 - sat
        val r = 0.213f * inv; val g = 0.715f * inv; val b = 0.072f * inv
        array[0] = r + sat; array[1] = g; array[2] = b
        array[5] = r; array[6] = g + sat; array[7] = b
        array[10] = r; array[11] = g; array[12] = b + sat
    }

    fun setScale(r: Float, g: Float, b: Float, a: Float) {
        array.fill(0f); array[0] = r; array[6] = g; array[12] = b; array[18] = a
    }

    /** this = this × [m] (this applied after [m]). */
    fun setConcat(a: ColorMatrix, b: ColorMatrix) {
        val out = FloatArray(20)
        for (j in 0 until 4) {
            for (i in 0 until 4) out[j * 5 + i] = a.array[j * 5] * b.array[i] + a.array[j * 5 + 1] * b.array[5 + i] + a.array[j * 5 + 2] * b.array[10 + i] + a.array[j * 5 + 3] * b.array[15 + i]
            out[j * 5 + 4] = a.array[j * 5] * b.array[4] + a.array[j * 5 + 1] * b.array[9] + a.array[j * 5 + 2] * b.array[14] + a.array[j * 5 + 3] * b.array[19] + a.array[j * 5 + 4]
        }
        out.copyInto(array)
    }

    fun preConcat(pre: ColorMatrix) = setConcat(ColorMatrix(this), pre)
    fun postConcat(post: ColorMatrix) = setConcat(post, ColorMatrix(this))
}

object Color {
    const val BLACK = 0xFF000000.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()
    const val TRANSPARENT = 0
    const val RED = 0xFFFF0000.toInt()
    const val GREEN = 0xFF00FF00.toInt()
    const val BLUE = 0xFF0000FF.toInt()
    const val GRAY = 0xFF888888.toInt()
    const val DKGRAY = 0xFF444444.toInt()
    const val LTGRAY = 0xFFCCCCCC.toInt()
    const val YELLOW = 0xFFFFFF00.toInt()
    const val CYAN = 0xFF00FFFF.toInt()
    const val MAGENTA = 0xFFFF00FF.toInt()

    fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b
    fun rgb(r: Int, g: Int, b: Int) = argb(255, r, g, b)
    fun argb(a: Float, r: Float, g: Float, b: Float) = argb((a * 255 + 0.5f).toInt(), (r * 255 + 0.5f).toInt(), (g * 255 + 0.5f).toInt(), (b * 255 + 0.5f).toInt())
    fun rgb(r: Float, g: Float, b: Float) = argb(1f, r, g, b)
    fun alpha(c: Int) = (c ushr 24) and 0xFF
    fun red(c: Int) = (c shr 16) and 0xFF
    fun green(c: Int) = (c shr 8) and 0xFF
    fun blue(c: Int) = c and 0xFF
    fun parseColor(s: String): Int {
        val h = s.removePrefix("#")
        val v = h.toLong(16).toInt()
        return if (h.length <= 6) v or (0xFF shl 24) else v
    }

    fun RGBToHSV(r: Int, g: Int, b: Int, hsv: FloatArray) {
        val f = java.awt.Color.RGBtoHSB(r, g, b, null)
        hsv[0] = f[0] * 360f; hsv[1] = f[1]; hsv[2] = f[2]
    }
    fun colorToHSV(c: Int, hsv: FloatArray) = RGBToHSV(red(c), green(c), blue(c), hsv)
    fun HSVToColor(hsv: FloatArray): Int = HSVToColor(255, hsv)
    fun HSVToColor(a: Int, hsv: FloatArray): Int = (java.awt.Color.HSBtoRGB(((hsv[0] % 360f + 360f) % 360f) / 360f, hsv[1].coerceIn(0f, 1f), hsv[2].coerceIn(0f, 1f)) and 0xFFFFFF) or (a shl 24)
}

/** Lines and curves for [Canvas.drawPath]. */
class Path {
    enum class FillType { WINDING, EVEN_ODD, INVERSE_WINDING, INVERSE_EVEN_ODD }
    enum class Direction { CW, CCW }
    val shape = java.awt.geom.Path2D.Float()
    var fillType = FillType.WINDING
        set(v) { field = v; shape.windingRule = if (v == FillType.EVEN_ODD) java.awt.geom.Path2D.WIND_EVEN_ODD else java.awt.geom.Path2D.WIND_NON_ZERO }
    private var open = false

    fun reset() { shape.reset(); open = false }
    fun rewind() = reset()
    fun moveTo(x: Float, y: Float) { shape.moveTo(x, y); open = true }
    fun lineTo(x: Float, y: Float) { if (!open) moveTo(0f, 0f); shape.lineTo(x, y) }
    fun quadTo(x1: Float, y1: Float, x2: Float, y2: Float) { if (!open) moveTo(0f, 0f); shape.quadTo(x1, y1, x2, y2) }
    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) { if (!open) moveTo(0f, 0f); shape.curveTo(x1, y1, x2, y2, x3, y3) }
    fun close() { if (open) shape.closePath() }
    fun addRect(l: Float, t: Float, r: Float, b: Float, dir: Direction) { shape.append(java.awt.geom.Rectangle2D.Float(l, t, r - l, b - t), false); open = true }
    fun addCircle(x: Float, y: Float, r: Float, dir: Direction) { shape.append(java.awt.geom.Ellipse2D.Float(x - r, y - r, 2 * r, 2 * r), false); open = true }
    fun addOval(rect: RectF, dir: Direction) { shape.append(java.awt.geom.Ellipse2D.Float(rect.left, rect.top, rect.width(), rect.height()), false); open = true }
    fun addRoundRect(r: RectF, rx: Float, ry: Float, dir: Direction) { shape.append(java.awt.geom.RoundRectangle2D.Float(r.left, r.top, r.width(), r.height(), rx * 2, ry * 2), false); open = true }
    fun addRoundRect(l: Float, t: Float, r: Float, b: Float, rx: Float, ry: Float, dir: Direction) = addRoundRect(RectF(l, t, r, b), rx, ry, dir)
    fun addPath(p: Path) { shape.append(p.shape, false); open = true }
    fun transform(m: Matrix) {
        if (m.isAffine) shape.transform(java.awt.geom.AffineTransform(m.v[0], m.v[3], m.v[1], m.v[4], m.v[2], m.v[5]))
    }
    val isEmpty: Boolean get() = shape.currentPoint == null
}
