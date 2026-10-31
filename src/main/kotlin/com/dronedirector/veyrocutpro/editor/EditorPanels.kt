package com.dronedirector.veyrocutpro.editor

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.dronedirector.veyrocutpro.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.ui.Pro
import com.dronedirector.veyrocutpro.ui.hintedScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput

/** Transition browser for the cut after clip [index]. */
@Composable
fun TransitionsPanel(
    clips: List<Clip>, index: Int, thumbs: Map<String, Bitmap>,
    onChange: (Transition) -> Unit, onApplyAll: (Transition) -> Unit, onPreview: () -> Unit,
    onApplyAllAudio: (Transition) -> Unit = {},
) {
    val a = clips.getOrNull(index)
    val b = clips.getOrNull(index + 1)
    var tab by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    SectionTitle("TRANSITIONS")
    Chips(listOf("Video transitions", "Audio transitions"), tab) { tab = it }
    if (a == null || b == null) {
        Text("Select a clip that has another clip after it — the transition goes on the cut between them.", color = Pro.Dim, fontSize = 12.sp)
        return
    }
    val t = a.transition
    Text("Clip ${index + 1} → clip ${index + 2}", color = Color.White, fontSize = 14.sp)
    if (tab == 1) {
        AudioTransitionsPanel(t, onChange, onApplyAllAudio, onPreview)
        return
    }
    Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("PREVIEW" to onPreview, "APPLY TO ALL CUTS" to { onApplyAll(t) }).forEach { (label, f) ->
            Text(label, style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Ed.Raised).clickable(onClick = f).padding(8.dp))
        }
    }
    val maxMs = (minOf(a.lengthMs, b.lengthMs) * 0.45f).coerceAtLeast(200f)
    if (t.type != TransitionType.NONE) {
        EdSlider("Duration", t.durationMs / 1000f, 0.2f..3f, 0.8f, { "%.1f s".format(it) }) { onChange(t.copy(durationMs = (it * 1000).toLong())) }
        if (t.durationMs > maxMs) Text("Limited to %.1f s by the clip lengths.".format(maxMs / 1000f), color = Pro.Dim, fontSize = 11.sp)
    }
    val anim = rememberInfiniteTransition(label = "tr")
    val p = anim.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "p")
    // Read only while drawing (not on every frame's recomposition), so 100+ moving previews stay light.
    val pp = remember { androidx.compose.runtime.derivedStateOf { ((p.value - 0.15f) / 0.7f).coerceIn(0f, 1f) } }
    // Small copies of the two clips' pictures: the previews only need tile-sized pictures.
    val ta = thumbs[a.id]?.let { remember(it) { smallThumb(it) } }; val tb = thumbs[b.id]?.let { remember(it) { smallThumb(it) } }
    TransitionGroup.entries.forEach { g ->
        SectionTitle(g.label.uppercase())
        TransitionType.entries.filter { it.group == g }.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { type ->
                    TransitionTile(type, type == t.type, ta, tb, pp, Modifier.weight(1f)) { onChange(t.copy(type = type)) }
                }
                repeat(3 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun TransitionTile(type: TransitionType, selected: Boolean, a: Bitmap?, b: Bitmap?, p: androidx.compose.runtime.State<Float>, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))
                .border(if (selected) 2.dp else 1.dp, if (selected) Pro.Amber else Pro.Line, RoundedCornerShape(8.dp))
        ) {
            TileSide(a, Color(0xFF2B4C7E), false, type, p)
            TileSide(b, Color(0xFF7E4A2B), true, type, p)
        }
        Text(
            type.label, color = if (selected) Pro.Amber else Color.White, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
        )
    }
}

