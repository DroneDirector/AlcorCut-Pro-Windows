package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.view.KeyEvent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import com.dronedirector.veyrocutpro.ui.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.ui.Pro
import com.dronedirector.veyrocutpro.ui.Text

/** One key with its modifiers (Ctrl also matches the ⌘ key on Mac-style keyboards). */
data class KeyCombo(val code: Int, val ctrl: Boolean = false, val shift: Boolean = false, val alt: Boolean = false) {
    fun text(): String = buildList {
        if (ctrl) add("Ctrl"); if (alt) add("Alt"); if (shift) add("Shift")
        add(keyName(code))
    }.joinToString(" + ")

    fun save() = "${if (ctrl) "c" else ""}${if (shift) "s" else ""}${if (alt) "a" else ""}:$code"

    companion object {
        fun of(e: KeyEvent) = KeyCombo(e.keyCode, e.isCtrlPressed || e.isMetaPressed, e.isShiftPressed, e.isAltPressed)
        fun load(s: String): KeyCombo? {
            val m = s.substringBefore(':'); val code = s.substringAfter(':').toIntOrNull() ?: return null
            return KeyCombo(code, 'c' in m, 's' in m, 'a' in m)
        }
        fun keyName(code: Int): String = when (code) {
            KeyEvent.KEYCODE_SPACE -> "Space"; KeyEvent.KEYCODE_DPAD_LEFT -> "←"; KeyEvent.KEYCODE_DPAD_RIGHT -> "→"
            KeyEvent.KEYCODE_DPAD_UP -> "↑"; KeyEvent.KEYCODE_DPAD_DOWN -> "↓"; KeyEvent.KEYCODE_MOVE_HOME -> "Home"; KeyEvent.KEYCODE_MOVE_END -> "End"
            KeyEvent.KEYCODE_DEL -> "Backspace"; KeyEvent.KEYCODE_FORWARD_DEL -> "Delete"; KeyEvent.KEYCODE_ENTER -> "Enter"; KeyEvent.KEYCODE_TAB -> "Tab"
            KeyEvent.KEYCODE_ESCAPE -> "Esc"; KeyEvent.KEYCODE_PAGE_UP -> "Page Up"; KeyEvent.KEYCODE_PAGE_DOWN -> "Page Down"
            KeyEvent.KEYCODE_MINUS -> "-"; KeyEvent.KEYCODE_EQUALS -> "="; KeyEvent.KEYCODE_PLUS -> "+"; KeyEvent.KEYCODE_COMMA -> ","; KeyEvent.KEYCODE_PERIOD -> "."
            KeyEvent.KEYCODE_SLASH -> "/"; KeyEvent.KEYCODE_BACKSLASH -> "\\"; KeyEvent.KEYCODE_LEFT_BRACKET -> "["; KeyEvent.KEYCODE_RIGHT_BRACKET -> "]"
            KeyEvent.KEYCODE_SEMICOLON -> ";"; KeyEvent.KEYCODE_APOSTROPHE -> "'"; KeyEvent.KEYCODE_GRAVE -> "`"
            else -> KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_").replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
                .let { if (it.length == 1) it.uppercase() else it }
        }
        /** Keys that are only modifiers (pressed on their own while choosing a key). */
        fun isModifier(code: Int) = code in setOf(
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT, KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT,
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT, KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT,
            KeyEvent.KEYCODE_FUNCTION, KeyEvent.KEYCODE_CAPS_LOCK, KeyEvent.KEYCODE_NUM_LOCK, KeyEvent.KEYCODE_SCROLL_LOCK,
        )
    }
}

/** One keyboard action: its id, what it's called, its group and its standard keys. */
class KeyAction(val id: String, val label: String, val group: String, vararg val defaults: KeyCombo)

private fun k(code: Int, ctrl: Boolean = false, shift: Boolean = false, alt: Boolean = false) = KeyCombo(code, ctrl, shift, alt)

/**
 * Keyboard shortcuts (Bluetooth / USB keyboard) for one part of the app: a list of actions with
 * sensible standard keys, each changeable (keyboard icon). Own code.
 */
