package com.dronedirector.veyrocutpro.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import com.dronedirector.veyrocutpro.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.camera.CameraUiState
import com.dronedirector.veyrocutpro.camera.CaptureExtras
import com.dronedirector.veyrocutpro.camera.CaptureMode
import com.dronedirector.veyrocutpro.camera.Portrait
import com.dronedirector.veyrocutpro.camera.PortraitLive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Portrait mode blur control, sitting just above the filter / zoom bar.
 * Open: a slim slider from f/1.4 (most blur, left) to f/16 (least, right). Minimised: blur picked automatically.
 */
@Composable
fun PortraitPanel(s: CameraUiState, modifier: Modifier = Modifier) {
    if (!s.mode.isPortrait) return
    val live = s.mode == CaptureMode.AI_PORTRAIT
    val ctx = LocalContext.current
    val auto = CaptureExtras.portraitAuto
    Column(
        modifier
            .widthIn(max = 320.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Row(
            Modifier.fillMaxWidth()
                .then(if (auto) Modifier.clickable { CaptureExtras.portraitAuto = false; CaptureExtras.save(ctx) } else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("BACKGROUND BLUR", style = Pro.Label.copy(fontSize = 10.sp, color = Pro.Amber))
            Spacer(Modifier.width(8.dp))
            Text(
                if (auto) (if (live && PortraitLive.found != 0) "AUTO ${Portrait.label(PortraitLive.autoF)}" else "AUTO") else Portrait.label(CaptureExtras.portraitF),
                style = Pro.Value.copy(fontSize = 12.sp, color = Color.White),
            )
            if (live && PortraitLive.found == 0) {
                Spacer(Modifier.width(6.dp))
                Text("no subject", style = Pro.Label.copy(fontSize = 9.sp, letterSpacing = 0.sp))
            }
            Spacer(Modifier.weight(1f))
            // Minimise = auto; expand = manual slider.
            Row(
                Modifier.clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable { CaptureExtras.portraitAuto = !auto; CaptureExtras.save(ctx) }
                    .padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (auto) "MANUAL" else "AUTO", style = Pro.Label.copy(fontSize = 10.sp, color = Color.White))
                Icon(
                    if (auto) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (auto) "Show blur slider" else "Hide slider, blur automatically",
                    tint = Color.White, modifier = Modifier.size(14.dp),
                )
            }
        }
        AnimatedVisibility(
            visible = !auto,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("1.4", style = Pro.Label.copy(fontSize = 9.sp, letterSpacing = 0.sp))
                BlurSlider(
                    fNumber = CaptureExtras.portraitF,
                    onChange = { CaptureExtras.portraitF = it },
                    onSettled = { CaptureExtras.save(ctx) },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Text("16", style = Pro.Label.copy(fontSize = 9.sp, letterSpacing = 0.sp))
            }
        }
    }
}

private val MAJOR = setOf(0, 3, 6, 9, 12, 15, 18, 21) // full stops: 1.4 2 2.8 4 5.6 8 11 16

/** Slim aperture slider: drag or tap anywhere on it; snaps to third stops with a tick you can feel. */
@Composable
fun BlurSlider(fNumber: Float, onChange: (Float) -> Unit, onSettled: () -> Unit, modifier: Modifier = Modifier) {
    val stops = Portrait.F_STOPS
    val last = stops.size - 1
    val idx = Portrait.indexOf(fNumber)
    val haptic = LocalHapticFeedback.current
    val amber = Pro.Amber
    val edge = with(LocalDensity.current) { 8.dp.toPx() }

    fun pick(x: Float, width: Int) {
        val usable = (width - 2 * edge).coerceAtLeast(1f)
        val i = (((x - edge) / usable) * last).roundToInt().coerceIn(0, last)
        if (i != Portrait.indexOf(CaptureExtras.portraitF)) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onChange(stops[i])
        }
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerInput(Unit) {
                detectTapGestures { o -> pick(o.x, size.width); onSettled() }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> pick(o.x, size.width) },
                    onDragEnd = { onSettled() },
                    onDragCancel = { onSettled() },
                ) { change, _ ->
                    change.consume()
                    pick(change.position.x, size.width)
                }
            },
    ) {
        val cy = size.height / 2f
        val x0 = edge; val x1 = size.width - edge
        val tx = x0 + (x1 - x0) * idx / last
        val track = 3.dp.toPx()
        drawLine(Color.White.copy(alpha = 0.25f), Offset(x0, cy), Offset(x1, cy), track, StrokeCap.Round)
        // Filled from the thumb to the left: how much blur.
        drawLine(amber.copy(alpha = 0.7f), Offset(x0, cy), Offset(tx, cy), track, StrokeCap.Round)
        for (i in MAJOR) {
            val x = x0 + (x1 - x0) * i / last
            drawLine(Color.White.copy(alpha = 0.45f), Offset(x, cy - 5.dp.toPx()), Offset(x, cy - 3.dp.toPx()), 1.dp.toPx())
        }
        drawCircle(amber, 7.dp.toPx(), Offset(tx, cy))
        drawCircle(Color.Black.copy(alpha = 0.35f), 2.dp.toPx(), Offset(tx, cy))
    }
}

/** Portrait mode: the viewfinder's background blurred live, at the slider's strength (or AUTO). */
@Composable
fun PortraitLivePreview(previewView: PreviewView, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var img by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    DisposableEffect(Unit) { onDispose { PortraitLive.found = 0 } }
    LaunchedEffect(Unit) {
        PortraitLive.reset()
        while (isActive) {
            val t0 = System.currentTimeMillis()
            val snap = try { previewView.bitmap } catch (_: Throwable) { null }
            if (snap != null && snap.width > 8 && snap.height > 8) {
                val fNum = if (CaptureExtras.portraitAuto) Portrait.AUTO else CaptureExtras.portraitF
                val res = withContext(Dispatchers.Default) {
                    try {
                        val sc = PortraitLive.SIDE.toFloat() / maxOf(snap.width, snap.height)
                        val small = android.graphics.Bitmap.createScaledBitmap(
                            snap, (snap.width * sc).roundToInt().coerceAtLeast(8), (snap.height * sc).roundToInt().coerceAtLeast(8), true,
                        )
                        snap.recycle()
                        PortraitLive.render(ctx, small, fNum).also { small.recycle() }
                    } catch (_: Throwable) { null }
                }
                if (res != null) {
                    img = res.overlay
                    PortraitLive.found = res.kind
                    if (res.kind != 0) PortraitLive.autoF = res.f
                }
            }
            delay((80L - (System.currentTimeMillis() - t0)).coerceAtLeast(15L))
        }
    }
    img?.let { Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = modifier) }
}
