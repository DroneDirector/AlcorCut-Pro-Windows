package android.graphics

import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.awt.image.DirectColorModel
import java.awt.image.Raster
import java.awt.image.SinglePixelPackedSampleModel
import java.io.OutputStream
import javax.imageio.ImageIO

/**
 * Pixel picture for the Windows app: 32-bit ARGB (not premultiplied), kept in one int array so the
 * shared image code works on it exactly as on the phone. Java2D draws straight into the same array.
 */
class Bitmap private constructor(private var w: Int, private var h: Int, internal var data: IntArray, val config: Config) {
    /** Bumped whenever the pixels change, so the on-screen copy is only made again when needed. */
    @Volatile @JvmField internal var gen = 0
    enum class Config { ARGB_8888, RGB_565, ALPHA_8, RGBA_F16, HARDWARE }
    enum class CompressFormat { JPEG, PNG, WEBP, WEBP_LOSSY, WEBP_LOSSLESS }

    val width: Int get() = w
    val height: Int get() = h
    var isRecycled = false
        private set
    val isMutable: Boolean get() = true
    private var alpha = true
    fun hasAlpha() = alpha
    fun setHasAlpha(v: Boolean) { alpha = v }
    val byteCount: Int get() = w * h * 4
    val allocationByteCount: Int get() = data.size * 4
    var density: Int = 0
    /** Colour space of the pixels (sRGB unless made for another space). */
    var colorSpace: ColorSpace? = ColorSpace.get(ColorSpace.Named.SRGB)
        internal set
    var gainmap: Gainmap? = null
    fun hasGainmap() = gainmap != null

    /** A Java2D view onto the same pixels (drawing into it changes this bitmap). */
    val image: BufferedImage by lazy {
        val cm = DirectColorModel(32, 0x00FF0000, 0x0000FF00, 0x000000FF, -0x1000000)
        val sm = SinglePixelPackedSampleModel(java.awt.image.DataBuffer.TYPE_INT, w, h, intArrayOf(0x00FF0000, 0x0000FF00, 0x000000FF, -0x1000000))
        val raster = Raster.createWritableRaster(sm, DataBufferInt(data, data.size), null)
        BufferedImage(cm, raster, false, null)
    }

    fun getPixel(x: Int, y: Int): Int = data[y * w + x]
    fun setPixel(x: Int, y: Int, c: Int) { data[y * w + x] = c; gen++ }

    fun getPixels(out: IntArray, offset: Int, stride: Int, x: Int, y: Int, width: Int, height: Int) {
        for (r in 0 until height) System.arraycopy(data, (y + r) * w + x, out, offset + r * stride, width)
    }

    fun setPixels(src: IntArray, offset: Int, stride: Int, x: Int, y: Int, width: Int, height: Int) {
        gen++
        for (r in 0 until height) System.arraycopy(src, offset + r * stride, data, (y + r) * w + x, width)
    }

    fun eraseColor(c: Int) { data.fill(c); gen++ }

    /** Raw RGBA bytes in / out (as the phone lays them out). */
    fun copyPixelsFromBuffer(buf: java.nio.Buffer) {
        gen++
        val b = buf as java.nio.ByteBuffer
        for (i in data.indices) {
            val r = b.get().toInt() and 0xFF; val g = b.get().toInt() and 0xFF; val bl = b.get().toInt() and 0xFF; val a = b.get().toInt() and 0xFF
            data[i] = (a shl 24) or (r shl 16) or (g shl 8) or bl
        }
    }
    fun copyPixelsToBuffer(buf: java.nio.Buffer) {
        val b = buf as java.nio.ByteBuffer
        for (p in data) { b.put((p shr 16).toByte()); b.put((p shr 8).toByte()); b.put(p.toByte()); b.put((p ushr 24).toByte()) }
    }
    val rowBytes: Int get() = w * 4
    fun prepareToDraw() {}
    fun reconfigure(width: Int, height: Int, config: Config) { gen++; this.w = width; this.h = height; if (data.size < width * height) data = IntArray(width * height) }

    fun copy(config: Config, mutable: Boolean): Bitmap = Bitmap(w, h, data.copyOf(), Config.ARGB_8888).also { it.alpha = alpha; it.colorSpace = colorSpace }

    fun recycle() { isRecycled = true }

    fun sameAs(o: Bitmap?): Boolean = o != null && o.w == w && o.h == h && o.data.contentEquals(data)

    fun compress(format: CompressFormat, quality: Int, out: OutputStream): Boolean = try {
        when (format) {
            CompressFormat.PNG, CompressFormat.WEBP_LOSSLESS -> ImageIO.write(image, "png", out)
            else -> {
                // The app's own JPEG encoder (full colour detail), with the colour profile when not sRGB.
                val cs = colorSpace
                val icc = if (cs != null && cs != ColorSpace.get(ColorSpace.Named.SRGB)) ColorSpace.icc(cs) else null
                com.dronedirector.veyrocutpro.editor.JpegMax.write(out, w, h, quality, icc) { y, n, dst -> System.arraycopy(data, y * w, dst, 0, n * w) }
                true
            }
        }
    } catch (_: Throwable) { false }