@Composable
private fun TileSide(bmp: Bitmap?, fallback: Color, incoming: Boolean, type: TransitionType, pState: androidx.compose.runtime.State<Float>) {
    // Built once per transition and kept, so opening the page again is instant.
    val shader = remember(type, incoming) { TransitionMath.thumbShader(type, incoming) }
    val mod = Modifier.fillMaxSize().graphicsLayer {
        val p0 = pState.value
        val p = if (type == TransitionType.NONE) (if (p0 < 0.5f) 0f else 1f) else p0
        val sh = shader
        val (tx, ty) = TransitionMath.move(type, p, incoming)
        translationX = tx * size.width; translationY = ty * size.height
        if (type == TransitionType.NONE) { alpha = if (incoming) p else 1f; return@graphicsLayer }
        if (sh != null && android.os.Build.VERSION.SDK_INT >= 33 && size.width > 0f) {
            sh.setFloatUniform("uSize", size.width, size.height)
            sh.setFloatUniform("uP", p)
            sh.setFloatUniform("uSide", if (incoming) 1f else 0f)
            sh.setFloatUniform("uMode", if (incoming && type.kind != TransitionKind.MOVE) 1f else 0f)
            renderEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(sh, "content").asComposeRenderEffect()
            clip = true
        } else if (incoming && type.kind != TransitionKind.MOVE) alpha = p
    }
    if (bmp != null) Image(bmp.asImageBitmap(), null, mod, contentScale = ContentScale.Crop)
    else Box(mod.background(Brush.linearGradient(listOf(fallback, fallback.copy(alpha = 0.5f)))), contentAlignment = Alignment.Center) {
        Text(if (incoming) "B" else "A", color = Color.White, fontSize = 16.sp)
    }
}

/** A clip picture scaled down to transition-tile size (at most 320 px wide). */
private fun smallThumb(b: Bitmap): Bitmap {
    if (b.width <= 320) return b
    val h = (b.height * 320f / b.width).toInt().coerceAtLeast(1)
    return try { Bitmap.createScaledBitmap(b, 320, h, true) } catch (_: Throwable) { b }
}

