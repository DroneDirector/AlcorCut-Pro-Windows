package com.dronedirector.veyrocutpro.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import com.dronedirector.veyrocutpro.ui.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.ui.Pro
import com.dronedirector.veyrocutpro.ui.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The video editor's File menu (file icon in the top bar): projects, saving, capture, import,
 * new items, team projects and dependencies, each group folding out. What each item does is
 * given by the editor in [FileActions]. Own code.
 */
class FileActions {
    /** Video editor (all groups) or photo editor (projects, saving, capture, import). */
    var video = true
    var hasContent: () -> Boolean = { false }
    var projectName: () -> String = { "" }
    var isTeam: () -> Boolean = { false }
    var teamReadOnly: () -> Boolean = { false }
    var teamLabel: () -> String = { "" }
    var openProjects: () -> List<Pair<String, String>> = { emptyList() }
    var activeKey: () -> String? = { null }
    var switchTo: (String) -> Unit = {}

    var newProject: () -> Unit = {}
    var newTeamProject: (TeamPlace, String) -> Unit = { _, _ -> }
    var openProject: () -> Unit = {}
    var openRecent: (String) -> Unit = {}
    var openTeam: (TeamPlace, String, Boolean) -> Unit = { _, _, _ -> }
    var shareAsTeam: (TeamPlace, String) -> Unit = { _, _ -> }
    var leaveTeam: () -> Unit = {}
    var projectSettings: () -> Unit = {}

    var close: () -> Unit = {}
    var closeOthers: () -> Unit = {}
    var closeAll: () -> Unit = {}

    var save: () -> Unit = {}
    var saveAs: () -> Unit = {}
    var saveCopy: (String) -> Unit = {}
    var canRevert: () -> Boolean = { false }
    var revert: () -> Unit = {}

    var capture: () -> Unit = {}
    var importGallery: () -> Unit = {}
    var importFiles: () -> Unit = {}
    var importFolder: () -> Unit = {}
    var importAudio: () -> Unit = {}
    var importNas: () -> Unit = {}
    var importArchive: () -> Unit = {}

    var newSequence: (String, Float, Int) -> Unit = { _, _, _ -> }
    var mediaClips: () -> List<Clip> = { emptyList() }
    var sequenceFromClip: (Clip) -> Unit = {}
    var newBin: (String) -> Unit = {}
    var offlineFile: (String, Int, Float) -> Unit = { _, _, _ -> }
    var hasTranscript: () -> Boolean = { false }
    var captionsAi: () -> Unit = {}
    var captionsFromTranscript: () -> Unit = {}
    var captionsImport: () -> Unit = {}
    var captionEmpty: () -> Unit = {}
    var transcript: () -> Unit = {}
    var title: () -> Unit = {}

    var collectFiles: () -> Unit = {}
    var makeOffline: (Boolean) -> Unit = {}
    var linkMedia: () -> Unit = {}
    var selectedOffline: () -> Boolean = { false }

    var exit: () -> Unit = {}
}

/** What the File menu has open (also opened from keyboard shortcuts). */
class FileMenuState {
    var menu by mutableStateOf(false)
    /** "new" / "team" — confirm before starting one. */
    var confirmNew by mutableStateOf<String?>(null)
    /** Team folder picker: "new", "share" or "open". */
    var teamWhere by mutableStateOf<String?>(null)
    var teamName by mutableStateOf(false)
    var sequence by mutableStateOf(false)
    var fromClip by mutableStateOf(false)
    var bin by mutableStateOf(false)
    var offline by mutableStateOf(false)
    var captions by mutableStateOf(false)
    var saveCopy by mutableStateOf(false)
    var revert by mutableStateOf(false)
    var makeOffline by mutableStateOf(false)
    var exit by mutableStateOf(false)
}

