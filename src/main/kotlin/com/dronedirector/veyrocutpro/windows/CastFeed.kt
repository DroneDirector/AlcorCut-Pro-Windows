package com.dronedirector.veyrocutpro.camera

import android.graphics.Bitmap
import com.dronedirector.veyrocutpro.windows.MfNative
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * The live picture and sound for a TV (Chromecast), as on the phone: frames from a source (Capture's
 * viewfinder) are encoded to H.264 and the sound to AAC with the PC's own encoders, and served from this
 * PC as a live HLS stream on the local network, the format a Chromecast plays. Own code: the HLS server
 * and the MPEG-TS packaging are written here. The TV lags a few seconds behind. When no screen gives a
 * picture, an AlcorCut Pro card is shown and the TV stays connected.
 */
object CastFeed {
    private const val PORT = 8766
    private const val W = 1280
    private const val H = 720
    private const val FPS = 24
    private const val SEG_FRAMES = FPS          // segments of a second or more (they start on key frames)
    private const val KEEP = 8
    private const val RATE = 48000

    /** Where the sound comes from: the microphone (Capture) or what the app plays ([pushPcm]). */
    enum class Sound { MIC, APP }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    @Volatile private var server: ServerSocket? = null
    private val segments = ArrayDeque<Pair<Int, ByteArray>>()
    private val durations = HashMap<Int, Double>()
    private var segStartPts = -1L
    private var seq = 0
    @Volatile var running = false
        private set
    /** The app colour, for the AlcorCut Pro card on the TV. */
    @Volatile var accent: Int = 0xFFE8B04A.toInt()
    @Volatile private var sound = Sound.MIC
    @Volatile private var t0 = 0L
    @Volatile private var enc = 0L
    private val encLock = Any()
    private var ts = TsWriter()
    private class Ring {
        val buf = ShortArray(RATE * 2)
        var r = 0; var n = 0
        @Synchronized fun write(x: ShortArray) {
            for (v in x) { if (n == buf.size) { r = (r + 1) % buf.size; n-- }; buf[(r + n) % buf.size] = v; n++ }
        }
        @Synchronized fun addTo(mix: IntArray) {
            val k = minOf(n, mix.size)
            for (i in 0 until k) { mix[i] += buf[r].toInt(); r = (r + 1) % buf.size }
            n -= k
        }
    }
    private val rings = java.util.concurrent.ConcurrentHashMap<Any, Ring>()
    /** This PC's address as the TV sees it (the side of the connection to the Chromecast). */
    @Volatile var host: String? = null

    /** The stream's address for the TV, or null when the PC isn't on a network. */
    fun url(): String? = (host ?: RemoteMonitor.localIpAddress())?.let { "http://$it:$PORT/live.m3u8" }

    fun setSound(s: Sound) { sound = s; rings.clear() }

    /** Starts serving [source] (asked about 24 times a second on the main thread; null = the idle card). */
    fun start(source: suspend () -> Bitmap?) {
        if (running) return
        if (!MfNative.ok) return
        running = true
        synchronized(segments) { segments.clear(); durations.clear(); seq = 0 }
        ts = TsWriter()
        t0 = System.nanoTime()
        val ss = try { ServerSocket(PORT) } catch (_: Throwable) { running = false; return }
        server = ss
        thread(name = "CastFeed-http", isDaemon = true) {
            while (!ss.isClosed) {
                val s = try { ss.accept() } catch (_: Throwable) { break }
                thread(isDaemon = true) { serve(s) }
            }
        }
        enc = MfNative.encOpen(W, H, FPS, 5_000_000, 160_000)
        if (enc == 0L) { stop(); return }
        job = scope.launch { encode(source) }
        thread(name = "CastFeed-audio", isDaemon = true) { audio() }
    }

    fun stop() {
        running = false
        job?.cancel(); job = null
        try { server?.close() } catch (_: Throwable) {}
        server = null
        rings.clear()
        thread(isDaemon = true) { Thread.sleep(300); synchronized(encLock) { if (enc != 0L && !running) { MfNative.encClose(enc); enc = 0L } } }
    }

