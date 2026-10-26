package com.dronedirector.veyrocutpro.editor

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import com.dronedirector.veyrocutpro.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.ui.Pro
import com.dronedirector.veyrocutpro.ui.hintedScroll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.max

/** UI-only state of the Masks and Heal panels. */
class LocalToolsState {
    var selected by mutableStateOf<String?>(null)
    var brushSize by mutableStateOf(0.08f)
    var brushSoft by mutableStateOf(0.5f)
    var erase by mutableStateOf(false)
    var showOverlay by mutableStateOf(true)
    var healSize by mutableStateOf(0.025f)
}

/** Where a Fit-scaled picture sits inside a box (with [pad] around). */
private data class FitRect(val left: Float, val top: Float, val w: Float, val h: Float)

private fun fit(boxW: Float, boxH: Float, imgW: Int, imgH: Int, pad: Float): FitRect {
    val aw = boxW - 2 * pad; val ah = boxH - 2 * pad
    val s = min(aw / imgW.coerceAtLeast(1), ah / imgH.coerceAtLeast(1))
    val w = imgW * s; val h = imgH * s
    return FitRect(pad + (aw - w) / 2, pad + (ah - h) / 2, w, h)
}

/** On-photo handles for the selected mask: drag gradient ends, move / size the ellipse, or paint. */
@Composable
fun MaskEditOverlay(mask: PhotoMask, imgW: Int, imgH: Int, ui: LocalToolsState, image: Bitmap? = null, pad: androidx.compose.ui.unit.Dp = 6.dp, onChange: (PhotoMask) -> Unit) {
    val cur by rememberUpdatedState(mask)
    val change by rememberUpdatedState(onChange)
    val live = remember { mutableStateListOf<Float>() }
    // Mask preview: red where the mask applies.
    var tint by remember { mutableStateOf<Bitmap?>(null) }
    val ranged = mask.type == MaskType.COLOR_RANGE || mask.type == MaskType.LUMA_RANGE
    LaunchedEffect(mask, ui.showOverlay, imgW, imgH, image) {
        if (!(ui.showOverlay || ranged) || imgW <= 0) { tint = null; return@LaunchedEffect }
        tint = withContext(Dispatchers.Default) {
            val w = if (ranged) 360 else 220; val h = (w.toFloat() * imgH / imgW).toInt().coerceIn(20, 900)
            val px = if (ranged && image != null) try {
                val sm = Bitmap.createScaledBitmap(image, w, h, true)
                IntArray(w * h).also { sm.getPixels(it, 0, w, 0, 0, w, h); if (sm !== image) sm.recycle() }
            } catch (_: Throwable) { null } else null
            val wt = PhotoLocal.weights(mask.copy(amount = 1f), w, h, px = px)
            Bitmap.createBitmap(IntArray(w * h) { ((wt[it] * 140).toInt().coerceIn(0, 255) shl 24) or 0xFF3030 }, w, h, Bitmap.Config.ARGB_8888)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val bw = with(density) { maxWidth.toPx() }; val bh = with(density) { maxHeight.toPx() }
        val r = fit(bw, bh, imgW, imgH, with(density) { pad.toPx() })
        fun toImg(o: Offset) = ((o.x - r.left) / r.w) to ((o.y - r.top) / r.h)
        fun toScreen(x: Float, y: Float) = Offset(r.left + x * r.w, r.top + y * r.h)
        tint?.let {
            Image(
                it.asImageBitmap(), null, contentScale = ContentScale.FillBounds,
                modifier = Modifier.offset { IntOffset(r.left.toInt(), r.top.toInt()) }.size(with(density) { r.w.toDp() }, with(density) { r.h.toDp() }),
            )
        }
        Canvas(
            Modifier.fillMaxSize().pointerInput(mask.type, r) {
                var mode = 0   // 1 = start handle / centre, 2 = end handle / edge, 3 = paint
                var startMask = cur
                var startPt = Offset.Zero
                detectDragGestures(
                    onDragStart = { o ->
                        startMask = cur; startPt = o
                        val m = cur
                        mode = when (m.type) {
                            MaskType.LINEAR -> if ((o - toScreen(m.x0, m.y0)).getDistance() < (o - toScreen(m.x1, m.y1)).getDistance()) 1 else 2
                            MaskType.RADIAL -> {
                                val c = toScreen(m.cx, m.cy)
                                val d = hypot(((o.x - c.x) / (m.rx * r.w)).toDouble(), ((o.y - c.y) / (m.ry * r.h)).toDouble())
                                if (d > 0.75) 2 else 1
                            }
                            MaskType.BRUSH -> { live.clear(); val (x, y) = toImg(o); live += x; live += y; 3 }
                            else -> 0
                        }
                    },
                    onDragEnd = {
                        if (mode == 3 && live.size >= 2) {
                            val m = cur
                            change(m.copy(strokes = m.strokes + BrushStroke(live.toList(), ui.brushSize, ui.erase, ui.brushSoft)))
                            live.clear()
                        }
                        mode = 0
                    },
                ) { ch, _ ->
                    ch.consume()
                    val (x, y) = toImg(ch.position)
                    val m = startMask
                    when (m.type) {
                        MaskType.LINEAR -> change(if (mode == 1) cur.copy(x0 = x, y0 = y) else cur.copy(x1 = x, y1 = y))
                        MaskType.RADIAL -> if (mode == 1) {
                            val (sx, sy) = toImg(startPt)
                            change(cur.copy(cx = (m.cx + x - sx).coerceIn(-0.2f, 1.2f), cy = (m.cy + y - sy).coerceIn(-0.2f, 1.2f)))
                        } else {
                            val c = toScreen(m.cx, m.cy)
                            val k = ((ch.position - c).getDistance() / (startPt - c).getDistance().coerceAtLeast(1f)).coerceIn(0.1f, 6f)
                            change(cur.copy(rx = (m.rx * k).coerceIn(0.02f, 2f), ry = (m.ry * k).coerceIn(0.02f, 2f)))
                        }
                        MaskType.BRUSH -> { live += x; live += y }
                        else -> {}
                    }
                }
            }.pointerInput(mask.type, r, "tap", image) {
                // Range masks: tap the photo to pick the colour / brightness under your finger.
                if (ranged && image != null) detectTapGestures { o ->
                    val (x, y) = toImg(o)
                    if (x !in 0f..1f || y !in 0f..1f) return@detectTapGestures
                    val c = image.getPixel((x * (image.width - 1)).toInt(), (y * (image.height - 1)).toInt())
                    val rr = (c shr 16) and 0xFF; val gg = (c shr 8) and 0xFF; val bb = c and 0xFF
                    val m = cur
                    if (m.type == MaskType.COLOR_RANGE) {
                        val hsv = FloatArray(3); android.graphics.Color.RGBToHSV(rr, gg, bb, hsv)
                        change(m.copy(hue = hsv[0]))
                    } else {
                        val l = (0.2126f * rr + 0.7152f * gg + 0.0722f * bb) / 255f
                        val half = ((m.lumHigh - m.lumLow) / 2f).coerceIn(0.05f, 0.5f)
                        change(m.copy(lumLow = (l - half).coerceIn(0f, 1f), lumHigh = (l + half).coerceIn(0f, 1f)))
                    }
                }
                // A tap with the brush paints a dot.
                else if (mask.type == MaskType.BRUSH) detectTapGestures { o ->
                    val (x, y) = toImg(o)
                    val m = cur
                    change(m.copy(strokes = m.strokes + BrushStroke(listOf(x, y), ui.brushSize, ui.erase, ui.brushSoft)))
                }
            },
        ) {
            val accent = Pro.Amber
            when (mask.type) {
                MaskType.LINEAR -> {
                    val a = toScreen(mask.x0, mask.y0); val b = toScreen(mask.x1, mask.y1)
                    val dir = (b - a); val len = dir.getDistance().coerceAtLeast(1f)
                    val n = Offset(-dir.y / len, dir.x / len) * 2000f
                    drawLine(Color.White, a - n, a + n, 2f)
                    drawLine(Color.White.copy(alpha = 0.6f), b - n, b + n, 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f)))
                    drawLine(accent, a, b, 3f)
                    drawCircle(accent, 12.dp.toPx(), a); drawCircle(Color.White, 12.dp.toPx(), b, style = Stroke(4f))
                }
                MaskType.RADIAL -> {
                    val c = toScreen(mask.cx, mask.cy)
                    val sz = Size(mask.rx * r.w * 2, mask.ry * r.h * 2)
                    drawOval(Color.White, Offset(c.x - sz.width / 2, c.y - sz.height / 2), sz, style = Stroke(3f))
                    val inner = 1f - mask.feather.coerceIn(0.02f, 1f)
                    val si = Size(sz.width * inner, sz.height * inner)
                    drawOval(Color.White.copy(alpha = 0.5f), Offset(c.x - si.width / 2, c.y - si.height / 2), si, style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))))
                    drawCircle(accent, 10.dp.toPx(), c)
                }
                MaskType.BRUSH -> {
                    if (live.size >= 4) {
                        val p = androidx.compose.ui.graphics.Path()
                        val s0 = toScreen(live[0], live[1]); p.moveTo(s0.x, s0.y)
                        var i = 2
                        while (i + 1 < live.size) { val s = toScreen(live[i], live[i + 1]); p.lineTo(s.x, s.y); i += 2 }
                        drawPath(p, (if (ui.erase) Color.White else accent).copy(alpha = 0.5f), style = Stroke(ui.brushSize * r.w, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                    }
                }
                else -> {}
            }
        }
    }
}

