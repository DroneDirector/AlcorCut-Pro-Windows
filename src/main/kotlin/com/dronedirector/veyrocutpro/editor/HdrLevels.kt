package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.ui.Pro
import com.dronedirector.veyrocutpro.ui.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow

/**
 * HDR levels in nits, read from the clip itself: one frame is decoded at full 10-bit (not the tone-mapped
 * preview), the luma code values are turned into display light with the HDR10 (PQ, SMPTE ST 2084) or HLG
 * (ITU-R BT.2100, 1000-nit reference display) curves, and drawn as a nits waveform with the usual guides:
 * 100 nits (SDR white), 203 nits (HDR reference white, ITU-R BT.2408) and 1000 nits. Own code.
 */
object HdrLevels {
    const val PQ = MediaFormat.COLOR_TRANSFER_ST2084
    const val HLG = MediaFormat.COLOR_TRANSFER_HLG

    class Result(
        val transfer: Int, val tenBit: Boolean,
        val peak: Float, val average: Float, val over203: Float, val over1000: Float,
        /** Waveform counts: [cols] × [rows], row 0 = 10 000 nits (PQ scale). */
        val wave: IntArray, val cols: Int, val rows: Int,
    )

    /** PQ signal (0..1) → nits. */
    fun pqToNits(e: Float): Float {
        val m1 = 0.1593017578125f; val m2 = 78.84375f; val c1 = 0.8359375f; val c2 = 18.8515625f; val c3 = 18.6875f
        val n = e.coerceIn(0f, 1f).pow(1f / m2)
        return 10000f * (max(n - c1, 0f) / (c2 - c3 * n)).pow(1f / m1)
    }

    /** Nits → PQ signal (0..1), used as the waveform's height scale. */
    fun nitsToPq(nits: Float): Float {
        val m1 = 0.1593017578125f; val m2 = 78.84375f; val c1 = 0.8359375f; val c2 = 18.8515625f; val c3 = 18.6875f
        val y = (nits / 10000f).coerceIn(0f, 1f).pow(m1)
        return ((c1 + c2 * y) / (1f + c3 * y)).pow(m2)
    }

    /** HLG signal (0..1) → nits on a 1000-nit display (inverse OETF, then the system gamma 1.2 on luminance). */
    fun hlgToNits(e: Float): Float {
        val a = 0.17883277f; val b = 0.28466892f; val c = 0.55991073f
        val x = e.coerceIn(0f, 1f)
        val scene = if (x <= 0.5f) x * x / 3f else (exp((x - c) / a) + b) / 12f
        return 1000f * scene.coerceIn(0f, 1f).pow(1.2f)
    }

    /** The clip's HDR transfer (PQ or HLG), or null for SDR / unknown. */
    fun transferOf(context: Context, uri: Uri): Int? = try {
        val ex = MediaExtractor(); ex.setDataSource(context, uri, null)
        val t = (0 until ex.trackCount).map { ex.getTrackFormat(it) }.firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
            ?.takeIf { it.containsKey(MediaFormat.KEY_COLOR_TRANSFER) }?.getInteger(MediaFormat.KEY_COLOR_TRANSFER)
        ex.release()
        t?.takeIf { it == PQ || it == HLG }
    } catch (_: Throwable) { null }

