package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.SingleColorLut
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.dronedirector.veyrocutpro.camera.GradeIO
import java.io.File
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

object ClipLoader {
    fun load(context: Context, uri: Uri): Clip? = try {
        val mmr = MediaMetadataRetriever()
        mmr.setDataSource(context, uri)
        val dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        var w = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        var h = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val rot = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
        mmr.release()
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment ?: "Clip"
        val info = StreamProbe.probe(context, uri)
        // 4:2:2 / 4:4:4: find a decoder on this phone that can read it.
        val ci = info.chroma
        var dec: String? = null
        var bad = false
        if (ci != null && ci.chroma >= 2) {
            dec = Chroma.workingDecoder(context, uri)
            bad = dec == null
        }
        if (dur <= 0) null else Clip(
            uri = uri, name = name, durationMs = dur, width = w, height = h, mime = info.mime, hdr = info.hdr,
            tenBit = info.tenBit || (ci?.bitDepth ?: 8) > 8,
            chroma = ci?.chroma?.takeIf { it >= 2 }, bitDepth = ci?.bitDepth,
            decoder = dec, undecodable = if (bad) true else null,
        )
    } catch (_: Throwable) {
        null
    }
}

/** Live preview effects for a clip (same pipeline as the export). */
@UnstableApi
fun clipEffects(c: Clip): List<Effect> = buildList {
    // A fresh LUT effect every time: an effect instance holds a GPU texture that belongs to one
    // player's GL context and must never be shared between the two preview players or the export.
    if (!c.grade.isIdentity) add(SingleColorLut.createFromCube(LutCache.cube(c.grade)))
    if (c.detail.needsVideoPass) add(DetailEffect(c.detail))
    c.activeMasks().takeIf { it.isNotEmpty() }?.let { add(MaskGradeEffect(it)) }
    addAll(filmEffects(c))
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

val EXPORT_PRESETS = listOf(
    ExportPreset("YouTube 4K", 2160, 0, false, 50, 16f / 9f),
    ExportPreset("YouTube 1080p", 1080, 0, false, 20, 16f / 9f),
    ExportPreset("Instagram Reel", 1080, 30, false, 20, 9f / 16f),
    ExportPreset("Instagram 4:5", 1080, 30, false, 20, 0.8f),
    ExportPreset("TikTok", 1080, 30, false, 20, 9f / 16f),
    ExportPreset("Square 1:1", 1080, 30, false, 20, 1f),
    ExportPreset("Small file", 720, 30, true, 0, 0f),
)

/** The part of an edit between [a] and [b] (timeline ms) as its own clips, layers and audio. */
data class RangeCut(val clips: List<Clip>, val layers: List<Layer>, val music: MusicTrack?, val extra: List<MusicTrack>)

fun cutRange(clips: List<Clip>, layers: List<Layer>, music: MusicTrack?, extra: List<MusicTrack>, a: Long, b: Long): RangeCut {
    val st = clipStarts(clips)
    val total = timelineLength(clips)
    val out = mutableListOf<Clip>()
    clips.forEachIndexed { i, c ->
        val s0 = st[i]; val e0 = s0 + c.lengthMs
        val from = maxOf(a, s0); val to = minOf(b, e0)
        if (to - from < 40) return@forEachIndexed
        val ns = c.srcAtOffset(from - s0)
        val ne = c.srcAtOffset(to - s0).coerceAtMost(c.endMs)
        // A ramp is kept for whole clips only (a part of a ramp would replay the whole curve).
        out += c.copy(id = java.util.UUID.randomUUID().toString(), startMs = ns, endMs = ne, ramp = if (ns == c.startMs && ne == c.endMs) c.ramp else null)
    }
    if (out.isNotEmpty()) out[out.lastIndex] = out.last().copy(transition = Transition())
    val len = (b - a).coerceAtLeast(1)
    val ls = layers.mapNotNull { l ->
        if (l.wholeVideo) l else {
            val s = l.startMs - a; val e = l.endMs - a
            if (e <= 0 || s >= len) null else l.copy(startMs = s.coerceAtLeast(0), endMs = e.coerceAtMost(len), videoStartMs = l.videoStartMs + (if (s < 0) -s else 0))
        }
    }
    fun shift(m: MusicTrack): MusicTrack? {
        val play = m.playLength(total)
        val s = m.startMs - a
        val e = s + play
        if (e <= 0 || s >= len) return null
        val cut = if (s < 0) -s else 0
        return m.copy(startMs = s.coerceAtLeast(0), offsetMs = m.offsetMs + cut, lengthMs = (play - cut).coerceAtLeast(1))
    }
    return RangeCut(out, ls, music?.let { shift(it) }, extra.mapNotNull { shift(it) })
}

/** Output frame size for the project (even numbers, as encoders need). */
fun outputSize(clips: List<Clip>, s: DeliverSettings): Pair<Int, Int> {
    val first = clips.firstOrNull()
    val fw = ((first?.width ?: 1920) * (first?.desqueeze ?: 1f)).toInt().coerceAtLeast(2)
    val fh = (first?.height ?: 1080).coerceAtLeast(2)
    val aspect = if (s.aspect > 0f) s.aspect else fw.toFloat() / fh
    val (w, h) = if (s.shortSide < 0) {
        // A long side (5.7K = 5760 wide), the frame keeping its shape.
        val long = -s.shortSide
        if (aspect >= 1f) long to (long / aspect).toInt() else (long * aspect).toInt() to long
    } else {
        val short = if (s.shortSide > 0) s.shortSide else minOf(fw, fh)
        if (aspect >= 1f) (short * aspect).toInt() to short else short to (short / aspect).toInt()
    }
    return (w / 2 * 2).coerceAtLeast(2) to (h / 2 * 2).coerceAtLeast(2)
}

/** Renders the timeline to a new video in DCIM/AlcorCutPro with Media3 Transformer. Start on the main thread. */
@UnstableApi
class TimelineExport(
    private val context: Context,
    private val clips: List<Clip>,
    private val settings: DeliverSettings,
    private val layers: List<Layer> = emptyList(),
    private val music: MusicTrack? = null,
    private val extraAudio: List<MusicTrack> = emptyList(),
    private val onProgress: (Int) -> Unit,
    private val onDone: (Uri?, String?) -> Unit,
    /** Keep the result in this file instead of saving it to the gallery (e.g. sound for captions). */
    private val toFile: File? = null,
) {
    private val audioOnly = settings.audioOnly
    private val out = File(context.cacheDir, "timeline_${System.currentTimeMillis()}." + if (settings.audioOnly) "m4a" else "mp4")
    private val handler = Handler(Looper.getMainLooper())
    private var finished = false
    private lateinit var transformer: Transformer
    /** Lossless / other audio formats: the finished mix is captured here and written afterwards. */
    private val fmt = if (settings.audioOnly && toFile == null) settings.audioFmt else AudioExportFormat.M4A
    private val rawFile = File(context.cacheDir, "mix_${System.currentTimeMillis()}.pcm")
    private val tap = if (fmt != AudioExportFormat.M4A) PcmTap(rawFile) else null

    /** Analysed morph cuts (cut index → data), freed when the export ends. */
    private var morphs: Map<Int, MorphData> = emptyMap()
    private var morphPrep: MorphPrep? = null
    private fun freeMorphs() { val m = morphs; morphs = emptyMap(); handler.postDelayed({ m.values.forEach { it.release() } }, 1500) }

    /** A clip's own picture effects (everything before its transition), for a clip starting [offsetMs] into it. */
    private fun clipChain(c: Clip, offsetMs: Long, outW: Int, outH: Int, layout: Int, sized: Boolean): List<Effect> = buildList {
        if (c.detail.aiDenoise > 0f && CleanRender.sourceFor(context, c, settings) == null) add(AiDenoiseEffect(context, c.detail.aiDenoise))
        // Background (people or Magic Mask) on the picture as filmed, before any reframing.
        bgEffectFor(context, c, offsetMs)?.let { add(it) }
        // Privacy blur, on the picture as filmed (its boxes are in the clip's own frame).
        if (c.privacy?.active == true) add(PrivacyBlurEffect(c, offsetMs, preview = false))
        // AI depth: lens blur, fog or background light, on the picture as filmed.
        depthEffectFor(context, c, offsetMs)?.let { add(it) }
        val motion = if (c.stab.enabled) Stabilizer.cached(context, c) else null
        if (!c.reframe.isIdentity || motion != null) add(ReframeEffect(c, motion, offsetMs))
        if (c.reframe.hasOpacity) add(ClipOpacityEffect(c, offsetMs))
        // Anamorphic: stretch the squeezed picture back to its real width.
        c.desqueeze?.takeIf { it > 1f }?.let { add(androidx.media3.effect.ScaleAndRotateTransformation.Builder().setScale(it, 1f).build()) }
        addAll(clipEffects(c))
        // Shape (pen) masks: their own grade inside the curve (tracked masks move frame by frame).
        if (PenMask.active(c).isNotEmpty()) add(PenMaskEffect(c, offsetMs))
        // When compositing, every clip is sized to the project frame first.
        if (sized) add(Presentation.createForWidthAndHeight(outW, outH, layout))
        // Hidden video track: see-through (layers below show; otherwise black).
        if (c.hidden == true) add(androidx.media3.effect.AlphaScale(0f))
    }

    fun start() {
        if (settings.smartRender && toFile == null && smartRenderBlocker(context, clips, settings, layers, music, extraAudio) == null) {
            val withAudio = settings.audio && clips.none { it.muted }
            Thread {
                val probe = SmartRender.probeMedia(context, clips, withAudio)
                if (!probe.ok) { handler.post { if (!finished) startNormal() }; return@Thread }
                val err = SmartRender.copy(context, clips, withAudio, out, { p -> handler.post { onProgress(p) } }, { finished })
                if (finished) { out.delete(); return@Thread }
                if (err != null) { out.delete(); handler.post { if (!finished) startNormal() }; return@Thread }
                finished = true
                saveVideo(null)
            }.start()
            smartRunning = true
            return
        }
        startNormal()
    }

    private var smartRunning = false

    /** Saves the finished video in [out] to the gallery / chosen folder and reports back. */
    private fun saveVideo(err0: String?) {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
        val name = "EDIT_$stamp.mp4"
        outputSize(clips, settings).let { (ow, oh) -> Spherical.tagIfNeeded(context, clips, ow, oh, out) }
        // Your details (Deliver → METADATA) written into the file.
        settings.meta?.let { if (!Mp4Meta.write(out, it)) com.dronedirector.veyrocutpro.ui.AppLog.w("Export: couldn't write the metadata into the video") }
        val uri = SaveFolder.write(context, true, name, "video/mp4") { os -> out.inputStream().use { it.copyTo(os) } }
            ?: GradeIO.publishVideo(context, out, com.dronedirector.veyrocutpro.camera.StorageArea.VIDEO)
        out.delete()
        if (uri != null && ContentCredentials.on(context, true)) ContentCredentials.sign(context, uri, true)
        ExportUpload.after(context, uri, true)
        handler.post { onDone(uri, if (uri == null) (err0 ?: "Couldn't save the video") else null) }
    }

    private fun startNormal() {
        smartRunning = false
        // Morph cuts: analyse them first (their frames go through each clip's own effects), then export.
        if (!audioOnly && !settings.keepHdr && morphCuts(clips).isNotEmpty() && MorphNative.available) {
            val (outW, outH) = outputSize(clips, settings)
            val layout = if (settings.fill) Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP else Presentation.LAYOUT_SCALE_TO_FIT
            val decoders = clips.mapNotNull { it.decoder }.distinct()
            morphPrep = MorphPrep(
                context, clips,
                effectsFor = { c, off -> clipChain(c, off, outW, outH, layout, true) },
                decoderFactory = if (decoders.isEmpty()) null else { b -> b.setAssetLoaderFactory(Chroma.assetLoaders(context, decoders)) },
                onStatus = { onProgress(0) },
                onDone = { m -> morphPrep = null; if (finished) { m.values.forEach { it.release() }; return@MorphPrep }; morphs = m; startMain() },
            ).also { it.start() }
            return
        }
        startMain()
    }

    private fun startMain() {
        val (outW, outH) = outputSize(clips, settings)
        val allVideoLayers = if (audioOnly) emptyList() else layers.filter { it.visible && it.kind == LayerKind.VIDEO && it.videoUri != null }
        // Video layers above the main video (drawn first = on top) and below it (after the timeline).
        val videoLayers = allVideoLayers.filter { !it.isBelow }
        val belowLayers = allVideoLayers.filter { it.isBelow }
        val layout = if (settings.fill) Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP else Presentation.LAYOUT_SCALE_TO_FIT
        val totalMs = timelineLength(clips)
        val starts = clipStarts(clips)
        val overlaps = LongArray(clips.size) { overlapMs(clips, it) }
        val ab = overlaps.any { it > 0 }
        val compositing = !audioOnly && (allVideoLayers.isNotEmpty() || ab)
        val items = clips.mapIndexed { i, c ->
            val media = MediaItem.Builder()
                // A clip already cleaned by the AI (done once, kept in the video catalog) is read as it is.
                .setUri(CleanRender.sourceFor(context, c, settings) ?: c.uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder().setStartPositionMs(c.startMs).setEndPositionMs(c.endMs).build()
                )
                .build()
            val audio = mutableListOf<AudioProcessor>()
            val gain = if (c.muted) 0f else c.volume * settings.videoGain * settings.masterGain
            // Audio transitions (Transitions → Audio) shape the sound at each cut.
            val inMs = if (i > 0) overlaps[i - 1] else 0L
            val outMs = overlaps[i]
            val xIn = if (i > 0) AudioXfade.forCut(clips, i - 1).second?.scaled(c.speed) else null
            val xOut = AudioXfade.forCut(clips, i).first?.scaled(c.speed)
            val afx = if (settings.videoPan != 0f) c.audio.copy(pan = (c.audio.pan + settings.videoPan).coerceIn(-1f, 1f)) else c.audio
            if (gain != 1f || !afx.isNeutral || !c.volKeys.isNullOrEmpty() || xIn != null || xOut != null) {
                audio += AudioFxProcessor(afx, gain, c.endMs - c.startMs, envelope = c.volKeys, xOut = xOut, xIn = xIn)
            }
            val video = mutableListOf<Effect>()
            if (!audioOnly) {
            video += clipChain(c, 0L, outW, outH, layout, compositing)
            if (ab) {
                // A/B roll: even clips on the top track, odd clips underneath.
                val top = i % 2 == 0
                val prevT = if (i > 0) clips[i - 1].transition.type else TransitionType.NONE
                val inMode = if (top && prevT.kind == TransitionKind.MASK) 1 else 0
                val outMode = if (top && c.transition.type.kind == TransitionKind.MASK) 2 else 0
                video += TransitionEffect(TransitionWindows(c.lengthMs, prevT, inMs, inMode, c.transition.type, outMs, outMode))
                // Morph cuts: the in-between frames drawn new from the analysed motion.
                val mIn = if (i > 0) morphs[i - 1] else null
                val mOut = morphs[i]
                if (mIn != null || mOut != null) video += MorphEffect(c.lengthMs, mIn, inMs, mOut, outMs)
            }
            }
            val b = EditedMediaItem.Builder(media)
                .setEffects(Effects(audio, video))
                .setRemoveAudio(!settings.audio && !audioOnly)
                .setRemoveVideo(audioOnly)
            if (c.ramp != null) {
                // Speed ramp: the speed changes every 40 ms of source time along the curve.
                b.setSpeed(object : SpeedProvider {
                    override fun getSpeed(timeUs: Long): Float = c.speedAtSrc(c.startMs + timeUs / 1000)
                    override fun getNextSpeedChangeTimeUs(timeUs: Long): Long {
                        val next = (timeUs / 40_000 + 1) * 40_000
                        return if (next >= (c.endMs - c.startMs) * 1000) androidx.media3.common.C.TIME_UNSET else next
                    }
                })
            } else if (c.speed != 1f) {
                val s = c.speed
                b.setSpeed(object : SpeedProvider {
                    override fun getSpeed(timeUs: Long): Float = s
                    override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = androidx.media3.common.C.TIME_UNSET
                })
            }
            b.build()
        }

        val global = mutableListOf<Effect>()
        // Project frame: size + shape, filling (crop) or fitting (bars).
        if (!audioOnly) global += Presentation.createForWidthAndHeight(outW, outH, layout)
        // Framing from the viewer (pinch): the finished picture zoomed and moved the same way.
        if (!audioOnly && settings.framed) {
            val z = settings.frameZoom; val fx = settings.frameX; val fy = settings.frameY
            global += androidx.media3.effect.MatrixTransformation { _ ->
                android.graphics.Matrix().apply { postScale(z, z); postTranslate(2f * fx, -2f * fy) }
            }
        }
        if (settings.fps > 0 && !audioOnly) global += FrameDropEffect.createDefaultFrameDropEffect(settings.fps.toFloat())
        // Adjustment layers: a grade over everything under them, inside their time window.
        if (!audioOnly) {
            val adj = layers.filter { it.visible && it.kind == LayerKind.ADJUST && it.adjGrade != null && !it.adjGrade.isIdentity }.take(MAX_VIDEO_MASKS)
            if (adj.isNotEmpty()) global += MaskGradeEffect(adj.map { asMask(it) }, adj.map { if (it.wholeVideo) null else it.startMs..it.endMs })
        }
        // Text, stickers, watermarks and logos, bottom layer first.
        val pictureLayers = if (audioOnly) emptyList() else layers.filter { it.visible && it.kind != LayerKind.VIDEO }
        val overlays = (pictureLayers.filter { (it.blend ?: LayerBlend.NORMAL) == LayerBlend.NORMAL && it.pin.isNullOrEmpty() }.mapNotNull { l ->
            LayerRenderer.render(context, l, outW, outH)?.let {
                LayerOverlay(
                    it, l, partial = { n -> LayerRenderer.render(context, l, outW, outH, n) }, highlighted = { n -> LayerRenderer.render(context, l, outW, outH, hi = n) },
                    frameIndex = if (l.kind == LayerKind.LOTTIE) { ms -> LottieRender.frameIndex(context, l, ms) } else if (l.kind == LayerKind.FX) { ms -> FxLayers.frameIndex(l, ms) } else null,
                    frameAt = if (l.kind == LayerKind.LOTTIE) { n -> LottieRender.render(context, l, maxOf(8f, l.size * outH), outW, n) } else if (l.kind == LayerKind.FX) { n -> FxLayers.renderFrame(l, outW, outH, n, context) } else null,
                ) as androidx.media3.effect.TextureOverlay
            }
        } + listOfNotNull(if (settings.review && !audioOnly) TimecodeOverlay(outW, outH, settings.reviewTcStart, if (settings.fps > 0) settings.fps else 30) as androidx.media3.effect.TextureOverlay else null))
        if (overlays.isNotEmpty()) global += androidx.media3.effect.OverlayEffect(overlays)
        // Layers with a blend mode are drawn by their own shader, in layer order.
        for (l in pictureLayers) {
            // Corner-pinned layers: drawn onto their four corners (perspective), in layer order.
            if (!l.pin.isNullOrEmpty()) { LayerRenderer.render(context, l, outW, outH)?.let { global += CornerPinEffect(it, l) }; continue }
            if ((l.blend ?: LayerBlend.NORMAL) == LayerBlend.NORMAL) continue
            LayerRenderer.render(context, l, outW, outH)?.let { global += BlendLayerEffect(it, l, outW, outH) }
        }

        // Video layers become extra sequences composited over the main timeline. The compositor puts
        // the first sequence on top, so layers go first (top layer first) and the timeline last.
        val sequences = mutableListOf<EditedMediaItemSequence>()
        val windows = mutableListOf<Pair<Long, Long>>()
        val belowWindows = mutableListOf<Pair<Long, Long>>()
        fun layerSequence(l: Layer, win: MutableList<Pair<Long, Long>>): EditedMediaItemSequence {
            val startMs = if (l.wholeVideo) 0L else l.startMs.coerceIn(0, totalMs)
            val wantEnd = if (l.wholeVideo) totalMs else l.endMs.coerceIn(startMs, totalMs)
            val avail = (l.videoDurationMs - l.videoStartMs).coerceAtLeast(100)
            val lenMs = (wantEnd - startMs).coerceIn(100, avail)
            val ph = ((l.size * outH).toInt() / 2 * 2).coerceAtLeast(16)
            val pw = ((ph * l.frameAspect).toInt() / 2 * 2).coerceAtLeast(16)
            val item = EditedMediaItem.Builder(
                MediaItem.Builder().setUri(l.videoUri!!)
                    .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(l.videoStartMs).setEndPositionMs(l.videoStartMs + lenMs).build())
                    .build()
            )
                // A video layer's own sound only when switched on (its track's speaker on the timeline).
                .setRemoveAudio(!(l.soundOn == true && settings.audio))
                .setEffects(Effects(if (l.soundOn == true && settings.audio) listOf(AudioFxProcessor(AudioFx(), l.soundVolume() * settings.masterGain, lenMs)) else emptyList(), buildList {
                    add(Presentation.createForWidthAndHeight(pw, ph, if (l.cellAspect != null) Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP else Presentation.LAYOUT_SCALE_TO_FIT))
                    if (l.bgRemove == true) {
                        val mm = l.layerMagic
                        val tr = mm?.let { MaskTrack.load(context, it.id) }
                        if (mm == null) add(BgRemoveEffect(0, 0, l.bgInvert == true))
                        else if (tr != null) add(BgRemoveEffect(0, 0, l.bgInvert == true, tr) { rel -> l.videoStartMs + rel })
                    }
                    if (l.chroma != null || (l.lumaKey ?: 0) != 0) add(ChromaKeyEffect(l.chroma, l.chromaTol ?: 0.4f, l.chromaSoft ?: 0.3f, l.lumaKey ?: 0, l.lumaLevelOrDefault(), l.lumaSoft ?: 0.15f))
                    // Picture-in-picture frame: rounded corners, border, drop shadow.
                    if (l.hasPipStyle) add(PipStyleEffect(
                        (l.corner ?: 0f) * minOf(pw, ph), (l.border ?: 0f) * outH, l.borderColor ?: 0xFFFFFFFF.toInt(),
                        if (l.dropShadow == true) (minOf(pw, ph) * 0.07f).toInt().coerceAtLeast(4) else 0,
                    ))
                    // Corner pin / planar track: warped onto a full-frame see-through canvas.
                    if (!l.pin.isNullOrEmpty()) add(PinWarpEffect(l, outW, outH, startMs))
                }))
                .build()
            val seq = EditedMediaItemSequence.Builder(
                if (l.soundOn == true && settings.audio) setOf(androidx.media3.common.C.TRACK_TYPE_VIDEO, androidx.media3.common.C.TRACK_TYPE_AUDIO)
                else setOf(androidx.media3.common.C.TRACK_TYPE_VIDEO)
            )
            if (startMs > 0) seq.addGap(startMs * 1000)
            seq.addItem(item)
            val after = totalMs - startMs - lenMs
            if (after > 0) seq.addGap(after * 1000)
            win += (startMs * 1000) to ((startMs + lenMs) * 1000)
            return seq.build()
        }
        for (l in videoLayers.asReversed()) sequences += layerSequence(l, windows)
        val layerSettings = videoLayers.asReversed()
        val firstTimeline = sequences.size
        // Clips without sound (drone footage, generated clips…): silence in their place, so sound exports don't fail.
        val wantAudio = settings.audio || audioOnly
        val silent = if (wantAudio) clips.map { !hasAudioTrack(context, CleanRender.sourceFor(context, it, settings) ?: it.uri) } else clips.map { false }
        if (!ab) {
            if (audioOnly) {
                val seq = EditedMediaItemSequence.Builder(setOf(androidx.media3.common.C.TRACK_TYPE_AUDIO))
                for (i in clips.indices) if (silent[i]) seq.addGap(clips[i].lengthMs * 1000) else seq.addItem(items[i])
                sequences += seq.build()
            } else sequences += EditedMediaItemSequence.Builder(items).experimentalSetForceAudioTrack(wantAudio && silent.any { it }).build()
        } else {
            val types = if (audioOnly) setOf(androidx.media3.common.C.TRACK_TYPE_AUDIO)
            else if (settings.audio) setOf(androidx.media3.common.C.TRACK_TYPE_AUDIO, androidx.media3.common.C.TRACK_TYPE_VIDEO)
            else setOf(androidx.media3.common.C.TRACK_TYPE_VIDEO)
            for (parity in 0..1) {
                val seq = EditedMediaItemSequence.Builder(types)
                var t = 0L
                var any = false
                for (i in clips.indices) {
                    if (i % 2 != parity) continue
                    if (starts[i] > t) seq.addGap((starts[i] - t) * 1000)
                    if (audioOnly && silent[i]) seq.addGap(clips[i].lengthMs * 1000) else seq.addItem(items[i])
                    any = true
                    t = starts[i] + clips[i].lengthMs
                }
                // The sound track is part of [types], so clips without sound get silence here too.
                if (totalMs > t) seq.addGap((totalMs - t) * 1000)
                if (any) sequences += seq.build()
            }
        }
        val abCount = sequences.size - firstTimeline
        // Video layers below the main video: after the timeline, so they're drawn underneath it.
        val firstBelow = sequences.size
        val belowSettings = belowLayers.asReversed()
        for (l in belowSettings) sequences += layerSequence(l, belowWindows)
        // Music + extra audio tracks, each placed at its own spot on the timeline.
        if (settings.audio || audioOnly) for (m in (listOfNotNull(music) + extraAudio).filter { !it.muted }) {
            val len = m.playLength(totalMs)
            if (len < 50) continue
            val end = m.offsetMs + len
            // Ducking ranges are relative to where this track starts.
            val ducks = duckRanges(clips).mapNotNull { r ->
                val a = r.first - m.startMs; val b = r.last - m.startMs
                if (b < 0 || a > len) null else a.coerceAtLeast(0) until b.coerceAtMost(len)
            }
            val musicItem = EditedMediaItem.Builder(
                MediaItem.Builder().setUri(m.uri)
                    .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(m.offsetMs).setEndPositionMs(end).build())
                    .build()
            )
                .setRemoveVideo(true)
                .setEffects(Effects(listOf(
                    AudioFxProcessor(
                        m.fx.copy(fadeInMs = m.fadeInMs, fadeOutMs = m.fadeOutMs), m.volume * settings.masterGain, len,
                        duckRanges = ducks, duckGain = 1f - m.duck,
                    )
                ), emptyList()))
                .build()
            val seq = EditedMediaItemSequence.Builder(setOf(androidx.media3.common.C.TRACK_TYPE_AUDIO))
            if (m.startMs > 0) seq.addGap(m.startMs * 1000)
            seq.addItem(musicItem)
            sequences += seq.build()
        }

        // Sound effects on cuts (built-in or imported): impacts start on the cut, risers end on it.
        if (settings.audio || audioOnly) for (k in 0 until clips.lastIndex) {
            val snd = try { cutSound(context, clips, k) } catch (_: Throwable) { null } ?: continue
            var at = cutSoundStart(clips, starts, k, snd.hitMs)
            var trim = 0L
            if (at < 0) { trim = -at; at = 0 }
            var len = snd.lengthMs - trim
            if (at + len > totalMs) len = totalMs - at
            if (len < 50) continue
            val item = EditedMediaItem.Builder(
                MediaItem.Builder().setUri(Uri.fromFile(snd.file))
                    .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(trim).setEndPositionMs(trim + len).build())
                    .build()
            )
                .setRemoveVideo(true)
                .setEffects(Effects(listOf(AudioFxProcessor(AudioFx(), snd.volume * settings.masterGain, len)), emptyList()))
                .build()
            val seq = EditedMediaItemSequence.Builder(setOf(androidx.media3.common.C.TRACK_TYPE_AUDIO))
            if (at > 0) seq.addGap(at * 1000)
            seq.addItem(item)
            sequences += seq.build()
        }

        val mixFx: List<AudioProcessor> = when {
            tap == null -> emptyList()
            // Opus runs at 48 kHz.
            fmt == AudioExportFormat.OPUS -> listOf(androidx.media3.common.audio.SonicAudioProcessor().apply { setOutputSampleRateHz(48_000) }, tap)
            else -> listOf(tap)
        }
        val composition = Composition.Builder(sequences)
            .setEffects(Effects(mixFx, global))
            .apply {
                if (compositing) setVideoCompositorSettings(object : androidx.media3.common.VideoCompositorSettings {
                    override fun getOutputSize(inputSizes: List<androidx.media3.common.util.Size>) = androidx.media3.common.util.Size(outW, outH)
                    override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): androidx.media3.common.OverlaySettings {
                        if (ab && inputId >= firstTimeline && inputId < firstTimeline + abCount) {
                            return abSettings(inputId - firstTimeline, presentationTimeUs / 1000, starts, overlaps)
                        }
                        val isBelowInput = inputId >= firstBelow && inputId < firstBelow + belowSettings.size
                        val l0 = (if (isBelowInput) belowSettings.getOrNull(inputId - firstBelow) else if (inputId < firstTimeline) layerSettings.getOrNull(inputId) else null)
                            ?: return androidx.media3.effect.StaticOverlaySettings.Builder().build()
                        val (s, e) = if (isBelowInput) belowWindows[inputId - firstBelow] else windows[inputId]
                        val on = presentationTimeUs in s..e
                        val l = l0.animAt(presentationTimeUs / 1000)
                        if (!l0.pin.isNullOrEmpty()) return androidx.media3.effect.StaticOverlaySettings.Builder()
                            .setAlphaScale(if (on) l.opacity.coerceIn(0f, 1f) else 0f).build()
                        val k = if (l0.size > 0f) l.size / l0.size else 1f
                        return androidx.media3.effect.StaticOverlaySettings.Builder()
                            .setBackgroundFrameAnchor((l.x * 2f - 1f).coerceIn(-1f, 1f), (1f - l.y * 2f).coerceIn(-1f, 1f))
                            .setOverlayFrameAnchor(0f, 0f)
                            .setScale(k, k)
                            .setAlphaScale(if (on) l.opacity.coerceIn(0f, 1f) else 0f)
                            .build()
                    }
                })
            }
            .setHdrMode(if (settings.keepHdr && !audioOnly) Composition.HDR_MODE_KEEP_HDR else Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
            .build()

        val encoderSettings = if (settings.bitrateMbps > 0)
            VideoEncoderSettings.Builder().setBitrate(settings.bitrateMbps * 1_000_000).build()
        else VideoEncoderSettings.DEFAULT

        transformer = Transformer.Builder(context)
            .also { b -> clips.mapNotNull { it.decoder }.distinct().takeIf { it.isNotEmpty() }?.let { b.setAssetLoaderFactory(Chroma.assetLoaders(context, it)) } }
            .setVideoMimeType(if (settings.hevc || settings.keepHdr) MimeTypes.VIDEO_H265 else MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(
                DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(encoderSettings)
                    .setEnableFallback(true)
                    .build()
            )
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    finished = true
                    freeMorphs()
                    if (toFile != null) {
                        val ok = out.renameTo(toFile) || try { out.copyTo(toFile, overwrite = true); out.delete(); true } catch (_: Throwable) { false }
                        handler.post { onDone(if (ok) Uri.fromFile(toFile) else null, if (ok) null else "Couldn't prepare the sound") }
                        return
                    }
                    Thread {
                        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
                        var err: String? = null
                        val uri = if (audioOnly) {
                            var file = out
                            if (tap != null) {
                                tap.close()
                                val f = File(context.cacheDir, "audio_${System.currentTimeMillis()}.${fmt.ext}")
                                err = AudioFiles.write(rawFile, tap, fmt, f)
                                rawFile.delete()
                                file = f
                            }
                            val name = "AUDIO_$stamp.${fmt.ext}"
                            val u = if (err != null) null else SaveFolder.write(context, true, name, fmt.mime) { os -> file.inputStream().use { it.copyTo(os) } }
                                ?: publishAudio(context, file, name, fmt.mime)
                            if (file !== out) file.delete()
                            u
                        } else {
                            outputSize(clips, settings).let { (ow, oh) -> Spherical.tagIfNeeded(context, clips, ow, oh, out) }
                            // Your details (Deliver → METADATA) written into the file.
                            settings.meta?.let { if (!Mp4Meta.write(out, it)) com.dronedirector.veyrocutpro.ui.AppLog.w("Export: couldn't write the metadata into the video") }
                            val name = "EDIT_$stamp.mp4"
                            SaveFolder.write(context, true, name, "video/mp4") { os -> out.inputStream().use { it.copyTo(os) } }
                                ?: GradeIO.publishVideo(context, out, com.dronedirector.veyrocutpro.camera.StorageArea.VIDEO)
                        }
                        out.delete()
                        if (uri != null && ContentCredentials.on(context, true)) ContentCredentials.sign(context, uri, true)
                        ExportUpload.after(context, uri, true)
                        handler.post { onDone(uri, if (uri == null) (err ?: if (audioOnly) "Couldn't save the audio" else "Couldn't save the video") else null) }
                    }.start()
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    finished = true
                    freeMorphs()
                    out.delete()
                    tap?.close(); rawFile.delete()
                    // Full details (what failed inside) kept for a report — shown with COPY / SHARE.
                    com.dronedirector.veyrocutpro.ui.CrashReport.problem(context, "export failed (${if (audioOnly) "audio only" else if (settings.audio) "video + sound" else "video"})", exportException,
                        "clips ${clips.size}, music ${if (music != null) 1 else 0}, extra tracks ${extraAudio.size}, audio formats: " +
                            clips.take(8).joinToString { c -> try { audioInfo(context, c.uri) } catch (_: Throwable) { "?" } })
                    var why = ""
                    var cause: Throwable? = exportException.cause
                    for (k in 0 until 8) {
                        val c0 = cause ?: break
                        why = (c0.javaClass.simpleName + (c0.message?.let { ": $it" } ?: "")).take(120)
                        cause = c0.cause
                    }
                    onDone(null, "Export failed: ${exportException.errorCodeName}" + if (why.isNotBlank()) " — $why" else "")
                }
            })
            .build()
        transformer.start(composition, out.path)
        poll()
    }

    /** Track A (0, even clips, on top) or B (1): visibility and movement during transitions. */
    private fun abSettings(track: Int, ms: Long, starts: LongArray, overlaps: LongArray): androidx.media3.common.OverlaySettings {
        val b = androidx.media3.effect.StaticOverlaySettings.Builder()
        val i = clips.indices.firstOrNull { it % 2 == track && ms >= starts[it] && ms < starts[it] + clips[it].lengthMs }
            ?: return b.setAlphaScale(if (track == 1) 1f else 0f).build()
        var alpha = 1f; var tx = 0f; var ty = 0f
        val c = clips[i]
        // Coming in from the previous clip?
        if (i > 0 && overlaps[i - 1] > 0 && ms < starts[i] + overlaps[i - 1]) {
            val t = clips[i - 1].transition.type
            val p = ((ms - starts[i]).toFloat() / overlaps[i - 1]).coerceIn(0f, 1f)
            if (t.kind == TransitionKind.UNIFORM && track == 0) alpha = TransitionMath.uniformReveal(t, p)
            if (t.kind == TransitionKind.MOVE) TransitionMath.move(t, p, true).let { tx = it.first; ty = it.second }
        }
        // Going out into the next clip?
        val endOv = overlaps[i]
        if (endOv > 0 && ms >= starts[i] + c.lengthMs - endOv) {
            val t = c.transition.type
            val p = ((ms - (starts[i] + c.lengthMs - endOv)).toFloat() / endOv).coerceIn(0f, 1f)
            if (t.kind == TransitionKind.UNIFORM && track == 0) alpha = 1f - TransitionMath.uniformReveal(t, p)
            if (t.kind == TransitionKind.MOVE) TransitionMath.move(t, p, false).let { tx = it.first; ty = it.second }
        }
        // A shift of up to a whole frame (2 in frame units) is split between the two anchors: each may only go from −1 to 1.
        val sx = (tx * 2f).coerceIn(-2f, 2f); val sy = (-ty * 2f).coerceIn(-2f, 2f)
        return b.setAlphaScale(alpha.coerceIn(0f, 1f))
            .setBackgroundFrameAnchor(sx / 2f, sy / 2f)
            .setOverlayFrameAnchor(-sx / 2f, -sy / 2f)
            .build()
    }

    private fun poll() {
        if (finished) return
        val holder = ProgressHolder()
        if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress)
        handler.postDelayed({ poll() }, 400)
    }

    fun cancel() {
        if (smartRunning) { finished = true; smartRunning = false; return }
        morphPrep?.let { it.cancel(); morphPrep = null; finished = true; return }
        if (finished || !::transformer.isInitialized) return
        finished = true
        freeMorphs()
        transformer.cancel()
        out.delete()
        tap?.close(); rawFile.delete()
    }
}

