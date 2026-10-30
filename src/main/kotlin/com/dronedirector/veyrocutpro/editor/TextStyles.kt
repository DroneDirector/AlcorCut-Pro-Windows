package com.dronedirector.veyrocutpro.editor

import android.content.res.AssetManager
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.max

/**
 * Fonts for text layers (photo and video editor): free fonts (SIL Open Font License / Apache 2.0) in the app's
 * assets, see assets/fonts/FONT_LICENSES.txt. [Layer.face] holds the chosen font's id.
 */
object TextFonts {
    class F(val id: String, val file: String, val label: String, val cat: Int, val wght: Int?)

    val CATEGORIES = listOf("Simple", "Serif", "Typewriter & mono", "Script & handwritten", "Display & fun")

    val ALL = listOf(
        F("arimo", "arimo.ttf", "Arimo (like Arial)", 0, null),
        F("arimo_bold", "arimo_bold.ttf", "Arimo Bold (like Arial Bold)", 0, null),
        F("carlito", "orig_Carlito-Regular.ttf", "Carlito (like Calibri)", 0, null),
        F("carlito_bold", "orig_Carlito-Bold.ttf", "Carlito Bold (like Calibri Bold)", 0, null),
        F("roboto", "roboto.ttf", "Roboto", 0, null),
        F("roboto_bold", "roboto_bold.ttf", "Roboto Bold", 0, null),
        F("opensans", "opensans.ttf", "Open Sans", 0, null),
        F("opensans_bold", "opensans_bold.ttf", "Open Sans Bold", 0, null),
        F("lato", "orig_Lato-Regular.ttf", "Lato", 0, null),
        F("lato_black", "orig_Lato-Black.ttf", "Lato Black", 0, null),
        F("montserrat", "montserrat.ttf", "Montserrat", 0, null),
        F("montserrat_xbold", "montserrat_xbold.ttf", "Montserrat Extra Bold", 0, null),
        F("poppins", "poppins.ttf", "Poppins", 0, null),
        F("poppins_bold", "poppins_bold.ttf", "Poppins Bold", 0, null),
        F("inter", "inter.ttf", "Inter", 0, null),
        F("inter_bold", "inter_bold.ttf", "Inter Bold", 0, null),
        F("nunito", "nunito.ttf", "Nunito", 0, null),
        F("nunito_xbold", "nunito_xbold.ttf", "Nunito Extra Bold", 0, null),
        F("raleway_light", "orig_Raleway_wght.ttf", "Raleway Light", 0, 300),
        F("raleway_bold", "orig_Raleway_wght.ttf", "Raleway Bold", 0, 700),
        F("sourcesans", "orig_SourceSans3_wght.ttf", "Source Sans 3", 0, 400),
        F("worksans", "worksans.ttf", "Work Sans", 0, null),
        F("josefin", "orig_JosefinSans_wght.ttf", "Josefin Sans", 0, 400),
        F("quicksand", "orig_Quicksand_wght.ttf", "Quicksand", 0, 500),
        F("barlowcond", "barlowcond.ttf", "Barlow Condensed", 0, null),
        F("robotocond", "robotocond.ttf", "Roboto Condensed Bold", 0, null),
        F("oswald", "oswald.ttf", "Oswald", 0, null),
        F("teko", "teko.ttf", "Teko", 0, null),
        F("bebas", "bebas.ttf", "Bebas Neue", 0, null),
        F("anton", "anton.ttf", "Anton", 0, null),
        F("archivoblack", "archivoblack.ttf", "Archivo Black", 0, null),
        F("staatliches", "staatliches.ttf", "Staatliches", 0, null),
        F("tinos", "tinos.ttf", "Tinos (like Times New Roman)", 1, null),
        F("tinos_bold", "tinos_bold.ttf", "Tinos Bold (like Times New Roman Bold)", 1, null),
        F("playfair", "orig_PlayfairDisplay_wght.ttf", "Playfair Display", 1, 400),
        F("playfair_black", "orig_PlayfairDisplay_wght.ttf", "Playfair Display Black", 1, 900),
        F("dmserif", "orig_DMSerifDisplay-Regular.ttf", "DM Serif Display", 1, null),
        F("garamond", "garamond.ttf", "EB Garamond", 1, null),
        F("garamond_bold", "garamond_bold.ttf", "EB Garamond Bold", 1, null),
        F("lora", "orig_Lora_wght.ttf", "Lora", 1, 400),
        F("ptserif", "orig_PT_Serif-Web-Regular.ttf", "PT Serif", 1, null),
        F("baskerville", "orig_LibreBaskerville_wght.ttf", "Libre Baskerville", 1, 400),
        F("robotoslab", "robotoslab.ttf", "Roboto Slab", 1, null),
        F("cinzel", "cinzel.ttf", "Cinzel", 1, null),
        F("abril", "orig_AbrilFatface-Regular.ttf", "Abril Fatface", 1, null),
        F("alfaslab", "orig_AlfaSlabOne-Regular.ttf", "Alfa Slab One", 1, null),
        F("cousine", "cousine.ttf", "Cousine (like Courier New)", 2, null),
        F("robotomono", "robotomono.ttf", "Roboto Mono", 2, null),
        F("spacemono", "spacemono.ttf", "Space Mono", 2, null),
        F("specialelite", "specialelite.ttf", "Special Elite (typewriter)", 2, null),
        F("pacifico", "pacifico.ttf", "Pacifico", 3, null),
        F("lobster", "orig_Lobster-Regular.ttf", "Lobster", 3, null),
        F("dancing", "orig_DancingScript_wght.ttf", "Dancing Script", 3, 600),
        F("greatvibes", "greatvibes.ttf", "Great Vibes", 3, null),
        F("satisfy", "satisfy.ttf", "Satisfy", 3, null),
        F("caveat", "caveat.ttf", "Caveat", 3, null),
        F("marker", "marker.ttf", "Permanent Marker", 3, null),
        F("shadows", "shadows.ttf", "Shadows Into Light", 3, null),
        F("kaushan", "orig_KaushanScript-Regular.ttf", "Kaushan Script", 3, null),
        F("sacramento", "orig_Sacramento-Regular.ttf", "Sacramento", 3, null),
        F("yellowtail", "yellowtail.ttf", "Yellowtail", 3, null),
        F("marck", "orig_MarckScript-Regular.ttf", "Marck Script", 3, null),
        F("amatic", "amatic.ttf", "Amatic SC", 3, null),
        F("righteous", "orig_Righteous-Regular.ttf", "Righteous", 4, null),
        F("bangers", "bangers.ttf", "Bangers (comic)", 4, null),
        F("pressstart", "orig_PressStart2P-Regular.ttf", "Press Start 2P (pixel)", 4, null),
        F("monoton", "orig_Monoton-Regular.ttf", "Monoton (neon lines)", 4, null),
        F("fredoka", "fredoka.ttf", "Fredoka", 4, null),
        F("comfortaa", "orig_Comfortaa_wght.ttf", "Comfortaa", 4, 600),
        F("orbitron", "orig_Orbitron_wght.ttf", "Orbitron (sci-fi)", 4, 700),
        F("audiowide", "orig_Audiowide-Regular.ttf", "Audiowide", 4, null),
        F("russo", "orig_RussoOne-Regular.ttf", "Russo One", 4, null),
        F("blackops", "blackops.ttf", "Black Ops One (stencil)", 4, null),
        F("creepster", "orig_Creepster-Regular.ttf", "Creepster (horror)", 4, null),
        F("bungee", "bungee.ttf", "Bungee", 4, null),
        F("rubikmono", "rubikmono.ttf", "Rubik Mono One", 4, null),
        F("luckiest", "luckiest.ttf", "Luckiest Guy", 4, null),
        F("titan", "orig_TitanOne-Regular.ttf", "Titan One", 4, null),
        F("exo2", "exo2.ttf", "Exo 2", 4, null),
    )

