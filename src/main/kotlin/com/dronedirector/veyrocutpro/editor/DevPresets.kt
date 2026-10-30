package com.dronedirector.veyrocutpro.editor

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.ui.Pro
import com.dronedirector.veyrocutpro.ui.Text
import com.dronedirector.veyrocutpro.ui.hintedScroll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** A built-in preset: changes made on top of the current edit (so the amount slider can fade it in). */
class BuiltInPreset(val name: String, val group: String, val grade: (ProGrade) -> ProGrade, val detail: (Detail) -> Detail = { it })

/**
 * Recommended presets: looks at the photo (what's in it, on the phone, and its light and colour) and picks
 * up to six built-in looks that suit it, each with a short reason.
 */
object PresetAdvisor {
    class Pick(val preset: BuiltInPreset, val why: String)

    /** What's in a picture: AI labels (lower case), or empty if they can't be found. */
    fun labels(b: Bitmap): List<String> = try {
        val l = com.google.mlkit.vision.label.ImageLabeling.getClient(com.google.mlkit.vision.label.defaults.ImageLabelerOptions.Builder().setConfidenceThreshold(0.55f).build())
        try {
            com.google.android.gms.tasks.Tasks.await(l.process(com.google.mlkit.vision.common.InputImage.fromBitmap(b, 0)), 20, java.util.concurrent.TimeUnit.SECONDS)
                .sortedByDescending { it.confidence }.take(10).map { it.text.lowercase() }
        } finally { l.close() }
    } catch (_: Throwable) { emptyList() }

    fun suggest(b: Bitmap, labels: List<String>): List<Pick> {
        // Light and colour: average brightness, colourfulness, warmth, how much is sky blue.
        val s = Bitmap.createScaledBitmap(b, 64, 64, true)
        val px = IntArray(64 * 64); s.getPixels(px, 0, 64, 0, 0, 64, 64); if (s !== b) s.recycle()
        var lum = 0.0; var sat = 0.0; var warm = 0.0; var blue = 0; var lo = 1.0; var hi = 0.0
        for (c in px) {
            val r = ((c shr 16) and 255) / 255.0; val g = ((c shr 8) and 255) / 255.0; val bl = (c and 255) / 255.0
            val y = 0.299 * r + 0.587 * g + 0.114 * bl
            lum += y; sat += maxOf(r, g, bl) - minOf(r, g, bl); warm += r - bl
            if (bl > r + 0.08 && bl > g - 0.02 && y > 0.35) blue++
            lo = minOf(lo, y); hi = maxOf(hi, y)
        }
        val n = px.size.toDouble(); lum /= n; sat /= n; warm /= n
        fun has(vararg w: String) = labels.any { l -> w.any { l.contains(it) } }
        val out = LinkedHashMap<String, String>()
        fun add(name: String, why: String) { if (name !in out && out.size < 6) out[name] = why }
        val night = lum < 0.22
        if (has("person", "selfie", "smile", "face", "skin", "hair", "beard", "child", "bride")) { add("Soft Skin", "people in the photo"); add("Warm Portrait", "people in the photo"); add("Clean Studio", "people in the photo") }
        if (has("food", "dish", "cuisine", "dessert", "cake", "meal", "drink", "fruit", "vegetable")) { add("Fresh Food", "food"); add("Bright & Airy", "food") }
        if (night) { add("Night City", "a dark, night-time photo"); add("Neon Night", "a dark, night-time photo"); add("Low Key", "a dark photo") }
        if (has("sunset", "dusk", "sunrise") || (warm > 0.12 && lum < 0.55)) { add("Golden Hour", "warm, low sun"); add("Rooftop Sunset", "warm, low sun") }
        if (has("beach", "sea", "ocean", "swimming", "coast", "lagoon", "boat")) { add("Tropical", "sea and beach"); add("Summer Pop", "sea and beach") }
        if (has("sky", "cloud", "mountain", "lake", "landscape", "hill", "cliff", "river", "nature") || blue > n * 0.18) { add("Vivid Landscape", "a landscape"); add("Big Sky", "lots of sky") }
        if (has("forest", "tree", "plant", "grass", "jungle", "leaf", "flower", "garden")) { add("Deep Greens", "greenery"); add("Autumn", "trees and plants") }
        if (has("building", "skyscraper", "city", "architecture", "tower", "bridge", "street", "road")) { add("Architecture", "buildings"); add("Street Gritty", "a city scene"); add("Concrete", "buildings") }
        if (has("snow", "ice", "winter", "glacier")) { add("Nordic", "snow and cold light"); add("Cold Thriller", "a cold scene") }
        if (has("fog", "mist", "haze") || (hi - lo < 0.45 && !night)) add("Misty", "soft, low-contrast light")
        if (sat < 0.12) add("B&W High Contrast", "few colours — strong in black & white")
        // Always a film look and a black & white to choose from.
        add("Portra", "a natural film look that suits most photos")
        add("B&W Neutral", "a clean black & white version")
        add("Kodak Warm", "a warm film look")
        return out.mapNotNull { (k, why) -> DevPresets.all.firstOrNull { it.name == k }?.let { Pick(it, why) } }
    }
}

object DevPresets {
    private fun hsl(vararg b: Pair<Int, HslBand>): List<HslBand> = List(8) { i -> b.firstOrNull { it.first == i }?.second ?: HslBand() }
    private fun w(h: Float, a: Float, l: Float = 0f) = Wheel(h, a, l)
    private fun mono(g: ProGrade) = g.copy(saturation = -1f, vibrance = 0f)
    private fun curve(vararg p: Float) = p.toList()