/** Crop / zoom / reposition and stabilisation controls for a clip. */
@Composable
fun ReframePanel(
    c: Clip, editingEnd: Boolean, cropMode: Boolean, stabProgress: Int?, analysed: Boolean,
    onEditEnd: (Boolean) -> Unit, onCropMode: (Boolean) -> Unit,
    onChange: ((Clip) -> Clip) -> Unit, onAnalyze: () -> Unit,
    /** Playhead inside this clip (source ms from its in point) and a way to jump there. */
    playheadRel: Long = 0L, onSeekRel: (Long) -> Unit = {},
) {
    val r = c.reframe
    val keyed = r.keyedAt(playheadRel)
    val f = keyed?.frame ?: if (r.animate && editingEnd) r.end else r.start
    fun setF(nf: Frame) = onChange { cl ->
        val k = cl.reframe.keyedAt(playheadRel)
        cl.copy(reframe = when {
            k != null -> cl.reframe.upsertKey(playheadRel, nf, k.opacity)
            cl.reframe.animate && editingEnd -> cl.reframe.copy(end = nf)
            else -> cl.reframe.copy(start = nf)
        })
    }
    SectionTitle("CROP & ZOOM", onReset = { onChange { it.copy(reframe = Reframe()) } })
    Text(
        if (cropMode) "DONE — STOP EDITING ON THE PLAYER" else "CROP ON THE PLAYER (PINCH · DRAG · TWIST)",
        style = Pro.Label.copy(color = if (cropMode) Color.Black else Pro.Amber, fontSize = 10.sp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (cropMode) Pro.Amber else Ed.Raised).clickable { onCropMode(!cropMode) }.padding(10.dp),
    )
    if (!r.hasKeys) Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Animate (Ken Burns)", color = Color.White, fontSize = 13.sp)
            Text("Moves smoothly from a start framing to an end framing over the clip", color = Pro.Dim, fontSize = 11.sp)
        }
        Switch(r.animate, { a -> onChange { it.copy(reframe = it.reframe.copy(animate = a, end = if (a && it.reframe.end == Frame()) it.reframe.start.copy(zoom = it.reframe.start.zoom * 1.3f) else it.reframe.end)) } },
            colors = SwitchDefaults.colors(checkedTrackColor = Pro.Amber, checkedThumbColor = Color.Black))
    }
    if (r.animate && !r.hasKeys) Chips(listOf("Edit start", "Edit end"), if (editingEnd) 1 else 0) { onEditEnd(it == 1) }
    if (r.hasKeys) Text("Keyframed: the controls below edit the clip at the playhead (a keyframe is added or updated there).", color = Pro.Amber, fontSize = 11.sp)
    EdSlider("Zoom", f.zoom, 0.25f..4f, 1f, { "%.2fx".format(it) }) { setF(f.copy(zoom = it)) }
    EdSlider("Position X", f.x, -1f..1f, 0f) { setF(f.copy(x = it)) }
    EdSlider("Position Y", f.y, -1f..1f, 0f) { setF(f.copy(y = it)) }
    EdSlider("Rotate", f.rotation, -180f..180f, 0f, { "%.1f°".format(it) }) { setF(f.copy(rotation = it)) }
    Chips(listOf("Fit 1×", "1.25×", "1.5×", "2× punch-in", "Flip ⇆"), -1) { i ->
        when (i) {
            0 -> setF(Frame()); 1 -> setF(f.copy(zoom = 1.25f)); 2 -> setF(f.copy(zoom = 1.5f)); 3 -> setF(f.copy(zoom = 2f))
            else -> onChange { it.copy(reframe = it.reframe.copy(flip = !it.reframe.flip)) }
        }
    }
    ClipKeyframes(c, playheadRel, onChange, onSeekRel)

    SectionTitle("STABILIZE")
    val s = c.stab
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Stabilize shaky footage", color = Color.White, fontSize = 13.sp)
            Text(
                when {
                    stabProgress != null -> "Analysing motion… $stabProgress%"
                    s.enabled && analysed -> "Analysed ✓ — smoothing applied in preview and export"
                    s.enabled -> "Needs analysis"
                    else -> "Measures camera shake and smooths it out (crops in slightly)"
                },
                color = if (s.enabled && analysed) Pro.Green else Pro.Dim, fontSize = 11.sp,
            )
        }
        Switch(s.enabled, { on -> onChange { it.copy(stab = it.stab.copy(enabled = on)) }; if (on && !analysed) onAnalyze() },
            colors = SwitchDefaults.colors(checkedTrackColor = Pro.Amber, checkedThumbColor = Color.Black))
    }
    if (s.enabled) {
        val gyroPick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { u ->
            if (u != null) onChange { it.copy(stab = it.stab.copy(gyro = true, gyroFile = u.toString())) }
        }
        Text("Source", color = Color.White, fontSize = 13.sp)
        Chips(listOf("Picture analysis", "Gyro (motion data)"), if (s.gyro == true) 1 else 0) { i -> onChange { it.copy(stab = it.stab.copy(gyro = i == 1)) } }
        if (s.gyro == true) {
            Text("Uses the phone's own motion data recorded with the clip (Capture → Settings → Video → Stabilization: Gyroflow saves a .gcsv file with the clip's name). Sharper and steadier than the picture alone — the picture is still measured once to line the data up.", color = Pro.Dim, fontSize = 11.sp)
            Text(if (s.gyroFile != null) "Using the file you picked." else "The file is found by the clip's name; or pick it yourself.", color = Pro.Dim, fontSize = 11.sp)
            Text("CHOOSE MOTION FILE (.GCSV)", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp),
                modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(6.dp)).background(Ed.Raised).clickable { gyroPick.launch(arrayOf("*/*")) }.padding(8.dp))
        }
        Chips(listOf("Smooth motion", "Locked (tripod)"), if (s.tripod) 1 else 0) { i -> onChange { it.copy(stab = it.stab.copy(tripod = i == 1)) } }
        if (!s.tripod) EdSlider("Smoothness", s.strength, 0f..1f, 0.6f, ::pct) { v -> onChange { it.copy(stab = it.stab.copy(strength = v)) } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Auto crop (hide moving edges)", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Switch(s.autoZoom, { v -> onChange { it.copy(stab = it.stab.copy(autoZoom = v)) } },
                colors = SwitchDefaults.colors(checkedTrackColor = Pro.Amber, checkedThumbColor = Color.Black))
        }
        if (!analysed && stabProgress == null) Text(
            "ANALYSE NOW", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp),
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Ed.Raised).clickable(onClick = onAnalyze).padding(8.dp),
        )
    }
}

