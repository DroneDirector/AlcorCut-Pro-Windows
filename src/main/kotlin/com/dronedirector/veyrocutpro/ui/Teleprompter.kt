package com.dronedirector.veyrocutpro.ui

import android.content.Context
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Teleprompter settings and text, kept by the app. */
object Prompter {
    var open by mutableStateOf(false)
    var minimized by mutableStateOf(false)
    var text by mutableStateOf("")
    var sizeSp by mutableFloatStateOf(26f)
    var bold by mutableStateOf(true)
    var italic by mutableStateOf(false)
    var underline by mutableStateOf(false)
    var narrow by mutableStateOf(false)
    var align by mutableStateOf(1) // 0 left, 1 centre, 2 right
    var textColor by mutableStateOf(0xFFFFFFFF.toInt())
    var bgColor by mutableStateOf(0xFF000000.toInt())
    var bgAlpha by mutableFloatStateOf(0.6f)
    /** Scrolling speed (screen points per second); 0 = no scrolling. */
    var speed by mutableFloatStateOf(30f)
    var playing by mutableStateOf(false)
    /** Start scrolling when recording starts. */
    var withRecording by mutableStateOf(true)
    /** Mirror the text (for teleprompter glass that reflects it). */
    var mirror by mutableStateOf(false)
    /** Scroll sideways as one line (like a news ticker) instead of upwards. */
    var horizontal by mutableStateOf(false)
    /** Open by itself when the selfie (front) camera is chosen. */
    var autoSelfie by mutableStateOf(true)
    /** Position (top-left, 0–1 of the screen) and size (0–1 of the screen). */
    var x by mutableFloatStateOf(0.1f)
    var y by mutableFloatStateOf(0.04f)
    var w by mutableFloatStateOf(0.8f)
    var h by mutableFloatStateOf(0.3f)
    /** Bumped to scroll back to the top. */
    var restart by mutableStateOf(0)

    val COLORS = listOf(0xFFFFFFFF, 0xFF000000, 0xFFFFC94A, 0xFFFF5252, 0xFF4FC3F7, 0xFF69F0AE, 0xFFE040FB, 0xFFFF9100, 0xFF9E9E9E, 0xFF1A237E).map { it.toInt() }

    private fun prefs(c: Context) = c.getSharedPreferences("procam", Context.MODE_PRIVATE)
    fun load(c: Context) {
        val p = prefs(c)
        text = p.getString("tp_text", "") ?: ""
        sizeSp = p.getFloat("tp_size", 26f); bold = p.getBoolean("tp_bold", true); italic = p.getBoolean("tp_italic", false)
        underline = p.getBoolean("tp_underline", false); narrow = p.getBoolean("tp_narrow", false); align = p.getInt("tp_align", 1)
        textColor = p.getInt("tp_fg", 0xFFFFFFFF.toInt()); bgColor = p.getInt("tp_bg", 0xFF000000.toInt()); bgAlpha = p.getFloat("tp_bga", 0.6f)
        speed = p.getFloat("tp_speed", 30f); withRecording = p.getBoolean("tp_with_rec", true); mirror = p.getBoolean("tp_mirror", false)
        autoSelfie = p.getBoolean("tp_auto_selfie", true); horizontal = p.getBoolean("tp_horizontal", false)
        x = p.getFloat("tp_x", 0.1f); y = p.getFloat("tp_y", 0.04f); w = p.getFloat("tp_w", 0.8f); h = p.getFloat("tp_h", 0.3f)
    }
    fun save(c: Context) {
        prefs(c).edit().putString("tp_text", text).putFloat("tp_size", sizeSp).putBoolean("tp_bold", bold).putBoolean("tp_italic", italic)
            .putBoolean("tp_underline", underline).putBoolean("tp_narrow", narrow).putInt("tp_align", align)
            .putInt("tp_fg", textColor).putInt("tp_bg", bgColor).putFloat("tp_bga", bgAlpha).putFloat("tp_speed", speed)
            .putBoolean("tp_with_rec", withRecording).putBoolean("tp_mirror", mirror).putBoolean("tp_auto_selfie", autoSelfie).putBoolean("tp_horizontal", horizontal)
            .putFloat("tp_x", x).putFloat("tp_y", y).putFloat("tp_w", w).putFloat("tp_h", h).apply()
    }
}