/** Saves an exported sound file to Music/AlcorCutPro. */
fun publishAudio(context: Context, file: File, name: String, mime: String = "audio/mp4"): Uri? {
    val resolver = context.contentResolver
    if (android.os.Build.VERSION.SDK_INT >= 29) {
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime)
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Music/AlcorCutPro")
            put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val target = com.dronedirector.veyrocutpro.camera.Storage.insert(context, com.dronedirector.veyrocutpro.camera.Storage.load(context, com.dronedirector.veyrocutpro.camera.StorageArea.VIDEO), values, com.dronedirector.veyrocutpro.camera.Storage.Kind.AUDIO) ?: return null
        return try {
            resolver.openOutputStream(target)?.use { o -> file.inputStream().use { it.copyTo(o, 1 shl 16) } }
            com.dronedirector.veyrocutpro.camera.Storage.publish(context, target)
            target
        } catch (_: Throwable) { com.dronedirector.veyrocutpro.camera.Storage.discard(context, target); null }
    }
    return try {
        @Suppress("DEPRECATION")
        val dir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC), "AlcorCutPro").apply { mkdirs() }
        val dest = File(dir, name)
        file.inputStream().use { i -> dest.outputStream().use { i.copyTo(it) } }
        android.media.MediaScannerConnection.scanFile(context, arrayOf(dest.path), arrayOf(mime), null)
        Uri.fromFile(dest)
    } catch (_: Throwable) { null }
}

