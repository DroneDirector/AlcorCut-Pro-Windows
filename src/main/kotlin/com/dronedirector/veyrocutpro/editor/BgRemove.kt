package com.dronedirector.veyrocutpro.editor



/**
 * Background removal (Color → Detail → BACKGROUND, or a video layer's REMOVE BACKGROUND).
 * People: ML Kit selfie segmentation on every frame. Magic Mask: any object picked by you and
 * tracked through the clip ([MagicSel], MobileSAM). Edges are refined against the full-size picture
 * (joint bilateral upsampling), so hair and outlines follow the real edges.
 * [mode] 0 = transparent (layers below show through), 1 = blurred background, 2 = a colour.
 * [invert]: affect the selected object instead of its surroundings (e.g. blur a number plate).
 */
data class BgRemoval(
    val mode: Int = 1,
    val color: Int = 0xFF00B140.toInt(),
    /** Magic Mask selection (any object); null = people, found automatically. */
    val magic: MagicSel? = null,
    val invert: Boolean? = null,
)