private val condensed = FontFamily(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL))

/**
 * The teleprompter: a movable, scrolling text box over the camera. Drag the top bar to move it,
 * drag the corner to resize it. ✕ closes it; — shrinks it to a small "TELEPROMPTER" bar, and the
 * same button opens it again.
 */
@Composable
fun TeleprompterOverlay(recording: Boolean, isFront: Boolean) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { Prompter.load(ctx) }
    // Selfie camera: opens by itself (if wanted).
    LaunchedEffect(isFront) { if (isFront && Prompter.autoSelfie && Prompter.text.isNotBlank()) { Prompter.open = true } }
    LaunchedEffect(recording) { if (recording && Prompter.withRecording && Prompter.open) Prompter.playing = true }
    if (!Prompter.open) return
    var editing by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val d = LocalDensity.current
        val sw = with(d) { maxWidth.toPx() }; val sh = with(d) { maxHeight.toPx() }
        val box = Modifier.offset { IntOffset((Prompter.x * sw).roundToInt(), (Prompter.y * sh).roundToInt()) }
        fun drag(dx: Float, dy: Float) {
            Prompter.x = (Prompter.x + dx / sw).coerceIn(0f, 0.95f)
            Prompter.y = (Prompter.y + dy / sh).coerceIn(0f, 0.95f)
        }
        val header = Modifier.pointerInput(Unit) { detectDragGestures(onDragEnd = { Prompter.save(ctx) }) { ch, a -> ch.consume(); drag(a.x, a.y) } }
        if (Prompter.minimized) {
            // Small narrow bar: still visible, opens again with the same button.
            Row(
                box.then(header).clip(RoundedCornerShape(14.dp)).background(Color(0xE6141619)).border(1.dp, Pro.Amber.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                    .padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TELEPROMPTER", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp, letterSpacing = 1.sp))
                HeaderButton("▢") { Prompter.minimized = false }
                HeaderButton("✕") { Prompter.open = false; Prompter.playing = false }
            }
            return@BoxWithConstraints
        }
        Column(
            box.width(with(d) { (Prompter.w * sw).toDp() }).height(with(d) { (Prompter.h * sh).toDp() })
                .clip(RoundedCornerShape(12.dp)).background(Color(Prompter.bgColor).copy(alpha = Prompter.bgAlpha))
                .border(1.dp, Pro.Amber.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
        ) {
            // Top bar: drag to move.
            Row(header.fillMaxWidth().background(Color(0x99141619)).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("TELEPROMPTER", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp, letterSpacing = 1.sp), modifier = Modifier.weight(1f).padding(start = 4.dp))
                HeaderButton(if (Prompter.playing) "❚❚" else "▶") { Prompter.playing = !Prompter.playing }
                HeaderButton("↺") { Prompter.restart++; Prompter.playing = false }
                HeaderButton(if (Prompter.horizontal) "⇆" else "⇅") { Prompter.horizontal = !Prompter.horizontal; Prompter.save(ctx) }
                HeaderButton("✎") { editing = true; Prompter.playing = false }
                HeaderButton("—") { Prompter.minimized = true }
                HeaderButton("✕") { Prompter.open = false; Prompter.playing = false }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                PromptText(Modifier.fillMaxSize())
                // Bottom-right corner: drag to resize.
                Box(
                    Modifier.align(Alignment.BottomEnd).size(26.dp)
                        .pointerInput(Unit) {
                            detectDragGestures(onDragEnd = { Prompter.save(ctx) }) { ch, a ->
                                ch.consume()
                                Prompter.w = (Prompter.w + a.x / sw).coerceIn(0.25f, 1f)
                                Prompter.h = (Prompter.h + a.y / sh).coerceIn(0.1f, 0.9f)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) { Text("◢", color = Pro.Amber.copy(alpha = 0.7f), fontSize = 14.sp) }
            }
            // Auto-scroll on / off and its speed, right on the teleprompter.
            Row(Modifier.fillMaxWidth().background(Color(0x99141619)).padding(start = 6.dp, end = 30.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (Prompter.playing) "❚❚ AUTO-SCROLL" else "▶ AUTO-SCROLL",
                    style = Pro.Label.copy(color = if (Prompter.playing) Color.Black else Pro.Amber, fontSize = 10.sp),
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (Prompter.playing) Pro.Amber else Color.Transparent)
                        .clickable { if (Prompter.speed < 1f) Prompter.speed = 30f; Prompter.playing = !Prompter.playing }.padding(horizontal = 8.dp, vertical = 6.dp),
                )
                Text("SPEED", style = Pro.Label.copy(color = Pro.Dim, fontSize = 9.sp), modifier = Modifier.padding(start = 6.dp))
                Slider(
                    Prompter.speed.coerceIn(5f, 120f), { Prompter.speed = it }, valueRange = 5f..120f,
                    onValueChangeFinished = { Prompter.save(ctx) },
                    colors = SliderDefaults.colors(thumbColor = Pro.Amber, activeTrackColor = Pro.Amber),
                    modifier = Modifier.weight(1f).height(28.dp).padding(horizontal = 4.dp),
                )
            }
        }
    }
    if (editing) PrompterEditor { editing = false; Prompter.save(ctx) }
}

