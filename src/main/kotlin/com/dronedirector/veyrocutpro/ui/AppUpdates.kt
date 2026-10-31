package com.dronedirector.veyrocutpro.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * In-app updates: the app checks the published releases (on start, at most every six hours, and
 * from any gear → Updates), tells you when a new version is ready, shows what changed — for the
 * whole app or for one part — in every version since the one installed, and downloads and installs
 * it when you choose. An update always installs the whole app (Capture, the Photo editor and the
 * Video editor together). You choose which parts you want to hear about: an update that only changes
 * parts you've switched off doesn't notify you, and when you do update, those skipped changes are
 * listed too. The download and the install itself are done by [UpdatePlatform] (phone or PC).
 */
object AppUpdates {
    enum class State { IDLE, CHECKING, LATEST, AVAILABLE, DOWNLOADING, INSTALLING, FAILED }
    class Release(val version: String, val lines: List<String>)
    class Info(val version: String, val url: String, val size: Long, val page: String, val releases: List<Release>)

    var state by mutableStateOf(State.IDLE); private set
    var info by mutableStateOf<Info?>(null); private set
    var pct by mutableIntStateOf(-1); private set
    var error by mutableStateOf<String?>(null); private set
    /** Open the update window, for the whole app (ALL) or one part. */
    var show by mutableStateOf<NewsArea?>(null)
    /** Bumped when the notification choices change (for the switches). */
    var prefsVersion by mutableIntStateOf(0); private set

    private const val LAST = "upd_last_check"
    private fun prefs(c: Context) = c.getSharedPreferences("procam", Context.MODE_PRIVATE)

    fun auto(c: Context) = prefs(c).getBoolean("upd_auto", true)
    fun setAuto(c: Context, on: Boolean) { prefs(c).edit().putBoolean("upd_auto", on).apply(); prefsVersion++ }
    /** Whether updates that change [area] notify you. */
    fun follows(c: Context, area: NewsArea) = prefs(c).getBoolean("upd_follow_" + area.name, true)
    fun setFollows(c: Context, area: NewsArea, on: Boolean) { prefs(c).edit().putBoolean("upd_follow_" + area.name, on).apply(); prefsVersion++ }

    fun installed(c: Context): String = UpdatePlatform.currentVersion(c)

    /** On start: a quiet check when the last one is older than six hours. */
    suspend fun startCheck(c: Context) {
        if (!auto(c) || !UpdatePlatform.supported) return
        if (System.currentTimeMillis() - prefs(c).getLong(LAST, 0L) < 6 * 3600 * 1000L) return
        kotlinx.coroutines.delay(8000)
        check(c, quiet = true)
    }

    /** Looks for a newer version. [quiet]: only a short notice, and only for the parts you follow. */
    suspend fun check(c: Context, quiet: Boolean, area: NewsArea = NewsArea.ALL) {
        if (state == State.DOWNLOADING || state == State.INSTALLING || state == State.CHECKING) return
        state = State.CHECKING; error = null
        try {
            val found = withContext(Dispatchers.IO) { fetch(c) }
            prefs(c).edit().putLong(LAST, System.currentTimeMillis()).apply()
            info = found
            if (found == null) {
                state = State.LATEST
                if (!quiet) toast(c, I18n.tr("You have the latest version, {0}").replace("{0}", installed(c)))
                return
            }
            state = State.AVAILABLE
            if (!quiet) { show = area; return }
            if (wanted(c, found)) toast(c, I18n.tr("Version {0} is ready to install — gear → Updates").replace("{0}", found.version))
        } catch (t: Throwable) {
            state = State.FAILED
            error = t.message ?: t.toString()
            AppLog.w("Update check failed: $error")
            if (!quiet) toast(c, I18n.tr("Couldn't check for updates — check the internet connection and try again"))
        }
    }

    /** Does the new version change something you follow (or the app as a whole)? */
    private fun wanted(c: Context, i: Info): Boolean {
        val lines = i.releases.flatMap { it.lines }
        val parts = listOf(NewsArea.CAPTURE, NewsArea.PHOTO, NewsArea.VIDEO)
        return lines.any { l ->
            val about = parts.filter { WhatsNew.about(l, it) }
            about.isEmpty() || about.any { follows(c, it) }
        }
    }

    private fun toast(c: Context, s: String) = android.widget.Toast.makeText(c, s, android.widget.Toast.LENGTH_LONG).show()