private val SHAPES = listOf("Same as clips" to 0f, "16:9" to 16f / 9f, "9:16" to 9f / 16f, "1:1" to 1f, "4:5" to 0.8f, "21:9" to 21f / 9f, "4:3" to 4f / 3f)
private val RATES = listOf(0, 24, 25, 30, 50, 60)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FileMenuButton(fa: FileActions, st: FileMenuState) {
    val context = LocalContext.current
    var group by remember { mutableStateOf<String?>("project") }
    Box {
        TopIcon(Icons.Filled.InsertDriveFile, "File") { st.menu = true }
        com.dronedirector.veyrocutpro.ui.ScrollMenu(st.menu, onDismissRequest = { st.menu = false }, width = 300.dp) {
            fun go(a: () -> Unit) { st.menu = false; a() }
            if (fa.isTeam()) Text(
                "Team project · " + fa.teamLabel() + if (fa.teamReadOnly()) " · read-only" else "",
                color = Pro.Amber, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            )
            @Composable
            fun Group(id: String, title: String, content: @Composable () -> Unit) {
                val open = group == id
                Row(
                    Modifier.fillMaxWidth().clickable { group = if (open) null else id }.padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title.uppercase(), style = Pro.Label.copy(color = if (open) Pro.Amber else Color.White, fontSize = 12.sp), modifier = Modifier.weight(1f))
                    Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = Pro.Dim, modifier = Modifier.size(18.dp))
                }
                if (open) {
                    val bring = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
                    Column(Modifier.padding(start = 10.dp).bringIntoViewRequester(bring)) { content() }
                    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); try { bring.bringIntoView() } catch (_: Throwable) {} }
                }
            }
            @Composable
            fun Item(label: String, enabled: Boolean = true, onClick: () -> Unit) {
                Text(
                    label, color = if (enabled) Color.White else Pro.Dim, fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { go(onClick) }.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }

            Group("project", "Project") {
                Item("New project") { if (fa.hasContent()) st.confirmNew = "new" else fa.newProject() }
                if (fa.video) Item("New team project…") { if (fa.hasContent()) st.confirmNew = "team" else st.teamWhere = "new" }
                Item("Open project…") { fa.openProject() }
                var recentOpen by remember { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth().clickable { recentOpen = !recentOpen }.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Open recent", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Icon(if (recentOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = Pro.Dim, modifier = Modifier.size(16.dp))
                }
                if (recentOpen) {
                    val recent by produceState(emptyList<ProjectEntry>()) { value = withContext(Dispatchers.IO) { try { ProjectStore.list(context, fa.video).take(8) } catch (_: Throwable) { emptyList() } } }
                    if (recent.isEmpty()) Text("No saved projects yet", color = Pro.Dim, fontSize = 12.sp, modifier = Modifier.padding(start = 26.dp, top = 2.dp, bottom = 6.dp))
                    for (p in recent) Text(p.name, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().clickable { go { fa.openRecent(p.id) } }.padding(start = 26.dp, end = 14.dp, top = 6.dp, bottom = 6.dp))
                }
                if (fa.video) Item("Open team project…") { st.teamWhere = "open" }
                Item("Project settings…") { fa.projectSettings() }
            }
            val open = fa.openProjects()
            if (fa.video) Group("open", "Open projects" + if (open.size > 1) " (${open.size})" else "") {
                for ((key, name) in open) {
                    val on = key == fa.activeKey()
                    Text((if (on) "● " else "    ") + name.ifBlank { "Untitled" }, color = if (on) Pro.Amber else Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().clickable(enabled = !on) { go { fa.switchTo(key) } }.padding(horizontal = 14.dp, vertical = 8.dp))
                }
                HorizontalDivider(color = com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.1f))
                Item("Close project") { fa.close() }
                Item("Close other projects", enabled = open.size > 1) { fa.closeOthers() }
                Item("Close all projects") { fa.closeAll() }
            }
            Group("save", "Save") {
                Item(if (fa.isTeam()) "Save project changes (to the team)" else "Save project changes") { fa.save() }
                Item("Save as…") { fa.saveAs() }
                Item("Save a copy…") { st.saveCopy = true }
                Item("Revert to saved", enabled = fa.canRevert()) { st.revert = true }
            }
            Group("capture", "Capture") {
                Item(if (fa.video) "Open Capture — shots go to the media strip" else "Open Capture — photos go to the filmstrip") { fa.capture() }
            }
            Group("import", "Import") {
                Item("Gallery") { fa.importGallery() }
                Item(if (fa.video) "Files (MKV, MOV…)" else "Files (RAW, TIFF…)") { fa.importFiles() }
                if (fa.video) Item("A whole folder") { fa.importFolder() }
                if (fa.video) Item("Audio files") { fa.importAudio() }
                Item("Network (NAS)") { fa.importNas() }
                if (fa.video) Item("Project archive (.zip)") { fa.importArchive() }
            }
            if (fa.video) Group("new", "New") {
                Item("Sequence…") { st.sequence = true }
                Item("Sequence from clip…") { st.fromClip = true }
                Item("Bin…") { st.bin = true }
                Item("Offline file…") { st.offline = true }
                Item("Captions…") { st.captions = true }
                Item("Transcript") { fa.transcript() }
                Item("Title") { fa.title() }
            }
            if (fa.video) Group("team", "Team") {
                Item("Open team project…") { st.teamWhere = "open" }
                Item("Share this project as a team project…", enabled = !fa.isTeam()) { st.teamWhere = "share" }
                Item("Let go of the team project", enabled = fa.isTeam()) { fa.leaveTeam() }
                Item("Your name in team projects…") { st.teamName = true }
            }
            if (fa.video) Group("deps", "Dependencies") {
                Item("Collect files…") { fa.collectFiles() }
                Item("Make offline…") { st.makeOffline = true }
                Item("Link media…", enabled = fa.selectedOffline()) { fa.linkMedia() }
            }
            HorizontalDivider(color = com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.1f))
            Text("EXIT", style = Pro.Label.copy(color = Pro.Red, fontSize = 12.sp),
                modifier = Modifier.fillMaxWidth().clickable { go { st.exit = true } }.padding(horizontal = 14.dp, vertical = 11.dp))
        }
    }
    FileMenuDialogs(fa, st)
}

