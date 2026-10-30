package com.dronedirector.veyrocutpro.editor

import android.content.Context
import kotlinx.coroutines.sync.withPermit
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.ui.Pro
import com.dronedirector.veyrocutpro.ui.Text
import com.dronedirector.veyrocutpro.ui.mouseActions
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** One photo in the editor's filmstrip: its file, its own edit (a hidden project), rating, flag and colour label. */
data class SessionPhoto(
    val uri: String,
    val name: String = "",
    val projectId: String? = null,
    /** 0–5 stars; flag 1 = pick, −1 = reject; label = ARGB colour or null. */
    val rating: Int = 0,
    val flag: Int = 0,
    val label: Int? = null,
    val edited: Boolean = false,
    /** Bumped when the edit is saved, so the thumbnail is drawn again. */
    val version: Long = 0L,
    /** Set for a virtual copy: the same photo with its own, separate edit. */
    val copyId: String? = null,
    /** AI culling's note for this photo ("Blurry", "Best of 4"…), shown on the thumbnail. */
    val cull: String? = null,
    /** When it was last opened (the catalog lets go of the photo opened longest ago first). */
    val opened: Long = 0L,
    /** File name of the photo's copy in the catalog (photos/), if one was made. */
    val copy: String? = null,
    /** Taken with the camera from inside the editor: never gets the remembered watermark automatically. */
    val camera: Boolean? = null,
) {
    /** Unique in the filmstrip (a photo and its virtual copies share the uri). */
    val key: String get() = uri + (copyId?.let { "#$it" } ?: "")
}

/**
 * The photo editor's working set, like a desktop filmstrip: many photos imported at once, each with
 * its own edit. Kept until cleared, so you can come back later and carry on.
 */
object PhotoSession {
    val items = mutableStateListOf<SessionPhoto>()
    var current by mutableStateOf(-1)
    /** Filmstrip → Select multiple: on, and the picked photos (by file). Edits on the open photo follow to them. */
    var selecting by mutableStateOf(false)
    val picked = mutableStateListOf<String>()
    private var loaded = false

    // The open catalog's photo list (see Catalogs).
    private fun file(context: Context) = File(Catalogs.dir(context), "session.json")

    /** Reads the photo list again (another catalog was opened or moved). */
    fun reload(context: Context) {
        loaded = false; current = -1; items.clear(); load(context)
    }

    /** Marks photo [i] as just opened. */
    fun touch(context: Context, i: Int) {
        if (i !in items.indices) return
        items[i] = items[i].copy(opened = System.currentTimeMillis()); save(context)
    }

    /** The photo to open: the original, or the catalog's copy when the original can't be read any more. */
    fun openUri(context: Context, p: SessionPhoto): Uri {
        val u = Uri.parse(p.uri)
        if (Catalogs.readable(context, u)) return u
        val c = p.copy?.let { File(Catalogs.sub(context, "photos"), it) }
        if (c == null || !c.exists()) return u
        return Uri.fromFile(if (c.name.endsWith(".gz")) Catalogs.unpacked(context, c) ?: return u else c)
    }

    /** Lets go of the photos opened longest ago while the catalog holds more than its limit. */
    fun enforceLimit(context: Context) {
        load(context)
        val lim = Catalogs.limit
        if (lim <= 0 || items.size <= lim) return
        val cur = items.getOrNull(current)?.key
        val drop = items.indices.filter { items[it].key != cur }.sortedBy { items[it].opened }.take(items.size - lim)
        if (drop.isNotEmpty()) remove(context, drop)
    }

    /** Removes the catalog's AI results (made again when needed); returns the bytes freed. */
    fun slimDown(context: Context): Long {
        val files = Catalogs.sub(context, "ai").listFiles().orEmpty()
        val n = files.sumOf { it.length() }
        files.forEach { it.delete() }
        return n
    }

