package com.dronedirector.veyrocutpro.editor

import android.graphics.Typeface
import android.net.Uri
import java.util.UUID

enum class LayerKind(val label: String) { TEXT("Text"), STICKER("Sticker"), WATERMARK("Watermark"), LOGO("Logo"), IMAGE("Image"), VIDEO("Video"), ADJUST("Adjustment"), SHAPE("Shape"), LOTTIE("Animation") }

enum class LayerFont(val label: String, val typeface: Typeface) {
    SANS("Sans", Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)),
    SERIF("Serif", Typeface.create(Typeface.SERIF, Typeface.BOLD)),
    MONO("Mono", Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)),
    LIGHT("Light", Typeface.create("sans-serif-light", Typeface.NORMAL)),
    CONDENSED("Condensed", Typeface.create("sans-serif-condensed", Typeface.BOLD)),
}

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
        ?: when (kind) { LayerKind.TEXT, LayerKind.WATERMARK, LayerKind.STICKER -> text.replace('\n', ' '); LayerKind.LOGO, LayerKind.IMAGE -> "Image"; else -> kind.label }
    val isMedia get() = kind == LayerKind.VIDEO || kind == LayerKind.IMAGE || kind == LayerKind.LOGO || kind == LayerKind.SHAPE || kind == LayerKind.LOTTIE
    fun activeAt(ms: Long) = visible && (wholeVideo || ms in startMs..endMs)
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
