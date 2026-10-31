package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

enum class TransitionGroup(val label: String) { BASIC("Basic"), WIPES("Wipes & shapes"), MOTION("Motion"), FANCY("Stylized"), CINEMATIC("Cinematic"), TRAVEL("Drone & travel"), CREATIVE("Creative") }

/** How the incoming clip is combined with the outgoing one. */
enum class TransitionKind { UNIFORM, MASK, MOVE }

enum class TransitionType(val id: Float, val label: String, val group: TransitionGroup, val kind: TransitionKind) {
    NONE(0f, "Cut", TransitionGroup.BASIC, TransitionKind.UNIFORM),
    CROSSFADE(1f, "Cross dissolve", TransitionGroup.BASIC, TransitionKind.UNIFORM),
    DIP_BLACK(2f, "Dip to black", TransitionGroup.BASIC, TransitionKind.UNIFORM),
    DIP_WHITE(3f, "Dip to white", TransitionGroup.BASIC, TransitionKind.UNIFORM),
    FLASH(4f, "Flash", TransitionGroup.BASIC, TransitionKind.UNIFORM),
    BLUR(5f, "Blur dissolve", TransitionGroup.BASIC, TransitionKind.UNIFORM),
    /** For jump cuts (interviews), in-between frames drawn new from optical flow (MorphCut.kt); a soft dissolve until analysed. */
    SMOOTH_CUT(7f, "Morph cut", TransitionGroup.BASIC, TransitionKind.UNIFORM),

    WIPE_LEFT(10f, "Wipe left", TransitionGroup.WIPES, TransitionKind.MASK),
    WIPE_RIGHT(11f, "Wipe right", TransitionGroup.WIPES, TransitionKind.MASK),
    WIPE_UP(12f, "Wipe up", TransitionGroup.WIPES, TransitionKind.MASK),
    WIPE_DOWN(13f, "Wipe down", TransitionGroup.WIPES, TransitionKind.MASK),
    DIAGONAL(14f, "Diagonal", TransitionGroup.WIPES, TransitionKind.MASK),
    IRIS(15f, "Iris", TransitionGroup.WIPES, TransitionKind.MASK),
    CLOCK(16f, "Clock", TransitionGroup.WIPES, TransitionKind.MASK),
    BLINDS(17f, "Blinds", TransitionGroup.WIPES, TransitionKind.MASK),
    BARN_DOOR(18f, "Barn door", TransitionGroup.WIPES, TransitionKind.MASK),
    MOSAIC(19f, "Mosaic", TransitionGroup.WIPES, TransitionKind.MASK),

    PUSH_LEFT(30f, "Push left", TransitionGroup.MOTION, TransitionKind.MOVE),
    PUSH_RIGHT(31f, "Push right", TransitionGroup.MOTION, TransitionKind.MOVE),
    PUSH_UP(32f, "Push up", TransitionGroup.MOTION, TransitionKind.MOVE),
    PUSH_DOWN(33f, "Push down", TransitionGroup.MOTION, TransitionKind.MOVE),
    WHIP_LEFT(34f, "Whip pan left", TransitionGroup.MOTION, TransitionKind.MOVE),
    WHIP_RIGHT(35f, "Whip pan right", TransitionGroup.MOTION, TransitionKind.MOVE),
    ZOOM_IN(36f, "Zoom in", TransitionGroup.MOTION, TransitionKind.UNIFORM),
    ZOOM_OUT(37f, "Zoom out", TransitionGroup.MOTION, TransitionKind.UNIFORM),
    SPIN(38f, "Spin", TransitionGroup.MOTION, TransitionKind.UNIFORM),

    LIGHT_LEAK(6f, "Light leak", TransitionGroup.FANCY, TransitionKind.UNIFORM),
    INK(20f, "Ink bleed", TransitionGroup.FANCY, TransitionKind.MASK),
    SPARKLE(21f, "Sparkle dissolve", TransitionGroup.FANCY, TransitionKind.MASK),
    GLITCH(40f, "Glitch", TransitionGroup.FANCY, TransitionKind.MASK),
    PIXELATE(41f, "Pixelate", TransitionGroup.FANCY, TransitionKind.UNIFORM),
    CHROMA(42f, "Chromatic", TransitionGroup.FANCY, TransitionKind.UNIFORM),
    RIPPLE(43f, "Ripple", TransitionGroup.FANCY, TransitionKind.UNIFORM),
    LIGHT_SPEED(44f, "Light speed", TransitionGroup.FANCY, TransitionKind.UNIFORM),
    SWIRL(45f, "Swirl", TransitionGroup.FANCY, TransitionKind.UNIFORM),
    GLITCH_MOSH(53f, "Glitch 2 · Datamosh", TransitionGroup.FANCY, TransitionKind.MASK),
    GLITCH_VHS(54f, "Glitch 3 · VHS rewind", TransitionGroup.FANCY, TransitionKind.UNIFORM),
    GLITCH_SORT(63f, "Glitch 4 · Pixel sort", TransitionGroup.FANCY, TransitionKind.UNIFORM),

    // Cinematic set: original AlcorCut Pro shaders (written for this app, no third-party code).
    FILM_BURN(50f, "Film burn", TransitionGroup.CINEMATIC, TransitionKind.MASK),
    ANAMORPHIC(51f, "Anamorphic flare", TransitionGroup.CINEMATIC, TransitionKind.UNIFORM),
    LENS_WARP(52f, "Lens warp", TransitionGroup.CINEMATIC, TransitionKind.UNIFORM),
    LETTERBOX(55f, "Letterbox close", TransitionGroup.CINEMATIC, TransitionKind.UNIFORM),
    APERTURE(56f, "Aperture", TransitionGroup.CINEMATIC, TransitionKind.UNIFORM),
    SMOKE(57f, "Smoke", TransitionGroup.CINEMATIC, TransitionKind.MASK),
    SHATTER(58f, "Shatter", TransitionGroup.CINEMATIC, TransitionKind.MASK),
    KALEIDO(59f, "Kaleidoscope", TransitionGroup.CINEMATIC, TransitionKind.UNIFORM),
    CUBE(60f, "Cube turn", TransitionGroup.CINEMATIC, TransitionKind.MASK),
    FLIP(61f, "Card flip", TransitionGroup.CINEMATIC, TransitionKind.MASK),
    SUN_RAYS(62f, "Sun rays", TransitionGroup.CINEMATIC, TransitionKind.UNIFORM),
    NEON(64f, "Neon edges", TransitionGroup.CINEMATIC, TransitionKind.UNIFORM),
    EMBERS(65f, "Ember drift", TransitionGroup.CINEMATIC, TransitionKind.MASK),
    RACK_FOCUS(67f, "Rack focus", TransitionGroup.CINEMATIC, TransitionKind.UNIFORM),
    LUMA_FADE(70f, "Luma fade", TransitionGroup.CINEMATIC, TransitionKind.MASK),
    SHINE(72f, "Shine sweep", TransitionGroup.CINEMATIC, TransitionKind.MASK),
    BOKEH(74f, "Bokeh lights", TransitionGroup.CINEMATIC, TransitionKind.UNIFORM),
    PRISM(75f, "Prism split", TransitionGroup.CINEMATIC, TransitionKind.UNIFORM),

    // Drone & travel set (original shaders).
    CLOUD_PASS(66f, "Cloud pass", TransitionGroup.TRAVEL, TransitionKind.UNIFORM),
    WARP_ZOOM(68f, "Warp zoom", TransitionGroup.TRAVEL, TransitionKind.UNIFORM),
    DRONE_ROLL(69f, "Drone roll", TransitionGroup.TRAVEL, TransitionKind.UNIFORM),
    DOLLY_ZOOM(71f, "Dolly zoom", TransitionGroup.TRAVEL, TransitionKind.UNIFORM),
    FILM_GATE(73f, "Film gate roll", TransitionGroup.TRAVEL, TransitionKind.MOVE),
    HEAT_HAZE(76f, "Heat haze", TransitionGroup.TRAVEL, TransitionKind.UNIFORM),

    // Creative set (original shaders).
    PAINT_SPLASH(77f, "Paint splash", TransitionGroup.CREATIVE, TransitionKind.MASK),
    HONEYCOMB(78f, "Honeycomb", TransitionGroup.CREATIVE, TransitionKind.MASK),
    HEART(79f, "Heart iris", TransitionGroup.CREATIVE, TransitionKind.MASK),
    STAR(80f, "Star iris", TransitionGroup.CREATIVE, TransitionKind.MASK),
    DIAMOND(81f, "Diamond", TransitionGroup.CREATIVE, TransitionKind.MASK),
    SPIN_BLUR(82f, "Spin blur", TransitionGroup.CREATIVE, TransitionKind.UNIFORM),
    WAVE_WIPE(83f, "Wave wipe", TransitionGroup.CREATIVE, TransitionKind.MASK),
    STRIPES(84f, "Stripes", TransitionGroup.CREATIVE, TransitionKind.MASK),
    CHECKER(85f, "Checkerboard", TransitionGroup.CREATIVE, TransitionKind.MASK),
    WATER_DROP(86f, "Water drop", TransitionGroup.CREATIVE, TransitionKind.MASK),
    TV_SWITCH(87f, "TV switch", TransitionGroup.CREATIVE, TransitionKind.UNIFORM),
    GLOW_DISSOLVE(88f, "Glow dissolve", TransitionGroup.CREATIVE, TransitionKind.UNIFORM),
    DIP_COLOUR(89f, "Dip to colour", TransitionGroup.CREATIVE, TransitionKind.UNIFORM),
    POLKA(90f, "Polka dots", TransitionGroup.CREATIVE, TransitionKind.MASK),
    CROSS_WARP(91f, "Stretch warp", TransitionGroup.CREATIVE, TransitionKind.UNIFORM),
    SPLIT_OPEN(92f, "Split open", TransitionGroup.CREATIVE, TransitionKind.MASK),
    PINWHEEL(93f, "Pinwheel", TransitionGroup.CREATIVE, TransitionKind.MASK),
    SLIDE_BLUR(94f, "Slide blur", TransitionGroup.CREATIVE, TransitionKind.MOVE),
    JELLY(95f, "Jelly", TransitionGroup.CREATIVE, TransitionKind.UNIFORM),
    PIXEL_RAIN(96f, "Pixel rain", TransitionGroup.CREATIVE, TransitionKind.MASK),
    INK_DROP(97f, "Ink drop", TransitionGroup.CREATIVE, TransitionKind.MASK),
    ZOOM_SPIN(98f, "Zoom spin", TransitionGroup.CREATIVE, TransitionKind.UNIFORM),
    MIRROR_FOLD(99f, "Mirror fold", TransitionGroup.CREATIVE, TransitionKind.UNIFORM),
    LIGHT_STREAKS(100f, "Light streaks", TransitionGroup.CREATIVE, TransitionKind.UNIFORM),
    SPIRAL_WIPE(101f, "Spiral wipe", TransitionGroup.CREATIVE, TransitionKind.MASK),
    BARS_RISE(102f, "Bars rise", TransitionGroup.CREATIVE, TransitionKind.MASK),
    SLICES(103f, "Shutter slices", TransitionGroup.CREATIVE, TransitionKind.MASK),
    VIGNETTE_CLOSE(104f, "Vignette close", TransitionGroup.CREATIVE, TransitionKind.UNIFORM),
    CHROMA_ZOOM(105f, "Chroma zoom", TransitionGroup.CREATIVE, TransitionKind.UNIFORM),
    OVEREXPOSE(106f, "Overexpose", TransitionGroup.CREATIVE, TransitionKind.UNIFORM),

    // More cinematic looks (original shaders).
    SUN_FLARE_WIPE(107f, "Sun flare wipe", TransitionGroup.CINEMATIC, TransitionKind.MASK),
    DUST_BEAM(108f, "Dust in light", TransitionGroup.CINEMATIC, TransitionKind.UNIFORM),
    PROJECTOR(109f, "Projector", TransitionGroup.CINEMATIC, TransitionKind.UNIFORM),
    LIGHTNING(110f, "Lightning", TransitionGroup.CINEMATIC, TransitionKind.UNIFORM),
    BLACK_HOLE(111f, "Black hole", TransitionGroup.CINEMATIC, TransitionKind.UNIFORM),
    FOG_ROLL(112f, "Fog roll", TransitionGroup.CINEMATIC, TransitionKind.MASK),
    RAIN_GLASS(113f, "Rain on glass", TransitionGroup.CINEMATIC, TransitionKind.UNIFORM),
    BLIZZARD(114f, "Blizzard", TransitionGroup.CINEMATIC, TransitionKind.UNIFORM),
    FIRE_WIPE(115f, "Fire wipe", TransitionGroup.CINEMATIC, TransitionKind.MASK),
    TUNNEL(116f, "Light tunnel", TransitionGroup.CINEMATIC, TransitionKind.UNIFORM),
    PRISM_LEAK(117f, "Prism leak", TransitionGroup.CINEMATIC, TransitionKind.UNIFORM),
    SILENT_IRIS(118f, "Silent film iris", TransitionGroup.CINEMATIC, TransitionKind.MASK),
    GLASS_PANE(119f, "Glass pane", TransitionGroup.CINEMATIC, TransitionKind.MASK),
    DEPTH_PUSH(120f, "Depth push", TransitionGroup.CINEMATIC, TransitionKind.UNIFORM),
    SHOCKWAVE(121f, "Shockwave", TransitionGroup.CINEMATIC, TransitionKind.MASK),
    TILT_DOWN(122f, "Tilt down", TransitionGroup.TRAVEL, TransitionKind.UNIFORM),
    HORIZON(123f, "Horizon wipe", TransitionGroup.TRAVEL, TransitionKind.MASK),
    GOLDEN_HOUR(124f, "Golden hour", TransitionGroup.TRAVEL, TransitionKind.UNIFORM),
    NIGHTFALL(125f, "Nightfall", TransitionGroup.TRAVEL, TransitionKind.UNIFORM),
    LENS_DIRT(126f, "Lens dirt flare", TransitionGroup.CINEMATIC, TransitionKind.UNIFORM),
    BRUSH(127f, "Brush strokes", TransitionGroup.CREATIVE, TransitionKind.MASK),
    TORN_PAPER(128f, "Torn paper", TransitionGroup.CREATIVE, TransitionKind.MASK),
    COMIC_BURST(129f, "Comic burst", TransitionGroup.CREATIVE, TransitionKind.MASK),
    HALFTONE(130f, "Halftone", TransitionGroup.CREATIVE, TransitionKind.UNIFORM),
    THERMAL(131f, "Thermal", TransitionGroup.FANCY, TransitionKind.UNIFORM),
    NIGHT_VISION(132f, "Night vision", TransitionGroup.FANCY, TransitionKind.UNIFORM),
    HOLOGRAM(133f, "Hologram", TransitionGroup.FANCY, TransitionKind.UNIFORM),
    PAGE_TURN(134f, "Page turn", TransitionGroup.CREATIVE, TransitionKind.MASK),
    BLUR_WIPE(135f, "Blur wipe", TransitionGroup.WIPES, TransitionKind.MASK),
    ZOOM_PUNCH(136f, "Zoom punch", TransitionGroup.MOTION, TransitionKind.UNIFORM),
}

/**
 * Transition from a clip into the next one: the picture ([type]) and the sound ([audio]).
 * [audio] / [audioMs] are nullable so projects saved before audio transitions still load.
 */
data class Transition(
    val type: TransitionType = TransitionType.NONE,
    val durationMs: Long = 800L,
    val audio: AudioTransitionType? = null,
    val audioMs: Long? = null,
    /** Sound effect on the cut (impact, whoosh…); null = none. */
    val sfx: TransitionSfx? = null,
    val sfxVol: Float? = null,
    /** An imported sound (UserSfxLibrary id); used instead of [sfx] when set. */
    val userSfx: String? = null,
) {
    val sfxVolume: Float get() = sfxVol ?: 0.8f
    val audioType: AudioTransitionType get() = audio ?: AudioTransitionType.AUTO
    val audioLen: Long get() = audioMs ?: 600L
}

