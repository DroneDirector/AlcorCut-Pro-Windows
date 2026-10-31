package com.dronedirector.veyrocutpro.editor

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** More visualizer and countdown looks (Layers → OVERLAYS). Own code. */
object FxStyles {
    private const val TAU = (2 * PI).toFloat()

    val MORE_VIS = listOf(
        "Dot matrix", "Neon bars", "Skyline", "Bubbles", "Oscilloscope", "Mountains", "8-bit blocks", "Flames", "Ribbons", "Aurora",
        "Sunburst", "Hexagon", "Spiral", "Kaleidoscope", "Star burst", "Vinyl", "Heartbeat", "Twin rings", "Flower", "Gauge",
    )
    /** Round visualizer styles among the extra ones (style numbers). */
    val ROUND_VIS = setOf(20, 21, 22, 23, 24, 25, 27, 28, 29)

    val MORE_TIMERS = listOf(
        "Digital", "Dotted ring", "Segments", "Double ring", "Pie", "Square", "Bar under", "Neon", "Film leader", "Hourglass",
        "Flip clock", "Minimal", "Pulse", "Radar", "Rainbow ring", "Clock ticks", "Battery", "Liquid", "Dots", "Scroll wheel bar",
    )

    fun hash(a: Int, b: Int, c: Int): Float {
        var h = a * 374761393 + b * 668265263 + c * 1274126177
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    private fun glowPaint(r: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply { maskFilter = BlurMaskFilter(max(3f, r), BlurMaskFilter.Blur.NORMAL) }
    /**
     * A soft glow drawn cheaply: [block] draws at a sixth of the size and the result is stretched back up with
     * smoothing, which blurs it (a blur filter on big shapes is far too slow for a live preview).
     */
    private fun glowPass(c: Canvas, w: Int, h: Int, k: Int = 6, block: (Canvas) -> Unit) {
        val sw = max(1, w / k); val sh = max(1, h / k)
        val b = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
        val cc = Canvas(b); cc.scale(sw / w.toFloat(), sh / h.toFloat())
        block(cc)
        c.drawBitmap(b, null, android.graphics.RectF(0f, 0f, w.toFloat(), h.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        b.recycle()
    }
    private fun withAlpha(c: Int, a: Float) = (c and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255).toInt() shl 24)

    /**
     * Visualizer styles 10–29. [v] band levels, [pk] held peaks, [beat] 0..1, [t] song time, [col] colour along 0..1,
     * [data] / [at] the whole analysis and this frame's place in it (for looks that show the recent past).
     */
    fun vis(style: Int, c: Canvas, w: Int, h: Int, v: FloatArray, pk: FloatArray, beat: Float, t: Long, col: (Float) -> Int, data: FloatArray?, at: Int?) {
        val nb = v.size
        fun lv(i: Int, n: Int): Float = v[(i.toFloat() / n * nb).toInt().coerceIn(0, nb - 1)]
        fun pkv(i: Int, n: Int): Float = pk[(i.toFloat() / n * nb).toInt().coerceIn(0, nb - 1)]
        val fw = w.toFloat(); val fh = h.toFloat()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val g = glowPaint(min(fw, fh) * 0.03f * (1f + beat))
        val cx = fw / 2f; val cy = fh / 2f
        val side = min(fw, fh)
        when (style) {
            10 -> { // Dot matrix: a grid of round dots lit up to each column's level.
                val cols = 40; val rows = 14
                val dx = fw / cols; val dy = fh / rows; val r = min(dx, dy) * 0.34f
                for (i in 0 until cols) {
                    val lit = lv(i, cols) * rows * (1f + 0.1f * beat)
                    val n = kotlin.math.ceil(lit).toInt().coerceIn(0, rows)
                    // One soft glow behind the lit dots of the column (a blur per dot is far too slow).
                    if (n > 0) {
                        val x = (i + 0.5f) * dx
                        g.color = col((n - 1) / (rows - 1f) * 0.5f); g.alpha = 120
                        c.drawRoundRect(x - r * 1.5f, fh - n * dy, x + r * 1.5f, fh, r * 1.5f, r * 1.5f, g)
                    }
                    for (j in 0 until rows) {
                        val x = (i + 0.5f) * dx; val y = fh - (j + 0.5f) * dy
                        val cc = col(j / (rows - 1f))
                        p.color = if (j < n) cc else withAlpha(cc, 0.12f)
                        c.drawCircle(x, y, r, p)
                    }
                }
            }
            11 -> { // Neon bars: thin tubes of light with a strong glow and no fill.
                val n = 36; val gap = fw / n
                p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.strokeWidth = max(2f, gap * 0.16f)
                g.style = Paint.Style.STROKE; g.strokeCap = Paint.Cap.ROUND; g.strokeWidth = p.strokeWidth * (3f + 2f * beat)
                for (i in 0 until n) {
                    val x = (i + 0.5f) * gap; val hh = (fh * 0.9f) * (0.04f + 0.96f * lv(i, n))
                    val cc = col(i / (n - 1f))
                    g.color = cc; g.alpha = (150 + 100 * beat).toInt().coerceAtMost(255); c.drawLine(x, fh - p.strokeWidth, x, fh - hh, g)
                    p.color = Color.WHITE; c.drawLine(x, fh - p.strokeWidth, x, fh - hh, p)
                }
            }
            12 -> { // Skyline: a city of towers rising with the music, windows lit by the highs.
                val n = 26; val bw = fw / n
                var high = 0f; for (k in nb * 3 / 4 until nb) high += v[k]; high /= (nb / 4)
                for (i in 0 until n) {
                    val seed = hash(i, 4, 0)
                    val hh = fh * (0.12f + 0.5f * seed + 0.38f * lv(i, n))
                    val x0 = i * bw + bw * 0.06f; val x1 = (i + 1) * bw - bw * 0.06f
                    p.shader = LinearGradient(0f, fh - hh, 0f, fh, col(0.2f + 0.6f * seed), withAlpha(col(1f), 1f) and 0xFF7F7F7F.toInt(), Shader.TileMode.CLAMP)
                    c.drawRect(x0, fh - hh, x1, fh, p); p.shader = null
                    val ww = (x1 - x0) / 4f
                    var y = fh - hh + ww
                    var row = 0
                    while (y < fh - ww) {
                        for (q in 0 until 2) {
                            val on = hash(i, row * 2 + q, (t / 400).toInt()) < 0.25f + 0.6f * high
                            p.color = if (on) 0xFFFFE9A8.toInt() else 0x33000000
                            c.drawRect(x0 + ww * (0.6f + q * 1.8f), y, x0 + ww * (1.4f + q * 1.8f), y + ww * 0.8f, p)
                        }
                        y += ww * 1.6f; row++
                    }
                }
            }
            13 -> { // Bubbles: bubbles rise from each band, bigger when it's louder.
                val n = 18
                p.style = Paint.Style.STROKE; p.strokeWidth = max(1.5f, side * 0.012f)
                for (i in 0 until n) for (b in 0 until 4) {
                    val ph = ((t / 2600f) + hash(i, b, 1)) % 1f
                    val x = (i + 0.5f) * fw / n + sin(ph * 9f + i) * fw / n * 0.25f
                    val y = fh * (1f - ph)
                    val r = side * (0.03f + 0.12f * lv(i, n)) * (0.6f + 0.4f * hash(i, b, 2))
                    val cc = col(i / (n - 1f))
                    p.color = withAlpha(cc, 0.9f * (1f - ph * 0.7f)); c.drawCircle(x, y, r, p)
                    val f = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(Color.WHITE, 0.5f * (1f - ph)) }
                    c.drawCircle(x - r * 0.35f, y - r * 0.35f, r * 0.18f, f)
                }
            }
            14 -> { // Oscilloscope: a glowing trace built from the music's own frequencies.
                val path = Path()
                val n = 220
                for (i in 0..n) {
                    val x = i * fw / n
                    var y = 0f
                    for (b in 0 until nb step 4) y += v[b] * sin(i * (b + 2) * 0.045f + t * 0.004f * (1 + b % 5)) / (1f + b * 0.08f)
                    val yy = cy - y * fh * 0.16f * (1f + 0.3f * beat)
                    if (i == 0) path.moveTo(x, yy) else path.lineTo(x, yy)
                }
                p.style = Paint.Style.STROKE; p.strokeWidth = max(2f, fh * 0.02f); p.strokeJoin = Paint.Join.ROUND; p.color = col(0.4f)
                g.style = Paint.Style.STROKE; g.strokeWidth = p.strokeWidth * 4f; g.color = col(0.4f); g.alpha = 200
                // Faint screen grid.
                val grid = Paint().apply { color = withAlpha(col(0.4f), 0.15f); strokeWidth = 1f }
                for (k in 1 until 8) c.drawLine(k * fw / 8, 0f, k * fw / 8, fh, grid)
                for (k in 1 until 4) c.drawLine(0f, k * fh / 4, fw, k * fh / 4, grid)
                c.drawPath(path, g); c.drawPath(path, p)
            }
            15 -> { // Mountains: three ridges (bass at the back, highs in front) that rise and fall.
                for (layer in 0 until 3) {
                    val path = Path(); path.moveTo(0f, fh)
                    val n = 60
                    for (i in 0..n) {
                        val f = i.toFloat() / n
                        val band = (layer * nb / 3 + (f * (nb / 3 - 1))).toInt().coerceIn(0, nb - 1)
                        val hill = 0.25f + 0.25f * sin(f * (5f + layer * 3f) + layer + t / 6000f)
                        val y = fh - fh * (0.15f + 0.45f * hill * (0.5f + v[band])) * (1f - layer * 0.18f)
                        path.lineTo(f * fw, y)
                    }
                    path.lineTo(fw, fh); path.close()
                    p.shader = LinearGradient(0f, 0f, 0f, fh, col(layer / 2f), withAlpha(col(layer / 2f), 0.3f), Shader.TileMode.CLAMP)
                    p.alpha = 150 + layer * 50
                    c.drawPath(path, p)
                }
                p.shader = null
            }
            16 -> { // 8-bit blocks: chunky retro squares stacked up.
                val n = 20; val bs = fw / n
                val rows = (fh / bs).toInt().coerceAtLeast(2)
                for (i in 0 until n) {
                    val lit = (lv(i, n) * rows * (1f + 0.1f * beat)).toInt().coerceIn(1, rows)
                    for (j in 0 until lit) {
                        val cc = col(j / max(1f, rows - 1f))
                        p.color = cc
                        c.drawRect(i * bs + bs * 0.08f, fh - (j + 1) * bs + bs * 0.08f, (i + 1) * bs - bs * 0.08f, fh - j * bs - bs * 0.08f, p)
                        p.color = withAlpha(Color.WHITE, 0.35f)
                        c.drawRect(i * bs + bs * 0.08f, fh - (j + 1) * bs + bs * 0.08f, (i + 1) * bs - bs * 0.08f, fh - (j + 1) * bs + bs * 0.24f, p)
                    }
                    val pj = (pkv(i, n) * rows).toInt().coerceIn(1, rows)
                    p.color = Color.WHITE
                    c.drawRect(i * bs + bs * 0.08f, fh - pj * bs + bs * 0.08f, (i + 1) * bs - bs * 0.08f, fh - pj * bs + bs * 0.3f, p)
                }
            }
            17 -> { // Flames: each band a flickering tongue of fire.
                val n = 28; val bw = fw / n
                for (i in 0 until n) {
                    val hh = fh * (0.08f + 0.9f * lv(i, n)) * (0.9f + 0.2f * hash(i, (t / 60).toInt(), 3))
                    val x = (i + 0.5f) * bw
                    val path = Path()
                    path.moveTo(x - bw * 0.55f, fh)
                    path.cubicTo(x - bw * 0.6f, fh - hh * 0.5f, x + bw * 0.3f * sin(t / 150f + i), fh - hh * 0.7f, x, fh - hh)
                    path.cubicTo(x - bw * 0.3f * sin(t / 170f + i), fh - hh * 0.7f, x + bw * 0.6f, fh - hh * 0.5f, x + bw * 0.55f, fh)
                    path.close()
                    p.shader = LinearGradient(0f, fh - hh, 0f, fh, intArrayOf(0x00FFD27A, 0xFFFF8A1F.toInt(), 0xFFFF2E1F.toInt()), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
                    c.drawPath(path, p)
                }
                p.shader = null
                val ga = (60 + 120 * beat).toInt()
                glowPass(c, w, h) { gc -> gc.drawRect(0f, fh * 0.8f, fw, fh, Paint().apply { color = 0xFFFF6A00.toInt(); alpha = ga }) }
            }
            18 -> { // Ribbons: three flowing glowing lines for bass, mids and highs.
                val paths = ArrayList<Pair<Path, Int>>()
                for (r in 0 until 3) {
                    val path = Path()
                    val n = 90
                    for (i in 0..n) {
                        val f = i.toFloat() / n
                        val band = (r * nb / 3 + f * (nb / 3 - 1)).toInt().coerceIn(0, nb - 1)
                        val y = cy + sin(f * TAU * (1.2f + r * 0.5f) + t / (900f + r * 300f) + r) * fh * 0.35f * (0.2f + v[band])
                        if (i == 0) path.moveTo(f * fw, y) else path.lineTo(f * fw, y)
                    }
                    paths += path to col(r / 2f)
                }
                val sw = max(2f, fh * 0.018f)
                glowPass(c, w, h) { gc ->
                    val gp = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.STROKE; strokeWidth = sw * (4f + 2f * beat); strokeJoin = Paint.Join.ROUND }
                    for ((path, cc) in paths) { gp.color = cc; gp.alpha = 190; gc.drawPath(path, gp) }
                }
                p.style = Paint.Style.STROKE; p.strokeWidth = sw; p.strokeJoin = Paint.Join.ROUND
                for ((path, cc) in paths) { p.color = cc; c.drawPath(path, p) }
            }
            19 -> { // Aurora: soft curtains of light that sway with the music.
                val n = 24
                glowPass(c, w, h, 4) { gc ->
                    val ap = Paint(Paint.ANTI_ALIAS_FLAG)
                    for (i in 0 until n) {
                        val x = (i + 0.5f) * fw / n + sin(t / 1300f + i * 0.7f) * fw / n
                        val hh = fh * (0.25f + 0.75f * lv(i, n))
                        val cc = col(i / (n - 1f))
                        ap.shader = LinearGradient(0f, fh - hh, 0f, fh, intArrayOf(withAlpha(cc, 0f), withAlpha(cc, 0.55f + 0.3f * beat), withAlpha(cc, 0f)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
                        gc.drawRect(x - fw / n * 0.6f, fh - hh, x + fw / n * 0.6f, fh, ap)
                    }
                }
            }
            20 -> { // Sunburst: rays shooting out from the middle.
                val n = 90; val base = side * 0.1f * (1f + 0.3f * beat)
                p.strokeCap = Paint.Cap.ROUND
                for (i in 0 until n) {
                    val a = i.toFloat() / n * TAU + t / 8000f
                    val f = if (i < n / 2) i / (n / 2f) else (n - i) / (n / 2f)
                    val len = side * 0.4f * (0.05f + 0.95f * lv((f * 40).toInt(), 40))
                    p.color = col(f); p.strokeWidth = max(1.5f, side * 0.006f)
                    c.drawLine(cx + base * cos(a), cy + base * sin(a), cx + (base + len) * cos(a), cy + (base + len) * sin(a), p)
                }
                p.shader = RadialGradient(cx, cy, base * 2.5f, intArrayOf(Color.WHITE, withAlpha(col(0f), 0.8f), 0), null, Shader.TileMode.CLAMP)
                c.drawCircle(cx, cy, base * 2.5f, p); p.shader = null
            }
            21 -> { // Hexagon: a six-sided spectrum shape that turns and swells.
                val n = 6 * 24
                val path = Path()
                val rot = t / 7000f
                for (i in 0..n) {
                    val k = i % n
                    val a = k.toFloat() / n * TAU + rot
                    val seg = (k % 24) / 23f
                    val corner = 1f / cos(((a - rot) % (TAU / 6)) - TAU / 12)
                    val r = side * (0.22f + 0.2f * lv((abs(seg - 0.5f) * 2f * 39).toInt(), 40)) * corner * (1f + 0.06f * beat)
                    val x = cx + r * cos(a); val y = cy + r * sin(a)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                val sweep = SweepGradient(cx, cy, IntArray(7) { col(if (it <= 3) it / 3f else (6 - it) / 3f) }, null)
                p.style = Paint.Style.STROKE; p.strokeWidth = max(2f, side * 0.012f); p.strokeJoin = Paint.Join.ROUND; p.shader = sweep
                g.style = Paint.Style.STROKE; g.strokeWidth = p.strokeWidth * 4f; g.shader = sweep; g.alpha = 180
                c.drawPath(path, g); c.drawPath(path, p)
            }
            22 -> { // Spiral: bars wound round a turning spiral.
                val n = 110
                p.strokeCap = Paint.Cap.ROUND; p.strokeWidth = max(1.5f, side * 0.008f)
                for (i in 0 until n) {
                    val f = i.toFloat() / n
                    val a = f * TAU * 2.5f + t / 5000f
                    val r = side * (0.06f + 0.34f * f)
                    val len = side * 0.09f * (0.1f + lv((f * 63).toInt(), 64)) * (1f + 0.3f * beat)
                    p.color = col(f)
                    c.drawLine(cx + r * cos(a), cy + r * sin(a), cx + (r + len) * cos(a), cy + (r + len) * sin(a), p)
                }
            }
            23 -> { // Kaleidoscope: an eight-way mirrored wave like a turning mandala.
                val petals = 8; val n = 40
                for (m in 0 until petals) {
                    val path = Path()
                    for (i in 0..n) {
                        val f = i.toFloat() / n
                        val a = (m + f) * TAU / petals + t / 9000f
                        val mf = if (f < 0.5f) f * 2f else (1f - f) * 2f
                        val r = side * (0.12f + 0.3f * lv((mf * 39).toInt(), 40)) * (1f + 0.08f * beat)
                        val x = cx + r * cos(a); val y = cy + r * sin(a)
                        if (i == 0) { path.moveTo(cx, cy); path.lineTo(x, y) } else path.lineTo(x, y)
                    }
                    path.close()
                    p.color = withAlpha(col(m / (petals - 1f)), 0.45f); c.drawPath(path, p)
                    p.style = Paint.Style.STROKE; p.strokeWidth = max(1.5f, side * 0.005f); p.color = col(m / (petals - 1f)); c.drawPath(path, p); p.style = Paint.Style.FILL
                }
            }
            24 -> { // Star burst: stars fly out from the middle, faster when the music is louder.
                var energy = 0f; for (k in 0 until nb) energy += v[k]; energy /= nb
                for (s in 0 until 160) {
                    val ph = ((t / (2200f - 1400f * energy)) + hash(s, 1, 9)) % 1f
                    val a = hash(s, 2, 9) * TAU
                    val r0 = side * 0.5f * ph * ph
                    val r1 = side * 0.5f * (ph * ph + 0.04f + 0.08f * beat)
                    p.color = withAlpha(col(hash(s, 3, 9)), ph); p.strokeWidth = max(1f, side * 0.004f * (0.5f + ph))
                    c.drawLine(cx + r0 * cos(a), cy + r0 * sin(a), cx + r1 * cos(a), cy + r1 * sin(a), p)
                }
            }
            25 -> { // Vinyl: a record spinning with grooves that light up with the music.
                val R = side * 0.46f
                p.color = 0xFF111214.toInt(); c.drawCircle(cx, cy, R, p)
                p.style = Paint.Style.STROKE
                for (gk in 0 until 22) {
                    val r = R * (0.38f + 0.6f * gk / 21f)
                    val band = (gk * nb / 22).coerceIn(0, nb - 1)
                    p.strokeWidth = max(1f, R * 0.012f); p.color = withAlpha(col(gk / 21f), 0.15f + 0.85f * v[band])
                    c.drawCircle(cx, cy, r, p)
                }
                p.style = Paint.Style.FILL
                val spin = (t / 1000f) * 200f
                p.shader = SweepGradient(cx, cy, intArrayOf(0x00FFFFFF, 0x33FFFFFF, 0x00FFFFFF, 0x00FFFFFF, 0x33FFFFFF, 0x00FFFFFF), null)
                    .also { it.setLocalMatrix(android.graphics.Matrix().apply { setRotate(spin, cx, cy) }) }
                c.drawCircle(cx, cy, R, p); p.shader = null
                p.color = col(0.3f); c.drawCircle(cx, cy, R * 0.33f * (1f + 0.05f * beat), p)
                p.color = 0xFF111214.toInt(); c.drawCircle(cx, cy, R * 0.03f, p)
            }
            26 -> { // Heartbeat: a heart-monitor line that spikes on each beat and scrolls along.
                val path = Path()
                val n = 160
                for (i in 0..n) {
                    val f = i.toFloat() / n
                    val back = ((1f - f) * 75).toInt()
                    val b = if (data != null && at != null) { val q = at - back * Spectrum.STRIDE; if (q >= 0) data[q + nb] else 0f } else 0f
                    val wob = sin(i * 1.7f) * 0.02f
                    val y = cy - fh * 0.42f * (b * (if (i % 2 == 0) 1f else -0.5f)) + wob * fh
                    if (i == 0) path.moveTo(f * fw, y) else path.lineTo(f * fw, y)
                }
                p.style = Paint.Style.STROKE; p.strokeWidth = max(2f, fh * 0.025f); p.strokeJoin = Paint.Join.ROUND; p.color = col(0.2f)
                val gw = p.strokeWidth * 4f; val gcCol = col(0.2f)
                glowPass(c, w, h) { gc -> gc.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.STROKE; strokeWidth = gw; strokeJoin = Paint.Join.ROUND; color = gcCol; alpha = 210 }) }
                c.drawPath(path, p)
                p.style = Paint.Style.FILL; p.color = Color.WHITE
                c.drawCircle(fw - p.strokeWidth * 2, cy, max(3f, fh * 0.03f) * (1f + beat), p)
            }
            27 -> { // Twin rings: two dotted rings turning opposite ways, dots growing with the music.
                for (ring in 0 until 2) {
                    val R = side * (if (ring == 0) 0.25f else 0.4f) * (1f + 0.05f * beat)
                    val n = if (ring == 0) 48 else 72
                    for (i in 0 until n) {
                        val a = i.toFloat() / n * TAU + (if (ring == 0) 1f else -1f) * t / 4000f
                        val f = if (i < n / 2) i / (n / 2f) else (n - i) / (n / 2f)
                        val e = lv(((if (ring == 0) f * 0.5f else 0.5f + f * 0.5f) * 63).toInt(), 64)
                        p.color = col(f); c.drawCircle(cx + R * cos(a), cy + R * sin(a), side * (0.004f + 0.016f * e), p)
                    }
                }
            }
            28 -> { // Flower: petals that open with their part of the music.
                val n = 12
                for (i in 0 until n) {
                    val f = if (i < n / 2) i / (n / 2f) else (n - i) / (n / 2f)
                    val len = side * (0.15f + 0.3f * lv((f * 63).toInt(), 64)) * (1f + 0.08f * beat)
                    c.save(); c.rotate(i * 360f / n + t / 120f, cx, cy)
                    val path = Path()
                    path.moveTo(cx, cy)
                    path.cubicTo(cx - len * 0.35f, cy - len * 0.35f, cx - len * 0.2f, cy - len, cx, cy - len)
                    path.cubicTo(cx + len * 0.2f, cy - len, cx + len * 0.35f, cy - len * 0.35f, cx, cy)
                    p.shader = LinearGradient(cx, cy, cx, cy - len, withAlpha(col(f), 0.9f), withAlpha(col(1f - f), 0.6f), Shader.TileMode.CLAMP)
                    c.drawPath(path, p); p.shader = null
                    c.restore()
                }
                p.color = Color.WHITE; c.drawCircle(cx, cy, side * 0.035f * (1f + 0.4f * beat), p)
            }
            else -> { // Gauge: a half-circle meter of bars, like a speedometer.
                val R = side * 0.42f; val base = R * 0.55f
                val n = 40
                p.strokeCap = Paint.Cap.ROUND; p.strokeWidth = max(2f, side * 0.014f)
                for (i in 0 until n) {
                    val a = PI.toFloat() + i.toFloat() / (n - 1) * PI.toFloat()
                    val e = lv(i, n)
                    val gy = cy + R * 0.25f
                    p.color = withAlpha(col(i / (n - 1f)), 0.18f)
                    c.drawLine(cx + base * cos(a), gy + base * sin(a), cx + R * cos(a), gy + R * sin(a), p)
                    p.color = col(i / (n - 1f))
                    c.drawLine(cx + base * cos(a), gy + base * sin(a), cx + (base + (R - base) * e) * cos(a), gy + (base + (R - base) * e) * sin(a), p)
                }
                var energy = 0f; for (k in 0 until nb) energy += v[k]; energy /= nb
                val na = PI.toFloat() + (energy * 1.4f).coerceIn(0f, 1f) * PI.toFloat()
                val gy = cy + R * 0.25f
                p.color = Color.WHITE; p.strokeWidth = max(2f, side * 0.01f)
                c.drawLine(cx, gy, cx + R * 0.9f * cos(na), gy + R * 0.9f * sin(na), p)
                c.drawCircle(cx, gy, side * 0.025f, p)
            }
        }
    }

    /**
     * Countdown styles 5–24. [k] share of time left (1 → 0), [left] / [el] ms left / gone, [txt] the time as text,
     * [col] the layer colour.
     */
    fun timer(style: Int, c: Canvas, side: Int, k: Float, left: Long, el: Long, txt: String, col: Int, still: Boolean) {
        val s = side.toFloat(); val cx = s / 2f; val cy = s / 2f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        val sec = el % 1000 / 1000f   // 0..1 within the current second
        val R = s * 0.4f
        val oval = RectF(cx - R, cy - R, cx + R, cy + R)
        var textSize = R * (if (txt.length <= 2) 0.95f else if (txt.length <= 4) 0.62f else 0.5f)
        var textColor = Color.WHITE
        var textX = cx
        var textY = cy
        var font: Typeface = Typeface.DEFAULT_BOLD
        var glow = 0f
        fun disc(a: Int = 0x8C000000.toInt()) { p.color = a; c.drawCircle(cx, cy, R + s * 0.06f, p) }
        when (style) {
            5 -> { // Digital: LCD-style digits in a dark panel.
                p.color = 0xCC0B0F0C.toInt(); c.drawRoundRect(RectF(s * 0.05f, s * 0.28f, s * 0.95f, s * 0.72f), s * 0.06f, s * 0.06f, p)
                font = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); textColor = col; glow = s * 0.05f; textSize *= 1.15f
            }
            6 -> { // Dotted ring: dots go dark one by one.
                disc(); val n = 40
                for (i in 0 until n) {
                    val a = -PI.toFloat() / 2 + i.toFloat() / n * TAU
                    p.color = if (i.toFloat() / n < k) col else 0x40FFFFFF
                    c.drawCircle(cx + R * cos(a), cy + R * sin(a), s * 0.022f, p)
                }
            }
            7 -> { // Segments: twelve blocks round the ring.
                disc(); ring.strokeWidth = s * 0.07f; ring.strokeCap = Paint.Cap.BUTT
                for (i in 0 until 12) {
                    ring.color = if ((i + 0.5f) / 12f < k) col else 0x30FFFFFF
                    c.drawArc(oval, -90f + i * 30f + 2f, 26f, false, ring)
                }
            }
            8 -> { // Double ring: the whole countdown outside, each second inside.
                disc(); ring.strokeWidth = s * 0.05f
                ring.color = 0x30FFFFFF; c.drawArc(oval, 0f, 360f, false, ring)
                ring.color = col; c.drawArc(oval, -90f, 360f * k, false, ring)
                val r2 = R * 0.78f; val o2 = RectF(cx - r2, cy - r2, cx + r2, cy + r2)
                ring.strokeWidth = s * 0.025f; ring.color = Color.WHITE; if (!still) c.drawArc(o2, -90f, 360f * (1f - sec), false, ring)
                textSize *= 0.85f
            }
            9 -> { // Pie with a rim.
                p.color = 0x8C000000.toInt(); c.drawCircle(cx, cy, R, p)
                p.color = withAlpha(col, 0.85f); c.drawArc(oval, -90f, 360f * k, true, p)
                ring.strokeWidth = s * 0.02f; ring.color = Color.WHITE; c.drawCircle(cx, cy, R, ring)
                textColor = Color.WHITE; glow = s * 0.03f
            }
            10 -> { // Square: a rounded square border that runs out.
                val r = RectF(s * 0.1f, s * 0.1f, s * 0.9f, s * 0.9f)
                p.color = 0x8C000000.toInt(); c.drawRoundRect(r, s * 0.12f, s * 0.12f, p)
                ring.strokeWidth = s * 0.05f; ring.color = 0x30FFFFFF; c.drawRoundRect(r, s * 0.12f, s * 0.12f, ring)
                val path = Path().apply { addRoundRect(r, s * 0.12f, s * 0.12f, Path.Direction.CW) }
                val pm = android.graphics.PathMeasure(path, true)
                val part = Path(); pm.getSegment(0f, pm.length * k, part, true)
                ring.color = col; c.drawPath(part, ring)
            }
            11 -> { // Bar under: a big number with a bar that shrinks under it.
                p.color = 0x8C000000.toInt(); c.drawRoundRect(RectF(s * 0.05f, s * 0.2f, s * 0.95f, s * 0.8f), s * 0.08f, s * 0.08f, p)
                p.color = 0x30FFFFFF; c.drawRoundRect(RectF(s * 0.15f, s * 0.66f, s * 0.85f, s * 0.71f), s * 0.025f, s * 0.025f, p)
                p.color = col; c.drawRoundRect(RectF(s * 0.15f, s * 0.66f, s * 0.15f + s * 0.7f * k, s * 0.71f), s * 0.025f, s * 0.025f, p)
                textY = cy - s * 0.06f
            }
            12 -> { // Neon: a glowing tube ring and glowing digits.
                ring.strokeWidth = s * 0.03f; ring.color = col
                val gr = Paint(ring).apply { strokeWidth = s * 0.09f; maskFilter = BlurMaskFilter(s * 0.05f, BlurMaskFilter.Blur.NORMAL) }
                c.drawArc(oval, -90f, 360f * k, false, gr); c.drawArc(oval, -90f, 360f * k, false, ring)
                textColor = Color.WHITE; glow = s * 0.06f
            }
            13 -> { // Film leader: the classic cinema countdown — rings, cross lines and a sweeping wedge each second.
                p.color = 0xFF2A2A2A.toInt(); c.drawRect(0f, 0f, s, s, p)
                p.color = 0xFF8A8A8A.toInt(); c.drawArc(RectF(cx - s, cy - s, cx + s, cy + s), -90f, 360f * (if (still) 0f else sec), true, p)
                ring.strokeWidth = s * 0.012f; ring.color = 0xFFE8E8E8.toInt(); ring.strokeCap = Paint.Cap.BUTT
                c.drawLine(cx, 0f, cx, s, ring); c.drawLine(0f, cy, s, cy, ring)
                c.drawCircle(cx, cy, R, ring); c.drawCircle(cx, cy, R * 0.8f, ring)
                textColor = 0xFF111111.toInt(); font = Typeface.create(Typeface.SERIF, Typeface.BOLD); textSize *= 1.1f
                // Film grain and scratches.
                for (q in 0 until 40) { p.color = if (hash(q, 1, (el / 40).toInt()) > 0.5f) 0x22FFFFFF else 0x22000000; c.drawCircle(hash(q, 2, (el / 40).toInt()) * s, hash(q, 3, (el / 40).toInt()) * s, s * 0.004f, p) }
            }
            14 -> { // Hourglass: sand runs from the top bulb to the bottom.
                val top = s * 0.12f; val bot = s * 0.88f; val wx = s * 0.3f
                val glass = Path().apply { moveTo(cx - wx, top); lineTo(cx + wx, top); lineTo(cx + s * 0.03f, cy); lineTo(cx + wx, bot); lineTo(cx - wx, bot); lineTo(cx - s * 0.03f, cy); close() }
                p.color = 0x55000000; c.drawPath(glass, p)
                p.color = col
                val tf = k; val bf = 1f - k
                val ty = cy - (cy - top) * tf * 0.85f
                c.save(); c.clipPath(glass)
                c.drawRect(cx - wx, ty, cx + wx, cy, p)
                c.drawRect(cx - wx, bot - (bot - cy) * bf * 0.85f, cx + wx, bot, p)
                if (k > 0f && !still) c.drawRect(cx - s * 0.008f, cy, cx + s * 0.008f, bot, p)
                c.restore()
                ring.strokeWidth = s * 0.015f; ring.color = 0xCCFFFFFF.toInt(); c.drawPath(glass, ring)
                // The time small above the glass.
                textSize *= 0.45f; textColor = Color.WHITE; glow = s * 0.03f; textY = s * 0.06f + textSize * 0.4f
            }
            15 -> { // Flip clock: dark cards with a split line.
                val n = txt.length.coerceAtLeast(1)
                val cw = s * 0.86f / n
                for (i in 0 until n) {
                    val r = RectF(s * 0.07f + i * cw + cw * 0.05f, s * 0.25f, s * 0.07f + (i + 1) * cw - cw * 0.05f, s * 0.75f)
                    if (txt[i] != ':') { p.color = 0xFF1C1C1E.toInt(); c.drawRoundRect(r, cw * 0.12f, cw * 0.12f, p); p.color = 0xFF000000.toInt(); c.drawRect(r.left, cy - s * 0.006f, r.right, cy + s * 0.006f, p) }
                }
                textSize = min(s * 0.42f, cw * 1.35f)
            }
            16 -> { // Minimal: thin elegant digits with a hairline that shortens.
                font = Typeface.create("sans-serif-light", Typeface.NORMAL); textSize *= 1.1f
                p.color = withAlpha(Color.WHITE, 0.3f); c.drawRect(s * 0.2f, cy + s * 0.2f, s * 0.8f, cy + s * 0.205f, p)
                p.color = col; c.drawRect(s * 0.2f, cy + s * 0.2f, s * 0.2f + s * 0.6f * k, cy + s * 0.207f, p)
                glow = s * 0.02f
            }
            17 -> { // Pulse: a ring that throws a ripple every second.
                disc(); ring.strokeWidth = s * 0.04f; ring.color = col; c.drawArc(oval, -90f, 360f * k, false, ring)
                if (!still) { val rr = R * (1f + 0.25f * sec); ring.strokeWidth = s * 0.02f * (1f - sec); ring.color = withAlpha(col, 1f - sec); c.drawCircle(cx, cy, rr, ring) }
            }
            18 -> { // Radar: a sweep beam turns once a second over a grid.
                p.color = 0xCC03140A.toInt(); c.drawCircle(cx, cy, R + s * 0.05f, p)
                ring.strokeWidth = s * 0.006f; ring.color = withAlpha(col, 0.5f)
                c.drawCircle(cx, cy, R, ring); c.drawCircle(cx, cy, R * 0.66f, ring); c.drawCircle(cx, cy, R * 0.33f, ring)
                c.drawLine(cx - R, cy, cx + R, cy, ring); c.drawLine(cx, cy - R, cx, cy + R, ring)
                if (!still) {
                    p.shader = SweepGradient(cx, cy, intArrayOf(0, 0, withAlpha(col, 0.7f)), floatArrayOf(0f, 0.75f, 1f)).also { it.setLocalMatrix(android.graphics.Matrix().apply { setRotate(sec * 360f - 90f, cx, cy) }) }
                    c.drawCircle(cx, cy, R, p); p.shader = null
                }
                textColor = col; glow = s * 0.04f
            }
            19 -> { // Rainbow ring.
                disc(); ring.strokeWidth = s * 0.07f
                ring.shader = SweepGradient(cx, cy, intArrayOf(0xFFFF3B30.toInt(), 0xFFFFCC00.toInt(), 0xFF34C759.toInt(), 0xFF0A84FF.toInt(), 0xFFBF5AF2.toInt(), 0xFFFF3B30.toInt()), null)
                    .also { it.setLocalMatrix(android.graphics.Matrix().apply { setRotate(-90f, cx, cy) }) }
                c.drawArc(oval, -90f, 360f * k, false, ring); ring.shader = null
            }
            20 -> { // Clock ticks: sixty ticks that go out as time passes.
                disc()
                for (i in 0 until 60) {
                    val a = -PI.toFloat() / 2 + i / 60f * TAU
                    val long = i % 5 == 0
                    ring.strokeWidth = s * (if (long) 0.02f else 0.01f); ring.color = if (i / 60f < k) col else 0x30FFFFFF
                    val r0 = R * (if (long) 0.82f else 0.88f)
                    c.drawLine(cx + r0 * cos(a), cy + r0 * sin(a), cx + R * cos(a), cy + R * sin(a), ring)
                }
                textSize *= 0.85f
            }
            21 -> { // Battery: a battery that drains, going red near the end.
                val r = RectF(s * 0.1f, s * 0.3f, s * 0.82f, s * 0.7f)
                ring.strokeWidth = s * 0.03f; ring.color = Color.WHITE; ring.strokeCap = Paint.Cap.BUTT
                p.color = 0x8C000000.toInt(); c.drawRoundRect(r, s * 0.04f, s * 0.04f, p)
                c.drawRoundRect(r, s * 0.04f, s * 0.04f, ring)
                p.color = Color.WHITE; c.drawRoundRect(RectF(s * 0.83f, s * 0.42f, s * 0.9f, s * 0.58f), s * 0.015f, s * 0.015f, p)
                p.color = if (k < 0.2f) 0xFFFF3B30.toInt() else col
                c.drawRoundRect(RectF(r.left + s * 0.03f, r.top + s * 0.03f, r.left + s * 0.03f + (r.width() - s * 0.06f) * k, r.bottom - s * 0.03f), s * 0.02f, s * 0.02f, p)
                textSize *= 0.7f; textX = s * 0.46f
            }
            22 -> { // Liquid: a glass of colour that empties, with a moving surface.
                p.color = 0x8C000000.toInt(); c.drawCircle(cx, cy, R, p)
                val lvl = cy + R - 2f * R * k
                val path = Path(); path.moveTo(cx - R, cy + R)
                var x = cx - R
                while (x <= cx + R) { path.lineTo(x, lvl + sin(x / s * 18f + el / 180f) * s * 0.015f); x += s / 60f }
                path.lineTo(cx + R, cy + R); path.close()
                c.save(); c.clipPath(Path().apply { addCircle(cx, cy, R, Path.Direction.CW) })
                p.color = withAlpha(col, 0.85f); c.drawPath(path, p)
                c.restore()
                ring.strokeWidth = s * 0.02f; ring.color = Color.WHITE; c.drawCircle(cx, cy, R, ring)
                glow = s * 0.03f
            }
            23 -> { // Dots: a row of dots that go out one by one.
                val n = 10
                for (i in 0 until n) {
                    p.color = if ((i + 0.5f) / n < k) col else 0x40FFFFFF
                    c.drawCircle(s * 0.1f + i * s * 0.8f / (n - 1), s * 0.8f, s * 0.03f, p)
                }
                textSize *= 1.1f; textY = cy - s * 0.05f; glow = s * 0.03f
            }
            else -> { // Scroll wheel bar: the timeline scroll bar's look under the digits.
                val wc = com.dronedirector.veyrocutpro.ui.CursorStyle.wheel
                fun argb(cc: androidx.compose.ui.graphics.Color, a: Float = 1f) = Color.argb((a * 255).toInt().coerceIn(0, 255), (cc.red * 255).toInt(), (cc.green * 255).toInt(), (cc.blue * 255).toInt())
                val c1 = argb(wc); val c2 = argb(com.dronedirector.veyrocutpro.ui.CursorStyle.partner(wc)); val tip = argb(androidx.compose.ui.graphics.lerp(wc, androidx.compose.ui.graphics.Color.White, 0.45f))
                val r = RectF(s * 0.08f, s * 0.7f, s * 0.92f, s * 0.82f)
                p.color = 0xCC0D1016.toInt(); c.drawRoundRect(r, r.height() / 2, r.height() / 2, p)
                val x1 = r.left + r.width() * k
                if (k > 0f) { p.shader = LinearGradient(r.left, 0f, x1.coerceAtLeast(r.left + 2f), 0f, intArrayOf(c1, c2, tip), null, Shader.TileMode.CLAMP); c.drawRoundRect(RectF(r.left, r.top + r.height() * 0.15f, x1.coerceAtLeast(r.left + r.height()), r.bottom - r.height() * 0.15f), r.height() * 0.35f, r.height() * 0.35f, p); p.shader = null }
                ring.strokeWidth = s * 0.01f; ring.shader = SweepGradient(r.left + r.width() * ((el / 40 % 60) / 60f), r.centerY(), intArrayOf(c1, c2, tip, c1), null); c.drawRoundRect(r, r.height() / 2, r.height() / 2, ring); ring.shader = null
                p.color = 0x8C000000.toInt(); c.drawRoundRect(RectF(s * 0.12f, s * 0.2f, s * 0.88f, s * 0.64f), s * 0.06f, s * 0.06f, p)
                textY = s * 0.42f; textColor = tip; glow = s * 0.04f
            }
        }
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor; textAlign = Paint.Align.CENTER; typeface = font; this.textSize = textSize
            if (glow > 0f) setShadowLayer(glow, 0f, 0f, if (textColor == Color.WHITE) col else textColor)
        }
        val pulse = if (still) 1f else 1f + 0.06f * (1f - (sec * 4f).coerceIn(0f, 1f))
        c.save(); c.scale(pulse, pulse, textX, textY)
        val fm = tp.fontMetrics
        c.drawText(txt, textX, textY - (fm.ascent + fm.descent) / 2f, tp)
        c.restore()
    }
}