/** Heal: tap a blemish to remove it; circles show healed spots. */
@Composable
fun HealOverlay(
    spots: List<Spot>, imgW: Int, imgH: Int, size: Float,
    onMove: (Int, Spot) -> Unit = { _, _ -> }, onDelete: (Int) -> Unit = {},
    onTap: (Float, Float) -> Unit,
) {
    val tap by rememberUpdatedState(onTap)
    val move by rememberUpdatedState(onMove)
    val del by rememberUpdatedState(onDelete)
    val current by rememberUpdatedState(spots)
    // While dragging: which spot, which part (true = the spot, false = its source) and where it is now.
    var drag by remember { mutableStateOf<Triple<Int, Boolean, Spot>?>(null) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val bw = with(density) { maxWidth.toPx() }; val bh = with(density) { maxHeight.toPx() }
        val r = fit(bw, bh, imgW, imgH, with(density) { 6.dp.toPx() })
        val grab = with(density) { 14.dp.toPx() }
        Canvas(Modifier.fillMaxSize().pointerInput(r) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val p0 = down.position
                // Hit test: the spot itself first (topmost = last added), then its source circle.
                var hit: Pair<Int, Boolean>? = null
                for (i in current.indices.reversed()) {
                    val sp = current[i]
                    val c = Offset(r.left + sp.x * r.w, r.top + sp.y * r.h)
                    val rad = sp.r * r.w + grab
                    if ((p0 - c).getDistance() <= rad) { hit = i to true; break }
                }
                if (hit == null) for (i in current.indices.reversed()) {
                    val sp = current[i]
                    val c = Offset(r.left + (sp.x + sp.dx) * r.w, r.top + sp.y * r.h + sp.dy * r.w)
                    if ((p0 - c).getDistance() <= sp.r * r.w + grab) { hit = i to false; break }
                }
                val slop = viewConfiguration.touchSlop
                val t0 = down.uptimeMillis
                var moved = false
                var longPressed = false
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                    if (!ch.pressed) break
                    val d = ch.position - p0
                    if (!moved && d.getDistance() > slop) moved = true
                    if (!moved && !longPressed && hit != null && ch.uptimeMillis - t0 > 550) {
                        longPressed = true
                        del(hit.first)
                        drag = null
                    }
                    if (moved && hit != null && !longPressed) {
                        ch.consume()
                        val (i, body) = hit
                        val sp = current.getOrNull(i) ?: break
                        val ddx = d.x / r.w; val ddy = d.y / r.w
                        drag = Triple(i, body, if (body) {
                            // Move the spot; its source stays where it was.
                            val nx = (sp.x + ddx).coerceIn(0f, 1f); val ny = (sp.y + d.y / r.h).coerceIn(0f, 1f)
                            sp.copy(x = nx, y = ny, dx = sp.dx - (nx - sp.x), dy = sp.dy - (ny - sp.y) * r.h / r.w)
                        } else sp.copy(dx = sp.dx + ddx, dy = sp.dy + ddy))
                    }
                }
                val dr = drag
                drag = null
                when {
                    longPressed -> {}
                    moved && dr != null -> move(dr.first, dr.third)
                    !moved && hit == null -> {
                        val x = (p0.x - r.left) / r.w; val y = (p0.y - r.top) / r.h
                        if (x in 0f..1f && y in 0f..1f) tap(x, y)
                    }
                }
            }
        }) {
            current.forEachIndexed { i, s0 ->
                val dragging = drag?.first == i
                val s = if (dragging) drag!!.third else s0
                val c = Offset(r.left + s.x * r.w, r.top + s.y * r.h)
                val col = if (dragging) Pro.Amber else Color.White
                drawCircle(Color.Black.copy(alpha = 0.35f), s.r * r.w, c, style = Stroke(4f))
                drawCircle(col, s.r * r.w, c, style = Stroke(2f))
                val src = Offset(c.x + s.dx * r.w, c.y + s.dy * r.w)
                drawCircle(col.copy(alpha = 0.6f), s.r * r.w, src, style = Stroke(1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))))
                drawLine(col.copy(alpha = 0.4f), c, src, 1.5f)
            }
            // Brush size preview in the corner.
            drawCircle(Pro.Amber, size * r.w, Offset(r.left + size * r.w + 12f, r.top + size * r.w + 12f), style = Stroke(3f))
        }
    }
}

