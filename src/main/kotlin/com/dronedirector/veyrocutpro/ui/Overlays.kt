package com.dronedirector.veyrocutpro.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.dronedirector.veyrocutpro.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.camera.AnalysisResult
import com.dronedirector.veyrocutpro.camera.CameraUiState
import com.dronedirector.veyrocutpro.camera.CaptureMode
import com.dronedirector.veyrocutpro.camera.GridMode
import com.dronedirector.veyrocutpro.camera.Stops
import com.dronedirector.veyrocutpro.camera.WhiteBalance
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt

@Composable
fun GridOverlay(mode: GridMode, modifier: Modifier = Modifier) {
    if (mode == GridMode.OFF) return
    Canvas(modifier.fillMaxSize()) {
        val c = Color.White.copy(alpha = 0.45f)
        val w = size.width
        val h = size.height
        val sw = 1.dp.toPx()
        fun v(x: Float) = drawLine(c, Offset(x, 0f), Offset(x, h), sw)
        fun hz(y: Float) = drawLine(c, Offset(0f, y), Offset(w, y), sw)
        when (mode) {
            GridMode.THIRDS -> {
                v(w / 3); v(2 * w / 3); hz(h / 3); hz(2 * h / 3)
            }
            GridMode.PHI -> {
                val a = 0.382f
                v(w * a); v(w * (1 - a)); hz(h * a); hz(h * (1 - a))
            }
            GridMode.SQUARE -> {
                for (i in 1..3) { v(w * i / 4); hz(h * i / 4) }
            }
            GridMode.CROSS -> {
                val len = min(w, h) * 0.05f
                drawLine(c, Offset(w / 2 - len, h / 2), Offset(w / 2 + len, h / 2), sw * 1.5f)
                drawLine(c, Offset(w / 2, h / 2 - len), Offset(w / 2, h / 2 + len), sw * 1.5f)
            }
            GridMode.OFF -> {}
        }
    }
}

/** Darkens everything outside a centered square for 1:1 shooting. */
@Composable
fun SquareMask(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val side = min(size.width, size.height)
        val dim = Color.Black.copy(alpha = 0.65f)
        if (size.width > size.height) {
            val pad = (size.width - side) / 2
            drawRect(dim, Offset.Zero, Size(pad, size.height))
            drawRect(dim, Offset(size.width - pad, 0f), Size(pad, size.height))
        } else {
            val pad = (size.height - side) / 2
            drawRect(dim, Offset.Zero, Size(size.width, pad))
            drawRect(dim, Offset(0f, size.height - pad), Size(size.width, pad))
        }
    }
}

/** Screen-relative roll angle in degrees, smoothed. */
@Composable
private fun rememberTilt(): Float {
    val context = LocalContext.current
    val view = LocalView.current
    var tilt by remember { mutableFloatStateOf(0f) }
    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val x = e.values[0]
                val y = e.values[1]
                val z = e.values[2]
                // Ignore when lying flat: roll is meaningless.
                if (sqrt(x * x + y * y) < 0.25f * sqrt(x * x + y * y + z * z)) return
                val deviceRoll = Math.toDegrees(atan2(-x.toDouble(), y.toDouble())).toFloat()
                val displayRot = (view.display?.rotation ?: 0) * 90f
                var t = deviceRoll + displayRot
                while (t > 180f) t -= 360f
                while (t <= -180f) t += 360f
                // Low-pass, handling wrap-around.
                var d = t - tilt
                if (d > 180f) d -= 360f
                if (d < -180f) d += 360f
                tilt += d * 0.2f
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }
    return tilt
}

@Composable
fun LevelIndicator(modifier: Modifier = Modifier) {
    val tilt = rememberTilt()
    val level = abs(tilt) < 1f
    Canvas(modifier.fillMaxSize()) {
        val cx = size.width / 2
        val cy = size.height / 2
        val half = min(size.width, size.height) * 0.18f
        val color = if (level) Pro.Green else Color.White.copy(alpha = 0.85f)
        val sw = 1.5.dp.toPx()
        // Fixed reference ticks.
        drawLine(Color.White.copy(alpha = 0.5f), Offset(cx - half - 24.dp.toPx(), cy), Offset(cx - half - 8.dp.toPx(), cy), sw)
        drawLine(Color.White.copy(alpha = 0.5f), Offset(cx + half + 8.dp.toPx(), cy), Offset(cx + half + 24.dp.toPx(), cy), sw)
        // Horizon line, counter-rotated so it stays parallel to the real horizon.
        rotate(-tilt, Offset(cx, cy)) {
            drawLine(color, Offset(cx - half, cy), Offset(cx + half, cy), sw * (if (level) 2f else 1f))
        }
        if (level) drawCircle(Pro.Green, 3.dp.toPx(), Offset(cx, cy))
    }
}