    private fun fetch(c: Context): Info? {
        val conn = java.net.URL("https://api.github.com/repos/${UpdatePlatform.repo}/releases?per_page=100").openConnection() as java.net.HttpURLConnection
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.connectTimeout = 15000; conn.readTimeout = 20000
        if (conn.responseCode != 200) throw java.io.IOException("The update server answered ${conn.responseCode}")
        val text = conn.inputStream.use { String(it.readBytes()) }
        val arr = com.google.gson.JsonParser.parseString(text).asJsonArray
        val mine = installed(c)
        val newer = arr.mapNotNull { e ->
            val o = e.asJsonObject
            if (o.get("draft")?.asBoolean == true || o.get("prerelease")?.asBoolean == true) return@mapNotNull null
            val tag = o.get("tag_name")?.asString ?: return@mapNotNull null
            if (!tag.startsWith(UpdatePlatform.tagPrefix)) return@mapNotNull null
            val v = tag.removePrefix(UpdatePlatform.tagPrefix)
            if (compare(v, mine) <= 0) return@mapNotNull null
            o to v
        }.sortedWith { a, b -> compare(b.second, a.second) }
        val (top, ver) = newer.firstOrNull() ?: return null
        val assets = top.getAsJsonArray("assets").map { it.asJsonObject }.map { Triple(it.get("name").asString, it.get("browser_download_url").asString, it.get("size").asLong) }
        val pick = UpdatePlatform.pickAsset(assets) ?: return null
        val releases = newer.map { (o, v) -> Release(v, notes(o.get("body")?.takeIf { !it.isJsonNull }?.asString ?: "")) }
        return Info(ver, pick.second, pick.third, top.get("html_url")?.asString ?: "", releases)
    }

    /** The change lines of a release (its "- " bullet points). */
    private fun notes(body: String): List<String> = body.lines().map { it.trim() }.filter { it.startsWith("- ") }.map { it.removePrefix("- ").trim() }

    fun compare(a: String, b: String): Int {
        val pa = a.split('.', '-').map { it.toIntOrNull() ?: 0 }; val pb = b.split('.', '-').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) { val d = pa.getOrElse(i) { 0 } - pb.getOrElse(i) { 0 }; if (d != 0) return if (d > 0) 1 else -1 }
        return 0
    }

    /** Downloads the new version and starts installing it (the whole app). */
    suspend fun install(c: Context) {
        val i = info ?: return
        if (state == State.DOWNLOADING || state == State.INSTALLING) return
        state = State.DOWNLOADING; pct = 0; error = null
        try {
            val f = withContext(Dispatchers.IO) { download(c, i) }
            state = State.INSTALLING
            val problem = UpdatePlatform.install(c, f)
            if (problem != null) { state = State.FAILED; error = problem; AppLog.w("Update install: $problem") }
        } catch (t: Throwable) {
            state = State.FAILED; error = t.message ?: t.toString()
            AppLog.w("Update download failed: $error")
        }
    }

    /** The installer reported a problem. */
    fun failed(msg: String) { state = State.FAILED; error = msg; AppLog.w("Update install: $msg") }

    private fun download(c: Context, i: Info): File {
        val dir = File(c.cacheDir, "update").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val name = i.url.substringAfterLast('/').ifBlank { "update" }
        val out = File(dir, name)
        var conn = java.net.URL(i.url).openConnection() as java.net.HttpURLConnection
        conn.instanceFollowRedirects = true; conn.connectTimeout = 20000; conn.readTimeout = 60000
        // GitHub sends the file from another address.
        var hops = 0
        while (conn.responseCode in 300..399 && hops++ < 5) {
            val to = conn.getHeaderField("Location") ?: break
            conn = java.net.URL(to).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 20000; conn.readTimeout = 60000
        }
        if (conn.responseCode != 200) throw java.io.IOException("The download answered ${conn.responseCode}")
        val total = conn.contentLengthLong.takeIf { it > 0 } ?: i.size
        conn.inputStream.use { ins ->
            out.outputStream().use { os ->
                val buf = ByteArray(1 shl 16); var got = 0L
                while (true) {
                    val n = ins.read(buf); if (n < 0) break
                    os.write(buf, 0, n); got += n
                    if (total > 0) pct = (got * 100 / total).toInt().coerceIn(0, 99)
                }
            }
        }
        pct = 100
        return out
    }
}

// ------------------------------------------------------------------ screens

private val PARTS = listOf(NewsArea.CAPTURE to "Capture", NewsArea.PHOTO to "Photo editor", NewsArea.VIDEO to "Video editor")

