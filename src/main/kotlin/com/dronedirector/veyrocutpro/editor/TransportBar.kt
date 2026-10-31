package com.dronedirector.veyrocutpro.editor

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import com.dronedirector.veyrocutpro.ui.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.ui.Pro
import com.dronedirector.veyrocutpro.ui.Text

/**
 * The video editor's button bar under the picture: which buttons it shows (chosen with its + button)
 * and the playback resolution of the preview. Own code.
 */
object TransportBar {
    private const val PREFS = "procam"
    private const val HIDDEN = "ve_bar_hidden"

    /** One button on the bar: [id] is kept in the settings, [label] names it in the + list. */
    class Item(val id: String, val label: String)

    /** Every button the bar can show, in bar order (the + list shows them in this order too). */
    val ALL = listOf(
        Item("split", "Split at playhead"), Item("undo", "Undo"), Item("redo", "Redo"),
        Item("markin", "Mark in"), Item("markout", "Mark out"), Item("marker", "Add marker"), Item("markers", "Markers (go to, edit, clear, lists)"),
        Item("speed", "Speed / Duration"), Item("interp", "Time interpolation"), Item("link", "Link / unlink tracks"), Item("group", "Group / ungroup"),
        Item("mute", "Mute preview"), Item("tlout", "Zoom timeline out"), Item("tlfit", "Show whole timeline"), Item("tlin", "Zoom timeline in"),
        Item("cast", "Cast to a TV"), Item("safe", "Safe margins"), Item("res", "Playback resolution"), Item("magnify", "Magnification"),
        Item("goin", "Go to in point"), Item("frameback", "Step back one frame"), Item("play", "Play / pause"), Item("framefwd", "Step forward one frame"), Item("loop", "Loop playback"),
        Item("goout", "Go to out point"), Item("prev", "Previous clip"), Item("next", "Next clip"), Item("start", "Playhead to start"),
        Item("full", "Full screen"), Item("frame", "Export frame"), Item("settings", "Settings"),
    )

    val hidden = mutableStateListOf<String>()
    private var loaded = false

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        hidden.clear()
        hidden.addAll(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(HIDDEN, emptySet()).orEmpty())
    }

    fun shown(id: String) = id !in hidden

    fun set(c: Context, id: String, on: Boolean) {
        if (on) hidden.remove(id) else if (id !in hidden) hidden.add(id)
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(HIDDEN, hidden.toSet()).apply()
    }

    fun showAll(c: Context) {
        hidden.clear()
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(HIDDEN, emptySet()).apply()
    }

    /** Preview resolution: 1 = full, 2 = ½, 4 = ¼, 8 = ⅛, 16 = 1/16 (the export is always full). */
    var playbackDiv by mutableStateOf(1)
    val DIVS = listOf(1 to "Full", 2 to "1/2", 4 to "1/4", 8 to "1/8", 16 to "1/16")

    /** Magnification of the preview (view only, never exported): 1, 2 or 4. */
    var magnify by mutableStateOf(1f)
}

/** The + button's list: a switch per button. */
@Composable
fun TransportButtonEditor(context: Context, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pro.PanelSolid,
        title = { Text("Buttons under the picture", color = Color.White) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Text("Choose which buttons the bar shows. The bar scrolls sideways when they don't all fit.", color = Pro.Dim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                for (it in TransportBar.ALL) {
                    Row(Modifier.fillMaxWidth().clickable { TransportBar.set(context, it.id, !TransportBar.shown(it.id)) }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { Icon(transportIcon(it.id), null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                        Text(it.label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(start = 8.dp))
                        Switch(TransportBar.shown(it.id), { on -> TransportBar.set(context, it.id, on) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Pro.Amber))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = Pro.Amber) } },
        dismissButton = { TextButton(onClick = { TransportBar.showAll(context) }) { Text("Show all", color = Pro.Dim) } },
    )
}

/** The icon of a bar button (also used in the + list). */
fun transportIcon(id: String): ImageVector = when (id) {
    "split" -> Icons.Filled.ContentCut
    "undo" -> Icons.AutoMirrored.Filled.Undo
    "redo" -> Icons.AutoMirrored.Filled.Redo
    "markin" -> BarIcons.markIn
    "markout" -> BarIcons.markOut
    "marker" -> BarIcons.marker
    "markers" -> Icons.Filled.Bookmarks
    "speed" -> Icons.Filled.Speed
    "interp" -> Icons.Filled.SlowMotionVideo
    "link" -> Icons.Filled.Link
    "group" -> Icons.Filled.Workspaces
    "mute" -> Icons.Filled.VolumeUp
    "tlout" -> Icons.Filled.ZoomOut
    "tlfit" -> Icons.Filled.FitScreen
    "tlin" -> Icons.Filled.ZoomIn
    "cast" -> Icons.Filled.Cast
    "safe" -> Icons.Filled.CropFree
    "res" -> Icons.Filled.Hd
    "magnify" -> Icons.Filled.Search
    "goin" -> BarIcons.goIn
    "frameback" -> BarIcons.frameBack
    "play" -> Icons.Filled.PlayArrow
    "framefwd" -> BarIcons.frameFwd
    "loop" -> Icons.Filled.Repeat
    "goout" -> BarIcons.goOut
    "prev" -> Icons.Filled.SkipPrevious
    "next" -> Icons.Filled.SkipNext
    "start" -> BarIcons.toStart
    "full" -> Icons.Filled.Fullscreen
    "frame" -> Icons.Filled.AddAPhoto
    "settings" -> Icons.Filled.Settings
    else -> Icons.Filled.Add
}

/** The bar's own icons (24 × 24, drawn in white; the bar tints them). */
object BarIcons {
    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            for (p in paths) addPath(PathParser().parsePathString(p).toNodes(), fill = SolidColor(Color.White))
        }.build()

    /** "[" with a small mark: set the in point here. */
    val markIn by lazy { icon("MarkIn", "M7,4h7v2.2H9.3v11.6H14V20H7z", "M15.5,10.2l4.5,1.8-4.5,1.8z") }
    /** "]" with a small mark: set the out point here. */
    val markOut by lazy { icon("MarkOut", "M17,4h-7v2.2h4.7v11.6H10V20h7z", "M8.5,10.2l-4.5,1.8 4.5,1.8z") }
    /** Arrow back to an in bracket. */
    val goIn by lazy { icon("GoIn", "M3,4h5v2H5v12h3v2H3z", "M10,12l6-5v3.5h5v3h-5V17z") }
    /** Arrow on to an out bracket. */
    val goOut by lazy { icon("GoOut", "M21,4h-5v2h3v12h-3v2h5z", "M14,12l-6-5v3.5H3v3h5V17z") }
    /** A diamond: add a marker. */
    val marker by lazy { icon("Marker", "M12,3l7,9-7,9-7-9z") }
    /** One frame back: a bar and a small triangle. */
    val frameBack by lazy { icon("FrameBack", "M6,6h2.2v12H6z", "M18,6v12l-8.5-6z") }
    /** One frame on. */
    val frameFwd by lazy { icon("FrameFwd", "M18,6h-2.2v12H18z", "M6,6v12l8.5-6z") }
    /** Playhead to the very start. */
    val toStart by lazy { icon("ToStart", "M2.5,4h2v16h-2z", "M6.5,12l7-6v4.5h8v3h-8V18z") }
}
