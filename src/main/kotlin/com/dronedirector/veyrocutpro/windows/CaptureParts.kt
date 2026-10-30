package com.dronedirector.veyrocutpro.camera

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri

/** What this PC can do for recordings (a PC is plugged in and has room to work). */
object DeviceClass {
    const val WHY = "This PC can't keep up with that setting"
    @Volatile var proAudio = true; private set
    fun check(c: Context) {}
    fun extremeBitrate(high: Int): Boolean = Runtime.getRuntime().availableProcessors() >= 6
}

/**
 * Where photos and videos were taken, from Windows' location service (Settings → Privacy → Location
 * must allow desktop apps). Asked once a minute while Capture is open with Save location on. Own code.
 */
object CaptureLocation {
    class Fix(val latitude: Double, val longitude: Double, val time: Long)
    @Volatile private var last: Fix? = null
    @Volatile private var thread: Thread? = null

    fun allowed(context: Context): Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows")

    fun start(context: Context) {
        if (thread != null || !CaptureExtras.location || !allowed(context)) return
        thread = Thread({
            while (thread != null) {
                read()?.let { last = it }
                try { Thread.sleep(60_000) } catch (_: InterruptedException) { break }
            }
        }, "location").apply { isDaemon = true; start() }
    }

    fun stop(context: Context) { val t = thread; thread = null; t?.interrupt() }

    private fun read(): Fix? = try {
        val script = "Add-Type -AssemblyName System.Device; \$w = New-Object System.Device.Location.GeoCoordinateWatcher; \$w.Start(); " +
            "\$i = 0; while ((\$w.Status -ne 'Ready') -and (\$i -lt 40)) { Start-Sleep -Milliseconds 250; \$i++ }; " +
            "\$p = \$w.Position.Location; if (-not \$p.IsUnknown) { '{0};{1}' -f \$p.Latitude.ToString([cultureinfo]::InvariantCulture), \$p.Longitude.ToString([cultureinfo]::InvariantCulture) }"
        val p = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText().trim(); p.waitFor()
        val parts = out.lines().lastOrNull { ';' in it }?.split(';')
        val la = parts?.getOrNull(0)?.toDoubleOrNull(); val lo = parts?.getOrNull(1)?.toDoubleOrNull()
        if (la != null && lo != null) Fix(la, lo, System.currentTimeMillis()) else null
    } catch (_: Throwable) { null }

    fun current(context: Context): Fix? {
        if (!CaptureExtras.location || !allowed(context)) return null
        start(context)
        return last?.takeIf { System.currentTimeMillis() - it.time < 10 * 60_000L }
    }
}

/** Re-grading a video from the Capture gallery comes with the video editor on Windows. */
class VideoGradeExport(
    private val context: Context, private val source: Uri, grade: Grade,
    private val onProgress: (Int) -> Unit, private val onDone: (Uri?, String?) -> Unit,
) {
    fun start() { onDone(null, "Grading videos comes with the video editor on Windows") }
    fun cancel() {}
}

/** The stop-motion frames as a video (each picture fitted into the first one's shape), with the PC's encoder. */
suspend fun com.dronedirector.veyrocutpro.ui.StopMotion.makeVideo(c: Context, list: List<Uri>, fps: Int): Uri? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    if (list.isEmpty()) return@withContext null
    val first = GradeIO.decode(c, list[0], 1920) ?: return@withContext null
    val w = first.width / 2 * 2; val h = first.height / 2 * 2
    val out = java.io.File(c.cacheDir, "stopmotion_${System.currentTimeMillis()}.mp4")
    val rec = try { WinRecorder(out, w, h, fps.toFloat(), 12_000_000, false, false, { 0 }, { false }, { _, _, _ -> }, timing = 1, outFps = fps.toFloat()) } catch (_: Throwable) { return@withContext null }
    val px = IntArray(w * h)
    for (u in list) {
        val b = GradeIO.decode(c, u, 1920) ?: continue
        // Fill the frame (crop the overflow), like the phone does.
        val k = maxOf(w.toFloat() / b.width, h.toFloat() / b.height)
        val sw = (w / k).toInt().coerceIn(1, b.width); val sh = (h / k).toInt().coerceIn(1, b.height)
        val crop = Bitmap.createBitmap(b, (b.width - sw) / 2, (b.height - sh) / 2, sw, sh)
        val fit = Bitmap.createScaledBitmap(crop, w, h, true)
        fit.getPixels(px, 0, w, 0, 0, w, h)
        rec.frameWait(px)
    }
    val f = rec.stop() ?: return@withContext null
    GradeIO.publishVideo(c, f, StorageArea.VIDEO).also { out.delete() }
}
