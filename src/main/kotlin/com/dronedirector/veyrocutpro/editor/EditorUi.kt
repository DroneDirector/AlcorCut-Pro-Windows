package com.dronedirector.veyrocutpro.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import com.dronedirector.veyrocutpro.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.camera.Filter
import com.dronedirector.veyrocutpro.ui.Pro
import com.dronedirector.veyrocutpro.ui.hintedScroll
import kotlin.math.roundToInt

/** Same look as the camera: dark panels, accent highlights. */
object Ed {
    val Bg: Color get() = if (com.dronedirector.veyrocutpro.ui.AppTheme.light) Color(0xFFE9EBEE) else DarkBg
    val Panel: Color get() = if (com.dronedirector.veyrocutpro.ui.AppTheme.light) Color(0xFFF4F5F7) else DarkPanel
    val Raised: Color get() = if (com.dronedirector.veyrocutpro.ui.AppTheme.light) Color(0xFFFFFFFF) else DarkRaised
    /** Always dark (picture, timeline and media-strip areas). */
    val DarkBg = Color(0xFF07080A)
    val DarkPanel = Color(0xFF111317)
    val DarkRaised = Color(0xFF1A1D22)
}

@Composable
fun SectionTitle(text: String, onReset: (() -> Unit)? = null) = com.dronedirector.veyrocutpro.ui.SectionHeader(text, onReset)

/** Labelled slider; tap the label to reset. */
@Composable
fun EdSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float> = -1f..1f,
    default: Float = 0f,
    format: (Float) -> String = { "%+d".format((it * 100).roundToInt()) },
    onChange: (Float) -> Unit,
) {
    val view = androidx.compose.ui.platform.LocalView.current
    // A light tick each time the slider passes its centre (default) point.
    val side = androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f).clickable { onChange(default) })
            Text(format(value), style = Pro.Value.copy(color = if (value != default) Pro.Amber else Pro.Dim, fontSize = 12.sp))
        }
        Slider(
            value = value, onValueChange = { v ->
                val sd = if (v > default) 1 else if (v < default) -1 else 0
                if (side.intValue != 0 && sd != side.intValue) com.dronedirector.veyrocutpro.ui.Haptics.tick(view)
                side.intValue = sd
                onChange(v)
            }, valueRange = range,
            modifier = Modifier.height(28.dp),
            colors = SliderDefaults.colors(thumbColor = Pro.Amber, activeTrackColor = Pro.Amber, inactiveTrackColor = com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.14f)),
        )
    }
}

fun pct(v: Float) = "${(v * 100).roundToInt()}%"
fun ev(v: Float) = "%+.2f EV".format(v)

/** Colour wheel as hue + amount + brightness sliders, with a swatch. */
@Composable
fun WheelControl(title: String, w: Wheel, showLuma: Boolean = true, onChange: (Wheel) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp)).background(Ed.Raised).padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (r, g, b) = hueRgb(w.hue)
            Box(
                Modifier.size(26.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), Color(r, g, b).copy(alpha = 0.3f + w.amount * 0.7f))))
                    .border(1.dp, Pro.Line, CircleShape)
            )
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp).weight(1f))
            Text(
                "RESET", style = Pro.Label.copy(fontSize = 10.sp),
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onChange(Wheel(w.hue)) }.padding(6.dp),
            )
        }
        HueBar(w.hue) { onChange(w.copy(hue = it)) }
        EdSlider("Amount", w.amount, 0f..1f, format = ::pct) { onChange(w.copy(amount = it)) }
        if (showLuma) EdSlider("Brightness", w.luma) { onChange(w.copy(luma = it)) }
    }
}

