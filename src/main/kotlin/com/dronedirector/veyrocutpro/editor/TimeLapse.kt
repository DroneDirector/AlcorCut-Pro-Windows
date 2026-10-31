package com.dronedirector.veyrocutpro.editor

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.camera.GradeIO
import com.dronedirector.veyrocutpro.ui.Pro
import com.dronedirector.veyrocutpro.ui.Text
import com.dronedirector.veyrocutpro.ui.hintedScroll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Time-lapse from a photo sequence (Library → select the frames → Time-lapse): one look for the whole
 * sequence, or a start look blending into an end look (day to night), an exposure ramp, deflicker
 * (each frame's brightness evened out against its neighbours) and an MP4 at the frame rate you pick.
 */
object TimeLapse {
    class Request(val frames: List<Uri>, val fps: Int, val longSide: Int, val aspect: Float, val start: ProGrade, val end: ProGrade?, val rampEv: Float, val deflicker: Boolean)

    private fun luma(b: Bitmap): Double {
        val s = Bitmap.createScaledBitmap(b, 64, 64, true)
        val px = IntArray(64 * 64); s.getPixels(px, 0, 64, 0, 0, 64, 64); if (s !== b) s.recycle()
        var sum = 0.0
        for (c in px) { val r = ((c shr 16) and 255) / 255.0; val g = ((c shr 8) and 255) / 255.0; val bl = (c and 255) / 255.0
            sum += Math.pow(0.2126 * r + 0.7152 * g + 0.0722 * bl, 2.2) }
        return (sum / px.size).coerceAtLeast(1e-5)
    }

    /** Builds the video in Movies/AlcorCut Pro; [progress] (done, total, what). */
    fun render(context: Context, r: Request, progress: (Int, Int, String) -> Unit, stop: () -> Boolean): Uri? {
        val n = r.frames.size
        if (n < 2) return null
        // Deflicker: each frame's exposure offset that brings it to the moving average of its neighbours.
        val offs = FloatArray(n)
        if (r.deflicker) {
            val lum = DoubleArray(n)
            for (i in 0 until n) {
                if (stop()) return null
                progress(i, n, "Measuring brightness")
                lum[i] = GradeIO.decode(context, r.frames[i], 256)?.let { b -> luma(b).also { b.recycle() } } ?: Double.NaN
            }
            val win = 7
            for (i in 0 until n) {
                if (lum[i].isNaN()) continue
                var s = 0.0; var c = 0
                for (k in maxOf(0, i - win)..minOf(n - 1, i + win)) if (!lum[k].isNaN()) { s += Math.log(lum[k]); c++ }
                offs[i] = ((s / c - Math.log(lum[i])) / Math.log(2.0)).toFloat().coerceIn(-2f, 2f)
            }
        }
        val first = GradeIO.decode(context, r.frames[0], r.longSide) ?: return null
        val aspect = if (r.aspect > 0f) r.aspect else first.width.toFloat() / first.height
        first.recycle()
        val w0 = if (aspect >= 1f) r.longSide else (r.longSide * aspect).toInt()
        val h0 = if (aspect >= 1f) (r.longSide / aspect).toInt() else r.longSide
        val w = w0 / 2 * 2; val h = h0 / 2 * 2
        val tmp = File(context.cacheDir, "timelapse_${System.currentTimeMillis()}.mp4")
        val enc = FrameEncoder(tmp, w, h, r.fps)
        try {
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
            for (i in 0 until n) {
                if (stop()) { enc.cancel(); tmp.delete(); return null }
                progress(i, n, "Rendering")
                val src = GradeIO.decode(context, r.frames[i], maxOf(w, h)) ?: continue
                val t = i / (n - 1f)
                val ev = offs[i] + r.rampEv * t
                fun look(g: ProGrade): Bitmap { val gg = g.copy(exposure = g.exposure + ev); return if (gg.isIdentity) src else ImageOps.render(src, gg, Detail(), Geometry(), gg.tables()) }
                val a = look(r.start)
                val frame = if (r.end == null) a else {
                    val b = look(r.end)
                    // The start look blending into the end look over the sequence.
                    val out = a.copy(Bitmap.Config.ARGB_8888, true)
                    android.graphics.Canvas(out).drawBitmap(b, 0f, 0f, Paint().apply { alpha = (t * 255).toInt() })
                    if (b !== src) b.recycle()
                    out
                }
                enc.frame { c ->
                    // Fill the frame, cropping what doesn't fit.
                    val k = maxOf(w.toFloat() / frame.width, h.toFloat() / frame.height)
                    val sw = (w / k).toInt(); val sh = (h / k).toInt()
                    val sx = (frame.width - sw) / 2; val sy = (frame.height - sh) / 2
                    c.drawBitmap(frame, Rect(sx, sy, sx + sw, sy + sh), Rect(0, 0, w, h), paint)
                }
                if (frame !== a) frame.recycle()
                if (a !== src) a.recycle()
                src.recycle()
            }
            enc.finish()
        } catch (t: Throwable) {
            com.dronedirector.veyrocutpro.ui.AppLog.error("Time-lapse failed", t, false); enc.cancel(); tmp.delete(); return null
        }
        progress(n, n, "Saving")
        val name = "TIMELAPSE_" + java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date()) + ".mp4"
        val cv = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name); put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/AlcorCut Pro")
        }
        val u = try { context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv) } catch (_: Throwable) { null }
        val ok = u != null && try { context.contentResolver.openOutputStream(u)?.use { os -> tmp.inputStream().use { it.copyTo(os) } } != null } catch (_: Throwable) { false }
        tmp.delete()
        com.dronedirector.veyrocutpro.ui.AppLog.i("Time-lapse: $n frames at ${r.fps} fps, ${w}x$h → " + if (ok) name else "not saved")
        return if (ok) u else null
    }
}