/** Timeline ranges (ms) of clips that ask for the music to be lowered under them. */
fun duckRanges(clips: List<Clip>): List<LongRange> {
    val out = mutableListOf<LongRange>()
    val s = clipStarts(clips)
    clips.forEachIndexed { i, c -> if (c.audio.duckMusic && !c.muted && c.volume > 0f) out += s[i] until s[i] + c.lengthMs }
    return out
}

/** Computing a colour table takes a moment; reuse the numbers (never the GPU effect itself). */
internal object LutCache {
    private val map = object : LinkedHashMap<ProGrade, Array<Array<IntArray>>>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ProGrade, Array<Array<IntArray>>>?) = size > 8
    }
    @Synchronized
    fun cube(g: ProGrade): Array<Array<IntArray>> = map.getOrPut(g) { g.cube() }
}

/**
 * Why this edit can't be smart-rendered (copied without re-encoding), or null when it can.
 * The media itself (same formats on every clip) is checked when the export starts.
 */
fun smartRenderBlocker(context: Context, clips: List<Clip>, s: DeliverSettings, layers: List<Layer>, music: MusicTrack?, extra: List<MusicTrack>): String? {
    if (clips.isEmpty()) return "there are no clips"
    if (s.audioOnly) return "this is a sound-only export"
    if (s.review) return "Review copy is on"
    if (s.shortSide != 0 || s.aspect != 0f) return "the resolution or shape is changed (choose Source)"
    if (s.fps != 0) return "the frame rate is changed (choose Source)"
    if (s.bitrateMbps != 0) return "a bitrate is chosen (choose Auto)"
    if (s.videoGain != 1f || s.masterGain != 1f || s.videoPan != 0f) return "the mixer changes the sound"
    if (layers.any { it.visible }) return "there are layers (text, logos, video)"
    if (clips.any { it.hidden == true }) return "the video track is hidden"
    if (s.framed) return "the picture is zoomed / moved in the viewer"
    if (s.audio && (music != null || extra.any { !it.muted })) return "there is music or an extra sound track"
    val anyMuted = clips.any { it.muted }
    if (s.audio && anyMuted && clips.any { !it.muted }) return "some clips are muted"
    for ((i, c) in clips.withIndex()) {
        val n = "clip ${i + 1}"
        if (c.nested != null) return "$n is a compound clip"
        if (c.speed != 1f || c.ramp != null) return "$n has a speed change"
        if (c.decoder != null) return "$n needs its own decoder"
        if (c.hdr && !s.keepHdr) return "$n is HDR and HDR is turned into SDR"
        if (c.detail.aiDenoise > 0f || c.bg != null || c.depth != null || !c.reframe.isIdentity || c.reframe.hasOpacity ||
            c.stab.enabled || (c.desqueeze ?: 1f) > 1f || clipEffects(c).isNotEmpty() || PenMask.active(c).isNotEmpty() || !c.masks.isNullOrEmpty() || c.privacy?.active == true) return "$n has a grade, effect, crop or mask"
        if (s.audio && !c.muted && (c.volume != 1f || !c.audio.isNeutral || !c.volKeys.isNullOrEmpty())) return "$n has sound changes"
        if (i < clips.lastIndex && overlapMs(clips, i) > 0) return "there is a transition after $n"
    }
    if (morphCuts(clips).isNotEmpty()) return "there is a morph cut"
    if (s.audio) for (k in 0 until clips.lastIndex) if (try { cutSound(context, clips, k) } catch (_: Throwable) { null } != null) return "there is a sound effect on a cut"
    return null
}

