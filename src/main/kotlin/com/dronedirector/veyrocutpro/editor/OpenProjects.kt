package com.dronedirector.veyrocutpro.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Several projects open in the video editor at once (File → Open projects): each keeps its edit
 * in memory while another is on screen, so switching is quick. Own code.
 */
class OpenProject(
    val key: String,
    /** The edit (null = a new, empty one not opened yet). Updated whenever another project comes on screen. */
    var data: VideoProjectData?,
    var team: TeamSession?,
    /** What it looked like when last saved / opened (to see unsaved changes); null = take it when it opens. */
    var savedSig: String? = null,
) {
    var name by mutableStateOf(data?.name ?: "")
}

class ProjectHost {
    val open = mutableStateListOf<OpenProject>()
    var active by mutableStateOf<String?>(null)
    /** Bumped to rebuild the editor (revert, reopen). */
    var reload by mutableIntStateOf(0)
    /** Set by the editor on screen: its edit as it is now. */
    var snapshot: (() -> VideoProjectData)? = null

    fun activeProject(): OpenProject? = open.firstOrNull { it.key == active }

    /** Unsaved changes in [op] (the one on screen is read live). */
    fun dirty(op: OpenProject): Boolean {
        val d = if (op.key == active) snapshot?.invoke() ?: op.data else op.data
        if (d == null) return false
        val empty = d.clips.isEmpty() && d.layers.isEmpty() && d.music == null && d.extraAudio.isEmpty()
        val s = op.savedSig ?: return !empty
        return sig(d) != s
    }

    companion object {
        /** The edit's content, without the things that change on every save. */
        fun sig(p: VideoProjectData): String = try { ProjectStore.gson.toJson(p.copy(id = "", savedAt = 0L, name = "")) } catch (_: Throwable) { "" }
    }
}

/** savedSig of a project read from disk: taken from the editor once it has opened. */
const val SIG_PENDING = "pending"

/** What the editor on screen can ask the video editor screen to do with the open projects. */
class EditorNav(
    val open: (data: VideoProjectData?, team: TeamSession?, fromDisk: Boolean) -> Unit,
    val switchTo: (String) -> Unit,
    val close: (String) -> Unit,
    val exitApp: () -> Unit,
    /** Opens [OpenProject] again from its (new) data — revert. */
    val reopen: (OpenProject) -> Unit,
)

/** The video editor's File menu, keyboard and bar-dialog state, kept in one place (the editor function is near Android's size limit). */
class WsFile {
    val fa = FileActions()
    val fm = FileMenuState()
    var showKeys by mutableStateOf(false)
    var barDlg by mutableStateOf<String?>(null)
    var closeAsk by mutableStateOf<Pair<List<OpenProject>, () -> Unit>?>(null)
    /** Picking several timeline clips, and the clip menu. */
    val pick = ClipPick()
    /** The button row under the picture: where Play is, to start with it centred. */
    val bar = BarCentre()
}

class BarCentre {
    val scroll = androidx.compose.foundation.ScrollState(0)
    // Layout positions (not state: scrolling moves them every frame and must not re-lay the row).
    private var bar: androidx.compose.ui.layout.LayoutCoordinates? = null
    private var row: androidx.compose.ui.layout.LayoutCoordinates? = null
    private var play: androidx.compose.ui.layout.LayoutCoordinates? = null
    /** Play's middle from the start of the buttons, and the buttons' width (−1 until laid out). They don't change with scrolling. */
    var playX by mutableStateOf(-1f)
        private set
    var rowW by mutableStateOf(0f)
        private set
    /** The row was scrolled by hand (then it stays where it was put). */
    var touched = false
    var auto = false

    fun onBar(c: androidx.compose.ui.layout.LayoutCoordinates) { bar = c }
    fun onRow(c: androidx.compose.ui.layout.LayoutCoordinates) {
        row = c
        val w = c.size.width.toFloat()
        if (kotlin.math.abs(w - rowW) > 1f) rowW = w
        measure()
    }
    fun onPlay(c: androidx.compose.ui.layout.LayoutCoordinates) { play = c; measure() }
    private fun measure() {
        val r = row ?: return; val p = play ?: return
        if (!r.isAttached || !p.isAttached) return
        val x = r.localPositionOf(p, androidx.compose.ui.geometry.Offset.Zero).x + p.size.width / 2f
        if (kotlin.math.abs(x - playX) > 1f) playX = x
    }

    /** Scrolls so Play sits in the middle of the bar. */
    suspend fun centre() {
        if (touched || playX < 0f) return
        // Wait until the spacers around the buttons are laid out.
        repeat(3) { androidx.compose.runtime.withFrameNanos { } }
        val b = bar ?: return; val p = play ?: return
        if (!b.isAttached || !p.isAttached) return
        // How far Play is from the middle of the bar on screen now.
        val off = b.localPositionOf(p, androidx.compose.ui.geometry.Offset.Zero).x + p.size.width / 2f - b.size.width / 2f
        val target = (scroll.value + off).toInt().coerceIn(0, scroll.maxValue.coerceAtLeast(0))
        auto = true
        try { scroll.scrollTo(target) } finally { auto = false }
    }
}
