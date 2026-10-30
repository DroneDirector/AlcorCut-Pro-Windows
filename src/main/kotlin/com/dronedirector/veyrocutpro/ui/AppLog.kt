package com.dronedirector.veyrocutpro.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The app's own log, kept on the device (never sent anywhere by itself): app starts, exports, problems and
 * crashes, with the app's recent system log lines when something goes wrong. Two files of up to 1 MB each
 * (the older one is replaced). Open it from the crash report (VIEW LOG) or gear → Troubleshooting → App log.
 */
object AppLog {
    private const val MAX = 1_000_000L
    @Volatile private var dir: File? = null
    private val lock = Any()
    private val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US)

    fun file(context: Context): File = File(File(context.filesDir, "logs").apply { mkdirs() }, "app-log.txt")
    private fun old(f: File) = File(f.parentFile, "app-log.1.txt")

    /** Once per process, at the app's start. */
    fun init(context: Context) {
        val f = file(context.applicationContext); dir = f.parentFile
        val v = try { context.packageManager.getPackageInfo(context.packageName, 0).versionName } catch (_: Throwable) { "?" }
        val proc = if (android.os.Build.VERSION.SDK_INT >= 28) android.app.Application.getProcessName() else "app"
        write("START", "AlcorCut Pro $v · Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}) · " +
            "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · process $proc")
    }

    fun i(what: String) = write("INFO", what)

    /** How much memory the app uses now, and how much the device has left. */
    fun memory(context: Context): String = try {
        val rt = Runtime.getRuntime()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val dm = android.os.Debug.MemoryInfo().also { android.os.Debug.getMemoryInfo(it) }
        "app ${dm.totalPss / 1024} MB (Java ${(rt.totalMemory() - rt.freeMemory()) / 1048576} MB of ${rt.maxMemory() / 1048576}, native ${android.os.Debug.getNativeHeapAllocatedSize() / 1048576} MB, " +
            "graphics ${(dm.getMemoryStat("summary.graphics")?.toIntOrNull() ?: 0) / 1024} MB) · device free ${mi.availMem / 1048576} of ${mi.totalMem / 1048576} MB" + (if (mi.lowMemory) " (LOW)" else "")
    } catch (_: Throwable) { "memory unknown" }
    fun w(what: String) = write("WARN", what)

    /** A problem or crash: the message, the error's stack and the app's last system log lines. */
    fun error(what: String, error: Throwable?, withSystemLog: Boolean = true) {
        val sw = java.io.StringWriter(); error?.printStackTrace(java.io.PrintWriter(sw))
        write("ERROR", what + (if (error != null) "\n" + sw.toString().take(12_000) else "") + (if (withSystemLog) systemLog()?.let { "\n--- system log (this app, last lines) ---\n$it" } ?: "" else ""))
    }

    /** This app's own recent lines from Android's log (an app may always read its own). */
    private fun systemLog(): String? = try {
        val p = ProcessBuilder("logcat", "-d", "-v", "time", "-t", "300", "--pid=${android.os.Process.myPid()}").redirectErrorStream(true).start()
        val done = if (android.os.Build.VERSION.SDK_INT >= 26) p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS) else true
        if (!done) { p.destroy(); null } else p.inputStream.bufferedReader().use { it.readText() }.takeLast(60_000)
    } catch (_: Throwable) { null }

    private fun write(level: String, msg: String) {
        val d = dir ?: return
        try {
            synchronized(lock) {
                val f = File(d, "app-log.txt")
                if (f.length() > MAX) { old(f).delete(); f.renameTo(old(f)) }
                f.appendText("${fmt.format(java.util.Date())} $level  $msg\n")
            }
        } catch (_: Throwable) {}
    }

    /** The whole log, older part first. */
    fun read(context: Context): String = synchronized(lock) {
        val f = file(context)
        (old(f).takeIf { it.exists() }?.readText() ?: "") + (f.takeIf { it.exists() }?.readText() ?: "")
    }

    fun clear(context: Context) = synchronized(lock) { val f = file(context); old(f).delete(); f.delete(); write("INFO", "Log cleared") }
}

/** The log on screen: newest at the bottom, with share, save a copy and clear. */
@Composable
fun AppLogDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var reload by remember { mutableStateOf(0) }
    val text by produceState("", reload) { value = withContext(Dispatchers.IO) { try { AppLog.read(context) } catch (_: Throwable) { "" } } }
    val lines = remember(text) { text.lines().takeLast(4000) }
    val list = rememberLazyListState()
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) list.scrollToItem(lines.lastIndex) }
    var note by remember { mutableStateOf<String?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) note = try { context.contentResolver.openOutputStream(uri)?.use { it.write(AppLog.read(context).toByteArray()) }; "Log saved" } catch (_: Throwable) { "Couldn't save the log" }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF0B0D12)).padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("App log", color = Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f))
                Text("CLOSE", style = Pro.Label.copy(color = Pro.Amber, fontSize = 12.sp), modifier = Modifier.clickable(onClick = onDismiss).padding(6.dp))
            }
            Text("Kept on this device only: app starts, exports, problems and crashes (no photos or videos). Share it with the developer when something goes wrong.", color = Pro.Dim, fontSize = 11.sp)
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF15181F)).padding(6.dp), state = list) {
                items(lines) { l ->
                    androidx.compose.material3.Text(l, fontSize = 10.sp, fontFamily = FontFamily.Monospace, lineHeight = 13.sp,
                        color = when { " ERROR " in l -> Color(0xFFFF6B6B); " WARN " in l -> Color(0xFFFFC857); " START " in l -> Color(0xFF7FD1FF); else -> Color(0xFFDDE3EA) })
                }
            }
            note?.let { Text(it, color = Pro.Amber, fontSize = 12.sp) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                @Composable fun b(t: String, a: () -> Unit) = Text(t, style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0x14FFFFFF)).clickable(onClick = a).padding(10.dp))
                b("SHARE") {
                    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "AlcorCut Pro app log"); putExtra(Intent.EXTRA_TEXT, AppLog.read(context).takeLast(400_000)) }
                    try { context.startActivity(Intent.createChooser(send, I18n.tr("Share the app log")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Throwable) {}
                }
                b("SAVE A COPY") { try { save.launch("AlcorCut-Pro-log.txt") } catch (_: Throwable) {} }
                b("CLEAR") { AppLog.clear(context); reload++; note = "Log cleared" }
            }
        }
    }
}
