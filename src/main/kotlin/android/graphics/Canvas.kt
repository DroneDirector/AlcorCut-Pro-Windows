package android.graphics

import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.util.stream.IntStream

/**
 * Draws onto a [Bitmap]. Pictures are placed with the app's own resampler (perspective included);
 * shapes and text go through Java2D. Own code.
 */
class Canvas(private var bmp: Bitmap? = null) {
    private val stack = ArrayList<Matrix>()
    private var m = Matrix()

    val width: Int get() = bmp?.width ?: 0
    val height: Int get() = bmp?.height ?: 0
    fun setBitmap(b: Bitmap?) { bmp = b }

    private val clips = ArrayList<java.awt.Shape?>()
    private var clip: java.awt.Shape? = null
    fun save(): Int { stack += Matrix(m); clips += clip; return stack.size }
    fun restore() { if (stack.isNotEmpty()) { m = stack.removeAt(stack.size - 1); clip = clips.removeAt(clips.size - 1) } }

    /** Limits drawing to [path] (shapes and pictures). */
    fun clipPath(path: Path): Boolean {
        val s = java.awt.geom.Area(path.shape)
        if (m.isAffine) s.transform(AffineTransform(m.v[0], m.v[3], m.v[1], m.v[4], m.v[2], m.v[5]))
        clip = clip?.let { java.awt.geom.Area(it).apply { intersect(s) } } ?: s
        return true
    }
    fun clipRect(l: Float, t: Float, r: Float, b: Float): Boolean = clipPath(Path().apply { addRect(l, t, r, b, Path.Direction.CW) })
    fun clipRect(r: RectF): Boolean = clipRect(r.left, r.top, r.right, r.bottom)
    fun clipRect(r: Rect): Boolean = clipRect(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat())
    fun restoreToCount(n: Int) { while (stack.size >= n && stack.isNotEmpty()) restore() }
    fun translate(dx: Float, dy: Float) { m.preTranslate(dx, dy) }
    fun scale(sx: Float, sy: Float) { m.preScale(sx, sy) }
    fun scale(sx: Float, sy: Float, px: Float, py: Float) { m.preScale(sx, sy, px, py) }
    fun rotate(deg: Float) { m.preRotate(deg) }
    fun rotate(deg: Float, px: Float, py: Float) { m.preRotate(deg, px, py) }
    fun concat(mx: Matrix?) { if (mx != null) m.preConcat(mx) }
    fun setMatrix(mx: Matrix?) { m = Matrix(mx) }
    fun getMatrix(): Matrix = Matrix(m)

    // ------------------------------------------------------------------ pictures

    fun drawBitmap(src: Bitmap, left: Float, top: Float, paint: Paint?) {
        bmp?.let { it.gen++ }
        drawBitmap(src, Matrix().apply { setTranslate(left, top) }, paint)
    }

    fun drawBitmap(src: Bitmap, srcRect: Rect?, dst: RectF, paint: Paint?) {
        bmp?.let { it.gen++ }
        val s = srcRect ?: Rect(0, 0, src.width, src.height)
        val part = if (s.left == 0 && s.top == 0 && s.right == src.width && s.bottom == src.height) src
            else Bitmap.createBitmap(src, s.left, s.top, s.width(), s.height())
        val mx = Matrix()
        mx.setRectToRect(RectF(0f, 0f, s.width().toFloat(), s.height().toFloat()), dst, Matrix.ScaleToFit.FILL)
        drawBitmap(part, mx, paint)
    }

    fun drawBitmap(src: Bitmap, srcRect: Rect?, dst: Rect, paint: Paint?) = drawBitmap(src, srcRect, RectF(dst), paint)