    /** Copies photos that have no copy in the catalog yet (in the background). */
    fun copyMissing(context: Context) {
        if (!Catalogs.keepCopies) return
        val app = context.applicationContext
        val todo = items.filter { it.copyId == null && it.copy == null }.map { it.uri to it.name }
        if (todo.isEmpty()) return
        Thread({
            for ((u, name) in todo) try {
                val f = Catalogs.copyFile(app, u, name)
                var out = f
                if (!f.exists() && !File(f.path + ".gz").exists()) {
                    val tmp = File(f.path + ".part")
                    app.contentResolver.openInputStream(Uri.parse(u))?.use { inp -> tmp.outputStream().use { inp.copyTo(it, 1 shl 16) } } ?: continue
                    tmp.renameTo(f)
                    // RAW / TIFF copies are packed losslessly when that saves real space (JPEG / HEIC are already compressed).
                    out = Catalogs.packIfWorthIt(f)
                } else if (!f.exists()) out = File(f.path + ".gz")
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    val i = items.indexOfFirst { it.uri == u && it.copyId == null }
                    if (i >= 0) { items[i] = items[i].copy(copy = out.name); save(app) }
                }
            } catch (_: Throwable) {}
        }, "catalog-copy").start()
    }

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        try {
            val f = file(context)
            if (!f.exists()) return
            val t = object : TypeToken<List<SessionPhoto>>() {}.type
            val list: List<SessionPhoto> = ProjectStore.gson.fromJson(f.readText(), t) ?: emptyList()
            items.clear(); items.addAll(list.map { Defaults.fix(it, SessionPhoto("")); it })
        } catch (_: Throwable) {}
    }

    fun save(context: Context) {
        try { file(context).writeText(ProjectStore.gson.toJson(items.toList())) } catch (_: Throwable) {}
    }

    /** Adds photos (skipping ones already there); returns the index of the first new one. */
    fun add(context: Context, uris: List<Uri>, camera: Boolean = false): Int {
        load(context)
        var first = -1
        for (u in uris) {
            try { context.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Throwable) {}
            val s = u.toString()
            val at = items.indexOfFirst { it.uri == s && it.copyId == null }
            if (at >= 0) { if (first < 0) first = at; continue }
            items.add(SessionPhoto(s, displayName(context, u), opened = System.currentTimeMillis(), camera = if (camera) true else null))
            if (first < 0) first = items.lastIndex
        }
        save(context)
        // Keep the catalog within its size (the new photos count as just opened), then copy them in.
        val keys = first.takeIf { it >= 0 }?.let { items[it].key }
        enforceLimit(context)
        copyMissing(context)
        return keys?.let { k -> items.indexOfFirst { it.key == k } } ?: -1
    }

    /** A virtual copy of [uri]: same photo, its own edit (starting from the original's edit). Returns its index. */
    fun addCopy(context: Context, uri: Uri): Int {
        load(context)
        val src = items.firstOrNull { it.uri == uri.toString() && it.copyId == null } ?: SessionPhoto(uri.toString(), displayName(context, uri)).also { items.add(it) }
        val n = items.count { it.uri == src.uri }
        // The copy starts with the original's edit, saved as its own project.
        val pid = src.projectId?.let { id -> ProjectStore.loadPhoto(context, id)?.let { p -> ProjectStore.savePhoto(context, p.copy(id = java.util.UUID.randomUUID().toString(), session = true), null).id } }
        items.add(SessionPhoto(src.uri, "${src.name} (copy $n)", pid, src.rating, src.flag, src.label, pid != null, copyId = java.util.UUID.randomUUID().toString().take(8)))
        save(context)
        return items.lastIndex
    }

    fun update(context: Context, i: Int, f: (SessionPhoto) -> SessionPhoto) {
        if (i !in items.indices) return
        items[i] = f(items[i]); save(context)
        // Stars, flags and labels of the photo itself (not virtual copies) show in the library too.
        val p = items[i]
        if (p.copyId == null) try {
            LibraryStore.load(context)
            LibraryStore.update(context, listOf(p.uri)) { it.copy(rating = p.rating, flag = p.flag, label = p.label) }
        } catch (_: Throwable) {}
    }

    fun remove(context: Context, idx: Collection<Int>) {
        val keep = items.filterIndexed { i, _ -> i !in idx }
        val cur = items.getOrNull(current)
        // The hidden per-photo edits go too, and — once no copy of the photo is left — its catalog copy and AI results.
        idx.mapNotNull { items.getOrNull(it)?.projectId }.forEach { ProjectStore.delete(context, false, it) }
        val gone = idx.mapNotNull { items.getOrNull(it) }.map { it.uri }.toSet().filter { u -> keep.none { it.uri == u } }
        for (u in gone) try {
            val id = Catalogs.photoId(u)
            Catalogs.sub(context, "photos").listFiles()?.filter { it.name.startsWith(id) }?.forEach { it.delete() }
            Catalogs.sub(context, "ai").listFiles()?.filter { it.name.startsWith(id + "_") }?.forEach { it.delete() }
        } catch (_: Throwable) {}
        items.clear(); items.addAll(keep)
        current = cur?.let { c -> items.indexOfFirst { it.key == c.key } } ?: -1
        save(context)
    }

    fun clear(context: Context) { remove(context, items.indices.toList()) }

    private fun displayName(context: Context, u: Uri): String = try {
        context.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (_: Throwable) { null } ?: u.lastPathSegment ?: "Photo"

    // Thumbnails: kept in memory (so switching photos never blanks the filmstrip) and on disk
    // (so they're there at once next time), made at most three at a time so they don't fight the viewer.
    private val thumbs = object : android.util.LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val thumbGate = kotlinx.coroutines.sync.Semaphore(3)
    private fun thumbKey(p: SessionPhoto) = "${p.key}|${p.projectId}|${p.version}"
    private fun thumbFile(context: Context, k: String) = File(File(context.cacheDir, "strip").apply { mkdirs() }, Integer.toHexString(k.hashCode()) + "_" + Integer.toHexString(k.length) + ".jpg")

    /** The thumbnail if it's already in memory (no waiting). */
    fun cachedThumb(p: SessionPhoto): Bitmap? = thumbs.get(thumbKey(p))

    /** The edited look (if saved) or the photo itself, small. RAWs use the camera's preview inside the DNG (no full decode). */
    suspend fun thumb(context: Context, p: SessionPhoto): Bitmap? {
        val k = thumbKey(p)
        thumbs.get(k)?.let { return it }
        return thumbGate.withPermit {
            thumbs.get(k)?.let { return@withPermit it }
            withContext(Dispatchers.IO) {
                val f = thumbFile(context, k)
                val b = (if (f.exists()) BitmapFactory.decodeFile(f.path) else null) ?: make(context, p)?.also { bmp ->
                    try { f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) } } catch (_: Throwable) {}
                }
                b?.also { thumbs.put(k, it) }
            }
        }
    }

    private fun make(context: Context, p: SessionPhoto): Bitmap? {
        p.projectId?.let { id -> ProjectStore.thumbFile(context, id)?.let { f -> BitmapFactory.decodeFile(f.path)?.let { return it } } }
        val u = openUri(context, p)
        return try {
            (if (RawDng.isRaw(context, u)) RawDng.embedded(context, u, 240)?.let { e -> small(PhotoViewer.matched(context, e), 320) } else null)
                ?: com.dronedirector.veyrocutpro.camera.GradeIO.decode(context, u, 240)
        } catch (_: Throwable) { null }
    }

    private fun small(b: Bitmap, side: Int): Bitmap {
        val k = side.toFloat() / maxOf(b.width, b.height)
        return if (k < 1f) Bitmap.createScaledBitmap(b, (b.width * k).toInt().coerceAtLeast(1), (b.height * k).toInt().coerceAtLeast(1), true) else b
    }
}

