package com.dronedirector.veyrocutpro.editor

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberUpdatedState
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import com.dronedirector.veyrocutpro.ui.Text
import com.dronedirector.veyrocutpro.ui.mouseActions
import androidx.compose.runtime.Composable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.camera.Filter
import com.dronedirector.veyrocutpro.camera.GradeIO
import com.dronedirector.veyrocutpro.camera.MediaLibrary
import com.dronedirector.veyrocutpro.camera.Storage
import com.dronedirector.veyrocutpro.ui.Pro
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.dronedirector.veyrocutpro.ui.hintedScroll
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class PhotoEdit(
    val grade: ProGrade = ProGrade(),
    val detail: Detail = Detail(),
    val geometry: Geometry = Geometry(),
    val watermark: Layer? = null,
    val masks: List<PhotoMask> = emptyList(),
    val spots: List<Spot> = emptyList(),
    val lens: LensFix = LensFix(),
    val bg: PhotoBackground = PhotoBackground(),
    val baseFile: String? = null,
    /** Text and sticker layers drawn on the photo (Text tab). */
    val texts: List<Layer> = emptyList(),
    val sky: SkySwap = SkySwap(),
    /** RAW (DNG) develop choices: profile and white balance preset. */
    val raw: RawDng.Dev = RawDng.Dev(),
    /** Lens Blur from the AI depth map. */
    val lensBlur: LensBlur? = null,
    /** Retouch: liquify, frequency separation, relight. */
    val retouch: Retouch = Retouch(),
)

/** RAW develop does exposure, white balance and the tonal ranges itself; the 8-bit grade skips them. */
private fun ProGrade.rawRest() = copy(exposure = 0f, highlights = 0f, shadows = 0f, whites = 0f, blacks = 0f, temperature = 0f, tint = 0f)

private enum class PhotoPanel(val label: String) {
    PRESETS("Presets"), LIGHT("Light"), CURVE("Curve"), COLOR("Color"), MIXER("HSL Mixer"),
    GRADING("Grading"), DETAIL("Detail"), EFFECTS("Effects"), MASKS("Masks"), HEAL("Heal"), RETOUCH("Retouch"), LENS("Lens"),
    CROP("Crop & perspective"), WATERMARK("Watermark"), TEXT("Text"), HISTORY("History"), EXPORT("Export"),
}

@Composable
fun PhotoEditorScreen(onExit: () -> Unit, initial: Uri? = null) {
    val context = LocalContext.current
    var source by remember { mutableStateOf(initial) }
    var project by remember { mutableStateOf<PhotoProjectData?>(null) }

    // Filmstrip: photos picked together stay in a working set, each with its own edit.
    LaunchedEffect(Unit) { PhotoSession.load(context) }
    fun openSession(i: Int) {
        val sp = PhotoSession.items.getOrNull(i) ?: return
        PhotoSession.current = i
        PhotoSession.touch(context, i)
        project = sp.projectId?.let { ProjectStore.loadPhoto(context, it) }
        // The original, or the catalog's copy if the original was moved or deleted.
        source = PhotoSession.openUri(context, sp)
    }
    // Another catalog was opened: back to the start page (its photos are in the filmstrip).
    var seenCatalog by remember { mutableStateOf(Catalogs.version) }
    LaunchedEffect(Catalogs.version) { if (Catalogs.version != seenCatalog) { seenCatalog = Catalogs.version; source = null; project = null } }
    fun addMany(list: List<Uri>, camera: Boolean = false) {
        if (list.isEmpty()) return
        val first = PhotoSession.add(context, list, camera)
        // From inside the editor (+ on the filmstrip) the photos are only added; the current edit stays open.
        if (first >= 0 && (source == null || source == Uri.EMPTY)) openSession(first)
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { addMany(it) }
    val browse = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { addMany(it) }
    // Capture (all its pro controls) opened over the editor: each photo taken goes straight into the
    // filmstrip (and opens if nothing is open yet).
    fun openCamera() = com.dronedirector.veyrocutpro.ui.CaptureForEditor.open("photo editor", video = false) { u, isVideo ->
        if (!isVideo) addMany(listOf(u), camera = true)
        else android.widget.Toast.makeText(context, com.dronedirector.veyrocutpro.ui.I18n.tr("Videos go to the video editor — the photo editor takes photos"), android.widget.Toast.LENGTH_SHORT).show()
    }
    var addFrom by remember { mutableStateOf(false) }
    if (addFrom) AddFromDialog("Add photos", listOf(
        Triple(Icons.Filled.PhotoLibrary, "Gallery") { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        Triple(Icons.Filled.FolderOpen, "Browse files") { browse.launch(arrayOf("image/*")) },
        Triple(Icons.Filled.PhotoCamera, "Camera — take a photo") { openCamera() },
    )) { addFrom = false }
    // Merge several photos (HDR, panorama, focus stack) into a new one, then edit it.
    val scope = rememberCoroutineScope()
    var mergeKind by remember { mutableStateOf<MergeKind?>(null) }
    var merging by remember { mutableStateOf<String?>(null) }
    val mergePick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(8)) { list ->
        val kind = mergeKind ?: return@rememberLauncherForActivityResult
        if (list.size < 2) { if (list.isNotEmpty()) android.widget.Toast.makeText(context, com.dronedirector.veyrocutpro.ui.I18n.tr("Pick at least 2 photos"), android.widget.Toast.LENGTH_SHORT).show(); return@rememberLauncherForActivityResult }
        merging = "Starting…"
        scope.launch {
            val uri = withContext(Dispatchers.Default) {
                try {
                    val b = Merge.run(context, kind, list) { msg -> scope.launch { merging = msg } } ?: return@withContext null
                    savePhotoAs(context, b, 0, 95, list[list.size / 2], false).also { b.recycle() }
                } catch (_: OutOfMemoryError) { null } catch (_: Throwable) { null }
            }
            merging = null
            if (uri != null) { project = null; source = uri }
            else android.widget.Toast.makeText(context, com.dronedirector.veyrocutpro.ui.I18n.tr("Couldn't merge those photos"), android.widget.Toast.LENGTH_LONG).show()
        }
    }

    val src = source
    var showLibrary by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    if (confirmClear) androidx.compose.material3.AlertDialog(
        onDismissRequest = { confirmClear = false }, containerColor = Pro.PanelSolid,
        title = { Text("Empty “${Catalogs.active(context).name}”?", color = Color.White) },
        text = { Text("Removes all ${PhotoSession.items.size} photos from this catalog, with their edits, copies and AI results. Your original photos and exports are not touched.", color = Pro.Dim) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { PhotoSession.clear(context); confirmClear = false }) { Text("EMPTY CATALOG", color = Pro.Amber) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmClear = false }) { Text("CANCEL", color = Color.White) } },
    )
    if (src == null && showLibrary) {
        LibraryScreen(
            onBack = { showLibrary = false },
            onEdit = { list -> showLibrary = false; val first = PhotoSession.add(context, list); if (first >= 0) openSession(first) },
            onVirtualCopy = { u -> showLibrary = false; openSession(PhotoSession.addCopy(context, u)) },
        )
        return
    }
    if (src == null) {
        BackHandler { onExit() }
        merging?.let { msg ->
            androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
                Column(Modifier.clip(RoundedCornerShape(16.dp)).background(Pro.PanelSolid).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Pro.Amber)
                    Text(msg, color = Color.White, modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
        // Panorama: choose the projection and how to handle the uneven edges first.
        var panoAsk by remember { mutableStateOf(false) }
        if (panoAsk) {
            var proj by remember { mutableStateOf(PanoOpts.projection) }
            var warp by remember { mutableStateOf(PanoOpts.warp) }
            var fill by remember { mutableStateOf(PanoOpts.fill) }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { panoAsk = false }, containerColor = Pro.PanelSolid,
                title = { Text("Panorama", color = Color.White) },
                text = {
                    Column {
                        Text("Projection", color = Color.White, fontSize = 13.sp)
                        Chips(PanoOpts.PROJECTIONS, proj) { proj = it }
                        Text(listOf("Best for wide, sweeping panoramas.", "Very wide or tall views (360° style); straight lines curve.", "Keeps straight lines straight (narrower panoramas, buildings).")[proj], color = Pro.Dim, fontSize = 11.sp)
                        EdSlider("Boundary warp", warp, 0f..1f, 0f, ::pct) { warp = it }
                        Text("Stretches the panorama to fill the frame instead of cropping away its uneven edges.", color = Pro.Dim, fontSize = 11.sp)
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Fill edges (AI)", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            androidx.compose.material3.Switch(fill, { fill = it }, colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Pro.Amber, checkedThumbColor = Color.Black))
                        }
                        Text("Paints in the empty corners instead of cropping them.", color = Pro.Dim, fontSize = 11.sp)
                    }
                },
                confirmButton = { androidx.compose.material3.TextButton(onClick = {
                    PanoOpts.projection = proj; PanoOpts.warp = warp; PanoOpts.fill = fill; panoAsk = false
                    mergeKind = MergeKind.PANORAMA; mergePick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("Pick photos", color = Pro.Amber) } },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { panoAsk = false }) { Text("Cancel", color = Pro.Dim) } },
            )
        }
        MediaChooser(
            onMerge = { k -> if (k == MergeKind.PANORAMA) panoAsk = true else { mergeKind = k; mergePick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } },
            title = "Photo editor",
            subtitle = "Choose a photo to edit. The original is never changed; edits are exported as a new picture.",
            videos = false,
            onBack = onExit,
            onGallery = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onFiles = { browse.launch(arrayOf("image/*")) },
            onRecent = { project = null; addMany(listOf(it)) },
            onSkip = { project = null; PhotoSession.current = -1; source = Uri.EMPTY },
            onCamera = { openCamera() },
            banner = {
                Row(
                    Modifier.padding(bottom = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ed.Raised)
                        .clickable { showLibrary = true }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = Pro.Amber)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text("Library", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("All your photos: stars, flags, albums, keywords, AI search, compare", color = Pro.Dim, fontSize = 11.sp)
                    }
                    Text("→", color = Pro.Amber, fontSize = 20.sp)
                }
                if (PhotoSession.items.isNotEmpty()) {
                Row(
                    Modifier.padding(bottom = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ed.Raised)
                        .clickable { openSession(PhotoSession.current.coerceIn(0, PhotoSession.items.lastIndex)) }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Continue “${Catalogs.active(context).name}” (${PhotoSession.items.size} photos)", color = Pro.Amber, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Catalog: each photo keeps its edit, settings and AI results. Switch or manage catalogs in ⚙ → Catalog.", color = Pro.Dim, fontSize = 11.sp)
                    }
                    Text("Clear", color = Pro.Dim, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(50)).clickable { confirmClear = true }.padding(8.dp))
                }
                }
            },
            onOpenProject = { id ->
                val p = ProjectStore.loadPhoto(context, id)
                if (p != null && p.uri != Uri.EMPTY) { PhotoSession.current = -1; project = p; source = p.uri }
                else android.widget.Toast.makeText(context, com.dronedirector.veyrocutpro.ui.I18n.tr("Couldn't open that project"), android.widget.Toast.LENGTH_SHORT).show()
            },
        )
    } else {
        androidx.compose.runtime.key(src, project?.id) {
            PhotoWorkspace(src, project, onClose = { source = null; project = null },
                onOpenSession = { openSession(it) },
                onAddGallery = { addFrom = true },
                onAddFiles = { browse.launch(arrayOf("image/*")) },
                onAddCamera = { openCamera() },
                onLoad = { id ->
                val p = ProjectStore.loadPhoto(context, id)
                if (p != null && p.uri != Uri.EMPTY) { PhotoSession.current = -1; project = p; source = p.uri }
                else android.widget.Toast.makeText(context, com.dronedirector.veyrocutpro.ui.I18n.tr("Couldn't open that project"), android.widget.Toast.LENGTH_SHORT).show()
            })
            com.dronedirector.veyrocutpro.ui.EditorTourHost(com.dronedirector.veyrocutpro.ui.GuideVariant.PHOTO)
        }
    }
}

/** Start page of an editor: pick from gallery, browse files, or recent AlcorCut Pro media. */
@Composable
fun MediaChooser(
    title: String,
    subtitle: String,
    videos: Boolean,
    onBack: () -> Unit,
    onGallery: () -> Unit,
    onFiles: () -> Unit,
    onRecent: (Uri) -> Unit,
    onOpenProject: (String) -> Unit = {},
    onMerge: ((MergeKind) -> Unit)? = null,
    /** Shown only when set: go straight into the editor without choosing anything first. */
    onSkip: (() -> Unit)? = null,
    /** Optional card under the subtitle (e.g. recover an unsaved edit). */
    banner: (@Composable () -> Unit)? = null,
    /** Shown only when set: take a photo with the phone's camera. */
    onCamera: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(false) }
    if (settings) EditorSettingsDialog(title, if (videos) com.dronedirector.veyrocutpro.ui.AppScreen.VIDEO_EDITOR else com.dronedirector.veyrocutpro.ui.AppScreen.PHOTO_EDITOR) { settings = false }
    val recent by produceState<List<com.dronedirector.veyrocutpro.camera.MediaItem>>(emptyList()) {
        value = withContext(Dispatchers.IO) { MediaLibrary.query(context).filter { it.isVideo == videos }.take(60) }
    }
    Column(Modifier.fillMaxSize().background(Ed.Bg).windowInsetsPadding(WindowInsets.displayCutout).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp).weight(1f))
            TopIcon(Icons.Filled.Settings, "Settings") { settings = true }
        }
        Text(subtitle, color = Pro.Dim, fontSize = 13.sp, modifier = Modifier.padding(vertical = 10.dp))
        banner?.invoke()
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton(Icons.Filled.PhotoLibrary, if (videos) "Gallery videos" else "Gallery photos", Modifier.weight(1f), onGallery)
            BigButton(Icons.Filled.FolderOpen, "Browse files", Modifier.weight(1f), onFiles)
        }
        if (onCamera != null) BigButton(Icons.Filled.PhotoCamera, "Take a photo", Modifier.fillMaxWidth().padding(top = 10.dp), onCamera)
        if (onSkip != null) {
            Row(
                Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Pro.Amber.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .clickable(onClick = onSkip)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Skip — open the editor", color = Pro.Amber, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (videos) "Start with an empty timeline and add clips later from the Media page." else "Open the editor now and add a photo from inside it.", color = Pro.Dim, fontSize = 11.sp)
                }
                Text("→", color = Pro.Amber, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (onMerge != null) {
            Text("MERGE PHOTOS", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.hintedScroll(horizontal = true)) {
                MergeKind.entries.forEach { k ->
                    Column(Modifier.width(150.dp).clip(RoundedCornerShape(10.dp)).background(Ed.Raised).clickable { onMerge(k) }.padding(10.dp)) {
                        Text(k.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(k.hint, color = Pro.Dim, fontSize = 10.sp, maxLines = 3)
                    }
                }
            }
        }
        if (videos) {
            Text("Browse files also opens MKV, MOV, WebM and clips from SD cards or USB drives.", color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        }
        SavedProjects(videos, onOpenProject)
        Text("RECENT ALCORCUT PRO ${if (videos) "VIDEOS" else "PHOTOS"}", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
        if (recent.isEmpty()) {
            Text("Nothing yet.", color = Pro.Dim, fontSize = 13.sp)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(96.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(recent, key = { it.uri.toString() }) { item ->
                    val thumb by produceState<ImageBitmap?>(null, item.uri) {
                        value = withContext(Dispatchers.IO) {
                            try {
                                if (Build.VERSION.SDK_INT >= 29) com.dronedirector.veyrocutpro.ui.UprightThumb.fix(context, item.uri, context.contentResolver.loadThumbnail(item.uri, android.util.Size(256, 256), null))!!.asImageBitmap()
                                else GradeIO.decode(context, item.uri, 256)?.asImageBitmap()
                            } catch (_: Throwable) { null }
                        }
                    }
                    Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(Ed.Raised).clickable { onRecent(item.uri) }) {
                        thumb?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                        FormatTag(item, Modifier.align(Alignment.BottomStart).padding(4.dp))
                    }
                }
            }
        }
    }
}

/** File type for the browse thumbnails: "DNG RAW", "JPEG", "PNG", "HEIC" / "MP4", "MOV", "MKV"… */
internal fun formatLabel(name: String, mime: String, raw: Boolean): String {
    val ext = name.substringAfterLast('.', "").lowercase()
    val t = ext.ifEmpty { mime.substringAfter('/', "").lowercase() }
    return when {
        t == "dng" || (raw && t.isEmpty()) -> "DNG RAW"
        raw -> "${t.uppercase()} RAW"
        t == "jpg" || t == "jpeg" -> "JPEG"
        t == "heif" -> "HEIF"
        t == "x-adobe-dng" -> "DNG RAW"
        t == "quicktime" -> "MOV"
        t == "x-matroska" -> "MKV"
        t == "3gpp" -> "3GP"
        t == "x-msvideo" -> "AVI"
        t == "mp2ts" || t == "mts" || t == "m2ts" -> "MTS"
        t.isEmpty() -> if (mime.startsWith("video")) "VIDEO" else "IMAGE"
        else -> t.uppercase().take(5)
    }
}

/** Small, readable file-type tag on a browse thumbnail (not shown inside the editor). */
@Composable
private fun FormatTag(item: com.dronedirector.veyrocutpro.camera.MediaItem, modifier: Modifier = Modifier) {
    val label = formatLabel(item.name, item.mime, item.isRaw && !item.isVideo)
    val raw = item.isRaw && !item.isVideo
    Text(
        label,
        color = if (raw) Color.Black else Color.White,
        fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp, maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(4.dp))
            .background(if (raw) Pro.Amber else Color.Black.copy(alpha = 0.72f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

@Composable
fun BigButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).background(Ed.Raised).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Pro.Amber)
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp))
    }
}

/** The edit a photo opens with: its saved project, or a fresh edit (with the remembered watermark). */
private fun editOf(context: Context, initial: PhotoProjectData?, autoWatermark: Boolean = true): PhotoEdit =
    @Suppress("USELESS_ELVIS")
    if (initial != null) PhotoEdit(initial.grade, initial.detail, initial.geometry, initial.watermark, initial.masks ?: emptyList(), initial.spots ?: emptyList(), initial.lens ?: LensFix(), initial.bg ?: PhotoBackground(), initial.baseFile, initial.texts ?: emptyList(), initial.sky ?: SkySwap(), initial.raw ?: RawDng.Dev(), initial.lensBlur, initial.retouch ?: Retouch())
    else PhotoEdit(watermark = if (autoWatermark) WatermarkMemory.load(context, false) else null)

/** AI masks, sky and depth pictures an edit draws from (file → picture). */
private fun PhotoEdit.aiFiles(): List<String> = masks.flatMap { m -> listOfNotNull(m.maskFile) + m.subs.orEmpty().mapNotNull { it.maskFile } } + listOfNotNull(bg.maskFile, sky.maskFile, sky.imagePath, lensBlur?.depthFile) + retouch.files()

/**
 * Reads ahead the filmstrip photos next to [idx]: decodes them and renders them with their own
 * edit, so switching to them shows the finished picture at once. Edits using AI noise reduction
 * are only decoded (the AI runs when the photo is opened).
 */
private fun prefetchAround(context: Context, idx: Int) {
    val app = context.applicationContext
    val items = PhotoSession.items.toList()
    val order = listOf(idx + 1, idx - 1).filter { it in items.indices && items[it].uri != items[idx].uri }
    if (order.isEmpty()) return
    PhotoViewer.prefetch {
        for (i in order) {
            kotlinx.coroutines.yield()
            val sp = items[i]
            val p = sp.projectId?.let { ProjectStore.loadPhoto(app, it) }
            val e = editOf(app, p, sp.camera != true)
            val sig = PhotoViewer.signature(e)
            val src = e.baseFile?.let { Uri.fromFile(java.io.File(it)) } ?: Uri.parse(sp.uri)
            val raw = if (e.baseFile == null) try { PhotoViewer.loadRaw(app, src).await() } catch (_: Throwable) { null } else null
            if (PhotoViewer.hasRendered(app, sp.key, sig) || e.detail.aiDenoise > 0f) continue
            kotlinx.coroutines.yield()
            val base = raw?.base ?: GradeIO.decode(app, src, PhotoViewer.viewerPx(app)) ?: continue
            val ai = e.aiFiles().mapNotNull { f -> BitmapFactory.decodeFile(f)?.let { f to it } }.toMap()
            val g = e.grade
            val s0 = if (raw != null) RawDng.develop(raw.lin, raw.meta, e.raw, g.exposure, g.highlights, g.shadows, g.whites, g.blacks, g.temperature, g.tint).let { d -> if (d.width != base.width || d.height != base.height) Bitmap.createScaledBitmap(d, base.width, base.height, true) else d } else base
            val eg = if (raw != null) g.rawRest() else g
            var o = ImageOps.renderPhoto(s0, eg, e.detail, e.geometry, if (eg.isIdentity) null else eg.tables(), e.lens, e.spots, e.masks, ai, e.bg, e.sky, e.lensBlur, e.retouch)
            for (t in e.texts) o = stampLayer(app, o, t)
            e.watermark?.let { o = stampLayer(app, o, it) }
            PhotoViewer.store(app, sp.key, sig, o)
        }
    }
}

/** The viewer's stand-in while a photo loads (see [PhotoViewer]) and its fade to the real render. */
private class ViewerState(val key: String, val sig: Int) {
    var placeholder by mutableStateOf<Bitmap?>(null)
    val fade = androidx.compose.animation.core.Animatable(0f)
    var readAhead = false

    /** Last render of this photo and edit, else its project thumbnail, else the camera's colour-matched embedded preview. */
    fun standIn(context: Context, uri: Uri, projectId: String?, rawOk: Boolean): Bitmap? = try {
        PhotoViewer.renderedFromDisk(context, key, sig)
            ?: projectId?.let { id -> ProjectStore.thumbFile(context, id)?.let { f -> BitmapFactory.decodeFile(f.path) } }
            ?: if (rawOk && PhotoViewer.raw(uri.toString()) == null && RawDng.isRaw(context, uri)) RawDng.embedded(context, uri)?.let { PhotoViewer.matched(context, it) } else null
    } catch (_: Throwable) { null }

    suspend fun reveal() {
        if (placeholder != null) { fade.snapTo(0f); fade.animateTo(1f, androidx.compose.animation.core.tween(180)) }
        placeholder = null
    }
}

