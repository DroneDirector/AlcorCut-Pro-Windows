package com.dronedirector.veyrocutpro.editor

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.dronedirector.veyrocutpro.camera.Storage
import com.dronedirector.veyrocutpro.camera.StorageArea
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * CineForm export (Deliver → Codec → CineForm): a professional editing codec in a .mov, like the
 * intermediate formats desktop editors use. 10-bit 4:2:2 wavelet, every frame a key frame, with
 * uncompressed 16-bit sound. Encoded by the open-source CineForm SDK (GoPro, Apache 2.0 / MIT).
 *
 * The edit is first rendered as usual (very high bitrate), then each frame is converted here.
 */
object CineForm {
    /** Medium, High, Film scan 1, Film scan 2. */
    val QUALITIES = listOf("Medium", "High", "Film scan 1", "Film scan 2")
    /** Rough data rate in bits per pixel for each quality (for the size estimate). */
    val BITS_PER_PIXEL = listOf(1.1, 1.5, 2.0, 2.7)

    val available: Boolean by lazy { try { System.loadLibrary("veyrocut_cineform"); true } catch (_: Throwable) { false } }

    @JvmStatic private external fun nOpen(fd: Int, w: Int, h: Int, fps: Double, quality: Int, rate: Int, channels: Int): Long
    @JvmStatic private external fun nFrame(h: Long, w: Int, ht: Int, y: ByteBuffer, yOff: Int, yRow: Int, u: ByteBuffer, uOff: Int, v: ByteBuffer, vOff: Int, cRow: Int, cPixel: Int): Boolean
    @JvmStatic private external fun nAudio(h: Long, pcm: ShortArray, frames: Int): Boolean
    @JvmStatic private external fun nClose(h: Long, keep: Boolean): Boolean

    /** The intermediate render for [s]: H.264 at a bitrate high enough that the CineForm file keeps the detail. */
    fun intermediate(s: DeliverSettings, w: Int, h: Int): DeliverSettings {
        val fps = if (s.fps > 0) s.fps else 30
        val mbps = (w.toDouble() * h * fps * 0.5 / 1_000_000).toInt().coerceIn(40, 200)
        return s.copy(hevc = false, bitrateMbps = mbps, smartRender = false)
    }

    private class Track(val index: Int, val format: MediaFormat)

