package com.dronedirector.veyrocutpro.ui

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.dronedirector.veyrocutpro.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A short first-run tour of the camera's gestures, shown once (replay from Settings). */
object Tour {
    var visible by mutableStateOf(false)
    private const val KEY = "tour_done_v1"

    fun maybeStart(c: Context) {
        if (!c.getSharedPreferences("procam", Context.MODE_PRIVATE).getBoolean(KEY, false)) visible = true
    }

    fun finish(c: Context) {
        visible = false
        c.getSharedPreferences("procam", Context.MODE_PRIVATE).edit().putBoolean(KEY, true).apply()
    }

    val steps = listOf(
        "👆" to ("Tap to focus" to "Tap anywhere on the picture to focus and set the brightness there."),
        "✋" to ("Hold 2 seconds for manual focus" to "A ring fills, then a focus slider appears on the right edge. Tap the small ✕ to go back to autofocus."),
        "🔒" to ("Lock the exposure" to "Hold about half a second until it ticks, then let go. Drag the slider under the lock to brighten or darken."),
        "🤏" to ("Pinch to zoom" to "Use two fingers on the picture. The lens buttons switch between wide, main and tele."),
        "🎬" to ("Regular and smart modes" to "REGULAR has Photo and Video (and Pro). SMART has Portrait, Pano, Long exposure, Time-lapse, Slow-mo and more."),
        "⚙" to ("Settings and guides" to "The gear has the full guides, the Simple / Pro layout, remote monitor, backup and everything else."),
    )
}

@Composable
fun TourOverlay() {
    if (!Tour.visible) return
    val ctx = LocalContext.current
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)).clickable(enabled = true, onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        TourCard(Tour.steps, "START SHOOTING", onSkip = { Tour.finish(ctx) }, onDone = { Tour.finish(ctx) })
    }
}

/** The tour card (icon, title, text, dots, SKIP / NEXT), shared by Capture and the editors. */
@Composable
internal fun TourCard(
    steps: List<Pair<String, Pair<String, String>>>,
    doneLabel: String,
    onSkip: () -> Unit,
    onDone: () -> Unit,
    extra: (@Composable () -> Unit)? = null,
) {
    var step by remember { mutableIntStateOf(0) }
    Column(
        Modifier.padding(24.dp).widthIn(max = 420.dp).clip(RoundedCornerShape(18.dp)).background(Pro.PanelSolid).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedContent(step, label = "tour") { i ->
            val (icon, tt) = steps[i]
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(icon, fontSize = 44.sp)
                Text(tt.first, color = Pro.Amber, fontSize = 19.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
                Text(tt.second, color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
            }
        }
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            steps.indices.forEach { i ->
                Box(Modifier.size(if (i == step) 9.dp else 6.dp).clip(CircleShape).background(if (i == step) Pro.Amber else Color.White.copy(alpha = 0.3f)))
            }
        }
        extra?.invoke()
        Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("SKIP", color = Pro.Dim, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onSkip).padding(horizontal = 16.dp, vertical = 10.dp))
            val last = step == steps.lastIndex
            Text(
                if (last) doneLabel else "NEXT", color = Color.Black, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Pro.Amber).clickable { if (last) onDone() else step++ }.padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
    }
}

/**
 * Quick tours for the Photo and Video editors. Shown when the editor opens until it is finished or
 * "Don't show this again" is ticked; SKIP only closes it for now (it comes back next time the app starts).
 */
object EditorTour {
    var showing by mutableStateOf<GuideVariant?>(null)
    private val skipped = mutableSetOf<GuideVariant>()
    private fun key(v: GuideVariant) = "tour_editor_done_" + v.name
    private fun prefs(c: Context) = c.getSharedPreferences("procam", Context.MODE_PRIVATE)

    fun autoShow(c: Context, v: GuideVariant) = !prefs(c).getBoolean(key(v), false)
    fun setAutoShow(c: Context, v: GuideVariant, on: Boolean) {
        prefs(c).edit().putBoolean(key(v), !on).apply()
        if (on) skipped.remove(v)
    }

    fun maybeStart(c: Context, v: GuideVariant) {
        if (showing == null && v !in skipped && autoShow(c, v)) showing = v
    }

    fun show(v: GuideVariant) { showing = v }

    fun close(c: Context, v: GuideVariant, neverAgain: Boolean) {
        showing = null
        skipped += v
        if (neverAgain) prefs(c).edit().putBoolean(key(v), true).apply()
    }

