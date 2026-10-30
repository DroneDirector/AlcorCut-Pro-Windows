package com.dronedirector.veyrocutpro.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dronedirector.veyrocutpro.camera.GradeIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Capture → STOP MOTION: every photo you take becomes the next frame; the last frame is shown faintly
 * over the viewfinder (onion skin) so you can move things a little each time. The frame strip lets
 * you play, delete or hold frames, then MAKE VIDEO turns them into a clip. Own code (video via Media3).
 */
object StopMotion {
    var active by mutableStateOf(false)
    var onion by mutableStateOf(0.4f)
    var fps by mutableIntStateOf(12)
    var editing by mutableStateOf(false)
    /** Frames in order; the same photo can appear more than once (held frames). */
    val frames = mutableStateListOf<Uri>()
    private var last: Uri? = null
    private val thumbs = android.util.LruCache<String, Bitmap>(64)

    fun add(u: Uri) { if (u != last) { last = u; frames.add(u) } }

    fun thumb(c: Context, u: Uri, px: Int): Bitmap? {
        val k = "$u/$px"
        thumbs.get(k)?.let { return it }
        return GradeIO.decode(c, u, px)?.also { thumbs.put(k, it) }
    }

    /** Builds the clip (each frame shown for 1 / fps s) and saves it to the gallery. */
}

/** The last frame, faintly over the viewfinder. */
@Composable
fun OnionSkin() {
    if (!StopMotion.active) return
    val ctx = LocalContext.current
    val u = StopMotion.frames.lastOrNull() ?: return
    val bmp by produceState<Bitmap?>(null, u) { value = withContext(Dispatchers.IO) { StopMotion.thumb(ctx, u, 1280) } }
    bmp?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, alpha = StopMotion.onion, modifier = Modifier.fillMaxSize()) }
}

/** Small bar over the viewfinder while stop motion is on. */
@Composable
fun StopMotionBar(modifier: Modifier = Modifier) {
    if (!StopMotion.active) return
    Row(
        modifier.clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("STOP MOTION · ${StopMotion.frames.size}", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp))
        Text("ONION ${(StopMotion.onion * 100).toInt()}%", style = Pro.Label.copy(color = Color.White, fontSize = 11.sp), modifier = Modifier.clickable {
            StopMotion.onion = listOf(0f, 0.25f, 0.4f, 0.6f).let { l -> l.firstOrNull { it > StopMotion.onion + 0.01f } ?: l.first() }
        })
        if (StopMotion.frames.isNotEmpty()) Text("UNDO", style = Pro.Label.copy(color = Color.White, fontSize = 11.sp), modifier = Modifier.clickable { StopMotion.frames.removeAt(StopMotion.frames.lastIndex) })
        Text("FRAMES", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.clickable { StopMotion.editing = true })
    }
}

/** The frame-by-frame timeline: play, delete, hold (duplicate) frames, frame rate, make the video. */
@Composable
fun StopMotionEditor(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var sel by remember { mutableIntStateOf((StopMotion.frames.size - 1).coerceAtLeast(0)) }
    var playing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(playing, StopMotion.fps) {
        while (playing && StopMotion.frames.isNotEmpty()) { delay(1000L / StopMotion.fps); sel = (sel + 1) % StopMotion.frames.size }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF111114)).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("STOP MOTION", style = Pro.Label.copy(color = Pro.Amber, fontSize = 13.sp), modifier = Modifier.weight(1f))
                Text("CLOSE", style = Pro.Label.copy(color = Color.White, fontSize = 12.sp), modifier = Modifier.clickable(onClick = onClose).padding(8.dp))
            }
            val cur = StopMotion.frames.getOrNull(sel)
            Box(Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                if (cur == null) Text("No frames yet — take photos with STOP MOTION on; each one is the next frame.", color = Pro.Dim, fontSize = 13.sp)
                else {
                    val b by produceState<Bitmap?>(null, cur) { value = withContext(Dispatchers.IO) { StopMotion.thumb(ctx, cur, 1280) } }
                    b?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
                }
            }
            Text("Frame ${if (cur == null) 0 else sel + 1} / ${StopMotion.frames.size} · ${"%.1f".format(StopMotion.frames.size / StopMotion.fps.toFloat())} s at ${StopMotion.fps} fps", color = Color.White, fontSize = 12.sp)
            LazyRow(Modifier.fillMaxWidth().height(72.dp).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(StopMotion.frames.toList()) { i, u ->
                    val t by produceState<Bitmap?>(null, u) { value = withContext(Dispatchers.IO) { StopMotion.thumb(ctx, u, 160) } }
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(6.dp)).border(2.dp, if (i == sel) Pro.Amber else Color.Transparent, RoundedCornerShape(6.dp)).background(Color.DarkGray).clickable { sel = i; playing = false }) {
                        t?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                        Text("${i + 1}", color = Color.White, fontSize = 9.sp, modifier = Modifier.align(Alignment.BottomStart).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 3.dp))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(6, 8, 12, 15, 24).forEach { f -> Text("$f", style = Pro.Label.copy(color = if (StopMotion.fps == f) Color.Black else Color.White, fontSize = 12.sp), modifier = Modifier.clip(RoundedCornerShape(50)).background(if (StopMotion.fps == f) Pro.Amber else Color.White.copy(alpha = 0.1f)).clickable { StopMotion.fps = f }.padding(horizontal = 10.dp, vertical = 6.dp)) }
                Text("fps", color = Pro.Dim, fontSize = 11.sp)
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                @Composable fun btn(t: String, col: Color = Pro.Amber, on: () -> Unit) = Text(t, style = Pro.Label.copy(color = col, fontSize = 11.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.08f)).clickable(enabled = busy == null, onClick = on).padding(10.dp))
                btn(if (playing) "STOP" else "PLAY") { playing = !playing }
                if (cur != null) {
                    btn("HOLD +1") { StopMotion.frames.add(sel + 1, cur); sel += 1 }
                    btn("DELETE", Pro.Red) { StopMotion.frames.removeAt(sel); sel = sel.coerceAtMost((StopMotion.frames.size - 1).coerceAtLeast(0)) }
                }
            }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                @Composable fun btn(t: String, col: Color = Pro.Amber, on: () -> Unit) = Text(t, style = Pro.Label.copy(color = col, fontSize = 11.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.08f)).clickable(enabled = busy == null, onClick = on).padding(10.dp))
                btn(busy ?: "MAKE VIDEO") {
                    if (StopMotion.frames.size < 2) return@btn
                    playing = false; busy = "MAKING…"
                    scope.launch {
                        val u = try { StopMotion.makeVideo(ctx, StopMotion.frames.toList(), StopMotion.fps) } catch (_: Throwable) { null }
                        busy = null
                        android.widget.Toast.makeText(ctx, I18n.tr(if (u != null) "Stop-motion video saved" else "Couldn't make the video"), android.widget.Toast.LENGTH_LONG).show()
                    }
                }
                btn("CLEAR ALL", Pro.Red) { StopMotion.frames.clear(); sel = 0 }
            }
            Text("Each photo taken with STOP MOTION on is the next frame. HOLD +1 shows a frame twice as long. The video goes to your gallery; open it in the Video editor to add music.", color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
