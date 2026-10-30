package com.dronedirector.veyrocutpro.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.ui.Pro
import com.dronedirector.veyrocutpro.ui.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun sizeText(b: Long): String = when {
    b >= 1L shl 30 -> "%.1f GB".format(b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> "%.0f MB".format(b / (1L shl 20).toDouble())
    else -> "%.0f KB".format(b / 1024.0)
}

/** Photo editor settings → Catalog: catalogs, how many photos they remember, photo copies, location. */
@Composable
fun PhotoCatalogSettings() = CatalogSettings(
    Catalogs, video = false,
    about = "A catalog remembers every photo you edit: the photo itself (a copy), its edit and settings, stars and flags, and the results of AI tools — so AI noise reduction never has to run again for a photo in the catalog.",
)

/** Video editor settings → Catalog: catalogs, how many clips they remember, clip copies, location, slimming down. */
@Composable
fun VideoCatalogSettings() = CatalogSettings(
    VideoCatalogs, video = true,
    about = "A catalog remembers every clip you use: its last settings (grade, detail, stabilisation, film look, audio) — given back when you add the clip to another project — and the slow work done for it: proxies, stabilisation analysis, waveforms, render cache, cloud AI results and transcripts. Nothing has to be made twice for a clip in the catalog.",
)

@Composable
private fun CatalogSettings(store: CatalogStore, video: Boolean, about: String) {
    val Catalogs = store
    val unit = if (video) "clip" else "photo"
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { Catalogs.init(ctx) }
    var refresh by remember { mutableStateOf(0) }
    var naming by remember { mutableStateOf<CatalogInfo?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<CatalogInfo?>(null) }
    var moving by remember { mutableStateOf<Int?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    val stats by produceState(emptyMap<String, Pair<Int, Long>>(), refresh, Catalogs.list.size, Catalogs.activeId) {
        value = withContext(Dispatchers.IO) { Catalogs.list.associate { it.id to (Catalogs.countOf(it) to Catalogs.sizeOf(it)) } }
    }
    val dim = @Composable { t: String -> Text(t, color = Pro.Dim, fontSize = 11.sp) }

    dim(about)

    // Catalogs
    Text("Catalogs", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
    val locs = remember { Catalogs.locations(ctx) }
    Catalogs.list.forEach { info ->
        val on = info.id == Catalogs.activeId
        val st = stats[info.id]
        Column(
            Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(10.dp))
                .background(if (on) Pro.Amber.copy(alpha = 0.14f) else com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.05f)).padding(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(info.name + if (on) "  ✓" else "", color = if (on) Pro.Amber else Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (!on) PillChip("OPEN", false, accentText = true) { Catalogs.open(ctx, info.id); refresh++ }
            }
            val where = locs.firstOrNull { java.io.File(info.root).path == it.second.path }?.first ?: info.root
            dim((st?.let { "${it.first} $unit" + (if (it.first == 1) "" else "s") + " · " + sizeText(it.second) + " · " } ?: "") + where)
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PillChip("RENAME", false) { naming = info }
                if (Catalogs.list.size > 1) PillChip("DELETE", false) { deleting = info }
            }
        }
    }
    Text(
        "+ NEW CATALOG", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
        modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable { creating = true }.padding(10.dp),
    )

    // How many photos
    Text(if (video) "Clips remembered" else "Photos remembered", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
    val presets = listOf(10, 25, 50, 100, 250, 500, 1000, 0)
    val idx = presets.indexOf(Catalogs.limit).let { if (it < 0) -1 else it }
    Chips(presets.map { if (it == 0) "Unlimited" else "$it" }, idx) { Catalogs.setLimit(ctx, presets[it]); refresh++ }
    var custom by remember { mutableStateOf(if (Catalogs.limit > 0 && Catalogs.limit !in presets) Catalogs.limit.toString() else "") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.OutlinedTextField(
            value = custom, onValueChange = { custom = it.filter(Char::isDigit).take(6) }, singleLine = true,
            label = { Text("Other number (10 or more)") }, modifier = Modifier.weight(1f),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedTextColor = com.dronedirector.veyrocutpro.ui.Pro.Fg, unfocusedTextColor = com.dronedirector.veyrocutpro.ui.Pro.Fg, focusedBorderColor = Pro.Amber, unfocusedBorderColor = Pro.Line, focusedLabelColor = Pro.Amber, cursorColor = Pro.Amber),
        )
        PillChip("SET", false, accentText = true) {
            custom.toIntOrNull()?.let { n -> Catalogs.setLimit(ctx, n.coerceAtLeast(Catalogs.MIN_LIMIT)); custom = Catalogs.limit.toString(); refresh++ }
        }
    }
    dim(if (Catalogs.limit == 0) "Unlimited: every $unit stays in the catalog until you remove it."
        else if (video) "When the catalog holds more than ${Catalogs.limit} clips, the one used longest ago is let go (with its copy, settings, proxies and analysis)."
        else "When the catalog holds more than ${Catalogs.limit} photos, the one opened longest ago is let go (with its copy, edit and AI results).")

    // Copies
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(if (video) "Keep a copy of each clip" else "Keep a copy of each photo", color = Color.White, fontSize = 13.sp)
            dim(if (video) "Off by default: videos are large. When on, a project still opens if a clip's original is moved or deleted. Copies are exact (videos are already compressed, so they're kept as they are)."
                else "The photo opens from the catalog even if the original is moved or deleted. RAW copies are packed losslessly — unpacked they're identical to the original.")
        }
        androidx.compose.material3.Switch(Catalogs.keepCopies, { Catalogs.setKeepCopies(ctx, it) },
            colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Pro.Amber, checkedThumbColor = Color.Black))
    }

    if (video) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Give clips their last settings", color = Color.White, fontSize = 13.sp)
                dim("When you add a clip you've used before, it gets its last grade, detail, stabilisation, film look and audio settings.")
            }
            androidx.compose.material3.Switch(MediaCatalog.recall, { MediaCatalog.setRecall(ctx, it) },
                colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Pro.Amber, checkedThumbColor = Color.Black))
        }
    }
    // Slim down: only what can be made again goes; photos / clips, copies and settings stay.
    var slimming by remember { mutableStateOf(false) }
    Text(
        if (slimming) "SLIMMING DOWN…" else "SLIM DOWN “${Catalogs.active(ctx).name}”", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
        modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable(enabled = !slimming) {
            slimming = true
            scope.launch {
                val freed = withContext(Dispatchers.IO) { if (video) MediaCatalog.slimDown(ctx) else PhotoSession.slimDown(ctx) }
                slimming = false; refresh++
                note = "Freed ${sizeText(freed)}"
            }
        }.padding(10.dp),
    )
    dim(if (video) "Removes what can be made again — proxies, analysis, waveforms, render cache, cloud AI results — and keeps every clip's settings and copies. They're made again when needed."
        else "Removes the AI results (they're made again when a photo needs them) and keeps every photo, copy, edit and setting.")

    // Location
    Text("Location of “${Catalogs.active(ctx).name}”", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
    val cur = locs.indexOfFirst { it.second.path == Catalogs.active(ctx).root }
    Chips(locs.map { it.first }, cur) { i ->
        if (i == cur || moving != null) return@Chips
        moving = 0
        val id = Catalogs.activeId
        scope.launch {
            val ok = withContext(Dispatchers.IO) { Catalogs.move(ctx, id, locs[i].second) { p -> scope.launch { moving = p } } }
            moving = null; refresh++
            note = if (ok) "Catalog moved to ${locs[i].first}" else "Couldn't move the catalog there (not enough space, or the storage isn't available)"
        }
    }
    moving?.let { dim("Moving… $it %") }
    dim("The Android/data and SD card folders are removed if the app is uninstalled — use “Keep settings after uninstall” or a backup to keep a copy.")
    note?.let { Text(it, color = Pro.Amber, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp)) }

    if (creating) ProjectNameDialog("Catalog ${Catalogs.list.size + 1}", onSave = { n ->
        val c = Catalogs.create(ctx, n); creating = false; Catalogs.open(ctx, c.id); refresh++
    }, onDismiss = { creating = false }, title = "New catalog", info = "A new, empty catalog. It opens straight away; your other catalogs stay as they are.", fieldLabel = "Catalog name")
    naming?.let { info ->
        ProjectNameDialog(info.name, onSave = { n -> Catalogs.rename(ctx, info.id, n); naming = null; refresh++ }, onDismiss = { naming = null },
            title = "Rename catalog", info = "", fieldLabel = "Catalog name")
    }
    deleting?.let { info ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleting = null }, containerColor = Pro.PanelSolid,
            title = { Text("Delete “${info.name}”?", color = Color.White) },
            text = { Text("Removes the catalog with its photo copies, edits and AI results (${stats[info.id]?.second?.let(::sizeText) ?: "…"}). Your original photos and exported pictures are not touched.", color = Pro.Dim) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { Catalogs.delete(ctx, info.id); deleting = null; refresh++ }) { Text("DELETE", color = Pro.Amber) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { deleting = null }) { Text("CANCEL", color = Color.White) } },
        )
    }
}