/** Reference view in the photo editor: a photo shown next to the one being edited, to match a set. */
object RefView {
    var uri by mutableStateOf<String?>(null)
    var shown by mutableStateOf(false)
}

private val LABELS = listOf(0xFFFF3B30, 0xFFFFD60A, 0xFF34C759, 0xFF0A84FF, 0xFFBF5AF2).map { it.toInt() }

/**
 * The filmstrip under the photo: tap to switch photos, hold for stars / flag / label, SELECT to work
 * on several (sync the current edit, rate, flag, export, remove). [current] = index being edited.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Filmstrip(
    onOpen: (Int) -> Unit,
    onAdd: () -> Unit,
    onSync: (List<Int>) -> Unit,
    onExport: (List<Int>) -> Unit,
    modifier: Modifier = Modifier,
    thumbSize: androidx.compose.ui.unit.Dp = 64.dp,
    /** Smaller (−) / bigger (+) thumbnails: the change in dp. */
    onZoom: ((Float) -> Unit)? = null,
    /** Monitor layouts: a tall column (grid) instead of a strip. */
    vertical: Boolean = false,
) {
    val context = LocalContext.current
    val items = PhotoSession.items
    var selecting by PhotoSession::selecting
    // Everything here works by the photo's file (uri), never by its position: positions change when photos are removed.
    val picked = PhotoSession.picked
    var filter by remember { mutableStateOf(0) }   // 0 all, 1 ★3+, 2 picks, 3 edited, 4 not rejected
    var labelF by remember { mutableStateOf<Int?>(null) }   // colour label filter (null = any)
    var menuFor by remember { mutableStateOf<String?>(null) }
    var askCull by remember { mutableStateOf(false) }
    var cullResult by remember { mutableStateOf<String?>(null) }
    val cullScope = androidx.compose.runtime.rememberCoroutineScope()
    var askRename by remember { mutableStateOf(false) }
    var renameTemplate by remember { mutableStateOf("{date}_{seq}") }
    var renameStart by remember { mutableStateOf(1) }
    // Gallery photos waiting for Android's permission to change them: uri → new name.
    var renameWaiting by remember { mutableStateOf<Map<Uri, String>>(emptyMap()) }
    fun applyRenamed(old: Uri, now: Uri, name: String) {
        for (i in PhotoSession.items.indices) if (PhotoSession.items[i].uri == old.toString()) PhotoSession.update(context, i) { it.copy(uri = now.toString(), name = name) }
    }
    val renamePermission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()) { res ->
        val todo = renameWaiting; renameWaiting = emptyMap()
        if (res.resultCode == android.app.Activity.RESULT_OK) cullScope.launch {
            var ok = 0
            withContext(Dispatchers.IO) { for ((u, n) in todo) { val r = Rename.rename(context, u, n); if (r is Rename.Outcome.Done) { ok++; withContext(Dispatchers.Main) { applyRenamed(u, r.uri, n) } } } }
            cullResult = "Renamed $ok more photo" + if (ok == 1) "" else "s"
        } else cullResult = "Not renamed — Android's permission was declined"
    }
    fun idx(key: String) = items.indexOfFirst { it.key == key }
    fun upd(uri: String, f: (SessionPhoto) -> SessionPhoto) { val k = idx(uri); if (k >= 0) PhotoSession.update(context, k, f) }
    fun indices(uris: Collection<String>) = uris.map { idx(it) }.filter { it >= 0 }
    val shown = items.toList().filter { p ->
        (labelF == null || p.label == labelF) && when (filter) { 1 -> p.rating >= 3; 2 -> p.flag == 1; 3 -> p.edited; 4 -> p.flag != -1; else -> true }
    }
    val curUri = items.getOrNull(PhotoSession.current)?.key
    // Drop selections of photos that are gone.
    LaunchedEffect(items.size) { picked.retainAll(items.map { it.key }.toSet()); if (menuFor != null && idx(menuFor!!) < 0) menuFor = null }
    val state = rememberLazyListState()
    LaunchedEffect(curUri, filter, labelF) { val k = shown.indexOfFirst { it.key == curUri }; if (k >= 0) state.animateScrollToItem((k - 2).coerceAtLeast(0)) }
    com.dronedirector.veyrocutpro.ui.ForceDark { Column(modifier.fillMaxWidth().background(Color(0xFF0B0D12))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (PhotoSession.current >= 0) "${PhotoSession.current + 1} / ${items.size}" else "${items.size}", style = Pro.Label.copy(color = Pro.Dim, fontSize = 10.sp), modifier = Modifier.padding(end = 8.dp))
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                listOf("All", "★3+", "Picks", "Edited", "No rejects").forEachIndexed { i, n ->
                    Text(n, style = Pro.Label.copy(color = if (filter == i) Pro.Amber else Pro.Dim, fontSize = 10.sp),
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).combinedClickable(onClick = { filter = i }).padding(horizontal = 6.dp, vertical = 3.dp))
                }
                // Colour label filter: tap a colour to show only those photos, tap it again for all.
                LABELS.forEach { c ->
                    Box(Modifier.padding(horizontal = 3.dp).size(14.dp).clip(CircleShape).background(Color(c))
                        .border(2.dp, if (labelF == c) Color.White else Color.Transparent, CircleShape).combinedClickable(onClick = { labelF = if (labelF == c) null else c }))
                }
            }
            if (onZoom != null) {
                Text("−", style = Pro.Label.copy(color = Color.White, fontSize = 13.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).combinedClickable(onClick = { onZoom(-12f) }).padding(horizontal = 8.dp, vertical = 2.dp))
                Text("+", style = Pro.Label.copy(color = Color.White, fontSize = 13.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).combinedClickable(onClick = { onZoom(12f) }).padding(horizontal = 8.dp, vertical = 2.dp))
            }
            Text(if (selecting) "DONE" else "SELECT", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp),
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).combinedClickable(onClick = { selecting = !selecting; picked.clear() }).padding(horizontal = 6.dp, vertical = 3.dp))
        }
        if (selecting) Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${picked.size} selected", style = Pro.Label.copy(color = Color.White, fontSize = 10.sp))
            Chip("Select all") { picked.clear(); picked.addAll(shown.map { it.key }) }
            Chip("AI cull") { askCull = true }
            if (picked.isNotEmpty()) {
                Chip("Sync edit") { onSync(indices(picked)) }
                Chip("Export") { onExport(indices(picked)) }
                Chip("★★★") { picked.toList().forEach { u -> upd(u) { it.copy(rating = 3) } } }
                Chip("Pick") { picked.toList().forEach { u -> upd(u) { it.copy(flag = 1) } } }
                Chip("Reject") { picked.toList().forEach { u -> upd(u) { it.copy(flag = -1) } } }
                Chip("Rename") { askRename = true }
                Chip("Remove") { val list = indices(picked); picked.clear(); PhotoSession.remove(context, list) }
            }
        }
        Cull.progress?.let { (d, t) -> Text("AI culling… $d / $t", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp), modifier = Modifier.padding(horizontal = 8.dp)) }
        cullResult?.let { Text(it, style = Pro.Label.copy(color = Color.White, fontSize = 10.sp), modifier = Modifier.padding(horizontal = 8.dp).combinedClickable(onClick = { cullResult = null })) }
        if (askRename) {
            val target = items.filter { it.key in picked && it.copyId == null }
            val first = target.firstOrNull()
            val preview by produceState("", renameTemplate, renameStart, first?.uri) { value = first?.let { withContext(Dispatchers.IO) { Rename.newName(context, Uri.parse(it.uri), renameTemplate, renameStart) } } ?: "" }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { askRename = false },
                containerColor = Pro.PanelSolid,
                title = { Text("Rename ${target.size} photo" + (if (target.size == 1) "" else "s"), color = Color.White) },
                text = {
                    Column {
                        androidx.compose.material3.OutlinedTextField(renameTemplate, { renameTemplate = it.take(80) }, singleLine = true, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                            Rename.TOKENS.forEach { t -> Chip(t) { renameTemplate += t } }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Numbers start at  ", color = Pro.Dim, fontSize = 12.sp)
                            Chip("−") { renameStart = (renameStart - 1).coerceAtLeast(0) }
                            Text("  $renameStart  ", color = Color.White, fontSize = 13.sp)
                            Chip("+") { renameStart++ }
                        }
                        if (preview.isNotEmpty()) Text("First: $preview", color = Pro.Amber, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                        Text("The files themselves get the new names (in the gallery too). Photos added with the gallery picker can't be renamed — open them from Browse files or the Library.", color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                },
                confirmButton = {
                    Text("RENAME", style = Pro.Label.copy(color = Pro.Amber), modifier = Modifier.combinedClickable(onClick = {
                        askRename = false
                        val tpl = renameTemplate; val start = renameStart
                        cullScope.launch {
                            var ok = 0; var cant = 0
                            val wait = LinkedHashMap<Uri, String>()
                            withContext(Dispatchers.IO) {
                                target.forEachIndexed { n, p ->
                                    val u = Uri.parse(p.uri)
                                    val name = Rename.newName(context, u, tpl, start + n)
                                    when (val r = Rename.rename(context, u, name)) {
                                        is Rename.Outcome.Done -> { ok++; withContext(Dispatchers.Main) { applyRenamed(u, r.uri, name) } }
                                        Rename.Outcome.NeedsPermission -> wait[u] = name
                                        Rename.Outcome.CantRename -> cant++
                                    }
                                }
                            }
                            cullResult = "Renamed $ok" + (if (cant > 0) ", $cant couldn't be renamed" else "") + if (wait.isNotEmpty()) " — allow the rest in Android's question" else ""
                            if (wait.isNotEmpty()) Rename.permissionRequest(context, wait.keys.toList())?.let { pi ->
                                renameWaiting = wait
                                try { renamePermission.launch(androidx.activity.result.IntentSenderRequest.Builder(pi.intentSender).build()) } catch (_: Throwable) { renameWaiting = emptyMap() }
                            }
                        }
                    }).padding(10.dp))
                },
                dismissButton = { Text("CANCEL", style = Pro.Label.copy(color = Color.White), modifier = Modifier.combinedClickable(onClick = { askRename = false }).padding(10.dp)) },
            )
        }
        if (askCull) {
            var blurry by remember { mutableStateOf(true) }
            var closed by remember { mutableStateOf(true) }
            var best by remember { mutableStateOf(true) }
            val target = if (picked.isNotEmpty()) picked.toList() else shown.map { it.key }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { askCull = false },
                containerColor = Pro.PanelSolid,
                title = { Text("AI cull ${target.size} photo" + (if (target.size == 1) "" else "s"), color = Color.White) },
                text = {
                    Column {
                        Text("The AI looks at each photo (on the phone) and flags it. Nothing is deleted; flags can be changed any time.", color = Pro.Dim, fontSize = 12.sp)
                        CullOption("Reject blurry and out-of-focus shots", blurry) { blurry = it }
                        CullOption("Reject shots with closed eyes", closed) { closed = it }
                        CullOption("Pick the best of similar shots (bursts)", best) { best = it }
                    }
                },
                confirmButton = {
                    Text("START", style = Pro.Label.copy(color = Pro.Amber), modifier = Modifier.combinedClickable(onClick = {
                        askCull = false
                        if (Cull.progress == null) cullScope.launch {
                            val r = withContext(Dispatchers.Default) { try { Cull.run(context, target, blurry, closed, best) { false } } catch (_: Throwable) { null } }
                            cullResult = if (r == null) "AI cull couldn't read the photos" else
                                "AI cull: ${r.rejected} rejected, ${r.picked} picked as the best of ${r.groups} group(s) of similar shots — use the Picks / No rejects filters (tap to hide)"
                        }
                    }).padding(10.dp))
                },
                dismissButton = { Text("CANCEL", style = Pro.Label.copy(color = Color.White), modifier = Modifier.combinedClickable(onClick = { askCull = false }).padding(10.dp)) },
            )
        }
        if (items.isEmpty()) Text("No photos in the filmstrip — tap + to add some (you can pick many at once).", style = Pro.Label.copy(color = Pro.Dim, fontSize = 10.sp), modifier = Modifier.padding(horizontal = 8.dp))
        val cell = @Composable { p: SessionPhoto ->
                val u = p.key
                val cur = u == curUri
                val sel = u in picked
                val bmp by produceState(PhotoSession.cachedThumb(p), p.uri, p.projectId, p.version) { value = PhotoSession.thumb(context, p) }
                Box(
                    Modifier.size(thumbSize).clip(RoundedCornerShape(6.dp)).background(Color(0xFF1A1E26))
                        .border(if (cur || sel) 2.dp else 1.dp, if (sel) Color.White else if (cur) Pro.Amber else Color.White.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                        .mouseActions(onRightClick = { menuFor = u })
                        .combinedClickable(
                            onClick = { if (selecting) { if (sel) picked.remove(u) else picked.add(u) } else if (!cur) { val k = idx(u); if (k >= 0) onOpen(k) } },
                            onLongClick = { menuFor = u },
                        ),
                ) {
                    bmp?.let { b -> val img = remember(b) { b.asImageBitmap() }; Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    if (p.flag == -1) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
                    Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (p.rating > 0) Text("★".repeat(p.rating), color = Pro.Amber, fontSize = 7.sp)
                        Box(Modifier.weight(1f))
                        if (p.flag == 1) Text("⚑", color = Color.White, fontSize = 8.sp)
                        if (p.flag == -1) Text("✕", color = Pro.Red, fontSize = 8.sp)
                    }
                    // Colour label: a stripe along the top, easy to see at any size.
                    p.label?.let { Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(4.dp).background(Color(it))) }
                    if (p.edited) Text("✎", color = Pro.Amber, fontSize = 9.sp, modifier = Modifier.align(Alignment.TopEnd).padding(2.dp))
                    p.cull?.takeIf { it != "OK" && !selecting }?.let { note ->
                        val bad = p.flag == -1 && (note.startsWith("Blurry") || note.startsWith("Eyes"))
                        Text(note, color = if (bad) Pro.Red else if (note.startsWith("Best")) Pro.Amber else Color.White, fontSize = 7.sp, maxLines = 1,
                            modifier = Modifier.align(Alignment.TopStart).padding(2.dp).clip(RoundedCornerShape(3.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 2.dp))
                    }
                    if (selecting) Text(if (sel) "☑" else "☐", color = Color.White, fontSize = 12.sp, modifier = Modifier.align(Alignment.TopStart).padding(2.dp))
                    DropdownMenu(expanded = menuFor == u, onDismissRequest = { menuFor = null }, containerColor = Pro.PanelSolid) {
                        if (selecting) Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            // Picking several: hold any photo for these.
                            Text("${picked.size} selected", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Chip("Deselect all") { menuFor = null; picked.clear(); selecting = false }
                                Chip("Select all") { picked.clear(); picked.addAll(shown.map { it.key }) }
                            }
                            Chip(if (sel) "Leave this photo out" else "Add this photo") { if (sel) picked.remove(u) else picked.add(u) }
                            if (picked.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    (0..5).forEach { r -> Chip(if (r == 0) "☆" else "★$r") { picked.toList().forEach { q -> upd(q) { it.copy(rating = r) } } } }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Chip("⚑ Pick") { picked.toList().forEach { q -> upd(q) { it.copy(flag = 1) } } }
                                    Chip("✕ Reject") { picked.toList().forEach { q -> upd(q) { it.copy(flag = -1) } } }
                                    Chip("Clear flag") { picked.toList().forEach { q -> upd(q) { it.copy(flag = 0) } } }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(20.dp).clip(CircleShape).border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape).combinedClickable(onClick = { picked.toList().forEach { q -> upd(q) { it.copy(label = null) } } }))
                                    LABELS.forEach { c -> Box(Modifier.size(20.dp).clip(CircleShape).background(Color(c)).combinedClickable(onClick = { picked.toList().forEach { q -> upd(q) { it.copy(label = c) } } })) }
                                }
                                Chip("Apply the open photo's edit") { menuFor = null; onSync(indices(picked)) }
                                Chip("Export selected") { menuFor = null; onExport(indices(picked)) }
                                Chip("Rename selected") { menuFor = null; askRename = true }
                                Chip("Remove selected from strip") { menuFor = null; val list = indices(picked); picked.clear(); PhotoSession.remove(context, list) }
                            }
                        } else Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                            Text(p.name, style = Pro.Label.copy(color = Pro.Dim, fontSize = 10.sp), maxLines = 1)
                            Chip("Select multiple") { menuFor = null; picked.clear(); picked.add(u); selecting = true }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                                (0..5).forEach { r -> Chip(if (r == 0) "☆" else "★$r", p.rating == r) { upd(u) { it.copy(rating = r) } } }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                                Chip("⚑ Pick", p.flag == 1) { upd(u) { it.copy(flag = if (it.flag == 1) 0 else 1) } }
                                Chip("✕ Reject", p.flag == -1) { upd(u) { it.copy(flag = if (it.flag == -1) 0 else -1) } }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(20.dp).clip(CircleShape).border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape).combinedClickable(onClick = { upd(u) { it.copy(label = null) } }))
                                LABELS.forEach { c -> Box(Modifier.size(20.dp).clip(CircleShape).background(Color(c)).border(2.dp, if (p.label == c) Color.White else Color.Transparent, CircleShape).combinedClickable(onClick = { upd(u) { it.copy(label = c) } })) }
                            }
                            Chip("Make virtual copy") { menuFor = null; PhotoSession.addCopy(context, Uri.parse(p.uri)) }
                            Chip("Use as reference") { menuFor = null; RefView.uri = p.key; RefView.shown = true }
                            if (!cur) Chip("Apply the open photo's edit") { menuFor = null; val k = idx(u); if (k >= 0) onSync(listOf(k)) }
                            Chip("Export this photo") { menuFor = null; val k = idx(u); if (k >= 0) onExport(listOf(k)) }
                            Chip("Remove from strip") {
                                // Close the menu first, then remove on the next frame (the menu must not outlive its photo).
                                menuFor = null
                                val k = idx(u)
                                if (k >= 0) PhotoSession.remove(context, listOf(k))
                            }
                        }
                    }
                }
        }
        val addTile = @Composable {
                Box(
                    Modifier.size(thumbSize).clip(RoundedCornerShape(6.dp)).border(1.dp, Pro.Amber.copy(alpha = 0.5f), RoundedCornerShape(6.dp)).combinedClickable(onClick = onAdd),
                    contentAlignment = Alignment.Center,
                ) { Text("+", color = Pro.Amber, fontSize = 26.sp) }
        }
        if (vertical) {
            // Monitor layout: the filmstrip as a column (grid) beside the photo.
            LazyVerticalGrid(GridCells.Adaptive(thumbSize + 5.dp), Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(6.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                gridItems(shown, key = { it.key }) { p -> cell(p) }
                item { addTile() }
            }
        } else LazyRow(state = state, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            items(shown, key = { it.key }) { p -> cell(p) }
            item { addTile() }
        }
    } }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Chip(text: String, on: Boolean = false, onClick: () -> Unit) {
    Text(
        text, style = Pro.Label.copy(color = if (on) Color.Black else Color.White, fontSize = 10.sp),
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Pro.Amber else Color.White.copy(alpha = 0.08f))
            .combinedClickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 5.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CullOption(label: String, v: Boolean, set: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { set(!v) })) {
        androidx.compose.material3.Checkbox(v, set, colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = Pro.Amber, checkmarkColor = Color.Black))
        Text(label, color = Color.White, fontSize = 13.sp)
    }
}
