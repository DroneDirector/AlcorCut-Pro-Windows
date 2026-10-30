package com.dronedirector.veyrocutpro.editor

import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.ui.Pro
import com.dronedirector.veyrocutpro.ui.Text
import com.dronedirector.veyrocutpro.ui.hintedScroll
import java.io.File
import java.util.Calendar

/** The five colour labels (red, yellow, green, blue, purple), shared by the photo and video libraries. */
val COLOUR_LABELS = listOf(0xFFFF3B30, 0xFFFFD60A, 0xFF34C759, 0xFF0A84FF, 0xFFBF5AF2).map { it.toInt() }
val COLOUR_LABEL_NAMES = listOf("Red", "Yellow", "Green", "Blue", "Purple")

/** Colour label circles: tap one to pick it, the empty ring for none. */
@Composable
fun LabelPicker(selected: Int?, allowNone: Boolean = true, onPick: (Int?) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (allowNone) Box(Modifier.size(22.dp).clip(CircleShape).border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape).clickable { onPick(null) })
        COLOUR_LABELS.forEach { c ->
            Box(Modifier.size(22.dp).clip(CircleShape).background(Color(c)).border(2.dp, if (selected == c) Color.White else Color.Transparent, CircleShape).clickable { onPick(if (selected == c) null else c) })
        }
    }
}

// ---------------- Metadata presets

/** A saved set of your details, written into photos and videos (and their library entries) in one go. */
data class MetaPreset(
    val name: String,
    val artist: String = "",
    val copyright: String = "",
    val contact: String = "",
    val caption: String = "",
    val keywords: String = "",
) {
    fun asEdit(title: String = ""): PhotoMetaEdit = PhotoMetaEdit(title = title, caption = listOf(caption, contact).filter { it.isNotBlank() }.joinToString(" · "),
        artist = artist, copyright = copyright, keywords = keywords)
    val keywordList: List<String> get() = keywords.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
}

object MetaPresets {
    val list = mutableStateListOf<MetaPreset>()
    private var loaded = false
    private fun file(c: Context) = File(c.filesDir, "meta_presets.json")

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        try {
            val t = object : com.google.gson.reflect.TypeToken<List<MetaPreset>>() {}.type
            file(c).takeIf { it.exists() }?.let { f -> (ProjectStore.gson.fromJson<List<MetaPreset>>(f.readText(), t) ?: emptyList()).forEach { list += it.copy(
                artist = it.artist ?: "", copyright = it.copyright ?: "", contact = it.contact ?: "", caption = it.caption ?: "", keywords = it.keywords ?: "") } }
        } catch (_: Throwable) {}
    }

    fun save(c: Context) { try { file(c).writeText(ProjectStore.gson.toJson(list.toList())) } catch (_: Throwable) {} }

    fun put(c: Context, p: MetaPreset) { val i = list.indexOfFirst { it.name == p.name }; if (i >= 0) list[i] = p else list += p; save(c) }
    fun remove(c: Context, p: MetaPreset) { list.remove(p); save(c) }
}

/** Pick a metadata preset (or make / edit one) and apply it. */
@Composable
fun MetaPresetDialog(count: Int, onApply: (MetaPreset) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    MetaPresets.load(context)
    var editing by remember { mutableStateOf<MetaPreset?>(null) }
    val e = editing
    if (e != null) {
        var name by remember(e) { mutableStateOf(e.name) }
        var artist by remember(e) { mutableStateOf(e.artist) }
        var copy by remember(e) { mutableStateOf(e.copyright) }
        var contact by remember(e) { mutableStateOf(e.contact) }
        var caption by remember(e) { mutableStateOf(e.caption) }
        var kw by remember(e) { mutableStateOf(e.keywords) }
        AlertDialog(
            onDismissRequest = { editing = null }, containerColor = Pro.PanelSolid,
            title = { Text("Metadata preset", color = Color.White) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    @Composable fun f(v: String, label: String, set: (String) -> Unit) = OutlinedTextField(v, set, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Pro.Fg, unfocusedTextColor = Pro.Fg, focusedBorderColor = Pro.Amber, focusedLabelColor = Pro.Amber, cursorColor = Pro.Amber))
                    f(name, "Preset name") { name = it }
                    f(artist, "Author / creator") { artist = it }
                    f(copy, "Copyright (e.g. © 2026 Your Name)") { copy = it }
                    f(contact, "Contact (web, e-mail)") { contact = it }
                    f(caption, "Description") { caption = it }
                    f(kw, "Keywords (comma between)") { kw = it }
                }
            },
            confirmButton = { Text("SAVE", style = Pro.Label.copy(color = Pro.Amber, fontSize = 12.sp), modifier = Modifier.clickable {
                if (name.isNotBlank()) { if (e.name.isNotBlank() && e.name != name.trim()) MetaPresets.remove(context, e); MetaPresets.put(context, MetaPreset(name.trim(), artist.trim(), copy.trim(), contact.trim(), caption.trim(), kw.trim())) }
                editing = null
            }.padding(10.dp)) },
            dismissButton = { Text("CANCEL", style = Pro.Label.copy(color = Pro.Dim, fontSize = 12.sp), modifier = Modifier.clickable { editing = null }.padding(10.dp)) },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Pro.PanelSolid,
        title = { Text("Metadata presets", color = Color.White) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Your details (author, copyright, contact, description, keywords) for $count item(s). Tap a preset to apply it; hold it to edit.", color = Pro.Dim, fontSize = 12.sp)
                MetaPresets.list.forEach { p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised)
                        .clickable { onApply(p) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, color = Color.White, fontSize = 14.sp)
                            Text(listOf(p.artist, p.copyright, p.keywords).filter { it.isNotBlank() }.joinToString(" · ").take(80), color = Pro.Dim, fontSize = 11.sp)
                        }
                        Text("EDIT", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp), modifier = Modifier.clickable { editing = p }.padding(6.dp))
                        Text("✕", color = Pro.Red, fontSize = 13.sp, modifier = Modifier.clickable { MetaPresets.remove(context, p) }.padding(6.dp))
                    }
                }
                Text("+ NEW PRESET", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(top = 8.dp).clickable { editing = MetaPreset("") }.padding(6.dp))
            }
        },
        confirmButton = { Text("CLOSE", style = Pro.Label.copy(color = Pro.Dim, fontSize = 12.sp), modifier = Modifier.clickable(onClick = onDismiss).padding(10.dp)) },
    )
}

