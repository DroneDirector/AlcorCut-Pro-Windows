package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.common.OverlaySettings
import androidx.media3.effect.StaticOverlaySettings
import com.dronedirector.veyrocutpro.camera.GradeIO
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

enum class LayerKind(val label: String) { TEXT("Text"), STICKER("Sticker"), WATERMARK("Watermark"), LOGO("Logo"), IMAGE("Image"), VIDEO("Video"), ADJUST("Adjustment"), SHAPE("Shape"), LOTTIE("Animation"), FX("Overlay") }

enum class LayerFont(val label: String, val typeface: Typeface) {
    SANS("Sans", Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)),
    SERIF("Serif", Typeface.create(Typeface.SERIF, Typeface.BOLD)),
    MONO("Mono", Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)),
    LIGHT("Light", Typeface.create("sans-serif-light", Typeface.NORMAL)),
    CONDENSED("Condensed", Typeface.create("sans-serif-condensed", Typeface.BOLD)),
    HEAVY("Heavy", Typeface.create("sans-serif-black", Typeface.NORMAL)),
}

val STICKERS = listOf("🔥", "⭐", "❤️", "😂", "😍", "👍", "🎉", "✨", "📍", "➡️", "🎬", "📸", "🌅", "🏔️", "🌊", "🚁", "💯", "⚡", "🙌", "😎", "🎵", "☀️", "🌙", "💥")