    @Volatile private var assets: AssetManager? = null
    private val cache = HashMap<String, Typeface>()

    /** Called once per process (the app's start). */
    fun init(a: AssetManager) { assets = a }

    fun byId(id: String?): F? = if (id == null) null else ALL.firstOrNull { it.id == id }

    /** The typeface for font [id], or null (then the layer's built-in font is used). */
    fun typeface(id: String?): Typeface? {
        val f = byId(id) ?: return null
        synchronized(cache) { cache[f.id]?.let { return it } }
        val a = assets ?: return null
        val tf = try {
            val b = Typeface.Builder(a, "fonts/" + f.file)
            if (f.wght != null) b.setFontVariationSettings("'wght' " + f.wght)
            b.build()
        } catch (_: Throwable) { null } ?: return null
        synchronized(cache) { cache[f.id] = tf }
        return tf
    }
}

/** Text effects ([Layer.textFx]): drawn into the text's own picture, so the preview and the export match. */
object TextFx {
    val NAMES = listOf(
        "None", "Neon glow", "Gradient", "Rainbow", "Chrome", "Gold", "3D block", "Long shadow", "Hollow outline", "Double outline",
        "Retro 80s", "Glitch", "Fire", "Ice", "Comic pop", "Embossed", "Soft shadow", "Highlighter", "Sticker", "Pastel",
    )