/** The Masks panel: add, pick, show / hide, delete masks; edit the selected mask's settings. */
@Composable
fun MasksPanel(
    masks: List<PhotoMask>, ui: LocalToolsState, onMasks: (List<PhotoMask>) -> Unit, onAi: ((MaskType) -> Unit)? = null, video: Boolean = false,
    onTrackPen: ((PhotoMask) -> Unit)? = null, penTracking: String? = null,
    /** Photo: add an AI selection (Subject / Sky / Depth) to mask [parentId] with combine mode 1 add, 2 subtract, 3 intersect. */
    onAiSub: ((type: MaskType, combine: Int, parentId: String) -> Unit)? = null,
    /** Photo: (re)make a People mask — mask id (null = new), the parts, and settings to give it. */
    onPeople: ((id: String?, parts: List<Int>, setup: (PhotoMask) -> PhotoMask) -> Unit)? = null,
) {
    SectionTitle("ADD A MASK")
    val full = video && masks.size >= MAX_VIDEO_MASKS
    if (full) Text("This clip has the most masks it can have ($MAX_VIDEO_MASKS). Delete one to add another.", color = Pro.Dim, fontSize = 11.sp)
    if (!full) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.hintedScroll(horizontal = true)) {
        SmallAction(Icons.Filled.Gradient, "Linear") { val m = PhotoMask(type = MaskType.LINEAR); onMasks(masks + m); ui.selected = m.id }
        SmallAction(Icons.Filled.RadioButtonUnchecked, "Radial") { val m = PhotoMask(type = MaskType.RADIAL); onMasks(masks + m); ui.selected = m.id }
        if (!video) SmallAction(Icons.Filled.Brush, "Brush") { val m = PhotoMask(type = MaskType.BRUSH); onMasks(masks + m); ui.selected = m.id }
        SmallAction(Icons.Filled.Palette, "Colour range") { val m = PhotoMask(type = MaskType.COLOR_RANGE); onMasks(masks + m); ui.selected = m.id }
        SmallAction(Icons.Filled.Contrast, "Brightness range") { val m = PhotoMask(type = MaskType.LUMA_RANGE); onMasks(masks + m); ui.selected = m.id }
        if (video) SmallAction(Icons.Filled.Gesture, "Shape (pen)") { val m = PhotoMask(type = MaskType.PEN, pts = PenMask.starter(), feather = 0.3f); onMasks(masks + m); ui.selected = m.id }
        if (onAi != null) {
            SmallAction(Icons.Filled.Visibility, "Subject (AI)") { onAi(MaskType.SUBJECT) }
            SmallAction(Icons.Filled.Visibility, "Sky (AI)") { onAi(MaskType.SKY) }
            if (!video) {
                SmallAction(Icons.Filled.TouchApp, "Object (AI)") { onAi(MaskType.OBJECT) }
                SmallAction(Icons.Filled.Layers, "Depth (AI)") { onAi(MaskType.DEPTH) }
                if (onPeople != null) SmallAction(Icons.Filled.Face, "People (AI)") { onPeople(null, listOf(1), { it }) }
            }
        }
    }
    if (masks.isEmpty() && !video) {
        Text("Masks change only part of the photo: a linear gradient (e.g. darken a sky), a radial (spotlight a face), a brush you paint with, or a colour / brightness range (e.g. only the blues, only the highlights).", color = Pro.Dim, fontSize = 11.sp)
        return
    }
    if (masks.isEmpty()) return
    SectionTitle("MASKS")
    masks.forEachIndexed { i, m ->
        val sel = m.id == ui.selected
        Row(
            Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp))
                .background(if (sel) Pro.Amber.copy(alpha = 0.16f) else Ed.Raised)
                .border(1.dp, if (sel) Pro.Amber else Color.Transparent, RoundedCornerShape(8.dp))
                .clickable { ui.selected = m.id }.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${i + 1}. ${m.type.label}" + (if (m.type == MaskType.PEOPLE) " · " + m.parts.orEmpty().joinToString(", ") { PeopleMask.PARTS.getOrElse(it) { "" } } else "") + if (m.invert) " (inverted)" else "", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Icon(if (m.visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, contentDescription = "Show", tint = Color.White,
                modifier = Modifier.size(20.dp).clickable { onMasks(masks.map { if (it.id == m.id) it.copy(visible = !it.visible) else it }) })
            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color.White,
                modifier = Modifier.padding(start = 10.dp).size(20.dp).clickable { onMasks(masks.filter { it.id != m.id }); if (sel) ui.selected = null })
        }
    }
    val m = masks.firstOrNull { it.id == ui.selected } ?: return
    fun upd(f: (PhotoMask) -> PhotoMask) = onMasks(masks.map { if (it.id == m.id) f(it) else it })
    fun g(f: (ProGrade) -> ProGrade) = upd { it.copy(grade = f(it.grade)) }
    SectionTitle("MASK ${masks.indexOf(m) + 1} · ${m.type.label.uppercase()}")
    when (m.type) {
        MaskType.LINEAR -> Text("Drag the filled handle (full effect) and the ring (no effect) on the photo.", color = Pro.Dim, fontSize = 11.sp)
        MaskType.RADIAL -> {
            Text("Drag the middle to move it, drag near the edge to resize.", color = Pro.Dim, fontSize = 11.sp)
            EdSlider("Feather", m.feather, 0.02f..1f, 0.5f, ::pct) { v -> upd { it.copy(feather = v) } }
        }
        MaskType.BRUSH -> {
            Text("Paint on the photo with your finger.", color = Pro.Dim, fontSize = 11.sp)
            Chips(listOf("Add", "Erase"), if (ui.erase) 1 else 0) { ui.erase = it == 1 }
            EdSlider("Brush size", ui.brushSize, 0.01f..0.3f, 0.08f, ::pct) { ui.brushSize = it }
            EdSlider("Softness", ui.brushSoft, 0f..1f, 0.5f, ::pct) { ui.brushSoft = it }
            Text("CLEAR STROKES", style = Pro.Label.copy(color = Pro.Red, fontSize = 10.sp), modifier = Modifier.clickable { upd { it.copy(strokes = emptyList()) } }.padding(6.dp))
        }
        MaskType.COLOR_RANGE -> {
            Text("Tap a colour on the photo to select everything with that colour (e.g. the blue of the sky). Red shows what's selected.", color = Pro.Dim, fontSize = 11.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(22.dp).clip(RoundedCornerShape(4.dp)).background(Color(android.graphics.Color.HSVToColor(floatArrayOf(m.hue, 0.8f, 0.9f)))))
                Text("  Picked colour", color = Pro.Dim, fontSize = 11.sp)
            }
            EdSlider("Colour", m.hue, 0f..360f, 210f, { "${it.toInt()}°" }) { v -> upd { it.copy(hue = v) } }
            EdSlider("Range", m.hueWidth, 4f..160f, 40f, { "${it.toInt()}°" }) { v -> upd { it.copy(hueWidth = v) } }
            EdSlider("Softness", m.rangeSoft, 0f..1f, 0.4f, ::pct) { v -> upd { it.copy(rangeSoft = v) } }
        }
        MaskType.LUMA_RANGE -> {
            Text("Selects by brightness — e.g. only the highlights or only the shadows. Tap the photo to centre the range on a spot.", color = Pro.Dim, fontSize = 11.sp)
            Chips(listOf("Shadows", "Midtones", "Highlights"), -1) { i ->
                val (a, b) = listOf(0f to 0.3f, 0.3f to 0.7f, 0.7f to 1f)[i]
                upd { it.copy(lumLow = a, lumHigh = b) }
            }
            EdSlider("From", m.lumLow, 0f..1f, 0.7f, ::pct) { v -> upd { it.copy(lumLow = v.coerceAtMost(it.lumHigh)) } }
            EdSlider("To", m.lumHigh, 0f..1f, 1f, ::pct) { v -> upd { it.copy(lumHigh = v.coerceAtLeast(it.lumLow)) } }
            EdSlider("Softness", m.rangeSoft, 0f..1f, 0.4f, ::pct) { v -> upd { it.copy(rangeSoft = v) } }
        }
        MaskType.PEN -> {
            Text("Drag the points onto the outline of what you want (pause first). Tap the picture to add a point there; hold a point to remove it. The curve runs smoothly through the points.", color = Pro.Dim, fontSize = 11.sp)
            EdSlider("Soft edge", m.feather, 0f..1f, 0.3f, ::pct) { v -> upd { it.copy(feather = v) } }
            if (onTrackPen != null) {
                Text(
                    if (penTracking == m.id) "■ STOP TRACKING" else "⌖ TRACK THE MASK FROM THE PLAYHEAD", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
                    modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable { onTrackPen(m) }.padding(10.dp),
                )
                if (!m.ptrack.isNullOrEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✓ Tracked through ${m.ptrack.size} frames", style = Pro.Label.copy(color = Pro.Green), modifier = Modifier.weight(1f))
                    Text("REMOVE TRACK", style = Pro.Label.copy(color = Pro.Red, fontSize = 10.sp), modifier = Modifier.clickable { upd { it.copy(ptrack = null, pref = null) } }.padding(6.dp))
                }
                Text("Planar tracking: the mask follows the surface it covers (a face, a sign, a car) as it moves.", color = Pro.Dim, fontSize = 11.sp)
            }
        }
        MaskType.DEPTH -> {
            Text("Selects by distance from the camera (AI depth): white in the mask = near. Set the range below.", color = Pro.Dim, fontSize = 11.sp)
            Chips(listOf("Near", "Middle", "Far"), -1) { i ->
                val (a, b) = listOf(0.65f to 1f, 0.35f to 0.65f, 0f to 0.35f)[i]
                upd { it.copy(lumLow = a, lumHigh = b) }
            }
            EdSlider("Farthest", m.lumLow, 0f..1f, 0.7f, ::pct) { v -> upd { it.copy(lumLow = v.coerceAtMost(it.lumHigh)) } }
            EdSlider("Nearest", m.lumHigh, 0f..1f, 1f, ::pct) { v -> upd { it.copy(lumHigh = v.coerceAtLeast(it.lumLow)) } }
            EdSlider("Softness", m.rangeSoft, 0f..1f, 0.4f, ::pct) { v -> upd { it.copy(rangeSoft = v) } }
        }
        MaskType.PEOPLE -> {
            Text("Parts of every person in the photo, found by the AI. Tap parts to add or remove them.", color = Pro.Dim, fontSize = 11.sp)
            val cur = m.parts.orEmpty()
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.hintedScroll(horizontal = true).padding(vertical = 4.dp)) {
                PeopleMask.PARTS.forEachIndexed { k, name ->
                    PillChip(name, k in cur) {
                        val next = if (k in cur) cur - k else (cur + k).sorted()
                        if (next.isNotEmpty() && onPeople != null) onPeople(m.id, next, { it })
                    }
                }
            }
            SectionTitle("PORTRAIT LOOKS")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.hintedScroll(horizontal = true).padding(vertical = 4.dp)) {
                @Composable fun look(name: String, parts: List<Int>, f: (PhotoMask) -> PhotoMask) = PillChip(name, false, accentText = true) { onPeople?.invoke(m.id, parts, f) }
                look("Smooth skin", listOf(1, 2)) { it.copy(texture = -0.6f, clarity = -0.15f, grade = ProGrade()) }
                look("Whiten teeth", listOf(7)) { it.copy(grade = ProGrade(exposure = 0.25f, saturation = -0.55f), texture = 0f, clarity = 0f) }
                look("Brighten eyes", listOf(3)) { it.copy(grade = ProGrade(exposure = 0.3f, saturation = -0.25f), texture = 0f, clarity = 0f) }
                look("Iris enhance", listOf(4)) { it.copy(grade = ProGrade(exposure = 0.2f, saturation = 0.25f), clarity = 0.5f, texture = 0.3f) }
                look("Lip colour", listOf(6)) { it.copy(grade = ProGrade(saturation = 0.25f, vibrance = 0.15f), texture = 0f, clarity = 0f) }
                look("Hair shine", listOf(8)) { it.copy(grade = ProGrade(contrast = 0.15f), texture = 0.35f, clarity = 0.3f) }
                look("Brows define", listOf(5)) { it.copy(grade = ProGrade(exposure = -0.15f, contrast = 0.15f), texture = 0.3f, clarity = 0f) }
            }
            Text("Each look picks the parts and sets the adjustments below — fine-tune them to taste.", color = Pro.Dim, fontSize = 11.sp)
        }
        else -> Text("Selected by the AI.", color = Pro.Dim, fontSize = 11.sp)
    }
    // ---- Add / subtract / intersect more selections (photo).
    if (!video) {
        SectionTitle("COMBINE") { upd { it.copy(subs = null) } }
        var mode by remember { mutableStateOf(1) }
        Chips(listOf("+ Add", "− Subtract", "∩ Intersect"), mode - 1) { mode = it + 1 }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.hintedScroll(horizontal = true).padding(vertical = 4.dp)) {
            fun addSub(t: MaskType) = upd { it.copy(subs = it.subs.orEmpty() + PhotoMask(type = t, combine = mode, lumLow = if (t == MaskType.LUMA_RANGE) 0.7f else 0.6f)) }
            PillChip("Colour range", false) { addSub(MaskType.COLOR_RANGE) }
            PillChip("Brightness range", false) { addSub(MaskType.LUMA_RANGE) }
            if (onAiSub != null) {
                PillChip("Depth (AI)", false) { onAiSub(MaskType.DEPTH, mode, m.id) }
                PillChip("Subject (AI)", false) { onAiSub(MaskType.SUBJECT, mode, m.id) }
                PillChip("Sky (AI)", false) { onAiSub(MaskType.SKY, mode, m.id) }
            }
        }
        Text("Refine this mask: e.g. a brush or gradient minus a brightness range, or the sky intersected with a colour range.", color = Pro.Dim, fontSize = 11.sp)
        m.subs.orEmpty().forEachIndexed { k, sub ->
            fun us(f: (PhotoMask) -> PhotoMask) = upd { it.copy(subs = it.subs.orEmpty().mapIndexed { j, x -> if (j == k) f(x) else x }) }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(listOf("+", "+", "−", "∩")[sub.combine.coerceIn(0, 3)] + " " + sub.type.label + if (sub.invert) " (inverted)" else "", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(20.dp).clickable { upd { it.copy(subs = it.subs.orEmpty().filterIndexed { j, _ -> j != k }.ifEmpty { null }) } })
            }
            when (sub.type) {
                MaskType.COLOR_RANGE -> {
                    EdSlider("  Colour", sub.hue, 0f..360f, 210f, { "${it.toInt()}°" }) { v -> us { it.copy(hue = v) } }
                    EdSlider("  Range", sub.hueWidth, 4f..160f, 40f, { "${it.toInt()}°" }) { v -> us { it.copy(hueWidth = v) } }
                }
                MaskType.LUMA_RANGE, MaskType.DEPTH -> {
                    EdSlider(if (sub.type == MaskType.DEPTH) "  Farthest" else "  From", sub.lumLow, 0f..1f, 0.7f, ::pct) { v -> us { it.copy(lumLow = v.coerceAtMost(it.lumHigh)) } }
                    EdSlider(if (sub.type == MaskType.DEPTH) "  Nearest" else "  To", sub.lumHigh, 0f..1f, 1f, ::pct) { v -> us { it.copy(lumHigh = v.coerceAtLeast(it.lumLow)) } }
                }
                else -> {}
            }
            if (sub.type == MaskType.COLOR_RANGE || sub.type == MaskType.LUMA_RANGE || sub.type == MaskType.DEPTH)
                EdSlider("  Softness", sub.rangeSoft, 0f..1f, 0.4f, ::pct) { v -> us { it.copy(rangeSoft = v) } }
            Chips(listOf("Normal", "Inverted"), if (sub.invert) 1 else 0) { i -> us { it.copy(invert = i == 1) } }
            EdSlider("  Strength", sub.amount, 0f..1f, 1f, ::pct) { v -> us { it.copy(amount = v) } }
        }
    }
    if (!video && m.type in listOf(MaskType.SUBJECT, MaskType.SKY, MaskType.OBJECT, MaskType.PEOPLE)) {
        EdSlider("Refine edge (hair)", m.refine, 0f..1f, 0f, ::pct) { v -> upd { it.copy(refine = v) } }
        Text("Makes the edge follow fine detail in the photo — hair, fur, leaves against the sky.", color = Pro.Dim, fontSize = 11.sp)
    }
    Chips(listOf("Normal", "Inverted"), if (m.invert) 1 else 0) { i -> upd { it.copy(invert = i == 1) } }
    Chips(listOf("Show mask", "Hide mask"), if (ui.showOverlay) 0 else 1) { ui.showOverlay = it == 0 }
    EdSlider("Mask strength", m.amount, 0f..1f, 1f, ::pct) { v -> upd { it.copy(amount = v) } }
    SectionTitle("ADJUST INSIDE THE MASK") { g { ProGrade() } }
    val gr = m.grade
    EdSlider("Exposure", gr.exposure, -3f..3f, format = ::ev) { v -> g { it.copy(exposure = v) } }
    EdSlider("Contrast", gr.contrast) { v -> g { it.copy(contrast = v) } }
    EdSlider("Highlights", gr.highlights) { v -> g { it.copy(highlights = v) } }
    EdSlider("Shadows", gr.shadows) { v -> g { it.copy(shadows = v) } }
    EdSlider("Temperature", gr.temperature) { v -> g { it.copy(temperature = v) } }
    EdSlider("Tint", gr.tint) { v -> g { it.copy(tint = v) } }
    EdSlider("Saturation", gr.saturation) { v -> g { it.copy(saturation = v) } }
    EdSlider("Dehaze", gr.dehaze) { v -> g { it.copy(dehaze = v) } }
    if (!video) {
        EdSlider("Texture", m.texture) { v -> upd { it.copy(texture = v) } }
        EdSlider("Clarity", m.clarity) { v -> upd { it.copy(clarity = v) } }
        EdSlider("Noise reduction", m.noise, 0f..1f, 0f, ::pct) { v -> upd { it.copy(noise = v) } }
    }
}