@Composable
private fun FileMenuDialogs(fa: FileActions, st: FileMenuState) {
    val context = LocalContext.current
    st.confirmNew?.let { kind ->
        AlertDialog(
            onDismissRequest = { st.confirmNew = null }, containerColor = Pro.PanelSolid,
            title = { Text(if (kind == "team") "Are you sure you want to start a new team project?" else "Are you sure you want to start a new project?", color = Color.White) },
            text = { Text(if (fa.video) "\"${fa.projectName().ifBlank { "Untitled" }}\" stays open — switch back to it under File → Open projects. Save it first if you want to keep your changes." else "The photo that's open now closes (its edit is kept in the catalog). Save it as a project first if you want a named copy.", color = Pro.Dim) },
            confirmButton = { TextButton(onClick = { st.confirmNew = null; if (kind == "team") st.teamWhere = "new" else fa.newProject() }) { Text("Start", color = Pro.Amber) } },
            dismissButton = { TextButton(onClick = { st.confirmNew = null }) { Text("Cancel", color = Color.White) } },
        )
    }
    st.teamWhere?.let { mode -> TeamWhereDialog(mode, fa, onClose = { st.teamWhere = null }, askName = { st.teamName = true }) }
    if (st.teamName) NameDialog("Your name in team projects", "Shown to your team when you have a project open or save it.", Team.userName(context), "Save") { n ->
        st.teamName = false; if (n != null) Team.setUserName(context, n)
    }
    if (st.sequence) SequenceDialog(onDismiss = { st.sequence = false }) { n, a, f -> st.sequence = false; fa.newSequence(n, a, f) }
    if (st.fromClip) {
        val list = fa.mediaClips()
        AlertDialog(
            onDismissRequest = { st.fromClip = false }, containerColor = Pro.PanelSolid,
            title = { Text("Sequence from clip", color = Color.White) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text("A new timeline with this clip, set to its shape and frame rate.", color = Pro.Dim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                    if (list.isEmpty()) Text("Add clips to the project first.", color = Pro.Dim)
                    for (c in list) Text(
                        c.name + "  ·  ${c.width}×${c.height}", color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised)
                            .clickable { st.fromClip = false; fa.sequenceFromClip(c) }.padding(10.dp),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { st.fromClip = false }) { Text("Cancel", color = Color.White) } },
        )
    }
    if (st.bin) NameDialog("New bin", "A bin keeps media together on the Media page.", "", "Create") { n -> st.bin = false; if (!n.isNullOrBlank()) fa.newBin(n.trim()) }
    if (st.offline) OfflineFileDialog(onDismiss = { st.offline = false }) { n, s, a -> st.offline = false; fa.offlineFile(n, s, a) }
    if (st.captions) AlertDialog(
        onDismissRequest = { st.captions = false }, containerColor = Pro.PanelSolid,
        title = { Text("Captions", color = Color.White) },
        text = {
            Column {
                @Composable
                fun Opt(t: String, d: String, enabled: Boolean = true, a: () -> Unit) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp)).background(Ed.Raised)
                        .clickable(enabled = enabled) { st.captions = false; a() }.padding(12.dp)) {
                        Text(t, color = if (enabled) Color.White else Pro.Dim, fontSize = 14.sp)
                        Text(d, color = Pro.Dim, fontSize = 11.sp)
                    }
                }
                Opt("From the transcript", if (fa.hasTranscript()) "Captions from the words transcribed on the phone (Text page) — no internet needed" else "Transcribe the edit first (File → New → Transcript)", fa.hasTranscript()) { fa.captionsFromTranscript() }
                Opt("AI captions (Cloud AI)", "The speech turned into captions by your fal.ai account") { fa.captionsAi() }
                Opt("Import a subtitle file", ".srt or .vtt") { fa.captionsImport() }
                Opt("Empty caption at the playhead", "Type it yourself") { fa.captionEmpty() }
            }
        },
        confirmButton = { TextButton(onClick = { st.captions = false }) { Text("Cancel", color = Color.White) } },
    )
    if (st.saveCopy) NameDialog("Save a copy", "Saves a copy under a new name. You keep working on the open project.", fa.projectName().ifBlank { "Untitled" } + " copy", "Save copy") { n ->
        st.saveCopy = false; if (!n.isNullOrBlank()) fa.saveCopy(n.trim())
    }
    if (st.revert) AlertDialog(
        onDismissRequest = { st.revert = false }, containerColor = Pro.PanelSolid,
        title = { Text("Revert to the saved project?", color = Color.White) },
        text = { Text("Every change since the last save is thrown away and the project opens as it was last saved" + (if (fa.isTeam()) " in the team folder." else "."), color = Pro.Dim) },
        confirmButton = { TextButton(onClick = { st.revert = false; fa.revert() }) { Text("Revert", color = Pro.Red) } },
        dismissButton = { TextButton(onClick = { st.revert = false }) { Text("Cancel", color = Color.White) } },
    )
    if (st.makeOffline) AlertDialog(
        onDismissRequest = { st.makeOffline = false }, containerColor = Pro.PanelSolid,
        title = { Text("Make offline", color = Color.White) },
        text = { Text("The selected clip's media is unlinked and a black OFFLINE stand-in takes its place — its trims and settings are kept. Link media brings the file back (or another one).", color = Pro.Dim) },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = { st.makeOffline = false; fa.makeOffline(false) }) { Text("This clip", color = Pro.Amber) }
                TextButton(onClick = { st.makeOffline = false; fa.makeOffline(true) }) { Text("Every use of this file", color = Pro.Amber) }
            }
        },
        dismissButton = { TextButton(onClick = { st.makeOffline = false }) { Text("Cancel", color = Color.White) } },
    )
    if (st.exit) AlertDialog(
        onDismissRequest = { st.exit = false }, containerColor = Pro.PanelSolid,
        title = { Text("Exit AlcorCut Pro?", color = Color.White) },
        text = { Text("Unsaved changes in open projects are asked about first.", color = Pro.Dim) },
        confirmButton = { TextButton(onClick = { st.exit = false; fa.exit() }) { Text("Exit", color = Pro.Red) } },
        dismissButton = { TextButton(onClick = { st.exit = false }) { Text("Cancel", color = Color.White) } },
    )
}

