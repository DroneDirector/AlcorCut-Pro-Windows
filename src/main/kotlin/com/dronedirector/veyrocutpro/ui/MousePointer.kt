package com.dronedirector.veyrocutpro.ui

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Colours for the mouse pointer and the timeline scroll wheels (set apart from the rest of the
 * graphics), and whether the pointer is drawn with effects. Chosen in each screen's gear settings.
 */
object CursorStyle {
    private const val PREFS = "procam"
    val swatches = listOf(
        0xFF4FE3FF, 0xFFFFB020, 0xFFFF4FD8, 0xFF7CFF4F, 0xFFFFFFFF, 0xFFFF3B30, 0xFF9B6BFF, 0xFFFF7A00, 0xFF00FFC2, 0xFFFFE600,
    ).map { Color(it) }
    var pointer by mutableStateOf(Color(0xFF4FE3FF)); private set
    var pointerFx by mutableStateOf(true); private set
    /** Off = the normal Android pointer. */
    var custom by mutableStateOf(true); private set
    var wheel by mutableStateOf(Color(0xFF4FE3FF)); private set
    private var loaded = false

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        pointer = Color(p.getInt("mouse_color", pointer.toArgb()))
        pointerFx = p.getBoolean("mouse_fx", true)
        custom = p.getBoolean("mouse_custom", true)
        wheel = Color(p.getInt("wheel_color", wheel.toArgb()))
    }
    private fun put(c: Context, f: android.content.SharedPreferences.Editor.() -> Unit) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply(f).apply()
    fun setPointer(c: Context, v: Color) { pointer = v; put(c) { putInt("mouse_color", v.toArgb()) } }
    fun setPointerFx(c: Context, v: Boolean) { pointerFx = v; put(c) { putBoolean("mouse_fx", v) } }
    fun setCustom(c: Context, v: Boolean) { custom = v; put(c) { putBoolean("mouse_custom", v) } }
    fun setWheel(c: Context, v: Color) { wheel = v; put(c) { putInt("wheel_color", v.toArgb()) } }

    /** A second tone for gradients: the colour turned a little round the colour wheel. */
    fun partner(c: Color): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(c.toArgb(), hsv)
        hsv[0] = (hsv[0] + 70f) % 360f
        if (hsv[1] < 0.15f) { hsv[1] = 0f; hsv[2] = 0.75f }
        return Color(android.graphics.Color.HSVToColor(hsv))
    }
}

private val NullIcon: PointerIcon? by lazy {
    try { PointerIcon(android.view.PointerIcon.getSystemIcon(appContextForIcon ?: return@lazy null, android.view.PointerIcon.TYPE_NULL)) } catch (_: Throwable) { null }
}
private var appContextForIcon: Context? = null

/**
 * Draws a glowing mouse pointer over the whole app (the Android arrow is hidden while it's on).
 * Effects: a soft halo, a comet trail while moving, a ripple on every click and a slowly orbiting
 * spark, in the same style as the scroll wheels and the download bar. Touch is not affected.
 */