/** A layer on top of the video (or photo): text, sticker, watermark text or logo image. */
data class Layer(
    val id: String = UUID.randomUUID().toString(),
    val kind: LayerKind = LayerKind.TEXT,
    val text: String = "Your text",
    val imageUri: Uri? = null,
    val color: Int = 0xFFFFFFFF.toInt(),
    /** Height of the text / sticker / logo as a share of the frame height. */
    val size: Float = 0.07f,
    /** Centre position, 0..1 from the top-left. */
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val opacity: Float = 1f,
    val font: LayerFont = LayerFont.SANS,
    val shadow: Boolean = true,
    val box: Boolean = false,
    /** Timeline window in ms; ignored when [wholeVideo]. */
    val startMs: Long = 0L,
    val endMs: Long = 3000L,
    val wholeVideo: Boolean = false,
    val visible: Boolean = true,
    /** Video layers: the overlaid clip, where in it to start, and its shape. */
    val videoUri: Uri? = null,
    val videoDurationMs: Long = 0L,
    val videoStartMs: Long = 0L,
    val mediaAspect: Float = 16f / 9f,
    /** Optional name you give the layer (shown in the layer list and on the timeline). */
    val name: String? = null,
    /** Locked on the timeline: can't be moved, trimmed, dragged or deleted. */
    val locked: Boolean = false,
    /** Linked to the main video: moves with it when clips are trimmed or deleted (null = linked). */
    val linked: Boolean? = null,
    /** Group: items with the same group move together on the timeline. */
    val group: String? = null,
    /** Video layers: play their own sound (off by default, as before). */
    val soundOn: Boolean? = null,
    /** Keyframes (timeline ms) for position, size and opacity; null / empty = static. */
    val keys: List<LayerKey>? = null,
    /** Blend mode for picture layers. */
    val blend: LayerBlend? = null,
    /** Chroma key: colour to remove (null = off), tolerance and softness (0..1). */
    val chroma: Int? = null,
    val chromaTol: Float? = null,
    val chromaSoft: Float? = null,
    /** Luma key: 1 = remove the dark parts, 2 = remove the bright parts (null / 0 = off); level and softness 0..1. */
    val lumaKey: Int? = null,
    val lumaLevel: Float? = null,
    val lumaSoft: Float? = null,
    /** Animation when the layer appears / disappears, and how long each takes (ms). */
    val animIn: LayerAnim? = null,
    val animOut: LayerAnim? = null,
    val animMs: Long? = null,
    /** Adjustment layer: the grade it puts on everything under it (strength = opacity). */
    val adjGrade: ProGrade? = null,
    /** Video layers: placed below the main video track instead of on top of it. */
    val below: Boolean? = null,
    /** Video layers: the shape of its frame (width / height, cropped to fill); null = the video's own shape. */
    val cellAspect: Float? = null,
    /** Video layers: rounded corners (share of the shorter side, 0..0.5), border (share of frame height) and its colour, drop shadow. */
    val corner: Float? = null,
    val border: Float? = null,
    val borderColor: Int? = null,
    val dropShadow: Boolean? = null,
    /** Word-by-word captions: when each word is spoken (timeline ms, one per word of [text]), its highlight colour and whether it gets a box. */
    val words: List<Long>? = null,
    val hiColor: Int? = null,
    val hiBox: Boolean? = null,
    /** Video layers / clips: remove the background behind people (AI, on the phone). */
    val bgRemove: Boolean? = null,
    /** Video layers: Magic Mask selection (any object) for REMOVE BACKGROUND; null = people. Invert = remove the object instead. */
    val layerMagic: MagicSel? = null,
    val bgInvert: Boolean? = null,
    /** Corner pin / planar track: the layer drawn onto four corners (see [PinQuad]); null = off. */
    val pin: List<PinQuad>? = null,
    /** Shape layers: which shape ([ShapeKind] ordinal), its width / height, and outline width (share of its size; null / 0 = filled). */
    val shape: Int? = null,
    val shapeAspect: Float? = null,
    val stroke: Float? = null,
    /** Overlay layers ([FxLayers]): which one, two settings, a position and the flight track (timeline ms, lat, lon …). */
    val fx: Int? = null,
    val fxA: Float? = null,
    val fxB: Float? = null,
    val fxX: Float? = null,
    val fxY: Float? = null,
    /** Visualizer: colour mode, and song time minus timeline time (ms). */
    val fxC: Float? = null,
    val fxD: Float? = null,
    val track: List<Double>? = null,
    /** Text: the font from [TextFonts] (null = [font]), the effect from [TextFx], a second colour (gradients, accents) and a lower-third design ([LowerThirds]). */
    val face: String? = null,
    val textFx: Int? = null,
    val textColor2: Int? = null,
    val lowerThird: Int? = null,
) {
    /** The word being spoken at [ms] (−1 before the first). */
    fun wordAt(ms: Long): Int = words?.let { w -> w.indexOfLast { it <= ms } } ?: -1
    /** The video layer's frame shape on screen. */
    val frameAspect: Float get() = cellAspect?.takeIf { it > 0f } ?: mediaAspect
    val hasPipStyle: Boolean get() = (corner ?: 0f) > 0f || (border ?: 0f) > 0f || dropShadow == true
    /** A video layer under the main video (shows where the main picture doesn't cover the frame). */
    val isBelow: Boolean get() = kind == LayerKind.VIDEO && below == true
    /** The name shown for this layer: your own name, else its text / kind. */
    val displayName: String get() = name?.takeIf { it.isNotBlank() }
        ?: when (kind) { LayerKind.TEXT, LayerKind.WATERMARK, LayerKind.STICKER -> text.replace('\n', ' '); LayerKind.LOGO, LayerKind.IMAGE -> "Image"; LayerKind.FX -> FxLayers.label(this); else -> kind.label }
    val isMedia get() = kind == LayerKind.VIDEO || kind == LayerKind.IMAGE || kind == LayerKind.LOGO || kind == LayerKind.SHAPE || kind == LayerKind.LOTTIE
    fun activeAt(ms: Long) = visible && (wholeVideo || ms in startMs..endMs)
    /** Loudness of a video layer's own sound (full by default). */
    fun soundVolume(): Float = 1f
}

