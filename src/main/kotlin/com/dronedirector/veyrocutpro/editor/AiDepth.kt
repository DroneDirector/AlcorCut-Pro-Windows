package com.dronedirector.veyrocutpro.editor



/**
 * AI depth for a clip (Color → Detail → AI DEPTH): how far away
 * every part of the picture is, worked out by Depth Anything V2 Small (Apache 2.0, on the phone),
 * then used for a lens blur (depth of field), haze / fog, or a darker / calmer background.
 * [mode] 0 = depth of field, 1 = fog, 2 = background light. [focus] = depth kept sharp (1 = nearest),
 * [range] = how deep the sharp zone is, [amount] = strength. Depths: 0 = far … 1 = near.
 */
data class DepthFx(
    val id: String = java.util.UUID.randomUUID().toString(),
    val frames: Int = 0,
    val mode: Int = 0,
    val focus: Float = 0.85f,
    val range: Float = 0.15f,
    val amount: Float = 0.6f,
    val fogColor: Int = 0xFFC9D3DD.toInt(),
    val bgExposure: Float = -0.6f,
    val bgSaturation: Float = -0.3f,
    /** Show the depth map itself in the preview (to set the focus). */
    val showMap: Boolean? = null,
)