@Composable
private fun PhotoWorkspace(
    uri: Uri, initial: PhotoProjectData?, onClose: () -> Unit, onLoad: (String) -> Unit,
    /** Opened with SKIP (no photo yet, uri = Uri.EMPTY): pick one from the gallery / files. */
    onAddGallery: () -> Unit = {}, onAddFiles: () -> Unit = {}, onAddCamera: () -> Unit = {},
    /** Switch to another photo of the filmstrip (index in [PhotoSession.items]). */
    onOpenSession: (Int) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var base by remember { mutableStateOf<Bitmap?>(null) }
    /** Which picture [base] was loaded from (set once that photo — and its RAW data — has loaded). */
    var baseFor by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var edit by remember {
        // Photos taken with the editor's camera start without the remembered watermark (add one by hand if wanted).
        mutableStateOf(editOf(context, initial, PhotoSession.items.none { it.camera == true && it.uri == uri.toString() }))
    }
    // A filmstrip photo's hidden edit is not a named project: SAVE makes a separate one.
    var projectId by remember { mutableStateOf(initial?.id?.takeIf { initial.session != true && initial.autosave != true }) }
    var projectName by remember { mutableStateOf(initial?.name ?: "") }
    var askName by remember { mutableStateOf(false) }
    var showShortcuts by remember { mutableStateOf(false) }
    var showLoad by remember { mutableStateOf(false) }
    if (showLoad) LoadProjectDialog(false, onOpen = { showLoad = false; onLoad(it) }, onDismiss = { showLoad = false })
    var panel by remember { mutableStateOf(PhotoPanel.PRESETS) }
    var comparing by remember { mutableStateOf(false) }
    // Develop tools: eyedropper (1 = white balance, 2 = Point Color), clipping warning, sharpening mask, zoom / 1:1 loupe.
    var picking by remember { mutableStateOf(0) }
    var pendingObject by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var pendingErasePick by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    // Targeted adjustment (drag on the photo): 6 = tone curve, 7 = HSL mixer ([tatMode] hue / sat / lum).
    var tatMode by remember { mutableStateOf(1) }
    var tatWeights by remember { mutableStateOf<FloatArray?>(null) }
    var dustView by remember { mutableStateOf(false) }
    var dustSens by remember { mutableStateOf(0.5f) }
    var dustImg by remember { mutableStateOf<Bitmap?>(null) }
    var proofImg by remember { mutableStateOf<Bitmap?>(null) }
    var uprightBusy by remember { mutableStateOf(false) }
    var clipWarn by remember { mutableStateOf(false) }
    var sharpMaskView by remember { mutableStateOf(false) }
    var zoomK by remember { mutableStateOf(1f) }
    var panX by remember { mutableStateOf(0f) }
    var panY by remember { mutableStateOf(0f) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var clipImg by remember { mutableStateOf<Bitmap?>(null) }
    var gamutImg by remember { mutableStateOf<Bitmap?>(null) }
    var lensBusy by remember { mutableStateOf(false) }
    var chartFirst by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    // Reference view: the reference photo (its edited look when it has one), loaded when chosen.
    var refImg by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(RefView.uri) {
        val k = RefView.uri
        refImg = if (k == null) null else withContext(Dispatchers.IO) {
            try {
                val sp = PhotoSession.items.firstOrNull { it.key == k }
                sp?.projectId?.let { id -> ProjectStore.thumbFile(context, id)?.let { f -> BitmapFactory.decodeFile(f.path) } }
                    ?: GradeIO.decode(context, Uri.parse(k.substringBefore('#')), 1600)
            } catch (_: Throwable) { null }
        }
    }
    val pickRef = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u -> if (u != null) { RefView.uri = u.toString(); RefView.shown = true } }
    var sharpImg by remember { mutableStateOf<Bitmap?>(null) }
    var loupe by remember { mutableStateOf<Bitmap?>(null) }
    // Before / after split view with a draggable divider.
    var split by remember { mutableStateOf(false) }
    var splitX by remember { mutableStateOf(0.5f) }
    var beforeImg by remember { mutableStateOf<Bitmap?>(null) }
    // Panels menu (layout icon next to the settings gear): which parts of the editor are shown.
    val showScope = com.dronedirector.veyrocutpro.ui.panelOn("pe.scope")
    val showControls = com.dronedirector.veyrocutpro.ui.panelOn("pe.controls")
    val showCopyBar = com.dronedirector.veyrocutpro.ui.panelOn("pe.copypaste")
    val showCompare = com.dronedirector.veyrocutpro.ui.panelOn("pe.compare")
    var paneVersion by remember { mutableStateOf(0) }
    var hist by remember { mutableStateOf<Array<FloatArray>?>(null) }
    var busy by remember { mutableStateOf(PhotoExportJob.active) }
    LaunchedEffect(PhotoExportJob.active) { if (!PhotoExportJob.active) busy = false }
    var exportT0 by remember { mutableStateOf(0L) }
    var exportEstMs by remember { mutableStateOf(0L) }
    var batchN by remember { mutableStateOf(0) }
    var batchI by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    if (showSettings) EditorSettingsDialog("Photo editor", com.dronedirector.veyrocutpro.ui.AppScreen.PHOTO_EDITOR) { showSettings = false }
    val undo = remember { mutableStateListOf(edit) }
    val wmOn = WatermarkMemory.isOn(context, false)
    LaunchedEffect(edit.watermark, wmOn) { if (wmOn && edit.watermark != null) WatermarkMemory.save(context, false, edit.watermark) }
    var undoPos by remember { mutableStateOf(0) }
    // Snapshots: named versions of this edit you can jump between (History panel).
    val snapshots = remember { mutableStateListOf<Pair<String, PhotoEdit>>() }

    // Export options
    var format by remember { mutableStateOf(initial?.format ?: 0) }       // JPEG, PNG
    var quality by remember { mutableStateOf(initial?.quality ?: 1f) }
    var sizeIdx by remember { mutableStateOf(initial?.sizeIdx ?: 0) }       // see `sizes`
    var aiScale by remember { mutableStateOf(0) }                          // 0 = off, 2, 4
    var keepExif by remember { mutableStateOf(true) }
    var stripGps by remember { mutableStateOf(false) }
    val tools = remember { LocalToolsState() }
    // Retouch panel: 0 liquify, 1 skin, 2 relight; liquify brush.
    var retouchTab by remember { mutableStateOf(0) }
    var liqTool by remember { mutableStateOf(0) }
    var liqSize by remember { mutableStateOf(0.08f) }
    var liqStrength by remember { mutableStateOf(0.5f) }
    // AI masks (subject / sky) are kept as grey pictures in the app's files.
    val aiMasks = remember { mutableStateMapOf<String, Bitmap>() }
    LaunchedEffect((edit.masks.flatMap { m -> listOfNotNull(m.maskFile) + m.subs.orEmpty().mapNotNull { it.maskFile } } + listOfNotNull(edit.bg.maskFile, edit.sky.maskFile, edit.sky.imagePath, edit.lensBlur?.depthFile) + edit.retouch.files())) {
        for (f in (edit.masks.flatMap { m -> listOfNotNull(m.maskFile) + m.subs.orEmpty().mapNotNull { it.maskFile } } + listOfNotNull(edit.bg.maskFile, edit.sky.maskFile, edit.sky.imagePath, edit.lensBlur?.depthFile) + edit.retouch.files())) if (aiMasks[f] == null) withContext(Dispatchers.IO) { BitmapFactory.decodeFile(f) }?.let { aiMasks[f] = it }
    }
    fun saveProject(name: String) {
        val e = edit
        val p = PhotoProjectData(
            id = projectId ?: java.util.UUID.randomUUID().toString(), name = name, uri = uri,
            grade = e.grade, detail = e.detail, geometry = e.geometry, watermark = e.watermark,
            format = format, quality = quality, sizeIdx = sizeIdx, masks = e.masks, spots = e.spots, lens = e.lens, bg = e.bg, baseFile = e.baseFile, texts = e.texts, sky = e.sky, raw = e.raw, lensBlur = e.lensBlur, retouch = e.retouch,
        )
        val thumb = preview
        scope.launch {
            val saved = withContext(Dispatchers.IO) { try { ProjectStore.savePhoto(context, p, thumb) } catch (_: Throwable) { null } }
            if (saved != null) { projectId = saved.id; projectName = saved.name; message = "Project \"${saved.name}\" saved" }
            else message = "Couldn't save the project"
        }
    }
    if (askName) ProjectNameDialog(
        projectName.ifBlank { ProjectStore.defaultName(false) },
        onSave = { askName = false; projectId = null; saveProject(it) }, onDismiss = { askName = false },
        video = false, currentId = projectId,
        onOverwrite = { id, n -> askName = false; projectId = id; saveProject(n) },
    )
    // Timed autosave (gear → AUTOSAVE): a full project copy every few minutes when something changed.
    val asMinutes = remember(AutoSave.changes) { AutoSave.minutes(context, false) }
    LaunchedEffect(asMinutes, uri) {
        if (asMinutes <= 0 || uri == Uri.EMPTY) return@LaunchedEffect
        var last: PhotoEdit? = initial?.let { null }
        while (true) {
            delay(asMinutes * 60_000L)
            val e = edit
            if (e == last || undo.size <= 1) continue
            last = e
            val p = PhotoProjectData(
                name = AutoSave.nameFor(projectName.ifBlank { uri.lastPathSegment?.substringAfterLast('/') ?: "" }), uri = uri,
                grade = e.grade, detail = e.detail, geometry = e.geometry, watermark = e.watermark,
                format = format, quality = quality, sizeIdx = sizeIdx, masks = e.masks, spots = e.spots, lens = e.lens, bg = e.bg, baseFile = e.baseFile, texts = e.texts, sky = e.sky, raw = e.raw, lensBlur = e.lensBlur, retouch = e.retouch,
            )
            val thumb = preview
            withContext(Dispatchers.IO) { try { AutoSave.savePhoto(context, p, thumb) } catch (_: Throwable) {} }
        }
    }
    // Long edge in pixels; the last one is the photo's own full resolution (no limit).
    val sizes = listOf(4800, 4096, 2560, 1920, 1080, 2048, 3840, 30000)

    // ---- Filmstrip: this photo's edit is kept automatically before switching or leaving.
    val sessionIdx = PhotoSession.current.takeIf { i ->
        PhotoSession.items.getOrNull(i)?.let { sp -> sp.uri == uri.toString() || (uri.scheme == "file" && sp.copy != null && uri.path?.endsWith("/" + sp.copy.removeSuffix(".gz")) == true) } == true
    } ?: -1
    /** The photo as the catalog knows it (its original uri, even when the catalog's copy is open). */
    val ownerUri = PhotoSession.items.getOrNull(sessionIdx)?.uri ?: uri.toString()
    fun sessionSave() {
        val sp = PhotoSession.items.getOrNull(sessionIdx) ?: return
        val changed = undo.size > 1 || sp.edited
        if (!changed) return
        val e = edit
        val p = PhotoProjectData(
            id = sp.projectId ?: java.util.UUID.randomUUID().toString(), name = sp.name, uri = uri,
            grade = e.grade, detail = e.detail, geometry = e.geometry, watermark = e.watermark,
            format = format, quality = quality, sizeIdx = sizeIdx, masks = e.masks, spots = e.spots, lens = e.lens, bg = e.bg, baseFile = e.baseFile, texts = e.texts, sky = e.sky, raw = e.raw, lensBlur = e.lensBlur, retouch = e.retouch,
            session = true,
        )
        try {
            val saved = ProjectStore.savePhoto(context, p, preview)
            PhotoSession.update(context, sessionIdx) { it.copy(projectId = saved.id, edited = true, version = System.currentTimeMillis()) }
        } catch (_: Throwable) {}
    }
    fun switchTo(i: Int) { if (busy) return; sessionSave(); onOpenSession(i) }
    // The edit is kept in the catalog as you work (a moment after each change), not only when leaving.
    LaunchedEffect(edit) { delay(2500); sessionSave() }
    androidx.compose.runtime.DisposableEffect(Unit) {
        Catalogs.beforeSwitch = { sessionSave() }
        onDispose { Catalogs.beforeSwitch = null }
    }
    fun closeEditor() { sessionSave(); onClose() }

    BackHandler { if (!busy) closeEditor() }
    val pickLogo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) edit = edit.copy(watermark = Layer(kind = LayerKind.LOGO, imageUri = u, size = 0.1f, x = 0.86f, y = 0.9f, opacity = 0.85f, wholeVideo = true))
    }

    // The photo being edited: the original, or its AI-erased copy.
    fun sourceUri(e: PhotoEdit): Uri = e.baseFile?.let { Uri.fromFile(java.io.File(it)) } ?: uri
    LaunchedEffect(ExportOpts.gamut, preview) { val pv = preview; gamutImg = if (ExportOpts.gamut != 0 && pv != null) withContext(Dispatchers.Default) { ExportOpts.gamutOverlay(pv) } else null }
    LaunchedEffect(clipWarn, preview) { val pv = preview; clipImg = if (clipWarn && pv != null) withContext(Dispatchers.Default) { Develop.clipOverlay(pv) } else null }
    LaunchedEffect(dustView, dustSens, preview, panel) { val pv = preview; dustImg = if (dustView && panel == PhotoPanel.HEAL && pv != null) withContext(Dispatchers.Default) { Upright.visualise(pv, dustSens) } else null }
    LaunchedEffect(ExportOpts.proof, preview, IccProof.on, IccProof.intent, IccProof.paper, IccProof.profile) {
        val pv = preview
        proofImg = if (pv == null) null
            else if (IccProof.on && IccProof.profile != null) withContext(Dispatchers.Default) { try { IccProof.apply(pv) } catch (_: Throwable) { null } }
            else if (ExportOpts.proof) withContext(Dispatchers.Default) { ExportOpts.proofView(pv) } else null
    }
    LaunchedEffect(Unit) { ExportOpts.load(context) }
    LaunchedEffect(sharpMaskView, preview, edit.detail.sharpenMasking) { val pv = preview; sharpImg = if (sharpMaskView && pv != null) withContext(Dispatchers.Default) { Develop.sharpenMaskPreview(pv, edit.detail) } else null }
    fun resetZoom() { zoomK = 1f; panX = 0f; panY = 0f }
    LaunchedEffect(panel, split) { if (panel == PhotoPanel.MASKS || panel == PhotoPanel.HEAL || panel == PhotoPanel.RETOUCH || split) resetZoom(); if (panel != PhotoPanel.COLOR && panel != PhotoPanel.MASKS && panel != PhotoPanel.EFFECTS) picking = 0 }
    /** A tap on the photo (box coordinates) → pixel of the shown picture, undoing padding, fit and zoom. */
    fun tapToPixel(o: androidx.compose.ui.geometry.Offset, img: Bitmap, padPx: Float): Pair<Int, Int>? {
        val bw = viewSize.width.toFloat(); val bh = viewSize.height.toFloat()
        if (bw < 2f || bh < 2f) return null
        val lx = o.x - padPx; val ly = o.y - padPx
        val ux = (lx - bw / 2f - panX) / zoomK + bw / 2f; val uy = (ly - bh / 2f - panY) / zoomK + bh / 2f
        val sc = minOf(bw / img.width, bh / img.height)
        val ox = (bw - img.width * sc) / 2f; val oy = (bh - img.height * sc) / 2f
        val px = ((ux - ox) / sc).toInt(); val py = ((uy - oy) / sc).toInt()
        return if (px in 0 until img.width && py in 0 until img.height) px to py else null
    }
    fun pickAt(o: androidx.compose.ui.geometry.Offset, padPx: Float) {
        val img = preview ?: return
        val (px, py) = tapToPixel(o, img, padPx) ?: return
        val c = Develop.sample(img, px, py, (img.width / 300).coerceAtLeast(2))
        if (picking == 1) {
            val (t, ti) = Develop.wbFromSample(c[0], c[1], c[2], edit.grade.temperature, edit.grade.tint)
            edit = edit.copy(grade = edit.grade.copy(temperature = t, tint = ti)); message = "White balance set from the picked spot"
        } else if (picking == 3) {
            // Masks are made on the uncropped photo; with no crop the tap maps straight across.
            pendingObject = px.toFloat() / img.width to py.toFloat() / img.height
            if (!edit.geometry.isIdentity) message = "Tip: the object pick works best before cropping"
        } else if (picking == 8) {
            chartFirst = px.toFloat() / img.width to py.toFloat() / img.height
            picking = 9; message = "Now tap the middle of the last patch (black)"
            return
        } else if (picking == 9) {
            val f = chartFirst; val b = base
            picking = 0
            if (f != null && b != null) {
                val res = ColorChart.solve(b, f.first, f.second, px.toFloat() / img.width, py.toFloat() / img.height)
                if (res == null) message = "Couldn't read the chart — tap the middle of the brown patch first, then the black one"
                else {
                    edit = edit.copy(grade = edit.grade.copy(chart = res.matrix, exposure = 0f, temperature = 0f, tint = 0f))
                    message = "Calibrated: colour error %.1f → %.1f. Save it as a preset (Presets → MY PRESETS) to use it for other photos in this light.".format(res.errBefore, res.errAfter)
                }
            }
            return
        } else if (picking == 5) {
            pendingErasePick = px.toFloat() / img.width to py.toFloat() / img.height
            return
        } else if (picking == 4) {
            val lb = edit.lensBlur
            val d = lb?.depthFile?.let { aiMasks[it] }
            if (lb != null && d != null) { edit = edit.copy(lensBlur = lb.copy(focus = LensBlurFx.depthAt(d, px.toFloat() / img.width, py.toFloat() / img.height))); message = "Focus set" }
        } else if (picking == 2) {
            val hsv = FloatArray(3); android.graphics.Color.RGBToHSV(c[0].toInt(), c[1].toInt(), c[2].toInt(), hsv)
            edit = edit.copy(grade = edit.grade.copy(pointColors = (edit.grade.pointColors ?: emptyList()) + PointColor(hsv[0], hsv[1], hsv[2])))
            message = "Colour picked — adjust it in POINT COLOR"
        }
        picking = 0
    }
    /** Targeted adjustment: what's under the finger decides which tones (curve) or colours (mixer) a drag changes. */
    fun tatStart(o: androidx.compose.ui.geometry.Offset, padPx: Float) {
        val img = preview ?: return
        val (px, py) = tapToPixel(o, img, padPx) ?: return
        val c = Develop.sample(img, px, py, (img.width / 300).coerceAtLeast(2))
        if (picking == 6) {
            val l = (0.2126f * c[0] + 0.7152f * c[1] + 0.0722f * c[2]) / 255f
            // Shadows, darks, lights, highlights: soft quarters of the tonal range.
            val w = FloatArray(4) { k -> (1f - kotlin.math.abs(l - (0.125f + 0.25f * k)) / 0.3f).coerceAtLeast(0f) }
            val sum = w.sum().coerceAtLeast(1e-3f); tatWeights = FloatArray(4) { w[it] / sum }
            message = "Drag up or down to brighten or darken these tones"
        } else {
            val hsv = FloatArray(3); android.graphics.Color.RGBToHSV(c[0].toInt(), c[1].toInt(), c[2].toInt(), hsv)
            val w = FloatArray(8) { k -> val d = (kotlin.math.abs(hsv[0] - HSL_CENTERS[k]) % 360f).let { if (it > 180f) 360f - it else it }; (1f - d / 45f).coerceAtLeast(0f) }
            val sum = w.sum()
            tatWeights = if (sum < 1e-3f || hsv[1] < 0.06f) null else FloatArray(8) { w[it] / sum }
            message = if (tatWeights == null) "That spot has almost no colour — try a more colourful one" else
                "Drag up or down to change the " + listOf("hue", "saturation", "luminance")[tatMode] + " of " + HSL_NAMES[w.indices.maxBy { w[it] }].lowercase()
        }
    }
    fun tatDrag(frac: Float) {
        val w = tatWeights ?: return
        val d = -frac * 1.6f
        val g = edit.grade
        edit = if (picking == 6) edit.copy(grade = g.copy(
            curveShadows = (g.curveShadows + d * w[0]).coerceIn(-1f, 1f), curveDarks = (g.curveDarks + d * w[1]).coerceIn(-1f, 1f),
            curveLights = (g.curveLights + d * w[2]).coerceIn(-1f, 1f), curveHighlights = (g.curveHighlights + d * w[3]).coerceIn(-1f, 1f),
        )) else edit.copy(grade = g.copy(hsl = g.hsl.mapIndexed { k, b ->
            val v = d * w[k]
            when (tatMode) { 0 -> b.copy(hue = (b.hue + v).coerceIn(-1f, 1f)); 1 -> b.copy(sat = (b.sat + v).coerceIn(-1f, 1f)); else -> b.copy(lum = (b.lum + v).coerceIn(-1f, 1f)) }
        }))
    }
    var refMatching by remember { mutableStateOf(false) }
    /** Matches this photo's light and colour (exposure, white balance, contrast, saturation) to the reference. */
    fun matchRef() {
        val r = refImg ?: return; val b = base ?: return
        refMatching = true
        scope.launch {
            val g = withContext(Dispatchers.Default) { try { ShotMatch.matchPixels(ShotMatch.pixels(r), null, ShotMatch.pixels(b), edit.grade) } catch (_: Throwable) { null } }
            refMatching = false
            if (g != null) { edit = edit.copy(grade = g); message = "Matched to the reference — undo goes back" } else message = "Couldn't match the reference"
        }
    }
    // .xmp presets / sidecars: import (as your presets, the first one is applied) and export.
    val pickXmp = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { list ->
        var n = 0; var first: Pair<ProGrade, Detail>? = null
        for (u in list) {
            val t = DevPresets.readText(context, u) ?: continue
            val res = DevPresets.parseXmp(t) ?: continue
            val name = DevPresets.nameOf(t, u.lastPathSegment?.substringAfterLast('/') ?: "Preset")
            UserPresets.init(context); UserPresets.add(name, res.first, res.second); n++
            if (first == null) first = DevPresets.parseXmp(t, edit.grade, edit.detail)
        }
        first?.let { (g, d) -> edit = edit.copy(grade = g, detail = d.copy(aiDenoise = edit.detail.aiDenoise)) }
        message = if (n == 0) "No develop settings found in that file" else "Imported $n preset" + (if (n == 1) "" else "s") + " (in MY PRESETS); the first one is applied"
    }
    fun exportXmp() {
        val name = projectName.ifBlank { "AlcorCut look " + java.text.SimpleDateFormat("d MMM HH.mm", java.util.Locale.getDefault()).format(java.util.Date()) }
        val e = edit
        scope.launch {
            val where = withContext(Dispatchers.IO) { DevPresets.savePreset(context, name, DevPresets.presetXmp(name, e.grade, e.detail)) }
            message = where?.let { "Preset saved: $it" } ?: "Couldn't save the preset"
        }
    }
    // Never a black viewer while a photo loads: show the last render of this photo and edit, its
    // project thumbnail, or the camera's colour-matched embedded preview, then fade to the real render.
    val vw = remember { ViewerState(PhotoSession.items.getOrNull(sessionIdx)?.key ?: initial?.id?.let { "p:$it" } ?: uri.toString(), PhotoViewer.signature(edit)).also { v -> if (uri != Uri.EMPTY) v.placeholder = PhotoViewer.rendered(v.key, v.sig) } }
    LaunchedEffect(Unit) {
        if (vw.placeholder != null || uri == Uri.EMPTY) return@LaunchedEffect
        val e0 = edit
        val ph = withContext(Dispatchers.IO) { vw.standIn(context, uri, initial?.id, e0.baseFile == null) }
        if (preview == null && ph != null) vw.placeholder = ph
    }
    // Fade the finished render in over the stand-in, then drop the stand-in.
    LaunchedEffect(preview != null) { if (preview != null) vw.reveal() }
    // RAW (DNG): the sensor data, developed by the editor itself (see RawDng). Null for other photos.
    var rawMeta by remember { mutableStateOf<RawDng.Meta?>(null) }
    var rawLin by remember { mutableStateOf<RawDng.Linear?>(null) }
    LaunchedEffect(uri, edit.baseFile) {
        if (uri == Uri.EMPTY && edit.baseFile == null) return@LaunchedEffect   // empty editor (SKIP)
        val src = sourceUri(edit)
        var note: String? = null
        // Shared with the filmstrip read-ahead: a neighbour read ahead is ready (or finishing) already.
        val r = if (edit.baseFile != null) null else try { PhotoViewer.loadRaw(context, src).await() }
            catch (t: RawDng.UnsupportedRaw) { note = "${t.message} — opened as a normal photo"; null }
            catch (_: OutOfMemoryError) { note = "This RAW is too big for the phone's memory — opened as a normal photo"; null }
            catch (t: Throwable) { if (t is kotlinx.coroutines.CancellationException) throw t; null }
        rawMeta = r?.meta; rawLin = r?.lin
        if (r?.meta?.shadingEstimated == true && note == null) note = "This RAW has no lens-shading data — its dark corners were evened out automatically (Light → Lens corrections from the file turns it off)"
        note?.let { message = it }
        val b = r?.base ?: withContext(Dispatchers.IO) { GradeIO.decode(context, src, PhotoViewer.viewerPx(context)) }
        if (b == null) failed = true else { base = b; baseFor = src.toString() }
    }
    /** True while the edit works on the RAW data (not after AI erase, which makes a normal copy). */
    fun rawOn(e: PhotoEdit) = rawMeta != null && rawLin != null && e.baseFile == null
    // 1:1 loupe: when zoomed in, a render of the photo at its full size (up to 6000 px) replaces the preview.
    var loupeSrc by remember { mutableStateOf<Pair<String, Bitmap>?>(null) }
    LaunchedEffect(zoomK > 1.6f, edit, aiMasks.size) {
        if (zoomK <= 1.6f) { loupe = null; return@LaunchedEffect }
        delay(500)
        val e = edit
        if (rawOn(e)) return@LaunchedEffect
        val key = sourceUri(e).toString()
        loupe = withContext(Dispatchers.Default) {
            try {
                val hi = loupeSrc?.takeIf { it.first == key }?.second ?: (GradeIO.decode(context, sourceUri(e), 6000) ?: return@withContext null).also { loupeSrc = key to it }
                val tables = if (e.grade.isIdentity) null else e.grade.tables()
                var o = ImageOps.renderPhoto(hi, e.grade, e.detail.copy(aiDenoise = 0f), e.geometry, tables, e.lens, e.spots, e.masks, aiMasks.toMap(), e.bg, e.sky, e.lensBlur, e.retouch)
                for (t in e.texts) o = stampLayer(context, o, t)
                e.watermark?.let { o = stampLayer(context, o, it) }
                o
            } catch (_: OutOfMemoryError) { null } catch (_: Throwable) { null }
        }
    }
    /** Developed RAW at full quality for export / AI steps; [sliders] false = neutral (they're applied later in 8-bit). */
    fun rawExport(e: PhotoEdit, maxPx: Int, sliders: Boolean = true): Bitmap {
        val g = e.grade
        return if (sliders) RawDng.export(context, rawMeta!!, e.raw, g.exposure, g.highlights, g.shadows, g.whites, g.blacks, g.temperature, g.tint, maxPx)
        else RawDng.export(context, rawMeta!!, e.raw, 0f, 0f, 0f, 0f, 0f, 0f, 0f, maxPx)
    }
    // Live preview: while a slider moves, a small copy is re-rendered continuously (never thrown
    // away half-way); once you pause, the full-size preview follows.
    val renderReq = remember { kotlinx.coroutines.channels.Channel<PhotoEdit>(kotlinx.coroutines.channels.Channel.CONFLATED) }
    LaunchedEffect(edit) { renderReq.trySend(edit) }
    // AI noise reduction: the AI cleans the preview image once; the strength slider then just blends.
    // Both results (preview and full size) are kept on the phone, so reopening the photo — or the app —
    // shows the cleaned photo straight away instead of running the AI again.
    var aiBase by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(edit.baseFile) { aiBase = null }
    val aiOn = edit.detail.aiDenoise > 0f
    // Only once this photo (and, for RAW, its sensor data) has finished loading.
    val loaded = base != null && baseFor == sourceUri(edit).toString()
    LaunchedEffect(base, aiOn, loaded) {
        val b = base ?: return@LaunchedEffect
        if (!aiOn || aiBase != null || !loaded) return@LaunchedEffect
        val pk = edit.aiPreviewKey(ownerUri, sourceUri(edit).toString(), rawOn(edit), b.width, b.height)
        withContext(Dispatchers.IO) { AiDenoiseCache.load(context, pk) }?.let { aiBase = it; return@LaunchedEffect }
        val res = withContext(Dispatchers.IO) {
            try {
                val px = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
                AiDenoiser.denoise(context, px, b.width, b.height)?.let { Bitmap.createBitmap(it, b.width, b.height, Bitmap.Config.ARGB_8888) } ?: "unavailable"
            } catch (_: AiDenoiser.Stopped) { "stopped" } catch (t: Throwable) { if (t.cause is AiDenoiser.Stopped) "stopped" else "unavailable" }
        }
        when (res) {
            is Bitmap -> { aiBase = res; withContext(Dispatchers.IO) { AiDenoiseCache.store(context, pk, res) } }
            "stopped" -> { edit = edit.copy(detail = edit.detail.copy(aiDenoise = 0f)); message = "AI noise reduction stopped" }
            else -> { edit = edit.copy(detail = edit.detail.copy(aiDenoise = 0f)); message = "AI noise reduction isn't available on this phone" }
        }
    }
    // The same AI at full size, once, in the background while you edit: kept, and used by the export
    // (so exporting never runs the AI again). Redone only when what goes into it changes (RAW develop).
    val aiKey = if (aiOn && loaded && !com.dronedirector.veyrocutpro.camera.CloudAi.usePhotos(context)) edit.aiKey(ownerUri, sourceUri(edit).toString(), rawOn(edit)) else null
    LaunchedEffect(aiKey) {
        val k = aiKey ?: return@LaunchedEffect
        if (AiDenoiseCache.has(context, k)) return@LaunchedEffect
        delay(1200)   // let RAW sliders settle first
        val e0 = edit
        AiDenoiseCache.prepare(context, k, sourceUri(e0).toString()) { if (rawOn(e0)) rawExport(e0, 30000) else GradeIO.decode(context, sourceUri(e0), 30000) }
    }
    // Heal works on the whole, uncropped photo so taps land where they should.
    val healMode = rememberUpdatedState(panel == PhotoPanel.HEAL || (panel == PhotoPanel.RETOUCH && retouchTab == 0))
    LaunchedEffect(healMode.value, aiMasks.size) { renderReq.trySend(edit) }
    LaunchedEffect(base, aiBase, rawLin) {
        val b = base ?: return@LaunchedEffect
        val ai = aiBase
        val rl = rawLin; val rm = rawMeta
        val rawSmall = rl?.let { withContext(Dispatchers.Default) { RawDng.shrink(it, 640) } }
        fun mixed(src: Bitmap, aiSrc: Bitmap?, t: Float): Bitmap {
            if (aiSrc == null || t <= 0f || aiSrc.width != src.width || aiSrc.height != src.height) return src
            val n = src.width * src.height
            val a = IntArray(n).also { src.getPixels(it, 0, src.width, 0, 0, src.width, src.height) }
            val c = IntArray(n).also { aiSrc.getPixels(it, 0, src.width, 0, 0, src.width, src.height) }
            return Bitmap.createBitmap(AiDenoiser.blend(a, c, t), src.width, src.height, Bitmap.Config.ARGB_8888)
        }
        fun scaleDown(x: Bitmap): Bitmap {
            val k = 640f / maxOf(x.width, x.height)
            return if (k < 1f) Bitmap.createScaledBitmap(x, (x.width * k).toInt().coerceAtLeast(1), (x.height * k).toInt().coerceAtLeast(1), true) else x
        }
        val small = withContext(Dispatchers.Default) { scaleDown(b) }
        val aiSmall = withContext(Dispatchers.Default) { ai?.let { scaleDown(it) } }
        // RAW + AI noise reduction: the AI cleaned the neutral develop once; its noise-free difference is reused.
        fun residual(dev: Bitmap, neutral: Bitmap, aiSrc: Bitmap?, t: Float): Bitmap {
            if (aiSrc == null || t <= 0f || aiSrc.width != neutral.width || aiSrc.height != neutral.height || dev.width != neutral.width || dev.height != neutral.height) return dev
            val n = dev.width * dev.height
            val d = IntArray(n).also { dev.getPixels(it, 0, dev.width, 0, 0, dev.width, dev.height) }
            val a = IntArray(n).also { neutral.getPixels(it, 0, dev.width, 0, 0, dev.width, dev.height) }
            val c = IntArray(n).also { aiSrc.getPixels(it, 0, dev.width, 0, 0, dev.width, dev.height) }
            for (i in 0 until n) {
                fun ch(sh: Int): Int { val v = (d[i] shr sh) and 0xFF; val nz = ((a[i] shr sh) and 0xFF) - ((c[i] shr sh) and 0xFF); return (v - nz * t).toInt().coerceIn(0, 255) }
                d[i] = (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
            }
            return Bitmap.createBitmap(d, dev.width, dev.height, Bitmap.Config.ARGB_8888)
        }
        fun draw(src: Bitmap, e: PhotoEdit): Bitmap {
            val isSmall = src === small
            val raw = rl != null && rm != null && e.baseFile == null
            val s0 = if (raw) {
                val g = e.grade
                var dev = RawDng.develop(if (isSmall && rawSmall != null) rawSmall else rl!!, rm!!, e.raw, g.exposure, g.highlights, g.shadows, g.whites, g.blacks, g.temperature, g.tint)
                if (dev.width != src.width || dev.height != src.height) dev = Bitmap.createScaledBitmap(dev, src.width, src.height, true)
                if (e.detail.aiDenoise > 0f) residual(dev, src, if (isSmall) aiSmall else ai, e.detail.aiDenoise) else dev
            } else if (e.detail.aiDenoise > 0f) mixed(src, if (isSmall) aiSmall else ai, e.detail.aiDenoise) else src
            val eg = if (raw) e.grade.rawRest() else e.grade
            val tables = if (eg.isIdentity) null else eg.tables()
            val healNow = healMode.value
            val r = ImageOps.renderPhoto(s0, eg, e.detail, if (healNow) Geometry() else e.geometry, tables, e.lens, e.spots, if (healNow) null else e.masks, aiMasks.toMap(), if (healNow) null else e.bg, if (healNow) null else e.sky, if (healNow) null else e.lensBlur, if (healNow) e.retouch.copy(fsSmooth = 0f, fsTexture = 1f, light = 0f, shadow = 0f) else e.retouch)
            if (healNow) return r
            var o = r
            for (t in e.texts) o = stampLayer(context, o, t)
            return e.watermark?.let { stampLayer(context, o, it) } ?: o
        }
        renderReq.trySend(edit)
        var next: PhotoEdit? = null
        var first = true
        while (true) {
            val e = next ?: renderReq.receive()
            next = null
            // Opening with a stand-in on screen: go straight to the sharp render (no soft quick pass in between).
            val skipQuick = first && vw.placeholder != null && preview == null
            first = false
            if (!skipQuick) {
                preview = withContext(Dispatchers.Default) { draw(small, e) }
                // Still dragging? Keep going with the newest value.
                val newer = kotlinx.coroutines.withTimeoutOrNull(140) { renderReq.receive() }
                if (newer != null) { next = newer; continue }
            }
            val full = withContext(Dispatchers.Default) { draw(b, e) }
            preview = full
            if (!healMode.value && e.baseFile == edit.baseFile) PhotoViewer.store(context, vw.key, PhotoViewer.signature(e), full)
            if (!vw.readAhead) { vw.readAhead = true; if (sessionIdx >= 0) prefetchAround(context, sessionIdx) }
            hist = withContext(Dispatchers.Default) { ImageOps.histogram(full) }
        }
    }
    // "Before" for the split view: the original with the same crop, so both halves line up.
    LaunchedEffect(split, base, edit.geometry) {
        val b = base
        beforeImg = if (!split || b == null) null else withContext(Dispatchers.Default) { ImageOps.render(b, ProGrade(), Detail(), edit.geometry, null) }
    }
    // Undo history: record settled states.
    LaunchedEffect(edit) {
        delay(600)
        if (undo.getOrNull(undoPos) != edit) {
            while (undo.size > undoPos + 1) undo.removeAt(undo.lastIndex)
            undo.add(edit)
            if (undo.size > 50) undo.removeAt(0)
            undoPos = undo.lastIndex
        }
    }
    LaunchedEffect(message) { if (message != null) { delay(2600); message = null } }

    // Export recipe: several presets exported one after another.
    val recipeQueue = remember { mutableStateListOf<ExportOpts.Preset>() }
    fun applyPreset(p: ExportOpts.Preset) {
        format = p.format; quality = p.quality; sizeIdx = p.sizeIdx
        ExportOpts.space = p.space; ExportOpts.sharpenFor = p.sharpenFor; ExportOpts.sharpenAmount = p.sharpenAmount; ExportOpts.template = p.template; ExportOpts.presetName = p.name
    }
    fun export() {
        if (busy || PhotoExportJob.active) return
        busy = true
        PhotoExportJob.begin(context, "Exporting photo")
        exportT0 = System.currentTimeMillis(); batchN = 0; batchI = 0
        // Time estimate from earlier exports (ms per megapixel), or a first guess.
        val mp = exportMp(base, sizes[sizeIdx])
        val per = context.getSharedPreferences("procam", android.content.Context.MODE_PRIVATE).getFloat("pe_ms_per_mp", 250f)
        exportEstMs = (mp * per * (if (aiScale > 1) 4 else 1)).toLong()
        val e = edit
        PhotoExportJob.scope.launch {
            // The export runs in its own process (all of its memory free for the photo); see PhotoExportProcess.
            // AI noise reduction made while editing: let it finish, then the export simply uses it.
            if (e.detail.aiDenoise > 0f) AiDenoiseCache.await(e.aiKey(ownerUri, sourceUri(e).toString(), rawOn(e)))
            val req = PhotoExportReq(
                edit = ProjectStore.gson.toJson(e), uri = uri.toString(), owner = ownerUri, source = sourceUri(e).toString(), raw = rawOn(e),
                maxPx = sizes[sizeIdx], aiScale = aiScale, format = format, quality = quality, keepExif = keepExif, stripGps = stripGps,
                opts = ExportOpts.snapshot(),
            )
            val res = PhotoExportRemote.run(context, req)
            ExportOpts.seq = maxOf(ExportOpts.seq, res.seq); ExportOpts.save(context)
            if (res.sidecarFailed) message = "Photo saved, but the .xmp sidecar couldn't be written"
            val savedPx = res.savedPx; val wantPx = res.wantPx
            PhotoExportError.last = res.error
            val ok: Any? = when {
                res.stopped -> "stopped"
                res.saved == null -> null
                wantPx > savedPx -> "smaller"
                else -> res.saved
            }
            busy = false
            // Learn how long exports take on this phone (plain exports only, no AI steps).
            if (ok != null && ok != "stopped" && e.detail.aiDenoise <= 0f && aiScale <= 1) {
                val mpDone = exportMp(base, if (wantPx > 0) wantPx else sizes[sizeIdx])
                val took = (System.currentTimeMillis() - exportT0) / mpDone.coerceAtLeast(0.5f)
                val p = context.getSharedPreferences("procam", android.content.Context.MODE_PRIVATE)
                p.edit().putFloat("pe_ms_per_mp", p.getFloat("pe_ms_per_mp", took) * 0.6f + took * 0.4f).apply()
            }
            if (ok == null || ok == "stopped") recipeQueue.clear()
            message = when {
                ok == "stopped" -> "Export stopped"
                ok == "smaller" -> "Saved at your size ($wantPx px). The phone's memory was short, so the heaviest steps ran at $savedPx px"
                ok != null -> "Saved"
                else -> PhotoExportError.last?.let { "Couldn't export — $it" } ?: "Couldn't export — try a smaller size"
            }
            PhotoExportJob.end(context, message)
        }
    }

    LaunchedEffect(busy) {
        if (busy || recipeQueue.isEmpty()) return@LaunchedEffect
        delay(400)
        if (busy || recipeQueue.isEmpty()) return@LaunchedEffect
        val next = recipeQueue.removeAt(0)
        applyPreset(next); export()
    }

    // ---- AI selections (subject / sky), background and AI erase.
    val useCloud = com.dronedirector.veyrocutpro.camera.CloudAi.usePhotos(context)
    fun findMask(type: MaskType, done: (String) -> Unit) {
        val b = base ?: return
        message = if (type == MaskType.SKY) "Finding the sky…" else "Finding the subject…"
        scope.launch {
            val path = withContext(Dispatchers.IO) {
                try {
                    val m = if (type == MaskType.SKY) AiSegment.sky(context, b, useCloud) else AiSegment.subject(context, b, useCloud)
                    m?.let { AiSegment.saveMask(context, it) }
                } catch (_: Throwable) { null }
            }
            if (path == null) message = if (type == MaskType.SKY) "Couldn't find a sky" else
                "Couldn't find a subject" + if (!useCloud && !AiSegment.hasModel(context)) " — turn on Cloud AI for photos" else ""
            else { message = null; done(path) }
        }
    }
    /** The photo's AI depth map (made once, reused by depth masks and Lens Blur). */
    var depthBusy by remember { mutableStateOf(false) }
    fun withDepth(done: (String) -> Unit) {
        val have = edit.lensBlur?.depthFile ?: edit.masks.flatMap { listOf(it) + it.subs.orEmpty() }.firstOrNull { it.type == MaskType.DEPTH }?.maskFile
        if (have != null && java.io.File(have).exists()) { done(have); return }
        val b = base ?: return
        if (depthBusy) return
        depthBusy = true; message = "Working out the depth…"
        scope.launch {
            val path = withContext(Dispatchers.Default) { try { LensBlurFx.analyse(context, b) } catch (_: Throwable) { null } }
            depthBusy = false
            if (path == null) message = "Couldn't work out the depth" + (DepthModel.failed?.let { " ($it)" } ?: " — the AI model may still be downloading")
            else { message = null; done(path) }
        }
    }
    val aiMaskAction: ((MaskType) -> Unit)? = { type ->
        when (type) {
            MaskType.OBJECT -> { picking = 3; message = "Tap the object you want to select" }
            MaskType.DEPTH -> withDepth { path ->
                val pm = PhotoMask(type = MaskType.DEPTH, maskFile = path, lumLow = 0.6f, lumHigh = 1f)
                edit = edit.copy(masks = edit.masks + pm); tools.selected = pm.id
            }
            else -> findMask(type) { path ->
                val pm = PhotoMask(type = type, maskFile = path)
                edit = edit.copy(masks = edit.masks + pm)
                tools.selected = pm.id
            }
        }
    }
    /** People masks: finds the chosen parts of everyone in the photo (on the phone) and makes / updates the mask. */
    var peopleBusy by remember { mutableStateOf(false) }
    val peopleAction: (String?, List<Int>, (PhotoMask) -> PhotoMask) -> Unit = { id, parts, setup ->
        val b = base
        if (b != null && !peopleBusy) {
            peopleBusy = true
            message = "Finding " + parts.joinToString(", ") { PeopleMask.PARTS[it].lowercase() } + "…"
            scope.launch {
                val path = withContext(Dispatchers.Default) {
                    try { PeopleMask.make(context, b, parts)?.let { AiSegment.saveMask(context, it) } } catch (_: Throwable) { null }
                }
                peopleBusy = false
                if (path == null) message = if (PeopleMask.lastFaces == 0) "No people or faces found in this photo" else "Couldn't find that part in this photo"
                else {
                    message = null
                    val old = id?.let { k -> edit.masks.firstOrNull { it.id == k } }
                    if (old != null) edit = edit.copy(masks = edit.masks.map { if (it.id == old.id) setup(it.copy(parts = parts, maskFile = path)) else it })
                    else {
                        val pm = setup(PhotoMask(type = MaskType.PEOPLE, maskFile = path, parts = parts))
                        edit = edit.copy(masks = edit.masks + pm); tools.selected = pm.id
                    }
                }
            }
        }
    }
    /** Retouch → Skin: a People mask (face / body skin) used only by frequency separation. */
    fun skinMask(parts: List<Int>, done: (String) -> Unit) {
        val b = base ?: return
        if (peopleBusy) return
        peopleBusy = true; message = "Finding the skin…"
        scope.launch {
            val path = withContext(Dispatchers.Default) { try { PeopleMask.make(context, b, parts)?.let { AiSegment.saveMask(context, it) } } catch (_: Throwable) { null } }
            peopleBusy = false
            if (path == null) message = if (PeopleMask.lastFaces == 0) "No people or faces found in this photo" else "Couldn't find the skin in this photo"
            else { message = null; done(path) }
        }
    }
    /** Adds an AI selection to a mask as add / subtract / intersect. */
    val aiSubAction: (MaskType, Int, String) -> Unit = { type, combine, parent ->
        fun add(path: String) {
            val sub = PhotoMask(type = type, maskFile = path, combine = combine, lumLow = if (type == MaskType.DEPTH) 0.6f else 0.7f)
            edit = edit.copy(masks = edit.masks.map { if (it.id == parent) it.copy(subs = it.subs.orEmpty() + sub) else it })
        }
        if (type == MaskType.DEPTH) withDepth { add(it) } else findMask(type) { add(it) }
    }
    // Select Objects: the AI's features of the photo, made once per photo.
    var samCache by remember { mutableStateOf<Pair<Bitmap, Sam.Embedding>?>(null) }
    fun selectObjectAt(nx: Float, ny: Float) {
        val b = base ?: return
        message = "Selecting the object…"
        scope.launch {
            val path = withContext(Dispatchers.Default) {
                try {
                    val emb = samCache?.takeIf { it.first === b }?.second ?: (Sam.encode(context, b) ?: return@withContext null).also { samCache = b to it }
                    val pts = listOf(MaskPoint(nx, ny, 1))
                    val ans = Sam.decode(emb, pts) ?: return@withContext null
                    val i = Sam.pick(ans, pts, 0)
                    val bytes = Sam.toMask(ans.mask(i), emb, b.width, b.height)
                    val px = IntArray(bytes.size) { k -> val v = bytes[k].toInt() and 255; (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
                    AiSegment.saveMask(context, Bitmap.createBitmap(px, b.width, b.height, Bitmap.Config.ARGB_8888))
                } catch (_: Throwable) { null }
            }
            if (path == null) message = "Couldn't select that" + (Sam.failed?.let { " ($it)" } ?: "")
            else {
                val pm = PhotoMask(type = MaskType.OBJECT, maskFile = path)
                edit = edit.copy(masks = edit.masks + pm); tools.selected = pm.id; message = "Object selected"
            }
        }
    }
    LaunchedEffect(pendingObject) { pendingObject?.let { (x, y) -> pendingObject = null; selectObjectAt(x, y) } }
    var eraseMask by remember { mutableStateOf<PhotoMask?>(null) }
    // Things picked with a tap (AI selection) for AI Remove: their mask files, and a red view of them.
    var eraseObjects by remember { mutableStateOf<List<String>>(emptyList()) }
    var eraseTint by remember { mutableStateOf<Bitmap?>(null) }
    var eraseStatus by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(eraseObjects) {
        val list = eraseObjects
        eraseTint = if (list.isEmpty()) null else withContext(Dispatchers.IO) {
            try {
                var tw = 0; var th = 0; var px: IntArray? = null
                for (f in list) {
                    val mb = BitmapFactory.decodeFile(f) ?: continue
                    if (px == null) { tw = mb.width; th = mb.height; px = IntArray(tw * th) }
                    val s2 = if (mb.width == tw && mb.height == th) mb else Bitmap.createScaledBitmap(mb, tw, th, true)
                    val mp = IntArray(tw * th); s2.getPixels(mp, 0, tw, 0, 0, tw, th)
                    for (i in mp.indices) if (((mp[i] shr 16) and 255) > 100) px[i] = 0x99FF3030.toInt()
                }
                px?.let { Bitmap.createBitmap(it, tw, th, Bitmap.Config.ARGB_8888) }
            } catch (_: Throwable) { null }
        }
    }
    fun pickEraseObject(nx: Float, ny: Float) {
        val b = base ?: return
        eraseStatus = "Selecting…"
        scope.launch {
            val path = withContext(Dispatchers.Default) {
                try {
                    val emb = samCache?.takeIf { it.first === b }?.second ?: (Sam.encode(context, b) ?: return@withContext null).also { samCache = b to it }
                    val pts = listOf(MaskPoint(nx, ny, 1))
                    val ans = Sam.decode(emb, pts) ?: return@withContext null
                    val bytes = Sam.toMask(ans.mask(Sam.pick(ans, pts, 0)), emb, b.width, b.height)
                    val px = IntArray(bytes.size) { k -> val v = bytes[k].toInt() and 255; (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
                    AiSegment.saveMask(context, Bitmap.createBitmap(px, b.width, b.height, Bitmap.Config.ARGB_8888))
                } catch (_: Throwable) { null }
            }
            eraseStatus = null
            if (path == null) message = "Couldn't select that" + (Sam.failed?.let { " ($it)" } ?: "")
            else { eraseObjects = eraseObjects + path; if (eraseMask == null) eraseMask = PhotoMask(type = MaskType.BRUSH); message = "Selected — tap more things, paint, or tap REMOVE" }
        }
    }
    LaunchedEffect(pendingErasePick) { pendingErasePick?.let { (x, y) -> pendingErasePick = null; pickEraseObject(x, y) } }
    fun runErase() {
        val em = eraseMask ?: PhotoMask(type = MaskType.BRUSH)
        val objs = eraseObjects
        if ((em.strokes.isEmpty() && objs.isEmpty()) || busy) return
        busy = true
        picking = 0
        val e = edit
        scope.launch {
            // The painting model is downloaded once (on the phone; not needed with Cloud AI).
            if (!useCloud && !ModelStore.has(context, AiModel.INPAINT)) {
                eraseStatus = "Downloading the AI model (${AiModel.INPAINT.sizeMb} MB, once)…"
                val err = withContext(Dispatchers.IO) { ModelStore.ensure(context, AiModel.INPAINT, { p -> eraseStatus = "Downloading the AI model… $p%" }, { false }) }
                if (err != null) message = "$err — using the simpler fill"
            }
            eraseStatus = "Removing…"
            val path = withContext(Dispatchers.IO) {
                try {
                    // Full size (up to 4800 px on the long side) so the photo keeps its detail; only the removed parts change.
                    val full = (if (rawOn(e)) rawExport(e, 4000, sliders = false) else GradeIO.decode(context, sourceUri(e), 4800)) ?: return@withContext null
                    val fw = full.width; val fh = full.height
                    val wt = if (em.strokes.isEmpty()) FloatArray(fw * fh) else PhotoLocal.weights(em.copy(amount = 1f), fw, fh)
                    for (f in objs) {
                        val mb = BitmapFactory.decodeFile(f) ?: continue
                        val sc = Bitmap.createScaledBitmap(mb, fw, fh, true)
                        val mp = IntArray(fw * fh); sc.getPixels(mp, 0, fw, 0, 0, fw, fh)
                        if (sc !== mb) sc.recycle(); mb.recycle()
                        for (i in mp.indices) if (((mp[i] shr 16) and 255) > 100) wt[i] = 1f
                    }
                    var result: Bitmap? = null
                    if (useCloud) try {
                        val mask = Bitmap.createBitmap(IntArray(wt.size) { if (wt[it] > 0.25f) -1 else 0xFF000000.toInt() }, fw, fh, Bitmap.Config.ARGB_8888)
                        val jpeg = java.io.ByteArrayOutputStream().also { full.compress(Bitmap.CompressFormat.JPEG, 95, it) }.toByteArray()
                        val png = java.io.ByteArrayOutputStream().also { mask.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
                        val res = com.dronedirector.veyrocutpro.camera.CloudAi.erase(context, jpeg, png)
                        result = BitmapFactory.decodeByteArray(res, 0, res.size)?.let { if (it.width == fw && it.height == fh) it else Bitmap.createScaledBitmap(it, fw, fh, true) }
                    } catch (_: com.dronedirector.veyrocutpro.camera.CloudAi.Stopped) { return@withContext "stopped" } catch (_: Throwable) {}
                    if (result == null) result = try { PhotoErase.fill(context, full, wt) { p -> eraseStatus = "Removing… $p%" } } catch (_: OutOfMemoryError) { null }
                    if (result == null) {
                        // No AI model: the simpler fill, at a size the phone can manage.
                        val k = minOf(1f, 2400f / maxOf(fw, fh))
                        val sw = (fw * k).toInt(); val sh = (fh * k).toInt()
                        val small = Bitmap.createScaledBitmap(full, sw, sh, true)
                        val mask = Bitmap.createBitmap(IntArray(wt.size) { if (wt[it] > 0.25f) -1 else 0xFF000000.toInt() }, fw, fh, Bitmap.Config.ARGB_8888)
                        result = AiSegment.fill(small, Bitmap.createScaledBitmap(mask, sw, sh, true))
                    }
                    val dir = java.io.File(context.filesDir, "erased").apply { mkdirs() }
                    val f = java.io.File(dir, "erased_${System.currentTimeMillis()}.jpg")
                    f.outputStream().use { result.compress(Bitmap.CompressFormat.JPEG, 96, it) }
                    f.path
                } catch (_: Throwable) { null }
            }
            busy = false; eraseStatus = null
            when (path) {
                null -> message = "Couldn't remove that"
                "stopped" -> message = "AI Remove stopped"
                else -> { edit = edit.copy(baseFile = path); eraseMask = null; eraseObjects = emptyList(); message = "Removed — tap REMOVE again for more, or RESET to go back" }
            }
        }
    }
    // ---- Generative expand (Cloud AI, Bria Expand): a wider canvas with the empty parts filled in.
    var expandStatus by remember { mutableStateOf<String?>(null) }
    fun expandTo(aspect: Float, pos: Int, prompt: String) {
        if (busy || expandStatus != null) return
        if (!com.dronedirector.veyrocutpro.camera.CloudAi.usePhotos(context)) { message = "Turn on Cloud AI for photos (and add your fal.ai key) to use Expand"; return }
        val e = edit
        expandStatus = "Expanding…"
        scope.launch {
            val path = withContext(Dispatchers.IO) {
                try {
                    var full = (if (rawOn(e)) rawExport(e, 3000, sliders = false) else GradeIO.decode(context, sourceUri(e), 3000)) ?: return@withContext null
                    // Bria needs the canvas under 5000 × 5000 and the photo to stay a good share of it.
                    val w0 = full.width; val h0 = full.height
                    var cw: Int; var ch: Int
                    if (aspect <= 0f) { cw = w0; ch = h0 }
                    else if (aspect >= w0.toFloat() / h0) { ch = h0; cw = (h0 * aspect).toInt() } else { cw = w0; ch = (w0 / aspect).toInt() }
                    // Always add at least a little around the photo.
                    if (cw == w0 && ch == h0) { cw = (w0 * 1.25f).toInt(); ch = (h0 * 1.25f).toInt() }
                    val k = minOf(1f, 4800f / maxOf(cw, ch))
                    if (k < 1f) { val sc = Bitmap.createScaledBitmap(full, (w0 * k).toInt(), (h0 * k).toInt(), true); full.recycle(); full = sc; cw = (cw * k).toInt(); ch = (ch * k).toInt() }
                    val w = full.width; val h = full.height
                    val x = when (pos) { 1 -> 0; 2 -> cw - w; else -> (cw - w) / 2 }
                    val y = when (pos) { 3 -> 0; 4 -> ch - h; else -> (ch - h) / 2 }
                    val jpeg = java.io.ByteArrayOutputStream().also { full.compress(Bitmap.CompressFormat.JPEG, 95, it) }.toByteArray()
                    full.recycle()
                    val res = com.dronedirector.veyrocutpro.camera.CloudAi.expand(context, jpeg, cw, ch, w, h, x, y, prompt)
                    val dir = java.io.File(context.filesDir, "erased").apply { mkdirs() }
                    val f = java.io.File(dir, "expanded_${System.currentTimeMillis()}.jpg")
                    f.writeBytes(res)
                    f.path
                } catch (_: com.dronedirector.veyrocutpro.camera.CloudAi.Stopped) { "stopped" } catch (t: Throwable) { "error:" + (t.message ?: "") }
            }
            expandStatus = null
            when {
                path == null -> message = "Couldn't expand the photo"
                path == "stopped" -> message = "Expand stopped"
                path.startsWith("error:") -> message = "Couldn't expand the photo" + path.removePrefix("error:").takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty()
                else -> {
                    // The picture's shape changed: spots, masks, crop and depth no longer fit, so they are cleared.
                    edit = edit.copy(baseFile = path, spots = emptyList(), masks = emptyList(), geometry = Geometry(), lensBlur = null, bg = PhotoBackground(), sky = SkySwap(),
                        retouch = edit.retouch.copy(liquify = emptyList(), fsArea = 0, fsMaskFile = null, depthFile = null))
                    message = "Expanded — RESET under Heal → AI REMOVE goes back to the original"
                }
            }
        }
    }
    val aiEraseSection: @Composable () -> Unit = {
        SectionTitle("AI REMOVE") { if (edit.baseFile != null) edit = edit.copy(baseFile = null) }
        if (eraseMask == null) {
            Text("Remove bigger things — a person, a car, a sign, a power line. Tap them (the AI selects each one) or paint over them, then tap REMOVE. The AI paints in what was behind them, at the photo's full size.", color = Pro.Dim, fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "TAP TO SELECT", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
                    modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable { eraseMask = PhotoMask(type = MaskType.BRUSH); picking = 5 }.padding(10.dp),
                )
                Text(
                    "PAINT", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
                    modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable { eraseMask = PhotoMask(type = MaskType.BRUSH); picking = 0 }.padding(10.dp),
                )
            }
            if (edit.baseFile != null) Text("The photo has removed parts. Tap RESET above to go back to the original.", color = Pro.Dim, fontSize = 11.sp)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillChip("TAP TO SELECT", picking == 5) { picking = if (picking == 5) 0 else 5 }
                PillChip("PAINT", picking != 5) { picking = 0 }
            }
            if (picking != 5) EdSlider("Brush size", tools.brushSize, 0.01f..0.3f, 0.08f, ::pct) { tools.brushSize = it }
            if (eraseObjects.isNotEmpty()) Text("${eraseObjects.size} thing(s) selected (shown in red)", style = Pro.Label)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (busy) "REMOVING…" else "REMOVE", style = Pro.Value.copy(color = Color.Black, fontSize = 12.sp),
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Pro.Amber).clickable(enabled = !busy) { runErase() }.padding(horizontal = 16.dp, vertical = 8.dp))
                if (eraseObjects.isNotEmpty()) Text("UNDO TAP", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.clickable { eraseObjects = eraseObjects.dropLast(1) }.padding(8.dp))
                Text("CANCEL", style = Pro.Label.copy(color = Color.White, fontSize = 11.sp), modifier = Modifier.clickable { eraseMask = null; eraseObjects = emptyList(); picking = 0 }.padding(8.dp))
            }
            eraseStatus?.let { Text(it, color = Pro.Amber, fontSize = 11.sp) }
            Text(if (useCloud) "Uses Bria Eraser in the cloud (your fal.ai account)." else "Runs on the phone with the LaMa AI (downloaded once, ${AiModel.INPAINT.sizeMb} MB).", color = Pro.Dim, fontSize = 11.sp)
        }
    }
    // Sky replacement: your own sky picture is copied into the app.
    val pickSky = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u == null) return@rememberLauncherForActivityResult
        scope.launch {
            val path = withContext(Dispatchers.IO) {
                try {
                    val f = java.io.File(java.io.File(context.filesDir, "skies").apply { mkdirs() }, java.util.UUID.randomUUID().toString() + ".jpg")
                    GradeIO.decode(context, u, 3000)?.let { b -> f.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 92, it) }; f.path }
                } catch (_: Throwable) { null }
            }
            if (path != null) edit = edit.copy(sky = edit.sky.copy(imagePath = path)) else message = "Couldn't open that picture"
        }
    }
    val skySection: @Composable () -> Unit = {
        val sk = edit.sky
        SectionTitle("SKY REPLACEMENT (AI)") { edit = edit.copy(sky = SkySwap()) }
        if (sk.maskFile == null) {
            Text("Finds the sky and swaps it for a new one — the rest of the photo takes on a little of its light.", color = Pro.Dim, fontSize = 11.sp)
            Text("FIND SKY", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
                modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable { findMask(MaskType.SKY) { p -> edit = edit.copy(sky = edit.sky.copy(maskFile = p)) } }.padding(10.dp))
        } else {
            Chips(Skies.names + "Your picture…", if (sk.imagePath != null) Skies.names.size else sk.preset) { i ->
                if (i == Skies.names.size) pickSky.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                else edit = edit.copy(sky = sk.copy(preset = i, imagePath = null))
            }
            EdSlider("Strength", sk.amount, 0f..1f, 1f, ::pct) { v -> edit = edit.copy(sky = sk.copy(amount = v)) }
            EdSlider("Match the light", sk.warm, 0f..1f, 0.3f, ::pct) { v -> edit = edit.copy(sky = sk.copy(warm = v)) }
            Text("FIND SKY AGAIN", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp), modifier = Modifier.clickable { findMask(MaskType.SKY) { p -> edit = edit.copy(sky = edit.sky.copy(maskFile = p)) } }.padding(8.dp))
        }
    }
    val backgroundSection: @Composable () -> Unit = {
        val bg = edit.bg
        SectionTitle("BACKGROUND (AI)") { edit = edit.copy(bg = PhotoBackground()) }
        if (bg.maskFile == null) {
            Text("Finds the subject (people, pets, objects) so you can blur, remove or recolour everything behind it.", color = Pro.Dim, fontSize = 11.sp)
            Text(
                "FIND SUBJECT", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
                modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable { findMask(MaskType.SUBJECT) { p -> edit = edit.copy(bg = edit.bg.copy(maskFile = p, mode = 1)) } }.padding(10.dp),
            )
        } else {
            Chips(listOf("Off", "Blur (portrait)", "Remove", "Colour"), bg.mode) { m -> edit = edit.copy(bg = bg.copy(mode = m)) }
            if (bg.mode == 1) EdSlider("Blur", bg.blur, 0.05f..1f, 0.5f, ::pct) { v -> edit = edit.copy(bg = bg.copy(blur = v)) }
            if (bg.mode == 2) Text("Exported as PNG (or WebP) with a transparent background.", color = Pro.Dim, fontSize = 11.sp)
            if (bg.mode == 3) Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(0xFFFFFFFF, 0xFF000000, 0xFF1E1E1E, 0xFFF2E8DA, 0xFF2D6CDF, 0xFF34C759, 0xFFFF7EB6, 0xFFFFB300).map { it.toInt() }.forEach { c ->
                    Box(Modifier.size(28.dp).clip(CircleShape).background(Color(c)).border(if (c == bg.color) 3.dp else 1.dp, if (c == bg.color) Pro.Amber else Pro.Line, CircleShape)
                        .clickable { edit = edit.copy(bg = bg.copy(color = c)) })
                }
            }
            Text("FIND SUBJECT AGAIN", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp), modifier = Modifier.clickable { findMask(MaskType.SUBJECT) { p -> edit = edit.copy(bg = edit.bg.copy(maskFile = p)) } }.padding(8.dp))
        }
    }

    /** Heal: tap a spot; the patch source is picked automatically. */
    var cloneMode by remember { mutableStateOf(false) }
    var cloneSrc by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var cloneOff by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    fun addSpot(x: Float, y: Float) {
        val b = base ?: return
        val r = tools.healSize
        // Clone stamp: first tap picks the source, then each tap paints a copy from the same offset.
        if (cloneMode) {
            val src = cloneSrc
            if (src == null) { cloneSrc = x to y; cloneOff = null; message = "Source set — now tap where to paint"; return }
            val off = cloneOff ?: ((src.first - x) to ((src.second - y) * b.height / b.width)).also { cloneOff = it }
            edit = edit.copy(spots = edit.spots + Spot(x, y, r, off.first, off.second, clone = true))
            return
        }
        scope.launch {
            val (dx, dy) = withContext(Dispatchers.Default) { PhotoLocal.findSource(b, x, y, r) }
            edit = edit.copy(spots = edit.spots + Spot(x, y, r, dx, dy))
        }
    }

    /** Batch: the current look (and optionally crop, masks and watermark) applied to many photos. */
    var batchWithCrop by remember { mutableStateOf(false) }
    val batchPick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { list ->
        if (list.isEmpty() || busy || PhotoExportJob.active) return@rememberLauncherForActivityResult
        busy = true
        val e = edit
        exportT0 = System.currentTimeMillis(); batchN = list.size; batchI = 0
        PhotoExportJob.begin(context, "Exporting ${list.size} photos")
        PhotoExportJob.scope.launch {
            var done = 0
            for ((i, u) in list.withIndex()) {
                batchI = i
                message = "Batch ${i + 1} / ${list.size}…"
                PhotoExportJob.progress(context, i, list.size)
                val ok = withContext(Dispatchers.IO) {
                    try {
                        // RAW files in the batch are developed with the same settings.
                        val developed = if (RawDng.isRaw(context, u)) {
                            val g = e.grade
                            try { RawDng.export(context, RawDng.open(context, u), e.raw, g.exposure, g.highlights, g.shadows, g.whites, g.blacks, g.temperature, g.tint, sizes[sizeIdx]) } catch (_: RawDng.UnsupportedRaw) { null }
                        } else null
                        val full = developed ?: GradeIO.decode(context, u, sizes[sizeIdx]) ?: return@withContext false
                        val eg = if (developed != null) e.grade.rawRest() else e.grade
                        val tables = if (eg.isIdentity) null else eg.tables()
                        var out = ImageOps.renderPhoto(full, eg, e.detail.copy(aiDenoise = 0f), if (batchWithCrop) e.geometry else Geometry(), tables, e.lens, null, if (batchWithCrop) e.masks.filter { it.maskFile == null } else null)
                        full.recycle()
                        if (batchWithCrop) e.watermark?.let { out = stampLayer(context, out, it) }
                        savePhotoAs(context, out, format, (quality * 100).toInt(), if (keepExif) u else null, stripGps) != null
                    } catch (_: Throwable) { false }
                }
                if (ok) done++
            }
            busy = false
            message = "Batch done: $done of ${list.size} photos saved"
            PhotoExportJob.end(context, message)
        }
    }

    /** The saved edit of a filmstrip photo, or a fresh one. */
    fun sessionProject(i: Int): PhotoProjectData? {
        val sp = PhotoSession.items.getOrNull(i) ?: return null
        return sp.projectId?.let { ProjectStore.loadPhoto(context, it) } ?: PhotoProjectData(name = sp.name, uri = Uri.parse(sp.uri), session = true)
    }
    /** Copies this photo's look (colour, light, detail, lens, RAW develop) to the selected photos. Crop, masks and spots stay their own. */
    fun syncTo(list: List<Int>) {
        if (busy) return
        sessionSave()
        val e = edit
        val targets = list.filter { it != sessionIdx }
        if (targets.isEmpty()) return
        busy = true
        com.dronedirector.veyrocutpro.ExportService.begin(context, "photo-sync", "Syncing edit to ${targets.size} photos", 0)
        scope.launch {
            for ((n, i) in targets.withIndex()) {
                message = "Syncing ${n + 1} / ${targets.size}…"
                com.dronedirector.veyrocutpro.ExportService.update(context, "photo-sync", n * 100 / targets.size, "Syncing edit · ${n + 1} of ${targets.size}")
                withContext(Dispatchers.IO) {
                    try {
                        val p = sessionProject(i) ?: return@withContext
                        val q = p.copy(grade = e.grade, detail = e.detail, lens = e.lens, raw = e.raw, session = true)
                        val small = GradeIO.decode(context, q.uri, 320)
                        val thumb = small?.let { b ->
                            val tables = if (q.grade.isIdentity) null else q.grade.tables()
                            ImageOps.renderPhoto(b, q.grade, q.detail.copy(aiDenoise = 0f), q.geometry, tables, q.lens, null, null).also { if (it !== b) b.recycle() }
                        }
                        val saved = ProjectStore.savePhoto(context, q, thumb)
                        withContext(Dispatchers.Main) { PhotoSession.update(context, i) { it.copy(projectId = saved.id, edited = true, version = System.currentTimeMillis()) } }
                    } catch (_: Throwable) {}
                }
            }
            busy = false
            message = "Edit synced to ${targets.size} photo" + (if (targets.size == 1) "" else "s")
            com.dronedirector.veyrocutpro.ExportService.end(context, "photo-sync")
        }
    }
    /** Exports the selected filmstrip photos, each with its own edit, using the export settings here. */
    fun exportSession(list: List<Int>) {
        if (busy || PhotoExportJob.active || list.isEmpty()) return
        sessionSave()
        busy = true
        exportT0 = System.currentTimeMillis(); batchN = list.size; batchI = 0
        PhotoExportJob.begin(context, "Exporting ${list.size} photos")
        PhotoExportJob.scope.launch {
            var done = 0
            for ((n, i) in list.withIndex()) {
                batchI = n
                message = "Export ${n + 1} / ${list.size}…"
                PhotoExportJob.progress(context, n, list.size)
                val ok = withContext(Dispatchers.IO) {
                    try {
                        val p = sessionProject(i) ?: return@withContext false
                        val u = p.baseFile?.let { Uri.fromFile(java.io.File(it)) } ?: p.uri
                        val developed = if (p.baseFile == null && RawDng.isRaw(context, u)) {
                            val g = p.grade
                            try { RawDng.export(context, RawDng.open(context, u), p.raw ?: RawDng.Dev(), g.exposure, g.highlights, g.shadows, g.whites, g.blacks, g.temperature, g.tint, sizes[sizeIdx]) } catch (_: RawDng.UnsupportedRaw) { null }
                        } else null
                        val full = developed ?: GradeIO.decode(context, u, sizes[sizeIdx]) ?: return@withContext false
                        val eg = if (developed != null) p.grade.rawRest() else p.grade
                        val tables = if (eg.isIdentity) null else eg.tables()
                        var out = ImageOps.renderPhoto(full, eg, p.detail.copy(aiDenoise = 0f), p.geometry, tables, p.lens, p.spots, p.masks.filter { it.maskFile == null }, retouch = p.retouch?.let { Retouch(liquify = it.liquify, fsSmooth = if (it.fsArea == 0) it.fsSmooth else 0f, fsTexture = if (it.fsArea == 0) it.fsTexture else 1f, fsRadius = it.fsRadius) })
                        if (out !== full) full.recycle()
                        p.watermark?.let { out = stampLayer(context, out, it) }
                        savePhotoAs(context, out, format, (quality * 100).toInt(), if (keepExif) p.uri else null, stripGps) != null
                    } catch (_: Throwable) { false }
                }
                if (ok) done++
            }
            busy = false
            message = "Exported $done of ${list.size} photos"
            PhotoExportJob.end(context, message)
        }
    }

    Box(Modifier.fillMaxSize().background(Ed.Bg)) {
        com.dronedirector.veyrocutpro.ui.AiBadgeHost(
            AiDenoiser.running, "AI NOISE REDUCTION", 1, AiDenoiser.progress, onStop = { AiDenoiser.stop() },
            modifier = Modifier.align(Alignment.TopCenter).zIndex(20f), note = AiDenoiseCache.note,
        )
        com.dronedirector.veyrocutpro.ui.AiBadgeHost(
            com.dronedirector.veyrocutpro.camera.Upscaler.pending > 0, "AI UPSCALING", 1, com.dronedirector.veyrocutpro.camera.Upscaler.progress,
            onStop = { com.dronedirector.veyrocutpro.camera.Upscaler.cancelAll() },
            modifier = Modifier.align(Alignment.TopCenter).zIndex(22f),
        )
        com.dronedirector.veyrocutpro.ui.AiBadgeHost(
            com.dronedirector.veyrocutpro.camera.CloudAi.stage != null, com.dronedirector.veyrocutpro.camera.CloudAi.stage ?: "", 1,
            com.dronedirector.veyrocutpro.camera.CloudAi.progress, onStop = { com.dronedirector.veyrocutpro.camera.CloudAi.stop() },
            modifier = Modifier.align(Alignment.TopCenter).zIndex(21f),
        )
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
            val wide = maxWidth > maxHeight || maxWidth >= 720.dp
            val boxW = maxWidth.value; val boxH = maxHeight.value
            val previewBox = @Composable { mod: Modifier ->
                Box(
                    // Mouse: wheel = next / previous filmstrip photo, right-click = before / after split, double-click = hide / show the tools.
                    // clipToBounds: a zoomed-in photo stays inside the viewer (never over the top bar or the corners).
                    mod.clipToBounds().mouseActions(
                        onWheel = { dy, _, at, ctrl, _ ->
                            if (ctrl || zoomK > 1f) {
                                val nz = (zoomK * (if (dy < 0) 1.25f else 0.8f)).coerceIn(1f, 8f)
                                val bw = viewSize.width.toFloat(); val bh = viewSize.height.toFloat()
                                panX = (panX - (at.x - bw / 2f - panX) * (nz / zoomK - 1f)).coerceIn(-bw * (nz - 1f) / 2f, bw * (nz - 1f) / 2f)
                                panY = (panY - (at.y - bh / 2f - panY) * (nz / zoomK - 1f)).coerceIn(-bh * (nz - 1f) / 2f, bh * (nz - 1f) / 2f)
                                zoomK = nz; if (nz <= 1.001f) resetZoom()
                                return@mouseActions true
                            }
                            val n = PhotoSession.items.size
                            if (sessionIdx >= 0 && n > 1) { val to = (sessionIdx + if (dy > 0) 1 else -1).coerceIn(0, n - 1); if (to != sessionIdx) switchTo(to); true } else false
                        },
                        onRightClick = { split = !split },
                        onDoubleClick = { com.dronedirector.veyrocutpro.ui.Panels.set(context, "pe.controls", !showControls) },
                    ).pointerInput(split, panel, picking, retouchTab) {
                        // Pinch to zoom in (1:1 loupe), drag to look around while zoomed. Off in Masks / Heal and the split view.
                        if (!split && picking == 0 && panel != PhotoPanel.MASKS && panel != PhotoPanel.HEAL && !(panel == PhotoPanel.RETOUCH && retouchTab == 0)) detectTransformGestures { c, pan, z, _ ->
                            val nz = (zoomK * z).coerceIn(1f, 8f)
                            if (nz != zoomK || zoomK > 1f) {
                                val bw = viewSize.width.toFloat(); val bh = viewSize.height.toFloat()
                                panX = ((panX + pan.x) - (c.x - bw / 2f - panX) * (nz / zoomK - 1f)).coerceIn(-bw * (nz - 1f) / 2f, bw * (nz - 1f) / 2f)
                                panY = ((panY + pan.y) - (c.y - bh / 2f - panY) * (nz / zoomK - 1f)).coerceIn(-bh * (nz - 1f) / 2f, bh * (nz - 1f) / 2f)
                                zoomK = nz
                                if (zoomK <= 1.001f) resetZoom()
                            }
                        }
                    }.pointerInput(split, panel, picking, retouchTab) {
                        if (split) {
                            // Drag anywhere to move the before / after divider.
                            detectHorizontalDragGestures { ch, _ -> splitX = (ch.position.x / size.width).coerceIn(0.02f, 0.98f) }
                        } else if (picking == 6 || picking == 7) {
                            // Targeted adjustment: put a finger on a tone or colour and drag up / down.
                            detectDragGestures(
                                onDragStart = { o -> tatStart(o, 6.dp.toPx()) },
                                onDragEnd = { tatWeights = null }, onDragCancel = { tatWeights = null },
                            ) { ch, d -> ch.consume(); tatDrag(d.y / size.height) }
                        } else if (picking != 0) {
                            // Eyedropper: tap the spot to sample.
                            detectTapGestures(onTap = { o -> pickAt(o, 6.dp.toPx()) })
                        } else if (panel != PhotoPanel.MASKS && panel != PhotoPanel.HEAL && !(panel == PhotoPanel.RETOUCH && retouchTab == 0)) {
                            detectTapGestures(
                                onPress = { if (zoomK <= 1f) { comparing = true; tryAwaitRelease(); comparing = false } },
                                // Double-tap: 1:1 loupe at that spot, again to fit.
                                onDoubleTap = { o ->
                                    if (zoomK > 1f) resetZoom() else {
                                        val bw = viewSize.width.toFloat(); val bh = viewSize.height.toFloat(); val z = 3f
                                        zoomK = z; panX = (-(o.x - bw / 2f) * (z - 1f)).coerceIn(-bw, bw); panY = (-(o.y - bh / 2f) * (z - 1f)).coerceIn(-bh, bh)
                                    }
                                },
                            )
                        }
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    val ph = vw.placeholder
                    // While a stand-in shows, the neutral develop isn't flashed in between.
                    val shown = if (comparing) base else preview ?: (if (ph != null) null else base)
                    val before = beforeImg
                    when {
                        failed -> Text("This file can't be opened.", color = Pro.Dim)
                        uri == Uri.EMPTY && edit.baseFile == null -> Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(16.dp),
                        ) {
                            Text("No photo yet", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text("Add a photo to start editing.", color = Pro.Dim, fontSize = 12.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Add photos", color = Color.Black, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(50)).background(Pro.Amber).clickable(onClick = onAddGallery).padding(horizontal = 18.dp, vertical = 10.dp))
                                Text("Browse files", color = Pro.Amber, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, Pro.Amber, RoundedCornerShape(50)).clickable(onClick = onAddFiles).padding(horizontal = 18.dp, vertical = 10.dp))
                                Text("Camera", color = Pro.Amber, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, Pro.Amber, RoundedCornerShape(50)).clickable(onClick = onAddCamera).padding(horizontal = 18.dp, vertical = 10.dp))
                            }
                        }
                        RefView.shown && refImg != null && shown != null -> {
                            val r = refImg!!
                            val side = viewSize.width >= viewSize.height
                            @Composable fun pane(b: Bitmap, label: String, mod: Modifier) = Box(mod) {
                                Image(b.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp))
                                Text(label, style = Pro.Label.copy(color = Color.White), modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 8.dp, vertical = 3.dp))
                            }
                            if (side) Row(Modifier.fillMaxSize()) { pane(r, "REFERENCE", Modifier.weight(1f).fillMaxHeight()); pane(shown, "EDITING", Modifier.weight(1f).fillMaxHeight()) }
                            else Column(Modifier.fillMaxSize()) { pane(r, "REFERENCE", Modifier.weight(1f).fillMaxWidth()); pane(shown, "EDITING", Modifier.weight(1f).fillMaxWidth()) }
                        }
                        split && before != null && shown != null -> {
                            Image(before.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(6.dp))
                            Image(
                                shown.asImageBitmap(), null, contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().padding(6.dp).drawWithContent {
                                    clipRect(left = size.width * splitX) { this@drawWithContent.drawContent() }
                                },
                            )
                            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                                val x = size.width * splitX
                                drawLine(Color.White, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), 3f)
                                drawCircle(Color.White, 14.dp.toPx(), androidx.compose.ui.geometry.Offset(x, size.height / 2))
                                drawCircle(Pro.Amber, 11.dp.toPx(), androidx.compose.ui.geometry.Offset(x, size.height / 2))
                            }
                            Text("BEFORE", style = Pro.Label.copy(color = Color.White), modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 8.dp, vertical = 3.dp))
                            Text("AFTER", style = Pro.Label.copy(color = Color.White), modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                        shown != null || ph != null -> Box(
                            Modifier.fillMaxSize().padding(6.dp).onSizeChanged { viewSize = it }
                                .graphicsLayer(scaleX = zoomK, scaleY = zoomK, translationX = panX, translationY = panY),
                        ) {
                            if (ph != null) Image(ph.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                            if (shown != null) {
                            val img = if (comparing) shown else dustImg ?: (if (zoomK > 1.6f) loupe else null) ?: proofImg ?: shown
                            Image(img.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(), alpha = if (ph != null) vw.fade.value else 1f)
                            }
                            if (shown != null && !comparing) {
                                sharpImg?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
                                clipImg?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
                                gamutImg?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
                                if (panel == PhotoPanel.HEAL) eraseTint?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
                            }
                        }
                        else -> CircularProgressIndicator(color = Pro.Amber)
                    }
                    // Mask handles / brush, and heal taps, on top of the photo.
                    val shownNow = if (comparing) base else preview ?: base
                    if (!split && !comparing && shownNow != null) {
                        if (panel == PhotoPanel.CROP) CropGuideOverlay(shownNow.width, shownNow.height)
                        val selMask = edit.masks.firstOrNull { it.id == tools.selected }
                        if (panel == PhotoPanel.MASKS && selMask != null && picking == 0) MaskEditOverlay(selMask, shownNow.width, shownNow.height, tools, image = shownNow) { m ->
                            edit = edit.copy(masks = edit.masks.map { if (it.id == m.id) m else it })
                        }
                        val em = eraseMask
                        if (panel == PhotoPanel.HEAL && em != null && picking == 5) Unit
                        else if (panel == PhotoPanel.HEAL && em != null) MaskEditOverlay(em, shownNow.width, shownNow.height, tools) { m -> eraseMask = m }
                        else if (panel == PhotoPanel.RETOUCH && retouchTab == 0) LiquifyOverlay(shownNow.width, shownNow.height, liqTool, liqSize, liqStrength) { st ->
                            edit = edit.copy(retouch = edit.retouch.copy(liquify = edit.retouch.liquify + st))
                        }
                        else if (panel == PhotoPanel.HEAL) HealOverlay(
                            edit.spots, shownNow.width, shownNow.height, tools.healSize,
                            onMove = { i, sp -> if (i in edit.spots.indices) edit = edit.copy(spots = edit.spots.toMutableList().also { it[i] = sp }) },
                            onDelete = { i -> if (i in edit.spots.indices) { edit = edit.copy(spots = edit.spots.filterIndexed { k, _ -> k != i }); message = "Spot removed" } },
                        ) { x, y -> addSpot(x, y) }
                    }
                    // RGB histogram: the same pull-out drawer as in the video editor, on the right edge.
                    if (showScope && !comparing) ScopeDrawer(hist, Modifier.align(Alignment.CenterEnd), frame = preview ?: base)
                    if (zoomK > 1f) Text(
                        (if (loupe != null && zoomK > 1.6f) "1:1 · " else "") + "${(zoomK * 100).toInt()}% · double-tap to fit", style = Pro.Label.copy(color = Color.White),
                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.6f)).clickable { resetZoom() }.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                    if (picking != 0) Text(
                        when (picking) { 1 -> "Tap something that should be neutral grey or white"; 3 -> "Tap the object to select"; 5 -> "Tap each thing to remove"; 8 -> "Tap the middle of the first patch (brown)"; 9 -> "Tap the middle of the last patch (black)"; 6 -> "Drag up / down on a tone"; 7 -> "Drag up / down on a colour"; 4 -> "Tap where the focus should be"; else -> "Tap the colour you want to change" },
                        style = Pro.Label.copy(color = Color.Black),
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 34.dp).clip(RoundedCornerShape(50)).background(Pro.Amber).clickable { picking = 0 }.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                    if (rawOn(edit)) Text(
                        "RAW", style = Pro.Label.copy(color = Color.Black),
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(50)).background(Pro.Amber).padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                    if (showCompare || split || RefView.shown) Row(Modifier.align(Alignment.TopStart).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (RefView.shown) "REFERENCE ✓" else "REFERENCE",
                            style = Pro.Label.copy(color = if (RefView.shown) Color.Black else Color.White),
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(if (RefView.shown) Pro.Amber else Color.Black.copy(alpha = 0.5f))
                                .clickable { if (RefView.shown) RefView.shown = false else if (RefView.uri != null) RefView.shown = true else try { pickRef.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } catch (_: Throwable) {} }
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                        if (RefView.shown) {
                            Text("CHANGE", style = Pro.Label.copy(color = Color.White), modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.5f))
                                .clickable { try { pickRef.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } catch (_: Throwable) {} }.padding(horizontal = 8.dp, vertical = 3.dp))
                            Text(if (refMatching) "MATCHING…" else "MATCH LOOK", style = Pro.Label.copy(color = Color.Black), modifier = Modifier.clip(RoundedCornerShape(50)).background(Pro.Amber)
                                .clickable(enabled = !refMatching) { matchRef() }.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                        if (!split) Text(
                            if (comparing) "BEFORE" else "HOLD FOR BEFORE",
                            style = Pro.Label.copy(color = Color.White),
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                        Text(
                            if (split) "SPLIT ✓" else "SPLIT",
                            style = Pro.Label.copy(color = if (split) Color.Black else Color.White),
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(if (split) Pro.Amber else Color.Black.copy(alpha = 0.5f)).clickable { split = !split }.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                    if (busy) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Pro.Amber)
                            Text("Exporting…", color = Color.White, modifier = Modifier.padding(top = 8.dp))
                            // Estimated time remaining: learned from earlier exports (batch: from the photos done so far).
                            var now by remember { mutableStateOf(System.currentTimeMillis()) }
                            LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(500) } }
                            val elapsed = now - exportT0
                            val eta = when {
                                batchN > 0 && batchI > 0 -> etaText(elapsed * (batchN - batchI) / batchI)
                                batchN > 0 -> "Estimating time left…"
                                exportEstMs > 0 -> if (elapsed < exportEstMs) etaText(exportEstMs - elapsed) else "Almost done…"
                                else -> "Estimating time left…"
                            }
                            Text(eta, color = Pro.Dim, fontSize = 13.sp)
                        }
                    }
                    message?.let {
                        Text(it, color = Color.White, fontSize = 13.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.75f)).padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }
            }
            // Keyboard shortcuts (Bluetooth / USB keyboard, Samsung DeX).
            DisposableEffect(Unit) {
                KeyBus.handler = handler@{ ev ->
                    if (ev.action != android.view.KeyEvent.ACTION_DOWN || askName) return@handler false
                    val ctrl = ev.isCtrlPressed || ev.isMetaPressed
                    val shift = ev.isShiftPressed
                    val si = sessionIdx
                    fun rate(f: (SessionPhoto) -> SessionPhoto, msg: String): Boolean {
                        if (si < 0) { message = "Open the photo from the filmstrip to rate it"; return true }
                        PhotoSession.update(context, si, f); message = msg; return true
                    }
                    fun go(d: Int): Boolean { val n = PhotoSession.items.size; if (si < 0 || n < 2) return false; val to = (si + d).coerceIn(0, n - 1); if (to != si) switchTo(to); return true }
                    fun open(pnl: PhotoPanel): Boolean { panel = if (panel == pnl && pnl != PhotoPanel.PRESETS) PhotoPanel.PRESETS else pnl; return true }
                    val labels = listOf(0xFFFF3B30, 0xFFFFD60A, 0xFF34C759, 0xFF0A84FF).map { it.toInt() }
                    val k = ev.keyCode
                    when {
                        ctrl && k == android.view.KeyEvent.KEYCODE_Z -> { if (shift) { if (undoPos < undo.lastIndex) { undoPos++; edit = undo[undoPos] } } else if (undoPos > 0) { undoPos--; edit = undo[undoPos] }; true }
                        ctrl && k == android.view.KeyEvent.KEYCODE_Y -> { if (undoPos < undo.lastIndex) { undoPos++; edit = undo[undoPos] }; true }
                        ctrl && k == android.view.KeyEvent.KEYCODE_C -> {
                            val e = edit
                            EditClipboard.copy(context, CopiedSettings(fromVideo = false, grade = e.grade, detail = e.detail, geometry = e.geometry, watermark = e.watermark))
                            message = "Settings copied — Ctrl+V on another photo pastes the look"; true
                        }
                        ctrl && k == android.view.KeyEvent.KEYCODE_V -> {
                            val c = EditClipboard.current
                            if (c == null) message = "Nothing copied yet (Ctrl+C copies this photo's settings)"
                            else {
                                var e = edit
                                c.grade?.let { e = e.copy(grade = it) }; c.detail?.let { e = e.copy(detail = it) }
                                if (shift) { c.geometry?.let { e = e.copy(geometry = it) }; c.watermark?.let { e = e.copy(watermark = it) } }
                                edit = e; message = if (shift) "All settings pasted" else "Look pasted (Ctrl+Shift+V also pastes crop and watermark)"
                            }
                            true
                        }
                        ctrl && k == android.view.KeyEvent.KEYCODE_S -> { if (projectId == null) askName = true else saveProject(projectName); true }
                        ctrl && k == android.view.KeyEvent.KEYCODE_E -> { if (!busy) export(); true }
                        ctrl -> false
                        k in android.view.KeyEvent.KEYCODE_0..android.view.KeyEvent.KEYCODE_5 -> { val n = k - android.view.KeyEvent.KEYCODE_0; rate({ it.copy(rating = n) }, if (n == 0) "Rating cleared" else "★".repeat(n)) }
                        k in android.view.KeyEvent.KEYCODE_6..android.view.KeyEvent.KEYCODE_9 -> {
                            val c = labels[k - android.view.KeyEvent.KEYCODE_6]
                            rate({ it.copy(label = if (it.label == c) null else c) }, "Colour label " + listOf("red", "yellow", "green", "blue")[k - android.view.KeyEvent.KEYCODE_6])
                        }
                        k == android.view.KeyEvent.KEYCODE_P -> rate({ it.copy(flag = 1) }, "Flagged as pick")
                        k == android.view.KeyEvent.KEYCODE_X -> rate({ it.copy(flag = -1) }, "Flagged as reject")
                        k == android.view.KeyEvent.KEYCODE_U -> rate({ it.copy(flag = 0) }, "Flag removed")
                        k == android.view.KeyEvent.KEYCODE_DPAD_LEFT || k == android.view.KeyEvent.KEYCODE_PAGE_UP -> go(-1)
                        k == android.view.KeyEvent.KEYCODE_DPAD_RIGHT || k == android.view.KeyEvent.KEYCODE_PAGE_DOWN -> go(1)
                        k == android.view.KeyEvent.KEYCODE_MOVE_HOME -> go(-10_000)
                        k == android.view.KeyEvent.KEYCODE_MOVE_END -> go(10_000)
                        k == android.view.KeyEvent.KEYCODE_BACKSLASH || k == android.view.KeyEvent.KEYCODE_Y -> { split = !split; true }
                        k == android.view.KeyEvent.KEYCODE_Z -> {
                            if (zoomK > 1f) resetZoom() else { zoomK = 3f; panX = 0f; panY = 0f }
                            true
                        }
                        k == android.view.KeyEvent.KEYCODE_ESCAPE -> { picking = 0; split = false; resetZoom(); true }
                        k == android.view.KeyEvent.KEYCODE_TAB -> { com.dronedirector.veyrocutpro.ui.Panels.set(context, "pe.controls", !showControls); true }
                        k == android.view.KeyEvent.KEYCODE_R && shift -> { if (RefView.uri != null) RefView.shown = !RefView.shown else message = "Choose a reference first (REFERENCE on the photo)"; true }
                        k == android.view.KeyEvent.KEYCODE_R -> open(PhotoPanel.CROP)
                        k == android.view.KeyEvent.KEYCODE_Q -> open(PhotoPanel.HEAL)
                        k == android.view.KeyEvent.KEYCODE_M -> open(PhotoPanel.MASKS)
                        k == android.view.KeyEvent.KEYCODE_L -> open(PhotoPanel.LIGHT)
                        k == android.view.KeyEvent.KEYCODE_C -> open(PhotoPanel.COLOR)
                        k == android.view.KeyEvent.KEYCODE_D -> open(PhotoPanel.DETAIL)
                        k == android.view.KeyEvent.KEYCODE_E -> open(PhotoPanel.EXPORT)
                        k == android.view.KeyEvent.KEYCODE_H -> open(PhotoPanel.HISTORY)
                        k == android.view.KeyEvent.KEYCODE_J -> { clipWarn = !clipWarn; message = if (clipWarn) "Clipping warning on" else "Clipping warning off"; true }
                        k == android.view.KeyEvent.KEYCODE_SLASH || k == android.view.KeyEvent.KEYCODE_F1 -> { showShortcuts = true; true }
                        else -> false
                    }
                }
                onDispose { KeyBus.handler = null }
            }
            if (showShortcuts) PhotoShortcutsDialog { showShortcuts = false }
            val controls = @Composable { mod: Modifier ->
                Column(mod.background(Ed.Panel)) {
                    PanelTabs(panel) { panel = it }
                    if (showCopyBar) CopyPasteBar(
                        video = false,
                        onCopy = {
                            val e = edit
                            EditClipboard.copy(context, CopiedSettings(fromVideo = false, grade = e.grade, detail = e.detail, geometry = e.geometry, watermark = e.watermark))
                            message = "Settings copied — open another photo or a video clip and tap PASTE SETTINGS"
                        },
                        onPaste = { parts, _ ->
                            val c = EditClipboard.current
                            if (c != null) {
                                var e = edit
                                if (PastePart.GRADE in parts) c.grade?.let { e = e.copy(grade = it) }
                                if (PastePart.DETAIL in parts) c.detail?.let { e = e.copy(detail = it) }
                                if (PastePart.CROP in parts) c.geometry?.let { e = e.copy(geometry = it) }
                                if (PastePart.WATERMARK in parts) c.watermark?.let { e = e.copy(watermark = it) }
                                edit = e
                                message = "Settings pasted"
                            }
                        },
                    )
                    com.dronedirector.veyrocutpro.ui.FoldColumn(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 4.dp), scope = "photo") {
                        when (panel) {
                            PhotoPanel.MASKS -> MasksPanel(edit.masks, tools, { edit = edit.copy(masks = it) }, onAi = aiMaskAction, onAiSub = aiSubAction, onPeople = peopleAction)
                            PhotoPanel.HEAL -> {
                                SectionTitle("HEAL / CLONE") { edit = edit.copy(spots = emptyList()) }
                                Chips(listOf("Heal", "Clone stamp"), if (cloneMode) 1 else 0) { cloneMode = it == 1; cloneSrc = null; cloneOff = null }
                                if (cloneMode) {
                                    Text(if (cloneSrc == null) "Tap the spot to copy FROM." else "Tap where to paint the copy; more taps keep the same offset.", color = Pro.Amber, fontSize = 11.sp)
                                    Text("NEW SOURCE", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp), modifier = Modifier.clickable { cloneSrc = null; cloneOff = null }.padding(8.dp))
                                }
                                Text("Tap dust, blemishes or small objects on the photo. A matching patch from nearby is blended over each one (the whole photo is shown while you heal). Drag a solid circle to move a spot, drag its dashed circle to choose where it copies from, hold a spot to delete it.", color = Pro.Dim, fontSize = 11.sp)
                                EdSlider("Size", tools.healSize, 0.005f..0.12f, 0.025f, ::pct) { tools.healSize = it }
                                Text("${edit.spots.size} spot(s) healed", style = Pro.Label)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("UNDO LAST", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp), modifier = Modifier.clickable { edit = edit.copy(spots = edit.spots.dropLast(1)) }.padding(8.dp))
                                    Text("CLEAR ALL", style = Pro.Label.copy(color = Pro.Red, fontSize = 10.sp), modifier = Modifier.clickable { edit = edit.copy(spots = emptyList()) }.padding(8.dp))
                                }
                                SectionTitle("DUST SPOTS")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PillChip(if (dustView) "VISUALISE SPOTS ✓" else "VISUALISE SPOTS", dustView) { dustView = !dustView }
                                    PillChip("FIND DUST AUTOMATICALLY", false, accentText = true) {
                                        val b = base
                                        if (b != null) scope.launch {
                                            message = "Looking for dust…"
                                            val found = withContext(Dispatchers.Default) { Upright.findDust(b, dustSens) }
                                            // Skip spots that are already healed.
                                            val fresh = found.filter { n -> edit.spots.none { kotlin.math.hypot((it.x - n.x).toDouble(), (it.y - n.y).toDouble()) < (it.r + n.r) } }
                                            edit = edit.copy(spots = edit.spots + fresh)
                                            message = if (fresh.isEmpty()) "No dust spots found" else "${fresh.size} dust spot(s) healed — check them and remove any that aren't dust"
                                        }
                                    }
                                }
                                EdSlider("Sensitivity", dustSens, 0f..1f, 0.5f, ::pct) { dustSens = it }
                                Text("VISUALISE SPOTS shows a black-and-white view where dust and specks stand out. FIND DUST heals small dark specks in smooth areas such as the sky.", color = Pro.Dim, fontSize = 11.sp)
                                aiEraseSection()
                            }
                            PhotoPanel.RETOUCH -> {
                                val rt = edit.retouch
                                fun rts(f: (Retouch) -> Retouch) { edit = edit.copy(retouch = f(edit.retouch)) }
                                Chips(listOf("Liquify", "Skin (frequency separation)", "Relight (AI)"), retouchTab) { retouchTab = it }
                                when (retouchTab) {
                                    0 -> {
                                        SectionTitle("LIQUIFY") { rts { it.copy(liquify = emptyList()) } }
                                        Chips(RetouchFx.TOOLS, liqTool) { liqTool = it }
                                        EdSlider("Brush size", liqSize, 0.01f..0.3f, 0.08f, ::pct) { liqSize = it }
                                        EdSlider("Strength", liqStrength, 0.05f..1f, 0.5f, ::pct) { liqStrength = it }
                                        Text("${rt.liquify.size} stroke(s)", style = Pro.Label)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text("UNDO LAST", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp), modifier = Modifier.clickable { rts { it.copy(liquify = it.liquify.dropLast(1)) } }.padding(8.dp))
                                            Text("CLEAR ALL", style = Pro.Label.copy(color = Pro.Red, fontSize = 10.sp), modifier = Modifier.clickable { rts { it.copy(liquify = emptyList()) } }.padding(8.dp))
                                        }
                                        Text("Drag on the photo. Push moves pixels along your drag, Pinch shrinks and Bloat enlarges under the brush, Twirl turns, Restore brings back the original. The whole photo is shown while you liquify.", color = Pro.Dim, fontSize = 11.sp)
                                    }
                                    1 -> {
                                        SectionTitle("FREQUENCY SEPARATION") { rts { it.copy(fsSmooth = 0f, fsTexture = 1f, fsArea = 0, fsMaskFile = null) } }
                                        Chips(listOf("Whole photo", "Face skin (AI)", "All skin (AI)"), rt.fsArea) { i ->
                                            if (i == 0) rts { it.copy(fsArea = 0) }
                                            else skinMask(if (i == 1) listOf(1) else listOf(1, 2)) { p -> rts { it.copy(fsArea = i, fsMaskFile = p) } }
                                        }
                                        EdSlider("Smooth tone", rt.fsSmooth, 0f..1f, 0f, ::pct) { v -> rts { it.copy(fsSmooth = v) } }
                                        EdSlider("Texture", rt.fsTexture, 0f..2f, 1f, ::pct) { v -> rts { it.copy(fsTexture = v) } }
                                        EdSlider("Detail size", rt.fsRadius, 0.001f..0.015f, 0.004f, { "%.1f‰".format(it * 1000) }) { v -> rts { it.copy(fsRadius = v) } }
                                        Text("Splits the photo into tone (colour and light) and texture (pores, hair, fine detail). Smooth tone evens out blotchy skin while Texture keeps the pores real — 100% = as photographed. Detail size sets what counts as texture.", color = Pro.Dim, fontSize = 11.sp)
                                    }
                                    else -> {
                                        SectionTitle("RELIGHT (AI DEPTH)") { rts { it.copy(light = 0f, shadow = 0f, warmth = 0f) } }
                                        if (rt.depthFile == null) {
                                            Text("WORK OUT THE DEPTH", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Ed.Raised).clickable { withDepth { p -> rts { it.copy(depthFile = p, light = if (it.light == 0f) 0.5f else it.light) } } }.padding(10.dp))
                                            Text(if (depthBusy) "Working out the depth…" else "Relight needs the photo's depth (made on the phone, once; also used by Lens Blur).", color = Pro.Dim, fontSize = 11.sp)
                                        } else {
                                            LightPad(rt.lightX, rt.lightY) { x, y -> rts { it.copy(lightX = x, lightY = y) } }
                                            EdSlider("Light", rt.light, 0f..1f, 0f, ::pct) { v -> rts { it.copy(light = v) } }
                                            EdSlider("Shadows", rt.shadow, 0f..1f, 0f, ::pct) { v -> rts { it.copy(shadow = v) } }
                                            EdSlider("Warmth", rt.warmth, -1f..1f, 0f, ::pct) { v -> rts { it.copy(warmth = v) } }
                                            EdSlider("Softness", rt.softness, 0f..1f, 0.5f, ::pct) { v -> rts { it.copy(softness = v) } }
                                            Text("Tap or drag in the square to place the light. Light brightens what faces it, Shadows darkens what faces away; nearer things catch more light.", color = Pro.Dim, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                            PhotoPanel.TEXT -> {
                                // Text and sticker layers on the photo.
                                var selText by remember { mutableStateOf<String?>(null) }
                                SectionTitle("TEXT & STICKERS") { edit = edit.copy(texts = emptyList()) }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("+ TEXT", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable {
                                        val l = Layer(kind = LayerKind.TEXT, text = "Your text", size = 0.07f, x = 0.5f, y = 0.5f, wholeVideo = true)
                                        edit = edit.copy(texts = edit.texts + l); selText = l.id
                                    }.padding(10.dp))
                                    listOf("⭐", "❤️", "📍", "🔥", "✅", "➡️").forEach { em ->
                                        Text(em, fontSize = 20.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable {
                                            val l = Layer(kind = LayerKind.STICKER, text = em, size = 0.12f, x = 0.5f, y = 0.5f, wholeVideo = true, shadow = false)
                                            edit = edit.copy(texts = edit.texts + l); selText = l.id
                                        }.padding(horizontal = 8.dp, vertical = 4.dp))
                                    }
                                }
                                edit.texts.forEach { t ->
                                    val sel = t.id == selText
                                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp)).background(if (sel) Pro.Amber.copy(alpha = 0.16f) else Ed.Raised)
                                        .clickable { selText = t.id }.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(t.text.replace('\n', ' ').take(30), color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                        Text("✕", color = Pro.Red, fontSize = 13.sp, modifier = Modifier.clickable { edit = edit.copy(texts = edit.texts.filter { it.id != t.id }) }.padding(horizontal = 8.dp))
                                    }
                                }
                                val t = edit.texts.firstOrNull { it.id == selText }
                                if (t != null) {
                                    fun upd(n: Layer) { edit = edit.copy(texts = edit.texts.map { if (it.id == t.id) n else it }) }
                                    if (t.kind == LayerKind.TEXT) {
                                        androidx.compose.material3.OutlinedTextField(value = t.text, onValueChange = { upd(t.copy(text = it)) }, label = { Text("Text") }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                                        Chips(LayerFont.entries.map { it.label }, t.font.ordinal) { upd(t.copy(font = LayerFont.entries[it])) }
                                        val cols = listOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFFFB300.toInt(), 0xFFFF3B30.toInt(), 0xFF34C759.toInt(), 0xFF0A84FF.toInt())
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                                            cols.forEach { cl -> Box(Modifier.size(26.dp).clip(CircleShape).background(Color(cl)).border(2.dp, if (t.color == cl) Pro.Amber else Pro.Line, CircleShape).clickable { upd(t.copy(color = cl)) }) }
                                        }
                                        Chips(listOf("Plain", "Shadow", "Box"), if (t.box) 2 else if (t.shadow) 1 else 0) { i -> upd(t.copy(shadow = i == 1, box = i == 2)) }
                                    }
                                    EdSlider("Size", t.size, 0.02f..0.4f, 0.07f, ::pct) { upd(t.copy(size = it)) }
                                    EdSlider("Across", t.x, 0f..1f, 0.5f, ::pct) { upd(t.copy(x = it)) }
                                    EdSlider("Down", t.y, 0f..1f, 0.5f, ::pct) { upd(t.copy(y = it)) }
                                    EdSlider("Opacity", t.opacity, 0.05f..1f, 1f, ::pct) { upd(t.copy(opacity = it)) }
                                } else if (edit.texts.isEmpty()) {
                                    Text("Add titles, captions, labels or stickers to the photo. They are drawn on top of the edit and saved with the export.", color = Pro.Dim, fontSize = 11.sp)
                                }
                            }
                            PhotoPanel.HISTORY -> {
                                SectionTitle("SNAPSHOTS")
                                Text("Save the look as it is now, try something else, and jump back any time. Snapshots last while this photo is open; SAVE the project to keep the current one.", color = Pro.Dim, fontSize = 11.sp)
                                Text("+ SAVE SNAPSHOT", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
                                    modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable { snapshots.add("Snapshot ${snapshots.size + 1}" to edit); message = "Snapshot ${snapshots.size} saved" }.padding(10.dp))
                                snapshots.toList().forEachIndexed { i, (name, se) ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp)).background(if (se == edit) Pro.Amber.copy(alpha = 0.16f) else Ed.Raised)
                                        .clickable { edit = se; message = "$name" }.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(name, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                        Text("✕", color = Pro.Red, fontSize = 13.sp, modifier = Modifier.clickable { snapshots.removeAt(i) }.padding(horizontal = 8.dp))
                                    }
                                }
                                SectionTitle("HISTORY")
                                Text("Every change, newest at the top — tap one to go back to it.", color = Pro.Dim, fontSize = 11.sp)
                                for (i in undo.indices.reversed()) {
                                    val label = if (i == 0) "Original" else "Step $i"
                                    Text(label + if (i == undoPos) "  ●" else "", color = if (i == undoPos) Pro.Amber else Color.White, fontSize = 13.sp,
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { undoPos = i; edit = undo[i] }.padding(horizontal = 10.dp, vertical = 6.dp))
                                }
                            }
                            PhotoPanel.LENS -> {
                                val l = edit.lens
                                SectionTitle("LENS CORRECTIONS") { edit = edit.copy(lens = LensFix()) }
                                EdSlider("Distortion", l.distortion) { v -> edit = edit.copy(lens = l.copy(distortion = v)) }
                                Text("+ straightens bulging lines from wide / ultra-wide lenses, − fixes pinched lines from telephotos.", color = Pro.Dim, fontSize = 11.sp)
                                EdSlider("Vignetting", l.vignette, 0f..1f, 0f, ::pct) { v -> edit = edit.copy(lens = l.copy(vignette = v)) }
                                EdSlider("Colour fringes", l.fringe) { v -> edit = edit.copy(lens = l.copy(fringe = v)) }
                                Text("Colour fringes lines up red and blue at the edges of the picture (purple / green outlines).", color = Pro.Dim, fontSize = 11.sp)
                                SectionTitle("AUTO")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PillChip(if (lensBusy) "MEASURING…" else "AUTO COLOUR FRINGES", l.caR != 0f || l.caB != 0f, accentText = true) {
                                        val b = base
                                        if (b != null && !lensBusy) { lensBusy = true; scope.launch {
                                            val (r, bl) = withContext(Dispatchers.Default) { try { PhotoLocal.measureCa(b) } catch (_: Throwable) { 0f to 0f } }
                                            lensBusy = false
                                            edit = edit.copy(lens = edit.lens.copy(caR = r, caB = bl))
                                            message = if (r == 0f && bl == 0f) "No colour fringing found (or too few edges to measure)" else "Colour fringes fixed: red %+.2f %%, blue %+.2f %%".format(r * 100, bl * 100)
                                        } }
                                    }
                                    PillChip("LENS PROFILE", l.profile != null, accentText = true) {
                                        scope.launch {
                                            val f35 = withContext(Dispatchers.IO) { try { context.contentResolver.openInputStream(sourceUri(edit))?.use { androidx.exifinterface.media.ExifInterface(it).getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0) } ?: 0 } catch (_: Throwable) { 0 } }
                                            val p = PhotoLocal.genericProfile(f35)
                                            if (p == null) message = "This photo doesn't say which lens it was taken with — set the sliders by eye"
                                            else { edit = edit.copy(lens = edit.lens.copy(distortion = p.second.distortion, vignette = p.second.vignette, profile = p.first)); message = "Lens profile: ${p.first}" }
                                        }
                                    }
                                }
                                if (l.caR != 0f || l.caB != 0f) Text("Measured fix: red %+.2f %%, blue %+.2f %% (tap again to measure again; RESET above clears it).".format(l.caR * 100, l.caB * 100), color = Pro.Dim, fontSize = 11.sp)
                                l.profile?.let { Text("Profile: $it — a generic profile for this kind of lens, chosen from the photo's camera details. Fine-tune with the sliders.", color = Pro.Dim, fontSize = 11.sp) }
                                Text("AUTO COLOUR FRINGES measures how far red and blue are off towards the corners and lines them up. LENS PROFILE sets distortion and corner shading for the lens the photo was taken with.", color = Pro.Dim, fontSize = 11.sp)
                            }
                            else -> PhotoPanelContent(
                                panel, edit, { edit = it },
                                aiUpscale = {
                                    Text("AI upscale", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                                    Chips(listOf("Off", "AI 2×", "AI 4×"), when (aiScale) { 2 -> 1; 4 -> 2; else -> 0 }) { aiScale = listOf(0, 2, 4)[it] }
                                    if (aiScale > 1) Text("Makes the exported photo bigger with the built-in AI (ESRGAN) — or Topaz in the cloud when Cloud AI is on. Up to 50 MP.", color = Pro.Dim, fontSize = 11.sp)
                                },
                                cameraInfo = {
                                    Text("Camera info", color = Color.White, fontSize = 13.sp)
                                    Chips(listOf("Keep camera info", "Remove all"), if (keepExif) 0 else 1) { keepExif = it == 0 }
                                    if (keepExif) Chips(listOf("Keep location", "Remove location"), if (stripGps) 1 else 0) { stripGps = it == 1 }
                                    Text("Date, camera, lens and settings are copied into JPEG and WebP exports.", color = Pro.Dim, fontSize = 11.sp)
                                },
                                exportState = ExportState(format, quality, sizeIdx),
                                onExportState = { f, q, si -> format = f; quality = q; sizeIdx = si },
                                onExport = { export() },
                                onExpand = { a, pos, pr -> expandTo(a, pos, pr) }, expandStatus = expandStatus,
                                onRecipe = { list -> if (list.isNotEmpty() && !busy) { recipeQueue.clear(); recipeQueue.addAll(list.drop(1)); applyPreset(list[0]); export() } },
                                busy = busy,
                                onPickLogo = { pickLogo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                isRaw = rawOn(edit),
                                rawMeta = if (rawOn(edit)) rawMeta else null,
                                thumbSrc = base,
                                dev = DevUi(
                                    picking = picking, setPicking = { picking = if (picking == it) 0 else it },
                                    tatMode = tatMode, setTatMode = { tatMode = it },
                                    clipWarn = clipWarn, setClipWarn = { clipWarn = it },
                                    sharpMask = sharpMaskView, setSharpMask = { sharpMaskView = it },
                                    autoTone = { base?.let { b -> edit = edit.copy(grade = Develop.autoTone(b, edit.grade)); message = "Auto tone applied" } },
                                    autoWb = { base?.let { b -> edit = edit.copy(grade = Develop.autoWb(b, edit.grade)); message = "Auto white balance applied" } },
                                    importXmp = { try { pickXmp.launch(arrayOf("*/*")) } catch (_: Throwable) {} },
                                    exportXmp = { exportXmp() },
                                    analyseDepth = { withDepth { p -> edit = edit.copy(lensBlur = (edit.lensBlur ?: LensBlur()).copy(depthFile = p, on = true)) } },
                                    depthBusy = depthBusy,
                                    upright = { mode ->
                                        val b = base
                                        if (mode < 0) edit = edit.copy(geometry = edit.geometry.copy(straighten = 0f, vertical = 0f, horizontal = 0f))
                                        else if (b != null && !uprightBusy) {
                                            uprightBusy = true
                                            scope.launch {
                                                val g = withContext(Dispatchers.Default) { try { Upright.solve(b, edit.geometry, mode) } catch (_: Throwable) { null } }
                                                uprightBusy = false
                                                if (g != null) { edit = edit.copy(geometry = g); message = "Upright: ${Upright.MODES[mode]} — rotate %.1f°".format(g.straighten) }
                                            }
                                        }
                                    },
                                    uprightBusy = uprightBusy,
                                ),
                            )
                        }
                        if (panel == PhotoPanel.EFFECTS) { backgroundSection(); skySection() }
                        if (panel == PhotoPanel.EXPORT) {
                            SectionTitle("BATCH")
                            Chips(listOf("Look only", "Look + crop + masks + watermark"), if (batchWithCrop) 1 else 0) { batchWithCrop = it == 1 }
                            Text(
                                if (busy) "WORKING…" else "BATCH EXPORT MANY PHOTOS…", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
                                modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised)
                                    .clickable(enabled = !busy) { batchPick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }.padding(10.dp),
                            )
                            Text("Pick up to 50 photos: each gets this edit and is exported with the settings above.", color = Pro.Dim, fontSize = 11.sp)
                        }
                    }
                }
            }

            Column(Modifier.fillMaxSize()) {
                // Top bar
                Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    // No back arrow here (it squeezed EXPORT); the phone's back gesture / button closes the editor.
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    BarIcon(Icons.AutoMirrored.Filled.Undo, "Undo", enabled = undoPos > 0) { undoPos--; edit = undo[undoPos] }
                    BarIcon(Icons.AutoMirrored.Filled.Redo, "Redo", enabled = undoPos < undo.lastIndex) { undoPos++; edit = undo[undoPos] }
                    BarIcon(Icons.Filled.BarChart, "Scope", tint = if (showScope) Pro.Amber else Color.White) { com.dronedirector.veyrocutpro.ui.Panels.set(context, "pe.scope", !showScope) }
                    PhotoPanelsButton(
                        onTab = { panel = it },
                        onReset = {
                            listOf("pe_controls_w", "pe_controls_h", "pe_strip_h", "pe_mon_film_s", "pe_mon_film_u", "pe_mon_tools_s", "pe_mon_tools_u").forEach { PaneSizes.set(context, it, -1f) }
                            paneVersion++
                            split = false
                            message = "Layout reset to original"
                        },
                    )
                    BarIcon(Icons.Filled.Settings, "Settings") { showSettings = true }
                    BarButton("LOAD", enabled = !busy) { showLoad = true }
                    BarButton("SAVE", enabled = !busy) { askName = true }
                    Text(
                        "EXPORT", style = Pro.Value.copy(color = Color.Black, fontSize = 12.sp), maxLines = 1, softWrap = false,
                        modifier = Modifier.padding(start = 4.dp, end = 4.dp).clip(RoundedCornerShape(50)).background(Pro.Amber).clickable(enabled = !busy) { panel = PhotoPanel.EXPORT; com.dronedirector.veyrocutpro.ui.Panels.set(context, "pe.controls", true) }.padding(horizontal = 10.dp, vertical = 7.dp),
                    )
                }
                // Shown whenever it is on in the Panels menu, even when empty (its + adds photos).
                val strip = com.dronedirector.veyrocutpro.ui.panelOn("pe.filmstrip")
                val stripPane = rememberPane("pe_strip_h", paneVersion)
                val stripSz = if (stripPane.dp < 0f) 64f else stripPane.dp
                // External monitor only: ultra-wide / super ultra-wide get filmstrip | photo | tools columns.
                val mon = com.dronedirector.veyrocutpro.ui.monitorClass(boxW, boxH)
                val monCols = mon == com.dronedirector.veyrocutpro.ui.MonitorClass.ULTRA_WIDE || mon == com.dronedirector.veyrocutpro.ui.MonitorClass.SUPER_ULTRA_WIDE
                val filmCol = @Composable { mod: Modifier ->
                    Filmstrip(
                        thumbSize = stripSz.dp, vertical = true, modifier = mod,
                        onZoom = { step -> stripPane.dp = (stripSz + step).coerceIn(40f, 200f); stripPane.save() },
                        onOpen = { switchTo(it) }, onAdd = onAddGallery, onSync = { syncTo(it) }, onExport = { exportSession(it) },
                    )
                }
                val viewer = @Composable { mod: Modifier ->
                    Column(mod) {
                        previewBox(Modifier.fillMaxWidth().weight(1f))
                        // Divider between the photo and the filmstrip: hold, then drag up for bigger thumbnails.
                        if (strip && !monCols) ResizeHandle(true,
                            onDrag = { d -> stripPane.dp = (stripSz - d).coerceIn(40f, 200f) },
                            onEnd = { stripPane.save() }, onReset = { stripPane.reset() })
                        if (strip && !monCols) Filmstrip(
                            thumbSize = stripSz.dp,
                            onZoom = { step -> stripPane.dp = (stripSz + step).coerceIn(40f, 200f); stripPane.save() },
                            onOpen = { switchTo(it) },
                            onAdd = onAddGallery,
                            onSync = { syncTo(it) },
                            onExport = { exportSession(it) },
                        )
                    }
                }
                // Resizable panels: hold the divider, then drag. Double-tap the divider to reset.
                // With the tools panel hidden, the photo fills the screen; shown again, it returns to its saved size.
                if (monCols) {
                    val sup = mon == com.dronedirector.veyrocutpro.ui.MonitorClass.SUPER_ULTRA_WIDE
                    val filmPane = rememberPane(if (sup) "pe_mon_film_s" else "pe_mon_film_u", paneVersion)
                    val toolsPane = rememberPane(if (sup) "pe_mon_tools_s" else "pe_mon_tools_u", paneVersion)
                    val filmW = if (filmPane.dp < 0f) boxW * (if (sup) 0.2f else 0.16f) else filmPane.dp
                    val toolsW = if (toolsPane.dp < 0f) boxW * (if (sup) 0.26f else 0.25f) else toolsPane.dp
                    Row(Modifier.fillMaxWidth().weight(1f)) {
                        if (strip) {
                            filmCol(Modifier.width(filmW.dp).fillMaxHeight())
                            ResizeHandle(false,
                                onDrag = { d -> filmPane.dp = (filmW + d).coerceIn(140f, boxW * 0.4f) },
                                onEnd = { filmPane.save() }, onReset = { filmPane.reset() })
                        }
                        viewer(Modifier.weight(1f).fillMaxHeight())
                        if (showControls) {
                            ResizeHandle(false,
                                onDrag = { d -> toolsPane.dp = (toolsW - d).coerceIn(280f, boxW * 0.45f) },
                                onEnd = { toolsPane.save() }, onReset = { toolsPane.reset() })
                            controls(Modifier.width(toolsW.dp).fillMaxHeight())
                        }
                    }
                } else if (!showControls) {
                    viewer(Modifier.fillMaxWidth().weight(1f))
                } else if (wide) {
                    val pane = rememberPane("pe_controls_w", paneVersion)
                    val maxW = boxW
                    Row(Modifier.fillMaxSize()) {
                        viewer(Modifier.weight(1f).fillMaxHeight())
                        ResizeHandle(false,
                            onDrag = { d -> pane.dp = ((if (pane.dp < 0f) 380f else pane.dp) - d).coerceIn(240f, maxW - 220f) },
                            onEnd = { pane.save() }, onReset = { pane.reset() })
                        controls(Modifier.width((if (pane.dp < 0f) 380f else pane.dp).dp).fillMaxHeight())
                    }
                } else {
                    val pane = rememberPane("pe_controls_h", paneVersion)
                    val maxH = boxH
                    viewer(Modifier.fillMaxWidth().weight(1f))
                    ResizeHandle(true,
                        onDrag = { d -> pane.dp = ((if (pane.dp < 0f) 330f else pane.dp) - d).coerceIn(150f, maxH - 220f) },
                        onEnd = { pane.save() }, onReset = { pane.reset() })
                    controls(Modifier.fillMaxWidth().height((if (pane.dp < 0f) 330f else pane.dp).dp))
                }
            }
        }
    }
}