/** In / out animations for layers (Layers → ANIMATION). */
enum class LayerAnim(val label: String) {
    NONE("None"), FADE("Fade"), SLIDE_UP("Slide up"), SLIDE_DOWN("Slide down"), SLIDE_LEFT("Slide left"), SLIDE_RIGHT("Slide right"),
    POP("Pop"), ZOOM("Zoom"), BOUNCE("Bounce"), REVEAL("Wipe reveal"), TYPEWRITER("Typewriter"),
    /** Rolling credits: the layer scrolls from below the frame to above it over its whole length. */
    ROLL("Roll (credits)"),
}

/** How an animation changes a layer at one moment: move (share of the frame), scale, fade, wipe and typed characters. */
data class AnimFx(val dx: Float = 0f, val dy: Float = 0f, val scale: Float = 1f, val alpha: Float = 1f, val reveal: Float = 1f, val chars: Float = 1f) {
    val isIdentity get() = this == AnimFx()
    operator fun times(o: AnimFx) = AnimFx(dx + o.dx, dy + o.dy, scale * o.scale, alpha * o.alpha, reveal * o.reveal, chars * o.chars)
}

object LayerAnims {
    fun hasAny(l: Layer) = (l.animIn ?: LayerAnim.NONE) != LayerAnim.NONE || (!l.wholeVideo && (l.animOut ?: LayerAnim.NONE) != LayerAnim.NONE)

    /** The animation state of [l] at timeline [ms]. */
    fun at(l: Layer, ms: Long): AnimFx {
        if (!hasAny(l)) return AnimFx()
        if (l.animIn == LayerAnim.ROLL || l.animOut == LayerAnim.ROLL) {
            // From just below the frame to just above it, at an even speed across the layer.
            val start = if (l.wholeVideo) 0L else l.startMs
            val len = if (l.wholeVideo) (l.animMs ?: 20_000L).coerceAtLeast(1000) else (l.endMs - l.startMs).coerceAtLeast(1)
            val p = ((ms - start).toFloat() / len).coerceIn(0f, 1f)
            val lines = l.text.lines().size.coerceAtLeast(1)
            val half = lines * l.size * 1.25f / 2f
            val from = 1f - l.y + half + 0.02f
            val to = -(l.y + half + 0.02f)
            return AnimFx(dy = from + (to - from) * p)
        }
        val len = if (l.wholeVideo) Long.MAX_VALUE else (l.endMs - l.startMs).coerceAtLeast(1)
        val d = (l.animMs ?: 500L).coerceIn(50, if (l.wholeVideo) 10_000 else len / 2 + 1)
        var fx = AnimFx()
        val ai = l.animIn ?: LayerAnim.NONE
        if (ai != LayerAnim.NONE) {
            val start = if (l.wholeVideo) 0L else l.startMs
            val p = (ms - start).toFloat() / d
            if (p < 1f) fx *= shape(l, ai, p.coerceIn(0f, 1f), true)
        }
        val ao = l.animOut ?: LayerAnim.NONE
        if (!l.wholeVideo && ao != LayerAnim.NONE) {
            val p = (l.endMs - ms).toFloat() / d
            if (p < 1f) fx *= shape(l, ao, p.coerceIn(0f, 1f), false)
        }
        return fx
    }

    private fun easeOut(p: Float) = 1f - (1f - p) * (1f - p) * (1f - p)
    private fun backOut(p: Float): Float { val c1 = 1.70158f; val c3 = c1 + 1f; val q = p - 1f; return 1f + c3 * q * q * q + c1 * q * q }
    private fun bounceOut(p: Float): Float {
        val n = 7.5625f; val d = 2.75f
        return when {
            p < 1f / d -> n * p * p
            p < 2f / d -> { val q = p - 1.5f / d; n * q * q + 0.75f }
            p < 2.5f / d -> { val q = p - 2.25f / d; n * q * q + 0.9375f }
            else -> { val q = p - 2.625f / d; n * q * q + 0.984375f }
        }
    }