    fun pushPcm(from: Any, pcm: ShortArray, rate: Int, channels: Int) {
        if (!running || sound != Sound.APP || pcm.isEmpty()) return
        val ch = channels.coerceAtLeast(1); val frames = pcm.size / ch
        val outFrames = if (rate == RATE) frames else (frames.toLong() * RATE / rate.coerceAtLeast(1)).toInt()
        val out = ShortArray(outFrames * 2)
        for (i in 0 until outFrames) {
            val src = if (rate == RATE) i else ((i.toLong() * rate / RATE).toInt()).coerceAtMost(frames - 1)
            val l = pcm[src * ch]; val r = if (ch >= 2) pcm[src * ch + 1] else l
            out[2 * i] = l; out[2 * i + 1] = r
        }
        rings.getOrPut(from) { Ring() }.write(out)
    }

    // ---- picture

    private suspend fun encode(source: suspend () -> Bitmap?) {
        val img = BufferedImage(W, H, BufferedImage.TYPE_INT_RGB)
        val px = (img.raster.dataBuffer as DataBufferInt).data
        var header: ByteArray? = null
        var frames = 0
        var srcPx = IntArray(0)
        while (scope.isActive && running) {
            val frameStart = System.nanoTime()
            val b = withContext(Dispatchers.Main) { try { source() } catch (_: Throwable) { null } }
            val g = img.createGraphics()
            g.color = java.awt.Color.BLACK; g.fillRect(0, 0, W, H)
            if (b != null && b.width > 0 && b.height > 0) {
                if (srcPx.size != b.width * b.height) srcPx = IntArray(b.width * b.height)
                b.getPixels(srcPx, 0, b.width, 0, 0, b.width, b.height)
                val si = BufferedImage(b.width, b.height, BufferedImage.TYPE_INT_RGB)
                si.setRGB(0, 0, b.width, b.height, srcPx, 0, b.width)
                val k = minOf(W.toFloat() / b.width, H.toFloat() / b.height)
                val dw = (b.width * k).toInt(); val dh = (b.height * k).toInt()
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                g.drawImage(si, (W - dw) / 2, (H - dh) / 2, dw, dh, null)
            } else {
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                g.color = java.awt.Color(accent, true); g.font = Font(Font.SANS_SERIF, Font.BOLD, 54)
                val text = "AlcorCut Pro"; val fw = g.fontMetrics.stringWidth(text)
                g.drawString(text, (W - fw) / 2, H / 2 + 18)
            }
            g.dispose()
            b?.recycle()
            for (i in px.indices) px[i] = px[i] or 0xFF000000.toInt()
            val out = synchronized(encLock) { if (enc == 0L) null else MfNative.encVideo(enc, px, (System.nanoTime() - t0) / 100, 10_000_000L / FPS) }
            if (out != null) {
                if (header == null) header = synchronized(encLock) { if (enc == 0L) null else MfNative.encHeader(enc) }
                var i = 0
                while (i + 8 <= out.size) {
                    val key = out[i + 3].toInt() == 0
                    val len = ((out[i + 4].toInt() and 0xFF) shl 24) or ((out[i + 5].toInt() and 0xFF) shl 16) or ((out[i + 6].toInt() and 0xFF) shl 8) or (out[i + 7].toInt() and 0xFF)
                    val data = out.copyOfRange(i + 8, minOf(out.size, i + 8 + len)); i += 8 + len
                    val pts = now90()
                    if (key && frames >= SEG_FRAMES) { finishSegment(pts); frames = 0 }
                    if (key || frames > 0) {
                        if (frames == 0) segStartPts = pts
                        val hasSps = Rtmp.nals(data).any { (it[0].toInt() and 0x1F) == 7 }
                        ts.video(if (key && !hasSps && header != null) header!! + data else data, pts, key)
                        frames++
                    }
                }
            }
            val took = (System.nanoTime() - frameStart) / 1_000_000L
            delay((1000L / FPS - took).coerceAtLeast(1))
        }
    }

    private fun now90() = (System.nanoTime() - t0) / 1_000_000L * 90 + 90_000   // starts at 1 s

