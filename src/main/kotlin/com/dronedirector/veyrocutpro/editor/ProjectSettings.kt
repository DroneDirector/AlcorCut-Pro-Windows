package com.dronedirector.veyrocutpro.editor

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.ui.Pro
import com.dronedirector.veyrocutpro.ui.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Names for the clip label colours, and which colour new media gets by kind. Own code. */
object LabelNames {
    private const val PREFS = "procam"
    val DEFAULTS = listOf("Red", "Orange", "Yellow", "Green", "Teal", "Blue", "Purple", "Pink")
    fun all(c: Context): List<String> {
        val s = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("label_names", null)?.split('\n')
        return DEFAULTS.indices.map { i -> s?.getOrNull(i)?.takeIf { it.isNotBlank() } ?: DEFAULTS[i] }
    }
    fun set(c: Context, i: Int, name: String) {
        val l = all(c).toMutableList(); l[i] = name.take(24)
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("label_names", l.joinToString("\n")).apply()
    }
    fun nameOf(c: Context, color: Int?): String? = color?.let { CLIP_LABELS.indexOf(it) }?.takeIf { it >= 0 }?.let { all(c)[it] }
    /** Kind of media: "video", "photo", "generated", "offline" → colour index (-1 none). */
    fun forKind(c: Context, kind: String): Int = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("label_kind_$kind", if (kind == "offline") 0 else -1)
    fun setForKind(c: Context, kind: String, i: Int) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("label_kind_$kind", i).apply()
}

/** File → Project settings → Ingest: what happens to media as it's added. */
object Ingest {
    private fun p(c: Context) = c.getSharedPreferences("procam", Context.MODE_PRIVATE)
    fun bin(c: Context): String = p(c).getString("ingest_bin", "") ?: ""
    fun setBin(c: Context, b: String) = p(c).edit().putString("ingest_bin", b).apply()
    fun proxies(c: Context) = p(c).getBoolean("ingest_proxy", false)
    fun setProxies(c: Context, on: Boolean) = p(c).edit().putBoolean("ingest_proxy", on).apply()
    fun labels(c: Context) = p(c).getBoolean("ingest_labels", false)
    fun setLabels(c: Context, on: Boolean) = p(c).edit().putBoolean("ingest_labels", on).apply()
    /** Heavy for the phone: 4K or more, HDR or 10-bit, or 4:2:2. */
    fun heavy(cl: Clip) = cl.width.toLong() * cl.height >= 3840L * 2160 * 9 / 10 || cl.hdr || cl.tenBit || cl.is422
    fun kind(cl: Clip): String = when {
        cl.offline == true -> "offline"
        cl.uri.path?.contains("/generated/") == true -> if (cl.name.startsWith("photo", true)) "photo" else "generated"
        else -> "video"
    }
}

private val P_SHAPES = listOf("Same as clips" to 0f, "16:9" to 16f / 9f, "9:16" to 9f / 16f, "1:1" to 1f, "4:5" to 0.8f, "21:9" to 21f / 9f, "4:3" to 4f / 3f)
private val P_RATES = listOf(0, 24, 25, 30, 50, 60)

/**
 * File → Project settings: General (the gear's main switches), Project, Storage location,
 * Ingest and Label colours (video editor); General and Storage (photo editor).
 */