    fun drawBitmap(src0: Bitmap, matrix: Matrix?, paint: Paint?) {
        bmp?.let { it.gen++ }
        val out = bmp ?: return
        clip?.let { c ->
            // Draw on a layer, then copy only the pixels inside the clip.
            val layer = out.copy(Bitmap.Config.ARGB_8888, true)
            val cv = Canvas(layer).also { it.m = Matrix(m) }
            cv.drawBitmap(src0, matrix, paint)
            val b = c.bounds
            for (y in maxOf(0, b.y) until minOf(out.height, b.y + b.height)) for (x in maxOf(0, b.x) until minOf(out.width, b.x + b.width))
                if (c.contains(x + 0.5, y + 0.5)) out.data[y * out.width + x] = layer.data[y * out.width + x]
            return
        }
        // Different colour spaces: convert a copy first.
        val src = if (src0.colorSpace != null && out.colorSpace != null && src0.colorSpace != out.colorSpace)
            src0.copy(Bitmap.Config.ARGB_8888, true).also { ColorSpace.convert(it.data, src0.colorSpace!!, out.colorSpace!!) } else src0
        val full = Matrix(m).apply { if (matrix != null) preConcat(matrix) }
        val inv = Matrix()
        if (!full.invert(inv)) return
        // Destination area covered.
        val r = RectF(0f, 0f, src.width.toFloat(), src.height.toFloat())
        full.mapRect(r)
        val x0 = kotlin.math.floor(r.left).toInt().coerceAtLeast(0); val x1 = kotlin.math.ceil(r.right).toInt().coerceAtMost(out.width)
        val y0 = kotlin.math.floor(r.top).toInt().coerceAtLeast(0); val y1 = kotlin.math.ceil(r.bottom).toInt().coerceAtMost(out.height)
        if (x0 >= x1 || y0 >= y1) return
        val sd = src.data; val sw = src.width; val sh = src.height
        val dd = out.data; val dw = out.width
        val iv = inv.v
        val cm = (paint?.colorFilter as? ColorMatrixColorFilter)?.m
        val pa = paint?.alpha ?: 255
        val clear = (paint?.xfermode as? PorterDuffXfermode)?.mode
        // Mixing modes (multiply, screen, overlay…): per pixel, like the phone.
        val mix: BlendMode? = paint?.blendMode?.takeIf { it in MIXES } ?: when (clear) {
            PorterDuff.Mode.MULTIPLY -> BlendMode.MULTIPLY; PorterDuff.Mode.SCREEN -> BlendMode.SCREEN
            PorterDuff.Mode.OVERLAY -> BlendMode.OVERLAY; PorterDuff.Mode.ADD -> BlendMode.PLUS
            PorterDuff.Mode.DARKEN -> BlendMode.DARKEN; PorterDuff.Mode.LIGHTEN -> BlendMode.LIGHTEN
            else -> null
        }
        val smooth = paint == null || paint.isFilterBitmap
        // Downscaling a lot: shrink first so the result isn't grainy.
        val scale = kotlin.math.sqrt(kotlin.math.abs(full.v[0] * full.v[4] - full.v[1] * full.v[3]))
        if (smooth && full.isAffine && scale < 0.5f && sw > 2 && sh > 2) {
            val k = scale.coerceAtLeast(1f / 64)
            val small = Bitmap.createScaledBitmap(src, (sw * k).toInt().coerceAtLeast(1), (sh * k).toInt().coerceAtLeast(1), true)
            val mm = Matrix(matrix).apply { preScale(sw.toFloat() / small.width, sh.toFloat() / small.height) }
            drawBitmap(small, mm, paint)
            return
        }
        IntStream.range(y0, y1).parallel().forEach { y ->
            val fy = y + 0.5f
            for (x in x0 until x1) {
                val fx = x + 0.5f
                val w = iv[6] * fx + iv[7] * fy + iv[8]
                if (w <= 0f) continue
                val sx = (iv[0] * fx + iv[1] * fy + iv[2]) / w
                val sy = (iv[3] * fx + iv[4] * fy + iv[5]) / w
                if (sx < 0f || sy < 0f || sx >= sw || sy >= sh) continue
                var c = if (smooth) Resample.sample(sd, sw, sh, sx - 0.5f, sy - 0.5f) else sd[sy.toInt() * sw + sx.toInt()]
                if (cm != null) c = applyMatrix(cm, c)
                val i = y * dw + x
                dd[i] = if (mix != null) mixBlend(c, dd[i], pa, mix) else blend(c, dd[i], pa, clear)
            }
        }
    }