/** Where a team project is (or goes): folders used before, a folder on the phone, or a NAS. */
@Composable
private fun TeamWhereDialog(mode: String, fa: FileActions, onClose: () -> Unit, askName: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val unlock = rememberPhoneUnlock()
    var place by remember { mutableStateOf<TeamPlace?>(null) }
    var nas by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var projects by remember { mutableStateOf<List<String>?>(null) }
    var held by remember { mutableStateOf<Pair<String, TeamLock>?>(null) }
    var name by remember { mutableStateOf(if (mode == "share") fa.projectName() else "") }
    var me by remember { mutableStateOf(Team.userName(context)) }
    var handed by remember { mutableStateOf(false) }
    // Cancelled: a NAS opened here is closed again (a used one now belongs to the project).
    fun cancel() { if (!handed) (place as? NasPlace)?.let { p -> kotlin.concurrent.thread { p.close() } }; onClose() }

    fun use(p: TeamPlace) {
        place = p; Team.remember(context, p.key)
        if (mode == "open") {
            busy = "Looking in the folder…"
            scope.launch {
                projects = withContext(Dispatchers.IO) { try { Team.projects(p) } catch (_: Throwable) { emptyList() } }
                busy = null
            }
        }
    }
    val tree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u: Uri? ->
        if (u != null) {
            try { context.contentResolver.takePersistableUriPermission(u, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: Throwable) {}
            use(FolderPlace(context, u))
        }
    }
    fun openRecent(key: String) {
        when {
            key.startsWith("folder|") -> use(FolderPlace(context, Uri.parse(key.removePrefix("folder|"))))
            key.startsWith("nas|") -> {
                val parts = key.removePrefix("nas|").split('|')
                val host = parts.getOrNull(0) ?: return; val share = parts.getOrNull(1) ?: return; val folder = parts.getOrNull(2) ?: ""
                fun connect() {
                    busy = "Connecting to $host…"
                    scope.launch {
                        val r = withContext(Dispatchers.IO) { Nas.open(context, host) }
                        busy = null
                        when (r) {
                            is Nas.Opened.Ok -> use(NasPlace(r.link, share, folder))
                            is Nas.Opened.NeedsSignIn -> { error = "The NAS asks for a sign-in — choose it under \"A NAS on the network\" to sign in."; nas = true }
                            is Nas.Opened.Failed -> error = r.why
                        }
                    }
                }
                PhoneLock.load(context)
                if (NasSignIns.has(context, host) && PhoneLock.nasLock && PhoneLock.isSet(context)) unlock("Unlock the sign-in for $host") { ok -> if (ok) connect() else error = "Not unlocked — the saved sign-in wasn't used." }
                else connect()
            }
        }
    }

    if (nas) NasBrowserDialog(NasKinds.FOLDERS, onDismiss = { nas = false }, onPicked = {}, pickFolder = { link, share, path -> use(NasPlace(link, share, path)) })

    AlertDialog(
        onDismissRequest = { if (busy == null) cancel() },
        containerColor = Pro.PanelSolid,
        title = { Text(when (mode) { "new" -> "New team project"; "share" -> "Share as a team project"; else -> "Open team project" }, color = Color.White) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                error?.let { Text(it, color = Pro.Red, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp)) }
                busy?.let {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = Pro.Amber, strokeWidth = 2.dp)
                        Text(it, color = Pro.Dim, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                val p = place
                if (p == null) {
                    Text("A team project lives in a shared folder that everyone on the team can reach — a folder on your NAS, or a shared / cloud folder on the phone. Who has it open is shown to the others, so nobody overwrites anybody.", color = Pro.Dim, fontSize = 12.sp)
                    val rec = remember { Team.recent(context) }
                    if (rec.isNotEmpty()) {
                        Text("USED BEFORE", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                        for (k in rec) Text(Team.describe(k), color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable { error = null; openRecent(k) }.padding(10.dp))
                    }
                    Text("CHOOSE THE FOLDER", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                    Text("A NAS on the network", color = Color.White, fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable { error = null; nas = true }.padding(12.dp))
                    Text("A folder on the phone, SD card or cloud drive", color = Color.White, fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable { error = null; try { tree.launch(null) } catch (_: Throwable) {} }.padding(12.dp))
                } else if (mode == "open") {
                    Text(p.label, color = Pro.Dim, fontSize = 12.sp)
                    val list = projects
                    if (list != null && list.isEmpty()) Text("No team projects in this folder yet.", color = Pro.Dim, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    list?.forEach { n ->
                        Text(n, color = Color.White, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable {
                            busy = "Checking who has it open…"
                            scope.launch {
                                val other = withContext(Dispatchers.IO) { Team.lock(p, n)?.takeIf { it.device != Team.deviceId(context) } }
                                busy = null
                                if (other != null && System.currentTimeMillis() - other.at < Team.STALE_MS) held = n to other
                                else { handed = true; onClose(); fa.openTeam(p, n, false) }
                            }
                        }.padding(12.dp))
                    }
                } else {
                    Text(p.label, color = Pro.Dim, fontSize = 12.sp)
                    OutlinedTextField(name, { name = it.take(60) }, singleLine = true, label = { Text("Project name") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = fieldColors())
                    OutlinedTextField(me, { me = it.take(40) }, singleLine = true, label = { Text("Your name (shown to the team)") }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), colors = fieldColors())
                    if (mode == "share") Text("Every file the edit uses is copied into the team folder once, so the others can open it.", color = Pro.Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        },
        confirmButton = {
            val p = place
            if (p != null && mode != "open") TextButton(enabled = name.isNotBlank() && busy == null, onClick = {
                if (me.isNotBlank()) Team.setUserName(context, me)
                val n = name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
                busy = "Checking the folder…"
                scope.launch {
                    val exists = withContext(Dispatchers.IO) { try { p.size(n + Team.EXT) >= 0 } catch (_: Throwable) { false } }
                    busy = null
                    if (exists) error = "There's already a team project called \"$n\" in this folder — open it, or choose another name."
                    else { handed = true; onClose(); if (mode == "new") fa.newTeamProject(p, n) else fa.shareAsTeam(p, n) }
                }
            }) { Text(if (mode == "new") "Create" else "Share", color = Pro.Amber) }
        },
        dismissButton = { TextButton(onClick = { cancel() }) { Text("Cancel", color = Color.White) } },
    )
    held?.let { (n, l) ->
        val p = place ?: return@let
        AlertDialog(
            onDismissRequest = { held = null }, containerColor = Pro.PanelSolid,
            title = { Text("${l.user} has \"$n\" open", color = Color.White) },
            text = { Text("Since " + java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(l.at)) + ". You can open it read-only (look, play, export — but not save over it), or wait until they close it.", color = Pro.Dim) },
            confirmButton = { TextButton(onClick = { held = null; handed = true; onClose(); fa.openTeam(p, n, true) }) { Text("Open read-only", color = Pro.Amber) } },
            dismissButton = { TextButton(onClick = { held = null }) { Text("Cancel", color = Color.White) } },
        )
    }
}

@Composable
private fun SequenceDialog(onDismiss: () -> Unit, onCreate: (String, Float, Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var shape by remember { mutableIntStateOf(0) }
    var rate by remember { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Pro.PanelSolid,
        title = { Text("New sequence", color = Color.White) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("A new, empty timeline in this project (the open one is kept under Media → TIMELINES).", color = Pro.Dim, fontSize = 12.sp)
                OutlinedTextField(name, { name = it.take(60) }, singleLine = true, label = { Text("Name") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = fieldColors())
                Text("SHAPE", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(top = 10.dp))
                Chips(SHAPES.map { it.first }, shape) { shape = it }
                Text("FRAME RATE", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(top = 8.dp))
                Chips(RATES.map { if (it == 0) "Same as clips" else "$it fps" }, rate) { rate = it }
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name.trim(), SHAPES[shape].second, RATES[rate]) }) { Text("Create", color = Pro.Amber) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White) } },
    )
}

@Composable
private fun OfflineFileDialog(onDismiss: () -> Unit, onCreate: (String, Int, Float) -> Unit) {
    var name by remember { mutableStateOf("") }
    var seconds by remember { mutableFloatStateOf(10f) }
    var shape by remember { mutableIntStateOf(1) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Pro.PanelSolid,
        title = { Text("New offline file", color = Color.White) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("A stand-in for media you don't have yet (a shot still to be filmed, a file on its way). It's black and marked OFFLINE on the timeline; Link media swaps in the real file later and keeps your trims.", color = Pro.Dim, fontSize = 12.sp)
                OutlinedTextField(name, { name = it.take(60) }, singleLine = true, label = { Text("Name (e.g. the missing shot)") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = fieldColors())
                Text("Length: ${seconds.toInt()} s", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                Slider(seconds, { seconds = it }, valueRange = 1f..300f, colors = SliderDefaults.colors(thumbColor = Pro.Amber, activeTrackColor = Pro.Amber))
                Text("SHAPE", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp))
                Chips(SHAPES.drop(1).map { it.first }, shape - 1) { shape = it + 1 }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onCreate(name.trim(), seconds.toInt(), SHAPES[shape].second) }) { Text("Create", color = Pro.Amber) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White) } },
    )
}