/** Camera details copied from the original into an exported photo. */
private val EXIF_KEEP = listOf(
    androidx.exifinterface.media.ExifInterface.TAG_DATETIME_ORIGINAL, androidx.exifinterface.media.ExifInterface.TAG_DATETIME,
    androidx.exifinterface.media.ExifInterface.TAG_OFFSET_TIME_ORIGINAL, androidx.exifinterface.media.ExifInterface.TAG_MAKE,
    androidx.exifinterface.media.ExifInterface.TAG_MODEL, androidx.exifinterface.media.ExifInterface.TAG_F_NUMBER,
    androidx.exifinterface.media.ExifInterface.TAG_EXPOSURE_TIME, androidx.exifinterface.media.ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
    androidx.exifinterface.media.ExifInterface.TAG_FOCAL_LENGTH, androidx.exifinterface.media.ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
    androidx.exifinterface.media.ExifInterface.TAG_LENS_MAKE, androidx.exifinterface.media.ExifInterface.TAG_LENS_MODEL,
    androidx.exifinterface.media.ExifInterface.TAG_WHITE_BALANCE, androidx.exifinterface.media.ExifInterface.TAG_FLASH,
    androidx.exifinterface.media.ExifInterface.TAG_ARTIST, androidx.exifinterface.media.ExifInterface.TAG_COPYRIGHT,
)
private val EXIF_GPS = listOf(
    androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE, androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE_REF,
    androidx.exifinterface.media.ExifInterface.TAG_GPS_LONGITUDE, androidx.exifinterface.media.ExifInterface.TAG_GPS_LONGITUDE_REF,
    androidx.exifinterface.media.ExifInterface.TAG_GPS_ALTITUDE, androidx.exifinterface.media.ExifInterface.TAG_GPS_ALTITUDE_REF,
    androidx.exifinterface.media.ExifInterface.TAG_GPS_TIMESTAMP, androidx.exifinterface.media.ExifInterface.TAG_GPS_DATESTAMP,
)