    /** Draws [src] warped through a grid of [meshW]×[meshH] cells ([verts] = destination points). */
    fun drawBitmapMesh(src: Bitmap, meshW: Int, meshH: Int, verts: FloatArray, vertOffset: Int, colors: IntArray?, colorOffset: Int, paint: Paint?) {
        bmp?.let { it.gen++ }
        val out = bmp ?: return
        val cols = meshW + 1
        fun vx(i: Int) = verts[vertOffset + i * 2]
        fun vy(i: Int) = verts[vertOffset + i * 2 + 1]
        val sw = src.width.toFloat(); val sh = src.height.toFloat()
        fun ux(i: Int) = (i % cols) * sw / meshW
        fun uy(i: Int) = (i / cols) * sh / meshH
        val tris = ArrayList<IntArray>()
        for (gy in 0 until meshH) for (gx in 0 until meshW) {
            val a = gy * cols + gx; val b = a + 1; val c = a + cols; val d = c + 1
            tris += intArrayOf(a, b, d); tris += intArrayOf(a, d, c)
        }
        val sd = src.data; val sW = src.width; val sH = src.height
        val dd = out.data; val dw = out.width; val dh = out.height
        tris.parallelStream().forEach { t ->
            val ax = vx(t[0]); val ay = vy(t[0]); val bx = vx(t[1]); val by = vy(t[1]); val cx = vx(t[2]); val cy = vy(t[2])
            val det = (bx - ax) * (cy - ay) - (cx - ax) * (by - ay)
            if (kotlin.math.abs(det) < 1e-6f) return@forEach
            val minX = kotlin.math.floor(minOf(ax, bx, cx)).toInt().coerceAtLeast(0); val maxX = kotlin.math.ceil(maxOf(ax, bx, cx)).toInt().coerceAtMost(dw - 1)
            val minY = kotlin.math.floor(minOf(ay, by, cy)).toInt().coerceAtLeast(0); val maxY = kotlin.math.ceil(maxOf(ay, by, cy)).toInt().coerceAtMost(dh - 1)
            for (y in minY..maxY) for (x in minX..maxX) {
                val px = x + 0.5f; val py = y + 0.5f
                val l1 = ((bx - px) * (cy - py) - (cx - px) * (by - py)) / det
                val l2 = ((cx - px) * (ay - py) - (ax - px) * (cy - py)) / det
                val l3 = 1f - l1 - l2
                if (l1 < -1e-4f || l2 < -1e-4f || l3 < -1e-4f) continue
                val su = l1 * ux(t[0]) + l2 * ux(t[1]) + l3 * ux(t[2])
                val sv = l1 * uy(t[0]) + l2 * uy(t[1]) + l3 * uy(t[2])
                dd[y * dw + x] = blend(Resample.sample(sd, sW, sH, su - 0.5f, sv - 0.5f), dd[y * dw + x], 255, null)
            }
        }
    }

    // ------------------------------------------------------------------ shapes and text

    fun drawColor(c: Int) = drawColor(c, PorterDuff.Mode.SRC_OVER)
    fun drawColor(c: Int, mode: PorterDuff.Mode) {
        bmp?.let { it.gen++ }
        val out = bmp ?: return
        if (mode == PorterDuff.Mode.CLEAR) { out.data.fill(0); return }
        if (mode == PorterDuff.Mode.SRC || (c ushr 24) == 255) { out.data.fill(c); return }
        val d = out.data
        for (i in d.indices) d[i] = blend(c, d[i], 255, null)
    }
    fun drawRGB(r: Int, g: Int, b: Int) = drawColor(Color.rgb(r, g, b))
    fun drawARGB(a: Int, r: Int, g: Int, b: Int) = drawColor(Color.argb(a, r, g, b))
    fun drawPaint(p: Paint) = drawColor(p.color)

