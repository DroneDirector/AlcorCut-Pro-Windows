package com.dronedirector.veyrocutpro.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.ui.Pro
import com.dronedirector.veyrocutpro.ui.Text
import com.dronedirector.veyrocutpro.ui.hintedScroll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** FONT: the categories, then every font of the chosen one written in itself. */
@Composable
fun FontPicker(selected: String?, builtIn: LayerFont, onPick: (String?, LayerFont?) -> Unit) {
    var cat by rememberSaveable { mutableStateOf(TextFonts.byId(selected)?.cat ?: -1) }
    Text("Font", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
    Row(Modifier.hintedScroll(horizontal = true).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PillChip("Basic", cat == -1) { cat = -1 }
        TextFonts.CATEGORIES.forEachIndexed { i, n -> PillChip(n, cat == i) { cat = i } }
    }
    @Composable
    fun cell(label: String, tf: android.graphics.Typeface?, on: Boolean, mod: Modifier, click: () -> Unit) {
        Box(mod.height(40.dp).clip(RoundedCornerShape(8.dp)).background(if (on) Pro.Amber.copy(alpha = 0.18f) else Ed.Raised)
            .border(1.dp, if (on) Pro.Amber else Color.Transparent, RoundedCornerShape(8.dp)).clickable(onClick = click).padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart) {
            Text(label, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontFamily = tf?.composeFamily())
        }
    }
    val items: List<Triple<String, android.graphics.Typeface?, () -> Unit>> =
        if (cat == -1) LayerFont.entries.map { f -> Triple(f.label, f.typeface as android.graphics.Typeface?) { onPick(null, f) } }
        else TextFonts.ALL.filter { it.cat == cat }.map { f -> Triple(f.label, TextFonts.typeface(f.id)) { onPick(f.id, null) } }
    val onIdx = if (cat == -1) (if (selected == null) builtIn.ordinal else -1) else TextFonts.ALL.filter { it.cat == cat }.indexOfFirst { it.id == selected }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.withIndex().chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { (i, t) -> cell(t.first, t.second, i == onIdx, Modifier.weight(1f), t.third) }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

/** A small picture of [l] (an effect or a lower third), made off the main thread. */
@Composable
private fun StylePreview(key: Any, l: Layer, label: String, on: Boolean, w: Int = 120, onClick: () -> Unit) {
    val bmp by produceState<Bitmap?>(null, key) { value = withContext(Dispatchers.Default) { try { LayerRenderer.textPreview(l, 44f, 900) } catch (_: Throwable) { null } } }
    Column(Modifier.width(w.dp).clip(RoundedCornerShape(10.dp)).background(Ed.Raised).border(1.dp, if (on) Pro.Amber else Color.Transparent, RoundedCornerShape(10.dp))
        .clickable(onClick = onClick).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF3A4A5C)), contentAlignment = Alignment.Center) {
            bmp?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.padding(3.dp)) }
        }
        Text(label, color = if (on) Pro.Amber else Color.White, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
    }
}

/** EFFECT for the selected text. */
@Composable
fun TextFxPicker(l: Layer, onPick: (Int?) -> Unit) {
    Text("Effect", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
    Row(Modifier.hintedScroll(horizontal = true).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TextFx.NAMES.forEachIndexed { i, n ->
            val sample = l.copy(text = "Aa", lowerThird = null, textFx = i.takeIf { it > 0 }, box = false, stroke = null, keys = null)
            StylePreview(Triple(i, l.face, l.color), sample, n, (l.textFx ?: 0) == i, 90) { onPick(i.takeIf { it > 0 }) }
        }
    }
}

/** The accent / second colour (gradients, highlighter, lower-third accent). */
@Composable
fun SecondColour(l: Layer, label: String, onPick: (Int) -> Unit) {
    val cols = listOf(0xFFFFB300, 0xFFFF3B30, 0xFFFF4FA3, 0xFFBF5AF2, 0xFF0A84FF, 0xFF00E5FF, 0xFF34C759, 0xFFFFFFFF, 0xFF111111).map { it.toInt() }
    Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        cols.forEach { c ->
            Box(Modifier.size(26.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(c))
                .border(2.dp, if ((l.textColor2 ?: 0xFFFFB300.toInt()) == c) Pro.Amber else Pro.Line, androidx.compose.foundation.shape.CircleShape).clickable { onPick(c) })
        }
    }
}

/** LOWER THIRDS: tap one to add it (name on the first line, title on the second). */
@Composable
fun LowerThirdGallery(selected: Int? = null, onPick: (Int) -> Unit) {
    LowerThirds.NAMES.withIndex().chunked(8).forEach { row ->
        Row(Modifier.hintedScroll(horizontal = true).padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEach { (i, n) ->
                val sample = Layer(kind = LayerKind.TEXT, text = "Your Name\nTitle or role", lowerThird = i, color = 0xFFFFFFFF.toInt(), font = LayerFont.SANS)
                StylePreview(i, sample, n, selected == i, 130) { onPick(i) }
            }
        }
    }
}

/** MORE TEXT: ready-made kinds of text, shown in their own style. */
@Composable
fun TextTypeGallery(onPick: (TextTypes.T) -> Unit) {
    TextTypes.ALL.chunked(10).forEach { row ->
        Row(Modifier.hintedScroll(horizontal = true).padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEach { t ->
                val sample = Layer(kind = LayerKind.TEXT, text = t.text.take(14), face = t.face, textFx = t.fx, color = t.color, textColor2 = t.color2, box = t.box, shadow = t.fx == null)
                StylePreview(t.name, sample, t.name, false, 120) { onPick(t) }
            }
        }
    }
}

/** A text layer made from text type [t]. */
fun TextTypes.T.layer(startMs: Long, endMs: Long): Layer = Layer(
    kind = LayerKind.TEXT, text = text, size = size, x = x, y = y, face = face, textFx = fx, color = color, textColor2 = color2, box = box,
    shadow = fx == null, animIn = anim, animOut = LayerAnim.FADE, animMs = if (anim == LayerAnim.TYPEWRITER) 1200L else 500L, startMs = startMs, endMs = endMs, name = name,
)

/** A lower third in design [i]. */
fun lowerThirdLayer(i: Int, startMs: Long, endMs: Long, wholeVideo: Boolean = false): Layer = Layer(
    kind = LayerKind.TEXT, text = "Your Name\nTitle or role", lowerThird = i, size = 0.055f, x = 0.3f, y = 0.82f, color = 0xFFFFFFFF.toInt(),
    shadow = false, animIn = if (i == 15) LayerAnim.FADE else LayerAnim.SLIDE_RIGHT, animOut = LayerAnim.FADE, animMs = 500L,
    startMs = startMs, endMs = endMs, wholeVideo = wholeVideo, name = "Lower third · " + LowerThirds.NAMES[i],
)