/** Why the last photo export failed, when it's something the person should know (e.g. no AV1 encoder). */
object PhotoExportError { @Volatile var last: String? = null }

val PHOTO_FORMATS = listOf("JPEG", "PNG", "WebP", "HEIC", "TIFF 16-bit", "TIFF 8-bit", "AVIF", "JPEG XL")

/**
 * Saves an exported photo as JPEG, PNG, WebP or HEIC, optionally with the original's camera
 * details (and location unless [stripGps]), into the chosen folder or DCIM/AlcorCutPro.
 */
fun savePhotoAs(context: Context, bmp0: Bitmap, format: Int, quality: Int, exifFrom: Uri?, stripGps: Boolean): Uri? {
    PhotoExportError.last = null
    val f = if (format == 3 && Build.VERSION.SDK_INT < 28) 0 else format.coerceIn(0, PHOTO_FORMATS.lastIndex)
    val ext = listOf("jpg", "png", "webp", "heic", "tif", "tif", "avif", "jxl")[f]
    val mime = listOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/tiff", "image/tiff", "image/avif", "image/jxl")[f]
    // File name from the template (Export → FILE NAME), output sharpening and colour space (Export → OUTPUT).
    val name = ExportOpts.fileName(context, exifFrom) + ".$ext"
    val sharp = ExportOpts.outputSharpen(bmp0)
    val bmp = ExportOpts.toSpace(sharp)
    val tmp = File(context.cacheDir, "export_${System.currentTimeMillis()}.$ext")
    try {
        when (f) {
            4, 5 -> ExportOpts.writeTiff(bmp, tmp, bits16 = f == 4)
            6 -> if (!AvifWriter.write(bmp, tmp, quality)) { PhotoExportError.last = "AVIF: " + (AvifWriter.lastError ?: "this phone has no AV1 encoder (needs Android 14 or newer)"); return null }
            7 -> try {
                tmp.writeBytes(com.awxkee.jxlcoder.JxlCoder.encode(bmp.let { if (it.config == Bitmap.Config.ARGB_8888) it else it.copy(Bitmap.Config.ARGB_8888, false) },
                    quality = quality.coerceIn(1, 100), effort = com.awxkee.jxlcoder.JxlEffort.SQUIRREL))
            } catch (t: Throwable) { PhotoExportError.last = "JPEG XL: " + (t.message ?: t.javaClass.simpleName); return null }
            3 -> {
                val w = androidx.heifwriter.HeifWriter.Builder(tmp.path, bmp.width, bmp.height, androidx.heifwriter.HeifWriter.INPUT_MODE_BITMAP)
                    .setQuality(quality.coerceIn(1, 100)).build()
                w.start(); w.addBitmap(bmp); w.stop(60_000); w.close()
            }
            0 -> {
                // Ultra HDR (Export → OUTPUT) keeps the system encoder (the gain map lives inside its JPEG);
                // otherwise the app's own encoder: full colour detail (4:4:4) and true maximum quality at 100 %.
                val gm = ExportOpts.withGainmap(bmp)
                if (gm !== bmp) tmp.outputStream().use { os -> gm.compress(Bitmap.CompressFormat.JPEG, quality, os) }
                else writeJpegMax(bmp, tmp, quality)
            }
            else -> tmp.outputStream().use { os ->
                val cf = when (f) {
                    1 -> Bitmap.CompressFormat.PNG
                    2 -> if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
                    else -> Bitmap.CompressFormat.JPEG
                }
                bmp.compress(cf, quality, os)
            }
        }
        // Camera details (EXIF) for JPEG / WebP.
        if (exifFrom != null && (f == 0 || f == 2)) try {
            val src = context.contentResolver.openInputStream(exifFrom)?.use { androidx.exifinterface.media.ExifInterface(it) }
            if (src != null) {
                val dst = androidx.exifinterface.media.ExifInterface(tmp.path)
                for (t in EXIF_KEEP) src.getAttribute(t)?.let { dst.setAttribute(t, it) }
                if (!stripGps) for (t in EXIF_GPS) src.getAttribute(t)?.let { dst.setAttribute(t, it) }
                dst.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_SOFTWARE, "AlcorCut Pro")
                dst.saveAttributes()
            }
        } catch (_: Throwable) {}
        // Your name and copyright (Settings → COPYRIGHT) in every export.
        if (f == 0 || f == 2 || f == 1) com.dronedirector.veyrocutpro.camera.Copyright.stampFile(context, tmp)
        // IPTC title / caption / keywords (Export → CAPTION & KEYWORDS), as XMP inside the picture.
        if (f == 0 || f == 2) PhotoMeta.embedXmp()?.let { xmp ->
            try {
                val ex = androidx.exifinterface.media.ExifInterface(tmp.path)
                ex.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_XMP, xmp)
                if (PhotoMeta.caption.isNotBlank()) ex.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_IMAGE_DESCRIPTION, PhotoMeta.caption)
                ex.saveAttributes()
            } catch (_: Throwable) {}
        }
        SaveFolder.write(context, false, name, mime) { os -> tmp.inputStream().use { it.copyTo(os) } }?.let { return it }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/AlcorCutPro")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val target = com.dronedirector.veyrocutpro.camera.Storage.insert(context, com.dronedirector.veyrocutpro.camera.Storage.load(context, com.dronedirector.veyrocutpro.camera.StorageArea.PHOTO), values, com.dronedirector.veyrocutpro.camera.Storage.Kind.IMAGE)
            // Older Android versions don't know the newest picture types in the photo library: save those in Downloads instead.
            ?: (if (f >= 6 && Build.VERSION.SDK_INT >= 29) try {
                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name); put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/AlcorCutPro"); put(MediaStore.MediaColumns.IS_PENDING, 1)
                })
            } catch (_: Throwable) { null } else null) ?: return null
        return try {
            resolver.openOutputStream(target)?.use { os -> tmp.inputStream().use { it.copyTo(os) } }
            com.dronedirector.veyrocutpro.camera.Storage.publish(context, target)
            target
        } catch (_: Throwable) { com.dronedirector.veyrocutpro.camera.Storage.discard(context, target); null }
    } finally {
        tmp.delete()
        if (bmp !== bmp0 && bmp !== sharp) bmp.recycle()
        if (sharp !== bmp0) sharp.recycle()
    }
}