@Composable
fun MousePointerHost(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { CursorStyle.load(ctx) }
    appContextForIcon = ctx.applicationContext
    var pos by remember { mutableStateOf<Offset?>(null) }
    // Recent positions with their time, for the trail.
    val trail = remember { ArrayDeque<Pair<Offset, Long>>() }
    var clickAt by remember { mutableLongStateOf(0L) }
    var clickPos by remember { mutableStateOf(Offset.Zero) }
    var pressed by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(0L) }
    val active = CursorStyle.custom && pos != null
    val onMonitor = androidx.compose.runtime.rememberUpdatedState(onExternalMonitor())
    // The system arrow is hidden only once our own pointer is actually being drawn, so there's always a pointer.
    val icon = if (active) NullIcon else null
    Box(
        Modifier.fillMaxSize()
            .then(if (icon != null) Modifier.pointerHoverIcon(icon, overrideDescendants = true) else Modifier)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        // Watch only (never consume), before the screens get the event.
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        val ch = e.changes.firstOrNull() ?: continue
                        // A hover can only come from a pointer (mouse, DeX touchpad, stylus hover). Samsung DeX may report
                        // its touchpad pointer as a finger: treat any hover as the mouse, and a real touch as touch.
                        val hover = (e.type == PointerEventType.Move || e.type == PointerEventType.Enter) && !ch.pressed
                        // On a monitor (Samsung DeX, desktop mode) there is no touch screen: everything there is the pointer.
                        val mouseLike = ch.type == PointerType.Mouse || hover || onMonitor.value
                        if (!mouseLike) { if (ch.type == PointerType.Touch) pos = null; continue }
                        val t = System.currentTimeMillis()
                        when (e.type) {
                            PointerEventType.Exit -> pos = null
                            PointerEventType.Press -> { pressed = true; clickAt = t; clickPos = ch.position; pos = ch.position }
                            PointerEventType.Release -> { pressed = false; pos = ch.position }
                            else -> pos = ch.position
                        }
                        pos?.let { p -> trail.addLast(p to t); while (trail.size > 24) trail.removeFirst() }
                    }
                }
            },
    ) {
        content()
        DragGhost.at?.let { g ->
            Canvas(Modifier.fillMaxSize().zIndex(999f)) {
                val u = 1.dp.toPx()
                val w = 96 * u; val h = 54 * u
                val tl = Offset(g.x - w / 2, g.y - h - 10 * u)
                val edge = if (DragGhost.canDrop) CursorStyle.pointer else Color.White.copy(alpha = 0.5f)
                drawRoundRect(edge.copy(alpha = 0.25f), tl - Offset(4 * u, 4 * u), androidx.compose.ui.geometry.Size(w + 8 * u, h + 8 * u), androidx.compose.ui.geometry.CornerRadius(10 * u))
                DragGhost.image?.let { im ->
                    drawImage(im, srcOffset = androidx.compose.ui.unit.IntOffset.Zero, srcSize = androidx.compose.ui.unit.IntSize(im.width, im.height),
                        dstOffset = androidx.compose.ui.unit.IntOffset(tl.x.toInt(), tl.y.toInt()), dstSize = androidx.compose.ui.unit.IntSize(w.toInt(), h.toInt()), alpha = 0.85f)
                } ?: drawRoundRect(Color(0xFF1A1E26), tl, androidx.compose.ui.geometry.Size(w, h), androidx.compose.ui.geometry.CornerRadius(6 * u))
                drawRoundRect(edge, tl, androidx.compose.ui.geometry.Size(w, h), androidx.compose.ui.geometry.CornerRadius(6 * u), style = Stroke(2 * u))
            }
        }
        if (active) {
            // Keeps the effects moving (and the trail fading) while the mouse is in the window.
            LaunchedEffect(Unit) { while (true) withFrameNanos { now = System.currentTimeMillis() } }
            val fx = CursorStyle.pointerFx
            val col = CursorStyle.pointer
            val second = CursorStyle.partner(col)
            val ph = if (fx) rememberInfiniteTransition(label = "ptr").animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "p").value else 0f
            Canvas(Modifier.fillMaxSize().zIndex(1000f)) {
                val p = pos ?: return@Canvas
                val u = 1.dp.toPx()
                if (fx) {
                    // Comet trail.
                    val pts = trail.filter { now - it.second < 260 }
                    pts.forEachIndexed { i, (q, tt) ->
                        val life = 1f - (now - tt) / 260f
                        val k = (i + 1f) / pts.size
                        drawCircle(Brush.radialGradient(listOf(second.copy(alpha = 0.45f * life * k), Color.Transparent), q, 9 * u * k + 2 * u), 9 * u * k + 2 * u, q)
                    }
                    // Halo, breathing slowly.
                    val br = 16 * u + sin(ph * 2 * PI.toFloat()) * 3 * u
                    drawCircle(Brush.radialGradient(listOf(col.copy(alpha = if (pressed) 0.42f else 0.26f), col.copy(alpha = 0.08f), Color.Transparent), p, br), br, p)
                    // Orbiting spark.
                    val a = ph * 2 * PI.toFloat()
                    val sp = p + Offset(cos(a) * 13 * u, sin(a) * 13 * u)
                    drawCircle(Color.White.copy(alpha = 0.85f), 1.6f * u, sp)
                    drawCircle(second.copy(alpha = 0.5f), 3.2f * u, sp)
                    // Click ripple.
                    val dt = now - clickAt
                    if (dt in 0..450) {
                        val f = dt / 450f
                        drawCircle(col.copy(alpha = (1f - f) * 0.9f), (6 + 22 * f) * u, clickPos, style = Stroke((2.5f - 1.5f * f) * u))
                        drawCircle(second.copy(alpha = (1f - f) * 0.5f), (4 + 34 * f) * u, clickPos, style = Stroke(1f * u))
                    }
                }
                // The arrow: tip at the mouse position.
                val s = if (pressed) 0.9f else 1f
                val arrow = Path().apply {
                    moveTo(p.x, p.y)
                    lineTo(p.x, p.y + 17 * u * s)
                    lineTo(p.x + 4.5f * u * s, p.y + 13 * u * s)
                    lineTo(p.x + 7.5f * u * s, p.y + 19.5f * u * s)
                    lineTo(p.x + 10 * u * s, p.y + 18.3f * u * s)
                    lineTo(p.x + 7 * u * s, p.y + 12 * u * s)
                    lineTo(p.x + 12.5f * u * s, p.y + 12 * u * s)
                    close()
                }
                drawPath(arrow, Color.Black.copy(alpha = 0.75f), style = Stroke(3 * u))
                if (fx) drawPath(arrow, Brush.linearGradient(listOf(col, second), p, p + Offset(12 * u, 19 * u)))
                else drawPath(arrow, col)
                drawPath(arrow, Color.White.copy(alpha = if (fx) 0.9f else 0.6f), style = Stroke(1 * u))
            }
        }
    }
}