class ShortcutSet(private val prefs: String, val title: String, val ALL: List<KeyAction>) {
    /** Current keys per action (changes are kept). */
    private val keys = HashMap<String, List<KeyCombo>>()
    var version by mutableIntStateOf(0)
        private set
    private var loaded = false

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        val p = c.getSharedPreferences(prefs, Context.MODE_PRIVATE)
        for (a in ALL) {
            val s = p.getString(a.id, null)
            keys[a.id] = if (s == null) a.defaults.toList() else s.split(',').mapNotNull { KeyCombo.load(it) }
        }
        version++
    }

    fun keysOf(id: String): List<KeyCombo> = keys[id] ?: ALL.firstOrNull { it.id == id }?.defaults?.toList().orEmpty()

    /** The action [e] runs, if any. */
    fun actionFor(e: KeyEvent): String? {
        val k = KeyCombo.of(e)
        return ALL.firstOrNull { a -> keysOf(a.id).any { it == k } }?.id
    }

    fun owner(k: KeyCombo): KeyAction? = ALL.firstOrNull { a -> keysOf(a.id).any { it == k } }

    private fun store(c: Context, id: String, l: List<KeyCombo>) {
        keys[id] = l
        c.getSharedPreferences(prefs, Context.MODE_PRIVATE).edit().putString(id, l.joinToString(",") { it.save() }).apply()
        version++
    }

    /** Gives [k] to [id] (taking it away from any other action). [replace] = it becomes the only key. */
    fun assign(c: Context, id: String, k: KeyCombo, replace: Boolean) {
        owner(k)?.takeIf { it.id != id }?.let { o -> store(c, o.id, keysOf(o.id).filter { it != k }) }
        store(c, id, if (replace) listOf(k) else (keysOf(id) + k).distinct())
    }

    fun clear(c: Context, id: String, k: KeyCombo) = store(c, id, keysOf(id).filter { it != k })

    fun resetAll(c: Context) {
        c.getSharedPreferences(prefs, Context.MODE_PRIVATE).edit().clear().apply()
        for (a in ALL) keys[a.id] = a.defaults.toList()
        version++
    }
}

private typealias Action = KeyAction