/** Review copy: the timeline timecode burned into the top-left corner, frame by frame. */
@androidx.media3.common.util.UnstableApi
class TimecodeOverlay(outW: Int, outH: Int, private val startMs: Long, private val fps: Int) : androidx.media3.effect.BitmapOverlay() {
    private val h = (outH * 0.045f).toInt().coerceAtLeast(16)
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE; textSize = h * 0.72f; typeface = android.graphics.Typeface.MONOSPACE
    }
    private val w = (paint.measureText("00:00:00:00") + h * 0.6f).toInt()
    private var lastFrame = -1L
    private var bmp: android.graphics.Bitmap? = null
    private val settings = androidx.media3.effect.StaticOverlaySettings.Builder()
        .setBackgroundFrameAnchor(-0.96f, 0.94f).setOverlayFrameAnchor(-1f, 1f).build()

    override fun getBitmap(presentationTimeUs: Long): android.graphics.Bitmap {
        val ms = startMs + presentationTimeUs / 1000
        val frame = ms * fps / 1000
        bmp?.let { if (frame == lastFrame) return it }
        val s = frame / fps; val ff = frame % fps
        val text = "%02d:%02d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60, ff)
        val b = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(b)
        c.drawColor(0xAA000000.toInt())
        c.drawText(text, h * 0.3f, h * 0.76f, paint)
        lastFrame = frame; bmp = b
        return b
    }

    override fun getOverlaySettings(presentationTimeUs: Long): androidx.media3.common.OverlaySettings = settings
}

