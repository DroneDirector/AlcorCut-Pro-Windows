package com.dronedirector.veyrocutpro.editor

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.draw.drawWithContent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import com.dronedirector.veyrocutpro.ui.Icon
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import com.dronedirector.veyrocutpro.ui.Text
import com.dronedirector.veyrocutpro.ui.mouseActions
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import com.dronedirector.veyrocutpro.camera.Filter
import com.dronedirector.veyrocutpro.camera.GradeIO
import com.dronedirector.veyrocutpro.ui.Pro
import com.dronedirector.veyrocutpro.ui.hintedScroll
import com.dronedirector.veyrocutpro.ui.pinchAndMove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.material.icons.filled.Storage
import com.dronedirector.veyrocutpro.ui.BarIconGroup
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.derivedStateOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class Page(val label: String) { STRIP("Media strip"), MEDIA("Media"), PROXY("Proxy"), EDIT("Edit"), TEXT("Text"), TRANSITIONS("Transitions"), COLOR("Color"), LAYERS("Layers"), AUDIO("Audio"), DELIVER("Deliver") }
internal enum class ColorTab(val label: String) { INPUT("Input"), LUTS("LUTs"), WHEELS("Wheels"), PRIMARIES("Primaries"), CURVES("Curves"), HSL("HSL"), MASKS("Masks"), DETAIL("Detail"), FILM("Film & lens"), EFFECTS("Effects"), LOOKS("Looks") }

@OptIn(UnstableApi::class)
@Composable
fun VideoEditorScreen(onExit: () -> Unit, initialUris: List<Uri> = emptyList()) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        com.dronedirector.veyrocutpro.ui.AppLog.i("Video editor opened · " + com.dronedirector.veyrocutpro.ui.AppLog.memory(context))
        // Every few minutes: how much memory the editor uses (to find what fills it if Android closes the app).
        while (true) { delay(180_000); com.dronedirector.veyrocutpro.ui.AppLog.i("Video editor · " + com.dronedirector.veyrocutpro.ui.AppLog.memory(context)) }
    }
    KeepScreenOnWhileEditing()
    val scope = rememberCoroutineScope()
    val clips = remember { mutableStateListOf<Clip>() }
    var project by remember { mutableStateOf<VideoProjectData?>(null) }
    var loading by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }
    // Several projects open at once (File → Open projects).
    val host = remember { ProjectHost() }
    var exiting by remember { mutableStateOf(false) }
    fun stash() {
        val op = host.activeProject() ?: return
        host.snapshot?.invoke()?.let { op.data = it; op.name = it.name }
        op.team = Team.current
    }
    fun show(op: OpenProject) {
        host.snapshot = null
        host.active = op.key
        clips.clear(); op.data?.let { clips.addAll(it.clips) }
        project = op.data
        Team.current = op.team
        started = true
    }
    fun openInEditor(p: VideoProjectData?, team: TeamSession?, fromDisk: Boolean) {
        stash()
        // An untouched, empty project is replaced rather than kept.
        host.activeProject()?.let { cur ->
            val d = cur.data
            val empty = d == null || (d.clips.isEmpty() && d.layers.isEmpty() && d.music == null && d.extraAudio.isEmpty())
            if (empty && cur.team == null && !host.dirty(cur)) host.open.remove(cur)
        }
        val op = OpenProject(java.util.UUID.randomUUID().toString(), p, team, if (fromDisk) SIG_PENDING else null)
        host.open.add(op)
        show(op)
    }
    fun closeKey(key: String) {
        val op = host.open.firstOrNull { it.key == key } ?: return
        op.team?.let { t -> kotlin.concurrent.thread { Team.release(context, t) } }
        host.open.remove(op)
        if (key == host.active) {
            host.snapshot = null; host.active = null
            val next = host.open.lastOrNull()
            if (next != null) show(next)
            else { started = false; project = null; clips.clear(); Team.current = null; ProjectStore.clearAutosave(context) }
        }
    }
    fun releaseAll() {
        val teams = host.open.mapNotNull { it.team }
        if (teams.isNotEmpty()) kotlin.concurrent.thread { teams.forEach { Team.release(context, it) } }
        Team.current = null
    }
    val leave by androidx.compose.animation.core.animateFloatAsState(
        if (exiting) 1f else 0f, androidx.compose.animation.core.tween(520),
        finishedListener = { if (it >= 1f) (context as? android.app.Activity)?.finishAndRemoveTask() }, label = "leave",
    )
    val nav = remember {
        EditorNav(
            open = { p, t, disk -> if (p != null) { clips.clear(); clips.addAll(p.clips) }; openInEditor(p, t, disk) },
            switchTo = { k -> stash(); host.open.firstOrNull { it.key == k }?.let { show(it) } },
            close = { k -> closeKey(k) },
            exitApp = { releaseAll(); ProjectStore.clearAutosave(context); com.dronedirector.veyrocutpro.ui.AppExit.exiting = true },
            reopen = { op -> show(op); host.reload++ },
        )
    }
    // Team projects: the "who has it open" note is kept fresh while they're open.
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            val teams = host.open.mapNotNull { it.team }.filter { !it.readOnly }
            if (teams.isNotEmpty()) withContext(Dispatchers.IO) { teams.forEach { Team.writeLock(context, it) } }
        }
    }

    fun addUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        loading = true
        scope.launch {
            // Videos as they are; photos stay photos (still clips), in the order picked.
            val loaded = Stills.loadAll(context, uris, EditorPrefs.photoClipSeconds(context)).map { withContext(Dispatchers.IO) { MediaCatalog.added(context, it) } }
            clips.addAll(loaded)
            loading = false
            loaded.firstOrNull { it.undecodable == true }?.let {
                android.widget.Toast.makeText(context, com.dronedirector.veyrocutpro.ui.I18n.tr("\"${it.name}\" is 4:2:2 and this PC can't decode it — see the Media page"), android.widget.Toast.LENGTH_LONG).show()
            }
            if (clips.isNotEmpty()) started = true
        }
    }

    // Videos shared from another app go straight onto the timeline.
    LaunchedEffect(Unit) { if (initialUris.isNotEmpty()) addUris(initialUris) }
    // Photo editor → Slideshow video: the photos as clips with a slow zoom and transitions.
    var slideshowStatus by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val r = Slideshow.request ?: return@LaunchedEffect
        Slideshow.request = null
        loading = true
        val made = Slideshow.build(context, r) { i, n -> slideshowStatus = "Making the slideshow: photo ${minOf(i + 1, n)} of $n" }
        slideshowStatus = null
        loading = false
        if (made.isEmpty()) { android.widget.Toast.makeText(context, com.dronedirector.veyrocutpro.ui.I18n.tr("Couldn't make clips from those photos"), android.widget.Toast.LENGTH_LONG).show(); return@LaunchedEffect }
        clips.addAll(made.map { MediaCatalog.added(context, it) })
        started = true
    }
    // Picture test on the test phone: colour bars with clarity and texture set, straight into the editor.
    LaunchedEffect(Unit) {
        val amt = FxTest.amount ?: return@LaunchedEffect
        FxTest.amount = null
        if (amt >= 3f) {
            // Visualizer test: analyse a made-up song with a kick on every beat and check the pictures move.
            withContext(Dispatchers.Default) { VisTest.run(context) }
            VisTest.transitions()
            withContext(Dispatchers.Default) { VisTest.text(context) }
            VisTest.slideshow(context)
            android.util.Log.i("VisTest", "content credentials: " + withContext(Dispatchers.IO) { ContentCredentials.selfTest(context) })
            android.util.Log.i("VisTest", "face recognition: " + withContext(Dispatchers.IO) { Faces.selfTest(context) })
            android.util.Log.i("VisTest", "cineform: " + withContext(Dispatchers.IO) { CineForm.selfTest(context) })
            // Playback test clips (the playback itself runs in the editor below once it shows them).
            val made = ArrayList<Clip>()
            repeat(3) {
                val u = kotlinx.coroutines.suspendCancellableCoroutine<Uri?> { cont ->
                    Generators.make(context, GenSpec(GenKind.BARS, seconds = 10), onProgress = {}) { u, err ->
                        android.util.Log.i("FxTest", "playback test clip ${it + 1}: $u ${err ?: ""}")
                        if (cont.isActive) cont.resumeWith(Result.success(u))
                    }
                }
                u?.let { withContext(Dispatchers.IO) { ClipLoader.load(context, it) } }?.let { made += it }
            }
            if (made.isEmpty()) { android.util.Log.e("FxTest", "playback test: no clips"); return@LaunchedEffect }
            clips.addAll(made); started = true
            android.util.Log.i("FxTest", "playback test: ${made.size} clip(s) on the timeline")
            FxTest.playback = true
            return@LaunchedEffect
        }
        android.util.Log.i("FxTest", "making the test clip ($amt)")
        Generators.make(context, GenSpec(GenKind.BARS, seconds = 3), onProgress = {}) { uri, err ->
            android.util.Log.i("FxTest", "clip made: $uri $err")
            if (uri != null) scope.launch {
                val c = withContext(Dispatchers.IO) { ClipLoader.load(context, uri) }
                android.util.Log.i("FxTest", "clip loaded: ${c?.width}x${c?.height}")
                if (c == null) return@launch
                clips.add(c)
                started = true
                // Like moving the sliders: the clip is already showing, then clarity and texture change.
                delay(8000)
                val i = clips.indexOfFirst { it.id == c.id }
                if (i >= 0) clips[i] = if (amt >= 2f) clips[i].copy(
                    // 2 = film look and a privacy blur spot in the middle, set on the open clip.
                    film = FilmLook(bloom = 0.8f, halation = 0.8f, grain = 0.8f, lensVig = 1f, lensCa = 1f),
                    privacy = PrivacyBlur(tracks = listOf(BlurTrack(false, listOf(0L, 600_000L), listOf(0.5f, 0.5f, 0.35f, 0.35f, 0.5f, 0.5f, 0.35f, 0.35f)))),
                ) else clips[i].copy(detail = clips[i].detail.copy(clarity = amt, texture = amt))
                android.util.Log.i("FxTest", "test effects ($amt) set on the open clip")
            }
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { addUris(it) }
    val browse = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { addUris(it) }
    val pickGallery = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
    val pickFiles = { browse.launch(arrayOf("video/*", "image/*")) }
    // Files from a NAS: videos go straight in, photos become 5 s clips first.
    fun addNas(uris: List<Uri>) {
        // Videos and photos (photos stay stills) in the order picked.
        addUris(uris)
    }
    var nasStart by remember { mutableStateOf(false) }
    if (nasStart) NasBrowserDialog(NasKinds.VIDEO_START, onDismiss = { nasStart = false }) { addNas(it) }
    // The video library and pro import, from the start page: what's picked starts the timeline.
    var startLib by remember { mutableStateOf(0) }
    if (startLib != 0) LibraryOverlay(startLib == 2, onClose = { startLib = 0 }, onAdd = { startLib = 0; addUris(it) })

    if (!started) {
        BackHandler { onExit() }
        Box(Modifier.fillMaxSize()) {
            MediaChooser(
                title = "Video editor",
                subtitle = "Add videos or photos to start a timeline (photos stay still photos). Log footage (D-Log, D-Log M, S-Log3, V-Log, LogC3, HLG) is converted in the Color page.",
                videos = true,
                onBack = onExit,
                onGallery = pickGallery,
                onFiles = pickFiles,
                onNetwork = { nasStart = true },
                onRecent = { addUris(listOf(it)) },
                onSkip = { started = true },
                banner = {
                    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp).hintedScroll(horizontal = true), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillChip("🎞 Video library", false, accentText = true) { startLib = 1 }
                        PillChip("⇩ Import from card / camera / USB", false, accentText = true) { startLib = 2 }
                    }
                    var recoverAt by remember { mutableStateOf(ProjectStore.autosaveTime(context)) }
                    if (recoverAt > 0L) Column(
                        Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(12.dp))
                            .background(Pro.Amber.copy(alpha = 0.12f)).border(1.dp, Pro.Amber.copy(alpha = 0.6f), RoundedCornerShape(12.dp)).padding(12.dp),
                    ) {
                        Text("Unsaved edit found", color = Pro.Amber, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        if (com.dronedirector.veyrocutpro.ui.CrashReport.recoveryCrashed(context)) Text(
                            "The last attempt to recover it closed the app. You can try again, or discard it.",
                            color = Pro.Red, fontSize = 12.sp,
                        )
                        Text(
                            "The editor closed without leaving normally (" + java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(recoverAt)) + "). Pick up where you left off?",
                            color = Pro.Dim, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("RECOVER", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(50)).background(Pro.Amber).clickable {
                                loading = true
                                scope.launch {
                                    // Load and check the edit off the main thread; media that can't be read is left out.
                                    val res = withContext(Dispatchers.IO) {
                                        try { ProjectStore.loadAutosave(context)?.let { ProjectStore.sanitizeRecovered(context, it) } } catch (_: Throwable) { null }
                                    }
                                    loading = false
                                    val p = res?.first
                                    if (p == null || (p.clips.isEmpty() && p.layers.isEmpty() && p.music == null && p.extraAudio.isEmpty())) {
                                        recoverAt = 0L; ProjectStore.clearAutosave(context)
                                        android.widget.Toast.makeText(context, com.dronedirector.veyrocutpro.ui.I18n.tr("The unsaved edit couldn't be recovered — its media is no longer available"), android.widget.Toast.LENGTH_LONG).show()
                                    } else {
                                        if (res.second > 0) android.widget.Toast.makeText(context, com.dronedirector.veyrocutpro.ui.I18n.tr("Recovered — {n} missing file(s) were left out").replace("{n}", res.second.toString()), android.widget.Toast.LENGTH_LONG).show()
                                        val fixed = p.copy(id = p.id.ifBlank { java.util.UUID.randomUUID().toString() }, name = p.name.ifBlank { "Recovered edit" })
                                        com.dronedirector.veyrocutpro.ui.CrashReport.startRecovering(context)
                                        clips.clear(); clips.addAll(fixed.clips); project = fixed; started = true
                                    }
                                }
                            }.padding(horizontal = 16.dp, vertical = 8.dp))
                            Text("DISCARD", color = Pro.Dim, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(50)).clickable {
                                ProjectStore.clearAutosave(context); recoverAt = 0L
                            }.padding(horizontal = 16.dp, vertical = 8.dp))
                        }
                    }
                },
                onOpenProject = { id ->
                    loading = true
                    scope.launch {
                        val p = withContext(Dispatchers.IO) { ProjectStore.loadVideo(context, id) }
                        loading = false
                        if (p == null || p.clips.isEmpty()) {
                            android.widget.Toast.makeText(context, com.dronedirector.veyrocutpro.ui.I18n.tr("Couldn't open that project"), android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            clips.clear(); clips.addAll(p.clips); project = p; started = true
                        }
                    }
                },
            )
            if (loading) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Pro.Amber)
                    slideshowStatus?.let { Text(it, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp)) }
                }
            }
        }
    } else {
        // The editor opened from the start page: it's the first open project.
        if (host.active == null) {
            val op = OpenProject(java.util.UUID.randomUUID().toString(), project, Team.current, if (project != null) SIG_PENDING else null)
            host.open.add(op); host.active = op.key
        }
        androidx.compose.runtime.key(host.active, host.reload) {
          Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - leave; scaleX = 1f - leave * 0.08f; scaleY = 1f - leave * 0.08f }) {
            Workspace(
                clips, project, onAddGallery = pickGallery, onAddFiles = pickFiles, onAddNas = { addNas(it) }, onExit = { releaseAll(); host.open.clear(); host.active = null; ProjectStore.clearAutosave(context); onExit() },
                host = host, nav = nav,
                onLoad = { id ->
                    val p = ProjectStore.loadVideo(context, id)
                    if (p == null || p.clips.isEmpty()) android.widget.Toast.makeText(context, com.dronedirector.veyrocutpro.ui.I18n.tr("Couldn't open that project"), android.widget.Toast.LENGTH_SHORT).show()
                    else nav.open(p, null, true)
                },
            )
            com.dronedirector.veyrocutpro.ui.EditorTourHost(com.dronedirector.veyrocutpro.ui.GuideVariant.VIDEO)
          }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun Workspace(clips: MutableList<Clip>, initial: VideoProjectData?, onAddGallery: () -> Unit, onAddFiles: () -> Unit, onAddNas: (List<Uri>) -> Unit, onExit: () -> Unit, host: ProjectHost, nav: EditorNav, onLoad: (String) -> Unit) {
    val wx = remember { WorkspaceExtra() }
    val context = LocalContext.current
    val es = remember { EditorState(initial, context) }
    val maskTools = remember { LocalToolsState() }
    /** Mixer: the one track being listened to alone (preview only), or null. */
    // An opened autosave is a copy: SAVE asks for a name instead of overwriting it.
    if (es.showLoad) LoadProjectDialog(true, onOpen = { es.showLoad = false; onLoad(it) }, onDismiss = { es.showLoad = false })
    // Timeline track switches for the main video track (clips).
    // Timeline markers and snapping.
    val markers = remember { mutableStateListOf<Long>().apply { initial?.markers?.let { addAll(it) } } }
    val markerNotes = remember { mutableStateMapOf<Long, String>().apply { initial?.markerNotes?.forEach { (k, v) -> k.toLongOrNull()?.let { put(it, v) } } } }
    // Media in the Proxy section (by stable media ID) — only these ever play from a proxy.
    val proxyMedia = remember { mutableStateListOf<String>().apply { initial?.proxyMedia?.let { addAll(it.distinct()) } } }
    // Media pool bins (file → bin) and colour-labelled organisation.
    val bins = remember { mutableStateMapOf<String, String>().apply { initial?.bins?.let { putAll(it) } } }
    val binNames = remember { mutableStateListOf<String>().apply { initial?.binNames?.let { addAll(it.distinct()) } } }
    // Settings → PROJECT ARCHIVE: 1 = save a .zip, 2 = open one (handled once the pickers exist).
    val thumbs = remember { mutableStateMapOf<String, Bitmap>() }
    val strips = remember { mutableStateMapOf<String, Filmstrip>() }
    // Panels menu (layout icon next to the settings gear): which parts of the editor are shown.
    val showInspector = com.dronedirector.veyrocutpro.ui.panelOn("ve.inspector")
    val showTransport = com.dronedirector.veyrocutpro.ui.panelOn("ve.transport")
    val showScope = com.dronedirector.veyrocutpro.ui.panelOn("ve.scope")
    val showWaves = com.dronedirector.veyrocutpro.ui.panelOn("ve.waves")
    LaunchedEffect(showScope) { if (!showScope) es.scopeOpen = false }
    val layers = remember { mutableStateListOf<Layer>().apply { initial?.layers?.let { addAll(it) } } }
    // Adjustment layers in the preview: their grade is added to the clip being shown.
    fun adjOf(c: Clip): Clip {
        val a = adjustMasks(layers, es.adjNowMs)
        return if (a.isEmpty()) c else c.copy(masks = a + (c.masks ?: emptyList()))
    }
    // Remembered watermark: added to every new project; kept up to date while the option is on.
    LaunchedEffect(Unit) {
        if (initial == null && layers.none { it.kind == LayerKind.WATERMARK || (it.kind == LayerKind.LOGO && it.wholeVideo) }) {
            WatermarkMemory.load(context, true)?.let { layers.add(it) }
        }
    }
    val wmLayer = layers.firstOrNull { it.kind == LayerKind.WATERMARK } ?: layers.firstOrNull { it.kind == LayerKind.LOGO }
    val wmOn = WatermarkMemory.isOn(context, true)
    LaunchedEffect(wmLayer, wmOn) { if (wmOn && wmLayer != null) WatermarkMemory.save(context, true, wmLayer) }
    // Extra audio tracks on the timeline, and audio files in the media pool.
    val extraAudio = remember { mutableStateListOf<MusicTrack>().apply { initial?.extraAudio?.let { addAll(it) } } }
    // Several timelines in one project: the open one lives in the normal state, the others here.
    val otherTimelines = remember { mutableStateListOf<SavedTimeline>().apply { initial?.timelines?.let { addAll(it) } } }
    val audioPool = remember { mutableStateListOf<PoolAudio>().apply { initial?.audioPool?.let { addAll(it) } } }
    // Media strip: all imported clips (on the timeline or not), with ratings.
    val library = remember { mutableStateListOf<Clip>().apply { initial?.library?.let { addAll(it.distinctBy { c -> c.uri.toString() }) } } }
    val ratings = remember { mutableStateMapOf<String, Int>().apply { initial?.ratings?.let { putAll(it) } } }
    LaunchedEffect(clips.map { it.uri.toString() }.distinct()) {
        val have = library.map { it.uri.toString() }.toHashSet()
        for (c in clips) if (c.nested == null && c.uri != Uri.EMPTY && have.add(c.uri.toString()))
            library.add(c.copy(id = java.util.UUID.randomUUID().toString(), startMs = 0, endMs = c.durationMs, speed = 1f, ramp = null, rampName = null, volKeys = null, transition = Transition()))
    }
    val libScope = rememberCoroutineScope()
    fun importToLibrary(uris: List<Uri>) {
        if (uris.isEmpty()) return
        es.libLoading += uris.size
        libScope.launch {
            var added = 0
            for (u in uris) {
                if (library.any { it.uri == u || it.still == u.toString() }) { es.libLoading--; continue }
                val c = if (Stills.isPhoto(context, u)) Stills.make(context, u, EditorPrefs.photoClipSeconds(context))?.let { withContext(Dispatchers.IO) { MediaCatalog.added(context, it) } }
                else withContext(Dispatchers.IO) {
                    try { context.contentResolver.takePersistableUriPermission(u, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Throwable) {}
                    ClipLoader.load(context, u)?.let { MediaCatalog.added(context, it) }
                }
                es.libLoading--
                if (c != null) { library.add(c); added++ }
            }
            es.message = "$added clip(s) added to the media strip"
        }
    }
    val libPick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { importToLibrary(it) }
    // Photos on the timeline: how long a still shows (its still clip is made again at the new length).
    var stillBusy by remember { mutableStateOf(false) }
    fun setStillLength(i: Int, seconds: Int) {
        val c = clips.getOrNull(i) ?: return
        val photo = c.still ?: return
        if (stillBusy) return
        stillBusy = true
        libScope.launch {
            val n = Stills.make(context, Uri.parse(photo), seconds)
            stillBusy = false
            if (n == null) { es.message = "Couldn't read the photo again"; return@launch }
            val k = clips.indexOfFirst { it.id == c.id }
            if (k < 0) return@launch
            clips[k] = clips[k].copy(uri = n.uri, durationMs = n.durationMs, startMs = 0L, endMs = n.durationMs, speed = 1f, ramp = null, rampName = null)
            // (The preview reloads by itself: the clip under the playhead changed.)
            es.message = "The photo now shows for $seconds s"
        }
    }
    // Round trip: a photo on the timeline opened in the photo editor (over this editor); its export replaces it.
    var roundTrip by remember { mutableStateOf<String?>(null) }
    fun replaceStill(clipId: String, photo: Uri) {
        val c = clips.firstOrNull { it.id == clipId } ?: return
        val secs = Math.round((c.endMs - c.startMs) / 1000f).coerceIn(1, 60)
        stillBusy = true
        libScope.launch {
            val n = Stills.make(context, photo, secs)
            stillBusy = false
            val k = clips.indexOfFirst { it.id == clipId }
            if (n == null || k < 0) { es.message = "Couldn't bring the edited photo back"; return@launch }
            clips[k] = clips[k].copy(uri = n.uri, still = photo.toString(), durationMs = n.durationMs, startMs = 0L, endMs = n.durationMs, speed = 1f, ramp = null, rampName = null)
            es.message = "The edited photo is on the timeline"
        }
    }
    roundTrip?.let { id ->
        val c = clips.firstOrNull { it.id == id }
        val photo = c?.still
        if (photo == null) roundTrip = null else {
            androidx.compose.runtime.DisposableEffect(id) {
                PhotoRoundTrip.onExported = { u -> roundTrip = null; replaceStill(id, u) }
                onDispose { PhotoRoundTrip.onExported = null }
            }
            androidx.compose.ui.window.Dialog(onDismissRequest = { roundTrip = null },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
                Box(Modifier.fillMaxSize().background(Ed.Bg)) {
                    PhotoEditorScreen(onExit = { roundTrip = null }, initial = Uri.parse(photo), onOpenVideo = { roundTrip = null })
                }
            }
        }
    }
    fun setStillZoom(i: Int, on: Boolean) {
        val c = clips.getOrNull(i) ?: return
        clips[i] = c.copy(reframe = if (on) Reframe(start = Frame(1f), end = Frame(1.12f, x = 0.03f, y = -0.02f), animate = true) else Reframe())
    }
    // Watched folders: checked every minute while the editor is open; new videos (and photos, as stills)
    // go into the media strip.
    androidx.compose.runtime.DisposableEffect(Unit) {
        WatchFolders.onVideos = { list -> importToLibrary(list) }
        onDispose { WatchFolders.onVideos = null }
    }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            withContext(Dispatchers.IO) { try { WatchFolders.check(context) } catch (_: Throwable) { 0 } }
        }
    }
    // The video library (calendar, smart collections, labels) and pro import: picked files go into the media strip.
    var libOpen by remember { mutableStateOf(0) }
    if (libOpen != 0) LibraryOverlay(libOpen == 2, onClose = { libOpen = 0 }, onAdd = { libOpen = 0; importToLibrary(it) })
    // Capture (all its pro controls) opened over the editor: what's taken goes into the media list, not onto the timeline.
    fun openCamera(video: Boolean) = com.dronedirector.veyrocutpro.ui.CaptureForEditor.open("video editor", video) { u, _ ->
        // A photo stays a photo (a still clip).
        importToLibrary(listOf(u))
    }
    var cameraAsk by remember { mutableStateOf(false) }
    if (cameraAsk) AddFromDialog("Camera → media list", listOf(
        Triple(Icons.Filled.Videocam, "Record a video") { openCamera(true) },
        Triple(Icons.Filled.PhotoCamera, "Take a photo (becomes a 5 s clip)") { openCamera(false) },
    )) { cameraAsk = false }
    val libFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) {
            try { context.contentResolver.takePersistableUriPermission(tree, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Throwable) {}
            libScope.launch {
                val list = withContext(Dispatchers.IO) { SkimFrames.folderVideos(context, tree) }
                if (list.isEmpty()) es.message = "No videos in that folder" else importToLibrary(list)
            }
        }
    }
    val audioPlayers = remember { HashMap<String, ExoPlayer>() }
    /** Each extra audio track's live processor (its EQ / dynamics, mixer level and meter). */
    val extraFx = remember { HashMap<String, AudioFxProcessor>() }
    // Real waveforms for clips and audio tracks (decoded once in the background).
    val waves = remember { mutableStateMapOf<String, FloatArray>() }
    LaunchedEffect(clips.map { it.uri }.distinct(), es.music?.uri, extraAudio.map { it.uri }) {
        val uris = (clips.map { it.uri } + listOfNotNull(es.music?.uri) + extraAudio.map { it.uri }).distinct()
        for (u in uris) {
            if (waves[u.toString()] != null) continue
            withContext(Dispatchers.IO) { Waveforms.load(context, u) }?.let { waves[u.toString()] = it }
        }
    }
    DisposableEffect(Unit) { onDispose { audioPlayers.values.forEach { it.release() } } }
    fun addAudio(uris: List<Uri>) {
        for (uri in uris) {
            try { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Throwable) {}
            val mmr = android.media.MediaMetadataRetriever()
            val dur = try { mmr.setDataSource(context, uri); mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L } catch (_: Throwable) { 0L } finally { try { mmr.release() } catch (_: Throwable) {} }
            val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: uri.lastPathSegment?.takeIf { uri.scheme == "file" } ?: "Audio"
            if (dur > 0 && audioPool.none { it.uri == uri }) audioPool.add(PoolAudio(uri, name, dur))
        }
    }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { addAudio(it) }
    var nasPool by remember { mutableStateOf(false) }
    if (nasPool) NasBrowserDialog(NasKinds.VIDEO_EDITOR, onDismiss = { nasPool = false }) { uris ->
        val (audio, rest) = uris.partition { Nas.isAudio(it.lastPathSegment ?: "") }
        addAudio(audio)
        onAddNas(rest)
        if (audio.isNotEmpty()) es.message = "${audio.size} audio file(s) added to the audio list"
    }
    val musicFxProc = remember { AudioFxProcessor(live = true) }
    val musicPlayer = remember { fxPlayer(context, musicFxProc) }
    DisposableEffect(Unit) { onDispose { musicPlayer.release() } }
    LaunchedEffect(Unit) { initial?.music?.let { musicPlayer.setMediaItem(MediaItem.fromUri(it.uri)); musicPlayer.prepare() } }
    val pickMusic = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Throwable) {}
            val mmr = android.media.MediaMetadataRetriever()
            val dur = try { mmr.setDataSource(context, uri); mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L } catch (_: Throwable) { 0L } finally { try { mmr.release() } catch (_: Throwable) {} }
            val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "Music"
            if (dur > 0) {
                es.music = MusicTrack(uri, name, dur)
                if (audioPool.none { it.uri == uri }) audioPool.add(PoolAudio(uri, name, dur))
                musicPlayer.setMediaItem(MediaItem.fromUri(uri))
                musicPlayer.prepare()
            }
        }
    }
    val layerScope = rememberCoroutineScope()
    if (es.showMulticam) MulticamDialog(
        pool = clips.distinctBy { it.uri.toString() },
        onDone = { cut ->
            es.showMulticam = false
            if (cut.isNotEmpty()) { clips.addAll(cut); es.message = "Multicam edit added: ${cut.size} shots at the end of the timeline" }
        },
        onDismiss = { es.showMulticam = false },
    )
    /** Lines an audio track up with the selected clip by matching their sound (external recorder, second camera…). */
    fun syncAudioTo(t: MusicTrack) {
        val c = clips.getOrNull(es.selected) ?: run { es.message = "Select the clip to sync to on the timeline"; return }
        es.message = "Matching the sound…"
        layerScope.launch {
            val pa = withContext(Dispatchers.IO) { Waveforms.load(context, c.uri) }
            val pb = withContext(Dispatchers.IO) { Waveforms.load(context, t.uri) }
            if (pa == null || pb == null) { es.message = "Couldn't read the sound of the clip or the track"; return@launch }
            val m = withContext(Dispatchers.Default) { AudioSync.match(pa, pb) }
            if (m == null || m.confidence < 0.15f) {
                es.message = "No clear match — both need to have recorded the same sound (a clap at the start helps)"
                return@launch
            }
            val base = clipStarts(clips).getOrNull(es.selected) ?: 0L
            val a0 = c.startMs + m.lagMs
            val nt = if (a0 >= 0) t.copy(startMs = base, offsetMs = a0) else t.copy(startMs = base - a0, offsetMs = 0L)
            if (es.music?.id == t.id) es.music = nt
            else { val i = extraAudio.indexOfFirst { it.id == t.id }; if (i >= 0) extraAudio[i] = nt }
            es.message = "Synced to clip ${es.selected + 1} (match ${(m.confidence * 100).toInt()}%). Turn the clip's own volume down in Audio to hear only the track."
        }
    }
    // Drone flight data (DJI .SRT) → timed overlay text on the selected clip.
    // Subtitle import (.srt / .vtt) → caption layers.
    val pickSubs = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val cues = try { context.contentResolver.openInputStream(uri)?.use { Interchange.parseSubs(it.readBytes().toString(Charsets.UTF_8)) } } catch (_: Throwable) { null }
            val total = timelineLength(clips).let { if (it > 0) it else Long.MAX_VALUE }
            var n = 0
            cues?.forEach { (a, b, t) ->
                if (a >= total) return@forEach
                layers.add(Layer(kind = LayerKind.TEXT, text = t, size = 0.046f, x = 0.5f, y = 0.84f, box = true, shadow = true,
                    startMs = a, endMs = b.coerceAtMost(total), name = "Caption"))
                n++
            }
            es.message = if (cues == null) "Couldn't read that file" else if (n == 0) "No subtitles found in that file" else "$n captions imported"
        }
    }
    val pickSrt = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) layerScope.launch {
            val text = withContext(Dispatchers.IO) {
                try { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } } catch (_: Throwable) { null }
            }
            if (es.srtForMap && text != null && text.contains("<gpx", ignoreCase = true)) {
                // A GPX track (hike, drive, ride): its route animated over the selected clip, start to finish.
                val pts = withContext(Dispatchers.IO) { try { Geotag.readGpx(context, uri) } catch (_: Throwable) { emptyList() } }
                val c = clips.getOrNull(es.current)
                if (pts.size < 2 || c == null) { es.message = if (c == null) "Select a clip on the timeline first" else "No timed points in that GPX file"; return@launch }
                val base = clipStarts(clips).getOrNull(es.current) ?: 0L
                val t0 = pts.first().t; val span = (pts.last().t - t0).coerceAtLeast(1)
                val step = (pts.size / 400).coerceAtLeast(1)
                val tr = ArrayList<Double>()
                for ((i, p) in pts.withIndex()) if (i % step == 0 || i == pts.lastIndex) { tr += base + (p.t - t0).toDouble() / span * c.lengthMs; tr += p.lat; tr += p.lon }
                val l = Layer(kind = LayerKind.FX, fx = FxLayers.MAP, text = "Route", size = 0.26f, x = 0.84f, y = 0.74f, color = 0xFFFFB300.toInt(),
                    shadow = false, startMs = base, endMs = base + c.lengthMs, track = tr)
                layers.add(l); es.selectedLayer = l.id
                es.message = "Route added over clip ${es.current + 1} — the whole track plays over the clip"
                return@launch
            }
            val samples = text?.let { withContext(Dispatchers.Default) { Telemetry.parse(it) } }.orEmpty()
            if (samples.isEmpty()) { es.message = "No flight data found in that file — pick the .SRT saved next to the drone video, or a .GPX track"; return@launch }
            val c = clips.getOrNull(es.current) ?: run { es.message = "Select the drone clip on the timeline first"; return@launch }
            val base = clipStarts(clips).getOrNull(es.current) ?: 0L
            if (es.srtForMap) {
                // Flight path mini-map: the GPS track inside this clip, on the timeline's clock.
                val pts = samples.filter { it.lat != null && it.lon != null && it.tMs in c.startMs..c.endMs }
                if (pts.size < 2) { es.message = "No GPS positions in that flight data for this clip"; return@launch }
                val step = (pts.size / 400).coerceAtLeast(1)
                val tr = ArrayList<Double>()
                for ((i, s) in pts.withIndex()) if (i % step == 0 || i == pts.lastIndex) { tr += (base + c.tlOffsetOf(s.tMs)).toDouble(); tr += s.lat!!; tr += s.lon!! }
                val l = Layer(kind = LayerKind.FX, fx = FxLayers.MAP, text = "Flight path", size = 0.26f, x = 0.84f, y = 0.74f, color = 0xFFFFB300.toInt(),
                    shadow = false, startMs = base, endMs = base + c.lengthMs, track = tr)
                layers.add(l); es.selectedLayer = l.id
                es.message = "Flight path added over clip ${es.current + 1} — drag it where you want it"
                return@launch
            }
            val segs = Telemetry.segments(samples, es.telemetryShow, c.startMs, c.endMs)
            if (segs.isEmpty()) { es.message = "The flight data has none of the chosen values"; return@launch }
            layers.removeAll { it.name == "Telemetry" && it.startMs >= base && it.startMs < base + c.lengthMs }
            var n = 0
            for ((a, b, txt) in segs) {
                val s0 = base + c.tlOffsetOf(a)
                val s1 = base + c.tlOffsetOf(b)
                if (s1 - s0 < 100) continue
                layers.add(Layer(
                    kind = LayerKind.TEXT, text = txt, size = 0.032f, x = 0.5f, y = 0.93f,
                    box = true, shadow = true, font = LayerFont.MONO, startMs = s0, endMs = s1, name = "Telemetry",
                ))
                n++
            }
            es.message = "Flight data added to clip ${es.current + 1} ($n overlays) — tap one on the timeline to restyle it"
        }
    }
    // Render queue (Deliver → RENDER QUEUE): settings to export one after another.
    val exportQueue = remember { mutableStateListOf<DeliverSettings>() }
    // Android 13+: ask once to show the export progress notification.
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
            !context.getSharedPreferences("procam", android.content.Context.MODE_PRIVATE).getBoolean("asked_notify", false)
        ) {
            context.getSharedPreferences("procam", android.content.Context.MODE_PRIVATE).edit().putBoolean("asked_notify", true).apply()
            try { askNotify.launch(android.Manifest.permission.POST_NOTIFICATIONS) } catch (_: Throwable) {}
        }
    }
    // New video layer: you choose first whether it goes above or below the main video.
    val pickLayerVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) layerScope.launch {
            val c = withContext(Dispatchers.IO) { ClipLoader.load(context, uri) }
            if (c == null) { es.message = "Can't open that video"; return@launch }
            val total = timelineLength(clips)
            val start = ((clipStarts(clips).getOrNull(es.current) ?: 0L) + (clips.getOrNull(es.current)?.let { it.tlOffsetOf(it.startMs + es.posMs) } ?: es.posMs)).coerceIn(0, total)
            val below = es.videoLayerBelow
            val l = Layer(
                kind = LayerKind.VIDEO, text = c.name, videoUri = uri, videoDurationMs = c.durationMs,
                mediaAspect = if (c.height > 0) c.width.toFloat() / c.height else 16f / 9f,
                // Above: picture-in-picture in the corner. Below: full frame behind the main video.
                size = if (below) 1f else 0.35f, x = if (below) 0.5f else 0.72f, y = if (below) 0.5f else 0.28f,
                startMs = start, endMs = (start + c.durationMs).coerceAtMost(total.coerceAtLeast(start + 500)),
                below = below,
            )
            // New layers go on top of the other layers in their group.
            layers.add(l); es.selectedLayer = l.id
            val n = layers.count { it.kind == LayerKind.VIDEO && it.visible }
            es.message = "Video layer added ${if (below) "below" else "above"} the main video" +
                if (n > 3) " — $n video layers play at once; if the preview stutters, use the Proxy tab or hide some with the eye" else ""
        }
    }
    if (es.askVideoLayer) androidx.compose.material3.AlertDialog(
        onDismissRequest = { es.askVideoLayer = false },
        containerColor = Pro.PanelSolid,
        title = { Text("Add a video layer", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Where should the new video go?", color = Pro.Dim, fontSize = 13.sp)
                listOf(false, true).forEach { below ->
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ed.Raised)
                            .border(1.dp, Pro.Amber.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .clickable {
                                es.askVideoLayer = false; es.videoLayerBelow = below
                                pickLayerVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                            }.padding(12.dp),
                    ) {
                        Text(if (below) "↓  BELOW the main video" else "↑  ABOVE the main video", color = Pro.Amber, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (below) "Placed behind your clips, full frame. It shows wherever the main video doesn't cover the frame (bars, a smaller or moved clip, gaps)."
                            else "Placed on top of your clips — picture-in-picture you can move and resize, or full frame with Size 100%.",
                            color = Pro.Dim, fontSize = 11.sp,
                        )
                    }
                }
                Text("You can add as many video layers as you like, and change above / below later in the layer's settings.", color = Pro.Dim, fontSize = 11.sp)
            }
        },
        confirmButton = {},
        dismissButton = { androidx.compose.material3.TextButton(onClick = { es.askVideoLayer = false }) { Text("Cancel", color = Pro.Dim) } },
    )
    val pickLayerImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val total = timelineLength(clips)
            val l = Layer(kind = LayerKind.IMAGE, text = "Image", imageUri = uri, size = 0.3f, x = 0.5f, y = 0.5f, startMs = 0, endMs = total.coerceAtLeast(1000), wholeVideo = true)
            layers.add(l); es.selectedLayer = l.id
        }
    }
    val addAtMs = remember { longArrayOf(0L) }
    val pickLottie = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) layerScope.launch {
            val u = withContext(Dispatchers.IO) { LottieRender.import(context, uri) }
            if (u == null) { es.message = "That isn't a Lottie animation (.json or .zip)"; return@launch }
            val dur = LottieRender.durationMs(context, Layer(kind = LayerKind.LOTTIE, imageUri = u))
            val l = Layer(kind = LayerKind.LOTTIE, text = "Animation", imageUri = u, size = 0.35f, startMs = addAtMs[0], endMs = addAtMs[0] + dur.coerceIn(1000L, 10_000L), shadow = false)
            layers.add(l); es.selectedLayer = l.id
            es.message = "Lottie animation added — it loops for the layer's length"
        }
    }
    val pickLogo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val l = Layer(kind = LayerKind.LOGO, imageUri = uri, size = 0.12f, x = 0.85f, y = 0.88f, opacity = 0.85f, wholeVideo = true)
            layers.add(l); es.selectedLayer = l.id
        }
    }

    // Proxy media, keyed by stable media ID (MediaId.of(clip)): only media in the Proxy section.
    val proxies = remember { mutableStateMapOf<String, Uri>() }       // ready proxy (current quality, or an older one while updating)
    val proxyStale = remember { mutableStateListOf<String>() }        // using an older quality while the new one is made
    val proxyBad = remember { mutableStateListOf<String>() }          // failed to play this session → the original is used
    val proxyFail = remember { mutableStateMapOf<String, String>() }  // generation failed (why)
    val proxyCancelled = remember { mutableStateListOf<String>() }
    val proxyQueue = remember { mutableStateListOf<String>() }
    val proxyProgress = remember { mutableStateMapOf<String, Int>() }
    val renderedFor = remember { mutableStateListOf<String?>(null, null) }
    // Timeline scroll wheels: animated "upscale" look, or plain colours without animation.
    // Render cache: heavy effects baked into a preview file (key → file).
    val cacheReady = wx.cacheReady
    val cacheProgress = wx.cacheProgress
    LaunchedEffect(clips.map { if (RenderCache.heavy(it)) RenderCache.key(it) else "" }.distinct()) {
        for (c in clips.toList()) if (RenderCache.heavy(c)) {
            val k = RenderCache.key(c)
            if (k !in cacheReady) withContext(Dispatchers.IO) { RenderCache.existing(context, c) }?.let { cacheReady[k] = it }
        }
    }
    fun cachedFor(c: Clip): Uri? = wx.cachedFor(c)
    fun pvc(c: Clip): Clip = wx.pvc(c)
    fun renderCacheNext() {
        if (wx.cacheCancel != null) return
        val c = clips.firstOrNull { RenderCache.heavy(it) && RenderCache.key(it) !in cacheReady && RenderCache.key(it) in cacheProgress } ?: return
        val k = RenderCache.key(c)
        wx.cacheCancel = RenderCache.make(context, c, { p -> cacheProgress[k] = p }) { u, err ->
            wx.cacheCancel = null
            cacheProgress.remove(k)
            if (u != null) cacheReady[k] = u else { es.message = err; cacheProgress.keys.toList().forEach { cacheProgress.remove(it) } }
            if (u != null) renderCacheNext()
        }
    }
    /** The proxy this clip plays from right now, or null (= the original). */
    fun proxyFor(c: Clip): Uri? {
        cachedFor(c)?.let { return it }
        if (!es.proxySettings.enabled || c.nested != null) return null
        val id = MediaId.of(c)
        if (id !in proxyMedia || id in proxyBad) return null
        return proxies[id]
    }
    fun sourceFor(c: Clip): Uri = proxyFor(c) ?: c.uri
    fun addToProxy(c: Clip) {
        val id = MediaId.of(c)
        proxyCancelled.remove(id); proxyFail.remove(id); proxyBad.remove(id)
        if (id !in proxyMedia) proxyMedia.add(id) else if (proxies[id] == null || id in proxyStale) { if (id !in proxyQueue) proxyQueue.add(id) }
    }
    fun cancelProxy(id: String) {
        val running = proxyQueue.firstOrNull() == id && proxyProgress[id] != null
        if (running) { es.proxyCancel?.invoke(); es.proxyCancel = null }
        proxyProgress.remove(id)
        if (proxyQueue.remove(id) && id !in proxyCancelled) proxyCancelled.add(id)
        if (running) es.proxyRun++
    }
    fun removeFromProxy(id: String) {
        cancelProxy(id)
        proxyMedia.remove(id); proxies.remove(id); proxyStale.remove(id); proxyCancelled.remove(id); proxyFail.remove(id); proxyBad.remove(id)
    }
    /** Short status of a media file in the Proxy section. */
    fun proxyStatus(c: Clip): String {
        val id = MediaId.of(c)
        val q = es.proxySettings.quality.label
        val have = proxies[id]?.let { ProxyMaker.qualityOf(it)?.label } ?: ""
        return when {
            id !in proxyMedia -> "NOT IN PROXY"
            proxyProgress[id] != null -> (if (proxies[id] != null) "UPDATING TO $q " else "GENERATING $q ") + "${proxyProgress[id]}%"
            id in proxyQueue -> if (proxies[id] != null) "QUEUED · UPDATE TO $q (USING $have)" else "QUEUED"
            id in proxyBad -> "COULDN'T PLAY — USING ORIGINAL"
            proxyFail[id] != null -> "FAILED — USING ORIGINAL"
            proxies[id] != null && id in proxyStale -> "READY $have · NOT UPDATED TO $q"
            proxies[id] != null -> "READY $q"
            id in proxyCancelled -> "CANCELLED — USING ORIGINAL"
            else -> "WAITING"
        }
    }
    // The gear's settings (also in File → Project settings, by group).
    val veSettings: @Composable (Set<String>?) -> Unit = { only ->
        EditorSettingsBody(
            "Video editor", com.dronedirector.veyrocutpro.ui.AppScreen.VIDEO_EDITOR,
        timelineExtra = {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Scroll wheel effects", color = Color.White, fontSize = 15.sp)
                    Text("Glow, pixel and scan-line animation on the timeline's scroll wheels. Off = plain colours, no animation.", color = Pro.Dim, fontSize = 12.sp)
                }
                androidx.compose.material3.Switch(es.wheelFx, {
                    es.wheelFx = it
                    context.getSharedPreferences("procam", android.content.Context.MODE_PRIVATE).edit().putBoolean("tl_wheel_fx", it).apply()
                }, colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Pro.Amber, checkedThumbColor = Color.Black))
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Trimming closes the gap", color = Color.White, fontSize = 15.sp)
                    Text("Off (normal): shortening a clip on the main video leaves a black gap where the cut-off part was, and everything after it stays in place. On: the gap closes — the clip and everything after it move up to the left, on every track.", color = Pro.Dim, fontSize = 12.sp)
                }
                androidx.compose.material3.Switch(es.trimRipple, {
                    es.trimRipple = it
                    context.getSharedPreferences("procam", android.content.Context.MODE_PRIVATE).edit().putBoolean("tl_trim_ripple", it).apply()
                }, colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Pro.Amber, checkedThumbColor = Color.Black))
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Marker effects", color = Color.White, fontSize = 15.sp)
                    Text("Markers, keyframes and in / out flags on the timeline shine like the scroll wheels. Off = plain markers, no animation.", color = Pro.Dim, fontSize = 12.sp)
                }
                androidx.compose.material3.Switch(es.markerFx, {
                    es.markerFx = it
                    context.getSharedPreferences("procam", android.content.Context.MODE_PRIVATE).edit().putBoolean("tl_marker_fx", it).apply()
                }, colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Pro.Amber, checkedThumbColor = Color.Black))
            }
        },
        playbackExtra = {
            PlayFxSettings()
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Show playback info", color = Color.White, fontSize = 15.sp)
                    Text("Small read-out on the player for troubleshooting", color = Pro.Dim, fontSize = 12.sp)
                }
                androidx.compose.material3.Switch(es.showInfo, {
                    es.showInfo = it
                    context.getSharedPreferences("procam", android.content.Context.MODE_PRIVATE).edit().putBoolean("ve_info", it).apply()
                }, colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Pro.Amber, checkedThumbColor = Color.Black))
            }
            Text("PROXY MEDIA", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(top = 14.dp))
            Text("Proxies are set up in the Proxy tab next to Media: add the clips you want, switch proxies on or off and pick the quality there. Exports always use your original files.", color = Pro.Dim, fontSize = 12.sp)
        },
        projectExtra = {
            Text("PROJECT ARCHIVE", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(top = 6.dp))
            Text("One .zip with this edit and every file it uses — to move it to another PC or keep it safe. OPEN ARCHIVE brings one back as a new project.", color = Pro.Dim, fontSize = 12.sp)
            if (es.archiveBusy != null) Text("Working… ${es.archiveBusy}%", style = Pro.Label.copy(color = Pro.Amber), modifier = Modifier.padding(vertical = 6.dp))
            else Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                Text("SAVE ARCHIVE", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Ed.Raised).clickable { es.archiveAction = 1; es.showSettings = false }.padding(8.dp))
                Text("OPEN ARCHIVE", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Ed.Raised).clickable { es.archiveAction = 2; es.showSettings = false }.padding(8.dp))
            }
            Text("VERSION HISTORY", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(top = 14.dp))
            val pid = es.projectId
            val vers = remember(pid, es.showSettings) { if (pid != null) ProTools.versions(context, pid) else emptyList() }
            if (pid == null) Text("Save the project once; every save (and every 10 minutes of editing) then keeps a version you can go back to.", color = Pro.Dim, fontSize = 12.sp)
            else if (vers.isEmpty()) Text("No earlier versions yet — they're kept every time you save and every 10 minutes while you edit.", color = Pro.Dim, fontSize = 12.sp)
            else vers.take(30).forEach { (t, f) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(java.text.SimpleDateFormat("EEE d MMM · HH:mm", java.util.Locale.getDefault()).format(java.util.Date(t)), color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text("RESTORE", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { es.pendingRestore = f; es.showSettings = false }.padding(6.dp))
                }
            }
        },
            only = only, onDismiss = { es.showSettings = false; es.projectSettings = false },
        )
    }
    if (es.showSettings) EditorSettingsShell("Video editor settings", onDismiss = { es.showSettings = false }) { veSettings(null) }
    // Project settings and ingest: own composable block (keeps this function small).
    val projectBlock: @Composable () -> Unit = {
        if (es.projectSettings) ProjectSettingsDialog(
            video = true, onDismiss = { es.projectSettings = false },
            general = { veSettings(setOf("look", "prefs", "app")) },
            storage = { veSettings(setOf("save", "nas", "catalog")) },
            projectName = es.projectName, onRename = { es.projectName = it },
            timelineName = es.timelineName, onRenameTimeline = { es.timelineName = it },
            aspect = es.deliver.aspect, fps = es.deliver.fps, onShape = { a, f -> es.deliver = es.deliver.copy(aspect = a, fps = f) },
            teamLabel = Team.current?.place?.label, bins = binNames.toList(),
        )
        // Ingest (File → Project settings): new media goes into a bin, gets a proxy or a colour label as it's added.
        val ingestSeen = remember { HashSet<String>().apply { clips.forEach { add(it.uri.toString()) }; library.forEach { add(it.uri.toString()) } } }
        LaunchedEffect(clips.size, library.size) {
            val fresh = (clips + library).filter { ingestSeen.add(it.uri.toString()) }
            if (fresh.isEmpty()) return@LaunchedEffect
            val bin = Ingest.bin(context).takeIf { it.isNotEmpty() && it in binNames }
            val px = Ingest.proxies(context); val lb = Ingest.labels(context)
            for (c in fresh) {
                if (bin != null && bins[c.uri.toString()] == null) bins[c.uri.toString()] = bin
                if (px && Ingest.heavy(c) && c.offline != true) addToProxy(c)
                if (lb && c.label == null) {
                    val k = LabelNames.forKind(context, Ingest.kind(c))
                    if (k >= 0) {
                        for (i in clips.indices) if (clips[i].uri == c.uri && clips[i].label == null) clips[i] = clips[i].copy(label = CLIP_LABELS[k])
                        for (i in library.indices) if (library[i].uri == c.uri && library[i].label == null) library[i] = library[i].copy(label = CLIP_LABELS[k])
                    }
                }
            }
        }
    }
    projectBlock()
    var videoAspect by remember { mutableStateOf(16f / 9f) }
    // Two players take turns: while one plays, the next clip is loaded and paused on its first
    // frame in the other, so the cut to the next clip is seamless.
    // Each preview player runs the clip's EQ / dynamics live.
    val audioFx = remember { listOf(AudioFxProcessor(meter = true, live = true), AudioFxProcessor(meter = true, live = true)) }
    val players = remember {
        audioFx.map { fx -> fxPlayer(context, fx).apply { repeatMode = Player.REPEAT_MODE_OFF } }
    }
    val zoom = remember { TimelineZoom() }
    // Each project opens with the timeline as you left it: zoom, scroll position and the open page.
    LaunchedEffect(es.projectId) {
        val id = es.projectId ?: return@LaunchedEffect
        val v = context.getSharedPreferences("procam", android.content.Context.MODE_PRIVATE).getString("tlview_$id", null)?.split('|') ?: return@LaunchedEffect
        v.getOrNull(0)?.toFloatOrNull()?.let { zoom.pxPerSec = it.coerceIn(0.5f, 600f) }
        v.getOrNull(2)?.let { n -> Page.entries.firstOrNull { it.name == n }?.let { es.page = it } }
        delay(400)
        v.getOrNull(1)?.toLongOrNull()?.let { zoom.scrollMs = it.coerceIn(0L, zoom.maxScrollMs) }
    }
    LaunchedEffect(es.projectId, zoom.pxPerSec, zoom.scrollMs, es.page) {
        val id = es.projectId ?: return@LaunchedEffect
        delay(1500)
        context.getSharedPreferences("procam", android.content.Context.MODE_PRIVATE).edit().putString("tlview_$id", "${zoom.pxPerSec}|${zoom.scrollMs}|${es.page.name}").apply()
    }
    // Crop / zoom on the player, stabilisation and transitions in the preview.
    val motions = remember { mutableStateMapOf<String, Motion>() }
    val stabProgress = remember { mutableStateMapOf<String, Int>() }
    val stabCancels = remember { HashMap<String, () -> Unit>() }
    val frameViews = remember { arrayOfNulls<android.view.View>(2) }
    val texViews = remember { arrayOfNulls<android.view.View>(2) }
    // Chromecast: tablets next to the zoom buttons below the picture; phones in the tools (Deliver → CAST).
    val isTablet = androidx.compose.ui.platform.LocalConfiguration.current.smallestScreenWidthDp >= 600
    val castPanel = com.dronedirector.veyrocutpro.ui.panelOn("ve.cast") && isTablet
    val castOnPhone = !isTablet
    val transitionOn = remember { booleanArrayOf(false) }
    val gpuGrade = remember { GradePreview.supported }
    val filmOn = remember { booleanArrayOf(false, false) }
    val filmShown = remember { arrayOfNulls<Any>(2) }
    fun motionFor(c: Clip): Motion? = if (c.stab.enabled) motions[Stabilizer.keyOf(c)] else null
    fun analyze(c: Clip) {
        val k = Stabilizer.keyOf(c)
        if (stabProgress[k] != null || motions[k] != null) return
        stabProgress[k] = 0
        stabCancels[k] = Stabilizer.analyze(context, c, onProgress = { stabProgress[k] = it }) { m, err ->
            stabProgress.remove(k); stabCancels.remove(k)
            if (m != null) { motions[k] = m; es.message = err ?: "Stabilisation ready for ${c.name}" } else es.message = err
        }
    }
    val deck = remember { Deck(players) }
    // The viewer's picture for a TV (Chromecast): the layers below, the video, then titles / layers on top —
    // put together like the viewer, without the editing aids (handles, guides, read-outs).
    val castBelow = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val castAbove = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val castFrame = remember { arrayOfNulls<androidx.compose.ui.geometry.Rect>(1) }
    val castHasBelow = remember { booleanArrayOf(false) }
    val castSource: suspend () -> android.graphics.Bitmap? = castSrc@{
        val fr = castFrame[0] ?: return@castSrc null
        if (fr.width < 2f || fr.height < 2f) return@castSrc null
        val k = 1280f / fr.width
        val out = android.graphics.Bitmap.createBitmap(1280, (fr.height * k).toInt().coerceAtLeast(2), android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(out)
        c.drawColor(android.graphics.Color.BLACK)
        val all = android.graphics.RectF(0f, 0f, out.width.toFloat(), out.height.toFloat())
        val p = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
        // Layer pictures come back as GPU bitmaps; a copy in normal memory can be drawn here.
        fun layerOn(img: androidx.compose.ui.graphics.ImageBitmap?) {
            try {
                val b = img?.asAndroidBitmap() ?: return
                val sw = if (b.config == android.graphics.Bitmap.Config.HARDWARE) b.copy(android.graphics.Bitmap.Config.ARGB_8888, false) else b
                c.drawBitmap(sw, null, all, p)
                if (sw !== b) sw.recycle()
            } catch (_: Throwable) {}
        }
        if (castHasBelow[0]) layerOn(try { castBelow.toImageBitmap() } catch (_: Throwable) { null })
        try { (texViews[deck.active] as? android.view.TextureView)?.let { tv ->
            if (tv.isAvailable && tv.width > 0 && tv.height > 0) {
                val loc = IntArray(2); tv.getLocationInWindow(loc)
                val bw = minOf(tv.width, 1920)
                val vb = tv.getBitmap(bw, (bw.toFloat() * tv.height / tv.width).toInt().coerceAtLeast(2))
                if (vb != null) {
                    val l = (loc[0] - fr.left) * k; val t = (loc[1] - fr.top) * k
                    c.drawBitmap(vb, null, android.graphics.RectF(l, t, l + tv.width * k, t + tv.height * k), p)
                    vb.recycle()
                }
            }
        } } catch (_: Throwable) {}
        layerOn(try { castAbove.toImageBitmap() } catch (_: Throwable) { null })
        out
    }
    val aspects = remember { mutableStateListOf(16f / 9f, 16f / 9f) }

    fun mediaItemFor(c: Clip) = MediaItem.Builder().setUri(sourceFor(c)).setMediaId(c.id)
        .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(c.startMs).setEndPositionMs(c.endMs).build())
        .build()

    fun prepareOn(p: ExoPlayer, c: Clip, startMs: Long, play: Boolean) {
        p.stop()
        val pi = players.indexOf(p).coerceAtLeast(0)
        if (gpuGrade) {
            // Android 13+: the grade is drawn on the view; the player only decodes plain video.
            texViews[pi]?.let { if (es.effectsOk) GradePreview.apply(it, adjOf(c), 0f, ClipPreviewFx.extra(context, it, pvc(c), c.startMs + startMs)) else GradePreview.clear(it) }
            // Deflicker needs to measure the frames: it runs in the player (only when used). Film looks and privacy blur are drawn on the view.
            val ff = if (es.effectsOk) liveEffects(c) else emptyList()
            if (ff.isNotEmpty() || filmOn[pi]) {
                try { p.setVideoEffects(ff); filmOn[pi] = ff.isNotEmpty(); filmShown[pi] = liveKey(c) } catch (_: Throwable) { filmOn[pi] = false }
            }
        } else {
            try { p.setVideoEffects(if (es.effectsOk) olderPreviewEffects(adjOf(c)) else emptyList()) } catch (_: Throwable) { es.effectsOk = false }
        }
        p.setMediaItem(mediaItemFor(c), startMs.coerceAtLeast(0))
        p.playbackParameters = PlaybackParameters(c.speedAtSrc(c.startMs + startMs))
        p.volume = if (c.muted || es.previewMuted || es.videoMuted) 0f else c.volume.coerceAtMost(1f)
        audioFx[players.indexOf(p).coerceAtLeast(0)].fx = c.audio
        p.playWhenReady = play
        p.prepare()
    }

    /** Loads the clip after [index] into the spare player, paused on its first frame. */
    fun preloadAfter(index: Int) {
        val n = clips.getOrNull(index + 1)
        if (n == null) { deck.spare.stop(); es.preloadedId = null; return }
        prepareOn(deck.spare, n, 0, false)
        es.preloadedId = n.id
    }

    fun load(index: Int, startMs: Long, play: Boolean) {
        val c = clips.getOrNull(index) ?: return
        es.current = index
        es.loadedId = c.id
        prepareOn(deck.cur, c, startMs, play)
        // The position belongs to the new clip from this moment on (not the old clip's, until the next update).
        es.posMs = startMs.coerceAtLeast(0); PreviewClock.posMs = es.posMs; PreviewClock.at = android.os.SystemClock.uptimeMillis()
        videoAspect = aspects[deck.active]
        preloadAfter(index)
    }

    /** End of a clip: switch to the preloaded next clip instantly. */
    fun advance() {
        val next = es.current + 1
        val n = clips.getOrNull(next)
        if (n == null) {
            // Loop: back to the in point (or the start) and on playing.
            if (es.loop && clips.isNotEmpty()) {
                val from = es.deliver.rangeStart?.takeIf { es.deliver.rangeEnd != null } ?: 0L
                val (i, local) = clipAt(clips, from)
                val c0 = clips[i]
                es.seekCount++
                load(i, c0.srcAtOffset(local) - c0.startMs, true)
                return
            }
            deck.cur.playWhenReady = false; return
        }
        if (es.preloadedId == n.id && deck.spare.playbackState != Player.STATE_IDLE) {
            val old = deck.cur
            deck.active = 1 - deck.active
            es.current = next
            es.loadedId = n.id
            videoAspect = aspects[deck.active]
            deck.cur.playbackParameters = PlaybackParameters(n.speedAtSrc(n.startMs))
            deck.cur.volume = if (n.muted || es.previewMuted || es.videoMuted) 0f else n.volume.coerceAtMost(1f)
            deck.cur.play()
            old.pause()
            // Same instant as the switch: the playhead goes on from the new clip's own position, without a jump.
            es.posMs = deck.cur.currentPosition; PreviewClock.posMs = es.posMs; PreviewClock.at = android.os.SystemClock.uptimeMillis()
            // Get the following clip ready in the player we just left.
            preloadAfter(next)
        } else {
            load(next, 0, true)
        }
    }

    DisposableEffect(Unit) {
        val listeners = players.mapIndexed { idx, p ->
            object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (idx == deck.active && state == Player.STATE_ENDED) advance()
                }

                override fun onRenderedFirstFrame() {
                    renderedFor[idx] = p.currentMediaItem?.mediaId
                }

                override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        aspects[idx] = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                        if (idx == deck.active) videoAspect = aspects[idx]
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    if (idx != deck.active) { es.preloadedId = null; return }
                    val pos = p.currentPosition
                    // A proxy that fails to decode: fall back to the original at the same point.
                    clips.getOrNull(es.current)?.let { c ->
                        val pu = proxyFor(c)
                        if (pu != null && p.currentMediaItem?.localConfiguration?.uri == pu) {
                            proxyBad.add(MediaId.of(c))
                            es.message = "The proxy for ${c.name} couldn't be played — using the original"
                            return
                        }
                    }
                    if (gpuGrade) {
                        // No effects pipeline in the player: just retry a couple of times.
                        if (es.errorRetries < 2) { es.errorRetries++; load(es.current, pos, p.playWhenReady) }
                        else es.message = "Can't play this clip on this PC (${error.errorCodeName})"
                        return
                    }
                    if (es.effectsOk) {
                        // The phone's GPU preview pipeline failed: keep previewing without the live grade.
                        es.effectsOk = false
                        es.message = "Live grade preview isn't supported here — the grade is still applied on export"
                        load(es.current, pos, p.playWhenReady)
                    } else {
                        es.message = "Can't play this clip on this PC (${error.errorCodeName})"
                    }
                }
            }.also { p.addListener(it) }
        }
        onDispose {
            players.forEachIndexed { i, p -> p.removeListener(listeners[i]); p.release() }
            es.cloudJob?.cancel(); es.export?.cancel()
            es.proxyCancel?.invoke()
            stabCancels.values.forEach { it() }
        }
    }

    BackHandler { if (!es.exporting) es.confirmExit = true }

    // Reload when the clip under the playhead changes (added, removed, trimmed, reordered).
    val structure = clips.map { listOf(it.id, it.startMs, it.endMs, sourceFor(it).toString()) }
    LaunchedEffect(structure) {
        if (clips.isEmpty()) { players.forEach { it.stop(); it.clearMediaItems() }; es.loadedId = null; es.preloadedId = null; return@LaunchedEffect }
        val idx = clips.indexOfFirst { it.id == es.loadedId }.takeIf { it >= 0 } ?: es.current.coerceIn(0, clips.lastIndex)
        load(idx, if (clips[idx].id == es.loadedId) deck.cur.currentPosition else 0L, deck.cur.playWhenReady && deck.cur.isPlaying)
    }
    LaunchedEffect(clips.mapNotNull { it.decoder }.toSet()) { liveDecoders.addAll(clips.mapNotNull { it.decoder }) }
    // Proxy section: find ready proxies, use an older quality while a new one is made, queue the rest.
    LaunchedEffect(proxyMedia.toList(), clips.map { MediaId.of(it) }.distinct(), es.proxySettings.quality, es.proxySettings.hevc) {
        val ids = proxyMedia.toSet()
        val onTimeline = clips.map { MediaId.of(it) }.toSet()
        // Stop work for media no longer in the section (or no longer in the project).
        for (id in proxyQueue.toList()) if (id !in ids || id !in onTimeline) {
            if (proxyQueue.firstOrNull() == id && proxyProgress[id] != null) { es.proxyCancel?.invoke(); es.proxyCancel = null; es.proxyRun++ }
            proxyProgress.remove(id); proxyQueue.remove(id)
        }
        for (id in ids) {
            val c = clips.firstOrNull { MediaId.of(it) == id && it.nested == null } ?: continue
            val ready = withContext(Dispatchers.IO) { ProxyMaker.existing(context, c, es.proxySettings.quality, es.proxySettings.hevc) }
            if (ready != null) {
                proxies[id] = ready; proxyStale.remove(id)
                if (proxyQueue.firstOrNull() == id && proxyProgress[id] != null) { es.proxyCancel?.invoke(); es.proxyCancel = null; es.proxyRun++ }
                proxyProgress.remove(id); proxyQueue.remove(id); continue
            }
            val old = withContext(Dispatchers.IO) { ProxyMaker.anyExisting(context, c, es.proxySettings.hevc) }
            if (old != null) { proxies[id] = old.second; if (id !in proxyStale) proxyStale.add(id) } else proxies.remove(id)
            if (id !in proxyCancelled && proxyFail[id] == null && id !in proxyQueue) proxyQueue.add(id)
        }
    }
    // Make proxies one at a time, in the background — playback and the timeline keep working
    // (the original plays until the proxy is ready). A quality change restarts the job.
    val nextProxy = proxyQueue.firstOrNull()
    LaunchedEffect(nextProxy, es.proxySettings.quality, es.proxySettings.hevc, es.proxyRun) {
        if (es.proxyCancel != null) { es.proxyCancel?.invoke(); es.proxyCancel = null; proxyProgress.clear() }
        val id = nextProxy ?: return@LaunchedEffect
        val c = clips.firstOrNull { MediaId.of(it) == id && it.nested == null }
        if (c == null) { proxyQueue.remove(id); return@LaunchedEffect }
        val q = es.proxySettings.quality; val hv = es.proxySettings.hevc
        // Already there (e.g. made at this quality earlier)?
        val have = withContext(Dispatchers.IO) { ProxyMaker.existing(context, c, q, hv) }
        if (have != null) { proxies[id] = have; proxyStale.remove(id); proxyQueue.remove(id); return@LaunchedEffect }
        proxyProgress[id] = 0
        var cancel: (() -> Unit)? = null
        cancel = ProxyMaker.make(
            context, c, q, hv,
            onProgress = { if (es.proxyCancel === cancel) proxyProgress[id] = it },
            onDone = { uri, err ->
                if (es.proxyCancel === cancel || cancel == null) {
                    proxyProgress.remove(id)
                    proxyQueue.remove(id)
                    es.proxyCancel = null
                    if (uri != null) {
                        proxies[id] = uri; proxyStale.remove(id); proxyBad.remove(id); proxyFail.remove(id)
                        es.message = "Proxy ${q.label} ready — ${c.name}"
                    } else {
                        proxyFail[id] = err ?: "Couldn't make the proxy"
                        es.message = (err ?: "Couldn't make the proxy") + " — ${c.name} plays from the original"
                    }
                }
            },
        )
        es.proxyCancel = cancel
    }

    // Before / after split view on the player (Color page).
    LaunchedEffect(es.abSplit, es.page) { GradePreview.split = if (es.page == Page.COLOR) es.abSplit else 0f }
    // Live grade on the clip being shown.
    val cur = clips.getOrNull(es.current)
    // Live grade while dragging: the newest value is applied every ~80 ms (not only after letting go),
    // the colour table is built off the main thread, and a paused frame is redrawn.
    val gradeReq = remember { kotlinx.coroutines.channels.Channel<Clip>(kotlinx.coroutines.channels.Channel.CONFLATED) }
    LaunchedEffect(cur?.grade, cur?.detail, cur?.masks, cur?.film, cur?.bg, cur?.depth, cur?.privacy, es.effectsOk) { cur?.let { gradeReq.trySend(it) } }
    LaunchedEffect(Unit) {
        for (c in gradeReq) {
            if (gpuGrade) {
                if (es.effectsOk && c.id == es.loadedId) {
                    texViews[deck.active]?.let { GradePreview.apply(it, adjOf(c), 0f, ClipPreviewFx.extra(context, it, pvc(c), c.startMs + deck.cur.currentPosition)) }
                    val a = deck.active
                    val want = liveKey(c)
                    if (want != filmShown[a] && (want != null || filmOn[a])) {
                        filmShown[a] = want
                        // The player only takes new picture effects when it loads the clip, so the clip is
                        // loaded again at the same spot once the slider has stopped moving for a moment.
                        es.fxJob?.cancel()
                        es.fxJob = layerScope.launch {
                            delay(300)
                            val cc = clips.getOrNull(es.current)
                            if (cc != null && cc.id == es.loadedId) prepareOn(deck.cur, cc, deck.cur.currentPosition, deck.cur.playWhenReady)
                        }
                    }
                }
                continue
            }
            if (!es.effectsOk || c.id != es.loadedId) continue
            val fx = withContext(Dispatchers.Default) { try { olderPreviewEffects(adjOf(c)) } catch (_: Throwable) { null } } ?: continue
            if (c.id != es.loadedId) continue
            try {
                deck.cur.setVideoEffects(fx)
                // Redraw a paused frame (never while loading or playing).
                if (!deck.cur.playWhenReady && deck.cur.playbackState == Player.STATE_READY) deck.cur.seekTo(deck.cur.currentPosition)
            } catch (_: Throwable) { es.effectsOk = false }
            delay(80)
        }
    }
    val nextClip = clips.getOrNull(es.current + 1)
    LaunchedEffect(nextClip?.id, nextClip?.grade, nextClip?.detail, nextClip?.startMs, nextClip?.endMs) {
        delay(150)
        if (clips.isNotEmpty()) preloadAfter(es.current)
    }
    LaunchedEffect(cur?.audio) { cur?.let { audioFx[deck.active].fx = it.audio } }
    LaunchedEffect(cur?.speed, cur?.volume, cur?.muted, es.previewMuted, es.videoMuted) {
        cur?.let {
            deck.cur.playbackParameters = PlaybackParameters(it.speedAtSrc(it.startMs + deck.cur.currentPosition))
            deck.cur.volume = if (it.muted || es.previewMuted || es.videoMuted) 0f else it.volume.coerceAtMost(1f)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            es.posMs = deck.cur.currentPosition
            PreviewClock.posMs = es.posMs; PreviewClock.at = android.os.SystemClock.uptimeMillis(); PreviewClock.playing = deck.cur.isPlaying
            // Loop with an in–out range: past the out point, back to the in point.
            val rs = es.deliver.rangeStart; val re = es.deliver.rangeEnd
            if (es.loop && rs != null && re != null && re > rs && deck.cur.isPlaying) {
                val ci = es.current; val cc = clips.getOrNull(ci)
                val gNow = (clipStarts(clips).getOrNull(ci) ?: 0L) + (cc?.tlOffsetOf(cc.startMs + es.posMs) ?: es.posMs)
                if (gNow >= re || gNow < rs - 500) {
                    val (i, local) = clipAt(clips, rs)
                    val c0 = clips[i]; val at = c0.srcAtOffset(local) - c0.startMs
                    es.seekCount++
                    if (i == es.current && es.loadedId == c0.id) deck.cur.seekTo(at) else load(i, at, true)
                }
            }
            val c0 = clips.getOrNull(es.current)
            if (es.showInfo) {
                fun st(p: ExoPlayer) = when (p.playbackState) { Player.STATE_IDLE -> "IDLE"; Player.STATE_BUFFERING -> "LOAD"; Player.STATE_READY -> "READY"; else -> "END" } +
                    (if (p.playWhenReady) "▶" else "‖") + " " + (p.currentPosition / 100) / 10f + "s" + (p.playerError?.let { " ERR ${it.errorCodeName}" } ?: "")
                es.infoText = "clip ${es.current + 1}/${clips.size} · player ${deck.active + 1}\n" +
                    "A: ${st(players[0])}  B: ${st(players[1])}\n" +
                    "src ${if (c0 != null && proxyFor(c0) != null) "proxy " + (proxyFor(c0)?.let { ProxyMaker.qualityOf(it)?.label } ?: "") else "original"} · fx ${if (es.effectsOk) "on" else "off"} · frame ${if (renderedFor[deck.active] == c0?.id) "ok" else "none"} · stalls ${wx.stallTries}"
            }
            val nowPlaying = deck.cur.isPlaying
            if (nowPlaying && !es.playing) wx.playSince = System.currentTimeMillis()
            es.playing = nowPlaying
            // Frozen (playing but the position stopped moving): reload the clip where it stopped.
            if (deck.cur.playWhenReady && deck.cur.playbackState != Player.STATE_ENDED && c0 != null && c0.id == es.loadedId) {
                val p = deck.cur.currentPosition
                if (p != wx.stallPos) { wx.stallPos = p; wx.stallSince = System.currentTimeMillis() }
                else if (System.currentTimeMillis() - wx.stallSince > 2500 && wx.stallTries < 3) {
                    wx.stallSince = System.currentTimeMillis() + 2000
                    wx.stallTries++
                    // Escalate: reload → reload without live effects → use a preview copy.
                    when (wx.stallTries) {
                        1 -> load(es.current, p, true)
                        2 -> { if (es.effectsOk && !gpuGrade) { es.effectsOk = false; es.message = "Live effects paused in the preview to keep playback smooth (the export keeps them)" }; load(es.current, p, true) }
                        else -> {
                            if (proxyFor(c0) != null) {
                                proxyBad.add(MediaId.of(c0)); es.message = "The proxy stalled — playing the original"
                            } else if (MediaId.of(c0) !in proxyMedia) es.message = "This clip is heavy for live playback — add it in the Proxy tab for smooth editing"
                            else es.message = "Playback stalled — tap play to retry"
                            deck.cur.pause()
                        }
                    }
                }
            } else wx.stallSince = System.currentTimeMillis()
            if (deck.cur.isPlaying && deck.cur.currentPosition != wx.stallPos) { wx.stallTries = 0; es.errorRetries = 0 }
            // Safety net: playing but no picture for a while → make a preview copy, or drop the live effects.
            val c = clips.getOrNull(es.current)
            if (c != null && nowPlaying && renderedFor[deck.active] != c.id && System.currentTimeMillis() - wx.playSince > 2500) {
                wx.playSince = System.currentTimeMillis() + 60_000
                if (proxyFor(c) != null) {
                    // The proxy gives no picture: never show a blank frame — use the original.
                    proxyBad.add(MediaId.of(c))
                    es.message = "The proxy for ${c.name} shows no picture — playing the original"
                } else if (es.effectsOk) {
                    es.effectsOk = false
                    es.message = "Live effects turned off for the preview; the export still uses them"
                    load(es.current, deck.cur.currentPosition, true)
                }
            }
            delay(100)
        }
    }
    // Make the sound effects used on cuts ahead of time (preview and export).
    LaunchedEffect(clips.mapNotNull { it.transition.sfx }.toSet()) {
        val used = clips.mapNotNull { it.transition.sfx }.toSet()
        withContext(Dispatchers.IO) { used.forEach { runCatching { SfxSynth.file(context, it) } } }
    }
    // Load saved stabilisation results.
    LaunchedEffect(clips.filter { it.stab.enabled }.map { Stabilizer.keyOf(it) }.distinct()) {
        for (c in clips.toList()) {
            if (!c.stab.enabled) continue
            val k = Stabilizer.keyOf(c)
            if (motions[k] != null) continue
            val m = withContext(Dispatchers.IO) { Stabilizer.cached(context, c) }
            if (m != null) motions[k] = m else analyze(c)
        }
    }
    // Morph cuts: in-between frames drawn ahead of time for the preview (the export draws its own at full size).
    val morphCache = remember { mutableStateMapOf<String, MorphPreviewFrames>() }
    val morphKeys = clips.indices.filter { it + 1 < clips.size && clips[it].transition.type == TransitionType.SMOOTH_CUT && overlapMs(clips, it) > 0 }
        .map { Triple(it, clips[it] to clips[it + 1], overlapMs(clips, it)) }
    LaunchedEffect(morphKeys.map { (_, p, ov) -> MorphPreview.keyOf(p.first, p.second, ov) }) {
        delay(600)
        val want = morphKeys.associateBy { (_, p, ov) -> MorphPreview.keyOf(p.first, p.second, ov) }
        morphCache.keys.filter { it !in want }.forEach { morphCache.remove(it) }
        for ((k, v) in want) {
            if (morphCache.containsKey(k)) continue
            val (_, p, ov) = v
            es.morphBusy = true
            val f = withContext(Dispatchers.Default) { try { MorphPreview.build(context, p.first, p.second, ov, motionFor(p.first), motionFor(p.second)) } catch (_: Throwable) { null } }
            if (f != null) morphCache[k] = f
        }
        es.morphBusy = false
    }
    /** The picture's speed relative to the clip's own speed: set while the picture keeps up with the music. */
    val syncMul = remember { floatArrayOf(1f) }
    // Every frame: crop/zoom/stabilise the picture and play transitions between clips.
    LaunchedEffect(Unit) {
        var was = false
        var cutFaded = false
        var lastG = -1L
        var startedFor: String? = null
        fun vol(c: Clip) = if (c.muted || es.previewMuted || es.videoMuted) 0f else c.volume.coerceAtMost(1f)
        while (true) {
            androidx.compose.runtime.withFrameNanos { }
            val i = es.current
            val c = clips.getOrNull(i) ?: continue
            val a = deck.active
            if (c.id == es.loadedId) texViews[a]?.let {
                ClipXform.applyToView(it, ClipXform.at(c, c.startMs + deck.cur.currentPosition, motionFor(c)))
                it.alpha = c.reframe.opacityAt(deck.cur.currentPosition)
                if (gpuGrade) { if (es.effectsOk) GradePreview.apply(it, adjOf(c), System.nanoTime() / 1e9f, ClipPreviewFx.extra(context, it, pvc(c), c.startMs + deck.cur.currentPosition)) else GradePreview.clear(it) }
            }
            val n = clips.getOrNull(i + 1)
            val ov = overlapMs(clips, i)
            val local = c.tlOffsetOf(c.startMs + deck.cur.currentPosition)
            // Speed ramps: follow the curve while playing.
            if (c.ramp != null && c.id == es.loadedId) {
                val sp = c.speedAtSrc(c.startMs + deck.cur.currentPosition) * syncMul[0]
                if (kotlin.math.abs(deck.cur.playbackParameters.speed - sp) > 0.04f) deck.cur.playbackParameters = PlaybackParameters(sp)
            }
            // Volume keyframes, and audio transitions on plain cuts (fade out / in around the cut).
            if (c.id == es.loadedId && !transitionOn[0]) {
                var m = 1f
                var xt = AudioXfx.NONE; var xa = 0f
                if (ov == 0L) AudioXfade.forCut(clips, i).first?.let { m *= it.gain(c.lengthMs - local); val pr = it.progress(c.lengthMs - local); if (it.fx != AudioXfx.NONE && pr > 0f) { xt = it.fx; xa = pr } }
                if (i > 0 && overlapMs(clips, i - 1) == 0L) AudioXfade.forCut(clips, i - 1).second?.let { m *= it.gain(local); val lf = it.left(local); if (xt == AudioXfx.NONE && it.fx != AudioXfx.NONE && lf > 0f) { xt = it.fx; xa = lf } }
                audioFx[deck.active].liveXfx = xt; audioFx[deck.active].liveXAmt = xa
                val keyed = !c.volKeys.isNullOrEmpty()
                if (keyed || m < 1f || cutFaded) {
                    deck.cur.volume = (vol(c) * (if (keyed) volAt(c.volKeys, deck.cur.currentPosition) else 1f) * m).coerceIn(0f, 1f)
                    cutFaded = m < 1f
                }
            }
            // Sound effects on cuts: start each one as the playhead passes its start.
            if (es.playing && !es.previewMuted && c.id == es.loadedId) {
                val st = clipStarts(clips)
                val gNow = (st.getOrNull(i) ?: 0L) + local
                if (lastG >= 0 && gNow > lastG && gNow - lastG < 400) {
                    for (k in 0 until clips.lastIndex) {
                        val tr = clips[k].transition
                        val user = UserSfxLibrary.find(context, tr.userSfx)
                        if (user != null) {
                            val at = cutSoundStart(clips, st, k, user.hitMs)
                            if (at in (lastG + 1)..gNow) UserSfxLibrary.play(context, user, tr.sfxVolume)
                            continue
                        }
                        val fx = tr.sfx ?: continue
                        val at = sfxStartMs(clips, st, k) ?: continue
                        if (at in (lastG + 1)..gNow) SfxPlayer.play(context, fx, tr.sfxVolume)
                    }
                }
                lastG = gNow
            } else lastG = -1L
            val startT = c.lengthMs - ov
            if (ov > 0 && n != null && es.preloadedId == n.id && local >= startT && deck.cur.playbackState == Player.STATE_READY) {
                val p = ((local - startT).toFloat() / ov).coerceIn(0f, 1f)
                // Start the incoming clip once (seeking again while it loads would stall it).
                if (deck.cur.playWhenReady && !deck.spare.playWhenReady) {
                    if (startedFor != n.id) {
                        startedFor = n.id
                        val want = n.srcAtOffset(local - startT) - n.startMs
                        if (kotlin.math.abs(deck.spare.currentPosition - want) > 250) deck.spare.seekTo(want)
                    }
                    deck.spare.play()
                }
                if (!deck.cur.playWhenReady && deck.spare.playWhenReady) deck.spare.pause()
                transitionOn[0] = true
                texViews[1 - a]?.let {
                    ClipXform.applyToView(it, ClipXform.at(n, n.startMs + deck.spare.currentPosition, motionFor(n)))
                    it.alpha = n.reframe.opacityAt(deck.spare.currentPosition)
                    if (gpuGrade && es.effectsOk) GradePreview.apply(it, adjOf(n), System.nanoTime() / 1e9f, ClipPreviewFx.extra(context, it, pvc(n), n.startMs + deck.spare.currentPosition))
                }
                frameViews[a]?.let { TransitionPreview.apply(it, c.transition.type, p, false) }
                frameViews[1 - a]?.let { TransitionPreview.apply(it, c.transition.type, p, true) }
                // Morph cut: show the analysed in-between frame on top.
                es.morphFrame = if (c.transition.type == TransitionType.SMOOTH_CUT) morphCache[MorphPreview.keyOf(c, n, ov)]?.frames?.let { fr -> fr[((p * (fr.size - 1)) + 0.5f).toInt().coerceIn(0, fr.lastIndex)] } else null
                // Audio transition over the overlap.
                val (fo, fi) = AudioXfade.forCut(clips, i)
                audioFx[a].liveXfx = fo?.fx ?: AudioXfx.NONE; audioFx[a].liveXAmt = p
                audioFx[1 - a].liveXfx = fi?.fx ?: AudioXfx.NONE; audioFx[1 - a].liveXAmt = 1f - p
                deck.cur.volume = (vol(c) * (fo?.gain(((1f - p) * ov).toLong()) ?: (1f - p))).coerceIn(0f, 1f)
                deck.spare.volume = (vol(n) * (fi?.gain((p * ov).toLong()) ?: p)).coerceIn(0f, 1f)
                was = true
            } else if (was) {
                was = false
                es.morphFrame = null
                transitionOn[0] = false
                frameViews[a]?.let { TransitionPreview.reset(it, true) }
                frameViews[1 - a]?.let { TransitionPreview.reset(it, false) }
                audioFx.forEach { it.liveXfx = AudioXfx.NONE; it.liveXAmt = 0f }
                deck.cur.volume = vol(c)
                if (deck.spare.playWhenReady) { deck.spare.pause(); deck.spare.seekTo(0) }
                startedFor = null
            }
        }
    }
    /**
     * Keeps an audio player in step with the picture without gaps: a small difference is closed by playing a
     * touch faster or slower (pitch kept) for a moment; only a big one (a jump, a stall) is fixed by seeking.
     */
    fun followSmoothly(p: ExoPlayer, want: Long, name: String) {
        val drift = p.currentPosition - want
        val speed = when {
            // Not started yet: start at the right place. Playing (or loading) far off: jump there.
            !p.playWhenReady || kotlin.math.abs(drift) > 1200 -> {
                if (p.playWhenReady) { com.dronedirector.veyrocutpro.ui.AppLog.w("Preview: $name re-synced (was ${drift} ms off)"); android.util.Log.w("FxTest", "$name re-synced, drift $drift ms") }
                p.seekTo(want); 1f
            }
            kotlin.math.abs(drift) < 40 -> 1f
            else -> (1f - drift / 4000f).coerceIn(0.94f, 1.06f)
        }
        if (kotlin.math.abs(p.playbackParameters.speed - speed) > 0.001f) p.playbackParameters = PlaybackParameters(speed)
    }
    // The soundtrack sets the preview's timing: once the music plays it is never jumped or sped up, so it
    // never glitches. The picture follows it instead — a touch faster or slower for a moment, or, if it has
    // fallen far behind (a heavy clip, a busy device), it skips ahead to the music.
    fun setPictureSpeed(mul: Float) {
        syncMul[0] = mul
        val c = clips.getOrNull(es.current) ?: return
        if (c.id != es.loadedId) return
        val sp = c.speedAtSrc(c.startMs + deck.cur.currentPosition) * mul
        if (kotlin.math.abs(deck.cur.playbackParameters.speed - sp) > 0.002f) deck.cur.playbackParameters = PlaybackParameters(sp)
    }
    LaunchedEffect(es.playing, es.music, es.previewMuted) {
        val m = es.music
        if (m == null) { musicPlayer.pause(); setPictureSpeed(1f); return@LaunchedEffect }
        musicFxProc.fx = m.fx
        musicPlayer.volume = if (es.previewMuted || m.muted) 0f else m.volume.coerceIn(0f, 1f)
        if (!es.playing) { musicPlayer.pause(); setPictureSpeed(1f); return@LaunchedEffect }
        if (musicPlayer.playbackParameters.speed != 1f) musicPlayer.playbackParameters = PlaybackParameters(1f)
        var lastSkip = 0L
        var started = false; var aligned = false
        var seen = es.seekCount
        try {
            while (true) {
                val ducked = clips.getOrNull(es.current)?.let { it.audio.duckMusic && !it.muted && it.volume > 0f } == true
                musicPlayer.volume = if (es.previewMuted || m.muted) 0f else (m.volume * (if (ducked) 1f - m.duck else 1f)).coerceIn(0f, 1f)
                val before = clipStarts(clips).getOrNull(es.current) ?: 0L
                val g = before + (clips.getOrNull(es.current)?.let { it.tlOffsetOf(it.startMs + deck.cur.currentPosition) } ?: deck.cur.currentPosition)
                val len = m.playLength(timelineLength(clips))
                val want = m.offsetMs + (g - m.startMs)
                if (g < m.startMs || g >= m.startMs + len) { musicPlayer.pause(); setPictureSpeed(1f); started = false }
                else if (seen != es.seekCount) {
                    // A jump made on purpose (seek, loop): the music goes there too, and is lined up again once it plays.
                    seen = es.seekCount
                    musicPlayer.seekTo(want); if (!musicPlayer.playWhenReady) musicPlayer.play()
                    setPictureSpeed(1f); started = true; aligned = false
                } else if (!started || musicPlayer.playbackState == Player.STATE_ENDED || musicPlayer.playbackState == Player.STATE_IDLE || !musicPlayer.playWhenReady) {
                    // The music comes in: start it where the picture is.
                    if (musicPlayer.playbackState == Player.STATE_IDLE) musicPlayer.prepare()
                    musicPlayer.seekTo(want); musicPlayer.play(); setPictureSpeed(1f)
                    started = true; aligned = false
                } else if (!aligned && musicPlayer.playbackState == Player.STATE_READY && musicPlayer.isPlaying) {
                    // Once, the moment it really plays: line it up with the picture (it may have taken a moment to load).
                    aligned = true
                    val musicG = m.startMs + (musicPlayer.currentPosition - m.offsetMs)
                    if (kotlin.math.abs(g - musicG) > 150) musicPlayer.seekTo(want)
                } else if (musicPlayer.playbackState == Player.STATE_READY && musicPlayer.isPlaying && deck.cur.isPlaying) {
                    // Where the music is on the timeline, and how far the picture is from it (+ = picture ahead).
                    val musicG = m.startMs + (musicPlayer.currentPosition - m.offsetMs)
                    val off = g - musicG
                    val now = android.os.SystemClock.uptimeMillis()
                    when {
                        // A big difference is a jump made on purpose (a seek while playing): the music goes there.
                        kotlin.math.abs(off) > 4000 -> { musicPlayer.seekTo(want); setPictureSpeed(1f) }
                        // The picture has fallen far behind (or run ahead): it skips to the music.
                        kotlin.math.abs(off) > 1200 && now - lastSkip > 1500 -> {
                            lastSkip = now
                            com.dronedirector.veyrocutpro.ui.AppLog.w("Preview: picture skipped to the music (was $off ms off)")
                            android.util.Log.w("FxTest", "Picture skipped to the music, off $off ms")
                            setPictureSpeed(1f)
                            if (clips.isNotEmpty()) {
                                val (i, local) = clipAt(clips, musicG.coerceAtLeast(0L))
                                val c2 = clips[i]; val at = c2.srcAtOffset(local) - c2.startMs
                                if (i == es.current && es.loadedId == c2.id) deck.cur.seekTo(at) else load(i, at, true)
                            }
                        }
                        kotlin.math.abs(off) < 40 -> setPictureSpeed(1f)
                        // In between: the picture plays a little faster or slower until it is with the music again.
                        else -> setPictureSpeed((1f - off / 2500f).coerceIn(0.85f, 1.15f))
                    }
                }
                delay(200)
            }
        } finally { syncMul[0] = 1f }
    }
    // Mixer → preview: track and master levels, track pan, solo.
    LaunchedEffect(es.deliver.videoGain, es.deliver.videoPan, es.deliver.masterGain, es.soloTrack, extraAudio.toList(), es.music) {
        fun solo(id: String) = if (es.soloTrack != null && es.soloTrack != id) 0f else 1f
        audioFx.forEach { it.liveGain = es.deliver.videoGain * es.deliver.masterGain * solo("video"); it.livePan = es.deliver.videoPan }
        musicFxProc.liveGain = es.deliver.masterGain * solo("music")
        extraFx.forEach { (id, p) -> p.liveGain = es.deliver.masterGain * solo(id) }
    }
    // Extra audio tracks follow the timeline too.
    LaunchedEffect(es.playing, extraAudio.toList(), es.previewMuted) {
        // Players for new tracks; drop players of removed tracks.
        val ids = extraAudio.map { it.id }.toSet()
        audioPlayers.keys.filter { it !in ids }.forEach { audioPlayers.remove(it)?.release(); extraFx.remove(it) }
        for (t in extraAudio) audioPlayers.getOrPut(t.id) {
            val fx = AudioFxProcessor(t.fx, live = true).also { extraFx[t.id] = it }
            fxPlayer(context, fx).apply { setMediaItem(MediaItem.fromUri(t.uri)); prepare() }
        }
        for (t in extraAudio) extraFx[t.id]?.fx = t.fx
        if (!es.playing) { audioPlayers.values.forEach { it.pause() }; return@LaunchedEffect }
        while (true) {
            val before = clipStarts(clips).getOrNull(es.current) ?: 0L
            val g = before + (clips.getOrNull(es.current)?.let { it.tlOffsetOf(it.startMs + deck.cur.currentPosition) } ?: deck.cur.currentPosition)
            val total = timelineLength(clips)
            for (t in extraAudio) {
                val p = audioPlayers[t.id] ?: continue
                p.volume = if (es.previewMuted || t.muted) 0f else t.volume.coerceIn(0f, 1f)
                val len = t.playLength(total)
                if (g < t.startMs || g >= t.startMs + len) { if (p.isPlaying) p.pause(); continue }
                val want = t.offsetMs + (g - t.startMs)
                followSmoothly(p, want, t.name)
                if (!p.isPlaying) p.play()
            }
            delay(250)
        }
    }
    LaunchedEffect(es.current) { if (es.page != Page.MEDIA) es.selected = es.current.coerceIn(0, (clips.size - 1).coerceAtLeast(0)) }
    // Timeline thumbnails: the cover at once (the phone's ready thumbnail or a kept one), several clips at a time.
    LaunchedEffect(clips.map { it.id to it.startMs }) {
        kotlinx.coroutines.coroutineScope {
            for (c in clips.toList()) if (thumbs[c.id] == null) launch {
                val b = withContext(VideoThumbs.io) { VideoThumbs.cover(context, c.uri, c.startMs + 500, 240) }
                if (b != null) thumbs[c.id] = b
            }
        }
    }
    // Filmstrips for the timeline (one per source file): shown at once, filled in coarse to fine, kept on disk.
    LaunchedEffect(clips.map { it.uri }.distinct()) {
        kotlinx.coroutines.coroutineScope {
            for (c in clips.distinctBy { it.uri.toString() }) {
                val key = c.uri.toString()
                if (strips[key]?.frames?.size?.let { it >= 4 } == true && strips[key]?.complete == true) continue
                launch {
                    val n = (c.durationMs / 1000).toInt().coerceIn(4, 40)
                    val h = 96
                    val w = if (c.height > 0) (h * c.width / c.height).coerceIn(48, 200) else 160
                    VideoThumbs.strip(context, c.uri, proxyFor(c) ?: c.uri, c.durationMs, n, h, w) { frames ->
                        strips[key] = Filmstrip(frames, c.durationMs)
                    }
                    strips[key]?.let { strips[key] = Filmstrip(it.frames, c.durationMs, complete = true) }
                }
            }
        }
    }
    // Scope: graded still of the paused frame.
    LaunchedEffect(es.scopeOpen, es.fullscreen, es.playing, es.current, es.posMs / 500, cur?.grade) {
        if (!es.scopeOpen || es.fullscreen || es.playing) return@LaunchedEffect
        val c = cur ?: return@LaunchedEffect
        delay(250)
        es.scopeHist = withContext(Dispatchers.Default) {
            val frame = GradeIO.videoFrame(context, c.uri, (c.startMs + es.posMs) * 1000, 360) ?: return@withContext null
            val out = ImageOps.render(frame, c.grade, Detail(), Geometry(), if (c.grade.isIdentity) null else c.grade.tables())
            es.scopeFrame = out
            ImageOps.histogram(out)
        }
    }
    LaunchedEffect(es.message) { if (es.message != null) { delay(3000); es.message = null } }

    val totalMs = timelineLength(clips)
    val beforeMs = clipStarts(clips).getOrNull(es.current) ?: 0L
    val globalMs = beforeMs + (cur?.tlOffsetOf(cur.startMs + es.posMs) ?: es.posMs)
    addAtMs[0] = globalMs
    androidx.compose.runtime.SideEffect { es.adjNowMs = globalMs }

    /** Opens a tools page with one of its sections unfolded and scrolled into view. */
    fun goToSection(page: Page, section: String?) {
        if (section != null) com.dronedirector.veyrocutpro.ui.Fold.jump("video", section)
        es.page = page
    }
    /** Colour & details: clip [i]'s grade (exposure, contrast, highlights, shadows…). */
    fun openColor(i: Int) {
        if (i !in clips.indices) return
        es.selected = i; es.colorTab = ColorTab.PRIMARIES; es.page = Page.COLOR
        es.message = "Colour & details for clip ${i + 1} — Wheels, Curves, HSL and Detail are in the tabs above"
    }
    /** Reveal in project: the clip's file is put in the media strip if it isn't there yet, then picked out there. */
    fun revealInLibrary(c: Clip) {
        if (library.none { it.uri == c.uri }) library.add(c.copy(id = java.util.UUID.randomUUID().toString(), startMs = 0, endMs = c.durationMs, speed = 1f, ramp = null, rampName = null, volKeys = null, transition = Transition()))
        if (!com.dronedirector.veyrocutpro.ui.Panels.isOn(context, "ve.mediastrip")) com.dronedirector.veyrocutpro.ui.Panels.set(context, "ve.mediastrip", true)
        es.revealUri = c.uri.toString(); es.revealTick++
        es.message = "\"${c.name}\" is picked out in the media strip"
    }
    fun globalNow(): Long = (clipStarts(clips).getOrNull(es.current) ?: 0L) + (clips.getOrNull(es.current)?.let { it.tlOffsetOf(it.startMs + deck.cur.currentPosition) } ?: deck.cur.currentPosition)
    LaunchedEffect(FxTest.playback) {
        if (!FxTest.playback) return@LaunchedEffect
        // (The flag is cleared at the end: clearing it here would restart this effect and stop the test.)
        android.util.Log.i("FxTest", "playback test: starting with ${clips.size} clip(s)")
        // Playback test: an edit like a real one (3 clips, a song, lower third, adjustment layer, shape,
        // watermark) plays for 24 s; memory, the playhead and the music's sync are logged every 2 s.
        es.music = MusicTrack(uri = VisTest.song(context, 40), name = "Test song", durationMs = 40_000L)
        val total = timelineLength(clips)
        layers.add(lowerThirdLayer(6, 2000L, 9000L))
        layers.add(Layer(kind = LayerKind.ADJUST, text = "Adjustment", adjGrade = ProGrade(exposure = 0.2f), startMs = 0, endMs = total))
        layers.add(Layer(kind = LayerKind.SHAPE, text = "Shape", shape = ShapeKind.ROUNDED.ordinal, size = 0.2f, color = 0xFFFFB300.toInt(), shadow = false, startMs = 0, endMs = total))
        layers.add(Layer(kind = LayerKind.WATERMARK, text = "© AlcorCut Pro", size = 0.04f, x = 0.84f, y = 0.92f, opacity = 0.65f, wholeVideo = true))
        delay(3000)
        load(0, 0, true)
        val t0 = android.os.SystemClock.uptimeMillis(); val g0 = globalNow()
        repeat(12) {
            delay(2000)
            val dt = android.os.SystemClock.uptimeMillis() - t0
            val g = globalNow()
            val m = es.music
            val drift = if (m != null) musicPlayer.currentPosition - (m.offsetMs + (g - m.startMs)) else 0L
            android.util.Log.i("VisTest", "playback ${dt / 1000}s: timeline ${(g - g0) / 1000f}s, playing ${deck.cur.isPlaying}, music drift $drift ms, picture speed ${"%.3f".format(syncMul[0])} · " +
                com.dronedirector.veyrocutpro.ui.AppLog.memory(context))
        }
        deck.cur.pause()
        FxTest.playback = false
    }
    fun seekGlobal(ms: Long) {
        if (clips.isEmpty()) return
        es.seekCount++
        val (i, local) = clipAt(clips, ms)
        val c = clips[i]
        val off = c.srcAtOffset(local) - c.startMs
        if (i == es.current && es.loadedId == c.id) deck.cur.seekTo(off) else load(i, off, deck.cur.isPlaying)
    }

    fun update(i: Int, f: (Clip) -> Clip) { if (i in clips.indices) clips[i] = f(clips[i]) }

    // ---- Pro trim tools (the main track is magnetic, so a normal trim is a ripple trim).
    /** Roll / slip / slide on clip [i] by [d] ms of source time. */
    fun trimTool(mode: Int, i: Int, d: Long) {
        if (es.videoLocked) { es.message = "The video track is locked"; return }
        val c = clips.getOrNull(i) ?: return
        val min = 100L
        when (mode) {
            // SLIP: same place and length, different part of the clip.
            0 -> {
                val dd = d.safeIn(-c.startMs, c.durationMs - c.endMs)
                if (dd == 0L) { es.message = "Nothing more of the clip to slip to"; return }
                clips[i] = c.copy(startMs = c.startMs + dd, endMs = c.endMs + dd)
            }
            // ROLL: move the cut between this clip and the next; total length stays the same.
            1 -> {
                val n = clips.getOrNull(i + 1) ?: run { es.message = "Roll moves the cut after this clip — select a clip that has one after it"; return }
                val dd = d.safeIn(maxOf(-(c.endMs - c.startMs - min), -n.startMs), minOf(c.durationMs - c.endMs, n.endMs - n.startMs - min))
                if (dd == 0L) return
                clips[i] = c.copy(endMs = c.endMs + dd)
                clips[i + 1] = n.copy(startMs = n.startMs + dd)
            }
            // SLIDE: the clip itself stays the same and moves along; its neighbours give and take.
            2 -> {
                val p = clips.getOrNull(i - 1); val n = clips.getOrNull(i + 1)
                if (p == null || n == null) { es.message = "Slide needs a clip before and after this one"; return }
                val dd = d.safeIn(maxOf(-(p.endMs - p.startMs - min), -n.startMs), minOf(p.durationMs - p.endMs, n.endMs - n.startMs - min))
                if (dd == 0L) return
                clips[i - 1] = p.copy(endMs = p.endMs + dd)
                clips[i + 1] = n.copy(startMs = n.startMs + dd)
            }
        }
    }

    // ---- Nested (compound) clips: several clips rendered into one, which can be opened up again.
    fun nestClips(from: Int, count: Int) {
        if (es.videoLocked) { es.message = "The video track is locked"; return }
        if (es.nesting || es.exporting) return
        val to = (from + count).coerceAtMost(clips.size)
        if (to - from < 2) { es.message = "Choose at least 2 clips to nest"; return }
        val group = clips.subList(from, to).toList()
        val dir = java.io.File(context.filesDir, "nested").apply { mkdirs() }
        val file = java.io.File(dir, "nest_${System.currentTimeMillis()}.mp4")
        es.nesting = true; es.progress = 0
        es.message = "Nesting ${group.size} clips…"
        val e = TimelineExport(
            context, group, es.deliver.copy(audioOnly = false, rangeStart = null, rangeEnd = null, loudness = null, bitrateMbps = maxOf(es.deliver.bitrateMbps, 40)),
            onProgress = { es.progress = it },
            onDone = { uri, err ->
                if (uri == null) { es.nesting = false; es.message = err ?: "Couldn't nest the clips"; return@TimelineExport }
                layerScope.launch {
                    val nc = withContext(Dispatchers.IO) { ClipLoader.load(context, Uri.fromFile(file)) }
                    es.nesting = false
                    if (nc == null) { es.message = "Couldn't open the nested clip"; return@launch }
                    // Replace the group (if the timeline didn't change meanwhile).
                    val idx = clips.indexOfFirst { it.id == group.first().id }
                    if (idx < 0 || clips.subList(idx, (idx + group.size).coerceAtMost(clips.size)).map { it.id } != group.map { it.id }) {
                        es.message = "The timeline changed while nesting — try again"; return@launch
                    }
                    repeat(group.size) { clips.removeAt(idx) }
                    clips.add(idx, nc.copy(name = "Nested · ${group.size} clips", nested = group, transition = group.last().transition))
                    es.selected = idx
                    es.message = "${group.size} clips nested into one — UN-NEST opens them up again"
                }
            },
            toFile = file,
        )
        try { e.start() } catch (t: Throwable) { es.nesting = false; es.message = "Couldn't nest: ${t.message}" }
    }
    fun unNest(i: Int) {
        val c = clips.getOrNull(i) ?: return
        val inner = c.nested ?: return
        clips.removeAt(i)
        clips.addAll(i, inner)
        es.message = "Opened up into ${inner.size} clips"
    }

    /** Saves the whole edit (clips, grades, layers, music, delivery settings) as a project. */
    fun currentData(): VideoProjectData = VideoProjectData(
            id = es.projectId ?: "", name = es.projectName,
            clips = clips.toList(), layers = layers.toList(), music = es.music, deliver = es.deliver,
            extraAudio = extraAudio.toList(), audioPool = audioPool.toList(), library = library.toList(), ratings = ratings.toMap(),
            videoMuted = es.videoMuted, videoLocked = es.videoLocked, videoHidden = es.videoHidden, markers = markers.toList(),
            markerNotes = markerNotes.filterKeys { it in markers }.mapKeys { it.key.toString() },
            timelines = otherTimelines.toList().takeIf { it.isNotEmpty() }, timelineName = es.timelineName,
            proxyMedia = proxyMedia.toList().takeIf { it.isNotEmpty() },
            bins = bins.toMap().takeIf { it.isNotEmpty() }, binNames = binNames.toList().takeIf { it.isNotEmpty() },
        )
    // This edit in the list of open projects: its "last saved" look, and the live edit for switching.
    val myOp = remember { host.activeProject() }
    DisposableEffect(Unit) {
        val f: () -> VideoProjectData = { currentData() }
        host.snapshot = f
        onDispose { if (host.snapshot === f) host.snapshot = null }
    }
    LaunchedEffect(Unit) {
        Team.current?.let { if (es.projectName.isBlank()) es.projectName = it.name }
        if (myOp?.savedSig == SIG_PENDING) { delay(1500); myOp.savedSig = ProjectHost.sig(currentData()) }
    }
    fun markSaved() { myOp?.let { it.savedSig = ProjectHost.sig(currentData()); it.name = es.projectName } }
    fun saveProject(name: String) {
        val p = VideoProjectData(
            id = es.projectId ?: java.util.UUID.randomUUID().toString(), name = name,
            clips = clips.toList(), layers = layers.toList(), music = es.music, deliver = es.deliver,
            extraAudio = extraAudio.toList(), audioPool = audioPool.toList(), library = library.toList(), ratings = ratings.toMap(),
            videoMuted = es.videoMuted, videoLocked = es.videoLocked, videoHidden = es.videoHidden, markers = markers.toList(),
            markerNotes = markerNotes.filterKeys { it in markers }.mapKeys { it.key.toString() },
            timelines = otherTimelines.toList().takeIf { it.isNotEmpty() }, timelineName = es.timelineName,
            proxyMedia = proxyMedia.toList().takeIf { it.isNotEmpty() },
            bins = bins.toMap().takeIf { it.isNotEmpty() }, binNames = binNames.toList().takeIf { it.isNotEmpty() },
        )
        val saved = try { ProjectStore.saveVideo(context, p, clips.firstOrNull()?.let { thumbs[it.id] }) } catch (_: Throwable) { null }
        if (saved != null) { es.projectId = saved.id; es.projectName = saved.name; markSaved(); es.message = "Project \"${saved.name}\" saved" }
        else es.message = "Couldn't save the project"
    }
    // A recovered edit that has run for a while is fine: forget the recovery marker.
    LaunchedEffect(Unit) { delay(15_000); com.dronedirector.veyrocutpro.ui.CrashReport.doneRecovering(context) }
    // Auto-save: every 20 s a hidden recovery copy of the edit, in case the app is closed or crashes.
    LaunchedEffect(Unit) {
        var last = ""
        while (true) {
            delay(20_000)
            if (clips.isEmpty() && layers.isEmpty() && es.music == null) continue
            val p = VideoProjectData(
                id = es.projectId ?: "", name = es.projectName,
                clips = clips.toList(), layers = layers.toList(), music = es.music, deliver = es.deliver,
                extraAudio = extraAudio.toList(), audioPool = audioPool.toList(), library = library.toList(), ratings = ratings.toMap(),
                videoMuted = es.videoMuted, videoLocked = es.videoLocked, videoHidden = es.videoHidden, markers = markers.toList(),
                markerNotes = markerNotes.filterKeys { it in markers }.mapKeys { it.key.toString() },
                timelines = otherTimelines.toList().takeIf { it.isNotEmpty() }, timelineName = es.timelineName,
            proxyMedia = proxyMedia.toList().takeIf { it.isNotEmpty() },
            bins = bins.toMap().takeIf { it.isNotEmpty() }, binNames = binNames.toList().takeIf { it.isNotEmpty() },
            )
            val json = withContext(Dispatchers.Default) { try { ProjectStore.gson.toJson(p) } catch (_: Throwable) { "" } }
            if (json.isNotEmpty() && json != last) {
                last = json
                withContext(Dispatchers.IO) {
                    ProjectStore.writeAutosave(context, json); try { ProjectStore.keepAutosaveAccess(context, p) } catch (_: Throwable) {}
                    // The video catalog keeps each clip's latest settings.
                    try { MediaCatalog.remember(context, p.clips) } catch (_: Throwable) {}
                }
            }
        }
    }
    // Timed autosave (gear → AUTOSAVE): a full project copy every few minutes when something changed.
    val asMinutes = remember(AutoSave.changes) { AutoSave.minutes(context, true) }
    LaunchedEffect(asMinutes) {
        if (asMinutes <= 0) return@LaunchedEffect
        var last = ""
        while (true) {
            delay(asMinutes * 60_000L)
            if (clips.isEmpty() && layers.isEmpty() && es.music == null) continue
            val p = VideoProjectData(
                name = AutoSave.nameFor(es.projectName),
                clips = clips.toList(), layers = layers.toList(), music = es.music, deliver = es.deliver,
                extraAudio = extraAudio.toList(), audioPool = audioPool.toList(), library = library.toList(), ratings = ratings.toMap(),
                videoMuted = es.videoMuted, videoLocked = es.videoLocked, videoHidden = es.videoHidden, markers = markers.toList(),
                markerNotes = markerNotes.filterKeys { it in markers }.mapKeys { it.key.toString() },
                timelines = otherTimelines.toList().takeIf { it.isNotEmpty() }, timelineName = es.timelineName,
                proxyMedia = proxyMedia.toList().takeIf { it.isNotEmpty() },
                bins = bins.toMap().takeIf { it.isNotEmpty() }, binNames = binNames.toList().takeIf { it.isNotEmpty() },
            )
            val sig = withContext(Dispatchers.Default) { try { ProjectStore.gson.toJson(p.copy(name = "", id = "")) } catch (_: Throwable) { "" } }
            if (sig.isEmpty() || sig == last) continue
            last = sig
            val thumb = clips.firstOrNull()?.let { thumbs[it.id] }
            withContext(Dispatchers.IO) { try { AutoSave.saveVideo(context, p, thumb) } catch (_: Throwable) {} }
        }
    }
    if (es.askName) ProjectNameDialog(
        es.projectName.ifBlank { ProjectStore.defaultName(true) },
        onSave = {
            es.askName = false; es.projectId = null
            // Saved under a name on this phone: a team project becomes a normal project here.
            Team.current?.let { t -> kotlin.concurrent.thread { Team.release(context, t) }; Team.current = null; host.activeProject()?.team = null }
            saveProject(it); if (es.leaveAfterSave) onExit()
        },
        onDismiss = { es.askName = false; es.leaveAfterSave = false },
        video = true, currentId = es.projectId,
        onOverwrite = { id, n -> es.askName = false; es.projectId = id; saveProject(n); if (es.leaveAfterSave) onExit() },
    )

    /** Blade: cut the clip under the playhead at the exact frame shown. */
    fun splitAtPlayhead() {
        if (es.videoLocked) { es.message = "The video track is locked — tap its lock on the timeline to unlock"; return }
        val c = clips.getOrNull(es.current) ?: return
        val cut = c.startMs + deck.cur.currentPosition
        if (cut <= c.startMs + 100 || cut >= c.endMs - 100) {
            es.message = "Move the playhead inside a clip to split"
            return
        }
        clips[es.current] = c.copy(endMs = cut)
        clips.add(es.current + 1, c.copy(id = java.util.UUID.randomUUID().toString(), startMs = cut))
        es.selected = es.current
        es.message = "Split at ${tc(globalMs)}"
    }

    // ---- AI voiceover from text (Kokoro, downloaded once)
    fun makeTts(text: String, v: Voiceover.Voice, speed: Float) {
        if (wx.ttsJob != null) return
        val at = globalMs
        wx.ttsProgress = 0
        wx.ttsJob = layerScope.launch {
            wx.ttsStatus = "Getting the voice…"
            val err = withContext(Dispatchers.IO) { Voiceover.ensure(context, v, { p -> wx.ttsProgress = p }, { !isActive }) }
            if (err != null) { wx.ttsProgress = null; wx.ttsStatus = null; wx.ttsJob = null; if (isActive) es.message = err; return@launch }
            wx.ttsStatus = "Speaking…"; wx.ttsProgress = 0
            val audio = withContext(Dispatchers.Default) {
                try { Voiceover.speak(context, text, v, speed, { p -> wx.ttsProgress = p }, { !isActive }) } catch (_: Throwable) { null }
            }
            wx.ttsProgress = null; wx.ttsStatus = null; wx.ttsJob = null
            if (!isActive) return@launch
            if (audio == null || audio.isEmpty()) { es.message = "Couldn't make the voice" + (Voiceover.failed?.let { " ($it)" } ?: ""); return@launch }
            val dir = java.io.File(context.filesDir, "voiceovers").apply { mkdirs() }
            val f = java.io.File(dir, "ai_voice_${System.currentTimeMillis()}.wav")
            withContext(Dispatchers.IO) { Voiceover.writeWav(f, audio) }
            val dur = audio.size * 1000L / Voiceover.RATE
            val n = extraAudio.count { it.name.startsWith("AI voice") } + 1
            extraAudio.add(MusicTrack(uri = android.net.Uri.fromFile(f), name = "AI voice $n · ${v.label.substringBefore(" (")}", durationMs = dur, volume = 1f, fadeInMs = 0, fadeOutMs = 100, duck = 0f, startMs = at))
            es.message = "AI voiceover added at ${tc(at)} (${tc(dur)})"
        }
    }

    // ---- Voiceover: record the microphone while the edit plays.
    LaunchedEffect(wx.voiceRec) {
        val t0 = System.currentTimeMillis()
        while (wx.voiceRec != null) { wx.voiceMs = System.currentTimeMillis() - t0; delay(200) }
    }
    fun beginVoice() {
        val dir = java.io.File(context.filesDir, "voice").apply { mkdirs() }
        val f = java.io.File(dir, "voice_${System.currentTimeMillis()}.m4a")
        val r = try {
            @Suppress("DEPRECATION")
            (if (android.os.Build.VERSION.SDK_INT >= 31) android.media.MediaRecorder(context) else android.media.MediaRecorder()).apply {
                setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(160_000)
                setAudioSamplingRate(48_000)
                setOutputFile(f.path)
                prepare()
                start()
            }
        } catch (t: Throwable) { es.message = "Couldn't start the microphone"; return }
        wx.voiceFile = f; wx.voiceRec = r; wx.voiceStart = globalMs
        wx.mutedBeforeVoice = es.previewMuted
        es.previewMuted = true
        if (!deck.cur.isPlaying) deck.cur.play()
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) beginVoice() else es.message = "Allow the microphone to record a voiceover"
    }
    fun startVoice() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) beginVoice()
        else micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
    }
    fun stopVoice() {
        val r = wx.voiceRec ?: return
        wx.voiceRec = null
        deck.cur.pause()
        es.previewMuted = wx.mutedBeforeVoice
        try { r.stop() } catch (_: Throwable) {}
        r.release()
        val f = wx.voiceFile ?: return
        val dur = try {
            android.media.MediaMetadataRetriever().let { m -> m.setDataSource(f.path); val d = m.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L; m.release(); d }
        } catch (_: Throwable) { 0L }
        if (dur < 300) { f.delete(); es.message = "Voiceover too short"; return }
        val n = extraAudio.count { it.name.startsWith("Voiceover") } + 1
        extraAudio.add(MusicTrack(uri = android.net.Uri.fromFile(f), name = "Voiceover $n", durationMs = dur, volume = 1f, fadeInMs = 0, fadeOutMs = 150, duck = 0f, startMs = wx.voiceStart))
        es.message = "Voiceover $n added at ${tc(wx.voiceStart)}"
    }
    DisposableEffect(Unit) { onDispose { wx.voiceRec?.let { try { it.stop() } catch (_: Throwable) {}; it.release() } } }

    /** Beat markers from a music / audio track. */
    fun addBeatMarkers(t: MusicTrack) {
        es.message = "Finding the beat…"
        layerScope.launch {
            val pk = waves[t.uri.toString()] ?: withContext(Dispatchers.IO) { Waveforms.load(context, t.uri) }
            if (pk == null) { es.message = "Couldn't read that audio"; return@launch }
            val beats = withContext(Dispatchers.Default) { Beats.detect(pk) }
            val total = timelineLength(clips)
            val len = t.playLength(total)
            var added = 0
            for (b in beats) {
                if (b < t.offsetMs || b > t.offsetMs + len) continue
                val tl = t.startMs + (b - t.offsetMs)
                if (tl > total || markers.any { kotlin.math.abs(it - tl) < 80 }) continue
                markers.add(tl); added++
            }
            markers.sort()
            es.message = if (added > 0) "$added beat markers added" else "No clear beat found"
        }
    }

    // ---- AI depth (Depth Anything V2 Small, downloaded once)
    fun analyseDepth(i: Int) {
        val c = clips.getOrNull(i) ?: return
        if (wx.depthJob != null) return
        deck.cur.pause()
        wx.depthProgress = 0
        wx.depthJob = layerScope.launch {
            val fx = c.depth ?: DepthFx()
            var err: String? = null
            if (!ModelStore.has(context, AiModel.DEPTH)) {
                wx.depthStatus = "Downloading the depth model…"
                err = withContext(Dispatchers.IO) { ModelStore.ensure(context, AiModel.DEPTH, { p -> wx.depthProgress = p }, { !isActive }) }
            }
            wx.depthStatus = null
            if (err != null) { wx.depthProgress = null; wx.depthJob = null; if (isActive) es.message = err; return@launch }
            wx.depthProgress = 0; wx.depthStatus = "Working out depth…"
            val id = java.util.UUID.randomUUID().toString()
            val n = withContext(Dispatchers.Default) {
                try { DepthTrack.make(context, c, id, { p -> wx.depthProgress = p }, { !isActive }) } catch (_: Throwable) { 0 }
            }
            wx.depthProgress = null; wx.depthStatus = null; wx.depthJob = null
            if (!isActive) return@launch
            val k = clips.indexOfFirst { it.id == c.id }
            if (n <= 0 || k < 0) { es.message = "Couldn't work out the depth" + (DepthModel.failed?.let { " ($it)" } ?: ""); return@launch }
            clips[k] = clips[k].copy(depth = (clips[k].depth ?: fx).copy(id = id, frames = n))
            es.message = "Depth worked out ($n maps) — set the focus and strength"
        }
    }

    // ---- Caption translation (ML Kit, on the phone)
    // ---- AI dubbing: the captions spoken in another voice / language as one audio track.
    fun dubCaptions(lang: String, man: Boolean, mute: Boolean) {
        if (wx.dubJob != null) { wx.dubJob?.cancel(); wx.dubJob = null; wx.dubStatus = null; es.message = "Dubbing stopped"; return }
        val caps = layers.filter { it.kind == LayerKind.TEXT && (it.name == "Caption" || it.name == "Word caption") && !it.wholeVideo && it.text.isNotBlank() }.sortedBy { it.startMs }
        if (caps.isEmpty()) { es.message = "Make captions first (AI CAPTIONS), and translate them if you want another language"; return }
        if (lang != "en" && !com.dronedirector.veyrocutpro.camera.CloudAi.useVideo(context) && !com.dronedirector.veyrocutpro.camera.CloudAi.usePhotos(context)) {
            es.message = "Dubbing in that language uses Cloud AI — add your fal.ai key in Cloud AI settings (English works on the PC)"; return
        }
        wx.dubStatus = "Starting…"
        wx.dubJob = layerScope.launch {
            if (lang == "en") {
                val v = Voiceover.voices.first { it.id == if (man) "am_michael" else "af_heart" }
                val err = withContext(Dispatchers.IO) { Voiceover.ensure(context, v, { p -> wx.dubStatus = "Getting the voice $p%" }, { !isActive }) }
                if (err != null) { wx.dubStatus = null; wx.dubJob = null; es.message = err; return@launch }
            }
            val lines = caps.map { Triple(it.startMs, it.endMs, it.text.replace('\n', ' ')) }
            val audio = withContext(Dispatchers.IO) {
                try { Dubbing.build(context, lines, lang, man, { p -> wx.dubStatus = "Speaking $p%" }, { !isActive }) }
                catch (t: Throwable) { layerScope.launch { es.message = t.message ?: "Dubbing failed" }; null }
            }
            wx.dubStatus = null; wx.dubJob = null
            if (!isActive || audio == null) return@launch
            val dir = java.io.File(context.filesDir, "voiceovers").apply { mkdirs() }
            val f = java.io.File(dir, "dub_${System.currentTimeMillis()}.wav")
            withContext(Dispatchers.IO) { Voiceover.writeWav(f, audio) }
            val label = Dubbing.LANGS.firstOrNull { it.second == lang }?.first?.substringBefore(" (") ?: lang
            extraAudio.add(MusicTrack(uri = android.net.Uri.fromFile(f), name = "AI dub · $label", durationMs = audio.size * 1000L / Dubbing.RATE, volume = 1f, fadeInMs = 0, fadeOutMs = 100, duck = 0f, startMs = 0L))
            es.deliver = es.deliver.copy(videoGain = if (mute) 0f else 0.18f)
            es.message = "AI dub added (${lines.size} lines) — the original voice is " + (if (mute) "muted" else "turned down") + " in the mixer (Audio page)"
        }
    }
    fun translateCaptions(to: String) {
        if (es.translateStatus != null) return
        val caps = layers.filter { it.kind == LayerKind.TEXT && (it.name == "Caption" || it.name == "Word caption") }
        if (caps.isEmpty()) { es.message = "Make captions first (AI CAPTIONS, or word-by-word captions on the Text page)"; return }
        es.translateStatus = "Starting…"
        layerScope.launch {
            val res = withContext(Dispatchers.IO) {
                try { Result.success(CaptionTranslate.translate(caps.map { it.text.replace('\n', ' ') }, null, to) { s -> layerScope.launch { es.translateStatus = s } }) }
                catch (e: Throwable) { Result.failure(e) }
            }
            es.translateStatus = null
            val out = res.getOrNull()
            if (out == null) { es.message = res.exceptionOrNull()?.message ?: "Translation failed"; return@launch }
            for ((k, l) in caps.withIndex()) {
                val i = layers.indexOfFirst { it.id == l.id }
                if (i < 0) continue
                // Word timings don't fit a translation: it becomes a plain caption.
                val t = captionPieces(out[k], 38).joinToString("\n").ifBlank { out[k] }
                layers[i] = l.copy(text = if (l.text == l.text.uppercase() && l.text.any { it.isLetter() }) t.uppercase() else t, words = null, name = "Caption")
            }
            es.message = "${caps.size} captions translated with OPUS-MT"
        }
    }

    // ---- Shape (pen) masks: planar-track the mask from the playhead to the clip's end
    fun trackPen(ci: Int, maskId: String) {
        if (wx.penTrackingId != null) { wx.penStop = true; return }
        val c = clips.getOrNull(ci) ?: return
        val m = c.masks?.firstOrNull { it.id == maskId } ?: return
        val st = clipStarts(clips).getOrNull(ci) ?: 0L
        val rel = (globalMs - st).coerceIn(0L, c.lengthMs - 1)
        val src = c.srcAtOffset(rel)
        val pts = PenMask.pointsAt(m, src)
        if (pts.size < 6) return
        val q0 = PenMask.boundsQuad(pts)
        if (c.lengthMs - rel < 300) { es.message = "Move the playhead to where tracking should start"; return }
        deck.cur.pause()
        wx.penTrackingId = maskId; wx.penStop = false; es.message = "Tracking the mask… 0%"
        layerScope.launch {
            val path = withContext(Dispatchers.Default) {
                try {
                    PlanarTracker.track(context, listOf(c), 0, rel, c.lengthMs, q0,
                        progress = { p -> layerScope.launch { if (wx.penTrackingId != null) es.message = "Tracking the mask… $p% — tap again to stop" } },
                        cancelled = { wx.penStop })
                } catch (_: Throwable) { emptyList() }
            }
            wx.penTrackingId = null
            val i = clips.indexOfFirst { it.id == c.id }
            if (i < 0 || path.size < 2) { es.message = "Couldn't follow what's under the mask — try a part with more detail"; return@launch }
            val track = path.map { it.copy(t = c.srcAtOffset(it.t)) }
            clips[i] = clips[i].copy(masks = clips[i].masks?.map { if (it.id == maskId) it.copy(pts = pts.toList(), pref = q0.toList(), ptrack = track) else it })
            es.message = "Mask tracked through ${track.size} frames — it follows the motion"
        }
    }

    // ---- Planar tracking: the corner-pinned layer follows a flat surface from the playhead on
    fun planarTrack(id: String) {
        if (wx.pinTrackingId != null) { wx.pinStop = true; return }
        val l0 = layers.firstOrNull { it.id == id } ?: return
        val q0 = l0.pinAt(globalMs) ?: return
        if (clips.isEmpty()) return
        deck.cur.pause()
        val from = globalMs
        val starts = clipStarts(clips)
        val ci = starts.indices.lastOrNull { starts[it] <= from } ?: 0
        val to = if (l0.wholeVideo) timelineLength(clips) else l0.endMs
        if (to - from < 300) { es.message = "Move the playhead to where tracking should start"; return }
        wx.pinTrackingId = id; wx.pinStop = false; es.message = "Planar tracking… 0%"
        val snapshot = clips.toList()
        layerScope.launch {
            val path = withContext(Dispatchers.Default) {
                try {
                    PlanarTracker.track(context, snapshot, ci, from, to, q0,
                        progress = { p -> layerScope.launch { if (wx.pinTrackingId != null) es.message = "Planar tracking… $p% — tap again to stop" } },
                        cancelled = { wx.pinStop })
                } catch (_: Throwable) { emptyList() }
            }
            wx.pinTrackingId = null
            val i = layers.indexOfFirst { it.id == id }
            if (i < 0 || path.size < 2) { es.message = "Couldn't follow that surface — pick one with some detail, or move the corners onto it"; return@launch }
            val l = layers[i]
            val end = path.last().t
            // Keep what was before / after the tracked stretch.
            val old = l.pin.orEmpty().let { if (it.size == 1) emptyList() else it }
            val kept = old.filter { it.t < from - 20 || it.t > end + 20 }
            layers[i] = l.copy(pin = (kept + path).sortedBy { it.t })
            es.message = if (end < to - 250) "Tracked to ${tc(end)} — the surface was lost there (${path.size} frames)"
            else "Planar tracked ${path.size} frames — the layer stays on the surface"
        }
    }

    // ---- Motion tracking: the selected layer follows the spot under it from the playhead on
    fun trackLayer(id: String) {
        if (es.trackingId != null) { es.trackStop = true; return }
        val l0 = layers.firstOrNull { it.id == id } ?: return
        if (clips.isEmpty()) return
        deck.cur.pause()
        val from = globalMs
        val starts = clipStarts(clips)
        val ci = starts.indices.lastOrNull { starts[it] <= from } ?: 0
        val to = if (l0.wholeVideo) timelineLength(clips) else l0.endMs
        if (to - from < 300) { es.message = "Move the playhead to where tracking should start"; return }
        val at = l0.animAt(from)
        es.trackingId = id; es.trackStop = false; es.message = "Tracking… 0%"
        val snapshot = clips.toList()
        layerScope.launch {
            val path = withContext(Dispatchers.Default) {
                Tracker.track(context, snapshot, ci, from, to, at.x, at.y,
                    progress = { p -> layerScope.launch { if (es.trackingId != null) es.message = "Tracking… $p% — tap TRACK again to stop" } },
                    cancelled = { es.trackStop })
            }
            es.trackingId = null
            val i = layers.indexOfFirst { it.id == id }
            if (i < 0 || path.size < 2) { es.message = "Couldn't follow that spot — try a sharper, high-contrast area"; return@launch }
            val l = layers[i]
            val end = path.last().first
            // Keep keyframes before and after the tracked stretch; one key every 200 ms in between.
            val kept = l.keys.orEmpty().filter { it.t < from - 60 || it.t > end + 60 }
            val newKeys = path.filterIndexed { k, _ -> k % 2 == 0 || k == path.lastIndex }.map { (t, x, y) ->
                val cur = l.animAt(t); LayerKey(t, x, y, cur.size, cur.opacity)
            }
            layers[i] = l.copy(keys = (kept + newKeys).sortedBy { it.t })
            es.message = if (end < to - 250) "Tracked to ${tc(end)} — the spot was lost there (${newKeys.size} keyframes)"
            else "Tracked ${newKeys.size} keyframes — the layer now follows the motion"
        }
    }

    // ---- AI captions
    /** Split-screen layouts and picture in picture from the video layers above the main video. */
    fun splitScreen(t: Int) {
        val aspect = if (es.deliver.aspect > 0f) es.deliver.aspect else clips.firstOrNull()?.let { it.width.toFloat() / it.height.coerceAtLeast(1) } ?: (16f / 9f)
        val vids = layers.indices.filter { layers[it].kind == LayerKind.VIDEO && !layers[it].isBelow }
        val g = 0.012f
        // Cells: centre x, centre y, width share, height share.
        val cells: List<FloatArray> = when (t) {
            0 -> { val w = (1f - 3 * g) / 2; listOf(floatArrayOf(g + w / 2, 0.5f, w, 1f - 2 * g), floatArrayOf(1f - g - w / 2, 0.5f, w, 1f - 2 * g)) }
            1 -> { val h = (1f - 3 * g) / 2; listOf(floatArrayOf(0.5f, g + h / 2, 1f - 2 * g, h), floatArrayOf(0.5f, 1f - g - h / 2, 1f - 2 * g, h)) }
            2 -> { val w = (1f - 4 * g) / 3; (0..2).map { floatArrayOf(g + w / 2 + it * (w + g), 0.5f, w, 1f - 2 * g) } }
            3 -> { val w = (1f - 3 * g) / 2; val h = (1f - 3 * g) / 2; listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1).map { (cx, cy) -> floatArrayOf(g + w / 2 + cx * (w + g), g + h / 2 + cy * (h + g), w, h) } }
            else -> emptyList()
        }
        if (t == 4) {
            val i = vids.firstOrNull() ?: run { es.message = "Add a video layer first (+ VIDEO → Above)"; return }
            val l = layers[i]
            val size = 0.32f
            val hw = size * l.mediaAspect / aspect / 2f
            layers[i] = l.copy(size = size, cellAspect = null, x = 1f - 0.035f - hw, y = 1f - 0.06f - size / 2f, keys = null, corner = 0.08f, border = 0.004f, borderColor = 0xFFFFFFFF.toInt(), dropShadow = true)
            es.selectedLayer = l.id
            es.message = "Picture in picture: bottom right with rounded corners, border and shadow — drag it anywhere"
            return
        }
        if (vids.size < cells.size) { es.message = "This layout needs ${cells.size} video layers — add ${cells.size - vids.size} more with + VIDEO (Above)"; return }
        vids.take(cells.size).forEachIndexed { k, i ->
            val c = cells[k]
            layers[i] = layers[i].copy(x = c[0], y = c[1], size = c[3], cellAspect = c[2] / c[3] * aspect, keys = null, corner = null, border = null, dropShadow = null)
        }
        es.message = "Split screen ready (${cells.size} cells) — the thin gaps show the main video; select a layer to change its frame"
    }

    fun makeCaptions(lang: String?, clipsOnly: Boolean, style: Int) {
        com.dronedirector.veyrocutpro.camera.CloudAi.load(context)
        if (!com.dronedirector.veyrocutpro.camera.CloudAi.hasKey) { es.message = "AI captions use your fal.ai account — add the key in settings → Cloud AI"; return }
        if (clips.isEmpty()) return
        deck.cur.pause()
        val file = java.io.File(context.cacheDir, "captions_${System.currentTimeMillis()}.m4a")
        es.message = "Preparing the sound…"
        val total = timelineLength(clips)
        val e = TimelineExport(
            context, clips.toList(), es.deliver.copy(audioOnly = true, rangeStart = null, rangeEnd = null), emptyList(),
            if (clipsOnly) null else es.music, if (clipsOnly) emptyList() else extraAudio.toList(),
            onProgress = {},
            onDone = { u, err ->
                if (u == null) es.message = err ?: "Couldn't prepare the sound"
                else layerScope.launch {
                    val segs = try { withContext(Dispatchers.IO) { com.dronedirector.veyrocutpro.camera.CloudAi.transcribe(context, file, lang) } }
                    catch (_: com.dronedirector.veyrocutpro.camera.CloudAi.Stopped) { es.message = "Captions stopped"; null }
                    catch (t: Throwable) { es.message = t.message ?: "Captions failed"; null }
                    file.delete()
                    if (segs != null) {
                        var n = 0
                        for ((a, b, text) in segs) {
                            // Long sentences become several captions of at most two short lines.
                            val pieces = captionPieces(text, 38)
                            val span = (b - a).coerceAtLeast(400) / pieces.size
                            pieces.forEachIndexed { k, p ->
                                val s0 = (a + k * span).coerceAtMost(total); val s1 = (a + (k + 1) * span).coerceAtMost(total)
                                if (s1 - s0 < 150) return@forEachIndexed
                                layers.add(Layer(
                                    kind = LayerKind.TEXT, text = p, size = if (style == 2) 0.062f else 0.046f, x = 0.5f, y = 0.84f,
                                    box = style == 0, shadow = true, font = if (style == 2) LayerFont.CONDENSED else LayerFont.SANS,
                                    startMs = s0, endMs = s1, name = "Caption",
                                ))
                                n++
                            }
                        }
                        es.message = if (n > 0) "$n captions added — tap one on the timeline to edit it" else "No speech found"
                    }
                }
            },
            toFile = file,
        )
        try { e.start() } catch (t: Throwable) { es.message = "Couldn't prepare the sound" }
    }

    // ---- Auto-reframe: follow the subject when changing the shape of the video.
    fun reframeTrack(c: Clip, aspect: Float): List<Float>? {
        val videoAspect = c.width * (c.desqueeze ?: 1f) / c.height.coerceAtLeast(1)
        val cw = (aspect / videoAspect).coerceAtMost(1f)
        if (cw > 0.97f) return null
        val maxShift = (1f - cw) / 2f
        val mmr = android.media.MediaMetadataRetriever()
        try {
            mmr.setDataSource(context, c.uri)
            val n = ((c.endMs - c.startMs) / 500).toInt().coerceIn(2, 120)
            var prev: IntArray? = null
            val xs = FloatArray(n)
            var last = 0.5f
            for (i in 0 until n) {
                val t = c.startMs + (c.endMs - c.startMs) * i / (n - 1)
                val f = (if (android.os.Build.VERSION.SDK_INT >= 27) mmr.getScaledFrameAtTime(t * 1000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 320, (320 / videoAspect).toInt().coerceAtLeast(16))
                    else mmr.getFrameAtTime(t * 1000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)) ?: continue
                val fb = if (f.config == Bitmap.Config.ARGB_8888) f else f.copy(Bitmap.Config.ARGB_8888, false)
                var cx = AiSegment.subjectCenter(context, fb)
                // No AI subject: follow where things move.
                val px = IntArray(fb.width * fb.height).also { fb.getPixels(it, 0, fb.width, 0, 0, fb.width, fb.height) }
                if (cx == null) prev?.takeIf { it.size == px.size }?.let { p ->
                    var sw = 0f; var sx = 0f
                    for (k in px.indices) {
                        val d = kotlin.math.abs((px[k] shr 8 and 0xFF) - (p[k] shr 8 and 0xFF)).toFloat()
                        if (d > 18) { sw += d; sx += d * (k % fb.width) }
                    }
                    if (sw > px.size * 0.5f) cx = sx / sw / fb.width
                }
                prev = px
                last = cx ?: last
                xs[i] = last
            }
            // Smooth, so the camera glides instead of jumping.
            val sm = FloatArray(n) { i -> var s = 0f; var k = 0; for (j in (i - 3).coerceAtLeast(0)..(i + 3).coerceAtMost(n - 1)) { s += xs[j]; k++ }; s / k }
            return sm.map { (0.5f - it).coerceIn(-maxShift, maxShift) }
        } catch (_: Throwable) { return null } finally { mmr.release() }
    }

    fun autoReframe(aspect: Float?) {
        if (es.videoLocked) { es.message = "The video track is locked"; return }
        if (aspect == null) {
            for (i in clips.indices) clips[i] = clips[i].copy(reframe = clips[i].reframe.copy(track = null))
            es.message = "Auto-reframe cleared"; return
        }
        es.deliver = es.deliver.copy(aspect = aspect, fill = true)
        deck.cur.pause()
        layerScope.launch {
            val list = clips.toList()
            for ((i, c) in list.withIndex()) {
                es.message = "Auto-reframe: clip ${i + 1} of ${list.size}…"
                val tr = withContext(Dispatchers.IO) { reframeTrack(c, aspect) } ?: continue
                val idx = clips.indexOfFirst { it.id == c.id }
                if (idx >= 0) clips[idx] = clips[idx].copy(reframe = clips[idx].reframe.copy(track = tr))
            }
            es.message = "Auto-reframe done — the picture follows the subject" + if (!AiSegment.hasModel(context)) " (motion)" else ""
        }
    }

    // ---- Optical flow (on the phone): smooth slow motion and motion blur.
    fun flowRetime(i: Int, factor: Int, speed: Float?, shutter: Float) {
        if (es.videoLocked) { es.message = "The video track is locked"; return }
        val c = clips.getOrNull(i) ?: return
        if (wx.flowJob != null) return
        if (!MorphNative.available) { es.message = "Optical flow isn't available on this PC"; return }
        if (c.nested != null) { es.message = "Un-nest the clip first (or export it), then smooth it"; return }
        deck.cur.pause()
        wx.flowProgress = 0
        wx.flowJob = layerScope.launch {
            val side = if (es.deliver.shortSide in 1..720) 1280 else 1920
            val f = withContext(Dispatchers.Default) {
                try { FlowRetime.make(context, c, factor, shutter, side, { wx.flowProgress = it }, { !isActive }) } catch (_: Throwable) { null }
            }
            wx.flowProgress = -1; wx.flowJob = null
            if (!isActive) return@launch
            val idx = clips.indexOfFirst { it.id == c.id }
            if (f == null || idx < 0) { es.message = "Couldn't process this clip"; return@launch }
            val len = c.endMs - c.startMs
            val cur = clips[idx]
            clips[idx] = cur.copy(
                uri = android.net.Uri.fromFile(f), name = (if (factor > 1) "Smooth · " else "Blur · ") + cur.name.removePrefix("Smooth · ").removePrefix("Blur · "),
                durationMs = len, startMs = 0, endMs = len,
                speed = speed ?: cur.speed, ramp = if (speed != null) null else cur.ramp, rampName = if (speed != null) null else cur.rampName,
                stab = Stabilize(), mime = "video/avc", hdr = false, tenBit = false, chroma = null, bitDepth = null, decoder = null, undecodable = null,
                bg = cur.bg?.takeIf { it.magic == null },
            )
            load(idx.coerceAtMost(clips.lastIndex), 0, false)
            es.message = if (factor > 1) "Smooth slow motion: ${factor}× the frames, drawn from the motion — undo brings the original back"
            else "Motion blur added — undo brings the original back"
        }
    }

    // ---- AI slow motion (frame interpolation in the cloud).
    fun aiSlowMo(i: Int) {
        if (es.videoLocked) { es.message = "The video track is locked"; return }
        val c = clips.getOrNull(i) ?: return
        com.dronedirector.veyrocutpro.camera.CloudAi.load(context)
        if (!com.dronedirector.veyrocutpro.camera.CloudAi.hasKey) { es.message = "AI slow-mo uses your fal.ai account — add the key in settings → Cloud AI"; return }
        if (c.endMs - c.startMs > 20_000) { es.message = "Trim the clip to 20 s or less for AI slow-mo"; return }
        deck.cur.pause()
        layerScope.launch {
            try {
                val trimmed = CloudVideoPrep.trim(context, c)
                val dir = java.io.File(context.filesDir, "edits").apply { mkdirs() }
                val out = java.io.File(dir, "slowmo_${System.currentTimeMillis()}.mp4")
                try { withContext(Dispatchers.IO) { com.dronedirector.veyrocutpro.camera.CloudAi.interpolate(context, trimmed, out, 3) } } finally { trimmed.delete() }
                val dur = withContext(Dispatchers.IO) {
                    android.media.MediaMetadataRetriever().let { m -> m.setDataSource(out.path); val d = m.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L; m.release(); d }
                }
                val idx = clips.indexOfFirst { it.id == c.id }
                if (idx < 0 || dur <= 0) { es.message = "AI slow-mo failed"; return@launch }
                clips[idx] = c.copy(
                    id = java.util.UUID.randomUUID().toString(), uri = android.net.Uri.fromFile(out), name = "Slow-mo · ${c.name}",
                    durationMs = dur, startMs = 0, endMs = dur, speed = 0.25f, ramp = null, rampName = null, volKeys = null, muted = true,
                    stab = Stabilize(), mime = "video/avc", hdr = false, tenBit = false,
                )
                load(idx.coerceAtMost(clips.lastIndex), 0, false)
                es.message = "AI slow motion: 4× more frames, playing at ¼ speed"
            } catch (_: com.dronedirector.veyrocutpro.camera.CloudAi.Stopped) { es.message = "AI slow-mo stopped" }
            catch (t: Throwable) { es.message = t.message ?: "AI slow-mo failed" }
        }
    }

    /** Freeze frame: 2 s of the frame under the playhead, inserted there (the clip is split around it). */
    fun freezeAtPlayhead() {
        if (es.videoLocked) { es.message = "The video track is locked"; return }
        val c = clips.getOrNull(es.current) ?: return
        val src = c.startMs + deck.cur.currentPosition
        deck.cur.pause()
        es.message = "Making a freeze frame…"
        layerScope.launch {
            val fc = withContext(Dispatchers.IO) { try { VideoTools.freeze(context, c, src, 2000) } catch (_: Throwable) { null } }
            if (fc == null) { es.message = "Couldn't make a freeze frame"; return@launch }
            val idx = clips.indexOfFirst { it.id == c.id }
            if (idx < 0) return@launch
            if (src > c.startMs + 100 && src < c.endMs - 100) {
                clips[idx] = c.copy(endMs = src, transition = Transition())
                clips.add(idx + 1, fc.copy(transition = Transition()))
                clips.add(idx + 2, c.copy(id = java.util.UUID.randomUUID().toString(), startMs = src))
            } else clips.add(idx + 1, fc)
            es.selected = idx + 1
            es.message = "2 s freeze frame added — trim it on the Edit page"
        }
    }

    /** Reverse: replaces the clip with a backwards copy (picture only). */
    fun reverseClip(i: Int, boomerang: Boolean = false) {
        if (es.videoLocked) { es.message = "The video track is locked"; return }
        val c = clips.getOrNull(i) ?: return
        deck.cur.pause()
        es.message = "Reversing… 0%"
        layerScope.launch {
            val rc = withContext(Dispatchers.IO) {
                try { VideoTools.reverse(context, c) { p -> layerScope.launch { es.message = "Reversing… $p%" } } } catch (_: Throwable) { null }
            }
            val idx = clips.indexOfFirst { it.id == c.id }
            if (rc == null || idx < 0) { es.message = "Couldn't reverse this clip"; return@launch }
            if (boomerang) {
                // Forwards, then the same moment backwards (with the same look), right after it.
                clips.add(idx + 1, rc.copy(id = java.util.UUID.randomUUID().toString(), grade = c.grade, detail = c.detail, film = c.film, muted = true))
                load(idx, 0, false)
                es.message = "Boomerang made: the clip plays forwards, then backwards"
            } else {
                clips[idx] = rc.copy(transition = c.transition)
                load(idx.coerceAtMost(clips.lastIndex), 0, false)
                es.message = if (c.endMs - c.startMs > 60_000) "Reversed (first 60 s) — reversed clips are silent" else "Reversed — reversed clips are silent"
            }
        }
    }

    /** Detach audio: the clip's sound becomes its own audio track (move / trim it for J- and L-cuts). */
    /** Cuts the pauses out of one clip (or every clip), using the clip's sound. */
    // ---- Text-based editing: transcribe on the phone, cut the video by deleting words.
    fun downloadModel() {
        if (wx.tDl >= 0) return
        wx.tDl = 0; wx.tDlCancel = false
        layerScope.launch {
            val m = wx.tModel
            val err = withContext(Dispatchers.IO) { Transcriber.download(context, m, { p -> wx.tDl = p }, { wx.tDlCancel }) }
            wx.tDl = -1
            es.message = err ?: "${m.label} speech model ready"
        }
    }
    fun transcribeAll() {
        if (wx.tJob != null || clips.isEmpty()) return
        if (!Transcriber.available) { es.message = "Transcription isn't available on this PC"; return }
        if (!Transcriber.hasModel(context, wx.tModel)) { downloadModel(); es.message = "Downloading the speech model first (${wx.tModel.sizeMb} MB)…"; return }
        wx.tJob = layerScope.launch {
            val list = clips.toList()
            val all = mutableListOf<TWord>()
            for ((i, c) in list.withIndex()) {
                if (c.muted) continue
                wx.tStatus = "Listening to clip ${i + 1} of ${list.size}…"
                val poll = launch { while (true) { delay(400); wx.tStatus = "Listening to clip ${i + 1} of ${list.size} · ${Transcriber.progress()}%" } }
                val w = withContext(Dispatchers.Default) { try { Transcriber.transcribe(context, c, wx.tModel, wx.tLang) } catch (_: Throwable) { null } }
                poll.cancel()
                if (Transcriber.stopped) break
                if (w != null) all += w
            }
            if (!Transcriber.stopped) {
                wx.tWords.clear(); wx.tWords.addAll(all)
                es.message = if (all.isEmpty()) "No speech found" else "Transcribed ${all.size} words — tap words to cut them"
            } else es.message = "Transcription stopped"
            wx.tStatus = null; wx.tJob = null
        }
    }
    /** Word-by-word caption layers from the transcript (style: 0 highlight, 1 box, 2 one word, 3 two lines). */
    fun makeWordCaptions(style: Int) {
        if (wx.tWords.isEmpty()) {
            es.pendingWordCaps = style
            transcribeAll()
            return
        }
        val starts = clipStarts(clips)
        // Every kept word on the timeline, in order.
        val tl = ArrayList<Triple<Long, Long, String>>()
        for ((ci, c) in clips.withIndex()) for (w in wx.tWords.filter { it.clipId == c.id && !it.deleted }.sortedBy { it.startMs }) {
            if (w.startMs < c.startMs || w.startMs >= c.endMs || w.text.isBlank()) continue
            val a = starts[ci] + c.tlOffsetOf(w.startMs); val b = starts[ci] + c.tlOffsetOf(w.endMs.coerceAtMost(c.endMs))
            tl += Triple(a, b.coerceAtLeast(a + 80), w.text.trim().replace(' ', '\u00A0'))
        }
        if (tl.isEmpty()) { es.message = "No speech found to caption"; return }
        val maxWords = when (style) { 2 -> 1; 3 -> 8; else -> 4 }
        val maxChars = when (style) { 2 -> 40; 3 -> 44; else -> 24 }
        val groups = ArrayList<List<Triple<Long, Long, String>>>()
        var cur = ArrayList<Triple<Long, Long, String>>()
        for (w in tl) {
            val chars = cur.sumOf { it.third.length + 1 } + w.third.length
            val gap = cur.lastOrNull()?.let { w.first - it.second } ?: 0L
            if (cur.isNotEmpty() && (cur.size >= maxWords || chars > maxChars || gap > 700 || cur.last().third.endsWith(".") || cur.last().third.endsWith("?") || cur.last().third.endsWith("!"))) { groups += cur; cur = ArrayList() }
            cur += w
        }
        if (cur.isNotEmpty()) groups += cur
        val total = timelineLength(clips)
        layers.removeAll { it.name == "Word caption" }
        groups.forEachIndexed { gi, g ->
            val next = groups.getOrNull(gi + 1)?.first()?.first ?: total
            val s0 = g.first().first.coerceIn(0, total)
            val s1 = (g.last().second + 300).coerceAtMost(next).coerceAtMost(total).coerceAtLeast(s0 + 150)
            var text = g.joinToString(" ") { it.third }
            if (style == 3 && g.size > 3) { val half = (g.size + 1) / 2; text = g.take(half).joinToString(" ") { it.third } + "\n" + g.drop(half).joinToString(" ") { it.third } }
            layers.add(Layer(
                kind = LayerKind.TEXT, text = if (style == 2) text.uppercase() else text,
                size = if (style == 2) 0.085f else 0.055f, x = 0.5f, y = if (style == 2) 0.62f else 0.78f,
                font = LayerFont.CONDENSED, shadow = true, startMs = s0, endMs = s1, name = "Word caption",
                words = g.map { it.first }, hiColor = 0xFFFFD60A.toInt(), hiBox = style == 1,
                animIn = if (style == 2) LayerAnim.POP else null, animMs = if (style == 2) 120L else null,
            ))
        }
        es.message = "${groups.size} word-by-word captions added — edit any of them like a text layer (run again to replace them)"
    }
    LaunchedEffect(wx.tJob) {
        val st = es.pendingWordCaps
        if (wx.tJob == null && st != null) { es.pendingWordCaps = null; if (wx.tWords.isNotEmpty()) makeWordCaptions(st) }
    }
    fun applyTextCuts() {
        if (es.videoLocked) { es.message = "The video track is locked — tap its lock on the timeline to unlock"; return }
        if (wx.tWords.none { it.deleted }) { es.message = "Tap the words you want to cut first"; return }
        val out = mutableListOf<Clip>(); val newWords = mutableListOf<TWord>()
        var cutMs = 0L; var skipped = 0
        for (c in clips.toList()) {
            val ranges = Transcriber.cutRanges(c, wx.tWords).sortedBy { it.first }
            val mine = wx.tWords.filter { it.clipId == c.id }
            if (ranges.isEmpty()) { out += c; newWords += mine; continue }
            if (c.ramp != null) { skipped++; out += c; newWords += mine.map { it.copy(deleted = false) }; continue }
            val keeps = mutableListOf<LongRange>()
            var cur = c.startMs
            for (r in ranges) { if (r.first > cur + 60) keeps += cur..r.first; cur = maxOf(cur, r.last + 1) }
            if (c.endMs > cur + 60) keeps += cur..c.endMs
            cutMs += (c.endMs - c.startMs) - keeps.sumOf { it.last - it.first }
            keeps.forEachIndexed { k, r ->
                val id = if (k == 0) c.id else java.util.UUID.randomUUID().toString()
                val shift = r.first - c.startMs
                out += c.copy(
                    id = id, startMs = r.first, endMs = r.last,
                    transition = if (k == keeps.lastIndex) c.transition else Transition(),
                    volKeys = null,
                    reframe = c.reframe.copy(keys = c.reframe.keys?.map { it.copy(t = it.t - shift) }?.filter { it.t >= 0 && it.t <= r.last - r.first }?.takeIf { it.isNotEmpty() }),
                )
                newWords += mine.filter { !it.deleted && it.startMs >= r.first - 30 && it.endMs <= r.last + 30 }.map { it.copy(clipId = id) }
            }
        }
        if (out.isEmpty()) { es.message = "That would cut everything — keep at least one word"; return }
        clips.clear(); clips.addAll(out)
        wx.tWords.clear(); wx.tWords.addAll(newWords)
        es.selected = es.selected.coerceIn(0, clips.lastIndex); es.current = es.current.coerceIn(0, clips.lastIndex)
        load(es.current, 0, false)
        es.message = "Cut %.1f s from the edit".format(cutMs / 1000f) + (if (skipped > 0) " · $skipped clip(s) with a speed ramp skipped" else "") + " — undo brings it back"
    }

    // ---- Pro tools: scene detection, auto-edit to the beat, fit music, multi-select, versions.
    val multi = remember { mutableStateListOf<String>() }      // clip ids picked in SELECT CLIPS
    fun detectScenes() {
        if (es.videoLocked) { es.message = "The video track is locked"; return }
        val c = clips.getOrNull(es.selected) ?: return
        if (es.sceneJob != null) return
        es.sceneProgress = 0
        var stop = false
        es.sceneJob = layerScope.launch {
            val src = proxyFor(c) ?: c.uri
            val cuts = withContext(Dispatchers.Default) {
                try { ProTools.detectScenes(context, c, src, onProgress = { es.sceneProgress = it }, stop = { stop || !isActive }) } catch (_: Throwable) { emptyList() }
            }
            es.sceneProgress = -1; es.sceneJob = null
            if (!isActive) return@launch
            if (cuts.isEmpty()) { es.message = "No scene changes found in this clip"; return@launch }
            val i = clips.indexOfFirst { it.id == c.id }
            if (i < 0) return@launch
            val parts = ProTools.splitAt(clips[i], cuts)
            clips.removeAt(i); clips.addAll(i, parts)
            load(es.current.coerceIn(0, clips.lastIndex), 0, false)
            es.message = "Split into ${parts.size} shots at the scene changes — undo brings the clip back"
        }
    }
    // ---- Shot match (Color → FILM & LENS → SHOT MATCH)
    fun matchShot(ref: Int) {
        val ti = es.selected
        val t = clips.getOrNull(ti) ?: return
        val r = clips.getOrNull(ref) ?: return
        if (ref == ti || es.matchBusy != null) return
        es.matchBusy = 0
        layerScope.launch {
            val g = withContext(Dispatchers.Default) { try { ShotMatch.match(context, r, t) { es.matchBusy = it } } catch (_: Throwable) { null } }
            es.matchBusy = null
            val i = clips.indexOfFirst { it.id == t.id }
            if (g == null || i < 0) { es.message = "Couldn't read the clips to match"; return@launch }
            clips[i] = clips[i].copy(grade = g)
            es.message = "Clip ${i + 1} matched to clip ${ref + 1} — fine-tune in Primaries; undo brings the old grade back"
        }
    }
    // ---- Deleting a clip (bin on the selected clip, or Delete in the clip tools): close the gap or leave one.
    /**
     * The main video got [d] ms shorter at [edgeAt]: linked layers, audio and markers after it move up
     * too (never before [floor]). Locked and unlinked tracks stay where they are.
     */
    fun rippleLinked(edgeAt: Long, d: Long, floor: Long) {
        fun moved(t: Long): Long = if (t < edgeAt) t else (t - d).coerceAtLeast(floor)
        for (j in layers.indices) {
            val l = layers[j]
            if (l.wholeVideo || l.locked || l.linked == false || l.startMs < edgeAt) continue
            val len = l.endMs - l.startMs
            val ns = moved(l.startMs)
            layers[j] = l.copy(startMs = ns, endMs = ns + len)
        }
        es.music?.let { m -> if (!m.locked && m.linked != false && m.startMs >= edgeAt) es.music = m.copy(startMs = moved(m.startMs)) }
        for (j in extraAudio.indices) { val a = extraAudio[j]; if (!a.locked && a.linked != false && a.startMs >= edgeAt) extraAudio[j] = a.copy(startMs = moved(a.startMs)) }
        for (j in markers.indices) markers[j] = moved(markers[j])
    }
    /** Moves the other members of [group] by [d] ms (layers and audio). */
    fun moveGroup(group: String, d: Long, except: String) {
        for (j in layers.indices) { val l = layers[j]; if (l.group == group && l.id != except && !l.locked) { val ns = (l.startMs + d).coerceAtLeast(0); layers[j] = l.copy(wholeVideo = false, startMs = ns, endMs = ns + (l.endMs - l.startMs)) } }
        es.music?.let { m -> if (m.group == group && m.id != except && !m.locked) es.music = m.copy(startMs = (m.startMs + d).coerceAtLeast(0)) }
        for (j in extraAudio.indices) { val a = extraAudio[j]; if (a.group == group && a.id != except && !a.locked) extraAudio[j] = a.copy(startMs = (a.startMs + d).coerceAtLeast(0)) }
    }
    fun deleteClip(i: Int, leaveGap: Boolean) {
        if (es.videoLocked) { es.message = "The video track is locked"; return }
        if (i !in clips.indices) return
        val c = clips[i]
        if (!leaveGap) {
            val st0 = clipStarts(clips)[i]
            rippleLinked(st0 + c.lengthMs, c.lengthMs, st0)
            clips.removeAt(i)
            es.selected = es.selected.coerceAtMost(clips.lastIndex).coerceAtLeast(0)
            es.current = es.current.coerceAtMost(clips.lastIndex).coerceAtLeast(0)
            if (clips.isNotEmpty()) load(es.current, 0, false)
            es.message = "Clip deleted — undo brings it back"
            return
        }
        // Leave a gap: the clip becomes black of the same length, so everything after it stays in place.
        val len = c.lengthMs
        val fps = if (es.deliver.fps > 0) es.deliver.fps else 30
        val w = (c.width.takeIf { it > 0 } ?: 1920).coerceAtMost(1920); val h = (c.height.takeIf { it > 0 } ?: 1080).coerceAtMost(1920)
        es.message = "Making the gap…"
        Generators.make(context, GenSpec(GenKind.BLACK, seconds = ((len + 999) / 1000).toInt().coerceAtLeast(1), width = w, height = h, fps = fps), onProgress = {}) { uri, err ->
            if (uri == null) { es.message = err ?: "Couldn't make the gap"; return@make }
            layerScope.launch {
                val nc = withContext(Dispatchers.IO) { ClipLoader.load(context, uri) }
                val at = clips.indexOfFirst { it.id == c.id }
                if (nc == null || at < 0) { es.message = "Couldn't make the gap"; return@launch }
                clips[at] = nc.copy(name = "Gap", startMs = 0L, endMs = len.coerceAtMost(nc.durationMs.takeIf { it > 0 } ?: len))
                if (es.current == at) load(at, 0, false)
                es.message = "Clip deleted, gap left — undo brings it back"
            }
        }
    }
    // ---- After dragging a main-video clip's edge: leave a black gap, or move everything after it (all tracks) by the same amount.
    fun afterTrim(i: Int, before: Clip, edge: Int) {
        if (i !in clips.indices || es.videoLocked) return
        val after = clips[i]
        val d = before.lengthMs - after.lengthMs   // > 0: shorter
        if (d == 0L) return
        val start = clipStarts(clips)[i]
        if (!es.trimRipple && d > 0) {
            if (edge > 0 && i == clips.lastIndex) return   // nothing after the last clip to keep in place
            // Until the black gap is made, the timeline keeps the clips where they were (no jump to the left).
            val holdId = if (edge < 0) after.id else clips.getOrNull(i + 1)?.id
            if (holdId != null) es.gapHold = holdId to d
            val fps = if (es.deliver.fps > 0) es.deliver.fps else 30
            val w = (after.width.takeIf { it > 0 } ?: 1920).coerceAtMost(1920); val h = (after.height.takeIf { it > 0 } ?: 1080).coerceAtMost(1920)
            Generators.make(context, GenSpec(GenKind.BLACK, seconds = ((d + 999) / 1000).toInt().coerceAtLeast(1), width = w, height = h, fps = fps), onProgress = {}) { uri, err ->
                if (uri == null) { es.gapHold = null; es.message = err ?: "Couldn't make the gap"; return@make }
                layerScope.launch {
                    val nc = withContext(Dispatchers.IO) { ClipLoader.load(context, uri) }
                    val at = clips.indexOfFirst { it.id == after.id }
                    es.gapHold = null
                    if (nc == null || at < 0) { es.message = "Couldn't make the gap"; return@launch }
                    clips.add(if (edge < 0) at else at + 1, nc.copy(name = "Gap", startMs = 0L, endMs = d.coerceAtMost(nc.durationMs.takeIf { it > 0 } ?: d)))
                    es.message = "Gap left — the rest stays in place"
                }
            }
            return
        }
        // Everything linked that starts after the trimmed edge moves with the video.
        rippleLinked(if (edge < 0) start else start + before.lengthMs, d, if (edge < 0) start else 0L)
    }
    es.deleteAsk?.let { di -> DeleteClipDialog(di, onClose = { es.deleteAsk = null }) { gap -> es.deleteAsk = null; deleteClip(di, gap) } }
    // ---- Generators (Media → GENERATORS)
    fun makeGenerator(kind: GenKind, c1: Int, c2: Int, seconds: Int) {
        if (es.genBusy != null) return
        if (es.videoLocked) { es.message = "The video track is locked"; return }
        val ref = clips.firstOrNull()
        var w = ref?.width ?: 1920; var h = ref?.height ?: 1080
        if (es.deliver.aspect > 0f) { h = h.coerceAtMost(w); w = (h * es.deliver.aspect).toInt() }
        // Keep generated clips light: at most 4K on the long side.
        val k = (3840f / maxOf(w, h)).coerceAtMost(1f); w = (w * k).toInt(); h = (h * k).toInt()
        val fps = if (es.deliver.fps > 0) es.deliver.fps else 30
        es.genBusy = 0
        es.genCancel = Generators.make(context, GenSpec(kind, c1, c2, seconds, w, h, fps), onProgress = { es.genBusy = it }) { uri, err ->
            es.genCancel = null
            if (uri == null) { es.genBusy = null; if (err != null) es.message = err; return@make }
            layerScope.launch {
                val nc = withContext(Dispatchers.IO) { ClipLoader.load(context, uri) }
                es.genBusy = null
                if (nc == null) { es.message = "Couldn't open the generated clip"; return@launch }
                val at = if (clips.isEmpty()) 0 else (es.selected + 1).coerceIn(0, clips.size)
                clips.add(at, nc.copy(name = kind.label + if (kind == GenKind.COUNTDOWN) "" else " · ${seconds}s"))
                es.selected = at
                if (clips.size == 1) { es.current = 0; load(0, 0, false) }
                es.message = "${kind.label} added after the selected clip"
            }
        }
    }
    // ---- Three-point editing (source viewer), relink, project archive
    // Where the timeline is on screen (window coordinates), for dropping clips dragged with the mouse.
    val tlLeftPad = with(androidx.compose.ui.platform.LocalDensity.current) { 50.dp.toPx() }
    /** Puts [src] on the timeline at the clip boundary nearest the time under the pointer. */
    fun dropAt(src: Clip, windowX: Float) {
        if (es.videoLocked) { es.message = "The video track is locked — tap its lock on the timeline to unlock"; return }
        val ms = zoom.scrollMs + ((windowX - es.tlBounds.left - tlLeftPad) / zoom.pxPerSec * 1000f).toLong()
        val nc = src.copy(id = java.util.UUID.randomUUID().toString(), startMs = 0, endMs = src.durationMs, transition = Transition(), volKeys = null, ramp = null, rampName = null, nested = null, speed = 1f)
        val st = clipStarts(clips)
        var at = clips.size
        for (i in clips.indices) if (ms < st[i] + clips[i].lengthMs / 2) { at = i; break }
        clips.add(at, nc); es.selected = at
        if (clips.size == 1) { es.current = 0; load(0, 0, false) }
        es.message = "${nc.name} placed on the timeline"
    }
    fun threePoint(src: Clip, mode: Int) {
        if (es.videoLocked) { es.message = "The video track is locked — tap its lock on the timeline to unlock"; return }
        val nc = src.copy(id = java.util.UUID.randomUUID().toString(), transition = Transition(), volKeys = null, ramp = null, rampName = null, reframe = Reframe(), nested = null, speed = 1f)
        if (clips.isEmpty() || mode == 2) {
            clips.add(nc); es.selected = clips.lastIndex
            if (clips.size == 1) { es.current = 0; load(0, 0, false) }
            es.message = "Appended to the end of the timeline (${tc(nc.lengthMs)})"; return
        }
        val (i, off) = clipAt(clips, globalMs)
        val c = clips[i]
        val at = when {
            off <= 60 -> i
            off >= c.lengthMs - 60 -> i + 1
            else -> {
                val cut = c.srcAtOffset(off)
                clips[i] = c.copy(endMs = cut, transition = Transition())
                clips.add(i + 1, c.copy(id = java.util.UUID.randomUUID().toString(), startMs = cut, volKeys = null))
                i + 1
            }
        }
        clips.add(at, nc)
        if (mode == 1) {
            // Overwrite: take the new clip's length out of what follows, so the edit keeps its length.
            var remain = nc.lengthMs
            val k = at + 1
            while (remain > 30 && k < clips.size) {
                val d = clips[k]
                if (d.lengthMs <= remain + 30) { remain -= d.lengthMs; clips.removeAt(k) }
                else { clips[k] = d.copy(startMs = d.srcAtOffset(remain), volKeys = null); remain = 0 }
            }
        }
        es.selected = at; es.current = at; load(at, 0, false)
        es.message = (if (mode == 1) "Overwritten" else "Inserted") + " at the playhead (${tc(nc.lengthMs)}) — undo takes it back"
    }
    val pickSource = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) layerScope.launch {
            try { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Throwable) {}
            val c = withContext(Dispatchers.IO) { ClipLoader.load(context, uri)?.let { MediaCatalog.added(context, it) } }
            if (c == null) es.message = "Couldn't open that video" else es.sourceClip = c
        }
    }
    es.sourceClip?.let { sc0 ->
        SourceViewerDialog(sc0, tc(globalMs), onDismiss = { es.sourceClip = null }) { cl, mode -> es.sourceClip = null; threePoint(cl, mode) }
    }
    // Media that can't be opened any more (moved, deleted, access lost): RELINK in the media pool.
    val missing = remember { mutableStateListOf<String>() }
    LaunchedEffect(clips.map { it.uri.toString() }.distinct()) {
        val bad = withContext(Dispatchers.IO) { clips.map { it.uri }.distinctBy { it.toString() }.filter { !ProjectArchive.readable(context, it) }.map { it.toString() } }
        missing.clear(); missing.addAll(bad)
    }
    /** Points every use of the missing file [from] at [uri]. Returns how many clips changed (-1 = couldn't open it). */
    suspend fun relinkTo(from: String, uri: Uri, quiet: Boolean = false): Int {
            val nc = withContext(Dispatchers.IO) { ClipLoader.load(context, uri) } ?: run { if (!quiet) es.message = "Couldn't open that file"; return -1 }
            var n = 0
            for (i in clips.indices) if (clips[i].uri.toString() == from) {
                val o = clips[i]
                val end = o.endMs.coerceAtMost(nc.durationMs)
                clips[i] = o.copy(uri = uri, offline = null, offlineOf = null, name = nc.name, durationMs = nc.durationMs, width = nc.width, height = nc.height, mime = nc.mime, startMs = o.startMs.coerceAtMost((end - 100).coerceAtLeast(0)), endMs = end)
                n++
            }
            for (i in layers.indices) if (layers[i].videoUri?.toString() == from) layers[i] = layers[i].copy(videoUri = uri, videoDurationMs = nc.durationMs)
            for (i in library.indices) if (library[i].uri.toString() == from) library[i] = library[i].copy(uri = uri, offline = null, offlineOf = null, name = nc.name, durationMs = nc.durationMs, width = nc.width, height = nc.height, mime = nc.mime)
            bins.remove(from)?.let { bins[uri.toString()] = it }
            missing.remove(from)
            if (!quiet) {
                if (clips.isNotEmpty()) load(es.current.coerceIn(0, clips.lastIndex), 0, false)
                es.message = "Relinked $n clip(s) to ${nc.name}"
            }
            return n
    }
    val relink = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val from = es.relinkFrom; es.relinkFrom = null
        if (uri == null || from == null) return@rememberLauncherForActivityResult
        layerScope.launch {
            try { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Throwable) {}
            relinkTo(from, uri)
        }
    }
    // Opening a project with files that can't be found: offer to look for them (by name, in the phone's
    // photos and videos — e.g. after they were moved to another folder or card).
    var missingAsked by remember { mutableStateOf<String?>(null) }
    val missingKey = es.projectId ?: "open"
    if (missing.isNotEmpty() && missingAsked != missingKey) AlertDialog(
        onDismissRequest = { missingAsked = missingKey },
        containerColor = Pro.PanelSolid,
        title = { Text("${missing.size} file(s) are missing", color = Color.White) },
        text = { Text("Some clips in this project can't be opened (moved, renamed or on a card that isn't in). FIND THEM looks for files with the same name on the PC and links them again. Or use RELINK in the media pool for each one.", color = Pro.Dim) },
        confirmButton = { TextButton(onClick = {
            missingAsked = missingKey
            layerScope.launch {
                es.message = "Looking for the missing files…"
                var found = 0
                for (from in missing.toList()) {
                    val name = (clips.firstOrNull { it.uri.toString() == from }?.name ?: library.firstOrNull { it.uri.toString() == from }?.name) ?: continue
                    val hit = withContext(Dispatchers.IO) { MissingMedia.findByName(context, name) } ?: continue
                    if (relinkTo(from, hit, quiet = true) > 0) found++
                }
                if (found > 0 && clips.isNotEmpty()) load(es.current.coerceIn(0, clips.lastIndex), 0, false)
                es.message = if (found > 0) "Found and linked $found file(s)" + (if (missing.isNotEmpty()) " · ${missing.size} still missing (RELINK in the media pool)" else "") else "None found by name — use RELINK in the media pool"
            }
        }) { Text("FIND THEM", color = Pro.Amber) } },
        dismissButton = { TextButton(onClick = { missingAsked = missingKey }) { Text("LATER", color = Color.White) } },
    )
    fun applyProject(p: VideoProjectData) {
        // A clip whose original is gone opens from the video catalog's copy, if one was kept.
        clips.clear(); clips.addAll(p.clips.map { try { MediaCatalog.resolve(context, it) } catch (_: Throwable) { it } })
        MediaCatalog.inUse = p.clips.map { it.uri.toString() }.toSet()
        layers.clear(); layers.addAll(p.layers)
        es.music = p.music
        extraAudio.clear(); extraAudio.addAll(p.extraAudio)
        audioPool.clear(); audioPool.addAll(p.audioPool)
        library.clear(); p.library?.let { library.addAll(it) }
        ratings.clear(); p.ratings?.let { ratings.putAll(it) }
        markers.clear(); markers.addAll(p.markers)
        markerNotes.clear(); p.markerNotes?.forEach { (k, v) -> k.toLongOrNull()?.let { markerNotes[it] = v } }
        bins.clear(); p.bins?.let { bins.putAll(it) }
        binNames.clear(); p.binNames?.let { binNames.addAll(it) }
        es.deliver = p.deliver
        if (clips.isNotEmpty()) { es.selected = 0; es.current = 0; load(0, 0, false) }
    }
    val saveArchive = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val p = VideoProjectData(
            id = es.projectId ?: java.util.UUID.randomUUID().toString(), name = es.projectName.ifBlank { "Project" },
            clips = clips.toList(), layers = layers.toList(), music = es.music, deliver = es.deliver,
            extraAudio = extraAudio.toList(), audioPool = audioPool.toList(), library = library.toList(), ratings = ratings.toMap(),
            videoMuted = es.videoMuted, videoLocked = es.videoLocked, videoHidden = es.videoHidden, markers = markers.toList(),
            markerNotes = markerNotes.filterKeys { it in markers }.mapKeys { it.key.toString() },
            bins = bins.toMap().takeIf { it.isNotEmpty() }, binNames = binNames.toList().takeIf { it.isNotEmpty() },
        )
        es.archiveBusy = 0
        layerScope.launch {
            val err = withContext(Dispatchers.IO) {
                try { context.contentResolver.openOutputStream(uri)?.use { o -> ProjectArchive.write(context, p, o, { es.archiveBusy = it }, { false }) } ?: "Couldn't create the file" }
                catch (t: Throwable) { "Couldn't save the archive: ${t.message}" }
            }
            es.archiveBusy = null
            es.message = err ?: "Project archive saved (${clips.size} clips and all their files)"
        }
    }
    val openArchive = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        es.archiveBusy = 0
        layerScope.launch {
            val (p, err) = withContext(Dispatchers.IO) { ProjectArchive.read(context, uri) { es.archiveBusy = it } }
            es.archiveBusy = null
            if (p == null) { es.message = err ?: "Couldn't open the archive"; return@launch }
            val saved = withContext(Dispatchers.IO) { try { ProjectStore.saveVideo(context, p, null) } catch (_: Throwable) { null } }
            applyProject(saved ?: p)
            es.projectId = saved?.id; es.projectName = (saved ?: p).name
            es.message = "Opened \"${(saved ?: p).name}\" from the archive"
        }
    }
    LaunchedEffect(es.archiveAction) {
        when (es.archiveAction) {
            1 -> try { saveArchive.launch("${es.projectName.ifBlank { "AlcorCut project" }}.zip") } catch (_: Throwable) { es.message = "Couldn't open the file picker" }
            2 -> try { openArchive.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) } catch (_: Throwable) { es.message = "Couldn't open the file picker" }
        }
        es.archiveAction = 0
    }
    // ---- Object removal: Magic Mask follows the object, then it is filled from the frames around it (and LaMa).
    fun startErase(t: MagicTarget, sel: MagicSel) {
        val c = clips.firstOrNull { it.id == t.clipId } ?: return
        if (es.eraseJob != null) return
        if (es.videoLocked) { es.message = "The video track is locked"; return }
        deck.cur.pause()
        es.eraseProgress = 0
        es.eraseJob = layerScope.launch {
            var err: String? = null
            if (!ModelStore.has(context, AiModel.INPAINT)) {
                es.eraseStatus = "Downloading the painting model…"
                err = withContext(Dispatchers.IO) { ModelStore.ensure(context, AiModel.INPAINT, { p -> es.eraseProgress = p }, { !isActive }) }
            }
            if (err != null) { es.eraseProgress = null; es.eraseStatus = null; es.eraseJob = null; if (isActive) es.message = err; return@launch }
            es.eraseStatus = "Following the object…"; es.eraseProgress = 0
            val tr = withContext(Dispatchers.Default) {
                try { MagicTracker.track(context, c.uri, c.startMs, c.endMs, sel, { p -> es.eraseProgress = p }, { !isActive }) } catch (_: Throwable) { null }
            }
            if (tr == null || !isActive) { es.eraseProgress = null; es.eraseStatus = null; es.eraseJob = null; if (isActive) es.message = "Magic Mask couldn't follow the object"; return@launch }
            withContext(Dispatchers.IO) { MaskTrack.save(context, sel.id, tr) }
            es.eraseStatus = "Removing the object…"; es.eraseProgress = 0
            val f = withContext(Dispatchers.Default) {
                try { ObjectRemoval.make(context, c, tr, sel.id, { p -> es.eraseProgress = p }, { !isActive }) } catch (_: Throwable) { null }
            }
            es.eraseProgress = null; es.eraseStatus = null; es.eraseJob = null
            if (!isActive) return@launch
            val idx = clips.indexOfFirst { it.id == c.id }
            if (f == null || idx < 0) { es.message = "Couldn't remove the object" + (ObjectRemoval.failed?.let { " ($it)" } ?: ""); return@launch }
            val len = c.endMs - c.startMs
            val cur = clips[idx]
            clips[idx] = cur.copy(
                uri = android.net.Uri.fromFile(f), name = "Erased · " + cur.name.removePrefix("Erased · "),
                durationMs = len, startMs = 0, endMs = len, stab = Stabilize(), mime = "video/avc", hdr = false, tenBit = false,
                chroma = null, bitDepth = null, decoder = null, undecodable = null, bg = cur.bg?.takeIf { it.magic == null }, depth = null,
            )
            load(idx.coerceAtMost(clips.lastIndex), 0, false)
            es.message = "Object removed — undo brings the original back"
        }
    }
    // ---- Magic Mask: pick any object, track it through the clip (Color → Detail → BACKGROUND, or a video layer).
    val magicProgress = remember { mutableStateMapOf<String, Int>() }
    val magicStops = remember { HashMap<String, java.util.concurrent.atomic.AtomicBoolean>() }
    fun startMagic(t: MagicTarget, sel: MagicSel) {
        if (t.remove) { startErase(t, sel); return }
        val key = t.clipId ?: t.layerId ?: return
        magicStops[key]?.set(true)
        val stop = java.util.concurrent.atomic.AtomicBoolean(false); magicStops[key] = stop
        val (from, to) = if (t.clipId != null) {
            val c = clips.firstOrNull { it.id == t.clipId } ?: return
            c.startMs to c.endMs
        } else {
            val l = layers.firstOrNull { it.id == t.layerId } ?: return
            val len = if (l.wholeVideo) timelineLength(clips) else (l.endMs - l.startMs)
            l.videoStartMs to (l.videoStartMs + len).coerceAtMost(l.videoDurationMs.coerceAtLeast(l.videoStartMs + 1))
        }
        magicProgress[key] = 0
        layerScope.launch {
            val tr = withContext(Dispatchers.Default) {
                try { MagicTracker.track(context, t.uri, from, to, sel, { p -> magicProgress[key] = p }, { stop.get() }) } catch (_: Throwable) { null }
            }
            magicProgress.remove(key)
            if (stop.get()) return@launch
            if (tr == null) { es.message = "Magic Mask couldn't follow the object" + (Sam.failed?.let { " ($it)" } ?: ""); return@launch }
            withContext(Dispatchers.IO) { MaskTrack.save(context, sel.id, tr) }
            val done = sel.copy(fromMs = from, toMs = to, frames = tr.times.size)
            if (t.clipId != null) {
                val i = clips.indexOfFirst { it.id == t.clipId }
                if (i >= 0) clips[i] = clips[i].copy(bg = (clips[i].bg ?: BgRemoval(1)).copy(magic = done))
            } else {
                val i = layers.indexOfFirst { it.id == t.layerId }
                if (i >= 0) layers[i] = layers[i].copy(bgRemove = true, layerMagic = done)
            }
            es.message = "Magic Mask follows the object through ${tr.times.size} frames"
        }
    }
    es.magicPick?.let { t -> MagicMaskDialog(t, onDismiss = { es.magicPick = null }) { sel -> es.magicPick = null; startMagic(t, sel) } }
    fun makeHighlights(targetMs: Long, toBeat: Boolean = false) {
        if (es.videoLocked) { es.message = "The video track is locked"; return }
        if (clips.isEmpty() || es.hlJob != null) return
        es.hlProgress = 0
        es.hlJob = layerScope.launch {
            val src = clips.distinctBy { it.id }.toList()
            val out = withContext(Dispatchers.Default) {
                try { Highlights.build(context, src, targetMs, wx.tWords.toList(), { proxyFor(it) ?: it.uri }, { es.hlProgress = it }, { !isActive }) } catch (_: Throwable) { emptyList() }
            }
            es.hlProgress = -1; es.hlJob = null
            if (!isActive) return@launch
            if (out.isEmpty()) { es.message = "Couldn't find highlights — the clips may be too short"; return@launch }
            var reel = out
            val m = es.music
            if (toBeat && m != null) {
                val peaks = waves[m.uri.toString()] ?: withContext(Dispatchers.IO) { Waveforms.load(context, m.uri) }
                if (peaks != null) reel = withContext(Dispatchers.Default) { Highlights.snapToBeats(out, ProTools.beatsOnTimeline(m, peaks)) }
            } else if (toBeat) es.message = "Add a music track to cut on its beat"
            clips.clear(); clips.addAll(reel)
            es.selected = 0; es.current = 0
            load(0, 0, false)
            es.message = "Highlight reel: ${out.size} moments, ${tc(out.sumOf { it.lengthMs })} — undo brings your clips back"
        }
    }
    fun autoEditToBeat(every: Int) {
        if (es.videoLocked) { es.message = "The video track is locked"; return }
        val m = es.music ?: run { es.message = "Add a music track first (Media → Sound libraries or Audio)"; return }
        if (clips.isEmpty()) return
        layerScope.launch {
            val peaks = waves[m.uri.toString()] ?: withContext(Dispatchers.IO) { Waveforms.load(context, m.uri) }
            if (peaks == null) { es.message = "Couldn't read the music"; return@launch }
            val beats = ProTools.beatsOnTimeline(m, peaks)
            val musicLen = if (m.lengthMs > 0) m.lengthMs else m.durationMs - m.offsetMs
            val sources = clips.distinctBy { it.id }.toList()
            val out = withContext(Dispatchers.Default) { ProTools.autoEdit(sources, beats, every, m.startMs + musicLen) }
            if (out.isEmpty()) { es.message = "Couldn't find the beat — try another song"; return@launch }
            clips.clear(); clips.addAll(out)
            es.selected = 0; es.current = 0
            load(0, 0, false)
            es.message = "Auto-edit: ${out.size} cuts on the beat — undo brings your clips back"
        }
    }
    /**
     * Text behind subject: a big title over the selected clip, with a copy of the clip on top that has its
     * background removed (AI) — so the person stands in front of the words.
     */
    fun textBehindSubject() {
        val c = clips.getOrNull(es.selected) ?: run { es.message = "Select a clip on the timeline first"; return }
        val start = clipStarts(clips).getOrNull(es.selected) ?: 0L
        val end = start + c.lengthMs
        val title = Layer(kind = LayerKind.TEXT, text = "TITLE", size = 0.3f, x = 0.5f, y = 0.42f, font = LayerFont.HEAVY, color = 0xFFFFFFFF.toInt(),
            shadow = false, startMs = start, endMs = end, animIn = LayerAnim.FADE, animMs = 500L, name = "Title behind")
        val cut = Layer(kind = LayerKind.VIDEO, text = c.name, videoUri = c.uri, videoDurationMs = c.durationMs, videoStartMs = c.startMs,
            mediaAspect = if (c.height > 0) c.width.toFloat() / c.height else 16f / 9f, size = 1f, x = 0.5f, y = 0.5f,
            startMs = start, endMs = end, bgRemove = true, name = "Subject in front")
        layers.add(title); layers.add(cut)
        es.selectedLayer = title.id; es.page = Page.LAYERS
        es.message = "Title behind the subject added over clip ${es.selected + 1} — change the words in the selected text layer" +
            (if (c.speed != 1f || c.ramp != null || !c.reframe.isIdentity) ". The subject copy plays at normal speed without the crop — best on clips without speed changes or reframing" else "")
    }
    /** Media → EDIT TEMPLATES: cut on the beat (with music), transitions on the cuts, title, ending and overlays. */
    fun applyTemplate(t: EditTemplate) {
        if (es.videoLocked) { es.message = "The video track is locked"; return }
        if (clips.isEmpty()) { es.message = "Add some clips first"; return }
        layerScope.launch {
            val m = es.music
            var cut: List<Clip>? = null
            if (m != null) {
                val peaks = waves[m.uri.toString()] ?: withContext(Dispatchers.IO) { Waveforms.load(context, m.uri) }
                if (peaks != null) {
                    val beats = ProTools.beatsOnTimeline(m, peaks)
                    val musicLen = if (m.lengthMs > 0) m.lengthMs else m.durationMs - m.offsetMs
                    cut = withContext(Dispatchers.Default) { ProTools.autoEdit(clips.distinctBy { it.id }.toList(), beats, t.everyBeats, m.startMs + musicLen) }.takeIf { it.isNotEmpty() }
                }
            }
            val out = EditTemplates.withTransitions(cut ?: clips.toList(), t)
            clips.clear(); clips.addAll(out)
            // Earlier template layers are replaced, your own layers stay.
            layers.removeAll { it.name == "Title" || it.name == "Ending" || (it.kind == LayerKind.FX && (it.fx == FxLayers.BARS || it.fx == FxLayers.RETRO) && it.startMs == 0L) }
            layers.addAll(EditTemplates.layers(t, timelineLength(clips)))
            es.selected = 0; es.current = 0
            load(0, 0, false)
            es.message = "\"${t.name}\" applied" + (if (cut != null) " — ${out.size} cuts on the beat" else " — add music first to cut on the beat") + ". Edit the title in Layers; undo brings your edit back"
        }
    }
    fun fitMusicToVideo() {
        val m = es.music ?: run { es.message = "Add a music track first"; return }
        if (m.locked) { es.message = "The music track is locked"; return }
        layerScope.launch {
            val peaks = waves[m.uri.toString()] ?: withContext(Dispatchers.IO) { Waveforms.load(context, m.uri) }
            if (peaks == null) { es.message = "Couldn't read the music"; return@launch }
            val target = timelineLength(clips) - m.startMs
            val parts = withContext(Dispatchers.Default) { ProTools.fitMusic(m, peaks, target) }
            if (parts == null) { es.message = "The music already fits the video"; return@launch }
            es.music = parts.first()
            extraAudio.addAll(parts.drop(1))
            musicPlayer.setMediaItem(MediaItem.fromUri(m.uri)); musicPlayer.prepare()
            es.message = "Music fitted: it now ends with the video, cut on the beat (${parts.size} pieces) — undo restores it"
        }
    }
    fun restoreVersion(f: java.io.File) {
        val p = ProTools.loadVersion(f) ?: run { es.message = "Couldn't open that version"; return }
        clips.clear(); clips.addAll(p.clips)
        layers.clear(); layers.addAll(p.layers)
        es.music = p.music
        extraAudio.clear(); extraAudio.addAll(p.extraAudio)
        markers.clear(); markers.addAll(p.markers)
        es.deliver = p.deliver
        if (clips.isNotEmpty()) { es.selected = 0; es.current = 0; load(0, 0, false) }
        es.message = "Version from " + java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(f.name.removeSuffix(".json").toLongOrNull() ?: 0L)) + " restored — SAVE to keep it"
    }
    LaunchedEffect(es.pendingRestore) { es.pendingRestore?.let { f -> es.pendingRestore = null; restoreVersion(f) } }
    // Every 10 minutes of editing, keep a version of a saved project (Settings → Version history).
    LaunchedEffect(Unit) {
        while (true) {
            delay(10 * 60_000L)
            val pid = es.projectId ?: continue
            if (clips.isEmpty()) continue
            val p = VideoProjectData(
                id = pid, name = es.projectName, savedAt = System.currentTimeMillis(),
                clips = clips.toList(), layers = layers.toList(), music = es.music, deliver = es.deliver,
                extraAudio = extraAudio.toList(), audioPool = audioPool.toList(), library = library.toList(), ratings = ratings.toMap(),
                videoMuted = es.videoMuted, videoLocked = es.videoLocked, videoHidden = es.videoHidden, markers = markers.toList(),
                markerNotes = markerNotes.filterKeys { it in markers }.mapKeys { it.key.toString() },
                timelines = otherTimelines.toList().takeIf { it.isNotEmpty() }, timelineName = es.timelineName,
                proxyMedia = proxyMedia.toList().takeIf { it.isNotEmpty() },
                bins = bins.toMap().takeIf { it.isNotEmpty() }, binNames = binNames.toList().takeIf { it.isNotEmpty() },
            )
            withContext(Dispatchers.IO) { ProTools.saveVersion(context, p) }
        }
    }

    fun removeSilences(all: Boolean, db: Float, minMs: Long) {
        if (es.videoLocked) { es.message = "The video track is locked — tap its lock on the timeline to unlock"; return }
        if (clips.isEmpty()) return
        val targets = if (all) clips.indices.toList() else listOf(es.selected)
        es.message = "Listening for pauses…"
        layerScope.launch {
            val list = clips.toList()
            var removedMs = 0L; var cuts = 0; var skipped = 0
            val out = mutableListOf<Clip>()
            for ((i, c) in list.withIndex()) {
                if (i !in targets) { out += c; continue }
                if (c.ramp != null) { skipped++; out += c; continue }
                val pk = waves[c.uri.toString()] ?: withContext(Dispatchers.IO) { Waveforms.load(context, c.uri) }
                if (pk == null) { skipped++; out += c; continue }
                val keeps = withContext(Dispatchers.Default) { Silence.keeps(pk, c.startMs, c.endMs, db, minMs) }
                if (keeps.size <= 1 && keeps.firstOrNull() == c.startMs..c.endMs) { out += c; continue }
                val kept = keeps.sumOf { it.last - it.first }
                removedMs += ((c.endMs - c.startMs) - kept)
                cuts += (keeps.size - 1).coerceAtLeast(0) + (if (keeps.isEmpty()) 1 else 0)
                keeps.forEachIndexed { k, r ->
                    val last = k == keeps.lastIndex
                    out += c.copy(
                        id = if (k == 0) c.id else java.util.UUID.randomUUID().toString(),
                        startMs = r.first, endMs = r.last,
                        transition = if (last) c.transition else Transition(),
                        volKeys = null,
                    )
                }
            }
            if (out.isEmpty()) { es.message = "Everything was quiet — nothing was removed"; return@launch }
            clips.clear(); clips.addAll(out)
            es.selected = es.selected.coerceIn(0, clips.lastIndex); es.current = es.current.coerceIn(0, clips.lastIndex)
            load(es.current, 0, false)
            es.message = when {
                removedMs < 100 -> "No pauses found" + if (skipped > 0) " ($skipped clip(s) skipped)" else ""
                else -> "Removed %.1f s of pauses in %d cut(s)".format(removedMs / 1000f, cuts) + if (skipped > 0) " · $skipped clip(s) skipped" else ""
            }
        }
    }

    /** Voice / music separation (Demucs, cloud): the clip's sound as a voice track and a music track. */
    fun separateVoice(i: Int) {
        val c = clips.getOrNull(i) ?: return
        if (c.ramp != null || c.speed != 1f) { es.message = "Set the clip to 1x without a ramp to separate its sound"; return }
        com.dronedirector.veyrocutpro.camera.CloudAi.load(context)
        if (!com.dronedirector.veyrocutpro.camera.CloudAi.hasKey) { es.message = "Voice / music separation uses your fal.ai account — add the key in settings → Cloud AI"; return }
        val st = clipStarts(clips).getOrNull(i) ?: 0L
        wx.sepStatus = "Separating… (tap the cloud bar to stop)"
        layerScope.launch {
            val res = withContext(Dispatchers.IO) {
                try {
                    val dir = java.io.File(context.filesDir, "stems/" + java.util.UUID.randomUUID().toString()).apply { mkdirs() }
                    val sound = StemMix.extractAudio(context, c.uri, dir)
                    val input = sound ?: java.io.File(dir, "clip.mp4").also { f -> context.contentResolver.openInputStream(c.uri)?.use { ins -> f.outputStream().use { ins.copyTo(it) } } }
                    val stems = com.dronedirector.veyrocutpro.camera.CloudAi.separate(context, input, if (sound != null) "audio/mp4" else "video/mp4", dir)
                    input.delete()
                    val music = java.io.File(dir, "music.wav")
                    val parts = listOfNotNull(stems["drums"], stems["bass"], stems["other"])
                    if (parts.isEmpty() || !StemMix.sum(parts, music)) throw IllegalStateException("no music")
                    parts.forEach { it.delete() }
                    stems.getValue("vocals") to music
                } catch (t: Throwable) { t }
            }
            wx.sepStatus = null
            if (res is Throwable) { es.message = res.message?.takeIf { it.isNotBlank() && it != "no music" } ?: "Couldn't separate the sound"; return@launch }
            val pair = res as Pair<*, *>
            val voice = pair.first as java.io.File; val music = pair.second as java.io.File
            fun track(f: java.io.File, name: String) = MusicTrack(
                uri = Uri.fromFile(f), name = name, durationMs = c.durationMs, offsetMs = c.startMs, volume = c.volume.coerceAtMost(1.5f),
                fadeInMs = 0L, fadeOutMs = 0L, duck = 0f, startMs = st, lengthMs = c.endMs - c.startMs,
            )
            extraAudio.add(track(voice, "Voice · ${c.name}"))
            extraAudio.add(track(music, "Music · ${c.name}"))
            val k = clips.indexOfFirst { it.id == c.id }
            if (k >= 0) clips[k] = clips[k].copy(muted = true)
            es.message = "Voice and music are now two audio tracks under the clip — set each one's volume in Audio → More audio tracks"
        }
    }
    fun detachAudio(i: Int) {
        if (es.videoLocked) { es.message = "The video track is locked"; return }
        val c = clips.getOrNull(i) ?: return
        if (c.ramp != null || c.speed != 1f) { es.message = "Set the clip to 1x without a ramp to detach its audio"; return }
        if (c.muted) { es.message = "This clip has no sound to detach"; return }
        val st = clipStarts(clips).getOrNull(i) ?: 0L
        extraAudio.add(MusicTrack(
            uri = c.uri, name = "Audio · ${c.name}", durationMs = c.durationMs, offsetMs = c.startMs, volume = c.volume.coerceAtMost(1.5f),
            fadeInMs = c.audio.fadeInMs, fadeOutMs = c.audio.fadeOutMs, fx = c.audio, duck = 0f, startMs = st, lengthMs = c.endMs - c.startMs,
        ))
        clips[i] = c.copy(muted = true)
        es.message = "Audio detached — hold its bar on the timeline to move or extend it"
    }

    // ---- Undo / redo: every settled change to clips, layers and audio is a step.
    val history = remember { mutableStateListOf<EditSnap>() }
    val snapNow by remember { derivedStateOf { EditSnap(clips.toList(), layers.toList(), es.music, extraAudio.toList()) } }
    LaunchedEffect(snapNow) {
        delay(450)   // a slider drag or a held timeline move becomes one step
        if (es.histPos in history.indices && history[es.histPos] == snapNow) return@LaunchedEffect
        while (history.lastIndex > es.histPos) history.removeAt(history.lastIndex)
        history.add(snapNow)
        if (history.size > 80) history.removeAt(0)
        es.histPos = history.lastIndex
    }
    fun applySnap(s: EditSnap) {
        val wasPlaying = deck.cur.isPlaying
        val pos = deck.cur.currentPosition
        clips.clear(); clips.addAll(s.clips)
        layers.clear(); layers.addAll(s.layers)
        es.music = s.music
        extraAudio.clear(); extraAudio.addAll(s.extra)
        if (es.selectedLayer != null && layers.none { it.id == es.selectedLayer }) es.selectedLayer = null
        if (clips.isNotEmpty()) {
            val i = es.current.coerceIn(0, clips.lastIndex)
            es.selected = es.selected.coerceIn(0, clips.lastIndex)
            val c = clips[i]
            load(i, pos.coerceIn(0, (c.endMs - c.startMs - 50).coerceAtLeast(0)), wasPlaying)
        }
    }
    // ---- Several timelines per project (Media → TIMELINES).
    fun currentTimeline() = SavedTimeline(
        name = es.timelineName, clips = clips.toList(), layers = layers.toList(), music = es.music, extraAudio = extraAudio.toList(),
        markers = markers.toList(), markerNotes = markerNotes.filterKeys { it in markers }.mapKeys { it.key.toString() }.takeIf { it.isNotEmpty() },
        deliver = es.deliver, videoMuted = es.videoMuted, videoHidden = es.videoHidden,
    )
    fun openTimeline(t: SavedTimeline, keepCurrent: Boolean = true) {
        if (keepCurrent) otherTimelines.add(currentTimeline())
        otherTimelines.remove(t)
        deck.cur.pause()
        es.timelineName = t.name
        clips.clear(); clips.addAll(t.clips)
        layers.clear(); layers.addAll(t.layers)
        es.music = t.music
        extraAudio.clear(); extraAudio.addAll(t.extraAudio)
        markers.clear(); markers.addAll(t.markers)
        markerNotes.clear(); t.markerNotes?.forEach { (k, v) -> k.toLongOrNull()?.let { markerNotes[it] = v } }
        es.deliver = t.deliver; es.videoMuted = t.videoMuted; es.videoHidden = t.videoHidden
        es.selectedLayer = null
        history.clear(); es.histPos = -1
        if (clips.isNotEmpty()) { es.selected = 0; es.current = 0; load(0, 0, false) } else deck.cur.clearMediaItems()
        es.message = "Opened \"${t.name}\""
    }
    fun uniqueTimelineName(base: String): String {
        val used = otherTimelines.map { it.name }.toSet() + es.timelineName
        if (base !in used) return base
        var n = 2
        while ("$base $n" in used) n++
        return "$base $n"
    }
    fun undo() {
        if (es.histPos <= 0) { es.message = "Nothing to undo"; return }
        es.histPos--; applySnap(history[es.histPos]); es.message = "Undo"
    }
    fun redo() {
        if (es.histPos >= history.lastIndex) { es.message = "Nothing to redo"; return }
        es.histPos++; applySnap(history[es.histPos]); es.message = "Redo"
    }
    // Panel RESET → UNDO bar goes back to the state recorded just before it.
    DisposableEffect(Unit) {
        com.dronedirector.veyrocutpro.ui.ResetUndo.capture = { val pos = es.histPos; { if (pos in history.indices) { es.histPos = pos; applySnap(history[pos]) } } }
        onDispose { com.dronedirector.veyrocutpro.ui.ResetUndo.capture = null }
    }

    /** Timeline scrubbing: fast keyframe seeks while dragging, exact frame when released. */
    fun scrub(ms: Long, final: Boolean) {
        val now = System.currentTimeMillis()
        if (!final && now - es.lastScrub < 60) return
        es.lastScrub = now
        if (deck.cur.isPlaying && !final) deck.cur.pause()
        deck.cur.setSeekParameters(if (final) androidx.media3.exoplayer.SeekParameters.EXACT else androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
        seekGlobal(ms)
    }

    /** The main video track's switches (sound off, picture hidden) applied to its clips for an export. */
    LaunchedEffect(es.previewMuted) { LayerSound.previewMuted = es.previewMuted }
    remember { TransportBar.load(context); 0 }

    fun trackSwitches(l: List<Clip>): List<Clip> =
        if (!es.videoMuted && !es.videoHidden) l else l.map { it.copy(muted = it.muted || es.videoMuted, hidden = if (es.videoHidden) true else it.hidden) }

    fun runExportNow(list0: List<Clip>, loudGain: Float) {
        // Export range: cut the edit down to the chosen part first.
        val ra = es.deliver.rangeStart; val rb = es.deliver.rangeEnd
        // Review copy: a big faint watermark over everything and each marker's note for 2.5 s.
        val reviewLayers = if (!es.deliver.review || es.deliver.audioOnly) emptyList() else buildList {
            if (es.deliver.reviewText.isNotBlank()) add(Layer(kind = LayerKind.TEXT, text = es.deliver.reviewText.take(40), size = 0.16f, x = 0.5f, y = 0.5f, opacity = 0.28f, shadow = false, wholeVideo = true, name = "Review"))
            if (es.deliver.reviewNotes) for (m in markers) add(Layer(kind = LayerKind.TEXT, text = (markerNotes[m] ?: "Marker").take(120), size = 0.045f, x = 0.5f, y = 0.12f, box = true, startMs = m, endMs = m + 2500, name = "Review"))
        }
        val allLayers = layers.toList() + reviewLayers
        es.deliver = es.deliver.copy(reviewTcStart = if (ra != null && rb != null && rb > ra) ra else 0L)
        val cut = if (ra != null && rb != null && rb > ra) cutRange(list0, allLayers, es.music, extraAudio.toList(), ra, rb)
        else RangeCut(list0, allLayers, es.music, extraAudio.toList())
        if (cut.clips.isEmpty()) { es.exporting = false; es.message = "Nothing to export in that range"; return }
        val g = loudGain
        val list = if (g == 1f) cut.clips else cut.clips.map { it.copy(volume = it.volume * g) }
        val cutMusic = cut.music?.let { if (g == 1f) it else it.copy(volume = it.volume * g) }
        val cutExtra = if (g == 1f) cut.extra else cut.extra.map { it.copy(volume = it.volume * g) }
        // Long films: rendered in parts (a failed part is done again on its own), then joined.
        val toCineForm = es.deliver.cineform in 0..3 && !es.deliver.audioOnly
        val parts = if (toCineForm || es.deliver.audioOnly || smartRenderBlocker(context, list, es.deliver, cut.layers, cutMusic, cutExtra) == null) listOf(0L, 0L)
            else ChunkedExport.bounds(list)
        if (parts.size > 2) {
            val all = trackSwitches(list)
            val (ow, oh) = outputSize(all, es.deliver)
            // Every part the same size as the whole film (the first clip of a part may differ).
            val fixed = es.deliver.copy(aspect = ow.toFloat() / oh, shortSide = minOf(ow, oh), rangeStart = null, rangeEnd = null)
            val base = es.deliver.reviewTcStart
            es.cloudJob = layerScope.launch {
                val n = parts.size - 1
                val files = ArrayList<java.io.File>()
                var failed: String? = null
                for (k in 0 until n) {
                    val a = parts[k]; val b = parts[k + 1]
                    val sub = cutRange(all, cut.layers, cutMusic, cutExtra, a, b)
                    var ok = false
                    for (attempt in 1..2) {
                        val f = java.io.File(context.cacheDir, "part_${k}_${System.currentTimeMillis()}.mp4")
                        es.message = if (attempt == 1) "Exporting part ${k + 1} of $n" else "Part ${k + 1} didn't finish — doing just that part again"
                        ok = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                            val e = TimelineExport(
                                context, sub.clips, fixed.copy(reviewTcStart = base + a), sub.layers, sub.music, sub.extra,
                                onProgress = { p -> es.progress = (k * 100 + p) * 95 / 100 / n },
                                onDone = { uri, err -> if (uri == null) failed = err; if (cont.isActive) cont.resumeWith(Result.success(uri != null)) },
                                toFile = f,
                            )
                            es.export = e
                            try { e.start() } catch (t: Throwable) {
                                failed = t.message ?: t.javaClass.simpleName
                                com.dronedirector.veyrocutpro.ui.CrashReport.problem(context, "export couldn't start (part ${k + 1})", t)
                                if (cont.isActive) cont.resumeWith(Result.success(false))
                            }
                            cont.invokeOnCancellation { try { e.cancel() } catch (_: Throwable) {} }
                        }
                        if (ok) { files += f; break } else f.delete()
                    }
                    if (!ok) break
                }
                es.export = null
                if (files.size != n) {
                    files.forEach { it.delete() }
                    es.exporting = false; es.message = "Export failed in part ${files.size + 1} of $n" + (failed?.let { " — $it" } ?: ""); return@launch
                }
                es.message = "Joining the parts…"; es.progress = 96
                val joined = java.io.File(context.cacheDir, "film_${System.currentTimeMillis()}.mp4")
                val ok = ChunkedExport.join(context, files, joined, es.deliver.bitrateMbps * 1_000_000)
                files.forEach { it.delete() }
                val uri = if (!ok) null else withContext(Dispatchers.IO) {
                    outputSize(clips, es.deliver).let { (ow, oh) -> Spherical.tagIfNeeded(context, clips, ow, oh, joined) }
                    val name = "EDIT_" + java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date()) + ".mp4"
                    (SaveFolder.write(context, true, name, "video/mp4") { os -> joined.inputStream().use { it.copyTo(os) } }
                        ?: GradeIO.publishVideo(context, joined, com.dronedirector.veyrocutpro.camera.StorageArea.VIDEO)).also { joined.delete() }
                }
                if (uri != null) { com.dronedirector.veyrocutpro.ExportService.shareUri = uri; com.dronedirector.veyrocutpro.ExportService.shareMime = "video/*" }
                es.exporting = false; es.progress = 100
                es.message = if (uri != null) "Exported ($n parts joined)" else "Couldn't join the parts"
            }
            return
        }
        // CineForm: render to a temporary file (very high bitrate), then convert it to a CineForm .mov.
        if (toCineForm) {
            val all = trackSwitches(list)
            val (ow, oh) = outputSize(all, es.deliver)
            val tmp = java.io.File(context.cacheDir, "cf_render_${System.currentTimeMillis()}.mp4")
            val quality = es.deliver.cineform; val sound = es.deliver.audio
            val e = TimelineExport(
                context, all, CineForm.intermediate(es.deliver, ow, oh), cut.layers, cutMusic, cutExtra,
                onProgress = { es.progress = it * 45 / 100 },
                onDone = cf@{ uri, err ->
                    es.export = null
                    if (uri == null || !es.exporting) { tmp.delete(); es.exporting = false; es.message = err ?: "Export failed"; return@cf }
                    es.message = "Converting to CineForm…"
                    es.cloudJob = layerScope.launch {
                        val (out, why) = withContext(Dispatchers.Default) {
                            CineForm.convert(context, tmp, quality, sound) { p -> layerScope.launch { es.progress = 45 + p * 55 / 100 } }
                        }
                        tmp.delete()
                        if (out != null) { com.dronedirector.veyrocutpro.ExportService.shareUri = out; com.dronedirector.veyrocutpro.ExportService.shareMime = "video/quicktime" }
                        es.exporting = false; es.progress = 100
                        es.message = if (out != null) "CineForm .mov exported to " + (SaveFolder.get(context, true)?.let { SaveFolder.label(it) } ?: "Videos\\AlcorCut Pro") else why ?: "Export stopped"
                    }
                },
                toFile = tmp,
            )
            es.export = e
            try { e.start() } catch (t: Throwable) {
                es.exporting = false; es.export = null; tmp.delete()
                es.message = "Export failed: ${t.message ?: t.javaClass.simpleName}"
            }
            return
        }
        val e = TimelineExport(
            context, trackSwitches(list), es.deliver, cut.layers, cutMusic, cutExtra,
            onProgress = { es.progress = if (es.deliver.loudness != null && (es.deliver.audio || es.deliver.audioOnly)) 20 + it * 4 / 5 else it },
            onDone = { uri, err ->
                if (uri != null) { com.dronedirector.veyrocutpro.ExportService.shareUri = uri; com.dronedirector.veyrocutpro.ExportService.shareMime = if (es.deliver.audioOnly) "audio/*" else "video/*" }
                es.exporting = false; es.export = null
                es.message = if (uri != null) {
                    val place = com.dronedirector.veyrocutpro.camera.Storage.load(context, com.dronedirector.veyrocutpro.camera.StorageArea.VIDEO)
                    val folder = SaveFolder.get(context, true)?.let { SaveFolder.label(it) }
                        ?: ((if (place == com.dronedirector.veyrocutpro.camera.Storage.INTERNAL) "" else com.dronedirector.veyrocutpro.camera.Storage.label(context, place) + " · ") + if (es.deliver.audioOnly) "Music/AlcorCutPro" else "Videos\\AlcorCut Pro")
                    (if (es.deliver.audioOnly) "Audio exported to " else "Exported to ") + folder
                } else err ?: "Export failed"
            },
        )
        es.export = e
        try { e.start() } catch (t: Throwable) {
            es.exporting = false; es.export = null
            es.message = "Export failed: ${t.message ?: t.javaClass.simpleName}"
            com.dronedirector.veyrocutpro.ui.CrashReport.problem(context, "export couldn't start", t)
        }
    }

    /** Loudness: render the sound once, measure it, then export with every track turned up / down to the target. */
    fun runExport(list0: List<Clip>) {
        val target = es.deliver.loudness
        if (target == null || (!es.deliver.audio && !es.deliver.audioOnly)) { runExportNow(list0, 1f); return }
        es.message = "Measuring loudness…"
        val tmp = java.io.File(context.cacheDir, "loudness_${System.currentTimeMillis()}.m4a")
        val ra = es.deliver.rangeStart; val rb = es.deliver.rangeEnd
        val cut = if (ra != null && rb != null && rb > ra) cutRange(list0, layers.toList(), es.music, extraAudio.toList(), ra, rb)
        else RangeCut(list0, layers.toList(), es.music, extraAudio.toList())
        if (cut.clips.isEmpty()) { runExportNow(list0, 1f); return }
        val probe = TimelineExport(
            context, trackSwitches(cut.clips),
            es.deliver.copy(audioOnly = true, audio = true, loudness = null, rangeStart = null, rangeEnd = null),
            emptyList(), cut.music, cut.extra,
            onProgress = { es.progress = it / 5 },
            onDone = { uri, _ ->
                es.export = null
                layerScope.launch {
                    val lufs = if (uri != null) withContext(Dispatchers.IO) { Loudness.measure(context, android.net.Uri.fromFile(tmp)) } else null
                    tmp.delete()
                    if (!es.exporting) return@launch
                    val g = lufs?.let { Loudness.gainFor(it, target) } ?: 1f
                    es.message = if (lufs != null) "Loudness %.1f LUFS → %.0f LUFS".format(lufs, target) else "Couldn't measure the loudness — exporting as mixed"
                    runExportNow(list0, g)
                }
            },
            toFile = tmp,
        )
        es.export = probe
        try { probe.start() } catch (t: Throwable) { runExportNow(list0, 1f) }
    }

    /** Saves the frame under the playhead (graded, with its text / logo layers) as a photo. */
    fun saveStill() {
        val c = clips.getOrNull(es.current) ?: return
        val src = c.startMs + deck.cur.currentPosition
        val at = globalMs
        val shownLayers = layers.filter { it.activeAt(at) && it.kind != LayerKind.VIDEO }
        es.message = "Saving frame…"
        layerScope.launch {
            val uri = withContext(Dispatchers.IO) {
                try {
                    val f = GradeIO.videoFrame(context, c.uri, src * 1000, 4096) ?: return@withContext null
                    var out = ImageOps.render(f, c.grade, c.detail.copy(aiDenoise = 0f), Geometry(), if (c.grade.isIdentity) null else c.grade.tables())
                    for (l in shownLayers) out = stampLayer(context, out, l)
                    savePicture(context, out, png = false, quality = 95, area = com.dronedirector.veyrocutpro.camera.StorageArea.VIDEO)
                } catch (_: Throwable) { null }
            }
            es.message = if (uri != null) "Frame saved as a photo" else "Couldn't save the frame"
        }
    }

    fun startExport() {
        ExportNotes.clear()
        com.dronedirector.veyrocutpro.ui.AppLog.i("Export started: ${clips.size} clips, ${layers.size} layers, music ${if (es.music != null) "yes" else "no"}, " +
            "${es.deliver.let { d -> (if (d.audioOnly) "audio only" else "video") + ", aspect " + d.aspect }}")
        if (es.exporting || clips.isEmpty()) return
        deck.cur.pause()
        es.exportStartAt = System.currentTimeMillis()
        es.exporting = true
        es.progress = 0
        es.cloudExport = !es.deliver.audioOnly && com.dronedirector.veyrocutpro.camera.CloudAi.useVideo(context) && clips.any { it.detail.aiDenoise > 0f }
        if (!es.cloudExport) {
            // AI noise reduction on the phone: clean each clip once (kept in the video catalog), then export from
            // the cleaned clips — later exports of the same clips skip this step.
            val todo = CleanRender.missing(context, clips.toList(), es.deliver)
            if (todo.isEmpty()) { runExport(clips.toList()); return }
            es.cloudJob = layerScope.launch {
                for ((n, c) in todo.withIndex()) {
                    es.message = "AI noise reduction — clip ${n + 1} of ${todo.size} (done once, reused next time)"
                    val ok = CleanRender.make(context, c) { p -> es.progress = (n * 100 + p) / todo.size }
                    if (!ok) es.message = "AI noise reduction couldn't be prepared for ${c.name} — it runs during the export instead"
                }
                es.progress = 0
                runExport(clips.toList())
            }
            return
        }
        // Cloud AI first (Topaz Nyx on fal.ai), then the normal export with the cleaned clips.
        es.cloudJob = layerScope.launch {
            val list = try {
                CloudVideoPrep.prepare(context, clips.toList())
            } catch (_: com.dronedirector.veyrocutpro.camera.CloudAi.Stopped) {
                es.exporting = false; es.message = "Export stopped"; return@launch
            } catch (_: kotlinx.coroutines.CancellationException) {
                es.exporting = false; return@launch
            } catch (t: Throwable) {
                es.message = "Cloud AI unavailable (${t.message}) — using the PC's AI"
                es.cloudExport = false
                clips.toList()
            } finally {
                com.dronedirector.veyrocutpro.camera.CloudAi.show(null)
            }
            runExport(list)
        }
    }

    // Render queue: start the next export whenever the previous one has finished.
    LaunchedEffect(es.exporting, es.queueAt) {
        if (es.queueAt < 0 || es.exporting) return@LaunchedEffect
        if (es.queueAt >= exportQueue.size) {
            val n = exportQueue.size
            es.queueAt = -1
            es.queueBase?.let { es.deliver = it }
            es.message = "Render queue finished — $n exports"
            return@LaunchedEffect
        }
        es.deliver = exportQueue[es.queueAt]
        es.queueAt += 1
        startExport()
    }
    // Background export: keep running (with a progress notification) when you leave the app.
    LaunchedEffect(es.exporting) {
        if (es.exporting) {
            val what = (if (es.queueAt > 0) "Exporting ${es.queueAt} of ${exportQueue.size}" else if (es.deliver.audioOnly) "Exporting audio" else "Exporting video") +
                (es.projectName.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
            com.dronedirector.veyrocutpro.ExportService.start(context, what)
            es.wasExporting = true
        } else if (es.wasExporting) {
            es.wasExporting = false
            ExportNotes.note()?.let { n -> es.message = (es.message?.let { "$it · " } ?: "") + n; ExportNotes.clear() }
            com.dronedirector.veyrocutpro.ui.AppLog.i("Export ended: ${es.message ?: ""}")
            com.dronedirector.veyrocutpro.ExportService.finish(context, if (es.queueAt in 1 until exportQueue.size) null else es.message)
        }
    }
    LaunchedEffect(es.progress, es.exporting) { if (es.exporting) com.dronedirector.veyrocutpro.ExportService.progress(context, es.progress) }
    // Proxy generation also shows in the background-tasks notification.
    val proxyNow = proxyQueue.firstOrNull()?.let { id -> proxyProgress[id] }
    val proxyLeft = proxyQueue.size
    LaunchedEffect(proxyNow, proxyLeft) {
        if (proxyNow != null) com.dronedirector.veyrocutpro.ExportService.update(context, "proxy", proxyNow,
            "Making proxies" + (if (proxyLeft > 1) " · $proxyLeft left" else ""))
        else if (proxyLeft == 0) com.dronedirector.veyrocutpro.ExportService.end(context, "proxy")
    }
    DisposableEffect(Unit) { onDispose { com.dronedirector.veyrocutpro.ExportService.end(context, "proxy") } }

    // ---- File menu (file icon in the top bar), keyboard shortcuts and bar dialogs: own composable block (keeps this function small).
    val wf = remember { WsFile() }
    val fm = wf.fm
    val fa = wf.fa
    val fileMenuBlock: @Composable () -> Unit = {
        var teamConflict by remember { mutableStateOf<Team.Saved.Newer?>(null) }
        var teamReadOnlyNote by remember { mutableStateOf(false) }
        var closeBusy by remember { mutableStateOf(false) }
        fun workspaceHasContent() = clips.isNotEmpty() || layers.isNotEmpty() || es.music != null || extraAudio.isNotEmpty() || library.isNotEmpty()
        fun teamSave(force: Boolean = false) {
            val s = Team.current ?: return
            if (s.readOnly) { teamReadOnlyNote = true; return }
            val p = currentData().copy(name = s.name)
            Team.stop = false
            layerScope.launch {
                when (val r = withContext(Dispatchers.IO) { Team.save(context, s, p, force) }) {
                    is Team.Saved.Ok -> { es.projectName = s.name; markSaved(); es.message = "Saved to the team folder" }
                    is Team.Saved.Newer -> teamConflict = r
                    is Team.Saved.Failed -> es.message = "Couldn't save to the team folder — ${r.why}"
                }
            }
        }
        fun saveChanges() {
            when {
                Team.current != null -> teamSave()
                es.projectId == null -> es.askName = true
                else -> saveProject(es.projectName)
            }
        }
        /** Saves one open project as it is (team folder or this phone). Blocking. */
        fun saveOpen(op: OpenProject): Boolean {
            val d = (if (op.key == host.active) currentData() else op.data) ?: return true
            val t = op.team
            return if (t != null) {
                Team.save(context, t, d.copy(name = t.name), false) is Team.Saved.Ok
            } else try {
                val saved = ProjectStore.saveVideo(context, d.copy(id = d.id.ifBlank { java.util.UUID.randomUUID().toString() }, name = d.name.ifBlank { ProjectStore.defaultName(true) }), null)
                if (op.key == host.active) { es.projectId = saved.id; es.projectName = saved.name }
                true
            } catch (_: Throwable) { false }
        }
        /** Runs [then] once [targets] are saved or their changes are let go (asks when something is unsaved). */
        fun closeWith(targets: List<OpenProject>, then: () -> Unit) {
            val d = targets.filter { host.dirty(it) }
            if (d.isEmpty()) then() else wf.closeAsk = d to then
        }
        fun leaveTeamProject(quiet: Boolean = false) {
            val t = Team.current ?: return
            kotlin.concurrent.thread { Team.release(context, t) }
            Team.current = null; host.activeProject()?.team = null
            es.projectId = null
            if (!quiet) es.message = "\"${t.name}\" is now a normal project on this PC — SAVE keeps it here"
        }
        // Offline stand-ins: black, light video of the given length.
        fun makeStandIn(seconds: Int, w0: Int, h0: Int, done: (Clip?) -> Unit) {
            val k = 640f / minOf(w0, h0).coerceAtLeast(1)
            val w = (w0 * k).toInt().coerceAtLeast(16); val h = (h0 * k).toInt().coerceAtLeast(16)
            Generators.make(context, GenSpec(GenKind.BLACK, seconds = seconds.coerceIn(1, 600), width = w, height = h, fps = 10), onProgress = {}) { uri, _ ->
                if (uri == null) { done(null); return@make }
                layerScope.launch { done(withContext(Dispatchers.IO) { ClipLoader.load(context, uri) }) }
            }
        }
        val importFilesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { importToLibrary(it) }
        val collectTo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
            if (tree == null) return@rememberLauncherForActivityResult
            try { context.contentResolver.takePersistableUriPermission(tree, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: Throwable) {}
            val p = currentData()
            val folder = (es.projectName.ifBlank { "Project" } + " files").replace(Regex("[\\\\/:*?\"<>|]"), "_")
            Team.stop = false
            layerScope.launch {
                val n = withContext(Dispatchers.IO) {
                    val place = FolderPlace(context, tree)
                    val uris = LinkedHashSet<String>()
                    ProjectArchive.mapUris(p) { u -> if (u != Uri.EMPTY) uris += u.toString(); u }
                    var done = 0; val names = HashSet<String>()
                    try {
                        uris.forEachIndexed { i, us ->
                            if (Team.stop) return@forEachIndexed
                            val u = Uri.parse(us)
                            var name = ProjectArchive.displayName(context, u).replace(Regex("[\\\\/:*?\"<>|]"), "_")
                            var k = 2; val base = name
                            while (!names.add(name)) { name = base.substringBeforeLast('.') + " ($k)." + base.substringAfterLast('.', "mp4"); k++ }
                            Team.busy = "Collecting files ${i + 1} of ${uris.size}…"
                            val total = ProjectArchive.sizeOf(context, u).coerceAtLeast(1)
                            val ok = context.contentResolver.openInputStream(u)?.use { input -> place.upload("$folder/$name", input, { !Team.stop }) { b -> Team.progress = (i + b.toFloat() / total) / uris.size } } ?: false
                            if (ok) done++
                        }
                        place.write("$folder/" + (es.projectName.ifBlank { "Project" }) + ".alcorproject.json", ProjectStore.gson.toJson(p).toByteArray())
                    } catch (_: Throwable) {}
                    Team.busy = null; Team.progress = 0f
                    done to uris.size
                }
                es.message = "Collected ${n.first} of ${n.second} files in \"$folder\""
            }
        }
        fa.apply {
            hasContent = { workspaceHasContent() }
            projectName = { es.projectName }
            isTeam = { Team.current != null }
            teamReadOnly = { Team.current?.readOnly == true }
            teamLabel = { Team.current?.let { it.name + " · " + it.place.label } ?: "" }
            openProjects = { host.open.map { it.key to (if (it.key == host.active) es.projectName else it.name) } }
            activeKey = { host.active }
            switchTo = { k -> deck.cur.pause(); nav.switchTo(k) }
            newProject = { deck.cur.pause(); nav.open(null, null, false) }
            newTeamProject = { place, name ->
                val s = TeamSession(place, name, false, 0)
                Team.stop = false
                layerScope.launch {
                    when (val r = withContext(Dispatchers.IO) { Team.save(context, s, VideoProjectData(name = name), true) }) {
                        is Team.Saved.Ok -> { deck.cur.pause(); nav.open(VideoProjectData(name = name), s, true) }
                        is Team.Saved.Failed -> es.message = "Couldn't create the team project — ${r.why}"
                        else -> {}
                    }
                }
            }
            openProject = { es.showLoad = true }
            openRecent = { id ->
                layerScope.launch {
                    val p = withContext(Dispatchers.IO) { ProjectStore.loadVideo(context, id) }
                    if (p == null) es.message = "Couldn't open that project" else { deck.cur.pause(); nav.open(p, null, true) }
                }
            }
            openTeam = { place, name, ro ->
                Team.stop = false
                layerScope.launch {
                    val (p, s) = withContext(Dispatchers.IO) { Team.open(context, place, name, ro).also { (_, s) -> if (s != null && !ro) Team.writeLock(context, s) } }
                    if (p == null || s == null) es.message = "Couldn't open \"$name\""
                    else { deck.cur.pause(); nav.open(p, s, true) }
                }
            }
            shareAsTeam = { place, name ->
                val s = TeamSession(place, name, false, 0)
                Team.stop = false
                val p = currentData().copy(name = name)
                layerScope.launch {
                    when (val r = withContext(Dispatchers.IO) { Team.save(context, s, p, true) }) {
                        is Team.Saved.Ok -> {
                            Team.current = s; host.activeProject()?.team = s
                            es.projectName = name; es.projectId = null; markSaved()
                            es.message = "\"$name\" is now a team project in ${place.label}"
                        }
                        is Team.Saved.Failed -> es.message = "Couldn't share it — ${r.why}"
                        else -> {}
                    }
                }
            }
            leaveTeam = { leaveTeamProject() }
            projectSettings = { es.projectSettings = true }
            close = { host.activeProject()?.let { op -> closeWith(listOf(op)) { deck.cur.pause(); nav.close(op.key) } } }
            closeOthers = { val others = host.open.filter { it.key != host.active }; closeWith(others) { others.forEach { nav.close(it.key) } } }
            closeAll = { val all = host.open.toList(); closeWith(all) { deck.cur.pause(); all.filter { it.key != host.active }.forEach { nav.close(it.key) }; host.active?.let { nav.close(it) } } }
            save = { saveChanges() }
            saveAs = { es.askName = true }
            saveCopy = { n ->
                val p = currentData().copy(id = java.util.UUID.randomUUID().toString(), name = n)
                layerScope.launch {
                    val ok = withContext(Dispatchers.IO) { try { ProjectStore.saveVideo(context, p, clips.firstOrNull()?.let { thumbs[it.id] }); true } catch (_: Throwable) { false } }
                    es.message = if (ok) "Copy saved as \"$n\" — you're still in \"${es.projectName.ifBlank { "Untitled" }}\"" else "Couldn't save the copy"
                }
            }
            canRevert = { es.projectId != null || Team.current != null }
            revert = {
                val op = host.activeProject()
                val t = Team.current
                val id = es.projectId
                layerScope.launch {
                    if (t != null) {
                        val (p, s) = withContext(Dispatchers.IO) { Team.open(context, t.place, t.name, t.readOnly) }
                        if (p == null || s == null || op == null) { es.message = "Couldn't read the team project"; return@launch }
                        op.data = p; op.team = s; op.savedSig = SIG_PENDING; deck.cur.pause(); nav.reopen(op)
                    } else if (id != null && op != null) {
                        val p = withContext(Dispatchers.IO) { ProjectStore.loadVideo(context, id) }
                        if (p == null) { es.message = "Couldn't read the saved project"; return@launch }
                        op.data = p; op.savedSig = SIG_PENDING; deck.cur.pause(); nav.reopen(op)
                    }
                }
            }
            capture = { cameraAsk = true }
            importGallery = { try { libPick.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) } catch (_: Throwable) {} }
            importFiles = { try { importFilesLauncher.launch(arrayOf("video/*", "image/*")) } catch (_: Throwable) {} }
            importFolder = { try { libFolder.launch(null) } catch (_: Throwable) {} }
            importAudio = { try { pickAudio.launch(arrayOf("audio/*")) } catch (_: Throwable) {} }
            importNas = { nasPool = true }
            importArchive = { try { openArchive.launch(arrayOf("application/zip", "application/octet-stream")) } catch (_: Throwable) {} }
            newSequence = { name, aspect, fps ->
                openTimeline(SavedTimeline(uniqueTimelineName(name.ifBlank { "Sequence ${otherTimelines.size + 2}" }), deliver = es.deliver.copy(aspect = aspect, fps = fps)))
            }
            mediaClips = { (library + clips).filter { it.nested == null }.distinctBy { it.uri.toString() } }
            sequenceFromClip = { c ->
                val a = if (c.height > 0) c.width * (c.desqueeze ?: 1f) / c.height else 0f
                openTimeline(SavedTimeline(uniqueTimelineName(c.name.substringBeforeLast('.')), clips = listOf(c.copy(id = java.util.UUID.randomUUID().toString())), deliver = es.deliver.copy(aspect = a, fps = 0)))
            }
            newBin = { n ->
                if (n !in binNames) binNames.add(n)
                es.page = Page.MEDIA
                es.message = "Bin \"$n\" made — pick it on the Media page, then add media to it"
            }
            offlineFile = { name, seconds, aspect ->
                val h = 1080; val w = (h * aspect).toInt()
                es.message = "Making the offline stand-in…"
                makeStandIn(seconds, w, h) { c ->
                    if (c == null) { es.message = "Couldn't make the offline file"; return@makeStandIn }
                    val oc = c.copy(name = name, offline = true, label = LabelNames.forKind(context, "offline").takeIf { it >= 0 }?.let { CLIP_LABELS[it] })
                    library.add(oc)
                    if (!es.videoLocked) clips.add(oc.copy(id = java.util.UUID.randomUUID().toString()))
                    es.message = "Offline file \"$name\" added (media strip and end of the timeline) — Link media swaps in the real file"
                }
            }
            hasTranscript = { wx.tWords.isNotEmpty() }
            captionsAi = { makeCaptions(null, false, 0) }
            captionsFromTranscript = { makeWordCaptions(1) }
            captionsImport = { try { pickSubs.launch(arrayOf("*/*")) } catch (_: Throwable) {} }
            captionEmpty = {
                val l = Layer(kind = LayerKind.TEXT, text = "Caption", size = 0.046f, x = 0.5f, y = 0.84f, box = true, shadow = true, startMs = globalMs, endMs = globalMs + EditorPrefs.titleSeconds(context) * 1000L, name = "Caption")
                layers.add(l); es.selectedLayer = l.id; es.page = Page.LAYERS
                es.message = "Caption added at ${tc(globalMs)} — type its text in Layers"
            }
            transcript = { es.page = Page.TEXT; if (wx.tWords.isEmpty() && clips.isNotEmpty()) transcribeAll() }
            title = {
                val l = Layer(kind = LayerKind.TEXT, text = "Title", size = 0.09f, x = 0.5f, y = 0.5f, shadow = true, startMs = globalMs, endMs = globalMs + EditorPrefs.titleSeconds(context) * 1000L, name = "Title")
                layers.add(l); es.selectedLayer = l.id; es.page = Page.LAYERS
                es.message = "Title added at ${tc(globalMs)} — change it in Layers"
            }
            collectFiles = { try { collectTo.launch(null) } catch (_: Throwable) {} }
            selectedOffline = { clips.getOrNull(es.selected)?.offline == true }
            makeOffline = { all ->
                val c = clips.getOrNull(es.selected)
                if (c == null) es.message = "Select a clip on the timeline first"
                else if (c.offline == true) es.message = "That clip is already offline"
                else {
                    es.message = "Making the clip offline…"
                    makeStandIn(((c.durationMs + 999) / 1000).toInt(), c.width.coerceAtLeast(16), c.height.coerceAtLeast(16)) { nc ->
                        if (nc == null) { es.message = "Couldn't make the stand-in"; return@makeStandIn }
                        val from = c.uri.toString()
                        var n = 0
                        for (i in clips.indices) if ((all && clips[i].uri.toString() == from) || clips[i].id == c.id) {
                            val o = clips[i]
                            clips[i] = o.copy(uri = nc.uri, offline = true, offlineOf = from, mime = nc.mime, durationMs = maxOf(nc.durationMs, o.endMs))
                            n++
                        }
                        if (es.current in clips.indices) load(es.current, 0, false)
                        es.message = "$n clip(s) offline — Link media brings the file back"
                    }
                }
            }
            linkMedia = {
                val c = clips.getOrNull(es.selected)
                val orig = c?.offlineOf?.let { Uri.parse(it) }
                if (c == null) es.message = "Select an offline clip first"
                else if (orig != null && ProjectArchive.readable(context, orig)) {
                    val ph = c.uri.toString()
                    for (i in clips.indices) if (clips[i].uri.toString() == ph) clips[i] = clips[i].copy(uri = orig, offline = null, offlineOf = null)
                    if (es.current in clips.indices) load(es.current, 0, false)
                    es.message = "Linked back to the original file"
                } else { es.relinkFrom = c.uri.toString(); try { relink.launch(arrayOf("video/*")) } catch (_: Throwable) { es.relinkFrom = null } }
            }
            exit = { val all = host.open.toList(); closeWith(all) { deck.cur.pause(); nav.exitApp() } }
        }
        if (wf.showKeys) ShortcutsDialog { wf.showKeys = false }
        TeamBusyOverlay()
        wf.closeAsk?.let { (list, then) ->
            AlertDialog(
                onDismissRequest = { if (!closeBusy) wf.closeAsk = null }, containerColor = Pro.PanelSolid,
                title = { Text(if (list.size == 1) "Save changes to \"${(if (list[0].key == host.active) es.projectName else list[0].name).ifBlank { "Untitled" }}\"?" else "${list.size} projects have unsaved changes", color = Color.White) },
                text = {
                    Column {
                        if (list.size > 1) for (op in list) Text("• " + (if (op.key == host.active) es.projectName else op.name).ifBlank { "Untitled" }, color = Color.White, fontSize = 13.sp)
                        Text(if (closeBusy) "Saving…" else "Unsaved changes are lost if you don't save.", color = Pro.Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                },
                confirmButton = {
                    Row {
                        TextButton(enabled = !closeBusy, onClick = { wf.closeAsk = null; then() }) { Text("Don't save", color = Pro.Red) }
                        TextButton(enabled = !closeBusy, onClick = {
                            closeBusy = true
                            layerScope.launch {
                                val ok = withContext(Dispatchers.IO) { list.all { saveOpen(it) } }
                                closeBusy = false; wf.closeAsk = null
                                if (ok) then() else es.message = "Not everything could be saved — nothing was closed"
                            }
                        }) { Text("Save", color = Pro.Amber) }
                    }
                },
                dismissButton = { TextButton(enabled = !closeBusy, onClick = { wf.closeAsk = null }) { Text("Cancel", color = Color.White) } },
            )
        }
        teamConflict?.let { r ->
            AlertDialog(
                onDismissRequest = { teamConflict = null }, containerColor = Pro.PanelSolid,
                title = { Text("${r.by} saved a newer version", color = Color.White) },
                text = { Text("At " + java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(r.at)) + ", after you opened it. Save yours over it, or save yours as a project on this PC and open theirs later.", color = Pro.Dim) },
                confirmButton = {
                    Row {
                        TextButton(onClick = { teamConflict = null; teamSave(force = true) }) { Text("Save over it", color = Pro.Red) }
                        TextButton(onClick = { teamConflict = null; es.askName = true }) { Text("Save on this PC", color = Pro.Amber) }
                    }
                },
                dismissButton = { TextButton(onClick = { teamConflict = null }) { Text("Cancel", color = Color.White) } },
            )
        }
        if (teamReadOnlyNote) AlertDialog(
            onDismissRequest = { teamReadOnlyNote = false }, containerColor = Pro.PanelSolid,
            title = { Text("Open read-only", color = Color.White) },
            text = { Text("Someone else has this team project open, so it can't be saved over. Save your version as a project on this PC instead?", color = Pro.Dim) },
            confirmButton = { TextButton(onClick = { teamReadOnlyNote = false; es.askName = true }) { Text("Save on this PC", color = Pro.Amber) } },
            dismissButton = { TextButton(onClick = { teamReadOnlyNote = false }) { Text("Cancel", color = Color.White) } },
        )
    }
    fileMenuBlock()

    /** Pinch / move on the viewer: the framing (exported), or the magnified view (view only). */
    fun moveFrame(pan: androidx.compose.ui.geometry.Offset, zoom: Float, w: Float, h: Float) {
        if (w <= 0f || h <= 0f) return
        val m = TransportBar.magnify
        if (m > 1f) {
            es.magX = (es.magX + pan.x).coerceIn(-w * (m - 1f) / 2f, w * (m - 1f) / 2f)
            es.magY = (es.magY + pan.y).coerceIn(-h * (m - 1f) / 2f, h * (m - 1f) / 2f)
            return
        }
        val d = es.deliver
        val z = (d.frameZoom * zoom).coerceIn(1f, 8f)
        // Never past the picture's edge: the frame is always filled.
        val lim = (z - 1f) / 2f
        val x = ((d.frameX * w + pan.x) / w).coerceIn(-lim, lim)
        val y = ((d.frameY * h + pan.y) / h).coerceIn(-lim, lim)
        es.deliver = d.copy(frameZoom = z, frameX = if (z <= 1.001f) 0f else x, frameY = if (z <= 1.001f) 0f else y)
    }
    fun resetFrame() {
        val was = es.deliver.framed || TransportBar.magnify > 1f
        es.deliver = es.deliver.copy(frameZoom = 1f, frameX = 0f, frameY = 0f)
        TransportBar.magnify = 1f; es.magX = 0f; es.magY = 0f
        if (was) es.message = "Whole picture again"
    }
    Box(Modifier.fillMaxSize().background(Ed.Bg)) {
        com.dronedirector.veyrocutpro.ui.AiBadgeHost(
            es.exporting && !es.cloudExport && clips.any { it.detail.aiDenoise > 0f }, "AI NOISE REDUCTION · EXPORT", 1, es.progress,
            onStop = { es.cloudJob?.cancel(); es.export?.cancel(); es.export = null; es.exporting = false; AiDenoiser.stop(); es.message = "Export stopped" },
            modifier = Modifier.align(Alignment.TopCenter).zIndex(20f),
        )
        com.dronedirector.veyrocutpro.ui.AiBadgeHost(
            com.dronedirector.veyrocutpro.camera.CloudAi.stage != null, com.dronedirector.veyrocutpro.camera.CloudAi.stage ?: "", 1,
            com.dronedirector.veyrocutpro.camera.CloudAi.progress,
            onStop = { com.dronedirector.veyrocutpro.camera.CloudAi.stop(); es.cloudJob?.cancel(); es.export?.cancel(); es.export = null; es.exporting = false; es.message = "Export stopped" },
            modifier = Modifier.align(Alignment.TopCenter).zIndex(21f),
        )
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
            val wide = maxWidth > maxHeight || maxWidth >= 720.dp
            val boxW = maxWidth.value; val boxH = maxHeight.value
            // External monitor only (DeX / HDMI desktop): wide, ultra-wide and super ultra-wide layouts.
            val mon = com.dronedirector.veyrocutpro.ui.monitorClass(boxW, boxH)
            val monCols = mon == com.dronedirector.veyrocutpro.ui.MonitorClass.ULTRA_WIDE || mon == com.dronedirector.veyrocutpro.ui.MonitorClass.SUPER_ULTRA_WIDE

            val viewerInner = @Composable { mod: Modifier ->
                // Mouse: double-click the player for full screen, wheel steps through frames.
                Box(mod.background(Color.Black).mouseActions(
                    onDoubleClick = { es.fullscreen = true },
                    onWheel = { dy, _, _, _, _ -> seekGlobal((globalMs + if (dy > 0) 40L else -40L).coerceAtLeast(0L)); true },
                ), contentAlignment = Alignment.Center) {
                    val (ow, oh) = outputSize(clips, es.deliver)
                    val projAspect = ow.toFloat() / oh
                    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        val boxAspect = maxWidth.value / maxHeight.value
                        val fw = if (boxAspect > projAspect) maxHeight * projAspect else maxWidth
                        val fh = if (boxAspect > projAspect) maxHeight else maxWidth / projAspect
                        Box(Modifier.width(fw).height(fh).clipToBounds().background(Color.Black)
                            .onGloballyPositioned { castFrame[0] = it.boundsInWindow() }
                            // Magnification (bar → magnifier): view only, never exported.
                            .graphicsLayer { scaleX = TransportBar.magnify; scaleY = TransportBar.magnify; translationX = es.magX; translationY = es.magY }
                            // Two fingers: pinch to zoom and move the picture (framing — exported as seen);
                            // one finger moves it while zoomed. When magnified, the gestures look around instead.
                            .pointerInput(fw, fh) {
                                pinchAndMove(
                                    minPointers = { if ((es.deliver.frameZoom > 1.001f || TransportBar.magnify > 1f) && es.page != Page.LAYERS) 1 else 2 },
                                    onGesture = { _, pan, zoom -> moveFrame(pan, zoom, size.width.toFloat(), size.height.toFloat()) },
                                )
                            }
                            // Double-tap: framing and magnification back to the whole picture.
                            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { resetFrame() }) }) {
                          Box(Modifier.fillMaxSize().graphicsLayer {
                              val z = es.deliver.frameZoom
                              scaleX = z; scaleY = z
                              translationX = es.deliver.frameX * size.width; translationY = es.deliver.frameY * size.height
                          }) {
                            // Video layers placed below the main video are drawn first (underneath it).
                            val belowLayers = layers.filter { it.isBelow }
                            castHasBelow[0] = belowLayers.isNotEmpty()
                            if (belowLayers.isNotEmpty()) Box(Modifier.fillMaxSize().drawWithContent {
                                castBelow.record { this@drawWithContent.drawContent() }
                                drawLayer(castBelow)
                            }) { LayerPreview(
                                layers = belowLayers, timeMs = globalMs, selectedId = null, showAll = false, playing = es.playing,
                                onMove = { _, _, _ -> }, onSelect = { es.selectedLayer = it; es.page = Page.LAYERS },
                            ) }
                            AndroidView(
                                factory = { ctx ->
                                    android.widget.FrameLayout(ctx).apply {
                                        players.forEachIndexed { pi, p ->
                                            val frame = AspectRatioFrameLayout(ctx)
                                            val tv = android.view.TextureView(ctx)
                                            frameViews[pi] = frame; texViews[pi] = tv
                                            frame.addView(tv, android.widget.FrameLayout.LayoutParams(-1, -1, android.view.Gravity.CENTER))
                                            frame.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> v.post { applyPlaybackRes(v as android.widget.FrameLayout, tv, TransportBar.playbackDiv) } }
                                            addView(frame, android.widget.FrameLayout.LayoutParams(-1, -1, android.view.Gravity.CENTER))
                                            p.setVideoTextureView(tv)
                                        }
                                    }
                                },
                                update = { root ->
                                    for (i in 0..1) {
                                        val frame = root.getChildAt(i) as AspectRatioFrameLayout
                                        frame.resizeMode = if (es.deliver.fill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                                        val shownClip = if (i == deck.active) clips.getOrNull(es.current) else clips.getOrNull(es.current + 1)
                                        frame.setAspectRatio(aspects[i] * (shownClip?.desqueeze ?: 1f))
                                        if (!transitionOn[0]) frame.alpha = if (i == deck.active) 1f else 0f
                                        // Playback resolution (bar → HD button): the picture is drawn smaller, then scaled up.
                                        val div = TransportBar.playbackDiv
                                        texViews[i]?.let { tv -> frame.post { applyPlaybackRes(frame, tv, div) } }
                                    }
                                },
                                // The video track's eye: hidden = see-through (layers below show, else black).
                                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (es.videoHidden) 0f else 1f },
                            )
                            es.morphFrame?.let { mf ->
                                val img = remember(mf) { mf.asImageBitmap() }
                                androidx.compose.foundation.Image(
                                    img, contentDescription = null, modifier = Modifier.fillMaxSize(),
                                    contentScale = if (es.deliver.fill) androidx.compose.ui.layout.ContentScale.Crop else androidx.compose.ui.layout.ContentScale.Fit,
                                )
                            }
                            if (es.morphBusy) Text("Analysing morph cut…", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp), modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 6.dp, vertical = 2.dp))
                            // A selected below-layer shows as a see-through ghost on top while you place it.
                            val ghost = if (es.page == Page.LAYERS && !es.playing) layers.firstOrNull { it.id == es.selectedLayer && it.isBelow } else null
                            // Titles and layers on top (also recorded for the TV).
                            Box(Modifier.fillMaxSize().drawWithContent {
                                castAbove.record { this@drawWithContent.drawContent() }
                                drawLayer(castAbove)
                            }) {
                            LayerPreview(
                                layers = layers.filter { !it.isBelow || it.id == ghost?.id }.map { if (it.id == ghost?.id) it.copy(opacity = it.opacity * 0.45f) else it },
                                timeMs = globalMs,
                                selectedId = if (es.page == Page.LAYERS) es.selectedLayer else null,
                                showAll = es.page == Page.LAYERS && !es.playing,
                                playing = es.playing,
                                onMove = { id, dx, dy ->
                                    val i = layers.indexOfFirst { it.id == id }
                                    if (i >= 0 && !layers[i].locked) {
                                        val o = layers[i]
                                        val cur = o.animAt(globalMs)
                                        layers[i] = o.editedAt(globalMs, cur.copy(x = (cur.x + dx).coerceIn(0f, 1f), y = (cur.y + dy).coerceIn(0f, 1f), keys = o.keys))
                                    }
                                },
                                onSelect = { es.selectedLayer = it; es.page = Page.LAYERS },
                                onPinCorner = { id, k, dx, dy ->
                                    val i = layers.indexOfFirst { it.id == id }
                                    val p = layers.getOrNull(i)?.pin
                                    if (p != null && !layers[i].locked) layers[i] = layers[i].copy(pin = p.map { q ->
                                        q.copy(p = q.p.mapIndexed { j, v -> if (j == 2 * k) (v + dx).coerceIn(-0.5f, 1.5f) else if (j == 2 * k + 1) (v + dy).coerceIn(-0.5f, 1.5f) else v })
                                    })
                                },
                            )
                            }
                          }  // framing
                            // Before / after split (Color page, Android 13+): drag the divider across the picture.
                            if (es.page == Page.COLOR && gpuGrade && clips.isNotEmpty()) {
                                if (es.abSplit > 0f) Box(
                                    Modifier.fillMaxSize().pointerInput(Unit) {
                                        detectDragGestures { ch, _ -> ch.consume(); es.abSplit = (ch.position.x / size.width).coerceIn(0.02f, 0.98f) }
                                    },
                                ) {
                                    Text("BEFORE", style = Pro.Label.copy(color = Color.White, fontSize = 9.sp), modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 4.dp))
                                    Text("AFTER", style = Pro.Label.copy(color = Color.White, fontSize = 9.sp), modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 4.dp))
                                }
                                Text(
                                    if (es.abSplit > 0f) "SPLIT ✕" else "BEFORE | AFTER",
                                    style = Pro.Label.copy(color = Color.Black, fontSize = 9.sp),
                                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 8.dp).clip(RoundedCornerShape(50)).background(Pro.Amber)
                                        .clickable { es.abSplit = if (es.abSplit > 0f) 0f else 0.5f }.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                            // Colour mask handles on the player (Color → Masks).
                            val mc = clips.getOrNull(es.current)
                            val selMask = mc?.masks?.firstOrNull { it.id == maskTools.selected }
                            if (es.page == Page.COLOR && es.colorTab == ColorTab.MASKS && selMask != null && es.selected == es.current && !es.playing && selMask.type == MaskType.PEN) {
                                PenEditOverlay(selMask, mc.startMs + deck.cur.currentPosition) { m ->
                                    update(es.current) { cl -> cl.copy(masks = cl.masks?.map { if (it.id == m.id) m else it }) }
                                }
                            } else if (es.page == Page.COLOR && es.colorTab == ColorTab.MASKS && selMask != null && es.selected == es.current && !es.playing) {
                                MaskEditOverlay(selMask, ow, oh, maskTools, image = thumbs[mc.id], pad = 0.dp) { m ->
                                    update(es.current) { cl -> cl.copy(masks = cl.masks?.map { if (it.id == m.id) m else it }) }
                                }
                            }
                            if (!es.cropMode) SafeZoneOverlay()
                            if (es.cropMode) CropOverlay(
                                editingEnd = es.cropEditEnd && clips.getOrNull(es.current)?.reframe?.animate == true,
                                onGesture = { pan, zf, rot, w, h ->
                                    update(es.current) { cl ->
                                        val r = cl.reframe
                                        val keyAt = r.keyedAt(deck.cur.currentPosition)
                                        val end = r.animate && es.cropEditEnd
                                        val f = keyAt?.frame ?: if (end) r.end else r.start
                                        val nf = f.copy(
                                            zoom = (f.zoom * zf).coerceIn(0.25f, 8f),
                                            x = (f.x + pan.x / w).coerceIn(-2f, 2f), y = (f.y + pan.y / h).coerceIn(-2f, 2f),
                                            rotation = f.rotation + rot,
                                        )
                                        cl.copy(reframe = if (keyAt != null) r.upsertKey(deck.cur.currentPosition, nf, keyAt.opacity)
                                            else if (end) r.copy(end = nf) else r.copy(start = nf))
                                    }
                                },
                                onDone = { es.cropMode = false },
                            )
                        }
                    }
                    if (!es.fullscreen) Box(
                        Modifier.align(Alignment.BottomEnd).padding(6.dp).size(36.dp).clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.5f)).clickable { es.fullscreen = true },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Fullscreen, contentDescription = "Full screen", tint = Color.White) }
                    clips.getOrNull(es.current)?.let { cc ->
                        val pu = proxyFor(cc)
                        val badge = when {
                            cachedFor(cc) != null -> "CACHED"
                            pu != null -> "PROXY " + (ProxyMaker.qualityOf(pu)?.label ?: "")
                            es.proxySettings.enabled && MediaId.of(cc) in proxyMedia -> "ORIGINAL"
                            else -> null
                        }
                        if (badge != null) Text(
                            badge, style = Pro.Label.copy(color = Color.Black, fontSize = 9.sp),
                            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).clip(RoundedCornerShape(4.dp)).background(Pro.Amber).padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    clips.getOrNull(es.current)?.let { cc ->
                        // Small, non-blocking progress chip: the original keeps playing meanwhile.
                        val pp = proxyProgress[MediaId.of(cc)]
                        if (pp != null) Row(
                            Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(progress = { pp / 100f }, color = Pro.Amber, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                            Text("Proxy ${es.proxySettings.quality.label} · $pp%", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                    if (es.showInfo) Text(
                        es.infoText, color = Color.White, fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).background(Color.Black.copy(alpha = 0.6f)).padding(4.dp),
                    )
                    // RGB histogram: a pull-out drawer on the right edge (never in full screen).
                    if (!es.fullscreen && showScope) ScopeDrawer(es.scopeHist, Modifier.align(Alignment.CenterEnd), frame = es.scopeFrame) { es.scopeOpen = it }
                    // The time read-out is in the timeline's bar below the picture only (not on the picture).
                    // Export: KEEP EDITING shrinks the progress to a small bar, so you can carry on while it renders
                    // (it keeps going with the screen off or in another app; the notification can share the result).
                    var exportSmall by remember { mutableStateOf(false) }
                    LaunchedEffect(es.exporting) { if (!es.exporting) exportSmall = false }
                    if (es.exporting && exportSmall) Row(
                        Modifier.align(Alignment.TopCenter).padding(top = 8.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.75f))
                            .clickable { exportSmall = false }.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(color = Pro.Amber, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                        Text("Rendering… ${es.progress}%", color = Color.White, fontSize = 12.sp)
                    }
                    if (es.exporting && !exportSmall) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(color = Pro.Amber)
                            Text("Rendering… ${es.progress}%", color = Color.White, fontSize = 15.sp)
                            // Estimated time remaining, from the progress so far.
                            var now by remember { mutableStateOf(0L) }
                            LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
                            Text(if (now > 0) etaFromProgress(es.exportStartAt, es.progress) ?: "Estimating time left…" else "Estimating time left…", color = Pro.Dim, fontSize = 13.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("KEEP EDITING", style = Pro.Label.copy(color = Color.White, fontSize = 12.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { exportSmall = true }.padding(10.dp))
                                Text("CANCEL", style = Pro.Label.copy(color = Pro.Amber, fontSize = 12.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { es.cloudJob?.cancel(); es.export?.cancel(); es.export = null; es.exporting = false }.padding(10.dp))
                            }
                            Text("It keeps rendering with the screen off or in another app; the notification tells you when it's done, with Share.", color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.widthIn(max = 300.dp))
                        }
                    }
                    es.message?.let {
                        Text(it, color = Color.White, fontSize = 13.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.75f)).padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }
            }

            fun togglePlay() {
                // Stuck loading? Play pressed again means "try again", not "do nothing".
                if (deck.cur.playWhenReady && !deck.cur.isPlaying && deck.cur.playbackState == Player.STATE_BUFFERING) {
                    wx.stallTries = 0
                    load(es.current, deck.cur.currentPosition, true)
                    return
                }
                if (deck.cur.playWhenReady && deck.cur.playbackState != Player.STATE_ENDED) deck.cur.pause() else {
                    when {
                        deck.cur.playbackState == Player.STATE_ENDED && es.current == clips.lastIndex -> load(0, 0, true)
                        deck.cur.playbackState == Player.STATE_IDLE -> load(es.current, es.posMs, true)
                        else -> deck.cur.play()
                    }
                }
            }

            fun prevClip() { if (deck.cur.currentPosition > 1500 || es.current == 0) deck.cur.seekTo(0) else load(es.current - 1, 0, deck.cur.isPlaying) }
            fun nextClip() { if (es.current < clips.lastIndex) load(es.current + 1, 0, deck.cur.isPlaying) }

            // The bar's actions (also on the keyboard: I, O, M, arrow keys).
            fun markIn() {
                es.deliver = es.deliver.copy(rangeStart = globalMs, rangeEnd = es.deliver.rangeEnd?.takeIf { it > globalMs } ?: timelineLength(clips))
                es.message = "In point at ${tc(globalMs)}"
            }
            fun markOut() {
                es.deliver = es.deliver.copy(rangeEnd = globalMs, rangeStart = es.deliver.rangeStart?.takeIf { it < globalMs } ?: 0L)
                es.message = "Out point at ${tc(globalMs)}"
            }
            fun toggleMarker() {
                val near = markers.firstOrNull { kotlin.math.abs(it - globalMs) < 250 }
                if (near != null) { markers.remove(near); markerNotes.remove(near); es.message = "Marker removed" }
                else { markers.add(globalMs); markers.sort(); es.message = "Marker at ${tc(globalMs)}" }
            }
            fun stepFrame(d: Int) {
                if (clips.isEmpty()) return
                if (es.playing) togglePlay()
                val f = 1000L / (if (es.deliver.fps > 0) es.deliver.fps else 30)
                seekGlobal((globalMs + d * f).coerceIn(0L, (timelineLength(clips) - 1).coerceAtLeast(0L)))
            }
            fun toggleSafe() {
                if (SafeZones.current != 0) { es.lastSafe = SafeZones.current; SafeZones.current = 0 }
                else SafeZones.current = es.lastSafe.takeIf { it in 1 until SafeZones.zones.size } ?: SafeZones.zones.lastIndex
            }
            fun cycleMagnify() {
                TransportBar.magnify = when (TransportBar.magnify) { 1f -> 2f; 2f -> 4f; else -> 1f }
                if (TransportBar.magnify == 1f) { es.magX = 0f; es.magY = 0f }
                es.message = if (TransportBar.magnify > 1f) "Magnified ${TransportBar.magnify.toInt()}× — view only, drag to look around" else "Magnification off"
            }

            // Keyboard shortcuts (Bluetooth / USB keyboard): each key runs its action (keyboard icon to change them).
            remember { Shortcuts.load(context); 0 }
            DisposableEffect(Unit) {
                KeyBus.handler = handler@{ e ->
                    if (e.action != android.view.KeyEvent.ACTION_DOWN) return@handler false
                    val id = Shortcuts.actionFor(e) ?: return@handler false
                    val frame = 1000L / (if (es.deliver.fps > 0) es.deliver.fps else 30)
                    val total = timelineLength(clips)
                    fun go(ms: Long) { if (clips.isNotEmpty()) seekGlobal(ms.coerceIn(0, (total - 1).coerceAtLeast(0))) }
                    val st = clipStarts(clips)
                    when (id) {
                        "play" -> if (clips.isNotEmpty()) togglePlay()
                        "back5" -> go(globalMs - 5000L); "fwd5" -> go(globalMs + 5000L)
                        "back1" -> go(globalMs - 1000L); "fwd1" -> go(globalMs + 1000L)
                        "frameback" -> stepFrame(-1); "framefwd" -> stepFrame(1)
                        "back10f" -> go(globalMs - frame * 10); "fwd10f" -> go(globalMs + frame * 10)
                        "prevcut" -> go(st.lastOrNull { it < globalMs - 50 } ?: 0L)
                        "nextcut" -> go(st.firstOrNull { it > globalMs + 50 } ?: total)
                        "start" -> go(0); "end" -> go(total)
                        "full" -> es.fullscreen = !es.fullscreen
                        "mute" -> es.previewMuted = !es.previewMuted
                        "magnify" -> cycleMagnify()
                        "safe" -> toggleSafe()
                        "markin" -> markIn(); "markout" -> markOut()
                        "goin" -> es.deliver.rangeStart?.let { go(it) }
                        "goout" -> es.deliver.rangeEnd?.let { go(it) }
                        "clearinout" -> { es.deliver = es.deliver.copy(rangeStart = null, rangeEnd = null); es.message = "In and out cleared" }
                        "marker" -> toggleMarker()
                        "prevmarker" -> markers.sorted().lastOrNull { it < globalMs - 50 }?.let { go(it) }
                        "nextmarker" -> markers.sorted().firstOrNull { it > globalMs + 50 }?.let { go(it) }
                        "split" -> splitAtPlayhead()
                        "delete" -> if (clips.isNotEmpty()) deleteClip(es.selected.coerceIn(0, clips.lastIndex), false)
                        "duplicate" -> clips.getOrNull(es.selected)?.let { c -> if (!es.videoLocked) { clips.add(es.selected + 1, c.copy(id = java.util.UUID.randomUUID().toString())); es.message = "Clip duplicated" } }
                        "undo" -> undo(); "redo" -> redo()
                        "prevclip" -> if (clips.isNotEmpty()) { es.selected = (es.selected - 1).coerceAtLeast(0); go(st[es.selected]) }
                        "nextclip" -> if (clips.isNotEmpty()) { es.selected = (es.selected + 1).coerceAtMost(clips.lastIndex); go(st[es.selected]) }
                        "snap" -> {
                            es.snapping = !es.snapping
                            context.getSharedPreferences("procam", android.content.Context.MODE_PRIVATE).edit().putBoolean("tl_snap", es.snapping).apply()
                            es.message = if (es.snapping) "Snapping on" else "Snapping off"
                        }
                        "title" -> fa.title()
                        "frame" -> saveStill()
                        "zoomin" -> zoom.zoomIn(); "zoomout" -> zoom.zoomOut(); "zoomfit" -> zoom.fit()
                        "p_media" -> es.page = Page.MEDIA; "p_edit" -> es.page = Page.EDIT; "p_text" -> es.page = Page.TEXT
                        "p_transitions" -> es.page = Page.TRANSITIONS; "p_color" -> es.page = Page.COLOR; "p_layers" -> es.page = Page.LAYERS
                        "p_audio" -> es.page = Page.AUDIO; "p_deliver" -> es.page = Page.DELIVER
                        "filemenu" -> fm.menu = true
                        "newproject" -> if (fa.hasContent()) fm.confirmNew = "new" else fa.newProject()
                        "open" -> fa.openProject()
                        "save" -> fa.save()
                        "saveas" -> fa.saveAs()
                        "import" -> fa.importFiles()
                        "capture" -> fa.capture()
                        "export" -> es.page = Page.DELIVER
                        "close" -> fa.close()
                        "nextproject" -> host.open.map { it.key }.let { keys -> if (keys.size > 1) fa.switchTo(keys[(keys.indexOf(host.active) + 1) % keys.size]) }
                        "projectsettings" -> fa.projectSettings()
                        "settings" -> es.showSettings = true
                        "shortcuts" -> wf.showKeys = true
                        else -> return@handler false
                    }
                    true
                }
                onDispose { KeyBus.handler = null }
            }

            val transport = @Composable {
                Column(Modifier.fillMaxWidth().background(Ed.Panel).padding(horizontal = 8.dp)) {
                    Slider(
                        value = if (totalMs > 0) globalMs.toFloat() / totalMs else 0f,
                        onValueChange = { seekGlobal((it * totalMs).toLong()) },
                        modifier = Modifier.height(26.dp),
                        colors = SliderDefaults.colors(thumbColor = Pro.Amber, activeTrackColor = Pro.Amber, inactiveTrackColor = com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.15f)),
                    )
                    // The whole button row scrolls sideways (with the hint arrows) whenever it is wider
                    // than the screen. Which buttons it shows is chosen with its + button. Play sits in the
                    // middle of the playback buttons and starts centred under the picture.
                    BoxWithConstraints(Modifier.fillMaxWidth().onGloballyPositioned { c -> wf.bar.onBar(c) }) {
                        val barPx = constraints.maxWidth.toFloat()
                        val bc = wf.bar
                        val lead = (barPx / 2f - bc.playX).coerceAtLeast(0f)
                        val trail = (barPx / 2f - (bc.rowW - bc.playX)).coerceAtLeast(0f)
                        val dens = androidx.compose.ui.platform.LocalDensity.current
                        // Until you scroll the row yourself, it keeps Play in the middle (also after the layout settles or turns).
                        LaunchedEffect(barPx, bc.playX, bc.rowW) { bc.centre() }
                        LaunchedEffect(Unit) { androidx.compose.runtime.snapshotFlow { bc.scroll.isScrollInProgress }.collect { if (it && !bc.auto) bc.touched = true } }
                        Row(Modifier.hintedScroll(horizontal = true, scroll = bc.scroll), verticalAlignment = Alignment.CenterVertically) {
                            @Composable fun b(id: String, content: @Composable () -> Unit) { if (TransportBar.shown(id)) content() }
                            androidx.compose.foundation.layout.Spacer(Modifier.width(with(dens) { lead.toDp() }))
                            Row(
                                Modifier.onGloballyPositioned { c -> bc.onRow(c) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                    b("split") { TransportIcon(Icons.Filled.ContentCut, "Split at playhead", accent = true) { splitAtPlayhead() } }
                                    b("undo") { TransportIcon(Icons.AutoMirrored.Filled.Undo, "Undo", tint = if (es.histPos > 0) Color.White else Pro.Dim) { undo() } }
                                    b("redo") { TransportIcon(Icons.AutoMirrored.Filled.Redo, "Redo", tint = if (es.histPos < history.lastIndex) Color.White else Pro.Dim) { redo() } }
                                    b("markin") { TransportIcon(BarIcons.markIn, "Mark in", tint = if (es.deliver.rangeStart != null) Pro.Amber else Color.White) { markIn() } }
                                    b("markout") { TransportIcon(BarIcons.markOut, "Mark out", tint = if (es.deliver.rangeEnd != null) Pro.Amber else Color.White) { markOut() } }
                                    b("marker") { TransportIcon(BarIcons.marker, "Add marker") { toggleMarker() } }
                                    b("markers") {
                                        MarkersMenuButton(
                                            markers.toList(), markerNotes.toMap(), globalMs, if (es.deliver.fps > 0) es.deliver.fps else 30,
                                            onGo = { seekGlobal(it) },
                                            onEdit = { old, new, note ->
                                                if (new != old) { markers.remove(old); markerNotes.remove(old); if (new !in markers) { markers.add(new); markers.sort() } }
                                                if (note.isBlank()) markerNotes.remove(new) else markerNotes[new] = note
                                                es.message = "Marker saved"
                                            },
                                            onClear = { m -> markers.remove(m); markerNotes.remove(m); es.message = "Marker cleared" },
                                            onClearAll = { markers.clear(); markerNotes.clear(); es.message = "All markers cleared" },
                                            onImport = { l ->
                                                for ((t, n) in l) { if (t !in markers) markers.add(t); if (n.isNotBlank()) markerNotes[t] = n }
                                                markers.sort(); es.message = "${l.size} markers imported"
                                            },
                                            onMessage = { es.message = it },
                                        )
                                    }
                                    b("speed") { TransportIcon(Icons.Filled.Speed, "Speed / Duration") { if (clips.getOrNull(es.selected) == null) es.message = "Select a clip first" else wf.barDlg = "speed" } }
                                    b("interp") { TransportIcon(Icons.Filled.SlowMotionVideo, "Time interpolation") { if (clips.getOrNull(es.selected) == null) es.message = "Select a clip first" else wf.barDlg = "interp" } }
                                    b("link") { TransportIcon(Icons.Filled.Link, "Link / unlink tracks") { wf.barDlg = "link" } }
                                    b("group") { TransportIcon(Icons.Filled.Workspaces, "Group / ungroup") { wf.barDlg = "group" } }
                            }
                            Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    b("mute") { TransportIcon(if (es.previewMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp, "Mute preview") { es.previewMuted = !es.previewMuted } }
                                    b("goin") { TransportIcon(BarIcons.goIn, "Go to in point") { seekGlobal(es.deliver.rangeStart ?: 0L) } }
                                    b("prev") { TransportIcon(Icons.Filled.SkipPrevious, "Previous clip") { prevClip() } }
                                    b("frameback") { TransportIcon(BarIcons.frameBack, "Step back one frame") { stepFrame(-1) } }
                                    b("play") { Box(Modifier.onGloballyPositioned { c -> wf.bar.onPlay(c) }) { PlayGlow(es.playing) { TransportIcon(if (es.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play", tint = Color.White, accent = !PlayFx.on, size = 46.dp, iconSize = 30.dp) { togglePlay() } } } }
                                    b("framefwd") { TransportIcon(BarIcons.frameFwd, "Step forward one frame") { stepFrame(1) } }
                                    b("loop") {
                                        TransportIcon(Icons.Filled.Repeat, "Loop playback", tint = if (es.loop) Pro.Amber else Color.White) {
                                            es.loop = !es.loop
                                            es.message = if (!es.loop) "Loop off" else if (es.deliver.rangeStart != null && es.deliver.rangeEnd != null) "Loop on — the in–out range plays over and over" else "Loop on — the timeline plays over and over"
                                        }
                                    }
                                    b("next") { TransportIcon(Icons.Filled.SkipNext, "Next clip") { nextClip() } }
                                    b("goout") { TransportIcon(BarIcons.goOut, "Go to out point") { seekGlobal(((es.deliver.rangeEnd ?: timelineLength(clips)) - 1).coerceAtLeast(0L)) } }
                                    b("start") { TransportIcon(BarIcons.toStart, "Playhead to start") { seekGlobal(0L) } }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                    b("tlout") { TransportIcon(Icons.Filled.ZoomOut, "Zoom timeline out") { zoom.zoomOut() } }
                                    b("tlfit") { TransportIcon(Icons.Filled.FitScreen, "Show whole timeline") { zoom.fit() } }
                                    b("tlin") { TransportIcon(Icons.Filled.ZoomIn, "Zoom timeline in") { zoom.zoomIn() } }
                                    b("cast") { if (castPanel) com.dronedirector.veyrocutpro.ui.CastButton(castSource, Modifier.padding(horizontal = 4.dp), small = true, sound = com.dronedirector.veyrocutpro.camera.CastFeed.Sound.APP) }
                                    b("safe") { TransportIcon(Icons.Filled.CropFree, "Safe margins", tint = if (SafeZones.current != 0) Pro.Amber else Color.White) { toggleSafe() } }
                                    b("res") {
                                        Box {
                                            var open by remember { mutableStateOf(false) }
                                            TransportIcon(Icons.Filled.Hd, "Playback resolution", tint = if (TransportBar.playbackDiv > 1) Pro.Amber else Color.White) { open = true }
                                            androidx.compose.material3.DropdownMenu(open, { open = false }, containerColor = Pro.PanelSolid) {
                                                Text("PLAYBACK RESOLUTION", style = Pro.Label.copy(color = Pro.Amber), modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
                                                for ((d, label) in TransportBar.DIVS) androidx.compose.material3.DropdownMenuItem(
                                                    text = { Text(label, color = if (TransportBar.playbackDiv == d) Pro.Amber else Color.White) },
                                                    onClick = { TransportBar.playbackDiv = d; open = false; es.message = if (d == 1) "Preview at full resolution" else "Preview at $label resolution (the export is always full)" },
                                                )
                                            }
                                        }
                                    }
                                    b("magnify") { TransportIcon(Icons.Filled.Search, "Magnification", tint = if (TransportBar.magnify > 1f) Pro.Amber else Color.White) { cycleMagnify() } }
                                    b("full") { TransportIcon(Icons.Filled.Fullscreen, "Full screen") { es.fullscreen = true } }
                                    b("frame") { TransportIcon(Icons.Filled.AddAPhoto, "Export frame") { saveStill() } }
                                    b("settings") { TransportIcon(Icons.Filled.Settings, "Settings") { es.showSettings = true } }
                                // + : choose which buttons the bar shows.
                                TransportIcon(Icons.Filled.Add, "Edit the buttons") { es.editBar = true }
                            }
                            }
                            androidx.compose.foundation.layout.Spacer(Modifier.width(with(dens) { trail.toDp() }))
                        }
                    }
                }
            }
            if (es.editBar) TransportButtonEditor(context) { es.editBar = false }
            // Speed / Duration, time interpolation, linking and grouping (buttons under the picture).
            val trackItems = layers.filter { !it.wholeVideo }.map { TrackItem(it.id, it.name ?: it.kind.label + (if (it.kind == LayerKind.TEXT) " · " + it.text.take(24) else ""), false, it.linked != false, it.group, it.locked) } +
                listOfNotNull(es.music).map { TrackItem(it.id, it.name, true, it.linked != false, it.group, it.locked) } +
                extraAudio.map { TrackItem(it.id, it.name, true, it.linked != false, it.group, it.locked) }
            when (wf.barDlg) {
                "speed" -> clips.getOrNull(es.selected)?.let { c ->
                    SpeedDurationDialog(c, onDismiss = { wf.barDlg = null }) { sp, rev ->
                        wf.barDlg = null
                        if (es.videoLocked) { es.message = "The video track is locked" }
                        else {
                            val i = es.selected
                            clips[i] = clips[i].copy(speed = sp, ramp = null, rampName = null)
                            if (rev) reverseClip(i)
                            es.message = "Speed ${kotlin.math.round(sp * 100).toInt()} % — ${tc(clips[i].lengthMs)}"
                        }
                    }
                } ?: run { wf.barDlg = null }
                "interp" -> clips.getOrNull(es.selected)?.let { c ->
                    TimeInterpolationDialog(c, onDismiss = { wf.barDlg = null }) { mode ->
                        wf.barDlg = null
                        val slowest = (c.ramp?.minOrNull() ?: 1f) * c.speed
                        when (mode) {
                            0 -> es.message = "Frame sampling — the clip plays its own frames"
                            1 -> if (slowest >= 0.95f) es.message = "Optical flow is for slowed-down clips — set a speed under 100 % first (Speed / Duration)"
                                 else flowRetime(es.selected, kotlin.math.ceil(1f / slowest).toInt().coerceIn(2, 8), null, 0f)
                            else -> flowRetime(es.selected, 1, null, 0.5f)
                        }
                    }
                } ?: run { wf.barDlg = null }
                "link" -> LinkTracksDialog(trackItems, onDismiss = { wf.barDlg = null }) { id, audio, on ->
                    if (!audio) { val j = layers.indexOfFirst { it.id == id }; if (j >= 0) layers[j] = layers[j].copy(linked = on) }
                    else if (es.music?.id == id) es.music = es.music?.copy(linked = on)
                    else { val j = extraAudio.indexOfFirst { it.id == id }; if (j >= 0) extraAudio[j] = extraAudio[j].copy(linked = on) }
                }
                "group" -> GroupDialog(trackItems, onDismiss = { wf.barDlg = null }, onGroup = { picked ->
                    val g = java.util.UUID.randomUUID().toString().take(8)
                    for (it in picked) {
                        if (!it.audio) { val j = layers.indexOfFirst { l -> l.id == it.id }; if (j >= 0) layers[j] = layers[j].copy(group = g) }
                        else if (es.music?.id == it.id) es.music = es.music?.copy(group = g)
                        else { val j = extraAudio.indexOfFirst { a -> a.id == it.id }; if (j >= 0) extraAudio[j] = extraAudio[j].copy(group = g) }
                    }
                    es.message = "${picked.size} items grouped — drag one and they all move"
                }, onUngroup = { g ->
                    for (j in layers.indices) if (layers[j].group == g) layers[j] = layers[j].copy(group = null)
                    if (es.music?.group == g) es.music = es.music?.copy(group = null)
                    for (j in extraAudio.indices) if (extraAudio[j].group == g) extraAudio[j] = extraAudio[j].copy(group = null)
                    es.message = "Ungrouped"
                })
                else -> {}
            }

            // The picture stays dark in the light look.
            val viewer = @Composable { mod: Modifier -> com.dronedirector.veyrocutpro.ui.ForceDark { viewerInner(mod) } }
            val timeline = @Composable { com.dronedirector.veyrocutpro.ui.ForceDark {
                var deviceClip by remember { mutableStateOf<Clip?>(null) }
                deviceClip?.let { RevealFileDialog(it) { deviceClip = null } }
                TimelineView(
                    onSelectAudio = { id -> goToSection(Page.AUDIO, if (id == es.music?.id) "MUSIC TRACK" else "MORE AUDIO TRACKS") },
                    onDeleteClip = { i -> if (es.videoLocked) es.message = "The video track is locked" else es.deleteAsk = i },
                    pick = wf.pick,
                    onDetachAudio = {
                        val ids = if (wf.pick.on && wf.pick.ids.isNotEmpty()) wf.pick.ids.toSet() else setOfNotNull(clips.getOrNull(es.selected)?.id)
                        clips.indices.filter { clips[it].id in ids && !clips[it].muted }.forEach { detachAudio(it) }
                        if (ids.isNotEmpty() && clips.none { it.id in ids && !it.muted }.not()) es.message = "Some clips couldn't be detached (speed changed or ramped)"
                    },
                    clipMenu = {
                        ClipMenu(clips, wf.pick, ClipActs(
                            select = { i -> es.selected = i; es.message = "Tap clips to pick them — Color changes go to every picked clip" },
                            splitHere = { i -> if (es.current == i) splitAtPlayhead() else es.message = "Move the playhead into this clip to split it" },
                            duplicate = { list ->
                                if (es.videoLocked) es.message = "The video track is locked"
                                else if (list.isNotEmpty()) {
                                    clips.addAll(list.max() + 1, list.map { clips[it].copy(id = java.util.UUID.randomUUID().toString()) })
                                    es.message = if (list.size == 1) "Clip duplicated" else "${list.size} clips duplicated"
                                }
                            },
                            speed = { i -> es.selected = i; wf.barDlg = "speed" },
                            reverse = { i -> reverseClip(i) },
                            boomerang = { i -> reverseClip(i, boomerang = true) },
                            mute = { list -> val on = list.all { clips[it].muted }; list.forEach { k -> update(k) { it.copy(muted = !on) } }; es.message = if (on) "Sound on" else "Sound muted" },
                            nest = { from, n -> nestClips(from, n) },
                            detach = { list -> list.forEach { detachAudio(it) } },
                            delete = { i -> if (es.videoLocked) es.message = "The video track is locked" else es.deleteAsk = i },
                            color = { i -> openColor(i) },
                            revealLibrary = { i -> clips.getOrNull(i)?.let { revealInLibrary(it) } },
                            revealDevice = { i -> deviceClip = clips.getOrNull(i) },
                            deleteMany = { list ->
                                if (es.videoLocked) es.message = "The video track is locked"
                                else { list.sortedDescending().forEach { deleteClip(it, false) }; es.message = "${list.size} clips deleted — undo brings them back" }
                            },
                        ))
                    },
                    clips = clips, layers = layers, music = es.music, strips = strips,
                    globalMs = globalMs, selectedClip = es.selected,
                    selectedLayerId = if (es.page == Page.LAYERS) es.selectedLayer else null,
                    onScrub = { ms, final -> scrub(ms, final) },
                    onSelectClip = { es.selected = it; if (es.page == Page.MEDIA || es.page == Page.LAYERS || es.page == Page.DELIVER) es.page = Page.EDIT },
                    onSelectLayer = { id -> es.selectedLayer = id; layers.firstOrNull { it.id == id }?.let { goToSection(Page.LAYERS, "SELECTED ${it.kind.label.uppercase()}") } ?: run { es.page = Page.LAYERS } },
                    zoom = zoom,
                    onSelectTransition = { es.selected = it; es.page = Page.TRANSITIONS },
                    extraAudio = extraAudio,
                    videoMuted = es.videoMuted, videoLocked = es.videoLocked, videoHidden = es.videoHidden,
                    onVideoHide = { es.videoHidden = !es.videoHidden; es.message = if (es.videoHidden) "Video track hidden" else "Video track shown" },
                    onVideoMute = { es.videoMuted = !es.videoMuted; es.message = if (es.videoMuted) "Video track sound muted" else "Video track sound on" },
                    onVideoLock = { es.videoLocked = !es.videoLocked; es.message = if (es.videoLocked) "Video track locked" else "Video track unlocked" },
                    onLayerFlags = { l -> val i = layers.indexOfFirst { it.id == l.id }; if (i >= 0) layers[i] = l },
                    onAudioFlags = { t ->
                        val m = es.music
                        if (m != null && m.id == t.id) es.music = t
                        else { val i = extraAudio.indexOfFirst { it.id == t.id }; if (i >= 0) extraAudio[i] = t }
                    },
                    onLocked = { es.message = "Locked — tap the lock at the left of the timeline to unlock" },
                    waves = if (showWaves) waves else emptyMap(),
                    markers = markers, snapping = es.snapping,
                    rangeIn = es.deliver.rangeStart?.takeIf { es.deliver.rangeEnd != null }, rangeOut = es.deliver.rangeEnd?.takeIf { es.deliver.rangeStart != null }, markerFx = es.markerFx,
                    trimLeavesGap = !es.trimRipple,
                    holdFrom = es.gapHold?.let { (id, d) -> clips.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { it to d } },
                    onToggleMarker = {
                        val near = markers.firstOrNull { kotlin.math.abs(it - globalMs) < 250 }
                        if (near != null) { markers.remove(near); markerNotes.remove(near); es.message = "Marker removed" }
                        else { markers.add(globalMs); markers.sort(); es.message = "Marker at ${tc(globalMs)} — tap ◆ MARKER on it again to remove" }
                    },
                    onAddVideoLayer = { es.askVideoLayer = true },
                    onClipTrim = { i, a, b ->
                        if (i in clips.indices && !es.videoLocked) clips[i] = clips[i].copy(startMs = a, endMs = b)
                    },
                    onClipTrimDone = { i, before, edge -> afterTrim(i, before, edge) },
                    onToggleSnap = {
                        es.snapping = !es.snapping
                        context.getSharedPreferences("procam", android.content.Context.MODE_PRIVATE).edit().putBoolean("tl_snap", es.snapping).apply()
                        es.message = if (es.snapping) "Snapping on" else "Snapping off"
                    },
                    onLayerTime = { id, a, b ->
                        val i = layers.indexOfFirst { it.id == id }
                        if (i >= 0) {
                            val o = layers[i]
                            layers[i] = o.copy(wholeVideo = false, startMs = a, endMs = b)
                            // A grouped item moved (not trimmed): the rest of its group moves the same way.
                            if (o.group != null && !o.wholeVideo && b - a == o.endMs - o.startMs && a != o.startMs) moveGroup(o.group, a - o.startMs, o.id)
                        }
                    },
                    onLayerOrder = { id, to ->
                        val i = layers.indexOfFirst { it.id == id }
                        if (i >= 0 && to in layers.indices && to != i) { val l = layers.removeAt(i); layers.add(to, l) }
                    },
                    onAudioTime = { id, st, off, len ->
                        val m = es.music
                        val o = if (m != null && m.id == id) m else extraAudio.firstOrNull { it.id == id }
                        if (m != null && m.id == id) es.music = m.copy(startMs = st, offsetMs = off, lengthMs = len)
                        else { val i = extraAudio.indexOfFirst { it.id == id }; if (i >= 0) extraAudio[i] = extraAudio[i].copy(startMs = st, offsetMs = off, lengthMs = len) }
                        if (o?.group != null && off == o.offsetMs && len == o.lengthMs && st != o.startMs) moveGroup(o.group, st - o.startMs, o.id)
                    },
                )
            } }

            val showStrip = com.dronedirector.veyrocutpro.ui.panelOn("ve.mediastrip")
            val stripPane = rememberPane("ve_strip_h", es.paneVersion)
            val stripH = if (stripPane.dp < 0f) 70f else stripPane.dp
            val tablet = androidx.compose.ui.platform.LocalConfiguration.current.smallestScreenWidthDp >= 600
            val landscape = maxWidth > maxHeight
            // Phones and tablets, any rotation: the media strip is the first tab of the tools panel.
            // (Monitors use their own layouts; with the tools panel hidden it sits above the timeline.)
            val stripInTools = mon == com.dronedirector.veyrocutpro.ui.MonitorClass.NONE && showInspector
            val stripView = @Composable { h: androidx.compose.ui.unit.Dp, vertical: Boolean -> com.dronedirector.veyrocutpro.ui.ForceDark {
            MediaStrip(
                thumbHeight = h,
                vertical = vertical,
                modifier = if (vertical) Modifier.fillMaxSize() else Modifier,
                reveal = es.revealUri, revealTick = es.revealTick,
                onColor = { c ->
                    val i = clips.indexOfFirst { it.uri == c.uri }
                    if (i < 0) es.message = "\"${c.name}\" isn't on the timeline yet — add it first" else openColor(i)
                },
                onRevealTimeline = { c ->
                    val i = clips.indexOfFirst { it.uri == c.uri }
                    if (i < 0) es.message = "\"${c.name}\" isn't on the timeline yet"
                    else {
                        es.selected = i; seekGlobal(clipStarts(clips).getOrNull(i) ?: 0L)
                        val n = clips.count { it.uri == c.uri }
                        es.message = "Clip ${i + 1} on the timeline" + (if (n > 1) " (used $n times — this is the first)" else "")
                    }
                },
                onZoom = { step -> stripPane.dp = (stripH + step).coerceIn(40f, 240f); stripPane.save() },
                    library = library, uses = { c -> clips.count { it.uri == c.uri } }, ratings = ratings,
                    bins = bins, binNames = binNames,
                    onRate = { list, r -> list.forEach { c -> if (r == 0) ratings.remove(c.uri.toString()) else ratings[c.uri.toString()] = r } },
                    onAppend = { list -> list.forEach { threePoint(it.copy(startMs = 0, endMs = it.durationMs), 2) } },
                    onInsert = { threePoint(it.copy(startMs = 0, endMs = it.durationMs), 0) },
                    onOverwrite = { threePoint(it.copy(startMs = 0, endMs = it.durationMs), 1) },
                    onSource = { es.sourceClip = it.copy(startMs = 0, endMs = it.durationMs) },
                    onRemove = { list ->
                        val used = list.filter { c -> clips.any { it.uri == c.uri } }
                        library.removeAll { c -> c in list && c !in used }
                        if (used.isNotEmpty()) es.message = "${used.size} clip(s) are on the timeline — delete them in the Media page to remove them"
                    },
                    onImport = { libPick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                    onCamera = { cameraAsk = true },
                    onImportFolder = { try { libFolder.launch(null) } catch (_: Throwable) {} },
                    onDragOver = { at -> es.tlBounds.contains(at) },
                    onDrop = { c, at -> if (es.tlBounds.contains(at)) dropAt(c, at.x) },
                )
            } }
            val inspector = @Composable { mod: Modifier ->
                Column(mod.background(Ed.Panel)) {
                    // Landscape / tablet: the media strip is the first tab, on the left.
                    val stripTab = stripInTools && showStrip
                    LaunchedEffect(stripTab) { if (!stripTab && es.page == Page.STRIP) es.page = Page.MEDIA }
                    // Reveal in project: the media strip tab is shown when the strip lives in the tools panel.
                    var revealSeen by remember { mutableStateOf(es.revealTick) }
                    LaunchedEffect(es.revealTick, stripTab) { if (es.revealTick != revealSeen && stripTab) { revealSeen = es.revealTick; es.page = Page.STRIP } }
                    val pages = (if (stripTab) listOf(Page.STRIP) else emptyList()) + (if (com.dronedirector.veyrocutpro.camera.SimpleMode.on)
                        listOf(Page.MEDIA, Page.PROXY, Page.EDIT, Page.TEXT, Page.TRANSITIONS, Page.COLOR, Page.LAYERS, Page.AUDIO, Page.DELIVER)
                    else Page.entries.filter { it != Page.STRIP })
                    // The tools bar: the pages, with "Reveal in Project" just before Deliver (it scrolls with them).
                    Row(
                        Modifier.fillMaxWidth().hintedScroll(horizontal = true).padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                      for (pg in pages) {
                        if (pg == Page.DELIVER) RevealMenu(clips.getOrNull(es.selected)) {
                            clips.getOrNull(es.selected)?.let { revealInLibrary(it) }
                        }
                        val sel = pg == es.page
                        Text(
                            pg.label, color = if (sel) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(if (sel) Pro.Amber else Pro.Fg.copy(alpha = 0.08f))
                                .clickable { es.page = pg }.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                      }
                    }
                    if (clips.isNotEmpty() && (es.page == Page.EDIT || es.page == Page.COLOR || es.page == Page.AUDIO)) CopyPasteBar(
                        video = true,
                        onCopy = {
                            clips.getOrNull(es.selected)?.let { c ->
                                EditClipboard.copy(context, CopiedSettings(
                                    fromVideo = true, grade = c.grade, detail = c.detail, reframe = c.reframe, stab = c.stab,
                                    audio = c.audio, volume = c.volume, muted = c.muted, speed = c.speed,
                                ))
                                es.message = "Clip ${es.selected + 1} settings copied — select another clip and tap PASTE SETTINGS"
                            }
                        },
                        onPaste = { parts, all ->
                            if (all) {
                                for (i in clips.indices) clips[i] = EditClipboard.pasteOnClip(clips[i], parts)
                                es.message = "Settings pasted on all ${clips.size} clips"
                            } else {
                                update(es.selected) { EditClipboard.pasteOnClip(it, parts) }
                                es.message = "Settings pasted on clip ${es.selected + 1}"
                            }
                        },
                    )
                    com.dronedirector.veyrocutpro.ui.FoldColumn(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 4.dp), scope = "video") {
                        val sc = clips.getOrNull(es.selected)
                        when (es.page) {
                            Page.STRIP -> com.dronedirector.veyrocutpro.ui.FoldColumn {
                                stripView(stripH.dp, false)
                                ResizeHandle(true,
                                    onDrag = { d -> stripPane.dp = (stripH + d).coerceIn(40f, 240f) },
                                    onEnd = { stripPane.save() }, onReset = { stripPane.reset() })
                                Text("Hold the divider and drag to make the thumbnails bigger or smaller. Drag a clip onto the timeline with a mouse, or pick it and use Append / Insert.", color = Pro.Dim, fontSize = 11.sp)
                            }
                            Page.MEDIA -> com.dronedirector.veyrocutpro.ui.FoldColumn {
                            SectionTitle("LIBRARY & IMPORT")
                            Text("Browse every video on the device by day, month and year, with stars, flags, colour labels and smart collections — or copy footage in from a memory card, USB drive or camera.", color = Pro.Dim, fontSize = 11.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.hintedScroll(horizontal = true).padding(vertical = 4.dp)) {
                                PillChip("🎞 Video library", false, accentText = true) { libOpen = 1 }
                                PillChip("⇩ Import from card / camera / USB", false, accentText = true) { libOpen = 2 }
                            }
                            SectionTitle("EDIT TEMPLATES")
                            Text("One tap to a finished-looking edit of your clips: cut on the music's beat (add music first), transitions, a title and more. Everything stays editable; undo brings your edit back.", color = Pro.Dim, fontSize = 11.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.hintedScroll(horizontal = true).padding(vertical = 4.dp)) {
                                EditTemplates.ALL.forEach { t ->
                                    Column(Modifier.width(160.dp).clip(RoundedCornerShape(10.dp)).background(Ed.Raised).clickable { applyTemplate(t) }.padding(10.dp)) {
                                        Text(t.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text(t.hint, color = Pro.Dim, fontSize = 10.sp, maxLines = 4)
                                    }
                                }
                            }
                            TimelinesSection(
                                es.timelineName, otherTimelines.map { it.name },
                                onRename = { n -> val v = n.trim().take(40); if (v.isNotEmpty() && otherTimelines.none { it.name == v }) es.timelineName = v },
                                onOpen = { i -> otherTimelines.getOrNull(i)?.let { openTimeline(it) } },
                                onNew = { openTimeline(SavedTimeline(uniqueTimelineName("Timeline ${otherTimelines.size + 2}"), deliver = es.deliver)) },
                                onDuplicate = { openTimeline(currentTimeline().copy(name = uniqueTimelineName("${es.timelineName} copy"))) },
                                onDelete = { i -> if (i in otherTimelines.indices) { val n = otherTimelines[i].name; otherTimelines.removeAt(i); es.message = "Deleted \"$n\"" } },
                            )
                            if (markers.isNotEmpty()) {
                                SectionTitle("MARKERS") { markers.clear(); markerNotes.clear() }
                                markers.toList().forEach { m ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(tc(m), style = Pro.Value.copy(fontSize = 12.sp, color = Pro.Amber), modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { seekGlobal(m) }.padding(6.dp))
                                        androidx.compose.material3.OutlinedTextField(
                                            value = markerNotes[m] ?: "", onValueChange = { v -> if (v.isBlank()) markerNotes.remove(m) else markerNotes[m] = v.take(120) },
                                            placeholder = { Text("Note", color = Pro.Dim, fontSize = 12.sp) }, singleLine = true,
                                            textStyle = androidx.compose.ui.text.TextStyle(color = com.dronedirector.veyrocutpro.ui.Pro.Fg, fontSize = 13.sp),
                                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                                        )
                                        Text("✕", color = Pro.Red, fontSize = 13.sp, modifier = Modifier.clickable { markers.remove(m); markerNotes.remove(m) }.padding(8.dp))
                                    }
                                }
                                Text("Tap a time to jump there. Notes travel with the project and into the FCPXML export.", color = Pro.Dim, fontSize = 11.sp)
                            }
                            MediaPage(
                                clips, onAddGallery, onAddFiles,
                                onCamera = { cameraAsk = true },
                                audioPool = audioPool,
                                audioUses = { a -> (if (es.music?.uri == a.uri) 1 else 0) + extraAudio.count { it.uri == a.uri } },
                                onAddAudio = { pickAudio.launch(arrayOf("audio/*")) },
                                onAddNas = { nasPool = true },
                                onMulticam = { es.showMulticam = true },
                                bins = bins.toMap(), binNames = binNames.toList(),
                                onNewBin = { b -> if (b !in binNames) binNames.add(b) },
                                onSetBin = { c, b -> if (b == null) bins.remove(c.uri.toString()) else bins[c.uri.toString()] = b },
                                onSource = { es.sourceClip = it },
                                onPickSource = { pickSource.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                                missing = missing.toList(),
                                onRelink = { c -> es.relinkFrom = c.uri.toString(); try { relink.launch(arrayOf("video/*")) } catch (_: Throwable) { es.relinkFrom = null } },
                                onLibraryAdd = { sec, t ->
                                    layerScope.launch {
                                        val uri = withContext(Dispatchers.IO) { SoundLibrary.file(context, sec, t) }
                                        if (uri == null) { es.message = "${t.title} is still being downloaded (first-time download) — try again in a moment"; return@launch }
                                        val a = audioPool.firstOrNull { it.uri == uri } ?: PoolAudio(uri, t.title, t.durationMs).also { audioPool.add(it) }
                                        val total = timelineLength(clips)
                                        val head = ((clipStarts(clips).getOrNull(es.current) ?: 0L) + (clips.getOrNull(es.current)?.let { it.tlOffsetOf(it.startMs + es.posMs) } ?: es.posMs)).coerceIn(0, total)
                                        if (es.music == null) {
                                            es.music = MusicTrack(a.uri, a.name, a.durationMs, startMs = head)
                                            musicPlayer.setMediaItem(MediaItem.fromUri(a.uri)); musicPlayer.prepare()
                                            es.message = "${a.name} added as the music track"
                                        } else {
                                            extraAudio.add(MusicTrack(a.uri, a.name, a.durationMs, volume = 1f, fadeInMs = 0, fadeOutMs = 300, startMs = head))
                                            es.message = "${a.name} added to the timeline at ${tc(head)}"
                                        }
                                    }
                                },
                                onPlaceAudio = { a ->
                                    val total = timelineLength(clips)
                                    val head = ((clipStarts(clips).getOrNull(es.current) ?: 0L) + (clips.getOrNull(es.current)?.let { it.tlOffsetOf(it.startMs + es.posMs) } ?: es.posMs)).coerceIn(0, total)
                                    if (es.music == null) {
                                        es.music = MusicTrack(a.uri, a.name, a.durationMs, startMs = head)
                                        musicPlayer.setMediaItem(MediaItem.fromUri(a.uri)); musicPlayer.prepare()
                                        es.message = "${a.name} added as the music track"
                                    } else {
                                        extraAudio.add(MusicTrack(a.uri, a.name, a.durationMs, volume = 1f, fadeInMs = 0, fadeOutMs = 300, startMs = head))
                                        es.message = "${a.name} added to the timeline at ${tc(head)}"
                                    }
                                },
                                onRemoveAudio = { a ->
                                    audioPool.remove(a)
                                    extraAudio.removeAll { it.uri == a.uri }
                                    if (es.music?.uri == a.uri) { es.music = null; musicPlayer.stop() }
                                },
                                proxyState = { c -> if (MediaId.of(c) in proxyMedia) proxyStatus(c) else null },
                                onProxyToggle = { c -> if (MediaId.of(c) in proxyMedia) es.page = Page.PROXY else { addToProxy(c); es.message = "${c.name} added to Proxy" } },
                                onRemove = { c ->
                                    val k = c.uri.toString()
                                    removeFromProxy(MediaId.of(c))
                                    clips.removeAll { it.uri.toString() == k }
                                    library.removeAll { it.uri.toString() == k }
                                    es.selected = es.selected.coerceAtMost(clips.lastIndex).coerceAtLeast(0)
                                    es.current = es.current.coerceAtMost(clips.lastIndex).coerceAtLeast(0)
                                    es.message = "${c.name} removed from the project"
                                },
                            )
                            GeneratorTools(es.genBusy, onMake = { k, a, b, s -> makeGenerator(k, a, b, s) }, onCancel = { es.genCancel?.invoke(); es.genCancel = null; es.genBusy = null })
                            }
                            Page.PROXY -> ProxyPage(
                                media = clips.filter { it.nested == null }.distinctBy { MediaId.of(it) },
                                inProxy = proxyMedia.toList(),
                                settings = es.proxySettings,
                                status = { proxyStatus(it) },
                                progress = { proxyProgress[MediaId.of(it)] },
                                busy = proxyQueue.isNotEmpty(),
                                onSettings = { ns ->
                                    val qChanged = ns.quality != es.proxySettings.quality || ns.hevc != es.proxySettings.hevc
                                    es.proxySettings = ns; ProxySettings.save(context, ns)
                                    if (qChanged) { proxyCancelled.clear(); proxyFail.clear() }
                                    es.message = if (!ns.enabled) "Proxy off — the editor plays your original files" else if (qChanged) "Proxy quality ${ns.quality.label} — updating proxies in the background" else "Proxy on — media in the Proxy section plays from its proxy"
                                },
                                onAdd = { addToProxy(it) },
                                onAddAll = { clips.filter { it.nested == null }.forEach { addToProxy(it) }; es.message = "All media added to Proxy" },
                                onRemove = { removeFromProxy(MediaId.of(it)) },
                                onCancel = { cancelProxy(MediaId.of(it)) },
                                onCancelAll = { proxyQueue.toList().forEach { cancelProxy(it) }; es.message = "Proxy generation cancelled — those clips play from the originals" },
                                onRetry = { c -> val id = MediaId.of(c); proxyFail.remove(id); proxyBad.remove(id); proxyCancelled.remove(id); if (id !in proxyQueue) proxyQueue.add(id) },
                                onClearFiles = {
                                    proxyQueue.toList().forEach { cancelProxy(it) }
                                    proxies.clear(); proxyStale.clear(); proxyCancelled.clear()
                                    val freed = ProxyMaker.clearAll(context)
                                    es.message = "Proxy files deleted (${freed / 1_000_000} MB) — tap GENERATE to make them again"
                                    proxyMedia.forEach { if (it !in proxyCancelled) proxyCancelled.add(it) }
                                },
                            ).also {
                                val heavy = clips.filter { RenderCache.heavy(it) }.distinctBy { RenderCache.key(it) }
                                RenderCacheSection(
                                    heavy.map { c -> Triple(c.name, cachedFor(c) != null, cacheProgress[RenderCache.key(c)]) },
                                    running = wx.cacheCancel != null || cacheProgress.isNotEmpty(),
                                    onRenderAll = {
                                        heavy.filter { RenderCache.key(it) !in cacheReady }.forEach { cacheProgress[RenderCache.key(it)] = 0 }
                                        renderCacheNext()
                                        if (cacheProgress.isEmpty()) es.message = "Nothing to render — every heavy clip is cached"
                                    },
                                    onStop = { wx.cacheCancel?.invoke(); wx.cacheCancel = null; cacheProgress.clear(); es.message = "Render cache stopped" },
                                    onClear = { wx.cacheCancel?.invoke(); wx.cacheCancel = null; cacheProgress.clear(); cacheReady.clear(); val n = RenderCache.clearAll(context); es.message = "Render cache deleted (${n / 1_000_000} MB)" },
                                )
                            }
                            Page.TEXT -> com.dronedirector.veyrocutpro.ui.FoldColumn {
                              TextToolbox(globalMs, timelineLength(clips)) { l ->
                                layers.add(l); es.selectedLayer = l.id
                                goToSection(Page.LAYERS, "SELECTED TEXT")
                                es.message = "Added — type your words in the text box; font, effect and colours are here too"
                              }
                              TextEditPage(
                                words = wx.tWords.toList(), clips = clips.toList(), currentClipId = clips.getOrNull(es.current)?.id, playSrcMs = clips.getOrNull(es.current)?.let { it.startMs + es.posMs } ?: 0L,
                                model = wx.tModel, onModel = { wx.tModel = it }, hasModel = Transcriber.hasModel(context, wx.tModel), downloading = wx.tDl,
                                onDownload = { downloadModel() }, onCancelDownload = { wx.tDlCancel = true },
                                lang = wx.tLang, onLang = { wx.tLang = it }, status = wx.tStatus,
                                onTranscribe = { transcribeAll() }, onStop = { Transcriber.stop(); wx.tJob?.cancel(); wx.tJob = null; wx.tStatus = null },
                                onWordCaptions = { makeWordCaptions(it) },
                                onToggle = { w -> val i = wx.tWords.indexOf(w); if (i >= 0) wx.tWords[i] = w.copy(deleted = !w.deleted) },
                                onSeekWord = { w ->
                                    val ci = clips.indexOfFirst { it.id == w.clipId }
                                    if (ci >= 0) seekGlobal((clipStarts(clips).getOrNull(ci) ?: 0L) + clips[ci].tlOffsetOf(w.startMs))
                                },
                                onFillers = { opt ->
                                    var n = 0
                                    for (i in Transcriber.findFillers(wx.tWords.toList(), opt)) if (!wx.tWords[i].deleted) { wx.tWords[i] = wx.tWords[i].copy(deleted = true); n++ }
                                    es.message = if (n == 0) "No filler words found" else "$n filler word(s) marked in red — check them, then tap APPLY CUTS"
                                },
                                onRestore = { for (i in wx.tWords.indices) if (wx.tWords[i].deleted) wx.tWords[i] = wx.tWords[i].copy(deleted = false) },
                                onApply = { applyTextCuts() },
                            )
                            }
                            Page.TRANSITIONS -> TransitionsPanel(
                                clips, es.selected, thumbs,
                                onChange = { t -> wf.pick.applyTo(clips, es.selected) { it.copy(transition = t) } },
                                onApplyAll = { t -> for (i in 0 until clips.lastIndex) clips[i] = clips[i].copy(transition = t); es.message = "${t.type.label} on every cut" },
                                onApplyAllAudio = { t ->
                                    for (i in 0 until clips.lastIndex) clips[i] = clips[i].copy(transition = clips[i].transition.copy(audio = t.audio, audioMs = t.audioMs, sfx = t.sfx, sfxVol = t.sfxVol, userSfx = t.userSfx))
                                    val snd = UserSfxLibrary.find(context, t.userSfx)?.name ?: t.sfx?.label
                                    es.message = "${t.audioType.label}" + (snd?.let { " + $it" } ?: "") + " on every cut"
                                },
                                onPreview = {
                                    val st = clipStarts(clips)
                                    if (es.selected + 1 < clips.size) { seekGlobal((st[es.selected + 1] - 1500).coerceAtLeast(st[es.selected])); if (!deck.cur.isPlaying) deck.cur.play() }
                                },
                            )
                            Page.EDIT -> if (sc != null) com.dronedirector.veyrocutpro.ui.FoldColumn {
                                if (sc.still != null) StillSection(sc, stillBusy, onSeconds = { s -> setStillLength(es.selected, s) }, onZoom = { z -> setStillZoom(es.selected, z) }, onEditPhoto = { roundTrip = sc.id })
                                EditPage(
                                sc, es.selected, clips.size, es.posMs, es.deliver, { es.deliver = it },
                                onChange = { f -> if (es.videoLocked) es.message = "The video track is locked" else wf.pick.applyTo(clips, es.selected, f) },
                                onSplit = { splitAtPlayhead() },
                                onFreeze = { freezeAtPlayhead() },
                                onReverse = { reverseClip(es.selected) },
                                onDetach = { detachAudio(es.selected) },
                                onRemoveSilence = { all, db, ms -> removeSilences(all, db, ms) },
                                onSlowMo = { aiSlowMo(es.selected) },
                                flowProgress = wx.flowProgress, onFlow = { f, sp, sh -> flowRetime(es.selected, f, sp, sh) }, onStopFlow = { wx.flowJob?.cancel(); wx.flowJob = null; wx.flowProgress = -1 },
                                onReframe = { a -> autoReframe(a) },
                                onDuplicate = { if (es.videoLocked) es.message = "The video track is locked" else clips.add(es.selected + 1, sc.copy(id = java.util.UUID.randomUUID().toString())) },
                                onDelete = {
                                    if (es.videoLocked) { es.message = "The video track is locked"; return@EditPage }
                                    es.deleteAsk = es.selected
                                },
                                onMove = { dir ->
                                    if (es.videoLocked) { es.message = "The video track is locked"; return@EditPage }
                                    val to = es.selected + dir
                                    if (to in clips.indices) { val c = clips.removeAt(es.selected); clips.add(to, c); es.selected = to }
                                },
                                onTrimTool = { mode, d -> trimTool(mode, es.selected, d) },
                                frameMs = 1000L / (if (es.deliver.fps > 0) es.deliver.fps else 30),
                                onNest = { n -> nestClips(es.selected, n) },
                                onUnNest = { unNest(es.selected) },
                                nesting = es.nesting, nestProgress = es.progress,
                                )
                                ProEditTools(
                                    clips = clips.toList(), selectedIdx = es.selected, sceneProgress = es.sceneProgress,
                                    onDetectScenes = { detectScenes() }, onStopScenes = { es.sceneJob?.cancel(); es.sceneJob = null; es.sceneProgress = -1 },
                                    hlProgress = es.hlProgress, onHighlights = { makeHighlights(it, wx.hlBeat) }, hlBeat = wx.hlBeat, onHlBeat = { wx.hlBeat = it }, onStopHighlights = { es.hlJob?.cancel(); es.hlJob = null; es.hlProgress = -1 },
                                    picked = multi.toList(),
                                    onPick = { id -> if (id in multi) multi.remove(id) else multi.add(id) },
                                    onPickAll = { if (multi.size == clips.size) multi.clear() else { multi.clear(); multi.addAll(clips.map { it.id }) } },
                                    onBatch = { action ->
                                        if (es.videoLocked) { es.message = "The video track is locked"; return@ProEditTools }
                                        val ids = multi.toSet()
                                        when (action) {
                                            "delete" -> { clips.removeAll { it.id in ids }; multi.clear(); es.selected = es.selected.coerceIn(0, (clips.size - 1).coerceAtLeast(0)); es.current = es.current.coerceIn(0, (clips.size - 1).coerceAtLeast(0)); if (clips.isNotEmpty()) load(es.current, 0, false) }
                                            "mute" -> for (i in clips.indices) if (clips[i].id in ids) clips[i] = clips[i].copy(muted = !clips[i].muted)
                                            "grade" -> clips.getOrNull(es.selected)?.let { src -> for (i in clips.indices) if (clips[i].id in ids) clips[i] = clips[i].copy(grade = src.grade, detail = src.detail) }
                                            "duplicate" -> { val add = clips.filter { it.id in ids }.map { it.copy(id = java.util.UUID.randomUUID().toString()) }; clips.addAll(add) }
                                            else -> action.removePrefix("speed:").toFloatOrNull()?.let { sp -> for (i in clips.indices) if (clips[i].id in ids) clips[i] = clips[i].copy(speed = sp, ramp = null, rampName = null) }
                                        }
                                        es.message = "Done for ${ids.size} clip(s)"
                                    },
                                )
                                val sk = Stabilizer.keyOf(sc)
                                ReframePanel(
                                    sc, es.cropEditEnd, es.cropMode, stabProgress[sk], motions[sk] != null,
                                    onEditEnd = { e ->
                                        es.cropEditEnd = e
                                        // Show the frame being edited.
                                        if (es.selected == es.current) deck.cur.seekTo(if (e) (sc.endMs - sc.startMs - 50).coerceAtLeast(0) else 0)
                                    },
                                    onCropMode = { on ->
                                        es.cropMode = on
                                        if (on && es.selected != es.current) load(es.selected, 0, false)
                                        if (on) deck.cur.pause()
                                    },
                                    onChange = { f -> wf.pick.applyTo(clips, es.selected, f) },
                                    onAnalyze = { analyze(sc) },
                                    playheadRel = if (es.selected == es.current) es.posMs else 0L,
                                    onSeekRel = { t ->
                                        val st = clipStarts(clips)
                                        seekGlobal((st.getOrNull(es.selected) ?: 0L) + sc.tlOffsetOf(sc.startMs + t))
                                    },
                                )
                                SectionTitle("ANAMORPHIC DESQUEEZE")
                                val sq = listOf(1f, 1.33f, 1.5f, 1.55f, 1.8f, 2f)
                                Chips(sq.map { if (it == 1f) "Off" else "${it}×" }, sq.indexOfFirst { it == (sc.desqueeze ?: 1f) }.coerceAtLeast(0)) { i ->
                                    update(es.selected) { it.copy(desqueeze = sq[i].takeIf { v -> v > 1f }) }
                                }
                                Text("For clips filmed with an anamorphic lens or adapter: stretches the picture back to its real shape (preview and export).", color = Pro.Dim, fontSize = 11.sp)
                            }
                            Page.COLOR -> if (sc != null) ColorPage(sc, es.colorTab, { es.colorTab = it }, maskTools = maskTools, posUs = (if (es.selected == es.current) sc.startMs + es.posMs else sc.startMs) * 1000, onChange = { f -> wf.pick.applyTo(clips, es.selected, f) },
                                onTrackPen = { m -> trackPen(es.selected, m.id) }, penTracking = wx.penTrackingId,
                                onErase = {
                                    val st = clipStarts(clips).getOrNull(es.selected) ?: 0L
                                    val src = if (globalMs in st until st + sc.lengthMs) sc.srcAtOffset(globalMs - st) else sc.startMs
                                    es.magicPick = MagicTarget(clipId = sc.id, uri = sc.uri, srcMs = src, initial = null, remove = true)
                                },
                                eraseProgress = es.eraseProgress, eraseStatus = es.eraseStatus, onStopErase = { es.eraseJob?.cancel(); es.eraseJob = null; es.eraseProgress = null; es.eraseStatus = null },
                                onDepth = { analyseDepth(es.selected) }, depthProgress = wx.depthProgress, depthStatus = wx.depthStatus, onStopDepth = { wx.depthJob?.cancel(); wx.depthJob = null; wx.depthProgress = null },
                                clipNames = clips.map { it.name }, selfIdx = es.selected, matchBusy = es.matchBusy, onMatch = { matchShot(it) },
                                onMagic = {
                                    val st = clipStarts(clips).getOrNull(es.selected) ?: 0L
                                    val src = if (globalMs in st until st + sc.lengthMs) sc.srcAtOffset(globalMs - st) else sc.startMs
                                    es.magicPick = MagicTarget(clipId = sc.id, uri = sc.uri, srcMs = src, initial = sc.bg?.magic)
                                },
                                magicProgress = magicProgress[sc.id], onStopMagic = { magicStops[sc.id]?.set(true); magicProgress.remove(sc.id) },
                                onCopyToAll = {
                                val g = sc.grade; val d = sc.detail; val fl = sc.film
                                for (i in clips.indices) clips[i] = clips[i].copy(grade = g, detail = d, film = fl)
                                es.message = "Grade copied to all clips"
                            })
                            Page.LAYERS -> LayersPage(
                                layers = layers, selectedId = es.selectedLayer, totalMs = totalMs, playheadMs = globalMs,
                                onSelect = { es.selectedLayer = it },
                                onAdd = { l -> layers.add(l); es.selectedLayer = l.id },
                                onAddLogo = { pickLogo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                onAddVideo = { es.askVideoLayer = true },
                                onAddImage = { pickLayerImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                onAddLottie = { pickLottie.launch(arrayOf("application/json", "application/zip", "application/octet-stream", "*/*")) },
                                onUpdate = { l ->
                                    val i = layers.indexOfFirst { it.id == l.id }
                                    if (i >= 0) {
                                        val o = layers[i]
                                        // A locked layer only takes lock / show / name changes.
                                        if (o.locked && l.locked && l.copy(visible = o.visible, name = o.name) != o) es.message = "This layer is locked — tap its lock to edit"
                                        else layers[i] = o.editedAt(globalMs, l)
                                    }
                                },
                                onSeek = { seekGlobal(it) },
                                onCaptions = { lang, clipsOnly, style -> makeCaptions(lang, clipsOnly, style) },
                                onImportSubs = { pickSubs.launch(arrayOf("*/*")) },
                                onTranslate = { to -> translateCaptions(to) }, translateStatus = es.translateStatus,
                                onDub = { l, m, mu -> dubCaptions(l, m, mu) }, dubStatus = wx.dubStatus,
                                telemetryShow = es.telemetryShow,
                                onTelemetryShow = { es.telemetryShow = it },
                                onTelemetry = { es.srtForMap = false; pickSrt.launch(arrayOf("*/*")) },
                                onFlightPath = { es.srtForMap = true; pickSrt.launch(arrayOf("*/*")) },
                                onTextBehind = { textBehindSubject() },
                                visSources = run {
                                    // Every sound in the project the visualizer can follow.
                                    val out = ArrayList<VisSource>()
                                    es.music?.let { out += VisSource("Music · " + it.name, it.uri, it.offsetMs - it.startMs) }
                                    extraAudio.forEach { out += VisSource(it.name, it.uri, it.offsetMs - it.startMs) }
                                    clips.getOrNull(es.current)?.let { c -> out += VisSource("Clip ${es.current + 1} sound", c.uri, c.startMs - (clipStarts(clips).getOrNull(es.current) ?: 0L)) }
                                    out
                                },
                                onVisualizer = {
                                    // The music track if there is one, else the first audio track, else the selected clip's own sound.
                                    val m = es.music ?: extraAudio.firstOrNull()
                                    val c = clips.getOrNull(es.current)
                                    val total = timelineLength(clips).coerceAtLeast(1)
                                    val src = m?.uri ?: c?.uri
                                    if (src == null) es.message = "Add music or a clip with sound first"
                                    else {
                                        val base = clipStarts(clips).getOrNull(es.current) ?: 0L
                                        val delta = if (m != null) m.offsetMs - m.startMs else (c!!.startMs - base)
                                        val from = if (m != null) m.startMs.coerceIn(0, total) else base
                                        val to = if (m != null) (m.startMs + m.playLength(total)).coerceIn(from + 1000, maxOf(total, from + 1000)) else base + (c?.lengthMs ?: 1000L)
                                        val l = Layer(kind = LayerKind.FX, fx = FxLayers.VIS, text = "Visualizer (analysing)", imageUri = src, fxD = delta.toFloat(), size = 0.22f, x = 0.5f, y = 0.84f,
                                            fxA = 0.9f, fxB = 0f, fxC = 0f, color = 0xFFFFB300.toInt(), shadow = false, startMs = from, endMs = to)
                                        layers.add(l); es.selectedLayer = l.id
                                        es.message = "Analysing the " + (if (m != null) "music" else "clip's sound") + "…"
                                        layerScope.launch {
                                            val ok = withContext(Dispatchers.IO) { Spectrum.load(context, src) } != null
                                            val i = layers.indexOfFirst { it.id == l.id }
                                            if (i >= 0) layers[i] = layers[i].copy(text = "Visualizer")  // redraw with the analysis
                                            es.message = if (ok) "Visualizer synced to the " + (if (m != null) "music" else "clip's sound") + " — pick a style in its settings" else "Couldn't read that sound"
                                        }
                                    }
                                },
                                onTrack = { trackLayer(it) }, trackingId = es.trackingId,
                                onPin = { id, p -> val i = layers.indexOfFirst { it.id == id }; if (i >= 0) { if (layers[i].locked) es.message = "This layer is locked — tap its lock to edit" else layers[i] = layers[i].copy(pin = p) } },
                                onPlanarTrack = { planarTrack(it) }, pinTrackingId = wx.pinTrackingId,
                                frameAspect = outputSize(clips, es.deliver).let { (w, h) -> w.toFloat() / h.coerceAtLeast(1) },
                                onSplit = { splitScreen(it) },
                                onMagicLayer = { id ->
                                    layers.firstOrNull { it.id == id }?.let { l ->
                                        val u = l.videoUri ?: return@let
                                        val src = (l.videoStartMs + (globalMs - (if (l.wholeVideo) 0L else l.startMs))).coerceIn(l.videoStartMs, (l.videoDurationMs - 1).coerceAtLeast(l.videoStartMs))
                                        es.magicPick = MagicTarget(layerId = id, uri = u, srcMs = src, initial = l.layerMagic)
                                    }
                                },
                                magicLayerProgress = magicProgress.toMap(), onStopMagicLayer = { id -> magicStops[id]?.set(true); magicProgress.remove(id) },
                                onDelete = { id ->
                                    if (layers.any { it.id == id && it.locked }) es.message = "This layer is locked — tap its lock to delete it"
                                    else { layers.removeAll { it.id == id }; es.selectedLayer = layers.lastOrNull()?.id }
                                },
                                onMove = { id, dir ->
                                    val i = layers.indexOfFirst { it.id == id }; val to = i + dir
                                    if (i >= 0 && to in layers.indices) { val l = layers.removeAt(i); layers.add(to, l) }
                                },
                            )
                            Page.AUDIO -> com.dronedirector.veyrocutpro.ui.FoldColumn {
                                AudioPage(
                                sc, es.deliver, es.music, es.playing,
                                mixer = MixerHooks(
                                    es.deliver, { es.deliver = it }, es.videoMuted, { es.videoMuted = it }, es.soloTrack, { es.soloTrack = it },
                                    { audioFx.maxOf { it.peak } }, { musicFxProc.peak }, { id -> extraFx[id]?.peak ?: 0f },
                                ),
                                onTts = { t, v, sp -> makeTts(t, v, sp) }, ttsProgress = wx.ttsProgress, ttsStatus = wx.ttsStatus, onStopTts = { wx.ttsJob?.cancel(); wx.ttsJob = null; wx.ttsProgress = null; wx.ttsStatus = null },
                                onChange = { f -> wf.pick.applyTo(clips, es.selected, f) },
                                onPickMusic = { pickMusic.launch(arrayOf("audio/*")) },
                                onMusic = { es.music = it; if (it == null) musicPlayer.stop() },
                                extra = extraAudio,
                                onExtra = { id, t ->
                                    val i = extraAudio.indexOfFirst { it.id == id }
                                    if (i >= 0) { if (t == null) extraAudio.removeAt(i) else extraAudio[i] = t }
                                },
                                posInClip = if (es.selected == es.current) es.posMs else 0L,
                                recording = wx.voiceRec != null, recordMs = wx.voiceMs,
                                onVoice = { if (wx.voiceRec == null) startVoice() else stopVoice() },
                                onBeats = { t -> addBeatMarkers(t) },
                                onSync = { t -> syncAudioTo(t) },
                                onSeparate = { separateVoice(es.selected) }, separateStatus = wx.sepStatus,
                            )
                                MusicTools(hasMusic = es.music != null, onFit = { fitMusicToVideo() }, onAutoEdit = { autoEditToBeat(it) })
                            }
                            Page.DELIVER -> DeliverPage(
                                es.deliver, clips, es.exporting, { es.deliver = it }, ::startExport,
                                onReviewNotes = { l -> for ((t, n) in l) { if (t !in markers) markers.add(t); if (n.isNotBlank()) markerNotes[t] = n }; markers.sort(); es.message = "${l.size} client note(s) added as markers" },
                                castSlot = if (castOnPhone) ({ com.dronedirector.veyrocutpro.ui.CastButton(castSource, sound = com.dronedirector.veyrocutpro.camera.CastFeed.Sound.APP) }) else null, playheadMs = globalMs, onSaveFrame = { saveStill() },
                                markers = markers.toList(), projectName = es.projectName, layers = layers.toList(), markerNotes = markerNotes.toMap(),
                                smartWhy = if (es.deliver.smartRender) smartRenderBlocker(context, clips.let { l -> if (es.videoMuted) l.map { it.copy(muted = true) } else l }, es.deliver, layers.toList(), es.music, extraAudio.toList()) else null,
                                queue = exportQueue.toList(), queueAt = es.queueAt,
                                onQueueAdd = { exportQueue.add(es.deliver); es.message = "Added to the queue (${exportQueue.size})" },
                                onExportBoth = {
                                    // The same edit as 16:9 and as 9:16 (filled, centred), one after the other.
                                    if (!es.exporting) {
                                        exportQueue.clear()
                                        exportQueue.add(es.deliver.copy(aspect = 16f / 9f, fill = true, audioOnly = false))
                                        exportQueue.add(es.deliver.copy(aspect = 9f / 16f, fill = true, audioOnly = false))
                                        es.queueBase = es.deliver; es.queueAt = 0
                                        es.message = "Exporting 16:9, then 9:16"
                                    }
                                },
                                onQueueRemove = { i -> if (i in exportQueue.indices) exportQueue.removeAt(i) },
                                onQueueStart = { if (!es.exporting) { es.queueBase = es.deliver; es.queueAt = 0 } },
                                onQueueStop = { es.queueAt = -1; es.cloudJob?.cancel(); es.export?.cancel(); es.export = null; es.exporting = false; es.deliver = es.queueBase ?: es.deliver; es.message = "Queue stopped" },
                            )
                        }
                        if (clips.isEmpty() && es.page != Page.MEDIA && es.page != Page.DELIVER) Text("Add a clip in the Media page.", color = Pro.Dim, modifier = Modifier.padding(12.dp))
                    }
                }
            }

            if (es.fullscreen) com.dronedirector.veyrocutpro.ui.ForceDark {
                BackHandler { es.fullscreen = false }
                var controls by remember { mutableStateOf(true) }
                var touch by remember { mutableStateOf(0) }
                // Controls hide by themselves a few seconds after the last touch while playing.
                LaunchedEffect(controls, touch, es.playing) {
                    if (controls && es.playing) { delay(3000); controls = false }
                }
                var shownAt by remember { mutableStateOf(0L) }
                fun poke() { if (!controls) shownAt = System.currentTimeMillis(); controls = true; touch++ }
                Box(
                    Modifier.fillMaxSize().background(Color.Black)
                        // Any tap on the picture brings the controls back at once — also while playing, and
                        // also when the picture itself uses the touch (framing, layers).
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                val up = waitForUpOrCancellation(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                if (up != null && (up.position - down.position).getDistance() < viewConfiguration.touchSlop && !controls) poke()
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { if (controls && System.currentTimeMillis() - shownAt > 700) controls = false },
                                onDoubleTap = { togglePlay(); poke() },
                            )
                        },
                ) {
                    viewer(Modifier.fillMaxSize())
                    androidx.compose.animation.AnimatedVisibility(
                        controls, enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut(),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            // Top: close + title
                            Row(
                                Modifier.align(Alignment.TopCenter).fillMaxWidth()
                                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TopIcon(Icons.Filled.FullscreenExit, "Exit full screen") { es.fullscreen = false }
                                Text(
                                    clips.getOrNull(es.current)?.name ?: "", color = Color.White, fontSize = 14.sp, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                                )
                                TopIcon(Icons.Filled.Close, "Close") { es.fullscreen = false }
                            }
                            // Centre: previous / play / next
                            Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                                RoundControl(Icons.Filled.SkipPrevious, "Previous", 52.dp) { prevClip(); poke() }
                                RoundControl(if (es.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play", 72.dp, accent = true) { togglePlay(); poke() }
                                RoundControl(Icons.Filled.SkipNext, "Next", 52.dp) { nextClip(); poke() }
                            }
                            // Bottom: time, seek, mute
                            Column(
                                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            ) {
                                Slider(
                                    value = if (totalMs > 0) globalMs.toFloat() / totalMs else 0f,
                                    onValueChange = { seekGlobal((it * totalMs).toLong()); poke() },
                                    modifier = Modifier.height(28.dp),
                                    colors = SliderDefaults.colors(thumbColor = Pro.Amber, activeTrackColor = Pro.Amber, inactiveTrackColor = com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.25f)),
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${tc(globalMs)} / ${tc(totalMs)}", style = Pro.Value.copy(fontSize = 12.sp), modifier = Modifier.weight(1f))
                                    TopIcon(if (es.previewMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp, "Mute") { es.previewMuted = !es.previewMuted; poke() }
                                }
                            }
                        }
                    }
                }
            } else Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TopIcon(Icons.AutoMirrored.Filled.ArrowBack, "Close", enabled = !es.exporting) { es.confirmExit = true }
                    // Phones: these icons scroll sideways (with hint arrows); LOAD, SAVE and EXPORT stay put.
                    BarIconGroup {
                    FileMenuButton(fa, fm)
                    TopIcon(Icons.Filled.Keyboard, "Keyboard shortcuts") { wf.showKeys = true }
                    com.dronedirector.veyrocutpro.ui.HelpButton(com.dronedirector.veyrocutpro.ui.GuideVariant.VIDEO)
                    VideoPanelsButton(
                        onPage = { es.page = it },
                        onReset = {
                            listOf("ve_timeline_land", "ve_timeline_port", "ve_inspector_w", "ve_viewer_port", "ve_strip_h", "ve_mon_media_s", "ve_mon_media_u", "ve_mon_tools_s", "ve_mon_tools_u", "ve_mon_tools_w").forEach { PaneSizes.set(context, it, -1f) }
                            es.paneVersion++
                            es.message = "Layout reset to original"
                        },
                    )
                    TopIcon(Icons.Filled.Settings, "Settings") { es.showSettings = true }
                    }
                    BarButton("LOAD", enabled = !es.exporting) { es.showLoad = true }
                    BarButton("SAVE", enabled = !es.exporting) { if (Team.current != null) fa.save() else es.askName = true }
                    Text(
                        "EXPORT", style = Pro.Value.copy(color = Color.Black, fontSize = 12.sp),
                        modifier = Modifier.padding(horizontal = 6.dp).clip(RoundedCornerShape(50)).background(Pro.Amber).clickable(enabled = !es.exporting) { es.page = Page.DELIVER; com.dronedirector.veyrocutpro.ui.Panels.set(context, "ve.inspector", true) }.padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
                // Resizable panels: hold a divider, then drag. Double-tap a divider to reset it.
                // Hidden panels give their space to the rest; shown again, everything returns to its saved size.
                val tlPane = rememberPane(if (wide) "ve_timeline_land" else "ve_timeline_port", es.paneVersion)
                // Starting height of the tracks: about a third of the screen in landscape on phones and tablets.
                val tlLand = wide && mon == com.dronedirector.veyrocutpro.ui.MonitorClass.NONE
                val tlDefault = if (tlLand) boxH * 0.31f else 150f
                val tlScroll = rememberScrollState()
                var tlEnergy by remember { mutableStateOf(0f) }
                var tlHEnergy by remember { mutableStateOf(0f) }
                // Shared with the portrait layout below: the media strip's top divider moves the viewer's edge too.
                val viewerPane = rememberPane("ve_viewer_port", es.paneVersion)
                val autoViewer = boxW * 9f / 16f
                val timelineBox = @Composable {
                    val stripOn = showStrip && !stripInTools && !monCols   // above the timeline (shown even when empty); a column on ultra-wide monitors
                    // Divider above the media strip: hold, then drag to give the viewer or the strip more room.
                    if (stripOn) ResizeHandle(true,
                        onDrag = { d ->
                            if (!wide && showInspector) viewerPane.dp = ((if (viewerPane.dp < 0f) autoViewer else viewerPane.dp) + d).coerceIn(110f, boxH - 300f)
                            stripPane.dp = (stripH - d).coerceIn(40f, 240f)
                        },
                        onEnd = { stripPane.save(); viewerPane.save() }, onReset = { stripPane.reset() })
                    if (stripOn) stripView(stripH.dp, false)
                    // Divider between the media strip and the timeline.
                    if (stripOn) ResizeHandle(true,
                        onDrag = { d ->
                            stripPane.dp = (stripH + d).coerceIn(40f, 240f)
                            if (tlPane.dp >= 0f) tlPane.dp = (tlPane.dp - d).coerceIn(70f, boxH * 0.6f)
                        },
                        onEnd = { stripPane.save(); tlPane.save() }, onReset = { stripPane.reset() })
                    // Tracks scroll up and down with the wheel on the right, left and right with the wheel along the bottom.
                    Box(Modifier.fillMaxWidth()) {
                        val sized = if (tlPane.dp < 0f) (if (tlLand) Modifier.fillMaxWidth().height(tlDefault.dp) else Modifier.fillMaxWidth().heightIn(max = (boxH * 0.42f).dp)) else Modifier.fillMaxWidth().height(tlPane.dp.dp)
                        // The tracks stop short of the right and bottom edges: each wheel has its own window there.
                        Box(
                            sized.padding(end = 26.dp, bottom = 26.dp)
                                .onGloballyPositioned { val p = it.positionInRoot(); es.tlBounds = androidx.compose.ui.geometry.Rect(p.x, p.y, p.x + it.size.width, p.y + it.size.height) }
                                .verticalScroll(tlScroll)
                                // Mouse: Ctrl + wheel zooms, Shift + wheel (or a sideways wheel) scrolls along the edit; the plain wheel moves the tracks up and down.
                                // Right-click a clip to pick it and open its tools.
                                .mouseActions(
                                    onWheel = { dy, dx, _, ctrl, shift ->
                                        when {
                                            ctrl -> { if (dy < 0) zoom.zoomIn() else zoom.zoomOut(); true }
                                            shift || dx != 0f -> { zoom.scrollBy((((if (dx != 0f) dx else dy)) * 120f / zoom.pxPerSec * 1000f).toLong()); true }
                                            else -> false
                                        }
                                    },
                                    onRightClick = { o ->
                                        val ms = zoom.scrollMs + ((o.x - tlLeftPad) / zoom.pxPerSec * 1000f).toLong()
                                        val st = clipStarts(clips)
                                        val i = clips.indices.firstOrNull { ms >= st[it] && ms < st[it] + clips[it].lengthMs }
                                        if (i != null) { es.selected = i; es.page = Page.EDIT; com.dronedirector.veyrocutpro.ui.Panels.set(context, "ve.inspector", true) }
                                    },
                                ),
                        ) { timeline() }
                        if (es.wheelFx) {
                            TimelineUpscaleOverlay(tlEnergy, tlScroll.value, Modifier.matchParentSize().padding(end = 26.dp, bottom = 26.dp))
                            TimelineUpscaleOverlay(tlHEnergy, zoom.scrollMs.toInt(), Modifier.matchParentSize().padding(end = 26.dp, bottom = 26.dp), horizontal = true)
                        }
                        // Horizontal wheel window: along the bottom, from the left edge to the vertical wheel's window.
                        Box(Modifier.matchParentSize()) {
                            Box(
                                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(end = 26.dp).height(26.dp)
                                    .background(Ed.Panel)
                                    .border(1.dp, com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.08f)),
                            ) {
                                TimelineHWheel(zoom, Modifier.align(Alignment.Center).fillMaxWidth().padding(start = 6.dp, end = 6.dp), fancy = es.wheelFx) { tlHEnergy = it }
                            }
                        }
                        // Scroll wheel window: its own panel on the right, as tall as the visible timeline,
                        // so it grows as tracks are added and never covers a track.
                        Box(Modifier.matchParentSize()) {
                            Box(
                                Modifier.align(Alignment.CenterEnd).width(26.dp).fillMaxHeight()
                                    .background(Ed.Panel)
                                    .border(1.dp, com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.08f)),
                            ) {
                                TimelineWheel(tlScroll, Modifier.align(Alignment.Center).fillMaxHeight().padding(top = 6.dp, bottom = 6.dp), fancy = es.wheelFx) { tlEnergy = it }
                            }
                        }
                    }
                }
                if (monCols) {
                    // Ultra-wide / super ultra-wide monitor: media | viewer | tools, with the timeline across the whole width.
                    val sup = mon == com.dronedirector.veyrocutpro.ui.MonitorClass.SUPER_ULTRA_WIDE
                    val mediaPane = rememberPane(if (sup) "ve_mon_media_s" else "ve_mon_media_u", es.paneVersion)
                    val toolsPane = rememberPane(if (sup) "ve_mon_tools_s" else "ve_mon_tools_u", es.paneVersion)
                    val mediaW = if (mediaPane.dp < 0f) boxW * (if (sup) 0.24f else 0.2f) else mediaPane.dp
                    val toolsW = if (toolsPane.dp < 0f) boxW * (if (sup) 0.27f else 0.25f) else toolsPane.dp
                    Row(Modifier.fillMaxWidth().weight(1f)) {
                        if (showStrip) {
                            Box(Modifier.width(mediaW.dp).fillMaxHeight().background(Ed.Panel)) { stripView(stripH.dp, true) }
                            ResizeHandle(false,
                                onDrag = { d -> mediaPane.dp = (mediaW + d).coerceIn(200f, boxW * 0.4f) },
                                onEnd = { mediaPane.save() }, onReset = { mediaPane.reset() })
                        }
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            viewer(Modifier.fillMaxWidth().weight(1f))
                            if (showTransport) transport()
                        }
                        if (showInspector) {
                            ResizeHandle(false,
                                onDrag = { d -> toolsPane.dp = (toolsW - d).coerceIn(280f, boxW * 0.45f) },
                                onEnd = { toolsPane.save() }, onReset = { toolsPane.reset() })
                            inspector(Modifier.width(toolsW.dp).fillMaxHeight())
                        }
                    }
                    ResizeHandle(true,
                        onDrag = { d -> tlPane.dp = ((if (tlPane.dp < 0f) tlDefault else tlPane.dp) - d).coerceIn(70f, boxH * 0.6f) },
                        onEnd = { tlPane.save() }, onReset = { tlPane.reset() })
                    timelineBox()
                } else if (wide) {
                    val insp = rememberPane(if (mon != com.dronedirector.veyrocutpro.ui.MonitorClass.NONE) "ve_mon_tools_w" else "ve_inspector_w", es.paneVersion)
                    val maxW = boxW
                    // Starting width of the tools panel: about 44 % of the screen on phones and tablets (390 dp on monitors).
                    val inspDefault = if (mon == com.dronedirector.veyrocutpro.ui.MonitorClass.NONE) (boxW * 0.435f).coerceIn(240f, maxW - 260f) else 390f
                    Row(Modifier.fillMaxWidth().weight(1f)) {
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            viewer(Modifier.fillMaxWidth().weight(1f))
                            if (showTransport) transport()
                        }
                        if (showInspector) {
                            ResizeHandle(false,
                                onDrag = { d -> insp.dp = ((if (insp.dp < 0f) inspDefault else insp.dp) - d).coerceIn(240f, maxW - 260f) },
                                onEnd = { insp.save() }, onReset = { insp.reset() })
                            inspector(Modifier.width((if (insp.dp < 0f) inspDefault else insp.dp).dp).fillMaxHeight())
                        }
                    }
                    ResizeHandle(true,
                        onDrag = { d -> tlPane.dp = ((if (tlPane.dp < 0f) tlDefault else tlPane.dp) - d).coerceIn(70f, boxH * 0.6f) },
                        onEnd = { tlPane.save() }, onReset = { tlPane.reset() })
                    timelineBox()
                } else {
                    val maxH = boxH
                    if (showInspector) {
                        viewer(Modifier.fillMaxWidth().height((if (viewerPane.dp < 0f) autoViewer else viewerPane.dp).dp))
                        ResizeHandle(true,
                            onDrag = { d -> viewerPane.dp = ((if (viewerPane.dp < 0f) autoViewer else viewerPane.dp) + d).coerceIn(110f, maxH - 300f) },
                            onEnd = { viewerPane.save() }, onReset = { viewerPane.reset() })
                    } else {
                        // No tools panel: the viewer grows into the free space.
                        viewer(Modifier.fillMaxWidth().weight(1f))
                    }
                    if (showTransport) transport()
                    timelineBox()
                    if (showInspector) {
                        ResizeHandle(true,
                            onDrag = { d -> tlPane.dp = ((if (tlPane.dp < 0f) tlDefault else tlPane.dp) + d).coerceIn(70f, maxH * 0.5f) },
                            onEnd = { tlPane.save() }, onReset = { tlPane.reset() })
                        inspector(Modifier.fillMaxWidth().weight(1f))
                    }
                }
            }

            if (es.confirmExit) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { es.confirmExit = false },
                    containerColor = Pro.PanelSolid,
                    title = { Text("Leave the video editor?", color = Color.White) },
                    text = { Text(if (es.projectId != null) "Save your latest changes to \"${es.projectName}\" before leaving?" else "This timeline isn't saved yet. Save it as a project to continue later.", color = Pro.Dim) },
                    confirmButton = {
                        Row {
                            androidx.compose.material3.TextButton(onClick = {
                                es.confirmExit = false
                                // Other open projects with unsaved changes are asked about first.
                                val others = host.open.filter { it.key != host.active && host.dirty(it) }
                                if (others.isEmpty()) onExit() else wf.closeAsk = others to { onExit() }
                            }) { Text("Leave", color = Pro.Red) }
                            androidx.compose.material3.TextButton(onClick = {
                                es.confirmExit = false
                                if (es.projectId == null) { es.leaveAfterSave = true; es.askName = true } else { saveProject(es.projectName); onExit() }
                            }) { Text("Save & leave", color = Pro.Amber) }
                        }
                    },
                    dismissButton = { androidx.compose.material3.TextButton(onClick = { es.confirmExit = false }) { Text("Stay", color = Color.White) } },
                )
            }
        }
    }
}

/** The Proxy section: which media plays from a light proxy, on / off, quality and progress. */
@OptIn(UnstableApi::class)
@Composable
private fun ProxyPage(
    media: List<Clip>, inProxy: List<String>, settings: ProxySettings,
    status: (Clip) -> String, progress: (Clip) -> Int?, busy: Boolean,
    onSettings: (ProxySettings) -> Unit, onAdd: (Clip) -> Unit, onAddAll: () -> Unit, onRemove: (Clip) -> Unit,
    onCancel: (Clip) -> Unit, onCancelAll: () -> Unit, onRetry: (Clip) -> Unit, onClearFiles: () -> Unit,
) {
    val context = LocalContext.current
    SectionTitle("PROXY")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ed.Raised).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(if (settings.enabled) "Proxy ON" else "Proxy OFF", color = Color.White, fontSize = 15.sp)
            Text(
                if (settings.enabled) "Media below plays from its light proxy" else "The editor plays the original, full-quality files",
                color = Pro.Dim, fontSize = 12.sp,
            )
        }
        androidx.compose.material3.Switch(
            settings.enabled, { onSettings(settings.copy(enabled = it)) },
            colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Pro.Amber, checkedThumbColor = Color.Black),
        )
    }
    Text("Proxy quality", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
    Chips(ProxyQuality.entries.map { it.label }, settings.quality.ordinal) { onSettings(settings.copy(quality = ProxyQuality.entries[it])) }
    Text(
        when (settings.quality) {
            ProxyQuality.P360 -> "Fastest and smallest — best for long or many 4K clips on slower PCs."
            ProxyQuality.P540 -> "Very light, still easy to judge framing."
            ProxyQuality.P720 -> "Balanced (recommended)."
            ProxyQuality.P1080 -> "Sharpest preview; heavier to make and play."
        } + " Changing quality remakes the proxies in the background; the old ones are used until then.",
        color = Pro.Dim, fontSize = 11.sp,
    )
    Text("Proxy codec", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
    Chips(listOf("H.264 (most compatible)", "HEVC (smaller)"), if (settings.hevc) 1 else 0) { onSettings(settings.copy(hevc = it == 1)) }
    Text(
        "Only media added here plays from a proxy. Proxies keep the original's timing, frame rate, orientation and sound, so edits never move when you switch. The final export always uses the original files.",
        color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp),
    )
    val added = media.filter { MediaId.of(it) in inProxy }
    val notAdded = media.filter { MediaId.of(it) !in inProxy }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (notAdded.isNotEmpty()) Text(
            "ADD ALL MEDIA", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp),
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Ed.Raised).clickable { onAddAll() }.padding(8.dp),
        )
        if (busy) Text(
            "CANCEL ALL", style = Pro.Label.copy(color = Pro.Red, fontSize = 10.sp),
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Ed.Raised).clickable { onCancelAll() }.padding(8.dp),
        )
    }
    if (media.isEmpty()) Text("Add clips in the Media page first.", color = Pro.Dim, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
    if (added.isNotEmpty()) SectionTitle("IN PROXY (${added.size})")
    added.forEach { c ->
        val st = status(c)
        val pp = progress(c)
        Column(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.name, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${c.width}×${c.height} · ${tc(c.durationMs)}", style = Pro.Label)
                }
                Text(
                    "REMOVE", style = Pro.Label.copy(color = Pro.Red, fontSize = 10.sp),
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onRemove(c) }.padding(6.dp),
                )
            }
            if (pp != null) androidx.compose.material3.LinearProgressIndicator(
                progress = { pp / 100f }, color = Pro.Amber, trackColor = com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
            )
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (!settings.enabled && st.startsWith("READY")) "$st · PROXY OFF" else st,
                    style = Pro.Label.copy(color = if (st.startsWith("READY")) Pro.Green else if (st.contains("FAILED") || st.contains("COULDN'T")) Pro.Red else Pro.Dim, fontSize = 10.sp),
                    modifier = Modifier.weight(1f),
                )
                val action = when {
                    st.startsWith("GENERATING") || st.startsWith("UPDATING") || st.startsWith("QUEUED") -> "CANCEL"
                    st.contains("FAILED") || st.contains("COULDN'T") -> "RETRY"
                    st.startsWith("CANCELLED") || st.contains("NOT UPDATED") -> "GENERATE"
                    else -> null
                }
                if (action != null) Text(
                    action, style = Pro.Label.copy(color = if (action == "CANCEL") Pro.Red else Pro.Amber, fontSize = 10.sp),
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { if (action == "CANCEL") onCancel(c) else onRetry(c) }.padding(6.dp),
                )
            }
        }
    }
    if (notAdded.isNotEmpty()) SectionTitle("NOT IN PROXY — PLAYS ORIGINAL")
    notAdded.forEach { c ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(c.name, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${c.width}×${c.height} · ${tc(c.durationMs)}" + if (c.hdr) " · HDR" else if (c.tenBit) " · 10-bit" else "", style = Pro.Label)
            }
            Text(
                "ADD", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp),
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onAdd(c) }.padding(6.dp),
            )
        }
    }
    var size by remember { mutableStateOf(ProxyMaker.sizeOnDisk(context)) }
    LaunchedEffect(busy, added.size) { size = ProxyMaker.sizeOnDisk(context) }
    Text(
        "DELETE PROXY FILES (${size / 1_000_000} MB)", style = Pro.Label.copy(color = Pro.Red, fontSize = 10.sp),
        modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(6.dp)).clickable { onClearFiles(); size = ProxyMaker.sizeOnDisk(context) }.padding(6.dp),
    )
}

@Composable
private fun MediaPage(
    clips: List<Clip>, onAddGallery: () -> Unit, onAddFiles: () -> Unit,
    onCamera: () -> Unit = {},
    audioPool: List<PoolAudio>, audioUses: (PoolAudio) -> Int, onAddAudio: () -> Unit,
    onPlaceAudio: (PoolAudio) -> Unit, onRemoveAudio: (PoolAudio) -> Unit,
    proxyState: (Clip) -> String?, onProxyToggle: (Clip) -> Unit, onRemove: (Clip) -> Unit,
    onLibraryAdd: (LibSection, LibTrack) -> Unit = { _, _ -> },
    onMulticam: () -> Unit = {},
    bins: Map<String, String> = emptyMap(), binNames: List<String> = emptyList(),
    onNewBin: (String) -> Unit = {}, onSetBin: (Clip, String?) -> Unit = { _, _ -> },
    onSource: (Clip) -> Unit = {}, onPickSource: () -> Unit = {},
    onAddNas: () -> Unit = {},
    missing: List<String> = emptyList(), onRelink: (Clip) -> Unit = {},
) {
    var confirm by remember { mutableStateOf<Clip?>(null) }
    var binFilter by remember { mutableStateOf<String?>(null) }
    var newBin by remember { mutableStateOf(false) }
    var binFor by remember { mutableStateOf<Clip?>(null) }
    if (newBin) {
        var name by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { newBin = false }, containerColor = Pro.PanelSolid,
            title = { Text("New bin", color = Color.White) },
            text = { androidx.compose.material3.OutlinedTextField(name, { name = it.take(24) }, singleLine = true, placeholder = { Text("e.g. Interviews, B-roll, Drone") }) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { if (name.isNotBlank()) onNewBin(name.trim()); newBin = false }) { Text("Add", color = Pro.Amber) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { newBin = false }) { Text("Cancel", color = Pro.Dim) } },
        )
    }
    binFor?.let { c ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { binFor = null }, containerColor = Pro.PanelSolid,
            title = { Text("Move to bin", color = Color.White) },
            text = {
                Column {
                    (listOf<String?>(null) + binNames).forEach { b ->
                        Text(b ?: "No bin", color = if (bins[c.uri.toString()] == b) Pro.Amber else Color.White, fontSize = 15.sp,
                            modifier = Modifier.fillMaxWidth().clickable { onSetBin(c, b); binFor = null }.padding(vertical = 10.dp))
                    }
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { binFor = null; newBin = true }) { Text("+ New bin", color = Pro.Amber) } },
        )
    }
    confirm?.let { c ->
        val uses = clips.count { it.uri == c.uri }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = Pro.PanelSolid,
            title = { Text("Delete this file?", color = Color.White) },
            text = {
                Text(
                    "Are you sure you want to delete \"${c.name}\" from the media pool?" +
                        (if (uses > 1) " It is used $uses times on the timeline; all of them will be removed." else " It will also be removed from the timeline.") +
                        " The original on your PC is kept.",
                    color = Pro.Dim,
                )
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { onRemove(c); confirm = null }) { Text("Yes", color = Pro.Red) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirm = null }) { Text("No", color = Pro.Amber) } },
        )
    }
    SectionTitle("MEDIA POOL")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BigButton(Icons.Filled.PhotoLibrary, "Gallery (videos, photos)", Modifier.weight(1f), onAddGallery)
        BigButton(Icons.Filled.FolderOpen, "Files (MKV…)", Modifier.weight(1f), onAddFiles)
    }
    BigButton(Icons.Filled.Videocam, "Camera → media list (video or photo)", Modifier.fillMaxWidth().padding(top = 8.dp), onCamera)
    BigButton(Icons.Filled.MusicNote, "Audio (MP3, WAV, AAC…)", Modifier.fillMaxWidth().padding(top = 8.dp), onAddAudio)
    BigButton(Icons.Filled.Storage, "Network (NAS) — videos, photos, audio", Modifier.fillMaxWidth().padding(top = 8.dp), onAddNas)
    if (clips.distinctBy { it.uri.toString() }.size >= 2) {
        BigButton(Icons.Filled.Movie, "Multicam (2–4 angles)", Modifier.fillMaxWidth().padding(top = 8.dp), onMulticam)
    }
    BigButton(Icons.Filled.Movie, "Source viewer (mark in / out, insert)", Modifier.fillMaxWidth().padding(top = 8.dp), onPickSource)
    SoundLibraries(onLibraryAdd)
    SectionTitle("BINS")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.hintedScroll(horizontal = true).padding(vertical = 4.dp)) {
        PillChip("All", binFilter == null) { binFilter = null }
        binNames.forEach { b -> PillChip("$b (${clips.distinctBy { it.uri.toString() }.count { bins[it.uri.toString()] == b }})", binFilter == b) { binFilter = b } }
        PillChip("+ Bin", false) { newBin = true }
    }
    Text(
        "Clips are added to the end of the timeline. For smooth editing of heavy clips (HDR, 10-bit, 4K), add them to the Proxy tab — exports always use the original files.",
        color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.padding(vertical = 6.dp),
    )
    clips.distinctBy { it.uri.toString() }.forEachIndexed { i, c ->
        if (binFilter != null && bins[c.uri.toString()] != binFilter) return@forEachIndexed
        val lbl = clips.firstOrNull { it.uri == c.uri && it.label != null }?.label
        Column(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).then(if (lbl != null) Modifier.border(2.dp, Color(lbl), RoundedCornerShape(8.dp)) else Modifier).padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", style = Pro.Value.copy(color = Pro.Amber), modifier = Modifier.width(26.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.name, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val codec = when {
                        c.mime.contains("hevc") -> "HEVC"; c.mime.contains("avc") -> "H.264"; c.mime.contains("vp9") -> "VP9"
                        c.mime.contains("av01") -> "AV1"; c.mime.contains("dolby") -> "Dolby Vision"; else -> c.mime.removePrefix("video/").uppercase()
                    }
                    Text(
                        "${c.width}×${c.height} · ${tc(c.durationMs)} · $codec" +
                            (if (c.is422) " · " + (if (c.chroma == 3) "4:4:4" else "4:2:2") + " ${c.bitDepth ?: 10}-bit" else "") +
                            (if (c.hdr) " · HDR" else if (c.tenBit && !c.is422) " · 10-bit" else ""),
                        style = Pro.Label,
                    )
                    if (c.undecodable == true) Text(
                        "This PC has no decoder for ${if (c.chroma == 3) "4:4:4" else "4:2:2"} ${c.mime.removePrefix("video/").uppercase()} — it can't be shown or exported here. Convert it to 4:2:0 (e.g. ProRes / DNxHR → H.265 4:2:0 10-bit) on a computer, or shoot 4:2:0 for phone editing.",
                        color = Pro.Red, fontSize = 11.sp,
                    ) else if (c.decoder != null && c.is422) Text(
                        "4:2:2 is read with ${if (c.decoder.startsWith("c2.android") || c.decoder.startsWith("OMX.google")) "Android's software decoder (slower)" else "the phone's hardware decoder"}; add it to the Proxy tab for smooth editing; the export is made from the original (saved as 4:2:0, which phones encode).",
                        color = Pro.Dim, fontSize = 11.sp,
                    )
                }
                Box(Modifier.size(36.dp).clip(CircleShape).clickable { confirm = c }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete from media pool", tint = Pro.Red, modifier = Modifier.size(20.dp))
                }
            }
            if (c.uri.toString() in missing) Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("MISSING — the file was moved, deleted or can't be opened", style = Pro.Label.copy(color = Pro.Red, fontSize = 10.sp), modifier = Modifier.weight(1f))
                Text("RELINK", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onRelink(c) }.padding(6.dp))
            }
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("BIN: ${bins[c.uri.toString()] ?: "—"}", style = Pro.Label.copy(fontSize = 10.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { binFor = c }.padding(6.dp))
                Spacer(Modifier.weight(1f))
                Text("SOURCE ›", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onSource(c.copy(startMs = 0, endMs = c.durationMs)) }.padding(6.dp))
            }
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                val st = proxyState(c)
                Text(if (st == null) "ORIGINAL" else "PROXY · $st", style = Pro.Label.copy(color = if (st?.startsWith("READY") == true) Pro.Green else Pro.Dim, fontSize = 10.sp), modifier = Modifier.weight(1f))
                Text(
                    if (st == null) "ADD TO PROXY" else "PROXY ›", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp),
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onProxyToggle(c) }.padding(6.dp),
                )
            }
        }
    }
    if (audioPool.isNotEmpty()) {
        var confirmAudio by remember { mutableStateOf<PoolAudio?>(null) }
        confirmAudio?.let { a ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { confirmAudio = null },
                containerColor = Pro.PanelSolid,
                title = { Text("Delete this file?", color = Color.White) },
                text = { Text("Are you sure you want to delete \"${a.name}\" from the media pool? It will also be removed from the timeline. The original on your PC is kept.", color = Pro.Dim) },
                confirmButton = { androidx.compose.material3.TextButton(onClick = { onRemoveAudio(a); confirmAudio = null }) { Text("Yes", color = Pro.Red) } },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmAudio = null }) { Text("No", color = Pro.Amber) } },
            )
        }
        SectionTitle("AUDIO")
        audioPool.forEach { a ->
            Column(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Pro.Amber, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(a.name, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val n = audioUses(a)
                        Text(tc(a.durationMs) + if (n > 0) " · on timeline ×$n" else "", style = Pro.Label)
                    }
                    Box(Modifier.size(36.dp).clip(CircleShape).clickable { confirmAudio = a }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete from media pool", tint = Pro.Red, modifier = Modifier.size(20.dp))
                    }
                }
                Text(
                    "ADD TO TIMELINE (AT PLAYHEAD)", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp),
                    modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(6.dp)).clickable { onPlaceAudio(a) }.padding(6.dp),
                )
            }
        }
        Text("On the timeline: hold an audio bar to move it, or hold its start or end edge to trim it.", color = Pro.Dim, fontSize = 11.sp)
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditPage(
    c: Clip, index: Int, count: Int, posMs: Long, deliver: DeliverSettings, onDeliver: (DeliverSettings) -> Unit,
    onChange: ((Clip) -> Clip) -> Unit,
    onSplit: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit, onMove: (Int) -> Unit,
    onFreeze: () -> Unit = {}, onReverse: () -> Unit = {}, onDetach: () -> Unit = {},
    onSlowMo: () -> Unit = {}, onReframe: (Float?) -> Unit = {},
    flowProgress: Int = -1, onFlow: (factor: Int, speed: Float?, shutter: Float) -> Unit = { _, _, _ -> }, onStopFlow: () -> Unit = {},
    onRemoveSilence: (all: Boolean, db: Float, minMs: Long) -> Unit = { _, _, _ -> },
    onTrimTool: (Int, Long) -> Unit = { _, _ -> }, frameMs: Long = 33L,
    onNest: (Int) -> Unit = {}, onUnNest: () -> Unit = {}, nesting: Boolean = false, nestProgress: Int = 0,
) {
    SectionTitle("CLIP ${index + 1} OF $count · ${c.name}".uppercase())
    // Colour label (shown on the timeline and in the media pool).
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.hintedScroll(horizontal = true).padding(vertical = 4.dp)) {
        Text("Label", style = Pro.Label)
        Box(Modifier.size(26.dp).clip(CircleShape).background(Ed.Raised).border(2.dp, if (c.label == null) Pro.Amber else Color.DarkGray, CircleShape).clickable { onChange { it.copy(label = null) } }, contentAlignment = Alignment.Center) { Text("✕", color = Color.White, fontSize = 11.sp) }
        CLIP_LABELS.forEach { col ->
            Box(Modifier.size(26.dp).clip(CircleShape).background(Color(col)).border(2.dp, if (c.label == col) Pro.Amber else Color.DarkGray, CircleShape).clickable { onChange { it.copy(label = col) } })
        }
        LabelNames.nameOf(LocalContext.current, c.label)?.let { Text(it, color = Pro.Dim, fontSize = 12.sp) }
    }
    // Pro trim: slip / roll / slide in frames.
    run {
        var mode by remember { mutableStateOf(0) }
        SectionTitle("TRIM TOOLS")
        Chips(listOf("Slip", "Roll", "Slide"), mode) { mode = it }
        Text(
            when (mode) {
                0 -> "SLIP: keeps the clip's place and length, shows an earlier or later part of it."
                1 -> "ROLL: moves the cut between this clip and the next — one gets longer, the other shorter."
                else -> "SLIDE: moves this clip along the timeline; the clips before and after give and take."
            } + " Normal trims ripple: the clips after move up.",
            color = Pro.Dim, fontSize = 11.sp,
        )
        Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(-10 to "◀◀ 10f", -1 to "◀ 1f", 1 to "1f ▶", 10 to "10f ▶▶").forEach { (n, label) ->
                Text(
                    label, style = Pro.Label.copy(color = Color.White, fontSize = 11.sp),
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable { onTrimTool(mode, n * frameMs) }.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
    }
    // Nested (compound) clips.
    run {
        SectionTitle("NEST (COMPOUND CLIP)")
        if (c.nested != null) {
            Text(
                "UN-NEST · OPEN UP INTO ${c.nested.size} CLIPS", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable { onUnNest() }.padding(10.dp),
            )
        } else if (nesting) {
            Text("Nesting… $nestProgress%", color = Pro.Amber, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
        } else {
            val left = count - index
            if (left >= 2) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.hintedScroll(horizontal = true)) {
                (2..minOf(left, 6)).forEach { n ->
                    Text(
                        "NEST $n", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable { onNest(n) }.padding(10.dp),
                    )
                }
                if (left > 6) Text(
                    "NEST ALL $left", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable { onNest(left) }.padding(10.dp),
                )
            }
            else Text("Select a clip with at least one clip after it to nest.", color = Pro.Dim, fontSize = 11.sp)
        }
        Text("Joins this clip and the next ones into a single clip (cuts and transitions baked in), so you can grade, speed up or reframe them as one. UN-NEST brings the original clips back.", color = Pro.Dim, fontSize = 11.sp)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.hintedScroll(horizontal = true)) {
        SmallAction(Icons.Filled.ContentCut, "Split", onSplit)
        SmallAction(Icons.Filled.PauseCircle, "Freeze", onFreeze)
        SmallAction(Icons.Filled.FastRewind, "Reverse", onReverse)
        SmallAction(Icons.Filled.CallSplit, "Detach audio", onDetach)
        SmallAction(Icons.Filled.SlowMotionVideo, "AI slow-mo", onSlowMo)
        SmallAction(Icons.Filled.ContentCopy, "Duplicate", onDuplicate)
        SmallAction(Icons.Filled.KeyboardArrowLeft, "Move left") { onMove(-1) }
        SmallAction(Icons.Filled.KeyboardArrowRight, "Move right") { onMove(1) }
        SmallAction(Icons.Filled.Delete, "Delete", onDelete)
    }
    SectionTitle("SMOOTH SLOW MOTION · OPTICAL FLOW")
    Text("Draws new in-between frames from the motion (on this PC), so slow motion is smooth instead of jerky. Pick the speed, or keep the clip's own speed / ramp.", color = Pro.Dim, fontSize = 11.sp)
    if (flowProgress >= 0) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        androidx.compose.material3.LinearProgressIndicator(progress = { flowProgress / 100f }, color = Pro.Amber, modifier = Modifier.weight(1f).height(4.dp))
        Text("$flowProgress%", style = Pro.Label, modifier = Modifier.padding(horizontal = 8.dp))
        Text("STOP", style = Pro.Label.copy(color = Pro.Red, fontSize = 10.sp), modifier = Modifier.clickable { onStopFlow() }.padding(6.dp))
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp).hintedScroll(horizontal = true)) {
            PillChip("½ speed", false) { onFlow(2, 0.5f, 0f) }
            PillChip("¼ speed", false) { onFlow(4, 0.25f, 0f) }
            PillChip("⅛ speed", false) { onFlow(8, 0.125f, 0f) }
            val slowest = (c.ramp?.minOrNull() ?: 1f) * c.speed
            if (slowest < 0.95f) PillChip("Keep my speed", false) {
                onFlow(kotlin.math.ceil(1f / slowest).toInt().coerceIn(2, 8), null, 0f)
            }
        }
        SectionTitle("MOTION BLUR")
        Text("Adds the natural blur of a film camera's shutter to moving things (on this PC) — great for screen recordings, drone shots, fast action and timelapses.", color = Pro.Dim, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp).hintedScroll(horizontal = true)) {
            PillChip("Light · 90°", false) { onFlow(1, null, 0.25f) }
            PillChip("Natural · 180°", false) { onFlow(1, null, 0.5f) }
            PillChip("Strong · 360°", false) { onFlow(1, null, 1f) }
        }
    }
    SectionTitle("REMOVE SILENCES")
    Text("Cuts out the pauses in talking clips. Quieter than the level below for longer than the minimum pause = removed.", color = Pro.Dim, fontSize = 11.sp)
    var silDb by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(-32f) }
    var silMin by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0.6f) }
    EdSlider("Silence level", silDb, -50f..-18f, -32f, { "${it.toInt()} dB" }) { silDb = it }
    EdSlider("Minimum pause", silMin, 0.2f..2f, 0.6f, { "%.1f s".format(it) }) { silMin = it }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("THIS CLIP" to false, "ALL CLIPS" to true).forEach { (label, all) ->
            Text(label, style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Ed.Raised).clickable { onRemoveSilence(all, silDb, (silMin * 1000).toLong()) }.padding(10.dp))
        }
    }
    Text("Undo brings it all back. Clips with a speed ramp are skipped.", color = Pro.Dim, fontSize = 11.sp)
    SectionTitle("TRIM  ·  ${tc(c.startMs)} → ${tc(c.endMs)}")
    RangeSlider(
        value = c.startMs.toFloat()..c.endMs.toFloat(),
        onValueChange = { r ->
            val s = r.start.toLong(); val e = r.endInclusive.toLong()
            if (e - s >= 200) onChange { it.copy(startMs = s, endMs = e) }
        },
        valueRange = 0f..c.durationMs.toFloat(),
        colors = SliderDefaults.colors(thumbColor = Pro.Amber, activeTrackColor = Pro.Amber, inactiveTrackColor = com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.15f)),
    )
    Text("Split cuts the clip under the playhead (${tc(posMs)} into the clip).", color = Pro.Dim, fontSize = 11.sp)
    SectionTitle("PROJECT FORMAT (ASPECT RATIO)")
    val formats = ASPECTS
    Chips(formats.map { it.first }, formats.indexOfFirst { it.second == deliver.aspect }.coerceAtLeast(0)) { onDeliver(deliver.copy(aspect = formats[it].second)) }
    Chips(listOf("Fill frame (crop)", "Fit (bars)"), if (deliver.fill) 0 else 1) { onDeliver(deliver.copy(fill = it == 0)) }
    SectionTitle("SPEED")
    val speeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 4f)
    Chips(speeds.map { if (it == 1f) "1x" else "${it}x" }, speeds.indexOf(c.speed).coerceAtLeast(0)) { i -> onChange { it.copy(speed = speeds[i]) } }
    PrivacySection(c, c.startMs + posMs, onChange)
    SectionTitle("AUTO-REFRAME (AI)")
    Chips(listOf("9:16", "1:1", "4:5", "Clear"), -1) { i -> onReframe(listOf(9f / 16f, 1f, 0.8f, null)[i]) }
    Text("Changes the project to the chosen shape and makes every clip follow its subject (people, animals, vehicles…) so nothing important is cut off.", color = Pro.Dim, fontSize = 11.sp)
    SectionTitle("SPEED RAMP")
    val ramps = RampPreset.entries
    Chips(listOf("None") + ramps.map { it.label }, if (c.ramp == null) 0 else ramps.indexOfFirst { it.label == c.rampName } + 1) { i ->
        onChange { if (i == 0) it.copy(ramp = null, rampName = null) else it.copy(ramp = ramps[i - 1].points, rampName = ramps[i - 1].label) }
    }
    c.ramp?.let { r ->
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(44.dp).padding(vertical = 4.dp).clip(RoundedCornerShape(6.dp)).background(Ed.Raised)) {
            val path = androidx.compose.ui.graphics.Path()
            for (i in 0..60) {
                val p = i / 60f
                val v = kotlin.math.ln(rampAt(r, p).toDouble()) / kotlin.math.ln(6.0)   // log scale, ±6x
                val y = size.height / 2f - (v.toFloat() * size.height / 2f * 0.9f)
                if (i == 0) path.moveTo(0f, y) else path.lineTo(p * size.width, y)
            }
            drawLine(com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.2f), androidx.compose.ui.geometry.Offset(0f, size.height / 2f), androidx.compose.ui.geometry.Offset(size.width, size.height / 2f), 1f)
            drawPath(path, Pro.Amber, style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
        }
        Text("Above the line = faster, below = slow motion. Combined with the speed above; the clip's length on the timeline follows the curve.", color = Pro.Dim, fontSize = 11.sp)
    }
}

/** Grade versions A / B / C: tap an empty one to save the look, tap a saved one to switch to it, hold to save over it. */
@Composable
private fun GradeVersions(c: Clip, onChange: ((Clip) -> Clip) -> Unit) {
    val now = ClipLook(c.grade, c.detail, c.film)
    val vs = c.versions.orEmpty()
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("VERSIONS", style = Pro.Label.copy(color = Pro.Dim, fontSize = 10.sp))
        for (k in 0 until 3) {
            val v = vs.getOrNull(k)
            val name = "ABC"[k].toString()
            fun save() = onChange { cl -> cl.copy(versions = List(3) { i -> if (i == k) ClipLook(cl.grade, cl.detail, cl.film) else cl.versions?.getOrNull(i) }) }
            PillChip(if (v == null) "+ $name" else name, v != null && v == now, onLongClick = { save() }) {
                if (v == null) save() else onChange { it.copy(grade = v.grade, detail = v.detail, film = v.film) }
            }
        }
        if (vs.any { it != null }) Text("CLEAR", style = Pro.Label.copy(color = Pro.Dim, fontSize = 10.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onChange { it.copy(versions = null) } }.padding(6.dp))
    }
    Text("Tap + to save this look as a version, tap a version to switch to it, hold to save over it.", color = Pro.Dim, fontSize = 10.sp)
}

@Composable
private fun ColorPage(
    c: Clip, tab: ColorTab, onTab: (ColorTab) -> Unit,
    onChange: ((Clip) -> Clip) -> Unit, onCopyToAll: () -> Unit,
    maskTools: LocalToolsState,
    clipNames: List<String> = emptyList(), selfIdx: Int = 0, matchBusy: Int? = null, onMatch: (Int) -> Unit = {},
    onMagic: () -> Unit = {}, magicProgress: Int? = null, onStopMagic: () -> Unit = {},
    onTrackPen: ((PhotoMask) -> Unit)? = null, penTracking: String? = null,
    onDepth: () -> Unit = {}, depthProgress: Int? = null, depthStatus: String? = null, onStopDepth: () -> Unit = {},
    onErase: () -> Unit = {}, eraseProgress: Int? = null, eraseStatus: String? = null, onStopErase: () -> Unit = {},
    posUs: Long = 0L,
) {
    val g = c.grade
    fun grade(f: (ProGrade) -> ProGrade) = onChange { it.copy(grade = f(it.grade)) }
    fun detail(f: (Detail) -> Detail) = onChange { it.copy(detail = f(it.detail)) }
    val tabs = if (com.dronedirector.veyrocutpro.camera.SimpleMode.on) listOf(ColorTab.INPUT, ColorTab.LOOKS, ColorTab.LUTS, ColorTab.PRIMARIES)
        else ColorTab.entries.toList()
    Chips(tabs.map { it.label }, tabs.indexOf(tab)) { onTab(tabs[it]) }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("COPY GRADE TO ALL CLIPS", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Ed.Raised).clickable(onClick = onCopyToAll).padding(8.dp))
        Text("RESET CLIP", style = Pro.Label.copy(color = Pro.Red, fontSize = 10.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Ed.Raised).clickable { onChange { it.copy(grade = ProGrade(), detail = Detail(), film = null) } }.padding(8.dp))
    }
    GradeVersions(c, onChange)
    when (tab) {
        ColorTab.INPUT -> {
            SectionTitle("INPUT COLOUR SPACE / GAMMA")
            Text("Pick how the clip was filmed. Log footage is converted to Rec.709 so it looks natural before grading.", color = Pro.Dim, fontSize = 11.sp)
            InputColor.entries.forEach { ic ->
                val sel = g.input == ic
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (sel) Pro.Amber.copy(alpha = 0.16f) else Ed.Raised)
                        .border(1.dp, if (sel) Pro.Amber else Color.Transparent, RoundedCornerShape(8.dp))
                        .clickable { grade { it.copy(input = ic) } }.padding(10.dp),
                ) {
                    Text(ic.label, color = if (sel) Pro.Amber else Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(90.dp))
                    Text(ic.detail, color = Pro.Dim, fontSize = 12.sp)
                }
            }
            Text("10-bit HDR (HLG / HDR10) clips are tone-mapped to SDR automatically on export.", color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            Text("Have the camera maker's conversion LUT? Import it in the LUTs tab and switch on \"Use as input conversion\".", color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            HdrLevelsSection(c.uri, posUs)
        }
        ColorTab.LUTS -> {
            Text("Import your own 3D LUTs (.cube) — from grading software, camera makers (DJI, Sony, Panasonic…), or LUT packs. Tap + IMPORT, pick the file, then choose how it's used.", color = Pro.Dim, fontSize = 11.sp)
            LutSection(g) { ng -> grade { ng } }
            SectionTitle("TIP")
            Text("For log footage, switch on \"Use as input conversion\" with the camera maker's log → Rec.709 LUT, then grade on top. For a creative look, leave it off and lower Strength to taste. COPY GRADE TO ALL CLIPS applies the LUT to the whole edit.", color = Pro.Dim, fontSize = 11.sp)
        }
        ColorTab.WHEELS -> {
            SectionTitle("PRIMARY WHEELS")
            WheelControl("Lift (shadows)", g.lift) { w -> grade { it.copy(lift = w) } }
            WheelControl("Gamma (midtones)", g.gamma) { w -> grade { it.copy(gamma = w) } }
            WheelControl("Gain (highlights)", g.gain) { w -> grade { it.copy(gain = w) } }
            SectionTitle("SPLIT TONES")
            WheelControl("Shadow tint", g.gradeShadows, showLuma = false) { w -> grade { it.copy(gradeShadows = w) } }
            WheelControl("Highlight tint", g.gradeHighlights, showLuma = false) { w -> grade { it.copy(gradeHighlights = w) } }
        }
        ColorTab.PRIMARIES -> {
            SectionTitle("PRIMARIES") { grade { it.copy(exposure = 0f, contrast = 0f, pivot = 0.435f, temperature = 0f, tint = 0f, saturation = 0f, vibrance = 0f, highlights = 0f, shadows = 0f, whites = 0f, blacks = 0f, dehaze = 0f) }; detail { it.copy(clarity = 0f, texture = 0f) } }
            EdSlider("Exposure", g.exposure, -3f..3f, format = ::ev) { v -> grade { it.copy(exposure = v) } }
            EdSlider("Contrast", g.contrast) { v -> grade { it.copy(contrast = v) } }
            EdSlider("Pivot", g.pivot, 0.2f..0.7f, 0.435f, ::pct) { v -> grade { it.copy(pivot = v) } }
            EdSlider("Temperature", g.temperature) { v -> grade { it.copy(temperature = v) } }
            EdSlider("Tint", g.tint) { v -> grade { it.copy(tint = v) } }
            EdSlider("Saturation", g.saturation) { v -> grade { it.copy(saturation = v) } }
            EdSlider("Vibrance", g.vibrance) { v -> grade { it.copy(vibrance = v) } }
            EdSlider("Highlights", g.highlights) { v -> grade { it.copy(highlights = v) } }
            EdSlider("Shadows", g.shadows) { v -> grade { it.copy(shadows = v) } }
            EdSlider("Whites", g.whites) { v -> grade { it.copy(whites = v) } }
            EdSlider("Blacks", g.blacks) { v -> grade { it.copy(blacks = v) } }
            EdSlider("Dehaze", g.dehaze) { v -> grade { it.copy(dehaze = v) } }
            EdSlider("Clarity", c.detail.clarity) { v -> detail { it.copy(clarity = v) } }
            EdSlider("Texture", c.detail.texture) { v -> detail { it.copy(texture = v) } }
        }
        ColorTab.CURVES -> {
            var ch by remember { mutableStateOf(0) }
            SectionTitle("POINT CURVES") { grade { it.copy(curveRgb = null, curveR = null, curveG = null, curveB = null) } }
            val all = listOf(g.curveRgb, g.curveR, g.curveG, g.curveB)
            CurveEditor(ch, { ch = it }, all[ch], { p ->
                grade { when (ch) { 1 -> it.copy(curveR = p); 2 -> it.copy(curveG = p); 3 -> it.copy(curveB = p); else -> it.copy(curveRgb = p) } }
            }, others = all)
            SectionTitle("CUSTOM CURVE (REGIONS)") { grade { it.copy(curveHighlights = 0f, curveLights = 0f, curveDarks = 0f, curveShadows = 0f) } }
            EdSlider("Highlights", g.curveHighlights) { v -> grade { it.copy(curveHighlights = v) } }
            EdSlider("Lights", g.curveLights) { v -> grade { it.copy(curveLights = v) } }
            EdSlider("Darks", g.curveDarks) { v -> grade { it.copy(curveDarks = v) } }
            EdSlider("Shadows", g.curveShadows) { v -> grade { it.copy(curveShadows = v) } }
            EdSlider("Fade (lift blacks)", g.fade, 0f..1f, format = ::pct) { v -> grade { it.copy(fade = v) } }
            SectionTitle("HUE CURVES") { grade { it.copy(hueCurves = null) } }
            HueCurvesEditor(g.hueCurves) { hc -> grade { it.copy(hueCurves = hc) } }
        }
        ColorTab.HSL -> HslMixer(g.hsl) { list -> grade { it.copy(hsl = list) } }
        ColorTab.MASKS -> {
            Text("Grade only part of the picture: a gradient (e.g. darken the sky), a radial (lift a face), or a colour / brightness range. Up to $MAX_VIDEO_MASKS masks per clip; drag the handles on the player.", color = Pro.Dim, fontSize = 11.sp)
            MasksPanel(c.masks ?: emptyList(), maskTools, { list -> onChange { it.copy(masks = list) } }, video = true, onTrackPen = onTrackPen, penTracking = penTracking)
        }
        ColorTab.DETAIL -> {
            SectionTitle("DETAIL") { detail { Detail() } }
            EdSlider("Sharpness", c.detail.sharpen, 0f..1f, format = ::pct) { v -> detail { it.copy(sharpen = v) } }
            EdSlider("Image detail", c.detail.localDetail) { v -> detail { it.copy(localDetail = v) } }
            EdSlider("Noise reduction", c.detail.denoise, 0f..1f, format = ::pct) { v -> detail { it.copy(denoise = v) } }
            var aiInfo by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("AI NOISE REDUCTION", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp))
                Box(
                    Modifier.padding(start = 8.dp).size(22.dp).clip(CircleShape)
                        .border(1.5.dp, Pro.Amber, CircleShape).clickable { aiInfo = true },
                    contentAlignment = Alignment.Center,
                ) { Text("!", color = Pro.Amber, fontSize = 13.sp, fontWeight = FontWeight.Black) }
            }
            EdSlider("Strength", c.detail.aiDenoise, 0f..1f, format = { if (it <= 0f) "Off" else pct(it) }) { v -> detail { it.copy(aiDenoise = v) } }
            if (aiInfo) androidx.compose.material3.AlertDialog(
                onDismissRequest = { aiInfo = false },
                containerColor = Pro.PanelSolid,
                title = { Text("AI noise reduction", color = Color.White) },
                text = {
                    Text(
                        "The built-in AI (NAFNet) cleans every single frame of the video when you export. " +
                            "This is very demanding: depending on your PC's hardware, the clip's length and resolution, " +
                            "an export can take from several minutes up to about 1 hour.\n\n" +
                            "You can stop it at any time with the Stop button on the AI badge. " +
                            "The preview shows the normal noise reduction; the AI result appears in the exported video.",
                        color = Pro.Dim,
                    )
                },
                confirmButton = { androidx.compose.material3.TextButton(onClick = { aiInfo = false }) { Text("OK", color = Pro.Amber) } },
            )
            SectionTitle("BACKGROUND (PEOPLE)") { onChange { it.copy(bg = null) } }
            val bgModes = listOf("Off", "Blur", "Colour", "Transparent")
            Chips(bgModes, c.bg?.let { it.mode.let { m -> if (m == 0) 3 else m } } ?: 0) { i ->
                onChange { cl -> cl.copy(bg = when (i) { 0 -> null; 3 -> BgRemoval(0, cl.bg?.color ?: 0xFF00B140.toInt()); else -> BgRemoval(i, cl.bg?.color ?: 0xFF00B140.toInt()) }) }
            }
            if (c.bg?.mode == 2) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.hintedScroll(horizontal = true).padding(vertical = 4.dp)) {
                listOf(0xFF00B140, 0xFF0047BB, 0xFF000000, 0xFFFFFFFF, 0xFF2A4C8C, 0xFFFFB300, 0xFF8E44AD).map { it.toInt() }.forEach { col ->
                    Box(Modifier.size(28.dp).clip(CircleShape).background(Color(col)).border(2.dp, if (c.bg?.color == col) Pro.Amber else Color.DarkGray, CircleShape).clickable { onChange { cl -> cl.copy(bg = cl.bg?.copy(color = col)) } })
                }
            }
            if (c.bg != null) {
                Text("Find", style = Pro.Label, modifier = Modifier.padding(top = 4.dp))
                Chips(listOf("People (automatic)", "Magic Mask (anything)"), if (c.bg.magic != null) 1 else 0) { i ->
                    if (i == 0) onChange { cl -> cl.copy(bg = cl.bg?.copy(magic = null)) } else onMagic()
                }
                val mg = c.bg.magic
                if (mg != null || magicProgress != null) {
                    if (magicProgress != null) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Text("Following the object… $magicProgress%", style = Pro.Label.copy(color = Pro.Amber), modifier = Modifier.weight(1f))
                        Text("STOP", style = Pro.Label.copy(color = Pro.Red, fontSize = 10.sp), modifier = Modifier.clickable { onStopMagic() }.padding(6.dp))
                    } else if (mg != null) Text(if (mg.frames > 0) "✓ Tracked through ${mg.frames} frames" else "Not tracked yet", style = Pro.Label.copy(color = if (mg.frames > 0) Pro.Green else Pro.Dim), modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        if (mg == null) "SELECT OBJECT…" else "CHANGE SELECTION…", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
                        modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable { onMagic() }.padding(10.dp),
                    )
                    if (mg != null && (c.startMs < mg.fromMs || c.endMs > mg.toMs)) Text("The clip is now longer than the tracked part — tap CHANGE SELECTION to track it again.", color = Pro.Amber, fontSize = 11.sp)
                }
                Chips(listOf("Keep the subject", "Affect the subject (invert)"), if (c.bg.invert == true) 1 else 0) { i -> onChange { cl -> cl.copy(bg = cl.bg?.copy(invert = i == 1)) } }
            }
            Text("People: found automatically in every frame. Magic Mask: tap any person, animal, car or object once and the AI follows it through the clip. Then blur, recolour or remove what's around it — or invert to blur / hide the object itself (a face, a number plate). Transparent shows the video layers placed below the main video.", color = Pro.Dim, fontSize = 11.sp)
            SectionTitle("REMOVE AN OBJECT (AI)")
            if (eraseProgress != null) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                androidx.compose.material3.LinearProgressIndicator(progress = { eraseProgress / 100f }, color = Pro.Amber, modifier = Modifier.weight(1f).height(4.dp))
                Text((eraseStatus ?: "") + " $eraseProgress%", style = Pro.Label, modifier = Modifier.padding(horizontal = 8.dp))
                Text("STOP", style = Pro.Label.copy(color = Pro.Red, fontSize = 10.sp), modifier = Modifier.clickable { onStopErase() }.padding(6.dp))
            } else Text(
                "SELECT THE OBJECT TO REMOVE…", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
                modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable { onErase() }.padding(10.dp),
            )
            Text("Tap a person, a car, a sign or a wire: Magic Mask follows it, then it's removed — what's behind it is taken from the frames before and after (the real background), and anything never seen is painted by the AI (LaMa, on this PC; the model, ${AiModel.INPAINT.sizeMb} MB, is downloaded once). It takes a while; the clip becomes a new file — undo brings the original back.", color = Pro.Dim, fontSize = 11.sp)
            SectionTitle("AI DEPTH") { onChange { it.copy(depth = null) } }
            val dfx = c.depth
            fun dset(f: (DepthFx) -> DepthFx) = onChange { cl -> cl.copy(depth = f(cl.depth ?: DepthFx())) }
            Chips(listOf("Off", "Lens blur", "Fog / haze", "Background light"), if (dfx == null) 0 else dfx.mode + 1) { i ->
                if (i == 0) onChange { it.copy(depth = null) } else dset { it.copy(mode = i - 1) }
            }
            if (dfx != null) {
                if (depthProgress != null) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    androidx.compose.material3.LinearProgressIndicator(progress = { depthProgress / 100f }, color = Pro.Amber, modifier = Modifier.weight(1f).height(4.dp))
                    Text(depthStatus ?: "$depthProgress%", style = Pro.Label, modifier = Modifier.padding(horizontal = 8.dp))
                    Text("STOP", style = Pro.Label.copy(color = Pro.Red, fontSize = 10.sp), modifier = Modifier.clickable { onStopDepth() }.padding(6.dp))
                } else Text(
                    if (dfx.frames > 0) "✓ Depth worked out (${dfx.frames} maps) — ANALYSE AGAIN" else "ANALYSE DEPTH", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
                    modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable { onDepth() }.padding(10.dp),
                )
                when (dfx.mode) {
                    0 -> {
                        EdSlider("Focus (far → near)", dfx.focus, 0f..1f, 0.85f, ::pct) { v -> dset { it.copy(focus = v) } }
                        EdSlider("Sharp zone", dfx.range, 0f..0.6f, 0.15f, ::pct) { v -> dset { it.copy(range = v) } }
                        EdSlider("Blur", dfx.amount, 0f..1f, 0.6f, ::pct) { v -> dset { it.copy(amount = v) } }
                    }
                    1 -> {
                        EdSlider("Fog", dfx.amount, 0f..1f, 0.6f, ::pct) { v -> dset { it.copy(amount = v) } }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.hintedScroll(horizontal = true).padding(vertical = 4.dp)) {
                            listOf(0xFFC9D3DD, 0xFFFFFFFF, 0xFFE8D9C0, 0xFFB8C8E8, 0xFF7F8C99, 0xFFF2C6A0).map { it.toInt() }.forEach { col ->
                                Box(Modifier.size(28.dp).clip(CircleShape).background(Color(col)).border(2.dp, if (dfx.fogColor == col) Pro.Amber else Color.DarkGray, CircleShape).clickable { dset { it.copy(fogColor = col) } })
                            }
                        }
                    }
                    else -> {
                        EdSlider("Subject distance", dfx.focus, 0f..1f, 0.85f, ::pct) { v -> dset { it.copy(focus = v) } }
                        EdSlider("Background exposure", dfx.bgExposure, -2f..1f, -0.6f, ::ev) { v -> dset { it.copy(bgExposure = v) } }
                        EdSlider("Background saturation", dfx.bgSaturation, -1f..0.5f, -0.3f, ::pct) { v -> dset { it.copy(bgSaturation = v) } }
                        EdSlider("Strength", dfx.amount, 0f..1f, 0.6f, ::pct) { v -> dset { it.copy(amount = v) } }
                    }
                }
                Chips(listOf("Picture", "Show depth map"), if (dfx.showMap == true) 1 else 0) { i -> dset { it.copy(showMap = (i == 1).takeIf { v -> v }) } }
            }
            Text("The AI works out how far away every part of the picture is (Depth Anything V2, on this PC — the model, ${AiModel.DEPTH.sizeMb} MB, is downloaded once). Then: a lens blur that keeps the chosen distance sharp, fog that thickens with distance, or a darker / calmer background behind your subject. Show depth map helps set the focus (white = near).", color = Pro.Dim, fontSize = 11.sp)
            SectionTitle("FINISHING")
            EdSlider("Vignette", c.detail.vignette) { v -> detail { it.copy(vignette = v) } }
            EdSlider("Film grain", c.detail.grain, 0f..1f, format = ::pct) { v -> detail { it.copy(grain = v) } }
        }
        ColorTab.FILM -> FilmLensPanel(c, onChange, clipNames, selfIdx, matchBusy, onMatch)
        ColorTab.EFFECTS -> {
            SectionTitle("EFFECTS") { detail { it.copy(fx = VideoFx.NONE, fxAmount = 0.6f) } }
            Chips(VideoFx.entries.map { it.label }, c.detail.fx.ordinal) { i -> detail { it.copy(fx = VideoFx.entries[i]) } }
            if (c.detail.fx != VideoFx.NONE) EdSlider("Amount", c.detail.fxAmount, 0f..1f, 0.6f, ::pct) { v -> detail { it.copy(fxAmount = v) } }
            Text("Effects show live in the viewer and are rendered on export.", color = Pro.Dim, fontSize = 11.sp)
        }
        ColorTab.LOOKS -> {
            SectionTitle("LOOKS / CREATIVE LUTS")
            LookStrip(g.look) { f -> grade { it.copy(look = f) } }
            if (g.look != Filter.NONE) EdSlider("Strength", g.lookAmount, 0f..1f, 1f, ::pct) { v -> grade { it.copy(lookAmount = v) } }
            UserPresetSection({ g to c.detail }) { p -> onChange { it.copy(grade = p.grade, detail = p.detail.copy(aiDenoise = it.detail.aiDenoise)) } }
            // Recommended looks for this clip (the AI looks at a frame from its middle), then all the built-in presets.
            val ctx = LocalContext.current
            val frame by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, c.uri, c.startMs, c.endMs) {
                value = withContext(Dispatchers.IO) { try { GradeIO.videoFrame(ctx, c.uri, (c.startMs + (c.endMs - c.startMs) / 2) * 1000, 448) } catch (_: Throwable) { null } }
            }
            BuiltInPresetSection(frame, g, c.detail) { ng, nd -> onChange { it.copy(grade = ng, detail = nd.copy(aiDenoise = it.detail.aiDenoise)) } }
        }
    }
}

@Composable
private fun AudioPage(
    c: Clip?, deliver: DeliverSettings, music: MusicTrack?, playing: Boolean,
    onChange: ((Clip) -> Clip) -> Unit, onPickMusic: () -> Unit, onMusic: (MusicTrack?) -> Unit,
    extra: List<MusicTrack> = emptyList(), onExtra: (String, MusicTrack?) -> Unit = { _, _ -> },
    posInClip: Long = 0L, recording: Boolean = false, recordMs: Long = 0L, onVoice: () -> Unit = {}, onBeats: (MusicTrack) -> Unit = {},
    onSync: (MusicTrack) -> Unit = {},
    onSeparate: () -> Unit = {}, separateStatus: String? = null,
    onTts: (String, Voiceover.Voice, Float) -> Unit = { _, _, _ -> }, ttsProgress: Int? = null, ttsStatus: String? = null, onStopTts: () -> Unit = {},
    mixer: MixerHooks? = null,
) {
    LevelMeter(playing)
    LoudnessMeter(playing)
    if (mixer != null) MixerSection(mixer, music, onMusic, extra, onExtra, playing)
    SectionTitle("AI VOICEOVER FROM TEXT")
    val previewScope = androidx.compose.runtime.rememberCoroutineScope()
    val previewCtx = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { Voiceover.stopPreview() } }
    var ttsText by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var ttsVoice by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
    var ttsSpeed by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(1f) }
    androidx.compose.material3.OutlinedTextField(
        value = ttsText, onValueChange = { ttsText = it },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), minLines = 2,
        label = { Text("What should the voice say? (English)") },
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedTextColor = com.dronedirector.veyrocutpro.ui.Pro.Fg, unfocusedTextColor = com.dronedirector.veyrocutpro.ui.Pro.Fg,
            focusedBorderColor = Pro.Amber, unfocusedBorderColor = Pro.Line, focusedLabelColor = Pro.Amber, cursorColor = Pro.Amber,
        ),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.hintedScroll(horizontal = true).padding(vertical = 4.dp)) {
        // Tap a voice to choose it and hear a short sample (your text's first sentence, or a hello).
        Voiceover.voices.forEachIndexed { i, v ->
            PillChip((if (Voiceover.previewing == v.id) "♪ " else "") + v.label, ttsVoice == i) {
                ttsVoice = i
                previewScope.launch {
                    val err = withContext(Dispatchers.IO) { try { Voiceover.preview(previewCtx, v, ttsText, { false }) } catch (t: Throwable) { t.message ?: "error" } }
                    if (err != null) android.widget.Toast.makeText(previewCtx, com.dronedirector.veyrocutpro.ui.I18n.tr("Couldn't play the voice sample") + " ($err)", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    Text(if (Voiceover.previewing != null) "Preparing the sample… (the first time, the voice is downloaded)" else "Tap a voice to hear it.", color = Pro.Dim, fontSize = 11.sp)
    EdSlider("Speed", ttsSpeed, 0.6f..1.5f, 1f, { "%.2f×".format(it) }) { ttsSpeed = it }
    if (ttsProgress != null) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        androidx.compose.material3.LinearProgressIndicator(progress = { ttsProgress / 100f }, color = Pro.Amber, modifier = Modifier.weight(1f).height(4.dp))
        Text((ttsStatus ?: "") + " $ttsProgress%", style = Pro.Label, modifier = Modifier.padding(horizontal = 8.dp))
        Text("STOP", style = Pro.Label.copy(color = Pro.Red, fontSize = 10.sp), modifier = Modifier.clickable { onStopTts() }.padding(6.dp))
    } else Text(
        "MAKE VOICEOVER AT THE PLAYHEAD", style = Pro.Label.copy(color = if (ttsText.isNotBlank()) Pro.Amber else Pro.Dim, fontSize = 11.sp),
        modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable(enabled = ttsText.isNotBlank()) { onTts(ttsText, Voiceover.voices[ttsVoice], ttsSpeed) }.padding(10.dp),
    )
    Text("A natural AI voice reads your text (Kokoro, on this PC — the voice model, ${AiModel.VOICE.sizeMb} MB, is downloaded once). It's added as an audio track at the playhead; numbers, money and times are read out properly.", color = Pro.Dim, fontSize = 11.sp)
    SectionTitle("VOICEOVER")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(54.dp).clip(CircleShape).background(if (recording) Pro.Red else Ed.Raised).border(2.dp, Pro.Red, CircleShape).clickable(onClick = onVoice),
            contentAlignment = Alignment.Center,
        ) {
            if (recording) Box(Modifier.size(18.dp).clip(RoundedCornerShape(3.dp)).background(Color.White))
            else Box(Modifier.size(22.dp).clip(CircleShape).background(Pro.Red))
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text(if (recording) "Recording… ${tc(recordMs)}" else "Record a voiceover", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (recording) "Tap to stop — it's added as an audio track where you started." else "Starts at the playhead and plays the edit (muted) while you speak. Headphones help.",
                color = Pro.Dim, fontSize = 11.sp,
            )
        }
    }
    if (music != null) {
        Text(
            "♪ ADD BEAT MARKERS FROM THE MUSIC", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
            modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable { onBeats(music) }.padding(10.dp),
        )
        Text("Finds the beat of the music track and drops a marker on every beat — with SNAP on, layers and cuts lock to them.", color = Pro.Dim, fontSize = 11.sp)
        Text(
            "⇆ SYNC TO THE SELECTED CLIP (BY SOUND)", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
            modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable { onSync(music) }.padding(10.dp),
        )
        Text("For sound from an external recorder or wireless mic: select the clip filmed at the same time, then sync — the track is shifted until its sound matches the clip's own sound (SYNC on any audio track does the same).", color = Pro.Dim, fontSize = 11.sp)
    }
    if (extra.isNotEmpty()) {
        SectionTitle("MORE AUDIO TRACKS")
        extra.forEach { t ->
            Column(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Color(0xFF0FA3A3))
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(t.name, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("at ${tc(t.startMs)}", style = Pro.Label)
                    }
                    Text("SYNC", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp), modifier = Modifier.clickable { onSync(t) }.padding(6.dp))
                    Text("BEATS", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp), modifier = Modifier.clickable { onBeats(t) }.padding(6.dp))
                    Text("REMOVE", style = Pro.Label.copy(color = Pro.Red, fontSize = 10.sp), modifier = Modifier.clickable { onExtra(t.id, null) }.padding(6.dp))
                }
                EdSlider("Volume", t.volume, 0f..1.5f, 1f, ::pct) { v -> onExtra(t.id, t.copy(volume = v)) }
                EdSlider("Fade in", t.fadeInMs / 1000f, 0f..5f, 0f, { "%.1f s".format(it) }) { v -> onExtra(t.id, t.copy(fadeInMs = (v * 1000).toLong())) }
                EdSlider("Fade out", t.fadeOutMs / 1000f, 0f..5f, 0.3f, { "%.1f s".format(it) }) { v -> onExtra(t.id, t.copy(fadeOutMs = (v * 1000).toLong())) }
            }
        }
    }
    if (c != null) {
        val fx = c.audio
        fun setFx(f: (AudioFx) -> AudioFx) = onChange { it.copy(audio = f(it.audio)) }
        SectionTitle("CLIP AUDIO", onReset = { onChange { it.copy(audio = AudioFx(), volume = 1f, muted = false) } })
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallAction(if (c.muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp, if (c.muted) "Muted" else "Sound on") { onChange { it.copy(muted = !it.muted) } }
            SmallAction(Icons.Filled.MusicNote, if (fx.duckMusic) "Lowers music" else "Music normal") { setFx { it.copy(duckMusic = !it.duckMusic) } }
        }
        EdSlider("Clip volume", c.volume, 0f..2f, 1f, { "%+.1f dB".format(dbfs(it)) }) { v -> onChange { it.copy(volume = v) } }
        Text("VOICE NOISE REDUCTION", style = Pro.Label, modifier = Modifier.padding(top = 6.dp))
        Chips(listOf("Off", "Light", "Strong", "Voice isolation (AI)", "Studio voice (AI)"), c.audio.denoise.coerceIn(0, 4)) { i -> onChange { it.copy(audio = it.audio.copy(denoise = i)) } }
        if (c.audio.denoise == 3) Text("Keeps the voice and removes wind, traffic, hum, crowd and background music — on export. Works best on speech.", color = Pro.Dim, fontSize = 11.sp)
        if (c.audio.denoise >= 4) Text("Studio voice: DeepFilterNet3, the cleanest AI voice clean-up — removes noise of every kind and takes room echo down, keeping the voice natural. On export, on the PC.", color = Pro.Dim, fontSize = 11.sp)
        Text("REPAIR", style = Pro.Label, modifier = Modifier.padding(top = 6.dp))
        Chips(listOf("Clicks off", "Remove clicks & crackle"), if (c.audio.declick) 1 else 0) { i -> onChange { it.copy(audio = it.audio.copy(declick = i == 1)) } }
        EdSlider("Remove room echo", c.audio.dereverb, 0f..1f, 0f, ::pct) { v -> onChange { it.copy(audio = it.audio.copy(dereverb = v)) } }
        Text("Cleans hiss, hum and background noise from this clip's sound. Heard in the export (the preview plays the original).", color = Pro.Dim, fontSize = 11.sp)
        Text("VOICE / MUSIC SEPARATION (AI)", style = Pro.Label, modifier = Modifier.padding(top = 6.dp))
        Text(separateStatus ?: "SEPARATE VOICE AND MUSIC", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
            modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable(enabled = separateStatus == null) { onSeparate() }.padding(10.dp))
        Text("Splits this clip's sound into a voice track and a music track (everything else), added in line with the clip so each has its own volume — the clip itself is muted. Uses Demucs via your fal.ai account (about \$0.04 per minute of the file's sound).", color = Pro.Dim, fontSize = 11.sp)
        SectionTitle("VOLUME KEYFRAMES") { onChange { it.copy(volKeys = null) } }
        Text("Rides the clip's volume over time — e.g. dip it under a voiceover. Add keyframes at different playhead positions and set each level.", color = Pro.Dim, fontSize = 11.sp)
        Text(
            "◆ ADD AT PLAYHEAD (${tc(posInClip)})", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
            modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable {
                onChange { cl ->
                    val ks = cl.volKeys.orEmpty().filter { kotlin.math.abs(it.t - posInClip) > 100 }
                    cl.copy(volKeys = (ks + VolKey(posInClip, volAt(cl.volKeys, posInClip))).sortedBy { it.t })
                }
            }.padding(10.dp),
        )
        c.volKeys.orEmpty().forEachIndexed { ki, k ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    EdSlider("◆ ${tc(k.t)}", k.gain, 0f..2f, 1f, { "%+.1f dB".format(dbfs(it)) }) { v ->
                        onChange { cl -> cl.copy(volKeys = cl.volKeys.orEmpty().mapIndexed { j, x -> if (j == ki) x.copy(gain = v) else x }) }
                    }
                }
                Icon(Icons.Filled.Close, contentDescription = "Delete keyframe", tint = Color.White, modifier = Modifier.padding(start = 6.dp).size(18.dp).clickable {
                    onChange { cl -> cl.copy(volKeys = cl.volKeys.orEmpty().filterIndexed { j, _ -> j != ki }.ifEmpty { null }) }
                })
            }
        }
        EdSlider("Pan / balance", fx.pan, -1f..1f, 0f, { if (it < -0.01f) "L ${(-it * 100).toInt()}" else if (it > 0.01f) "R ${(it * 100).toInt()}" else "Centre" }) { v -> setFx { it.copy(pan = v) } }
        Text("Channels", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        Chips(AudioChannels.entries.map { it.label }, fx.channels.ordinal) { i -> setFx { it.copy(channels = AudioChannels.entries[i]) } }
        EdSlider("Fade in", fx.fadeInMs / 1000f, 0f..5f, 0f, { "%.1f s".format(it) }) { v -> setFx { it.copy(fadeInMs = (v * 1000).toLong()) } }
        EdSlider("Fade out", fx.fadeOutMs / 1000f, 0f..5f, 0f, { "%.1f s".format(it) }) { v -> setFx { it.copy(fadeOutMs = (v * 1000).toLong()) } }

        SectionTitle("PITCH & VOICE", onReset = { setFx { it.copy(pitch = 0f, voiceFx = 0) } })
        EdSlider("Pitch", fx.pitch, -12f..12f, 0f, { v -> if (kotlin.math.abs(v) < 0.05f) "Normal" else "%+.1f semitones".format(v) }) { v -> setFx { it.copy(pitch = (kotlin.math.round(v * 2f) / 2f)) } }
        Text("Higher or lower voice without changing the speed (+12 = an octave up, −12 = an octave down).", color = Pro.Dim, fontSize = 11.sp)
        Chips(listOf("Normal", "Robot", "Radio", "Phone", "Megaphone"), fx.voiceFx) { i -> setFx { it.copy(voiceFx = i) } }

        SectionTitle("PRESETS")
        Chips(AudioPreset.entries.map { it.label }, AudioPreset.entries.indexOfFirst { p ->
            p.fx.copy(pan = fx.pan, fadeInMs = fx.fadeInMs, fadeOutMs = fx.fadeOutMs, duckMusic = fx.duckMusic, limiter = fx.limiter) == fx
        }) { i ->
            val p = AudioPreset.entries[i].fx
            setFx { it.copy(low = p.low, mid = p.mid, high = p.high, lowCut = p.lowCut, voice = p.voice, compressor = p.compressor, gate = p.gate, channels = p.channels) }
        }

        SectionTitle("EQUALIZER", onReset = { setFx { it.copy(low = 0f, mid = 0f, high = 0f, lowCut = false, voice = false) } })
        val db = { v: Float -> "%+.1f dB".format(v) }
        EdSlider("Low (bass)", fx.low, -12f..12f, 0f, db) { v -> setFx { it.copy(low = v) } }
        EdSlider("Mid", fx.mid, -12f..12f, 0f, db) { v -> setFx { it.copy(mid = v) } }
        EdSlider("High (treble)", fx.high, -12f..12f, 0f, db) { v -> setFx { it.copy(high = v) } }
        AudioSwitch("Low cut (80 Hz)", "Removes wind rumble and handling noise", fx.lowCut) { b -> setFx { it.copy(lowCut = b) } }
        AudioSwitch("Voice enhance", "Clearer speech: trims boom, lifts presence", fx.voice) { b -> setFx { it.copy(voice = b) } }

        SectionTitle("PARAMETRIC EQ", onReset = { setFx { it.copy(bands = null) } })
        ParametricEq(fx.bands.orEmpty(), playing) { nb -> setFx { it.copy(bands = nb) } }

        SectionTitle("DE-ESSER", onReset = { setFx { it.copy(deess = 0f) } })
        EdSlider("De-esser", fx.deess, 0f..1f, 0f, { if (it <= 0f) "Off" else "%.0f%%".format(it * 100) }) { v -> setFx { it.copy(deess = v) } }
        Text("Tames harsh \"s\" and \"sh\" sounds in speech without dulling the rest of the voice.", color = Pro.Dim, fontSize = 11.sp)

        SectionTitle("DYNAMICS", onReset = { setFx { it.copy(compressor = 0f, gate = 0f, limiter = true, leveler = 0f) } })
        EdSlider("Dialogue leveller", fx.leveler, 0f..1f, 0f, ::pct) { v -> setFx { it.copy(leveler = v) } }
        Text("Rides the level of speech: quiet words come up, loud ones go down, so the whole clip sounds even. Pauses are left alone.", color = Pro.Dim, fontSize = 11.sp)
        EdSlider("Compressor", fx.compressor, 0f..1f, 0f, ::pct) { v -> setFx { it.copy(compressor = v) } }
        Text("Evens out loud and quiet parts and adds make-up gain.", color = Pro.Dim, fontSize = 11.sp)
        EdSlider("Noise gate", fx.gate, 0f..1f, 0f, { if (it <= 0f) "Off" else "%.0f dB".format(-62f + it * 30f) }) { v -> setFx { it.copy(gate = v) } }
        Text("Silences background hiss between words or sounds below the threshold.", color = Pro.Dim, fontSize = 11.sp)
        AudioSwitch("Limiter", "Soft-limits peaks so boosted audio never clips", fx.limiter) { b -> setFx { it.copy(limiter = b) } }
        Text("Fades play in the export; everything else is heard live in the preview.", color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    }

    SectionTitle("MUSIC TRACK")
    if (music == null) {
        BigButton(Icons.Filled.MusicNote, "Add music (MP3, AAC, WAV…)", Modifier.fillMaxWidth(), onPickMusic)
        Text("The music plays under the whole timeline, mixed with the clips' own sound.", color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
    } else {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Ed.Raised).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Pro.Amber)
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(music.name, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(tc(music.durationMs), style = Pro.Label)
            }
            Text("REPLACE", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp), modifier = Modifier.clickable(onClick = onPickMusic).padding(6.dp))
            Text("REMOVE", style = Pro.Label.copy(color = Pro.Red, fontSize = 10.sp), modifier = Modifier.clickable { onMusic(null) }.padding(6.dp))
        }
        EdSlider("Start from", music.offsetMs.toFloat(), 0f..(music.durationMs - 1000).coerceAtLeast(1).toFloat(), 0f, { tc(it.toLong()) }) { v -> onMusic(music.copy(offsetMs = v.toLong())) }
        EdSlider("Music volume", music.volume, 0f..1f, 0.8f, ::pct) { v -> onMusic(music.copy(volume = v)) }
        EdSlider("Fade in", music.fadeInMs / 1000f, 0f..5f, 1f, { "%.1f s".format(it) }) { v -> onMusic(music.copy(fadeInMs = (v * 1000).toLong())) }
        EdSlider("Fade out", music.fadeOutMs / 1000f, 0f..8f, 2f, { "%.1f s".format(it) }) { v -> onMusic(music.copy(fadeOutMs = (v * 1000).toLong())) }
        EdSlider("Ducking", music.duck, 0f..0.95f, 0.6f, { "%.0f dB".format(dbfs(1f - it)) }) { v -> onMusic(music.copy(duck = v)) }
        Text("How much the music drops under clips set to \"Lowers music\" (tap the music button on a clip).", color = Pro.Dim, fontSize = 11.sp)
        val db = { v: Float -> "%+.1f dB".format(v) }
        EdSlider("Music bass", music.fx.low, -12f..12f, 0f, db) { v -> onMusic(music.copy(fx = music.fx.copy(low = v))) }
        EdSlider("Music mids", music.fx.mid, -12f..12f, 0f, db) { v -> onMusic(music.copy(fx = music.fx.copy(mid = v))) }
        EdSlider("Music treble", music.fx.high, -12f..12f, 0f, db) { v -> onMusic(music.copy(fx = music.fx.copy(high = v))) }
    }
    if (!deliver.audio) Text("Audio is turned off in Deliver.", color = Pro.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun AudioSwitch(title: String, detail: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 13.sp)
            Text(detail, color = Pro.Dim, fontSize = 11.sp)
        }
        androidx.compose.material3.Switch(
            checked = on, onCheckedChange = onChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Pro.Amber, checkedThumbColor = Color.Black),
        )
    }
}

/** What the mixer needs from the editor. */
class MixerHooks(
    val deliver: DeliverSettings, val onDeliver: (DeliverSettings) -> Unit,
    val videoMuted: Boolean, val onVideoMuted: (Boolean) -> Unit,
    val solo: String?, val onSolo: (String?) -> Unit,
    val videoPeak: () -> Float, val musicPeak: () -> Float, val extraPeak: (String) -> Float,
)

/** Live loudness (LUFS) of the preview: momentary, short-term and integrated since RESET. */
@Composable
private fun LoudnessMeter(playing: Boolean) {
    var m by remember { mutableStateOf(-70f) }; var st by remember { mutableStateOf(-70f) }; var i by remember { mutableStateOf(-70f) }
    LaunchedEffect(playing) {
        while (playing) { m = AudioMeter.momentary; st = AudioMeter.shortTerm; i = AudioMeter.integrated; delay(200) }
        AudioMeter.momentary = -70f; m = -70f; st = AudioMeter.shortTerm; i = AudioMeter.integrated
    }
    fun f(v: Float) = if (v <= -69f) "—" else "%.1f".format(v)
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("LUFS", style = Pro.Label, modifier = Modifier.width(40.dp))
        listOf("M" to m, "S" to st, "I" to i).forEach { (k, v) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(k, style = Pro.Label.copy(color = Pro.Dim))
                Text(f(v), style = Pro.Value.copy(fontSize = 13.sp, color = if (v > -9f) Color(0xFFFF3B30) else if (v in -16f..-12f) Color(0xFF34C759) else Color.White))
            }
        }
        Text("RESET", style = Pro.Label.copy(color = Pro.Amber), modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { AudioMeter.resetIntegrated(); AudioMeter.integrated = -70f }.padding(6.dp))
    }
    Text("Momentary, short-term and integrated loudness of what plays (play the whole edit for the integrated value). Targets: YouTube / Spotify −14, Apple −16, broadcast −23 — Deliver → LOUDNESS sets the export to one.", color = Pro.Dim, fontSize = 11.sp)
}

/** Mixer: every track with a live meter, level, pan, mute and solo, and the master level. */
@Composable
private fun MixerSection(mx: MixerHooks, music: MusicTrack?, onMusic: (MusicTrack?) -> Unit, extra: List<MusicTrack>, onExtra: (String, MusicTrack?) -> Unit, playing: Boolean) {
    var tick by remember { mutableStateOf(0) }
    // Meters move only while playing; when stopped they settle once (no constant redraws).
    LaunchedEffect(playing) { while (playing) { tick++; delay(80) }; repeat(6) { tick++; delay(80) } }
    SectionTitle("MIXER", onReset = { mx.onDeliver(mx.deliver.copy(videoGain = 1f, videoPan = 0f, masterGain = 1f)); mx.onSolo(null) })
    @Composable
    fun Strip(id: String, name: String, level: Float, pan: Float, muted: Boolean, peak: Float, onLevel: (Float) -> Unit, onPan: (Float) -> Unit, onMute: (Boolean) -> Unit) {
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp)).background(Ed.Raised).padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, color = Color.White, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f))
                val solo = mx.solo == id
                Text("S", style = Pro.Label.copy(color = if (solo) Color.Black else Color.White), modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(6.dp)).background(if (solo) Color(0xFFFFCC00) else com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.1f)).clickable { mx.onSolo(if (solo) null else id) }.padding(horizontal = 10.dp, vertical = 5.dp))
                Text("M", style = Pro.Label.copy(color = if (muted) Color.Black else Color.White), modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(6.dp)).background(if (muted) Color(0xFFFF3B30) else com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.1f)).clickable { onMute(!muted) }.padding(horizontal = 10.dp, vertical = 5.dp))
            }
            // Live meter of this track.
            androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.08f))) {
                @Suppress("UNUSED_EXPRESSION") tick
                val k = ((dbfs(peak) + 60f) / 60f).coerceIn(0f, 1f)
                drawRect(androidx.compose.ui.graphics.Brush.horizontalGradient(0f to Color(0xFF34C759), 0.75f to Color(0xFFFFCC00), 0.95f to Color(0xFFFF3B30)), size = androidx.compose.ui.geometry.Size(size.width * k, size.height))
            }
            EdSlider("Level", level, 0f..2f, 1f, { if (it <= 0.001f) "−∞" else "%+.1f dB".format(20 * kotlin.math.log10(it)) }, onLevel)
            EdSlider("Pan", pan, -1f..1f, 0f, { if (it < -0.01f) "L ${(-it * 100).toInt()}" else if (it > 0.01f) "R ${(it * 100).toInt()}" else "Centre" }, onPan)
        }
    }
    val d = mx.deliver
    Strip("video", "Video track (clip sound)", d.videoGain, d.videoPan, mx.videoMuted, mx.videoPeak(), { mx.onDeliver(d.copy(videoGain = it)) }, { mx.onDeliver(d.copy(videoPan = it)) }, mx.onVideoMuted)
    music?.let { m -> Strip("music", "Music · ${m.name}", m.volume / 0.8f, m.fx.pan, m.muted, mx.musicPeak(), { onMusic(m.copy(volume = it * 0.8f)) }, { onMusic(m.copy(fx = m.fx.copy(pan = it))) }, { onMusic(m.copy(muted = it)) }) }
    extra.forEach { t -> Strip(t.id, t.name, t.volume / 0.8f, t.fx.pan, t.muted, mx.extraPeak(t.id), { onExtra(t.id, t.copy(volume = it * 0.8f)) }, { onExtra(t.id, t.copy(fx = t.fx.copy(pan = it))) }, { onExtra(t.id, t.copy(muted = it)) }) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp)).background(Pro.Amber.copy(alpha = 0.12f)).padding(8.dp)) {
        Text("MASTER", style = Pro.Label.copy(color = Pro.Amber))
        EdSlider("Master level", d.masterGain, 0f..2f, 1f, { if (it <= 0.001f) "−∞" else "%+.1f dB".format(20 * kotlin.math.log10(it)) }) { mx.onDeliver(d.copy(masterGain = it)) }
    }
    Text("Levels, pan and mute go into the export; solo (S) is for listening only. Level 0 dB = as recorded; the limiter keeps boosted tracks from clipping.", color = Pro.Dim, fontSize = 11.sp)
    TrackGroups(mx, music, onMusic, extra, onExtra)
}

/** A mixer track for the groups: id, name, level (0..2), muted. */
private data class GroupTrack(val id: String, val name: String, val level: Float, val muted: Boolean)

/**
 * Track groups (VCA faders): every track belongs to Dialogue, Music or Effects. A group's fader raises or
 * lowers all its tracks together (keeping their balance) and its mute silences them all.
 */
@Composable
private fun TrackGroups(mx: MixerHooks, music: MusicTrack?, onMusic: (MusicTrack?) -> Unit, extra: List<MusicTrack>, onExtra: (String, MusicTrack?) -> Unit) {
    val d = mx.deliver
    val names = listOf("Dialogue", "Music", "Effects")
    fun groupOf(id: String): Int = d.groups?.get(id) ?: when (id) { "video" -> 0; "music" -> 1; else -> 2 }
    val tracks = buildList {
        add(GroupTrack("video", "Video track (clip sound)", d.videoGain, mx.videoMuted))
        music?.let { add(GroupTrack("music", "Music · ${it.name}", it.volume / 0.8f, it.muted)) }
        extra.forEach { add(GroupTrack(it.id, it.name, it.volume / 0.8f, it.muted)) }
    }
    val levels = (d.groupLevels ?: listOf(1f, 1f, 1f)).let { if (it.size < 3) listOf(1f, 1f, 1f) else it }
    fun setLevels(members: List<GroupTrack>, ratio: Float, base: DeliverSettings) {
        var nd = base
        members.forEach { t ->
            val nl = (t.level * ratio).coerceIn(0f, 2f)
            when (t.id) {
                "video" -> nd = nd.copy(videoGain = nl)
                "music" -> music?.let { onMusic(it.copy(volume = nl * 0.8f)) }
                else -> extra.firstOrNull { it.id == t.id }?.let { onExtra(it.id, it.copy(volume = nl * 0.8f)) }
            }
        }
        mx.onDeliver(nd)
    }
    SectionTitle("TRACK GROUPS (VCA)", onReset = { mx.onDeliver(d.copy(groups = null, groupLevels = null)) })
    for (k in 0..2) {
        val members = tracks.filter { groupOf(it.id) == k }
        val allMuted = members.isNotEmpty() && members.all { it.muted }
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp)).background(Ed.Raised).padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(names[k], color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                androidx.compose.material3.Text("${members.size} track" + if (members.size == 1) "" else "s", color = Pro.Dim, fontSize = 11.sp)
                Text("M", style = Pro.Label.copy(color = if (allMuted) Color.Black else Color.White),
                    modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(6.dp)).background(if (allMuted) Color(0xFFFF9F0A) else com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.1f))
                        .clickable(enabled = members.isNotEmpty()) {
                            val m = !allMuted
                            members.forEach { t ->
                                when (t.id) {
                                    "video" -> mx.onVideoMuted(m)
                                    "music" -> music?.let { onMusic(it.copy(muted = m)) }
                                    else -> extra.firstOrNull { it.id == t.id }?.let { onExtra(it.id, it.copy(muted = m)) }
                                }
                            }
                        }.padding(horizontal = 8.dp, vertical = 4.dp))
            }
            EdSlider("Group fader", levels[k], 0.06f..2f, 1f, { "%+.1f dB".format(20 * kotlin.math.log10(it)) }) { nv ->
                val old = levels[k].coerceAtLeast(0.06f)
                val nl = levels.toMutableList().also { it[k] = nv }
                setLevels(members, nv / old, d.copy(groupLevels = nl))
            }
        }
    }
    androidx.compose.material3.Text("TRACKS", style = Pro.Label, modifier = Modifier.padding(top = 6.dp))
    tracks.forEach { t ->
        androidx.compose.material3.Text(t.name, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
        Chips(names, groupOf(t.id)) { g -> mx.onDeliver(d.copy(groups = (d.groups ?: emptyMap()) + (t.id to g))) }
    }
    Text("A group fader moves all its tracks together and keeps their balance; M mutes the whole group. Each track's own level stays in the strips above.", color = Pro.Dim, fontSize = 11.sp)
}

/** Live L/R peak meter of the preview (after the clip's audio processing). */
@Composable
private fun LevelMeter(playing: Boolean) {
    var l by remember { mutableStateOf(-60f) }
    var r by remember { mutableStateOf(-60f) }
    LaunchedEffect(playing) {
        while (playing) {
            l = dbfs(AudioMeter.left); r = dbfs(AudioMeter.right)
            AudioMeter.left *= 0.8f; AudioMeter.right *= 0.8f
            delay(60)
        }
        // Stopped: let the bars fall to silence, then stop redrawing.
        repeat(12) { AudioMeter.left *= 0.5f; AudioMeter.right *= 0.5f; l = dbfs(AudioMeter.left); r = dbfs(AudioMeter.right); delay(60) }
    }
    SectionTitle("LEVELS")
    listOf("L" to l, "R" to r).forEach { (name, v) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = Pro.Label, modifier = Modifier.width(16.dp))
            androidx.compose.foundation.Canvas(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(3.dp)).background(com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.08f))) {
                val k = ((v + 60f) / 60f).coerceIn(0f, 1f)
                drawRect(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        0f to Color(0xFF34C759), 0.75f to Color(0xFFFFCC00), 0.95f to Color(0xFFFF3B30),
                    ),
                    size = androidx.compose.ui.geometry.Size(size.width * k, size.height),
                )
                // -12 dB and -6 dB guides
                listOf(-12f, -6f).forEach { g -> val x = size.width * (g + 60f) / 60f; drawLine(Color.White.copy(alpha = 0.35f), androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height)) }
            }
            Text(if (v <= -59.9f) "−∞" else "%.0f".format(v), style = Pro.Value.copy(fontSize = 10.sp), modifier = Modifier.width(34.dp).padding(start = 6.dp))
        }
    }
}

/** Text, stickers and watermarks drawn over the viewer; drag the selected one to move it. */
@Composable
private fun LayerPreview(
    layers: List<Layer>, timeMs: Long, selectedId: String?, showAll: Boolean, playing: Boolean,
    onMove: (String, Float, Float) -> Unit, onSelect: (String) -> Unit,
    onPinCorner: (String, Int, Float, Float) -> Unit = { _, _, _, _ -> },
) {
    val context = LocalContext.current
    // Moving overlays (visualizer, countdown, progress bar, flight path) redraw 25 times a second while playing,
    // on the player's own clock, instead of at the editor's slower position updates.
    val hasLive = layers.any { it.kind == LayerKind.FX && it.fx != FxLayers.BARS && it.fx != FxLayers.FLARE }
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(playing, hasLive) { if (playing && hasLive) while (true) { kotlinx.coroutines.delay(40); tick++ } }
    val liveMs = if (playing && hasLive) { tick; timeMs + (PreviewClock.now() - PreviewClock.posMs).coerceIn(0L, 250L) } else timeMs
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }.toInt().coerceAtLeast(2)
        val hPx = with(density) { maxHeight.toPx() }.toInt().coerceAtLeast(2)
        layers.forEach { base -> androidx.compose.runtime.key(base.id) { LayerPreviewItem(base, timeMs, liveMs, selectedId, showAll, playing, wPx, hPx, density, onMove, onSelect, onPinCorner) } }
    }
}

/** One layer drawn over the picture (its own composable, so each layer keeps its own remembered state). */
@Composable
private fun LayerPreviewItem(
    base: Layer, timeMs: Long, liveMs: Long, selectedId: String?, showAll: Boolean, playing: Boolean,
    wPx: Int, hPx: Int, density: androidx.compose.ui.unit.Density,
    onMove: (String, Float, Float) -> Unit, onSelect: (String) -> Unit, onPinCorner: (String, Int, Float, Float) -> Unit,
) {
    val context = LocalContext.current
    // Hidden layers (eye off on the timeline) are never drawn in the video feed.
    if (!base.visible) return
    // While playing, a layer shows only in its own time. Paused, the selected layer (and on the Layers page every
    // layer) is also shown outside its time so it can be placed — faded, so it's clear it isn't on at this moment.
    val ghost = !base.activeAt(timeMs)
    if (ghost && (playing || !(showAll || base.id == selectedId))) return
    // Keyframed layers: where they are at this moment.
    val l = base.animAt(timeMs)
    val pinQ = base.pinAt(timeMs)
    if (pinQ != null) {
        // Corner pin: drawn in perspective onto its four corners; drag the corners to place it.
        if (l.kind == LayerKind.VIDEO) Box(Modifier.fillMaxSize()) { VideoLayerView(l, timeMs, playing && l.activeAt(timeMs), pinQ) }
        else {
            val pchars = LayerAnims.charCount(base, 1f)
            val plf = if (base.kind == LayerKind.LOTTIE) LottieRender.frameIndex(context, base, timeMs) else 0
            val pbmp by androidx.compose.runtime.produceState<Bitmap?>(null, base.copy(x = 0f, y = 0f, startMs = 0, endMs = 0, keys = null, opacity = 1f, animIn = null, animOut = null, animMs = null, pin = null), wPx, hPx, plf) {
                value = withContext(Dispatchers.Default) { LayerRenderer.render(context, base, wPx, hPx, pchars, atMs = if (base.kind == LayerKind.LOTTIE) timeMs else -1L) }
            }
            pbmp?.let { b0 ->
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    drawIntoCanvas { cv ->
                        val nc = cv.nativeCanvas
                        nc.save()
                        nc.concat(pinMatrix(pinQ, b0.width.toFloat(), b0.height.toFloat(), size.width, size.height))
                        nc.drawBitmap(b0, 0f, 0f, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG).apply { alpha = (l.opacity.coerceIn(0f, 1f) * 255).toInt() })
                        nc.restore()
                    }
                }
            }
        }
        if (base.id == selectedId && showAll && !base.locked) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val pts = (0 until 4).map { Offset(pinQ[2 * it] * size.width, pinQ[2 * it + 1] * size.height) }
                for (k in 0 until 4) drawLine(Pro.Amber, pts[k], pts[(k + 1) % 4], strokeWidth = 2f)
            }
            for (k in 0 until 4) {
                val hs = with(density) { 30.dp.toPx() }
                Box(
                    Modifier
                        .offset { androidx.compose.ui.unit.IntOffset((pinQ[2 * k] * wPx - hs / 2).toInt(), (pinQ[2 * k + 1] * hPx - hs / 2).toInt()) }
                        .size(30.dp).clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f)).border(2.dp, Pro.Amber, androidx.compose.foundation.shape.CircleShape)
                        .pointerInput(base.id, k) {
                            detectDragGestures { change, drag -> change.consume(); onPinCorner(base.id, k, drag.x / wPx, drag.y / hPx) }
                        },
                )
            }
        }
        return
    }
    if (l.kind == LayerKind.VIDEO) {
        val h = (l.size * hPx).toInt().coerceAtLeast(8)
        val w = (h * l.frameAspect).toInt().coerceAtLeast(8)
        val sel = l.id == selectedId
        // Picture-in-picture frame: rounded corners, border and drop shadow (as in the export).
        val shape = RoundedCornerShape(with(density) { ((l.corner ?: 0f) * minOf(w, h)).toDp() })
        val bpx = with(density) { ((l.border ?: 0f) * hPx).toDp() }
        Box(
            Modifier
                .offset { androidx.compose.ui.unit.IntOffset((l.x * wPx - w / 2f).toInt(), (l.y * hPx - h / 2f).toInt()) }
                .size(with(density) { w.toDp() }, with(density) { h.toDp() })
                .then(if (l.dropShadow == true) Modifier.shadow(10.dp, shape) else Modifier)
                .clip(shape)
                .then(if ((l.border ?: 0f) > 0f) Modifier.border(bpx, Color(l.borderColor ?: 0xFFFFFFFF.toInt()), shape) else Modifier)
                .then(if (sel) Modifier.border(1.dp, Pro.Amber, shape) else Modifier)
                .pointerInput(l.id) {
                    detectDragGestures(onDragStart = { onSelect(l.id) }) { change, drag ->
                        change.consume()
                        onMove(l.id, drag.x / wPx, drag.y / hPx)
                    }
                },
        ) {
            if (l.cellAspect != null) {
                // Cropped to fill its cell (split screen): the video keeps its shape and overflows.
                val ww: Int; val hh: Int
                if (l.mediaAspect > l.frameAspect) { hh = h; ww = (h * l.mediaAspect).toInt() } else { ww = w; hh = (w / l.mediaAspect).toInt() }
                Box(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
                    Box(Modifier.requiredSize(with(density) { ww.toDp() }, with(density) { hh.toDp() })) { VideoLayerView(l, timeMs, playing && l.activeAt(timeMs)) }
                }
            } else VideoLayerView(l, timeMs, playing && l.activeAt(timeMs))
        }
        return
    }
    // In / out animation (not while you're editing this layer with the video paused).
    val fx = if ((base.id == selectedId && !playing) || !base.activeAt(timeMs)) AnimFx() else LayerAnims.at(base, timeMs)
    val chars = LayerAnims.charCount(base, fx.chars)
    val hiWord = if (base.words != null) base.wordAt(timeMs) else -1
    // Lottie animations: a new picture for each animation frame.
    val fxMs = if (base.kind == LayerKind.FX) liveMs else timeMs
    val lf = if (base.kind == LayerKind.LOTTIE) LottieRender.frameIndex(context, base, timeMs) else 0
    val liveRef = androidx.compose.runtime.rememberUpdatedState(fxMs)
    val shape = base.copy(x = 0f, y = 0f, keys = null, opacity = 1f, animIn = null, animOut = null, animMs = null)
    val bmp by if (base.kind == LayerKind.FX) {
        // Moving overlays: one render after another, always at the newest moment. (Restarting the render on every
        // tick would cancel it before it finished when a frame takes longer than a tick, and the picture would freeze.)
        androidx.compose.runtime.produceState<Bitmap?>(null, shape, wPx, hPx) {
            var last = Int.MIN_VALUE
            while (true) {
                val ms = liveRef.value
                val n = FxLayers.frameIndex(base, ms)
                if (n != last || value == null) {
                    last = n
                    value = withContext(Dispatchers.Default) { LayerRenderer.render(context, base, wPx, hPx, atMs = ms) }
                } else kotlinx.coroutines.delay(15)
            }
        }
    } else androidx.compose.runtime.produceState<Bitmap?>(null, shape, wPx, hPx, chars, hiWord, lf) {
        value = withContext(Dispatchers.Default) { LayerRenderer.render(context, base, wPx, hPx, chars, hiWord, atMs = if (base.kind == LayerKind.LOTTIE) timeMs else -1L) }
    }
    // Keep the last picture while a new one (next word / letter) is drawn, so it never blinks.
    val lastBmp = remember(base.id) { arrayOfNulls<Bitmap>(1) }
    val b0 = bmp ?: lastBmp[0] ?: return
    lastBmp[0] = b0
    // Keyframed size: scale the picture rendered at the layer's own size.
    val k = (if (base.size > 0f) l.size / base.size else 1f) * fx.scale
    val bw = (b0.width * k).toInt().coerceAtLeast(1); val bh = (b0.height * k).toInt().coerceAtLeast(1)
    val left = ((l.x + fx.dx) * wPx - bw / 2f).toInt()
    val top = ((l.y + fx.dy) * hPx - bh / 2f).toInt()
    val sel = l.id == selectedId
    Box(
        Modifier
            .offset { androidx.compose.ui.unit.IntOffset(left, top) }
            .size(with(density) { bw.toDp() }, with(density) { bh.toDp() })
            .then(if (sel && !FxLayers.fullFrame(base) && !(base.kind == LayerKind.FX && base.fx == FxLayers.PROGRESS)) Modifier.border(1.dp, Pro.Amber) else Modifier)
            // Full-frame overlays (bars, flare) don't take touches, so everything under them stays reachable.
            .then(if (FxLayers.fullFrame(base)) Modifier else Modifier.pointerInput(l.id) {
                detectDragGestures(onDragStart = { onSelect(l.id) }) { change, drag ->
                    change.consume()
                    onMove(l.id, if (base.kind == LayerKind.FX && base.fx == FxLayers.PROGRESS) 0f else drag.x / wPx, drag.y / hPx)
                }
            }.clickable { onSelect(l.id) }),
    ) {
        val img = remember(b0) { b0.asImageBitmap() }
        val mode = (l.blend ?: LayerBlend.NORMAL).compose
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            clipRect(right = size.width * fx.reveal.coerceIn(0f, 1f)) {
                drawImage(img, dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()), alpha = (l.opacity * fx.alpha * (if (ghost) 0.3f else 1f)).coerceIn(0f, 1f), blendMode = mode)
            }
        }
    }
}

/** Colour labels for clips. */
val CLIP_LABELS = listOf(0xFFFF3B30, 0xFFFF9500, 0xFFFFD60A, 0xFF34C759, 0xFF00C7BE, 0xFF0A84FF, 0xFFBF5AF2, 0xFFFF7EB6).map { it.toInt() }

private val TEXT_COLORS = listOf(0xFFFFFFFF, 0xFF000000, 0xFFFFB300, 0xFFFF3B30, 0xFF34C759, 0xFF0A84FF, 0xFFFF7EB6, 0xFFB79CFF).map { it.toInt() }

@kotlin.OptIn(ExperimentalMaterial3Api::class)
/** Text page: everything that makes new text — title templates, lower thirds, effect text and more kinds of text. */
@Composable
private fun TextToolbox(playheadMs: Long, totalMs: Long, onAdd: (Layer) -> Unit) {
    SectionTitle("TITLE TEMPLATES")
    Text("Animated titles at the playhead — tap to add, then change the words like any text layer.", color = Pro.Dim, fontSize = 11.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.hintedScroll(horizontal = true).padding(vertical = 4.dp)) {
        val t0 = playheadMs
        val t1 = (playheadMs + 4000).coerceAtMost(totalMs.coerceAtLeast(playheadMs + 1000))
        fun tl(text: String, size: Float, x: Float, y: Float, font: LayerFont, aIn: LayerAnim, aOut: LayerAnim, ms: Long = 450, box: Boolean = false, color: Int = 0xFFFFFFFF.toInt(), name: String) =
            Layer(kind = LayerKind.TEXT, text = text, size = size, x = x, y = y, font = font, animIn = aIn, animOut = aOut, animMs = ms, box = box, color = color, shadow = true, startMs = t0, endMs = t1, name = name)
        PillChip("Lower third", false) {
            onAdd(tl("Your Name", 0.05f, 0.26f, 0.8f, LayerFont.SANS, LayerAnim.SLIDE_RIGHT, LayerAnim.SLIDE_LEFT, box = true, name = "Lower third · name"))
            onAdd(tl("Title / role", 0.032f, 0.26f, 0.87f, LayerFont.LIGHT, LayerAnim.SLIDE_RIGHT, LayerAnim.SLIDE_LEFT, ms = 650, color = 0xFFFFB300.toInt(), name = "Lower third · role"))
        }
        PillChip("Big title", false) { onAdd(tl("BIG TITLE", 0.13f, 0.5f, 0.5f, LayerFont.CONDENSED, LayerAnim.POP, LayerAnim.ZOOM, name = "Big title")) }
        PillChip("Cinematic", false) { onAdd(tl("THE JOURNEY", 0.07f, 0.5f, 0.5f, LayerFont.SERIF, LayerAnim.FADE, LayerAnim.FADE, ms = 1000, name = "Cinematic title")) }
        PillChip("Location", false) { onAdd(tl("📍 Place, Country", 0.042f, 0.5f, 0.86f, LayerFont.MONO, LayerAnim.TYPEWRITER, LayerAnim.FADE, ms = 1200, name = "Location")) }
        PillChip("Chapter", false) {
            onAdd(tl("CHAPTER ONE", 0.035f, 0.5f, 0.42f, LayerFont.LIGHT, LayerAnim.REVEAL, LayerAnim.FADE, ms = 700, color = 0xFFFFB300.toInt(), name = "Chapter · number"))
            onAdd(tl("The Beginning", 0.08f, 0.5f, 0.52f, LayerFont.SERIF, LayerAnim.SLIDE_UP, LayerAnim.FADE, ms = 800, name = "Chapter · title"))
        }
        PillChip("Subscribe", false) { onAdd(tl("SUBSCRIBE 🔔", 0.05f, 0.5f, 0.82f, LayerFont.SANS, LayerAnim.BOUNCE, LayerAnim.ZOOM, ms = 600, box = true, color = 0xFFFF3B30.toInt(), name = "Subscribe")) }
        PillChip("End card", false) {
            onAdd(tl("Thanks for watching", 0.07f, 0.5f, 0.45f, LayerFont.SERIF, LayerAnim.FADE, LayerAnim.FADE, ms = 900, name = "End card"))
            onAdd(tl("@yourchannel", 0.035f, 0.5f, 0.56f, LayerFont.LIGHT, LayerAnim.SLIDE_UP, LayerAnim.FADE, ms = 1000, color = 0xFFFFB300.toInt(), name = "End card · handle"))
        }
    }
    SectionTitle("LOWER THIRDS")
    Text("Name and title cards for people and places — tap one to add it at the playhead (5 s), then type the name and title in the text layer.", color = Pro.Dim, fontSize = 11.sp)
    LowerThirdGallery { i -> onAdd(lowerThirdLayer(i, playheadMs, (playheadMs + 5000).coerceAtMost(totalMs.coerceAtLeast(playheadMs + 1000)))) }
    SectionTitle("TEXT EFFECTS")
    Text("Special effect text — neon, chrome, gold, fire, 3D, glitch and more. Tap one to add it at the playhead; a selected text layer can switch its effect under SELECTED TEXT.", color = Pro.Dim, fontSize = 11.sp)
    Row(Modifier.hintedScroll(horizontal = true).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TextFx.NAMES.withIndex().drop(1).forEach { (i, n) ->
            PillChip(n, false) {
                onAdd(Layer(kind = LayerKind.TEXT, text = n.uppercase(), size = 0.1f, x = 0.5f, y = 0.5f, face = if (i in listOf(1, 10)) "monoton" else "anton", textFx = i,
                    color = when (i) { 1 -> 0xFFFF2D95.toInt(); 14 -> 0xFFFFD60A.toInt(); else -> 0xFFFFFFFF.toInt() }, shadow = false,
                    animIn = LayerAnim.POP, animOut = LayerAnim.FADE, animMs = 500L, startMs = playheadMs, endMs = (playheadMs + 4000).coerceAtMost(totalMs.coerceAtLeast(playheadMs + 1000)), name = n))
            }
        }
    }
    SectionTitle("MORE TEXT")
    Text("Ready-made kinds of text — headlines, subtitles, quotes, speech bubbles, hashtags, sale tags and more, each with its own font and style.", color = Pro.Dim, fontSize = 11.sp)
    TextTypeGallery { t -> onAdd(t.layer(playheadMs, (playheadMs + 4000).coerceAtMost(totalMs.coerceAtLeast(playheadMs + 1000)))) }
}

@Composable
private fun LayersPage(
    layers: List<Layer>, selectedId: String?, totalMs: Long, playheadMs: Long,
    onSelect: (String) -> Unit, onAdd: (Layer) -> Unit, onAddLogo: () -> Unit,
    onAddVideo: () -> Unit = {}, onAddImage: () -> Unit = {}, onAddLottie: () -> Unit = {},
    onUpdate: (Layer) -> Unit, onDelete: (String) -> Unit, onMove: (String, Int) -> Unit,
    onSeek: (Long) -> Unit = {},
    onCaptions: (String?, Boolean, Int) -> Unit = { _, _, _ -> },
    onImportSubs: () -> Unit = {},
    onFlightPath: () -> Unit = {},
    onTextBehind: () -> Unit = {},
    onVisualizer: () -> Unit = {},
    visSources: List<VisSource> = emptyList(),
    onTranslate: (String) -> Unit = {}, translateStatus: String? = null,
    onDub: (String, Boolean, Boolean) -> Unit = { _, _, _ -> }, dubStatus: String? = null,
    onTrack: (String) -> Unit = {}, trackingId: String? = null,
    onPin: (String, List<PinQuad>?) -> Unit = { _, _ -> }, onPlanarTrack: (String) -> Unit = {}, pinTrackingId: String? = null, frameAspect: Float = 16f / 9f,
    telemetryShow: TelemetryShow = TelemetryShow(), onTelemetryShow: (TelemetryShow) -> Unit = {}, onTelemetry: () -> Unit = {},
    onSplit: (Int) -> Unit = {},
    onMagicLayer: (String) -> Unit = {}, magicLayerProgress: Map<String, Int> = emptyMap(), onStopMagicLayer: (String) -> Unit = {},
) {
    var stickers by remember { mutableStateOf(false) }
    // AI captions: speech to text (Whisper) turned into caption layers.
    var capLang by remember { mutableStateOf(0) }
    var capStyle by remember { mutableStateOf(0) }
    var capClipsOnly by remember { mutableStateOf(true) }
    val langs = listOf<Pair<String, String?>>("Auto" to null, "English" to "en", "Danish" to "da", "Faroese" to "fo", "Norwegian" to "no", "Swedish" to "sv", "German" to "de", "Spanish" to "es", "French" to "fr")
    SectionTitle("AI CAPTIONS")
    Chips(langs.map { it.first }, capLang) { capLang = it }
    Chips(listOf("Boxed", "Clean", "Big"), capStyle) { capStyle = it }
    Chips(listOf("Clips' speech only", "Everything (incl. music)"), if (capClipsOnly) 0 else 1) { capClipsOnly = it == 0 }
    Text(
        "MAKE CAPTIONS", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
        modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable { onCaptions(langs[capLang].second, capClipsOnly, capStyle) }.padding(10.dp),
    )
    Text("Writes timed caption layers from the speech in your edit (Whisper, via your fal.ai account in Cloud AI settings). Edit or delete any caption like a normal text layer.", color = Pro.Dim, fontSize = 11.sp)
    Text(
        "IMPORT SUBTITLES (.srt / .vtt)", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
        modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable(onClick = onImportSubs).padding(10.dp),
    )
    Text("Turns a subtitle file into caption layers (in the style chosen above), timed from the start of the timeline.", color = Pro.Dim, fontSize = 11.sp)
    SectionTitle("AI DUBBING")
    var dubLang by remember { mutableStateOf(0) }
    var dubMan by remember { mutableStateOf(false) }
    var dubMute by remember { mutableStateOf(false) }
    Chips(Dubbing.LANGS.map { it.first }, dubLang) { dubLang = it }
    Chips(listOf("Woman's voice", "Man's voice"), if (dubMan) 1 else 0) { dubMan = it == 1 }
    Chips(listOf("Original voice quiet underneath", "Mute the original"), if (dubMute) 1 else 0) { dubMute = it == 1 }
    Text(
        dubStatus?.let { "$it · tap to stop" } ?: "DUB THE CAPTIONS", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
        modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable { onDub(Dubbing.LANGS[dubLang].second, dubMan, dubMute) }.padding(10.dp),
    )
    Text("Speaks the captions at their times as a new audio track. Translate the captions first (below) for another language — pick the same language here. English is made on the PC; other languages use Cloud AI (ElevenLabs via your fal.ai account, about \$0.10 per 1,000 characters).", color = Pro.Dim, fontSize = 11.sp)
    SectionTitle("TRANSLATE CAPTIONS")
    var trTo by remember { mutableStateOf(0) }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.hintedScroll(horizontal = true).padding(vertical = 4.dp)) {
        CaptionTranslate.targets.forEachIndexed { i, (_, n) -> PillChip(n, trTo == i) { trTo = i } }
    }
    if (translateStatus != null) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        androidx.compose.material3.CircularProgressIndicator(color = Pro.Amber, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
        Text(translateStatus, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
    } else Text(
        "TRANSLATE ALL CAPTIONS", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
        modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable { onTranslate(CaptionTranslate.targets[trTo].first) }.padding(10.dp),
    )
    Text("Translates every caption layer (AI captions and word-by-word captions) on the PC; the language is found by itself. Each language uses translation packs (about 130 MB each, into and out of English), downloaded once. Undo brings the originals back. Translation: OPUS-MT, University of Helsinki.", color = Pro.Dim, fontSize = 11.sp)
    SectionTitle("DRONE TELEMETRY")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.hintedScroll(horizontal = true)) {
        val t = telemetryShow
        PillChip("Altitude", t.alt) { onTelemetryShow(t.copy(alt = !t.alt)) }
        PillChip("Speed", t.speed) { onTelemetryShow(t.copy(speed = !t.speed)) }
        PillChip("GPS", t.gps) { onTelemetryShow(t.copy(gps = !t.gps)) }
        PillChip("Camera", t.camera) { onTelemetryShow(t.copy(camera = !t.camera)) }
        PillChip(if (t.metric) "m · km/h" else "ft · mph", true) { onTelemetryShow(t.copy(metric = !t.metric)) }
    }
    Text(
        "LOAD FLIGHT DATA (.SRT) FOR THIS CLIP", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
        modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable { onTelemetry() }.padding(10.dp),
    )
    Text("DJI drones save a .SRT file next to each video (turn on Video Subtitles / Video Caption in the DJI Fly app). Select the drone clip, then load its .SRT: altitude, speed, GPS and camera settings appear as a live read-out, matched to the clip's trim and speed.", color = Pro.Dim, fontSize = 11.sp)
    val end = (playheadMs + 3000).coerceAtMost(totalMs.coerceAtLeast(1))
    Text("Titles, lower thirds, text effects and more kinds of text are on the Text page; the selected text's font and effect are below.", color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
    SectionTitle("SPLIT SCREEN")
    Text("Arranges your video layers (above the main video) into a layout — each fills its cell, cropped to fit. Add the video layers first with + VIDEO.", color = Pro.Dim, fontSize = 11.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.hintedScroll(horizontal = true).padding(vertical = 4.dp)) {
        listOf("2 side by side", "2 stacked", "3 columns", "2 × 2 grid", "Picture in picture").forEachIndexed { i, n -> PillChip(n, false) { onSplit(i) } }
    }
    SectionTitle("ADD LAYER")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.hintedScroll(horizontal = true)) {
        SmallAction(Icons.Filled.TextFields, "Text") { onAdd(Layer(kind = LayerKind.TEXT, startMs = playheadMs, endMs = end)) }
        SmallAction(Icons.Filled.EmojiEmotions, "Sticker") { stickers = !stickers }
        SmallAction(Icons.Filled.WaterDrop, "Watermark") {
            onAdd(Layer(kind = LayerKind.WATERMARK, text = "© AlcorCut Pro", size = 0.04f, x = 0.84f, y = 0.92f, opacity = 0.65f, shadow = true, wholeVideo = true, font = LayerFont.LIGHT))
        }
        SmallAction(Icons.Filled.Movie, "Video", onAddVideo)
        SmallAction(Icons.Filled.Photo, "Image", onAddImage)
        SmallAction(Icons.Filled.Image, "Logo", onAddLogo)
        SmallAction(Icons.Filled.Star, "Shape") { onAdd(Layer(kind = LayerKind.SHAPE, text = "Shape", shape = ShapeKind.ROUNDED.ordinal, size = 0.2f, color = 0xFFFFB300.toInt(), shadow = false, startMs = playheadMs, endMs = end)) }
        SmallAction(Icons.Filled.Movie, "Lottie", onAddLottie)
        SmallAction(Icons.Filled.TextFields, "Credits") {
            // Rolling credits over the last 12 s (or the whole video if it is shorter).
            val total = totalMs.coerceAtLeast(1)
            val from = (total - 12_000L).coerceAtLeast(0L)
            onAdd(Layer(kind = LayerKind.TEXT, text = """Directed by
Your Name

Edited by
Your Name

Music
Artist — Song title

Filmed on
AlcorCut Pro

Thanks for watching""", size = 0.045f, y = 0.5f, startMs = from, endMs = total, animIn = LayerAnim.ROLL, animOut = LayerAnim.NONE, shadow = true))
        }
        SmallAction(Icons.Filled.Tune, "Adjustment") {
            onAdd(Layer(kind = LayerKind.ADJUST, text = "Adjustment", startMs = playheadMs, endMs = end, adjGrade = ProGrade(exposure = 0f), opacity = 1f))
        }
    }
    SectionTitle("OVERLAYS")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.hintedScroll(horizontal = true)) {
        val total = totalMs.coerceAtLeast(1)
        SmallAction(Icons.Filled.Movie, "Cinema bars") { onAdd(Layer(kind = LayerKind.FX, fx = FxLayers.BARS, text = "Cinema bars", fxA = 2.39f, color = 0xFF000000.toInt(), shadow = false, startMs = 0, endMs = total)) }
        SmallAction(Icons.Filled.Map, "Flight path / route", onFlightPath)
        SmallAction(Icons.Filled.LinearScale, "Progress bar") { onAdd(Layer(kind = LayerKind.FX, fx = FxLayers.PROGRESS, text = "Progress bar", fxA = 0.008f, y = 0.996f, color = 0xFFFFB300.toInt(), shadow = false, startMs = 0, endMs = total)) }
        SmallAction(Icons.Filled.GraphicEq, "Visualizer", onVisualizer)
        SmallAction(Icons.Filled.Timer, "Countdown") { onAdd(Layer(kind = LayerKind.FX, fx = FxLayers.TIMER, text = "Countdown", size = 0.18f, x = 0.88f, y = 0.18f, fxB = 1f, color = 0xFFFFB300.toInt(), shadow = false, startMs = playheadMs, endMs = (playheadMs + 10_000L).coerceAtMost(total).let { if (it - playheadMs < 1000) playheadMs + 10_000L else it })) }
        SmallAction(Icons.Filled.TextFields, "Text behind subject", onTextBehind)
        SmallAction(Icons.Filled.Tv, "Retro look") {
            val now = System.currentTimeMillis()
            val cal = java.util.Calendar.getInstance()
            onAdd(Layer(kind = LayerKind.FX, fx = FxLayers.RETRO, text = "Retro look", fxA = 0f, fxX = ((now + cal.timeZone.getOffset(now)) / 86_400_000L).toFloat(),
                fxY = (cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)).toFloat(), opacity = 1f, shadow = false, startMs = 0, endMs = total))
        }
        SmallAction(Icons.Filled.WbSunny, "Lens flare") { onAdd(Layer(kind = LayerKind.FX, fx = FxLayers.FLARE, text = "Lens flare", fxA = 0f, fxX = 0.72f, fxY = 0.28f, opacity = 0.9f, shadow = false, startMs = playheadMs, endMs = end)) }
    }
    Text("Cinema bars, the progress bar and the flare cover the whole video or part of it — trim them on the timeline. Flight path / route reads the drone's .SRT flight data, or a .GPX track from a GPS app (hikes, drives, rides), for the selected clip.", color = Pro.Dim, fontSize = 11.sp)
    if (stickers) {
        Column(Modifier.padding(vertical = 6.dp)) {
            STICKERS.chunked(8).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    row.forEach { e ->
                        Text(e, fontSize = 26.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                            onAdd(Layer(kind = LayerKind.STICKER, text = e, size = 0.14f, shadow = false, startMs = playheadMs, endMs = end)); stickers = false
                        }.padding(4.dp))
                    }
                }
            }
        }
    }
    SectionTitle("LAYERS (TOP FIRST)")
    if (layers.isEmpty()) Text("No layers yet. Add text, a sticker, a watermark or a logo.", color = Pro.Dim, fontSize = 12.sp)
    layers.asReversed().forEach { l ->
        val sel = l.id == selectedId
        Row(
            Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp))
                .background(if (sel) Pro.Amber.copy(alpha = 0.16f) else Ed.Raised)
                .border(1.dp, if (sel) Pro.Amber else Color.Transparent, RoundedCornerShape(8.dp))
                .clickable { onSelect(l.id) }.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(l.kind.label.uppercase(), style = Pro.Label.copy(color = Pro.Amber), modifier = Modifier.width(78.dp))
            Text(l.displayName, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(if (l.wholeVideo) "ALL" else "${tc(l.startMs)}", style = Pro.Label)
            Icon(
                if (l.visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, contentDescription = "Show",
                tint = Color.White, modifier = Modifier.padding(start = 8.dp).size(20.dp).clickable { onUpdate(l.copy(visible = !l.visible)) },
            )
            Icon(
                if (l.locked) Icons.Filled.Lock else Icons.Filled.LockOpen, contentDescription = "Lock",
                tint = if (l.locked) Pro.Amber else Pro.Dim, modifier = Modifier.padding(start = 8.dp).size(20.dp).clickable { onUpdate(l.copy(locked = !l.locked)) },
            )
        }
    }
    val l = layers.firstOrNull { it.id == selectedId } ?: return
    SectionTitle("SELECTED ${l.kind.label.uppercase()}")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.hintedScroll(horizontal = true)) {
        SmallAction(Icons.Filled.KeyboardArrowUp, "Up") { onMove(l.id, 1) }
        SmallAction(Icons.Filled.KeyboardArrowDown, "Down") { onMove(l.id, -1) }
        SmallAction(Icons.Filled.ContentCopy, "Copy") { onAdd(l.copy(id = java.util.UUID.randomUUID().toString(), x = (l.x + 0.05f).coerceAtMost(1f), y = (l.y + 0.05f).coerceAtMost(1f))) }
        SmallAction(Icons.Filled.Delete, "Delete") { onDelete(l.id) }
    }
    androidx.compose.material3.OutlinedTextField(
        value = l.name ?: "", onValueChange = { onUpdate(l.copy(name = it)) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        singleLine = true,
        label = { Text(if (l.kind == LayerKind.WATERMARK) "Watermark name (list & timeline)" else "Layer name (list & timeline)") },
        placeholder = { Text(l.displayName, color = Pro.Dim) },
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedTextColor = com.dronedirector.veyrocutpro.ui.Pro.Fg, unfocusedTextColor = com.dronedirector.veyrocutpro.ui.Pro.Fg,
                focusedBorderColor = Pro.Amber, unfocusedBorderColor = Pro.Line, focusedLabelColor = Pro.Amber, cursorColor = Pro.Amber,
            ),
    )
    if (l.kind == LayerKind.ADJUST) {
        // Adjustment layer: one grade over every clip under it.
        val g = l.adjGrade ?: ProGrade()
        fun ag(f: (ProGrade) -> ProGrade) = onUpdate(l.copy(adjGrade = f(g)))
        Text("Grades everything under it on the timeline — every clip in its time range — without touching the clips themselves. Up to $MAX_VIDEO_MASKS adjustment layers at a time.", color = Pro.Dim, fontSize = 11.sp)
        EdSlider("Strength", l.opacity, 0f..1f, 1f, ::pct) { v -> onUpdate(l.copy(opacity = v)) }
        SectionTitle("GRADE") { onUpdate(l.copy(adjGrade = ProGrade())) }
        EdSlider("Exposure", g.exposure, -3f..3f, format = ::ev) { v -> ag { it.copy(exposure = v) } }
        EdSlider("Contrast", g.contrast) { v -> ag { it.copy(contrast = v) } }
        EdSlider("Highlights", g.highlights) { v -> ag { it.copy(highlights = v) } }
        EdSlider("Shadows", g.shadows) { v -> ag { it.copy(shadows = v) } }
        EdSlider("Temperature", g.temperature) { v -> ag { it.copy(temperature = v) } }
        EdSlider("Tint", g.tint) { v -> ag { it.copy(tint = v) } }
        EdSlider("Saturation", g.saturation) { v -> ag { it.copy(saturation = v) } }
        EdSlider("Vibrance", g.vibrance) { v -> ag { it.copy(vibrance = v) } }
        EdSlider("Fade", g.fade, 0f..1f, format = ::pct) { v -> ag { it.copy(fade = v) } }
        SectionTitle("LOOK")
        LookStrip(g.look) { f -> ag { it.copy(look = f) } }
        if (g.look != Filter.NONE) EdSlider("Look strength", g.lookAmount, 0f..1f, 1f, ::pct) { v -> ag { it.copy(lookAmount = v) } }
    }
    if (l.kind == LayerKind.TEXT || l.kind == LayerKind.WATERMARK) {
        androidx.compose.material3.OutlinedTextField(
            value = l.text, onValueChange = { onUpdate(l.copy(text = it)) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            label = { Text(if (l.kind == LayerKind.WATERMARK) "Watermark text (shown on the video)" else "Text") },
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedTextColor = com.dronedirector.veyrocutpro.ui.Pro.Fg, unfocusedTextColor = com.dronedirector.veyrocutpro.ui.Pro.Fg,
                focusedBorderColor = Pro.Amber, unfocusedBorderColor = Pro.Line, focusedLabelColor = Pro.Amber, cursorColor = Pro.Amber,
            ),
        )
        if (l.kind == LayerKind.TEXT && l.lowerThird != null) {
            Text("Lower third: the first line is the name, the next line the title.", color = Pro.Dim, fontSize = 11.sp)
            Text("Design", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            LowerThirdGallery(l.lowerThird) { i -> onUpdate(l.copy(lowerThird = i)) }
            SecondColour(l, "Accent colour") { c -> onUpdate(l.copy(textColor2 = c)) }
        }
        FontPicker(l.face, l.font) { id, f -> onUpdate(if (id != null) l.copy(face = id) else l.copy(face = null, font = f ?: l.font)) }
        if (l.kind == LayerKind.TEXT && l.lowerThird == null) {
            TextFxPicker(l) { fx -> onUpdate(l.copy(textFx = fx)) }
            if ((l.textFx ?: 0) in listOf(2, 9, 17)) SecondColour(l, if (l.textFx == 2) "Gradient to" else if (l.textFx == 9) "Outer outline" else "Highlighter colour") { c -> onUpdate(l.copy(textColor2 = c)) }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TEXT_COLORS.forEach { c ->
                Box(Modifier.size(28.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(c))
                    .border(if (c == l.color) 3.dp else 1.dp, if (c == l.color) Pro.Amber else Pro.Line, androidx.compose.foundation.shape.CircleShape)
                    .clickable { onUpdate(l.copy(color = c)) })
            }
        }
        Chips(listOf("Shadow", "Box"), -1) { if (it == 0) onUpdate(l.copy(shadow = !l.shadow)) else onUpdate(l.copy(box = !l.box)) }
        Text("Shadow: ${if (l.shadow) "on" else "off"} · Box: ${if (l.box) "on" else "off"}", style = Pro.Label)
        if (l.kind == LayerKind.TEXT) {
            EdSlider("Outline", l.stroke ?: 0f, 0f..0.25f, 0f, ::pct) { onUpdate(l.copy(stroke = it.takeIf { v -> v > 0.005f })) }
            if ((l.stroke ?: 0f) > 0f) Chips(listOf("Black outline", "White outline", "Red outline"), when (l.borderColor) { 0xFFFFFFFF.toInt() -> 1; 0xFFFF3B30.toInt() -> 2; else -> 0 }) { i ->
                onUpdate(l.copy(borderColor = listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFFFF3B30.toInt())[i]))
            }
        }
    }
    if (l.kind == LayerKind.SHAPE) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp).hintedScroll(horizontal = true)) {
            ShapeKind.entries.forEach { k -> PillChip(k.label, (l.shape ?: 0) == k.ordinal) { onUpdate(l.copy(shape = k.ordinal, shapeAspect = null)) } }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TEXT_COLORS.forEach { c ->
                Box(Modifier.size(28.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(c))
                    .border(if (c == l.color) 3.dp else 1.dp, if (c == l.color) Pro.Amber else Pro.Line, androidx.compose.foundation.shape.CircleShape)
                    .clickable { onUpdate(l.copy(color = c)) })
            }
        }
        Chips(listOf("Filled", "Outline"), if ((l.stroke ?: 0f) > 0f) 1 else 0) { onUpdate(l.copy(stroke = if (it == 1) 0.06f else null)) }
        if ((l.stroke ?: 0f) > 0f) EdSlider("Outline width", l.stroke ?: 0.06f, 0.01f..0.3f, 0.06f, ::pct) { onUpdate(l.copy(stroke = it)) }
        val defAspect = when (ShapeKind.entries.getOrElse(l.shape ?: 0) { ShapeKind.RECT }) { ShapeKind.LINE, ShapeKind.ARROW -> 4f; ShapeKind.RECT, ShapeKind.ROUNDED -> 1.6f; else -> 1f }
        EdSlider("Width", l.shapeAspect ?: defAspect, 0.2f..10f, defAspect, { "%.1f×".format(it) }) { onUpdate(l.copy(shapeAspect = it)) }
        Chips(listOf("No shadow", "Shadow"), if (l.shadow) 1 else 0) { onUpdate(l.copy(shadow = it == 1)) }
        Text("Shapes animate, keyframe, blend and corner-pin like any layer — e.g. a box behind a title, an arrow pointing at something, a highlight circle.", color = Pro.Dim, fontSize = 11.sp)
    }
    if (l.kind == LayerKind.FX) {
        when (l.fx) {
            FxLayers.BARS -> {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp).hintedScroll(horizontal = true)) {
                    FxLayers.BAR_SHAPES.forEach { (n, a) -> PillChip(n, (l.fxA ?: 2.39f) == a) { onUpdate(l.copy(fxA = a)) } }
                }
                Chips(listOf("Still", "Slide in and out"), if (l.fxB == 1f) 1 else 0) { onUpdate(l.copy(fxB = if (it == 1) 1f else null)) }
                Text("Black bars top and bottom for a widescreen film look. Slide in and out: they close in over the first second and open over the last.", color = Pro.Dim, fontSize = 11.sp)
            }
            FxLayers.PROGRESS -> {
                Chips(listOf("Top", "Bottom"), if (l.y < 0.5f) 0 else 1) { val th = l.fxA ?: 0.008f; onUpdate(l.copy(y = if (it == 0) th / 2f else 1f - th / 2f)) }
                EdSlider("Thickness", (l.fxA ?: 0.008f) * 100f, 0.3f..3f, 0.8f, { "%.1f%%".format(it) }) { v -> val th = v / 100f; onUpdate(l.copy(fxA = th, y = if (l.y < 0.5f) th / 2f else 1f - th / 2f)) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp).hintedScroll(horizontal = true)) {
                    FxLayers.PROGRESS_STYLES.forEachIndexed { i, n -> PillChip(n, (l.fxB ?: 0f).toInt() == i) {
                        // Scroll wheel needs some height to show its detail.
                        val th = if (i == 7) maxOf(l.fxA ?: 0.008f, 0.016f) else (l.fxA ?: 0.008f)
                        onUpdate(l.copy(fxB = i.toFloat(), fxA = th, y = if (l.y < 0.5f) th * 0.8f else 1f - th * 0.8f))
                    } }
                }
                if ((l.fxB ?: 0f).toInt() == 4) EdSlider("Segments", l.fxX ?: 5f, 2f..12f, 5f, { "%.0f".format(it) }) { onUpdate(l.copy(fxX = kotlin.math.round(it))) }
                if ((l.fxB ?: 0f).toInt() == 7) Text("Scroll wheel uses the scroll wheels' own colour (gear → SCROLL WHEELS).", color = Pro.Dim, fontSize = 11.sp)
                Text("Fills from left to right over the layer's length (the whole video when added). Drawn into the exported video.", color = Pro.Dim, fontSize = 11.sp)
            }
            FxLayers.FLARE -> {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp).hintedScroll(horizontal = true)) {
                    FxLayers.FLARE_STYLES.forEachIndexed { i, n -> PillChip(n, (l.fxA ?: 0f).toInt() == i) { onUpdate(l.copy(fxA = i.toFloat())) } }
                }
                EdSlider("Across", (l.fxX ?: 0.72f) * 100f, 0f..100f, 72f, { "%.0f%%".format(it) }) { onUpdate(l.copy(fxX = it / 100f)) }
                EdSlider("Down", (l.fxY ?: 0.28f) * 100f, 0f..100f, 28f, { "%.0f%%".format(it) }) { onUpdate(l.copy(fxY = it / 100f)) }
                Text("Put the flare on the sun or a light. Strength = the layer's opacity; Fade in / out under ANIMATION eases it in.", color = Pro.Dim, fontSize = 11.sp)
            }
            FxLayers.VIS -> {
                // Ten styles per row, each row scrolls sideways.
                FxLayers.VIS_STYLES.withIndex().chunked(10).forEach { rowItems -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 3.dp).hintedScroll(horizontal = true)) {
                    rowItems.forEach { (i, n) -> PillChip(n, (l.fxB ?: 0f).toInt() == i) { val wasRound = FxLayers.visRound((l.fxB ?: 0f).toInt()); val round = FxLayers.visRound(i)
                        onUpdate(l.copy(fxB = i.toFloat(), y = if (round && !wasRound) 0.5f else if (!round && wasRound) 0.84f else l.y, size = if (round && !wasRound) 0.25f else if (!round && wasRound) 0.22f else l.size)) } }
                } }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp).hintedScroll(horizontal = true)) {
                    FxLayers.VIS_COLOURS.forEachIndexed { i, n -> PillChip(n, (l.fxC ?: 0f).toInt() == i) { onUpdate(l.copy(fxC = i.toFloat())) } }
                }
                SectionTitle("FOLLOWS THIS SOUND")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp).hintedScroll(horizontal = true)) {
                    visSources.forEach { src -> PillChip(src.name.take(28), l.imageUri == src.uri && (l.fxD ?: 0f).toLong() == src.delta) { onUpdate(l.copy(imageUri = src.uri, fxD = src.delta.toFloat())) } }
                }
                if (visSources.none { it.uri == l.imageUri }) Text("The sound it followed is no longer in the project — pick one above.", color = Pro.Red, fontSize = 11.sp)
                if (!FxLayers.visRound((l.fxB ?: 0f).toInt())) EdSlider("Width", (l.fxA ?: 0.9f) * 100f, 20f..100f, 90f, { "%.0f%%".format(it) }) { onUpdate(l.copy(fxA = it / 100f)) }
                EdSlider(if (FxLayers.visRound((l.fxB ?: 0f).toInt())) "Size" else "Height", l.size * 100f, 5f..100f, 22f, { "%.0f%%".format(it) }) { onUpdate(l.copy(size = it / 100f)) }
                Text("Moves with the music's own frequencies and pulses on every beat (kick drum), worked out from the sound itself — so it's in sync in the preview and the export. Uses the music track, or the selected clip's sound when there's no music. Drag it into place.", color = Pro.Dim, fontSize = 11.sp)
            }
            FxLayers.TIMER -> {
                FxLayers.TIMER_STYLES.withIndex().chunked(9).forEach { rowItems -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 3.dp).hintedScroll(horizontal = true)) {
                    rowItems.forEach { (i, n) -> PillChip(n, (l.fxB ?: 0f).toInt() == i) { onUpdate(l.copy(fxB = i.toFloat())) } }
                } }
                val manual = l.fxX != null
                Chips(listOf("Automatic", "Manual"), if (manual) 1 else 0) {
                    onUpdate(if (it == 1) l.copy(fxX = ((l.endMs - l.startMs) / 1000f).coerceIn(1f, 3600f).let { s -> kotlin.math.round(s) }) else l.copy(fxX = null))
                }
                if (manual) EdSlider("Count down from", l.fxX ?: 10f, 1f..600f, 10f, { v -> val n = v.toInt(); if (n >= 60) "%d:%02d".format(n / 60, n % 60) else "$n s" }) { v ->
                    val n = kotlin.math.round(v).coerceAtLeast(1f)
                    onUpdate(l.copy(fxX = n, endMs = l.startMs + (n * 1000).toLong()))
                }
                Chips(listOf("Seconds", "m:ss"), if ((l.fxY ?: 0f) == 1f) 1 else 0) { onUpdate(l.copy(fxY = if (it == 1) 1f else null)) }
                Text(if (manual) "Counts down from your number of seconds, starting where the layer starts (the layer is made that long)." else "Counts down the layer's own length — trim it on the timeline and the countdown follows.", color = Pro.Dim, fontSize = 11.sp)
            }
            FxLayers.RETRO -> {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp).hintedScroll(horizontal = true)) {
                    RetroFx.STYLES.forEachIndexed { i, n -> PillChip(n, (l.fxA ?: 0f).toInt() == i) { onUpdate(l.copy(fxA = i.toFloat())) } }
                }
                EdSlider("Strength", l.opacity * 100f, 10f..100f, 100f, { "%.0f%%".format(it) }) { onUpdate(l.copy(opacity = it / 100f)) }
                val st = (l.fxA ?: 0f).toInt()
                if (st != 1 && st != 3) {
                    // The date / time shown on the picture (it runs on with the video).
                    val day = (l.fxX ?: 0f).toLong(); val mins = (l.fxY ?: 720f).toInt()
                    val f = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault()).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                    Text("Date on the picture: " + f.format(java.util.Date(day * 86_400_000L)) + "  " + "%02d:%02d".format(mins / 60, mins % 60), color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    EdSlider("Year", (1970 + day / 365.25).toFloat(), 1980f..2035f, 1998f, { "%.0f".format(it) }) { y ->
                        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = day * 86_400_000L; set(java.util.Calendar.YEAR, kotlin.math.round(y).toInt()) }
                        onUpdate(l.copy(fxX = (cal.timeInMillis / 86_400_000L).toFloat()))
                    }
                    EdSlider("Day of the year", java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = day * 86_400_000L }.get(java.util.Calendar.DAY_OF_YEAR).toFloat(), 1f..365f, 180f, { "%.0f".format(it) }) { d ->
                        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = day * 86_400_000L; set(java.util.Calendar.DAY_OF_YEAR, kotlin.math.round(d).toInt()) }
                        onUpdate(l.copy(fxX = (cal.timeInMillis / 86_400_000L).toFloat()))
                    }
                    EdSlider("Time of day", mins.toFloat(), 0f..1439f, 720f, { "%02d:%02d".format(it.toInt() / 60, it.toInt() % 60) }) { onUpdate(l.copy(fxY = kotlin.math.round(it))) }
                }
                Text("VHS tape, an old TV, a camcorder with its date stamp, old film with dust and scratches, or a security camera — drawn over the picture in the preview and the export. Combine it with a look on the Color page (e.g. faded or black and white) for the full effect.", color = Pro.Dim, fontSize = 11.sp)
            }
            FxLayers.MAP -> Text("The drone's route from its flight data, north up; the dot follows the drone while the video plays. Drag it into place; size and colour as for any layer.", color = Pro.Dim, fontSize = 11.sp)
        }
        if (l.fx != FxLayers.FLARE && l.fx != FxLayers.RETRO) Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TEXT_COLORS.forEach { c ->
                Box(Modifier.size(28.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(c))
                    .border(if (c == l.color) 3.dp else 1.dp, if (c == l.color) Pro.Amber else Pro.Line, androidx.compose.foundation.shape.CircleShape)
                    .clickable { onUpdate(l.copy(color = c)) })
            }
        }
    }
    if (l.kind == LayerKind.LOTTIE) {
        Text("A Lottie animation (After Effects, exported as .json or .zip). It loops over the layer's length (one loop: ${tc(LottieRender.durationMs(LocalContext.current, l))}). Free animations: lottiefiles.com — check each one's licence.", color = Pro.Dim, fontSize = 11.sp)
    }
    if (l.kind == LayerKind.VIDEO && l.videoDurationMs > 0) {
        EdSlider("Start inside the video", l.videoStartMs.toFloat(), 0f..(l.videoDurationMs - 500).coerceAtLeast(1).toFloat(), 0f, { tc(it.toLong()) }) { onUpdate(l.copy(videoStartMs = it.toLong())) }
        Text("Overlay videos play muted. Size 100% = full frame height.", color = Pro.Dim, fontSize = 11.sp)
    }
    if (l.kind == LayerKind.VIDEO) {
        SectionTitle("PLACEMENT")
        Chips(listOf("↑ Above main video", "↓ Below main video"), if (l.isBelow) 1 else 0) { onUpdate(l.copy(below = it == 1)) }
        Text(
            if (l.isBelow) "Behind your clips: visible where the main video doesn't cover the frame. Drag it up / down on the timeline to change its order among the other layers."
            else "On top of your clips. Drag it up / down on the timeline to change its order among the other layers.",
            color = Pro.Dim, fontSize = 11.sp,
        )
        SectionTitle("FRAME") { onUpdate(l.copy(cellAspect = null, corner = null, border = null, borderColor = null, dropShadow = null)) }
        val shapes = listOf<Pair<String, Float?>>("Video's own" to null, "16:9" to 16f / 9f, "4:3" to 4f / 3f, "1:1" to 1f, "4:5" to 0.8f, "9:16" to 9f / 16f)
        Chips(shapes.map { it.first }, shapes.indexOfFirst { s -> s.second == l.cellAspect || (s.second != null && l.cellAspect != null && kotlin.math.abs(s.second!! - l.cellAspect) < 0.01f) }.let { if (it < 0) -1 else it }) { onUpdate(l.copy(cellAspect = shapes[it].second)) }
        EdSlider("Rounded corners", l.corner ?: 0f, 0f..0.5f, 0f, ::pct) { onUpdate(l.copy(corner = it.takeIf { v -> v > 0.001f })) }
        EdSlider("Border", l.border ?: 0f, 0f..0.03f, 0f, { "%.1f %%".format(it * 100) }) { onUpdate(l.copy(border = it.takeIf { v -> v > 0.0005f })) }
        if ((l.border ?: 0f) > 0f) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            TEXT_COLORS.forEach { col ->
                Box(Modifier.size(28.dp).clip(CircleShape).background(Color(col)).border(2.dp, if ((l.borderColor ?: 0xFFFFFFFF.toInt()) == col) Pro.Amber else Color.DarkGray, CircleShape).clickable { onUpdate(l.copy(borderColor = col)) })
            }
        }
        Chips(listOf("Drop shadow off", "Drop shadow on"), if (l.dropShadow == true) 1 else 0) { onUpdate(l.copy(dropShadow = it == 1)) }
        SectionTitle("REMOVE BACKGROUND")
        Chips(listOf("Off", "People (automatic)", "Magic Mask (anything)"), if (l.bgRemove != true) 0 else if (l.layerMagic != null) 2 else 1) { i ->
            when (i) { 0 -> onUpdate(l.copy(bgRemove = null)); 1 -> onUpdate(l.copy(bgRemove = true, layerMagic = null)); else -> onMagicLayer(l.id) }
        }
        val lp = magicLayerProgress[l.id]
        if (lp != null) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            Text("Following the object… $lp%", style = Pro.Label.copy(color = Pro.Amber), modifier = Modifier.weight(1f))
            Text("STOP", style = Pro.Label.copy(color = Pro.Red, fontSize = 10.sp), modifier = Modifier.clickable { onStopMagicLayer(l.id) }.padding(6.dp))
        } else l.layerMagic?.let { mg -> Text(if (mg.frames > 0) "✓ Tracked through ${mg.frames} frames" else "Not tracked yet", style = Pro.Label.copy(color = if (mg.frames > 0) Pro.Green else Pro.Dim), modifier = Modifier.padding(vertical = 4.dp)) }
        if (l.layerMagic != null) Text("CHANGE SELECTION…", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable { onMagicLayer(l.id) }.padding(10.dp))
        if (l.bgRemove == true) Chips(listOf("Keep the subject", "Remove the subject (invert)"), if (l.bgInvert == true) 1 else 0) { onUpdate(l.copy(bgInvert = (it == 1).takeIf { v -> v })) }
        Text("AI cuts the subject out of this video layer (on the PC), so only it appears over your main video. People are found automatically; Magic Mask follows anything you tap.", color = Pro.Dim, fontSize = 11.sp)
        Text("Frame shape crops the video to fill it (e.g. 1:1 or 9:16 picture-in-picture). Corners, border and shadow show in the preview and the export.", color = Pro.Dim, fontSize = 11.sp)
    }
    // With keyframes, the controls show (and edit) the layer as it is at the playhead.
    val la = l.animAt(playheadMs)
    EdSlider("Size", la.size, 0.02f..(if (l.isMedia || l.kind == LayerKind.FX) 1f else 0.4f), 0.07f, ::pct) { onUpdate(la.copy(size = it)) }
    EdSlider("Opacity", la.opacity, 0.05f..1f, 1f, ::pct) { onUpdate(la.copy(opacity = it)) }
    if (l.kind != LayerKind.ADJUST) {
    SectionTitle("POSITION")
    Text("Drag the layer in the viewer, or snap it:", color = Pro.Dim, fontSize = 11.sp)
    val spots = listOf(0.12f, 0.5f, 0.88f)
    Column {
        spots.forEach { y ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 2.dp)) {
                spots.forEach { x ->
                    val on = kotlin.math.abs(la.x - x) < 0.02f && kotlin.math.abs(la.y - y) < 0.02f
                    Box(Modifier.size(width = 44.dp, height = 26.dp).clip(RoundedCornerShape(4.dp)).background(if (on) Pro.Amber else Ed.Raised).clickable { onUpdate(la.copy(x = x, y = y)) })
                }
            }
        }
    }
    SectionTitle("KEYFRAMES") { onUpdate(l.copy(keys = null)) }
    Text(
        "Add a keyframe, move the playhead, then drag / resize / fade the layer — a keyframe is added there and the layer glides between them.",
        color = Pro.Dim, fontSize = 11.sp,
    )
    Text(
        "◆ ADD KEYFRAME AT PLAYHEAD", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
        modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable { onUpdate(l.keyAt(playheadMs)) }.padding(10.dp),
    )
    Text(
        if (trackingId == l.id) "■ STOP TRACKING" else "⌖ TRACK MOTION FROM PLAYHEAD", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
        modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable { onTrack(l.id) }.padding(10.dp),
    )
    Text(
        "Place the layer's centre on the object at the playhead, then track: it follows the object to the layer's end. Works best on sharp, high-contrast spots.",
        color = Pro.Dim, fontSize = 11.sp,
    )
    if (l.kind != LayerKind.ADJUST) {
        SectionTitle("CORNER PIN & PLANAR TRACK")
        if (l.pin.isNullOrEmpty()) {
            Text("Put the layer in perspective onto a flat surface — a phone or TV screen, a sign, a wall — by dragging its four corners. Then planar-track it so it stays stuck to the surface as the camera moves.", color = Pro.Dim, fontSize = 11.sp)
            Text(
                "◇ PIN TO FOUR CORNERS", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
                modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable { onPin(l.id, l.defaultPin(frameAspect, playheadMs)) }.padding(10.dp),
            )
        } else {
            val tracked = l.pin.size > 1
            Text(
                if (tracked) "Planar tracked: ${l.pin.size} frames. Dragging a corner moves it on every frame." else "Drag the four amber corners on the picture onto the corners of the surface (pause the video first).",
                color = Pro.Dim, fontSize = 11.sp,
            )
            Text(
                if (pinTrackingId == l.id) "■ STOP PLANAR TRACKING" else "⌖ PLANAR TRACK FROM PLAYHEAD", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
                modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable { onPlanarTrack(l.id) }.padding(10.dp),
            )
            Text("Follows the surface inside the corners from the playhead to the layer's end (works best on surfaces with some texture or detail).", color = Pro.Dim, fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                if (tracked) PillChip("Keep corners, drop track", false) { l.pinAt(playheadMs)?.let { q -> onPin(l.id, listOf(PinQuad(0L, q.toList()))) } }
                PillChip("Remove corner pin", false) { onPin(l.id, null) }
            }
        }
    }
    val keyList = l.keys.orEmpty()
    if (keyList.size > 24) Text("${keyList.size} keyframes (tracked) — showing the first and last", style = Pro.Label, modifier = Modifier.padding(vertical = 2.dp))
    (if (keyList.size > 24) keyList.take(4) + keyList.takeLast(4) else keyList).forEach { k ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp))
                .background(if (kotlin.math.abs(k.t - playheadMs) < 120) Pro.Amber.copy(alpha = 0.16f) else Ed.Raised)
                .clickable { onSeek(k.t) }.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("◆ ${tc(k.t)}", style = Pro.Value.copy(color = Pro.Amber, fontSize = 12.sp), modifier = Modifier.width(96.dp))
            Text("size ${pct(k.size)} · opacity ${pct(k.opacity)}", style = Pro.Label, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.Close, contentDescription = "Delete keyframe", tint = Color.White, modifier = Modifier.size(18.dp).clickable {
                onUpdate(l.copy(keys = l.keys.orEmpty().filter { it !== k }.ifEmpty { null }))
            })
        }
    }
    if (l.kind != LayerKind.VIDEO) {
        SectionTitle("BLEND MODE")
        Chips(LayerBlend.entries.map { it.label }, (l.blend ?: LayerBlend.NORMAL).ordinal) { onUpdate(l.copy(blend = LayerBlend.entries[it].takeIf { b -> b != LayerBlend.NORMAL })) }
        Text("Screen / Add for light leaks and glows, Multiply for shadows and textures, Overlay / Soft light for colour.", color = Pro.Dim, fontSize = 11.sp)
    }
    if (l.kind == LayerKind.VIDEO || l.kind == LayerKind.IMAGE || l.kind == LayerKind.LOGO) {
        SectionTitle("CHROMA KEY (GREEN SCREEN)")
        val keyColors = listOf<Int?>(null, 0xFF00B140.toInt(), 0xFF0047BB.toInt(), 0xFF000000.toInt(), 0xFFFFFFFF.toInt())
        Chips(listOf("Off", "Green", "Blue", "Black", "White"), keyColors.indexOf(l.chroma).coerceAtLeast(0)) { onUpdate(l.copy(chroma = keyColors[it])) }
        if (l.chroma != null) {
            EdSlider("Tolerance", l.chromaTol ?: 0.4f, 0f..1f, 0.4f, ::pct) { onUpdate(l.copy(chromaTol = it)) }
            EdSlider("Edge softness", l.chromaSoft ?: 0.3f, 0f..1f, 0.3f, ::pct) { onUpdate(l.copy(chromaSoft = it)) }
            if (l.kind == LayerKind.VIDEO && android.os.Build.VERSION.SDK_INT < 33) Text("This phone can't show the key in the preview (Android 13+ needed); the export is keyed.", color = Pro.Dim, fontSize = 11.sp)
        }
    }
    if (l.kind == LayerKind.VIDEO || l.kind == LayerKind.IMAGE || l.kind == LayerKind.LOGO) {
        SectionTitle("LUMA KEY")
        Chips(listOf("Off", "Remove dark", "Remove bright"), (l.lumaKey ?: 0).coerceIn(0, 2)) { onUpdate(l.copy(lumaKey = it.takeIf { m -> m != 0 }, lumaLevel = null)) }
        if ((l.lumaKey ?: 0) != 0) {
            EdSlider("Level", l.lumaLevelOrDefault(), 0f..1f, if (l.lumaKey == 2) 0.85f else 0.15f, ::pct) { onUpdate(l.copy(lumaLevel = it)) }
            EdSlider("Softness", l.lumaSoft ?: 0.15f, 0f..1f, 0.15f, ::pct) { onUpdate(l.copy(lumaSoft = it)) }
            if (l.kind == LayerKind.VIDEO && android.os.Build.VERSION.SDK_INT < 33) Text("This phone can't show the key in the preview (Android 13+ needed); the export is keyed.", color = Pro.Dim, fontSize = 11.sp)
        }
        Text("Cuts out by brightness: Remove dark for fire, sparks, light leaks and smoke on black; Remove bright for shapes on white.", color = Pro.Dim, fontSize = 11.sp)
    }
    if (l.kind != LayerKind.VIDEO) {
        SectionTitle("ANIMATION") { onUpdate(l.copy(animIn = null, animOut = null, animMs = null)) }
        val anims = LayerAnim.entries.filter { it != LayerAnim.TYPEWRITER || l.kind == LayerKind.TEXT || l.kind == LayerKind.WATERMARK }
        Text("IN", style = Pro.Label)
        Chips(anims.map { it.label }, anims.indexOf(l.animIn ?: LayerAnim.NONE)) { i -> onUpdate(l.copy(animIn = anims[i])) }
        if (!l.wholeVideo) {
            Text("OUT", style = Pro.Label)
            Chips(anims.map { it.label }, anims.indexOf(l.animOut ?: LayerAnim.NONE)) { i -> onUpdate(l.copy(animOut = anims[i])) }
        }
        if (LayerAnims.hasAny(l)) EdSlider("Animation length", (l.animMs ?: 500L) / 1000f, 0.1f..3f, 0.5f, { "%.1f s".format(it) }) { v -> onUpdate(l.copy(animMs = (v * 1000).toLong())) }
        Text("Plays when the layer appears and disappears. Press play to see it (it stays still while you edit this layer).", color = Pro.Dim, fontSize = 11.sp)
    }
    }
    SectionTitle("TIMING")
    Chips(listOf("Part of the video", "Whole video"), if (l.wholeVideo) 1 else 0) { onUpdate(l.copy(wholeVideo = it == 1)) }
    if (!l.wholeVideo && totalMs > 0) {
        RangeSlider(
            value = l.startMs.coerceIn(0, totalMs).toFloat()..l.endMs.coerceIn(0, totalMs).toFloat(),
            onValueChange = { r -> onUpdate(l.copy(startMs = r.start.toLong(), endMs = r.endInclusive.toLong())) },
            valueRange = 0f..totalMs.toFloat(),
            colors = SliderDefaults.colors(thumbColor = Pro.Amber, activeTrackColor = Pro.Amber, inactiveTrackColor = com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.15f)),
        )
        Text("${tc(l.startMs)} → ${tc(l.endMs)}", style = Pro.Label)
        Text("START AT PLAYHEAD", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp), modifier = Modifier.clickable {
            val len = (l.endMs - l.startMs).coerceAtLeast(500)
            onUpdate(l.copy(startMs = playheadMs, endMs = (playheadMs + len).coerceAtMost(totalMs)))
        }.padding(vertical = 8.dp))
    }
}

@Composable
private fun DeliverPage(
    s: DeliverSettings, clips: List<Clip>, exporting: Boolean, onChange: (DeliverSettings) -> Unit, onExport: () -> Unit,
    playheadMs: Long = 0L, onSaveFrame: () -> Unit = {},
    markers: List<Long> = emptyList(), projectName: String? = null, layers: List<Layer> = emptyList(),
    markerNotes: Map<Long, String> = emptyMap(),
    queue: List<DeliverSettings> = emptyList(), queueAt: Int = -1,
    onQueueAdd: () -> Unit = {}, onQueueRemove: (Int) -> Unit = {}, onQueueStart: () -> Unit = {}, onQueueStop: () -> Unit = {},
    onExportBoth: () -> Unit = {},
    smartWhy: String? = null,
    /** Phones: the cast button lives here (tablets have it next to the zoom buttons). */
    castSlot: (@Composable () -> Unit)? = null,
    /** Client review notes (time, note) → markers. */
    onReviewNotes: (List<Pair<Long, String>>) -> Unit = {},
) {
    // Send the edit to a desktop editor (EDL / FCPXML).
    val ictx = LocalContext.current
    val fpsX = if (s.fps > 0) s.fps else 30
    val title = projectName?.takeIf { it.isNotBlank() } ?: "AlcorCut Pro edit"
    var xNote by remember { mutableStateOf<String?>(null) }
    fun writeTo(uri: android.net.Uri?, text: String) {
        if (uri == null) return
        xNote = try {
            ictx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
            "Saved. On the computer, import it and point the editor at the folder with the original clips."
        } catch (t: Throwable) { "Couldn't save: ${t.message}" }
    }
    val saveEdl = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { u ->
        writeTo(u, Interchange.edl(title, clips, fpsX))
    }
    val saveSrt = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-subrip")) { u ->
        writeTo(u, Interchange.srt(layers))
    }
    val saveVtt = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/vtt")) { u ->
        writeTo(u, Interchange.vtt(layers))
    }
    val saveOtio = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { u ->
        writeTo(u, Interchange.otio(title, clips, fpsX, markers, markerNotes))
    }
    val saveXml = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/xml")) { u ->
        val (w, h) = outputSize(clips, s)
        writeTo(u, Interchange.fcpxml(title, clips, fpsX, w, h, markers, markerNotes))
    }
    val total = if (s.rangeStart != null && s.rangeEnd != null) (s.rangeEnd - s.rangeStart).coerceAtLeast(0) else timelineLength(clips)
    // Estimated file size: video bitrate (chosen, or what the encoder picks on Auto) + AAC audio.
    val (ow, oh) = outputSize(clips, s)
    val outFps = if (s.fps > 0) s.fps else 30
    val autoMbps = ow.toDouble() * oh * outFps * 0.14 / 1_000_000
    val cf = s.cineform in 0..3 && !s.audioOnly
    val videoMbps = if (s.audioOnly) 0.0 else if (cf) ow.toDouble() * oh * outFps * CineForm.BITS_PER_PIXEL[s.cineform] / 1_000_000
        else if (s.bitrateMbps > 0) s.bitrateMbps.toDouble() else autoMbps
    val audioMbps = if (s.audioOnly) s.audioFmt.bytesPerSecond() * 8 / 1_000_000 else if (s.audio) (if (cf) 1.536 else 0.13) else 0.0
    val bytes = (videoMbps + audioMbps) * 1_000_000 / 8 * (total / 1000.0) * 1.01
    // First in Deliver: everything about the file itself, then the button — one place to set up and start the render.
    SectionTitle("RENDER & EXPORT")
    Text("Audio", color = Color.White, fontSize = 13.sp)
    Chips(listOf("Video with audio", "Video, no audio", "Audio only"), if (s.audioOnly) 2 else if (s.audio) 0 else 1) {
        onChange(s.copy(audio = it != 1, audioOnly = it == 2))
    }
    if (s.audioOnly) {
        Text("Audio format", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        val fmts = AudioExportFormat.entries.filter { it.available }
        Chips(fmts.map { it.label }, fmts.indexOf(s.audioFmt).coerceAtLeast(0)) { onChange(s.copy(audioFormat = fmts[it])) }
        Text(
            when (s.audioFmt) {
                AudioExportFormat.M4A -> "AAC 128 kbps — small, plays everywhere."
                AudioExportFormat.WAV16 -> "Uncompressed CD quality (dithered) — opens in every audio and video program."
                AudioExportFormat.WAV24 -> "Uncompressed studio quality — best for further mixing or mastering."
                AudioExportFormat.WAV32F -> "Uncompressed 32-bit float — the mix exactly as rendered, with no clipping in the file."
                AudioExportFormat.AIFF -> "Uncompressed 24-bit — the usual format on Mac (Logic, GarageBand, Final Cut)."
                AudioExportFormat.FLAC -> "Lossless and about half the size of WAV — sounds identical."
                AudioExportFormat.OPUS -> "Opus 160 kbps — very small for its quality, great for web and voice."
            } + " Saves just the sound of the timeline — clips, music and audio tracks — to Music/AlcorCutPro (or your chosen save folder). Muted tracks are left out.",
            color = Pro.Dim, fontSize = 11.sp,
        )
    }
    if (!s.audioOnly) {
        Text("Resolution", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        val res = listOf(0 to "Source", 720 to "720p", 1080 to "1080p", 1440 to "1440p", 1512 to "2.7K", 2160 to "4K UHD") + EncoderCaps.BIG
        Chips(res.map { it.second }, res.indexOfFirst { it.first == s.shortSide }.coerceAtLeast(0)) { onChange(s.copy(shortSide = res[it].first)) }
        // 5K–8K: only when this phone's encoder can make that size (otherwise say what it can do).
        EncoderCaps.labelFor(s.shortSide)?.let { big ->
            val bigFps = remember(ow, oh, s.hevc) { EncoderCaps.maxFps(EncoderCaps.mimeOf(s.hevc), ow, oh) }
            val otherFps = remember(ow, oh, s.hevc) { EncoderCaps.maxFps(EncoderCaps.mimeOf(!s.hevc), ow, oh) }
            val wantFps = if (s.fps > 0) s.fps.toDouble() else 30.0
            val msg = when {
                bigFps <= 0.0 && otherFps > 0.0 -> "$big ($ow×$oh) needs ${if (s.hevc) "H.264" else "HEVC"} on this PC — switch the codec below."
                bigFps <= 0.0 -> "This PC's video encoder can't make $big ($ow×$oh). The export would come out smaller — choose 4K UHD or less."
                bigFps + 0.5 < wantFps -> "$big ($ow×$oh) works on this PC up to ${bigFps.toInt()} fps — set the frame rate to ${bigFps.toInt()} or less."
                else -> "$big ($ow×$oh) is supported by this PC's encoder (up to ${bigFps.toInt()} fps). Use a high bitrate (120–200 Mbps) for the detail; exports take longer."
            }
            Text(msg, color = if (bigFps <= 0.0 || bigFps + 0.5 < wantFps) Pro.Red else Pro.Dim, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
        // 5.7K: a 360° video stays 360° (5760 × 2880 with the 360° tag); any other video is just 5760 wide.
        if (s.shortSide == -5760) {
            val ctx360 = androidx.compose.ui.platform.LocalContext.current
            val first = clips.firstOrNull()
            val is360 by androidx.compose.runtime.produceState(false, first?.uri) { value = first != null && withContext(Dispatchers.IO) { Spherical.is360(ctx360, first.uri) } }
            Text(
                if (is360 && kotlin.math.abs(ow.toFloat() / oh - 2f) < 0.02f) "360° video: exported at $ow×$oh and marked as 360°, so players, YouTube and Facebook show it as a sphere. Keep crop, zoom and stabilisation off on 360° clips."
                else if (is360) "360° video, but the project shape isn't 2:1 — set the shape to Original to keep it 360°."
                else "Not a 360° video: exported at $ow×$oh (5760 wide, its own shape).",
                color = Pro.Amber, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp),
            )
        }
        Text("Codec", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        Chips(listOf("H.264", "HEVC (H.265)", "CineForm (.mov)"), if (s.cineform in 0..3) 2 else if (s.hevc) 1 else 0) {
            onChange(when (it) { 2 -> s.copy(cineform = if (s.cineform in 0..3) s.cineform else 2); 1 -> s.copy(hevc = true, cineform = -1); else -> s.copy(hevc = false, cineform = -1) })
        }
        if (s.cineform in 0..3) {
            Text("CineForm quality", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            Chips(CineForm.QUALITIES, s.cineform) { onChange(s.copy(cineform = it)) }
            Text(
                "A professional editing codec (10-bit 4:2:2, every frame stands alone, uncompressed sound) in a .mov — for finishing on a computer: it opens in the main desktop editing and effects programs and plays smoothly while you cut. Files are large (≈ ${"%.0f".format(ow.toDouble() * oh * outFps * CineForm.BITS_PER_PIXEL[s.cineform] / 1_000_000)} Mbps). The edit is rendered first, then converted, so it takes longer than a normal export." +
                    if (!CineForm.available) " Not available on this PC." else "",
                color = if (CineForm.available) Pro.Dim else Pro.Red, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp),
            )
        } else {
            Text("Bitrate", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            val rates = listOf(0, 20, 50, 80, 120, 200, 300, 400)
            Chips(rates.map { if (it == 0) "Auto" else "$it Mbps" }, rates.indexOf(s.bitrateMbps).coerceAtLeast(0)) { onChange(s.copy(bitrateMbps = rates[it])) }
        }
    }
    Text("Estimated file size", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ed.Raised).border(1.dp, Pro.Amber.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("≈ ${fileSizeText(bytes)}", style = Pro.Value.copy(color = Pro.Amber, fontSize = 22.sp), modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            if (s.audioOnly) {
                Text("Audio only · ${s.audioFmt.label}", style = Pro.Label)
                Text(tc(total), style = Pro.Label)
            } else {
                Text("${ow}×${oh} · ${if (s.fps > 0) "${s.fps} fps" else "source fps"}", style = Pro.Label)
                Text("%.0f Mbps${if (s.bitrateMbps == 0) " (auto)" else ""} · ${tc(total)}".format(videoMbps + audioMbps), style = Pro.Label)
            }
        }
    }
    Text(
        "Timeline ${tc(total)} · ${clips.size} clips. The real size can differ a little${if (s.bitrateMbps == 0) " (on Auto the PC's encoder decides the bitrate)" else ""}. If the PC's encoder can't do the chosen size or bitrate, the closest supported setting is used.",
        color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp),
    )
    Text(
        if (exporting) "RENDERING…" else if (s.audioOnly) "EXPORT AUDIO" else "RENDER & EXPORT",
        style = Pro.Value.copy(color = Color.Black, fontSize = 14.sp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (exporting || clips.isEmpty()) Pro.Amber.copy(alpha = 0.4f) else Pro.Amber)
            .clickable(enabled = !exporting && clips.isNotEmpty(), onClick = onExport).padding(14.dp),
    )
    Text(
        "Exports keep running if you switch apps or lock the screen — a notification shows the progress and tells you when it's done.",
        color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp),
    )
    if (castSlot != null) {
        SectionTitle("CAST")
        Row(verticalAlignment = Alignment.CenterVertically) {
            castSlot()
            Text("Show the viewer on a TV with a Chromecast or Google TV on the same Wi-Fi — picture, titles, layers and sound (a few seconds behind).",
                color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.padding(start = 10.dp))
        }
    }
    ContentCredentialsSection(video = true)
    ExportUploadSection(video = true)
    VideoReviewSection(onReviewNotes)
    SectionTitle("METADATA")
    MetaPresets.load(ictx)
    Text("Your name, copyright, contact, caption and keywords written into the video file.", color = Pro.Dim, fontSize = 11.sp)
    Row(Modifier.hintedScroll(horizontal = true).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PillChip("None", s.meta == null) { onChange(s.copy(meta = null)) }
        MetaPresets.list.forEach { p -> PillChip(p.name, s.meta == p.asEdit()) { onChange(s.copy(meta = p.asEdit())) } }
        var make by remember { mutableStateOf(false) }
        PillChip("+ New preset", false, accentText = true) { make = true }
        if (make) MetaPresetDialog(0, onApply = { p -> make = false; onChange(s.copy(meta = p.asEdit())) }, onDismiss = { make = false })
    }
    SectionTitle("PRESETS")
    Chips(EXPORT_PRESETS.map { it.label }, EXPORT_PRESETS.indexOfFirst { p ->
        s.shortSide == p.shortSide && s.fps == p.fps && s.hevc == p.hevc && s.bitrateMbps == p.mbps && s.aspect == p.aspect
    }) { i ->
        val p = EXPORT_PRESETS[i]
        onChange(s.copy(shortSide = p.shortSide, fps = p.fps, hevc = p.hevc, bitrateMbps = p.mbps, aspect = p.aspect, audioOnly = false, audio = true))
    }
    Text("Sets size, shape, frame rate and quality for where the video is going. Change anything below afterwards.", color = Pro.Dim, fontSize = 11.sp)
    SectionTitle("RANGE") { onChange(s.copy(rangeStart = null, rangeEnd = null)) }
    val total0 = timelineLength(clips)
    val ra = s.rangeStart; val rb = s.rangeEnd
    Chips(listOf("Whole timeline", "Part (in / out)"), if (ra != null && rb != null) 1 else 0) { i ->
        if (i == 0) onChange(s.copy(rangeStart = null, rangeEnd = null))
        else onChange(s.copy(rangeStart = ra ?: 0L, rangeEnd = rb ?: total0))
    }
    if (ra != null && rb != null) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("IN ${tc(ra)}", style = Pro.Value.copy(fontSize = 12.sp), modifier = Modifier.weight(1f))
            Text("SET IN", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Ed.Raised).clickable { onChange(s.copy(rangeStart = playheadMs.coerceAtMost(rb - 100))) }.padding(8.dp))
            Text("OUT ${tc(rb)}", style = Pro.Value.copy(fontSize = 12.sp), modifier = Modifier.weight(1f))
            Text("SET OUT", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Ed.Raised).clickable { onChange(s.copy(rangeEnd = playheadMs.coerceAtLeast(ra + 100))) }.padding(8.dp))
        }
        Text("Move the playhead on the timeline, then tap SET IN / SET OUT. Only that part is exported (${tc(rb - ra)}).", color = Pro.Dim, fontSize = 11.sp)
    }
    SectionTitle("REVIEW COPY")
    Chips(listOf("Off", "Review copy"), if (s.review) 1 else 0) { onChange(s.copy(review = it == 1)) }
    if (s.review) {
        androidx.compose.material3.OutlinedTextField(s.reviewText, { onChange(s.copy(reviewText = it.take(40))) }, label = { Text("Watermark text (empty = none)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
        Chips(listOf("Marker notes on screen", "No notes"), if (s.reviewNotes) 0 else 1) { onChange(s.copy(reviewNotes = it == 0)) }
    }
    Text("For sending a cut to a client: timecode burned into the corner, a faint watermark, and each marker's note on screen for 2.5 s — so feedback can say exactly where. Turn it off again for the final export.", color = Pro.Dim, fontSize = 11.sp)
    SectionTitle("SMART RENDER")
    Chips(listOf("Off", "Fast copy when possible"), if (s.smartRender) 1 else 0) { onChange(s.copy(smartRender = it == 1)) }
    Text(
        if (!s.smartRender) "A cut-only edit can be copied straight from the clips: no re-encoding, no quality loss, and many times faster."
        else if (smartWhy == null) "This edit can be fast-copied. Each clip starts on the key frame at or just before its in point (up to about a second earlier). If the clips' formats don't match, it exports normally."
        else "Exports normally: $smartWhy.",
        color = if (s.smartRender && smartWhy == null) Pro.Amber else Pro.Dim, fontSize = 11.sp,
    )
    SectionTitle("SAFE ZONES")
    Chips(SafeZones.zones.map { it.label }, SafeZones.current) { SafeZones.current = it }
    Text("Shows on the viewer where TikTok, Reels or Shorts put their buttons and captions, so titles stay clear of them (approximate — the apps change). Action / title safe is the TV standard. Never part of the export.", color = Pro.Dim, fontSize = 11.sp)
    SectionTitle("STILL FRAME")
    Text(
        "SAVE FRAME AS PHOTO", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable(onClick = onSaveFrame).padding(10.dp),
    )
    Text("Saves the frame under the playhead, with its grade and text / logo layers, as a full-size JPEG.", color = Pro.Dim, fontSize = 11.sp)
    SectionTitle("FRAME RATE")
    val fps = listOf(0, 24, 25, 30, 60)
    Chips(fps.map { if (it == 0) "Source" else "$it fps" }, fps.indexOf(s.fps).coerceAtLeast(0)) { onChange(s.copy(fps = fps[it])) }
    SectionTitle("DYNAMIC RANGE")
    Chips(listOf("SDR (plays everywhere)", "Keep HDR (HLG / HDR10)"), if (s.keepHdr) 1 else 0) { onChange(s.copy(keepHdr = it == 1)) }
    Text(if (s.keepHdr) "HDR clips stay HDR in a 10-bit HEVC file — brighter highlights on HDR screens (YouTube, Instagram, newer phones). Works best with light grading; if the export fails on this PC, switch back to SDR." else "HDR clips are converted to standard range so they look right on every screen.", color = Pro.Dim, fontSize = 11.sp)
    if (s.audio || s.audioOnly) {
        SectionTitle("LOUDNESS")
        val opts = listOf<Float?>(null) + Loudness.TARGETS
        Chips(listOf("As mixed", "−14 LUFS (YouTube, Instagram, Spotify)", "−16 LUFS (Apple, podcasts)", "−23 LUFS (TV broadcast)"), opts.indexOf(s.loudness).coerceAtLeast(0)) { i -> onChange(s.copy(loudness = opts[i])) }
        Text("Makes every export equally loud, so it sounds right next to other videos. The sound is measured first (a few seconds), then the whole mix is turned up or down; a limiter stops it from clipping.", color = Pro.Dim, fontSize = 11.sp)
    }
    SectionTitle("SEND TO DESKTOP EDITOR")
    Text("Continue the edit on a computer: the cuts (and dissolves / speed in the EDL) open in desktop editing software that reads EDL or FCPXML. Grades, effects and layers stay here. Copy the original clips to the computer too.", color = Pro.Dim, fontSize = 11.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        Text("EDL (.edl)", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Ed.Raised).clickable(enabled = clips.isNotEmpty()) { saveEdl.launch(title.replace(Regex("[^A-Za-z0-9 _-]"), "") + ".edl") }.padding(10.dp))
        Text("FCPXML (.fcpxml)", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Ed.Raised).clickable(enabled = clips.isNotEmpty()) { saveXml.launch(title.replace(Regex("[^A-Za-z0-9 _-]"), "") + ".fcpxml") }.padding(10.dp))
        Text("OTIO (.otio)", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Ed.Raised).clickable(enabled = clips.isNotEmpty()) { saveOtio.launch(title.replace(Regex("[^A-Za-z0-9 _-]"), "") + ".otio") }.padding(10.dp))
    }
    Text("OTIO = OpenTimelineIO, the open timeline format many editing and finishing tools read.", color = Pro.Dim, fontSize = 11.sp)
    if (layers.any { it.name == "Caption" && it.kind == LayerKind.TEXT }) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("SUBTITLES (.srt)", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(6.dp)).background(Ed.Raised).clickable { saveSrt.launch(title.replace(Regex("[^A-Za-z0-9 _-]"), "") + ".srt") }.padding(10.dp))
            Text("WEB SUBTITLES (.vtt)", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(6.dp)).background(Ed.Raised).clickable { saveVtt.launch(title.replace(Regex("[^A-Za-z0-9 _-]"), "") + ".vtt") }.padding(10.dp))
        }
        Text("The captions as a subtitle file — .srt for YouTube, Vimeo or a desktop editor, .vtt for web players.", color = Pro.Dim, fontSize = 11.sp)
    }
    Text("Frame rate: $fpsX fps (set it under FRAME RATE above to match your clips).", color = Pro.Dim, fontSize = 11.sp)
    xNote?.let { Text(it, color = Color.White, fontSize = 11.sp) }
    // Animated GIF of the timeline (or the chosen range), up to 20 seconds.
    SectionTitle("ANIMATED GIF")
    val gScope = rememberCoroutineScope()
    var gifW by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(480) }
    var gifFps by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(12) }
    var gifProgress by remember { mutableStateOf<Int?>(null) }
    val gTotal = timelineLength(clips)
    val gFrom = (s.rangeStart ?: 0L).coerceIn(0, gTotal)
    val gTo = minOf(s.rangeEnd ?: gTotal, gTotal, gFrom + 20_000)
    val saveGif = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/gif")) { u ->
        if (u == null) return@rememberLauncherForActivityResult
        gifProgress = 0
        gScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    ictx.contentResolver.openOutputStream(u, "wt")?.use { o ->
                        GifExport.render(ictx, clips, layers, gFrom, gTo, gifW, gifFps, java.io.BufferedOutputStream(o)) { p -> gifProgress = p }
                    } ?: false
                } catch (_: Throwable) { false }
            }
            gifProgress = null
            xNote = if (ok) "GIF saved" else "Couldn't make the GIF"
        }
    }
    Chips(listOf("320 px", "480 px", "640 px"), listOf(320, 480, 640).indexOf(gifW)) { gifW = listOf(320, 480, 640)[it] }
    Chips(listOf("10 fps", "12 fps", "15 fps"), listOf(10, 12, 15).indexOf(gifFps)) { gifFps = listOf(10, 12, 15)[it] }
    val gp = gifProgress
    Text(
        if (gp != null) "MAKING GIF… $gp%" else "SAVE GIF (%.1f s)".format((gTo - gFrom) / 1000f),
        style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
        modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(6.dp)).background(Ed.Raised).clickable(enabled = gp == null && clips.isNotEmpty()) { saveGif.launch(title.replace(Regex("[^A-Za-z0-9 _-]"), "") + ".gif") }.padding(10.dp),
    )
    Text("Uses the RANGE above if set (up to 20 s). Includes the grade and text / logo layers; no sound.", color = Pro.Dim, fontSize = 11.sp)
    // Render queue: several exports of the same edit, one after another.
    SectionTitle("YOUTUBE CHAPTERS")
    Text("Your markers (with their notes as titles) as a chapter list for the YouTube description — starts at 0:00, chapters at least 10 s apart.", color = Pro.Dim, fontSize = 11.sp)
    Text(
        "COPY YOUTUBE CHAPTERS", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
        modifier = Modifier.padding(vertical = 6.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable {
            val text = ProTools.chapters(markers, markerNotes, timelineLength(clips))
            val cm = ictx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("YouTube chapters", text))
            xNote = if (text.lines().size < 3) "Copied — YouTube shows chapters when there are at least 3; add more markers (◆ MARKER on the timeline)." else "Chapters copied — paste them into the YouTube description:\n$text"
        }.padding(10.dp),
    )
    SectionTitle("WIDE + VERTICAL")
    Text(
        "EXPORT 16:9 AND 9:16", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
        modifier = Modifier.padding(vertical = 6.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable(enabled = queueAt < 0 && !exporting) { onExportBoth() }.padding(10.dp),
    )
    Text("Two videos from the same edit with the settings above: a 16:9 one for YouTube and a 9:16 one for Reels, Shorts and TikTok (the picture fills the frame, centred — use Auto-reframe on clips to follow the action).", color = Pro.Dim, fontSize = 11.sp)
    SectionTitle("RENDER QUEUE")
    Text("Set up an export above and tap + ADD TO QUEUE; change the settings and add again (e.g. YouTube 4K, a 9:16 Reel and an audio file). START QUEUE renders them one after another.", color = Pro.Dim, fontSize = 11.sp)
    Text(
        "+ ADD TO QUEUE", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
        modifier = Modifier.padding(vertical = 6.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable(enabled = queueAt < 0) { onQueueAdd() }.padding(10.dp),
    )
    queue.forEachIndexed { i, q ->
        val (qw, qh) = outputSize(clips, q)
        val label = if (q.audioOnly) "Audio only · ${q.audioFmt.label}"
            else "${qw}×${qh} · ${if (q.fps > 0) "${q.fps} fps" else "source fps"} · ${if (q.hevc || q.keepHdr) "HEVC" else "H.264"}${if (q.keepHdr) " HDR" else ""}"
        val state = when {
            queueAt < 0 -> ""
            i < queueAt - 1 -> "✓"
            i == queueAt - 1 && exporting -> "…"
            else -> ""
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${i + 1}", style = Pro.Value.copy(color = Pro.Amber), modifier = Modifier.width(22.dp))
            Text(label, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
            if (state.isNotEmpty()) Text(state, color = if (state == "✓") Pro.Green else Pro.Amber, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 6.dp))
            if (queueAt < 0) Text("✕", color = Pro.Red, fontSize = 14.sp, modifier = Modifier.clickable { onQueueRemove(i) }.padding(6.dp))
        }
    }
    if (queue.isNotEmpty()) Text(
        if (queueAt >= 0) "STOP QUEUE" else "START QUEUE (${queue.size})",
        style = Pro.Value.copy(color = if (queueAt >= 0) Color.White else Color.Black, fontSize = 13.sp),
        modifier = Modifier.padding(top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (queueAt >= 0) Pro.Red else Pro.Amber)
            .clickable(enabled = clips.isNotEmpty() && (queueAt >= 0 || !exporting)) { if (queueAt >= 0) onQueueStop() else onQueueStart() }.padding(12.dp),
    )
}

/** 1.2 GB / 340 MB style. */
fun fileSizeText(bytes: Double): String = when {
    bytes >= 1e9 -> "%.2f GB".format(bytes / 1e9)
    bytes >= 1e6 -> "%.0f MB".format(bytes / 1e6)
    else -> "%.0f KB".format((bytes / 1e3).coerceAtLeast(1.0))
}

/** Timecode mm:ss.ff-style (hours when needed). */
fun tc(ms: Long): String {
    val t = ms.coerceAtLeast(0)
    val h = t / 3_600_000; val m = (t / 60_000) % 60; val s = (t / 1000) % 60; val cs = (t % 1000) / 10
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d.%02d".format(m, s, cs)
}

/** Common aspect ratios (label, width / height; 0 = original). */
val ASPECTS = listOf(
    "Original" to 0f, "16:9" to 16f / 9f, "9:16" to 9f / 16f, "1:1" to 1f, "4:5" to 0.8f, "4:3" to 4f / 3f,
    "3:4" to 0.75f, "21:9" to 21f / 9f, "2.39:1" to 2.39f, "3:2" to 1.5f,
)

/**
 * Playback resolution: [tv] is laid out at 1/[div] of its frame and scaled back up, so the preview
 * (and the grade drawn on it) is rendered at that resolution. 1 = full size.
 */
internal fun applyPlaybackRes(frame: android.widget.FrameLayout, tv: android.view.View, div: Int) {
    val fw = frame.width; val fh = frame.height
    if (fw <= 0 || fh <= 0) return
    val w = if (div <= 1) -1 else (fw / div).coerceAtLeast(2)
    val h = if (div <= 1) -1 else (fh / div).coerceAtLeast(2)
    val lp = tv.layoutParams as? android.widget.FrameLayout.LayoutParams ?: return
    if (lp.width == w && lp.height == h) return
    tv.layoutParams = android.widget.FrameLayout.LayoutParams(w, h, if (div <= 1) android.view.Gravity.CENTER else android.view.Gravity.TOP or android.view.Gravity.START)
    tv.pivotX = 0f; tv.pivotY = 0f
    tv.scaleX = if (div <= 1) 1f else fw.toFloat() / w
    tv.scaleY = if (div <= 1) 1f else fh.toFloat() / h
}

/** The preview's mute switch, for the video layers' own players. */
internal object LayerSound {
    var previewMuted by mutableStateOf(false)
}

/** Live preview of a video layer: its own player (silent unless its sound is on), kept in step with the timeline. */
@OptIn(UnstableApi::class)
@Composable
private fun VideoLayerView(l: Layer, timeMs: Long, play: Boolean, pin: FloatArray? = null) {
    val context = LocalContext.current
    val p = remember(l.videoUri) {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            l.videoUri?.let { setMediaItem(MediaItem.fromUri(it)); prepare() }
        }
    }
    DisposableEffect(p) { onDispose { p.release() } }
    // Its own sound only when switched on for this layer (and the preview isn't muted).
    LaunchedEffect(l.soundOn, LayerSound.previewMuted) { p.volume = if (l.soundOn == true && !LayerSound.previewMuted) l.soundVolume() else 0f }
    val target = l.videoStartMs + (timeMs - (if (l.wholeVideo) 0L else l.startMs)).coerceAtLeast(0)
    LaunchedEffect(play) { if (play) p.play() else p.pause() }
    LaunchedEffect(target / 300, play) {
        if (!play || kotlin.math.abs(p.currentPosition - target) > 400) p.seekTo(target.coerceAtMost((l.videoDurationMs - 50).coerceAtLeast(0)))
    }
    // Background removal (people or Magic Mask): drawn on the view, frame by frame, like the export.
    val tvRef = remember { arrayOfNulls<android.view.TextureView>(1) }
    val cutBg = remember { BgRemoval(0) }
    LaunchedEffect(l.bgRemove, l.layerMagic, l.bgInvert, l.chroma, l.chromaTol, l.chromaSoft, l.lumaKey, l.lumaLevel, l.lumaSoft) {
        if (l.bgRemove != true) { tvRef[0]?.let { ChromaPreview.apply(it, l) }; return@LaunchedEffect }
        while (true) {
            androidx.compose.runtime.withFrameNanos { }
            val tv = tvRef[0] ?: continue
            val mm = l.layerMagic
            val fx = if (mm != null && mm.frames <= 0) null else CutoutPreview.effect(context, tv, cutBg, mm, l.bgInvert == true, p.currentPosition)
            ChromaPreview.apply(tv, l, fx)
        }
    }
    AndroidView(
        factory = { ctx -> android.view.TextureView(ctx).also { it.isOpaque = false; p.setVideoTextureView(it); tvRef[0] = it } },
        update = { tv ->
            if (l.bgRemove != true) ChromaPreview.apply(tv, l)
            // Corner pin: the picture drawn in perspective onto its corners.
            if (pin != null && tv.width > 0) tv.setTransform(pinMatrix(pin, tv.width.toFloat(), tv.height.toFloat(), tv.width.toFloat(), tv.height.toFloat()))
            else tv.setTransform(null)
        },
        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = l.opacity },
    )
}

@Composable
private fun RoundControl(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, size: androidx.compose.ui.unit.Dp, accent: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(androidx.compose.foundation.shape.CircleShape)
            .background(if (accent) Pro.Amber else Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = label, tint = if (accent) Color.Black else Color.White, modifier = Modifier.size(size * 0.55f)) }
}

/** The two preview players and which one is on screen. */
private class Deck(val players: List<ExoPlayer>) {
    var active by mutableStateOf(0)
    val cur: ExoPlayer get() = players[active]
    val spare: ExoPlayer get() = players[1 - active]
}

/** Icon: the playhead (marker and line, right) jumping along an arrow to the start of the timeline (bar, left). Own drawing. */
private val PlayheadToStart: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder("PlayheadToStart", 24.dp, 24.dp, 24f, 24f).apply {
        val fill = androidx.compose.ui.graphics.SolidColor(Color.White)
        // Start of the timeline.
        addPath(androidx.compose.ui.graphics.vector.PathParser().parsePathString("M2.5,4h2v16h-2z").toNodes(), fill = fill)
        // Arrow towards it.
        addPath(androidx.compose.ui.graphics.vector.PathParser().parsePathString("M6.5,12l4.5,-4.2v3.2h4.5v2h-4.5v3.2z").toNodes(), fill = fill)
        // The playhead: marker on top and its line.
        addPath(androidx.compose.ui.graphics.vector.PathParser().parsePathString("M16.5,3h5v3.6l-2.5,2.6l-2.5,-2.6z").toNodes(), fill = fill)
        addPath(androidx.compose.ui.graphics.vector.PathParser().parsePathString("M18.4,8.5h1.2v12.5h-1.2z").toNodes(), fill = fill)
    }.build()
}

@Composable
internal fun TransportIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color = Color.White, accent: Boolean = false, size: androidx.compose.ui.unit.Dp = 34.dp, iconSize: androidx.compose.ui.unit.Dp = 20.dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).then(if (accent) Modifier.background(Pro.Amber) else Modifier).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = if (accent) Color.Black else tint, modifier = Modifier.size(iconSize))
    }
}

/** ExoPlayer whose audio runs through [fx] (EQ, dynamics, channels) before output. */
/** Decoders that read the project's 4:2:2 originals (filled in as clips are added). */
private val liveDecoders = java.util.concurrent.CopyOnWriteArraySet<String>()

@OptIn(UnstableApi::class)
private fun fxPlayer(context: android.content.Context, fx: AudioFxProcessor): ExoPlayer {
    val rf = object : androidx.media3.exoplayer.DefaultRenderersFactory(context) {
        override fun buildAudioSink(context: android.content.Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): androidx.media3.exoplayer.audio.AudioSink =
            androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                // The sound also goes to a TV while casting (mixed with the other players there).
                .setAudioProcessors(arrayOf(fx, androidx.media3.exoplayer.audio.TeeAudioProcessor(CastTap())))
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .build()
    }
    // Originals that need a special decoder (4:2:2) play with it when they aren't on a proxy.
    rf.setMediaCodecSelector(Chroma.selector(liveDecoders)).setEnableDecoderFallback(true)
    return ExoPlayer.Builder(context, rf).build().also { com.dronedirector.veyrocutpro.ui.AudioHardware.track(context, it) }
}

/** Hands what a player is playing to the TV feed (only while casting). */
@OptIn(UnstableApi::class)
private class CastTap : androidx.media3.exoplayer.audio.TeeAudioProcessor.AudioBufferSink {
    private var rate = 48000; private var channels = 2; private var encoding = androidx.media3.common.C.ENCODING_PCM_16BIT
    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) { rate = sampleRateHz; channels = channelCount; this.encoding = encoding }
    override fun handleBuffer(buffer: java.nio.ByteBuffer) {
        if (!com.dronedirector.veyrocutpro.camera.CastFeed.running) return
        val b = buffer.duplicate().order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val pcm = when (encoding) {
            androidx.media3.common.C.ENCODING_PCM_16BIT -> ShortArray(b.remaining() / 2) { b.getShort() }
            androidx.media3.common.C.ENCODING_PCM_FLOAT -> ShortArray(b.remaining() / 4) { (b.getFloat().coerceIn(-1f, 1f) * 32767f).toInt().toShort() }
            else -> return
        }
        com.dronedirector.veyrocutpro.camera.CastFeed.pushPcm(this, pcm, rate, channels)
    }
}

/** Pinch to zoom, drag to move and twist to rotate the picture; thirds grid while editing. */
@Composable
private fun CropOverlay(editingEnd: Boolean, onGesture: (androidx.compose.ui.geometry.Offset, Float, Float, Float, Float) -> Unit, onDone: () -> Unit) {
    val cb by androidx.compose.runtime.rememberUpdatedState(onGesture)
    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            detectTransformGestures { _, pan, zoomF, rot ->
                cb(pan, zoomF, rot, size.width.toFloat(), size.height.toFloat())
            }
        }
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val c = Color.White.copy(alpha = 0.45f)
            for (k in 1..2) {
                drawLine(c, androidx.compose.ui.geometry.Offset(size.width * k / 3, 0f), androidx.compose.ui.geometry.Offset(size.width * k / 3, size.height), 1.5f)
                drawLine(c, androidx.compose.ui.geometry.Offset(0f, size.height * k / 3), androidx.compose.ui.geometry.Offset(size.width, size.height * k / 3), 1.5f)
            }
            drawRect(Pro.Amber, style = androidx.compose.ui.graphics.drawscope.Stroke(4f))
        }
        Text(
            if (editingEnd) "CROP · END FRAME" else "CROP", style = Pro.Label.copy(color = Color.Black, fontSize = 10.sp),
            modifier = Modifier.align(Alignment.TopCenter).padding(8.dp).clip(RoundedCornerShape(6.dp)).background(Pro.Amber).padding(horizontal = 8.dp, vertical = 3.dp),
        )
        Text(
            "DONE", style = Pro.Label.copy(color = Color.Black, fontSize = 11.sp),
            modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp).clip(RoundedCornerShape(50)).background(Pro.Amber).clickable(onClick = onDone).padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

/** One undo step in the video editor. */
private data class EditSnap(val clips: List<Clip>, val layers: List<Layer>, val music: MusicTrack?, val extra: List<MusicTrack>)

/** Splits caption text into pieces of at most two lines of about [max] characters. */
fun captionPieces(text: String, max: Int): List<String> {
    val words = text.replace('\n', ' ').split(' ').filter { it.isNotBlank() }
    val lines = ArrayList<String>()
    var cur = StringBuilder()
    for (w in words) {
        if (cur.isNotEmpty() && cur.length + 1 + w.length > max) { lines += cur.toString(); cur = StringBuilder() }
        if (cur.isNotEmpty()) cur.append(' ')
        cur.append(w)
    }
    if (cur.isNotEmpty()) lines += cur.toString()
    return lines.chunked(2).map { it.joinToString("\n") }.ifEmpty { listOf(text) }
}

/** Panels menu for the video editor: viewer and timeline always stay; the rest can be hidden. */
@Composable
private fun VideoPanelsButton(onPage: (Page) -> Unit, onReset: () -> Unit) {
    val panels = listOf(
        com.dronedirector.veyrocutpro.ui.PanelDef("ve.inspector", "Tools panel", "Media, Edit, Color, Layers, Audio and Deliver pages"),
        com.dronedirector.veyrocutpro.ui.PanelDef("ve.transport", "Transport bar", "Play, split, undo / redo, timeline zoom"),
        com.dronedirector.veyrocutpro.ui.PanelDef("ve.scope", "Histogram pull-out", "Show or hide the RGB histogram tab on the right edge of the player"),
        com.dronedirector.veyrocutpro.ui.PanelDef("ve.waves", "Audio waveforms", "Sound waves drawn on the timeline clips"),
        com.dronedirector.veyrocutpro.ui.PanelDef("ve.cast", "Cast button", "Chromecast button: show the viewer on a TV"),
        com.dronedirector.veyrocutpro.ui.PanelDef("ve.mediastrip", "Media strip", "Imported clips above the timeline: skim, rate, add to the timeline"),
    )
    val all = panels.map { it.key }.toSet()
    com.dronedirector.veyrocutpro.ui.PanelsButton(
        prefix = "ve",
        panels = panels,
        presets = listOf(
            com.dronedirector.veyrocutpro.ui.PanelPreset("Full", all),
            com.dronedirector.veyrocutpro.ui.PanelPreset("Cut", setOf("ve.transport")),
            com.dronedirector.veyrocutpro.ui.PanelPreset("Color", setOf("ve.inspector", "ve.transport", "ve.scope")) { onPage(Page.COLOR) },
            com.dronedirector.veyrocutpro.ui.PanelPreset("Audio", setOf("ve.inspector", "ve.transport", "ve.waves")) { onPage(Page.AUDIO) },
        ),
        onReset = onReset,
    )
}


/** Built-in music and sounds, by section; listen first, then add at the playhead. */
@Composable
private fun SoundLibraries(onAdd: (LibSection, LibTrack) -> Unit) {
    val context = LocalContext.current
    var open by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(SoundLibrary.playingId) }
    DisposableEffect(Unit) { onDispose { SoundLibrary.stopPreview() } }
    SectionTitle("SOUND LIBRARIES")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.hintedScroll(horizontal = true)) {
        SoundLibrary.sections.forEach { sec ->
            PillChip("${sec.title} · ${sec.tracks.size}", open == sec.folder) {
                open = if (open == sec.folder) null else sec.folder
            }
        }
    }
    val sec = SoundLibrary.sections.firstOrNull { it.folder == open } ?: run {
        Text("Music and sounds you can use in your videos. Tap a section to browse.", color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.padding(vertical = 4.dp))
        return
    }
    Text(sec.subtitle, color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
    if (sec.tracks.isEmpty()) {
        Text("Coming soon.", color = Pro.Dim, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
        return
    }
    sec.tracks.forEach { t ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val on = playing == t.id
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(if (on) Pro.Amber else com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.08f))
                    .clickable { SoundLibrary.togglePreview(context, sec, t) { playing = it } },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (on) "■" else "▶", color = if (on) Color.Black else Pro.Amber, fontSize = 13.sp)
            }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(t.title, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(tc(t.durationMs) + if (on) " · playing" else "", style = Pro.Label)
            }
            Text(
                "+ ADD", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable {
                    SoundLibrary.stopPreview(); playing = null
                    onAdd(sec, t)
                }.padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
    Text("+ ADD puts the track on the timeline at the playhead (the first one becomes the music track). It plays in the preview and is mixed into your export.", color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
}


/** Text-based editing: the edit as text; tap words to cut them, then APPLY CUTS. */
@kotlin.OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun TextEditPage(
    words: List<TWord>, clips: List<Clip>, currentClipId: String?, playSrcMs: Long,
    model: SpeechModel, onModel: (SpeechModel) -> Unit, hasModel: Boolean, downloading: Int,
    onDownload: () -> Unit, onCancelDownload: () -> Unit,
    lang: String?, onLang: (String?) -> Unit, status: String?,
    onTranscribe: () -> Unit, onStop: () -> Unit,
    onToggle: (TWord) -> Unit, onSeekWord: (TWord) -> Unit,
    onFillers: (Transcriber.FillerOptions) -> Unit, onRestore: () -> Unit, onApply: () -> Unit,
    onWordCaptions: (Int) -> Unit = {},
) {
    SectionTitle("TEXT-BASED EDITING")
    Text(
        "Your edit as text, made on this PC (nothing is uploaded). Tap words to cut them — they turn red — then APPLY CUTS. Hold a word to jump there.",
        color = Pro.Dim, fontSize = 11.sp,
    )
    Text("Speech model", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
    Chips(SpeechModel.entries.map { "${it.label} · ${it.sizeMb} MB" }, model.ordinal) { onModel(SpeechModel.entries[it]) }
    if (downloading >= 0) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        androidx.compose.material3.LinearProgressIndicator(progress = { downloading / 100f }, color = Pro.Amber, modifier = Modifier.weight(1f).height(4.dp))
        Text("$downloading%", style = Pro.Label, modifier = Modifier.padding(horizontal = 8.dp))
        Text("CANCEL", style = Pro.Label.copy(color = Pro.Red, fontSize = 10.sp), modifier = Modifier.clickable { onCancelDownload() }.padding(6.dp))
    } else if (!hasModel) Text(
        "DOWNLOAD MODEL (${model.sizeMb} MB, once)", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
        modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable { onDownload() }.padding(10.dp),
    ) else Text("Model ready ✓", color = Pro.Green, fontSize = 11.sp)
    val langs = listOf<Pair<String, String?>>("Auto" to null, "English" to "en", "Danish" to "da", "Faroese" to "fo", "Norwegian" to "no", "Swedish" to "sv", "Icelandic" to "is", "German" to "de", "Spanish" to "es", "French" to "fr")
    Text("Language", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
    Chips(langs.map { it.first }, langs.indexOfFirst { it.second == lang }.coerceAtLeast(0)) { onLang(langs[it].second) }
    if (status != null) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        CircularProgressIndicator(color = Pro.Amber, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
        Text(status, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f).padding(start = 8.dp))
        Text("STOP", style = Pro.Label.copy(color = Pro.Red, fontSize = 10.sp), modifier = Modifier.clickable { onStop() }.padding(6.dp))
    } else Text(
        if (words.isEmpty()) "TRANSCRIBE THE EDIT" else "TRANSCRIBE AGAIN",
        style = Pro.Value.copy(color = Color.Black, fontSize = 13.sp),
        modifier = Modifier.padding(vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Pro.Amber).clickable(enabled = clips.isNotEmpty()) { onTranscribe() }.padding(12.dp),
    )
    if (words.isEmpty()) return
    var fSounds by remember { mutableStateOf(true) }
    var fRepeats by remember { mutableStateOf(true) }
    var fPhrases by remember { mutableStateOf(false) }
    Text("Filler words to find", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 2.dp)) {
        PillChip("um / uh", fSounds) { fSounds = !fSounds }
        PillChip("Repeats & stutters", fRepeats) { fRepeats = !fRepeats }
        PillChip("you know / like", fPhrases) { fPhrases = !fPhrases }
    }
    val deleted = words.count { it.deleted }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        Text("REMOVE FILLERS", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp),
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable { onFillers(Transcriber.FillerOptions(fSounds, fRepeats, fPhrases)) }.padding(8.dp))
        if (deleted > 0) Text("RESTORE ALL", style = Pro.Label.copy(color = Color.White, fontSize = 10.sp),
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable { onRestore() }.padding(8.dp))
    }
    clips.forEachIndexed { ci, c ->
        val ws = words.filter { it.clipId == c.id }
        if (ws.isEmpty()) return@forEachIndexed
        Text("CLIP ${ci + 1} · ${c.name}", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp), modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            ws.forEach { w ->
                val now = c.id == currentClipId && playSrcMs in w.startMs until w.endMs
                Text(
                    w.text,
                    color = if (w.deleted) Pro.Red else if (now) Color.Black else Color.White,
                    fontSize = 14.sp,
                    textDecoration = if (w.deleted) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                    modifier = Modifier.clip(RoundedCornerShape(4.dp))
                        .background(if (now) Pro.Amber else if (w.deleted) Pro.Red.copy(alpha = 0.12f) else Color.Transparent)
                        .combinedClickable(onClick = { onToggle(w) }, onLongClick = { onSeekWord(w) })
                        .padding(horizontal = 3.dp, vertical = 1.dp),
                )
            }
        }
    }
    Text(
        if (deleted > 0) "APPLY CUTS ($deleted WORDS)" else "TAP WORDS TO CUT THEM",
        style = Pro.Value.copy(color = if (deleted > 0) Color.Black else Pro.Dim, fontSize = 13.sp),
        modifier = Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (deleted > 0) Pro.Amber else Ed.Raised).clickable(enabled = deleted > 0) { onApply() }.padding(12.dp),
    )
    SectionTitle("WORD-BY-WORD CAPTIONS")
    Text("Animated captions from the transcript: each word lights up as it's spoken (like Reels / TikTok / Shorts). Made on the PC; cut words are left out.", color = Pro.Dim, fontSize = 11.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.hintedScroll(horizontal = true).padding(vertical = 4.dp)) {
        listOf("Highlight", "Box", "One word", "Two lines").forEachIndexed { k, n -> PillChip(n, false) { onWordCaptions(k) } }
    }
    if (words.isEmpty()) Text("Transcribe the edit first (above) — or tap a style and it's transcribed for you.", color = Pro.Dim, fontSize = 11.sp)

}

/** Edit page extras: scene detection and working on several clips at once. */
@Composable
private fun ProEditTools(
    clips: List<Clip>, selectedIdx: Int, sceneProgress: Int,
    onDetectScenes: () -> Unit, onStopScenes: () -> Unit,
    hlProgress: Int, onHighlights: (Long) -> Unit, onStopHighlights: () -> Unit,
    hlBeat: Boolean = false, onHlBeat: (Boolean) -> Unit = {},
    picked: List<String>, onPick: (String) -> Unit, onPickAll: () -> Unit, onBatch: (String) -> Unit,
) {
    SectionTitle("SCENE DETECTION")
    Text("Finds every camera cut inside the selected clip (e.g. a long recording or a finished film) and splits it into separate shots.", color = Pro.Dim, fontSize = 11.sp)
    if (sceneProgress >= 0) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        androidx.compose.material3.LinearProgressIndicator(progress = { sceneProgress / 100f }, color = Pro.Amber, modifier = Modifier.weight(1f).height(4.dp))
        Text("$sceneProgress%", style = Pro.Label, modifier = Modifier.padding(horizontal = 8.dp))
        Text("STOP", style = Pro.Label.copy(color = Pro.Red, fontSize = 10.sp), modifier = Modifier.clickable { onStopScenes() }.padding(6.dp))
    } else Text(
        "DETECT SCENES & SPLIT CLIP ${selectedIdx + 1}", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
        modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable(enabled = clips.isNotEmpty()) { onDetectScenes() }.padding(10.dp),
    )
    SectionTitle("AUTO HIGHLIGHTS")
    Chips(listOf("Cut between words", "Cut on the music's beat"), if (hlBeat) 1 else 0) { onHlBeat(it == 1) }
    Text("Finds the liveliest moments of your clips — laughter, cheering, a raised voice, movement, lively talk, sharp shots and faces — and makes a short reel of them, cut between words or on the music's beat (add a music track first). Transcribe first (Text page) for the cleanest cuts. Undo brings your clips back.", color = Pro.Dim, fontSize = 11.sp)
    if (hlProgress >= 0) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        androidx.compose.material3.LinearProgressIndicator(progress = { hlProgress / 100f }, color = Pro.Amber, modifier = Modifier.weight(1f).height(4.dp))
        Text("$hlProgress%", style = Pro.Label, modifier = Modifier.padding(horizontal = 8.dp))
        Text("STOP", style = Pro.Label.copy(color = Pro.Red, fontSize = 10.sp), modifier = Modifier.clickable { onStopHighlights() }.padding(6.dp))
    } else Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        listOf("15 s" to 15_000L, "30 s" to 30_000L, "60 s" to 60_000L, "2 min" to 120_000L).forEach { (n, ms) ->
            PillChip("Reel $n", false) { if (clips.isNotEmpty()) onHighlights(ms) }
        }
    }
    SectionTitle("SELECT CLIPS")
    Text("Tick several clips, then delete, mute, change speed, duplicate or copy the selected clip's grade to all of them in one go.", color = Pro.Dim, fontSize = 11.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp).hintedScroll(horizontal = true)) {
        PillChip(if (picked.size == clips.size && clips.isNotEmpty()) "None" else "All", false) { onPickAll() }
        if (picked.isNotEmpty()) {
            PillChip("Delete (${picked.size})", false) { onBatch("delete") }
            PillChip("Mute / unmute", false) { onBatch("mute") }
            PillChip("0.5×", false) { onBatch("speed:0.5") }
            PillChip("1×", false) { onBatch("speed:1") }
            PillChip("2×", false) { onBatch("speed:2") }
            PillChip("Duplicate", false) { onBatch("duplicate") }
            PillChip("Copy grade of clip ${selectedIdx + 1}", false) { onBatch("grade") }
        }
    }
    clips.forEachIndexed { i, c ->
        val on = c.id in picked
        Row(
            Modifier.fillMaxWidth().padding(vertical = 1.dp).clip(RoundedCornerShape(6.dp)).background(if (on) Pro.Amber.copy(alpha = 0.16f) else Ed.Raised)
                .clickable { onPick(c.id) }.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (on) "☑" else "☐", color = if (on) Pro.Amber else Pro.Dim, fontSize = 16.sp, modifier = Modifier.width(26.dp))
            Text("${i + 1} · ${c.name}", color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(tc(c.lengthMs) + if (c.speed != 1f) " · ${c.speed}×" else "", style = Pro.Label.copy(fontSize = 10.sp))
        }
    }
}

/** Audio page extras: fit the music to the video and cut the clips on the beat. */
@Composable
private fun MusicTools(hasMusic: Boolean, onFit: () -> Unit, onAutoEdit: (Int) -> Unit) {
    SectionTitle("MUSIC TOOLS")
    if (!hasMusic) { Text("Add a music track to fit it to the video or cut your clips on its beat.", color = Pro.Dim, fontSize = 11.sp); return }
    Text(
        "FIT MUSIC TO VIDEO", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
        modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable { onFit() }.padding(10.dp),
    )
    Text("Shortens or lengthens the song so it ends exactly with your video, on its real ending — a middle part is cut out or repeated on the beat.", color = Pro.Dim, fontSize = 11.sp)
    var every by remember { mutableStateOf(1) }
    Text("Auto-edit to the beat", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
    Chips(listOf("Every beat", "Every 2", "Every 4", "Every 8"), listOf(1, 2, 4, 8).indexOf(every)) { every = listOf(1, 2, 4, 8)[it] }
    Text(
        "AUTO-EDIT MY CLIPS TO THE MUSIC", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
        modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable { onAutoEdit(every) }.padding(10.dp),
    )
    Text("Rebuilds the timeline from your clips with a cut on the beat, taking the next part of each clip in turn, until the music ends. Undo brings your edit back.", color = Pro.Dim, fontSize = 11.sp)
}

@Composable
private fun FilmLensPanel(
    c: Clip, onChange: ((Clip) -> Clip) -> Unit,
    clipNames: List<String>, selfIdx: Int, matchBusy: Int?, onMatch: (Int) -> Unit,
) {
    val f = c.film ?: FilmLook()
    fun film(t: (FilmLook) -> FilmLook) = onChange { cl -> cl.copy(film = t(cl.film ?: FilmLook()).takeIf { !it.isZero }) }
    SectionTitle("SKY REPLACEMENT") { film { it.copy(sky = -1) } }
    Chips(listOf("Off") + VideoSky.NAMES, f.sky + 1) { i -> film { it.copy(sky = i - 1) } }
    if (f.sky >= 0) {
        EdSlider("Horizon", f.horizon, 0.1f..0.9f, 0.45f, ::pct) { v -> film { it.copy(horizon = v) } }
        EdSlider("How much is sky", f.skyTol, 0f..1f, 0.5f, ::pct) { v -> film { it.copy(skyTol = v) } }
        EdSlider("Strength", f.skyAmount, 0f..1f, 1f, ::pct) { v -> film { it.copy(skyAmount = v) } }
        EdSlider("Match the light", f.skyLight, 0f..1f, 0.3f, ::pct) { v -> film { it.copy(skyLight = v) } }
    }
    Text("Swaps a blue or pale sky above the horizon line for a new one, frame by frame, in the preview and the export. Set Horizon just below where the sky ends; raise How much is sky if parts of the sky stay, lower it if clouds or bright walls change too. Best on clear or hazy skies.", color = Pro.Dim, fontSize = 11.sp)
    SectionTitle("SHOT MATCH")
    Text("Makes this clip's brightness and colour match another clip (as it looks with its grade). Pick the clip to match:", color = Pro.Dim, fontSize = 11.sp)
    if (matchBusy != null) Text("Matching… $matchBusy%", style = Pro.Label.copy(color = Pro.Amber), modifier = Modifier.padding(vertical = 6.dp))
    else if (clipNames.size < 2) Text("Add another clip to match to.", color = Pro.Dim, fontSize = 11.sp)
    else Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.hintedScroll(horizontal = true).padding(vertical = 4.dp)) {
        clipNames.forEachIndexed { i, n -> if (i != selfIdx) PillChip("${i + 1} · ${n.take(18)}", false) { onMatch(i) } }
    }
    SectionTitle("FILM EMULATION") { film { it.copy(halation = 0f, bloom = 0f, weave = 0f, grain = 0f, grainSize = 0.4f, preset = null) } }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.hintedScroll(horizontal = true).padding(vertical = 4.dp)) {
        FilmPresets.films.forEach { (n, p) -> PillChip(n, f.preset == n || (n == "Off" && f.copy(lensK = 0f, lensCa = 0f, lensVig = 0f, deflicker = 0f, preset = null) == FilmLook())) { film { FilmPresets.withFilm(it, n, p) } } }
    }
    EdSlider("Halation", f.halation, 0f..1f, 0f, ::pct) { v -> film { it.copy(halation = v) } }
    EdSlider("Bloom", f.bloom, 0f..1f, 0f, ::pct) { v -> film { it.copy(bloom = v) } }
    EdSlider("Grain", f.grain, 0f..1f, 0f, ::pct) { v -> film { it.copy(grain = v) } }
    EdSlider("Grain size", f.grainSize, 0f..1f, 0.4f, ::pct) { v -> film { it.copy(grainSize = v) } }
    EdSlider("Gate weave", f.weave, 0f..1f, 0f, ::pct) { v -> film { it.copy(weave = v) } }
    Text("Halation: the warm glow film gives bright lights. Gate weave: the gentle wobble of a film projector.", color = Pro.Dim, fontSize = 11.sp)
    SectionTitle("LENS CORRECTION") { film { it.copy(lensK = 0f, lensCa = 0f, lensVig = 0f) } }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.hintedScroll(horizontal = true).padding(vertical = 4.dp)) {
        FilmPresets.lenses.forEach { (n, p) -> PillChip(n, f.lensK == p.lensK && f.lensCa == p.lensCa && f.lensVig == p.lensVig) { film { FilmPresets.withLens(it, p) } } }
    }
    EdSlider("Distortion", f.lensK, -1f..1f, 0f, { if (it > 0f) "Barrel +${pct(it)}" else if (it < 0f) "Pincushion ${pct(-it)}" else "Off" }) { v -> film { it.copy(lensK = v) } }
    EdSlider("Colour fringing", f.lensCa, -1f..1f, 0f, { "%+.0f".format(it * 100) }) { v -> film { it.copy(lensCa = v) } }
    EdSlider("Vignette fix", f.lensVig, 0f..1f, 0f, ::pct) { v -> film { it.copy(lensVig = v) } }
    Text("Straightens bent lines from wide lenses (action cams, drones, ultra-wide phones), removes colour fringes at the edges and brightens dark corners. The picture is enlarged slightly so no edges show.", color = Pro.Dim, fontSize = 11.sp)
    SectionTitle("DEFLICKER") { film { it.copy(deflicker = 0f) } }
    EdSlider("Deflicker", f.deflicker, 0f..1f, 0f, { if (it <= 0f) "Off" else pct(it) }) { v -> film { it.copy(deflicker = v) } }
    Text("Evens out brightness flicker — timelapses and hyperlapses, LED / fluorescent lights, drones flying past the sun. Higher = smoother; scene cuts are left alone.", color = Pro.Dim, fontSize = 11.sp)
}

@Composable
private fun GeneratorTools(busy: Int?, onMake: (GenKind, Int, Int, Int) -> Unit, onCancel: () -> Unit) {
    var kind by remember { mutableStateOf(GenKind.BLACK) }
    var c1 by remember { mutableStateOf(0xFF101010.toInt()) }
    var c2 by remember { mutableStateOf(0xFF2A4C8C.toInt()) }
    var secs by remember { mutableStateOf(5f) }
    SectionTitle("GENERATORS")
    Text("Make a clip: black, a colour, a gradient, TV noise, colour bars or a film countdown leader. It's added after the selected clip, at your project's size.", color = Pro.Dim, fontSize = 11.sp)
    Chips(GenKind.entries.map { it.label }, kind.ordinal) { kind = GenKind.entries[it]; if (kind == GenKind.COUNTDOWN) secs = 8f }
    val swatches = listOf(0xFF000000, 0xFF101010, 0xFFFFFFFF, 0xFF2A4C8C, 0xFF0A84FF, 0xFFFFB300, 0xFFFF3B30, 0xFF34C759, 0xFF8E44AD, 0xFFFF7EB6).map { it.toInt() }
    @Composable fun swatchRow(sel: Int, on: (Int) -> Unit) = Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.hintedScroll(horizontal = true).padding(vertical = 4.dp)) {
        swatches.forEach { col -> Box(Modifier.size(28.dp).clip(CircleShape).background(Color(col)).border(2.dp, if (sel == col) Pro.Amber else Color.DarkGray, CircleShape).clickable { on(col) }) }
    }
    if (kind == GenKind.SOLID || kind == GenKind.GRADIENT) { Text(if (kind == GenKind.GRADIENT) "Top colour" else "Colour", style = Pro.Label); swatchRow(c1) { c1 = it } }
    if (kind == GenKind.GRADIENT) { Text("Bottom colour", style = Pro.Label); swatchRow(c2) { c2 = it } }
    if (kind == GenKind.COUNTDOWN) EdSlider("Count from", secs, 3f..10f, 8f, { "${it.toInt()}" }) { secs = it.toInt().toFloat() }
    else EdSlider("Length", secs, 1f..30f, 5f, { "${it.toInt()} s" }) { secs = it.toInt().toFloat() }
    if (busy != null) Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Making… $busy%", style = Pro.Label.copy(color = Pro.Amber), modifier = Modifier.padding(vertical = 8.dp))
        Text("CANCEL", style = Pro.Label.copy(color = Pro.Red, fontSize = 10.sp), modifier = Modifier.padding(start = 10.dp).clickable { onCancel() }.padding(6.dp))
    } else Text(
        "+ ADD ${kind.label.uppercase()}", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
        modifier = Modifier.padding(vertical = 6.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable { onMake(kind, c1, c2, secs.toInt()) }.padding(10.dp),
    )
}

/** Pen mask on the player: drag points, tap to add one (on the nearest edge), hold a point to remove it. */
@Composable
private fun PenEditOverlay(m: PhotoMask, srcMs: Long, onChange: (PhotoMask) -> Unit) {
    // Points as shown at this moment (a tracked mask is edited in its moved place, then the track is re-based).
    val shown = PenMask.pointsAt(m, srcMs)
    val cur = androidx.compose.runtime.rememberUpdatedState(m)
    val pts = androidx.compose.runtime.rememberUpdatedState(shown)
    fun commit(p: FloatArray) {
        val mm = cur.value
        // Editing a tracked mask: keep its track, but store the points where they are on the reference frame.
        val tr = mm.ptrack; val ref = mm.pref
        val stored = if (!tr.isNullOrEmpty() && ref != null) {
            val q = Layer(pin = tr).pinAt(srcMs)
            val a = Homography.squareTo(ref.toFloatArray()); val b = q?.let { Homography.squareTo(it) }
            val inv = if (a != null && b != null) Homography.invert(Homography.mul(b, Homography.invert(a) ?: a)) else null
            if (inv == null) p else FloatArray(p.size) { i -> val (u, v) = Homography.apply(inv, p[i - i % 2].toDouble(), p[i - i % 2 + 1].toDouble()); if (i % 2 == 0) u.toFloat() else v.toFloat() }
        } else p
        onChange(mm.copy(pts = stored.toList()))
    }
    var drag by remember { mutableStateOf(-1) }
    androidx.compose.foundation.Canvas(
        Modifier.fillMaxSize()
            .pointerInput(m.id) {
                detectTapGestures(
                    onLongPress = { o ->
                        val p = pts.value
                        val k = (0 until p.size / 2).minByOrNull { val dx = p[2 * it] * size.width - o.x; val dy = p[2 * it + 1] * size.height - o.y; dx * dx + dy * dy } ?: return@detectTapGestures
                        val dx = p[2 * k] * size.width - o.x; val dy = p[2 * k + 1] * size.height - o.y
                        if (dx * dx + dy * dy < 60f * 60f && p.size > 6) commit(p.toMutableList().also { it.removeAt(2 * k); it.removeAt(2 * k) }.toFloatArray())
                    },
                    onTap = { o ->
                        val p = pts.value
                        val n = p.size / 2
                        val x = o.x / size.width; val y = o.y / size.height
                        // Insert on the nearest edge.
                        var best = 0; var bd = Float.MAX_VALUE
                        for (i in 0 until n) {
                            val ax = p[2 * i]; val ay = p[2 * i + 1]; val bx = p[2 * ((i + 1) % n)]; val by = p[2 * ((i + 1) % n) + 1]
                            val ex = bx - ax; val ey = by - ay
                            val t = (((x - ax) * ex + (y - ay) * ey) / (ex * ex + ey * ey).coerceAtLeast(1e-6f)).coerceIn(0f, 1f)
                            val qx = ax + ex * t - x; val qy = ay + ey * t - y
                            val d = qx * qx + qy * qy
                            if (d < bd) { bd = d; best = i }
                        }
                        val l = p.toMutableList(); l.add(2 * best + 2, y); l.add(2 * best + 2, x)
                        commit(l.toFloatArray())
                    },
                )
            }
            .pointerInput(m.id + "d") {
                detectDragGestures(
                    onDragStart = { o ->
                        val p = pts.value
                        drag = (0 until p.size / 2).filter { val dx = p[2 * it] * size.width - o.x; val dy = p[2 * it + 1] * size.height - o.y; dx * dx + dy * dy < 70f * 70f }
                            .minByOrNull { val dx = p[2 * it] * size.width - o.x; val dy = p[2 * it + 1] * size.height - o.y; dx * dx + dy * dy } ?: -1
                    },
                    onDragEnd = { drag = -1 }, onDragCancel = { drag = -1 },
                ) { ch, d ->
                    val k = drag
                    if (k >= 0) {
                        ch.consume()
                        val p = pts.value.copyOf()
                        p[2 * k] = (p[2 * k] + d.x / size.width).coerceIn(-0.2f, 1.2f); p[2 * k + 1] = (p[2 * k + 1] + d.y / size.height).coerceIn(-0.2f, 1.2f)
                        commit(p)
                    }
                }
            },
    ) {
        if (shown.size < 6) return@Canvas
        val path = PenMask.path(shown, size.width, size.height)
        drawIntoCanvas { cv ->
            cv.nativeCanvas.drawPath(path, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.STROKE; strokeWidth = 5f; color = 0x88000000.toInt() })
            cv.nativeCanvas.drawPath(path, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.STROKE; strokeWidth = 2.5f; color = 0xFFFFB300.toInt() })
        }
        for (i in 0 until shown.size / 2) {
            val c = Offset(shown[2 * i] * size.width, shown[2 * i + 1] * size.height)
            drawCircle(Color.Black.copy(alpha = 0.5f), 13f, c)
            drawCircle(if (i == drag) Color.White else Pro.Amber, 9f, c)
        }
    }
}

/** Like coerceIn, but when the range is empty (a clip shorter than the minimum, or a file that reports a shorter length than the clip uses) nothing moves. */
private fun Long.safeIn(lo: Long, hi: Long): Long = if (hi >= lo) coerceIn(lo, hi) else 0L

/** Delete clip [index]: close the gap or leave one (its own function: the editor screen is already very large). */
@Composable
private fun DeleteClipDialog(index: Int, onClose: () -> Unit, onDelete: (leaveGap: Boolean) -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        containerColor = Pro.PanelSolid,
        title = { Text("Delete clip ${index + 1}?", color = Color.White) },
        text = { Text("Close the gap: the clips after it move left. Leave a gap: black of the same length takes its place, so everything after it stays where it is.", color = Pro.Dim, fontSize = 13.sp) },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("LEAVE GAP", style = Pro.Label.copy(color = Color.White), modifier = Modifier.clickable { onDelete(true) }.padding(10.dp))
                Text("CLOSE GAP", style = Pro.Label.copy(color = Pro.Red), modifier = Modifier.clickable { onDelete(false) }.padding(10.dp))
            }
        },
        dismissButton = { Text("CANCEL", style = Pro.Label.copy(color = Pro.Dim), modifier = Modifier.clickable(onClick = onClose).padding(10.dp)) },
    )
}

/** Less-used editor state, kept together (one slot in the editor's main function instead of many). */
class WorkspaceExtra {
    // Render cache, AI dubbing, highlight reel options (kept here to keep the workspace function small).
    val cacheReady = androidx.compose.runtime.mutableStateMapOf<String, android.net.Uri>()
    val cacheProgress = androidx.compose.runtime.mutableStateMapOf<String, Int>()
    var cacheCancel by androidx.compose.runtime.mutableStateOf<(() -> Unit)?>(null)
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun cachedFor(c: Clip): android.net.Uri? = if (RenderCache.heavy(c)) cacheReady[RenderCache.key(c)] else null
    /** The clip as the preview should treat it: with a render cache, its baked effects aren't drawn again. */
    fun pvc(c: Clip): Clip = if (cachedFor(c) != null) c.copy(bg = null, depth = null) else c
    var dubStatus by androidx.compose.runtime.mutableStateOf<String?>(null)
    var dubJob by androidx.compose.runtime.mutableStateOf<kotlinx.coroutines.Job?>(null)
    var hlBeat by androidx.compose.runtime.mutableStateOf(false)
    var ttsProgress by androidx.compose.runtime.mutableStateOf<Int?>(null)
    var ttsStatus by androidx.compose.runtime.mutableStateOf<String?>(null)
    var sepStatus by androidx.compose.runtime.mutableStateOf<String?>(null)
    var ttsJob by androidx.compose.runtime.mutableStateOf<kotlinx.coroutines.Job?>(null)
    var voiceRec by androidx.compose.runtime.mutableStateOf<android.media.MediaRecorder?>(null)
    var voiceFile by androidx.compose.runtime.mutableStateOf<java.io.File?>(null)
    var voiceStart by androidx.compose.runtime.mutableStateOf<Long>(0L)
    var voiceMs by androidx.compose.runtime.mutableStateOf<Long>(0L)
    var mutedBeforeVoice by androidx.compose.runtime.mutableStateOf<Boolean>(false)
    var depthProgress by androidx.compose.runtime.mutableStateOf<Int?>(null)
    var depthStatus by androidx.compose.runtime.mutableStateOf<String?>(null)
    var depthJob by androidx.compose.runtime.mutableStateOf<kotlinx.coroutines.Job?>(null)
    var penTrackingId by androidx.compose.runtime.mutableStateOf<String?>(null)
    var penStop by androidx.compose.runtime.mutableStateOf<Boolean>(false)
    var pinTrackingId by androidx.compose.runtime.mutableStateOf<String?>(null)
    var pinStop by androidx.compose.runtime.mutableStateOf<Boolean>(false)
    var flowProgress by androidx.compose.runtime.mutableStateOf<Int>(-1)
    var flowJob by androidx.compose.runtime.mutableStateOf<kotlinx.coroutines.Job?>(null)
    var tModel by androidx.compose.runtime.mutableStateOf<SpeechModel>(SpeechModel.BASE)
    var tLang by androidx.compose.runtime.mutableStateOf<String?>(null)
    var tStatus by androidx.compose.runtime.mutableStateOf<String?>(null)
    var tJob by androidx.compose.runtime.mutableStateOf<kotlinx.coroutines.Job?>(null)
    var tDl by androidx.compose.runtime.mutableStateOf<Int>(-1)
    var tDlCancel by androidx.compose.runtime.mutableStateOf<Boolean>(false)
    var playSince by androidx.compose.runtime.mutableStateOf<Long>(0L)
    var stallPos by androidx.compose.runtime.mutableStateOf<Long>(-1L)
    var stallSince by androidx.compose.runtime.mutableStateOf<Long>(0L)
    var stallTries by androidx.compose.runtime.mutableStateOf<Int>(0)
    val tWords = androidx.compose.runtime.mutableStateListOf<TWord>()
}

/** Media → TIMELINES: the open timeline (renamable) and the project's other timelines. */
@Composable
private fun TimelinesSection(
    name: String, others: List<String>, onRename: (String) -> Unit, onOpen: (Int) -> Unit,
    onNew: () -> Unit, onDuplicate: () -> Unit, onDelete: (Int) -> Unit,
) {
    var confirm by remember { mutableStateOf(-1) }
    SectionTitle("TIMELINES")
    var edit by remember(name) { mutableStateOf(name) }
    androidx.compose.material3.OutlinedTextField(
        value = edit, onValueChange = { edit = it.take(40); onRename(edit) }, singleLine = true,
        label = { Text("Open timeline", fontSize = 11.sp) },
        textStyle = androidx.compose.ui.text.TextStyle(color = com.dronedirector.veyrocutpro.ui.Pro.Fg, fontSize = 13.sp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    )
    others.forEachIndexed { i, n ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(n, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(Ed.Raised).clickable { onOpen(i) }.padding(10.dp))
            Text(if (confirm == i) "DELETE?" else "✕", color = Pro.Red, fontSize = 12.sp, modifier = Modifier.clickable {
                if (confirm == i) { confirm = -1; onDelete(i) } else confirm = i
            }.padding(10.dp))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        Text("NEW TIMELINE", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Ed.Raised).clickable(onClick = onNew).padding(10.dp))
        Text("DUPLICATE", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Ed.Raised).clickable(onClick = onDuplicate).padding(10.dp))
    }
    Text("Keep several cuts in one project — a long and a short version, or one per platform. Tap a timeline to open it; the media strip is shared. Saved with the project (an archive .zip holds the open timeline only).", color = Pro.Dim, fontSize = 11.sp)
}

/** Proxy page → RENDER CACHE: clips with heavy effects, rendered once for smooth playback. */
@Composable
private fun RenderCacheSection(items: List<Triple<String, Boolean, Int?>>, running: Boolean, onRenderAll: () -> Unit, onStop: () -> Unit, onClear: () -> Unit) {
    SectionTitle("RENDER CACHE")
    Text("AI noise reduction, background cut-out and AI depth effects are heavy to play live. RENDER CACHE draws them once into a preview file so those clips play smoothly. The grade and everything else stay live; the export always uses the originals.", color = Pro.Dim, fontSize = 11.sp)
    if (items.isEmpty()) { Text("No clips with heavy effects in this edit.", color = Pro.Dim, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp)); return }
    items.forEach { (name, ready, prog) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(name.take(36), color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(when { ready -> "CACHED ✓"; prog != null -> "$prog%"; else -> "live" }, style = Pro.Label.copy(color = if (ready) Pro.Amber else Pro.Dim, fontSize = 10.sp))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        if (running) Text("STOP", style = Pro.Label.copy(color = Pro.Red, fontSize = 11.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Ed.Raised).clickable(onClick = onStop).padding(10.dp))
        else Text("RENDER CACHE", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Ed.Raised).clickable(onClick = onRenderAll).padding(10.dp))
        Text("DELETE CACHE", style = Pro.Label.copy(color = Pro.Dim, fontSize = 11.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Ed.Raised).clickable(onClick = onClear).padding(10.dp))
    }
    Text("Changing one of those effects makes the clip play live again until you render it again.", color = Pro.Dim, fontSize = 11.sp)
}

/** The video library or the import screen over the editor; [onAdd] gets what's picked or imported. */
@Composable
internal fun LibraryOverlay(import: Boolean, onClose: () -> Unit, onAdd: (List<Uri>) -> Unit) {
    var showImport by remember { mutableStateOf(import) }
    // Videos, or photos (they go in as stills).
    var photos by remember { mutableStateOf(false) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        com.dronedirector.veyrocutpro.ui.ForceDark {
            Box(Modifier.fillMaxSize().background(Ed.Bg)) {
                if (showImport) ProImportScreen(video = true, onClose = { if (import) onClose() else showImport = false }) { res ->
                    // Videos and photos (photos go in as stills).
                    val v = res.map { it.first }
                    if (v.isNotEmpty()) onAdd(v)
                }
                else Column(Modifier.fillMaxSize()) {
                    Row(Modifier.padding(start = 12.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PillChip("Videos", !photos) { photos = false }
                        PillChip("Photos", photos) { photos = true }
                    }
                    Box(Modifier.weight(1f)) {
                        if (photos) LibraryScreen(onBack = onClose, onEdit = onAdd, onVirtualCopy = {}, pickLabel = "+ Add to project")
                        else VideoLibraryScreen(onBack = onClose, onAdd = onAdd, onImport = { showImport = true })
                    }
                }
            }
        }
    }
}

/** Looks for a moved file by its name among the phone's videos and photos. */
internal object MissingMedia {
    fun findByName(context: android.content.Context, name: String): Uri? {
        if (name.isBlank()) return null
        val sets = listOf(
            android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        )
        for (base in sets) try {
            context.contentResolver.query(base, arrayOf(android.provider.MediaStore.MediaColumns._ID), "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} = ?", arrayOf(name), null)?.use { c ->
                if (c.moveToFirst()) {
                    val u = android.content.ContentUris.withAppendedId(base, c.getLong(0))
                    if (ProjectArchive.readable(context, u)) return u
                }
            }
        } catch (_: Throwable) {}
        return null
    }
}