/** Retouch → Liquify: drag on the photo to push, pinch, bloat, twirl or restore; each drag becomes one stroke. */
@Composable
fun LiquifyOverlay(imgW: Int, imgH: Int, tool: Int, size: Float, strength: Float, onStroke: (LiqStroke) -> Unit) {
    val done by rememberUpdatedState(onStroke)
    val t by rememberUpdatedState(tool); val sz by rememberUpdatedState(size); val st by rememberUpdatedState(strength)
    var path by remember { mutableStateOf<List<Offset>>(emptyList()) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val bw = with(density) { maxWidth.toPx() }; val bh = with(density) { maxHeight.toPx() }
        val r = fit(bw, bh, imgW, imgH, with(density) { 6.dp.toPx() })
        val longPx = max(r.w, r.h)
        Canvas(Modifier.fillMaxSize().pointerInput(r) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val pts = arrayListOf(down.position)
                path = pts.toList()
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                    if (!ch.pressed) break
                    if ((ch.position - pts.last()).getDistance() > 3f) { pts += ch.position; path = pts.toList() }
                    ch.consume()
                }
                val norm = ArrayList<Float>(pts.size * 2)
                for (p in pts) { norm += ((p.x - r.left) / r.w).coerceIn(0f, 1f); norm += ((p.y - r.top) / r.h).coerceIn(0f, 1f) }
                path = emptyList()
                done(LiqStroke(t, norm, sz, st))
            }
        }) {
            val rad = sz * longPx
            for (i in 1 until path.size) drawLine(Color.White.copy(alpha = 0.5f), path[i - 1], path[i], 2f)
            path.lastOrNull()?.let { p ->
                drawCircle(Color.Black.copy(alpha = 0.4f), rad, p, style = Stroke(3f))
                drawCircle(Color.White, rad, p, style = Stroke(1.5f))
            }
        }
    }
}