/** Audio transitions for one cut: how the sound goes from the clip before to the clip after. */
@Composable
private fun AudioTransitionsPanel(
    t: Transition,
    onChange: (Transition) -> Unit,
    onApplyAll: (Transition) -> Unit,
    onPreview: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val cur = t.audioType
    Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("PREVIEW" to onPreview, "APPLY TO ALL CUTS" to { onApplyAll(t) }).forEach { (label, f) ->
            Text(label, style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Ed.Raised).clickable(onClick = f).padding(8.dp))
        }
    }
    val plainCut = t.type == TransitionType.NONE
    if (plainCut) {
        Text("This cut has no video transition, so the clips can't play together — the sound fades out and back in around the cut.", color = Pro.Dim, fontSize = 11.sp)
        if (cur != AudioTransitionType.AUTO && cur != AudioTransitionType.CUT) {
            EdSlider("Duration", t.audioLen / 1000f, 0.1f..3f, 0.6f, { "%.1f s".format(it) }) { onChange(t.copy(audioMs = (it * 1000).toLong())) }
        }
    } else {
        Text("The sound changes over the video transition (%.1f s).".format(t.durationMs / 1000f), color = Pro.Dim, fontSize = 11.sp)
    }
    SectionTitle("AUDIO TRANSITIONS")
    AudioTransitionType.entries.forEach { type ->
        val sel = type == cur
        Row(
            Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(8.dp))
                .border(if (sel) 2.dp else 1.dp, if (sel) Pro.Amber else Pro.Line, RoundedCornerShape(8.dp))
                .clickable { onChange(t.copy(audio = type)) }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FadeGraph(type, Modifier.padding(end = 10.dp).androidxSize(64, 34))
            Column(Modifier.weight(1f)) {
                Text(type.label, color = if (sel) Pro.Amber else Color.White, fontSize = 13.sp)
                Text(type.detail, color = Pro.Dim, fontSize = 11.sp)
            }
        }
    }

    SectionTitle("SOUND EFFECTS")
    Text("Plays a sound on the cut: impacts and booms land on it, risers and reverse cymbals build up to it. Tap ▶ to listen.", color = Pro.Dim, fontSize = 11.sp)
    UserSfxLibrary.load(context)
    val userSel = UserSfxLibrary.find(context, t.userSfx)
    val fxSel = if (userSel != null) null else t.sfx
    if (fxSel != null || userSel != null) {
        EdSlider("Effect volume", t.sfxVolume, 0f..1f, 0.8f, { "${(it * 100).toInt()}%" }) { onChange(t.copy(sfxVol = it)) }
    }

    // My sounds: the user's own files.
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var importing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var note by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true; note = null
        scope.launch {
            val s = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { UserSfxLibrary.import(context, uri) }
            importing = false
            if (s == null) note = "Couldn't read that file — try MP3, WAV, M4A, AAC, OGG or FLAC."
            else { onChange(t.copy(userSfx = s.id, sfx = null)); note = "${s.name} added to My sounds" }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("MY SOUNDS", style = Pro.Label.copy(color = Color.White, fontSize = 10.sp), modifier = Modifier.weight(1f))
        Text(
            if (importing) "IMPORTING…" else "+ IMPORT SOUND",
            style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp),
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Ed.Raised).clickable(enabled = !importing) { pick.launch(arrayOf("audio/*")) }.padding(8.dp),
        )
    }
    note?.let { Text(it, color = Pro.Dim, fontSize = 11.sp) }
    if (UserSfxLibrary.items.isEmpty()) {
        Text("Import your own whooshes, hits or risers (MP3, WAV, M4A…). They're copied into the app and can be used on any cut.", color = Pro.Dim, fontSize = 11.sp)
    }
    UserSfxLibrary.items.toList().forEach { u ->
        val sel = u.id == userSel?.id
        Column(
            Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(8.dp))
                .border(if (sel) 2.dp else 1.dp, if (sel) Pro.Amber else Pro.Line, RoundedCornerShape(8.dp))
                .clickable { onChange(t.copy(userSfx = u.id, sfx = null)); UserSfxLibrary.play(context, u, t.sfxVolume) }
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(u.name, color = if (sel) Pro.Amber else Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("%.1f s · %s".format(u.lengthMs / 1000f, u.anchorOrStart.label.lowercase()), color = Pro.Dim, fontSize = 11.sp)
                }
                Text(
                    "▶", color = Pro.Amber, fontSize = 16.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Ed.Raised).clickable { UserSfxLibrary.play(context, u, t.sfxVolume) }.padding(horizontal = 12.dp, vertical = 6.dp),
                )
                Text(
                    "✕", color = Pro.Red, fontSize = 14.sp,
                    modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(50)).clickable {
                        if (sel) onChange(t.copy(userSfx = null))
                        UserSfxLibrary.remove(context, u.id)
                    }.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            if (sel) {
                Text("Where it sits on the cut:", color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                Chips(SfxAnchor.entries.map { it.label }, u.anchorOrStart.ordinal) { UserSfxLibrary.setAnchor(context, u.id, SfxAnchor.entries[it]) }
            }
        }
    }

    Text("BUILT-IN", style = Pro.Label.copy(color = Color.White, fontSize = 10.sp), modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
    val rows = mutableListOf<Any?>(null)
    SfxGroup.entries.forEach { g -> rows.add(g); rows.addAll(TransitionSfx.entries.filter { it.group == g }) }
    rows.forEach { row ->
        if (row is SfxGroup) {
            Text(row.label.uppercase(), style = Pro.Label.copy(fontSize = 9.sp), modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
            return@forEach
        }
        val fx = row as TransitionSfx?
        val sel = if (fx == null) fxSel == null && userSel == null else fx == fxSel
        Row(
            Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(8.dp))
                .border(if (sel) 2.dp else 1.dp, if (sel) Pro.Amber else Pro.Line, RoundedCornerShape(8.dp))
                .clickable { onChange(t.copy(sfx = fx, userSfx = null)); if (fx != null) SfxPlayer.play(context, fx, t.sfxVolume) }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(fx?.label ?: "No sound effect", color = if (sel) Pro.Amber else Color.White, fontSize = 13.sp)
                if (fx != null) Text(fx.detail, color = Pro.Dim, fontSize = 11.sp)
            }
            if (fx != null) Text(
                "▶", color = Pro.Amber, fontSize = 16.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Ed.Raised).clickable { SfxPlayer.play(context, fx, t.sfxVolume) }.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

private fun Modifier.androidxSize(w: Int, h: Int) = this.then(Modifier.width(w.dp).height(h.dp))

/** Small picture of the two volume curves: outgoing (amber) and incoming (white). */
@Composable
private fun FadeGraph(type: AudioTransitionType, modifier: Modifier) {
    val (fo, fi) = AudioXfade.shapes(type, 1000, 1000)
    androidx.compose.foundation.Canvas(modifier.clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.35f))) {
        val n = 40
        fun path(g: (Long) -> Float): androidx.compose.ui.graphics.Path = androidx.compose.ui.graphics.Path().apply {
            for (k in 0..n) {
                val ms = k * 1000L / n
                val x = size.width * k / n
                val y = size.height * (1f - g(ms) * 0.85f) - size.height * 0.05f
                if (k == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
        drawPath(path { ms -> fo?.gain(1000 - ms) ?: (1f - ms / 1000f) }, Pro.Amber, style = stroke)
        drawPath(path { ms -> fi?.gain(ms) ?: (ms / 1000f) }, Color.White, style = stroke)
    }
}


/** Keyframes on a clip: zoom, position, rotation and opacity over time, with easing. */
@Composable
private fun ClipKeyframes(c: Clip, playheadRel: Long, onChange: ((Clip) -> Clip) -> Unit, onSeekRel: (Long) -> Unit) {
    val r = c.reframe
    val keys = r.keys.orEmpty().sortedBy { it.t }
    val now = r.keyedAt(playheadRel)
    SectionTitle("KEYFRAMES", onReset = if (keys.isNotEmpty()) ({ onChange { it.copy(reframe = it.reframe.copy(keys = null)) } }) else null)
    Text(
        "Move the playhead, set zoom / position / rotation / opacity, and tap ◆ ADD KEYFRAME. The clip moves smoothly between keyframes, in the preview and the export.",
        color = Pro.Dim, fontSize = 11.sp,
    )
    val onKey = keys.any { kotlin.math.abs(it.t - playheadRel) <= 40 }
    Text(
        if (onKey) "◆ UPDATE KEYFRAME AT ${tc(playheadRel)}" else "◆ ADD KEYFRAME AT ${tc(playheadRel)}",
        style = Pro.Label.copy(color = Color.Black, fontSize = 11.sp),
        modifier = Modifier.padding(vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Pro.Amber).clickable {
            onChange { cl ->
                val cur = cl.reframe.keyedAt(playheadRel)
                // The first key starts from the clip's current framing (start / Ken Burns at this point).
                val fr = cur?.frame ?: cl.reframe.at(playheadRel.toFloat() / (cl.endMs - cl.startMs).coerceAtLeast(1))
                cl.copy(reframe = cl.reframe.upsertKey(playheadRel, fr, cur?.opacity ?: 1f))
            }
        }.padding(10.dp),
    )
    EdSlider("Opacity", now?.opacity ?: 1f, 0f..1f, 1f, ::pct) { v ->
        onChange { cl ->
            val cur = cl.reframe.keyedAt(playheadRel)
            val fr = cur?.frame ?: cl.reframe.start
            cl.copy(reframe = cl.reframe.upsertKey(playheadRel, fr, v))
        }
    }
    keys.forEachIndexed { i, k ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp))
                .background(if (kotlin.math.abs(k.t - playheadRel) <= 40) Pro.Amber.copy(alpha = 0.18f) else Ed.Raised).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("◆ ${tc(k.t)}", style = Pro.Value.copy(color = Pro.Amber, fontSize = 12.sp), modifier = Modifier.clickable { onSeekRel(k.t) }.padding(4.dp))
            Text(
                "%.2fx · %.0f%%".format(k.frame.zoom, k.opacity * 100), style = Pro.Label.copy(fontSize = 10.sp),
                modifier = Modifier.weight(1f).padding(start = 6.dp),
            )
            if (i < keys.lastIndex) {
                val labels = listOf("Smooth", "Linear", "Hold", "Curve")
                Text(
                    labels[k.ease.coerceIn(0, 3)], style = Pro.Label.copy(color = Color.White, fontSize = 10.sp),
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.08f)).clickable {
                        onChange { cl -> cl.copy(reframe = cl.reframe.copy(keys = cl.reframe.keys?.map { if (it.t == k.t) it.copy(ease = (it.ease + 1) % 4, bez = it.bez ?: KeyCurve.PRESETS[2].second) else it })) }
                    }.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Text("✕", color = Pro.Red, fontSize = 14.sp, modifier = Modifier.clickable {
                onChange { cl -> cl.copy(reframe = cl.reframe.copy(keys = cl.reframe.keys?.filter { it.t != k.t }?.takeIf { it.isNotEmpty() })) }
            }.padding(8.dp))
        }
        if (k.ease == 3 && i < keys.lastIndex) {
            fun setBez(b: List<Float>) = onChange { cl -> cl.copy(reframe = cl.reframe.copy(keys = cl.reframe.keys?.map { if (it.t == k.t) it.copy(bez = b) else it })) }
            KeyCurveEditor(k.bez ?: KeyCurve.PRESETS[2].second) { setBez(it) }
            Row(Modifier.hintedScroll(horizontal = true), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyCurve.PRESETS.forEach { (n, b) -> PillChip(n, k.bez == b) { setBez(b) } }
            }
        }
    }
    if (keys.any { it.ease == 3 }) Text("Curve: drag the two amber handles to shape how the move speeds up and slows down to the next keyframe (above the top line = overshoot).", color = Pro.Dim, fontSize = 11.sp)
}