/** Gear → Updates: the state, Check now, and the update window for this part ([area]) or the whole app. */
@Composable
fun UpdatesSection(area: NewsArea, onOpen: () -> Unit = {}) {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val s = AppUpdates.state
    val i = AppUpdates.info
    Column(
        Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Pro.Fg.copy(alpha = 0.06f)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(I18n.tr("UPDATES"), style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp))
        Text(
            when (s) {
                AppUpdates.State.CHECKING -> I18n.tr("Checking for updates…")
                AppUpdates.State.AVAILABLE -> I18n.tr("Version {0} is ready to install").replace("{0}", i?.version ?: "")
                AppUpdates.State.DOWNLOADING -> I18n.tr("Downloading version {0}, {1}%").replace("{0}", i?.version ?: "").replace("{1}", AppUpdates.pct.coerceAtLeast(0).toString())
                AppUpdates.State.INSTALLING -> I18n.tr(UpdatePlatform.installingText)
                AppUpdates.State.LATEST -> I18n.tr("You have the latest version, {0}").replace("{0}", AppUpdates.installed(ctx))
                AppUpdates.State.FAILED -> I18n.tr("The last update attempt failed: {0}").replace("{0}", AppUpdates.error ?: "")
                AppUpdates.State.IDLE -> I18n.tr("Installed version: {0}").replace("{0}", AppUpdates.installed(ctx))
            },
            color = if (s == AppUpdates.State.AVAILABLE) Pro.Amber else Pro.Fg, fontSize = 13.sp,
        )
        if (i != null && area != NewsArea.ALL && s == AppUpdates.State.AVAILABLE) {
            val mine = i.releases.flatMap { it.lines }.count { WhatsNew.about(it, area) }
            Text(
                if (mine == 0) I18n.tr("Nothing new for this part — the update changes other parts of the app")
                else I18n.tr("{0} change(s) for this part").replace("{0}", mine.toString()),
                color = Pro.Dim, fontSize = 12.sp,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                I18n.tr("CHECK NOW"), style = Pro.Label.copy(color = Color.White, fontSize = 11.sp),
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Pro.Fg.copy(alpha = 0.08f))
                    .clickable { scope.launch { AppUpdates.check(ctx, quiet = false, area = area) } }.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            if (i != null) Text(
                I18n.tr("WHAT'S NEW · INSTALL"), style = Pro.Label.copy(color = Color.Black, fontSize = 11.sp),
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Pro.Amber)
                    .clickable { onOpen(); AppUpdates.show = area }.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        Text(I18n.tr("An update always installs the whole app — Capture, the Photo editor and the Video editor. Your projects, photos and settings are kept."), color = Pro.Dim, fontSize = 11.sp)
    }
}

/** Start screen → gear: automatic checks and which parts' updates notify you. */
@Composable
fun UpdateChoices() {
    val ctx = LocalContext.current
    AppUpdates.prefsVersion
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(I18n.tr("Check for updates automatically"), color = Color.White, fontSize = 15.sp)
                Text(I18n.tr("When the app starts, at most every six hours"), color = Pro.Dim, fontSize = 12.sp)
            }
            Switch(checked = AppUpdates.auto(ctx), onCheckedChange = { AppUpdates.setAuto(ctx, it) }, colors = SwitchDefaults.colors(checkedTrackColor = Pro.Amber))
        }
        Text(I18n.tr("Tell me about updates for:"), color = Pro.Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        for ((a, label) in PARTS) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(I18n.tr(label), color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Switch(checked = AppUpdates.follows(ctx, a), onCheckedChange = { AppUpdates.setFollows(ctx, a, it) }, colors = SwitchDefaults.colors(checkedTrackColor = Pro.Amber))
        }
        Text(I18n.tr("Parts you switch off don't notify you. An update still installs the whole app, and the changes you skipped are listed when you update."), color = Pro.Dim, fontSize = 11.sp)
    }
}