    /** Extra room round the letters for effect [fx], as a share of the text height. */
    fun pad(fx: Int): Float = when (fx) {
        1, 12, 13, 16 -> 0.45f
        6, 7, 10, 14 -> 0.45f
        9, 18 -> 0.3f
        11 -> 0.15f
        else -> 0f
    }

    /** Draws one line [s] with its left end at [x] and baseline at [y]. [top] / [bot] bound the whole text. */
    fun draw(c: Canvas, s: String, x: Float, y: Float, base: Paint, fx: Int, col: Int, col2: Int, px: Float, top: Float, bot: Float) {
        val w = base.measureText(s)
        fun p() = Paint(base).apply { clearShadowLayer() }
        fun glow(color: Int, r: Float, a: Int = 255) = p().apply { this.color = color; alpha = a; maskFilter = BlurMaskFilter(max(1f, r), BlurMaskFilter.Blur.NORMAL) }
        fun stroke(color: Int, wd: Float) = p().apply { style = Paint.Style.STROKE; strokeWidth = wd; strokeJoin = Paint.Join.ROUND; this.color = color }
        fun vgrad(cols: IntArray, pos: FloatArray? = null) = p().apply { shader = LinearGradient(0f, top, 0f, bot, cols, pos, Shader.TileMode.CLAMP) }
        fun hgrad(cols: IntArray) = p().apply { shader = LinearGradient(x, 0f, x + w, 0f, cols, null, Shader.TileMode.CLAMP) }
        when (fx) {
            1 -> { // Neon glow: wide coloured glow, a bright tube, a white core.
                c.drawText(s, x, y, glow(col, px * 0.32f)); c.drawText(s, x, y, glow(col, px * 0.12f))
                c.drawText(s, x, y, stroke(col, px * 0.07f)); c.drawText(s, x, y, p().apply { color = 0xFFFFFFFF.toInt() })
            }
            2 -> c.drawText(s, x, y, vgrad(intArrayOf(col, col2)))
            3 -> c.drawText(s, x, y, hgrad(intArrayOf(0xFFFF3B30.toInt(), 0xFFFF9500.toInt(), 0xFFFFCC00.toInt(), 0xFF34C759.toInt(), 0xFF0A84FF.toInt(), 0xFFBF5AF2.toInt())))
            4 -> { // Chrome
                c.drawText(s, x, y, stroke(0xFF2A2F36.toInt(), px * 0.06f))
                c.drawText(s, x, y, vgrad(intArrayOf(0xFFFFFFFF.toInt(), 0xFFC4CCD4.toInt(), 0xFF5E6873.toInt(), 0xFFE2E7EC.toInt(), 0xFF8A949E.toInt()), floatArrayOf(0f, 0.45f, 0.5f, 0.62f, 1f)))
            }
            5 -> { // Gold
                c.drawText(s, x, y, stroke(0xFF5A3A08.toInt(), px * 0.06f))
                c.drawText(s, x, y, vgrad(intArrayOf(0xFFFFF3B0.toInt(), 0xFFE0A526.toInt(), 0xFF9C6A12.toInt(), 0xFFF7D774.toInt(), 0xFFB8862B.toInt()), floatArrayOf(0f, 0.45f, 0.5f, 0.65f, 1f)))
            }
            6 -> { // 3D block: the letters stacked down-right in a darker shade.
                val n = (px * 0.12f).toInt().coerceIn(2, 40)
                val dark = p().apply { color = darker(col, 0.45f) }
                for (i in n downTo 1) c.drawText(s, x + i, y + i, dark)
                c.drawText(s, x, y, p().apply { color = col })
            }
            7 -> { // Long shadow
                val n = (px * 0.4f).toInt().coerceIn(4, 120)
                val sh = p().apply { color = 0x30000000 }
                for (i in n downTo 1 step 2) c.drawText(s, x + i, y + i, sh)
                c.drawText(s, x, y, p().apply { color = col })
            }
            8 -> c.drawText(s, x, y, stroke(col, px * 0.05f))
            9 -> {
                c.drawText(s, x, y, stroke(col2, px * 0.26f)); c.drawText(s, x, y, stroke(0xFF000000.toInt(), px * 0.12f))
                c.drawText(s, x, y, p().apply { color = col })
            }
            10 -> { // Retro 80s
                c.drawText(s, x + px * 0.06f, y + px * 0.06f, p().apply { color = 0xFF00E5FF.toInt() })
                c.drawText(s, x, y, vgrad(intArrayOf(0xFFFFE259.toInt(), 0xFFFF4FA3.toInt(), 0xFF8E2DE2.toInt())))
                c.drawText(s, x, y, stroke(0xFFFFFFFF.toInt(), px * 0.02f))
            }
            11 -> { // Glitch: red and cyan copies pulled apart.
                val d = px * 0.05f
                c.drawText(s, x - d, y, p().apply { color = 0xCCFF0040.toInt() }); c.drawText(s, x + d, y, p().apply { color = 0xCC00F0FF.toInt() })
                c.drawText(s, x, y, p().apply { color = col })
            }
            12 -> { // Fire
                c.drawText(s, x, y, glow(0xFFFF5A00.toInt(), px * 0.3f, 220))
                c.drawText(s, x, y, vgrad(intArrayOf(0xFFFF2D00.toInt(), 0xFFFF8A00.toInt(), 0xFFFFE066.toInt())))
            }
            13 -> { // Ice
                c.drawText(s, x, y, glow(0xFF6FE7FF.toInt(), px * 0.28f, 200))
                c.drawText(s, x, y, vgrad(intArrayOf(0xFFFFFFFF.toInt(), 0xFFBDF3FF.toInt(), 0xFF3FB8E8.toInt())))
                c.drawText(s, x, y, stroke(0xAAFFFFFF.toInt(), px * 0.015f))
            }
            14 -> { // Comic pop
                c.drawText(s, x + px * 0.08f, y + px * 0.08f, stroke(0xFF000000.toInt(), px * 0.16f))
                c.drawText(s, x, y, stroke(0xFF000000.toInt(), px * 0.16f))
                c.drawText(s, x, y, p().apply { color = col })
            }
            15 -> { // Embossed
                val d = max(1f, px * 0.03f)
                c.drawText(s, x - d, y - d, p().apply { color = 0x99FFFFFF.toInt() }); c.drawText(s, x + d, y + d, p().apply { color = 0x99000000.toInt() })
                c.drawText(s, x, y, p().apply { color = col })
            }
            16 -> { // Soft shadow
                c.drawText(s, x + px * 0.06f, y + px * 0.1f, glow(0xFF000000.toInt(), px * 0.22f, 190))
                c.drawText(s, x, y, p().apply { color = col })
            }
            17 -> { // Highlighter: a marker stripe behind the words.
                val fm = base.fontMetrics
                val hl = p().apply { color = col2; alpha = 170 }
                c.drawRoundRect(RectF(x - px * 0.08f, y + fm.ascent * 0.55f, x + w + px * 0.08f, y + fm.descent * 0.6f), px * 0.06f, px * 0.06f, hl)
                c.drawText(s, x, y, p().apply { color = col })
            }
            18 -> { // Sticker: a thick white edge with a shadow.
                c.drawText(s, x + px * 0.03f, y + px * 0.06f, stroke(0x66000000, px * 0.3f))
                c.drawText(s, x, y, stroke(0xFFFFFFFF.toInt(), px * 0.3f))
                c.drawText(s, x, y, p().apply { color = col })
            }
            19 -> c.drawText(s, x, y, hgrad(intArrayOf(0xFFFFB3C6.toInt(), 0xFFB5EAEA.toInt(), 0xFFFFF1A8.toInt())))
            else -> c.drawText(s, x, y, base)
        }
    }