    val all: List<BuiltInPreset> = listOf(
        // ---- Portrait
        BuiltInPreset("Soft Skin", "Portrait", { it.copy(exposure = it.exposure + 0.15f, contrast = -0.1f, highlights = -0.2f, shadows = 0.15f, vibrance = 0.1f, saturation = -0.05f, hsl = hsl(1 to HslBand(0.05f, -0.1f, 0.1f))) }, { it.copy(texture = -0.2f, clarity = -0.1f) }),
        BuiltInPreset("Warm Portrait", "Portrait", { it.copy(temperature = 0.18f, tint = 0.05f, contrast = 0.1f, shadows = 0.1f, vibrance = 0.12f, gradeHighlights = w(40f, 0.15f)) }),
        BuiltInPreset("Clean Studio", "Portrait", { it.copy(exposure = it.exposure + 0.1f, whites = 0.15f, blacks = -0.1f, contrast = 0.12f, saturation = -0.08f) }, { it.copy(sharpen = 0.35f, sharpenMasking = 0.6f) }),
        BuiltInPreset("Matte Portrait", "Portrait", { it.copy(contrast = -0.15f, fade = 0.35f, curveShadows = 0.1f, vibrance = 0.05f, gradeShadows = w(210f, 0.15f)) }),
        BuiltInPreset("Glow", "Portrait", { it.copy(exposure = it.exposure + 0.2f, highlights = -0.3f, whites = 0.2f, contrast = -0.1f, temperature = 0.08f) }, { it.copy(clarity = -0.25f) }),
        BuiltInPreset("Dramatic Portrait", "Portrait", { it.copy(contrast = 0.3f, highlights = -0.3f, shadows = -0.1f, blacks = -0.2f, saturation = -0.15f) }, { it.copy(clarity = 0.25f, vignette = -0.3f) }),
        // ---- Landscape
        BuiltInPreset("Vivid Landscape", "Landscape", { it.copy(vibrance = 0.35f, saturation = 0.08f, contrast = 0.15f, highlights = -0.35f, shadows = 0.25f, dehaze = 0.15f) }, { it.copy(clarity = 0.2f) }),
        BuiltInPreset("Golden Hour", "Landscape", { it.copy(temperature = 0.3f, tint = 0.08f, vibrance = 0.2f, highlights = -0.2f, gradeHighlights = w(35f, 0.25f), gradeShadows = w(220f, 0.1f)) }),
        BuiltInPreset("Blue Hour", "Landscape", { it.copy(temperature = -0.25f, tint = 0.05f, contrast = 0.1f, vibrance = 0.15f, gradeShadows = w(230f, 0.25f), gradeHighlights = w(300f, 0.1f)) }),
        BuiltInPreset("Deep Greens", "Landscape", { it.copy(hsl = hsl(2 to HslBand(0.15f, -0.1f, 0f), 3 to HslBand(0.1f, 0.15f, -0.2f)), contrast = 0.1f, vibrance = 0.1f) }),
        BuiltInPreset("Big Sky", "Landscape", { it.copy(highlights = -0.6f, whites = 0.1f, shadows = 0.3f, hsl = hsl(5 to HslBand(-0.05f, 0.2f, -0.25f)), dehaze = 0.2f) }),
        BuiltInPreset("Misty", "Landscape", { it.copy(contrast = -0.25f, dehaze = -0.2f, fade = 0.2f, saturation = -0.2f, temperature = -0.05f) }, { it.copy(clarity = -0.2f) }),
        // ---- B&W
        BuiltInPreset("B&W Neutral", "B&W", { mono(it).copy(contrast = 0.1f) }),
        BuiltInPreset("B&W High Contrast", "B&W", { mono(it).copy(contrast = 0.45f, whites = 0.2f, blacks = -0.3f) }, { it.copy(clarity = 0.25f) }),
        BuiltInPreset("B&W Low Contrast", "B&W", { mono(it).copy(contrast = -0.3f, fade = 0.2f) }),
        BuiltInPreset("B&W Soft", "B&W", { mono(it).copy(contrast = -0.1f, highlights = -0.2f, shadows = 0.2f) }, { it.copy(clarity = -0.15f) }),
        BuiltInPreset("B&W Selenium", "B&W", { mono(it).copy(contrast = 0.2f, gradeShadows = w(280f, 0.18f), gradeHighlights = w(260f, 0.06f)) }),
        BuiltInPreset("B&W Sepia", "B&W", { mono(it).copy(contrast = 0.1f, gradeShadows = w(30f, 0.25f), gradeHighlights = w(45f, 0.2f)) }),
        BuiltInPreset("B&W Film Grain", "B&W", { mono(it).copy(contrast = 0.25f, fade = 0.1f) }, { it.copy(grain = 0.35f, grainSize = 0.1f, grainRough = 0.1f) }),
        BuiltInPreset("B&W Infrared", "B&W", { mono(it).copy(contrast = 0.35f, whites = 0.3f, hsl = hsl(2 to HslBand(0f, 0f, 0.8f), 3 to HslBand(0f, 0f, 0.9f), 5 to HslBand(0f, 0f, -0.6f))) }, { it.copy(clarity = -0.3f) }),
        // ---- Film
        BuiltInPreset("Kodak Warm", "Film", { it.copy(temperature = 0.12f, contrast = 0.12f, curveShadows = 0.08f, gradeShadows = w(200f, 0.12f), gradeHighlights = w(40f, 0.18f), hsl = hsl(1 to HslBand(0.05f, 0.1f, 0.05f))) }, { it.copy(grain = 0.15f) }),
        BuiltInPreset("Fuji Cool", "Film", { it.copy(temperature = -0.08f, tint = -0.06f, contrast = 0.1f, hsl = hsl(3 to HslBand(0.15f, -0.15f, 0f), 5 to HslBand(-0.1f, 0.1f, 0f)), gradeShadows = w(170f, 0.15f)) }, { it.copy(grain = 0.15f) }),
        BuiltInPreset("Portra", "Film", { it.copy(contrast = -0.05f, saturation = -0.1f, temperature = 0.08f, fade = 0.12f, hsl = hsl(0 to HslBand(0.05f, -0.1f, 0f), 1 to HslBand(0f, -0.05f, 0.1f))) }, { it.copy(grain = 0.12f) }),
        BuiltInPreset("Ektar Punch", "Film", { it.copy(vibrance = 0.35f, contrast = 0.2f, hsl = hsl(0 to HslBand(0f, 0.15f, 0f), 5 to HslBand(0f, 0.2f, -0.1f))) }, { it.copy(grain = 0.1f) }),
        BuiltInPreset("Cross Process", "Film", { it.copy(curveR = curve(0f, 0f, 0.25f, 0.2f, 0.75f, 0.82f, 1f, 1f), curveB = curve(0f, 0.1f, 0.5f, 0.45f, 1f, 0.85f), contrast = 0.1f, saturation = 0.1f) }),
        BuiltInPreset("Faded Film", "Film", { it.copy(fade = 0.4f, contrast = -0.15f, saturation = -0.15f, gradeShadows = w(200f, 0.12f), temperature = 0.05f) }, { it.copy(grain = 0.25f) }),
        BuiltInPreset("Slide Film", "Film", { it.copy(contrast = 0.35f, saturation = 0.15f, blacks = -0.2f, highlights = -0.15f) }),
        BuiltInPreset("Expired", "Film", { it.copy(temperature = 0.2f, tint = 0.15f, fade = 0.25f, contrast = -0.1f, gradeHighlights = w(60f, 0.25f), gradeShadows = w(300f, 0.15f)) }, { it.copy(grain = 0.3f, vignette = -0.2f) }),
        // ---- Cinematic
        BuiltInPreset("Teal & Orange", "Cinematic", { it.copy(contrast = 0.15f, gradeShadows = w(190f, 0.35f), gradeHighlights = w(35f, 0.3f), hsl = hsl(1 to HslBand(0f, 0.15f, 0.05f), 4 to HslBand(0.1f, 0.1f, -0.1f))) }),
        BuiltInPreset("Bleach Bypass", "Cinematic", { it.copy(contrast = 0.4f, saturation = -0.45f, highlights = -0.1f) }, { it.copy(clarity = 0.2f) }),
        BuiltInPreset("Noir Teal", "Cinematic", { it.copy(contrast = 0.3f, saturation = -0.4f, gradeShadows = w(185f, 0.3f), blacks = -0.2f) }, { it.copy(vignette = -0.3f) }),
        BuiltInPreset("Blockbuster", "Cinematic", { it.copy(contrast = 0.25f, vibrance = 0.15f, gradeShadows = w(200f, 0.3f), gradeMids = w(30f, 0.1f), gradeHighlights = w(40f, 0.25f), highlights = -0.2f) }),
        BuiltInPreset("Desert Heat", "Cinematic", { it.copy(temperature = 0.35f, tint = 0.05f, contrast = 0.15f, saturation = -0.1f, gradeHighlights = w(45f, 0.3f)) }),
        BuiltInPreset("Cold Thriller", "Cinematic", { it.copy(temperature = -0.3f, tint = -0.1f, contrast = 0.2f, saturation = -0.25f, gradeShadows = w(200f, 0.2f)) }, { it.copy(vignette = -0.25f) }),
        BuiltInPreset("Neon Night", "Cinematic", { it.copy(contrast = 0.2f, vibrance = 0.4f, gradeShadows = w(270f, 0.3f), gradeHighlights = w(320f, 0.2f), hsl = hsl(6 to HslBand(0f, 0.3f, 0f), 7 to HslBand(0f, 0.3f, 0f))) }),
        BuiltInPreset("Anamorphic", "Cinematic", { it.copy(contrast = 0.15f, highlights = -0.25f, fade = 0.1f, gradeShadows = w(210f, 0.2f), gradeHighlights = w(30f, 0.15f)) }, { it.copy(vignette = -0.25f, vignetteRound = -0.5f) }),
        // ---- Vintage
        BuiltInPreset("70s", "Vintage", { it.copy(temperature = 0.25f, fade = 0.25f, contrast = -0.1f, saturation = -0.1f, gradeShadows = w(30f, 0.15f), hsl = hsl(3 to HslBand(-0.3f, -0.2f, 0f))) }, { it.copy(grain = 0.25f) }),
        BuiltInPreset("Polaroid", "Vintage", { it.copy(fade = 0.3f, contrast = -0.1f, temperature = 0.1f, tint = -0.05f, gradeShadows = w(180f, 0.15f), gradeHighlights = w(55f, 0.15f)) }, { it.copy(vignette = -0.3f) }),
        BuiltInPreset("Old Print", "Vintage", { it.copy(saturation = -0.5f, fade = 0.3f, contrast = 0.05f, gradeHighlights = w(40f, 0.2f), gradeShadows = w(25f, 0.2f)) }, { it.copy(grain = 0.35f, vignette = -0.35f) }),
        BuiltInPreset("Lomo", "Vintage", { it.copy(contrast = 0.35f, saturation = 0.25f, curveShadows = -0.1f) }, { it.copy(vignette = -0.6f) }),
        BuiltInPreset("Dusty Rose", "Vintage", { it.copy(tint = 0.15f, fade = 0.2f, saturation = -0.15f, gradeHighlights = w(340f, 0.15f)) }),
        BuiltInPreset("Tintype", "Vintage", { mono(it).copy(contrast = 0.3f, gradeMids = w(35f, 0.15f), fade = 0.1f) }, { it.copy(vignette = -0.45f, grain = 0.2f) }),
        // ---- Urban
        BuiltInPreset("Street Gritty", "Urban", { it.copy(contrast = 0.3f, saturation = -0.3f, highlights = -0.2f, blacks = -0.15f) }, { it.copy(clarity = 0.4f, texture = 0.2f) }),
        BuiltInPreset("Concrete", "Urban", { it.copy(saturation = -0.5f, contrast = 0.15f, temperature = -0.08f) }, { it.copy(clarity = 0.2f) }),
        BuiltInPreset("Night City", "Urban", { it.copy(temperature = -0.15f, contrast = 0.2f, vibrance = 0.25f, shadows = 0.15f, gradeShadows = w(220f, 0.2f)) }),
        BuiltInPreset("Architecture", "Urban", { it.copy(contrast = 0.15f, highlights = -0.3f, shadows = 0.25f, saturation = -0.1f) }, { it.copy(clarity = 0.25f, sharpen = 0.4f, sharpenMasking = 0.5f) }),
        BuiltInPreset("Rooftop Sunset", "Urban", { it.copy(temperature = 0.25f, vibrance = 0.25f, highlights = -0.3f, gradeHighlights = w(25f, 0.3f), gradeShadows = w(260f, 0.15f)) }),
        BuiltInPreset("Muted Urban", "Urban", { it.copy(saturation = -0.25f, fade = 0.15f, contrast = 0.05f, curveHighlights = -0.1f) }),
        // ---- Food & product
        BuiltInPreset("Fresh Food", "Food", { it.copy(exposure = it.exposure + 0.15f, vibrance = 0.3f, contrast = 0.1f, temperature = 0.05f) }, { it.copy(texture = 0.2f) }),
        BuiltInPreset("Dark Food", "Food", { it.copy(exposure = it.exposure - 0.2f, contrast = 0.2f, highlights = -0.2f, shadows = -0.1f, vibrance = 0.15f) }, { it.copy(vignette = -0.35f) }),
        BuiltInPreset("Bright & Airy", "Food", { it.copy(exposure = it.exposure + 0.35f, highlights = -0.2f, whites = 0.15f, contrast = -0.1f, saturation = -0.05f) }),
        BuiltInPreset("Product White", "Food", { it.copy(exposure = it.exposure + 0.25f, whites = 0.35f, blacks = -0.1f, contrast = 0.1f, saturation = -0.05f) }, { it.copy(sharpen = 0.4f, sharpenMasking = 0.6f) }),
        // ---- Travel / bright
        BuiltInPreset("Tropical", "Travel", { it.copy(vibrance = 0.4f, hsl = hsl(4 to HslBand(0.1f, 0.25f, 0f), 5 to HslBand(-0.05f, 0.2f, 0f)), contrast = 0.1f, temperature = 0.05f) }),
        BuiltInPreset("Summer Pop", "Travel", { it.copy(exposure = it.exposure + 0.15f, vibrance = 0.35f, contrast = 0.15f, temperature = 0.1f) }),
        BuiltInPreset("Nordic", "Travel", { it.copy(temperature = -0.15f, saturation = -0.2f, contrast = 0.1f, fade = 0.1f, hsl = hsl(5 to HslBand(0f, -0.1f, -0.1f))) }),
        BuiltInPreset("Autumn", "Travel", { it.copy(temperature = 0.15f, hsl = hsl(1 to HslBand(0f, 0.25f, 0f), 2 to HslBand(-0.2f, 0.2f, 0f), 3 to HslBand(-0.35f, -0.1f, 0f)), contrast = 0.1f) }),
        // ---- Moody
        BuiltInPreset("Moody", "Moody", { it.copy(exposure = it.exposure - 0.2f, contrast = 0.2f, saturation = -0.25f, highlights = -0.3f, gradeShadows = w(200f, 0.2f)) }, { it.copy(vignette = -0.3f) }),
        BuiltInPreset("Forest Moody", "Moody", { it.copy(exposure = it.exposure - 0.15f, hsl = hsl(3 to HslBand(0.2f, -0.35f, -0.25f), 2 to HslBand(-0.1f, -0.3f, 0f)), contrast = 0.15f, fade = 0.1f) }),
        BuiltInPreset("Stormy", "Moody", { it.copy(temperature = -0.1f, contrast = 0.3f, highlights = -0.5f, dehaze = 0.3f, saturation = -0.3f) }, { it.copy(clarity = 0.3f) }),
        BuiltInPreset("Low Key", "Moody", { it.copy(exposure = it.exposure - 0.4f, contrast = 0.25f, blacks = -0.3f, shadows = -0.1f) }, { it.copy(vignette = -0.4f) }),
    )