// ---------------- Calendar

/** A day range: the start of the first day to the end of the last. */
fun dayRange(y: Int, m: Int?, d: Int?): LongRange {
    val c = Calendar.getInstance(); c.clear()
    c.set(y, m ?: 0, d ?: 1)
    val a = c.timeInMillis
    when { d != null -> c.add(Calendar.DAY_OF_MONTH, 1); m != null -> c.add(Calendar.MONTH, 1); else -> c.add(Calendar.YEAR, 1) }
    return a until c.timeInMillis
}

/**
 * Calendar view: years with their counts → a year's months → a month's days (a real calendar). Picking one
 * filters the library to that year, month or day.
 */
@Composable
fun CalendarBrowser(dates: List<Long>, onPick: (LongRange, String) -> Unit) {
    var year by remember { mutableStateOf<Int?>(null) }
    var month by remember { mutableStateOf<Int?>(null) }
    val cal = remember { Calendar.getInstance() }
    data class Ymd(val y: Int, val m: Int, val d: Int)
    val ymd = remember(dates) { dates.map { cal.timeInMillis = it; Ymd(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)) } }
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
            PillChip("All years", year == null) { year = null; month = null }
            year?.let { y -> Text(" › ", color = Pro.Dim); PillChip("$y", month == null) { month = null } }
            month?.let { m -> Text(" › ", color = Pro.Dim); PillChip(months[m], true) {} }
        }
        @Composable fun cell(label: String, n: Int, mod: Modifier, on: Boolean = false, click: () -> Unit) {
            Column(mod.height(58.dp).clip(RoundedCornerShape(8.dp)).background(if (n > 0) (if (on) Pro.Amber.copy(alpha = 0.25f) else Ed.Raised) else Ed.Raised.copy(alpha = 0.35f))
                .clickable(enabled = n > 0, onClick = click).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, color = if (n > 0) Color.White else Pro.Dim, fontSize = 14.sp)
                if (n > 0) Text("$n", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp))
            }
        }
        val y = year; val m = month
        when {
            y == null -> {
                val byYear = ymd.groupingBy { it.y }.eachCount().toSortedMap(compareByDescending { it })
                if (byYear.isEmpty()) Text("Nothing here yet.", color = Pro.Dim, fontSize = 12.sp)
                byYear.entries.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { (yy, n) -> cell("$yy", n, Modifier.weight(1f)) { year = yy } }
                        repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                    }
                }
            }
            m == null -> {
                val byMonth = ymd.filter { it.y == y }.groupingBy { it.m }.eachCount()
                Text("SHOW ALL OF $y", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.clickable { onPick(dayRange(y, null, null), "$y") }.padding(6.dp))
                (0 until 12).chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { mm -> cell(months[mm], byMonth[mm] ?: 0, Modifier.weight(1f)) { month = mm } }
                    }
                }
            }
            else -> {
                val byDay = ymd.filter { it.y == y && it.m == m }.groupingBy { it.d }.eachCount()
                Text("SHOW ALL OF ${months[m].uppercase()} $y", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.clickable { onPick(dayRange(y, m, null), "${months[m]} $y") }.padding(6.dp))
                val c = Calendar.getInstance(); c.clear(); c.set(y, m, 1)
                val first = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7   // Monday first
                val days = c.getActualMaximum(Calendar.DAY_OF_MONTH)
                Row(Modifier.fillMaxWidth()) { listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su").forEach { Text(it, color = Pro.Dim, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) } }
                val cells = List(first) { 0 } + (1..days).toList()
                cells.chunked(7).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { d -> if (d == 0) Box(Modifier.weight(1f)) else cell("$d", byDay[d] ?: 0, Modifier.weight(1f)) { onPick(dayRange(y, m, d), "$d ${months[m]} $y") } }
                        repeat(7 - row.size) { Box(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

// ---------------- Smart collections

/** How the date rule of a smart collection reads. */
val SMART_DATES = listOf("Any time", "Today", "Last 7 days", "Last 30 days", "This year", "Last year")

/** The date range of rule [mode] (null = any time); worked out now, so the collection keeps itself up to date. */
fun smartDateRange(mode: Int?): LongRange? {
    val now = System.currentTimeMillis()
    val c = Calendar.getInstance()
    return when (mode) {
        1 -> { c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0); c.timeInMillis..now }
        2 -> (now - 7L * 86_400_000)..now
        3 -> (now - 30L * 86_400_000)..now
        4 -> dayRange(c.get(Calendar.YEAR), null, null)
        5 -> dayRange(c.get(Calendar.YEAR) - 1, null, null)
        else -> null
    }
}

/**
 * Smart collection editor: every rule at once (stars, flag, colour label, keyword, search words, date,
 * file type, location), starting from the filters set now.
 */
@Composable
fun SmartCollectionDialog(start: SmartAlbum, fileTypes: List<String>, video: Boolean, onDone: (SmartAlbum?) -> Unit) {
    var s by remember { mutableStateOf(start) }
    AlertDialog(
        onDismissRequest = { onDone(null) }, containerColor = Pro.PanelSolid,
        title = { Text("Smart collection", color = Color.White) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Shows everything that matches all the rules — and keeps itself up to date.", color = Pro.Dim, fontSize = 12.sp)
                val tf = OutlinedTextFieldDefaults.colors(focusedTextColor = Pro.Fg, unfocusedTextColor = Pro.Fg, focusedBorderColor = Pro.Amber, focusedLabelColor = Pro.Amber, cursorColor = Pro.Amber)
                OutlinedTextField(s.name, { s = s.copy(name = it) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = tf)
                Text("Stars", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.hintedScroll(horizontal = true), horizontalArrangement = Arrangement.spacedBy(6.dp)) { (0..5).forEach { r -> PillChip(if (r == 0) "Any" else "★$r+", s.minRating == r) { s = s.copy(minRating = r) } } }
                Text("Flag", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Any", "Picks only", "Not rejected").forEachIndexed { i, n -> PillChip(n, s.flag == i) { s = s.copy(flag = i) } } }
                Text("Colour label", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                LabelPicker(s.label) { s = s.copy(label = it) }
                Text("Date", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.hintedScroll(horizontal = true), horizontalArrangement = Arrangement.spacedBy(6.dp)) { SMART_DATES.forEachIndexed { i, n -> PillChip(n, (s.dateMode ?: 0) == i) { s = s.copy(dateMode = i.takeIf { it > 0 }) } } }
                Text("Type", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.hintedScroll(horizontal = true), horizontalArrangement = Arrangement.spacedBy(6.dp)) { fileTypes.forEachIndexed { i, n -> PillChip(n, (s.fileType ?: 0) == i) { s = s.copy(fileType = i.takeIf { it > 0 }) } } }
                if (!video) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                    PillChip("Any place", s.hasPlace == null) { s = s.copy(hasPlace = null) }
                    PillChip("With location", s.hasPlace == true) { s = s.copy(hasPlace = true) }
                    PillChip("No location", s.hasPlace == false) { s = s.copy(hasPlace = false) }
                }
                OutlinedTextField(s.keyword ?: "", { s = s.copy(keyword = it.takeIf { v -> v.isNotBlank() }) }, label = { Text("Keyword") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = tf)
                OutlinedTextField(s.text, { s = s.copy(text = it) }, label = { Text("Search words (name, keywords, what the AI sees)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = tf)
            }
        },
        confirmButton = { Text("SAVE", style = Pro.Label.copy(color = Pro.Amber, fontSize = 12.sp), modifier = Modifier.clickable { if (s.name.isNotBlank()) onDone(s.copy(name = s.name.trim())) }.padding(10.dp)) },
        dismissButton = { Text("CANCEL", style = Pro.Label.copy(color = Pro.Dim, fontSize = 12.sp), modifier = Modifier.clickable { onDone(null) }.padding(10.dp)) },
    )
}

// ---------------- Keyword tree and suggestions

/**
 * Keywords can be nested: "Places › Faroe Islands › Tórshavn" (type it with › , > or /). They are kept as
 * "places/faroe islands/tórshavn"; a photo with a nested keyword is found under every level of it.
 */
object Keywords {
    fun norm(k: String): String = k.split('/', '>', '›').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.joinToString("/")
    fun show(k: String): String = k.split('/').joinToString(" › ") { w -> w.replaceFirstChar { it.uppercase() } }
    /** Does a keyword list have [path] (or anything inside it)? */
    fun has(list: List<String>, path: String): Boolean = list.any { it == path || it.startsWith("$path/") }
    /** Typed text → keywords (comma separated, each can be nested). */
    fun parse(t: String): List<String> = t.split(',', ';', '\n').map { norm(it) }.filter { it.isNotEmpty() }

    class Node(val name: String, val path: String, val count: Int, val kids: List<Node>)

    /** The tree of all keywords with how many items have each (counting what's inside). */
    fun tree(all: List<List<String>>): List<Node> {
        val counts = HashMap<String, Int>()
        for (list in all) {
            val paths = HashSet<String>()
            for (k in list) { val parts = k.split('/'); for (i in 1..parts.size) paths += parts.take(i).joinToString("/") }
            paths.forEach { counts[it] = (counts[it] ?: 0) + 1 }
        }
        fun build(prefix: String, depth: Int): List<Node> = counts.keys.filter { it.count { c -> c == '/' } == depth && (prefix.isEmpty() || it.startsWith("$prefix/")) }
            .sorted().map { p -> Node(p.substringAfterLast('/'), p, counts[p] ?: 0, build(p, depth + 1)) }
        return build("", 0)
    }

    /** Suggestions for [items]: what the AI saw in them (most common first), then keywords you use a lot. */
    fun suggest(ai: List<List<String>>, used: List<List<String>>, have: Set<String>): List<String> {
        val seen = ai.flatten().groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
        val often = used.flatten().groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
        return (seen + often).map { norm(it) }.filter { it.isNotEmpty() && it !in have }.distinct().take(24)
    }
}

/** Adding keywords: a field (comma separated, › for nesting) and tap-to-add suggestions. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun KeywordDialog(count: Int, suggestions: List<String>, onDone: (List<String>?) -> Unit) {
    var t by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { onDone(null) }, containerColor = Ed.Panel,
        title = { Text("Keywords for $count item(s)", color = Pro.Fg) },
        text = {
            Column {
                Text("Separate keywords with commas. Nest them with › (or > or /), e.g. Places › Faroe Islands › Tórshavn.", color = Pro.Dim, fontSize = 12.sp)
                OutlinedTextField(t, { t = it }, singleLine = false, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Pro.Fg, unfocusedTextColor = Pro.Fg, focusedBorderColor = Pro.Amber, cursorColor = Pro.Amber))
                if (suggestions.isNotEmpty()) {
                    Text("Suggestions (tap to add)", color = Pro.Fg, fontSize = 13.sp)
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                        suggestions.forEach { s -> PillChip(Keywords.show(s), false) { t = if (t.isBlank()) Keywords.show(s) else t.trimEnd().trimEnd(',') + ", " + Keywords.show(s) } }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(Keywords.parse(t)) }) { Text("ADD", color = Pro.Amber) } },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("CANCEL", color = Pro.Dim) } },
    )
}

/** The keyword tree: tap a keyword to see everything with it (or inside it); ▸ opens a level. */
@Composable
fun KeywordTree(nodes: List<Keywords.Node>, onPick: (String) -> Unit) {
    val open = remember { mutableStateListOf<String>() }
    if (nodes.isEmpty()) { Text("No keywords yet. Select items and tap Keywords to add some.", color = Pro.Dim, fontSize = 12.sp, modifier = Modifier.padding(8.dp)); return }
    androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth()) {
        fun add(list: List<Keywords.Node>, depth: Int, scope: androidx.compose.foundation.lazy.LazyListScope) {
            for (n in list) {
                scope.item(key = n.path) {
                    Row(Modifier.fillMaxWidth().padding(start = (depth * 18).dp).clip(RoundedCornerShape(6.dp)).clickable { onPick(n.path) }.padding(vertical = 8.dp, horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (n.kids.isEmpty()) "•" else if (n.path in open) "▾" else "▸", color = Pro.Amber, fontSize = 15.sp,
                            modifier = Modifier.width(24.dp).clickable(enabled = n.kids.isNotEmpty()) { if (n.path in open) open.remove(n.path) else open.add(n.path) })
                        Text(n.name.replaceFirstChar { it.uppercase() }, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text("${n.count}", color = Pro.Dim, fontSize = 12.sp)
                    }
                }
                if (n.path in open) add(n.kids, depth + 1, scope)
            }
        }
        add(nodes, 0, this)
    }
}
