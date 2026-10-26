package com.dronedirector.veyrocutpro.editor



/** One keyframe of a layer: where it is, how big and how visible at timeline time [t] (ms). */
data class LayerKey(val t: Long, val x: Float, val y: Float, val size: Float, val opacity: Float)

/** How a picture layer mixes with the video under it. */
enum class LayerBlend(val label: String) {
    NORMAL("Normal"), SCREEN("Screen"), MULTIPLY("Multiply"), OVERLAY("Overlay"), ADD("Add"),
    LIGHTEN("Lighten"), DARKEN("Darken"), SOFT_LIGHT("Soft light"), DIFFERENCE("Difference");

    val compose: androidx.compose.ui.graphics.BlendMode get() = when (this) {
        NORMAL -> androidx.compose.ui.graphics.BlendMode.SrcOver
        SCREEN -> androidx.compose.ui.graphics.BlendMode.Screen
        MULTIPLY -> androidx.compose.ui.graphics.BlendMode.Multiply
        OVERLAY -> androidx.compose.ui.graphics.BlendMode.Overlay
        ADD -> androidx.compose.ui.graphics.BlendMode.Plus
        LIGHTEN -> androidx.compose.ui.graphics.BlendMode.Lighten
        DARKEN -> androidx.compose.ui.graphics.BlendMode.Darken
        SOFT_LIGHT -> androidx.compose.ui.graphics.BlendMode.Softlight
        DIFFERENCE -> androidx.compose.ui.graphics.BlendMode.Difference
    }
}
