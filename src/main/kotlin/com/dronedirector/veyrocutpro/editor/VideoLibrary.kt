package com.dronedirector.veyrocutpro.editor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.heightIn
import androidx.core.content.ContextCompat
import com.dronedirector.veyrocutpro.camera.GradeIO
import com.dronedirector.veyrocutpro.ui.Pro
import com.dronedirector.veyrocutpro.ui.Text
import com.dronedirector.veyrocutpro.ui.hintedScroll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.mutableStateMapOf
import android.provider.MediaStore

/** A video in the video library grid. */
data class LibVideo(val uri: Uri, val name: String, val date: Long, val w: Int, val h: Int, val size: Long, val durationMs: Long)

/**
 * The video library: every video on the device with stars, flags, colour labels, keywords, albums, smart
 * collections, metadata presets and a calendar — kept in the app's files, the videos are never changed.
 */
object VideoLibrary {
    val meta = mutableStateMapOf<String, LibMeta>()
    val albums = mutableStateListOf<String>()
    val smart = mutableStateListOf<SmartAlbum>()
    private var loaded = false
    private data class Saved(val meta: Map<String, LibMeta>? = null, val albums: List<String>? = null, val smart: List<SmartAlbum>? = null)
    private fun file(c: Context) = java.io.File(c.filesDir, "video_library.json")

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        try {
            val f = file(c); if (!f.exists()) return
            val s: Saved = ProjectStore.gson.fromJson(f.readText(), Saved::class.java) ?: return
            s.meta?.forEach { (k, v) -> meta[k] = v.copy(keywords = v.keywords ?: emptyList(), albums = v.albums ?: emptyList()) }
            s.albums?.let { albums.addAll(it) }
            s.smart?.let { smart.addAll(it) }
        } catch (_: Throwable) {}
    }
    fun save(c: Context) { try { file(c).writeText(ProjectStore.gson.toJson(Saved(meta.toMap(), albums.toList(), smart.toList()))) } catch (_: Throwable) {} }
    fun get(uri: String) = meta[uri] ?: LibMeta()
    fun update(c: Context, uris: Collection<String>, f: (LibMeta) -> LibMeta) { load(c); for (u in uris) meta[u] = f(get(u)); save(c) }

    val TYPES = listOf("Any", "4K and up", "HD and smaller", "Drone", "Vertical")

    /** Every video on the device (needs the video permission), newest first. */
    fun videos(c: Context): List<LibVideo> {
        val out = ArrayList<LibVideo>()
        val col = if (Build.VERSION.SDK_INT >= 29) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT, MediaStore.MediaColumns.SIZE, MediaStore.Video.VideoColumns.DURATION) + (if (Build.VERSION.SDK_INT >= 29) arrayOf(MediaStore.MediaColumns.DATE_TAKEN) else emptyArray())
        try {
            c.contentResolver.query(col, proj, null, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { cur ->
                val iTaken = if (Build.VERSION.SDK_INT >= 29) cur.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN) else -1
                while (cur.moveToNext()) {
                    val taken = if (iTaken >= 0 && !cur.isNull(iTaken)) cur.getLong(iTaken) else cur.getLong(2) * 1000
                    out += LibVideo(android.content.ContentUris.withAppendedId(col, cur.getLong(0)), cur.getString(1) ?: "", taken, cur.getInt(3), cur.getInt(4), cur.getLong(5), cur.getLong(6))
                }
            }
        } catch (_: Throwable) {}
        return out
    }

    fun matches(v: LibVideo, text: String): Boolean {
        if (text.isBlank()) return true
        val m = get(v.uri.toString())
        val hay = (listOf(v.name) + m.keywords + m.albums + listOfNotNull(m.meta?.caption, m.meta?.title)).joinToString(" ").lowercase()
        return text.lowercase().split(' ', ',').filter { it.isNotBlank() }.all { hay.contains(it) }
    }

    fun matchesSmart(v: LibVideo, s: SmartAlbum): Boolean {
        val m = get(v.uri.toString())
        if (m.rating < s.minRating) return false
        if (s.flag == 1 && m.flag != 1) return false
        if (s.flag == 2 && m.flag == -1) return false
        if (s.label != null && m.label != s.label) return false
        s.keyword?.takeIf { it.isNotBlank() }?.let { k -> if (m.keywords.none { it.equals(k.trim(), ignoreCase = true) }) return false }
        smartDateRange(s.dateMode)?.let { r -> if (v.date !in r) return false }
        val long = maxOf(v.w, v.h); val short = minOf(v.w, v.h)
        when (s.fileType) {
            1 -> if (short < 2000 && long < 3800) return false
            2 -> if (long >= 3800 || short >= 2000) return false
            3 -> if (!v.name.startsWith("DJI_", ignoreCase = true)) return false
            4 -> if (v.h <= v.w) return false
        }
        return matches(v, s.text)
    }
}

