package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.runtime.rememberUpdatedState
import com.dronedirector.veyrocutpro.ui.DragGhost
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.ui.Pro
import com.dronedirector.veyrocutpro.ui.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext

/** Small frames of library clips for the media strip: a cover and up to [STEPS] skim positions per clip. */
@OptIn(ExperimentalCoroutinesApi::class)
internal object SkimFrames {
    const val STEPS = 24
    private val cache = LruCache<String, Bitmap>(260)
    /** One reader at a time, kept open for the clip being skimmed. */
    val io = Dispatchers.IO.limitedParallelism(1)
    private var openUri: String? = null
    private var mmr: MediaMetadataRetriever? = null

    fun cached(uri: Uri, step: Int): Bitmap? = cache.get("$uri#$step")
    fun putCover(uri: Uri, b: Bitmap) { cache.put("$uri#-1", b) }

    /** Frame at skim step [step] (0 until STEPS), or the cover for −1. Call on [io]. */
    fun frame(context: Context, uri: Uri, durationMs: Long, step: Int): Bitmap? {
        val key = "$uri#$step"
        cache.get(key)?.let { return it }
        return try {
            if (openUri != uri.toString()) {
                try { mmr?.release() } catch (_: Throwable) {}
                mmr = MediaMetadataRetriever().apply { setDataSource(context, uri) }
                openUri = uri.toString()
            }
            val r = mmr ?: return null
            val tMs = if (step < 0) minOf(1000L, durationMs / 3) else durationMs * step / (STEPS - 1)
            val us = tMs.coerceIn(0, (durationMs - 40).coerceAtLeast(0)) * 1000
            val b = if (Build.VERSION.SDK_INT >= 27) r.getScaledFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 240, 240)
            else r.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { f ->
                val s = 240f / maxOf(f.width, f.height)
                Bitmap.createScaledBitmap(f, (f.width * s).toInt().coerceAtLeast(1), (f.height * s).toInt().coerceAtLeast(1), true).also { if (it !== f) f.recycle() }
            }
            b?.also { cache.put(key, it) }
        } catch (_: Throwable) { openUri = null; null }
    }

    /** Video files in a picked folder (and up to two levels of sub-folders). */
    fun folderVideos(context: Context, tree: Uri): List<Uri> {
        val out = ArrayList<Uri>()
        fun walk(docId: String, depth: Int) {
            val kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
            try {
                context.contentResolver.query(kids, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0) ?: continue
                        val mime = c.getString(1) ?: ""
                        val name = (c.getString(2) ?: "").lowercase()
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) { if (depth < 2) walk(id, depth + 1) }
                        else if (mime.startsWith("video/") || name.endsWith(".mkv") || name.endsWith(".mts") || name.endsWith(".mov")) out += DocumentsContract.buildDocumentUriUsingTree(tree, id)
                    }
                }
            } catch (_: Throwable) {}
        }
        try { walk(DocumentsContract.getTreeDocumentId(tree), 0) } catch (_: Throwable) {}
        return out
    }
}

/**
 * The media strip: every clip imported into the project, always on screen above the timeline (like
 * Final Cut's browser). Skim a clip by hovering the mouse over it or holding and sliding a finger;
 * tap to pick, double-tap to add it to the end of the timeline. Clips on the timeline carry a
 * USED mark; stars and reject come from [ratings] (file → 1–5 stars, −1 rejected).
 */