@Composable
fun TopIcon(icon: ImageVector, label: String, enabled: Boolean = true, tint: Color = Color.White, onClick: () -> Unit) {
    Box(Modifier.size(42.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = label, tint = if (enabled) tint else tint.copy(alpha = 0.3f), modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun PanelTabs(selected: PhotoPanel, onSelect: (PhotoPanel) -> Unit) {
    val shown = if (com.dronedirector.veyrocutpro.camera.SimpleMode.on)
        listOf(PhotoPanel.PRESETS, PhotoPanel.LIGHT, PhotoPanel.COLOR, PhotoPanel.HEAL, PhotoPanel.CROP, PhotoPanel.TEXT, PhotoPanel.EXPORT)
    else PhotoPanel.entries.toList()
    Chips(shown.map { it.label }, shown.indexOf(selected)) { onSelect(shown[it]) }
}

/**
 * Photo exports (single, batch and filmstrip selection) run in this app-wide scope instead of the editor's own,
 * and hold the export service (progress notification + wake lock) while they run, so leaving the app, turning the
 * screen off or the editor being rebuilt doesn't stop them or get the app closed by Android.
 */
internal object PhotoExportJob {
    val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
    var active by mutableStateOf(false)
        private set
    private const val KEY = "photo-export"
    private var what = "Exporting"

    fun begin(context: Context, title: String) {
        active = true; what = title
        com.dronedirector.veyrocutpro.ExportService.begin(context, KEY, title, 0)
    }
    /** [done] photos finished out of [total]. */
    fun progress(context: Context, done: Int, total: Int) {
        val p = if (total > 0) done * 100 / total else 0
        com.dronedirector.veyrocutpro.ExportService.update(context, KEY, p, if (total > 1) "$what · ${done + 1} of $total" else what)
    }
    fun end(context: Context, result: String?) {
        active = false
        com.dronedirector.veyrocutpro.ExportService.end(context, KEY, result)
    }
}

/** The preview's AI noise reduction: the photo, its RAW develop and the preview size. */
private fun PhotoEdit.aiPreviewKey(owner: String, source: String, raw: Boolean, w: Int, h: Int): String {
    val k = "pv|$source|${baseFile ?: ""}|$w|$h|" + (if (raw) ProjectStore.gson.toJson(this.raw) else "")
    return Catalogs.photoId(owner) + "_" + java.security.MessageDigest.getInstance("SHA-1").digest(k.toByteArray()).joinToString("") { "%02x".format(it) }
}

/** What the AI noise reduction works on: the photo (and, for RAW, its develop and tonal sliders). */
private fun PhotoEdit.aiKey(owner: String, source: String, raw: Boolean): String {
    val g = grade
    val k = if (raw) "$source|raw|${ProjectStore.gson.toJson(this.raw)}|${g.exposure}|${g.highlights}|${g.shadows}|${g.whites}|${g.blacks}|${g.temperature}|${g.tint}"
    else "$source|${baseFile ?: ""}"
    // Named after the photo, so the catalog can let go of a photo's AI results together with the photo.
    return Catalogs.photoId(owner) + "_" + java.security.MessageDigest.getInstance("SHA-1").digest(k.toByteArray()).joinToString("") { "%02x".format(it) }
}

/**
 * Full-size AI noise reduction made while editing, kept in the app's cache (at full strength; the
 * strength slider blends it with the original). The export — in its own process — reads it from there.
 */
internal object AiDenoiseCache {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    private val jobs = HashMap<String, kotlinx.coroutines.Job>()
    // In the open catalog, with the photo (see Catalogs), so results are kept as long as the photo is.
    private fun dir(c: Context) = Catalogs.sub(c, "ai")
    private fun file(c: Context, key: String) = java.io.File(dir(c), "$key.px")
    private fun png(c: Context, key: String) = java.io.File(dir(c), "$key.png")
    /** Why the full-size pass is running (shown in the AI progress badge); null when it isn't. */
    var note by mutableStateOf<String?>(null)
        private set
    /** The photo the last full-size pass was made for: a new pass for the same photo is a re-run. */
    private var lastSource: String? = null
    @Volatile private var runs = 0

    fun has(c: Context, key: String) = png(c, key).length() > 8 || file(c, key).length() > 8

    /** Runs the AI on [decode]'s full-size picture once for [key] (any other unfinished one is dropped). */
    fun prepare(c: Context, key: String, source: String, decode: () -> Bitmap?) {
        val app = c.applicationContext
        synchronized(jobs) {
            if (jobs[key]?.isActive == true) return
            jobs.values.forEach { it.cancel() }   // an unfinished pass for older settings stops at its next tile
            jobs.clear()
            jobs[key] = scope.launch {
                var src: Bitmap? = null
                val again = lastSource == source
                val run = ++runs
                note = if (again) "Cleaning again — RAW settings changed" else "Cleaning the full-size photo"
                try {
                    src = decode() ?: return@launch
                    val job = coroutineContext[kotlinx.coroutines.Job]
                    val den = AiDenoiser.denoiseBitmap(app, src, 1f) { job?.isActive == false } ?: return@launch
                    store(app, key, den); den.recycle()
                    lastSource = source
                } catch (_: Throwable) {
                } finally { src?.recycle(); if (runs == run) note = null }
            }
        }
    }

    /** Waits for the background job of [key], if one is running. */
    suspend fun await(key: String) { synchronized(jobs) { jobs[key] }?.join() }

    fun store(c: Context, key: String, b: Bitmap) {
        // Lossless PNG: exactly the same pixels back, at roughly a third of the raw size.
        val f = png(c, key); val tmp = java.io.File(f.path + ".tmp")
        tmp.outputStream().buffered(1 shl 16).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        tmp.renameTo(f)
        file(c, key).delete()
        // Results of photos that aren't in the catalog (e.g. opened from a named project) are kept for 30 days of not being used.
        try {
            val ids = PhotoSession.items.map { Catalogs.photoId(it.uri) }.toSet()
            val old = System.currentTimeMillis() - 30L * 24 * 3600 * 1000
            dir(c).listFiles()?.filter { (it.name.endsWith(".px") || it.name.endsWith(".png")) && it.name.substringBefore('_') !in ids && it.lastModified() < old }?.forEach { it.delete() }
            java.io.File(c.cacheDir, "ai_denoise").deleteRecursively()
        } catch (_: Throwable) {}
    }

    fun load(c: Context, key: String): Bitmap? = try {
        val p = png(c, key)
        if (p.exists()) {
            p.setLastModified(System.currentTimeMillis())
            BitmapFactory.decodeFile(p.path, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888; inPremultiplied = true })
        } else loadRaw(c, key)
    } catch (_: Throwable) { null }

    /** Older, uncompressed results (.px); packed losslessly the first time they're read. */
    private fun loadRaw(c: Context, key: String): Bitmap? {
        val f = file(c, key)
        if (!f.exists()) return null
        val bmp = java.io.RandomAccessFile(f, "r").use { r ->
            val w = r.readInt(); val h = r.readInt()
            val buf = java.nio.ByteBuffer.allocateDirect(w * h * 4)
            while (buf.hasRemaining()) if (r.channel.read(buf) < 0) break
            buf.rewind()
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { copyPixelsFromBuffer(buf) }
        }
        try { store(c, key, bmp) } catch (_: Throwable) {}
        return bmp
    }
}

/** One photo export, handed to the export process as JSON. */
internal class PhotoExportReq(
    val edit: String, val uri: String, val owner: String, val source: String, val raw: Boolean, val maxPx: Int, val aiScale: Int,
    val format: Int, val quality: Float, val keepExif: Boolean, val stripGps: Boolean, val opts: ExportOpts.Snapshot,
)

/** What came of it: the saved picture, the size it was saved at, or why not. */
internal class PhotoExportRes(
    var saved: String? = null, var savedPx: Int = 0, var wantPx: Int = 0, var error: String? = null,
    var stopped: Boolean = false, var sidecarFailed: Boolean = false, var seq: Int = 0,
)

/**
 * Renders and saves one photo at the chosen size. The size chosen is always the size saved: if the
 * memory can't hold every step at that size, the heavy steps run a little smaller and the result is
 * enlarged back. The decoded, AI-cleaned picture is kept, so a retry never runs the AI again.
 */
internal fun runPhotoExport(context: Context, req: PhotoExportReq): PhotoExportRes {
    val res = PhotoExportRes()
    ExportOpts.apply(context, req.opts)
    PhotoExportError.last = null
    val e = ProjectStore.gson.fromJson(req.edit, PhotoEdit::class.java)
    val uri = Uri.parse(req.uri); val source = Uri.parse(req.source)
    val aiMasks = HashMap<String, Bitmap>()
    for (f in e.aiFiles()) try { BitmapFactory.decodeFile(f)?.let { aiMasks[f] = it } } catch (_: Throwable) {}
    val meta = if (req.raw) try { RawDng.open(context, uri) } catch (_: Throwable) { null } else null
    var cached: Bitmap? = null
    var savedPx = 0; var wantPx = 0

    fun attempt(maxPx: Int): Uri? {
        val c = cached
        var full: Bitmap = if (c != null) {
            val long = maxOf(c.width, c.height)
            if (long <= maxPx) c else { val k = maxPx.toFloat() / long; Bitmap.createScaledBitmap(c, (c.width * k).toInt().coerceAtLeast(1), (c.height * k).toInt().coerceAtLeast(1), true) }
        } else {
            var f = (if (meta != null) {
                val g = e.grade
                RawDng.export(context, meta, e.raw, g.exposure, g.highlights, g.shadows, g.whites, g.blacks, g.temperature, g.tint, maxPx)
            } else GradeIO.decode(context, source, maxPx)) ?: return null
            val ready = if (e.detail.aiDenoise > 0f) AiDenoiseCache.load(context, e.aiKey(req.owner, req.source, req.raw)) else null
            if (ready != null) {
                // Already cleaned while editing: blend it in at the chosen strength — the AI doesn't run again.
                val o = f.copy(Bitmap.Config.ARGB_8888, true)
                android.graphics.Canvas(o).drawBitmap(ready, null, android.graphics.Rect(0, 0, o.width, o.height),
                    android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG).apply { alpha = (e.detail.aiDenoise.coerceIn(0f, 1f) * 255).toInt() })
                ready.recycle(); f.recycle(); f = o
            } else if (e.detail.aiDenoise > 0f) {
                // Full-resolution AI noise reduction, tile by tile into a new picture (very little memory).
                var den: Bitmap? = null
                // Cloud AI (Topaz Denoise) when chosen in settings; otherwise / on failure the on-phone AI.
                if (com.dronedirector.veyrocutpro.camera.CloudAi.usePhotos(context)) {
                    try {
                        val jpeg = java.io.ByteArrayOutputStream().also { f.compress(Bitmap.CompressFormat.JPEG, 100, it) }.toByteArray()
                        val r = com.dronedirector.veyrocutpro.camera.CloudAi.denoisePhoto(context, jpeg)
                        BitmapFactory.decodeByteArray(r, 0, r.size)?.let { b ->
                            // Blend with the original at the chosen strength (drawn, not copied into arrays).
                            val o = f.copy(Bitmap.Config.ARGB_8888, true)
                            android.graphics.Canvas(o).drawBitmap(b, null, android.graphics.Rect(0, 0, o.width, o.height),
                                android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG).apply { alpha = (e.detail.aiDenoise.coerceIn(0f, 1f) * 255).toInt() })
                            b.recycle(); den = o
                        }
                    } catch (t: com.dronedirector.veyrocutpro.camera.CloudAi.Stopped) { throw AiDenoiser.Stopped() } catch (_: Throwable) {}
                }
                if (den == null) den = AiDenoiser.denoiseBitmap(context, f, e.detail.aiDenoise)
                den?.let { f.recycle(); f = it }
            }
            cached = f
            f
        }
        savedPx = maxOf(full.width, full.height)
        if (wantPx == 0) wantPx = savedPx
        val eg = if (meta != null) e.grade.rawRest() else e.grade
        val tables = if (eg.isIdentity) null else eg.tables()
        var out = ImageOps.renderPhoto(full, eg, e.detail, e.geometry, tables, e.lens, e.spots, e.masks, aiMasks, e.bg, e.sky, e.lensBlur, e.retouch)
        if (full !== cached) full.recycle()
        full = out
        // Back to the chosen size when the steps above had to run smaller (drawn in native memory).
        if (wantPx > savedPx) {
            val k = wantPx.toFloat() / savedPx
            val big = Bitmap.createBitmap((out.width * k).toInt().coerceAtLeast(1), (out.height * k).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(big).drawBitmap(out, null, android.graphics.Rect(0, 0, big.width, big.height), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.DITHER_FLAG))
            if (out.hasAlpha()) big.setHasAlpha(true)
            out.recycle(); out = big
        }
        // AI upscale (×2 / ×4, at most 50 MP).
        if (req.aiScale > 1) {
            val k = minOf(req.aiScale.toFloat(), kotlin.math.sqrt(50e6f / (out.width.toFloat() * out.height)))
            if (k > 1.05f) {
                val up = com.dronedirector.veyrocutpro.camera.Upscaler.upscaleBitmap(context, out, (out.width * k).toInt() / 2 * 2, (out.height * k).toInt() / 2 * 2)
                    ?: throw AiDenoiser.Stopped()
                out.recycle(); out = up
            }
        }
        for (t in e.texts) out = stampLayer(context, out, t)
        e.watermark?.let { out = stampLayer(context, out, it) }
        // A removed background needs a format with transparency.
        val fmt = if (e.bg.active && e.bg.mode == 2 && req.format !in listOf(1, 2, 4, 5)) 1 else req.format
        val saved = savePhotoAs(context, out, fmt, (req.quality * 100).toInt(), if (req.keepExif) uri else null, req.stripGps)
        out.recycle()
        // Camera Raw (.xmp) sidecar for the original, with the closest matching settings.
        if (saved != null && PhotoMeta.sidecar && uri != Uri.EMPTY) {
            if (PhotoMeta.writeSidecar(context, uri, PhotoMeta.sidecarXmp(e.grade, e.detail, e.geometry, meta != null)) == null) res.sidecarFailed = true
        }
        return saved
    }

    var px = req.maxPx
    try {
        for (tryNo in 0 until 6) {
            try { res.saved = attempt(px)?.toString(); if (res.saved == null && res.error == null) res.error = PhotoExportError.last; break }
            catch (_: OutOfMemoryError) {
                System.gc()
                // Start from what the photo really is, then step down.
                val real = if (savedPx > 0) savedPx else px.coerceAtMost(8000)
                px = (minOf(px, real) * 0.8f).toInt()
                res.error = "not enough memory for this size with these settings"
                if (px < 1000) break
            } catch (t: Throwable) {
                if (t is AiDenoiser.Stopped || t.cause is AiDenoiser.Stopped) res.stopped = true
                else res.error = (t.message ?: t.javaClass.simpleName).take(80)
                break
            }
        }
    } finally {
        cached?.recycle(); aiMasks.values.forEach { it.recycle() }
    }
    if (res.saved != null) res.error = null
    res.savedPx = savedPx; res.wantPx = wantPx; res.seq = ExportOpts.seq
    return res
}