/** A name to type; null = cancelled. */
@Composable
fun NameDialog(title: String, info: String, start: String, ok: String, onDone: (String?) -> Unit) {
    var v by remember { mutableStateOf(start) }
    AlertDialog(
        onDismissRequest = { onDone(null) }, containerColor = Pro.PanelSolid,
        title = { Text(title, color = Color.White) },
        text = {
            Column {
                if (info.isNotEmpty()) Text(info, color = Pro.Dim, fontSize = 12.sp)
                OutlinedTextField(v, { v = it.take(60) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = fieldColors())
            }
        },
        confirmButton = { TextButton(enabled = v.isNotBlank(), onClick = { onDone(v) }) { Text(ok, color = Pro.Amber) } },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("Cancel", color = Color.White) } },
    )
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = com.dronedirector.veyrocutpro.ui.Pro.Fg, unfocusedTextColor = com.dronedirector.veyrocutpro.ui.Pro.Fg,
    focusedBorderColor = Pro.Amber, focusedLabelColor = Pro.Amber, cursorColor = Pro.Amber,
)

/** Team copying in progress (media up or down), over the editor. */
@Composable
fun TeamBusyOverlay() {
    val b = Team.busy ?: return
    AlertDialog(
        onDismissRequest = {}, containerColor = Pro.PanelSolid,
        title = { Text("Team project", color = Color.White) },
        text = {
            Column {
                Text(b, color = Pro.Dim, fontSize = 13.sp)
                LinearProgressIndicator(progress = { Team.progress }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), color = Pro.Amber)
            }
        },
        confirmButton = { TextButton(onClick = { Team.stop = true }) { Text("Stop", color = Pro.Red) } },
    )
}