/** How the sound changes from one clip to the next. */
enum class AudioTransitionType(val label: String, val detail: String) {
    AUTO("Match video", "Smooth crossfade over the video transition; nothing on a plain cut."),
    CUT("Hard cut", "Sound switches in the middle of the transition, no fade."),
    CONSTANT_GAIN("Constant gain", "Straight-line crossfade. Can dip slightly in the middle."),
    CONSTANT_POWER("Constant power", "Equal-power crossfade — no dip in loudness. Best all-rounder."),
    EXPONENTIAL("Exponential fade", "Outgoing sound drops away quickly, incoming swells in late. Natural for music."),
    DIP("Dip to silence", "Outgoing fades out, a short silence, then the next clip fades in."),
    QUICK_OUT("Quick out, slow in", "Old sound disappears fast; new sound eases in."),
    QUICK_IN("Slow out, quick in", "Old sound lingers and fades; new sound arrives fast."),
    S_CURVE("S-curve", "Eases out and in at both ends, quick in the middle. The softest crossfade, for dialogue and ambience."),
    PAUSE("Dramatic pause", "Old sound fades fast, a beat of silence, then the new sound lands. For big reveals."),
    LATE_SWAP("Late swap", "Keeps the old sound until near the end of the transition, then swaps quickly."),
    EARLY_SWAP("Early swap", "The new clip's sound takes over at the start of the transition; the picture follows the sound."),
    SWELL("Swell", "The new sound rises slowly and blooms at the end while the old one fades evenly. Lovely into music."),
    UNDERWATER("Underwater", "The old sound sinks under water, the new one surfaces from it."),
    MUFFLE_OUT("Muffle out", "The old sound goes muffled, as if a door closes, while the new one fades in clear."),
    OPEN_UP("Open up", "The new sound starts muffled and opens up to full brightness."),
    MUFFLED_X("Muffled crossfade", "Both sides muffled in the middle: the old closes, the new opens."),
    THIN_OUT("Thin out", "The bass drains out of the old sound until only a thin top is left."),
    ECHO_OUT("Echo out", "The old sound trails off in echoes under the new one."),
    ECHO_IN("Echo in", "The new sound arrives with echoes that settle down."),
    REVERB_WASH("Reverb wash", "Both sides wash into a big room; the new one dries off."),
    REVERSE_SWELL("Reverb swell", "The new sound blooms out of a reverb swell, like a reversed tail."),
    TAPE_STOP("Tape stop", "The old sound slows to a stop like a tape machine switched off."),
    TAPE_START("Tape start", "The new sound speeds up from a standstill."),
    TAPE_BOTH("Tape stop & start", "Old stops, new spins up — a DJ-style turnaround."),
    STUTTER("Stutter out", "The old sound stutters in short repeats as it leaves."),
    GLITCH("Glitch", "Repeats and digital crunch on both sides."),
    BITCRUSH("Bitcrush", "The old sound breaks down into lo-fi digital grit."),
    TELEPHONE("Telephone", "The old sound shrinks into a phone line; the new one comes back full."),
    RADIO("Old radio", "An old radio sound with a little hiss, on both sides."),
    PITCH_DIVE("Pitch dive", "The old sound dives down in pitch as it fades."),
    PITCH_RISE("Pitch rise", "The old sound rises in pitch as it fades — builds tension."),
    TREMOLO("Tremolo fade", "The old sound flutters faster and faster as it fades."),
    CHOPPER("Chopper", "The old sound is chopped into a fast rhythm before the cut."),
    FLANGER("Flanger sweep", "A jet-like sweep over both sides."),
    WOBBLE("Wobble", "A wobbling filter on the old sound, faster towards the cut."),
    RISER("Riser", "A rising noise sweep builds up into the cut."),
    DRIVE("Distortion fade", "The old sound saturates and breaks up as it fades."),
    SPIN_BACK("DJ rewind", "The old sound spins backwards like a DJ rewinding the record."),
    VINYL_BRAKE("Record brake", "The old sound brakes to a stop and goes dark, like a turntable switched off."),
    PUMP("Pump", "The old sound pumps on the beat, harder towards the cut, like dance music."),
    RING("Metal ring", "The old sound turns metallic and robotic as it fades."),
    AUTOPAN("Auto-pan", "Both sides swing left and right in the stereo picture (stereo sound)."),
    SLAP("Slap echo", "A short slap-back echo on the old sound, like a canyon wall."),
    LOFI("Lo-fi", "The old sound turns into a warm, wobbly lo-fi tape; the new one comes in lo-fi and clears."),
    KARAOKE("Voice out", "The voice in the middle disappears from the old sound first (stereo sound)."),
    AIRY("Airy", "Thin and bright with a light room, on both sides."),
    GATE("Trance gate", "The old sound is gated into a fast pulsing rhythm before the cut."),
    DOPPLER("Doppler fly-by", "The old sound drops in pitch and dulls, as if it flies past."),
    FILTER_SWEEP("Filter sweep", "A resonant filter sweeps up through the old sound."),
    VINYL("Vinyl crackle", "The old sound turns into an old record with crackle as it fades."),
    RADIO_IN("Radio in", "The new sound starts as an old radio and comes up to full sound."),
    PHONE_IN("Phone in", "The new sound starts on a phone line and opens up."),
    STUTTER_IN("Stutter in", "The new sound arrives in stuttering repeats."),
    PITCH_SLIDE_IN("Pitch slide in", "The new sound slides up into pitch from below."),
    SHIMMER("Shimmer", "The old sound blooms into a bright, heavenly reverb."),
    FREEZE("Freeze", "The last moment of the old sound is held and rings out like a drone."),
    SUB_DROP("Sub drop", "A deep falling boom lands right on the cut."),
    DOWNLIFTER("Downlifter", "A falling noise sweep carries the new sound in."),
    CHORUS("Chorus wash", "Both sides turn wide and lush around the cut."),
    CATHEDRAL("Cathedral", "A huge, long reverb on both sides."),
    RISE_DROP("Rise & drop", "The old sound rises in pitch, then the new one drops in from muffled."),
    SURFACE("Surface", "The new sound comes up from under water."),
    PHASER_SWEEP("Phaser sweep", "A swooshing phaser over both sides."),
    PING_PONG("Ping-pong echo", "The old sound bounces away in echoes from left to right."),
    MEGAPHONE("Megaphone", "The old sound squeezes into a megaphone as it fades."),
    NARROW_OUT("Narrow out", "The old sound shrinks from wide stereo to a single point."),
    WIDE_IN("Wide in", "The new sound arrives extra wide, then settles."),
    HARMONY_RISE("Harmony rise", "A voice a fifth above swells out of the old sound."),
    OCTAVE_DROP("Octave drop", "A deep octave below grows under the old sound."),
    REVERSE_OUT("Reverse out", "The old sound plays backwards into the cut."),
    CRUSH_IN("Crush in", "The new sound starts crunchy and lo-fi, then cleans up."),
    BASS_IN("Bass drop in", "The new sound starts thin, then its bass drops in."),
    THIN_X("Thin crossfade", "Both sides lose their bass in the middle."),
    FLUTTER_IN("Flutter in", "The new sound flutters, then steadies."),
    GATE_IN("Gate in", "The new sound pulses in a trance gate, then plays freely."),
    CHOP_IN("Chop in", "The new sound is chopped, then runs smooth."),
    WOBBLE_IN("Wobble in", "A wobbling filter settles on the new sound."),
    GRIT_IN("Grit in", "The new sound starts gritty and cleans up."),
    METAL_IN("Metal in", "The new sound starts metallic and turns natural."),
    SHIMMER_IN("Shimmer in", "The new sound comes out of a bright, heavenly reverb."),
    HALL_TAIL("Hall tail", "The old sound fades into a long concert-hall tail."),
    SLAP_IN("Slap in", "The new sound arrives with a quick slap-back echo."),
    PUMP_X("Pump crossfade", "Both sides pump like side-chained dance music."),
    PHONE_CALL("Phone call", "Both sides sound like a phone line around the cut."),
    RECORD_IN("Old record in", "The new sound starts as a crackly record and clears."),
    FLY_IN("Fly in", "The new sound flies in, low and dull, then clear."),
    FILTER_OPEN("Filter open", "A resonant filter opens up on the new sound."),
    FREEZE_ECHO("Freeze & echo", "The old sound freezes; the new one arrives in echoes."),
    TAPE_ECHO("Tape stop echo", "The old sound stops like tape; the new one echoes in."),
    STUTTER_X("Stutter swap", "Both sides stutter across the cut."),
    BUILD_BOOM("Build & boom", "A riser builds up and the new sound lands with a deep boom."),
    MUFFLE_ECHO("Muffle to echo", "The old sound goes muffled; the new one echoes in."),
    VOICE_IN("Voice in", "The new sound starts without its centre (usually the voice), then it comes in."),
    AIRY_OUT("Airy out", "The old sound turns thin and airy as it leaves."),
    LOFI_OUT("Lo-fi out", "The old sound turns warm and wobbly as it leaves."),
    LUSH_IN("Lush in", "The new sound arrives wide and lush, then focuses."),
    REVERSE_SWELL_X("Reverse & swell", "The old sound plays backwards; the new one swells out of reverb."),
    MEGAPHONE_IN("Megaphone in", "The new sound starts through a megaphone and opens up."),
    REWIND_RESTART("Rewind & restart", "The old sound rewinds, the new one spins up."),
}

enum class FadeCurve { LINEAR, POWER, EXP, SMOOTH }

private fun curveIn(c: FadeCurve, p: Float): Float = when (c) {
    FadeCurve.LINEAR -> p
    FadeCurve.POWER -> kotlin.math.sin(p * (Math.PI / 2).toFloat())
    FadeCurve.EXP -> p * p
    FadeCurve.SMOOTH -> 0.5f - 0.5f * kotlin.math.cos(p * Math.PI.toFloat())
}

/** Fade at the end of the outgoing clip: starts [fromEndMs] before its end, takes [lenMs], then silence. */
data class FadeOut(val fromEndMs: Long, val lenMs: Long, val curve: FadeCurve, val fx: AudioXfx = AudioXfx.NONE) {
    /** How far into the transition (0..1) at [remainingMs] before the clip ends. */
    fun progress(remainingMs: Long): Float = if (remainingMs >= fromEndMs) 0f else (1f - remainingMs.toFloat() / fromEndMs.coerceAtLeast(1)).coerceIn(0f, 1f)
    fun gain(remainingMs: Long): Float {
        val elapsed = fromEndMs - remainingMs
        if (elapsed <= 0) return 1f
        val r = elapsed.toFloat() / lenMs.coerceAtLeast(1)
        return if (r >= 1f) 0f else curveIn(curve, 1f - r)
    }
    fun scaled(k: Float) = copy(fromEndMs = (fromEndMs * k).toLong(), lenMs = (lenMs * k).toLong().coerceAtLeast(1))
}

/** Fade at the start of the incoming clip: silent for [delayMs], then rises over [lenMs]. */
data class FadeIn(val delayMs: Long, val lenMs: Long, val curve: FadeCurve, val fx: AudioXfx = AudioXfx.NONE) {
    /** How much effect is left (1 → 0) at [tMs] into the clip. */
    fun left(tMs: Long): Float = (1f - tMs.toFloat() / (delayMs + lenMs).coerceAtLeast(1)).coerceIn(0f, 1f)
    fun gain(tMs: Long): Float {
        if (tMs < delayMs) return 0f
        val p = (tMs - delayMs).toFloat() / lenMs.coerceAtLeast(1)
        return if (p >= 1f) 1f else curveIn(curve, p)
    }
    fun scaled(k: Float) = copy(delayMs = (delayMs * k).toLong(), lenMs = (lenMs * k).toLong().coerceAtLeast(1))
}