@Composable
fun HueBar(hue: Float, onChange: (Float) -> Unit) {
    Box(Modifier.fillMaxWidth().height(30.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))) {
            drawRect(Brush.horizontalGradient((0..6).map { i -> hueRgb(i * 60f).let { (r, g, b) -> Color(r, g, b) } }))
        }
        Slider(
            value = hue, onValueChange = onChange, valueRange = 0f..360f,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent),
        )
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun Chips(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val tick = com.dronedirector.veyrocutpro.ui.rememberTick()
    val view = androidx.compose.ui.platform.LocalView.current
    Row(
        Modifier.fillMaxWidth().hintedScroll(horizontal = true).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEachIndexed { i, o ->
            val sel = i == selected
            Text(
                o, color = if (sel) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (sel) Pro.Amber else com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.08f))
                    .combinedClickable(onClick = { tick(); onSelect(i) }, onLongClick = { com.dronedirector.veyrocutpro.ui.HelpTip.show(o, view) })
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
fun LookStrip(selected: Filter, onPick: (Filter) -> Unit) {
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
                    Modifier.size(46.dp).clip(CircleShape).background(Color(f.swatch))
                        .border(if (sel) 3.dp else 1.dp, if (sel) Pro.Amber else Pro.Line, CircleShape)
                )
                Text(if (f == Filter.NONE) "None" else f.label, color = if (sel) Pro.Amber else Color.White, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}

/** RGB + luma histogram (scope). */
@Composable
fun Scope(hist: Array<FloatArray>?, modifier: Modifier = Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.6f))) {
        val h = hist ?: return@Canvas
        val colors = listOf(Color(0xCCFF4D4D), Color(0xCC4DFF6A), Color(0xCC4D8BFF), Color(0x99FFFFFF))
        for (ch in 0..3) {
            val path = Path()
            val bins = h[ch]
            path.moveTo(0f, size.height)
            bins.forEachIndexed { i, v -> path.lineTo(i / 63f * size.width, size.height - v * size.height * 0.95f) }
            path.lineTo(size.width, size.height)
            path.close()
            if (ch < 3) drawPath(path, colors[ch].copy(alpha = 0.35f))
            drawPath(path, colors[ch], style = Stroke(1.2f))
        }
        // Clipping guides
        drawLine(com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.15f), Offset(size.width / 2, 0f), Offset(size.width / 2, size.height))
    }
}

/** Settings for an editor, in folded groups like the Capture settings. */
@Composable
fun EditorSettingsDialog(
    editorName: String,
    screen: com.dronedirector.veyrocutpro.ui.AppScreen,
    playbackExtra: (@Composable () -> Unit)? = null,
    projectExtra: (@Composable () -> Unit)? = null,
    colourExtra: (@Composable () -> Unit)? = null,
    /** Video editor: timeline switches (scroll wheel effects, trimming, marker effects) — under Preferences → Timeline. */
    timelineExtra: (@Composable () -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var always by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(com.dronedirector.veyrocutpro.ui.StartScreen.get(ctx) == screen)
    }
    val isVideo = screen == com.dronedirector.veyrocutpro.ui.AppScreen.VIDEO_EDITOR
    val k = "ed_" + screen.name + "_"
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pro.PanelSolid,
        title = { Text("$editorName settings", color = Color.White) },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                EditorSettingsBody(editorName, screen, playbackExtra, projectExtra, colourExtra, timelineExtra, null, onDismiss)
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Done", color = Pro.Amber) } },
    )
}

/** "About 2 min 10 s left" for export progress. */
fun etaText(remainingMs: Long): String {
    val s = (remainingMs / 1000).coerceAtLeast(0)
    return when {
        s < 5 -> "Almost done…"
        s < 60 -> "About $s s left"
        s < 3600 -> "About ${s / 60} min ${(s % 60) / 5 * 5} s left"
        else -> "About ${s / 3600} h ${(s % 3600) / 60} min left"
    }
}

/** Remaining time from progress so far (percent 0..100), or null while it's too early to tell. */
fun etaFromProgress(startMs: Long, percent: Int): String? {
    val elapsed = System.currentTimeMillis() - startMs
    if (percent < 3 || elapsed < 2000) return null
    return etaText(elapsed * (100 - percent) / percent)
}

/**
 * Point curve editor (RGB / R / G / B): drag points, tap the curve to add one, double-tap a point
 * to remove it. [pts] are x, y pairs 0..1; null means a straight line.
 */