@Composable
fun Histogram(result: AnalysisResult?, width: Dp, height: Dp, modifier: Modifier = Modifier) {
    val r = result ?: return
    val hist = r.histogram ?: return
    Box(
        modifier
            .size(width, height)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.45f))
    ) {
        Canvas(Modifier.fillMaxSize().padding(4.dp)) {
            val n = hist.size
            val step = size.width / (n - 1)
            val path = Path().apply {
                moveTo(0f, size.height)
                for (i in 0 until n) lineTo(i * step, size.height - hist[i] * size.height)
                lineTo(size.width, size.height)
                close()
            }
            drawPath(path, Color.White.copy(alpha = 0.75f))
            // Clipping warnings at each edge.
            if (r.clipLow > 0.02f) drawRect(Color(0xFF2D7DFF), Offset.Zero, Size(3.dp.toPx(), size.height))
            if (r.clipHigh > 0.02f) drawRect(Pro.Red, Offset(size.width - 3.dp.toPx(), 0f), Size(3.dp.toPx(), size.height))
        }
    }
}

@Composable
fun FocusReticle(point: Pair<Float, Float>?, id: Int, locked: Boolean) {
    if (point == null || id == 0) return
    val anim = remember { Animatable(0f) }
    LaunchedEffect(id) {
        anim.snapTo(1f)
        anim.animateTo(0f, tween(durationMillis = 1600))
    }
    if (anim.value <= 0f) return
    Canvas(Modifier.fillMaxSize()) {
        val r = 34.dp.toPx() * (1f + 0.25f * (anim.value - 0.6f).coerceAtLeast(0f))
        val c = Offset(point.first, point.second)
        val color = (if (locked) Pro.Amber else Color.White).copy(alpha = anim.value.coerceIn(0f, 1f))
        drawRect(color, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), style = Stroke(1.5.dp.toPx()))
        val t = 6.dp.toPx()
        drawLine(color, Offset(c.x, c.y - r), Offset(c.x, c.y - r + t), 1.5.dp.toPx())
        drawLine(color, Offset(c.x, c.y + r), Offset(c.x, c.y + r - t), 1.5.dp.toPx())
        drawLine(color, Offset(c.x - r, c.y), Offset(c.x - r + t, c.y), 1.5.dp.toPx())
        drawLine(color, Offset(c.x + r, c.y), Offset(c.x + r - t, c.y), 1.5.dp.toPx())
    }
}

@Composable
fun ShutterFlash(trigger: Int) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        alpha.snapTo(0.7f)
        alpha.animateTo(0f, tween(220))
    }
    if (alpha.value > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = alpha.value)))
}

/** Exposure readouts along the top edge of the frame. A = automatic, M = manual. */
@Composable
fun ExposureHud(s: CameraUiState, isoShown: Int?, shutterShown: Long?, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HudItem("ISO", isoShown?.toString() ?: "--", s.manualIso)
        HudItem("S", shutterShown?.let { Stops.shutterLabel(it) } ?: "--", s.manualShutter)
        s.liveAperture?.let { HudItem("ƒ", "%.1f".format(it), null) }
        HudItem("F", Stops.focusLabel(if (s.manualFocus) s.focusDiopters else (s.liveFocusDiopters ?: 0f)), s.manualFocus)
        HudItem("WB", s.whiteBalance.label, s.whiteBalance != WhiteBalance.AUTO || s.awbLock)
        if (s.ev != 0) HudItem("EV", "%+.1f".format(s.ev * s.evStep), null)
        if (s.aeLock && !s.manualIso && !s.manualShutter) Text("AE-L", style = Pro.Value.copy(color = Pro.Amber, fontSize = 11.sp))
    }
}