/** Sound shaping for one cut, used by both the preview and the export. */
object AudioXfade {
    /**
     * Fade-out for the clip before the cut and fade-in for the clip after it.
     * With a video transition the sound works inside the overlap ([overlapMs]); on a plain cut
     * (no overlap) the clips can't play together, so the sound fades out and back in around the cut,
     * over [audioMs] in total.
     */
    fun shapes(t: AudioTransitionType, overlapMs: Long, audioMs: Long): Pair<FadeOut?, FadeIn?> {
        val w = when {
            overlapMs > 0 -> overlapMs
            t == AudioTransitionType.AUTO || t == AudioTransitionType.CUT -> return null to null
            else -> (audioMs / 2).coerceAtLeast(20)
        }
        val q = (w / 4).coerceAtLeast(1)
        val h = (w / 2).coerceAtLeast(1)
        return when (t) {
            AudioTransitionType.AUTO, AudioTransitionType.CONSTANT_GAIN -> FadeOut(w, w, FadeCurve.LINEAR) to FadeIn(0, w, FadeCurve.LINEAR)
            AudioTransitionType.CONSTANT_POWER -> FadeOut(w, w, FadeCurve.POWER) to FadeIn(0, w, FadeCurve.POWER)
            AudioTransitionType.EXPONENTIAL -> FadeOut(w, w, FadeCurve.EXP) to FadeIn(0, w, FadeCurve.EXP)
            AudioTransitionType.CUT -> FadeOut(h, 1, FadeCurve.LINEAR) to FadeIn(h, 1, FadeCurve.LINEAR)
            AudioTransitionType.DIP -> FadeOut(w, h, FadeCurve.POWER) to FadeIn(h, h, FadeCurve.POWER)
            AudioTransitionType.QUICK_OUT -> FadeOut(w, q, FadeCurve.LINEAR) to FadeIn(0, w, FadeCurve.POWER)
            AudioTransitionType.QUICK_IN -> FadeOut(w, w, FadeCurve.POWER) to FadeIn(0, q, FadeCurve.LINEAR)
            AudioTransitionType.S_CURVE -> FadeOut(w, w, FadeCurve.SMOOTH) to FadeIn(0, w, FadeCurve.SMOOTH)
            AudioTransitionType.PAUSE -> FadeOut(w, q, FadeCurve.POWER) to FadeIn(w * 3 / 5, (w * 2 / 5).coerceAtLeast(1), FadeCurve.POWER)
            AudioTransitionType.LATE_SWAP -> FadeOut(q, q, FadeCurve.POWER) to FadeIn(w - q, q, FadeCurve.POWER)
            AudioTransitionType.EARLY_SWAP -> FadeOut(w, q, FadeCurve.POWER) to FadeIn(0, q, FadeCurve.POWER)
            AudioTransitionType.SWELL -> FadeOut(w, w, FadeCurve.LINEAR) to FadeIn(0, w, FadeCurve.EXP)
            AudioTransitionType.UNDERWATER -> FadeOut(w, w, FadeCurve.POWER, AudioXfx.UNDERWATER) to FadeIn(0, w, FadeCurve.POWER, AudioXfx.UNDERWATER)
            AudioTransitionType.MUFFLE_OUT -> FadeOut(w, w, FadeCurve.POWER, AudioXfx.LOWPASS) to FadeIn(0, w, FadeCurve.POWER)
            AudioTransitionType.OPEN_UP -> FadeOut(w, w, FadeCurve.POWER) to FadeIn(0, w, FadeCurve.POWER, AudioXfx.LOWPASS)
            AudioTransitionType.MUFFLED_X -> FadeOut(w, w, FadeCurve.SMOOTH, AudioXfx.LOWPASS) to FadeIn(0, w, FadeCurve.SMOOTH, AudioXfx.LOWPASS)
            AudioTransitionType.THIN_OUT -> FadeOut(w, w, FadeCurve.POWER, AudioXfx.HIGHPASS) to FadeIn(0, w, FadeCurve.POWER)
            AudioTransitionType.ECHO_OUT -> FadeOut(w, w, FadeCurve.EXP, AudioXfx.ECHO) to FadeIn(0, w, FadeCurve.POWER)
            AudioTransitionType.ECHO_IN -> FadeOut(w, w, FadeCurve.POWER) to FadeIn(0, w, FadeCurve.POWER, AudioXfx.ECHO)
            AudioTransitionType.REVERB_WASH -> FadeOut(w, w, FadeCurve.POWER, AudioXfx.REVERB) to FadeIn(0, w, FadeCurve.POWER, AudioXfx.REVERB)
            AudioTransitionType.REVERSE_SWELL -> FadeOut(w, h, FadeCurve.POWER) to FadeIn(0, w, FadeCurve.EXP, AudioXfx.REVERB)
            AudioTransitionType.TAPE_STOP -> FadeOut(w, w, FadeCurve.LINEAR, AudioXfx.TAPE_STOP) to FadeIn(w * 3 / 5, (w * 2 / 5).coerceAtLeast(1), FadeCurve.POWER)
            AudioTransitionType.TAPE_START -> FadeOut(w, h, FadeCurve.POWER) to FadeIn(0, q, FadeCurve.POWER, AudioXfx.TAPE_START)
            AudioTransitionType.TAPE_BOTH -> FadeOut(w, w, FadeCurve.LINEAR, AudioXfx.TAPE_STOP) to FadeIn(h, h, FadeCurve.POWER, AudioXfx.TAPE_START)
            AudioTransitionType.STUTTER -> FadeOut(w, w, FadeCurve.LINEAR, AudioXfx.STUTTER) to FadeIn(h, h, FadeCurve.POWER)
            AudioTransitionType.GLITCH -> FadeOut(w, w, FadeCurve.POWER, AudioXfx.GLITCH) to FadeIn(0, w, FadeCurve.POWER, AudioXfx.GLITCH)
            AudioTransitionType.BITCRUSH -> FadeOut(w, w, FadeCurve.POWER, AudioXfx.BITCRUSH) to FadeIn(0, w, FadeCurve.POWER)
            AudioTransitionType.TELEPHONE -> FadeOut(w, w, FadeCurve.POWER, AudioXfx.TELEPHONE) to FadeIn(0, w, FadeCurve.POWER)
            AudioTransitionType.RADIO -> FadeOut(w, w, FadeCurve.POWER, AudioXfx.RADIO) to FadeIn(0, w, FadeCurve.POWER, AudioXfx.RADIO)
            AudioTransitionType.PITCH_DIVE -> FadeOut(w, w, FadeCurve.POWER, AudioXfx.PITCH_DOWN) to FadeIn(0, w, FadeCurve.POWER)
            AudioTransitionType.PITCH_RISE -> FadeOut(w, w, FadeCurve.POWER, AudioXfx.PITCH_UP) to FadeIn(0, w, FadeCurve.POWER)
            AudioTransitionType.TREMOLO -> FadeOut(w, w, FadeCurve.POWER, AudioXfx.TREMOLO) to FadeIn(0, w, FadeCurve.POWER)
            AudioTransitionType.CHOPPER -> FadeOut(w, w, FadeCurve.LINEAR, AudioXfx.CHOP) to FadeIn(0, w, FadeCurve.POWER)
            AudioTransitionType.FLANGER -> FadeOut(w, w, FadeCurve.POWER, AudioXfx.FLANGER) to FadeIn(0, w, FadeCurve.POWER, AudioXfx.FLANGER)
            AudioTransitionType.WOBBLE -> FadeOut(w, w, FadeCurve.POWER, AudioXfx.WOBBLE) to FadeIn(0, w, FadeCurve.POWER)
            AudioTransitionType.RISER -> FadeOut(w, w, FadeCurve.POWER, AudioXfx.RISER) to FadeIn(0, q, FadeCurve.POWER)
            AudioTransitionType.DRIVE -> FadeOut(w, w, FadeCurve.POWER, AudioXfx.DRIVE) to FadeIn(0, w, FadeCurve.POWER)
            AudioTransitionType.SPIN_BACK -> FadeOut(w, w, FadeCurve.LINEAR, AudioXfx.SPIN_BACK) to FadeIn(h, h, FadeCurve.POWER)
            AudioTransitionType.VINYL_BRAKE -> FadeOut(w, w, FadeCurve.LINEAR, AudioXfx.VINYL_BRAKE) to FadeIn(w * 3 / 5, (w * 2 / 5).coerceAtLeast(1), FadeCurve.POWER)
            AudioTransitionType.PUMP -> FadeOut(w, w, FadeCurve.LINEAR, AudioXfx.PUMP) to FadeIn(0, w, FadeCurve.POWER)
            AudioTransitionType.RING -> FadeOut(w, w, FadeCurve.POWER, AudioXfx.RING) to FadeIn(0, w, FadeCurve.POWER)
            AudioTransitionType.AUTOPAN -> FadeOut(w, w, FadeCurve.POWER, AudioXfx.AUTOPAN) to FadeIn(0, w, FadeCurve.POWER, AudioXfx.AUTOPAN)
            AudioTransitionType.SLAP -> FadeOut(w, w, FadeCurve.EXP, AudioXfx.SLAP) to FadeIn(0, w, FadeCurve.POWER)
            AudioTransitionType.LOFI -> FadeOut(w, w, FadeCurve.POWER, AudioXfx.LOFI) to FadeIn(0, w, FadeCurve.POWER, AudioXfx.LOFI)
            AudioTransitionType.KARAOKE -> FadeOut(w, w, FadeCurve.POWER, AudioXfx.KARAOKE) to FadeIn(0, w, FadeCurve.POWER)
            AudioTransitionType.AIRY -> FadeOut(w, w, FadeCurve.POWER, AudioXfx.AIRY) to FadeIn(0, w, FadeCurve.POWER, AudioXfx.AIRY)
            AudioTransitionType.GATE -> FadeOut(w, w, FadeCurve.LINEAR, AudioXfx.GATE) to FadeIn(0, w, FadeCurve.POWER)
            AudioTransitionType.DOPPLER -> FadeOut(w, w, FadeCurve.EXP, AudioXfx.DOPPLER) to FadeIn(0, w, FadeCurve.POWER)
            AudioTransitionType.FILTER_SWEEP -> FadeOut(w, w, FadeCurve.POWER, AudioXfx.BANDSWEEP) to FadeIn(0, w, FadeCurve.POWER)
            AudioTransitionType.VINYL -> FadeOut(w, w, FadeCurve.POWER, AudioXfx.VINYL) to FadeIn(0, w, FadeCurve.POWER)
            AudioTransitionType.RADIO_IN -> FadeOut(w, w, FadeCurve.POWER) to FadeIn(0, w, FadeCurve.POWER, AudioXfx.RADIO)
            AudioTransitionType.PHONE_IN -> FadeOut(w, w, FadeCurve.POWER) to FadeIn(0, w, FadeCurve.POWER, AudioXfx.TELEPHONE)
            AudioTransitionType.STUTTER_IN -> FadeOut(w, h, FadeCurve.POWER) to FadeIn(0, w, FadeCurve.LINEAR, AudioXfx.STUTTER)
            AudioTransitionType.PITCH_SLIDE_IN -> FadeOut(w, w, FadeCurve.POWER) to FadeIn(0, w, FadeCurve.POWER, AudioXfx.PITCH_DOWN)
            AudioTransitionType.SHIMMER -> FadeOut(w, w, FadeCurve.EXP, AudioXfx.SHIMMER) to FadeIn(0, w, FadeCurve.POWER)
            AudioTransitionType.FREEZE -> FadeOut(w, w, FadeCurve.LINEAR, AudioXfx.FREEZE) to FadeIn(h, h, FadeCurve.POWER)
            AudioTransitionType.SUB_DROP -> FadeOut(w, w, FadeCurve.POWER, AudioXfx.SUBDROP) to FadeIn(0, q, FadeCurve.POWER)
            AudioTransitionType.DOWNLIFTER -> FadeOut(w, w, FadeCurve.POWER) to FadeIn(0, w, FadeCurve.POWER, AudioXfx.RISER)
            AudioTransitionType.CHORUS -> FadeOut(w, w, FadeCurve.POWER, AudioXfx.CHORUS) to FadeIn(0, w, FadeCurve.POWER, AudioXfx.CHORUS)
            AudioTransitionType.CATHEDRAL -> FadeOut(w, w, FadeCurve.POWER, AudioXfx.HALL) to FadeIn(0, w, FadeCurve.POWER, AudioXfx.HALL)
            AudioTransitionType.RISE_DROP -> FadeOut(w, w, FadeCurve.POWER, AudioXfx.PITCH_UP) to FadeIn(0, w, FadeCurve.POWER, AudioXfx.LOWPASS)
            AudioTransitionType.SURFACE -> FadeOut(w, w, FadeCurve.POWER) to FadeIn(0, w, FadeCurve.POWER, AudioXfx.UNDERWATER)
            AudioTransitionType.PHASER_SWEEP -> FadeOut(w, w, FadeCurve.POWER, AudioXfx.PHASER) to FadeIn(0, w, FadeCurve.POWER, AudioXfx.PHASER)
            AudioTransitionType.PING_PONG -> FadeOut(w, w, FadeCurve.EXP, AudioXfx.PINGPONG) to FadeIn(0, w, FadeCurve.POWER)
            AudioTransitionType.MEGAPHONE -> FadeOut(w, w, FadeCurve.POWER, AudioXfx.MEGAPHONE) to FadeIn(0, w, FadeCurve.POWER)
            AudioTransitionType.NARROW_OUT -> FadeOut(w, w, FadeCurve.POWER, AudioXfx.NARROW) to FadeIn(0, w, FadeCurve.POWER)
            AudioTransitionType.WIDE_IN -> FadeOut(w, w, FadeCurve.POWER) to FadeIn(0, w, FadeCurve.POWER, AudioXfx.WIDEN)
            AudioTransitionType.HARMONY_RISE -> FadeOut(w, w, FadeCurve.POWER, AudioXfx.HARMONY) to FadeIn(0, w, FadeCurve.POWER)
            AudioTransitionType.OCTAVE_DROP -> FadeOut(w, w, FadeCurve.POWER, AudioXfx.OCTAVER) to FadeIn(0, w, FadeCurve.POWER)
            AudioTransitionType.REVERSE_OUT -> FadeOut(w, w, FadeCurve.POWER, AudioXfx.REVERSE) to FadeIn(0, w, FadeCurve.POWER)
            AudioTransitionType.CRUSH_IN -> FadeOut(w, w, FadeCurve.POWER) to FadeIn(0, w, FadeCurve.POWER, AudioXfx.BITCRUSH)
            AudioTransitionType.BASS_IN -> FadeOut(w, w, FadeCurve.POWER) to FadeIn(0, w, FadeCurve.POWER, AudioXfx.HIGHPASS)
            AudioTransitionType.THIN_X -> FadeOut(w, w, FadeCurve.SMOOTH, AudioXfx.HIGHPASS) to FadeIn(0, w, FadeCurve.SMOOTH, AudioXfx.HIGHPASS)
            AudioTransitionType.FLUTTER_IN -> FadeOut(w, w, FadeCurve.POWER) to FadeIn(0, w, FadeCurve.POWER, AudioXfx.TREMOLO)
            AudioTransitionType.GATE_IN -> FadeOut(w, w, FadeCurve.POWER) to FadeIn(0, w, FadeCurve.POWER, AudioXfx.GATE)
            AudioTransitionType.CHOP_IN -> FadeOut(w, w, FadeCurve.POWER) to FadeIn(0, w, FadeCurve.POWER, AudioXfx.CHOP)
            AudioTransitionType.WOBBLE_IN -> FadeOut(w, w, FadeCurve.POWER) to FadeIn(0, w, FadeCurve.POWER, AudioXfx.WOBBLE)
            AudioTransitionType.GRIT_IN -> FadeOut(w, w, FadeCurve.POWER) to FadeIn(0, w, FadeCurve.POWER, AudioXfx.DRIVE)
            AudioTransitionType.METAL_IN -> FadeOut(w, w, FadeCurve.POWER) to FadeIn(0, w, FadeCurve.POWER, AudioXfx.RING)
            AudioTransitionType.SHIMMER_IN -> FadeOut(w, w, FadeCurve.POWER) to FadeIn(0, w, FadeCurve.EXP, AudioXfx.SHIMMER)
            AudioTransitionType.HALL_TAIL -> FadeOut(w, w, FadeCurve.EXP, AudioXfx.HALL) to FadeIn(0, w, FadeCurve.POWER)
            AudioTransitionType.SLAP_IN -> FadeOut(w, w, FadeCurve.POWER) to FadeIn(0, w, FadeCurve.POWER, AudioXfx.SLAP)
            AudioTransitionType.PUMP_X -> FadeOut(w, w, FadeCurve.POWER, AudioXfx.PUMP) to FadeIn(0, w, FadeCurve.POWER, AudioXfx.PUMP)
            AudioTransitionType.PHONE_CALL -> FadeOut(w, w, FadeCurve.POWER, AudioXfx.TELEPHONE) to FadeIn(0, w, FadeCurve.POWER, AudioXfx.TELEPHONE)
            AudioTransitionType.RECORD_IN -> FadeOut(w, w, FadeCurve.POWER) to FadeIn(0, w, FadeCurve.POWER, AudioXfx.VINYL)
            AudioTransitionType.FLY_IN -> FadeOut(w, w, FadeCurve.POWER) to FadeIn(0, w, FadeCurve.POWER, AudioXfx.DOPPLER)
            AudioTransitionType.FILTER_OPEN -> FadeOut(w, w, FadeCurve.POWER) to FadeIn(0, w, FadeCurve.POWER, AudioXfx.BANDSWEEP)
            AudioTransitionType.FREEZE_ECHO -> FadeOut(w, w, FadeCurve.LINEAR, AudioXfx.FREEZE) to FadeIn(0, w, FadeCurve.POWER, AudioXfx.ECHO)
            AudioTransitionType.TAPE_ECHO -> FadeOut(w, w, FadeCurve.LINEAR, AudioXfx.TAPE_STOP) to FadeIn(0, w, FadeCurve.POWER, AudioXfx.ECHO)
            AudioTransitionType.STUTTER_X -> FadeOut(w, w, FadeCurve.LINEAR, AudioXfx.STUTTER) to FadeIn(0, w, FadeCurve.LINEAR, AudioXfx.STUTTER)
            AudioTransitionType.BUILD_BOOM -> FadeOut(w, w, FadeCurve.POWER, AudioXfx.RISER) to FadeIn(0, w, FadeCurve.POWER, AudioXfx.SUBDROP)
            AudioTransitionType.MUFFLE_ECHO -> FadeOut(w, w, FadeCurve.POWER, AudioXfx.LOWPASS) to FadeIn(0, w, FadeCurve.POWER, AudioXfx.ECHO)
            AudioTransitionType.VOICE_IN -> FadeOut(w, w, FadeCurve.POWER) to FadeIn(0, w, FadeCurve.POWER, AudioXfx.KARAOKE)
            AudioTransitionType.AIRY_OUT -> FadeOut(w, w, FadeCurve.POWER, AudioXfx.AIRY) to FadeIn(0, w, FadeCurve.POWER)
            AudioTransitionType.LOFI_OUT -> FadeOut(w, w, FadeCurve.POWER, AudioXfx.LOFI) to FadeIn(0, w, FadeCurve.POWER)
            AudioTransitionType.LUSH_IN -> FadeOut(w, w, FadeCurve.POWER) to FadeIn(0, w, FadeCurve.POWER, AudioXfx.CHORUS)
            AudioTransitionType.REVERSE_SWELL_X -> FadeOut(w, w, FadeCurve.POWER, AudioXfx.REVERSE) to FadeIn(0, w, FadeCurve.EXP, AudioXfx.REVERB)
            AudioTransitionType.MEGAPHONE_IN -> FadeOut(w, w, FadeCurve.POWER) to FadeIn(0, w, FadeCurve.POWER, AudioXfx.MEGAPHONE)
            AudioTransitionType.REWIND_RESTART -> FadeOut(w, w, FadeCurve.LINEAR, AudioXfx.SPIN_BACK) to FadeIn(0, w, FadeCurve.POWER, AudioXfx.TAPE_START)
        }
    }

    /** The sound shaping for the cut after clip [i] (null pair when there is no next clip). */
    fun forCut(clips: List<Clip>, i: Int): Pair<FadeOut?, FadeIn?> {
        val a = clips.getOrNull(i) ?: return null to null
        if (i + 1 > clips.lastIndex) return null to null
        return shapes(a.transition.audioType, overlapMs(clips, i), a.transition.audioLen)
    }
}

/** Overlap (ms) between clip [i] and clip [i + 1]. */
fun overlapMs(clips: List<Clip>, i: Int): Long {
    val a = clips.getOrNull(i) ?: return 0
    val b = clips.getOrNull(i + 1) ?: return 0
    if (a.transition.type == TransitionType.NONE) return 0
    return a.transition.durationMs.coerceAtMost((minOf(a.lengthMs, b.lengthMs) * 0.45).toLong()).coerceAtLeast(0)
}

/** Where each clip starts on the timeline (transitions overlap neighbouring clips). */
fun clipStarts(clips: List<Clip>): LongArray {
    val out = LongArray(clips.size)
    for (i in 1 until clips.size) out[i] = out[i - 1] + clips[i - 1].lengthMs - overlapMs(clips, i - 1)
    return out
}

fun timelineLength(clips: List<Clip>): Long {
    if (clips.isEmpty()) return 0
    return clipStarts(clips).last() + clips.last().lengthMs
}

/** Clip under a timeline time (the later clip wins inside a transition) and the offset into it (timeline ms). */
fun clipAt(clips: List<Clip>, ms: Long): Pair<Int, Long> {
    if (clips.isEmpty()) return 0 to 0
    val s = clipStarts(clips)
    var i = 0
    while (i + 1 < clips.size && ms >= s[i + 1]) i++
    return i to (ms - s[i]).coerceIn(0, clips[i].lengthMs)
}

object TransitionMath {
    private fun ss(a: Float, b: Float, x: Float): Float { val t = ((x - a) / (b - a)).coerceIn(0f, 1f); return t * t * (3 - 2 * t) }
    fun ease(x: Float): Float = if (x < 0.5f) 4 * x * x * x else 1 - Math.pow((-2.0 * x + 2), 3.0).toFloat() / 2

    /** How much of the incoming clip shows for uniform transitions (same as the shader). */
    fun uniformReveal(t: TransitionType, p: Float): Float = when (t.id.toInt()) {
        1 -> p
        2, 3 -> ss(0.48f, 0.52f, p)
        4 -> ss(0.35f, 0.65f, p)
        5, 6 -> ss(0.3f, 0.7f, p)
        7 -> ss(0.1f, 0.9f, p)
        36, 37, 38 -> ss(0.4f, 0.6f, p)
        41 -> ss(0.45f, 0.55f, p)
        42, 43, 44, 45 -> ss(0.25f, 0.75f, p)
        51, 52, 59, 63, 64 -> ss(0.4f, 0.6f, p)
        54 -> ss(0.45f, 0.55f, p)
        55, 56 -> ss(0.49f, 0.51f, p)
        62 -> ss(0.35f, 0.65f, p)
        66 -> ss(0.42f, 0.58f, p)
        67, 74 -> ss(0.35f, 0.65f, p)
        68, 69 -> ss(0.45f, 0.55f, p)
        71, 75 -> ss(0.4f, 0.6f, p)
        76, 88 -> ss(0.3f, 0.7f, p)
        82, 91 -> ss(0.4f, 0.6f, p)
        87 -> ss(0.49f, 0.51f, p)
        89 -> ss(0.48f, 0.52f, p)
        95, 98, 105 -> ss(0.45f, 0.55f, p)
        99, 104 -> ss(0.49f, 0.51f, p)
        100 -> ss(0.35f, 0.65f, p)
        106, 111, 114, 116 -> ss(0.4f, 0.6f, p)
        108, 117 -> ss(0.3f, 0.7f, p)
        109 -> ss(0.49f, 0.51f, p)
        110, 120 -> ss(0.45f, 0.55f, p)
        113 -> ss(0.3f, 0.7f, p)
        122, 125, 126, 130 -> ss(0.45f, 0.55f, p)
        124 -> ss(0.3f, 0.7f, p)
        131, 132, 133 -> ss(0.48f, 0.52f, p)
        136 -> ss(0.48f, 0.52f, p)
        else -> 1f
    }