/** Graph editor for one keyframe's curve: time across, progress up; two Bézier handles. */
@Composable
fun KeyCurveEditor(bez: List<Float>, onChange: (List<Float>) -> Unit) {
    val cur by androidx.compose.runtime.rememberUpdatedState(bez)
    val ch by androidx.compose.runtime.rememberUpdatedState(onChange)
    Box(
        Modifier.padding(vertical = 6.dp).fillMaxWidth().height(170.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.35f))
            .pointerInput(Unit) {
                // The graph area: 0..1 in time, −0.5..1.5 in progress.
                fun toXY(o: androidx.compose.ui.geometry.Offset): Pair<Float, Float> {
                    val pad = 18f
                    val x = ((o.x - pad) / (size.width - 2 * pad)).coerceIn(0f, 1f)
                    val y = (1.5f - (o.y - pad) / (size.height - 2 * pad) * 2f).coerceIn(-0.5f, 1.5f)
                    return x to y
                }
                fun fromXY(x: Float, y: Float): androidx.compose.ui.geometry.Offset {
                    val pad = 18f
                    return androidx.compose.ui.geometry.Offset(pad + x * (size.width - 2 * pad), pad + (1.5f - y) / 2f * (size.height - 2 * pad))
                }
                awaitEachGesture {
                    val d = awaitFirstDown()
                    val b = cur
                    val h1 = fromXY(b[0], b[1]); val h2 = fromXY(b[2], b[3])
                    val which = if ((d.position - h1).getDistance() <= (d.position - h2).getDistance()) 0 else 1
                    while (true) {
                        val ev = awaitPointerEvent(); val c = ev.changes.firstOrNull() ?: break
                        if (!c.pressed) break
                        val (x, y) = toXY(c.position)
                        val nb = cur.toMutableList()
                        nb[which * 2] = (Math.round(x * 100) / 100f); nb[which * 2 + 1] = (Math.round(y * 100) / 100f)
                        ch(nb); c.consume()
                    }
                }
            },
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val pad = 18f
            fun p(x: Float, y: Float) = androidx.compose.ui.geometry.Offset(pad + x * (size.width - 2 * pad), pad + (1.5f - y) / 2f * (size.height - 2 * pad))
            val grid = com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.12f)
            drawLine(grid, p(0f, 0f), p(1f, 0f)); drawLine(grid, p(0f, 1f), p(1f, 1f)); drawLine(grid, p(0f, 0f), p(0f, 1f)); drawLine(grid, p(1f, 0f), p(1f, 1f))
            val path = androidx.compose.ui.graphics.Path()
            for (i in 0..60) {
                val u = i / 60f
                val o = p(u, KeyCurve.bezier(u, bez))
                if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            drawPath(path, Color.White, style = androidx.compose.ui.graphics.drawscope.Stroke(2.5f))
            val h1 = p(bez[0], bez[1]); val h2 = p(bez[2], bez[3])
            drawLine(Pro.Amber.copy(alpha = 0.7f), p(0f, 0f), h1, 1.5f); drawLine(Pro.Amber.copy(alpha = 0.7f), p(1f, 1f), h2, 1.5f)
            drawCircle(Pro.Amber, 7.dp.toPx(), h1); drawCircle(Pro.Amber, 7.dp.toPx(), h2)
        }
    }
}
