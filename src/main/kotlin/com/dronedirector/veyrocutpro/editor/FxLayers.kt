package com.dronedirector.veyrocutpro.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/**
 * Overlay layers drawn by the app (Layers → ADD LAYER): cinema bars, a flight path mini-map from the
 * drone's flight data, a progress bar and a lens flare. Drawn the same in the preview and the export. Own code.
 */
/** A sound the visualizer can follow: its name, file, and song time minus timeline time. */
data class VisSource(val name: String, val uri: android.net.Uri, val delta: Long)

object FxLayers {
    const val BARS = 0
    const val MAP = 1
    const val PROGRESS = 2
    const val FLARE = 3
    const val TIMER = 4
    const val VIS = 5
    const val RETRO = 6
    val VIS_STYLES = listOf("Spectrum bars", "Mirror bars", "Glow line", "Circular", "Waveform", "LED equalizer", "Radial wave", "Sound rings", "Particles", "Bass orb") + FxStyles.MORE_VIS
    /** Visualizer styles drawn in a square around a centre. */
    fun visRound(style: Int) = style == 3 || style in 6..9 || style in FxStyles.ROUND_VIS
    val VIS_COLOURS = listOf("Layer colour", "Scroll wheel colours", "Rainbow")

    val BAR_SHAPES = listOf("2.39:1" to 2.39f, "2.35:1" to 2.35f, "2:1" to 2f, "1.85:1" to 1.85f, "2.76:1" to 2.76f)
    val FLARE_STYLES = listOf("Warm sun", "Anamorphic blue", "Soft glow")
    private const val STEP_MS = 40L

    fun label(l: Layer): String = when (l.fx) { BARS -> "Cinema bars"; MAP -> "Flight path"; PROGRESS -> "Progress bar"; TIMER -> "Countdown"; VIS -> "Visualizer"; RETRO -> RetroFx.STYLES.getOrElse((l.fxA ?: 0f).toInt()) { "Retro look" }; else -> "Lens flare" }
    val TIMER_STYLES = listOf("Ring", "Glowing ring", "Scroll wheel colours", "Solid disc", "Big numbers") + FxStyles.MORE_TIMERS

    /** Full-frame overlays sit still in the middle (they're not dragged; change them in the layer's settings). */
    fun fullFrame(l: Layer) = l.kind == LayerKind.FX && l.fx != MAP && l.fx != PROGRESS && l.fx != TIMER && l.fx != VIS

    /** Which picture to draw at [ms] (a new number = a new picture); 0 for overlays that don't change. */
    fun frameIndex(l: Layer, ms: Long): Int = when (l.fx) {
        MAP, PROGRESS, TIMER, VIS, RETRO -> ((ms - l.startMs).coerceAtLeast(0) / STEP_MS).toInt()
        BARS -> if (l.fxB == 1f) {
            val inMs = ms - l.startMs; val outMs = l.endMs - ms
            when { inMs in 0 until 1000 -> 1 + (inMs / STEP_MS).toInt(); outMs in 0 until 1000 -> 1000 + (outMs / STEP_MS).toInt(); else -> 0 }
        } else 0
        else -> 0
    }

    private fun msOf(l: Layer, n: Int): Long = when (l.fx) {
        BARS -> if (n == 0) -1L else if (n < 1000) l.startMs + (n - 1) * STEP_MS else l.endMs - (n - 1000) * STEP_MS
        else -> l.startMs + n * STEP_MS
    }

    fun renderFrame(l: Layer, frameW: Int, frameH: Int, n: Int, context: android.content.Context? = null): Bitmap? = render(l, frameW, frameH, msOf(l, n), context)

    /** The overlay at timeline time [ms] (−1 = at rest), for a [frameW] × [frameH] picture. */
    fun render(l: Layer, frameW: Int, frameH: Int, ms: Long, context: android.content.Context? = null): Bitmap? = try {
        when (l.fx) {
            BARS -> bars(l, frameW, frameH, ms)
            MAP -> map(l, frameH, ms)
            PROGRESS -> progress(l, frameW, frameH, ms)
            TIMER -> timer(l, frameH, ms)
            VIS -> vis(context, l, frameW, frameH, ms)
            RETRO -> RetroFx.render(l, frameW, frameH, ms)
            else -> flare(l, frameW, frameH)
        }
    } catch (_: Throwable) { null }