@Composable
private fun HudItem(label: String, value: String, manual: Boolean?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Pro.Label.copy(fontSize = 9.sp))
        Spacer(Modifier.width(3.dp))
        Text(value, style = Pro.Value.copy(fontSize = 12.sp, color = if (manual == true) Pro.Amber else Color.White))
    }
}

@Composable
fun RecordingBadge(s: CameraUiState, modifier: Modifier = Modifier) {
    if (!s.isVideo || !s.recording) return
    val blink = remember { Animatable(1f) }
    LaunchedEffect(s.paused) {
        while (true) {
            blink.animateTo(if (s.paused) 0.3f else 1f, tween(500))
            blink.animateTo(1f, tween(500))
        }
    }
    val sec = s.recordSeconds
    val time = "%02d:%02d:%02d".format(sec / 3600, (sec / 60) % 60, sec % 60)
    Row(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(Pro.Red.copy(alpha = blink.value)))
        when {
            s.mode.isLapse -> {
                Text(time, style = Pro.Value)
                Text(
                    "${s.lapseFrames} frames → %.1f s".format(s.lapseFrames / 30f),
                    style = Pro.Value.copy(fontSize = 11.sp, color = Pro.Amber),
                )
            }
            s.mode.isSlowMo -> {
                Text(time, style = Pro.Value)
                Text("${s.slowFps} FPS · ×${(s.slowFps / 30).coerceAtLeast(1)} SLOWER", style = Pro.Value.copy(fontSize = 11.sp, color = Pro.Amber))
            }
            else -> {
                Text(if (s.paused) "PAUSED $time" else time, style = Pro.Value)
                if (s.audio) AudioMeter(s.audioLevel)
            }
        }
    }
}

/** One-line explanation of what the current smart mode will produce, shown before recording. */
@Composable
fun SmartModeHint(s: CameraUiState, modifier: Modifier = Modifier) {
    if (s.recording || s.panoActive || s.stitching || s.longProgress >= 0f) return
    val text = when (s.mode) {
        CaptureMode.LONG_EXPOSURE -> {
            val k = com.dronedirector.veyrocutpro.camera.LongKind.entries[com.dronedirector.veyrocutpro.camera.CaptureExtras.longKind.coerceIn(0, 2)]
            "${k.label} · ${com.dronedirector.veyrocutpro.camera.CaptureExtras.longSec} s · ${k.hint}"
        }
        CaptureMode.TIMELAPSE -> {
            val perMin = 60_000f / s.lapseIntervalMs / 30f
            "1 frame every %s · 1 min → %.1f s of video".format(
                if (s.lapseIntervalMs < 1000) "${s.lapseIntervalMs / 1000f} s" else "${s.lapseIntervalMs / 1000} s", perMin,
            ) + if (s.lapseLimitMin > 0) " · stops after ${s.lapseLimitMin} min" else ""
        }
        CaptureMode.HYPERLAPSE -> "×${s.hyperSpeed} faster · walk smoothly · 1 min → %.1f s".format(60f / s.hyperSpeed) +
            if (s.lapseStabilize) " · stabilized" else ""
        CaptureMode.SLOW_MO, CaptureMode.SUPER_SLOW_MO ->
            if (s.slowFps <= 0) "This camera can't film fast enough for this mode"
            else "Films at ${s.slowFps} fps · plays ×${(s.slowFps / 30).coerceAtLeast(1)} slower · no sound"
        CaptureMode.BURST -> "Tap for ${s.burstModeCount} shots · hold the shutter to keep shooting"
        else -> return
    }
    Text(
        text,
        style = Pro.Value.copy(fontSize = 11.sp, color = Color.White),
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** "Saving…" while a time-lapse finishes encoding or a slow-motion clip is being written. */
@Composable
fun ProcessingBadge(s: CameraUiState, modifier: Modifier = Modifier) {
    if (!s.processing) return
    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        androidx.compose.material3.CircularProgressIndicator(color = Pro.Amber, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Text("Saving video…", style = Pro.Value)
    }
}

@Composable
private fun AudioMeter(level: Float) {
    val l = sqrt(level.coerceIn(0f, 1f))
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("AUD", style = Pro.Label.copy(fontSize = 7.sp))
        Box(Modifier.width(56.dp).height(5.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.2f))) {
            val color = when {
                l > 0.9f -> Pro.Red
                l > 0.7f -> Pro.Amber
                else -> Pro.Green
            }
            Box(Modifier.fillMaxWidth(l).height(5.dp).background(color))
        }
    }
}