/** The video editor's shortcuts. */
val Shortcuts = ShortcutSet("procam_keys", "Video editor", listOf(
        // Playback
        Action("play", "Play / pause", "Playback", k(KeyEvent.KEYCODE_SPACE), k(KeyEvent.KEYCODE_K)),
        Action("back5", "Back 5 seconds", "Playback", k(KeyEvent.KEYCODE_J)),
        Action("fwd5", "Forward 5 seconds", "Playback", k(KeyEvent.KEYCODE_L)),
        Action("back1", "Back 1 second", "Playback", k(KeyEvent.KEYCODE_J, shift = true)),
        Action("fwd1", "Forward 1 second", "Playback", k(KeyEvent.KEYCODE_L, shift = true)),
        Action("frameback", "Back one frame", "Playback", k(KeyEvent.KEYCODE_DPAD_LEFT)),
        Action("framefwd", "Forward one frame", "Playback", k(KeyEvent.KEYCODE_DPAD_RIGHT)),
        Action("back10f", "Back 10 frames", "Playback", k(KeyEvent.KEYCODE_DPAD_LEFT, shift = true)),
        Action("fwd10f", "Forward 10 frames", "Playback", k(KeyEvent.KEYCODE_DPAD_RIGHT, shift = true)),
        Action("prevcut", "Previous cut", "Playback", k(KeyEvent.KEYCODE_DPAD_UP)),
        Action("nextcut", "Next cut", "Playback", k(KeyEvent.KEYCODE_DPAD_DOWN)),
        Action("start", "Go to start", "Playback", k(KeyEvent.KEYCODE_MOVE_HOME)),
        Action("end", "Go to end", "Playback", k(KeyEvent.KEYCODE_MOVE_END)),
        Action("full", "Full screen", "Playback", k(KeyEvent.KEYCODE_F)),
        Action("mute", "Mute preview", "Playback", k(KeyEvent.KEYCODE_M, shift = true)),
        Action("magnify", "Magnifier", "Playback", k(KeyEvent.KEYCODE_F, shift = true)),
        Action("safe", "Safe margins", "Playback", k(KeyEvent.KEYCODE_G, shift = true)),
        // Marking
        Action("markin", "Mark in", "Marking", k(KeyEvent.KEYCODE_I)),
        Action("markout", "Mark out", "Marking", k(KeyEvent.KEYCODE_O)),
        Action("goin", "Go to in point", "Marking", k(KeyEvent.KEYCODE_I, shift = true)),
        Action("goout", "Go to out point", "Marking", k(KeyEvent.KEYCODE_O, shift = true)),
        Action("clearinout", "Clear in and out", "Marking", k(KeyEvent.KEYCODE_X, alt = true)),
        Action("marker", "Add / remove marker", "Marking", k(KeyEvent.KEYCODE_M)),
        Action("prevmarker", "Previous marker", "Marking", k(KeyEvent.KEYCODE_DPAD_UP, shift = true)),
        Action("nextmarker", "Next marker", "Marking", k(KeyEvent.KEYCODE_DPAD_DOWN, shift = true)),
        // Editing
        Action("split", "Split at playhead", "Editing", k(KeyEvent.KEYCODE_S), k(KeyEvent.KEYCODE_B)),
        Action("delete", "Delete clip (close gap)", "Editing", k(KeyEvent.KEYCODE_FORWARD_DEL), k(KeyEvent.KEYCODE_DEL)),
        Action("duplicate", "Duplicate clip", "Editing", k(KeyEvent.KEYCODE_D, ctrl = true)),
        Action("undo", "Undo", "Editing", k(KeyEvent.KEYCODE_Z, ctrl = true)),
        Action("redo", "Redo", "Editing", k(KeyEvent.KEYCODE_Z, ctrl = true, shift = true), k(KeyEvent.KEYCODE_Y, ctrl = true)),
        Action("prevclip", "Select previous clip", "Editing", k(KeyEvent.KEYCODE_COMMA)),
        Action("nextclip", "Select next clip", "Editing", k(KeyEvent.KEYCODE_PERIOD)),
        Action("snap", "Snapping on / off", "Editing", k(KeyEvent.KEYCODE_N)),
        Action("title", "Add a title", "Editing", k(KeyEvent.KEYCODE_T)),
        Action("frame", "Export frame", "Editing", k(KeyEvent.KEYCODE_E, shift = true)),
        // Timeline
        Action("zoomin", "Zoom timeline in", "Timeline", k(KeyEvent.KEYCODE_EQUALS), k(KeyEvent.KEYCODE_PLUS)),
        Action("zoomout", "Zoom timeline out", "Timeline", k(KeyEvent.KEYCODE_MINUS)),
        Action("zoomfit", "Show whole timeline", "Timeline", k(KeyEvent.KEYCODE_BACKSLASH)),
        // Pages
        Action("p_media", "Media page", "Pages", k(KeyEvent.KEYCODE_1, alt = true)),
        Action("p_edit", "Edit page", "Pages", k(KeyEvent.KEYCODE_2, alt = true)),
        Action("p_text", "Text page", "Pages", k(KeyEvent.KEYCODE_3, alt = true)),
        Action("p_transitions", "Transitions page", "Pages", k(KeyEvent.KEYCODE_4, alt = true)),
        Action("p_color", "Color page", "Pages", k(KeyEvent.KEYCODE_5, alt = true)),
        Action("p_layers", "Layers page", "Pages", k(KeyEvent.KEYCODE_6, alt = true)),
        Action("p_audio", "Audio page", "Pages", k(KeyEvent.KEYCODE_7, alt = true)),
        Action("p_deliver", "Deliver page", "Pages", k(KeyEvent.KEYCODE_8, alt = true)),
        // File
        Action("filemenu", "File menu", "File", k(KeyEvent.KEYCODE_F, alt = true)),
        Action("newproject", "New project", "File", k(KeyEvent.KEYCODE_N, ctrl = true)),
        Action("open", "Open project", "File", k(KeyEvent.KEYCODE_O, ctrl = true)),
        Action("save", "Save project changes", "File", k(KeyEvent.KEYCODE_S, ctrl = true)),
        Action("saveas", "Save as", "File", k(KeyEvent.KEYCODE_S, ctrl = true, shift = true)),
        Action("import", "Import media", "File", k(KeyEvent.KEYCODE_I, ctrl = true)),
        Action("capture", "Capture", "File", k(KeyEvent.KEYCODE_C, ctrl = true, shift = true)),
        Action("export", "Export", "File", k(KeyEvent.KEYCODE_E, ctrl = true)),
        Action("close", "Close project", "File", k(KeyEvent.KEYCODE_W, ctrl = true)),
        Action("nextproject", "Next open project", "File", k(KeyEvent.KEYCODE_TAB, ctrl = true)),
        Action("projectsettings", "Project settings", "File", k(KeyEvent.KEYCODE_P, ctrl = true, shift = true)),
        Action("settings", "Settings", "File", k(KeyEvent.KEYCODE_COMMA, ctrl = true)),
        Action("shortcuts", "Keyboard shortcuts", "File", k(KeyEvent.KEYCODE_SLASH, ctrl = true)),
    ))