    private fun finishSegment(endPts: Long) {
        val bytes = ts.take()
        if (bytes.isEmpty()) return
        synchronized(segments) {
            durations[seq] = ((endPts - segStartPts) / 90000.0).coerceIn(0.2, 10.0)
            segments.addLast(seq++ to bytes)
            while (segments.size > KEEP) durations.remove(segments.removeFirst().first)
        }
    }

    /** Segments ready (the TV is told to play once there are a few). */
    fun ready(): Int = synchronized(segments) { segments.size }

    // ---- sound (AAC, 48 kHz stereo)

    private fun audio() {
        var mic: android.media.AudioRecord? = null
        val chunk = ShortArray(2048)   // 1024 stereo frames
        val bytes = ByteArray(4096)
        var nextMix = System.nanoTime()
        var samples = 0L
        try {
            while (running) {
                val pcm: ShortArray = when (sound) {
                    Sound.MIC -> {
                        if (mic == null) mic = try {
                            android.media.AudioRecord(android.media.MediaRecorder.AudioSource.CAMCORDER, RATE, android.media.AudioFormat.CHANNEL_IN_STEREO, android.media.AudioFormat.ENCODING_PCM_16BIT, 1 shl 15)
                                .takeIf { it.state == android.media.AudioRecord.STATE_INITIALIZED }?.also { it.startRecording() }
                        } catch (_: Throwable) { null }
                        val m = mic
                        if (m != null) {
                            val ch = m.channelCount.coerceAtLeast(1)
                            val raw = ShortArray(1024 * ch); val n = m.read(raw, 0, raw.size)
                            if (n <= 0) ShortArray(0) else { val f = n / ch; ShortArray(f * 2) { i -> raw[(i / 2) * ch + (if (ch >= 2) i % 2 else 0)] } }
                        } else { Thread.sleep(21); ShortArray(chunk.size) }
                    }
                    Sound.APP -> {
                        mic?.let { try { it.stop(); it.release() } catch (_: Throwable) {} }; mic = null
                        val due = nextMix - System.nanoTime()
                        if (due > 0) Thread.sleep(due / 1_000_000, (due % 1_000_000).toInt())
                        nextMix = maxOf(nextMix, System.nanoTime() - 100_000_000L) + 1024L * 1_000_000_000L / RATE
                        val mix = IntArray(chunk.size)
                        rings.values.forEach { it.addTo(mix) }
                        ShortArray(mix.size) { mix[it].coerceIn(-32768, 32767).toShort() }
                    }
                }
                if (pcm.isEmpty()) continue
                val frames = pcm.size / 2
                val buf = if (bytes.size >= pcm.size * 2) bytes else ByteArray(pcm.size * 2)
                for (i in pcm.indices) { buf[i * 2] = pcm[i].toInt().toByte(); buf[i * 2 + 1] = (pcm[i].toInt() shr 8).toByte() }
                val tsAudio = samples * 10_000_000 / RATE
                samples += frames
                val out = synchronized(encLock) { if (enc == 0L) null else MfNative.encAudio(enc, buf, frames * 4, tsAudio, frames.toLong() * 10_000_000 / RATE) } ?: continue
                var i = 0
                while (i + 2 <= out.size) {
                    val len = ((out[i].toInt() and 0xFF) shl 8) or (out[i + 1].toInt() and 0xFF)
                    val data = out.copyOfRange(i + 2, minOf(out.size, i + 2 + len)); i += 2 + len
                    ts.audio(adts(data.size) + data, now90())
                }
            }
        } finally {
            mic?.let { try { it.stop(); it.release() } catch (_: Throwable) {} }
        }
    }

    /** ADTS header for one AAC-LC frame (48 kHz, stereo). */
    private fun adts(payload: Int): ByteArray {
        val len = payload + 7
        val profile = 1; val freqIdx = 3; val chan = 2
        return byteArrayOf(
            0xFF.toByte(), 0xF1.toByte(),
            ((profile shl 6) or (freqIdx shl 2) or (chan shr 2)).toByte(),
            (((chan and 3) shl 6) or (len shr 11)).toByte(),
            ((len shr 3) and 0xFF).toByte(),
            (((len and 7) shl 5) or 0x1F).toByte(),
            0xFC.toByte(),
        )
    }