@androidx.compose.runtime.Composable
fun CurveEditor(
    channel: Int, onChannel: (Int) -> Unit,
    pts: List<Float>?, onChange: (List<Float>?) -> Unit,
    others: List<List<Float>?> = emptyList(),
) {
    val colors = listOf(Color.White, Color(0xFFFF5252), Color(0xFF4CDB6A), Color(0xFF4D8BFF))
    Chips(listOf("RGB", "Red", "Green", "Blue"), channel) { onChannel(it) }
    val cur = pts ?: listOf(0f, 0f, 1f, 1f)
    val latest by androidx.compose.runtime.rememberUpdatedState(cur)
    val change by androidx.compose.runtime.rememberUpdatedState(onChange)
    var drag by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(-1) }
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxWidth().aspectRatio(1.4f).padding(vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.35f))
            .pointerInput(channel) {
                detectTapGestures(
                    onDoubleTap = { o ->
                        val x = o.x / size.width; val y = 1f - o.y / size.height
                        val l = latest
                        val n = l.size / 2
                        val near = (0 until n).minByOrNull { kotlin.math.hypot((l[it * 2] - x).toDouble(), (l[it * 2 + 1] - y).toDouble()) } ?: return@detectTapGestures
                        if (near != 0 && near != n - 1) change(l.toMutableList().apply { removeAt(near * 2); removeAt(near * 2) })
                    },
                    onTap = { o ->
                        val x = (o.x / size.width).coerceIn(0.01f, 0.99f)
                        val l = latest
                        val y = PointCurve.eval(l, x)
                        val pairs = (0 until l.size / 2).map { l[it * 2] to l[it * 2 + 1] } + (x to y)
                        change(pairs.sortedBy { it.first }.flatMap { listOf(it.first, it.second) })
                    },
                )
            }
            .pointerInput(channel, "drag") {
                detectDragGestures(
                    onDragStart = { o ->
                        val x = o.x / size.width; val y = 1f - o.y / size.height
                        val l = latest
                        drag = (0 until l.size / 2).minByOrNull { kotlin.math.hypot((l[it * 2] - x).toDouble(), (l[it * 2 + 1] - y).toDouble()) } ?: -1
                    },
                    onDragEnd = { drag = -1 }, onDragCancel = { drag = -1 },
                ) { ch, _ ->
                    ch.consume()
                    val i = drag; val l = latest
                    if (i < 0 || i >= l.size / 2) return@detectDragGestures
                    val n = l.size / 2
                    val lo = if (i == 0) 0f else l[(i - 1) * 2] + 0.01f
                    val hi = if (i == n - 1) 1f else l[(i + 1) * 2] - 0.01f
                    val x = if (i == 0) 0f else if (i == n - 1) 1f else (ch.position.x / size.width).coerceIn(lo, hi)
                    val y = (1f - ch.position.y / size.height).coerceIn(0f, 1f)
                    change(l.toMutableList().apply { set(i * 2, x); set(i * 2 + 1, y) })
                }
            },
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val grid = com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.12f)
            for (k in 1..3) {
                drawLine(grid, androidx.compose.ui.geometry.Offset(size.width * k / 4, 0f), androidx.compose.ui.geometry.Offset(size.width * k / 4, size.height))
                drawLine(grid, androidx.compose.ui.geometry.Offset(0f, size.height * k / 4), androidx.compose.ui.geometry.Offset(size.width, size.height * k / 4))
            }
            drawLine(grid, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, 0f))
            fun curve(p: List<Float>, col: Color, w: Float) {
                val path = androidx.compose.ui.graphics.Path()
                for (s in 0..64) {
                    val x = s / 64f
                    val px = x * size.width; val py = (1f - PointCurve.eval(p, x).coerceIn(0f, 1f)) * size.height
                    if (s == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                drawPath(path, col, style = androidx.compose.ui.graphics.drawscope.Stroke(w))
            }
            others.forEachIndexed { k, o -> if (o != null && k != channel) curve(o, colors[k].copy(alpha = 0.35f), 1.5f) }
            curve(cur, colors[channel], 2.5f)
            for (i in 0 until cur.size / 2) {
                drawCircle(colors[channel], 6.dp.toPx(), androidx.compose.ui.geometry.Offset(cur[i * 2] * size.width, (1f - cur[i * 2 + 1]) * size.height))
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("RESET CURVE", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp), modifier = Modifier.clickable { onChange(null) }.padding(8.dp))
        Text("Tap to add a point · drag to move · double-tap to remove", color = Pro.Dim, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

/** Gear → Preferences → Autosave. */
@Composable
fun AutoSaveSettings(vid: Boolean) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
                                                        var mins by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(AutoSave.minutes(ctx, vid)) }
                            var keep by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(AutoSave.keep(ctx, vid)) }
                            Text("AUTOSAVE", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(top = 14.dp))
                            Text("Saves a copy of the open project automatically (only when something changed). Find them under LOAD → AUTOSAVES.", color = Pro.Dim, fontSize = 12.sp)
                            Text("Every", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                            Chips(AutoSave.INTERVALS.map { if (it == 0) "Off" else "$it min" }, AutoSave.INTERVALS.indexOf(mins).coerceAtLeast(0)) { i -> mins = AutoSave.INTERVALS[i]; AutoSave.setMinutes(ctx, vid, mins) }
                            if (mins > 0) {
                                Text("Autosaves to keep", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                                Chips((1..5).map { "$it" }, keep - 1) { i -> keep = i + 1; AutoSave.setKeep(ctx, vid, keep) }
                                Text("The newest $keep autosave" + (if (keep == 1) " is" else "s are") + " kept side by side; older ones are removed. Your own saved projects are never touched.", color = Pro.Dim, fontSize = 11.sp)
                            }
                        }

/** Small editor preferences that aren't anywhere else. */
object EditorPrefs {
    private fun p(c: android.content.Context) = c.getSharedPreferences("procam", android.content.Context.MODE_PRIVATE)
    fun keepScreenOn(c: android.content.Context) = p(c).getBoolean("keep_screen_on", true)
    fun setKeepScreenOn(c: android.content.Context, on: Boolean) = p(c).edit().putBoolean("keep_screen_on", on).apply()
    /** Photos added to the video editor become clips this long (s). */
    fun photoClipSeconds(c: android.content.Context) = p(c).getInt("photo_clip_s", 5).coerceIn(1, 60)
    fun setPhotoClipSeconds(c: android.content.Context, s: Int) = p(c).edit().putInt("photo_clip_s", s).apply()
    /** New titles, captions and text layers last this long (s). */
    fun titleSeconds(c: android.content.Context) = p(c).getInt("title_s", 4).coerceIn(1, 30)
    fun setTitleSeconds(c: android.content.Context, s: Int) = p(c).edit().putInt("title_s", s).apply()
    /** Appearance: 0 dark, 1 light, 2 like the phone. */
    fun theme(c: android.content.Context) = p(c).getInt("app_theme", 0)
    fun setTheme(c: android.content.Context, t: Int) = p(c).edit().putInt("app_theme", t).apply()
}

/** Keeps the screen on while an editor is open (Preferences → Editing). */
@Composable
fun KeepScreenOnWhileEditing() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.DisposableEffect(Unit) {
        val w = (ctx as? android.app.Activity)?.window
        val on = EditorPrefs.keepScreenOn(ctx)
        if (on) w?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { if (on) w?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

/** Gear → Preferences: appearance, sound, audio hardware, autosave, playback, timeline and editing. */
@Composable
fun PreferencesSection(screen: com.dronedirector.veyrocutpro.ui.AppScreen, playbackExtra: (@Composable () -> Unit)?, timelineExtra: (@Composable () -> Unit)?) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val k = "pref_" + screen.name + "_"
    val isVideo = screen == com.dronedirector.veyrocutpro.ui.AppScreen.VIDEO_EDITOR
    val isEditor = isVideo || screen == com.dronedirector.veyrocutpro.ui.AppScreen.PHOTO_EDITOR
    com.dronedirector.veyrocutpro.ui.SettingsGroup(k + "look", "Appearance", "Dark, light or like the phone, one-handed layout, haptic feedback") {
        Chips(listOf("Dark", "Light", "Like the phone"), com.dronedirector.veyrocutpro.ui.AppTheme.pref) { com.dronedirector.veyrocutpro.ui.AppTheme.set(ctx, it) }
        Text("The light look is used in the photo and video editors. The picture, the timeline and the media strip stay dark so colours are judged right, and Capture stays dark because it sits on the camera picture.", color = Pro.Dim, fontSize = 12.sp)
        com.dronedirector.veyrocutpro.ui.SettingsKeywords("One-handed", "Haptic feedback", "thumb", "vibration")
        com.dronedirector.veyrocutpro.ui.ComfortSettings()
    }
    com.dronedirector.veyrocutpro.ui.SettingsGroup(k + "sound", "Sound", "Shutter sound on or off, and which one") {
        val required = android.os.Build.VERSION.SDK_INT >= 33 && android.media.MediaActionSound.mustPlayShutterSound()
        var on by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(ctx.getSharedPreferences("procam", android.content.Context.MODE_PRIVATE).getBoolean("camera_sounds", true)) }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Shutter sound", color = Color.White, fontSize = 15.sp)
                Text(if (required) "Required by law in this region and can't be turned off" else "Click when taking photos, beeps when recording starts and stops", color = Pro.Dim, fontSize = 12.sp)
            }
            androidx.compose.material3.Switch(on || required, {
                if (!required) { on = it; ctx.getSharedPreferences("procam", android.content.Context.MODE_PRIVATE).edit().putBoolean("camera_sounds", it).apply() }
            }, enabled = !required, colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Pro.Amber, checkedThumbColor = Color.Black))
        }
        if ((on || required) && !required) {
            var style by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(com.dronedirector.veyrocutpro.camera.ShutterSounds.style(ctx)) }
            Text("Shutter sound — tap to hear it", color = Pro.Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            Chips(com.dronedirector.veyrocutpro.camera.ShutterSounds.NAMES, style) { i ->
                style = i; com.dronedirector.veyrocutpro.camera.ShutterSounds.setStyle(ctx, i)
                if (i > 0) com.dronedirector.veyrocutpro.camera.ShutterSounds.play(i) else android.media.MediaActionSound().play(android.media.MediaActionSound.SHUTTER_CLICK)
            }
        }
    }
    com.dronedirector.veyrocutpro.ui.SettingsGroup(k + "hw", "Audio hardware", "Which microphone and speaker / headphones the app uses") {
        com.dronedirector.veyrocutpro.ui.AudioHardwareSettings()
    }
    if (isEditor) com.dronedirector.veyrocutpro.ui.SettingsGroup(k + "autosave", "Autosave", "Automatic project copies and how many to keep") { AutoSaveSettings(isVideo) }
    if (playbackExtra != null) com.dronedirector.veyrocutpro.ui.SettingsGroup(k + "playback", "Playback", "Play button effect, playback info read-out, proxy media") { playbackExtra() }
    if (timelineExtra != null) com.dronedirector.veyrocutpro.ui.SettingsGroup(k + "timeline", "Timeline", "Scroll wheel effects, trimming, marker effects") { timelineExtra() }
    if (isEditor) com.dronedirector.veyrocutpro.ui.SettingsGroup(k + "editing", "Editing", "Screen on while editing" + if (isVideo) ", photo clip and title lengths" else "") {
        var keep by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(EditorPrefs.keepScreenOn(ctx)) }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Keep the screen on", color = Color.White, fontSize = 15.sp)
                Text("The screen doesn't dim or lock while an editor is open (takes effect when you next open it)", color = Pro.Dim, fontSize = 12.sp)
            }
            androidx.compose.material3.Switch(keep, { keep = it; EditorPrefs.setKeepScreenOn(ctx, it) },
                colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Pro.Amber, checkedThumbColor = Color.Black))
        }
        if (isVideo) {
            var ph by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(EditorPrefs.photoClipSeconds(ctx)) }
            Text("Photos become clips of", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
            val ps = listOf(2, 3, 5, 8, 10, 15)
            Chips(ps.map { "$it s" }, ps.indexOf(ph).coerceAtLeast(0)) { i -> ph = ps[i]; EditorPrefs.setPhotoClipSeconds(ctx, ps[i]) }
            var tl by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(EditorPrefs.titleSeconds(ctx)) }
            Text("New titles and captions last", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            val ts = listOf(2, 3, 4, 5, 8, 10)
            Chips(ts.map { "$it s" }, ts.indexOf(tl).coerceAtLeast(0)) { i -> tl = ts[i]; EditorPrefs.setTitleSeconds(ctx, ts[i]) }
        }
    }
}