@Composable
fun MediaStrip(
    library: List<Clip>,
    uses: (Clip) -> Int,
    ratings: Map<String, Int>,
    bins: Map<String, String>,
    binNames: List<String>,
    onRate: (List<Clip>, Int) -> Unit,
    onAppend: (List<Clip>) -> Unit,
    onInsert: (Clip) -> Unit,
    onOverwrite: (Clip) -> Unit,
    onSource: (Clip) -> Unit,
    onRemove: (List<Clip>) -> Unit,
    onImport: () -> Unit,
    onImportFolder: () -> Unit,
    onCamera: () -> Unit = {},
    modifier: Modifier = Modifier,
    /** Mouse drag: is this window position a drop target (the timeline)? */
    onDragOver: (Offset) -> Boolean = { false },
    /** Mouse drop at a window position. */
    onDrop: (Clip, Offset) -> Unit = { _, _ -> },
    /** Thumbnail height (the strip can be made taller or shorter with its dividers). */
    thumbHeight: androidx.compose.ui.unit.Dp = 70.dp,
    /** Smaller (−) / bigger (+) thumbnails: the change in dp. */
    onZoom: ((Float) -> Unit)? = null,
    /** Monitor layouts: a tall column (grid) instead of a strip. */
    vertical: Boolean = false,
    /** File → Reveal in library: this file (uri text) is shown, picked out and scrolled to (each new [revealTick]). */
    reveal: String? = null,
    revealTick: Int = 0,
    /** Colour & details for this file's clip on the timeline. */
    onColor: (Clip) -> Unit = {},
    /** Reveal in project: the first place this file is used on the timeline. */
    onRevealTimeline: (Clip) -> Unit = {},
) {
    var deviceFor by remember { mutableStateOf<Clip?>(null) }
    deviceFor?.let { RevealFileDialog(it) { deviceFor = null } }
    var filter by remember { mutableStateOf(0) }       // 0 all, 1 unused, 2 used, 3 ★3+, 4 no rejects
    // Colour labels (red, yellow, green, blue, purple): kept with the file in the video library, so both show the same.
    val ctx = LocalContext.current
    remember { try { VideoLibrary.load(ctx) } catch (_: Throwable) {} }
    fun labelOf(c: Clip): Int? { return VideoLibrary.meta[c.uri.toString()]?.label }
    fun setLabel(list: List<Clip>, l: Int?) { VideoLibrary.update(ctx, list.map { it.uri.toString() }) { it.copy(label = l) } }
    var labelF by remember { mutableStateOf<Int?>(null) }
    // Smart bins: fill themselves from what each file is.
    var smartBin by remember { mutableStateOf<Int?>(null) }
    var bin by remember { mutableStateOf<String?>(null) }
    var selecting by remember { mutableStateOf(false) }
    val picked = remember { mutableStateListOf<String>() }
    var focus by remember { mutableStateOf<String?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    val shown = library.filter { c ->
        val k = c.uri.toString(); val r = ratings[k] ?: 0; val u = uses(c)
        (bin == null || bins[k] == bin) && (labelF == null || labelOf(c) == labelF) && (smartBin == null || SmartBins.match(smartBin!!, c)) && when (filter) { 1 -> u == 0; 2 -> u > 0; 3 -> r >= 3; 4 -> r != -1; else -> true }
    }
    val focused = library.firstOrNull { it.uri.toString() == focus }
    val rowState = androidx.compose.foundation.lazy.rememberLazyListState()
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    androidx.compose.runtime.LaunchedEffect(revealTick) {
        if (reveal == null || revealTick == 0) return@LaunchedEffect
        filter = 0; bin = null; focus = reveal
        val i = library.indexOfFirst { it.uri.toString() == reveal }
        if (i >= 0) try { if (vertical) gridState.animateScrollToItem(i) else rowState.animateScrollToItem(i) } catch (_: Throwable) {}
    }
    Column(modifier.fillMaxWidth().background(Color(0xFF0B0D12))) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("MEDIA ${library.size}", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp), modifier = Modifier.padding(end = 6.dp))
            if (onZoom != null) {
                // Thumbnail size: zoom out / in.
                StripChip("−") { onZoom(-15f) }
                StripChip("+") { onZoom(15f) }
                Text("·", color = Pro.Dim, modifier = Modifier.padding(horizontal = 4.dp))
            }
            listOf("All", "Unused", "Used", "★3+", "No rejects").forEachIndexed { i, n -> StripChip(n, filter == i) { filter = i } }
            Text("·", color = Pro.Dim, modifier = Modifier.padding(horizontal = 4.dp))
            SmartBins.NAMES.forEachIndexed { i, n -> StripChip("⚙ $n", smartBin == i) { smartBin = if (smartBin == i) null else i } }
            Text("·", color = Pro.Dim, modifier = Modifier.padding(horizontal = 4.dp))
            COLOUR_LABELS.forEach { col ->
                Box(Modifier.padding(horizontal = 2.dp).size(16.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(col))
                    .border(2.dp, if (labelF == col) Color.White else Color.Transparent, androidx.compose.foundation.shape.CircleShape).clickable { labelF = if (labelF == col) null else col })
            }
            if (binNames.isNotEmpty()) {
                Text("·", color = Pro.Dim, modifier = Modifier.padding(horizontal = 4.dp))
                StripChip("Any bin", bin == null) { bin = null }
                binNames.forEach { b -> StripChip(b, bin == b) { bin = b } }
            }
            Text("·", color = Pro.Dim, modifier = Modifier.padding(horizontal = 4.dp))
            StripChip(if (selecting) "DONE" else "SELECT", selecting) { selecting = !selecting; picked.clear() }
        }
        // Actions for the picked clip(s).
        val targets = if (selecting) library.filter { it.uri.toString() in picked } else listOfNotNull(focused)
        if (targets.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (targets.size == 1) targets[0].name else "${targets.size} selected", style = Pro.Label.copy(color = Color.White, fontSize = 10.sp), maxLines = 1, modifier = Modifier.width(110.dp))
            StripChip("Append", true) { onAppend(targets) }
            if (targets.size == 1) {
                StripChip("Insert at playhead") { onInsert(targets[0]) }
                StripChip("Overwrite") { onOverwrite(targets[0]) }
                StripChip("Source ›") { onSource(targets[0]) }
            }
            (1..5).forEach { r -> StripChip("★$r", targets.all { (ratings[it.uri.toString()] ?: 0) == r }) { onRate(targets, r) } }
            StripChip("☆") { onRate(targets, 0) }
            StripChip("✕ Reject", targets.all { ratings[it.uri.toString()] == -1 }) { onRate(targets, -1) }
            LabelPicker(targets.map { labelOf(it) }.distinct().singleOrNull()) { l -> setLabel(targets, l) }
            StripChip("Remove") { onRemove(targets); picked.clear(); focus = null }
            if (selecting) StripChip("All") { picked.clear(); picked.addAll(shown.map { it.uri.toString() }) }
        }
        val cell = @Composable { c: Clip ->
                val k = c.uri.toString()
                StripThumb(
                    c, uses(c), ratings[k] ?: 0, label = labelOf(c),
                    selected = if (selecting) k in picked else focus == k,
                    selecting = selecting,
                    onTap = { if (selecting) { if (k in picked) picked.remove(k) else picked.add(k) } else focus = if (focus == k) null else k },
                    onDoubleTap = { onAppend(listOf(c)) },
                    onMenu = { menuFor = k },
                    onDragOver = onDragOver,
                    onDrop = { at -> onDrop(c, at) },
                    th = thumbHeight,
                )
                DropdownMenu(expanded = menuFor == k, onDismissRequest = { menuFor = null }, containerColor = Pro.PanelSolid) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(c.name, style = Pro.Label.copy(color = Pro.Dim, fontSize = 10.sp), maxLines = 1)
                        StripChip("Append to timeline") { menuFor = null; onAppend(listOf(c)) }
                        StripChip("Insert at playhead") { menuFor = null; onInsert(c) }
                        StripChip("Overwrite at playhead") { menuFor = null; onOverwrite(c) }
                        StripChip("Open in source viewer") { menuFor = null; onSource(c) }
                        StripChip("Colour & details") { menuFor = null; onColor(c) }
                        StripChip("Reveal in project") { menuFor = null; onRevealTimeline(c) }
                        StripChip("Reveal on device") { menuFor = null; deviceFor = c }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { (0..5).forEach { r -> StripChip(if (r == 0) "☆" else "★$r", (ratings[k] ?: 0) == r) { onRate(listOf(c), r) } } }
                        StripChip("✕ Reject", ratings[k] == -1) { onRate(listOf(c), if (ratings[k] == -1) 0 else -1) }
                        LabelPicker(labelOf(c)) { l -> setLabel(listOf(c), l) }
                        StripChip("Remove from media") { menuFor = null; onRemove(listOf(c)) }
                    }
                }
        }
        val addButtons = @Composable {
                Column(Modifier.height(thumbHeight), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.width(70.dp).weight(1f).clip(RoundedCornerShape(6.dp)).border(1.dp, Pro.Amber.copy(alpha = 0.5f), RoundedCornerShape(6.dp)).clickable(onClick = onImport), contentAlignment = Alignment.Center) {
                        Text("+ Clips", color = Pro.Amber, fontSize = 11.sp)
                    }
                    Box(Modifier.width(70.dp).weight(1f).clip(RoundedCornerShape(6.dp)).border(1.dp, Pro.Amber.copy(alpha = 0.5f), RoundedCornerShape(6.dp)).clickable(onClick = onImportFolder), contentAlignment = Alignment.Center) {
                        Text("+ Folder", color = Pro.Amber, fontSize = 11.sp)
                    }
                    Box(Modifier.width(70.dp).weight(1f).clip(RoundedCornerShape(6.dp)).border(1.dp, Pro.Amber.copy(alpha = 0.5f), RoundedCornerShape(6.dp)).clickable(onClick = onCamera), contentAlignment = Alignment.Center) {
                        Text("+ Camera", color = Pro.Amber, fontSize = 11.sp)
                    }
                }
        }
        if (vertical) {
            // Monitor layout: a tall column of clips (grid), the add buttons on top.
            Row(Modifier.padding(horizontal = 6.dp, vertical = 3.dp)) { addButtons() }
            LazyVerticalGrid(GridCells.Adaptive(thumbHeight * 1.6f + 5.dp), Modifier.weight(1f).fillMaxWidth(), state = gridState, contentPadding = PaddingValues(6.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                gridItems(shown, key = { it.uri.toString() }) { c -> cell(c) }
            }
        } else LazyRow(state = rowState, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            items(shown, key = { it.uri.toString() }) { c -> cell(c) }
            item { addButtons() }
        }
    }
}