private data class ExportState(val format: Int, val quality: Float, val sizeIdx: Int)

@Composable
private fun PhotoPanelContent(
    panel: PhotoPanel,
    e: PhotoEdit,
    set: (PhotoEdit) -> Unit,
    exportState: ExportState,
    onExportState: (Int, Float, Int) -> Unit,
    onRecipe: (List<ExportOpts.Preset>) -> Unit = {},
    onExpand: (Float, Int, String) -> Unit = { _, _, _ -> }, expandStatus: String? = null,
    onExport: () -> Unit,
    busy: Boolean,
    onPickLogo: () -> Unit = {},
    isRaw: Boolean = false,
    rawMeta: RawDng.Meta? = null,
    thumbSrc: Bitmap? = null,
    dev: DevUi? = null,
    aiUpscale: @Composable () -> Unit = {},
    cameraInfo: @Composable () -> Unit = {},
) {
    val g = e.grade
    fun grade(f: (ProGrade) -> ProGrade) = set(e.copy(grade = f(g)))
    fun detail(f: (Detail) -> Detail) = set(e.copy(detail = f(e.detail)))
    fun geo(f: (Geometry) -> Geometry) = set(e.copy(geometry = f(e.geometry)))

    when (panel) {
        PhotoPanel.PRESETS -> {
            UserPresetSection({ g to e.detail }) { p -> set(e.copy(grade = p.grade, detail = p.detail.copy(aiDenoise = e.detail.aiDenoise))) }
            if (dev != null) Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillChip("IMPORT .XMP PRESETS", false, accentText = true) { dev.importXmp() }
                PillChip("EXPORT AS .XMP", false, accentText = true) { dev.exportXmp() }
            }
            Text("Import .xmp presets or sidecars (Camera Raw settings) (they're added to MY PRESETS). Export saves this look as an .xmp preset in Documents/AlcorCutPro/Presets.", color = Pro.Dim, fontSize = 11.sp)
            BuiltInPresetSection(thumbSrc, g, e.detail) { ng, nd -> set(e.copy(grade = ng, detail = nd.copy(aiDenoise = e.detail.aiDenoise))) }
            LutSection(g) { ng -> set(e.copy(grade = ng)) }
            SectionTitle("PROFILE / LOOK")
            LookStrip(g.look) { f -> grade { it.copy(look = f) } }
            if (g.look != Filter.NONE) EdSlider("Amount", g.lookAmount, 0f..1f, 1f, ::pct) { v -> grade { it.copy(lookAmount = v) } }
            SectionTitle("INPUT (LOG / FLAT PHOTOS)")
            Chips(InputColor.entries.map { it.label }, g.input.ordinal) { i -> grade { it.copy(input = InputColor.entries[i]) } }
            Text(g.input.detail, color = Pro.Dim, fontSize = 11.sp)
            SectionTitle("EVERYTHING")
            Text(
                "RESET ALL EDITS", style = Pro.Label.copy(color = Pro.Red, fontSize = 11.sp),
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { set(PhotoEdit()) }.padding(8.dp),
            )
        }
        PhotoPanel.LIGHT -> {
            if (isRaw) {
                RawLookStrip(rawMeta, e.raw) { i -> set(e.copy(raw = e.raw.copy(look = i))) }
                DcpRow(rawMeta, e.raw) { d -> set(e.copy(raw = d)) }
                SectionTitle("RAW PROFILE") { set(e.copy(raw = e.raw.copy(profile = 0))) }
                // "Camera" (the maker's own profile in the file) only when the DNG has one.
                val profs = RawDng.PROFILES.withIndex().filter { !it.value.camera || rawMeta?.hasCameraProfile == true }
                Chips(profs.map { p -> if (p.value.camera) "Camera" + (rawMeta?.profileName?.let { " · " + it.take(18) } ?: "") else p.value.label },
                    profs.indexOfFirst { it.index == e.raw.profile }.coerceAtLeast(0)) { i -> set(e.copy(raw = e.raw.copy(profile = profs[i].index))) }
                Text("RAW (DNG): exposure, highlights, shadows, whites, blacks and white balance work on the sensor data, with headroom above white.", color = Pro.Dim, fontSize = 11.sp)
                if (rawMeta?.hasLensOps == true) {
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).background(Ed.Raised)
                        .clickable { set(e.copy(raw = e.raw.copy(lensOff = !e.raw.lensOff))) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Lens corrections from the file", color = Color.White, fontSize = 14.sp)
                            Text("Distortion, colour fringing and vignetting as the camera recorded them in the DNG.", color = Pro.Dim, fontSize = 11.sp)
                        }
                        androidx.compose.material3.Switch(checked = !e.raw.lensOff, onCheckedChange = { set(e.copy(raw = e.raw.copy(lensOff = !it))) },
                            colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Pro.Amber))
                    }
                    if (!e.raw.lensOff) Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillChip(if (e.raw.vigOff) "Dark corners: OFF" else "Dark corners ✓", !e.raw.vigOff) { set(e.copy(raw = e.raw.copy(vigOff = !e.raw.vigOff))) }
                        PillChip(if (e.raw.shapeOff) "Lens shape: OFF" else "Lens shape ✓", !e.raw.shapeOff) { set(e.copy(raw = e.raw.copy(shapeOff = !e.raw.shapeOff))) }
                    }
                }
                rawMeta?.info?.takeIf { it.isNotEmpty() }?.let { info ->
                    SectionTitle("RAW INFO")
                    info.forEach { (k, v) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(k, color = Pro.Dim, fontSize = 12.sp, modifier = Modifier.width(130.dp))
                            Text(v, color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
            SectionTitle("LIGHT") { grade { it.copy(exposure = 0f, contrast = 0f, highlights = 0f, shadows = 0f, whites = 0f, blacks = 0f) } }
            if (dev != null) Row(Modifier.padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillChip("AUTO", false, accentText = true) { dev.autoTone() }
                PillChip(if (dev.clipWarn) "CLIPPING ⚠ ON" else "CLIPPING ⚠", dev.clipWarn) { dev.setClipWarn(!dev.clipWarn) }
            }
            if (dev?.clipWarn == true) Text("Red = blown-out highlights, blue = crushed shadows.", color = Pro.Dim, fontSize = 11.sp)
            EdSlider("Exposure", g.exposure, if (isRaw) -5f..5f else -3f..3f, format = ::ev) { v -> grade { it.copy(exposure = v) } }
            EdSlider("Contrast", g.contrast) { v -> grade { it.copy(contrast = v) } }
            EdSlider("Highlights", g.highlights) { v -> grade { it.copy(highlights = v) } }
            EdSlider("Shadows", g.shadows) { v -> grade { it.copy(shadows = v) } }
            EdSlider("Whites", g.whites) { v -> grade { it.copy(whites = v) } }
            EdSlider("Blacks", g.blacks) { v -> grade { it.copy(blacks = v) } }
        }
        PhotoPanel.CURVE -> {
            var ch by remember { mutableStateOf(0) }
            SectionTitle("POINT CURVES") { grade { it.copy(curveRgb = null, curveR = null, curveG = null, curveB = null) } }
            val all = listOf(g.curveRgb, g.curveR, g.curveG, g.curveB)
            CurveEditor(ch, { ch = it }, all[ch], { p ->
                grade { when (ch) { 1 -> it.copy(curveR = p); 2 -> it.copy(curveG = p); 3 -> it.copy(curveB = p); else -> it.copy(curveRgb = p) } }
            }, others = all)
            SectionTitle("PARAMETRIC TONE CURVE") { grade { it.copy(curveHighlights = 0f, curveLights = 0f, curveDarks = 0f, curveShadows = 0f) } }
            if (dev != null) PillChip(if (dev.picking == 6) "◎ TARGETED — DRAG ON THE PHOTO" else "◎ TARGETED ADJUSTMENT", dev.picking == 6, accentText = true) { dev.setPicking(6) }
            EdSlider("Highlights", g.curveHighlights) { v -> grade { it.copy(curveHighlights = v) } }
            EdSlider("Lights", g.curveLights) { v -> grade { it.copy(curveLights = v) } }
            EdSlider("Darks", g.curveDarks) { v -> grade { it.copy(curveDarks = v) } }
            EdSlider("Shadows", g.curveShadows) { v -> grade { it.copy(curveShadows = v) } }
            SectionTitle("CONTRAST PIVOT")
            EdSlider("Pivot", g.pivot, 0.2f..0.7f, 0.435f, ::pct) { v -> grade { it.copy(pivot = v) } }
        }
        PhotoPanel.COLOR -> {
            SectionTitle("WHITE BALANCE") { set(e.copy(raw = if (isRaw) e.raw.copy(wb = 0) else e.raw, grade = g.copy(temperature = 0f, tint = 0f))) }
            if (isRaw) Chips(RawDng.WB_PRESETS.map { it.first }, e.raw.wb) { i -> set(e.copy(raw = e.raw.copy(wb = i))) }
            if (dev != null) Row(Modifier.padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillChip("AUTO", false, accentText = true) { dev.autoWb() }
                PillChip(if (dev.picking == 1) "EYEDROPPER — TAP THE PHOTO" else "EYEDROPPER", dev.picking == 1) { dev.setPicking(1) }
            }
            EdSlider("Temperature", g.temperature) { v -> grade { it.copy(temperature = v) } }
            EdSlider("Tint", g.tint) { v -> grade { it.copy(tint = v) } }
            SectionTitle("PRESENCE") { grade { it.copy(vibrance = 0f, saturation = 0f) } }
            EdSlider("Vibrance", g.vibrance) { v -> grade { it.copy(vibrance = v) } }
            EdSlider("Saturation", g.saturation) { v -> grade { it.copy(saturation = v) } }
            // ---- Point Color
            SectionTitle("POINT COLOR") { grade { it.copy(pointColors = null) } }
            if (dev != null) PillChip(if (dev.picking == 2) "TAP A COLOUR ON THE PHOTO" else "+ PICK A COLOUR", dev.picking == 2, accentText = true) { dev.setPicking(2) }
            val pcs = g.pointColors.orEmpty()
            var pcSel by remember { mutableStateOf(0) }
            if (pcs.isEmpty()) Text("Pick a colour on the photo, then shift just that colour's hue, saturation and brightness.", color = Pro.Dim, fontSize = 11.sp)
            else {
                val sel = pcSel.coerceIn(0, pcs.lastIndex)
                Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    pcs.forEachIndexed { i, pc ->
                        Box(Modifier.size(28.dp).clip(CircleShape).background(Color(android.graphics.Color.HSVToColor(floatArrayOf(pc.hue, pc.sat, pc.lum.coerceAtLeast(0.3f)))))
                            .border(2.dp, if (i == sel) Color.White else Color.Transparent, CircleShape).clickable { pcSel = i })
                    }
                    Text("DELETE", style = Pro.Label.copy(color = Pro.Red, fontSize = 10.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { grade { it.copy(pointColors = pcs.filterIndexed { k, _ -> k != sel }.ifEmpty { null }) }; pcSel = 0 }.padding(6.dp))
                }
                val pc = pcs[sel]
                fun upd(n: PointColor) = grade { it.copy(pointColors = pcs.toMutableList().also { l -> l[sel] = n }) }
                EdSlider("Hue shift", pc.dHue) { v -> upd(pc.copy(dHue = v)) }
                EdSlider("Saturation", pc.dSat) { v -> upd(pc.copy(dSat = v)) }
                EdSlider("Luminance", pc.dLum) { v -> upd(pc.copy(dLum = v)) }
                EdSlider("Range", pc.range, 0f..1f, 0.5f, ::pct) { v -> upd(pc.copy(range = v)) }
            }
            // ---- Calibration
            val cal = g.calib ?: Calibration()
            fun calib(f: (Calibration) -> Calibration) = grade { it.copy(calib = f(cal).takeIf { c -> !c.isZero }) }
            SectionTitle("CALIBRATION") { grade { it.copy(calib = null) } }
            if (dev != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PillChip(if (dev.picking == 8 || dev.picking == 9) "TAP THE CHART…" else "FROM A COLOUR CHART", dev.picking == 8 || dev.picking == 9, accentText = true) { dev.setPicking(8) }
                    if (g.chart != null) Text("CLEAR CHART", style = Pro.Label.copy(color = Pro.Red, fontSize = 10.sp), modifier = Modifier.clickable { grade { it.copy(chart = null) } }.padding(6.dp))
                }
                Text(if (g.chart != null) "Calibrated from a colour chart. Save the look in MY PRESETS to use it on other photos taken in the same light." else
                    "Photograph a 24-patch colour chart (4 rows × 6) in your light, open that photo, tap FROM A COLOUR CHART, then tap the middle of the brown first patch and of the black last patch. Do it before cropping.", color = Pro.Dim, fontSize = 11.sp)
            }
            EdSlider("Shadows tint", cal.shadowTint) { v -> calib { it.copy(shadowTint = v) } }
            EdSlider("Red primary · hue", cal.redHue) { v -> calib { it.copy(redHue = v) } }
            EdSlider("Red primary · saturation", cal.redSat) { v -> calib { it.copy(redSat = v) } }
            EdSlider("Green primary · hue", cal.greenHue) { v -> calib { it.copy(greenHue = v) } }
            EdSlider("Green primary · saturation", cal.greenSat) { v -> calib { it.copy(greenSat = v) } }
            EdSlider("Blue primary · hue", cal.blueHue) { v -> calib { it.copy(blueHue = v) } }
            EdSlider("Blue primary · saturation", cal.blueSat) { v -> calib { it.copy(blueSat = v) } }
        }
        PhotoPanel.MIXER -> {
            if (dev != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    PillChip(if (dev.picking == 7) "◎ DRAG ON THE PHOTO" else "◎ TARGETED", dev.picking == 7, accentText = true) { dev.setPicking(7) }
                    listOf("Hue", "Sat", "Lum").forEachIndexed { k, n -> PillChip(n, dev.tatMode == k) { dev.setTatMode(k); if (dev.picking != 7) dev.setPicking(7) } }
                }
                Text("Targeted: put a finger on a colour in the photo and drag up or down — the matching colour sliders move with it.", color = Pro.Dim, fontSize = 11.sp)
            }
            HslMixer(g.hsl) { list -> grade { it.copy(hsl = list) } }
        }
        PhotoPanel.GRADING -> {
            SectionTitle("COLOR GRADING") { grade { it.copy(gradeShadows = Wheel(200f), gradeMids = Wheel(40f), gradeHighlights = Wheel(40f)) } }
            WheelControl("Shadows", g.gradeShadows, showLuma = false) { w -> grade { it.copy(gradeShadows = w) } }
            WheelControl("Midtones", g.gradeMids, showLuma = false) { w -> grade { it.copy(gradeMids = w) } }
            WheelControl("Highlights", g.gradeHighlights, showLuma = false) { w -> grade { it.copy(gradeHighlights = w) } }
            WheelControl("Global", g.gradeGlobal ?: Wheel(40f), showLuma = true) { w -> grade { it.copy(gradeGlobal = w.takeIf { x -> !x.isZero }) } }
            EdSlider("Blending", 0.5f + g.gradeBlend, 0f..1f, 0.5f, ::pct) { v -> grade { it.copy(gradeBlend = v - 0.5f) } }
            EdSlider("Balance", g.gradeBalance) { v -> grade { it.copy(gradeBalance = v) } }
            Text("Blending: how much the shadow, midtone and highlight tints overlap. Balance: moves the split between shadows and highlights.", color = Pro.Dim, fontSize = 11.sp)
        }
        PhotoPanel.DETAIL -> {
            SectionTitle("SHARPENING")
            EdSlider("Amount", e.detail.sharpen, 0f..1f, format = ::pct) { v -> detail { it.copy(sharpen = v) } }
            EdSlider("Radius", e.detail.sharpRadius, 0.5f..3f, 1f, { "%.1f".format(it) }) { v -> detail { it.copy(sharpenRadius = v - 1f) } }
            EdSlider("Detail", e.detail.sharpDetail, 0f..1f, 0.25f, ::pct) { v -> detail { it.copy(sharpenDetail = v - 0.25f) } }
            EdSlider("Masking", e.detail.sharpenMasking, 0f..1f, 0f, ::pct) { v -> detail { it.copy(sharpenMasking = v) } }
            if (dev != null) PillChip(if (dev.sharpMask) "SHOW MASK ✓" else "SHOW MASK", dev.sharpMask) { dev.setSharpMask(!dev.sharpMask) }
            Text("Masking keeps sharpening off smooth areas (skin, sky): white in SHOW MASK is sharpened. Pinch or double-tap the photo for a 1:1 view.", color = Pro.Dim, fontSize = 11.sp)
            SectionTitle("AI NOISE REDUCTION")
            EdSlider("Strength", e.detail.aiDenoise, 0f..1f, format = { if (it <= 0f) "Off" else pct(it) }) { v -> detail { it.copy(aiDenoise = v) } }
            Text("Built-in AI (NAFNet, trained on real phone-camera noise). Runs on the phone and cleans the full-size photo once while you edit; the export reuses it.", color = Pro.Dim, fontSize = 11.sp)
            // When the AI has to run again: only what changes the picture it works on.
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).background(Pro.Amber.copy(alpha = 0.12f)).padding(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = Pro.Amber, modifier = Modifier.size(18.dp).padding(top = 1.dp))
                Text(
                    if (isRaw) "Use this last for RAW photos: first set exposure, white balance, highlights, shadows, whites and blacks. Changing those afterwards makes the AI noise reduction run again. Clarity, texture, colour grading, curves, masks and everything else can be changed freely."
                    else "Clarity, texture, shadows, colour grading and every other slider can be changed freely afterwards — the AI noise reduction doesn't run again. Only AI object removal or AI expand (they make a new picture) needs a new run.",
                    color = Color.White, fontSize = 11.5.sp, modifier = Modifier.padding(start = 8.dp),
                )
            }
            SectionTitle("NOISE REDUCTION")
            EdSlider("Luminance & colour", e.detail.denoise, 0f..1f, format = ::pct) { v -> detail { it.copy(denoise = v) } }
            EdSlider("Colour noise", e.detail.colorNoise, 0f..1f, 0f, ::pct) { v -> detail { it.copy(colorNoise = v) } }
            Text("Colour noise smooths blotchy colour specks — like blue patches in the dark parts of photos taken in dim, warm light — and keeps the fine detail.", color = Pro.Dim, fontSize = 11.sp)
            SectionTitle("PRESENCE")
            EdSlider("Texture", e.detail.texture) { v -> detail { it.copy(texture = v) } }
            EdSlider("Clarity", e.detail.clarity) { v -> detail { it.copy(clarity = v) } }
            EdSlider("Dehaze", g.dehaze) { v -> grade { it.copy(dehaze = v) } }
            SectionTitle("DEFRINGE") { detail { it.copy(defringePurple = 0f, defringeGreen = 0f) } }
            EdSlider("Purple fringe", e.detail.defringePurple, 0f..1f, 0f, ::pct) { v -> detail { it.copy(defringePurple = v) } }
            EdSlider("Green fringe", e.detail.defringeGreen, 0f..1f, 0f, ::pct) { v -> detail { it.copy(defringeGreen = v) } }
        }
        PhotoPanel.EFFECTS -> {
            SectionTitle("EFFECTS")
            EdSlider("Vignette", e.detail.vignette) { v -> detail { it.copy(vignette = v) } }
            if (e.detail.vignette != 0f) {
                EdSlider("  Midpoint", e.detail.vigMid, 0f..1f, 0.5f, ::pct) { v -> detail { it.copy(vignetteMid = v - 0.5f) } }
                EdSlider("  Roundness", e.detail.vignetteRound) { v -> detail { it.copy(vignetteRound = v) } }
                EdSlider("  Feather", e.detail.vigFeather, 0f..1f, 0.5f, ::pct) { v -> detail { it.copy(vignetteFeather = v - 0.5f) } }
                EdSlider("  Highlights", e.detail.vignetteHighlights, 0f..1f, 0f, ::pct) { v -> detail { it.copy(vignetteHighlights = v) } }
            }
            EdSlider("Grain", e.detail.grain, 0f..1f, format = ::pct) { v -> detail { it.copy(grain = v) } }
            if (e.detail.grain > 0f) {
                EdSlider("  Size", e.detail.grainSz, 0f..1f, 0.25f, ::pct) { v -> detail { it.copy(grainSize = v - 0.25f) } }
                EdSlider("  Roughness", e.detail.grainRg, 0f..1f, 0.5f, ::pct) { v -> detail { it.copy(grainRough = v - 0.5f) } }
            }
            EdSlider("Fade", g.fade, 0f..1f, format = ::pct) { v -> grade { it.copy(fade = v) } }
            // ---- Lens Blur
            val lb = e.lensBlur
            SectionTitle("LENS BLUR (AI DEPTH)") { set(e.copy(lensBlur = null)) }
            if (lb?.depthFile == null) {
                Text(
                    if (dev?.depthBusy == true) "Working out the depth…" else "ANALYSE DEPTH", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
                    modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable(enabled = dev?.depthBusy != true) { dev?.analyseDepth?.invoke() }.padding(10.dp),
                )
                Text("A shallow depth of field like a fast lens: the AI works out how far everything is, then you pick the focus and how strong the blur is.", color = Pro.Dim, fontSize = 11.sp)
            } else {
                fun lbs(f: (LensBlur) -> LensBlur) = set(e.copy(lensBlur = f(lb)))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Lens blur", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(lb.on, { v -> lbs { it.copy(on = v) } }, colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Pro.Amber, checkedThumbColor = Color.Black))
                }
                EdSlider("Blur amount", lb.amount, 0f..1f, 0.5f, ::pct) { v -> lbs { it.copy(amount = v) } }
                if (dev != null) PillChip(if (dev.picking == 4) "TAP THE PHOTO TO FOCUS" else "⌖ PICK FOCUS", dev.picking == 4, accentText = true) { dev.setPicking(4) }
                EdSlider("Focus distance", lb.focus, 0f..1f, 0.8f, { if (it > 0.66f) "Near ${pct(it)}" else if (it < 0.33f) "Far ${pct(it)}" else pct(it) }) { v -> lbs { it.copy(focus = v) } }
                EdSlider("Focus range", lb.range, 0f..0.5f, 0.12f, ::pct) { v -> lbs { it.copy(range = v) } }
                Text("Bokeh", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                Chips(BOKEH_SHAPES, lb.shape) { i -> lbs { it.copy(shape = i) } }
                EdSlider("Bokeh boost", lb.boost, 0f..1f, 0.4f, ::pct) { v -> lbs { it.copy(boost = v) } }
            }
        }
        PhotoPanel.CROP -> {
            val geom = e.geometry
            SectionTitle("ASPECT") { geo { Geometry() } }
            val aspects = listOf(
                "Original" to 0f, "1:1" to 1f, "4:5" to 0.8f, "5:4" to 1.25f, "4:3" to 4f / 3f, "3:4" to 0.75f, "3:2" to 1.5f, "2:3" to 0.6667f,
                "16:9" to 16f / 9f, "9:16" to 9f / 16f, "21:9" to 21f / 9f, "2.39:1" to 2.39f, "5:7" to 5f / 7f,
            )
            Chips(aspects.map { it.first }, aspects.indexOfFirst { it.second == geom.aspect }.coerceAtLeast(0)) { i -> geo { it.copy(aspect = aspects[i].second) } }
            SectionTitle("EXPAND (CLOUD AI)")
            var exAsp by remember { mutableStateOf(0) }
            var exPos by remember { mutableStateOf(0) }
            var exPrompt by remember { mutableStateOf("") }
            val exAspects = listOf("16:9" to 16f / 9f, "3:2" to 1.5f, "4:3" to 4f / 3f, "1:1" to 1f, "4:5" to 0.8f, "9:16" to 9f / 16f, "Wider all round" to 0f)
            Chips(exAspects.map { it.first }, exAsp) { exAsp = it }
            Chips(listOf("Photo centred", "Photo left", "Photo right", "Photo top", "Photo bottom"), exPos) { exPos = it }
            androidx.compose.material3.OutlinedTextField(exPrompt, { exPrompt = it.take(200) }, label = { Text("What to add (optional, English)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
            Text(expandStatus ?: "EXPAND", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Ed.Raised).clickable(enabled = expandStatus == null) {
                val a = exAspects[exAsp].second
                onExpand(if (a == 0f) -1f else a, exPos, exPrompt)
            }.padding(10.dp))
            Text("Makes the picture bigger and paints in what's around it (Bria Expand via your fal.ai account, about \$0.04 per photo; trained only on licensed images). Crop, spots and masks are cleared afterwards.", color = Pro.Dim, fontSize = 11.sp)
            SectionTitle("GUIDES")
            Chips(CropGuides.MODES, CropGuides.mode) { CropGuides.mode = it }
            if (CropGuides.turnable) Text("TURN ↻", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Ed.Raised).clickable { CropGuides.turn = (CropGuides.turn + 1) % 4 }.padding(10.dp))
            Text("Composition lines over the photo while you crop — only on screen, never in the saved picture.", color = Pro.Dim, fontSize = 11.sp)
            SectionTitle("UPRIGHT") { dev?.upright?.invoke(-1) }
            if (dev != null) {
                Row(Modifier.hintedScroll(horizontal = true), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Upright.MODES.forEachIndexed { i, n -> PillChip(n, false, accentText = i == 0) { dev.upright(i) } }
                    PillChip("Off", false) { dev.upright(-1) }
                }
                Text(if (dev.uprightBusy) "Finding the lines…" else "Auto: balanced fix · Level: straightens the horizon · Vertical: also makes buildings upright · Full: vertical and horizontal perspective.", color = if (dev.uprightBusy) Pro.Amber else Pro.Dim, fontSize = 11.sp)
            }
            SectionTitle("ROTATE & FLIP")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallAction(Icons.Filled.RotateRight, "Rotate") { geo { it.copy(rotate90 = it.rotate90 + 1) } }
                SmallAction(Icons.Filled.Flip, "Flip H") { geo { it.copy(flipH = !it.flipH) } }
                SmallAction(Icons.Filled.Flip, "Flip V") { geo { it.copy(flipV = !it.flipV) } }
            }
            EdSlider("Straighten", geom.straighten, -45f..45f, format = { "%+.1f°".format(it) }) { v -> geo { it.copy(straighten = v) } }
            SectionTitle("PERSPECTIVE") { geo { it.copy(vertical = 0f, horizontal = 0f) } }
            EdSlider("Vertical", geom.vertical) { v -> geo { it.copy(vertical = v) } }
            Text("+ straightens buildings that lean in at the top (shot from below), − the opposite.", color = Pro.Dim, fontSize = 11.sp)
            EdSlider("Horizontal", geom.horizontal) { v -> geo { it.copy(horizontal = v) } }
            SectionTitle("FRAMING")
            EdSlider("Zoom", geom.zoom, 1f..4f, 1f, { "%.2fx".format(it) }) { v -> geo { it.copy(zoom = v) } }
            EdSlider("Horizontal", geom.offsetX) { v -> geo { it.copy(offsetX = v) } }
            EdSlider("Vertical", geom.offsetY) { v -> geo { it.copy(offsetY = v) } }
        }
        PhotoPanel.WATERMARK -> {
            val wm = e.watermark
            SectionTitle("WATERMARK") { set(e.copy(watermark = null)) }
            Chips(listOf("None", "Text", "Logo image"), when { wm == null -> 0; wm.kind == LayerKind.LOGO -> 2; else -> 1 }) { i ->
                when (i) {
                    0 -> set(e.copy(watermark = null))
                    1 -> set(e.copy(watermark = Layer(kind = LayerKind.WATERMARK, text = "© AlcorCut Pro", size = 0.035f, x = 0.84f, y = 0.93f, opacity = 0.7f, font = LayerFont.LIGHT, wholeVideo = true)))
                    2 -> onPickLogo()
                }
            }
            if (wm != null) {
                if (wm.kind != LayerKind.LOGO) {
                    androidx.compose.material3.OutlinedTextField(
                        value = wm.text, onValueChange = { set(e.copy(watermark = wm.copy(text = it))) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), label = { Text("Watermark text") },
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedBorderColor = Pro.Amber, unfocusedBorderColor = Pro.Line, focusedLabelColor = Pro.Amber, cursorColor = Pro.Amber,
                        ),
                    )
                    Chips(LayerFont.entries.map { it.label }, wm.font.ordinal) { set(e.copy(watermark = wm.copy(font = LayerFont.entries[it]))) }
                    Chips(listOf("White", "Black", "Accent"), when (wm.color) { 0xFF000000.toInt() -> 1; 0xFFFFFFFF.toInt() -> 0; else -> 2 }) {
                        val c = when (it) { 1 -> 0xFF000000.toInt(); 2 -> android.graphics.Color.argb(255, (Pro.Amber.red * 255).toInt(), (Pro.Amber.green * 255).toInt(), (Pro.Amber.blue * 255).toInt()); else -> 0xFFFFFFFF.toInt() }
                        set(e.copy(watermark = wm.copy(color = c)))
                    }
                }
                EdSlider("Size", wm.size, 0.015f..0.25f, 0.035f, ::pct) { set(e.copy(watermark = wm.copy(size = it))) }
                EdSlider("Opacity", wm.opacity, 0.05f..1f, 0.7f, ::pct) { set(e.copy(watermark = wm.copy(opacity = it))) }
                SectionTitle("POSITION")
                val spots = listOf(0.08f, 0.5f, 0.92f)
                spots.forEach { y ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 2.dp)) {
                        spots.forEach { x ->
                            val on = kotlin.math.abs(wm.x - x) < 0.1f && kotlin.math.abs(wm.y - y) < 0.1f
                            Box(Modifier.size(width = 44.dp, height = 26.dp).clip(RoundedCornerShape(4.dp)).background(if (on) Pro.Amber else Ed.Raised)
                                .clickable { set(e.copy(watermark = wm.copy(x = if (x < 0.5f) 0.16f else if (x > 0.5f) 0.84f else 0.5f, y = if (y < 0.5f) 0.07f else if (y > 0.5f) 0.93f else 0.5f))) })
                        }
                    }
                }
            }
        }
        PhotoPanel.MASKS, PhotoPanel.HEAL, PhotoPanel.RETOUCH, PhotoPanel.LENS, PhotoPanel.HISTORY, PhotoPanel.TEXT -> {}   // drawn by the workspace
        PhotoPanel.EXPORT -> {
            SectionTitle("EXPORT")
            Text("Format", color = Color.White, fontSize = 13.sp)
            Chips(PHOTO_FORMATS, exportState.format) { onExportState(it, exportState.quality, exportState.sizeIdx) }
            if (exportState.format in 4..5) Text("TIFF: uncompressed and lossless (large files), for print and other editing software.", color = Pro.Dim, fontSize = 11.sp)
            if (exportState.format == 3) Text("HEIC: about half the size of JPEG (Android 9+).", color = Pro.Dim, fontSize = 11.sp)
            if (exportState.format == 6) Text(if (AvifWriter.supported) "AVIF: often half the size of JPEG at the same quality; opens on Android 12+, current browsers and most photo apps." else "AVIF needs an AV1 encoder — this phone doesn't have one (Android 14+ has it built in).", color = Pro.Dim, fontSize = 11.sp)
            if (exportState.format == 7) Text("JPEG XL: high quality at small sizes, wide colour and HDR-ready; opens in newer photo software, Safari and on Apple devices. Few Android apps can show it yet.", color = Pro.Dim, fontSize = 11.sp)
            if (exportState.format != 1 && (exportState.format < 4 || exportState.format >= 6)) EdSlider("Quality", exportState.quality, 0.6f..1f, 0.95f, ::pct) { onExportState(exportState.format, it, exportState.sizeIdx) }
            if (exportState.format == 0) Text(if (exportState.quality >= 0.995f) "JPEG at maximum: full colour detail (4:4:4) and almost no compression — the largest, highest-quality files." else "JPEG keeps full colour detail (4:4:4). 100 % = maximum quality.", color = Pro.Dim, fontSize = 11.sp)
            Text("Size (long edge)", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            Chips(listOf("Full (≤4800)", "4096", "2560", "1920", "Instagram 1080", "Web 2048", "4K 3840", "Original (full resolution)"), exportState.sizeIdx) { onExportState(exportState.format, exportState.quality, it) }
            if (exportState.sizeIdx == 7) Text("The photo's own full size, as big as the sensor or file goes. Always saved at that size, however heavy the edit.", color = Pro.Dim, fontSize = 11.sp)
            aiUpscale()
            Text("Exports a new picture to DCIM/AlcorCutPro; the original stays untouched.", color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp))
            Text(
                if (busy) "EXPORTING…" else "EXPORT PHOTO",
                style = Pro.Value.copy(color = Color.Black, fontSize = 14.sp),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (busy) Pro.Amber.copy(alpha = 0.4f) else Pro.Amber)
                    .clickable(enabled = !busy, onClick = onExport).padding(14.dp),
            )
            run {
                val ctx = LocalContext.current
                SectionTitle("EXPORT PRESETS")
                var naming by remember { mutableStateOf(false) }
                Row(Modifier.hintedScroll(horizontal = true).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (ExportOpts.builtIn + ExportOpts.user).forEach { p ->
                        PillChip(p.name, ExportOpts.presetName == p.name, onLongClick = if (p in ExportOpts.user) ({ ExportOpts.user.remove(p); ExportOpts.save(ctx) }) else null) {
                            onExportState(p.format, p.quality, p.sizeIdx)
                            ExportOpts.space = p.space; ExportOpts.sharpenFor = p.sharpenFor; ExportOpts.sharpenAmount = p.sharpenAmount; ExportOpts.template = p.template; ExportOpts.presetName = p.name
                            ExportOpts.save(ctx)
                        }
                    }
                    PillChip("+ SAVE", false, accentText = true) { naming = true }
                }
                // Recipe: tick several presets and export the photo with all of them in one go.
                var recipe by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(listOf<String>()) }
                Text("Export recipe", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.hintedScroll(horizontal = true).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (ExportOpts.builtIn + ExportOpts.user).forEach { p ->
                        PillChip((if (p.name in recipe) "✓ " else "") + p.name, p.name in recipe) { recipe = if (p.name in recipe) recipe - p.name else recipe + p.name }
                    }
                }
                val chosen = (ExportOpts.builtIn + ExportOpts.user).filter { it.name in recipe }
                if (chosen.isNotEmpty()) Text(
                    "EXPORT WITH ${chosen.size} PRESET" + (if (chosen.size > 1) "S" else ""), style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Ed.Raised).clickable(enabled = !busy) { onRecipe(chosen) }.padding(10.dp),
                )
                Text("Tick presets (e.g. Web + Print + Archive) and export this photo with each of them, one after another — each with its own size, format and file name.", color = Pro.Dim, fontSize = 11.sp)
                if (naming) ProjectNameDialog("My export ${ExportOpts.user.size + 1}", onSave = { n ->
                    ExportOpts.user.add(ExportOpts.Preset(n, exportState.format, exportState.quality, exportState.sizeIdx, ExportOpts.space, ExportOpts.sharpenFor, ExportOpts.sharpenAmount, ExportOpts.template))
                    ExportOpts.presetName = n; ExportOpts.save(ctx); naming = false
                }, onDismiss = { naming = false }, title = "Save export preset", info = "Saves the format, size, quality, colour space, output sharpening and file name.", fieldLabel = "Preset name")
            }
            run {
                val ctx = LocalContext.current
                SectionTitle("COLOUR & SHARPENING")
                Text("Colour space (profile embedded)", color = Color.White, fontSize = 13.sp)
                Chips(ExportOpts.SPACES, ExportOpts.space) { ExportOpts.space = it; ExportOpts.save(ctx) }
                Text("Output sharpening", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                Chips(ExportOpts.SHARPEN_FOR, ExportOpts.sharpenFor) { ExportOpts.sharpenFor = it; ExportOpts.save(ctx) }
                if (ExportOpts.sharpenFor != 0) Chips(ExportOpts.SHARPEN_AMOUNT, ExportOpts.sharpenAmount) { ExportOpts.sharpenAmount = it; ExportOpts.save(ctx) }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Soft proof (matte print)", color = Color.White, fontSize = 13.sp)
                        Text("Shows on screen how a print on matte paper will look: softer blacks and whites.", color = Pro.Dim, fontSize = 11.sp)
                    }
                    androidx.compose.material3.Switch(ExportOpts.proof, { ExportOpts.proof = it }, colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Pro.Amber, checkedThumbColor = Color.Black))
                }
                IccProofSection()
                Text("Gamut warning", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                Chips(ExportOpts.GAMUT, ExportOpts.gamut) { ExportOpts.gamut = it }
                if (ExportOpts.gamut != 0) Text("Cyan marks colours that paper can't print — they'll come out duller. Lower their saturation (HSL Mixer) if they matter.", color = Pro.Dim, fontSize = 11.sp)
                Text("Ultra HDR (JPEG)", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                if (ExportOpts.hdrSupported) {
                    Chips(ExportOpts.HDR_LEVELS, ExportOpts.hdr) { ExportOpts.hdr = it; ExportOpts.save(ctx) }
                    Text("On HDR screens the bright parts (sky, lights, reflections) glow brighter; everywhere else the photo looks exactly as edited. JPEG only.", color = Pro.Dim, fontSize = 11.sp)
                } else Text("Needs Android 14 or newer.", color = Pro.Dim, fontSize = 11.sp)
                SectionTitle("FILE NAME")
                androidx.compose.material3.OutlinedTextField(
                    value = ExportOpts.template, onValueChange = { ExportOpts.template = it.take(80); ExportOpts.save(ctx) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), label = { Text("Template") },
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Pro.Amber, unfocusedBorderColor = Pro.Line, focusedLabelColor = Pro.Amber, cursorColor = Pro.Amber),
                )
                Row(Modifier.hintedScroll(horizontal = true).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExportOpts.TOKENS.forEach { t -> PillChip(t, false) { ExportOpts.template = ExportOpts.template + (if (ExportOpts.template.isEmpty() || t == "{name}") "" else "_") + t; ExportOpts.save(ctx) } }
                }
                Text("{name} original file name · {date} · {time} · {seq} counting number (next: ${ExportOpts.seq}) · {preset} export preset", color = Pro.Dim, fontSize = 11.sp)
            }
            run {
                val ctx = LocalContext.current
                LaunchedEffect(Unit) { PhotoMeta.load(ctx) }
                SectionTitle("METADATA")
                cameraInfo()
                Text("Caption & keywords (IPTC)", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                val tf = @Composable { label: String, value: String, single: Boolean, onChange: (String) -> Unit ->
                    androidx.compose.material3.OutlinedTextField(
                        value = value, onValueChange = { onChange(it.take(if (single) 200 else 2000)); PhotoMeta.save(ctx) },
                        label = { Text(label) }, singleLine = single,
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedBorderColor = Pro.Amber, unfocusedBorderColor = Pro.Line, focusedLabelColor = Pro.Amber, cursorColor = Pro.Amber,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    )
                }
                tf("Title / headline", PhotoMeta.title, true) { PhotoMeta.title = it }
                tf("Caption", PhotoMeta.caption, false) { PhotoMeta.caption = it }
                tf("Keywords (comma-separated)", PhotoMeta.keywords, true) { PhotoMeta.keywords = it }
                Text("Written into every exported JPEG / WebP with your name and copyright — read by photo-management software and stock / news agencies. Kept for the next photos until you change it.", color = Pro.Dim, fontSize = 11.sp)
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(10.dp)).background(Ed.Raised)
                        .clickable { PhotoMeta.sidecar = !PhotoMeta.sidecar; PhotoMeta.save(ctx) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Also save an .xmp sidecar", color = Color.White, fontSize = 14.sp)
                        Text("Your edit (light, colour, HSL, detail, straighten) as Adobe Camera Raw settings, named after the original, in Documents/AlcorCutPro/XMP. Put it next to the original to continue in other photo software.", color = Pro.Dim, fontSize = 11.sp)
                    }
                    androidx.compose.material3.Switch(
                        checked = PhotoMeta.sidecar, onCheckedChange = { PhotoMeta.sidecar = it; PhotoMeta.save(ctx) },
                        colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Pro.Amber),
                    )
                }
            }
        }
    }
}

