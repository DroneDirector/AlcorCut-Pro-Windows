package com.dronedirector.veyrocutpro.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import com.dronedirector.veyrocutpro.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.camera.Filter
import com.dronedirector.veyrocutpro.camera.Grade
import com.dronedirector.veyrocutpro.camera.GradeIO
import com.dronedirector.veyrocutpro.camera.GradeLut
import com.dronedirector.veyrocutpro.camera.GradeDetail
import com.dronedirector.veyrocutpro.camera.MediaItem
import com.dronedirector.veyrocutpro.camera.VideoGradeExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private enum class GradeTab(val label: String) { LOOKS("Looks"), LIGHT("Light"), COLOUR("Colour"), DETAIL("Detail"), TONING("Toning") }

/**
 * Colour grading for a photo or video from the gallery. The preview updates live; hold the
 * picture to compare with the original. Saving always creates a new file, the original is kept.
 */
@Composable
fun GradeEditor(item: MediaItem, onClose: () -> Unit, onSaved: (Uri) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var grade by remember { mutableStateOf(Grade()) }
    var tab by remember { mutableStateOf(GradeTab.LOOKS) }
    var base by remember { mutableStateOf<Bitmap?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var comparing by remember { mutableStateOf(false) }
    var frameAt by remember { mutableFloatStateOf(0f) }
    var saving by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(-1) }
    var message by remember { mutableStateOf<String?>(null) }
    var export by remember { mutableStateOf<VideoGradeExport?>(null) }

    BackHandler { if (!saving) onClose() }
    DisposableEffect(Unit) { onDispose { export?.cancel() } }

    // Source picture (for videos: a frame picked with the scrubber).
    LaunchedEffect(item.uri, frameAt) {
        if (item.isVideo) delay(120)
        val bmp = withContext(Dispatchers.IO) {
            if (item.isVideo) GradeIO.videoFrame(context, item.uri, (frameAt * item.durationMs * 1000L).toLong(), 1080)
            else GradeIO.decode(context, item.uri, 1080)
        }
        if (bmp != null) base = bmp
    }
    // Live preview: while a slider moves, a small copy is redrawn for every change (instant);
    // when it stops, the full-size preview follows.
    val gradeNow by androidx.compose.runtime.rememberUpdatedState(grade)
    LaunchedEffect(base) {
        val b = base ?: return@LaunchedEffect
        val k = 540f / maxOf(b.width, b.height)
        val small = if (k < 1f) Bitmap.createScaledBitmap(b, (b.width * k).toInt().coerceAtLeast(1), (b.height * k).toInt().coerceAtLeast(1), true) else b
        fun render(src: Bitmap, g: Grade, n: Int): Bitmap {
            val lut = if (g.copy(clarity = 0f, texture = 0f, dehaze = 0f, sharpen = 0f, vignette = 0f).isIdentity) src else GradeLut(g, n).apply(src)
            return if (!item.isVideo && g.hasDetail) GradeDetail.apply(lut, g) else lut
        }
        var shown: Grade? = null
        var full = true
        while (true) {
            val g = gradeNow
            if (g != shown) {
                preview = withContext(Dispatchers.Default) { render(small, g, 17) }
                shown = g; full = false
            } else if (!full) {
                delay(120)
                if (gradeNow == g) {
                    val f = withContext(Dispatchers.Default) { render(b, g, 33) }
                    if (gradeNow == g) { preview = f; full = true }
                }
            } else delay(16)
        }
    }
    LaunchedEffect(message) { if (message != null) { delay(2500); message = null } }

    fun save() {
        if (saving) return
        saving = true
        if (item.isVideo) {
            progress = 0
            val e = VideoGradeExport(
                context, item.uri, grade,
                onProgress = { progress = it },
                onDone = { uri, err ->
                    saving = false; progress = -1; export = null
                    if (uri != null) { message = "Graded video saved"; onSaved(uri) } else message = err ?: "Export failed"
                },
            )
            export = e
            e.start()
        } else {
            scope.launch {
                val uri = withContext(Dispatchers.IO) { GradeIO.savePhoto(context, item.uri, grade) }
                saving = false
                if (uri != null) { message = "Graded photo saved"; onSaved(uri) } else message = "Couldn't save the photo"
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF07080A))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
            // Top bar
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).clickable(enabled = !saving, onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text("Colour grade", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(if (item.isVideo) "VIDEO · SAVED AS A NEW CLIP" else "PHOTO · SAVED AS A NEW PICTURE", style = Pro.Label)
                }
                Text(
                    "RESET",
                    style = Pro.Label.copy(color = if (grade.isIdentity) Pro.Dim else Color.White, fontSize = 11.sp),
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { grade = Grade() }.padding(10.dp),
                )
                Text(
                    "SAVE",
                    style = Pro.Value.copy(color = Color.Black, fontSize = 13.sp),
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (saving || grade.isIdentity) Pro.Amber.copy(alpha = 0.4f) else Pro.Amber)
                        .clickable(enabled = !saving && !grade.isIdentity) { save() }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            // Preview: hold to see the original.
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(Unit) {
                        detectTapGestures(onPress = {
                            comparing = true
                            tryAwaitRelease()
                            comparing = false
                        })
                    },
                contentAlignment = Alignment.Center,
            ) {
                val shown = if (comparing) base else preview ?: base
                if (shown != null) {
                    Image(shown.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                } else {
                    CircularProgressIndicator(color = Pro.Amber)
                }
                Text(
                    if (comparing) "ORIGINAL" else "HOLD TO COMPARE",
                    style = Pro.Label.copy(color = Color.White),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
                if (saving) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(color = Pro.Amber)
                            Text(
                                if (item.isVideo) "Exporting graded video… ${progress.coerceAtLeast(0)}%" else "Saving graded photo…",
                                color = Color.White, fontSize = 14.sp,
                            )
                            if (item.isVideo) {
                                Text(
                                    "CANCEL",
                                    style = Pro.Label.copy(color = Pro.Amber, fontSize = 12.sp),
                                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable {
                                        export?.cancel(); export = null; saving = false; progress = -1
                                    }.padding(10.dp),
                                )
                            }
                        }
                    }
                }
                message?.let {
                    Text(
                        it, color = Color.White, fontSize = 13.sp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }

            if (item.isVideo) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("FRAME", style = Pro.Label, modifier = Modifier.width(52.dp))
                    Slider(
                        value = frameAt,
                        onValueChange = { frameAt = it },
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White.copy(alpha = 0.6f)),
                    )
                }
            }

            // Tabs: one line, slide sideways for the ones that don't fit.
            Row(
                Modifier.fillMaxWidth().hintedScroll(horizontal = true).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GradeTab.entries.forEach { t ->
                    val sel = t == tab
                    Text(
                        t.label,
                        color = if (sel) Color.Black else Color.White,
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (sel) Pro.Amber else com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.08f))
                            .clickable { tab = t }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                when (tab) {
                    GradeTab.LOOKS -> {
                        LookPicker(grade.look) { grade = grade.copy(look = it) }
                        if (grade.look != Filter.NONE) {
                            GradeSlider("Look strength", grade.lookAmount, 0f..1f, percent = true) { grade = grade.copy(lookAmount = it) }
                        }
                    }
                    GradeTab.LIGHT -> {
                        GradeSlider("Exposure", grade.exposure, -2f..2f, unit = " EV") { grade = grade.copy(exposure = it) }
                        GradeSlider("Contrast", grade.contrast) { grade = grade.copy(contrast = it) }
                        GradeSlider("Highlights", grade.highlights) { grade = grade.copy(highlights = it) }
                        GradeSlider("Shadows", grade.shadows) { grade = grade.copy(shadows = it) }
                        GradeSlider("Whites", grade.whites) { grade = grade.copy(whites = it) }
                        GradeSlider("Blacks", grade.blacks) { grade = grade.copy(blacks = it) }
                    }
                    GradeTab.COLOUR -> {
                        GradeSlider("Temperature", grade.temperature) { grade = grade.copy(temperature = it) }
                        GradeSlider("Tint", grade.tint) { grade = grade.copy(tint = it) }
                        GradeSlider("Saturation", grade.saturation) { grade = grade.copy(saturation = it) }
                        GradeSlider("Vibrance", grade.vibrance) { grade = grade.copy(vibrance = it) }
                        GradeSlider("Fade", grade.fade, 0f..1f, percent = true) { grade = grade.copy(fade = it) }
                    }
                    GradeTab.DETAIL -> {
                        if (item.isVideo) Text("Detail works on photos. For videos, use the Video editor's Color page.", color = Pro.Dim, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
                        else {
                            GradeSlider("Clarity", grade.clarity) { grade = grade.copy(clarity = it) }
                            GradeSlider("Texture", grade.texture) { grade = grade.copy(texture = it) }
                            GradeSlider("Dehaze", grade.dehaze) { grade = grade.copy(dehaze = it) }
                            GradeSlider("Sharpening", grade.sharpen, 0f..1f, percent = true) { grade = grade.copy(sharpen = it) }
                            GradeSlider("Vignette", grade.vignette) { grade = grade.copy(vignette = it) }
                        }
                    }
                    GradeTab.TONING -> {
                        HueSlider("Shadows colour", grade.shadowHue) { grade = grade.copy(shadowHue = it) }
                        GradeSlider("Shadows amount", grade.shadowTone, 0f..1f, percent = true) { grade = grade.copy(shadowTone = it) }
                        HueSlider("Highlights colour", grade.highlightHue) { grade = grade.copy(highlightHue = it) }
                        GradeSlider("Highlights amount", grade.highlightTone, 0f..1f, percent = true) { grade = grade.copy(highlightTone = it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LookPicker(selected: Filter, onPick: (Filter) -> Unit) {
    Row(
        Modifier.fillMaxWidth().hintedScroll(horizontal = true).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Filter.entries.forEach { f ->
            val sel = f == selected
            Column(
                Modifier.clip(RoundedCornerShape(8.dp)).clickable { onPick(f) }.padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(f.swatch))
                        .border(if (sel) 3.dp else 1.dp, if (sel) Pro.Amber else Pro.Line, CircleShape)
                )
                Text(
                    if (f == Filter.NONE) "None" else f.label,
                    color = if (sel) Pro.Amber else Color.White, fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun GradeSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float> = -1f..1f,
    unit: String = "",
    percent: Boolean = false,
    onChange: (Float) -> Unit,
) {
    val default = if (range.start < 0f) 0f else if (label == "Look strength") 1f else 0f
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f).clickable { onChange(default) })
            val txt = when {
                percent -> "${(value * 100).roundToInt()}%"
                unit.isNotEmpty() -> "%+.1f$unit".format(value)
                else -> "%+d".format((value * 100).roundToInt())
            }
            Text(txt, style = Pro.Value.copy(color = if (value != default) Pro.Amber else Pro.Dim, fontSize = 12.sp))
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.height(30.dp),
            colors = SliderDefaults.colors(thumbColor = Pro.Amber, activeTrackColor = Pro.Amber, inactiveTrackColor = com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.15f)),
        )
    }
}

@Composable
private fun HueSlider(label: String, hue: Float, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
            val (r, g, b) = Grade.hueColor(hue)
            Box(Modifier.size(14.dp).clip(CircleShape).background(Color(r, g, b)))
        }
        Box(Modifier.fillMaxWidth().height(30.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))) {
                drawRect(Brush.horizontalGradient((0..6).map { i -> Grade.hueColor(i * 60f).let { (r, g, b) -> Color(r, g, b) } }))
            }
            Slider(
                value = hue,
                onValueChange = onChange,
                valueRange = 0f..360f,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                ),
            )
        }
    }
}