    private fun decodeAudio(src: File, track: Track, out: File, alive: () -> Boolean): Pair<Int, Int>? {
        val ex = MediaExtractor(); ex.setDataSource(src.path); ex.selectTrack(track.index)
        val mime = track.format.getString(MediaFormat.KEY_MIME) ?: return null
        val dec = MediaCodec.createDecoderByType(mime)
        var rate = track.format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var ch = track.format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        try {
            dec.configure(track.format, null, null, 0); dec.start()
            val info = MediaCodec.BufferInfo()
            var inDone = false; var outDone = false
            out.outputStream().buffered(1 shl 16).use { os ->
                val tmp = ByteArray(1 shl 16)
                while (!outDone && alive()) {
                    if (!inDone) {
                        val i = dec.dequeueInputBuffer(5_000)
                        if (i >= 0) {
                            val buf = dec.getInputBuffer(i)!!
                            val n = ex.readSampleData(buf, 0)
                            if (n < 0) { dec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                            else { dec.queueInputBuffer(i, 0, n, ex.sampleTime, 0); ex.advance() }
                        }
                    }
                    val o = dec.dequeueOutputBuffer(info, 5_000)
                    if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val f = dec.outputFormat
                        rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE); ch = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    } else if (o >= 0) {
                        val b = dec.getOutputBuffer(o)!!
                        b.position(info.offset); b.limit(info.offset + info.size)
                        while (b.hasRemaining()) { val k = minOf(b.remaining(), tmp.size); b.get(tmp, 0, k); os.write(tmp, 0, k) }
                        dec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                    }
                }
            }
        } finally { try { dec.stop() } catch (_: Throwable) {}; dec.release(); ex.release() }
        return rate to ch
    }

    /** Where the .mov goes: the picked save folder, or DCIM/AlcorCutPro. */
    private fun create(context: Context, name: String): Uri? {
        val place = Storage.resolve(context, Storage.load(context, StorageArea.VIDEO))
        if (Storage.isTree(place)) Storage.createInTree(context, place, "video/quicktime", name)?.let { return it }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/quicktime")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/AlcorCutPro")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        return Storage.insert(context, Storage.load(context, StorageArea.VIDEO), values, Storage.Kind.VIDEO)
    }

    /**
     * Converts the rendered [src] into a CineForm .mov. Returns the new file, or null with the reason.
     * [onProgress] gets 0–100.
     */
    suspend fun convert(context: Context, src: File, quality: Int, sound: Boolean, onProgress: (Int) -> Unit): Pair<Uri?, String?> {
        if (!available) return null to "CineForm isn't available on this phone"
        val ctx = currentCoroutineContext()
        val alive = { ctx.isActive }
        val ex = MediaExtractor()
        try { ex.setDataSource(src.path) } catch (t: Throwable) { ex.release(); return null to "Couldn't read the render" }
        var video: Track? = null; var audio: Track? = null
        for (i in 0 until ex.trackCount) {
            val f = ex.getTrackFormat(i); val m = f.getString(MediaFormat.KEY_MIME) ?: continue
            if (m.startsWith("video/") && video == null) video = Track(i, f)
            if (m.startsWith("audio/") && audio == null && sound) audio = Track(i, f)
        }
        val vt = video ?: run { ex.release(); return null to "The render has no picture" }
        val durUs = try { vt.format.getLong(MediaFormat.KEY_DURATION) } catch (_: Throwable) { 0L }
        // The sound first, as 16-bit PCM in a temporary file.
        val pcmFile = File(context.cacheDir, "cf_pcm_${System.currentTimeMillis()}.raw")
        var rate = 0; var ch = 0
        if (audio != null) {
            onProgress(1)
            val r = try { decodeAudio(src, audio, pcmFile, alive) } catch (_: Throwable) { null }
            if (r != null) { rate = r.first; ch = r.second.coerceIn(1, 8) }
        }
        if (!alive()) { pcmFile.delete(); ex.release(); return null to null }
        // Frame rate from the render (exact for 23.976 / 29.97 / 59.94).
        val fps = run {
            val f = try { vt.format.getInteger(MediaFormat.KEY_FRAME_RATE).toDouble() } catch (_: Throwable) { 0.0 }
            if (f > 0) f else 30.0
        }.let { f -> listOf(23.976, 24.0, 25.0, 29.97, 30.0, 48.0, 50.0, 59.94, 60.0, 120.0).minByOrNull { kotlin.math.abs(it - f) }?.takeIf { kotlin.math.abs(it - f) < 0.6 } ?: f }

        val name = "EDIT_" + java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date()) + ".mov"
        val target = create(context, name) ?: run { pcmFile.delete(); ex.release(); return null to "Couldn't create the file" }
        val pfd = try { context.contentResolver.openFileDescriptor(target, "rw") } catch (_: Throwable) { null }
            ?: run { Storage.discard(context, target); pcmFile.delete(); ex.release(); return null to "Couldn't write the file" }

        ex.selectTrack(vt.index)
        val mime = vt.format.getString(MediaFormat.KEY_MIME)!!
        val dec = MediaCodec.createDecoderByType(mime)
        vt.format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
        var handle = 0L
        var encW = 0; var encH = 0
        var frames = 0
        var err: String? = null
        val pcmIn = if (rate > 0 && pcmFile.length() > 0) pcmFile.inputStream().buffered(1 shl 16) else null
        val frameBytes = ch * 2
        var audioSent = 0L
        val totalAudio = if (rate > 0) pcmFile.length() / maxOf(frameBytes, 1) else 0L
        fun sendAudio(upTo: Long) {
            val inp = pcmIn ?: return
            var left = (upTo.coerceAtMost(totalAudio) - audioSent).toInt()
            while (left > 0) {
                val n = minOf(left, 4096)
                val bytes = ByteArray(n * frameBytes)
                var got = 0
                while (got < bytes.size) { val k = inp.read(bytes, got, bytes.size - got); if (k <= 0) break; got += k }
                val fr = got / frameBytes
                if (fr <= 0) { audioSent = totalAudio; return }
                val sh = ShortArray(fr * ch)
                ByteBuffer.wrap(bytes, 0, fr * frameBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(sh)
                nAudio(handle, sh, fr)
                audioSent += fr; left -= fr
            }
        }
        try {
            dec.configure(vt.format, null, null, 0); dec.start()
            val info = MediaCodec.BufferInfo()
            var inDone = false; var outDone = false
            while (!outDone) {
                if (!alive()) { err = null; break }
                if (!inDone) {
                    val i = dec.dequeueInputBuffer(5_000)
                    if (i >= 0) {
                        val buf = dec.getInputBuffer(i)!!
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0) { dec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                        else { dec.queueInputBuffer(i, 0, n, ex.sampleTime, 0); ex.advance() }
                    }
                }
                val o = dec.dequeueOutputBuffer(info, 5_000)
                if (o < 0) continue
                if (info.size > 0 || info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM == 0) {
                    val img = try { dec.getOutputImage(o) } catch (_: Throwable) { null }
                    if (img != null) {
                        val crop = img.cropRect
                        val cw = crop.width() and 1.inv(); val chh = crop.height() and 1.inv()
                        if (handle == 0L) {
                            // CineForm wants the width in steps of 8: a few pixels are trimmed evenly if needed.
                            encW = cw - cw % 8; encH = chh
                            handle = nOpen(pfd.fd, encW, encH, fps, quality, if (pcmIn != null) rate else 0, ch)
                            if (handle == 0L) { err = "This picture size can't be made into CineForm"; img.close(); dec.releaseOutputBuffer(o, false); break }
                        }
                        val left = (crop.left + (cw - encW) / 2) and 1.inv(); val top = crop.top and 1.inv()
                        val p = img.planes
                        val yRow = p[0].rowStride; val cRow = p[1].rowStride; val cPix = p[1].pixelStride
                        val ok = nFrame(handle, encW, encH,
                            p[0].buffer, top * yRow + left,
                            yRow, p[1].buffer, (top / 2) * cRow + (left / 2) * cPix, p[2].buffer, (top / 2) * cRow + (left / 2) * cPix, cRow, cPix)
                        img.close()
                        if (!ok) { err = "CineForm encoding failed (out of space?)"; dec.releaseOutputBuffer(o, false); break }
                        frames++
                        sendAudio(Math.round(frames / fps * rate))
                        if (durUs > 0) onProgress((info.presentationTimeUs * 100 / durUs).toInt().coerceIn(1, 99))
                    }
                }
                dec.releaseOutputBuffer(o, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
            }
        } catch (t: Throwable) {
            err = "CineForm export failed: ${t.message ?: t.javaClass.simpleName}"
        } finally {
            try { dec.stop() } catch (_: Throwable) {}
            dec.release(); ex.release()
        }
        val cancelled = !alive()
        val keep = err == null && !cancelled && frames > 0 && handle != 0L
        if (keep) sendAudio(totalAudio)
        val closed = if (handle != 0L) nClose(handle, keep) else false
        try { pcmIn?.close() } catch (_: Throwable) {}
        pcmFile.delete()
        try { pfd.close() } catch (_: Throwable) {}
        return if (keep && closed) {
            if (Build.VERSION.SDK_INT >= 29 && target.authority == MediaStore.AUTHORITY) Storage.publish(context, target)
            onProgress(100)
            target to null
        } else {
            Storage.discard(context, target)
            null to (if (cancelled) null else err ?: "Couldn't finish the CineForm file")
        }
    }

    /** Emulator test run: encodes a short synthetic clip with sound and checks the file. */
    fun selfTest(context: Context): String = try {
        if (!available) "library missing" else {
            val f = File(context.cacheDir, "cf_selftest.mov"); f.delete()
            val w = 640; val h = 360
            val pfd = android.os.ParcelFileDescriptor.open(f, android.os.ParcelFileDescriptor.MODE_READ_WRITE or android.os.ParcelFileDescriptor.MODE_CREATE)
            val hd = nOpen(pfd.fd, w, h, 29.97, 2, 48000, 2)
            if (hd == 0L) { pfd.close(); "open failed" } else {
                val y = ByteBuffer.allocateDirect(w * h); val u = ByteBuffer.allocateDirect(w * h / 4); val v = ByteBuffer.allocateDirect(w * h / 4)
                val t0 = System.nanoTime()
                var ok = true
                for (n in 0 until 30) {
                    for (i in 0 until w * h) y.put(i, ((i % w + n * 4) and 255).toByte())
                    for (i in 0 until w * h / 4) { u.put(i, (96 + n).toByte()); v.put(i, (160 - n).toByte()) }
                    ok = ok && nFrame(hd, w, h, y, 0, w, u, 0, v, 0, w / 2, 1)
                    val pcm = ShortArray(1602 * 2) { k -> (8000 * kotlin.math.sin((n * 1602 + k / 2) * 2 * Math.PI * 440 / 48000)).toInt().toShort() }
                    nAudio(hd, pcm, 1602)
                }
                val closed = nClose(hd, ok)
                pfd.close()
                val ms = (System.nanoTime() - t0) / 1_000_000
                val head = f.inputStream().use { val b = ByteArray(8); it.read(b); String(b, 4, 4, Charsets.ISO_8859_1) }
                val tail = String(f.readBytes().let { b -> b.copyOfRange(0, b.size) }, Charsets.ISO_8859_1).contains("moov")
                "${if (ok && closed && head == "ftyp" && tail) "ok" else "FAILED"}, ${f.length()} bytes, ${30 * 1000 / maxOf(ms, 1)} fps".also { f.delete() }
            }
        }
    } catch (t: Throwable) { "error ${t.message}" }
}
