package com.dronedirector.veyrocutpro.ui

import android.content.Context
import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import java.io.File

/**
 * Keeps the details of the last crash on the phone (never sent anywhere by itself). On the next
 * start the app shows them once, so the user can share the report with the developer.
 */
object CrashReport {
    private fun file(context: Context) = File(context.filesDir, "last_crash.txt")
    /** Set while an unsaved edit is being recovered (cleared once the editor has run a while). */
    private fun marker(context: Context) = File(context.filesDir, "recovering.flag")

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val version = try { app.packageManager.getPackageInfo(app.packageName, 0).versionName } catch (_: Throwable) { "?" }
                val sw = java.io.StringWriter()
                error.printStackTrace(java.io.PrintWriter(sw))
                val during = if (marker(app).exists()) "while recovering an unsaved edit\n" else ""
                AppLog.error("CRASH on thread ${thread.name}", error)
                SafeMode.crashed(app)
                file(app).writeText(
                    "AlcorCut Pro $version · Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}) · " +
                        "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n" +
                        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date()) + " · thread ${thread.name}\n" +
                        during + "\n" + sw.toString().take(12_000),
                )
            } catch (_: Throwable) {}
            previous?.uncaughtException(thread, error)
        }
    }

    /** Bumped when a problem report is written while the app runs (the dialog then shows it at once). */
    var version by androidx.compose.runtime.mutableIntStateOf(0)
        private set

    private fun header(app: Context, what: String): String {
        val v = try { app.packageManager.getPackageInfo(app.packageName, 0).versionName } catch (_: Throwable) { "?" }
        return "AlcorCut Pro $v · Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}) · " +
            "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (${android.os.Build.HARDWARE})\n" +
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date()) + " · $what\n"
    }

    /** A problem that didn't close the app (e.g. a failed export): saved and shown now, with its full details. */
    fun problem(context: Context, what: String, error: Throwable?, extra: String = "") {
        try {
            val app = context.applicationContext
            val sw = java.io.StringWriter()
            error?.printStackTrace(java.io.PrintWriter(sw))
            file(app).writeText(header(app, what) + extra + "\n" + sw.toString().take(12_000))
            AppLog.error("PROBLEM: $what" + (if (extra.isNotBlank()) "\n" + extra.trim() else ""), error)
            version++
        } catch (_: Throwable) {}
    }

    /**
     * Android 11+: if the app was closed by a crash in native code (no Java report), what Android
     * recorded about it — so those crashes can be reported too.
     */
    fun checkNativeExit(context: Context) {
        if (android.os.Build.VERSION.SDK_INT < 30 || file(context).exists()) return
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val p = context.getSharedPreferences("procam", Context.MODE_PRIVATE)
            val seen = p.getLong("exit_seen", 0L)
            val e = am.getHistoricalProcessExitReasons(context.packageName, 0, 5)
                .firstOrNull { it.timestamp > seen && (it.reason == android.app.ApplicationExitInfo.REASON_CRASH_NATIVE || it.reason == android.app.ApplicationExitInfo.REASON_CRASH || (it.reason == android.app.ApplicationExitInfo.REASON_LOW_MEMORY &&
                    // Closed while in the background (another app was open): normal on Android, not a problem.
                    it.importance <= android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE)) }
            am.getHistoricalProcessExitReasons(context.packageName, 0, 1).firstOrNull()?.let { p.edit().putLong("exit_seen", it.timestamp).apply() }
            if (e == null || seen == 0L) return
            val kind = when (e.reason) {
                android.app.ApplicationExitInfo.REASON_CRASH_NATIVE -> "native crash"
                android.app.ApplicationExitInfo.REASON_LOW_MEMORY -> "closed by Android: low memory"
                else -> "crash"
            }
            // Readable parts of the native trace (library and function names).
            val trace = try {
                e.traceInputStream?.use { ins ->
                    String(ins.readBytes().take(400_000).toByteArray(), Charsets.ISO_8859_1)
                        .split(Regex("[^\\x20-\\x7E]+")).filter { it.length > 6 && (".so" in it || "(" in it || "Abort" in it || "signal" in it) }
                        .distinct().take(60).joinToString("\n")
                }
            } catch (_: Throwable) { null }
            file(context).writeText(header(context, "$kind (process ${e.processName}, ${java.util.Date(e.timestamp)})") + (e.description ?: "") + "\n\n" + (trace ?: ""))
            AppLog.error("Last run ended with a $kind (process ${e.processName}, ${java.util.Date(e.timestamp)}): ${e.description ?: ""}\n${trace ?: ""}", null, withSystemLog = false)
        } catch (_: Throwable) {}
    }

    fun pending(context: Context): String? = file(context).takeIf { it.exists() }?.let { try { it.readText() } catch (_: Throwable) { null } }
    fun clear(context: Context) { try { file(context).delete() } catch (_: Throwable) {} }

    fun startRecovering(context: Context) { try { marker(context).writeText("1") } catch (_: Throwable) {} }
    fun doneRecovering(context: Context) { try { marker(context).delete() } catch (_: Throwable) {} }
    /** True when the app crashed during the last recovery attempt. */
    fun recoveryCrashed(context: Context): Boolean = marker(context).exists()
}

/** Shown once after a crash: share or copy the report, or dismiss it. */
@Composable
fun CrashReportDialog() {
    val context = LocalContext.current
    var report by remember { mutableStateOf(CrashReport.run { checkNativeExit(context); pending(context) }) }
    androidx.compose.runtime.LaunchedEffect(CrashReport.version) { if (CrashReport.version > 0) report = CrashReport.pending(context) }
    val isProblem = report?.lineSequence()?.drop(1)?.firstOrNull()?.contains("export") == true
    val r = report ?: return
    var showLog by remember { mutableStateOf(false) }
    if (showLog) { AppLogDialog { showLog = false }; return }
    AlertDialog(
        onDismissRequest = { CrashReport.clear(context); report = null },
        containerColor = Pro.PanelSolid,
        title = { Text(if (isProblem) "Export problem — details" else "AlcorCut Pro closed unexpectedly", color = Color.White) },
        text = {
            Text(
                "Sorry about that. A crash report was saved on this phone (it contains no photos or videos — only what went wrong). " +
                    "Share it with the developer so it can be fixed.\n\n" + r.lineSequence().take(6).joinToString("\n"),
                color = Pro.Dim, fontSize = 12.sp,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "AlcorCut Pro crash report"); putExtra(Intent.EXTRA_TEXT, r) }
                try { context.startActivity(Intent.createChooser(send, I18n.tr("Share crash report")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Throwable) {}
                CrashReport.clear(context); report = null
            }) { Text("SHARE REPORT", color = Pro.Amber) }
        },
        dismissButton = {
            Row2(
                { TextButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("AlcorCut Pro crash report", r))
                    CrashReport.clear(context); report = null
                }) { Text("COPY", color = Pro.Amber) } },
                { TextButton(onClick = { showLog = true }) { Text("VIEW LOG", color = Pro.Amber) } },
                { TextButton(onClick = { CrashReport.clear(context); report = null }) { Text("DISMISS", color = Pro.Dim) } },
            )
        },
    )
}

@Composable
private fun Row2(vararg items: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Row { items.forEach { it() } }
}