    fun darker(c: Int, k: Float): Int {
        val r = (((c shr 16) and 0xFF) * k).toInt(); val g = (((c shr 8) and 0xFF) * k).toInt(); val b = ((c and 0xFF) * k).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}

/**
 * Lower thirds ([Layer.lowerThird]): a name (first line) and a title (the rest) on one of these designs, in the
 * layer's colour with [Layer.textColor2] as the accent.
 */
object LowerThirds {
    val NAMES = listOf(
        "Classic bar", "Blocks", "Underline", "News", "Pill", "Minimal line", "Gradient", "Brackets",
        "Two-tone", "Glass", "Tag on top", "One line", "Outline box", "Sports", "Retro", "Elegant",
    )

    fun render(l: Layer, px: Float, frameW: Int): android.graphics.Bitmap {
        val style = (l.lowerThird ?: 0).coerceIn(0, NAMES.lastIndex)
        val parts = l.text.ifEmpty { " " }.split('\n')
        val name = parts[0]; val title = parts.drop(1).joinToString(" ").trim()
        val accent = l.textColor2 ?: 0xFFFFB300.toInt()
        val txt = l.color
        val face = TextFonts.typeface(l.face)
        val np = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = px; typeface = face ?: l.font.typeface; color = txt }
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = px * 0.58f; typeface = face ?: LayerFont.LIGHT.typeface; color = txt }
        val nfm = np.fontMetrics; val tfm = tp.fontMetrics
        val nH = nfm.descent - nfm.ascent; val tH = if (title.isEmpty()) 0f else tfm.descent - tfm.ascent
        val nw = np.measureText(name); val tw = if (title.isEmpty()) 0f else tp.measureText(title)
        val pad = px * 0.35f
        val gap = px * 0.12f
        val stripe = px * 0.16f
        val oneLine = style == 11
        val W0 = if (oneLine) nw + tw + pad * 4 + stripe else max(nw, tw) + pad * 2
        val W = (W0 + stripe * 2 + pad).toInt().coerceIn(8, frameW * 2)
        val H = (if (oneLine) nH + pad * 2 else nH + tH + pad * 3 + gap).toInt().coerceAtLeast(8)
        val bmp = android.graphics.Bitmap.createBitmap(W, H, android.graphics.Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        fun box(l0: Float, t0: Float, r0: Float, b0: Float, color: Int, rad: Float = 0f) { fill.shader = null; fill.style = Paint.Style.FILL; fill.color = color; c.drawRoundRect(RectF(l0, t0, r0, b0), rad, rad, fill) }
        fun shadowed(p: Paint) = p.apply { setShadowLayer(px * 0.08f, 0f, px * 0.04f, 0xAA000000.toInt()) }
        val nTop = pad; val nBase = nTop - nfm.ascent
        val tTop = nTop + nH + gap; val tBase = tTop - tfm.ascent
        val x0 = stripe + pad
        val wf = W.toFloat(); val hf = H.toFloat()
        when (style) {
            0 -> { box(0f, 0f, wf, hf, 0xCC000000.toInt()); box(0f, 0f, stripe, hf, accent) }
            1 -> {
                box(0f, 0f, nw + x0 + pad, nTop + nH + gap * 0.5f, accent)
                if (title.isNotEmpty()) box(0f, nTop + nH + gap * 0.5f, tw + x0 + pad, hf, 0xE6111111.toInt())
                np.color = 0xFF111111.toInt()
            }
            2 -> { shadowed(np); shadowed(tp); box(x0, nTop + nH + gap * 0.2f, x0 + max(nw, tw), nTop + nH + gap * 0.2f + px * 0.07f, accent) }
            3 -> {
                box(0f, 0f, nw + x0 + pad, nTop + nH + gap * 0.5f, 0xFFFFFFFF.toInt()); np.color = 0xFF111111.toInt()
                if (title.isNotEmpty()) { box(0f, nTop + nH + gap * 0.5f, max(tw, nw * 0.6f) + x0 + pad, hf, accent); tp.color = 0xFFFFFFFF.toInt() }
            }
            4 -> { box(0f, 0f, wf, hf, 0xD9000000.toInt(), hf / 2f); fill.color = accent; c.drawCircle(stripe * 1.4f, hf / 2f, stripe * 0.7f, fill) }
            5 -> { shadowed(np); shadowed(tp); box(stripe * 0.6f, pad * 0.5f, stripe * 0.6f + px * 0.07f, hf - pad * 0.5f, accent) }
            6 -> { fill.style = Paint.Style.FILL; fill.shader = LinearGradient(0f, 0f, wf, 0f, intArrayOf(accent, (accent and 0x00FFFFFF) or 0x99000000.toInt(), 0x00000000), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP); c.drawRect(0f, 0f, wf, hf, fill); fill.shader = null }
            7 -> {
                shadowed(np); shadowed(tp)
                val s = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.STROKE; strokeWidth = px * 0.07f; color = accent; strokeCap = Paint.Cap.SQUARE }
                val k = px * 0.45f; val i = s.strokeWidth
                c.drawLine(i, i, i + k, i, s); c.drawLine(i, i, i, i + k, s)
                c.drawLine(wf - i, hf - i, wf - i - k, hf - i, s); c.drawLine(wf - i, hf - i, wf - i, hf - i - k, s)
            }
            8 -> { box(0f, 0f, wf, nTop + nH + gap * 0.5f, accent); np.color = 0xFF111111.toInt(); if (title.isNotEmpty()) box(0f, nTop + nH + gap * 0.5f, wf, hf, 0xF2111111.toInt()) }
            9 -> {
                box(0f, 0f, wf, hf, 0x40FFFFFF, px * 0.3f)
                c.drawRoundRect(RectF(1f, 1f, wf - 1f, hf - 1f), px * 0.3f, px * 0.3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.STROKE; strokeWidth = max(1f, px * 0.03f); color = 0x80FFFFFF.toInt() })
                shadowed(np); shadowed(tp)
            }
            10 -> {
                // The title as a small tag above the name.
                if (title.isNotEmpty()) {
                    box(x0 - pad * 0.4f, pad * 0.4f, x0 + tw + pad * 0.4f, pad * 0.4f + tH + gap, accent); tp.color = 0xFF111111.toInt()
                    c.drawText(title, x0, pad * 0.4f + gap * 0.5f - tfm.ascent, tp)
                }
                shadowed(np)
                c.drawText(name, x0, (if (title.isNotEmpty()) pad * 0.4f + tH + gap * 2f else nTop) - nfm.ascent, np)
                return bmp
            }
            11 -> {
                box(0f, 0f, wf, hf, 0xD9000000.toInt())
                c.drawText(name, x0, pad - nfm.ascent, np)
                val dx = x0 + nw + pad
                box(dx, pad * 0.6f, dx + stripe * 0.5f, hf - pad * 0.6f, accent)
                tp.color = accent
                c.drawText(title, dx + stripe * 0.5f + pad, hf / 2f - (tfm.ascent + tfm.descent) / 2f, tp)
                return bmp
            }
            12 -> {
                c.drawRect(px * 0.04f, px * 0.04f, wf - px * 0.04f, hf - px * 0.04f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.STROKE; strokeWidth = px * 0.07f; color = accent })
                shadowed(np); shadowed(tp)
            }
            13 -> {
                val sk = px * 0.35f
                fun para(l0: Float, t0: Float, r0: Float, b0: Float, color: Int) {
                    fill.shader = null; fill.color = color
                    c.drawPath(Path().apply { moveTo(l0 + sk, t0); lineTo(r0, t0); lineTo(r0 - sk, b0); lineTo(l0, b0); close() }, fill)
                }
                para(0f, 0f, nw + x0 + pad + sk, nTop + nH + gap * 0.5f, accent); np.color = 0xFF111111.toInt()
                if (title.isNotEmpty()) para(sk * 0.5f, nTop + nH + gap * 0.5f, tw + x0 + pad + sk, hf, 0xF2111111.toInt())
            }
            14 -> {
                box(0f, 0f, wf, hf, 0xF2111111.toInt())
                for (k in 0 until 3) box(wf - stripe * (3 - k) * 1.6f, 0f, wf - stripe * (3 - k) * 1.6f + stripe, hf, if (k == 1) 0xFFFF3B30.toInt() else accent)
                np.color = accent
            }
            15 -> {
                shadowed(np); shadowed(tp)
                val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent; strokeWidth = max(1f, px * 0.03f) }
                c.drawLine(wf * 0.3f, pad * 0.5f, wf * 0.7f, pad * 0.5f, lp); c.drawLine(wf * 0.3f, hf - pad * 0.5f, wf * 0.7f, hf - pad * 0.5f, lp)
                c.drawText(name, (wf - nw) / 2f, nBase, np)
                if (title.isNotEmpty()) c.drawText(title, (wf - tw) / 2f, tBase, tp)
                return bmp
            }
        }
        c.drawText(name, x0, nBase, np)
        if (title.isNotEmpty()) c.drawText(title, x0, tBase, tp)
        return bmp
    }
}