    /** Movement (fraction of the frame, x right, y down) for push / whip transitions. */
    fun move(t: TransitionType, p: Float, incoming: Boolean): Pair<Float, Float> {
        val e = if (t == TransitionType.WHIP_LEFT || t == TransitionType.WHIP_RIGHT) ease(ease(p)) else ease(p)
        return when (t) {
            TransitionType.PUSH_LEFT, TransitionType.WHIP_LEFT, TransitionType.SLIDE_BLUR -> (if (incoming) 1 - e else -e) to 0f
            TransitionType.PUSH_RIGHT, TransitionType.WHIP_RIGHT -> (if (incoming) -(1 - e) else e) to 0f
            TransitionType.PUSH_UP, TransitionType.FILM_GATE -> 0f to (if (incoming) 1 - e else -e)
            TransitionType.PUSH_DOWN -> 0f to (if (incoming) -(1 - e) else e)
            else -> 0f to 0f
        }
    }

    /**
     * Shared transition shader body (GLSL ES 1.0 and AGSL both accept it). Needs `tex(uv)` and
     * uniforms uType, uP (0..1), uSide (0 outgoing, 1 incoming), uMode (0 opaque, 1 alpha = reveal,
     * 2 alpha = 1 - reveal) and uSize (pixels). uv has its origin bottom-left.
     */
    const val CORE = """
        float rnd(vec2 c) { return fract(sin(dot(c, vec2(12.9898, 78.233))) * 43758.5453); }
        float vnoise(vec2 q) {
            vec2 i = floor(q); vec2 f = fract(q); f = f * f * (3.0 - 2.0 * f);
            float a = rnd(i); float b = rnd(i + vec2(1.0, 0.0)); float c = rnd(i + vec2(0.0, 1.0)); float d = rnd(i + vec2(1.0, 1.0));
            return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
        }
        float fbm(vec2 q) { float v = 0.0; float a = 0.5; for (int i = 0; i < 5; i++) { v += a * vnoise(q); q *= 2.0; a *= 0.5; } return v; }
        bool isT(float id) { return abs(uType - id) < 0.5; }
        float easeF(float x) { if (x < 0.5) return 4.0 * x * x * x; float y = -2.0 * x + 2.0; return 1.0 - y * y * y / 2.0; }
        float sstep(float a, float b, float x) { return smoothstep(a, b, x); }
        float cover(float v, float e, float s) { return 1.0 - smoothstep(e - s, e + s, v); }
        vec4 mtex(vec2 uv) { vec2 m = abs(mod(uv + 1.0, 2.0) - 1.0); return tex(m); }


        // ---------------- Cinematic set (original shaders) ----------------
        float lum(vec3 v) { return dot(v, vec3(0.2126, 0.7152, 0.0722)); }
        float burnField(vec2 uv, float aspect) { vec2 q = uv * vec2(aspect, 1.0); return length(q - vec2(0.32 * aspect, 0.62)) * 0.75 + fbm(q * 2.6) * 0.6; }
        float burnEdge(float p) { return mix(-0.05, 1.75, p); }
        vec2 moshFlow(vec2 uv, float k) { vec2 b = floor(uv * vec2(20.0, 12.0)); return (vec2(rnd(b + 0.7), rnd(b + 5.1)) - 0.5) * 0.09 * k; }
        float moshMask(vec2 uv, float p) {
            float k = 1.0 - abs(2.0 * p - 1.0);
            vec2 b = floor((uv - moshFlow(uv, k)) * vec2(20.0, 12.0));
            return step(rnd(b + 9.0) * 0.7 + (1.0 - uv.y) * 0.25 + 0.02, p);
        }
        float smokeField(vec2 uv, float aspect, float p) { return fbm(uv * vec2(aspect, 1.0) * 2.2 + vec2(p * 0.8, p * 0.3)) * 0.7 + uv.x * 0.3; }
        float emberField(vec2 uv, float aspect) {
            vec2 g = floor(uv * uSize / 2.5);
            return uv.x * 0.7 + fbm(uv * vec2(aspect, 1.0) * 3.0) * 0.3 + (rnd(g) - 0.5) * 0.06;
        }
        // Box (cube) face hit: xy = face uv, z = 1 when the ray hits it.
        vec3 boxFace(vec2 uv0, float aspect, float p, float rightFace) {
            float ang = easeF(p) * 1.5707963; float k = 1.0 - abs(2.0 * p - 1.0);
            vec3 sp = vec3((uv0.x * 2.0 - 1.0) * aspect, uv0.y * 2.0 - 1.0, 0.0);
            vec3 o = vec3(0.0, 0.0, 3.2);
            vec3 d = sp - o;
            vec3 cen = vec3(0.0, 0.0, -aspect - k * 0.8);
            float cs = cos(ang); float sn = sin(ang);
            vec3 ol = o - cen;
            vec3 ol2 = vec3(ol.x * cs + ol.z * sn, ol.y, -ol.x * sn + ol.z * cs);
            vec3 dl = vec3(d.x * cs + d.z * sn, d.y, -d.x * sn + d.z * cs);
            float t = 0.0; vec2 f = vec2(-1.0);
            if (rightFace > 0.5) {
                if (dl.x < -1e-5) { t = (aspect - ol2.x) / dl.x; vec3 h = ol2 + dl * t; f = vec2(-h.z / aspect * 0.5 + 0.5, h.y * 0.5 + 0.5); }
            } else {
                if (dl.z < -1e-5) { t = (aspect - ol2.z) / dl.z; vec3 h = ol2 + dl * t; f = vec2(h.x / aspect * 0.5 + 0.5, h.y * 0.5 + 0.5); }
            }
            float hit = step(0.0, f.x) * step(f.x, 1.0) * step(0.0, f.y) * step(f.y, 1.0) * step(0.0, t);
            return vec3(f, hit);
        }
        // Card flip: xy = card uv (unmirrored on the back), z = hit.
        vec3 flipFace(vec2 uv0, float aspect, float p) {
            float a = easeF(p) * 3.14159265; float k = 1.0 - abs(2.0 * p - 1.0);
            vec3 sp = vec3((uv0.x * 2.0 - 1.0) * aspect, uv0.y * 2.0 - 1.0, 0.0);
            vec3 o = vec3(0.0, 0.0, 3.5);
            vec3 d = sp - o;
            vec3 cen = vec3(0.0, 0.0, -k * 1.2);
            float cs = cos(a); float sn = sin(a);
            vec3 ol = o - cen;
            vec3 ol2 = vec3(ol.x * cs + ol.z * sn, ol.y, -ol.x * sn + ol.z * cs);
            vec3 dl = vec3(d.x * cs + d.z * sn, d.y, -d.x * sn + d.z * cs);
            float hit = 0.0; vec2 f = vec2(-1.0);
            if (abs(dl.z) > 1e-5) {
                float t = -ol2.z / dl.z; vec3 h = ol2 + dl * t;
                f = vec2(h.x / aspect * 0.5 + 0.5, h.y * 0.5 + 0.5);
                hit = step(0.0, f.x) * step(f.x, 1.0) * step(0.0, f.y) * step(f.y, 1.0) * step(0.0, t);
                if (p >= 0.5) f.x = 1.0 - f.x;
            }
            return vec3(f, hit);
        }
        vec2 shardC(vec2 cl) { return (cl + 0.2 + 0.6 * vec2(rnd(cl + 3.1), rnd(cl + 7.7))) / vec2(6.0, 4.0); }
        float shardT(vec2 cl, float p) { vec2 cc = shardC(cl); float dly = length((cc - vec2(0.5, 0.55)) * vec2(1.6, 1.0)) * 0.55; return clamp((p * 1.35 - dly) / 0.8, 0.0, 1.0); }
        vec3 shardNear(vec2 q) {
            vec2 g = vec2(6.0, 4.0); vec2 b = floor(q * g);
            vec2 asp = vec2(uSize.x / uSize.y, 1.0);
            float d1 = 9.0; float d2 = 9.0; vec2 best = b;
            for (int j = -1; j <= 1; j++) {
                for (int i = -1; i <= 1; i++) {
                    vec2 cl = clamp(b + vec2(float(i), float(j)), vec2(0.0), g - 1.0);
                    float d = length((q - shardC(cl)) * asp);
                    if (d < d1) { d2 = d1; d1 = d; best = cl; }
                    else if (d < d2 && length(cl - best) > 0.5) { d2 = d; }
                }
            }
            return vec3(best, d2 - d1);
        }
        // Shatter: xy = where in the outgoing picture, z = edge distance, w = 1 + fall (0 = no shard here).
        vec4 shatter(vec2 uv, float p) {
            vec4 res = vec4(0.0);
            float aspect = uSize.x / uSize.y;
            for (int j = 0; j < 4; j++) {
                for (int i = 0; i < 6; i++) {
                    vec2 cl = vec2(float(i), float(j));
                    if (res.w < 0.5) {
                        float t = shardT(cl, p); vec2 cen = shardC(cl);
                        vec2 off = vec2((rnd(cl + 1.3) - 0.5) * 0.5 * t, -1.6 * t * t + 0.12 * t);
                        float ang = (rnd(cl + 4.0) - 0.5) * 2.5 * t;
                        float cs = cos(ang); float sn = sin(ang);
                        vec2 v = (uv - off - cen) * vec2(aspect, 1.0);
                        v = vec2(v.x * cs - v.y * sn, v.x * sn + v.y * cs) / vec2(aspect, 1.0);
                        vec2 q = cen + v / (1.0 - 0.35 * t);
                        if (q.x >= 0.0 && q.x <= 1.0 && q.y >= 0.0 && q.y <= 1.0) {
                            vec3 nb = shardNear(q);
                            if (length(nb.xy - cl) < 0.5) res = vec4(q, nb.z, 1.0 + t);
                        }
                    }
                }
            }
            return res;
        }

        float shineX(vec2 uv, float aspect) { return (uv.x * aspect + (1.0 - uv.y) * 0.5) / (aspect + 0.5); }
        vec2 rot2(vec2 q, float a) { float cs = cos(a); float sn = sin(a); return vec2(q.x * cs - q.y * sn, q.x * sn + q.y * cs); }

        float splashField(vec2 uv, float aspect) {
            vec2 q = uv * vec2(aspect, 1.0);
            float n = fbm(q * 3.0) * 0.28;
            float d = length(q - vec2(0.30 * aspect, 0.42));
            d = min(d, length(q - vec2(0.72 * aspect, 0.66)) * 1.1);
            d = min(d, length(q - vec2(0.52 * aspect, 0.18)) * 1.25);
            d = min(d, length(q - vec2(0.18 * aspect, 0.82)) * 1.35);
            return d + n;
        }
        float hexReveal(vec2 uv, float aspect, float p) {
            vec2 q = (uv - 0.5) * vec2(aspect, 1.0) * 9.0;
            vec2 r = vec2(1.0, 1.7320508); vec2 h = r * 0.5;
            vec2 a = mod(q, r) - h; vec2 b = mod(q - h, r) - h;
            vec2 gv = dot(a, a) < dot(b, b) ? a : b;
            vec2 id = q - gv;
            float dly = length(id) / (9.0 * length(vec2(aspect, 1.0)) * 0.5) * 0.7;
            float sz = clamp((p * 1.35 - dly) * 3.0, 0.0, 1.0) * 0.58;
            vec2 ag = abs(gv);
            float hd = max(dot(ag, normalize(vec2(1.0, 1.7320508))), ag.x);
            return 1.0 - smoothstep(sz - 0.02, sz + 0.02, hd);
        }

        float revealC(vec2 uv) {
            float p = uP; float aspect = uSize.x / uSize.y;
            if (isT(50.0)) return 1.0 - smoothstep(-0.015, 0.015, burnField(uv, aspect) - burnEdge(p));
            if (isT(51.0) || isT(52.0) || isT(59.0) || isT(63.0) || isT(64.0)) return sstep(0.4, 0.6, p);
            if (isT(54.0)) return sstep(0.45, 0.55, p);
            if (isT(55.0) || isT(56.0)) return sstep(0.49, 0.51, p);
            if (isT(62.0)) return sstep(0.35, 0.65, p);
            if (isT(53.0)) return moshMask(uv, p);
            if (isT(57.0)) return smoothstep(-0.18, 0.18, mix(-0.35, 1.35, p) - smokeField(uv, aspect, p));
            if (isT(58.0)) return 1.0 - step(0.5, shatter(uv, p).w);
            if (isT(60.0)) return boxFace(uv, aspect, p, 1.0).z;
            if (isT(61.0)) return step(0.5, p) * flipFace(uv, aspect, p).z;
            if (isT(65.0)) return step(emberField(uv, aspect), mix(-0.1, 1.1, p));
            if (isT(66.0)) return sstep(0.42, 0.58, p);
            if (isT(67.0) || isT(74.0)) return sstep(0.35, 0.65, p);
            if (isT(68.0) || isT(69.0)) return sstep(0.45, 0.55, p);
            if (isT(70.0)) { float th = mix(1.15, -0.15, p); return smoothstep(th - 0.12, th + 0.12, lum(tex(uv).rgb)); }
            if (isT(71.0) || isT(75.0)) return sstep(0.4, 0.6, p);
            if (isT(72.0)) return cover(shineX(uv, aspect), mix(-0.15, 1.15, p), 0.035);
            if (isT(76.0) || isT(88.0)) return sstep(0.3, 0.7, p);
            if (isT(77.0)) return 1.0 - smoothstep(-0.012, 0.012, splashField(uv, aspect) - p * 1.25);
            if (isT(78.0)) return hexReveal(uv, aspect, p);
            if (isT(79.0)) { vec2 q = (uv - 0.5) * vec2(aspect, 1.0) / max(p * 1.7, 0.001) * 1.25; q.y += 0.25; float r2 = dot(q, q) - 1.0; float f = r2 * r2 * r2 - q.x * q.x * q.y * q.y * q.y; return 1.0 - smoothstep(-0.03, 0.03, f); }
            if (isT(80.0)) { vec2 q = (uv - 0.5) * vec2(aspect, 1.0); float a = atan(q.y, q.x) + 1.5707963; float rs = p * 1.5 * (0.62 + 0.38 * cos(5.0 * a)); return 1.0 - smoothstep(rs - 0.01, rs + 0.01, length(q)); }
            if (isT(81.0)) { vec2 q = abs(uv - 0.5) * vec2(aspect, 1.0); return cover(q.x + q.y, p * (aspect * 0.5 + 0.5) * 1.05, 0.01); }
            if (isT(82.0) || isT(91.0)) return sstep(0.4, 0.6, p);
            if (isT(83.0)) return cover(uv.x - sin(uv.y * 12.0 + p * 6.0) * 0.04, mix(-0.1, 1.1, p), 0.015);
            if (isT(84.0)) { float row = floor(uv.y * 8.0); float e = mix(-0.05, 1.05, p); return mod(row, 2.0) < 0.5 ? cover(uv.x, e, 0.01) : cover(1.0 - uv.x, e, 0.01); }
            if (isT(85.0)) { vec2 cl = floor(uv * vec2(10.0, 6.0)); float dly = (cl.x + cl.y) / 14.0 * 0.6 + rnd(cl) * 0.2; return clamp((p - dly) / 0.15, 0.0, 1.0); }
            if (isT(86.0)) { float r = length((uv - 0.5) * vec2(aspect, 1.0)); float R = p * (length(vec2(aspect, 1.0)) * 0.5 + 0.05); return 1.0 - smoothstep(R - 0.015, R + 0.015, r); }
            if (isT(87.0)) return sstep(0.49, 0.51, p);
            if (isT(89.0)) return sstep(0.48, 0.52, p);
            if (isT(90.0)) { vec2 g = vec2(12.0 * aspect, 12.0); vec2 cl = floor(uv * g); vec2 lc = fract(uv * g) - 0.5; float dly = rnd(cl + 3.0) * 0.45 + uv.x * 0.25; float rad = clamp((p * 1.4 - dly) * 2.2, 0.0, 0.75); return 1.0 - smoothstep(rad - 0.04, rad, length(lc)); }
            if (isT(92.0)) { float e = easeF(p); return 1.0 - smoothstep(e * 0.5 - 0.005, e * 0.5 + 0.005, abs(uv.y - 0.5)); }
            if (isT(93.0)) { vec2 q = (uv - 0.5) * vec2(aspect, 1.0); float f = fract((atan(q.y, q.x) / 6.2831853 + 0.5) * 6.0); return cover(f, mix(-0.02, 1.02, p), 0.01); }
            if (isT(95.0) || isT(98.0) || isT(105.0)) return sstep(0.45, 0.55, p);
            if (isT(99.0) || isT(104.0)) return sstep(0.49, 0.51, p);
            if (isT(100.0)) return sstep(0.35, 0.65, p);
            if (isT(106.0) || isT(111.0) || isT(114.0) || isT(116.0)) return sstep(0.4, 0.6, p);
            if (isT(108.0) || isT(113.0) || isT(117.0)) return sstep(0.3, 0.7, p);
            if (isT(109.0)) return sstep(0.49, 0.51, p);
            if (isT(122.0) || isT(125.0) || isT(126.0) || isT(130.0)) return sstep(0.45, 0.55, p);
            if (isT(124.0)) return sstep(0.3, 0.7, p);
            if (isT(131.0) || isT(132.0) || isT(133.0) || isT(136.0)) return sstep(0.48, 0.52, p);
            if (isT(123.0)) return cover(1.0 - uv.y, mix(-0.1, 1.1, p), 0.08);
            if (isT(127.0)) { float row = floor(uv.y * 6.0); float x = mod(row, 2.0) < 0.5 ? uv.x : 1.0 - uv.x; float rag = (fbm(vec2(uv.x * 30.0, uv.y * 80.0)) - 0.5) * 0.06; float dly = row / 6.0 * 0.5; return cover(x + rag, (p - dly) * 2.2 - 0.1, 0.01); }
            if (isT(128.0)) { float tear = uv.x + (fbm(vec2(uv.y * 14.0, 3.0)) - 0.5) * 0.12 + (rnd(vec2(floor(uv.y * 90.0), 1.0)) - 0.5) * 0.01; return cover(tear, mix(-0.1, 1.15, p), 0.004); }
            if (isT(129.0)) { vec2 q = (uv - 0.5) * vec2(aspect, 1.0); float a = atan(q.y, q.x); float rs = p * 1.5 * (0.7 + 0.3 * step(0.5, fract(a / 6.2831853 * 14.0))); return 1.0 - step(rs, length(q)); }
            if (isT(134.0)) { float d = (uv.x + (1.0 - uv.y) * 0.35) / 1.35; return cover(1.0 - d, mix(-0.05, 1.05, p), 0.004); }
            if (isT(135.0)) return cover(uv.x, mix(-0.15, 1.15, p), 0.1);
            if (isT(110.0) || isT(120.0)) return sstep(0.45, 0.55, p);
            if (isT(107.0)) return cover(uv.x + (uv.y - 0.5) * 0.25, mix(-0.25, 1.25, p), 0.03);
            if (isT(112.0)) return smoothstep(-0.12, 0.12, mix(-0.3, 1.4, p) - ((1.0 - uv.y) + fbm(uv * vec2(aspect, 1.0) * 3.0 + vec2(p, 0.0)) * 0.45));
            if (isT(115.0)) return 1.0 - smoothstep(-0.01, 0.01, uv.y - (mix(-0.2, 1.25, p) + (fbm(vec2(uv.x * aspect * 4.0, p * 3.0)) - 0.5) * 0.18));
            if (isT(118.0)) { float r = length((uv - 0.5) * vec2(aspect, 1.0)); float R = p * (length(vec2(aspect, 1.0)) * 0.5 + 0.02); return 1.0 - smoothstep(R - 0.006, R + 0.006, r); }
            if (isT(119.0)) return cover(uv.x, mix(-0.08, 1.08, easeF(p)), 0.002);
            if (isT(121.0)) { float r = length((uv - 0.5) * vec2(aspect, 1.0)); float R = easeF(p) * (length(vec2(aspect, 1.0)) * 0.5 + 0.1); return 1.0 - smoothstep(R - 0.01, R + 0.01, r); }
            if (isT(97.0)) { vec2 q = (uv - 0.5) * vec2(aspect, 1.0); float d = length(q) + (fbm(q * 5.0 + 3.0) - 0.5) * 0.18; float R = p * (length(vec2(aspect, 1.0)) * 0.5 + 0.15); return 1.0 - smoothstep(R - 0.01, R + 0.01, d); }
            if (isT(101.0)) { vec2 q = (uv - 0.5) * vec2(aspect, 1.0); float a = atan(q.y, q.x) / 6.2831853 + 0.5; float r = length(q) / (length(vec2(aspect, 1.0)) * 0.5); return cover(fract(a + r * 2.0) * 0.33 + r * 0.67, mix(-0.02, 1.02, p), 0.02); }
            if (isT(102.0)) { float b = floor(uv.x * 12.0); float dly = rnd(vec2(b, 2.0)) * 0.35; float hgt = clamp((p - dly) / 0.6, 0.0, 1.0); return step(uv.y, easeF(hgt) * 1.001); }
            if (isT(103.0)) { float sl = floor(uv.x * 9.0); float f = fract(uv.x * 9.0); float dly = sl / 9.0 * 0.5; float e = clamp((p - dly) / 0.5, 0.0, 1.0); return step(abs(f - 0.5) * 2.0, e); }
            if (isT(96.0)) { float col = floor(uv.x * uSize.x / 6.0); float sp = 0.7 + 0.8 * rnd(vec2(col, 5.0)); float yy = floor((1.0 - uv.y) * uSize.y / 6.0) * 6.0 / uSize.y; return step(yy, p * 1.6 * sp - rnd(vec2(col, 9.0)) * 0.35); }
            return 1.0;
        }

        vec4 cine(vec2 uv0) {
            float p = uP;
            float k = 1.0 - abs(2.0 * p - 1.0);
            bool inc = uSide > 0.5;
            float aspect = uSize.x / uSize.y;
            vec2 c = uv0 - 0.5;
            vec2 uv = uv0;
            vec3 col = vec3(0.0);
            bool any = false;
            if (isT(50.0)) { any = true;
                // Film burn: a hot edge eats the picture from a burn point; charring ahead of it.
                col = tex(uv0).rgb;
                float v = burnField(uv0, aspect) - burnEdge(p);
                col *= 1.0 - 0.75 * (1.0 - smoothstep(0.0, 0.14, v)) * step(0.0, v);
                float fire = 1.0 - smoothstep(0.0, 0.07, abs(v));
                col += mix(vec3(1.0, 0.32, 0.04), vec3(1.0, 0.93, 0.72), 1.0 - smoothstep(0.0, 0.025, abs(v))) * fire * 1.7;
                col += vec3(0.28, 0.09, 0.0) * k * (0.7 + 0.3 * rnd(vec2(floor(p * 24.0), 1.0)));
            }
            if (isT(51.0)) { any = true;
                // Anamorphic flare: a long blue streak with halo and lens ghosts sweeps across.
                col = mtex(uv0).rgb;
                float cx = mix(-0.3, 1.3, easeF(p)); float cy = 0.56;
                float dx = (uv0.x - cx) * aspect; float dy = uv0.y - cy;
                float streak = exp(-dy * dy * 1800.0) * exp(-abs(dx) * 0.9);
                float halo = exp(-(dx * dx + dy * dy) * 30.0);
                float gh = 0.0;
                for (int i = 1; i <= 3; i++) {
                    float fi = float(i);
                    vec2 gp = vec2(0.5) - (vec2(cx, cy) - 0.5) * (0.45 * fi);
                    float gl = length((uv0 - gp) * vec2(aspect, 1.0));
                    gh += exp(-pow(gl - 0.045 * fi, 2.0) * 3000.0) * 0.35 + exp(-gl * gl * 300.0) * 0.12;
                }
                col += max(col - 0.7, 0.0) * k * 1.5;
                col += (vec3(0.3, 0.55, 1.0) * streak * 2.4 + vec3(1.0, 0.9, 0.8) * halo * 1.4 + vec3(0.55, 0.75, 1.0) * gh) * k * 1.3 + vec3(k * k * 0.15);
            }
            if (isT(52.0)) { any = true;
                // Lens warp: the outgoing shot bulges like a fisheye, the next one snaps in from a pinch.
                vec2 q = c * vec2(aspect, 1.0);
                float d2 = dot(q, q);
                float a = k * k * 1.8;
                float f = 1.0 + a * d2 * 2.0;
                float z = inc ? mix(1.3, 1.0, easeF(p)) : mix(1.0, 1.3, easeF(p));
                vec2 base = inc ? c * f : c / f;
                vec2 u1 = base / z + 0.5;
                vec2 u2 = base * (1.0 + 0.025 * k) / z + 0.5;
                vec2 u3 = base * (1.0 - 0.025 * k) / z + 0.5;
                col = vec3(mtex(u2).r, mtex(u1).g, mtex(u3).b);
                col *= 1.0 - k * 0.9 * d2;
            }
            if (isT(53.0)) { any = true;
                // Datamosh: blocks smear along their own motion, colours posterize and swap.
                vec2 g = vec2(20.0, 12.0);
                vec2 bk = floor(uv0 * g);
                vec2 f = moshFlow(uv0, k);
                col = mtex(uv0 - f * (1.0 + rnd(bk + 2.0) * 1.5)).rgb;
                float lv = mix(64.0, 14.0, k);
                col = floor(col * lv + 0.5) / lv;
                vec2 fr = fract(uv0 * g);
                col *= 1.0 - 0.18 * k * clamp(step(fr.x, 0.06) + step(fr.y, 0.06), 0.0, 1.0);
                float sw = step(0.9, rnd(bk + floor(p * 10.0)));
                col = mix(col, col.brg * vec3(1.1, 0.9, 1.2), sw * k * 0.45);
            }
            if (isT(54.0)) { any = true;
                // VHS rewind: the picture rolls, tracking bands tear across, colours bleed.
                float dir = inc ? -1.0 : 1.0;
                float roll = dir * k * k * 0.9;
                uv.y = fract(uv0.y + roll);
                float by = fract(p * 1.7);
                float band = exp(-pow((uv0.y - by) * 14.0, 2.0));
                uv.x += (rnd(vec2(floor(uv0.y * uSize.y / 2.0), floor(p * 60.0))) - 0.5) * 0.03 * k + band * 0.04 * k + sin(uv0.y * 120.0 + p * 40.0) * 0.003 * k;
                col = vec3(tex(uv + vec2(0.006 * k, 0.0)).r, tex(uv).g, tex(uv - vec2(0.004 * k, 0.0)).b);
                col = mix(col, vec3(lum(col)), 0.4 * k);
                col += (rnd(uv0 * uSize + p * 100.0) - 0.5) * 0.25 * k + band * 0.35 * k;
                col *= 1.0 - 0.18 * k * step(0.5, fract(uv0.y * uSize.y / 3.0));
                float wr = fract(uv0.y + roll);
                col *= mix(1.0, smoothstep(0.0, 0.025, min(wr, 1.0 - wr)), step(0.05, k));
                col *= mix(vec3(1.0), vec3(1.06, 0.94, 1.1), k);
            }
            if (isT(55.0)) { any = true;
                // Letterbox close: cinema bars close to black and open on the next shot, with a slow push.
                float z = inc ? mix(1.12, 1.0, easeF(p)) : mix(1.0, 1.12, easeF(p));
                col = mtex(c / z + 0.5).rgb;
                float h = 0.5 * pow(k, 2.5);
                float bar = max(1.0 - smoothstep(h - 0.003, h + 0.003, uv0.y), smoothstep(1.0 - h - 0.003, 1.0 - h + 0.003, uv0.y));
                float edge = (1.0 - smoothstep(0.0, 0.004, abs(uv0.y - h))) + (1.0 - smoothstep(0.0, 0.004, abs(uv0.y - (1.0 - h))));
                col = col * (1.0 - bar) + vec3(0.95, 0.72, 0.35) * edge * 0.55 * k;
            }
            if (isT(56.0)) { any = true;
                // Aperture: six iris blades close to black and open on the next shot.
                col = mtex(uv0).rgb;
                vec2 q = c * vec2(aspect, 1.0);
                float rr = length(q);
                float th = atan(q.y, q.x) + p * 1.2 + 3.14159265;
                float seg = 1.0471976;
                float hx = rr * cos(mod(th, seg) - seg * 0.5);
                float R = 1.08 * pow(1.0 - k, 0.8);
                float inside = 1.0 - smoothstep(R - 0.004, R + 0.004, hx);
                float bl = fract((th + hx * 2.0) / seg);
                vec3 metal = vec3(0.07, 0.07, 0.08) + vec3(0.13) * smoothstep(0.92, 1.0, bl) + vec3(0.04) * (1.0 - rr);
                col = mix(metal, col, inside);
                col += vec3(0.6, 0.5, 0.35) * (1.0 - smoothstep(0.0, 0.01, abs(hx - R))) * 0.5 * step(0.02, k);
            }
            if (isT(57.0)) { any = true;
                // Smoke: drifting haze rolls through and clears on the next shot.
                vec3 acc = vec3(0.0);
                for (int i = 0; i < 4; i++) {
                    float a = float(i) * 1.5707963;
                    acc += mtex(uv0 + vec2(cos(a), sin(a)) * 0.006 * k).rgb;
                }
                col = acc / 4.0;
                float haze = k * (0.55 + 0.45 * fbm(uv0 * vec2(aspect, 1.0) * 4.0 - vec2(p * 1.2, p * 0.4)));
                float edge = 1.0 - smoothstep(0.0, 0.25, abs(mix(-0.35, 1.35, p) - smokeField(uv0, aspect, p)));
                col = mix(col, vec3(0.86, 0.88, 0.92), clamp(haze * 1.1 + edge * 0.35 * k, 0.0, 0.92));
            }
            if (isT(58.0)) { any = true;
                // Shatter: the shot cracks, then falls away in spinning shards.
                if (inc) col = tex(uv0).rgb * mix(0.55, 1.0, p);
                else {
                    vec4 sh = shatter(uv0, p);
                    if (sh.w > 0.5) {
                        col = tex(sh.xy).rgb * (1.0 - 0.3 * (sh.w - 1.0));
                        float crack = (1.0 - smoothstep(0.0, 0.004, sh.z)) * smoothstep(0.0, 0.06, p);
                        col = mix(col, vec3(0.9, 0.95, 1.0), crack * 0.8);
                    }
                }
            }
            if (isT(59.0)) { any = true;
                // Kaleidoscope: the frame folds into a turning six-way mirror and unfolds as the next shot.
                vec2 q = c * vec2(aspect, 1.0);
                float rr = length(q);
                float th = atan(q.y, q.x) + p * 2.5;
                float seg = 1.0471976;
                float tf = abs(mod(th, seg) - seg * 0.5);
                vec2 kq = vec2(cos(tf), sin(tf)) * rr * (1.0 + k * 0.5);
                col = mix(mtex(uv0).rgb, mtex(kq / vec2(aspect, 1.0) + 0.5).rgb, smoothstep(0.05, 0.45, k));
                col = mix(vec3(lum(col)), col, 1.0 + 0.4 * k);
                col += vec3(1.0, 0.9, 0.75) * exp(-rr * rr * 20.0) * k * 0.5;
            }
            if (isT(60.0)) { any = true;
                // Cube turn: the shot turns away as a side of a 3D cube, the next side turns in.
                vec3 hf = boxFace(uv0, aspect, p, inc ? 1.0 : 0.0);
                float ang = easeF(p) * 1.5707963;
                if (hf.z > 0.5) col = tex(hf.xy).rgb * (inc ? sin(ang) * 0.55 + 0.45 : cos(ang) * 0.55 + 0.45);
            }
            if (isT(61.0)) { any = true;
                // Card flip: the frame flips over like a card, with the next shot on its back.
                vec3 hf = flipFace(uv0, aspect, p);
                bool mine = inc ? p >= 0.5 : p < 0.5;
                if (hf.z > 0.5 && mine) col = tex(hf.xy).rgb * (abs(cos(easeF(p) * 3.14159265)) * 0.5 + 0.5);
            }
            if (isT(62.0)) { any = true;
                // Sun rays: light breaks through the bright parts of the frame in god rays.
                col = mtex(uv0).rgb;
                vec2 L = vec2(mix(0.85, 0.6, p), 0.85);
                vec2 dl = L - uv0;
                float acc = 0.0; float w = 1.0;
                float jit = rnd(uv0 * uSize);
                for (int i = 0; i < 16; i++) {
                    vec2 sp = uv0 + dl * ((float(i) + jit) / 16.0) * 0.6 * k;
                    acc += max(lum(mtex(sp).rgb) - 0.5, 0.0) * w;
                    w *= 0.93;
                }
                float n = vnoise(vec2(atan(dl.y, dl.x) * 14.0, p * 3.0));
                vec3 warm = vec3(1.0, 0.78, 0.45);
                col += warm * (acc * 0.2 + 0.45 * pow(n, 3.0) * exp(-length(dl * vec2(aspect, 1.0)) * 2.0)) * k * 1.3 + warm * k * k * 0.18;
            }
            if (isT(63.0)) { any = true;
                // Pixel sort: bright pixels drip down the frame in sorted streaks.
                col = mtex(uv0).rgb;
                float bl = lum(col);
                float cw = floor(uv0.x * uSize.x / 2.0);
                float len = k * (0.15 + 0.35 * rnd(vec2(cw, 3.0)));
                float thr = mix(1.0, 0.42, k);
                for (int i = 1; i <= 10; i++) {
                    vec3 v = mtex(uv0 + vec2(0.0, len * float(i) / 10.0)).rgb;
                    float l = lum(v);
                    if (l > bl && l > thr) { col = v; bl = l; }
                }
            }
            if (isT(64.0)) { any = true;
                // Neon edges: the shot turns into glowing neon outlines, then the next shot lights up.
                col = mtex(uv0).rgb;
                vec2 px = 1.5 / uSize;
                float tl = lum(mtex(uv0 + vec2(-px.x, px.y)).rgb); float tc = lum(mtex(uv0 + vec2(0.0, px.y)).rgb); float tr = lum(mtex(uv0 + px).rgb);
                float ml = lum(mtex(uv0 - vec2(px.x, 0.0)).rgb); float mr = lum(mtex(uv0 + vec2(px.x, 0.0)).rgb);
                float bl = lum(mtex(uv0 - px).rgb); float bc = lum(mtex(uv0 - vec2(0.0, px.y)).rgb); float br = lum(mtex(uv0 + vec2(px.x, -px.y)).rgb);
                float gx = (tr + 2.0 * mr + br) - (tl + 2.0 * ml + bl);
                float gy = (tl + 2.0 * tc + tr) - (bl + 2.0 * bc + br);
                float e = smoothstep(0.08, 0.35, length(vec2(gx, gy)));
                float hue = fract(uv0.x * 0.6 + uv0.y * 0.3 + p * 0.8);
                vec3 neon = 0.5 + 0.5 * cos(6.2831853 * (hue + vec3(0.0, 0.33, 0.67)));
                col = mix(col, col * 0.15 + neon * e * 2.2, smoothstep(0.0, 0.6, k));
            }
            if (isT(65.0)) { any = true;
                // Ember drift: the shot burns away into glowing embers that float upward.
                float th = emberField(uv0, aspect);
                float e = mix(-0.1, 1.1, p);
                float ahead = th - e;
                uv.y += (1.0 - smoothstep(0.0, 0.15, ahead)) * step(0.0, ahead) * 0.01 * sin(uv0.x * 80.0 + p * 30.0);
                col = tex(uv).rgb;
                vec2 g = floor(uv0 * uSize / 2.5);
                float flick = 0.6 + 0.4 * rnd(g + floor(p * 30.0));
                float ember = 1.0 - smoothstep(0.0, 0.035, abs(ahead));
                col *= 1.0 - 0.6 * (1.0 - smoothstep(0.0, 0.1, ahead)) * step(0.0, ahead);
                col += vec3(1.0, 0.45, 0.1) * ember * flick * 1.8;
                float sp = step(0.994, rnd(floor((uv0 - vec2(0.0, p * 0.35)) * uSize / 4.0)));
                col += vec3(1.0, 0.55, 0.15) * sp * (1.0 - smoothstep(0.0, 0.3, -ahead)) * step(ahead, 0.0) * 2.0;
            }
            if (isT(66.0)) { any = true;
                // Cloud pass: flying forward into a cloud bank, out of it into the next shot.
                float z = inc ? mix(1.25, 1.0, easeF(p)) : mix(1.0, 1.35, easeF(p));
                col = mtex(c / z + 0.5).rgb;
                vec2 q = uv0 * vec2(aspect, 1.0);
                float n1 = fbm(q * 2.4 + vec2(p * 1.6, p * 0.5));
                float n2 = fbm(q * 5.5 - vec2(p * 2.4, p * 0.2));
                float dens = clamp(k * 1.7 - 0.3 + (n1 * 0.7 + n2 * 0.3 - 0.5) * 1.3, 0.0, 1.0);
                vec3 cloud = mix(vec3(0.74, 0.79, 0.86), vec3(1.0, 0.99, 0.97), smoothstep(0.3, 0.8, n2 + uv0.y * 0.3));
                col = mix(col, cloud, smoothstep(0.0, 1.0, dens));
            }
            if (isT(67.0)) { any = true;
                // Rack focus: the shot melts into soft bokeh, the next one pulls into focus.
                float r = 0.022 * pow(k, 1.4);
                vec2 b = c / (1.0 + 0.025 * k) + 0.5;
                vec3 acc = vec3(0.0); float ws = 0.0;
                for (int i = 0; i < 24; i++) {
                    float fi = float(i);
                    float a = fi * 2.39996;
                    float rr = sqrt((fi + 0.5) / 24.0) * r;
                    vec3 s = mtex(b + vec2(cos(a), sin(a)) * rr * vec2(1.0 / aspect, 1.0)).rgb;
                    float w = 1.0 + pow(lum(s), 4.0) * 8.0 * k;
                    acc += s * w; ws += w;
                }
                col = acc / ws;
            }
            if (isT(68.0)) { any = true;
                // Warp zoom: a fast zoom with motion blur that punches through into the next shot.
                float s = inc ? mix(0.35, 1.0, easeF(p)) : mix(1.0, 3.0, easeF(p));
                vec2 b = c / s;
                vec3 acc = vec3(0.0);
                for (int i = 0; i < 16; i++) {
                    float f = float(i) / 15.0 - 0.5;
                    vec2 o = b * (1.0 + f * 0.55 * k);
                    acc += vec3(mtex(o * (1.0 + 0.02 * k) + 0.5).r, mtex(o + 0.5).g, mtex(o * (1.0 - 0.02 * k) + 0.5).b);
                }
                col = acc / 16.0 + vec3(pow(k, 4.0) * 0.7);
            }
            if (isT(69.0)) { any = true;
                // Drone roll: the horizon rolls over with motion blur into the next shot.
                float ang = inc ? -(1.0 - easeF(p)) * 3.14159265 : easeF(p) * 3.14159265;
                vec2 q = c * vec2(aspect, 1.0);
                vec3 acc = vec3(0.0);
                for (int i = 0; i < 12; i++) {
                    float f = float(i) / 11.0 - 0.5;
                    vec2 r = rot2(q, ang + f * 0.5 * k);
                    acc += mtex(r / vec2(aspect, 1.0) / (1.0 + k * 0.7) + 0.5).rgb;
                }
                col = acc / 12.0;
            }
            if (isT(70.0)) { any = true;
                // Luma fade: the brightest parts change first, the shadows last, with a soft glow on the edge.
                col = tex(uv0).rgb;
                float th = mix(1.15, -0.15, p);
                col += vec3(1.0, 0.95, 0.85) * (1.0 - smoothstep(0.0, 0.12, abs(lum(col) - th))) * 0.35 * k;
            }
            if (isT(71.0)) { any = true;
                // Dolly zoom: the middle holds still while the background stretches away.
                vec2 q = c * vec2(aspect, 1.0);
                float d2 = dot(q, q);
                float z = inc ? mix(1.15, 1.0, easeF(p)) : mix(1.0, 1.15, easeF(p));
                vec3 acc = vec3(0.0);
                for (int i = 0; i < 6; i++) {
                    float kk = k * (0.85 + 0.05 * float(i));
                    vec2 w = c * mix(1.0, 1.0 / (1.0 + 1.6 * d2), kk) / z;
                    acc += mtex(w + 0.5).rgb;
                }
                col = acc / 6.0;
                col *= 1.0 - 0.35 * k * d2;
            }
            if (isT(72.0)) { any = true;
                // Shine sweep: a streak of light sweeps across and leaves the next shot behind it.
                col = tex(uv0).rgb;
                float d = shineX(uv0, aspect) - mix(-0.15, 1.15, p);
                col += vec3(1.0, 0.96, 0.88) * exp(-d * d * 500.0) * 1.6 + vec3(0.7, 0.8, 1.0) * exp(-d * d * 30.0) * 0.25;
            }
            if (isT(73.0)) { any = true;
                // Film gate roll: the frame slips up like film off its sprockets, with the frame line and flicker.
                vec3 acc = vec3(0.0);
                for (int i = 0; i < 8; i++) acc += tex(uv0 + vec2(0.0, (float(i) / 7.0 - 0.5) * 0.06 * k)).rgb;
                col = acc / 8.0;
                col *= 0.85 + 0.15 * rnd(vec2(floor(p * 24.0), 3.0)) * k + 0.15 * (1.0 - k);
                float line = inc ? smoothstep(0.955, 0.965, uv0.y) : 1.0 - smoothstep(0.035, 0.045, uv0.y);
                col = mix(col, vec3(0.02), line);
                col += (rnd(uv0 * uSize + p * 50.0) - 0.5) * 0.08 * k;
            }
            if (isT(74.0)) { any = true;
                // Bokeh lights: the shot softens behind floating out-of-focus lights.
                vec3 acc = vec3(0.0);
                for (int i = 0; i < 8; i++) { float a = float(i) * 0.785398; acc += mtex(uv0 + vec2(cos(a), sin(a)) * 0.012 * k * vec2(1.0 / aspect, 1.0)).rgb; }
                col = mix(tex(uv0).rgb, acc / 8.0, smoothstep(0.0, 0.5, k));
                vec2 g = vec2(7.0, 4.0);
                vec2 q = uv0 + vec2(p * 0.12, -p * 0.05);
                vec2 cell = floor(q * g);
                for (int j = -1; j <= 1; j++) {
                    for (int i = -1; i <= 1; i++) {
                        vec2 cl = cell + vec2(float(i), float(j));
                        vec2 cen = (cl + vec2(rnd(cl + 1.7), rnd(cl + 8.3))) / g;
                        float rad = mix(0.035, 0.09, rnd(cl + 4.4));
                        float d = length((q - cen) * vec2(aspect, 1.0));
                        float disc = 1.0 - smoothstep(rad * 0.85, rad, d);
                        float rim = (1.0 - smoothstep(0.0, rad * 0.12, abs(d - rad * 0.9))) * 0.4;
                        vec3 tint = mix(vec3(1.0, 0.72, 0.38), vec3(1.0, 0.92, 0.7), rnd(cl + 2.2));
                        col += tint * (disc * 0.28 + rim * 0.3) * k * step(0.35, rnd(cl + 6.1));
                    }
                }
            }
            if (isT(75.0)) { any = true;
                // Prism split: the colours split apart and turn like light through a prism.
                vec3 o = vec3(0.0);
                for (int ch = 0; ch < 3; ch++) {
                    float a = float(ch) * 2.0943951 + p * 4.0;
                    vec2 off = vec2(cos(a), sin(a)) * 0.035 * k * vec2(1.0 / aspect, 1.0);
                    vec3 s = mtex(c / (1.0 + 0.08 * k) + 0.5 + off).rgb;
                    if (ch == 0) o.r = s.r; else if (ch == 1) o.g = s.g; else o.b = s.b;
                }
                col = o + max(o - 0.65, 0.0) * k * 1.2;
            }
            if (isT(76.0)) { any = true;
                // Heat haze: the shot shimmers like air over hot ground, warms and blooms.
                float n = fbm(uv0 * vec2(aspect, 1.0) * 6.0 + vec2(0.0, -p * 3.0));
                vec2 w = vec2(sin(uv0.y * 45.0 + p * 25.0 + n * 6.0) * 0.006, cos(uv0.x * 30.0 + p * 18.0) * 0.003) * k * (0.5 + n);
                col = mtex(uv0 + w).rgb;
                col = mix(col, col * vec3(1.1, 0.98, 0.84), k * 0.6);
                col += max(col - 0.6, 0.0) * k * 1.4;
            }
            if (isT(77.0)) { any = true;
                // Paint splash: blobs of the next shot splash open, with a wet rim.
                col = tex(uv0).rgb;
                float d = splashField(uv0, aspect) - p * 1.25;
                col += vec3(0.25) * (1.0 - smoothstep(0.0, 0.03, abs(d))) * k;
            }
            if (isT(78.0) || isT(79.0) || isT(80.0) || isT(81.0) || isT(83.0) || isT(84.0) || isT(85.0) || isT(90.0) || isT(93.0) || isT(96.0)) { any = true;
                // Shape reveals: the picture as it is (the shape is in the reveal), a soft light on the edge.
                col = tex(uv0).rgb;
            }
            if (isT(82.0)) { any = true;
                // Spin blur: the picture smears round the middle without turning.
                vec2 q = c * vec2(aspect, 1.0);
                vec3 acc = vec3(0.0);
                for (int i = 0; i < 14; i++) { float f = float(i) / 13.0 - 0.5; acc += mtex(rot2(q, f * 0.6 * k) / vec2(aspect, 1.0) + 0.5).rgb; }
                col = acc / 14.0;
            }
            if (isT(86.0)) { any = true;
                // Water drop: a ring wave runs out from the middle, bending the picture as it passes.
                float r = length(c * vec2(aspect, 1.0));
                float R = p * (length(vec2(aspect, 1.0)) * 0.5 + 0.05);
                vec2 dir = c / max(length(c), 0.001);
                uv = uv0 + dir * sin((r - R) * 70.0) * 0.012 * k * exp(-abs(r - R) * 18.0);
                col = mtex(uv).rgb + vec3(0.18) * exp(-abs(r - R) * 60.0) * k;
            }
            if (isT(87.0)) { any = true;
                // TV switch: the picture collapses to a line, then a dot, like an old TV turning off — and back on.
                float f = inc ? 1.0 - p : p;
                float a = clamp(f * 2.0, 0.0, 1.0);
                float sy = mix(1.0, 0.004, smoothstep(0.0, 0.7, a));
                float sx = mix(1.0, 0.004, smoothstep(0.65, 1.0, a));
                float inside = step(abs(c.x), sx * 0.5) * step(abs(c.y), sy * 0.5);
                col = (mtex(c / vec2(sx, sy) + 0.5).rgb + vec3(smoothstep(0.3, 1.0, a) * 0.9)) * inside;
            }
            if (isT(88.0)) { any = true;
                // Glow dissolve: the bright parts bloom and carry the change.
                vec3 acc = vec3(0.0);
                for (int i = 0; i < 8; i++) { float a = float(i) * 0.785398; acc += mtex(uv0 + vec2(cos(a), sin(a)) * 0.01 * k * vec2(1.0 / aspect, 1.0)).rgb; }
                col = mix(tex(uv0).rgb, acc / 8.0, k);
                col += max(col - 0.45, 0.0) * k * 2.2 + vec3(k * k * 0.25);
            }
            if (isT(89.0)) { any = true;
                // Dip to colour: through a warm amber.
                col = tex(uv0).rgb;
                float dip = inc ? clamp((1.0 - p) * 2.0, 0.0, 1.0) : clamp(p * 2.0, 0.0, 1.0);
                col = mix(col, vec3(1.0, 0.62, 0.18), dip);
            }
            if (isT(91.0)) { any = true;
                // Stretch warp: the picture stretches sideways into streaks and snaps back as the next one.
                float s = 1.0 + k * 3.0;
                vec3 acc = vec3(0.0);
                for (int i = 0; i < 10; i++) { float f = float(i) / 9.0 - 0.5; acc += mtex(vec2((uv0.x - 0.5) / s + 0.5 + f * 0.08 * k, uv0.y)).rgb; }
                col = acc / 10.0;
            }
            if (isT(92.0)) { any = true;
                // Split open: the shot parts top and bottom, the next shot behind it.
                float e = easeF(p);
                if (inc) col = mtex(c / mix(1.08, 1.0, e) + 0.5).rgb;
                else col = mtex(vec2(uv0.x, uv0.y > 0.5 ? uv0.y - e * 0.5 : uv0.y + e * 0.5)).rgb;
            }
            if (isT(94.0)) { any = true;
                // Slide blur: a fast slide with motion blur.
                vec3 acc = vec3(0.0);
                for (int i = 0; i < 12; i++) { float f = float(i) / 11.0 - 0.5; acc += tex(uv0 + vec2(f * 0.12 * k, 0.0)).rgb; }
                col = acc / 12.0;
            }
            if (isT(95.0)) { any = true;
                // Jelly: the picture wobbles like jelly through the change.
                float wv = sin(p * 22.0);
                vec2 q = c * vec2(1.0 + 0.16 * k * wv, 1.0 - 0.16 * k * wv);
                q.x += sin(uv0.y * 10.0 + p * 15.0) * 0.025 * k;
                col = mtex(q + 0.5).rgb;
            }
            if (isT(97.0)) { any = true;
                // Ink drop: the next shot spreads like ink in water, with a dark soaking edge.
                col = tex(uv0).rgb;
                vec2 q = c * vec2(aspect, 1.0);
                float d = length(q) + (fbm(q * 5.0 + 3.0) - 0.5) * 0.18 - p * (length(vec2(aspect, 1.0)) * 0.5 + 0.15);
                col *= 1.0 - 0.6 * (1.0 - smoothstep(0.0, 0.05, abs(d))) * k;
            }
            if (isT(98.0)) { any = true;
                // Zoom spin: a quick zoom and half turn with blur.
                float ang = inc ? -(1.0 - easeF(p)) * 1.5707963 : easeF(p) * 1.5707963;
                float z = inc ? mix(2.2, 1.0, easeF(p)) : mix(1.0, 0.45, easeF(p));
                vec2 q = c * vec2(aspect, 1.0);
                vec3 acc = vec3(0.0);
                for (int i = 0; i < 10; i++) { float f = float(i) / 9.0 - 0.5; acc += mtex(rot2(q, ang + f * 0.3 * k) / vec2(aspect, 1.0) / (z * (1.0 + f * 0.1 * k)) + 0.5).rgb; }
                col = acc / 10.0;
            }
            if (isT(99.0)) { any = true;
                // Mirror fold: the shot folds into the middle line like a closing book, the next one unfolds.
                float f = inc ? 1.0 - easeF(p) : easeF(p);
                float hw = max(1.0 - f, 0.002);
                float inside = step(abs(c.x), hw * 0.5);
                col = mtex(vec2(c.x / hw + 0.5, uv0.y)).rgb * inside * (1.0 - 0.5 * f);
            }
            if (isT(100.0)) { any = true;
                // Light streaks: bright parts smear into long horizontal streaks of light.
                col = tex(uv0).rgb;
                vec3 acc = vec3(0.0);
                for (int i = 0; i < 16; i++) { float f = (float(i) / 15.0 - 0.5) * 0.3 * k; acc += max(mtex(uv0 + vec2(f, 0.0)).rgb - 0.55, 0.0); }
                col += acc / 16.0 * 4.0 * k + vec3(k * k * 0.12);
            }
            if (isT(101.0) || isT(102.0) || isT(103.0)) { any = true;
                col = tex(uv0).rgb;
            }
            if (isT(104.0)) { any = true;
                // Vignette close: the corners darken in until it's black, then open on the next shot.
                col = tex(uv0).rgb;
                float r = length(c * vec2(aspect, 1.0)) / (length(vec2(aspect, 1.0)) * 0.5);
                float R = 1.15 * (1.0 - pow(k, 0.8));
                col *= 1.0 - smoothstep(R - 0.35, R, r);
            }
            if (isT(105.0)) { any = true;
                // Chroma zoom: a zoom punch with the colours pulling apart.
                float z = inc ? mix(1.35, 1.0, easeF(p)) : mix(1.0, 1.35, easeF(p));
                vec2 b = c / z;
                col = vec3(mtex(b * (1.0 + 0.06 * k) + 0.5).r, mtex(b + 0.5).g, mtex(b * (1.0 - 0.06 * k) + 0.5).b);
            }
            if (isT(106.0)) { any = true;
                // Overexpose: the shot blows out to white like an overexposed frame, then settles on the next.
                col = tex(uv0).rgb;
                float e = pow(k, 1.5);
                col = 1.0 - (1.0 - col) * (1.0 - e);
                col = mix(col, vec3(1.0), e * e * 0.6);
            }
            if (isT(107.0)) { any = true;
                // Sun flare wipe: a blinding flare with a long streak crosses the frame; the next shot is behind it.
                col = tex(uv0).rgb;
                float e = mix(-0.25, 1.25, p);
                float d = (uv0.x + (uv0.y - 0.5) * 0.25 - e) * aspect;
                float dy = uv0.y - 0.55;
                col += vec3(1.0, 0.85, 0.6) * exp(-d * d * 9.0) * 1.4 + vec3(1.0, 0.95, 0.85) * exp(-(d * d + dy * dy) * 40.0) * 1.5;
                col += vec3(0.5, 0.7, 1.0) * exp(-dy * dy * 900.0) * exp(-abs(d) * 1.2) * 1.2;
            }
            if (isT(108.0)) { any = true;
                // Dust in light: a soft beam with floating dust lights the change.
                col = tex(uv0).rgb;
                float bq = (uv0.x - uv0.y * 0.5 - 0.25 - p * 0.3) * 4.0; float beam = exp(-bq * bq);
                float dust = 0.0;
                vec2 g = floor(uv0 * uSize / 3.0 + vec2(0.0, p * 40.0));
                dust = step(0.993, rnd(g)) * beam;
                col += vec3(1.0, 0.9, 0.7) * (beam * 0.55 * k + dust * 1.5 * k) + vec3(k * k * 0.2);
            }
            if (isT(109.0)) { any = true;
                // Projector: flicker, a bright gate and dark edges, as on an old projector between reels.
                float fl = 0.75 + 0.25 * rnd(vec2(floor(p * 30.0), 3.0));
                vec2 q = c * vec2(aspect, 1.0);
                col = mtex(uv0 + vec2(0.0, (rnd(vec2(floor(p * 24.0), 1.0)) - 0.5) * 0.02 * k)).rgb;
                col = mix(col, vec3(lum(col)) * vec3(1.05, 0.98, 0.85), k * 0.6) * mix(1.0, fl, k);
                col *= 1.0 - k * 0.8 * smoothstep(0.3, 0.75, length(q));
                col += vec3(1.0, 0.95, 0.85) * k * k * 0.6;
            }
            if (isT(110.0)) { any = true;
                // Lightning: a bolt strikes, the frame flashes white, and the next shot appears.
                col = tex(uv0).rgb;
                float y = uv0.y;
                float bx = 0.55 + (fbm(vec2(y * 6.0, floor(p * 6.0))) - 0.5) * 0.35 + (rnd(vec2(floor(y * 40.0), floor(p * 6.0))) - 0.5) * 0.03;
                float b1 = (uv0.x - bx) * aspect * 120.0; float b2 = (uv0.x - bx) * aspect * 18.0; float bolt = exp(-b1 * b1) + exp(-b2 * b2) * 0.35;
                float flash = step(0.35, p) * step(p, 0.65) * (0.6 + 0.4 * step(0.5, fract(p * 20.0)));
                col = mix(col, vec3(0.8, 0.85, 1.0), flash * 0.8 * k) + vec3(0.85, 0.9, 1.0) * bolt * k * 1.5;
            }
            if (isT(111.0)) { any = true;
                // Black hole: the picture spirals into a dark centre and spirals back out as the next shot.
                vec2 q = c * vec2(aspect, 1.0);
                float d = length(q);
                float a = k * 6.0 * exp(-d * 2.5);
                vec2 r = rot2(q, a) * (1.0 + k * 1.5 * exp(-d * 3.0));
                col = mtex(r / vec2(aspect, 1.0) + 0.5).rgb;
                col *= smoothstep(0.0, 0.25 * k + 0.001, d);
                float hr = (d - 0.12 * k) * 25.0; col += vec3(1.0, 0.6, 0.3) * exp(-hr * hr) * k;
            }
            if (isT(112.0)) { any = true;
                // Fog roll: ground fog rolls up and leaves the next shot behind it.
                col = tex(uv0).rgb;
                float fv = mix(-0.3, 1.4, p) - ((1.0 - uv0.y) + fbm(uv0 * vec2(aspect, 1.0) * 3.0 + vec2(p, 0.0)) * 0.45);
                col = mix(col, vec3(0.86, 0.88, 0.92), (1.0 - smoothstep(0.0, 0.35, abs(fv))) * 0.85);
            }
            if (isT(113.0)) { any = true;
                // Rain on glass: drops run down a pane in front of the camera, bending the picture.
                vec2 g = vec2(18.0 * aspect, 9.0);
                vec2 cell = floor(uv0 * g + vec2(0.0, p * 3.0));
                vec2 lc = fract(uv0 * g + vec2(0.0, p * 3.0)) - 0.5;
                vec2 off = vec2(rnd(cell), rnd(cell + 7.0)) - 0.5;
                float dd = length((lc - off * 0.6) * vec2(1.0, 0.7));
                float drop = (1.0 - smoothstep(0.1, 0.22, dd)) * step(0.45, rnd(cell + 3.0)) * k;
                col = mtex(uv0 + (lc - off * 0.6) * drop * 0.06).rgb;
                vec3 acc = vec3(0.0);
                for (int i = 0; i < 6; i++) { float a = float(i) * 1.047; acc += mtex(uv0 + vec2(cos(a), sin(a)) * 0.008 * k).rgb; }
                col = mix(col, acc / 6.0, k * 0.7 * (1.0 - drop)) + vec3(drop * 0.15);
            }
            if (isT(114.0)) { any = true;
                // Blizzard: a burst of snow whites out the frame and clears on the next shot.
                col = tex(uv0).rgb;
                float sn = 0.0;
                for (int l = 0; l < 3; l++) {
                    float fl = float(l);
                    vec2 g = floor((uv0 + vec2(p * (0.6 + fl * 0.4), -p * (0.3 + fl * 0.2))) * uSize / (3.0 + fl * 2.0));
                    sn += step(1.0 - 0.02 * (1.0 + fl) * k * 3.0, rnd(g + fl * 11.0));
                }
                col = mix(col, vec3(0.95), clamp(sn, 0.0, 1.0) * 0.9 + k * k * 0.6);
            }
            if (isT(115.0)) { any = true;
                // Fire wipe: a wall of fire rises and burns the shot away.
                col = tex(uv0).rgb;
                float edge = uv0.y - (mix(-0.2, 1.25, p) + (fbm(vec2(uv0.x * aspect * 4.0, p * 3.0)) - 0.5) * 0.18);
                float fire = 1.0 - smoothstep(0.0, 0.12, abs(edge));
                float flick = 0.7 + 0.3 * fbm(vec2(uv0.x * 20.0, uv0.y * 8.0 - p * 12.0));
                col += mix(vec3(1.0, 0.25, 0.02), vec3(1.0, 0.85, 0.4), fire) * fire * flick * 1.6;
            }
            if (isT(116.0)) { any = true;
                // Light tunnel: rings of light rush past as you fly through a tunnel into the next shot.
                vec2 q = c * vec2(aspect, 1.0);
                float d = length(q);
                float z = inc ? mix(0.5, 1.0, easeF(p)) : mix(1.0, 3.0, easeF(p));
                col = mtex(c / z + 0.5).rgb;
                float rings = pow(0.5 + 0.5 * sin(1.0 / max(d, 0.02) * 2.0 - p * 40.0), 6.0);
                col = mix(col, col * 0.3, k * 0.6) + vec3(0.6, 0.8, 1.0) * rings * k * smoothstep(0.05, 0.5, d) * 1.3;
            }
            if (isT(117.0)) { any = true;
                // Prism leak: rainbow light leaks across the frame.
                col = tex(uv0).rgb;
                float x = uv0.x * 0.8 + uv0.y * 0.4 - mix(-0.4, 1.4, p);
                vec3 rb = 0.5 + 0.5 * cos(6.2831853 * (x * 1.5 + vec3(0.0, 0.33, 0.67)));
                col += rb * exp(-x * x * 6.0) * k * 1.3 + vec3(k * k * 0.15);
            }
            if (isT(118.0)) { any = true;
                // Silent film iris: an old-film iris with sepia, grain and flicker.
                col = tex(uv0).rgb;
                col = mix(col, vec3(lum(col)) * vec3(1.1, 0.95, 0.75), 0.85 * k);
                col *= 0.85 + 0.15 * rnd(vec2(floor(p * 18.0), 2.0)) * k;
                col += (rnd(uv0 * uSize + floor(p * 24.0)) - 0.5) * 0.15 * k;
            }
            if (isT(119.0)) { any = true;
                // Glass pane: a sheet of glass slides across; its edge bends and catches the light.
                col = tex(uv0).rgb;
                float e = mix(-0.08, 1.08, easeF(p));
                float d = uv0.x - e;
                float edge = exp(-d * d * 3000.0);
                if (!inc) col = mtex(uv0 + vec2(edge * 0.03, 0.0)).rgb;
                col += vec3(0.9, 0.95, 1.0) * edge * 0.8 + vec3(0.6, 0.8, 1.0) * exp(-d * d * 200.0) * 0.12 * k;
            }
            if (isT(120.0)) { any = true;
                // Depth push: the shot rushes towards you and blurs, the next one comes in from behind.
                float z = inc ? mix(0.75, 1.0, easeF(p)) : mix(1.0, 1.6, easeF(p));
                vec3 acc = vec3(0.0);
                for (int i = 0; i < 10; i++) { float f = float(i) / 9.0; acc += mtex(c / (z * (1.0 + f * 0.12 * k)) + 0.5).rgb; }
                col = acc / 10.0 * (1.0 - 0.35 * k);
            }
            if (isT(121.0)) { any = true;
                // Shockwave: a ring of force bursts out with a colour fringe, carrying the next shot.
                float r = length(c * vec2(aspect, 1.0));
                float R = easeF(p) * (length(vec2(aspect, 1.0)) * 0.5 + 0.1);
                float bd = (r - R) * 22.0; float band = exp(-bd * bd);
                vec2 dir = c / max(length(c), 0.001);
                float push = band * 0.06 * k;
                col = vec3(mtex(uv0 - dir * push * 1.3).r, mtex(uv0 - dir * push).g, mtex(uv0 - dir * push * 0.7).b) + vec3(band * 0.35 * k);
            }
            if (isT(122.0)) { any = true;
                // Tilt down: the camera tilts down with motion blur and lands on the next shot.
                float off = inc ? (1.0 - easeF(p)) * -0.6 : easeF(p) * 0.6;
                vec3 acc = vec3(0.0);
                for (int i = 0; i < 12; i++) { float f = float(i) / 11.0 - 0.5; acc += mtex(vec2(uv0.x, uv0.y + off + f * 0.15 * k)).rgb; }
                col = acc / 12.0;
            }
            if (isT(123.0)) { any = true;
                // Horizon wipe: a warm sunrise line rises up the frame.
                col = tex(uv0).rgb;
                float d = (1.0 - uv0.y) - mix(-0.1, 1.1, p);
                col += vec3(1.0, 0.6, 0.25) * exp(-d * d * 120.0) * 0.9 * k;
            }
            if (isT(124.0)) { any = true;
                // Golden hour: a warm golden glow washes over and carries the change.
                col = tex(uv0).rgb;
                col = mix(col, col * vec3(1.25, 1.0, 0.7) + vec3(0.12, 0.06, 0.0), k);
                col += max(col - 0.55, 0.0) * k * 1.6 + vec3(1.0, 0.75, 0.4) * k * k * 0.3;
            }
            if (isT(125.0)) { any = true;
                // Nightfall: the scene goes blue and dark with twinkling stars, then the next shot.
                col = tex(uv0).rgb;
                col = mix(col, vec3(lum(col)) * vec3(0.35, 0.45, 0.8) * 0.5, k);
                float st = step(0.997, rnd(floor(uv0 * uSize / 2.0))) * (0.5 + 0.5 * sin(p * 40.0 + rnd(floor(uv0 * uSize / 2.0)) * 20.0));
                col += vec3(st) * k * step(0.45, uv0.y);
            }
            if (isT(126.0)) { any = true;
                // Lens dirt flare: blown-out light shows the smudges on the lens.
                col = tex(uv0).rgb;
                float dirt = fbm(uv0 * vec2(aspect, 1.0) * 5.0) * smoothstep(0.3, 0.9, fbm(uv0 * 9.0 + 2.0));
                float glow = exp(-length((uv0 - vec2(mix(0.2, 0.8, p), 0.7)) * vec2(aspect, 1.0)) * 2.5);
                col += vec3(1.0, 0.92, 0.8) * (glow * 0.9 + dirt * glow * 1.6) * k + vec3(k * k * 0.35);
            }
            if (isT(127.0) || isT(128.0) || isT(134.0) || isT(135.0)) { any = true;
                col = tex(uv0).rgb;
                if (isT(128.0)) { float tear = uv0.x + (fbm(vec2(uv0.y * 14.0, 3.0)) - 0.5) * 0.12 - mix(-0.1, 1.15, p); col = mix(col, vec3(0.96, 0.95, 0.9), (1.0 - smoothstep(0.0, 0.012, abs(tear))) * step(0.02, k)); }
                if (isT(134.0)) { float d = 1.0 - (uv0.x + (1.0 - uv0.y) * 0.35) / 1.35 - mix(-0.05, 1.05, p); col *= 1.0 - 0.5 * exp(-d * d * 400.0) * k; col += vec3(0.3) * exp(-(d + 0.02) * (d + 0.02) * 2000.0) * k; }
                if (isT(135.0)) { float d = abs(uv0.x - mix(-0.15, 1.15, p)); float bl = (1.0 - smoothstep(0.0, 0.2, d)) * 0.015; vec3 acc = vec3(0.0); for (int i = 0; i < 8; i++) { float a = float(i) * 0.785398; acc += mtex(uv0 + vec2(cos(a), sin(a)) * bl).rgb; } col = acc / 8.0; }
            }
            if (isT(129.0)) { any = true;
                // Comic burst: a jagged starburst with halftone dots bursts open.
                col = tex(uv0).rgb;
                vec2 g = fract(uv0 * vec2(aspect, 1.0) * 60.0) - 0.5;
                col = mix(col, col * 0.5 + vec3(1.0, 0.85, 0.2) * 0.5, step(length(g), 0.25 + 0.2 * k) * k * 0.8);
            }
            if (isT(130.0)) { any = true;
                // Halftone: the picture turns into printed dots and back.
                float cells = mix(260.0, 45.0, k);
                vec2 g = uv0 * vec2(aspect, 1.0) * cells;
                vec2 cl = (floor(g) + 0.5) / cells / vec2(aspect, 1.0);
                float l = lum(mtex(cl).rgb);
                float dotv = 1.0 - smoothstep(l * 0.55 - 0.05, l * 0.55 + 0.05, length(fract(g) - 0.5));
                col = mix(tex(uv0).rgb, mtex(cl).rgb * dotv + vec3(0.06), smoothstep(0.0, 0.5, k));
            }
            if (isT(131.0)) { any = true;
                // Thermal: a heat-camera look through the middle of the change.
                vec3 s0 = tex(uv0).rgb; float l = lum(s0);
                vec3 th = clamp(vec3(l * 3.0 - 1.4, sin(l * 3.14159), 1.2 - l * 2.4), 0.0, 1.0) + vec3(0.2, 0.0, 0.1) * l;
                col = mix(s0, th, smoothstep(0.0, 0.6, k));
            }
            if (isT(132.0)) { any = true;
                // Night vision: green, grainy and scan-lined.
                vec3 s0 = tex(uv0).rgb; float l = lum(s0) * 1.6;
                vec3 nv = vec3(0.1, 1.0, 0.25) * l + (rnd(uv0 * uSize + p * 60.0) - 0.5) * 0.25;
                nv *= 0.85 + 0.15 * step(0.5, fract(uv0.y * uSize.y / 3.0));
                nv *= 1.0 - smoothstep(0.35, 0.75, length(c * vec2(aspect, 1.0)));
                col = mix(s0, nv, smoothstep(0.0, 0.6, k));
            }
            if (isT(133.0)) { any = true;
                // Hologram: cyan scan lines, flicker and sideways slips.
                float slip = (rnd(vec2(floor(uv0.y * 30.0), floor(p * 20.0))) - 0.5) * 0.04 * k;
                vec3 s0 = mtex(uv0 + vec2(slip, 0.0)).rgb; float l = lum(s0);
                vec3 hg = vec3(0.3, 0.9, 1.0) * (l * 1.3 + 0.1) * (0.7 + 0.3 * step(0.5, fract(uv0.y * uSize.y / 4.0 - p * 30.0)));
                col = mix(s0, hg, smoothstep(0.0, 0.6, k)) * (0.85 + 0.15 * rnd(vec2(floor(p * 30.0), 4.0)));
            }
            if (isT(136.0)) { any = true;
                // Zoom punch: a hard punch-in with a little shake.
                float z = inc ? mix(1.6, 1.0, easeF(p)) : mix(1.0, 1.8, easeF(p));
                vec2 sh = vec2(rnd(vec2(floor(p * 40.0), 1.0)) - 0.5, rnd(vec2(floor(p * 40.0), 2.0)) - 0.5) * 0.03 * k;
                col = mtex(c / z + 0.5 + sh).rgb;
            }
            if (!any) col = tex(uv0).rgb;
            float rv = revealC(uv0);
            float al = uMode > 1.5 ? 1.0 - rv : (uMode > 0.5 ? rv : 1.0);
            return vec4(clamp(col, 0.0, 1.0), al);
        }

        float reveal(vec2 uv) {
            if (uType > 49.5) return revealC(uv);
            float p = uP;
            float aspect = uSize.x / uSize.y;
            if (isT(1.0)) return p;
            if (isT(2.0) || isT(3.0)) return sstep(0.48, 0.52, p);
            if (isT(4.0)) return sstep(0.35, 0.65, p);
            if (isT(5.0) || isT(6.0)) return sstep(0.3, 0.7, p);
            if (isT(7.0)) return sstep(0.1, 0.9, p);
            if (isT(36.0) || isT(37.0) || isT(38.0)) return sstep(0.4, 0.6, p);
            if (isT(41.0)) return sstep(0.45, 0.55, p);
            if (isT(42.0) || isT(43.0) || isT(44.0) || isT(45.0)) return sstep(0.25, 0.75, p);
            float e = mix(-0.05, 1.05, p);
            if (isT(10.0)) return cover(1.0 - uv.x, e, 0.02);
            if (isT(11.0)) return cover(uv.x, e, 0.02);
            if (isT(12.0)) return cover(uv.y, e, 0.02);
            if (isT(13.0)) return cover(1.0 - uv.y, e, 0.02);
            if (isT(14.0)) return cover((uv.x + 1.0 - uv.y) * 0.5, e, 0.03);
            vec2 c = (uv - 0.5) * vec2(aspect, 1.0);
            if (isT(15.0)) { float md = length(vec2(0.5 * aspect, 0.5)); return cover(length(c), p * md * 1.06, 0.012); }
            if (isT(16.0)) { float a = fract(atan(c.x, c.y) / 6.2831853 + 1.0); return cover(a, mix(-0.02, 1.02, p), 0.006); }
            if (isT(17.0)) return cover(fract(uv.y * 10.0), e, 0.02);
            if (isT(18.0)) return cover(abs(uv.x - 0.5) * 2.0, e, 0.02);
            if (isT(19.0)) return step(rnd(floor(uv * vec2(14.0 * aspect, 14.0))) * 0.98 + 0.01, p);
            if (isT(20.0)) { float v = mix(uv.x, fbm(uv * vec2(aspect, 1.0) * 3.0), 0.6); return cover(v, mix(-0.15, 1.15, p), 0.05); }
            if (isT(21.0)) return step(rnd(floor(uv * uSize / 3.0)) * 0.98 + 0.01, p);
            if (isT(40.0)) { float b = rnd(vec2(floor(uv.y * 18.0), floor(p * 12.0))); return step(0.5 + (b - 0.5) * 0.4, p); }
            return 1.0;
        }

        vec4 shade(vec2 uv0) {
            if (uType < 0.5) return tex(uv0);
            if (uType > 49.5) return cine(uv0);
            float p = uP;
            float k = 1.0 - abs(2.0 * p - 1.0);
            bool inc = uSide > 0.5;
            float aspect = uSize.x / uSize.y;
            vec2 uv = uv0;
            vec2 c = uv - 0.5;
            if (isT(36.0)) { float s = inc ? mix(0.5, 1.0, easeF(p)) : mix(1.0, 2.5, easeF(p)); uv = c / s + 0.5; }
            if (isT(7.0)) { float s = inc ? mix(1.03, 1.0, easeF(p)) : mix(1.0, 1.03, easeF(p)); uv = c / s + 0.5; }
            if (isT(37.0)) { float s = inc ? mix(2.5, 1.0, easeF(p)) : mix(1.0, 0.5, easeF(p)); uv = c / s + 0.5; }
            if (isT(38.0)) {
                float ang = inc ? -(1.0 - easeF(p)) * 3.14159 : easeF(p) * 3.14159;
                vec2 q = c * vec2(aspect, 1.0);
                float cs = cos(ang); float sn = sin(ang);
                q = vec2(q.x * cs - q.y * sn, q.x * sn + q.y * cs);
                uv = q / vec2(aspect, 1.0) / (1.0 + k * 0.6) + 0.5;
            }
            if (isT(43.0)) { float d = length(c * vec2(aspect, 1.0)); uv += c / max(d, 0.001) * sin(d * 40.0 - p * 30.0) * 0.02 * k; }
            if (isT(45.0)) {
                vec2 q = c * vec2(aspect, 1.0); float d = length(q);
                float a = k * 4.0 * (1.0 - smoothstep(0.0, 0.7, d));
                float cs = cos(a); float sn = sin(a);
                q = vec2(q.x * cs - q.y * sn, q.x * sn + q.y * cs);
                uv = q / vec2(aspect, 1.0) + 0.5;
            }
            if (isT(41.0)) { float cells = mix(200.0, 12.0, k); vec2 cl = vec2(cells * aspect, cells); uv = (floor(uv * cl) + 0.5) / cl; }
            if (isT(40.0)) {
                float blk = rnd(vec2(floor(uv.y * 24.0), floor(p * 20.0)));
                if (blk > 1.0 - k * 0.6) uv.x += (rnd(vec2(blk, p)) - 0.5) * 0.25 * k;
            }
            vec3 col;
            if (isT(5.0) || isT(7.0) || isT(34.0) || isT(35.0) || isT(36.0) || isT(37.0) || isT(38.0) || isT(44.0)) {
                vec3 acc = vec3(0.0);
                for (int i = 0; i < 12; i++) {
                    float f = float(i) / 11.0 - 0.5;
                    vec2 o;
                    if (isT(34.0) || isT(35.0)) o = vec2(f * 0.14 * k, 0.0);
                    else if (isT(5.0)) o = vec2(cos(f * 40.0), sin(f * 40.0)) * 0.014 * k * (0.3 + abs(f));
                    else if (isT(7.0)) o = vec2(cos(f * 40.0), sin(f * 40.0)) * 0.003 * k * (0.3 + abs(f));
                    else o = (uv - 0.5) * f * 0.2 * k * (isT(44.0) ? 2.0 : 1.0);
                    acc += mtex(uv + o).rgb;
                }
                col = acc / 12.0;
            } else if (isT(40.0) || isT(42.0)) {
                float amt = (isT(40.0) ? 0.03 : 0.025) * k;
                vec2 dir = isT(42.0) ? (uv - 0.5) * 2.0 : vec2(1.0, 0.0);
                col = vec3(mtex(uv + dir * amt).r, mtex(uv).g, mtex(uv - dir * amt).b);
            } else {
                col = mtex(uv).rgb;
            }
            float dip = inc ? clamp((1.0 - p) * 2.0, 0.0, 1.0) : clamp(p * 2.0, 0.0, 1.0);
            if (isT(2.0)) col *= 1.0 - dip;
            if (isT(3.0)) col = mix(col, vec3(1.0), dip);
            if (isT(4.0)) col += vec3(k * k * 1.2);
            if (isT(6.0)) {
                float x = uv0.x + uv0.y * 0.3 - mix(-0.3, 1.6, p);
                float band = exp(-(x * 3.0) * (x * 3.0));
                col += vec3(1.0, 0.55, 0.2) * band * 1.4 * k + vec3(1.0, 0.8, 0.6) * k * k * 0.5;
            }
            if (isT(44.0)) col += vec3(k * k * 0.8);
            if (isT(40.0)) col *= 1.0 - 0.25 * k * step(0.5, fract(uv0.y * uSize.y * 0.5));
            if (isT(21.0)) { float r = rnd(floor(uv0 * uSize / 3.0)) * 0.98 + 0.01; col += vec3(1.0, 0.9, 0.7) * (1.0 - smoothstep(0.0, 0.05, abs(r - p))) * 1.5; }
            if (isT(20.0)) {
                float v = mix(uv0.x, fbm(uv0 * vec2(aspect, 1.0) * 3.0), 0.6);
                col += vec3(0.25, 0.1, 0.35) * (1.0 - smoothstep(0.0, 0.08, abs(v - mix(-0.15, 1.15, p)))) * k;
            }
            float rv = reveal(uv0);
            float a = uMode > 1.5 ? 1.0 - rv : (uMode > 0.5 ? rv : 1.0);
            return vec4(clamp(col, 0.0, 1.0), a);
        }
    """