    /** [p]: 0 = fully gone, 1 = settled in place. Out-animations leave in the named direction. */
    private fun shape(l: Layer, a: LayerAnim, p: Float, incoming: Boolean): AnimFx {
        val e = easeOut(p)
        val dir = if (incoming) 1f else -1f
        val text = l.kind == LayerKind.TEXT || l.kind == LayerKind.WATERMARK
        return when (a) {
            LayerAnim.NONE -> AnimFx()
            LayerAnim.FADE -> AnimFx(alpha = e)
            LayerAnim.SLIDE_UP -> AnimFx(dy = (1f - e) * 0.15f * dir, alpha = e)
            LayerAnim.SLIDE_DOWN -> AnimFx(dy = -(1f - e) * 0.15f * dir, alpha = e)
            LayerAnim.SLIDE_LEFT -> AnimFx(dx = (1f - e) * 0.2f * dir, alpha = e)
            LayerAnim.SLIDE_RIGHT -> AnimFx(dx = -(1f - e) * 0.2f * dir, alpha = e)
            LayerAnim.POP -> AnimFx(scale = backOut(p).coerceAtLeast(0.01f), alpha = (p * 3f).coerceAtMost(1f))
            LayerAnim.ZOOM -> AnimFx(scale = 1f + (1f - e) * 0.8f, alpha = e)
            LayerAnim.BOUNCE -> AnimFx(dy = -(1f - bounceOut(p)) * 0.25f * dir, alpha = (p * 4f).coerceAtMost(1f))
            LayerAnim.REVEAL -> AnimFx(reveal = e)
            LayerAnim.TYPEWRITER -> if (text) AnimFx(chars = p) else AnimFx(reveal = e)
            LayerAnim.ROLL -> AnimFx()
        }
    }

    /** Characters shown for a typewriter state ([chars] 0..1 of the text). */
    fun charCount(l: Layer, chars: Float): Int {
        val n = l.text.replace("\n", "").length
        return if (chars >= 1f) Int.MAX_VALUE else (n * chars).toInt().coerceIn(0, n)
    }

    /** A copy of [full] with only the left [reveal] share visible (same size, so it stays in place). */
    fun revealed(full: Bitmap, reveal: Float): Bitmap {
        if (reveal >= 1f) return full
        val out = Bitmap.createBitmap(full.width, full.height, Bitmap.Config.ARGB_8888)
        val w = (full.width * reveal.coerceIn(0f, 1f)).toInt()
        if (w > 0) Canvas(out).drawBitmap(full, android.graphics.Rect(0, 0, w, full.height), android.graphics.Rect(0, 0, w, full.height), null)
        return out
    }
}

object LayerRenderer {
    private val logoCache = HashMap<String, Bitmap>()

    /** Draws a layer at the size it should have on a [frameW]×[frameH] frame. */
    fun render(context: Context, l: Layer, frameW: Int, frameH: Int, chars: Int = Int.MAX_VALUE, hi: Int = -1, atMs: Long = -1L): Bitmap? {
        val px = max(8f, l.size * frameH)
        return when (l.kind) {
            LayerKind.VIDEO, LayerKind.ADJUST -> null
            LayerKind.SHAPE -> ShapeRender.render(l, px, frameW)
            LayerKind.LOTTIE -> LottieRender.render(context, l, px, frameW, if (atMs < 0) 0 else LottieRender.frameIndex(context, l, atMs))
            LayerKind.FX -> FxLayers.render(l, frameW, frameH, atMs, context)
            LayerKind.LOGO, LayerKind.IMAGE -> {
                val uri = l.imageUri ?: return null
                val src = synchronized(logoCache) {
                    logoCache.getOrPut(uri.toString()) { GradeIO.decode(context, uri, 1024) ?: return null }
                }
                val h = px.toInt().coerceAtLeast(4)
                val w = (src.width * (h.toFloat() / src.height)).toInt().coerceAtLeast(4)
                val scaled = Bitmap.createScaledBitmap(src, min(w, frameW), h, true)
                val key = l.chroma
                (if (key != null) chromaKeyBitmap(scaled, key, l.chromaTol ?: 0.4f, l.chromaSoft ?: 0.3f) else scaled)
                    .let { if ((l.lumaKey ?: 0) != 0) lumaKeyBitmap(it, l.lumaKey ?: 0, l.lumaLevelOrDefault(), l.lumaSoft ?: 0.15f) else it }
            }
            else -> renderText(l, px, frameW, chars, hi)
        }
    }