    fun steps(v: GuideVariant): List<Pair<String, Pair<String, String>>> = if (v == GuideVariant.VIDEO) listOf(
        "🎞" to ("Add your clips" to "The media strip holds every clip you imported. Tap a clip, then Append or Insert at playhead — or double-tap it to add it to the timeline."),
        "👆" to ("Move around the timeline" to "Drag one finger to move the playhead. Pinch with two fingers to zoom and slide them to scroll. The glowing wheel on the right scrolls the tracks."),
        "✂" to ("Cut and trim" to "The scissors under the player split at the playhead. Tap a clip to select it; the Edit tab has trim tools, speed, freeze, reverse and more."),
        "✨" to ("Transitions" to "Tap a cut between two clips to choose a transition."),
        "🎨" to ("Colour, titles and sound" to "The tool tabs: Color grades the picture, Text and Layers add titles, stickers and picture-in-picture, Audio mixes music and voice."),
        "📤" to ("Export" to "The Deliver tab saves the finished video — choose the size, quality and format there."),
        "⚙" to ("Settings and guides" to "The gear has the full guide, autosave, Cloud AI and this tour (App → Quick tour)."),
    ) else listOf(
        "🖼" to ("Start with a look" to "Presets has ready-made looks. The Amount slider fades a preset in or pushes it further."),
        "☀" to ("Light and colour" to "Light sets exposure, contrast, highlights and shadows — AUTO does it for you. Curve, Color and HSL Mixer fine-tune the colours."),
        "🤏" to ("Zoom in" to "Pinch or double-tap the photo to zoom in. Double-tap again to fit it to the screen."),
        "🎯" to ("Masks and healing" to "Masks select the sky, people, objects or a painted area to adjust them on their own. Heal removes dust spots, wires and people."),
        "↶" to ("Undo anything" to "Undo and redo are in the top bar; History lists every step you made."),
        "📤" to ("Save and export" to "Export saves the finished photo — JPEG, PNG or TIFF, in the size you want."),
        "⚙" to ("Settings and guides" to "The gear has the full guide, autosave, Cloud AI and this tour (App → Quick tour)."),
    )
}

/** Shows the editor's quick tour over the editor (first time, or when asked from the gear). */
@Composable
fun EditorTourHost(v: GuideVariant) {
    val ctx = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(v) { EditorTour.maybeStart(ctx, v) }
    if (EditorTour.showing != v) return
    var never by remember { mutableStateOf(false) }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { EditorTour.close(ctx, v, never) },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)), contentAlignment = Alignment.Center) {
            TourCard(
                EditorTour.steps(v), "START EDITING",
                onSkip = { EditorTour.close(ctx, v, never) },
                onDone = { EditorTour.close(ctx, v, true) },
                extra = {
                    Row(
                        Modifier.padding(top = 14.dp).clip(RoundedCornerShape(8.dp)).clickable { never = !never }.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = never, onCheckedChange = { never = it },
                            colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = Pro.Amber, checkmarkColor = Color.Black, uncheckedColor = Pro.Dim),
                        )
                        Text("Don't show this again", color = Pro.Dim, fontSize = 13.sp)
                    }
                },
            )
        }
    }
}

/** Gear → App → Quick tour, for an editor: show it now, and whether it opens by itself. */
@Composable
fun EditorTourSettings(v: GuideVariant, onShow: () -> Unit) {
    val ctx = LocalContext.current
    var auto by remember { mutableStateOf(EditorTour.autoShow(ctx, v)) }
    Text("QUICK TOUR", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(top = 10.dp))
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Show the quick tour", color = Color.White, fontSize = 15.sp)
            Text("Opens the short tour when the editor starts. Turned off by \"Don't show this again\" or by finishing the tour.", color = Pro.Dim, fontSize = 12.sp)
        }
        androidx.compose.material3.Switch(
            checked = auto,
            onCheckedChange = { auto = it; EditorTour.setAutoShow(ctx, v, it) },
            colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Pro.Amber, checkedThumbColor = Color.Black),
        )
    }
    Text(
        "▶ SHOW THE QUICK TOUR NOW", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
        modifier = Modifier.padding(vertical = 6.dp).clip(RoundedCornerShape(8.dp)).clickable { EditorTour.show(v); onShow() }.padding(8.dp),
    )
}

/** Settings row to show the tour again. */
@Composable
fun TourButton() {
    Text(
        "▶ SHOW THE QUICK TOUR AGAIN", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
        modifier = Modifier.padding(vertical = 6.dp).clip(RoundedCornerShape(8.dp)).clickable { Tour.visible = true }.padding(8.dp),
    )
}