@Composable
fun Countdown(value: Int) {
    if (value <= 0) return
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("$value", color = Color.White, fontSize = 120.sp, fontWeight = FontWeight.Light)
    }
}

// ------------------------------------------------------------------ audio graph (video modes)

/**
 * Scrolling audio level graph: one bar per ~40 ms, coloured green / amber / red by loudness,
 * with a peak-hold marker and dBFS readout.
 */
@Composable
fun AudioGraph(levels: com.dronedirector.veyrocutpro.camera.AudioLevels, width: Dp, height: Dp, recording: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Row(Modifier.width(width), verticalAlignment = Alignment.CenterVertically) {
            Text(if (recording) "AUDIO ● REC" else "AUDIO", style = Pro.Label.copy(fontSize = 8.sp, color = if (recording) Pro.Red else Pro.Dim))
            Spacer(Modifier.weight(1f))
            val db = levels.db
            Text(
                if (!levels.live) "--" else if (db <= com.dronedirector.veyrocutpro.camera.AudioMeter.MIN_DB) "-∞ dB" else "%.0f dB".format(db),
                style = Pro.Value.copy(fontSize = 10.sp, color = if (db > -3f) Pro.Red else Color.White),
            )
        }
        Spacer(Modifier.height(3.dp))
        Canvas(Modifier.width(width).height(height)) {
            val n = levels.history.size
            val barW = size.width / n
            // Reference lines at -6, -12, -24 dBFS.
            for (ref in listOf(-6f, -12f, -24f)) {
                val y = size.height * (ref / com.dronedirector.veyrocutpro.camera.AudioMeter.MIN_DB)
                drawLine(Color.White.copy(alpha = 0.15f), Offset(0f, y), Offset(size.width, y), 1f)
            }
            for (i in 0 until n) {
                val v = levels.history[i]
                if (v <= 0f) continue
                val h = v * size.height
                val color = when {
                    v > 0.95f -> Pro.Red
                    v > 0.8f -> Pro.Amber
                    else -> Pro.Green
                }
                drawRect(color, Offset(i * barW, size.height - h), Size((barW - 1f).coerceAtLeast(1f), h))
            }
            if (levels.live && levels.peakDb > com.dronedirector.veyrocutpro.camera.AudioMeter.MIN_DB) {
                val py = size.height * (1f - (levels.peakDb - com.dronedirector.veyrocutpro.camera.AudioMeter.MIN_DB) / -com.dronedirector.veyrocutpro.camera.AudioMeter.MIN_DB)
                drawLine(Color.White, Offset(size.width - 6.dp.toPx(), py), Offset(size.width, py), 2.dp.toPx())
            }
        }
        // Left / right meters (stereo microphones), green → amber → red.
        Spacer(Modifier.height(3.dp))
        for ((label, d) in listOf("L" to levels.leftDb, "R" to levels.rightDb)) {
            Row(Modifier.width(width).padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = Pro.Label.copy(fontSize = 7.sp, color = Pro.Dim), modifier = Modifier.width(8.dp))
                Canvas(Modifier.weight(1f).height(4.dp)) {
                    val v = if (!levels.live) 0f else ((d - com.dronedirector.veyrocutpro.camera.AudioMeter.MIN_DB) / -com.dronedirector.veyrocutpro.camera.AudioMeter.MIN_DB).coerceIn(0f, 1f)
                    drawRect(Color.White.copy(alpha = 0.12f), Offset.Zero, size)
                    val segs = 24
                    val sw = size.width / segs
                    for (i in 0 until (v * segs).toInt()) {
                        val f = i / segs.toFloat()
                        drawRect(if (f > 0.95f) Pro.Red else if (f > 0.8f) Pro.Amber else Pro.Green, Offset(i * sw, 0f), Size(sw - 1f, size.height))
                    }
                }
            }
        }
        if (levels.live && !levels.stereo) Text("mono mic", style = Pro.Label.copy(fontSize = 7.sp, color = Pro.Dim))
    }
}