@Composable
private fun HeaderButton(label: String, onClick: () -> Unit) {
    Box(Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PromptText(modifier: Modifier) {
    val scroll: ScrollState = rememberScrollState()
    LaunchedEffect(Prompter.restart, Prompter.horizontal) { scroll.scrollTo(0) }
    val d = LocalDensity.current
    // Scrolls smoothly at the chosen speed while playing.
    LaunchedEffect(Prompter.playing, Prompter.speed) {
        var last = System.nanoTime()
        while (Prompter.playing) {
            delay(16)
            val now = System.nanoTime()
            val px = with(d) { (Prompter.speed * (now - last) / 1e9f).dp.toPx() }
            last = now
            scroll.scrollBy(px)
            if (scroll.value >= scroll.maxValue) Prompter.playing = false
        }
    }
    if (Prompter.horizontal) {
        // Sideways: one line running right to left, like a news ticker.
        Box(modifier.graphicsLayer { if (Prompter.mirror) scaleX = -1f }, contentAlignment = Alignment.CenterStart) {
            Row(Modifier.horizontalScroll(scroll).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(40.dp))
                Text(
                    Prompter.text.ifBlank { "Tap ✎ to type what you want to say." }.replace(Regex("\\s*\\n\\s*"), "   ·   "),
                    style = promptStyle().copy(textAlign = TextAlign.Start), maxLines = 1, softWrap = false,
                )
                // Room to run the last word out of view.
                Spacer(Modifier.width(400.dp))
            }
        }
        return
    }
    Column(modifier.verticalScroll(scroll).graphicsLayer { if (Prompter.mirror) scaleX = -1f }.padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(
            Prompter.text.ifBlank { "Tap ✎ to type what you want to say." },
            style = promptStyle(),
            modifier = Modifier.fillMaxWidth(),
        )
        // Room to scroll the last line up to the top.
        Spacer(Modifier.height(200.dp))
    }
}

private fun promptStyle() = TextStyle(
    color = Color(Prompter.textColor),
    fontSize = Prompter.sizeSp.sp,
    lineHeight = (Prompter.sizeSp * 1.3f).sp,
    fontWeight = if (Prompter.bold) FontWeight.Bold else FontWeight.Normal,
    fontStyle = if (Prompter.italic) FontStyle.Italic else FontStyle.Normal,
    textDecoration = if (Prompter.underline) TextDecoration.Underline else null,
    fontFamily = if (Prompter.narrow) condensed else FontFamily.Default,
    textAlign = when (Prompter.align) { 0 -> TextAlign.Start; 2 -> TextAlign.End; else -> TextAlign.Center },
)

/** Type the text and choose its look. */
@Composable
private fun PrompterEditor(onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth(0.94f).heightIn(max = 640.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF15171C))
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("TELEPROMPTER", style = Pro.Label.copy(color = Pro.Amber, fontSize = 13.sp, letterSpacing = 2.sp))
            OutlinedTextField(
                Prompter.text, { Prompter.text = it }, Modifier.fillMaxWidth().heightIn(min = 140.dp),
                placeholder = { Text("Type what you want to say…") },
                textStyle = TextStyle(color = com.dronedirector.veyrocutpro.ui.Pro.Fg, fontSize = 16.sp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Pro.Amber, cursorColor = Pro.Amber),
            )
            // Preview of the look.
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(Prompter.bgColor).copy(alpha = Prompter.bgAlpha.coerceAtLeast(0.15f))).padding(10.dp)) {
                Text(Prompter.text.lineSequence().firstOrNull()?.ifBlank { null } ?: "Your text looks like this", style = promptStyle(), maxLines = 2, modifier = Modifier.fillMaxWidth())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StyleChip("B", Prompter.bold, bold = true) { Prompter.bold = !Prompter.bold }
                StyleChip("I", Prompter.italic, italic = true) { Prompter.italic = !Prompter.italic }
                StyleChip("U", Prompter.underline, underline = true) { Prompter.underline = !Prompter.underline }
                StyleChip("NARROW", Prompter.narrow) { Prompter.narrow = !Prompter.narrow }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StyleChip("LEFT", Prompter.align == 0) { Prompter.align = 0 }
                StyleChip("CENTRE", Prompter.align == 1) { Prompter.align = 1 }
                StyleChip("RIGHT", Prompter.align == 2) { Prompter.align = 2 }
            }
            LabeledSlider("Text size", Prompter.sizeSp, 14f..72f, "${Prompter.sizeSp.roundToInt()}") { Prompter.sizeSp = it }
            Text("Scrolling", color = Color.White, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StyleChip("UP (VERTICAL)", !Prompter.horizontal) { Prompter.horizontal = false }
                StyleChip("SIDEWAYS (HORIZONTAL)", Prompter.horizontal) { Prompter.horizontal = true }
            }
            LabeledSlider("Scroll speed", Prompter.speed, 0f..120f, if (Prompter.speed < 1f) "OFF" else "${Prompter.speed.roundToInt()}") { Prompter.speed = it }
            Text("Text colour", color = Color.White, fontSize = 13.sp)
            Swatches(Prompter.textColor) { Prompter.textColor = it }
            Text("Background colour", color = Color.White, fontSize = 13.sp)
            Swatches(Prompter.bgColor) { Prompter.bgColor = it }
            LabeledSlider("Background opacity", Prompter.bgAlpha, 0f..1f, "${(Prompter.bgAlpha * 100).roundToInt()} %") { Prompter.bgAlpha = it }
            SwitchLine("Start scrolling when recording starts", Prompter.withRecording) { Prompter.withRecording = it }
            SwitchLine("Open with the selfie camera", Prompter.autoSelfie) { Prompter.autoSelfie = it }
            SwitchLine("Mirror text (for teleprompter glass)", Prompter.mirror) { Prompter.mirror = it }
            Text(
                "DONE", style = Pro.Label.copy(color = Color.Black, fontSize = 12.sp),
                modifier = Modifier.align(Alignment.End).clip(RoundedCornerShape(8.dp)).background(Pro.Amber).clickable(onClick = onClose).padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun StyleChip(label: String, on: Boolean, bold: Boolean = false, italic: Boolean = false, underline: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        style = TextStyle(
            color = if (on) Color.Black else Color.White, fontSize = 13.sp,
            fontWeight = if (bold) FontWeight.Black else FontWeight.SemiBold,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            textDecoration = if (underline) TextDecoration.Underline else null,
        ),
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (on) Pro.Amber else Color.White.copy(alpha = 0.1f)).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun Swatches(selected: Int, onPick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (c in Prompter.COLORS) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(Color(c))
                    .border(if (c == selected) 3.dp else 1.dp, if (c == selected) Pro.Amber else Color.White.copy(alpha = 0.3f), CircleShape)
                    .clickable { onPick(c) },
            )
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, shown: String, onChange: (Float) -> Unit) {
    Column {
        Row { Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f)); Text(shown, color = Pro.Amber, fontSize = 13.sp) }
        Slider(value, onChange, valueRange = range, colors = SliderDefaults.colors(thumbColor = Pro.Amber, activeTrackColor = Pro.Amber))
    }
}

@Composable
private fun SwitchLine(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Pro.Amber))
    }
}