    /**
     * The preview shader for one transition. The type is built in as a constant, so the shader compiler
     * drops every other transition's code (one shader with all of them is too large for Android).
     */
    fun agsl(t: TransitionType): String = only(AGSL.replace("uniform float uType;", "const float uType = ${t.id};"), setOf(t.id), runtime = false)

    private val IS_T = Regex("""isT\(\s*([0-9]+(?:\.[0-9]+)?)\s*\)""")

    /**
     * Every isT(n) for a transition not in [keep] becomes the literal false (and, in the preview, the kept one
     * true), so the compiler drops the other transitions' code before it counts the program's size. In the
     * export ([runtime]) the kept ones still check uType, as both sides of a cut share one program.
     */
    private fun only(src: String, keep: Set<Float>, runtime: Boolean): String = IS_T.replace(src) { m ->
        val id = m.groupValues[1].toFloat()
        if (keep.any { kotlin.math.abs(it - id) < 0.5f }) (if (runtime) m.value else "true") else "false"
    }

    /** A compiled preview shader for [t], or null if this phone can't build it (the preview then fades). */
    private val thumbShaders = HashMap<Pair<TransitionType, Boolean>, android.graphics.RuntimeShader?>()

    /** The shader for one side of a transition thumbnail, built once and kept (built off the main thread). */
    fun thumbShader(t: TransitionType, incoming: Boolean): android.graphics.RuntimeShader? =
        synchronized(thumbShaders) { thumbShaders[t to incoming] } ?: shaderFor(t).also { sh -> synchronized(thumbShaders) { thumbShaders[t to incoming] = sh } }
    fun cachedThumbShader(t: TransitionType, incoming: Boolean): android.graphics.RuntimeShader? = synchronized(thumbShaders) { thumbShaders[t to incoming] }