@Composable
private fun StripThumb(
    c: Clip, uses: Int, rating: Int, label: Int? = null, selected: Boolean, selecting: Boolean,
    onTap: () -> Unit, onDoubleTap: () -> Unit, onMenu: () -> Unit,
    onDragOver: (Offset) -> Boolean, onDrop: (Offset) -> Unit,
    th: androidx.compose.ui.unit.Dp = 70.dp,
) {
    var rootOff by remember { mutableStateOf(Offset.Zero) }
    val curOver by rememberUpdatedState(onDragOver)
    val curDrop by rememberUpdatedState(onDrop)
    val context = LocalContext.current
    val density = LocalDensity.current
    val tw = th * 1.6f
    val wPx = with(density) { tw.toPx() }
    var skim by remember { mutableStateOf<Float?>(null) }
    val cover by produceState<Bitmap?>(SkimFrames.cached(c.uri, -1) ?: VideoThumbs.cached(c.uri, "c${minOf(1000L, c.durationMs / 3) / 1000}_240"), c.uri) {
        // The cover comes from the fast thumbnails (phone's ready thumbnail / kept on disk), many clips at a time.
        if (value == null) value = withContext(VideoThumbs.io) { VideoThumbs.cover(context, c.uri, minOf(1000L, c.durationMs / 3), 240) }
            ?.also { SkimFrames.putCover(c.uri, it) }
    }
    val step = skim?.let { (it * (SkimFrames.STEPS - 1)).toInt().coerceIn(0, SkimFrames.STEPS - 1) }
    var skimFrame by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(step) {
        if (step == null) { skimFrame = null; return@LaunchedEffect }
        skimFrame = SkimFrames.cached(c.uri, step) ?: withContext(SkimFrames.io) { SkimFrames.frame(context, c.uri, c.durationMs, step) } ?: skimFrame
    }
    val img = skimFrame ?: cover
    Box(
        Modifier.size(tw, th).clip(RoundedCornerShape(6.dp)).background(Color(0xFF1A1E26))
            .border(if (selected) 2.dp else 1.dp, if (selected) Pro.Amber else Color.White.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
            .onGloballyPositioned { rootOff = it.positionInRoot() }
            // Mouse: hover to skim, right-click for the menu, press and drag onto the timeline.
            .pointerInput(c.uri) {
                awaitPointerEventScope {
                    var downAt: Offset? = null
                    var dragging = false
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        val ch = e.changes.firstOrNull() ?: continue
                        if (ch.type != PointerType.Mouse) continue
                        when (e.type) {
                            PointerEventType.Move, PointerEventType.Enter -> {
                                if (!ch.pressed) skim = (ch.position.x / wPx).coerceIn(0f, 1f)
                                else downAt?.let { d ->
                                    if (!dragging && (ch.position - d).getDistance() > 10f) {
                                        dragging = true; skim = null
                                        DragGhost.image = (SkimFrames.cached(c.uri, -1))?.asImageBitmap(); DragGhost.label = c.name
                                    }
                                    if (dragging) {
                                        val at = rootOff + ch.position
                                        DragGhost.at = at; DragGhost.canDrop = curOver(at)
                                        e.changes.forEach { it.consume() }
                                    }
                                }
                            }
                            PointerEventType.Exit -> if (!dragging) skim = null
                            PointerEventType.Press -> {
                                if (e.buttons.isSecondaryPressed) { e.changes.forEach { it.consume() }; onMenu() }
                                else if (e.buttons.isPrimaryPressed) { downAt = ch.position; dragging = false }
                            }
                            PointerEventType.Release -> {
                                if (dragging) { e.changes.forEach { it.consume() }; curDrop(rootOff + ch.position); DragGhost.clear() }
                                dragging = false; downAt = null
                            }
                            else -> {}
                        }
                    }
                }
            }
            // Touch: hold, then slide to skim.
            .pointerInput(c.uri) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { o -> skim = (o.x / wPx).coerceIn(0f, 1f) },
                    onDrag = { ch, _ -> skim = (ch.position.x / wPx).coerceIn(0f, 1f) },
                    onDragEnd = { skim = null }, onDragCancel = { skim = null },
                )
            }
            .pointerInput(c.uri, selecting) { detectTapGestures(onTap = { onTap() }, onDoubleTap = { onDoubleTap() }) },
    ) {
        img?.let { b -> val ib = remember(b) { b.asImageBitmap() }; Image(ib, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        if (rating == -1) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
        // Used on the timeline: an amber bar along the top.
        if (uses > 0) Box(Modifier.fillMaxWidth().height(3.dp).background(Pro.Amber))
        skim?.let { s ->
            Box(Modifier.fillMaxHeight().width(1.5.dp).offset(x = tw * s).background(Color.White))
            Text(tc((c.durationMs * s).toLong()), color = Color.White, fontSize = 8.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(3.dp).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 2.dp))
        }
        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(c.name, color = Color.White, fontSize = 7.sp, maxLines = 1, modifier = Modifier.weight(1f))
            if (rating > 0) Text("★$rating", color = Pro.Amber, fontSize = 7.sp)
            if (rating == -1) Text("✕", color = Pro.Red, fontSize = 8.sp)
        }
        if (skim == null) Text(
            (if (uses > 0) "USED ×$uses · " else "") + (if (c.still != null) "PHOTO" else tc(c.durationMs)), color = if (uses > 0) Pro.Amber else Color.White, fontSize = 7.sp,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 3.dp).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 2.dp),
        )
        // Colour label: a stripe along the bottom.
        if (label != null) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp).background(Color(label)))
        if (selecting) Text(if (selected) "☑" else "☐", color = Color.White, fontSize = 12.sp, modifier = Modifier.align(Alignment.TopStart).padding(2.dp))
    }
}

@Composable
private fun StripChip(text: String, on: Boolean = false, onClick: () -> Unit) {
    Text(
        text, style = Pro.Label.copy(color = if (on) Color.Black else Color.White, fontSize = 10.sp), maxLines = 1,
        modifier = Modifier.padding(end = 3.dp).clip(RoundedCornerShape(50)).background(if (on) Pro.Amber else Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** Smart bins for the media strip: they fill themselves from what each file is. */
object SmartBins {
    val NAMES = listOf("Photos", "Drone", "4K+", "Vertical", "Under 10 s", "Log / HDR")
    fun match(i: Int, c: Clip): Boolean = when (i) {
        0 -> c.still != null
        1 -> c.still == null && (c.name.startsWith("DJI_", ignoreCase = true) || c.name.contains("DJI", ignoreCase = true) || c.name.startsWith("dji", ignoreCase = true))
        2 -> maxOf(c.width, c.height) >= 3800
        3 -> c.height > c.width
        4 -> c.still == null && c.durationMs < 10_000L
        5 -> c.hdr || c.tenBit
        else -> true
    }
}