    fun drawRect(l: Float, t: Float, r: Float, b: Float, p: Paint) = shape(Rectangle2D.Float(l, t, r - l, b - t), p)
    fun drawRect(r: RectF, p: Paint) = drawRect(r.left, r.top, r.right, r.bottom, p)
    fun drawRect(r: Rect, p: Paint) = drawRect(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat(), p)
    fun drawRoundRect(r: RectF, rx: Float, ry: Float, p: Paint) = shape(RoundRectangle2D.Float(r.left, r.top, r.width(), r.height(), rx * 2, ry * 2), p)
    fun drawRoundRect(l: Float, t: Float, r: Float, b: Float, rx: Float, ry: Float, p: Paint) = drawRoundRect(RectF(l, t, r, b), rx, ry, p)
    fun drawCircle(cx: Float, cy: Float, r: Float, p: Paint) = shape(Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r), p)
    fun drawOval(r: RectF, p: Paint) = shape(Ellipse2D.Float(r.left, r.top, r.width(), r.height()), p)
    fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, p: Paint) = shape(Line2D.Float(x0, y0, x1, y1), Paint(p).apply { style = Paint.Style.STROKE })
    fun drawPath(path: Path, p: Paint) = shape(path.shape, p)
    fun drawPoint(x: Float, y: Float, p: Paint) = drawCircle(x, y, maxOf(0.5f, p.strokeWidth / 2), Paint(p).apply { style = Paint.Style.FILL })

    fun drawText(text: String, x: Float, y: Float, p: Paint) {
        bmp?.let { it.gen++ }
        val out = bmp ?: return
        withGraphics(out, p) { g ->
            g.font = font(p)
            val w = g.fontMetrics.stringWidth(text)
            val dx = when (p.textAlign) { Paint.Align.CENTER -> -w / 2f; Paint.Align.RIGHT -> -w.toFloat(); else -> 0f }
            g.drawString(text, x + dx, y)
        }
    }

    private fun shape(s: java.awt.Shape, p: Paint) {
        bmp?.let { it.gen++ }
        val out = bmp ?: return
        val blur = (p.maskFilter as? BlurMaskFilter)?.radius ?: 0f
        if (blur <= 0f) { withGraphics(out, p) { g -> paintShape(g, s, p) }; return }
        // Soft edge: draw as a mask on its own layer, blur it, then lay the colour through it.
        val layer = Bitmap.createBitmap(out.width, out.height, Bitmap.Config.ARGB_8888)
        withGraphics(layer, Paint(p).apply { maskFilter = null; xfermode = null; color = Color.WHITE }) { g -> paintShape(g, s, p) }
        val a = IntArray(layer.data.size) { (layer.data[it] ushr 24) and 0xFF }
        boxBlur(a, out.width, out.height, (blur / 1.7f).toInt().coerceAtLeast(1))
        val clear = (p.xfermode as? PorterDuffXfermode)?.mode == PorterDuff.Mode.CLEAR
        val d = out.data
        val rgb = p.color and 0xFFFFFF; val pa = p.alpha
        for (i in d.indices) {
            val k = a[i]; if (k == 0) continue
            d[i] = if (clear) {
                val da = (d[i] ushr 24) and 0xFF
                (((da * (255 - k)) / 255) shl 24) or (d[i] and 0xFFFFFF)
            } else blend((k shl 24) or rgb, d[i], pa, null)
        }
    }

    private fun paintShape(g: Graphics2D, s: java.awt.Shape, p: Paint) {
        if (p.style != Paint.Style.STROKE) g.fill(s)
        if (p.style != Paint.Style.FILL) {
            g.stroke = BasicStroke(maxOf(p.strokeWidth, 1f),
                when (p.strokeCap) { Paint.Cap.ROUND -> BasicStroke.CAP_ROUND; Paint.Cap.SQUARE -> BasicStroke.CAP_SQUARE; else -> BasicStroke.CAP_BUTT },
                when (p.strokeJoin) { Paint.Join.ROUND -> BasicStroke.JOIN_ROUND; Paint.Join.BEVEL -> BasicStroke.JOIN_BEVEL; else -> BasicStroke.JOIN_MITER })
            g.draw(s)
        }
    }

    private inline fun withGraphics(target: Bitmap, p: Paint, block: (Graphics2D) -> Unit) {
        val g = target.image.createGraphics()
        try {
            if (p.isAntiAlias) {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            }
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
            clip?.let { g.clip = it }
            val v = m.v
            if (m.isAffine) g.transform = AffineTransform(v[0], v[3], v[1], v[4], v[2], v[5])
            val mode = (p.xfermode as? PorterDuffXfermode)?.mode
            g.composite = when (mode) {
                PorterDuff.Mode.CLEAR -> AlphaComposite.Clear
                PorterDuff.Mode.SRC -> AlphaComposite.Src
                PorterDuff.Mode.DST_IN -> AlphaComposite.DstIn
                PorterDuff.Mode.DST_OUT -> AlphaComposite.DstOut
                PorterDuff.Mode.SRC_IN -> AlphaComposite.SrcIn
                PorterDuff.Mode.SRC_ATOP -> AlphaComposite.SrcAtop
                else -> AlphaComposite.SrcOver
            }
            g.color = java.awt.Color(p.color, true)
            when (val sh = p.shader) {
                is LinearGradient -> if (sh.colors.size >= 2) g.paint = java.awt.LinearGradientPaint(sh.x0, sh.y0, sh.x1 + 0.001f, sh.y1 + 0.001f,
                    sh.positions ?: FloatArray(sh.colors.size) { it / (sh.colors.size - 1f) }, sh.colors.map { java.awt.Color(it, true) }.toTypedArray())
                is RadialGradient -> if (sh.colors.size >= 2) g.paint = java.awt.RadialGradientPaint(sh.cx, sh.cy, maxOf(sh.radius, 0.01f),
                    sh.positions ?: FloatArray(sh.colors.size) { it / (sh.colors.size - 1f) }, sh.colors.map { java.awt.Color(it, true) }.toTypedArray())
                else -> {}
            }
            block(g)
        } finally { g.dispose() }
    }

    companion object {
        fun font(p: Paint): Font {
            val tf = p.typeface
            var style = tf?.style ?: 0
            if (p.isFakeBoldText) style = style or 1
            tf?.awt?.let { f -> return f.deriveFont((if (style and 1 != 0 && !f.isBold) Font.BOLD else 0) or (if (style and 2 != 0) Font.ITALIC else 0), p.textSize) }
            return Font(tf?.family ?: "SansSerif", (if (style and 1 != 0) Font.BOLD else 0) or (if (style and 2 != 0) Font.ITALIC else 0), 12).deriveFont(p.textSize)
        }

        /** Source over destination (not premultiplied), with extra opacity [pa]. */
        internal val MIXES = setOf(BlendMode.PLUS, BlendMode.SCREEN, BlendMode.OVERLAY, BlendMode.DARKEN, BlendMode.LIGHTEN,
            BlendMode.SOFT_LIGHT, BlendMode.HARD_LIGHT, BlendMode.DIFFERENCE, BlendMode.EXCLUSION, BlendMode.MULTIPLY,
            BlendMode.COLOR_DODGE, BlendMode.COLOR_BURN)

        /** Separable blend modes (W3C compositing): result = mix of source and backdrop by [mode]. */
        internal fun mixBlend(s: Int, d: Int, pa: Int, mode: BlendMode): Int {
            var sa = ((s ushr 24) and 0xFF) / 255f
            if (pa < 255) sa *= pa / 255f
            if (sa <= 0f) return d
            val da = ((d ushr 24) and 0xFF) / 255f
            val oa = sa + da * (1f - sa)
            if (oa <= 0f) return 0
            fun b(cs: Float, cb: Float): Float = when (mode) {
                BlendMode.MULTIPLY -> cs * cb
                BlendMode.SCREEN -> cs + cb - cs * cb
                BlendMode.OVERLAY -> if (cb <= 0.5f) 2f * cs * cb else 1f - 2f * (1f - cs) * (1f - cb)
                BlendMode.HARD_LIGHT -> if (cs <= 0.5f) 2f * cs * cb else 1f - 2f * (1f - cs) * (1f - cb)
                BlendMode.DARKEN -> minOf(cs, cb)
                BlendMode.LIGHTEN -> maxOf(cs, cb)
                BlendMode.DIFFERENCE -> kotlin.math.abs(cs - cb)
                BlendMode.EXCLUSION -> cs + cb - 2f * cs * cb
                BlendMode.PLUS -> minOf(1f, cs + cb)
                BlendMode.COLOR_DODGE -> if (cb == 0f) 0f else if (cs >= 1f) 1f else minOf(1f, cb / (1f - cs))
                BlendMode.COLOR_BURN -> if (cb >= 1f) 1f else if (cs <= 0f) 0f else 1f - minOf(1f, (1f - cb) / cs)
                BlendMode.SOFT_LIGHT -> if (cs <= 0.5f) cb - (1f - 2f * cs) * cb * (1f - cb) else {
                    val dd = if (cb <= 0.25f) ((16f * cb - 12f) * cb + 4f) * cb else kotlin.math.sqrt(cb)
                    cb + (2f * cs - 1f) * (dd - cb)
                }
                else -> cs
            }
            fun ch(sh: Int): Int {
                val cs = ((s shr sh) and 0xFF) / 255f; val cb = ((d shr sh) and 0xFF) / 255f
                val co = (1f - da) * sa * cs + (1f - sa) * da * cb + sa * da * b(cs, cb)
                return (co / oa * 255f + 0.5f).toInt().coerceIn(0, 255)
            }
            return ((oa * 255f + 0.5f).toInt().coerceIn(0, 255) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }

        internal fun blend(s: Int, d: Int, pa: Int, mode: PorterDuff.Mode?): Int {
            if (mode == PorterDuff.Mode.CLEAR) return 0
            if (mode == PorterDuff.Mode.SRC) return s
            var sa = (s ushr 24) and 0xFF
            if (pa < 255) sa = sa * pa / 255
            if (sa == 255) return s
            if (sa == 0) return d
            val da = (d ushr 24) and 0xFF
            if (mode == PorterDuff.Mode.DST_IN) return ((da * sa / 255) shl 24) or (d and 0xFFFFFF)
            val oa = sa + da * (255 - sa) / 255
            if (oa == 0) return 0
            fun ch(sh: Int): Int {
                val sc = (s shr sh) and 0xFF; val dc = (d shr sh) and 0xFF
                return ((sc * sa + dc * da * (255 - sa) / 255) / oa).coerceIn(0, 255)
            }
            return (oa shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }

        internal fun applyMatrix(m: FloatArray, c: Int): Int {
            val a = (c ushr 24) and 0xFF; val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
            fun row(i: Int) = (m[i] * r + m[i + 1] * g + m[i + 2] * b + m[i + 3] * a + m[i + 4]).toInt().coerceIn(0, 255)
            return (row(15) shl 24) or (row(0) shl 16) or (row(5) shl 8) or row(10)
        }

        /** Three box passes ≈ gaussian. */
        internal fun boxBlur(a: IntArray, w: Int, h: Int, r: Int) {
            val tmp = IntArray(a.size)
            repeat(3) {
                for (y in 0 until h) {
                    var sum = 0; val o = y * w
                    for (x in -r..r) sum += a[o + x.coerceIn(0, w - 1)]
                    for (x in 0 until w) {
                        tmp[o + x] = sum / (2 * r + 1)
                        sum += a[o + (x + r + 1).coerceAtMost(w - 1)] - a[o + (x - r).coerceAtLeast(0)]
                    }
                }
                for (x in 0 until w) {
                    var sum = 0
                    for (y in -r..r) sum += tmp[y.coerceIn(0, h - 1) * w + x]
                    for (y in 0 until h) {
                        a[y * w + x] = sum / (2 * r + 1)
                        sum += tmp[(y + r + 1).coerceAtMost(h - 1) * w + x] - tmp[(y - r).coerceAtLeast(0) * w + x]
                    }
                }
            }
        }
    }
}

/** Reads pictures from files (JPEG, PNG, BMP, GIF; more via the app's own readers). */
object BitmapFactory {
    class Options {
        @JvmField var inJustDecodeBounds = false
        @JvmField var inSampleSize = 1
        @JvmField var inMutable = false
        @JvmField var inPreferredConfig: Bitmap.Config = Bitmap.Config.ARGB_8888
        @JvmField var outWidth = 0
        @JvmField var outHeight = 0
        @JvmField var outMimeType: String? = null
        @JvmField var inPremultiplied = true
        @JvmField var inScaled = true
        @JvmField var inDensity = 0
        @JvmField var inTargetDensity = 0
    }

    fun decodeFile(path: String): Bitmap? = decodeFile(path, null)
    fun decodeFile(path: String, o: Options?): Bitmap? = try { java.io.File(path).inputStream().use { decodeStream(it, null, o) } } catch (_: Throwable) { null }
    fun decodeByteArray(b: ByteArray, off: Int, len: Int): Bitmap? = decodeStream(java.io.ByteArrayInputStream(b, off, len), null, null)
    fun decodeByteArray(b: ByteArray, off: Int, len: Int, o: Options?): Bitmap? = decodeStream(java.io.ByteArrayInputStream(b, off, len), null, o)
    fun decodeStream(s: java.io.InputStream): Bitmap? = decodeStream(s, null, null)

    fun decodeStream(s: java.io.InputStream, pad: Rect?, o: Options?): Bitmap? = try {
        val iis = javax.imageio.ImageIO.createImageInputStream(s)
        val readers = javax.imageio.ImageIO.getImageReaders(iis)
        if (!readers.hasNext()) null else {
            val rd = readers.next()
            rd.input = iis
            o?.outWidth = rd.getWidth(0); o?.outHeight = rd.getHeight(0); o?.outMimeType = "image/" + rd.formatName.lowercase()
            if (o?.inJustDecodeBounds == true) { rd.dispose(); null } else {
                val p = rd.defaultReadParam
                val ss = (o?.inSampleSize ?: 1).coerceAtLeast(1)
                if (ss > 1) p.setSourceSubsampling(ss, ss, 0, 0)
                val img = rd.read(0, p)
                rd.dispose()
                Bitmap.fromImage(img)
            }
        }
    } catch (_: Throwable) { null }
}
