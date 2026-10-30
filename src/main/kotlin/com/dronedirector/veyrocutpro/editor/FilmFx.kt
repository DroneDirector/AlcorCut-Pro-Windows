package com.dronedirector.veyrocutpro.editor



/**
 * Film emulation, lens correction and deflicker for a clip (Color → FILM & LENS). Written for
 * AlcorCut Pro (no third-party code).
 */
data class FilmLook(
    /** Red-orange glow around bright highlights, like film's halation (0..1). */
    val halation: Float = 0f,
    /** Soft glow from the brightest parts (0..1). */
    val bloom: Float = 0f,
    /** Gentle frame-to-frame wobble of a film gate (0..1). */
    val weave: Float = 0f,
    /** Film grain amount (0..1) and its size (0 fine .. 1 coarse). */
    val grain: Float = 0f,
    val grainSize: Float = 0.4f,
    /** Lens distortion fix: + straightens barrel (wide / action cams), − straightens pincushion. */
    val lensK: Float = 0f,
    /** Chromatic aberration fix (colour fringes at the edges), −1..1. */
    val lensCa: Float = 0f,
    /** Vignette fix: brightens dark corners (0..1). */
    val lensVig: Float = 0f,
    /** Deflicker strength (0 = off): evens out brightness flicker (timelapse, lights, hyperlapse). */
    val deflicker: Float = 0f,
    /** Name of the preset last picked (display only). */
    val preset: String? = null,
    /** Sky replacement: which sky ([VideoSky.NAMES], −1 = off), strength, the horizon (0..1 from the top), how much counts as sky, and light matching. */
    val sky: Int = -1,
    val skyAmount: Float = 1f,
    val horizon: Float = 0.45f,
    val skyTol: Float = 0.5f,
    val skyLight: Float = 0.3f,
) {
    val isZero: Boolean get() = copy(preset = null) == FilmLook()
}