// ------------------------------------------------------------------ frame guides (pro video)

@Composable
fun SafeAreasOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val sw = 1.dp.toPx()
        val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
        for ((k, a) in listOf(0.9f to 0.7f, 0.8f to 0.45f)) {
            val w = size.width * k; val h = size.height * k
            drawRect(Color.White.copy(alpha = a), Offset((size.width - w) / 2, (size.height - h) / 2), Size(w, h),
                style = androidx.compose.ui.graphics.drawscope.Stroke(sw, pathEffect = if (k < 0.85f) dash else null))
        }
    }
}

@Composable
fun FrameGuideOverlay(guide: com.dronedirector.veyrocutpro.camera.FrameGuide, modifier: Modifier = Modifier) {
    if (guide == com.dronedirector.veyrocutpro.camera.FrameGuide.OFF) return
    Canvas(modifier.fillMaxSize()) {
        val frameRatio = size.width / size.height
        val dim = Color.Black.copy(alpha = 0.55f)
        val line = Pro.Amber.copy(alpha = 0.9f)
        val sw = 1.5.dp.toPx()
        if (guide.ratio > frameRatio) {
            val h = size.width / guide.ratio
            val top = (size.height - h) / 2
            drawRect(dim, Offset.Zero, Size(size.width, top))
            drawRect(dim, Offset(0f, top + h), Size(size.width, top))
            drawLine(line, Offset(0f, top), Offset(size.width, top), sw)
            drawLine(line, Offset(0f, top + h), Offset(size.width, top + h), sw)
        } else {
            val w = size.height * guide.ratio
            val left = (size.width - w) / 2
            drawRect(dim, Offset.Zero, Size(left, size.height))
            drawRect(dim, Offset(left + w, 0f), Size(left, size.height))
            drawLine(line, Offset(left, 0f), Offset(left, size.height), sw)
            drawLine(line, Offset(left + w, 0f), Offset(left + w, size.height), sw)
        }
    }
}

// ------------------------------------------------------------------ panorama guide

@Composable
fun PanoramaOverlay(s: CameraUiState, modifier: Modifier = Modifier) {
    if (s.mode != CaptureMode.PANORAMA && !s.stitching) return
    Box(modifier.fillMaxSize()) {
        // Centre guide line to keep the horizon steady while turning.
        Canvas(Modifier.fillMaxSize()) {
            val y = size.height / 2
            drawLine(Pro.Amber.copy(alpha = 0.8f), Offset(size.width * 0.1f, y), Offset(size.width * 0.9f, y), 2.dp.toPx())
        }
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(top = 140.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                s.stitching -> {
                    androidx.compose.material3.CircularProgressIndicator(color = Pro.Amber, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.height(6.dp))
                    Text("Stitching panorama…", style = Pro.Value)
                }
                s.panoActive -> {
                    val arrow = when (s.panoDirection) { 1 -> "→"; -1 -> "←"; else -> "← →" }
                    Text("$arrow  Turn slowly  $arrow", style = Pro.Value.copy(fontSize = 15.sp))
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.width(200.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.2f))) {
                        Box(Modifier.fillMaxWidth(s.panoProgress.coerceIn(0.02f, 1f)).height(6.dp).background(Pro.Amber))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("${s.panoFrames} frames · about ${com.dronedirector.veyrocutpro.camera.Panorama.TARGET_DEG.toInt()}° · tap to finish early", style = Pro.Label.copy(fontSize = 10.sp, letterSpacing = 0.sp))
                }
                else -> {
                    Text("PANORAMA", style = Pro.Value.copy(color = Pro.Amber))
                    Spacer(Modifier.height(4.dp))
                    Text("Tap the shutter, then turn slowly left or right", style = Pro.Label.copy(fontSize = 11.sp, letterSpacing = 0.sp, color = Color.White))
                }
            }
        }
    }
}

@Composable
fun IntervalBadge(s: CameraUiState, modifier: Modifier = Modifier) {
    if (!s.intervalRunning) return
    Row(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(Pro.Red))
        Text("INTERVAL ${s.intervalSec}s · ${s.intervalCount} shots", style = Pro.Value)
    }
}