    // ------------------------------------------------------------------------- .xmp presets

    /** Reads a Camera Raw .xmp preset or sidecar into a grade and detail (unknown keys are skipped). */
    fun parseXmp(text: String, base: ProGrade = ProGrade(), detail: Detail = Detail()): Pair<ProGrade, Detail>? {
        val kv = HashMap<String, String>()
        Regex("""crs:([A-Za-z0-9]+)\s*=\s*"([^"]*)"""").findAll(text).forEach { kv[it.groupValues[1]] = it.groupValues[2] }
        Regex("""<crs:([A-Za-z0-9]+)>([^<]*)</crs:\1>""").findAll(text).forEach { kv[it.groupValues[1]] = it.groupValues[2].trim() }
        if (kv.isEmpty()) return null
        fun f(k: String): Float? = kv[k]?.trim()?.removePrefix("+")?.toFloatOrNull()
        fun p(k: String) = f(k)?.div(100f)
        var g = base; var d = detail
        f("Exposure2012")?.let { g = g.copy(exposure = it) }
        p("Contrast2012")?.let { g = g.copy(contrast = it) }
        p("Highlights2012")?.let { g = g.copy(highlights = it) }
        p("Shadows2012")?.let { g = g.copy(shadows = it) }
        p("Whites2012")?.let { g = g.copy(whites = it) }
        p("Blacks2012")?.let { g = g.copy(blacks = it) }
        p("Vibrance")?.let { g = g.copy(vibrance = it) }
        p("Saturation")?.let { g = g.copy(saturation = it) }
        p("Dehaze")?.let { g = g.copy(dehaze = it) }
        p("ParametricHighlights")?.let { g = g.copy(curveHighlights = it) }
        p("ParametricLights")?.let { g = g.copy(curveLights = it) }
        p("ParametricDarks")?.let { g = g.copy(curveDarks = it) }
        p("ParametricShadows")?.let { g = g.copy(curveShadows = it) }
        p("IncrementalTemperature")?.let { g = g.copy(temperature = it) }
        p("IncrementalTint")?.let { g = g.copy(tint = it) }
        if (kv["WhiteBalance"] == "Custom") {
            f("Temperature")?.let { g = g.copy(temperature = ((it - 5500f) / 4000f).coerceIn(-1f, 1f)) }
            f("Tint")?.let { g = g.copy(tint = (it / 150f).coerceIn(-1f, 1f)) }
        }
        val names = listOf("Red", "Orange", "Yellow", "Green", "Aqua", "Blue", "Purple", "Magenta")
        if (names.any { kv.containsKey("HueAdjustment$it") || kv.containsKey("SaturationAdjustment$it") || kv.containsKey("LuminanceAdjustment$it") }) {
            g = g.copy(hsl = names.mapIndexed { i, n -> HslBand(p("HueAdjustment$n") ?: g.hsl[i].hue, p("SaturationAdjustment$n") ?: g.hsl[i].sat, p("LuminanceAdjustment$n") ?: g.hsl[i].lum) })
        }
        fun wheel(prefix: String, old: Wheel?): Wheel? {
            val h = f("${prefix}Hue"); val s = f("${prefix}Sat"); val l = f("${prefix}Lum")
            if (h == null && s == null && l == null) return old
            return Wheel(h ?: old?.hue ?: 0f, (s ?: 0f) / 100f, (l ?: 0f) / 100f)
        }
        g = g.copy(
            gradeShadows = wheel("ColorGradeShadow", g.gradeShadows) ?: g.gradeShadows,
            gradeMids = wheel("ColorGradeMidtone", g.gradeMids) ?: g.gradeMids,
            gradeHighlights = wheel("ColorGradeHighlight", g.gradeHighlights) ?: g.gradeHighlights,
            gradeGlobal = wheel("ColorGradeGlobal", g.gradeGlobal),
        )
        // Older split toning.
        if (kv.containsKey("SplitToningShadowSaturation") && !kv.containsKey("ColorGradeShadowSat")) {
            g = g.copy(gradeShadows = Wheel(f("SplitToningShadowHue") ?: 0f, (f("SplitToningShadowSaturation") ?: 0f) / 100f), gradeHighlights = Wheel(f("SplitToningHighlightHue") ?: 0f, (f("SplitToningHighlightSaturation") ?: 0f) / 100f))
        }
        f("ColorGradeBlending")?.let { g = g.copy(gradeBlend = (it / 100f - 0.5f).coerceIn(-0.5f, 0.5f)) }
        p("SplitToningBalance")?.let { g = g.copy(gradeBalance = it) }
        val cal = Calibration(p("ShadowTint") ?: 0f, p("RedHue") ?: 0f, p("RedSaturation") ?: 0f, p("GreenHue") ?: 0f, p("GreenSaturation") ?: 0f, p("BlueHue") ?: 0f, p("BlueSaturation") ?: 0f)
        if (!cal.isZero) g = g.copy(calib = cal)
        // Tone curves (0..255 points).
        fun pts(key: String): List<Float>? {
            val block = Regex("""<crs:$key>(.*?)</crs:$key>""", RegexOption.DOT_MATCHES_ALL).find(text)?.groupValues?.get(1) ?: return null
            val v = Regex("""<rdf:li>\s*(\d+)\s*,\s*(\d+)\s*</rdf:li>""").findAll(block).flatMap { sequenceOf(it.groupValues[1].toFloat() / 255f, it.groupValues[2].toFloat() / 255f) }.toList()
            return v.takeIf { it.size >= 4 && !(it.size == 4 && it[0] == 0f && it[1] == 0f && it[2] == 1f && it[3] == 1f) }
        }
        pts("ToneCurvePV2012")?.let { g = g.copy(curveRgb = it) }
        pts("ToneCurvePV2012Red")?.let { g = g.copy(curveR = it) }
        pts("ToneCurvePV2012Green")?.let { g = g.copy(curveG = it) }
        pts("ToneCurvePV2012Blue")?.let { g = g.copy(curveB = it) }
        p("Clarity2012")?.let { d = d.copy(clarity = it) }
        p("Texture")?.let { d = d.copy(texture = it) }
        f("Sharpness")?.let { d = d.copy(sharpen = (it / 150f).coerceIn(0f, 1f)) }
        f("SharpenRadius")?.let { d = d.copy(sharpenRadius = (it - 1f).coerceIn(-0.5f, 2f)) }
        p("SharpenDetail")?.let { d = d.copy(sharpenDetail = (it - 0.25f).coerceIn(-0.25f, 0.75f)) }
        p("SharpenEdgeMasking")?.let { d = d.copy(sharpenMasking = it) }
        p("LuminanceSmoothing")?.let { d = d.copy(denoise = it) }
        p("PostCropVignetteAmount")?.let { d = d.copy(vignette = it) }
        p("PostCropVignetteMidpoint")?.let { d = d.copy(vignetteMid = it - 0.5f) }
        p("PostCropVignetteRoundness")?.let { d = d.copy(vignetteRound = it) }
        p("PostCropVignetteFeather")?.let { d = d.copy(vignetteFeather = it - 0.5f) }
        p("PostCropVignetteHighlightContrast")?.let { d = d.copy(vignetteHighlights = it) }
        p("GrainAmount")?.let { d = d.copy(grain = it) }
        p("GrainSize")?.let { d = d.copy(grainSize = it - 0.25f) }
        p("GrainFrequency")?.let { d = d.copy(grainRough = it - 0.5f) }
        f("DefringePurpleAmount")?.let { d = d.copy(defringePurple = (it / 20f).coerceIn(0f, 1f)) }
        f("DefringeGreenAmount")?.let { d = d.copy(defringeGreen = (it / 20f).coerceIn(0f, 1f)) }
        return g to d
    }

    /** A Camera Raw .xmp preset with these settings. */
    fun presetXmp(name: String, g: ProGrade, d: Detail): String {
        val a = StringBuilder()
        fun put(k: String, v: String) { a.append("\n    crs:").append(k).append("=\"").append(v).append('"') }
        fun n(x: Float, s: Float = 100f) = Math.round(x * s).toString()
        put("PresetType", "Normal"); put("Cluster", ""); put("UUID", java.util.UUID.randomUUID().toString().replace("-", "").uppercase(Locale.US))
        put("SupportsAmount2", "True"); put("SupportsColor", "True"); put("SupportsMonochrome", "True"); put("SupportsHighDynamicRange", "True")
        put("SupportsNormalDynamicRange", "True"); put("SupportsSceneReferred", "True"); put("SupportsOutputReferred", "True")
        put("Version", "15.0"); put("ProcessVersion", "11.0")
        put("Exposure2012", String.format(Locale.US, "%+.2f", g.exposure))
        put("Contrast2012", n(g.contrast)); put("Highlights2012", n(g.highlights)); put("Shadows2012", n(g.shadows))
        put("Whites2012", n(g.whites)); put("Blacks2012", n(g.blacks)); put("Texture", n(d.texture)); put("Clarity2012", n(d.clarity)); put("Dehaze", n(g.dehaze))
        put("Vibrance", n(g.vibrance)); put("Saturation", n(g.saturation))
        put("IncrementalTemperature", n(g.temperature)); put("IncrementalTint", n(g.tint))
        put("ParametricShadows", n(g.curveShadows)); put("ParametricDarks", n(g.curveDarks)); put("ParametricLights", n(g.curveLights)); put("ParametricHighlights", n(g.curveHighlights))
        val names = listOf("Red", "Orange", "Yellow", "Green", "Aqua", "Blue", "Purple", "Magenta")
        g.hsl.take(8).forEachIndexed { i, b -> put("HueAdjustment${names[i]}", n(b.hue)); put("SaturationAdjustment${names[i]}", n(b.sat)); put("LuminanceAdjustment${names[i]}", n(b.lum)) }
        fun wheel(prefix: String, w: Wheel?) { val x = w ?: Wheel(); put("${prefix}Hue", Math.round(x.hue).toString()); put("${prefix}Sat", n(x.amount)); put("${prefix}Lum", n(x.luma)) }
        wheel("ColorGradeShadow", g.gradeShadows); wheel("ColorGradeMidtone", g.gradeMids); wheel("ColorGradeHighlight", g.gradeHighlights); wheel("ColorGradeGlobal", g.gradeGlobal)
        put("ColorGradeBlending", n(0.5f + g.gradeBlend)); put("SplitToningBalance", n(g.gradeBalance))
        g.calib?.let { c -> put("ShadowTint", n(c.shadowTint)); put("RedHue", n(c.redHue)); put("RedSaturation", n(c.redSat)); put("GreenHue", n(c.greenHue)); put("GreenSaturation", n(c.greenSat)); put("BlueHue", n(c.blueHue)); put("BlueSaturation", n(c.blueSat)) }
        put("Sharpness", n(d.sharpen, 150f)); put("SharpenRadius", String.format(Locale.US, "%+.1f", d.sharpRadius)); put("SharpenDetail", n(d.sharpDetail)); put("SharpenEdgeMasking", n(d.sharpenMasking))
        put("LuminanceSmoothing", n(d.denoise))
        put("PostCropVignetteAmount", n(d.vignette)); put("PostCropVignetteMidpoint", n(d.vigMid)); put("PostCropVignetteRoundness", n(d.vignetteRound))
        put("PostCropVignetteFeather", n(d.vigFeather)); put("PostCropVignetteHighlightContrast", n(d.vignetteHighlights)); put("PostCropVignetteStyle", "1")
        put("GrainAmount", n(d.grain)); put("GrainSize", n(d.grainSz)); put("GrainFrequency", n(d.grainRg))
        put("DefringePurpleAmount", n(d.defringePurple, 20f)); put("DefringeGreenAmount", n(d.defringeGreen, 20f))
        val curves = StringBuilder()
        listOf("ToneCurvePV2012" to g.curveRgb, "ToneCurvePV2012Red" to g.curveR, "ToneCurvePV2012Green" to g.curveG, "ToneCurvePV2012Blue" to g.curveB).forEach { (k, c) ->
            val pts = c ?: listOf(0f, 0f, 1f, 1f)
            curves.append("   <crs:$k>\n    <rdf:Seq>\n")
            for (i in 0 until pts.size / 2) curves.append("     <rdf:li>${Math.round(pts[i * 2] * 255)}, ${Math.round(pts[i * 2 + 1] * 255)}</rdf:li>\n")
            curves.append("    </rdf:Seq>\n   </crs:$k>\n")
        }
        put("HasSettings", "True")
        val safe = name.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;")
        return """<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="AlcorCut Pro">
 <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
  <rdf:Description rdf:about=""
    xmlns:crs="http://ns.adobe.com/camera-raw-settings/1.0/"$a>
   <crs:Name>
    <rdf:Alt>
     <rdf:li xml:lang="x-default">$safe</rdf:li>
    </rdf:Alt>
   </crs:Name>
$curves  </rdf:Description>
 </rdf:RDF>
</x:xmpmeta>
"""
    }

    /** Saves a preset .xmp in Documents/AlcorCutPro/Presets. Returns where, or null. */
    fun savePreset(context: Context, name: String, xmp: String): String? {
        val file = name.replace(Regex("[^A-Za-z0-9 _.-]"), "_").ifBlank { "Preset" } + ".xmp"
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val v = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, file)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/AlcorCutPro/Presets")
                }
                val u = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), v) ?: return null
                context.contentResolver.openOutputStream(u)?.use { it.write(xmp.toByteArray()) }
            } else {
                @Suppress("DEPRECATION")
                val dir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS), "AlcorCutPro/Presets").apply { mkdirs() }
                File(dir, file).writeText(xmp)
            }
            "Documents/AlcorCutPro/Presets/$file"
        } catch (_: Throwable) { null }
    }

    /** Preset name from an .xmp (crs:Name), or the file name. */
    fun nameOf(text: String, fallback: String): String =
        Regex("""<crs:Name>.*?<rdf:li[^>]*>([^<]+)</rdf:li>""", RegexOption.DOT_MATCHES_ALL).find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
            ?: fallback.substringBeforeLast('.')

    fun readText(context: Context, uri: Uri): String? = try { context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } } catch (_: Throwable) { null }
}