/** Video library screen. [onAdd]: put the picked videos into the open project (media strip). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoLibraryScreen(onBack: () -> Unit, onAdd: (List<Uri>) -> Unit, onImport: () -> Unit = {}) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { VideoLibrary.load(context) }
    val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var reload by remember { mutableStateOf(0) }
    var watchOpen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            if (withContext(Dispatchers.IO) { try { WatchFolders.check(context) } catch (_: Throwable) { 0 } } > 0) reload++
            kotlinx.coroutines.delay(60_000)
        }
    }
    if (watchOpen) WatchFoldersDialog(onDismiss = { watchOpen = false }) { reload++ }
    val all by produceState(emptyList<LibVideo>(), granted, reload) { value = withContext(Dispatchers.IO) { VideoLibrary.videos(context) } }
    var text by remember { mutableStateOf("") }
    var minRating by remember { mutableStateOf(0) }
    var flagF by remember { mutableStateOf(2) }
    var labelF by remember { mutableStateOf<Int?>(null) }
    var album by remember { mutableStateOf<String?>(null) }
    var smart by remember { mutableStateOf<SmartAlbum?>(null) }
    var dateF by remember { mutableStateOf<LongRange?>(null) }
    var dateLabel by remember { mutableStateOf("") }
    var calendar by remember { mutableStateOf(false) }
    var kwTree by remember { mutableStateOf(false) }
    var kwF by remember { mutableStateOf<String?>(null) }
    // People in the videos (AI, on the phone): a frame every few seconds is looked at.
    var peopleOn by remember { mutableStateOf(false) }
    var personF by remember { mutableStateOf<Int?>(null) }
    var findPeople by remember { mutableStateOf(0) }
    var stopFind by remember { mutableStateOf(false) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { stopFind = true } }
    var selecting by remember { mutableStateOf(false) }
    val picked = remember { mutableStateListOf<String>() }
    var dialog by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<LibVideo?>(null) }
    LaunchedEffect(findPeople) {
        if (findPeople == 0) return@LaunchedEffect
        val err = withContext(Dispatchers.IO) { ModelStore.ensure(context, AiModel.FACES, { p -> Faces.status = "Downloading the face model… $p%" }, { stopFind }) }
        Faces.status = err
        if (err != null) return@LaunchedEffect
        withContext(Dispatchers.Default) { Faces.indexVideos(context, all.map { Triple(it.uri.toString(), it.uri, it.durationMs) }) { stopFind } }
    }
    val personKeys = remember(Faces.version, personF) { personF?.let { Faces.keysOf(it) } }
    val shown = all.filter { v ->
        val m = VideoLibrary.get(v.uri.toString())
        val s = smart
        (s == null || VideoLibrary.matchesSmart(v, s)) && (album == null || album in m.albums) && m.rating >= minRating &&
            when (flagF) { 1 -> m.flag == 1; 2 -> m.flag != -1; 3 -> m.flag == -1; else -> true } &&
            (labelF == null || m.label == labelF) && (dateF == null || v.date in dateF!!) && (kwF == null || Keywords.has(m.keywords, kwF!!)) && (personKeys == null || v.uri.toString() in personKeys) &&
            (VideoLibrary.matches(v, text) || Faces.nameMatches(v.uri.toString(), text))
    }
    Column(Modifier.fillMaxSize().background(Ed.Bg).windowInsetsPadding(WindowInsets.displayCutout).padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TopIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back") { onBack() }
            Text("Video library", color = Color.White, fontSize = 22.sp, modifier = Modifier.padding(start = 6.dp).weight(1f))
            Text("IMPORT", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onImport).padding(8.dp))
            Text(if (selecting) "DONE" else "SELECT", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { selecting = !selecting; picked.clear() }.padding(8.dp))
        }
        if (!granted) Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(10.dp)).background(Pro.Amber.copy(alpha = 0.12f)).clickable { ask.launch(perm) }.padding(12.dp)) {
            Text("Allow access to your videos to see them all here. Tap to allow.", color = Color.White, fontSize = 12.sp)
        }
        OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search: a keyword, a description or a file name…", color = Pro.Dim, fontSize = 13.sp) },
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Pro.Fg, unfocusedTextColor = Pro.Fg, focusedBorderColor = Pro.Amber, unfocusedBorderColor = Pro.Line, cursorColor = Pro.Amber))
        Row(Modifier.fillMaxWidth().hintedScroll(horizontal = true).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            PillChip(if (minRating == 0) "★ Any" else "★ $minRating+", minRating > 0) { minRating = (minRating + 1) % 6 }
            PillChip("Picks", flagF == 1) { flagF = if (flagF == 1) 2 else 1 }
            PillChip("Rejected", flagF == 3) { flagF = if (flagF == 3) 2 else 3 }
            PillChip("Show rejects", flagF == 0) { flagF = if (flagF == 0) 2 else 0 }
            LabelPicker(labelF, allowNone = false) { labelF = it }
        }
        Row(Modifier.fillMaxWidth().hintedScroll(horizontal = true).padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PillChip("All videos", album == null && smart == null) { album = null; smart = null }
            VideoLibrary.albums.forEach { a -> PillChip("▣ $a", album == a, onLongClick = { VideoLibrary.albums.remove(a); VideoLibrary.save(context); if (album == a) album = null }) { album = a; smart = null } }
            VideoLibrary.smart.forEach { s -> PillChip("⚙ ${s.name}", smart == s, onLongClick = { VideoLibrary.smart.remove(s); VideoLibrary.save(context); if (smart == s) smart = null }) { smart = s; album = null } }
            PillChip("+ Album", false, accentText = true) { dialog = "newAlbum" }
            PillChip("+ Smart collection", false, accentText = true) { dialog = "newSmart" }
            PillChip("Watched folders…", false, accentText = true) { watchOpen = true }
        }
        Row(Modifier.fillMaxWidth().hintedScroll(horizontal = true).padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PillChip("Grid", !calendar && !kwTree) { calendar = false; kwTree = false }
            PillChip("Calendar", calendar) { calendar = true; kwTree = false }
            PillChip("Keywords", kwTree) { kwTree = true; calendar = false }
            PillChip("People", peopleOn) { peopleOn = !peopleOn }
            if (dateF != null) PillChip("📅 $dateLabel ✕", true) { dateF = null }
            kwF?.let { k -> PillChip("🏷 ${Keywords.show(k)} ✕", true) { kwF = null } }
        }
        if (peopleOn) PeopleRow(all.map { it.uri.toString() }.toSet(), personF, { personF = it }, { k -> Uri.parse(k) }, video = true) { findPeople++ }
        val sel = shown.filter { it.uri.toString() in picked }
        if (selecting) Row(Modifier.fillMaxWidth().hintedScroll(horizontal = true).padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${picked.size} selected", style = Pro.Label.copy(color = Color.White, fontSize = 11.sp))
            PillChip("Select all", false) { picked.clear(); picked.addAll(shown.map { it.uri.toString() }) }
            if (picked.isNotEmpty()) {
                PillChip("+ Add to project", false, accentText = true) { onAdd(sel.map { it.uri }) }
                (1..5).forEach { r -> PillChip("★$r", false) { VideoLibrary.update(context, picked) { it.copy(rating = r) } } }
                PillChip("⚑ Pick", false) { VideoLibrary.update(context, picked) { it.copy(flag = 1) } }
                PillChip("✕ Reject", false) { VideoLibrary.update(context, picked) { it.copy(flag = -1) } }
                PillChip("Unflag", false) { VideoLibrary.update(context, picked) { it.copy(flag = 0) } }
                LabelPicker(null) { c -> VideoLibrary.update(context, picked) { it.copy(label = c) } }
                PillChip("+ Album", false) { dialog = "album" }
                PillChip("Metadata preset", false) { dialog = "meta" }
                PillChip("Keywords", false) { dialog = "keywords" }
            }
        }
        Text("${shown.size} video" + (if (shown.size == 1) "" else "s"), style = Pro.Label.copy(color = Pro.Dim, fontSize = 10.sp), modifier = Modifier.padding(bottom = 4.dp))
        if (kwTree) Box(Modifier.weight(1f).fillMaxWidth()) { KeywordTree(Keywords.tree(all.map { VideoLibrary.get(it.uri.toString()).keywords })) { k -> kwF = k; kwTree = false } }
        else if (calendar) Box(Modifier.weight(1f).fillMaxWidth()) { CalendarBrowser(shown.map { it.date }) { r, l -> dateF = r; dateLabel = l; calendar = false } }
        else LazyVerticalGrid(GridCells.Adaptive(150.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            items(shown, key = { it.uri.toString() }) { v ->
                val k = v.uri.toString()
                val m = VideoLibrary.get(k)
                val on = k in picked
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(6.dp)).background(Color(0xFF15181F))
                    .border(2.dp, if (on) Pro.Amber else Color.Transparent, RoundedCornerShape(6.dp))
                    .clickable { if (selecting) { if (on) picked.remove(k) else picked.add(k) } else detail = v }) {
                    val img by produceState<Bitmap?>(VideoThumbs.cached(v.uri, "c1_240"), v.uri) { if (value == null) value = withContext(VideoThumbs.io) { VideoThumbs.cover(context, v.uri, minOf(1000L, v.durationMs / 3), 240) } }
                    img?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    if (m.flag == -1) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
                    Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        m.label?.let { Box(Modifier.size(9.dp).clip(CircleShape).background(Color(it))) }
                        if (m.rating > 0) Text("★".repeat(m.rating), color = Pro.Amber, fontSize = 9.sp, modifier = Modifier.padding(start = 3.dp))
                        if (m.flag == 1) Text(" ⚑", color = Color.White, fontSize = 9.sp)
                        Box(Modifier.weight(1f))
                        Text(tc(v.durationMs), color = Color.White, fontSize = 9.sp)
                    }
                }
            }
        }
    }
    when (dialog) {
        "newAlbum", "album" -> {
            var t by remember { mutableStateOf("") }
            AlertDialog(onDismissRequest = { dialog = null }, containerColor = Pro.PanelSolid,
                title = { Text(if (dialog == "album") "Add to album" else "New album", color = Color.White) },
                text = {
                    Column {
                        if (dialog == "album") VideoLibrary.albums.forEach { a -> Text("▣ $a", color = Color.White, modifier = Modifier.fillMaxWidth().clickable { VideoLibrary.update(context, picked) { it.copy(albums = (it.albums + a).distinct()) }; dialog = null }.padding(8.dp)) }
                        OutlinedTextField(t, { t = it }, label = { Text("New album name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Pro.Fg, unfocusedTextColor = Pro.Fg, focusedBorderColor = Pro.Amber, focusedLabelColor = Pro.Amber, cursorColor = Pro.Amber))
                    }
                },
                confirmButton = { Text("OK", style = Pro.Label.copy(color = Pro.Amber, fontSize = 12.sp), modifier = Modifier.clickable {
                    val n = t.trim()
                    if (n.isNotEmpty()) { if (n !in VideoLibrary.albums) VideoLibrary.albums += n; if (dialog == "album") VideoLibrary.update(context, picked) { it.copy(albums = (it.albums + n).distinct()) } else VideoLibrary.save(context) }
                    dialog = null
                }.padding(10.dp)) },
                dismissButton = { Text("CANCEL", style = Pro.Label.copy(color = Pro.Dim, fontSize = 12.sp), modifier = Modifier.clickable { dialog = null }.padding(10.dp)) })
        }
        "newSmart" -> SmartCollectionDialog(SmartAlbum("", minRating, if (flagF == 1) 1 else if (flagF == 2) 2 else 0, labelF, text), VideoLibrary.TYPES, video = true) { s ->
            dialog = null
            if (s != null) { VideoLibrary.smart.add(s); VideoLibrary.save(context); smart = s; album = null }
        }
        "keywords" -> KeywordDialog(picked.size, Keywords.suggest(emptyList(), all.map { VideoLibrary.get(it.uri.toString()).keywords }, picked.flatMap { VideoLibrary.get(it).keywords }.toSet())) { kws ->
            dialog = null
            if (!kws.isNullOrEmpty()) VideoLibrary.update(context, picked.toList()) { it.copy(keywords = (it.keywords + kws).distinct()) }
        }
        "meta" -> MetaPresetDialog(picked.size, onApply = { p ->
            dialog = null
            VideoLibrary.update(context, picked.toList()) { it.copy(meta = p.asEdit(), keywords = (it.keywords + p.keywordList).distinct()) }
            android.widget.Toast.makeText(context, com.dronedirector.veyrocutpro.ui.I18n.tr("\"${p.name}\" applied to ${picked.size} video(s)"), android.widget.Toast.LENGTH_LONG).show()
        }, onDismiss = { dialog = null })
    }
    detail?.let { v -> VideoDetail(v, onClose = { detail = null }, onAdd = { detail = null; onAdd(listOf(v.uri)) }) }
}

/** One video: its stars, flag, colour label, keywords and albums, and adding it to the project. */
@Composable
private fun VideoDetail(v: LibVideo, onClose: () -> Unit, onAdd: () -> Unit) {
    val context = LocalContext.current
    val k = v.uri.toString()
    val m = VideoLibrary.get(k)
    var kw by remember(k) { mutableStateOf(m.keywords.joinToString(", ")) }
    AlertDialog(onDismissRequest = onClose, containerColor = Pro.PanelSolid,
        title = { Text(v.name, color = Color.White, fontSize = 15.sp, maxLines = 1) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("${v.w}×${v.h} · ${tc(v.durationMs)} · " + java.text.SimpleDateFormat("d MMM yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(v.date)), color = Pro.Dim, fontSize = 12.sp)
                Row(Modifier.hintedScroll(horizontal = true).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (0..5).forEach { r -> PillChip(if (r == 0) "☆" else "★$r", m.rating == r) { VideoLibrary.update(context, listOf(k)) { it.copy(rating = r) } } }
                    PillChip("⚑ Pick", m.flag == 1) { VideoLibrary.update(context, listOf(k)) { it.copy(flag = if (it.flag == 1) 0 else 1) } }
                    PillChip("✕ Reject", m.flag == -1) { VideoLibrary.update(context, listOf(k)) { it.copy(flag = if (it.flag == -1) 0 else -1) } }
                }
                LabelPicker(m.label) { c -> VideoLibrary.update(context, listOf(k)) { it.copy(label = c) } }
                OutlinedTextField(kw, { kw = it; VideoLibrary.update(context, listOf(k)) { mm -> mm.copy(keywords = it.split(',').map { s -> s.trim().lowercase() }.filter { s -> s.isNotEmpty() }) } },
                    label = { Text("Keywords (comma separated)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Pro.Fg, unfocusedTextColor = Pro.Fg, focusedBorderColor = Pro.Amber, focusedLabelColor = Pro.Amber, cursorColor = Pro.Amber))
                if (VideoLibrary.albums.isNotEmpty()) Row(Modifier.hintedScroll(horizontal = true).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    VideoLibrary.albums.forEach { a -> PillChip("▣ $a", a in m.albums) { VideoLibrary.update(context, listOf(k)) { it.copy(albums = if (a in it.albums) it.albums - a else it.albums + a) } } }
                }
                m.meta?.let { mm -> Text(listOf(mm.artist, mm.copyright, mm.caption).filter { it.isNotBlank() }.joinToString(" · "), color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp)) }
            }
        },
        confirmButton = { Text("+ ADD TO PROJECT", style = Pro.Label.copy(color = Pro.Amber, fontSize = 12.sp), modifier = Modifier.clickable(onClick = onAdd).padding(10.dp)) },
        dismissButton = { Text("CLOSE", style = Pro.Label.copy(color = Pro.Dim, fontSize = 12.sp), modifier = Modifier.clickable(onClick = onClose).padding(10.dp)) })
}
