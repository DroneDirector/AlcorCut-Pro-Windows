package com.dronedirector.veyrocutpro.editor

import android.Manifest
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

private val LIB_LABELS = listOf(0xFFFF3B30, 0xFFFFD60A, 0xFF34C759, 0xFF0A84FF, 0xFFBF5AF2).map { it.toInt() }

/**
 * The photo library: every photo on the phone with ratings, flags, colour labels, keywords,
 * albums and smart albums, a search that also finds what's IN the photos (AI labels), info
 * (camera details), compare (two side by side) and survey (several at once), and editing many
 * photos together in the filmstrip.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(onBack: () -> Unit, onEdit: (List<Uri>) -> Unit, onVirtualCopy: (Uri) -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { LibraryStore.load(context) }
    val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var reload by remember { mutableStateOf(0) }
    val all by produceState(emptyList<LibPhoto>(), granted, reload) { value = withContext(Dispatchers.IO) { LibraryStore.photos(context) } }
    // AI labels in the background so photos can be found by what's in them.
    var stopIndex by remember { mutableStateOf(false) }
    LaunchedEffect(all) { if (all.isNotEmpty()) withContext(Dispatchers.Default) { LibraryStore.index(context, all) { stopIndex } } }
    DisposableEffect(Unit) { onDispose { stopIndex = true } }

    var text by remember { mutableStateOf("") }
    var minRating by remember { mutableStateOf(0) }
    var flagF by remember { mutableStateOf(2) }      // 0 any, 1 picks, 2 hide rejects, 3 rejects only
    var labelF by remember { mutableStateOf<Int?>(null) }
    var album by remember { mutableStateOf<String?>(null) }
    var smart by remember { mutableStateOf<SmartAlbum?>(null) }
    var selecting by remember { mutableStateOf(false) }
    val picked = remember { mutableStateListOf<String>() }
    var detail by remember { mutableStateOf<LibPhoto?>(null) }
    var compare by remember { mutableStateOf<List<LibPhoto>?>(null) }
    var survey by remember { mutableStateOf<List<LibPhoto>?>(null) }
    var dialog by remember { mutableStateOf<String?>(null) }   // "album", "keywords", "newAlbum", "newSmart"
    // Views: 0 grid, 1 stacks (bursts together), 2 people, 3 map.
    var view by remember { mutableStateOf(0) }
    var peopleF by remember { mutableStateOf(-1) }   // -1 all, 0 no people, 1 one person, 2 two, 3 groups
    var mapPick by remember { mutableStateOf<Set<String>?>(null) }
    val expanded = remember { mutableStateListOf<String>() }
    val locPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { reload++ }
    LaunchedEffect(view, all) {
        if (all.isEmpty()) return@LaunchedEffect
        if (view == 2) withContext(Dispatchers.Default) { LibraryStore.indexFaces(context, all) { stopIndex } }
        if (view == 3) {
            if (Build.VERSION.SDK_INT >= 29 && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                try { locPerm.launch(Manifest.permission.ACCESS_MEDIA_LOCATION) } catch (_: Throwable) {}
            }
            withContext(Dispatchers.IO) { LibraryStore.indexPlaces(context, all) { stopIndex } }
        }
    }

    val shown = all.filter { p ->
        val m = LibraryStore.get(p.uri.toString())
        val s = smart
        (s == null || LibraryStore.matchesSmart(p, s)) &&
            (album == null || album in m.albums) &&
            m.rating >= minRating &&
            when (flagF) { 1 -> m.flag == 1; 2 -> m.flag != -1; 3 -> m.flag == -1; else -> true } &&
            (labelF == null || m.label == labelF) &&
            (view != 2 || peopleF == -1 || when (peopleF) { 0 -> m.faces == 0; 1 -> m.faces == 1; 2 -> m.faces == 2; else -> (m.faces ?: 0) >= 3 }) &&
            (mapPick == null || p.uri.toString() in mapPick!!) &&
            LibraryStore.matches(p, text)
    }
    // Stacks: bursts shown as one photo with a count, until tapped open.
    val gridList: List<Pair<LibPhoto, Int>> = if (view != 1) shown.map { it to 0 } else LibraryStore.stacks(shown).flatMap { g ->
        if (g.size == 1) listOf(g[0] to 0)
        else if (g[0].uri.toString() in expanded) g.mapIndexed { i, p -> p to (if (i == 0) -g.size else -1) }
        else listOf(g[0] to g.size)
    }
    val sel = all.filter { it.uri.toString() in picked }

    Column(Modifier.fillMaxSize().background(Ed.Bg).windowInsetsPadding(WindowInsets.displayCutout).padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TopIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back") { onBack() }
            Text("Library", color = Color.White, fontSize = 22.sp, modifier = Modifier.padding(start = 6.dp).weight(1f))
            LibraryStore.indexing?.let { (d, t) -> Text("AI ${d}/${t}", style = Pro.Label.copy(color = Pro.Dim, fontSize = 10.sp), modifier = Modifier.padding(end = 8.dp)) }
            Text(if (selecting) "DONE" else "SELECT", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { selecting = !selecting; picked.clear() }.padding(8.dp))
        }
        if (!granted) {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(10.dp)).background(Pro.Amber.copy(alpha = 0.12f)).clickable { ask.launch(perm) }.padding(12.dp)) {
                Text("Allow access to your photos to see the whole library (now only AlcorCut Pro's own photos are shown). Tap to allow.", color = Color.White, fontSize = 12.sp)
            }
        }
        OutlinedTextField(
            value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search: dog, beach, sunset, a keyword or a file name…", color = Pro.Dim, fontSize = 13.sp) },
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Pro.Amber, unfocusedBorderColor = Pro.Line, cursorColor = Pro.Amber),
        )
        // Filters.
        Row(Modifier.fillMaxWidth().hintedScroll(horizontal = true).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            PillChip(if (minRating == 0) "★ Any" else "★ $minRating+", minRating > 0) { minRating = (minRating + 1) % 6 }
            PillChip("Picks", flagF == 1) { flagF = if (flagF == 1) 2 else 1 }
            PillChip("Rejected", flagF == 3) { flagF = if (flagF == 3) 2 else 3 }
            PillChip("Show rejects", flagF == 0) { flagF = if (flagF == 0) 2 else 0 }
            LIB_LABELS.forEach { c -> Box(Modifier.size(22.dp).clip(CircleShape).background(Color(c)).border(2.dp, if (labelF == c) Color.White else Color.Transparent, CircleShape).clickable { labelF = if (labelF == c) null else c }) }
        }
        Row(Modifier.fillMaxWidth().hintedScroll(horizontal = true).padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PillChip("All photos", album == null && smart == null) { album = null; smart = null }
            LibraryStore.albums.forEach { a -> PillChip("▣ $a", album == a, onLongClick = { LibraryStore.albums.remove(a); LibraryStore.save(context); if (album == a) album = null }) { album = a; smart = null } }
            LibraryStore.smart.forEach { s -> PillChip("⚙ ${s.name}", smart == s, onLongClick = { LibraryStore.smart.remove(s); LibraryStore.save(context); if (smart == s) smart = null }) { smart = s; album = null } }
            PillChip("+ Album", false, accentText = true) { dialog = "newAlbum" }
            PillChip("+ Smart album", false, accentText = true) { dialog = "newSmart" }
        }
        Row(Modifier.fillMaxWidth().hintedScroll(horizontal = true).padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("Grid", "Stacks", "People", "Map").forEachIndexed { i, n -> PillChip(n, view == i) { view = i; if (i != 3) mapPick = null } }
            if (view == 2) listOf("Everyone", "No people", "1 person", "2 people", "Groups").forEachIndexed { i, n -> PillChip(n, peopleF == i - 1) { peopleF = i - 1 } }
            if (mapPick != null) PillChip("📍 ${mapPick!!.size} here ✕", true) { mapPick = null }
            LibraryStore.scanning?.let { (d, t) -> Text(if (view == 3) "Reading locations $d / $t" else "Finding people $d / $t", style = Pro.Label.copy(color = Pro.Dim, fontSize = 10.sp)) }
        }
        if (view == 2) Text("Groups photos by how many people are in them (faces found on the phone). It doesn't recognise who they are.", color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
        if (selecting) Row(Modifier.fillMaxWidth().hintedScroll(horizontal = true).padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${picked.size} selected", style = Pro.Label.copy(color = Color.White, fontSize = 11.sp))
            PillChip("Select all", false) { picked.clear(); picked.addAll(shown.map { it.uri.toString() }) }
            if (picked.isNotEmpty()) {
                PillChip("✎ Edit", false, accentText = true) { onEdit(sel.map { it.uri }) }
                if (picked.size == 2) PillChip("Compare", false) { compare = sel }
                if (picked.size in 2..12) PillChip("Survey", false) { survey = sel }
                (1..5).forEach { r -> PillChip("★$r", false) { LibraryStore.update(context, picked) { it.copy(rating = r) } } }
                PillChip("⚑ Pick", false) { LibraryStore.update(context, picked) { it.copy(flag = 1) } }
                PillChip("✕ Reject", false) { LibraryStore.update(context, picked) { it.copy(flag = -1) } }
                PillChip("Unflag", false) { LibraryStore.update(context, picked) { it.copy(flag = 0) } }
                PillChip("+ Album", false) { dialog = "album" }
                PillChip("Keywords", false) { dialog = "keywords" }
                PillChip("Contact sheet PDF", false) { dialog = "sheet" }
                PillChip("Print layout", false) { dialog = "print" }
                if (picked.size in 2..9) PillChip("Collage", false) { dialog = "collage" }
            }
        }
        Text("${shown.size} photo" + (if (shown.size == 1) "" else "s"), style = Pro.Label.copy(color = Pro.Dim, fontSize = 10.sp), modifier = Modifier.padding(bottom = 4.dp))
        if (view == 3 && mapPick == null) LibraryMap(
            shown.mapNotNull { p -> LibraryStore.get(p.uri.toString()).place?.takeIf { it.size == 2 }?.let { p.uri.toString() to it } },
            onPick = { mapPick = it.toSet() },
            modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(8.dp)),
        ) else LazyVerticalGrid(GridCells.Adaptive(104.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            items(gridList, key = { it.first.uri.toString() }) { (p, stack) ->
                val k = p.uri.toString()
                val m = LibraryStore.get(k)
                val on = k in picked
                Box(
                    Modifier.aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(Ed.Raised)
                        .border(if (on) 2.dp else 0.dp, if (on) Pro.Amber else Color.Transparent, RoundedCornerShape(6.dp))
                        .combinedClickable(
                            onClick = { if (selecting) { if (on) picked.remove(k) else picked.add(k) } else if (stack > 1) expanded.add(k) else detail = p },
                            onLongClick = { selecting = true; if (!on) picked.add(k) },
                        ),
                ) {
                    LibThumb(p.uri, 256)
                    if (m.flag == -1) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
                    Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (m.rating > 0) Text("★".repeat(m.rating), color = Pro.Amber, fontSize = 8.sp)
                        Box(Modifier.weight(1f))
                        if (m.flag == 1) Text("⚑", color = Color.White, fontSize = 9.sp)
                        m.label?.let { Box(Modifier.padding(start = 2.dp).size(7.dp).clip(CircleShape).background(Color(it))) }
                    }
                    if (selecting) Text(if (on) "☑" else "☐", color = Color.White, fontSize = 13.sp, modifier = Modifier.align(Alignment.TopStart).padding(3.dp))
                    if (stack > 1) Text("▣ $stack", style = Pro.Label.copy(color = Color.Black, fontSize = 10.sp), modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).clip(RoundedCornerShape(4.dp)).background(Pro.Amber).padding(horizontal = 4.dp, vertical = 1.dp))
                    if (stack < -1) Text("▣ ${-stack} ▾", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp), modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.6f)).clickable { expanded.remove(k) }.padding(horizontal = 6.dp, vertical = 3.dp))
                    if (stack == -1) Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(6.dp).clip(CircleShape).background(Pro.Amber))
                }
            }
        }
    }

    // ---- Contact sheet PDF
    var sheetCols by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(4) }
    var sheetLetter by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var sheetCaps by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(true) }
    var sheetBusy by remember { mutableStateOf<String?>(null) }
    val sheetScope = androidx.compose.runtime.rememberCoroutineScope()
    val sheetUris = sel.map { it.uri }
    val saveSheet = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { out ->
        if (out == null || sheetUris.isEmpty()) return@rememberLauncherForActivityResult
        sheetBusy = "Making the contact sheet…"
        sheetScope.launch {
            val res = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try { "Contact sheet saved (" + ContactSheet.write(context, sheetUris, out, ContactSheet.Options(sheetCols, sheetLetter, sheetCaps)) { i, n -> sheetBusy = "Contact sheet $i / $n" } + " pages)" }
                catch (t: Throwable) { "Couldn't make the contact sheet: ${t.message}" }
            }
            sheetBusy = null
            android.widget.Toast.makeText(context, res, android.widget.Toast.LENGTH_LONG).show()
        }
    }
    sheetBusy?.let { Text(it, color = Pro.Amber, fontSize = 12.sp) }
    if (dialog == "sheet") androidx.compose.material3.AlertDialog(
        onDismissRequest = { dialog = null },
        containerColor = Ed.Raised,
        title = { Text("Contact sheet PDF", color = Color.White) },
        text = {
            Column {
                Text("${sheetUris.size} photos, as a grid on printable pages.", color = Pro.Dim, fontSize = 12.sp)
                Text("Photos per row", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(3, 4, 5, 6).forEach { n -> PillChip("$n", sheetCols == n) { sheetCols = n } } }
                Text("Paper", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { PillChip("A4", !sheetLetter) { sheetLetter = false }; PillChip("Letter", sheetLetter) { sheetLetter = true } }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) { PillChip("File names & stars", sheetCaps) { sheetCaps = true }; PillChip("Photos only", !sheetCaps) { sheetCaps = false } }
            }
        },
        confirmButton = { Text("SAVE PDF", style = Pro.Label.copy(color = Pro.Amber, fontSize = 12.sp), modifier = Modifier.clickable { dialog = null; saveSheet.launch("Contact sheet.pdf") }.padding(10.dp)) },
        dismissButton = { Text("CANCEL", style = Pro.Label.copy(color = Pro.Dim, fontSize = 12.sp), modifier = Modifier.clickable { dialog = null }.padding(10.dp)) },
    )

    // ---- Print layout (PDF at exact print sizes)
    var prPaper by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
    var prLayout by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
    var prBorder by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(5f) }
    var prFill by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(true) }
    var prMarks by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(true) }
    val savePrint = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { out ->
        if (out == null || sheetUris.isEmpty()) return@rememberLauncherForActivityResult
        sheetBusy = "Making the print layout…"
        sheetScope.launch {
            val res = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try { "Print layout saved (" + PrintLayout.write(context, sheetUris, out, PrintLayout.Options(prPaper, prLayout, prBorder, prFill, prMarks)) { i, n -> sheetBusy = "Print layout $i / $n" } + " pages)" }
                catch (t: Throwable) { "Couldn't make the print layout: ${t.message}" }
            }
            sheetBusy = null
            android.widget.Toast.makeText(context, res, android.widget.Toast.LENGTH_LONG).show()
        }
    }
    if (dialog == "print") androidx.compose.material3.AlertDialog(
        onDismissRequest = { dialog = null },
        containerColor = Ed.Raised,
        title = { Text("Print layout", color = Color.White) },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                Text("${sheetUris.size} photos at exact print sizes, as a PDF for a printer or print shop (about 300 dpi).", color = Pro.Dim, fontSize = 12.sp)
                Text("Paper", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.hintedScroll(horizontal = true), horizontalArrangement = Arrangement.spacedBy(6.dp)) { PrintLayout.PAPERS.forEachIndexed { i, p -> PillChip(p.label, prPaper == i) { prPaper = i } } }
                Text("Layout", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.hintedScroll(horizontal = true), horizontalArrangement = Arrangement.spacedBy(6.dp)) { PrintLayout.LAYOUTS.forEachIndexed { i, l -> PillChip(l.label, prLayout == i) { prLayout = i } } }
                Text("Border", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(0f, 3f, 5f, 10f).forEach { b -> PillChip(if (b == 0f) "None" else "${b.toInt()} mm", prBorder == b) { prBorder = b } } }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) { PillChip("Fill (crop)", prFill) { prFill = true }; PillChip("Whole photo", !prFill) { prFill = false } }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) { PillChip("Crop marks", prMarks) { prMarks = !prMarks } }
            }
        },
        confirmButton = { Text("SAVE PDF", style = Pro.Label.copy(color = Pro.Amber, fontSize = 12.sp), modifier = Modifier.clickable { dialog = null; savePrint.launch("Print layout.pdf") }.padding(10.dp)) },
        dismissButton = { Text("CANCEL", style = Pro.Label.copy(color = Pro.Dim, fontSize = 12.sp), modifier = Modifier.clickable { dialog = null }.padding(10.dp)) },
    )

    // ---- Collage
    if (dialog == "collage") CollageDialog(sheetUris, onDone = { msg -> dialog = null; if (msg != null) android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show() })

    // ---- Dialogs
    when (dialog) {
        "newAlbum", "album" -> NameDialog(if (dialog == "album") "Add to album" else "New album", LibraryStore.albums.toList()) { name ->
            dialog = null
            if (name != null) {
                if (name !in LibraryStore.albums) LibraryStore.albums.add(name)
                if (picked.isNotEmpty()) LibraryStore.update(context, picked) { it.copy(albums = (it.albums + name).distinct()) } else LibraryStore.save(context)
            }
        }
        "keywords" -> NameDialog("Add keywords (comma separated)", emptyList()) { t ->
            dialog = null
            val kws = t?.split(',')?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() }.orEmpty()
            if (kws.isNotEmpty()) LibraryStore.update(context, picked) { it.copy(keywords = (it.keywords + kws).distinct()) }
        }
        "newSmart" -> NameDialog("Smart album name (uses the filters and search now set)", emptyList()) { name ->
            dialog = null
            if (name != null) { LibraryStore.smart.add(SmartAlbum(name, minRating, if (flagF == 1) 1 else if (flagF == 2) 2 else 0, labelF, text)); LibraryStore.save(context) }
        }
    }
    detail?.let { p -> PhotoDetail(p, onClose = { detail = null }, onEdit = { detail = null; onEdit(listOf(p.uri)) }, onVirtualCopy = { detail = null; onVirtualCopy(p.uri) }) }
    compare?.let { list -> CompareView(list, onClose = { compare = null }) }
    survey?.let { list -> SurveyView(list, onClose = { survey = null }, onEdit = { survey = null; onEdit(it) }) }
}

@Composable
private fun LibThumb(uri: Uri, size: Int, crop: Boolean = true) {
    val context = LocalContext.current
    val img by produceState<ImageBitmap?>(null, uri, size) {
        value = withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= 29 && size <= 512) com.dronedirector.veyrocutpro.ui.UprightThumb.fix(context, uri, context.contentResolver.loadThumbnail(uri, android.util.Size(size, size), null))!!.asImageBitmap()
                else GradeIO.decode(context, uri, size)?.asImageBitmap()
            } catch (_: Throwable) { null }
        }
    }
    img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = if (crop) ContentScale.Crop else ContentScale.Fit) }
}

@Composable
private fun NameDialog(title: String, existing: List<String>, onDone: (String?) -> Unit) {
    var t by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { onDone(null) }, containerColor = Pro.PanelSolid,
        title = { Text(title, color = Color.White, fontSize = 16.sp) },
        text = {
            Column {
                OutlinedTextField(t, { t = it.take(60) }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Pro.Amber, cursorColor = Pro.Amber))
                if (existing.isNotEmpty()) Row(Modifier.hintedScroll(horizontal = true).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    existing.forEach { e -> PillChip(e, false) { onDone(e) } }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(t.trim().ifBlank { null }) }) { Text("OK", color = Pro.Amber) } },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("Cancel", color = Pro.Dim) } },
    )
}

/** One photo large, with its rating, flag, label, keywords, albums and camera details. */
@Composable
private fun PhotoDetail(p: LibPhoto, onClose: () -> Unit, onEdit: () -> Unit, onVirtualCopy: () -> Unit) {
    val context = LocalContext.current
    val k = p.uri.toString()
    val m = LibraryStore.get(k)
    val info by produceState(emptyList<Pair<String, String>>(), p) { value = withContext(Dispatchers.IO) { LibraryStore.exif(context, p) } }
    var kw by remember(k) { mutableStateOf(m.keywords.joinToString(", ")) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Ed.Bg).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.name, color = Color.White, fontSize = 15.sp, maxLines = 1, modifier = Modifier.weight(1f))
                Text("✕", color = Color.White, fontSize = 20.sp, modifier = Modifier.clickable(onClick = onClose).padding(8.dp))
            }
            Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(8.dp)).background(Color.Black)) { LibThumb(p.uri, 1600, crop = false) }
            Column(Modifier.fillMaxWidth().height(260.dp).verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.hintedScroll(horizontal = true)) {
                    (0..5).forEach { r -> PillChip(if (r == 0) "☆" else "★$r", m.rating == r) { LibraryStore.update(context, listOf(k)) { it.copy(rating = r) } } }
                    PillChip("⚑ Pick", m.flag == 1) { LibraryStore.update(context, listOf(k)) { it.copy(flag = if (it.flag == 1) 0 else 1) } }
                    PillChip("✕ Reject", m.flag == -1) { LibraryStore.update(context, listOf(k)) { it.copy(flag = if (it.flag == -1) 0 else -1) } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(22.dp).clip(CircleShape).border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape).clickable { LibraryStore.update(context, listOf(k)) { it.copy(label = null) } })
                    LIB_LABELS.forEach { c -> Box(Modifier.size(22.dp).clip(CircleShape).background(Color(c)).border(2.dp, if (m.label == c) Color.White else Color.Transparent, CircleShape).clickable { LibraryStore.update(context, listOf(k)) { it.copy(label = c) } }) }
                }
                OutlinedTextField(kw, { kw = it; LibraryStore.update(context, listOf(k)) { mm -> mm.copy(keywords = it.split(',').map { s -> s.trim().lowercase() }.filter { s -> s.isNotEmpty() }) } },
                    label = { Text("Keywords (comma separated)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Pro.Amber, focusedLabelColor = Pro.Amber, cursorColor = Pro.Amber))
                if (LibraryStore.albums.isNotEmpty()) Row(Modifier.hintedScroll(horizontal = true).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LibraryStore.albums.forEach { a -> PillChip("▣ $a", a in m.albums) { LibraryStore.update(context, listOf(k)) { it.copy(albums = if (a in it.albums) it.albums - a else it.albums + a) } } }
                }
                m.ai?.takeIf { it.isNotEmpty() }?.let { Text("AI sees: " + it.joinToString(", "), color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.padding(vertical = 4.dp)) }
                SectionTitle("INFO")
                info.forEach { (a, b) -> Row(Modifier.padding(vertical = 1.dp)) { Text(a, color = Pro.Dim, fontSize = 12.sp, modifier = Modifier.width(110.dp)); Text(b, color = Color.White, fontSize = 12.sp) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                PillChip("✎ EDIT", false, accentText = true) { onEdit() }
                PillChip("VIRTUAL COPY", false) { onVirtualCopy() }
            }
        }
    }
}

/** Two photos side by side (stacked when upright), zoomed and moved together. */
@Composable
private fun CompareView(list: List<LibPhoto>, onClose: () -> Unit) {
    val context = LocalContext.current
    var z by remember { mutableStateOf(1f) }
    var px by remember { mutableStateOf(0f) }
    var py by remember { mutableStateOf(0f) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).padding(8.dp)) {
            val wide = maxWidth > maxHeight
            val pane = @Composable { p: LibPhoto, mod: Modifier ->
                Column(mod.padding(4.dp)) {
                    Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(6.dp)).pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ -> z = (z * zoom).coerceIn(1f, 8f); px += pan.x; py += pan.y; if (z <= 1.001f) { px = 0f; py = 0f } }
                    }) {
                        Box(Modifier.fillMaxSize().graphicsLayer(scaleX = z, scaleY = z, translationX = px, translationY = py)) { LibThumb(p.uri, 2000, crop = false) }
                    }
                    val m = LibraryStore.get(p.uri.toString())
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(p.name, color = Color.White, fontSize = 11.sp, maxLines = 1, modifier = Modifier.weight(1f))
                        PillChip("⚑ Pick", m.flag == 1) { LibraryStore.update(context, listOf(p.uri.toString())) { it.copy(flag = if (it.flag == 1) 0 else 1) } }
                        PillChip("✕", m.flag == -1) { LibraryStore.update(context, listOf(p.uri.toString())) { it.copy(flag = if (it.flag == -1) 0 else -1) } }
                    }
                }
            }
            Column(Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("COMPARE · pinch to zoom both", style = Pro.Label.copy(color = Pro.Amber), modifier = Modifier.weight(1f))
                    Text("✕", color = Color.White, fontSize = 20.sp, modifier = Modifier.clickable(onClick = onClose).padding(8.dp))
                }
                if (wide) Row(Modifier.fillMaxWidth().weight(1f)) { pane(list[0], Modifier.weight(1f)); pane(list[1], Modifier.weight(1f)) }
                else Column(Modifier.fillMaxWidth().weight(1f)) { pane(list[0], Modifier.weight(1f)); pane(list[1], Modifier.weight(1f)) }
            }
        }
    }
}