/**
 * Built-in presets with live thumbnails of your photo, by group, and an Amount slider that fades
 * the last one you tapped in and out.
 */
@Composable
fun BuiltInPresetSection(
    thumbSrc: Bitmap?,
    grade: ProGrade, detail: Detail,
    onSet: (ProGrade, Detail) -> Unit,
) {
    var group by remember { mutableStateOf("Portrait") }
    // The edit before the preset was tapped, the preset, and its amount.
    var before by remember { mutableStateOf<Pair<ProGrade, Detail>?>(null) }
    var chosen by remember { mutableStateOf<BuiltInPreset?>(null) }
    var amount by remember { mutableStateOf(1f) }
    val small = remember(thumbSrc) {
        thumbSrc?.let { b -> val k = 96f / maxOf(b.width, b.height); if (k < 1f) Bitmap.createScaledBitmap(b, (b.width * k).toInt().coerceAtLeast(1), (b.height * k).toInt().coerceAtLeast(1), true) else b }
    }
    // Recommended for this photo (the AI looks at what's in it, on the phone).
    val rec by produceState<List<PresetAdvisor.Pick>?>(null, thumbSrc) {
        val b = thumbSrc ?: return@produceState
        value = withContext(Dispatchers.Default) {
            try {
                val k = 448f / maxOf(b.width, b.height)
                val m = if (k < 1f) Bitmap.createScaledBitmap(b, (b.width * k).toInt().coerceAtLeast(1), (b.height * k).toInt().coerceAtLeast(1), true) else b
                PresetAdvisor.suggest(m, PresetAdvisor.labels(m))
            } catch (_: Throwable) { emptyList() }
        }
    }
    @Composable fun Tile(pr: BuiltInPreset, sub: String? = null) {
        val key = "${System.identityHashCode(small)}/${pr.group}/${pr.name}"
        val thumb by produceState(PresetThumbs.cache.get(key), small, pr) {
            if (value != null) return@produceState
            value = small?.let { s -> withContext(Dispatchers.Default) { try { PresetThumbs.render(s, pr).also { PresetThumbs.cache.put(key, it) } } catch (_: Throwable) { null } } }
        }
        val on = chosen === pr
        Column(
            Modifier.width(76.dp).clip(RoundedCornerShape(8.dp)).border(if (on) 2.dp else 0.dp, if (on) Pro.Amber else Color.Transparent, RoundedCornerShape(8.dp))
                .clickable {
                    val b = if (chosen != null && before != null) before!! else grade to detail
                    before = b; chosen = pr; amount = 1f
                    onSet(pr.grade(b.first), pr.detail(b.second))
                }.padding(3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(70.dp).clip(RoundedCornerShape(6.dp)).background(com.dronedirector.veyrocutpro.ui.AppTheme.pick(Color(0xFF1A1E26), Color(0xFFE2E4E8)))) {
                thumb?.let { t -> val img = remember(t) { t.asImageBitmap() }; Image(img, null, Modifier.size(70.dp), contentScale = ContentScale.Crop) }
            }
            Text(pr.name, color = if (on) Pro.Amber else Color.White, fontSize = 10.sp, maxLines = 1)
            if (sub != null) Text(sub, color = Pro.Dim, fontSize = 8.sp, maxLines = 2, lineHeight = 9.sp)
        }
    }
    SectionTitle("RECOMMENDED FOR THIS PHOTO")
    when {
        thumbSrc == null -> Text("Open a photo to see looks that suit it.", color = Pro.Dim, fontSize = 11.sp)
        rec == null -> Text("Looking at the photo…", color = Pro.Dim, fontSize = 11.sp)
        else -> Row(Modifier.fillMaxWidth().hintedScroll(horizontal = true).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            rec!!.forEach { p -> Tile(p.preset, p.why) }
        }
    }
    SectionTitle("PRESETS (${DevPresets.all.size})")
    val groups = DevPresets.all.map { it.group }.distinct()
    Chips(groups, groups.indexOf(group).coerceAtLeast(0)) { group = groups[it] }
    Row(Modifier.fillMaxWidth().hintedScroll(horizontal = true).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DevPresets.all.filter { it.group == group }.forEach { pr ->
            val key = "${System.identityHashCode(small)}/${pr.group}/${pr.name}"
            val thumb by produceState(PresetThumbs.cache.get(key), small, pr) {
                if (value != null) return@produceState
                value = small?.let { s -> withContext(Dispatchers.Default) { try { PresetThumbs.render(s, pr).also { PresetThumbs.cache.put(key, it) } } catch (_: Throwable) { null } } }
            }
            val on = chosen === pr
            Column(
                Modifier.width(76.dp).clip(RoundedCornerShape(8.dp)).border(if (on) 2.dp else 0.dp, if (on) Pro.Amber else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable {
                        val b = if (chosen != null && before != null) before!! else grade to detail
                        before = b; chosen = pr; amount = 1f
                        onSet(pr.grade(b.first), pr.detail(b.second))
                    }.padding(3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(70.dp).clip(RoundedCornerShape(6.dp)).background(com.dronedirector.veyrocutpro.ui.AppTheme.pick(Color(0xFF1A1E26), Color(0xFFE2E4E8)))) {
                    thumb?.let { t -> val img = remember(t) { t.asImageBitmap() }; Image(img, null, Modifier.size(70.dp), contentScale = ContentScale.Crop) }
                }
                Text(pr.name, color = if (on) Pro.Amber else Color.White, fontSize = 10.sp, maxLines = 1)
            }
        }
    }
    val c = chosen; val b = before
    if (c != null && b != null) {
        EdSlider("Amount · ${c.name}", amount, 0f..2f, 1f, ::pct) { v ->
            amount = v
            // 0–100 % fades the preset in; above 100 % pushes further the same way.
            onSet(Develop.lerpGrade(b.first, c.grade(b.first), v), Develop.lerpDetail(b.second, c.detail(b.second), v))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("KEEP", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { chosen = null; before = null }.padding(8.dp))
            Text("UNDO PRESET", style = Pro.Label.copy(color = Pro.Red, fontSize = 11.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onSet(b.first, b.second); chosen = null; before = null }.padding(8.dp))
        }
    }
}

/**
 * Preset thumbnails: small (120 px) and quick — the look is applied straight to the few thousand
 * pixels instead of first building a full colour table for each preset — and kept, so they show at
 * once the next time.
 */
object PresetThumbs {
    val cache = android.util.LruCache<String, Bitmap>(240)
    fun render(s: Bitmap, pr: BuiltInPreset): Bitmap {
        val g = pr.grade(ProGrade())
        val w = s.width; val h = s.height
        val px = IntArray(w * h); s.getPixels(px, 0, w, 0, 0, w, h)
        if (!g.isIdentity) {
            val c = FloatArray(3)
            for (i in px.indices) {
                val p = px[i]
                c[0] = ((p shr 16) and 255) / 255f; c[1] = ((p shr 8) and 255) / 255f; c[2] = (p and 255) / 255f
                g.apply(c)
                px[i] = (0xFF shl 24) or ((c[0].coerceIn(0f, 1f) * 255f + 0.5f).toInt() shl 16) or ((c[1].coerceIn(0f, 1f) * 255f + 0.5f).toInt() shl 8) or (c[2].coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            }
        }
        val graded = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        return ImageOps.render(graded, ProGrade(), pr.detail(Detail()).copy(aiDenoise = 0f), Geometry(), null)
    }
}