/** The photo editor's shortcuts. */
val PhotoShortcuts = ShortcutSet("procam_keys_photo", "Photo editor", listOf(
    Action("undo", "Undo", "Editing", k(KeyEvent.KEYCODE_Z, ctrl = true)),
    Action("redo", "Redo", "Editing", k(KeyEvent.KEYCODE_Z, ctrl = true, shift = true), k(KeyEvent.KEYCODE_Y, ctrl = true)),
    Action("copy", "Copy settings", "Editing", k(KeyEvent.KEYCODE_C, ctrl = true)),
    Action("paste", "Paste the look", "Editing", k(KeyEvent.KEYCODE_V, ctrl = true)),
    Action("pasteall", "Paste all settings", "Editing", k(KeyEvent.KEYCODE_V, ctrl = true, shift = true)),
    Action("split", "Before / after", "View", k(KeyEvent.KEYCODE_BACKSLASH), k(KeyEvent.KEYCODE_Y)),
    Action("zoom", "Zoom in / out", "View", k(KeyEvent.KEYCODE_Z)),
    Action("resetview", "Whole photo again", "View", k(KeyEvent.KEYCODE_ESCAPE)),
    Action("controls", "Show / hide the tools", "View", k(KeyEvent.KEYCODE_TAB)),
    Action("reference", "Reference photo", "View", k(KeyEvent.KEYCODE_R, shift = true)),
    Action("clipping", "Clipping warning", "View", k(KeyEvent.KEYCODE_J)),
    Action("prev", "Previous photo", "Filmstrip", k(KeyEvent.KEYCODE_DPAD_LEFT), k(KeyEvent.KEYCODE_PAGE_UP)),
    Action("next", "Next photo", "Filmstrip", k(KeyEvent.KEYCODE_DPAD_RIGHT), k(KeyEvent.KEYCODE_PAGE_DOWN)),
    Action("first", "First photo", "Filmstrip", k(KeyEvent.KEYCODE_MOVE_HOME)),
    Action("last", "Last photo", "Filmstrip", k(KeyEvent.KEYCODE_MOVE_END)),
    Action("rate0", "No stars", "Rating", k(KeyEvent.KEYCODE_0)),
    Action("rate1", "1 star", "Rating", k(KeyEvent.KEYCODE_1)),
    Action("rate2", "2 stars", "Rating", k(KeyEvent.KEYCODE_2)),
    Action("rate3", "3 stars", "Rating", k(KeyEvent.KEYCODE_3)),
    Action("rate4", "4 stars", "Rating", k(KeyEvent.KEYCODE_4)),
    Action("rate5", "5 stars", "Rating", k(KeyEvent.KEYCODE_5)),
    Action("red", "Red label", "Rating", k(KeyEvent.KEYCODE_6)),
    Action("yellow", "Yellow label", "Rating", k(KeyEvent.KEYCODE_7)),
    Action("green", "Green label", "Rating", k(KeyEvent.KEYCODE_8)),
    Action("blue", "Blue label", "Rating", k(KeyEvent.KEYCODE_9)),
    Action("pick", "Pick", "Rating", k(KeyEvent.KEYCODE_P)),
    Action("reject", "Reject", "Rating", k(KeyEvent.KEYCODE_X)),
    Action("unflag", "Remove flag", "Rating", k(KeyEvent.KEYCODE_U)),
    Action("crop", "Crop & perspective", "Panels", k(KeyEvent.KEYCODE_R)),
    Action("heal", "Heal", "Panels", k(KeyEvent.KEYCODE_Q)),
    Action("masks", "Masks", "Panels", k(KeyEvent.KEYCODE_M)),
    Action("light", "Light", "Panels", k(KeyEvent.KEYCODE_L)),
    Action("color", "Colour", "Panels", k(KeyEvent.KEYCODE_C)),
    Action("detail", "Detail", "Panels", k(KeyEvent.KEYCODE_D)),
    Action("exportpanel", "Export panel", "Panels", k(KeyEvent.KEYCODE_E)),
    Action("history", "History", "Panels", k(KeyEvent.KEYCODE_H)),
    Action("filemenu", "File menu", "File", k(KeyEvent.KEYCODE_F, alt = true)),
    Action("open", "Open project", "File", k(KeyEvent.KEYCODE_O, ctrl = true)),
    Action("save", "Save project changes", "File", k(KeyEvent.KEYCODE_S, ctrl = true)),
    Action("saveas", "Save as", "File", k(KeyEvent.KEYCODE_S, ctrl = true, shift = true)),
    Action("import", "Import photos", "File", k(KeyEvent.KEYCODE_I, ctrl = true)),
    Action("capture", "Capture", "File", k(KeyEvent.KEYCODE_C, ctrl = true, shift = true)),
    Action("export", "Export", "File", k(KeyEvent.KEYCODE_E, ctrl = true)),
    Action("settings", "Settings", "File", k(KeyEvent.KEYCODE_COMMA, ctrl = true)),
    Action("shortcuts", "Keyboard shortcuts", "File", k(KeyEvent.KEYCODE_SLASH, ctrl = true), k(KeyEvent.KEYCODE_SLASH), k(KeyEvent.KEYCODE_F1)),
))

