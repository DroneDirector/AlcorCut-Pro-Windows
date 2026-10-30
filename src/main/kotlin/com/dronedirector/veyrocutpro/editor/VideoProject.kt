package com.dronedirector.veyrocutpro.editor

import android.net.Uri
import java.util.UUID

/** One clip on the timeline. Times in milliseconds of the source file. */
data class Clip(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val name: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val startMs: Long = 0L,
    val endMs: Long = durationMs,
    val speed: Float = 1f,
    val volume: Float = 1f,
    val muted: Boolean = false,
    val fadeIn: Boolean = false,
    val audio: AudioFx = AudioFx(),
    /** Transition into the next clip. */
    val transition: Transition = Transition(),
    /** Colour masks: parts of the picture with their own grade (Color → Masks). */
    val masks: List<PhotoMask>? = null,
    val reframe: Reframe = Reframe(),
    val stab: Stabilize = Stabilize(),
    val grade: ProGrade = ProGrade(),
    val detail: Detail = Detail(),
    /** Stream facts used to decide on a preview proxy. */
    val mime: String = "",
    val hdr: Boolean = false,
    val tenBit: Boolean = false,
    /** Anamorphic squeeze to undo (null / 1 = none), e.g. 1.33 or 1.8. */
    val desqueeze: Float? = null,
    /** Speed ramp: multipliers through the clip (null = constant speed) and its name. */
    val ramp: List<Float>? = null,
    val rampName: String? = null,
    /** Volume keyframes: (source ms from the clip start, gain 0..2). */
    val volKeys: List<VolKey>? = null,
    /** Nested (compound) clip: the clips it was made from, so it can be opened up again. */
    val nested: List<Clip>? = null,
    /** Colour sampling: 2 = 4:2:2, 3 = 4:4:4 (null / 1 = normal 4:2:0), and bit depth. */
    val chroma: Int? = null,
    val bitDepth: Int? = null,
    /** Decoder that reads this clip when the phone's usual one can't (4:2:2), or null. */
    val decoder: String? = null,
    /** True when no decoder on this phone can read the clip. */
    val undecodable: Boolean? = null,
    /** Film emulation, lens correction and deflicker (Color → FILM & LENS); null = none. */
    val film: FilmLook? = null,
    /** Background removal behind people; null = off. */
    val bg: BgRemoval? = null,
    /** Colour label (ARGB) shown on the timeline; null = none. */
    val label: Int? = null,
    /** AI depth effect (lens blur, fog, background light); null = off. */
    val depth: DepthFx? = null,
    /** Picture hidden (the main video track's eye): see-through in the preview and the export. */
    val hidden: Boolean? = null,
    /** Offline: a black stand-in whose real media isn't linked (yet); [offlineOf] = the file it stood for, if any. */
    val offline: Boolean? = null,
    val offlineOf: String? = null,
    /** Privacy blur: faces and tapped spots blurred or pixelated, followed through the clip. */
    val privacy: PrivacyBlur? = null,
    /** Grade versions A / B / C (Color page): saved looks to switch between and compare. */
    val versions: List<ClipLook?>? = null,
    /** A still photo on the timeline: the original photo (the clip plays it as a still). */
    val still: String? = null,
) {
    val is422: Boolean get() = (chroma ?: 1) >= 2

    val lengthMs: Long get() = (if (ramp != null) rampedLength() else ((endMs - startMs) / speed).toLong()).coerceAtLeast(1)
}

/** One saved grade version of a clip: colour, detail and film look. */
data class ClipLook(val grade: ProGrade = ProGrade(), val detail: Detail = Detail(), val film: FilmLook? = null)

data class DeliverSettings(
    /** Short side in pixels, 0 = same as the first clip. */
    val shortSide: Int = 0,
    val fps: Int = 0,
    val hevc: Boolean = false,
    /** CineForm .mov (pro editing codec) instead of H.264 / HEVC: quality 0–3, −1 = off. */
    val cineform: Int = -1,
    /** Mbit/s, 0 = automatic. */
    val bitrateMbps: Int = 0,
    val audio: Boolean = true,
    /** Project frame shape (width / height), 0 = same as the first clip. */
    val aspect: Float = 0f,
    /** true = fill the frame (crop), false = fit inside (bars). */
    val fill: Boolean = true,
    /** Save only the sound (AAC in an .m4a file, to Music/AlcorCutPro). */
    val audioOnly: Boolean = false,
    /** Your details (from a metadata preset) written into the exported video; null = none. */
    val meta: PhotoMetaEdit? = null,
    /** Export only this part of the timeline (ms); null = the whole edit. */
    val rangeStart: Long? = null,
    val rangeEnd: Long? = null,
    /** Bring the finished sound to this loudness (LUFS), e.g. −14; null = leave as mixed. */
    val loudness: Float? = null,
    /** Keep HDR (HLG / HDR10) clips as HDR in the export (HEVC 10-bit) instead of converting to SDR. */
    val keepHdr: Boolean = false,
    /** Audio-only file format (null = AAC .m4a). */
    val audioFormat: AudioExportFormat? = null,
    /** Mixer: the video track's level and pan, and the master level (1 = unchanged). */
    val videoGain: Float = 1f,
    val videoPan: Float = 0f,
    val masterGain: Float = 1f,
    /** Track groups (VCA): track id → 0 dialogue, 1 music, 2 effects (null = by kind), and each group's fader. */
    val groups: Map<String, Int>? = null,
    val groupLevels: List<Float>? = null,
    /** Smart render: copy the clips without re-encoding when the edit is cut-only (see [SmartRender]). */
    val smartRender: Boolean = false,
    /** Review copy: burned-in timecode, a watermark text (e.g. DRAFT) and marker notes on screen. */
    val review: Boolean = false,
    val reviewText: String = "DRAFT",
    val reviewNotes: Boolean = true,
    /** Timecode shown in a review copy starts here (the export range's start). */
    val reviewTcStart: Long = 0L,
    /**
     * Framing set by pinching the viewer: zoom (1 = whole frame) and the shift of the picture as a
     * share of the frame's width / height. The export shows exactly what the viewer shows.
     */
    val frameZoom: Float = 1f,
    val frameX: Float = 0f,
    val frameY: Float = 0f,
) {
    val framed: Boolean get() = frameZoom > 1.001f || kotlin.math.abs(frameX) > 0.0005f || kotlin.math.abs(frameY) > 0.0005f
    val audioFmt: AudioExportFormat get() = audioFormat?.takeIf { it.available } ?: AudioExportFormat.M4A
}

/** One-tap delivery settings for common destinations. */
data class ExportPreset(val label: String, val shortSide: Int, val fps: Int, val hevc: Boolean, val mbps: Int, val aspect: Float)

/** The part of an edit between [a] and [b] (timeline ms) as its own clips, layers and audio. */
data class RangeCut(val clips: List<Clip>, val layers: List<Layer>, val music: MusicTrack?, val extra: List<MusicTrack>)
