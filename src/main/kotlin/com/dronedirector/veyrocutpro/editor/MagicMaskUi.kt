package com.dronedirector.veyrocutpro.editor

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dronedirector.veyrocutpro.ui.Pro
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What a Magic Mask is being picked for: a clip on the timeline, or a video layer. */
data class MagicTarget(val clipId: String? = null, val layerId: String? = null, val uri: Uri, val srcMs: Long, val initial: MagicSel?, val remove: Boolean = false)

/**
 * Magic Mask picker: tap the object (and, if needed, tap what isn't part of it, or drag a box round
 * it); the AI outline updates as you go. TRACK follows it through the whole clip.
 */
@Composable
fun MagicMaskDialog(target: MagicTarget, onDismiss: () -> Unit, onTrack: (MagicSel) -> Unit) {
    val context = LocalContext.current
    var frame by remember { mutableStateOf<Bitmap?>(null) }
    var emb by remember { mutableStateOf<Sam.Embedding?>(null) }
    var status by remember { mutableStateOf("Preparing the AI…") }
    val keep = target.initial?.takeIf { it.srcMs == target.srcMs }
    var points by remember { mutableStateOf(keep?.points ?: emptyList()) }
    var choice by remember { mutableStateOf(keep?.choice ?: 0) }
    var tool by remember { mutableStateOf(0) }     // 0 add, 1 remove, 2 box
    var overlay by remember { mutableStateOf<Bitmap?>(null) }
    var boxStart by remember { mutableStateOf<Offset?>(null) }
    var boxEnd by remember { mutableStateOf<Offset?>(null) }

    LaunchedEffect(target.uri, target.srcMs) {
        val f = withContext(Dispatchers.IO) { try { FrameReader.frameAt(context, target.uri, target.srcMs, 1024) } catch (_: Throwable) { null } }
        if (f == null) { status = "Couldn't read this frame"; return@LaunchedEffect }
        frame = f
        status = "Preparing the AI…"
        val e = withContext(Dispatchers.Default) { try { Sam.encode(context, f) } catch (_: Throwable) { null } }
        if (e == null) { status = "The AI couldn't start on this phone" + (Sam.failed?.let { " ($it)" } ?: ""); return@LaunchedEffect }
        emb = e
        status = "Tap the object you want. Tap “Not it” and tap again to leave something out, or drag a box round it."
    }
    // Live outline.
    LaunchedEffect(emb, points, choice) {
        val e = emb ?: return@LaunchedEffect
        val f = frame ?: return@LaunchedEffect
        if (points.isEmpty()) { overlay = null; return@LaunchedEffect }
        val ow = 384; val oh = (384f * f.height / f.width).toInt().coerceAtLeast(16)
        val m = withContext(Dispatchers.Default) { MagicTracker.preview(e, MagicSel(srcMs = target.srcMs, points = points, choice = choice), ow, oh) }
        overlay = m?.let { bytes ->
            val px = IntArray(bytes.size) { val a = bytes[it].toInt() and 255; if (a > 127) ((150 shl 24) or 0xFFB300) else 0 }
            Bitmap.createBitmap(px, ow, oh, Bitmap.Config.ARGB_8888)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().padding(10.dp).clip(RoundedCornerShape(14.dp)).background(Pro.PanelSolid).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("MAGIC MASK", style = Pro.Label.copy(color = Pro.Amber, fontSize = 12.sp), modifier = Modifier.weight(1f))
                Text("✕", color = Color.White, fontSize = 18.sp, modifier = Modifier.clickable { onDismiss() }.padding(6.dp))
            }
            val f = frame
            val aspect = if (f != null) f.width.toFloat() / f.height else 16f / 9f
            com.dronedirector.veyrocutpro.ui.ForceDark { Box(Modifier.fillMaxWidth().aspectRatio(aspect.coerceIn(0.4f, 3f)).padding(top = 6.dp).background(Color.Black)) {
                if (f != null) {
                    val img = remember(f) { f.asImageBitmap() }
                    Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                    overlay?.let { o -> val oi = remember(o) { o.asImageBitmap() }; Image(oi, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
                }
                Canvas(
                    Modifier.fillMaxSize()
                        .pointerInput(tool, emb) {
                            if (emb == null) return@pointerInput
                            if (tool == 2) detectDragGestures(
                                onDragStart = { boxStart = it; boxEnd = it },
                                onDrag = { ch, _ -> ch.consume(); boxEnd = ch.position },
                                onDragEnd = {
                                    val a = boxStart; val b = boxEnd
                                    if (a != null && b != null) {
                                        val x0 = minOf(a.x, b.x) / size.width; val x1 = maxOf(a.x, b.x) / size.width
                                        val y0 = minOf(a.y, b.y) / size.height; val y1 = maxOf(a.y, b.y) / size.height
                                        if (x1 - x0 > 0.01f && y1 - y0 > 0.01f)
                                            points = points.filter { it.label < 2 } + MaskPoint(x0, y0, 2) + MaskPoint(x1, y1, 3)
                                    }
                                    boxStart = null; boxEnd = null
                                },
                            ) else detectTapGestures { o ->
                                points = points + MaskPoint((o.x / size.width).coerceIn(0f, 1f), (o.y / size.height).coerceIn(0f, 1f), if (tool == 1) 0 else 1)
                            }
                        },
                ) {
                    for (p in points) {
                        val c = Offset(p.x * size.width, p.y * size.height)
                        when (p.label) {
                            1 -> { drawCircle(Color.White, 9f, c); drawCircle(Color(0xFF34C759), 7f, c) }
                            0 -> { drawCircle(Color.White, 9f, c); drawCircle(Color(0xFFFF3B30), 7f, c) }
                        }
                    }
                    val b0 = points.firstOrNull { it.label == 2 }; val b1 = points.firstOrNull { it.label == 3 }
                    if (b0 != null && b1 != null) drawRect(Pro.Amber, Offset(b0.x * size.width, b0.y * size.height), Size((b1.x - b0.x) * size.width, (b1.y - b0.y) * size.height), style = Stroke(3f))
                    val a = boxStart; val b = boxEnd
                    if (a != null && b != null) drawRect(Color.White, Offset(minOf(a.x, b.x), minOf(a.y, b.y)), Size(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.y - a.y)), style = Stroke(2f))
                }
            } }
            Text(status, color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PillChip("+ The object", tool == 0) { tool = 0 }
                PillChip("− Not it", tool == 1) { tool = 1 }
                PillChip("▭ Box", tool == 2) { tool = 2 }
            }
            if (points.size == 1 && points[0].label == 1) Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Best guess", "Whole", "Part", "Detail").forEachIndexed { i, n -> PillChip(n, choice == i) { choice = i } }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PillChip("↶ Undo", false) { points = points.dropLast(if (points.lastOrNull()?.label == 3) 2 else 1) }
                PillChip("Clear", false) { points = emptyList() }
            }
            Text(
                "TRACK THROUGH THE CLIP", textAlign = TextAlign.Center,
                style = Pro.Label.copy(color = if (points.isNotEmpty() && emb != null) Color.Black else Pro.Dim, fontSize = 12.sp),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (points.isNotEmpty() && emb != null) Pro.Amber else Color.DarkGray)
                    .clickable(enabled = points.isNotEmpty() && emb != null) {
                        onTrack(MagicSel(id = java.util.UUID.randomUUID().toString(), srcMs = target.srcMs, points = points, choice = choice))
                    }.padding(vertical = 12.dp),
            )
            Text("The AI follows the object frame by frame (on the phone). It takes a little while — you can keep editing.", color = Pro.Dim, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
