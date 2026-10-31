package com.dronedirector.veyrocutpro.editor

import androidx.annotation.OptIn
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import com.dronedirector.veyrocutpro.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.dronedirector.veyrocutpro.ui.Pro
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Multicam: pick 2–4 angles filmed at the same time, sync them by their sound, then play them
 * side by side and tap an angle to "cut" to it, like a live vision mixer. DONE adds the edit to
 * the end of the timeline as normal clips (trim, grade and transition them as usual).
 */
@OptIn(UnstableApi::class)
@Composable
fun MulticamDialog(pool: List<Clip>, onDone: (List<Clip>) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val picked = remember { mutableStateListOf<Clip>() }
    var offsets by remember { mutableStateOf<LongArray?>(null) }   // angle time = master time + offset
    var syncing by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Ed.Bg)) {
            val off = offsets
            if (off == null) {
                // 1. Choose the angles.
                Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
                    Text("Multicam", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Choose 2–4 clips filmed at the same time, from different angles. The first one you pick is the main angle: its sound sets the timing.", color = Pro.Dim, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                    pool.forEach { c ->
                        val i = picked.indexOfFirst { it.uri == c.uri }
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp))
                                .background(if (i >= 0) Pro.Amber.copy(alpha = 0.15f) else Ed.Raised)
                                .clickable { if (i >= 0) picked.removeAt(i) else if (picked.size < 4) picked.add(c) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(28.dp).clip(RoundedCornerShape(50)).background(if (i >= 0) Pro.Amber else com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.08f)),
                                contentAlignment = Alignment.Center,
                            ) { Text(if (i >= 0) "${i + 1}" else "", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                Text(c.name, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(tc(c.durationMs) + if (i == 0) " · main angle (sound & timing)" else "", style = Pro.Label)
                            }
                        }
                    }
                    note?.let { Text(it, color = Pro.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
                    Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("CANCEL", color = Pro.Dim, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onDismiss).padding(horizontal = 16.dp, vertical = 10.dp))
                        val ready = picked.size >= 2 && !syncing
                        Text(
                            if (syncing) "SYNCING…" else "SYNC BY SOUND & START",
                            color = Color.Black, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(if (ready) Pro.Amber else Pro.Amber.copy(alpha = 0.35f))
                                .clickable(enabled = ready) { syncing = true; note = null }.padding(horizontal = 18.dp, vertical = 10.dp),
                        )
                        if (syncing) CircularProgressIndicator(color = Pro.Amber, modifier = Modifier.size(22.dp))
                    }
                }
                if (syncing) LaunchedEffect(Unit) {
                    val waves = withContext(Dispatchers.IO) { picked.map { Waveforms.load(context, it.uri) } }
                    val main = waves[0]
                    if (main == null) { note = "Couldn't read the sound of the main angle"; syncing = false; return@LaunchedEffect }
                    val res = LongArray(picked.size)
                    var bad: String? = null
                    for (k in 1 until picked.size) {
                        val w = waves[k]
                        val m = if (w == null) null else withContext(Dispatchers.Default) { AudioSync.match(main, w) }
                        if (m == null || m.confidence < 0.15f) bad = picked[k].name else res[k] = m.lagMs
                    }
                    syncing = false
                    if (bad != null) note = "\"$bad\" doesn't share enough sound with the main angle to sync"
                    else offsets = res
                }
            } else {
                MulticamSwitcher(picked.toList(), off, onDone = onDone, onBack = { offsets = null })
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun MulticamSwitcher(angles: List<Clip>, off: LongArray, onDone: (List<Clip>) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val proxy = remember { ProxySettings.load(context) }
    val players = remember {
        angles.mapIndexed { i, c ->
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri((if (proxy.enabled) ProxyMaker.existing(context, c, proxy.quality, proxy.hevc) else null) ?: c.uri))
                volume = if (i == 0) 1f else 0f
                prepare()
            }
        }
    }
    DisposableEffect(Unit) { onDispose { players.forEach { it.release() } } }
    // Master time = the main angle's own time.
    val masterLen = angles[0].durationMs
    var t by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var live by remember { mutableStateOf(0) }
    // Cuts: (master time, angle). Recording starts at the first PLAY.
    val cuts = remember { mutableStateListOf<Pair<Long, Int>>() }
    var startT by remember { mutableLongStateOf(-1L) }
    var endT by remember { mutableLongStateOf(-1L) }
    // Auto switching (who's talking): the cuts are made up front, playback follows them.
    var auto by remember { mutableStateOf(false) }
    var autoBusy by remember { mutableStateOf(false) }
    var wide by remember { mutableStateOf(-1) }
    var autoRun by remember { mutableStateOf(0) }
    fun angleAt(mt: Long): Int = cuts.lastOrNull { it.first <= mt }?.second ?: live
    if (autoRun > 0) LaunchedEffect(autoRun) {
        autoBusy = true
        val waves = withContext(Dispatchers.IO) { angles.map { Waveforms.load(context, it.uri) } }
        if (waves.any { it == null }) { autoBusy = false; return@LaunchedEffect }
        val res = withContext(Dispatchers.Default) { AutoSwitch.cuts(waves.map { it!! }, off, masterLen, AutoSwitch.Options(wide = wide)) }
        cuts.clear(); cuts.addAll(res)
        startT = 0L; endT = masterLen; auto = true
        live = angleAt(t)
        autoBusy = false
    }

    fun seekAll(mt: Long) {
        players.forEachIndexed { i, p -> p.seekTo((mt + off[i]).coerceIn(0L, angles[i].durationMs)) }
    }
    LaunchedEffect(Unit) { seekAll(0L) }
    LaunchedEffect(playing) {
        players.forEach { if (playing) it.play() else it.pause() }
        while (playing) {
            t = players[0].currentPosition
            if (endT < t) endT = t
            if (auto) live = angleAt(t)
            // Keep the other angles in step.
            players.forEachIndexed { i, p ->
                if (i > 0) {
                    val want = t + off[i]
                    val inRange = want in 0..angles[i].durationMs
                    if (inRange && kotlin.math.abs(p.currentPosition - want) > 90) p.seekTo(want)
                    if (inRange && !p.isPlaying) p.play()
                }
            }
            if (t >= masterLen - 40) playing = false
            delay(60)
        }
    }

    Column(Modifier.fillMaxSize().padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹ ANGLES", color = Pro.Dim, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(50)).clickable { playing = false; onBack() }.padding(10.dp))
            Text(tc(t), style = Pro.Value.copy(color = Color.White), modifier = Modifier.weight(1f).padding(start = 8.dp))
            if (startT >= 0) Text("● CUTTING", color = Pro.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
        }
        // Angle grid: tap one to cut to it.
        val cols = 2
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            angles.indices.chunked(cols).forEach { row ->
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { i ->
                        val on = live == i
                        Box(
                            Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(10.dp)).background(Color.Black)
                                .border(if (on) 3.dp else 1.dp, if (on) Pro.Red else com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                .clickable {
                                    if (live != i) {
                                        live = i
                                        if (startT >= 0) {
                                            // In time order (auto cuts may lie ahead); a tap overrides until the next cut.
                                            cuts.removeAll { kotlin.math.abs(it.first - t) < 100 }
                                            val at = cuts.indexOfFirst { it.first > t }.let { if (it < 0) cuts.size else it }
                                            cuts.add(at, t to i)
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            AndroidView(
                                factory = { ctx -> android.view.TextureView(ctx).also { players[i].setVideoTextureView(it) } },
                                modifier = Modifier.fillMaxWidth().aspectRatio(angles[i].width.toFloat() / angles[i].height.coerceAtLeast(1)),
                            )
                            Text(
                                "${i + 1}" + if (on) " · LIVE" else "", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(6.dp))
                                    .background(if (on) Pro.Red else Color.Black.copy(alpha = 0.55f)).padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    if (row.size < cols) Box(Modifier.weight(1f))
                }
            }
        }
        Text(
            if (auto) "Auto cut: ${cuts.size} shots, following who's talking. PLAY to watch it; tap an angle while it plays to change a shot. DONE adds the edit to your timeline."
            else if (startT < 0) "Tap an angle to start on it, then PLAY. While it plays, tap an angle to cut to it. Or let AUTO cut to whoever is talking."
            else "Cuts so far: ${cuts.size}. Pause and play again to continue; DONE adds the edit to your timeline.",
            color = Pro.Dim, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
            Text("Wide shot", color = Color.White, fontSize = 12.sp)
            PillChip("None", wide < 0) { wide = -1 }
            angles.indices.forEach { i -> PillChip("${i + 1}", wide == i) { wide = i } }
        }
        Row(Modifier.padding(bottom = 6.dp)) {
            Text(
                if (autoBusy) "LISTENING…" else "AUTO: WHO'S TALKING",
                color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (autoBusy) Pro.Amber.copy(alpha = 0.35f) else Pro.Amber)
                    .clickable(enabled = !autoBusy) { playing = false; autoRun++ }.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (playing) "PAUSE" else "PLAY", color = Color.Black, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Pro.Amber).clickable {
                    if (!playing && startT < 0) { startT = t; endT = t; cuts.clear(); cuts.add(t to live) }
                    playing = !playing
                }.padding(horizontal = 22.dp, vertical = 12.dp),
            )
            Text("RESTART", color = Pro.Dim, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(50)).clickable {
                playing = false; t = 0L; seekAll(0L); startT = -1L; endT = -1L; cuts.clear(); auto = false
            }.padding(horizontal = 14.dp, vertical = 12.dp))
            Box(Modifier.weight(1f))
            val canDone = startT >= 0 && endT > startT + 500
            Text(
                "DONE", color = Color.Black, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (canDone) Pro.Amber else Pro.Amber.copy(alpha = 0.35f))
                    .clickable(enabled = canDone) {
                        playing = false
                        onDone(buildCut(angles, off, cuts.toList(), endT))
                    }.padding(horizontal = 22.dp, vertical = 12.dp),
            )
        }
    }
}

/** Turns the cut list into timeline clips (each a trimmed copy of its angle). */
private fun buildCut(angles: List<Clip>, off: LongArray, cuts: List<Pair<Long, Int>>, endT: Long): List<Clip> {
    val out = ArrayList<Clip>()
    for ((n, cut) in cuts.withIndex()) {
        val (from, k) = cut
        val to = if (n + 1 < cuts.size) cuts[n + 1].first else endT
        if (to - from < 120) continue
        val a = angles[k]
        val s = (from + off[k]).coerceIn(0L, a.durationMs)
        val e = (to + off[k]).coerceIn(0L, a.durationMs)
        if (e - s < 120) continue
        val last = out.lastOrNull()
        if (last != null && last.uri == a.uri && kotlin.math.abs(last.endMs - s) < 60) {
            out[out.size - 1] = last.copy(endMs = e)
        } else {
            out += a.copy(id = UUID.randomUUID().toString(), startMs = s, endMs = e, speed = 1f, ramp = null, rampName = null, transition = Transition())
        }
    }
    return out
}