/** Capture's shortcuts. */
val CaptureShortcuts = ShortcutSet("procam_keys_capture", "Capture", listOf(
    Action("shutter", "Take photo / start or stop recording", "Shooting", k(KeyEvent.KEYCODE_SPACE), k(KeyEvent.KEYCODE_ENTER)),
    Action("zoomin", "Zoom in", "Shooting", k(KeyEvent.KEYCODE_EQUALS), k(KeyEvent.KEYCODE_PLUS)),
    Action("zoomout", "Zoom out", "Shooting", k(KeyEvent.KEYCODE_MINUS)),
    Action("zoom1", "Zoom 1×", "Shooting", k(KeyEvent.KEYCODE_0)),
    Action("evup", "Exposure up", "Shooting", k(KeyEvent.KEYCODE_RIGHT_BRACKET)),
    Action("evdown", "Exposure down", "Shooting", k(KeyEvent.KEYCODE_LEFT_BRACKET)),
    Action("aelock", "Exposure lock", "Shooting", k(KeyEvent.KEYCODE_L)),
    Action("awblock", "White balance lock", "Shooting", k(KeyEvent.KEYCODE_W)),
    Action("autofocus", "Back to autofocus", "Shooting", k(KeyEvent.KEYCODE_A)),
    Action("flip", "Front / back camera", "Shooting", k(KeyEvent.KEYCODE_C)),
    Action("flash", "Flash", "Shooting", k(KeyEvent.KEYCODE_F)),
    Action("timer", "Self-timer", "Shooting", k(KeyEvent.KEYCODE_T)),
    Action("nextmode", "Next mode", "Modes", k(KeyEvent.KEYCODE_TAB)),
    Action("prevmode", "Previous mode", "Modes", k(KeyEvent.KEYCODE_TAB, shift = true)),
    Action("grid", "Grid", "View", k(KeyEvent.KEYCODE_G)),
    Action("level", "Level", "View", k(KeyEvent.KEYCODE_V)),
    Action("histogram", "Histogram", "View", k(KeyEvent.KEYCODE_H)),
    Action("zebras", "Zebras", "View", k(KeyEvent.KEYCODE_Z)),
    Action("peaking", "Focus peaking", "View", k(KeyEvent.KEYCODE_K)),
    Action("falsecolor", "False colour", "View", k(KeyEvent.KEYCODE_F, shift = true)),
    Action("shortcuts", "Keyboard shortcuts", "View", k(KeyEvent.KEYCODE_SLASH, ctrl = true)),
))