    private fun bars(l: Layer, w: Int, h: Int, ms: Long): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val target = l.fxA ?: 2.39f
        var bar = ((h - w / target) / 2f).coerceAtLeast(0f)
        if (l.fxB == 1f && ms >= 0) {
            // Slide in over the first second, out over the last.
            val k = min(((ms - l.startMs) / 1000f).coerceIn(0f, 1f), ((l.endMs - ms) / 1000f).coerceIn(0f, 1f))
            bar *= k * k * (3 - 2 * k)
        }
        if (bar >= 0.5f) {
            val p = Paint().apply { color = l.color or 0xFF000000.toInt() }
            val c = Canvas(out)
            c.drawRect(0f, 0f, w.toFloat(), bar, p)
            c.drawRect(0f, h - bar, w.toFloat(), h.toFloat(), p)
        }
        return out
    }

    val PROGRESS_STYLES = listOf("Plain", "Rounded", "Glowing tip", "Gradient", "Segments", "Dots", "Thin + dot", "Scroll wheel")

    private fun hash(a: Int, b: Int, c: Int): Float {
        var h = a * 374761393 + b * 668265263 + c * 1274126177
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    private fun progress(l: Layer, w: Int, h: Int, ms: Long): Bitmap {
        val style = (l.fxB ?: 0f).toInt()
        val th = ((l.fxA ?: 0.008f) * h).coerceAtLeast(if (style == 7) h * 0.016f else 2f)
        // Room above and below the bar for glows, dots and handles (the bar stays centred on the layer's place).
        val tall = when (style) { 2 -> 4f; 5 -> 2.2f; 6 -> 3.2f; 7 -> 1.6f; else -> 1f }
        val bh = (th * tall).toInt().coerceAtLeast(2)
        val out = Bitmap.createBitmap(w, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val len = (l.endMs - l.startMs).coerceAtLeast(1)
        val k = if (ms < 0) 0.35f else ((ms - l.startMs).toFloat() / len).coerceIn(0f, 1f)
        val col = l.color or 0xFF000000.toInt()
        val col2 = com.dronedirector.veyrocutpro.ui.CursorStyle.partner(androidx.compose.ui.graphics.Color(col)).let { android.graphics.Color.argb(255, (it.red * 255).toInt(), (it.green * 255).toInt(), (it.blue * 255).toInt()) }
        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40FFFFFF }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col }
        val cy = bh / 2f; val top = cy - th / 2f; val bot = cy + th / 2f
        val x1 = w * k
        val step = ((ms.coerceAtLeast(0)) / STEP_MS).toInt()
        when (style) {
            1 -> { // Rounded: a pill inset from the sides.
                val m = w * 0.03f; val r = th / 2f
                c.drawRoundRect(RectF(m, top, w - m, bot), r, r, track)
                if (k > 0f) c.drawRoundRect(RectF(m, top, m + (w - 2 * m) * k, bot).also { if (it.width() < th) it.right = it.left + th }, r, r, fill)
            }
            2 -> { // Glowing tip: only the line and the soft glow at its front edge.
                c.drawRect(0f, top, x1, bot, fill)
                val gr = bh / 2f
                c.drawCircle(x1, cy, gr, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(x1, cy, gr, intArrayOf(0xFFFFFFFF.toInt(), col and 0x99FFFFFF.toInt(), 0), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP) })
            }
            3 -> { // Gradient: from the colour into its partner colour.
                c.drawRect(0f, top, w.toFloat(), bot, track)
                if (x1 > 1f) c.drawRect(0f, top, x1, bot, Paint().apply { shader = LinearGradient(0f, 0f, w.toFloat(), 0f, col, col2, Shader.TileMode.CLAMP) })
            }
            4 -> { // Segments: the length split into equal parts with small gaps.
                val n = (l.fxX ?: 5f).toInt().coerceIn(2, 12)
                val gap = (w * 0.004f).coerceAtLeast(2f)
                for (i in 0 until n) {
                    val a = w * i / n.toFloat() + (if (i > 0) gap / 2 else 0f); val b = w * (i + 1) / n.toFloat() - (if (i < n - 1) gap / 2 else 0f)
                    c.drawRect(a, top, b, bot, track)
                    if (x1 > a) c.drawRect(a, top, minOf(b, x1), bot, fill)
                }
            }
            5 -> { // Dots: a row of dots lighting up one by one.
                val r = bh / 2f * 0.8f
                val n = (w / (r * 3.2f)).toInt().coerceIn(8, 120)
                for (i in 0 until n) {
                    val x = (i + 0.5f) * w / n
                    c.drawCircle(x, cy, r, if (x <= x1) fill else track)
                }
            }
            6 -> { // Thin + dot: a hairline with a round handle, like a video player.
                val lt = (th * 0.35f).coerceAtLeast(1.5f)
                c.drawRect(0f, cy - lt / 2, w.toFloat(), cy + lt / 2, track)
                c.drawRect(0f, cy - lt / 2, x1, cy + lt / 2, fill)
                val r = bh / 2f * 0.85f
                c.drawCircle(x1.coerceIn(r, w - r), cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
                c.drawCircle(x1.coerceIn(r, w - r), cy, r * 0.6f, fill)
            }
            7 -> { // Scroll wheel: the timeline scroll bar's look, with its moving effects always on.
                val wc = com.dronedirector.veyrocutpro.ui.CursorStyle.wheel
                fun argb(cc: androidx.compose.ui.graphics.Color, a: Float = 1f) = android.graphics.Color.argb((a * 255).toInt().coerceIn(0, 255), (cc.red * 255).toInt(), (cc.green * 255).toInt(), (cc.blue * 255).toInt())
                val cyanC = wc; val violetC = com.dronedirector.veyrocutpro.ui.CursorStyle.partner(wc)
                val tipC = androidx.compose.ui.graphics.lerp(wc, androidx.compose.ui.graphics.Color.White, 0.45f)
                val r = bh / 2f
                val full = RectF(0.5f, 0.5f, w - 0.5f, bh - 0.5f)
                // Dark track with rolling ridges.
                c.drawRoundRect(full, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC0D1016.toInt() })
                val sp = (bh * 0.4f).coerceAtLeast(3f)
                var x = -((step * bh * 0.08f) % sp)
                val ridge = Paint().apply { strokeWidth = (bh * 0.07f).coerceAtLeast(1f) }
                while (x < w) {
                    val edge = 1f - kotlin.math.abs(x / w - 0.5f) * 2f
                    ridge.color = argb(androidx.compose.ui.graphics.Color.White, 0.08f + 0.14f * edge)
                    c.drawLine(x, bh * 0.28f, x, bh * 0.72f, ridge); x += sp
                }
                if (x1 > 1f) {
                    // "Upscaling" pixel blocks: coarse near the front edge, finer further back, flickering.
                    val zone = minOf(x1, bh * 10f)
                    val pp = Paint()
                    var px = x1 - zone
                    while (px < x1) {
                        val near = 1f - ((x1 - px) / zone).coerceIn(0f, 1f)
                        val cell = (bh * (0.18f + 0.3f * near)).coerceAtLeast(2f)
                        var py = 0f; var j = 0
                        val i = (px / 2f).toInt()
                        while (py < bh) {
                            val v = hash(i, j, step / 2)
                            val a = near * (0.25f + 0.6f * v)
                            if (a > 0.04f) { pp.color = argb(if (v > 0.5f) cyanC else violetC, a); c.drawRect(px, py, px + cell - 1f, py + cell - 1f, pp) }
                            py += cell; j++
                        }
                        px += cell
                    }
                    // The glowing gradient fill (thumb).
                    val fr = RectF(1f, bh * 0.16f, x1.coerceAtLeast(bh.toFloat()), bh * 0.84f)
                    c.drawRoundRect(fr, bh * 0.34f, bh * 0.34f,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, 0f, x1.coerceAtLeast(2f), 0f, intArrayOf(argb(cyanC, 0.9f), argb(violetC, 0.9f), argb(tipC, 0.95f)), null, Shader.TileMode.CLAMP) })
                    // Light on the front edge.
                    c.drawCircle(x1, bh / 2f, bh * 0.9f, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(x1, bh / 2f, bh * 0.9f, intArrayOf(argb(tipC, 0.9f), argb(tipC, 0f)), null, Shader.TileMode.CLAMP) })
                    // Sparkles.
                    val spk = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF2FFFFFF.toInt() }
                    for (q in 0 until 7) {
                        val tw = hash(q, 3, step)
                        if (tw > 0.25f) c.drawCircle(x1 - zone * hash(q, 7, step / 3), bh * (0.15f + 0.7f * hash(q, 11, step / 3)), (bh * 0.09f).coerceAtLeast(1.2f) * tw, spk)
                    }
                }
                // Coloured rim with a sweep that travels along.
                val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.style = Paint.Style.STROKE; strokeWidth = (bh * 0.11f).coerceAtLeast(1.5f)
                    shader = android.graphics.SweepGradient(w * ((step % 60) / 60f), bh / 2f, intArrayOf(argb(cyanC), argb(violetC), argb(tipC), argb(cyanC)), null)
                }
                c.drawRoundRect(full, r, r, rim)
            }
            else -> { // Plain.
                c.drawRect(0f, top, w.toFloat(), bot, track)
                c.drawRect(0f, top, x1, bot, fill)
            }
        }
        return out
    }

    /**
     * Countdown: a ring that empties as time runs out, with the time left in the middle. Automatic counts down the
     * layer's own length; Manual starts from your number of seconds ([Layer.fxX]) and the layer is that long.
     */
    private fun timer(l: Layer, h: Int, ms: Long): Bitmap {
        val side = max(48, (l.size * h).toInt())
        val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val style = (l.fxB ?: 0f).toInt()
        val total = (l.endMs - l.startMs).coerceAtLeast(1)
        val el = if (ms < 0) 0L else (ms - l.startMs).coerceIn(0, total)
        val left = total - el
        val k = left.toFloat() / total
        val col = l.color or 0xFF000000.toInt()
        if (style >= 5) {
            val sec = ((left + 999) / 1000).toInt()
            val txt = if ((l.fxY ?: 0f) == 1f || sec >= 60) "%d:%02d".format(sec / 60, sec % 60) else sec.toString()
            FxStyles.timer(style, c, side, k, left, el, txt, col, ms < 0)
            return out
        }
        val cx = side / 2f; val cy = side / 2f
        val sw = side * 0.085f
        val rr = side / 2f - sw * (if (style == 1 || style == 2) 1.4f else 0.8f)
        val oval = RectF(cx - rr, cy - rr, cx + rr, cy + rr)
        val wc = com.dronedirector.veyrocutpro.ui.CursorStyle.wheel
        fun argb(cc: androidx.compose.ui.graphics.Color, a: Float = 1f) = android.graphics.Color.argb((a * 255).toInt().coerceIn(0, 255), (cc.red * 255).toInt(), (cc.green * 255).toInt(), (cc.blue * 255).toInt())
        // Background disc so the numbers read on any picture (none for big numbers).
        if (style != 4) c.drawCircle(cx, cy, rr + sw * 0.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (style == 3) col else 0x8C000000.toInt() })
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.STROKE; strokeWidth = sw; strokeCap = Paint.Cap.ROUND }
        if (style == 4) {
            // Big numbers: a thin arc behind large glowing digits.
            ring.strokeWidth = sw * 0.35f; ring.color = 0x30FFFFFF; c.drawArc(oval, 0f, 360f, false, ring)
            ring.color = col; if (k > 0f) c.drawArc(oval, -90f, 360f * k, false, ring)
        } else if (style != 3) {
            ring.color = 0x40FFFFFF; c.drawArc(oval, 0f, 360f, false, ring)
            val sweep = 360f * k
            if (style == 2) {
                ring.shader = android.graphics.SweepGradient(cx, cy, intArrayOf(argb(wc), argb(com.dronedirector.veyrocutpro.ui.CursorStyle.partner(wc)), argb(androidx.compose.ui.graphics.lerp(wc, androidx.compose.ui.graphics.Color.White, 0.45f)), argb(wc)), null)
                    .also { g -> g.setLocalMatrix(android.graphics.Matrix().apply { setRotate(-90f + (el / 12f) % 360f, cx, cy) }) }
            } else ring.color = col
            if (sweep > 0.5f) c.drawArc(oval, -90f, sweep, false, ring)
            ring.shader = null
            if ((style == 1 || style == 2) && k > 0f) {
                // Glow at the moving end of the ring.
                val a = Math.toRadians((-90.0 + sweep))
                val ex = cx + rr * kotlin.math.cos(a).toFloat(); val ey = cy + rr * kotlin.math.sin(a).toFloat()
                val gc = if (style == 2) argb(androidx.compose.ui.graphics.lerp(wc, androidx.compose.ui.graphics.Color.White, 0.45f)) else col
                c.drawCircle(ex, ey, sw * 1.6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(ex, ey, sw * 1.6f, intArrayOf(0xFFFFFFFF.toInt(), gc and 0x99FFFFFF.toInt(), 0), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP) })
            }
        } else {
            // Solid disc: a pie that empties.
            c.drawArc(RectF(cx - rr, cy - rr, cx + rr, cy + rr), -90f, 360f * k, true, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55FFFFFF })
        }
        // Time left: whole seconds, rounded up (shows 0 only at the very end); m:ss from a minute.
        val sec = ((left + 999) / 1000).toInt()
        val txt = if ((l.fxY ?: 0f) == 1f || sec >= 60) "%d:%02d".format(sec / 60, sec % 60) else sec.toString()
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (style == 3) 0xFF000000.toInt() else 0xFFFFFFFF.toInt(); textAlign = Paint.Align.CENTER; isFakeBoldText = true
            textSize = rr * (if (txt.length <= 2) 0.95f else if (txt.length <= 4) 0.62f else 0.5f) * (if (style == 4) 1.45f else 1f)
            if (style == 4) setShadowLayer(rr * 0.12f, 0f, 0f, col)
        }
        // A short pulse on each new second.
        val pulse = 1f + 0.08f * (1f - ((el % 1000) / 250f).coerceIn(0f, 1f)) * (if (ms < 0) 0f else 1f)
        c.save(); c.scale(pulse, pulse, cx, cy)
        val fm = tp.fontMetrics
        c.drawText(txt, cx, cy - (fm.ascent + fm.descent) / 2f, tp)
        c.restore()
        return out
    }

    /**
     * Music visualizer: the spectrum of the song (or a clip's sound) at this moment, moving with the music and
     * pulsing on the beats. [Layer.imageUri] = the sound, [Layer.fxD] = song time minus timeline time.
     */
    private fun vis(context: android.content.Context?, l: Layer, fw: Int, fh: Int, ms: Long): Bitmap? {
        val uri = l.imageUri ?: return null
        val data = Spectrum.cached(uri) ?: context?.let { Spectrum.load(it, uri) }
        val style = (l.fxB ?: 0f).toInt()
        val round = visRound(style)
        val bw = if (round) (l.size * fh * 2f).coerceAtMost(max(fw, fh).toFloat()).toInt().coerceAtLeast(64) else (fw * (l.fxA ?: 0.9f)).toInt().coerceAtLeast(64)
        val bh = if (round) bw else (l.size * fh).toInt().coerceAtLeast(24)
        val out = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val songMs = (if (ms < 0) l.startMs + 4000 else ms) + (l.fxD ?: 0f).toLong()
        val at = data?.let { Spectrum.frame(it, songMs) }
        val nb = Spectrum.BANDS
        val v = FloatArray(nb) { b -> if (data != null && at != null) data[at + b] else 0.04f + 0.03f * (b % 3) }
        val beat = if (data != null && at != null) data[at + nb] else 0f
        // Peaks hold, then fall.
        val peak = FloatArray(nb) { b ->
            var p = v[b]
            if (data != null && at != null) for (j in 1..18) { val q = at - j * Spectrum.STRIDE; if (q < 0) break; p = max(p, data[q + b] - j * 0.03f) }
            p
        }
        val col = l.color or 0xFF000000.toInt()
        val wc = com.dronedirector.veyrocutpro.ui.CursorStyle.wheel
        fun argb(cc: androidx.compose.ui.graphics.Color, a: Float = 1f) = android.graphics.Color.argb((a * 255).toInt().coerceIn(0, 255), (cc.red * 255).toInt(), (cc.green * 255).toInt(), (cc.blue * 255).toInt())
        val mode = (l.fxC ?: 0f).toInt()
        val c1: Int; val c2: Int
        when (mode) {
            1 -> { c1 = argb(wc); c2 = argb(com.dronedirector.veyrocutpro.ui.CursorStyle.partner(wc)) }
            else -> { c1 = col; c2 = argb(com.dronedirector.veyrocutpro.ui.CursorStyle.partner(androidx.compose.ui.graphics.Color(col))) }
        }
        fun colourAt(f: Float): Int = if (mode == 2) android.graphics.Color.HSVToColor(floatArrayOf((f * 300f + (songMs / 40f)) % 360f, 0.75f, 1f))
            else android.graphics.Color.rgb(
                ((c1 shr 16 and 0xFF) + ((c2 shr 16 and 0xFF) - (c1 shr 16 and 0xFF)) * f).toInt(),
                ((c1 shr 8 and 0xFF) + ((c2 shr 8 and 0xFF) - (c1 shr 8 and 0xFF)) * f).toInt(),
                ((c1 and 0xFF) + ((c2 and 0xFF) - (c1 and 0xFF)) * f).toInt())
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { maskFilter = android.graphics.BlurMaskFilter(maxOf(4f, bh * 0.06f) * (1f + beat), android.graphics.BlurMaskFilter.Blur.NORMAL) }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val bars = 48
        fun level(i: Int, n: Int): Float { val b = (i.toFloat() / n * nb).toInt().coerceIn(0, nb - 1); return v[b] }
        fun pk(i: Int, n: Int): Float { val b = (i.toFloat() / n * nb).toInt().coerceIn(0, nb - 1); return peak[b] }
        if (style >= 10) { FxStyles.vis(style, c, bw, bh, v, peak, beat, songMs, ::colourAt, data, at); return out }
        when (style) {
            0, 1 -> { // Spectrum bars (from the bottom, with a reflection) / mirror bars (from the middle).
                val gap = bw / bars.toFloat() * 0.28f
                val bwid = bw / bars.toFloat() - gap
                val mid = if (style == 1) bh / 2f else bh * 0.78f
                val maxH = if (style == 1) bh / 2f * 0.95f else mid * 0.97f
                val r = bwid / 2f
                for (i in 0 until bars) {
                    val x = i * (bwid + gap) + gap / 2
                    val h = (maxH * (0.03f + 0.97f * level(i, bars)) * (1f + 0.12f * beat)).coerceAtMost(maxH)
                    val cc = colourAt(i / (bars - 1f))
                    glow.color = cc; glow.alpha = (90 + 120 * beat).toInt().coerceAtMost(220)
                    fill.color = cc
                    val top = mid - h
                    val bot = if (style == 1) mid + h else mid
                    c.drawRoundRect(RectF(x, top, x + bwid, bot), r, r, glow)
                    fill.shader = LinearGradient(0f, top, 0f, bot, intArrayOf(android.graphics.Color.rgb((cc shr 16 and 0xFF) + (255 - (cc shr 16 and 0xFF)) * 2 / 5, (cc shr 8 and 0xFF) + (255 - (cc shr 8 and 0xFF)) * 2 / 5, (cc and 0xFF) + (255 - (cc and 0xFF)) * 2 / 5), cc), null, Shader.TileMode.CLAMP)
                    c.drawRoundRect(RectF(x, top, x + bwid, bot), r, r, fill)
                    fill.shader = null
                    // Peak cap.
                    val ph = maxH * (0.03f + 0.97f * pk(i, bars)).coerceAtMost(1f)
                    fill.color = 0xE6FFFFFF.toInt()
                    c.drawRoundRect(RectF(x, mid - ph - bwid * 0.5f, x + bwid, mid - ph), r * 0.5f, r * 0.5f, fill)
                    if (style == 1) c.drawRoundRect(RectF(x, mid + ph, x + bwid, mid + ph + bwid * 0.5f), r * 0.5f, r * 0.5f, fill)
                    if (style == 0) {
                        // Soft reflection under the line.
                        fill.shader = LinearGradient(0f, mid, 0f, mid + h * 0.35f, intArrayOf((cc and 0x00FFFFFF) or 0x55000000, cc and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
                        c.drawRect(x, mid + bh * 0.01f, x + bwid, mid + bh * 0.01f + h * 0.35f, fill)
                        fill.shader = null
                    }
                }
            }
            2, 4 -> { // Glow line (smooth spectrum curve) / waveform-like mirrored ribbon.
                val n = 96
                val path = Path(); val area = Path()
                val mid = if (style == 4) bh / 2f else bh * 0.9f
                val amp = if (style == 4) bh * 0.46f else bh * 0.85f
                val pts = FloatArray(n) { i -> (0.03f + 0.97f * level(i, n)) * (1f + 0.15f * beat) }
                for (i in 0 until n) {
                    val x = i * bw / (n - 1f); val y = mid - amp * pts[i]
                    if (i == 0) { path.moveTo(x, y); area.moveTo(x, mid); area.lineTo(x, y) }
                    else { val px = (i - 1) * bw / (n - 1f); val py = mid - amp * pts[i - 1]; val cx = (px + x) / 2; path.cubicTo(cx, py, cx, y, x, y); area.cubicTo(cx, py, cx, y, x, y) }
                }
                area.lineTo(bw.toFloat(), mid); area.close()
                val grad = LinearGradient(0f, 0f, bw.toFloat(), 0f, IntArray(6) { colourAt(it / 5f) }, null, Shader.TileMode.CLAMP)
                if (style == 4) {
                    // Mirror the area below the middle.
                    val m = android.graphics.Matrix().apply { setScale(1f, -1f, 0f, mid) }
                    val below = Path(area).also { it.transform(m) }
                    fill.shader = grad; fill.alpha = 110
                    c.drawPath(area, fill); c.drawPath(below, fill)
                    fill.alpha = 255
                } else {
                    fill.shader = LinearGradient(0f, mid - amp, 0f, mid, intArrayOf((colourAt(0.5f) and 0x00FFFFFF) or 0x77000000, colourAt(0.5f) and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
                    c.drawPath(area, fill)
                }
                fill.shader = null
                val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.STROKE; strokeWidth = maxOf(2f, bh * 0.025f); strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND; shader = grad }
                val lg = Paint(line).apply { strokeWidth = line.strokeWidth * (3f + 2f * beat); maskFilter = android.graphics.BlurMaskFilter(maxOf(6f, bh * 0.05f), android.graphics.BlurMaskFilter.Blur.NORMAL); alpha = (140 + 100 * beat).toInt().coerceAtMost(240) }
                c.drawPath(path, lg); c.drawPath(path, line)
                if (style == 4) { val m = android.graphics.Matrix().apply { setScale(1f, -1f, 0f, mid) }; val p2 = Path(path).also { it.transform(m) }; c.drawPath(p2, lg); c.drawPath(p2, line) }
            }
            3 -> { // Circular: bars around a ring that pulses on the beat.
                val cx = bw / 2f; val cy = bh / 2f
                val base = bw * 0.24f * (1f + 0.10f * beat)
                val len = bw * 0.24f
                val n = 72
                val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeWidth = (2f * Math.PI.toFloat() * base / n * 0.55f).coerceAtLeast(2f) }
                val bg = Paint(bar).apply { maskFilter = android.graphics.BlurMaskFilter(maxOf(5f, bw * 0.02f) * (1f + beat), android.graphics.BlurMaskFilter.Blur.NORMAL) }
                for (i in 0 until n) {
                    // Low notes at the top, mirrored left and right.
                    val f = if (i < n / 2) i / (n / 2f) else (n - i) / (n / 2f)
                    val lv = 0.04f + 0.96f * level((f * (n / 2)).toInt(), n / 2)
                    val a = (i.toFloat() / n) * 2f * Math.PI.toFloat() - Math.PI.toFloat() / 2f
                    val x0 = cx + base * kotlin.math.cos(a); val y0 = cy + base * kotlin.math.sin(a)
                    val x1 = cx + (base + len * lv) * kotlin.math.cos(a); val y1 = cy + (base + len * lv) * kotlin.math.sin(a)
                    val cc = colourAt(f)
                    bg.color = cc; bg.alpha = (110 + 120 * beat).toInt().coerceAtMost(230); c.drawLine(x0, y0, x1, y1, bg)
                    bar.color = cc; c.drawLine(x0, y0, x1, y1, bar)
                }
                // Inner ring and beat flash.
                c.drawCircle(cx, cy, base * 0.92f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.STROKE; strokeWidth = maxOf(2f, bw * 0.008f); color = colourAt(0.5f) })
                c.drawCircle(cx, cy, base * 0.9f, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(cx, cy, base * 0.9f, intArrayOf((colourAt(0f) and 0x00FFFFFF) or ((40 + 120 * beat).toInt().coerceAtMost(200) shl 24), 0), null, Shader.TileMode.CLAMP) })
            }
            5 -> { // LED equalizer: lit segments like a hi-fi display, with a held peak segment.
                val cols = 28; val segs = 18
                val gx = bw / cols.toFloat(); val gy = bh / segs.toFloat()
                val seg = Paint(Paint.ANTI_ALIAS_FLAG)
                for (i in 0 until cols) {
                    val lit = (level(i, cols) * (1f + 0.1f * beat) * segs).toInt().coerceIn(1, segs)
                    val pkSeg = (pk(i, cols) * segs).toInt().coerceIn(1, segs)
                    for (j in 0 until segs) {
                        val y1 = bh - j * gy; val y0 = y1 - gy * 0.72f
                        val x0 = i * gx + gx * 0.12f; val x1 = (i + 1) * gx - gx * 0.12f
                        val f = j / (segs - 1f)
                        val cc = if (mode == 0 && l.color == 0xFFFFB300.toInt()) when { f < 0.6f -> 0xFF34C759.toInt(); f < 0.85f -> 0xFFFFD60A.toInt(); else -> 0xFFFF3B30.toInt() } else colourAt(f)
                        when {
                            j < lit -> { glow.color = cc; glow.alpha = (70 + 110 * beat).toInt().coerceAtMost(200); c.drawRect(x0, y0, x1, y1, glow); seg.color = cc; c.drawRect(x0, y0, x1, y1, seg) }
                            j == pkSeg - 1 -> { seg.color = 0xEEFFFFFF.toInt(); c.drawRect(x0, y0, x1, y1, seg) }
                            else -> { seg.color = (cc and 0x00FFFFFF) or 0x1E000000; c.drawRect(x0, y0, x1, y1, seg) }
                        }
                    }
                }
            }
            6 -> { // Radial wave: a glowing closed wave round a circle, turning slowly.
                val cx = bw / 2f; val cy = bh / 2f
                val base = bw * 0.26f * (1f + 0.08f * beat)
                val amp = bw * 0.2f
                val n = 120
                val rot = (songMs / 9000f) * 2f * Math.PI.toFloat()
                val path = Path()
                fun rad(i: Int): Float { val f = if (i < n / 2) i / (n / 2f) else (n - i) / (n / 2f); return base + amp * (0.05f + 0.95f * level((f * 47).toInt(), 48)) }
                for (i in 0..n) {
                    val a = rot + i.toFloat() / n * 2f * Math.PI.toFloat()
                    val r = rad(i % n)
                    val x = cx + r * kotlin.math.cos(a); val y = cy + r * kotlin.math.sin(a)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                val sweep = android.graphics.SweepGradient(cx, cy, IntArray(7) { colourAt(if (it <= 3) it / 3f else (6 - it) / 3f) }, null)
                c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = sweep; alpha = (50 + 70 * beat).toInt() })
                val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.STROKE; strokeWidth = maxOf(2f, bw * 0.01f); strokeJoin = Paint.Join.ROUND; shader = sweep }
                c.drawPath(path, Paint(line).apply { strokeWidth = line.strokeWidth * (4f + 3f * beat); maskFilter = android.graphics.BlurMaskFilter(maxOf(6f, bw * 0.025f), android.graphics.BlurMaskFilter.Blur.NORMAL) })
                c.drawPath(path, line)
                c.drawCircle(cx, cy, base * 0.85f, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(cx, cy, base * 0.85f, intArrayOf((colourAt(0f) and 0x00FFFFFF) or ((30 + 140 * beat).toInt().coerceAtMost(200) shl 24), 0), null, Shader.TileMode.CLAMP) })
            }
            7 -> { // Sound rings: bass, mids and highs as glowing rings that swell and brighten.
                val cx = bw / 2f; val cy = bh / 2f
                fun range(a: Int, b: Int): Float { var sum = 0f; for (k in a until b) sum += v[k]; return sum / (b - a) }
                val parts = listOf(range(0, 10), range(10, 30), range(30, nb))
                for ((i, e) in parts.withIndex()) {
                    val r = bw * (0.14f + 0.12f * i) * (1f + 0.25f * e + (if (i == 0) 0.12f * beat else 0f))
                    val sw = bw * (0.012f + 0.035f * e)
                    val cc = colourAt(i / 2f)
                    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.STROKE; strokeWidth = sw; color = cc }
                    c.drawCircle(cx, cy, r, Paint(ring).apply { strokeWidth = sw * 3f; alpha = (60 + 150 * e).toInt().coerceAtMost(220); maskFilter = android.graphics.BlurMaskFilter(maxOf(5f, sw * 2f), android.graphics.BlurMaskFilter.Blur.NORMAL) })
                    c.drawCircle(cx, cy, r, ring)
                }
                // A ripple that runs out on each beat.
                if (beat > 0.05f) c.drawCircle(cx, cy, bw * (0.48f - 0.22f * beat), Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.STROKE; strokeWidth = maxOf(2f, bw * 0.006f); color = colourAt(0f); alpha = (200 * beat).toInt() })
            }
            8 -> { // Particles: sparks stream out from a ring, faster and brighter with the music, bursting on beats.
                val cx = bw / 2f; val cy = bh / 2f
                var energy = 0f; for (k in 0 until nb) energy += v[k]; energy /= nb
                val base = bw * 0.16f * (1f + 0.15f * beat)
                val pp = Paint(Paint.ANTI_ALIAS_FLAG)
                for (p in 0 until 140) {
                    val ph = ((songMs / (1400f - 600f * energy)) + hash(p, 1, 0)) % 1f
                    val a = hash(p, 2, 0) * 2f * Math.PI.toFloat() + (songMs / 12000f)
                    val band = (hash(p, 3, 0) * nb).toInt().coerceIn(0, nb - 1)
                    val r = base + ph * bw * 0.34f * (0.5f + v[band] + 0.6f * beat)
                    val x = cx + r * kotlin.math.cos(a); val y = cy + r * kotlin.math.sin(a)
                    val sz = bw * 0.006f * (0.6f + 1.4f * v[band]) * (1f - ph * 0.5f)
                    pp.color = colourAt(band / (nb - 1f)); pp.alpha = ((1f - ph) * (120 + 135 * v[band])).toInt().coerceIn(0, 255)
                    c.drawCircle(x, y, sz.coerceAtLeast(1.2f), pp)
                }
                c.drawCircle(cx, cy, base * 1.6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(cx, cy, base * 1.6f, intArrayOf((colourAt(0.3f) and 0x00FFFFFF) or ((60 + 150 * beat).toInt().coerceAtMost(230) shl 24), 0), null, Shader.TileMode.CLAMP) })
                c.drawCircle(cx, cy, base, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.STROKE; strokeWidth = maxOf(2f, bw * 0.008f); color = colourAt(0.5f) })
            }
            9 -> { // Bass orb: a glowing sphere that breathes with the bass and throws a ring on every beat.
                val cx = bw / 2f; val cy = bh / 2f
                var bass = 0f; for (k in 0 until 10) bass += v[k]; bass /= 10f
                var high = 0f; for (k in 40 until nb) high += v[k]; high /= (nb - 40)
                val r = bw * (0.17f + 0.09f * bass + 0.05f * beat)
                c.drawCircle(cx, cy, r * 2.2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(cx, cy, r * 2.2f, intArrayOf((colourAt(0f) and 0x00FFFFFF) or ((50 + 150 * bass).toInt().coerceAtMost(220) shl 24), 0), null, Shader.TileMode.CLAMP) })
                c.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(cx - r * 0.3f, cy - r * 0.35f, r * 1.3f, intArrayOf(0xFFFFFFFF.toInt(), colourAt(0.3f), colourAt(1f) and 0x00FFFFFF or 0xFF000000.toInt()), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP) })
                // Shimmer of the highs on the surface.
                val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFFFFF.toInt() }
                for (q in 0 until 24) {
                    val a = hash(q, 5, (songMs / 120).toInt()) * 2f * Math.PI.toFloat()
                    val d = r * (0.95f + 0.1f * hash(q, 6, (songMs / 120).toInt()))
                    sp.alpha = (high * 255 * hash(q, 9, (songMs / 80).toInt())).toInt().coerceIn(0, 255)
                    c.drawCircle(cx + d * kotlin.math.cos(a), cy + d * kotlin.math.sin(a), maxOf(1.2f, bw * 0.004f), sp)
                }
                if (beat > 0.05f) c.drawCircle(cx, cy, r * (1f + 1.2f * (1f - beat)), Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.STROKE; strokeWidth = maxOf(2f, bw * 0.01f * beat); color = colourAt(0.6f); alpha = (230 * beat).toInt() })
            }
        }
        return out
    }

    /** Where the drone is at [ms]: index into the track and the share between two points. */
    private fun posAt(t: List<Double>, ms: Long): Pair<Double, Double>? {
        val n = t.size / 3
        if (n == 0) return null
        if (ms <= t[0]) return t[1] to t[2]
        for (i in 1 until n) {
            if (ms <= t[i * 3]) {
                val a = t[(i - 1) * 3]; val b = t[i * 3]
                val k = if (b > a) (ms - a) / (b - a) else 0.0
                return (t[(i - 1) * 3 + 1] + (t[i * 3 + 1] - t[(i - 1) * 3 + 1]) * k) to (t[(i - 1) * 3 + 2] + (t[i * 3 + 2] - t[(i - 1) * 3 + 2]) * k)
            }
        }
        return t[(n - 1) * 3 + 1] to t[(n - 1) * 3 + 2]
    }

    private fun map(l: Layer, h: Int, ms: Long): Bitmap {
        val side = max(48, (l.size * h).toInt())
        val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val r = side * 0.12f
        c.drawRoundRect(RectF(0f, 0f, side.toFloat(), side.toFloat()), r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99000000.toInt() })
        val t = l.track.orEmpty()
        val n = t.size / 3
        if (n < 2) return out
        // Flat projection: longitude shrinks with latitude; north up; the track fitted into the box.
        var la0 = Double.MAX_VALUE; var la1 = -Double.MAX_VALUE; var lo0 = Double.MAX_VALUE; var lo1 = -Double.MAX_VALUE
        for (i in 0 until n) { la0 = min(la0, t[i * 3 + 1]); la1 = max(la1, t[i * 3 + 1]); lo0 = min(lo0, t[i * 3 + 2]); lo1 = max(lo1, t[i * 3 + 2]) }
        val kx = cos(Math.toRadians((la0 + la1) / 2))
        val spanX = (lo1 - lo0) * kx; val spanY = la1 - la0
        val span = max(max(spanX, spanY), 1e-6)
        val pad = side * 0.14f; val box = side - pad * 2
        fun px(la: Double, lo: Double): Pair<Float, Float> {
            val x = pad + (((lo - lo0) * kx + (span - spanX) / 2) / span * box).toFloat()
            val y = pad + ((1 - ((la - la0) + (span - spanY) / 2) / span) * box).toFloat()
            return x to y
        }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = side * 0.022f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        val all = Path(); val done = Path()
        val now = if (ms < 0) t[(n - 1) * 3] else ms.toDouble()
        for (i in 0 until n) {
            val (x, y) = px(t[i * 3 + 1], t[i * 3 + 2])
            if (i == 0) all.moveTo(x, y) else all.lineTo(x, y)
            if (t[i * 3] <= now) { if (i == 0) done.moveTo(x, y) else done.lineTo(x, y) }
        }
        val cur = posAt(t, now.toLong())
        cur?.let { (la, lo) -> val (x, y) = px(la, lo); done.lineTo(x, y) }
        line.color = 0x66FFFFFF; c.drawPath(all, line)
        line.color = l.color or 0xFF000000.toInt(); c.drawPath(done, line)
        cur?.let { (la, lo) ->
            val (x, y) = px(la, lo)
            c.drawCircle(x, y, side * 0.05f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
            c.drawCircle(x, y, side * 0.032f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = l.color or 0xFF000000.toInt() })
        }
        // Start point.
        val (sx, sy) = px(t[1], t[2])
        c.drawCircle(sx, sy, side * 0.025f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFFFFF.toInt() })
        // North arrow.
        val na = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFFFFF.toInt(); textSize = side * 0.09f; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
        c.drawText("N", side - pad * 0.6f, pad * 0.85f, na)
        return out
    }

    private fun flare(l: Layer, w: Int, h: Int): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val cx = (l.fxX ?: 0.72f) * w; val cy = (l.fxY ?: 0.28f) * h
        val s = min(w, h).toFloat()
        val style = (l.fxA ?: 0f).toInt()
        val core = when (style) { 1 -> 0xFFCFE4FF.toInt(); 2 -> 0xFFFFF4E0.toInt(); else -> 0xFFFFF0C8.toInt() }
        val glow = when (style) { 1 -> 0x6650A0FF; 2 -> 0x55FFE6C0; else -> 0x77FFA040 }
        fun radial(x: Float, y: Float, r: Float, inner: Int, outer: Int = 0) {
            c.drawCircle(x, y, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(x, y, r, inner, outer, Shader.TileMode.CLAMP) })
        }
        // Wide glow, bright core.
        radial(cx, cy, s * (if (style == 2) 0.75f else 0.55f), glow)
        radial(cx, cy, s * 0.09f, core)
        radial(cx, cy, s * 0.03f, 0xFFFFFFFF.toInt())
        // Streak across: long and blue for anamorphic, short and warm otherwise.
        if (style != 2) {
            val len = if (style == 1) w * 0.9f else w * 0.35f
            val th = s * (if (style == 1) 0.012f else 0.008f)
            val sc = if (style == 1) 0xCC6FB0FF.toInt() else 0x99FFD8A0.toInt()
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(cx - len, cy, cx + len, cy, intArrayOf(0, sc, 0xFFFFFFFF.toInt(), sc, 0), floatArrayOf(0f, 0.35f, 0.5f, 0.65f, 1f), Shader.TileMode.CLAMP) }
            c.drawOval(RectF(cx - len, cy - th, cx + len, cy + th), p)
        }
        // Ghosts along the line through the middle of the picture.
        val gx = w / 2f - cx; val gy = h / 2f - cy
        val ghosts = listOf(0.5f to 0.035f, 0.85f to 0.02f, 1.25f to 0.06f, 1.6f to 0.028f, 2.0f to 0.09f)
        for ((i, g) in ghosts.withIndex()) {
            val x = cx + gx * g.first * 2f; val y = cy + gy * g.first * 2f
            val col = when (style) {
                1 -> if (i % 2 == 0) 0x3360A0FF else 0x2890FFE0
                2 -> 0x22FFFFFF
                else -> if (i % 2 == 0) 0x33FFB060 else 0x2860FF90
            }
            radial(x, y, s * g.second, col, col and 0x00FFFFFF)
            if (style == 0 && i == 4) c.drawCircle(x, y, s * g.second, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.STROKE; strokeWidth = s * 0.004f; color = 0x30FFC890 })
        }
        return out
    }
}