/** Several photos at once to choose from: drop the ones you don't want, keep the rest. */
@Composable
private fun SurveyView(list: List<LibPhoto>, onClose: () -> Unit, onEdit: (List<Uri>) -> Unit) {
    val context = LocalContext.current
    val left = remember { mutableStateListOf<LibPhoto>().apply { addAll(list) } }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("SURVEY · ${left.size} left — tap ✕ to drop one", style = Pro.Label.copy(color = Pro.Amber), modifier = Modifier.weight(1f))
                Text("✕", color = Color.White, fontSize = 20.sp, modifier = Modifier.clickable(onClick = onClose).padding(8.dp))
            }
            val cols = when { left.size <= 2 -> 2; left.size <= 6 -> 3; else -> 4 }
            LazyVerticalGrid(GridCells.Fixed(cols), Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(left.toList(), key = { it.uri.toString() }) { p ->
                    Box(Modifier.aspectRatio(0.8f).clip(RoundedCornerShape(6.dp)).background(Ed.Raised)) {
                        LibThumb(p.uri, 900, crop = false)
                        Text("✕", color = Color.White, fontSize = 16.sp, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).clickable { left.remove(p) }.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                PillChip("⚑ PICK THESE", false, accentText = true) { LibraryStore.update(context, left.map { it.uri.toString() }) { it.copy(flag = 1) }; onClose() }
                PillChip("✎ EDIT THESE", false, accentText = true) { onEdit(left.map { it.uri }) }
            }
        }
    }
}