    fun shaderFor(t: TransitionType): android.graphics.RuntimeShader? =
        if (android.os.Build.VERSION.SDK_INT < 33) null else try { android.graphics.RuntimeShader(agsl(t)) } catch (e: Throwable) {
            android.util.Log.w("TransitionPreview", "Shader for ${t.name} unavailable: ${e.message}"); null
        }

    private const val AGSL = """
        uniform shader content;
        uniform float2 uSize;
        uniform float uType;
        uniform float uP;
        uniform float uSide;
        uniform float uMode;
        vec4 tex(vec2 uv) { return vec4(content.eval(vec2(clamp(uv.x, 0.0, 1.0), 1.0 - clamp(uv.y, 0.0, 1.0)) * uSize)); }
    """ + CORE + """
        half4 main(float2 fc) {
            vec4 c = shade(vec2(fc.x / uSize.x, 1.0 - fc.y / uSize.y));
            return half4(c.rgb * c.a, c.a);
        }
    """

    /** The export shader with only [a] and [b] in it: the other transitions' code is dropped when it's compiled. */
    fun glslFor(a: TransitionType, b: TransitionType): String = only(GLSL_FRAGMENT, setOf(a.id, b.id), runtime = true)

    const val GLSL_VERTEX = """
        attribute vec4 aFramePosition;
        varying vec2 vTexSamplingCoord;
        void main() {
            gl_Position = aFramePosition;
            vTexSamplingCoord = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5);
        }
    """