@Composable
fun SmallAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(10.dp)).background(Ed.Raised).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(20.dp))
        Text(label, color = Color.White, fontSize = 11.sp)
    }
}

@Composable
fun HslMixer(bands: List<HslBand>, onChange: (List<HslBand>) -> Unit) {
    var sel by remember { mutableStateOf(0) }
    SectionTitle("HSL / COLOR MIXER") { onChange(List(8) { HslBand() }) }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        HSL_CENTERS.forEachIndexed { i, h ->
            val (r, g, b) = hueRgb(h)
            Box(
                Modifier.size(if (i == sel) 32.dp else 26.dp).clip(CircleShape).background(Color(r, g, b))
                    .clickable { sel = i },
                contentAlignment = Alignment.Center,
            ) {
                if (bands[i] != HslBand()) Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
            }
        }
    }
    Text(HSL_NAMES[sel].uppercase(), style = Pro.Label.copy(color = Color.White, fontSize = 11.sp))
    val b = bands[sel]
    fun upd(nb: HslBand) = onChange(bands.toMutableList().also { it[sel] = nb })
    EdSlider("Hue", b.hue) { upd(b.copy(hue = it)) }
    EdSlider("Saturation", b.sat) { upd(b.copy(sat = it)) }
    EdSlider("Luminance", b.lum) { upd(b.copy(lum = it)) }
}