/** A clip being dragged with the mouse (e.g. from the media strip to the timeline), drawn at the root. */
object DragGhost {
    var image by mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    var label by mutableStateOf("")
    var at by mutableStateOf<Offset?>(null)
    /** True while the ghost is over a place it can be dropped. */
    var canDrop by mutableStateOf(false)
    fun clear() { image = null; at = null; label = ""; canDrop = false }
}

/** Gear-settings block: mouse pointer colour and effects, and (separately) the scroll wheel colour. */
@Composable
fun PointerAndWheelSettings(showWheel: Boolean = true) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { CursorStyle.load(ctx) }
    Text("MOUSE POINTER", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(top = 14.dp))
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Glowing pointer", color = Color.White, fontSize = 15.sp)
            Text("With a mouse connected: AlcorCut's own pointer in your colour. Off = the normal Android arrow.", color = Pro.Dim, fontSize = 12.sp)
        }
        androidx.compose.material3.Switch(CursorStyle.custom, { CursorStyle.setCustom(ctx, it) },
            colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Pro.Amber, checkedThumbColor = Color.Black))
    }
    if (CursorStyle.custom) {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Pointer effects", color = Color.White, fontSize = 15.sp)
                Text("Halo, trail, click ripple and orbiting spark. Off = a plain arrow in the colour below.", color = Pro.Dim, fontSize = 12.sp)
            }
            androidx.compose.material3.Switch(CursorStyle.pointerFx, { CursorStyle.setPointerFx(ctx, it) },
                colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Pro.Amber, checkedThumbColor = Color.Black))
        }
        Text("Pointer colour", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        Swatches(CursorStyle.pointer) { CursorStyle.setPointer(ctx, it) }
    }
    if (showWheel) {
        Text("SCROLL WHEELS", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(top = 14.dp))
        Text("Scroll wheel colour (timeline wheels), kept separate from the pointer and the rest of the app", color = Pro.Dim, fontSize = 12.sp)
        Swatches(CursorStyle.wheel) { CursorStyle.setWheel(ctx, it) }
    }
}

@Composable
private fun Swatches(current: Color, onPick: (Color) -> Unit) {
    Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CursorStyle.swatches.chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { c ->
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).background(c)
                            .border(2.dp, if (c.toArgb() == current.toArgb()) Color.White else Color.Black.copy(alpha = 0.4f), CircleShape)
                            .clickable { onPick(c) },
                    )
                }
            }
        }
    }
}

/**
 * Mouse buttons and wheel on a screen area. [onWheel] gets the wheel step (positive = down / towards
 * you), the pointer position and whether Ctrl / Shift are held; return true when it was used (the
 * scroll is then not passed on). Right and middle clicks are only reported for a mouse.
 */
fun Modifier.mouseActions(
    onWheel: ((dy: Float, dx: Float, at: Offset, ctrl: Boolean, shift: Boolean) -> Boolean)? = null,
    onRightClick: ((Offset) -> Unit)? = null,
    onMiddleClick: ((Offset) -> Unit)? = null,
    onDoubleClick: ((Offset) -> Unit)? = null,
): Modifier = this.pointerInput(onWheel != null, onRightClick != null, onMiddleClick != null, onDoubleClick != null) {
    var lastClick = 0L
    var lastAt = Offset.Zero
    awaitPointerEventScope {
        while (true) {
            val e = awaitPointerEvent()
            val ch = e.changes.firstOrNull() ?: continue
            when (e.type) {
                PointerEventType.Scroll -> if (onWheel != null) {
                    val d = ch.scrollDelta
                    val k = e.keyboardModifiers
                    if (onWheel(d.y, d.x, ch.position, k.isCtrlPressed || k.isMetaPressed, k.isShiftPressed)) e.changes.forEach { it.consume() }
                }
                PointerEventType.Press -> if (ch.type == PointerType.Mouse) {
                    if (onRightClick != null && e.buttons.isSecondaryPressed) { e.changes.forEach { it.consume() }; onRightClick(ch.position) }
                    else if (onMiddleClick != null && e.buttons.isTertiaryPressed) { e.changes.forEach { it.consume() }; onMiddleClick(ch.position) }
                    else if (onDoubleClick != null && e.buttons.isPrimaryPressed) {
                        val t = System.currentTimeMillis()
                        if (t - lastClick < 320 && (ch.position - lastAt).getDistance() < 12f) { lastClick = 0L; onDoubleClick(ch.position) }
                        else { lastClick = t; lastAt = ch.position }
                    }
                }
                else -> {}
            }
        }
    }
}
