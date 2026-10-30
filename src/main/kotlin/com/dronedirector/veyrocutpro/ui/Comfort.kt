package com.dronedirector.veyrocutpro.ui

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import java.io.File

private fun prefs(c: Context) = c.getSharedPreferences("procam", Context.MODE_PRIVATE)

/** Appearance → One-handed: 0 off, 1 right hand, 2 left hand (Capture on a phone held upright). */
object OneHanded {
    var side by mutableIntStateOf(0)
        private set
    fun load(c: Context) { side = prefs(c).getInt("one_handed", 0) }
    fun set(c: Context, v: Int) { side = v; prefs(c).edit().putInt("one_handed", v).apply() }
}

/** Appearance → Haptic feedback: a light tick on the shutter, record, toggles, snapping and centre points. */
object Haptics {
    var on by mutableStateOf(true)
        private set
    fun load(c: Context) { on = prefs(c).getBoolean("haptics", true) }
    fun set(c: Context, v: Boolean) { on = v; prefs(c).edit().putBoolean("haptics", v).apply() }
    fun tick(v: View?) { if (on) v?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    fun press(v: View?) { if (on) v?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
    fun confirm(v: View?) {
        if (!on || v == null) return
        v.performHapticFeedback(if (android.os.Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
    }
}

/** A tick for Compose code: `val tick = rememberTick(); … tick()`. */
@Composable
fun rememberTick(): () -> Unit { val v = LocalView.current; return remember(v) { { Haptics.tick(v) } } }

/** Appearance rows shared by Capture and the editors. */
@Composable
fun ComfortSettings() {
    val ctx = LocalContext.current
    Text("One-handed", color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(top = 10.dp))
    Text("Capture on a phone held upright: the top bar moves down and the shutter moves under your thumb.", color = Pro.Dim, fontSize = 12.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
        listOf("Off", "Right hand", "Left hand").forEachIndexed { i, n ->
            val sel = OneHanded.side == i
            Text(n, color = if (sel) Color.Black else Color.White, fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (sel) Pro.Amber else Pro.Fg.copy(alpha = 0.08f))
                    .clickable { OneHanded.set(ctx, i) }.padding(horizontal = 12.dp, vertical = 6.dp))
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Haptic feedback", color = Color.White, fontSize = 15.sp)
            Text("A light tick on the shutter, record, switches, snapping on the timeline and slider centre points", color = Pro.Dim, fontSize = 12.sp)
        }
        androidx.compose.material3.Switch(Haptics.on, { Haptics.set(ctx, it) },
            colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Pro.Amber, checkedThumbColor = Color.Black))
    }
}

// ------------------------------------------------------------------ hold for help

/** Hold a button for a short explanation of what it does. */
object HelpTip {
    var text by mutableStateOf<String?>(null)
        private set
    var stamp by mutableIntStateOf(0)
        private set
    fun show(label: String, view: View? = null) {
        val t = HelpTexts.of(label) ?: return
        text = t; stamp++
        Haptics.press(view)
    }
    fun hide() { text = null }
}

/** The explanation for a button, by its label (null = nothing to say). */
object HelpTexts {
    private val map = mapOf(
        "Undo" to "Undo: steps back one change.",
        "Redo" to "Redo: brings back what Undo took away.",
        "Settings" to "Settings: everything for this screen, with a search at the top.",
        "Keyboard shortcuts" to "Keyboard shortcuts: see and change the keys for a connected keyboard.",
        "Scope" to "Scope: the histogram, waveform, parade and vectorscope on the right edge.",
        "Back" to "Back: leave this screen (your work is kept).",
        "LOAD" to "LOAD: open a saved project or an autosave.",
        "SAVE" to "SAVE: keep this edit as a project you can open again.",
        "EXPORT" to "EXPORT: make the finished photo or video.",
        "MERGE PHOTOS ▾" to "Merge photos: HDR, panorama, focus stack, long exposure and more from several shots.",
        "Switch camera" to "Switch camera: front ↔ back.",
        "Pause" to "Pause: pause the recording; tap again to carry on in the same clip.",
        "Photo while recording" to "Photo while recording: a still from the video, without stopping.",
        "Gallery" to "Gallery: your latest photos and videos.",
        "Close" to "Close.",
        "Delete project" to "Delete this project (your photos and videos are kept).",
        "Help" to "Help: the guide, tutorials and troubleshooting.",
        "Panels" to "Panels: show or hide parts of the screen, or reset the layout.",
        "Loop" to "Loop: play the in–out range or the whole timeline over and over.",
        "Split" to "Split: cut the clip at the playhead.",
        "Full screen" to "Full screen: only the picture.",
        "FLASH" to "Flash: off, on, auto or torch.",
        "TIMER" to "Self-timer: wait a few seconds before the photo.",
        "GRID" to "Grid: lines to help you frame (thirds, square, golden ratio).",
        "RATIO" to "Ratio: the shape of the photo.",
        "HDR" to "HDR: keeps detail in both bright and dark parts.",
        "STAB" to "Stabilization: off, in-app or Gyroflow.",
        "PROMPTER" to "Teleprompter: your script scrolling on the screen.",
        "FOCUS" to "Focus mask: the sharpest parts of the photo in green.",
        "SAMPLERS" to "Colour samplers: tap up to four points to read their R, G, B values.",
        "SPLIT" to "Split view: before and after side by side.",
        "AUDIO" to "Audio: microphone, levels and sound settings.",
        "LUT" to "LUT: a look on the viewfinder, or recorded into the video.",
    )
    fun of(label: String): String? {
        val k = label.trim()
        if (k.isEmpty()) return null
        map[k]?.let { return I18n.tr(it) }
        map[k.removeSuffix(" ✓").trim()]?.let { return I18n.tr(it) }
        return I18n.tr(k)
    }
}

/** Shows the help bubble on top of everything (MainActivity). */
@Composable
fun HelpTipHost() {
    val t = HelpTip.text
    LaunchedEffect(HelpTip.stamp) { if (t != null) { delay(3200); HelpTip.hide() } }
    Box(Modifier.fillMaxSize().zIndex(50f).windowInsetsPadding(WindowInsets.statusBars), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(t != null, enter = fadeIn(), exit = fadeOut()) {
            Text(
                t ?: "", color = Color.White, fontSize = 13.sp,
                modifier = Modifier.padding(top = 64.dp, start = 24.dp, end = 24.dp).widthIn(max = 420.dp)
                    .clip(RoundedCornerShape(12.dp)).background(Color(0xF0202024))
                    .border(1.dp, Pro.Amber.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .clickable { HelpTip.hide() }.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

// ------------------------------------------------------------------ undo bar

/** "Photo moved to Trash — UNDO": a short bar instead of asking first. */
object UndoBar {
    var text by mutableStateOf<String?>(null)
        private set
    private var action: (() -> Unit)? = null
    var stamp by mutableIntStateOf(0)
        private set
    fun show(message: String, undo: () -> Unit) { text = message; action = undo; stamp++ }
    fun undo() { val a = action; action = null; text = null; a?.invoke() }
    fun hide() { text = null; action = null }
}

@Composable
fun UndoBarHost() {
    val t = UndoBar.text
    val stamp = UndoBar.stamp
    LaunchedEffect(stamp) { if (t != null) { delay(6000); if (UndoBar.stamp == stamp) UndoBar.hide() } }
    Box(Modifier.fillMaxSize().zIndex(49f).windowInsetsPadding(WindowInsets.navigationBars), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(t != null, enter = fadeIn(), exit = fadeOut()) {
            Row(
                Modifier.padding(bottom = 90.dp, start = 16.dp, end = 16.dp).widthIn(max = 460.dp).clip(RoundedCornerShape(12.dp))
                    .background(Color(0xF0202024)).padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(I18n.tr(t ?: ""), color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { UndoBar.undo() }) { Text("UNDO", color = Pro.Amber, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/** Set by the open editor: takes a snapshot of the edit and returns how to go back to it. */
object ResetUndo {
    @Volatile var capture: (() -> (() -> Unit))? = null
}

// ------------------------------------------------------------------ what's new

/** Which part of the app a "What's new" list is for. */
enum class NewsArea { ALL, CAPTURE, PHOTO, VIDEO }

/** After an update: a card with what changed (from the changelog inside the app). */
object WhatsNew {
    private const val KEY = "seen_version"

    /** Gear → What's new: the last three releases, for the whole app or one part of it. */
    var request by mutableStateOf<NewsArea?>(null)
    fun show(area: NewsArea) { request = area }

    class Release(val title: String, val lines: List<String>)

    /** Newest first; "Unreleased" (this build) is named after the installed version. */
    fun releases(c: Context, version: String, count: Int): List<Release> = try {
        val text = c.assets.open("CHANGELOG.md").bufferedReader().readText()
        text.split(Regex("(?m)^## ")).drop(1).mapNotNull { sec ->
            val head = sec.lineSequence().first().trim()
            val lines = sec.lines().drop(1).filter { it.startsWith("- ") }.map { it.removePrefix("- ").trim() }
            if (lines.isEmpty()) null else Release(if (head.startsWith("Unreleased")) version else head, lines)
        }.distinctBy { it.title.substringBefore(" ") }.take(count)
    } catch (_: Throwable) { emptyList() }

    private val words = mapOf(
        NewsArea.CAPTURE to listOf("capture", "camera", "record", "shutter", "quick bar", "viewfinder", "lens", "c1–c3", "watch remote", "gallery"),
        NewsArea.PHOTO to listOf("photo editor", "merge", "filmstrip", "photo library", "photo layer", "sampler", "focus mask", "presets", "raw", "heal", "photo book", "catalog"),
        NewsArea.VIDEO to listOf("video editor", "timeline", "deliver", "media strip", "clip", "export", "playback", "music", "transition", "video library", "cineform", "proxy"),
    )

    /** Does this line belong to [area]? */
    fun about(line: String, area: NewsArea): Boolean {
        if (area == NewsArea.ALL) return true
        val l = line.lowercase()
        return words[area]!!.any { l.contains(it) }
    }

    /** The lines for [version] only (the card after an update). */
    fun lines(c: Context, version: String): List<String> = releases(c, version, 1).firstOrNull()?.lines.orEmpty()

    /** True when the card should show now (an update, not a fresh install). Marks the version seen. */
    fun due(c: Context, version: String): Boolean {
        val p = prefs(c)
        val seen = p.getString(KEY, null)
        p.edit().putString(KEY, version).apply()
        if (seen == null) return p.getBoolean("first_run_done", false) || p.all.size > 3
        return seen != version
    }
}

/** Turns "**bold** rest" into styled text. */
private fun boldMarkup(s: String) = buildAnnotatedString {
    var rest = s
    while (true) {
        val a = rest.indexOf("**"); if (a < 0) { append(rest); break }
        val b = rest.indexOf("**", a + 2); if (b < 0) { append(rest); break }
        append(rest.substring(0, a))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Pro.Amber)) { append(rest.substring(a + 2, b)) }
        rest = rest.substring(b + 2)
    }
}

@Composable
fun WhatsNewDialog() {
    val ctx = LocalContext.current
    val version = remember { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (_: Throwable) { "" } }
    var title by remember { mutableStateOf("") }
    var list by remember { mutableStateOf<List<WhatsNew.Release>?>(null) }
    // After an update: this version only.
    LaunchedEffect(Unit) {
        if (version.isNotEmpty() && WhatsNew.due(ctx, version)) {
            val r = WhatsNew.releases(ctx, version, 1)
            if (r.isNotEmpty()) { title = I18n.tr("What's new in {0}").replace("{0}", version); list = r }
        }
    }
    // From a gear: the last three releases, for the whole app or one part of it.
    LaunchedEffect(WhatsNew.request) {
        val area = WhatsNew.request ?: return@LaunchedEffect
        WhatsNew.request = null
        title = I18n.tr(when (area) {
            NewsArea.ALL -> "What's new"
            NewsArea.CAPTURE -> "What's new in Capture"
            NewsArea.PHOTO -> "What's new in the Photo editor"
            NewsArea.VIDEO -> "What's new in the Video editor"
        })
        list = WhatsNew.releases(ctx, version, 3).map { r -> WhatsNew.Release(r.title, r.lines.filter { WhatsNew.about(it, area) }) }
    }
    val l = list ?: return
    AlertDialog(
        onDismissRequest = { list = null },
        containerColor = Pro.PanelSolid,
        title = { Text(title, color = Color.White) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // One fold-out per version: the newest open, the earlier ones closed.
                val several = l.size > 1
                l.forEachIndexed { i, r ->
                    if (!several) {
                        if (r.lines.isEmpty()) Text("Nothing new here in this version.", color = Pro.Dim, fontSize = 12.sp)
                        r.lines.forEach { Text(boldMarkup("• " + it), color = Pro.Fg, fontSize = 13.sp) }
                    } else {
                        var open by remember(l) { mutableStateOf(i == 0) }
                        Row(
                            Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(10.dp)).background(Pro.Fg.copy(alpha = 0.06f))
                                .clickable { open = !open }.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text((if (i == 0) I18n.tr("Latest update") + " · " else "") + r.title, color = if (open) Pro.Amber else Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(I18n.tr("{0} change(s)").replace("{0}", r.lines.size.toString()), color = Pro.Dim, fontSize = 11.sp)
                            }
                            Text(if (open) "▴" else "▾", color = Pro.Dim, fontSize = 16.sp)
                        }
                        if (open) Column(Modifier.padding(start = 6.dp, top = 2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (r.lines.isEmpty()) Text("Nothing new here in this version.", color = Pro.Dim, fontSize = 12.sp)
                            r.lines.forEach { Text(boldMarkup("• " + it), color = Pro.Fg, fontSize = 13.sp) }
                        }
                    }
                }
                if (l.isEmpty()) Text("No notes for this version.", color = Pro.Dim, fontSize = 12.sp)
                Text("Each tool is described in the guide (? or gear → App → Guide).", color = Pro.Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        },
        confirmButton = { TextButton(onClick = { list = null }) { Text("GOT IT", color = Pro.Amber) } },
    )
}

/** Gear → What's new (the last three releases for this part of the app). */
@Composable
fun WhatsNewButton(area: NewsArea, onOpen: () -> Unit = {}) {
    Text(
        "★  " + I18n.tr("WHAT'S NEW"), style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
        modifier = Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(Pro.Fg.copy(alpha = 0.06f)).clickable { onOpen(); WhatsNew.show(area) }.padding(12.dp),
    )
}

// ------------------------------------------------------------------ safe mode

/**
 * Two crashes in a row right after starting: the next start offers safe mode, which opens the
 * start screen without reopening the last project or the last camera set-up.
 */
object SafeMode {
    private fun f(c: Context) = File(c.filesDir, "start_crashes.txt")
    @Volatile private var startedAt = 0L
    var active by mutableStateOf(false)
    var offer by mutableStateOf(false)

    fun started(c: Context) {
        startedAt = android.os.SystemClock.elapsedRealtime()
        offer = count(c) >= 2
    }
    fun count(c: Context): Int = try { f(c).readText().trim().toInt() } catch (_: Throwable) { 0 }
    /** From the crash handler. */
    fun crashed(c: Context) {
        val early = startedAt > 0 && android.os.SystemClock.elapsedRealtime() - startedAt < 20_000
        try { f(c).writeText(if (early) "${count(c) + 1}" else "0") } catch (_: Throwable) {}
    }
    /** The app has run fine for a while. */
    fun healthy(c: Context) { try { f(c).delete() } catch (_: Throwable) {} }
}

@Composable
fun SafeModeDialog(onSafe: () -> Unit) {
    val ctx = LocalContext.current
    if (!SafeMode.offer) return
    AlertDialog(
        onDismissRequest = {},
        containerColor = Pro.PanelSolid,
        title = { Text("AlcorCut Pro closed twice while starting", color = Color.White) },
        text = {
            Text("Open in safe mode? It starts on the start screen and doesn't reopen your last project or camera set-up, so you can carry on. Nothing is deleted.", color = Pro.Dim)
        },
        confirmButton = { TextButton(onClick = { SafeMode.offer = false; SafeMode.active = true; SafeMode.healthy(ctx); onSafe() }) { Text("SAFE MODE", color = Pro.Amber) } },
        dismissButton = { TextButton(onClick = { SafeMode.offer = false; SafeMode.healthy(ctx) }) { Text("START NORMALLY", color = Color.White) } },
    )
}

// ------------------------------------------------------------------ settings search

/** What's typed in the settings search (empty = show everything as normal). */
val LocalSettingsQuery = androidx.compose.runtime.compositionLocalOf { "" }
/** Set by rows of a settings group that match the search. */
internal val LocalSettingsHit = androidx.compose.runtime.compositionLocalOf<androidx.compose.runtime.MutableState<Boolean>?> { null }

fun settingsMatch(q: String, vararg texts: String): Boolean {
    if (q.isBlank()) return true
    val words = q.trim().lowercase().split(Regex("\\s+"))
    val hay = texts.joinToString(" ") { it + " " + I18n.tr(it) }.lowercase()
    return words.all { hay.contains(it) }
}

/** Marks the group as matching when [texts] match the search (for rows that aren't ToggleRow / ChoiceRow). */
@Composable
fun SettingsKeywords(vararg texts: String) {
    val q = LocalSettingsQuery.current
    val hit = LocalSettingsHit.current
    if (q.isNotBlank() && hit != null && settingsMatch(q, *texts)) androidx.compose.runtime.SideEffect { hit.value = true }
}

@Composable
fun SettingsSearchField(query: String, onChange: (String) -> Unit) {
    androidx.compose.material3.OutlinedTextField(
        query, onChange, singleLine = true,
        placeholder = { Text("Search settings (e.g. zebra, proxy, location)", color = Pro.Dim, fontSize = 13.sp) },
        leadingIcon = { androidx.compose.material3.Icon(Icons.Filled.Search, null, tint = Pro.Dim) },
        trailingIcon = if (query.isNotEmpty()) ({ Text("✕", color = Pro.Dim, modifier = Modifier.clickable { onChange("") }.padding(10.dp)) }) else null,
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedTextColor = Pro.Fg, unfocusedTextColor = Pro.Fg, focusedBorderColor = Pro.Amber, unfocusedBorderColor = Pro.Fg.copy(alpha = 0.2f), cursorColor = Pro.Amber,
        ),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    )
}