/** Saves an edited picture as a new file in DCIM/AlcorCutPro. */
fun savePicture(context: Context, bmp: Bitmap, png: Boolean, quality: Int, area: com.dronedirector.veyrocutpro.camera.StorageArea = com.dronedirector.veyrocutpro.camera.StorageArea.PHOTO): Uri? {
    val name = "EDIT_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + if (png) ".png" else ".jpg"
    // A folder chosen in the editor settings wins; otherwise the default app location.
    SaveFolder.write(context, false, name, if (png) "image/png" else "image/jpeg") {
        bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, quality, it)
    }?.let { return it }
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, if (png) "image/png" else "image/jpeg")
        if (Build.VERSION.SDK_INT >= 29) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/AlcorCutPro")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }
    val resolver = context.contentResolver
    val target = Storage.insert(context, Storage.load(context, area), values, Storage.Kind.IMAGE) ?: return null
    return try {
        resolver.openOutputStream(target)?.use {
            bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, quality, it)
        }
        Storage.publish(context, target)
        target
    } catch (_: Throwable) {
        Storage.discard(context, target)
        null
    }
}

/** Slightly smaller top-bar icon so the photo editor's bar fits phones. */
@Composable
private fun BarIcon(icon: ImageVector, label: String, enabled: Boolean = true, tint: Color = Color.White, onClick: () -> Unit) {
    Box(Modifier.size(36.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = label, tint = if (enabled) tint else tint.copy(alpha = 0.3f), modifier = Modifier.size(21.dp))
    }
}