    /** Decodes the frame at [timeUs] and measures it. Null if this phone can't decode it to memory. */
    fun read(context: Context, uri: Uri, timeUs: Long, transfer: Int): Result? {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            ex.setDataSource(context, uri, null)
            val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true } ?: return null
            ex.selectTrack(track)
            val fmt = ex.getTrackFormat(track)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: return null
            ex.seekTo(timeUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            codec = MediaCodec.createDecoderByType(mime)
            // Ask for 10-bit P010 where the decoder offers it, else any YUV 4:2:0.
            val p010 = if (android.os.Build.VERSION.SDK_INT >= 29) MediaCodecInfo.CodecCapabilities.COLOR_FormatYUVP010 else -1
            val caps = try { codec.codecInfo.getCapabilitiesForType(mime).colorFormats.toList() } catch (_: Throwable) { emptyList() }
            fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT, if (p010 > 0 && p010 in caps) p010 else MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            codec.configure(fmt, null, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var loops = 0
            while (loops++ < 600) {
                if (!inputDone) {
                    val ii = codec.dequeueInputBuffer(5000)
                    if (ii >= 0) {
                        val buf = codec.getInputBuffer(ii) ?: continue
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0) { codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                        else { codec.queueInputBuffer(ii, 0, n, ex.sampleTime, 0); ex.advance() }
                    }
                }
                val oi = codec.dequeueOutputBuffer(info, 5000)
                if (oi >= 0) {
                    val last = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    if (info.presentationTimeUs >= timeUs - 20_000 || last) {
                        val img = codec.getOutputImage(oi)
                        val r = img?.let { measure(it, transfer) }
                        img?.close()
                        codec.releaseOutputBuffer(oi, false)
                        return r
                    }
                    codec.releaseOutputBuffer(oi, false)
                }
            }
            return null
        } catch (_: Throwable) {
            return null
        } finally {
            try { codec?.stop() } catch (_: Throwable) {}
            try { codec?.release() } catch (_: Throwable) {}
            try { ex.release() } catch (_: Throwable) {}
        }
    }

    private fun measure(img: android.media.Image, transfer: Int): Result {
        val plane = img.planes[0]
        val buf = plane.buffer
        val ps = plane.pixelStride; val rs = plane.rowStride
        val w = img.width; val h = img.height
        val tenBit = ps >= 2
        val cols = 256; val rows = 128
        val wave = IntArray(cols * rows)
        val stepX = max(1, w / cols); val stepY = max(1, h / 160)
        var peak = 0f; var sum = 0.0; var n = 0; var o203 = 0; var o1000 = 0
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val off = y * rs + x * ps
                // 10-bit P010: value in the top 10 bits of a little-endian 16-bit word; 8-bit: one byte.
                val code = if (tenBit) (((buf.get(off + 1).toInt() and 0xFF) shl 8) or (buf.get(off).toInt() and 0xFF)) ushr 6
                    else (buf.get(off).toInt() and 0xFF) shl 2
                val e = ((code - 64) / 876f).coerceIn(0f, 1f)   // limited (video) range
                val nits = if (transfer == PQ) pqToNits(e) else hlgToNits(e)
                if (nits > peak) peak = nits
                sum += nits; n++
                if (nits > 203f) o203++
                if (nits > 1000f) o1000++
                val cx = (x * cols / w).coerceIn(0, cols - 1)
                val ry = ((1f - nitsToPq(nits)) * (rows - 1)).toInt().coerceIn(0, rows - 1)
                wave[ry * cols + cx]++
                x += stepX
            }
            y += stepY
        }
        return Result(transfer, tenBit, peak, if (n > 0) (sum / n).toFloat() else 0f,
            if (n > 0) o203 * 100f / n else 0f, if (n > 0) o1000 * 100f / n else 0f, wave, cols, rows)
    }

    /** The waveform as a picture: brighter where more of the frame sits at that level. */
    fun waveBitmap(r: Result): Bitmap {
        val mx = (r.wave.maxOrNull() ?: 1).coerceAtLeast(1)
        val px = IntArray(r.cols * r.rows)
        for (i in px.indices) {
            val v = r.wave[i]
            if (v == 0) continue
            val k = (kotlin.math.ln(1f + v) / kotlin.math.ln(1f + mx)).coerceIn(0f, 1f)
            val a = (60 + 195 * k).toInt()
            px[i] = (a shl 24) or (((140 + 115 * k).toInt()) shl 16) or (((210 + 45 * k).toInt()) shl 8) or 255
        }
        return Bitmap.createBitmap(px, r.cols, r.rows, Bitmap.Config.ARGB_8888)
    }
}