@Composable
fun ProjectSettingsDialog(
    video: Boolean,
    onDismiss: () -> Unit,
    general: @Composable () -> Unit,
    storage: @Composable () -> Unit,
    projectName: String = "", onRename: (String) -> Unit = {},
    timelineName: String = "", onRenameTimeline: (String) -> Unit = {},
    aspect: Float = 0f, fps: Int = 0, onShape: (Float, Int) -> Unit = { _, _ -> },
    teamLabel: String? = null,
    bins: List<String> = emptyList(),
) {
    val context = LocalContext.current
    val tabs = if (video) listOf("General", "Project", "Storage", "Ingest", "Label colours") else listOf("General", "Storage")
    var tab by remember { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Pro.PanelSolid,
        title = { Text("Project settings", color = Color.White) },
        text = {
            Column {
                Chips(tabs, tab) { tab = it }
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    when (tabs[tab]) {
                        "General" -> general()
                        "Storage" -> {
                            storage()
                            AppCopies()
                        }
                        "Project" -> {
                            var n by remember { mutableStateOf(projectName) }
                            var t by remember { mutableStateOf(timelineName) }
                            OutlinedTextField(n, { n = it.take(60); onRename(n) }, singleLine = true, label = { Text("Project name") }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), colors = fieldColors())
                            OutlinedTextField(t, { t = it.take(60); onRenameTimeline(t) }, singleLine = true, label = { Text("Timeline name") }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), colors = fieldColors())
                            if (teamLabel != null) Text("Team project · $teamLabel", color = Pro.Amber, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                            var sh by remember { mutableIntStateOf(P_SHAPES.indexOfFirst { kotlin.math.abs(it.second - aspect) < 0.01f }.coerceAtLeast(0)) }
                            var r by remember { mutableIntStateOf(P_RATES.indexOf(fps).coerceAtLeast(0)) }
                            Text("TIMELINE SHAPE", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(top = 12.dp))
                            Chips(P_SHAPES.map { it.first }, sh) { sh = it; onShape(P_SHAPES[sh].second, P_RATES[r]) }
                            Text("FRAME RATE", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(top = 8.dp))
                            Chips(P_RATES.map { if (it == 0) "Same as clips" else "$it fps" }, r) { r = it; onShape(P_SHAPES[sh].second, P_RATES[r]) }
                            Text("The same as Deliver's shape and frame rate — the export follows these.", color = Pro.Dim, fontSize = 11.sp)
                        }
                        "Ingest" -> {
                            Text("What happens to new media as it's added to this editor.", color = Pro.Dim, fontSize = 12.sp)
                            var bin by remember { mutableStateOf(Ingest.bin(context)) }
                            Text("PUT NEW MEDIA IN A BIN", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(top = 10.dp))
                            val opts = listOf("") + bins
                            Chips(opts.map { it.ifEmpty { "No bin" } }, opts.indexOf(bin).coerceAtLeast(0)) { bin = opts[it]; Ingest.setBin(context, bin) }
                            if (bins.isEmpty()) Text("Make bins on the Media page (or File → New → Bin).", color = Pro.Dim, fontSize = 11.sp)
                            var px by remember { mutableStateOf(Ingest.proxies(context)) }
                            ToggleLine("Make proxies for heavy clips", "4K, HDR, 10-bit and 4:2:2 clips are added to the Proxy section at once, so they play smoothly", px) { px = it; Ingest.setProxies(context, it) }
                            var lb by remember { mutableStateOf(Ingest.labels(context)) }
                            ToggleLine("Colour label by kind", "New media gets the label chosen for its kind under Label colours", lb) { lb = it; Ingest.setLabels(context, it) }
                        }
                        "Label colours" -> {
                            Text("Name the colours (shown in the clip tools), and choose a colour for each kind of media (used when Ingest → Colour label by kind is on, and for offline files).", color = Pro.Dim, fontSize = 12.sp)
                            val names = remember { LabelNames.all(context).toMutableList() }
                            CLIP_LABELS.forEachIndexed { i, col ->
                                var v by remember { mutableStateOf(names[i]) }
                                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(22.dp).clip(CircleShape).background(Color(col)))
                                    OutlinedTextField(v, { v = it.take(24); LabelNames.set(context, i, v) }, singleLine = true, modifier = Modifier.weight(1f).padding(start = 8.dp), colors = fieldColors())
                                }
                            }
                            for ((kind, label) in listOf("video" to "Video clips", "photo" to "Photos (as clips)", "generated" to "Generated and gaps", "offline" to "Offline files")) {
                                var k by remember { mutableIntStateOf(LabelNames.forKind(context, kind)) }
                                Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(24.dp).clip(CircleShape).background(Ed.Raised).border(2.dp, if (k < 0) Pro.Amber else Color.DarkGray, CircleShape).clickable { k = -1; LabelNames.setForKind(context, kind, -1) }, contentAlignment = Alignment.Center) { Text("✕", color = Color.White, fontSize = 10.sp) }
                                    CLIP_LABELS.forEachIndexed { i, col ->
                                        Box(Modifier.padding(start = 6.dp).size(24.dp).clip(CircleShape).background(Color(col)).border(2.dp, if (k == i) Pro.Amber else Color.DarkGray, CircleShape).clickable { k = i; LabelNames.setForKind(context, kind, i) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = Pro.Amber) } },
    )
}

@Composable
private fun ToggleLine(title: String, detail: String, on: Boolean, set: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp)
            Text(detail, color = Pro.Dim, fontSize = 12.sp)
        }
        Switch(on, set, colors = SwitchDefaults.colors(checkedTrackColor = Pro.Amber, checkedThumbColor = Color.Black))
    }
}

/** Storage: copies the app keeps (from the NAS and team projects), with their size and Clear. */
@Composable
private fun AppCopies() {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    Text("COPIES KEPT IN THE APP", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(top = 14.dp))
    for ((dir, label) in listOf("nas" to "Files copied from a NAS", "team" to "Team project media")) {
        val size by produceState(-1L, version) { value = withContext(Dispatchers.IO) { File(context.filesDir, dir).walkBottomUp().filter { it.isFile }.sumOf { it.length() } } }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, color = Color.White, fontSize = 13.sp)
                Text(if (size < 0) "…" else String.format(java.util.Locale.US, "%.1f MB", size / 1048576f), color = Pro.Dim, fontSize = 11.sp)
            }
            TextButton(enabled = size > 0, onClick = { File(context.filesDir, dir).deleteRecursively(); version++ }) { Text("Clear", color = Pro.Red) }
        }
    }
    Text("Clearing them frees space; projects that use those files then show them as missing until they're added again.", color = Pro.Dim, fontSize = 11.sp)
}