    /** [chars]: how many characters to draw (typewriter); the size and layout always follow the full text. */
    /** The text layer's picture (for previews of styles in the editors). */
    fun textPreview(l: Layer, px: Float, frameW: Int): Bitmap = renderText(l, px, frameW)

    private fun renderText(l: Layer, px: Float, frameW: Int, chars: Int = Int.MAX_VALUE, hi: Int = -1): Bitmap {
        if (l.kind == LayerKind.TEXT && l.lowerThird != null) return LowerThirds.render(l, px, frameW)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = px
            color = l.color
            typeface = if (l.kind == LayerKind.STICKER) Typeface.DEFAULT else (TextFonts.typeface(l.face) ?: l.font.typeface)
            if (l.shadow && l.kind != LayerKind.STICKER) setShadowLayer(px * 0.08f, 0f, px * 0.04f, 0xAA000000.toInt())
        }
        val lines = l.text.ifEmpty { " " }.split('\n')
        val fm = paint.fontMetrics
        val lineH = fm.descent - fm.ascent
        // Outline (text layers): a stroke round the letters, [Layer.stroke] = width as a share of the text height.
        val ow = if (l.kind == LayerKind.TEXT) (l.stroke ?: 0f) * px else 0f
        val tfx = if (l.kind == LayerKind.TEXT) (l.textFx ?: 0) else 0
        val pad = (if (l.box) px * 0.35f else px * 0.15f) + ow + TextFx.pad(tfx) * px
        val textW = lines.maxOf { paint.measureText(it) }
        val w = (textW + pad * 2).toInt().coerceIn(4, frameW * 2)
        val h = (lineH * lines.size + pad * 2).toInt().coerceAtLeast(4)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (l.box) {
            c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), px * 0.3f, px * 0.3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99000000.toInt() })
        }
        var left = chars
        lines.forEachIndexed { i, line ->
            val lw = paint.measureText(line)
            val part = if (left >= line.length) line else line.substring(0, left.coerceAtLeast(0))
            left -= line.length
            if (part.isNotEmpty()) {
                if (ow > 0f) c.drawText(part, (w - lw) / 2f, pad - fm.ascent + i * lineH, Paint(paint).apply {
                    style = Paint.Style.STROKE; strokeWidth = ow * 2f; strokeJoin = Paint.Join.ROUND; color = l.borderColor ?: 0xFF000000.toInt()
                })
                if (tfx > 0) TextFx.draw(c, part, (w - lw) / 2f, pad - fm.ascent + i * lineH, Paint(paint).apply { clearShadowLayer() }, tfx, l.color,
                    l.textColor2 ?: 0xFFFFB300.toInt(), px, pad, h - pad)
                else c.drawText(part, (w - lw) / 2f, pad - fm.ascent + i * lineH, if (ow > 0f) Paint(paint).apply { clearShadowLayer() } else paint)
            }
        }
        // Word-by-word captions: the word being spoken in the highlight colour (or on a box).
        if (hi >= 0 && l.words != null) {
            var k = 0
            lines.forEachIndexed { i, line ->
                val lw = paint.measureText(line)
                var pos = 0
                for (word in line.split(' ')) {
                    if (word.isNotEmpty()) {
                        if (k == hi) {
                            val x = (w - lw) / 2f + paint.measureText(line, 0, pos)
                            val ww = paint.measureText(word)
                            val base = pad - fm.ascent + i * lineH
                            val col = l.hiColor ?: 0xFFFFD60A.toInt()
                            if (l.hiBox == true) {
                                val pp = px * 0.12f
                                c.drawRoundRect(RectF(x - pp, base + fm.ascent * 0.92f - pp * 0.4f, x + ww + pp, base + fm.descent * 0.9f + pp * 0.4f), px * 0.18f, px * 0.18f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col })
                                c.drawText(word, x, base, Paint(paint).apply { color = 0xFF000000.toInt(); clearShadowLayer() })
                            } else c.drawText(word, x, base, Paint(paint).apply { color = col })
                        }
                        k++
                    }
                    pos += word.length + 1
                }
            }
        }
        return bmp
    }
}