/** The gear's settings, also shown in File → Project settings ([only] = just these groups). */
@Composable
fun EditorSettingsBody(
    editorName: String,
    screen: com.dronedirector.veyrocutpro.ui.AppScreen,
    playbackExtra: (@Composable () -> Unit)? = null,
    projectExtra: (@Composable () -> Unit)? = null,
    colourExtra: (@Composable () -> Unit)? = null,
    timelineExtra: (@Composable () -> Unit)? = null,
    only: Set<String>? = null,
    onDismiss: () -> Unit = {},
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var always by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(com.dronedirector.veyrocutpro.ui.StartScreen.get(ctx) == screen)
    }
    val isVideo = screen == com.dronedirector.veyrocutpro.ui.AppScreen.VIDEO_EDITOR
    val k = "ed_" + screen.name + "_"
    fun show(g: String) = only == null || g in only
                if (show("look")) com.dronedirector.veyrocutpro.ui.SettingsGroup(k + "look", "Layout & look", if (isVideo) "Simple layout, Play button effect, app colour, scroll wheel effects, mouse pointer, scroll wheel, fold-out sections" else "Simple layout, app colour, mouse pointer, fold-out sections") {
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Simple layout", color = Color.White, fontSize = 15.sp)
                                Text("Shows the main tools only (fewer panels and colour tabs). Turn off for every Pro tool. Same switch in Capture settings.", color = Pro.Dim, fontSize = 12.sp)
                            }
                            androidx.compose.material3.Switch(
                                checked = com.dronedirector.veyrocutpro.camera.SimpleMode.on,
                                onCheckedChange = { com.dronedirector.veyrocutpro.camera.SimpleMode.set(ctx, it) },
                                colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Pro.Amber, checkedThumbColor = Color.Black),
                            )
                        }
                        if (isVideo) PlayFxSettings()
                        Text("APP COLOUR", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                        com.dronedirector.veyrocutpro.ui.AccentPicker()
                        if (colourExtra != null) colourExtra()
                        com.dronedirector.veyrocutpro.ui.PointerAndWheelSettings(showWheel = screen == com.dronedirector.veyrocutpro.ui.AppScreen.VIDEO_EDITOR)
                    com.dronedirector.veyrocutpro.ui.FoldModeSetting(if (isVideo) "video" else "photo")
                }
                if (show("nas")) com.dronedirector.veyrocutpro.ui.SettingsGroup(k + "nas", "Network (NAS)", "Saved NAS sign-ins: see or remove them") {
                    NasSignInsSettings()
                }
                if (show("save")) com.dronedirector.veyrocutpro.ui.SettingsGroup(k + "save", "Saving & backup", "Save location, autosave, backup and restore") {
                        val video = screen == com.dronedirector.veyrocutpro.ui.AppScreen.VIDEO_EDITOR
                        Text("SAVE LOCATION", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(top = 14.dp))
                        com.dronedirector.veyrocutpro.ui.StorageChooser(
                            if (video) com.dronedirector.veyrocutpro.camera.StorageArea.VIDEO else com.dronedirector.veyrocutpro.camera.StorageArea.PHOTO,
                        )
                    com.dronedirector.veyrocutpro.ui.BackupSection()
                }
                if (show("catalog") && (screen == com.dronedirector.veyrocutpro.ui.AppScreen.PHOTO_EDITOR)) com.dronedirector.veyrocutpro.ui.SettingsGroup(k + "catalog", "Catalog", "Catalogs, photos remembered, photo copies, location") { PhotoCatalogSettings() }
                if (show("catalog") && (isVideo)) com.dronedirector.veyrocutpro.ui.SettingsGroup(k + "catalog", "Catalog", "Catalogs, clips remembered, their settings, proxies and analysis, copies, location") { VideoCatalogSettings() }
                if (show("project") && (projectExtra != null)) com.dronedirector.veyrocutpro.ui.SettingsGroup(k + "project", "Project archive & versions", "Save or open a project archive (.zip), version history") { projectExtra() }
                if (show("prefs")) com.dronedirector.veyrocutpro.ui.SettingsGroup(k + "prefs", "Preferences", "Appearance, sound, audio hardware, autosave" + (if (isVideo) ", playback, timeline" else "") + ", editing") {
                    PreferencesSection(screen, playbackExtra, timelineExtra)
                }
                if (show("sysinfo")) com.dronedirector.veyrocutpro.ui.SettingsGroup(k + "sysinfo", "System Info", "This phone's hardware and drivers") {
                    com.dronedirector.veyrocutpro.ui.SystemInfoSection()
                }
                if (show("wm") && (screen != com.dronedirector.veyrocutpro.ui.AppScreen.EDITOR)) com.dronedirector.veyrocutpro.ui.SettingsGroup(k + "wm", "Watermark", "Remember your watermark and add it to everything new") {
                        if (screen != com.dronedirector.veyrocutpro.ui.AppScreen.EDITOR) {
                            val v = screen == com.dronedirector.veyrocutpro.ui.AppScreen.VIDEO_EDITOR
                            Text("WATERMARK", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(top = 14.dp))
                            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Remember watermark", color = Color.White, fontSize = 15.sp)
                                    Text(
                                        "Keeps your last watermark (text or logo, font, colour, size, position, opacity) and adds it to every new " +
                                            (if (v) "video" else "photo") + " automatically.",
                                        color = Pro.Dim, fontSize = 12.sp,
                                    )
                                }
                                androidx.compose.material3.Switch(
                                    checked = WatermarkMemory.isOn(ctx, v),
                                    onCheckedChange = { WatermarkMemory.setOn(ctx, v, it) },
                                    colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Pro.Amber, checkedThumbColor = Color.Black),
                                )
                            }
                        }
                }
                val gv = if (isVideo) com.dronedirector.veyrocutpro.ui.GuideVariant.VIDEO else com.dronedirector.veyrocutpro.ui.GuideVariant.PHOTO
                if (show("ai")) com.dronedirector.veyrocutpro.ui.SettingsGroup(k + "ai", "AI", "Cloud AI (fal.ai key, noise reduction, upscale and more)") {
                    com.dronedirector.veyrocutpro.ui.CloudAiSettings(gv)
                }
                if (show("app")) com.dronedirector.veyrocutpro.ui.SettingsGroup(k + "app", "App", "Language, startup screen, quick tour, guide, what's new, licences, privacy") {
                    com.dronedirector.veyrocutpro.ui.LanguagePicker()
                        Text("STARTUP", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(top = 10.dp))
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Always start in $editorName", color = Color.White, fontSize = 15.sp)
                                Text("Open AlcorCut Pro straight into the $editorName. Turn off to show the start screen again.", color = Pro.Dim, fontSize = 12.sp)
                            }
                            androidx.compose.material3.Switch(
                                checked = always,
                                onCheckedChange = {
                                    always = it
                                    com.dronedirector.veyrocutpro.ui.StartScreen.set(ctx, if (it) screen else com.dronedirector.veyrocutpro.ui.AppScreen.HOME)
                                },
                                colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Pro.Amber, checkedThumbColor = Color.Black),
                            )
                        }
                    com.dronedirector.veyrocutpro.ui.EditorTourSettings(gv, onShow = onDismiss)
                    com.dronedirector.veyrocutpro.ui.GuideButton(gv)
                    com.dronedirector.veyrocutpro.ui.WhatsNewButton(if (isVideo) com.dronedirector.veyrocutpro.ui.NewsArea.VIDEO else com.dronedirector.veyrocutpro.ui.NewsArea.PHOTO, onDismiss)
                    com.dronedirector.veyrocutpro.ui.UpdatesSection(if (isVideo) com.dronedirector.veyrocutpro.ui.NewsArea.VIDEO else com.dronedirector.veyrocutpro.ui.NewsArea.PHOTO, onDismiss)
                    com.dronedirector.veyrocutpro.ui.LicensesButton()
                    com.dronedirector.veyrocutpro.ui.PrivacyButton()
                }
            
}

/** The gear's dialog frame (title, scrolling content, Done). */
@Composable
fun EditorSettingsShell(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pro.PanelSolid,
        title = { Text(title, color = Color.White) },
        text = {
            var query by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                com.dronedirector.veyrocutpro.ui.SettingsSearchField(query) { query = it }
                androidx.compose.runtime.CompositionLocalProvider(com.dronedirector.veyrocutpro.ui.LocalSettingsQuery provides query) { content() }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Done", color = Pro.Amber) } },
    )
}