    companion object {
        fun createBitmap(width: Int, height: Int, config: Config): Bitmap = Bitmap(width, height, IntArray(width * height), config)
        fun createBitmap(width: Int, height: Int, config: Config, hasAlpha: Boolean): Bitmap = createBitmap(width, height, config).also { it.alpha = hasAlpha }
        fun createBitmap(width: Int, height: Int, config: Config, hasAlpha: Boolean, cs: ColorSpace): Bitmap = createBitmap(width, height, config).also { it.alpha = hasAlpha; it.colorSpace = cs }
        fun createBitmap(px: IntArray, width: Int, height: Int, config: Config): Bitmap = Bitmap(width, height, px.copyOf(width * height), config)
        fun createBitmap(px: IntArray, offset: Int, stride: Int, width: Int, height: Int, config: Config): Bitmap {
            val b = createBitmap(width, height, config); b.setPixels(px, offset, stride, 0, 0, width, height); return b
        }
        fun createBitmap(src: Bitmap): Bitmap = src.copy(Config.ARGB_8888, true)
        fun createBitmap(src: Bitmap, x: Int, y: Int, width: Int, height: Int): Bitmap {
            val b = createBitmap(width, height, Config.ARGB_8888)
            src.getPixels(b.data, 0, width, x, y, width, height)
            return b
        }
        fun createBitmap(src: Bitmap, x: Int, y: Int, width: Int, height: Int, m: Matrix?, filter: Boolean): Bitmap {
            val part = createBitmap(src, x, y, width, height)
            if (m == null || m.isIdentity) return part
            val r = RectF(0f, 0f, width.toFloat(), height.toFloat())
            m.mapRect(r)
            val out = createBitmap(Math.round(r.width()).coerceAtLeast(1), Math.round(r.height()).coerceAtLeast(1), Config.ARGB_8888)
            val mm = Matrix(m).apply { postTranslate(-r.left, -r.top) }
            Canvas(out).drawBitmap(part, mm, null)
            return out
        }

        /** Smooth resize: area average when shrinking, bilinear when growing. */
        fun createScaledBitmap(src: Bitmap, dw: Int, dh: Int, filter: Boolean): Bitmap {
            if (dw == src.w && dh == src.h) return src
            val out = createBitmap(dw, dh, Config.ARGB_8888)
            Resample.scale(src.data, src.w, src.h, out.data, dw, dh)
            out.alpha = src.alpha
            return out
        }

        /** Wraps a Java2D image (copied into ARGB). */
        fun fromImage(img: BufferedImage): Bitmap {
            val b = createBitmap(img.width, img.height, Config.ARGB_8888)
            img.getRGB(0, 0, img.width, img.height, b.data, 0, img.width)
            b.alpha = img.colorModel.hasAlpha()
            return b
        }
    }
}

/** Resizing used by the bitmap and canvas (own code). */
internal object Resample {
    fun scale(s: IntArray, sw: Int, sh: Int, d: IntArray, dw: Int, dh: Int) {
        if (dw <= sw && dh <= sh) area(s, sw, sh, d, dw, dh) else bilinear(s, sw, sh, d, dw, dh)
    }

    private fun area(s: IntArray, sw: Int, sh: Int, d: IntArray, dw: Int, dh: Int) {
        val fx = sw.toDouble() / dw; val fy = sh.toDouble() / dh
        java.util.stream.IntStream.range(0, dh).parallel().forEach { y ->
            val y0 = y * fy; val y1 = (y + 1) * fy
            for (x in 0 until dw) {
                val x0 = x * fx; val x1 = (x + 1) * fx
                var a = 0.0; var r = 0.0; var g = 0.0; var b = 0.0; var wt = 0.0
                var yy = y0.toInt()
                while (yy < y1 && yy < sh) {
                    val wy = minOf(y1, yy + 1.0) - maxOf(y0, yy.toDouble())
                    var xx = x0.toInt()
                    while (xx < x1 && xx < sw) {
                        val wx = minOf(x1, xx + 1.0) - maxOf(x0, xx.toDouble())
                        val k = wx * wy
                        val p = s[yy * sw + xx]
                        a += ((p ushr 24) and 0xFF) * k; r += ((p shr 16) and 0xFF) * k; g += ((p shr 8) and 0xFF) * k; b += (p and 0xFF) * k
                        wt += k
                        xx++
                    }
                    yy++
                }
                if (wt <= 0) wt = 1.0
                d[y * dw + x] = ((a / wt + 0.5).toInt() shl 24) or ((r / wt + 0.5).toInt() shl 16) or ((g / wt + 0.5).toInt() shl 8) or (b / wt + 0.5).toInt()
            }
        }
    }

    private fun bilinear(s: IntArray, sw: Int, sh: Int, d: IntArray, dw: Int, dh: Int) {
        val fx = sw.toFloat() / dw; val fy = sh.toFloat() / dh
        java.util.stream.IntStream.range(0, dh).parallel().forEach { y ->
            val sy = (y + 0.5f) * fy - 0.5f
            for (x in 0 until dw) d[y * dw + x] = sample(s, sw, sh, (x + 0.5f) * fx - 0.5f, sy)
        }
    }

    /** Bilinear sample at (x, y), clamped to the edges. */
    fun sample(s: IntArray, sw: Int, sh: Int, x: Float, y: Float): Int {
        val cx = x.coerceIn(0f, sw - 1f); val cy = y.coerceIn(0f, sh - 1f)
        val x0 = cx.toInt(); val y0 = cy.toInt()
        val x1 = minOf(x0 + 1, sw - 1); val y1 = minOf(y0 + 1, sh - 1)
        val tx = cx - x0; val ty = cy - y0
        val p00 = s[y0 * sw + x0]; val p10 = s[y0 * sw + x1]; val p01 = s[y1 * sw + x0]; val p11 = s[y1 * sw + x1]
        var out = 0
        var sh2 = 24
        while (sh2 >= 0) {
            val a = (p00 ushr sh2) and 0xFF; val b = (p10 ushr sh2) and 0xFF; val c = (p01 ushr sh2) and 0xFF; val e = (p11 ushr sh2) and 0xFF
            val top = a + (b - a) * tx; val bot = c + (e - c) * tx
            out = out or (((top + (bot - top) * ty) + 0.5f).toInt().coerceIn(0, 255) shl sh2)
            sh2 -= 8
        }
        return out
    }
}
