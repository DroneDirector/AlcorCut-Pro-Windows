package com.dronedirector.veyrocutpro.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Gear → Preferences → Audio hardware: on Windows the sound devices are chosen in Windows itself. */
@Composable
fun AudioHardwareSettings() {
    val devices = remember {
        try {
            javax.sound.sampled.AudioSystem.getMixerInfo().map { it.name }.filter { n -> !n.startsWith("Port ") && !n.contains("Primary Sound") }.distinct()
        } catch (_: Throwable) { emptyList() }
    }
    Column(Modifier.fillMaxWidth()) {
        Text("AlcorCut Pro plays and records through the devices chosen in Windows (Settings → System → Sound). Change them there and the app follows.", color = Pro.Dim, fontSize = 12.sp)
        if (devices.isNotEmpty()) {
            Text("SOUND DEVICES ON THIS PC", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
            devices.forEach { Text("•  $it", color = Pro.Fg, fontSize = 13.sp) }
        }
    }
}

/** Shown on the phone when a chosen sound device is unplugged; Windows tells you itself. */
@Composable
fun AudioHardwareNotice() {}

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