    const val GLSL_FRAGMENT = """
        precision highp float;
        uniform sampler2D uTexSampler;
        uniform vec2 uSize;
        uniform float uType;
        uniform float uP;
        uniform float uSide;
        uniform float uMode;
        varying vec2 vTexSamplingCoord;
        vec4 tex(vec2 uv) { return texture2D(uTexSampler, clamp(uv, 0.0, 1.0)); }
    """ + CORE + """
        void main() {
            gl_FragColor = shade(vTexSamplingCoord);
            // Keeps every setting in use (never true), so the compiler doesn't drop one a transition doesn't need.
            if (uSize.x < -1.0 && uType < -1.0 && uP < -1.0 && uSide < -1.0 && uMode < -1.0) gl_FragColor = vec4(0.0);
        }
    """
}

/** What one clip does at its start (coming in) and end (going out) during export. */
data class TransitionWindows(
    val lengthMs: Long,
    val inType: TransitionType, val inMs: Long, val inMode: Int,
    val outType: TransitionType, val outMs: Long, val outMode: Int,
)

/** Export: runs the transition shader on a clip's first / last frames. */
@UnstableApi
class TransitionEffect(private val w: TransitionWindows) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = TransitionProgram(w, useHdr)
    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean =
        (w.inType == TransitionType.NONE || w.inMs <= 0) && (w.outType == TransitionType.NONE || w.outMs <= 0)
}