/** The update window and the automatic check; once, at the top of the app. */
@Composable
fun UpdateHost() {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(Unit) { AppUpdates.startCheck(ctx.applicationContext) }
    val area = AppUpdates.show ?: return
    val i = AppUpdates.info ?: run { AppUpdates.show = null; return }
    val busy = AppUpdates.state == AppUpdates.State.DOWNLOADING || AppUpdates.state == AppUpdates.State.INSTALLING
    AlertDialog(
        onDismissRequest = { if (!busy) AppUpdates.show = null },
        containerColor = Pro.PanelSolid,
        title = { Text(I18n.tr("Update available"), color = Color.White) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column { Text(I18n.tr("Installed"), color = Pro.Dim, fontSize = 11.sp); Text(AppUpdates.installed(ctx), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                    Text("  →  ", color = Pro.Dim, fontSize = 16.sp)
                    Column { Text(I18n.tr("New"), color = Pro.Dim, fontSize = 11.sp); Text(i.version, color = Pro.Green, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                    if (i.size > 0) Text("   " + "%.0f MB".format(i.size / 1048576f), color = Pro.Dim, fontSize = 12.sp)
                }
                if (area != NewsArea.ALL) Text(I18n.tr("Changes for this part first; the rest of the app below."), color = Pro.Dim, fontSize = 12.sp)
                // Every version since the installed one (also the changes of parts you don't follow), newest open.
                i.releases.forEachIndexed { k, r ->
                    var open by remember(i) { mutableStateOf(k == 0) }
                    val mine = if (area == NewsArea.ALL) r.lines else r.lines.filter { WhatsNew.about(it, area) }
                    val rest = if (area == NewsArea.ALL) emptyList() else r.lines - mine.toSet()
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Pro.Fg.copy(alpha = 0.06f)).clickable { open = !open }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(r.version, color = if (open) Pro.Amber else Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(I18n.tr("{0} change(s)").replace("{0}", r.lines.size.toString()), color = Pro.Dim, fontSize = 11.sp)
                        }
                        Text(if (open) "▴" else "▾", color = Pro.Dim, fontSize = 16.sp)
                    }
                    if (open) Column(Modifier.padding(start = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (r.lines.isEmpty()) Text(I18n.tr("No notes for this version."), color = Pro.Dim, fontSize = 12.sp)
                        if (area != NewsArea.ALL && mine.isEmpty() && r.lines.isNotEmpty()) Text(I18n.tr("Nothing new for this part in this version."), color = Pro.Dim, fontSize = 12.sp)
                        mine.forEach { Text("• " + it.replace("**", ""), color = Pro.Fg, fontSize = 13.sp) }
                        if (rest.isNotEmpty()) {
                            Text(I18n.tr("Other parts of the app"), color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                            rest.forEach { Text("• " + it.replace("**", ""), color = Pro.Dim, fontSize = 12.sp) }
                        }
                    }
                }
                if (busy) {
                    Row(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Pro.Fg.copy(alpha = 0.1f))) {
                        Row(Modifier.fillMaxWidth(AppUpdates.pct.coerceIn(4, 100) / 100f).height(6.dp).background(Pro.Amber)) {}
                    }
                    Text(if (AppUpdates.state == AppUpdates.State.INSTALLING) I18n.tr(UpdatePlatform.installingText) else I18n.tr("Downloading… {0}%").replace("{0}", AppUpdates.pct.coerceAtLeast(0).toString()), color = Pro.Dim, fontSize = 12.sp)
                }
                AppUpdates.error?.takeIf { AppUpdates.state == AppUpdates.State.FAILED }?.let { Text(it, color = Pro.Red, fontSize = 12.sp) }
                Text(I18n.tr("The whole app is updated — Capture, the Photo editor and the Video editor. Your projects, photos and settings are kept.") + " " + I18n.tr(UpdatePlatform.installNote), color = Pro.Dim, fontSize = 11.sp)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = { scope.launch { AppUpdates.install(ctx) } }) {
                Text(if (busy) I18n.tr("INSTALLING…") else I18n.tr("DOWNLOAD AND INSTALL"), color = if (busy) Pro.Dim else Pro.Amber)
            }
        },
        dismissButton = { if (!busy) TextButton(onClick = { AppUpdates.show = null }) { Text(I18n.tr("LATER"), color = Pro.Dim) } },
    )
}

/** Start screen: a strip when a new version is ready. */
@Composable
fun UpdateStrip(modifier: Modifier = Modifier) {
    val i = AppUpdates.info ?: return
    if (AppUpdates.state != AppUpdates.State.AVAILABLE) return
    Text(
        I18n.tr("Version {0} is ready — see what's new and install").replace("{0}", i.version),
        style = Pro.Label.copy(color = Color.Black, fontSize = 12.sp),
        modifier = modifier.clip(RoundedCornerShape(50)).background(Pro.Amber).clickable { AppUpdates.show = NewsArea.ALL }.padding(horizontal = 16.dp, vertical = 10.dp),
    )
}