/** Draws a watermark/logo layer onto a photo (returns a new bitmap). */
fun stampLayer(context: Context, src: Bitmap, l: Layer): Bitmap {
    if (!l.visible) return src
    // Photo layers (pictures with a blend mode) are drawn at full size with their blend.
    if (PhotoLayers.isPhotoLayer(l)) return PhotoLayers.composite(context, src, l)
    val mark = LayerRenderer.render(context, l, src.width, src.height) ?: return src
    val out = if (src.isMutable) src else src.copy(Bitmap.Config.ARGB_8888, true)
    val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { alpha = (l.opacity * 255).toInt().coerceIn(0, 255) }
    Canvas(out).drawBitmap(mark, l.x * src.width - mark.width / 2f, l.y * src.height - mark.height / 2f, p)
    return out
}

/** Media3 overlay for one layer: shown only inside its time window. */
@UnstableApi
class LayerOverlay(
    private val bitmap: Bitmap,
    private val l: Layer,
    /** Draws the layer with only the first n characters (typewriter animation). */
    private val partial: ((Int) -> Bitmap?)? = null,
    /** Draws the layer with word n highlighted (word-by-word captions). */
    private val highlighted: ((Int) -> Bitmap?)? = null,
    /** Animated layers (Lottie): the frame index at a time, and that frame's picture. */
    private val frameIndex: ((Long) -> Int)? = null,
    private val frameAt: ((Int) -> Bitmap?)? = null,
) : BitmapOverlay() {
    private var lastFrame = -1
    private var lastBmp: Bitmap? = null
    private val blank = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
    private val startUs = l.startMs * 1000
    private val endUs = l.endMs * 1000
    private val whole = l.wholeVideo
    private val animated = LayerAnims.hasAny(l)
    private val frames = HashMap<Int, Bitmap>()
    private val settings: OverlaySettings = StaticOverlaySettings.Builder()
        .setBackgroundFrameAnchor((l.x * 2f - 1f).coerceIn(-1f, 1f), (1f - l.y * 2f).coerceIn(-1f, 1f))
        .setOverlayFrameAnchor(0f, 0f)
        .setAlphaScale(l.opacity.coerceIn(0f, 1f))
        .build()

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        if (!(whole || presentationTimeUs in startUs..endUs)) return blank
        if (frameIndex != null && frameAt != null) {
            // A new bitmap per frame (the overlay re-uploads only when the bitmap object changes).
            val n = frameIndex.invoke(presentationTimeUs / 1000)
            if (n != lastFrame || lastBmp == null) { lastBmp = frameAt.invoke(n) ?: lastBmp; lastFrame = n }
            return lastBmp ?: bitmap
        }
        if (l.words != null && highlighted != null) {
            val n = l.wordAt(presentationTimeUs / 1000)
            if (n >= 0) return frames.getOrPut(100_000 + n) { highlighted.invoke(n) ?: bitmap }
        }
        if (!animated) return bitmap
        val fx = LayerAnims.at(l, presentationTimeUs / 1000)
        if (fx.chars < 1f && partial != null) {
            val n = LayerAnims.charCount(l, fx.chars)
            return frames.getOrPut(-1 - n) { partial.invoke(n) ?: bitmap }
        }
        if (fx.reveal < 1f) {
            val step = (fx.reveal * 40).toInt()
            return frames.getOrPut(step) { LayerAnims.revealed(bitmap, step / 40f) }
        }
        return bitmap
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        if (l.keys.isNullOrEmpty() && !animated) return settings
        // Keyframes and in / out animations: move, scale and fade over time.
        val ms = presentationTimeUs / 1000
        val a = l.animAt(ms)
        val fx = LayerAnims.at(l, ms)
        val k = (if (l.size > 0f) a.size / l.size else 1f) * fx.scale
        val x = a.x + fx.dx; val y = a.y + fx.dy
        return StaticOverlaySettings.Builder()
            // Up to half a frame past the edge: the part beyond −1…1 goes to the layer's own anchor (each must stay in −1…1).
            .setBackgroundFrameAnchor((x * 2f - 1f).coerceIn(-1f, 1f), (1f - y * 2f).coerceIn(-1f, 1f))
            .setOverlayFrameAnchor((x * 2f - 1f).coerceIn(-1f, 1f) - (x * 2f - 1f).coerceIn(-1.5f, 1.5f), (1f - y * 2f).coerceIn(-1f, 1f) - (1f - y * 2f).coerceIn(-1.5f, 1.5f))
            .setScale(k, k)
            .setAlphaScale((a.opacity * fx.alpha).coerceIn(0f, 1f))
            .build()
    }
}