/** Keyboard icon: every shortcut, changeable — tap one and press the new key. */
@Composable
fun ShortcutsDialog(set: ShortcutSet = Shortcuts, onDismiss: () -> Unit) {
    val context = LocalContext.current
    set.load(context)
    val v = set.version
    // Waiting for a key: the action, and whether the key replaces its keys or is added.
    var waiting by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    val hasKeyboard = LocalConfiguration.current.keyboard != android.content.res.Configuration.KEYBOARD_NOKEYS
    // The dialog has its own window, so the keys are read here (and through KeyBus as a fallback).
    fun capture(e: KeyEvent): Boolean {
        val w = waiting ?: return false
        if (e.action != KeyEvent.ACTION_DOWN || KeyCombo.isModifier(e.keyCode)) return true
        if (e.keyCode == KeyEvent.KEYCODE_ESCAPE && !e.isShiftPressed && !e.isCtrlPressed) { waiting = null; return true }
        val k = KeyCombo.of(e)
        val before = set.owner(k)?.takeIf { it.id != w.first }
        set.assign(context, w.first, k, w.second)
        note = if (before != null) "${k.text()} was used for \"${before.label}\" — moved here" else null
        waiting = null
        return true
    }
    DisposableEffect(waiting) {
        if (waiting != null) KeyBus.capture = { capture(it) }
        onDispose { KeyBus.capture = null }
    }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(waiting) { if (waiting != null) try { focus.requestFocus() } catch (_: Throwable) {} }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pro.PanelSolid,
        title = { Text("Keyboard shortcuts · " + set.title, color = Color.White) },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp)
                    .focusRequester(focus).focusable()
                    .onPreviewKeyEvent { capture(com.dronedirector.veyrocutpro.phoneKeyEvent(it)) }
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    if (hasKeyboard) "Tap a shortcut, then press the key (with Ctrl, Shift or Alt if you like) to use for it. + adds a second key, ✕ removes one. Esc cancels."
                    else "Connect a keyboard (Bluetooth or USB) to use shortcuts and change their keys.",
                    color = Pro.Dim, fontSize = 12.sp,
                )
                note?.let { Text(it, color = Pro.Amber, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
                var group = ""
                for (a in set.ALL) {
                    if (a.group != group) {
                        group = a.group
                        Text(group.uppercase(), style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
                    }
                    val w = waiting?.first == a.id
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp))
                            .background(if (w) Pro.Amber.copy(alpha = 0.18f) else Ed.Raised)
                            .clickable { waiting = a.id to true; note = null }.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(a.label, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        if (w) Text("Press a key…", color = Pro.Amber, fontSize = 12.sp)
                        else {
                            v.let { }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                val ks = set.keysOf(a.id)
                                if (ks.isEmpty()) Text("—", color = Pro.Dim, fontSize = 12.sp)
                                for (k in ks) Row(
                                    Modifier.clip(RoundedCornerShape(5.dp)).background(Color.Black.copy(alpha = 0.4f)).padding(start = 6.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(k.text(), color = Color.White, fontSize = 11.sp)
                                    Box(Modifier.size(18.dp).clip(RoundedCornerShape(50)).clickable { set.clear(context, a.id, k) }, contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.Close, "Remove key", tint = Pro.Dim, modifier = Modifier.size(12.dp))
                                    }
                                }
                                Box(Modifier.size(22.dp).clip(RoundedCornerShape(50)).clickable { waiting = a.id to false; note = null }, contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Add, "Add a key", tint = Pro.Amber, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = Pro.Amber) } },
        dismissButton = { TextButton(onClick = { set.resetAll(context); note = "Back to the standard keys" }) { Text("Reset all", color = Pro.Dim) } },
    )
}