    // ---- HTTP (playlist + segments), with the CORS header a Chromecast needs

    private fun serve(s: Socket) = s.use { sock ->
        try {
            sock.soTimeout = 10_000
            val reader = sock.getInputStream().bufferedReader()
            val line = reader.readLine() ?: return
            while (true) { val h = reader.readLine() ?: break; if (h.isEmpty()) break }
            val path = line.split(' ').getOrNull(1) ?: "/"
            val out = sock.getOutputStream()
            fun reply(code: String, type: String, body: ByteArray) {
                out.write(("HTTP/1.1 $code\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\n" +
                    "Access-Control-Allow-Origin: *\r\nCache-Control: no-cache\r\nConnection: close\r\n\r\n").toByteArray())
                out.write(body); out.flush()
            }
            val method = line.substringBefore(' ')
            when {
                method == "OPTIONS" -> {
                    out.write(("HTTP/1.1 204 No Content\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n" +
                        "Access-Control-Allow-Headers: *\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").toByteArray()); out.flush()
                }
                path.startsWith("/live.m3u8") -> {
                    val (list, durs) = synchronized(segments) { segments.toList() to HashMap(durations) }
                    val first = list.firstOrNull()?.first ?: 0
                    val target = kotlin.math.ceil(durs.values.maxOrNull() ?: 1.0).toInt().coerceAtLeast(1) + 1
                    val sb = StringBuilder("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:$target\n#EXT-X-MEDIA-SEQUENCE:$first\n")
                    list.forEach { (n, _) -> sb.append("#EXTINF:%.3f,\nseg$n.ts\n".format(java.util.Locale.US, durs[n] ?: 1.0)) }
                    reply("200 OK", "application/vnd.apple.mpegurl", sb.toString().toByteArray())
                }
                path.startsWith("/seg") -> {
                    val n = path.removePrefix("/seg").substringBefore(".ts").toIntOrNull()
                    val seg = synchronized(segments) { segments.firstOrNull { it.first == n }?.second }
                    if (seg != null) reply("200 OK", "video/mp2t", seg) else reply("404 Not Found", "text/plain", ByteArray(0))
                }
                else -> reply("404 Not Found", "text/plain", ByteArray(0))
            }
        } catch (_: Throwable) {}
    }

    /** MPEG transport stream: one H.264 and one AAC track (PAT, PMT and PES packets). Thread-safe. */
    private class TsWriter {
        private val out = ByteArrayOutputStream()
        private val cc = IntArray(4)   // PAT, PMT, video, audio
        private var tablesWritten = false

        @Synchronized fun take(): ByteArray { val b = out.toByteArray(); out.reset(); tablesWritten = false; return b }

        @Synchronized fun video(nal: ByteArray, pts: Long, key: Boolean) {
            if (!tablesWritten || key) { psi(0, pat(), 0); psi(PMT_PID, pmt(), 1); tablesWritten = true }
            pes(VID_PID, 0xE0, nal, pts, pcr = key, counter = 2)
        }

        @Synchronized fun audio(adtsFrame: ByteArray, pts: Long) {
            if (!tablesWritten) return   // a segment starts with a picture
            pes(AUD_PID, 0xC0, adtsFrame, pts, pcr = false, counter = 3)
        }