/** Background music for the video editor. */
data class MusicTrack(
    val uri: Uri,
    val name: String,
    val durationMs: Long,
    /** Where in the song to start. */
    val offsetMs: Long = 0L,
    val volume: Float = 0.8f,
    val fadeInMs: Long = 1000L,
    val fadeOutMs: Long = 2000L,
    /** EQ / dynamics for the music. */
    val fx: AudioFx = AudioFx(),
    /** How much the music drops under clips marked "lower music" (0..1). */
    val duck: Float = 0.6f,
    val id: String = java.util.UUID.randomUUID().toString(),
    /** Where the track starts on the timeline. */
    val startMs: Long = 0L,
    /** How long it plays (0 = until the song or the timeline ends). */
    val lengthMs: Long = 0L,
    /** Track switches on the timeline: silenced, and protected from edits. */
    val muted: Boolean = false,
    val locked: Boolean = false,
    /** Linked to the main video (null = linked) and group, as for layers. */
    val linked: Boolean? = null,
    val group: String? = null,
) {
    /** Length on a timeline of [total] ms. */
    fun playLength(total: Long): Long {
        val avail = (durationMs - offsetMs).coerceAtLeast(0)
        val want = if (lengthMs > 0) lengthMs.coerceAtMost(avail) else avail
        return want.coerceAtMost((total - startMs).coerceAtLeast(0))
    }
}

/** An audio file in the media pool (not necessarily on the timeline). */
data class PoolAudio(val uri: android.net.Uri, val name: String, val durationMs: Long)

/** Gain with fade-in / fade-out for a stream of known length (16-bit PCM). */
@UnstableApi
class FadeAudioProcessor(private val volume: Float, private val fadeInMs: Long, private val fadeOutMs: Long, private val totalMs: Long) : BaseAudioProcessor() {
    private var frames = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val fmt = inputAudioFormat
        val size = inputBuffer.remaining()
        val out = replaceOutputBuffer(size)
        val ch = fmt.channelCount.coerceAtLeast(1)
        val rate = fmt.sampleRate.coerceAtLeast(1)
        while (inputBuffer.remaining() >= 2 * ch) {
            val tMs = frames * 1000L / rate
            var g = volume
            if (fadeInMs > 0 && tMs < fadeInMs) g *= tMs.toFloat() / fadeInMs
            if (fadeOutMs > 0 && totalMs > 0 && tMs > totalMs - fadeOutMs) g *= ((totalMs - tMs).toFloat() / fadeOutMs).coerceIn(0f, 1f)
            for (c in 0 until ch) {
                val v = (inputBuffer.short * g).toInt().coerceIn(-32768, 32767)
                out.putShort(v.toShort())
            }
            frames++
        }
        while (inputBuffer.hasRemaining()) out.put(inputBuffer.get())
        out.flip()
    }

    override fun onFlush() { frames = 0 }
    override fun onReset() { frames = 0 }
}