/** Color → Input: HDR levels in nits for HDR clips (HLG / HDR10), measured at the playhead. */
@Composable
fun HdrLevelsSection(uri: Uri, timeUs: Long) {
    val context = LocalContext.current
    var transfer by remember(uri) { mutableStateOf<Int?>(null) }
    var checked by remember(uri) { mutableStateOf(false) }
    var result by remember(uri) { mutableStateOf<HdrLevels.Result?>(null) }
    var wave by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember(uri) { mutableStateOf(false) }
    LaunchedEffect(uri) { transfer = withContext(Dispatchers.IO) { HdrLevels.transferOf(context, uri) }; checked = true }
    val t = transfer
    if (!checked || t == null) return
    // Measure when the section is shown and whenever the playhead settles somewhere new.
    LaunchedEffect(uri, t, timeUs / 250_000) {
        kotlinx.coroutines.delay(300)
        busy = true
        val r = withContext(Dispatchers.Default) { HdrLevels.read(context, uri, timeUs, t) }
        busy = false
        if (r == null) failed = true else { failed = false; result = r; wave = HdrLevels.waveBitmap(r) }
    }
    SectionTitle("HDR LEVELS (NITS)")
    androidx.compose.material3.Text((if (t == HdrLevels.PQ) "HDR10 (PQ)" else "HLG") + if (busy) " · …" else "", color = Pro.Amber, fontSize = 12.sp)
    Text("Measured from the clip at the playhead (before your grade).", color = Pro.Dim, fontSize = 11.sp)
    if (failed && result == null) { Text("This phone can't read the frame at full HDR quality.", color = Pro.Dim, fontSize = 11.sp); return }
    val r = result ?: return
    Box(Modifier.fillMaxWidth().height(150.dp).padding(vertical = 6.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF0A0C10))) {
        wave?.let { androidx.compose.foundation.Image(it.asImageBitmap(), null, modifier = Modifier.fillMaxWidth().height(150.dp), contentScale = androidx.compose.ui.layout.ContentScale.FillBounds, filterQuality = androidx.compose.ui.graphics.FilterQuality.Low) }
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            for ((nits, col) in listOf(100f to Color.White.copy(alpha = 0.35f), 203f to Pro.Amber.copy(alpha = 0.8f), 1000f to Color(0xFFFF5A5A).copy(alpha = 0.8f), 4000f to com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.18f))) {
                val y = (1f - HdrLevels.nitsToPq(nits)) * size.height
                drawLine(col, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            drawRect(com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.12f), style = Stroke(1.dp.toPx()))
        }
        Column(Modifier.padding(start = 6.dp, top = 2.dp)) {
            for (l in listOf("4000", "1000", "203", "100")) Text(l, color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp, modifier = Modifier.padding(top = when (l) { "4000" -> 14.dp; "1000" -> 12.dp; "203" -> 12.dp; else -> 3.dp }))
        }
    }
    Row(Modifier.fillMaxWidth()) {
        @Composable fun stat(label: String, v: String, warn: Boolean = false) = Column(Modifier.weight(1f)) {
            androidx.compose.material3.Text(v, color = if (warn) Color(0xFFFF7A7A) else Color.White, fontSize = 14.sp)
            Text(label, color = Pro.Dim, fontSize = 10.sp)
        }
        stat("Peak", "%.0f nits".format(r.peak), r.peak > 1000f)
        stat("Average", "%.0f nits".format(r.average))
        stat("Above 203", "%.1f %%".format(r.over203))
        stat("Above 1000", "%.1f %%".format(r.over1000), r.over1000 > 0.5f)
    }
    Text("Guides: 100 nits = SDR white, 203 nits = HDR reference white (skin and paper sit around here), 1000 nits = what most HDR screens can show — keep important detail below it.", color = Pro.Dim, fontSize = 11.sp)
    if (!r.tenBit) Text("This phone gave an 8-bit frame, so levels are approximate.", color = Pro.Dim, fontSize = 11.sp)
}