@Composable
private fun CollageDialog(uris: List<android.net.Uri>, onDone: (String?) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var tpl by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
    var asp by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
    var gap by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0.02f) }
    var corner by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0f) }
    var bg by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
    var order by remember { mutableStateOf(uris) }
    var saving by remember { mutableStateOf(false) }
    val cache = remember { HashMap<android.net.Uri, android.graphics.Bitmap>() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val opts = Collage.Options(tpl, asp, gap, corner, bg)
    val preview by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, opts, order) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { try { Collage.render(context, order, opts, 640, cache) } catch (_: Throwable) { null } }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!saving) onDone(null) },
        containerColor = Ed.Raised,
        title = { Text("Collage", color = Color.White) },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                preview?.let { androidx.compose.foundation.Image(it.asImageBitmap(), null, modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit) }
                Row(Modifier.hintedScroll(horizontal = true).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { Collage.TEMPLATES.forEachIndexed { i, t -> PillChip(t, tpl == i) { tpl = i } } }
                Row(Modifier.hintedScroll(horizontal = true).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { Collage.ASPECTS.forEachIndexed { i, a -> PillChip(a.first, asp == i) { asp = i } } }
                Text("Spacing", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                androidx.compose.material3.Slider(gap, { gap = it }, valueRange = 0f..0.06f)
                Text("Rounded corners", color = Color.White, fontSize = 13.sp)
                androidx.compose.material3.Slider(corner, { corner = it }, valueRange = 0f..0.08f)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Collage.BACKGROUNDS.forEachIndexed { i, c -> Box(Modifier.size(28.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(c)).border(2.dp, if (bg == i) Pro.Amber else Pro.Line, androidx.compose.foundation.shape.CircleShape).clickable { bg = i }) }
                }
                Text("SHUFFLE ORDER", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(top = 6.dp).clickable { order = order.drop(1) + order.take(1) }.padding(8.dp))
                Text("The first photo you picked is the big one in Big + small; SHUFFLE moves every photo one place on.", color = Pro.Dim, fontSize = 11.sp)
            }
        },
        confirmButton = {
            Text(if (saving) "SAVING…" else "SAVE", style = Pro.Label.copy(color = Pro.Amber, fontSize = 12.sp), modifier = Modifier.clickable(enabled = !saving) {
                saving = true
                scope.launch {
                    val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        try { val b = Collage.render(context, order, opts, 3000); savePhotoAs(context, b, 0, 95, null, false).also { b.recycle() } != null } catch (_: Throwable) { false }
                    }
                    saving = false
                    onDone(if (ok) "Collage saved" else "Couldn't save the collage")
                }
            }.padding(10.dp))
        },
        dismissButton = { Text("CANCEL", style = Pro.Label.copy(color = Pro.Dim, fontSize = 12.sp), modifier = Modifier.clickable(enabled = !saving) { onDone(null) }.padding(10.dp)) },
    )
}