        private fun pes(pid: Int, streamId: Int, payloadIn: ByteArray, pts: Long, pcr: Boolean, counter: Int) {
            val pes = ByteArrayOutputStream()
            val len = if (streamId == 0xE0) 0 else (payloadIn.size + 8).coerceAtMost(0xFFFF)
            pes.write(byteArrayOf(0, 0, 1, streamId.toByte(), (len shr 8).toByte(), len.toByte(), 0x80.toByte(), 0x80.toByte(), 5))
            pes.write(ptsBytes(pts))
            pes.write(payloadIn)
            val data = pes.toByteArray()
            var pos = 0; var first = true
            while (pos < data.size) {
                val pkt = ByteArray(188) { 0xFF.toByte() }
                pkt[0] = 0x47
                pkt[1] = ((if (first) 0x40 else 0) or (pid shr 8)).toByte()
                pkt[2] = (pid and 0xFF).toByte()
                val remaining = data.size - pos
                val needPcr = first && pcr
                val hdr: Int
                if (needPcr || remaining < 184) {
                    val payload = minOf(remaining, if (needPcr) 176 else 182)
                    val afLen = 183 - payload
                    pkt[3] = (0x30 or (cc[counter] and 0x0F)).toByte()
                    pkt[4] = afLen.toByte()
                    if (afLen > 0) {
                        pkt[5] = if (needPcr) 0x50 else 0x00
                        var p = 6
                        if (needPcr) {
                            val base = pts - 2 * 90
                            pkt[6] = (base shr 25).toByte(); pkt[7] = (base shr 17).toByte(); pkt[8] = (base shr 9).toByte()
                            pkt[9] = (base shr 1).toByte(); pkt[10] = (((base and 1) shl 7) or 0x7E).toInt().toByte(); pkt[11] = 0
                            p = 12
                        }
                        while (p < 5 + afLen) { pkt[p] = 0xFF.toByte(); p++ }
                    }
                    hdr = 5 + afLen
                } else { pkt[3] = (0x10 or (cc[counter] and 0x0F)).toByte(); hdr = 4 }
                val n = 188 - hdr
                System.arraycopy(data, pos, pkt, hdr, n)
                pos += n; first = false; cc[counter]++
                out.write(pkt)
            }
        }

        private fun psi(pid: Int, section: ByteArray, counter: Int) {
            val pkt = ByteArray(188) { 0xFF.toByte() }
            pkt[0] = 0x47; pkt[1] = (0x40 or (pid shr 8)).toByte(); pkt[2] = (pid and 0xFF).toByte()
            pkt[3] = (0x10 or (cc[counter]++ and 0x0F)).toByte(); pkt[4] = 0
            System.arraycopy(section, 0, pkt, 5, section.size)
            out.write(pkt)
        }

        private fun pat(): ByteArray = withCrc(byteArrayOf(0x00, 0xB0.toByte(), 0x0D, 0x00, 0x01, 0xC1.toByte(), 0x00, 0x00,
            0x00, 0x01, (0xE0 or (PMT_PID shr 8)).toByte(), (PMT_PID and 0xFF).toByte()))

        private fun pmt(): ByteArray = withCrc(byteArrayOf(0x02, 0xB0.toByte(), 0x17, 0x00, 0x01, 0xC1.toByte(), 0x00, 0x00,
            (0xE0 or (VID_PID shr 8)).toByte(), (VID_PID and 0xFF).toByte(), 0xF0.toByte(), 0x00,
            0x1B, (0xE0 or (VID_PID shr 8)).toByte(), (VID_PID and 0xFF).toByte(), 0xF0.toByte(), 0x00,
            0x0F, (0xE0 or (AUD_PID shr 8)).toByte(), (AUD_PID and 0xFF).toByte(), 0xF0.toByte(), 0x00))

        private fun withCrc(b: ByteArray): ByteArray {
            var crc = -1
            for (x in b) {
                crc = crc xor ((x.toInt() and 0xFF) shl 24)
                repeat(8) { crc = if (crc and 0x80000000.toInt() != 0) (crc shl 1) xor 0x04C11DB7 else crc shl 1 }
            }
            return b + byteArrayOf((crc ushr 24).toByte(), (crc ushr 16).toByte(), (crc ushr 8).toByte(), crc.toByte())
        }

        private fun ptsBytes(pts: Long): ByteArray = byteArrayOf(
            (0x20 or (((pts shr 30) and 0x07).toInt() shl 1) or 1).toByte(),
            (pts shr 22).toByte(), ((((pts shr 15) and 0x7F).toInt() shl 1) or 1).toByte(),
            (pts shr 7).toByte(), ((((pts) and 0x7F).toInt() shl 1) or 1).toByte(),
        )

        companion object { const val PMT_PID = 0x1000; const val VID_PID = 0x100; const val AUD_PID = 0x101 }
    }
}
