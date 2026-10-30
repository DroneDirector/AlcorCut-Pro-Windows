package com.dronedirector.veyrocutpro.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip

/**
 * Gear → Preferences → Audio hardware: which microphone and which speakers / headphones / interface
 * AlcorCut Pro uses on this PC (Windows' own choice unless you pick one). Own code.
 */
object AudioHardware {
    private const val PREFS = "procam"
    var version by androidx.compose.runtime.mutableIntStateOf(0)
        private set
    var notice by androidx.compose.runtime.mutableStateOf<String?>(null)
    @Volatile private var appCtx: android.content.Context? = null

    fun typeName(t: Int): String = "sound device"
    fun key(d: android.media.AudioDeviceInfo) = d.mixerName
    fun label(d: android.media.AudioDeviceInfo) = d.mixerName
    fun devices(c: android.content.Context, inputs: Boolean): List<android.media.AudioDeviceInfo> = android.media.AudioManager.list(inputs)
    private fun pref(inputs: Boolean) = if (inputs) "audio_in" else "audio_out"
    fun chosenKey(c: android.content.Context, inputs: Boolean): String? = c.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getString(pref(inputs), null)
    fun chosen(c: android.content.Context, inputs: Boolean): android.media.AudioDeviceInfo? = chosenKey(c, inputs)?.let { k -> devices(c, inputs).firstOrNull { key(it) == k } }

    fun set(c: android.content.Context, inputs: Boolean, d: android.media.AudioDeviceInfo?) {
        c.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().apply { if (d == null) remove(pref(inputs)) else putString(pref(inputs), key(d)) }.apply()
        apply(c)
        version++
    }
    private fun apply(c: android.content.Context) {
        android.media.AudioRecord.preferredInput = chosen(c, true)?.mixerName
        android.media.AudioTrack.preferredOutput = chosen(c, false)?.mixerName
    }
    fun applyTo(r: android.media.AudioRecord) { val c = appCtx ?: return; try { r.setPreferredDevice(chosen(c, true)) } catch (_: Throwable) {} }

    /** Remembers the choice; when a chosen device is gone at start, says so once and uses Windows' default. */
    fun start(c: android.content.Context) {
        if (appCtx != null) return
        appCtx = c.applicationContext
        for (inputs in listOf(true, false)) {
            val k = chosenKey(c, inputs) ?: continue
            if (devices(c, inputs).none { key(it) == k }) {
                c.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().remove(pref(inputs)).apply()
                notice = "$k isn't connected. AlcorCut Pro uses Windows' default " + (if (inputs) "microphone" else "speakers / headphones") + " again."
            }
        }
        apply(c)
    }
}

@Composable
fun AudioHardwareNotice() {
    val n = AudioHardware.notice ?: return
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { AudioHardware.notice = null }, containerColor = panelHere(),
        title = { Text("Audio device disconnected", color = androidx.compose.ui.graphics.Color.White) },
        text = { Text(n, color = Pro.Dim) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { AudioHardware.notice = null }) { Text("OK", color = Pro.Amber) } },
    )
}

@Composable
fun AudioHardwareSettings() {
    val context = androidx.compose.ui.platform.LocalContext.current
    remember { AudioHardware.start(context); 0 }
    val v = AudioHardware.version
    Column(Modifier.fillMaxWidth()) {
        Text("Pick what AlcorCut Pro records from and plays through. When it isn't connected, the app uses Windows' default and tells you.", color = Pro.Dim, fontSize = 12.sp)
        for (inputs in listOf(true, false)) {
            Text(if (inputs) "MICROPHONE" else "PLAYBACK", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
            val list = remember(v) { AudioHardware.devices(context, inputs) }
            val cur = remember(v) { AudioHardware.chosenKey(context, inputs) }
            @Composable
            fun Opt(label: String, on: Boolean, pick: () -> Unit) {
                Text(label, color = if (on) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White, fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .background(if (on) Pro.Amber else fgHere().copy(alpha = 0.06f)).clickable(onClick = pick).padding(10.dp))
            }
            Opt("Automatic (Windows' default)", cur == null) { AudioHardware.set(context, inputs, null) }
            for (d in list) Opt(AudioHardware.label(d), cur == AudioHardware.key(d)) { AudioHardware.set(context, inputs, d) }
        }
    }
}

/** This PC's hardware, for System Info and support. */
object SystemInfo {
    fun collect(c: android.content.Context): List<Pair<String, String>> {
        val rt = Runtime.getRuntime()
        val os = java.lang.management.ManagementFactory.getOperatingSystemMXBean()
        val total = try { (os as com.sun.management.OperatingSystemMXBean).totalMemorySize } catch (_: Throwable) { 0L }
        val free = try { (os as com.sun.management.OperatingSystemMXBean).freeMemorySize } catch (_: Throwable) { 0L }
        val gpu = try {
            java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.joinToString(" · ") { d ->
                "${d.displayMode.width}×${d.displayMode.height} @ ${d.displayMode.refreshRate} Hz"
            }
        } catch (_: Throwable) { "" }
        fun gb(b: Long) = "%.1f GB".format(b / 1_073_741_824.0)
        return listOfNotNull(
            "Windows" to "${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})",
            "Processor" to (System.getenv("PROCESSOR_IDENTIFIER") ?: "") + " · ${rt.availableProcessors()} threads",
            if (total > 0) "Memory" to "${gb(total)} (${gb(free)} free)" else null,
            "App memory" to "up to ${gb(rt.maxMemory())}",
            if (gpu.isNotEmpty()) "Screens" to gpu else null,
            "Java" to "${System.getProperty("java.vendor")} ${System.getProperty("java.version")}",
            "Drawing" to drawingApi(),
        )
    }
    /** How the window is drawn: on the graphics card (Direct3D / OpenGL) or, slowly, by the processor. */
    fun drawingApi(): String {
        val api = try { (com.dronedirector.veyrocutpro.TaskbarProgress.window as? androidx.compose.ui.awt.ComposeWindow)?.renderApi?.name } catch (_: Throwable) { null } ?: "unknown"
        return when {
            api.startsWith("SOFTWARE") -> "processor ($api) — the graphics card isn't being used, so the app is slow. Updating the graphics driver usually fixes this."
            api == "DIRECT3D" -> "graphics card (Direct3D)"
            api == "OPENGL" -> "graphics card (OpenGL)"
            else -> api
        }
    }
    fun asText(rows: List<Pair<String, String>>): String = rows.joinToString("\n") { "${it.first}: ${it.second}" }
}

/** Gear → System Info: this PC's processor, memory, graphics and Windows version. */
@Composable
fun SystemInfoSection() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val rows = remember { SystemInfo.collect(ctx) }
    Column(Modifier.fillMaxWidth()) {
        rows.forEach { (k, v) ->
            Text(k, style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(top = 8.dp))
            Text(v, color = Pro.Fg, fontSize = 13.sp)
        }
    }
}

/** Help → Proof of purchase: the Windows version (sold separately later). */
@Composable
fun ProofOfPurchaseDialog(onDismiss: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val v = remember { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (_: Throwable) { "" } }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss, containerColor = Pro.PanelSolid,
        title = { Text("Proof of purchase", color = androidx.compose.ui.graphics.Color.White) },
        text = { Text("AlcorCut Pro for Windows $v. This is a test version: every feature is open and nothing has to be bought.", color = Pro.Dim) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("OK", color = Pro.Amber) } },
    )
}