@UnstableApi
private class TransitionProgram(private val w: TransitionWindows, useHdr: Boolean) : BaseGlShaderProgram(useHdr, 1) {
    private val program: GlProgram = try {
        GlProgram(TransitionMath.GLSL_VERTEX, TransitionMath.glslFor(w.inType, w.outType))
    } catch (e: GlUtil.GlException) {
        throw VideoFrameProcessingException(e)
    }
    private var width = 1f
    private var height = 1f
    private var t0 = Long.MIN_VALUE

    init {
        program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        width = inputWidth.toFloat(); height = inputHeight.toFloat()
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        if (t0 == Long.MIN_VALUE) t0 = presentationTimeUs
        val local = (presentationTimeUs - t0) / 1000L
        var type = 0f; var p = 0f; var side = 0f; var mode = 0f
        if (w.inType != TransitionType.NONE && w.inMs > 0 && local < w.inMs) {
            type = w.inType.id; p = (local.toFloat() / w.inMs).coerceIn(0f, 1f); side = 1f; mode = w.inMode.toFloat()
        } else if (w.outType != TransitionType.NONE && w.outMs > 0 && local > w.lengthMs - w.outMs) {
            type = w.outType.id; p = (1f - (w.lengthMs - local).toFloat() / w.outMs).coerceIn(0f, 1f); side = 0f; mode = w.outMode.toFloat()
        }
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            // The compiler drops uniforms a transition doesn't use; setting one of those would fail, so each is optional.
            fun set(n: String, v: Float) { try { program.setFloatUniform(n, v) } catch (_: RuntimeException) { ExportNotes.skipped("$n (${w.inType.label} / ${w.outType.label})") } }
            try { program.setFloatsUniform("uSize", floatArrayOf(width, height)) } catch (_: RuntimeException) { ExportNotes.skipped("uSize (${w.inType.label} / ${w.outType.label})") }
            set("uType", type); set("uP", p); set("uSide", side); set("uMode", mode)
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    override fun release() {
        super.release()
        try { program.delete() } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    }
}

/** Live preview: applies a transition to a player's view (API 33+ shader, simple fade before that). */
object TransitionPreview {
    private val shaders = HashMap<android.view.View, Pair<TransitionType, Any?>>()

    fun apply(view: android.view.View, t: TransitionType, p: Float, incoming: Boolean) {
        val (tx, ty) = TransitionMath.move(t, p, incoming)
        view.translationX = tx * view.width
        view.translationY = ty * view.height
        view.translationZ = if (incoming) 2f else 0f
        val sh = if (android.os.Build.VERSION.SDK_INT >= 33) {
            val cur = shaders[view]
            (if (cur != null && cur.first == t) cur.second else TransitionMath.shaderFor(t).also { shaders[view] = t to it }) as android.graphics.RuntimeShader?
        } else null
        if (sh != null && view.width > 0 && view.height > 0) {
            view.alpha = 1f
            sh.setFloatUniform("uSize", view.width.toFloat(), view.height.toFloat())
            sh.setFloatUniform("uP", p)
            sh.setFloatUniform("uSide", if (incoming) 1f else 0f)
            sh.setFloatUniform("uMode", if (incoming && t.kind != TransitionKind.MOVE) 1f else 0f)
            view.setRenderEffect(android.graphics.RenderEffect.createRuntimeShaderEffect(sh, "content"))
        } else {
            view.alpha = if (incoming) (if (t.kind == TransitionKind.MOVE) 1f else TransitionMath.uniformReveal(t, p).let { if (t.kind == TransitionKind.MASK) p else it }) else 1f
        }
    }

    fun reset(view: android.view.View, visible: Boolean) {
        view.translationX = 0f; view.translationY = 0f; view.translationZ = 0f
        view.alpha = if (visible) 1f else 0f
        if (android.os.Build.VERSION.SDK_INT >= 31) view.setRenderEffect(null)
    }
}


/** Things the export had to leave out (shown as a short note when it finishes). */
object ExportNotes {
    private val items = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    fun clear() = items.clear()
    fun skipped(what: String) { if (items.add(what)) { android.util.Log.w("ExportNotes", "skipped transition setting $what"); com.dronedirector.veyrocutpro.ui.AppLog.w("Export: transition setting skipped — $what") } }
    /** A one-line note, or null when nothing was skipped. */
    fun note(): String? = if (items.isEmpty()) null else "Note: ${items.size} transition setting(s) couldn't be applied and were skipped (${items.take(3).joinToString(", ")})"
}