/** Writes [bmp] with the app's own JPEG encoder ([JpegMax]), its colour space embedded. Pixels are read in native memory. */
internal fun writeJpegMax(bmp: Bitmap, file: File, quality: Int) {
    val w = bmp.width; val h = bmp.height
    val src = if (bmp.config == Bitmap.Config.ARGB_8888) bmp else bmp.copy(Bitmap.Config.ARGB_8888, false)
    // Raw RGBA bytes in the picture's own colour space (getPixels would convert to sRGB).
    val buf = java.nio.ByteBuffer.allocateDirect(w * h * 4)
    src.copyPixelsToBuffer(buf); buf.rewind()
    if (src !== bmp) src.recycle()
    val icc = if (Build.VERSION.SDK_INT >= 26) bmp.colorSpace?.takeIf { it != android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB) }?.let { iccOf(it) } else null
    val row = ByteArray(w * 4)
    file.outputStream().buffered(1 shl 16).use { os ->
        JpegMax.write(os, w, h, quality, icc) { y, n, dst ->
            for (r in 0 until n) {
                buf.position((y + r) * w * 4); buf.get(row)
                val o = r * w
                for (x in 0 until w) {
                    val i = x * 4
                    dst[o + x] = (0xFF shl 24) or ((row[i].toInt() and 0xFF) shl 16) or ((row[i + 1].toInt() and 0xFF) shl 8) or (row[i + 2].toInt() and 0xFF)
                }
            }
        }
    }
}

/** The ICC profile Android writes for [cs]: taken from a tiny JPEG the system encodes in that colour space. */
@androidx.annotation.RequiresApi(26)
private fun iccOf(cs: android.graphics.ColorSpace): ByteArray? = try {
    val t = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888, false, cs)
    val b = java.io.ByteArrayOutputStream().also { t.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    t.recycle()
    val parts = java.util.TreeMap<Int, ByteArray>()
    var i = 2
    while (i + 4 <= b.size && (b[i].toInt() and 0xFF) == 0xFF) {
        val m = b[i + 1].toInt() and 0xFF
        if (m == 0xDA || m == 0xD9) break
        val len = ((b[i + 2].toInt() and 0xFF) shl 8) or (b[i + 3].toInt() and 0xFF)
        if (m == 0xE2 && len > 16 && String(b, i + 4, 11, Charsets.US_ASCII) == "ICC_PROFILE") parts[b[i + 16].toInt() and 0xFF] = b.copyOfRange(i + 18, i + 2 + len)
        i += 2 + len
    }
    if (parts.isEmpty()) null else parts.values.fold(ByteArray(0)) { acc, p -> acc + p }
} catch (_: Throwable) { null }