/** The audio track of [uri] as the decoder sees it (for problem reports). */
internal fun audioInfo(context: Context, uri: Uri): String {
    val ex = android.media.MediaExtractor()
    return try {
        ex.setDataSource(context, uri, null)
        (0 until ex.trackCount).map { ex.getTrackFormat(it) }.firstOrNull { it.getString(android.media.MediaFormat.KEY_MIME)?.startsWith("audio/") == true }?.let { f ->
            "${f.getString(android.media.MediaFormat.KEY_MIME)} ${f.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE)}Hz ${f.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT)}ch" +
                (if (f.containsKey("pcm-encoding")) " pcm${f.getInteger("pcm-encoding")}" else "")
        } ?: "no audio"
    } finally { ex.release() }
}

private val audioTrackCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

/** Whether [uri] has a sound track (remembered per file). */
internal fun hasAudioTrack(context: Context, uri: Uri): Boolean = audioTrackCache.getOrPut(uri.toString()) {
    val ex = android.media.MediaExtractor()
    try {
        ex.setDataSource(context, uri, null)
        (0 until ex.trackCount).any { ex.getTrackFormat(it).getString(android.media.MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
    } catch (_: Throwable) { true } finally { ex.release() }
        // A second opinion before treating a clip as silent (its sound must never be dropped by mistake).
        || try {
            val m = android.media.MediaMetadataRetriever()
            try { m.setDataSource(context, uri); m.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes" } finally { m.release() }
        } catch (_: Throwable) { true }
}