/** Panels menu for the photo editor: the photo always stays; the rest can be hidden. */
@Composable
private fun PhotoPanelsButton(onTab: (PhotoPanel) -> Unit, onReset: () -> Unit) {
    val panels = listOf(
        com.dronedirector.veyrocutpro.ui.PanelDef("pe.controls", "Tools panel", "Presets, Light, Color, Masks, Heal, Export and the other tabs"),
        com.dronedirector.veyrocutpro.ui.PanelDef("pe.copypaste", "Copy / paste bar", "Copy settings to other photos or video clips"),
        com.dronedirector.veyrocutpro.ui.PanelDef("pe.scope", "Histogram pull-out", "Show or hide the RGB histogram tab on the right edge of the photo"),
        com.dronedirector.veyrocutpro.ui.PanelDef("pe.compare", "Before / after buttons", "HOLD FOR BEFORE and SPLIT on the photo"),
        com.dronedirector.veyrocutpro.ui.PanelDef("pe.filmstrip", "Filmstrip", "The row of imported photos under the photo"),
    )
    val all = panels.map { it.key }.toSet()
    com.dronedirector.veyrocutpro.ui.PanelsButton(
        prefix = "pe",
        panels = panels,
        presets = listOf(
            com.dronedirector.veyrocutpro.ui.PanelPreset("Full", all),
            com.dronedirector.veyrocutpro.ui.PanelPreset("View", emptySet()),
            com.dronedirector.veyrocutpro.ui.PanelPreset("Edit", setOf("pe.controls", "pe.compare")) { onTab(PhotoPanel.LIGHT) },
            com.dronedirector.veyrocutpro.ui.PanelPreset("Color", setOf("pe.controls", "pe.scope", "pe.compare")) { onTab(PhotoPanel.COLOR) },
        ),
        onReset = onReset,
    )
}