/** Ready-made kinds of text (Layers → MORE TEXT, photo Text panel). */
object TextTypes {
    class T(val name: String, val text: String, val size: Float, val x: Float, val y: Float, val face: String?, val fx: Int?, val color: Int,
            val box: Boolean = false, val anim: LayerAnim = LayerAnim.FADE, val color2: Int? = null)
    val ALL = listOf(
        T("Headline", "HEADLINE", 0.12f, 0.5f, 0.5f, "anton", null, 0xFFFFFFFF.toInt(), anim = LayerAnim.POP),
        T("Subtitle", "Subtitle text", 0.045f, 0.5f, 0.88f, "arimo", null, 0xFFFFFFFF.toInt(), box = true),
        T("Quote", "“A quote that matters.”", 0.06f, 0.5f, 0.5f, "playfair", null, 0xFFFFFFFF.toInt(), anim = LayerAnim.FADE),
        T("Speech bubble", "Hello!", 0.06f, 0.65f, 0.3f, "fredoka", 18, 0xFF111111.toInt(), anim = LayerAnim.POP),
        T("Hashtag", "#adventure", 0.055f, 0.5f, 0.85f, "montserrat_xbold", 2, 0xFFFF4FA3.toInt(), color2 = 0xFFFFB300.toInt(), anim = LayerAnim.SLIDE_UP),
        T("Social handle", "@yourname", 0.045f, 0.82f, 0.9f, "poppins_bold", 16, 0xFFFFFFFF.toInt(), anim = LayerAnim.SLIDE_LEFT),
        T("Breaking news", "BREAKING NEWS", 0.06f, 0.5f, 0.8f, "oswald", null, 0xFFFFFFFF.toInt(), box = true, anim = LayerAnim.REVEAL),
        T("Sale tag", "SALE −50%", 0.08f, 0.5f, 0.5f, "bangers", 14, 0xFFFFD60A.toInt(), anim = LayerAnim.BOUNCE),
        T("Date", "28 OCTOBER 2026", 0.04f, 0.5f, 0.12f, "cousine", null, 0xFFFFFFFF.toInt(), anim = LayerAnim.TYPEWRITER),
        T("Call-out", "→ Look here", 0.05f, 0.65f, 0.4f, "marker", null, 0xFFFFD60A.toInt(), anim = LayerAnim.POP),
        T("Handwritten note", "Best day ever", 0.07f, 0.5f, 0.75f, "caveat", null, 0xFFFFFFFF.toInt(), anim = LayerAnim.REVEAL),
        T("Wedding", "Anna & Jón", 0.1f, 0.5f, 0.5f, "greatvibes", 5, 0xFFFFFFFF.toInt(), anim = LayerAnim.FADE),
        T("Gaming", "LEVEL UP!", 0.07f, 0.5f, 0.5f, "pressstart", 11, 0xFFFFFFFF.toInt(), anim = LayerAnim.ZOOM),
        T("Sci-fi", "MISSION START", 0.07f, 0.5f, 0.5f, "orbitron", 1, 0xFF00E5FF.toInt(), anim = LayerAnim.FADE),
        T("Horror", "DON'T LOOK BACK", 0.08f, 0.5f, 0.5f, "creepster", 16, 0xFFD50000.toInt(), anim = LayerAnim.FADE),
        T("Movie title", "THE JOURNEY", 0.09f, 0.5f, 0.5f, "cinzel", 5, 0xFFFFFFFF.toInt(), anim = LayerAnim.FADE),
        T("Neon sign", "Open late", 0.08f, 0.5f, 0.45f, "monoton", 1, 0xFFFF2D95.toInt(), anim = LayerAnim.FADE),
        T("Retro", "SUMMER 86", 0.1f, 0.5f, 0.5f, "righteous", 10, 0xFFFFFFFF.toInt(), anim = LayerAnim.SLIDE_UP),
        T("Comic", "BOOM!", 0.13f, 0.5f, 0.5f, "bangers", 14, 0xFFFFD60A.toInt(), anim = LayerAnim.POP),
        T("Typewriter", "Day one.", 0.06f, 0.5f, 0.5f, "specialelite", null, 0xFFFFFFFF.toInt(), anim = LayerAnim.TYPEWRITER),
    )
}
