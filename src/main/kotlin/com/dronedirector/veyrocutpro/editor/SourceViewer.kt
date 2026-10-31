package com.dronedirector.veyrocutpro.editor

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.dronedirector.veyrocutpro.ui.Pro
import kotlinx.coroutines.delay

/**
 * Source viewer for three-point editing: play a file, mark IN and OUT, then INSERT it at the
 * timeline's playhead (pushing the rest along), OVERWRITE from the playhead (same total length),
 * or APPEND it to the end. [source] is the whole file (startMs 0, endMs = its duration).
 */
@Composable
fun SourceViewerDialog(
    source: Clip,
    playheadText: String,
    onDismiss: () -> Unit,
    onEdit: (Clip, Int) -> Unit,
) {
    val context = LocalContext.current
    val dur = source.durationMs.coerceAtLeast(1)
    val p = remember(source.uri) {
        ExoPlayer.Builder(context).build().apply { setMediaItem(MediaItem.fromUri(source.uri)); prepare() }
    }
    DisposableEffect(p) { onDispose { p.release() } }
    var pos by remember { mutableStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var inMs by remember { mutableStateOf(0L) }
    var outMs by remember { mutableStateOf(dur) }
    LaunchedEffect(p) { while (true) { pos = p.currentPosition.coerceIn(0, dur); playing = p.isPlaying; delay(50) } }
    val frame = 1000L / 30
    fun seek(ms: Long) { val t = ms.coerceIn(0, dur); p.seekTo(t); pos = t }

    Dialog(onDismissRequest = { p.pause(); onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(14.dp)).background(Pro.PanelSolid).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("SOURCE · ${source.name}", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("✕", color = Color.White, fontSize = 18.sp, modifier = Modifier.clickable { p.pause(); onDismiss() }.padding(6.dp))
            }
            val aspect = if (source.height > 0) (source.width.toFloat() / source.height).coerceIn(0.4f, 3f) else 16f / 9f
            com.dronedirector.veyrocutpro.ui.ForceDark { Box(Modifier.fillMaxWidth().aspectRatio(aspect).padding(top = 6.dp).background(Color.Black)) {
                AndroidView(factory = { ctx -> android.view.TextureView(ctx).also { p.setVideoTextureView(it) } }, modifier = Modifier.fillMaxSize())
            } }
            // Scrub bar with the marked range.
            Box(
                Modifier.fillMaxWidth().height(34.dp).padding(top = 8.dp)
                    .pointerInput(dur) { detectTapGestures { o -> seek((o.x / size.width * dur).toLong()) } }
                    .pointerInput(dur) { detectDragGestures { ch, _ -> ch.consume(); seek((ch.position.x / size.width * dur).toLong()) } },
            ) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    val y = size.height / 2
                    drawRect(com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.18f), Offset(0f, y - 4f), Size(size.width, 8f))
                    val a = size.width * inMs / dur; val b = size.width * outMs / dur
                    drawRect(Pro.Amber.copy(alpha = 0.55f), Offset(a, y - 6f), Size((b - a).coerceAtLeast(2f), 12f))
                    val x = size.width * pos / dur
                    drawRect(Color.White, Offset(x - 1.5f, 0f), Size(3f, size.height))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(tc(pos), style = Pro.Value.copy(fontSize = 12.sp))
                Text("IN ${tc(inMs)} · OUT ${tc(outMs)} · ${tc((outMs - inMs).coerceAtLeast(0))}", style = Pro.Label.copy(fontSize = 10.sp))
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PillChip("◀ 1f", false) { p.pause(); seek(pos - frame) }
                PillChip(if (playing) "‖ Pause" else "▶ Play", playing) { if (p.isPlaying) p.pause() else { if (pos >= dur - 50) seek(0); p.play() } }
                PillChip("1f ▶", false) { p.pause(); seek(pos + frame) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PillChip("[ MARK IN", false) { inMs = pos.coerceAtMost(outMs - 100) }
                PillChip("MARK OUT ]", false) { outMs = pos.coerceAtLeast(inMs + 100) }
                PillChip("Clear", false) { inMs = 0; outMs = dur }
            }
            Text("Goes into the timeline at the playhead ($playheadText).", color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                fun go(mode: Int) {
                    p.pause()
                    if (outMs - inMs < 100) return
                    onEdit(source.copy(id = java.util.UUID.randomUUID().toString(), startMs = inMs, endMs = outMs), mode)
                }
                listOf("INSERT", "OVERWRITE", "APPEND").forEachIndexed { k, n ->
                    Text(
                        n, style = Pro.Label.copy(color = Color.Black, fontSize = 11.sp),
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (k == 0) Pro.Amber else Pro.Amber.copy(alpha = 0.75f)).clickable { go(k) }.padding(vertical = 10.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            Text("INSERT pushes the rest of the edit along · OVERWRITE replaces what's there (same length) · APPEND adds it at the end.", color = Pro.Dim, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