/** Megapixels an export will have: long side [longSide] with the photo's shape. */
private fun exportMp(b: Bitmap?, longSide: Int): Float {
    val aspect = if (b != null && b.width > 0 && b.height > 0) minOf(b.width, b.height).toFloat() / maxOf(b.width, b.height) else 0.75f
    val side = if (longSide >= 20000) 6000 else longSide   // "Original": a typical full-size photo until the real size is known
    return side.toFloat() * side * aspect / 1e6f
}

/** The photo editor's keyboard shortcuts (shown with ? or F1). */
val PHOTO_SHORTCUTS = listOf(
    "← / →" to "Previous / next photo in the filmstrip",
    "Home / End" to "First / last photo",
    "0 – 5" to "Star rating (0 clears)",
    "6 / 7 / 8 / 9" to "Colour label red / yellow / green / blue",
    "P / X / U" to "Flag pick / reject / remove flag",
    "\\ or Y" to "Before / after",
    "Z" to "Zoom 1:1 (again to fit)",
    "J" to "Clipping warning",
    "L / C / D" to "Light / Color / Detail",
    "R / Q / M" to "Crop / Heal / Masks",
    "E / H" to "Export / History",
    "Shift+R" to "Reference view on / off",
    "Tab" to "Hide / show the panels",
    "Esc" to "Cancel a pick, leave zoom and before / after",
    "Ctrl+Z / Ctrl+Shift+Z" to "Undo / redo",
    "Ctrl+C / Ctrl+V" to "Copy / paste the look",
    "Ctrl+Shift+V" to "Paste everything (with crop and watermark)",
    "Ctrl+S" to "Save the project",
    "Ctrl+E" to "Export",
    "? or F1" to "This list",
)

@Composable
private fun PhotoShortcutsDialog(onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ed.Panel,
        title = { Text("Keyboard shortcuts", color = Color.White) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PHOTO_SHORTCUTS.forEach { (k, v) ->
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text(k, color = Pro.Amber, fontSize = 13.sp, modifier = Modifier.width(150.dp))
                        Text(v, color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = { Text("CLOSE", style = Pro.Label.copy(color = Pro.Amber), modifier = Modifier.clickable(onClick = onDismiss).padding(10.dp)) },
    )
}



/** Small cached previews of each RAW look on this photo. */
private object RawLookThumbs { val cache = android.util.LruCache<String, Bitmap>(48) }

/**
 * RAW LOOK, folded into one line. Unfolded: Default and Match camera picture, then a fold-out
 * sub-menu per kind of camera (Phones, Drones, Cameras, Action & 360°), each a scrollable row of
 * small previews of this photo in that look — like the transitions list.
 */
@Composable
internal fun RawLookStrip(meta: RawDng.Meta?, dev: RawDng.Dev, onPick: (Int) -> Unit) {
    val sel = dev.look.coerceIn(0, RawDng.LOOKS.size - 1)
    val lin = meta?.let { RawDng.lookSource(it) }
    var open by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var group by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(RawDng.LOOKS[sel].group) }
    FoldRow(
        "RAW LOOK · " + RawDng.LOOKS[sel].label + (RawDng.LOOKS[sel].group.takeIf { it.isNotEmpty() }?.let { " ($it)" } ?: ""),
        if (open) "Tap a look to use it · tap here to fold" else "Tap here to see different DNG looks",
        open, highlight = sel != 0, big = true,
    ) { open = !open }
    if (!open) return
    Column(Modifier.fillMaxWidth().padding(start = 8.dp)) {
        // Default and "Match camera picture" first, always in view.
        LookTiles(meta, lin, dev, sel, RawDng.LOOKS.indices.filter { RawDng.LOOKS[it].group.isEmpty() }, onPick)
        for (g in RawDng.LOOK_GROUPS) {
            val idx = RawDng.LOOKS.indices.filter { RawDng.LOOKS[it].group == g }
            if (idx.isEmpty()) continue
            val gOpen = group == g
            FoldRow(g, idx.joinToString(", ") { RawDng.LOOKS[it].label }, gOpen, highlight = RawDng.LOOKS[sel].group == g) { group = if (gOpen) "" else g }
            if (gOpen) LookTiles(meta, lin, dev, sel, idx, onPick)
        }
        Text(
            "FOLD ▲", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { open = false }.padding(horizontal = 8.dp, vertical = 6.dp),
        )
        Text(when {
            sel == 0 -> "Default is the app's own RAW look."
            RawDng.LOOKS[sel].match -> "Copies the brightness and colour of the preview the camera stored in the file."
            else -> "Tone and colour measured from how these cameras render their own pictures of their RAW files."
        }, color = Pro.Dim, fontSize = 11.sp)
    }
}

/** A tappable fold / unfold line in the editor's style. */
@Composable
private fun FoldRow(title: String, sub: String, open: Boolean, highlight: Boolean, big: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = if (big) 6.dp else 4.dp).clip(RoundedCornerShape(10.dp)).background(Ed.Raised).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = if (big) 10.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (highlight) Pro.Amber else Color.White, fontSize = if (big) 13.sp else 12.sp, maxLines = 1)
            Text(sub, color = Pro.Dim, fontSize = 10.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        androidx.compose.material3.Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = Pro.Dim)
    }
}

/** A scrollable row of small previews of this photo in each of the looks [idx]. */
@Composable
private fun LookTiles(meta: RawDng.Meta?, lin: RawDng.Linear?, dev: RawDng.Dev, sel: Int, idx: List<Int>, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().hintedScroll(horizontal = true).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        idx.forEach { i ->
            val look = RawDng.LOOKS[i]
            val key = "${System.identityHashCode(lin)}/$i/${dev.profile}/${dev.wb}/${dev.vigOn}"
            val thumb by produceState(RawLookThumbs.cache.get(key), key) {
                if (value != null || meta == null || lin == null) return@produceState
                value = withContext(Dispatchers.Default) {
                    try { RawDng.develop(lin, meta, dev.copy(look = i), 0f, 0f, 0f, 0f, 0f, 0f, 0f).also { RawLookThumbs.cache.put(key, it) } } catch (_: Throwable) { null }
                }
            }
            val on = i == sel
            Column(
                Modifier.width(126.dp).clip(RoundedCornerShape(8.dp)).border(if (on) 2.dp else 0.dp, if (on) Pro.Amber else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { onPick(i) }.padding(3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(120.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1A1E26))) {
                    thumb?.let { t -> val img = remember(t) { t.asImageBitmap() }; Image(img, null, Modifier.size(120.dp), contentScale = ContentScale.Crop) }
                }
                Text(look.label, color = if (on) Pro.Amber else Color.White, fontSize = 14.sp, maxLines = 2, lineHeight = 17.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

/**
 * CAMERA PROFILE (.dcp): import DNG camera profiles and use one instead of the RAW file's own
 * colour. Picking one also switches RAW PROFILE to Camera, so its tone curve and look table apply.
 */
@Composable
internal fun DcpRow(meta: RawDng.Meta?, dev: RawDng.Dev, onDev: (RawDng.Dev) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var tick by remember { mutableStateOf(0) }
    var note by remember { mutableStateOf<String?>(null) }
    val camera = meta?.info?.firstOrNull { it.first == "Camera" }?.second
    val list = remember(tick, camera) { DcpStore.forCamera(context, camera) }
    val camIdx = RawDng.PROFILES.indexOfFirst { it.camera }.coerceAtLeast(0)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) note = try {
            val en = DcpStore.import(context, u); tick++
            onDev(dev.copy(dcp = en.id, profile = camIdx))
            "Imported \"${en.name}\"" + if (en.camera.isNotEmpty()) " for ${en.camera}" else ""
        } catch (t: Throwable) { t.message ?: "Couldn't read that profile" }
    }
    SectionTitle("CAMERA PROFILE (.dcp)") { onDev(dev.copy(dcp = null)) }
    Chips(listOf("File's own") + list.map { it.name.take(22) }, if (dev.dcp == null) 0 else list.indexOfFirst { it.id == dev.dcp } + 1) { i ->
        onDev(if (i == 0) dev.copy(dcp = null) else dev.copy(dcp = list[i - 1].id, profile = camIdx))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        Text("IMPORT .DCP", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Ed.Raised).clickable { pick.launch(arrayOf("*/*")) }.padding(10.dp))
        val cur = list.firstOrNull { it.id == dev.dcp }
        if (cur != null) Text("REMOVE PROFILE", style = Pro.Label.copy(color = Pro.Red, fontSize = 11.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Ed.Raised).clickable {
            DcpStore.delete(context, cur.id); tick++; onDev(dev.copy(dcp = null)); note = "Removed \"${cur.name}\""
        }.padding(10.dp))
    }
    Text(note ?: "Use a DNG camera profile (.dcp) — e.g. one made with a colour chart for this camera — instead of the colour in the RAW file. Only profiles for this camera are listed.", color = Pro.Dim, fontSize = 11.sp)
}

/** Export → PRINTER PROFILE: import a printer / paper ICC profile and soft-proof the photo with it. */
@Composable
private fun IccProofSection() {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { IccProof.load(ctx) } }
    var note by remember { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) note = try { val p = IccProof.import(ctx, u); IccProof.on = true; "Imported \"${p.name}\"" } catch (t: Throwable) { t.message ?: "Couldn't read that profile" }
    }
    val p = IccProof.profile
    Text("Printer profile (.icc)", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
    if (p == null) {
        Text("IMPORT PRINTER PROFILE", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(6.dp)).background(Ed.Raised).clickable { pick.launch(arrayOf("*/*")) }.padding(10.dp))
        Text(note ?: "Your print lab or paper maker offers an ICC profile for each paper. Import it to see on screen how the photo will print.", color = Pro.Dim, fontSize = 11.sp)
        return
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Soft proof: " + p.name.take(40), color = Color.White, fontSize = 12.sp)
            Text(note ?: "The screen shows the photo as this printer and paper reproduce it.", color = Pro.Dim, fontSize = 11.sp)
        }
        androidx.compose.material3.Switch(IccProof.on, { IccProof.on = it }, colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Pro.Amber, checkedThumbColor = Color.Black))
    }
    if (IccProof.on) {
        Chips(listOf("Perceptual", "Relative colorimetric"), IccProof.intent) { IccProof.intent = it }
        Chips(listOf("Paper white & ink black", "Screen white"), if (IccProof.paper) 0 else 1) { IccProof.paper = it == 0 }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        Text("CHANGE PROFILE", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Ed.Raised).clickable { pick.launch(arrayOf("*/*")) }.padding(10.dp))
        Text("REMOVE", style = Pro.Label.copy(color = Pro.Red, fontSize = 11.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Ed.Raised).clickable { IccProof.remove(ctx); note = null }.padding(10.dp))
    }
}

/** Relight: a small square to place the light (centre = straight in front). */
@Composable
private fun LightPad(x: Float, y: Float, onChange: (Float, Float) -> Unit) {
    val ch by androidx.compose.runtime.rememberUpdatedState(onChange)
    Box(
        Modifier.padding(vertical = 6.dp).size(120.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).border(1.dp, Pro.Line, RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                fun set(o: androidx.compose.ui.geometry.Offset) = ch((o.x / size.width * 2 - 1).coerceIn(-1f, 1f), (o.y / size.height * 2 - 1).coerceIn(-1f, 1f))
                awaitEachGesture {
                    val d = awaitFirstDown(); set(d.position)
                    while (true) { val ev = awaitPointerEvent(); val c = ev.changes.firstOrNull() ?: break; if (!c.pressed) break; set(c.position); c.consume() }
                }
            },
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val c = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
            drawCircle(Color.White.copy(alpha = 0.25f), size.minDimension * 0.18f, c)
            val p = androidx.compose.ui.geometry.Offset((x + 1) / 2 * size.width, (y + 1) / 2 * size.height)
            drawLine(Pro.Amber.copy(alpha = 0.6f), p, c, 2f)
            drawCircle(Pro.Amber, 9.dp.toPx(), p)
        }
    }
}