/** Frames drawn with a Canvas into an H.264 encoder, timed at [fps]. */
class FrameEncoder(private val file: File, val w: Int, val h: Int, private val fps: Int) {
    private val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
    private val surface: android.view.Surface
    private val muxer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private var track = -1
    private var count = 0L
    private var written = 0L
    private val info = MediaCodec.BufferInfo()

    init {
        val f = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, (w.toLong() * h * fps * 0.2).toInt().coerceIn(4_000_000, 120_000_000))
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        codec.configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        surface = codec.createInputSurface()
        codec.start()
    }

    private fun drain(end: Boolean) {
        while (true) {
            val i = codec.dequeueOutputBuffer(info, if (end) 20_000 else 0)
            when {
                i == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!end) return
                i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { track = muxer.addTrack(codec.outputFormat); muxer.start() }
                i >= 0 -> {
                    val buf = codec.getOutputBuffer(i)
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (info.size > 0 && track >= 0 && buf != null) {
                        // Even timing: frame number × frame length (the canvas gives no timestamps of its own).
                        info.presentationTimeUs = written * 1_000_000L / fps; written++
                        muxer.writeSampleData(track, buf, info)
                    }
                    codec.releaseOutputBuffer(i, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    fun frame(draw: (android.graphics.Canvas) -> Unit) {
        val c = if (Build.VERSION.SDK_INT >= 23) surface.lockHardwareCanvas() else surface.lockCanvas(null)
        try { draw(c) } finally { surface.unlockCanvasAndPost(c) }
        count++
        // Let the encoder keep up (it has only a few input slots).
        Thread.sleep(8)
        drain(false)
    }

    fun finish() {
        codec.signalEndOfInputStream()
        drain(true)
        cancel()
    }

    fun cancel() {
        try { codec.stop() } catch (_: Throwable) {}
        try { codec.release() } catch (_: Throwable) {}
        try { surface.release() } catch (_: Throwable) {}
        try { if (track >= 0) muxer.stop() } catch (_: Throwable) {}
        try { muxer.release() } catch (_: Throwable) {}
    }
}

/** Settings for a time-lapse of [frames] (in the order of their dates). */
@Composable
fun TimeLapseDialog(frames: List<Uri>, onDone: (Uri?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    UserPresets.init(context)
    var fps by remember { mutableStateOf(1) }
    var res by remember { mutableStateOf(0) }
    var shape by remember { mutableStateOf(0) }
    var start by remember { mutableStateOf(-1) }
    var end by remember { mutableStateOf(-2) }
    var ramp by remember { mutableStateOf(0f) }
    var deflicker by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf<String?>(null) }
    var stop by remember { mutableStateOf(false) }
    val fpsList = listOf(24, 25, 30, 60)
    val presets = UserPresets.list
    fun gradeOf(i: Int): ProGrade? = if (i == -1) ProGrade() else presets.getOrNull(i)?.grade
    AlertDialog(onDismissRequest = { if (busy == null) onDone(null) }, containerColor = Ed.Panel,
        title = { Text("Time-lapse video", color = Pro.Fg) },
        text = {
            Column(Modifier.hintedScroll(horizontal = false)) {
                Text("${frames.size} frames → ${String.format(java.util.Locale.US, "%.1f", frames.size / fpsList[fps].toFloat())} s of video.", color = Pro.Dim, fontSize = 12.sp)
                Text("Frame rate", color = Pro.Fg, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                Chips(fpsList.map { "$it fps" }, fps) { fps = it }
                Text("Size", color = Pro.Fg, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                Chips(listOf("HD 1920", "4K 3840"), res) { res = it }
                Chips(listOf("16:9", "Photo's own", "9:16", "1:1"), shape) { shape = it }
                Text("Look", color = Pro.Fg, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                Chips(listOf("None") + presets.map { it.name }, start + 1) { start = it - 1 }
                Text("Blend into (end of the sequence)", color = Pro.Fg, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                Chips(listOf("Same look", "None") + presets.map { it.name }, end + 2) { end = it - 2 }
                EdSlider("Exposure ramp (start → end)", ramp, -3f..3f, 0f, { v -> String.format(java.util.Locale.US, "%+.1f EV", v) }) { ramp = it }
                Chips(listOf("Deflicker on", "Off"), if (deflicker) 0 else 1) { deflicker = it == 0 }
                busy?.let { Text(it, color = Pro.Amber, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (busy != null) { stop = true; return@TextButton }
                stop = false; busy = "Starting…"
                val r = TimeLapse.Request(frames, fpsList[fps], if (res == 0) 1920 else 3840, listOf(16f / 9f, 0f, 9f / 16f, 1f)[shape],
                    gradeOf(start) ?: ProGrade(), if (end == -2) null else gradeOf(end), ramp, deflicker)
                scope.launch {
                    val u = withContext(Dispatchers.Default) { try { TimeLapse.render(context, r, { d, t, what -> busy = "$what… $d of $t" }) { stop } } catch (_: Throwable) { null } }
                    busy = null
                    onDone(u)
                }
            }) { Text(if (busy == null) "MAKE VIDEO" else "STOP", color = Pro.Amber) }
        },
        dismissButton = { if (busy == null) TextButton(onClick = { onDone(null) }) { Text("CANCEL", color = Pro.Dim) } },
    )
}
