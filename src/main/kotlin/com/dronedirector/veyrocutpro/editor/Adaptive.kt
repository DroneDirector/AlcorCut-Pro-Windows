package com.dronedirector.veyrocutpro.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.ui.Pro
import com.dronedirector.veyrocutpro.ui.Text
import com.dronedirector.veyrocutpro.ui.hintedScroll

/**
 * One AI selection an adaptive preset makes: what to find (subject, sky, people parts) and the
 * adjustment it gets. [invert] = everything except it (the background).
 */
data class AdaptiveStep(val type: MaskType, val grade: ProGrade, val label: String, val parts: List<Int>? = null, val invert: Boolean = false)

/** A preset that finds things in each photo it's used on, so the same look fits different pictures. */
data class AdaptivePreset(val name: String, val hint: String, val steps: List<AdaptiveStep>)

object AdaptivePresets {
    val ALL = listOf(
        AdaptivePreset("Pop the subject", "Brighter, crisper subject", listOf(AdaptiveStep(MaskType.SUBJECT, ProGrade(exposure = 0.3f, contrast = 0.12f, vibrance = 0.15f), "Subject: pop"))),
        AdaptivePreset("Darken background", "Background a little darker and calmer", listOf(AdaptiveStep(MaskType.SUBJECT, ProGrade(exposure = -0.45f, saturation = -0.15f), "Background: darker", invert = true))),
        AdaptivePreset("Spotlight", "Subject brighter, background darker", listOf(
            AdaptiveStep(MaskType.SUBJECT, ProGrade(exposure = 0.2f, contrast = 0.08f), "Subject: brighter"),
            AdaptiveStep(MaskType.SUBJECT, ProGrade(exposure = -0.35f), "Background: darker", invert = true))),
        AdaptivePreset("Blue sky", "Deeper blue sky with detail", listOf(AdaptiveStep(MaskType.SKY, ProGrade(exposure = -0.2f, highlights = -0.3f, temperature = -0.15f, saturation = 0.18f, dehaze = 0.1f), "Sky: blue"))),
        AdaptivePreset("Dramatic sky", "Dark, moody sky", listOf(AdaptiveStep(MaskType.SKY, ProGrade(exposure = -0.5f, contrast = 0.3f, highlights = -0.5f, dehaze = 0.3f, saturation = -0.1f), "Sky: dramatic"))),
        AdaptivePreset("Golden sky", "Warm sunset glow in the sky", listOf(AdaptiveStep(MaskType.SKY, ProGrade(exposure = -0.15f, temperature = 0.35f, tint = 0.08f, saturation = 0.2f), "Sky: golden"))),
        AdaptivePreset("Polished portrait", "Softer skin, brighter eyes, whiter teeth", listOf(
            AdaptiveStep(MaskType.PEOPLE, ProGrade(contrast = -0.08f, exposure = 0.08f), "Skin: softer", parts = listOf(1)),
            AdaptiveStep(MaskType.PEOPLE, ProGrade(exposure = 0.25f, saturation = -0.2f), "Eye whites: brighter", parts = listOf(3)),
            AdaptiveStep(MaskType.PEOPLE, ProGrade(exposure = 0.15f, contrast = 0.15f, saturation = 0.15f), "Irises: clearer", parts = listOf(4)),
            AdaptiveStep(MaskType.PEOPLE, ProGrade(exposure = 0.2f, saturation = -0.4f), "Teeth: whiter", parts = listOf(7)))),
        AdaptivePreset("Brighten faces", "Lifts faces in shade or backlight", listOf(AdaptiveStep(MaskType.PEOPLE, ProGrade(exposure = 0.4f, shadows = 0.3f), "Faces: brighter", parts = listOf(1)))),
        AdaptivePreset("Hair shine", "More shine and detail in the hair", listOf(AdaptiveStep(MaskType.PEOPLE, ProGrade(contrast = 0.2f, highlights = 0.15f, curveLights = 0.1f), "Hair: shine", parts = listOf(8)))),
        AdaptivePreset("Landscape", "Blue sky plus a stronger foreground", listOf(
            AdaptiveStep(MaskType.SKY, ProGrade(exposure = -0.25f, highlights = -0.35f, temperature = -0.1f, saturation = 0.15f), "Sky: deeper"),
            AdaptiveStep(MaskType.SKY, ProGrade(contrast = 0.12f, vibrance = 0.2f, shadows = 0.15f), "Land: stronger", invert = true))),
    )
}

/** The adaptive presets as cards; [busy] text while one is being worked out. */
@Composable
fun AdaptivePresetSection(busy: String?, onApply: (AdaptivePreset) -> Unit) {
    SectionTitle("ADAPTIVE PRESETS (AI)")
    Text("These find the subject, sky or people in each photo and adjust only those, so one preset fits every picture. Each makes masks you can change in Masks.", color = Pro.Dim, fontSize = 11.sp)
    busy?.let { Text(it, color = Pro.Amber, fontSize = 11.sp) }
    Row(Modifier.hintedScroll(horizontal = true).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AdaptivePresets.ALL.forEach { p ->
            Column(Modifier.width(140.dp).clip(RoundedCornerShape(10.dp)).background(Ed.Raised).clickable(enabled = busy == null) { onApply(p) }.padding(10.dp)) {
                Text(p.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(p.hint, color = Pro.Dim, fontSize = 10.sp, maxLines = 3)
            }
        }
    }
}
