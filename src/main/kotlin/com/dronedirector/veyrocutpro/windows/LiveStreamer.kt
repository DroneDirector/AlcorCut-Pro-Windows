package com.dronedirector.veyrocutpro.camera

import android.content.Context
import android.view.SurfaceView
import com.dronedirector.veyrocutpro.windows.MfNative
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/** Where a live stream can go. Users paste the stream key from each platform's own dashboard. */
enum class LivePlatform(
    val label: String,
    val defaultServer: String,
    val keyPage: String,
    val vertical: Boolean,
    val help: String,
) {
    YOUTUBE("YouTube", "rtmp://a.rtmp.youtube.com/live2", "https://studio.youtube.com/", false, "YouTube Studio → Create → Go live → Stream. Copy the Stream key."),
    TIKTOK("TikTok", "", "https://livecenter.tiktok.com/producer", true, "TikTok LIVE Producer shows a Server URL and Stream key. Not every TikTok account has access to stream keys."),
    TWITCH("Twitch", "rtmp://live.twitch.tv/app", "https://dashboard.twitch.tv/settings/stream", false, "Twitch Creator Dashboard → Settings → Stream. Copy the Primary Stream key."),
    FACEBOOK("Facebook", "rtmps://live-api-s.facebook.com:443/rtmp", "https://www.facebook.com/live/producer", false, "Facebook Live Producer → Streaming software. Copy the Stream key."),
    CUSTOM("Custom", "rtmp://", "", false, "Any RTMP or RTMPS server (your own server, Restream, Kick…). Put the full URL in Server."),
}

enum class LiveQuality(val label: String, val width: Int, val height: Int, val bitrate: Int) {
    SD("480p", 854, 480, 1_200_000),
    HD("720p", 1280, 720, 2_500_000),
    FHD("1080p", 1920, 1080, 4_500_000),
}

enum class LiveStatus { IDLE, CONNECTING, LIVE, RECONNECTING }

data class LiveState(
    val platform: LivePlatform = LivePlatform.YOUTUBE,
    val server: String = LivePlatform.YOUTUBE.defaultServer,
    val key: String = "",
    val vertical: Boolean = false,
    val quality: LiveQuality = LiveQuality.HD,
    val hevc: Boolean = false,
    val status: LiveStatus = LiveStatus.IDLE,
    val seconds: Long = 0,
    val kbps: Int = 0,
    val muted: Boolean = false,
    val torch: Boolean = false,
    val showSetup: Boolean = false,
    val message: String? = null,
    val messageId: Int = 0,
) {
    val isConnected: Boolean get() = status == LiveStatus.LIVE
    val isBusy: Boolean get() = status != LiveStatus.IDLE
    val hasKey: Boolean get() = key.isNotBlank() || platform == LivePlatform.CUSTOM
}

/**
 * Streams the PC camera and microphone to RTMP / RTMPS servers: H.264 + AAC from Windows' own
 * encoders, sent by the app's own RTMP code. Reconnects by itself when the connection drops. Own code.
 */
class LiveStreamer(private val context: Context) {
    private val prefs = context.getSharedPreferences("procam_live", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(loadPlatform(LivePlatform.entries.getOrElse(prefs.getInt("platform", 0)) { LivePlatform.YOUTUBE }, LiveState()))
    val state: StateFlow<LiveState> = _state.asStateFlow()

    /** Set by the camera: its newest picture. */
    var frameSource: () -> Triple<IntArray, Int, Int>? = { null }
    private var preview: SurfaceView? = null
    @Volatile private var session: Session? = null
    private var ticker: Thread? = null

    private fun toast(msg: String) = _state.update { it.copy(message = msg, messageId = it.messageId + 1) }

    private fun loadPlatform(p: LivePlatform, base: LiveState): LiveState = base.copy(
        platform = p,
        server = prefs.getString("server_${p.name}", null) ?: p.defaultServer,
        key = prefs.getString("key_${p.name}", null) ?: "",
        vertical = prefs.getBoolean("vertical_${p.name}", p.vertical),
        quality = LiveQuality.entries.getOrElse(prefs.getInt("quality", 1)) { LiveQuality.HD },
        hevc = false,
    )

    fun selectPlatform(p: LivePlatform) {
        if (_state.value.isBusy) return
        prefs.edit().putInt("platform", p.ordinal).apply()
        _state.value = loadPlatform(p, _state.value).copy(showSetup = true)
    }

    fun saveConnection(server: String, key: String) {
        val p = _state.value.platform
        prefs.edit().putString("server_${p.name}", server.trim()).putString("key_${p.name}", key.trim()).apply()
        _state.update { it.copy(server = server.trim(), key = key.trim(), showSetup = false) }
        toast("${p.label} connected — click GO LIVE")
    }

    fun forgetKey() {
        val p = _state.value.platform
        prefs.edit().remove("key_${p.name}").apply()
        _state.update { it.copy(key = "") }
    }

    fun showSetup(show: Boolean) = _state.update { it.copy(showSetup = show) }

    fun setVertical(vertical: Boolean) {
        if (_state.value.isBusy || _state.value.vertical == vertical) return
        prefs.edit().putBoolean("vertical_${_state.value.platform.name}", vertical).apply()
        _state.update { it.copy(vertical = vertical) }
    }

    fun setHevc(on: Boolean) { if (on) toast("Streaming from a PC uses H.264, which every platform takes") }

    fun setQuality(q: LiveQuality) {
        if (_state.value.isBusy || _state.value.quality == q) return
        prefs.edit().putInt("quality", q.ordinal).apply()
        _state.update { it.copy(quality = q) }
    }

    fun open() {}
    fun close() {
        stopStream()
        preview = null
        _state.update { it.copy(status = LiveStatus.IDLE, seconds = 0, kbps = 0, torch = false) }
    }

    fun attachPreview(view: SurfaceView) { preview = view }
    fun detachPreview(view: SurfaceView) { if (preview === view) preview = null }

    /** The camera's newest picture: shown in the LIVE view and sent while live. */
    fun frame(px: IntArray, w: Int, h: Int) {
        preview?.let { v -> if (System.nanoTime() - lastPreview > 33_000_000L) { lastPreview = System.nanoTime(); v.preview.post(px, w, h) } }
        session?.frame(px, w, h)
    }
    @Volatile private var lastPreview = 0L

    fun streamUrl(st: LiveState = _state.value): String {
        val server = st.server.trim().trimEnd('/')
        return if (st.key.isBlank()) server else "$server/${st.key.trim()}"
    }

    fun toggleLive() {
        val st = _state.value
        if (st.isBusy) { stopStream(); toast("Stream ended"); return }
        if (!st.hasKey || st.server.length < 8) {
            _state.update { it.copy(showSetup = true) }
            toast("Add your ${st.platform.label} stream key first")
            return
        }
        if (st.server.startsWith("srt", true)) { toast("SRT streaming isn't on Windows — use the RTMP / RTMPS address"); return }
        if (!MfNative.ok) { toast("The camera part of the app is missing — reinstall AlcorCut Pro"); return }
        _state.update { it.copy(status = LiveStatus.CONNECTING, seconds = 0, kbps = 0) }
        val s = Session(streamUrl(st), st)
        session = s
        s.start()
        val t0 = System.currentTimeMillis()
        ticker = Thread({
            while (session === s) {
                try { Thread.sleep(1000) } catch (_: InterruptedException) { break }
                if (_state.value.isConnected) _state.update { it.copy(seconds = (System.currentTimeMillis() - s.liveSince) / 1000, kbps = s.kbps()) }
            }
        }, "live-ticker").apply { isDaemon = true; start() }
    }

    private fun stopStream() {
        val s = session ?: return
        session = null
        ticker?.interrupt()
        s.stop()
        _state.update { it.copy(status = LiveStatus.IDLE, kbps = 0) }
    }

    fun switchCamera() { toast("Pick the camera in Capture before going live") }
    fun toggleTorch() { toast("No torch on this camera") }
    fun toggleMute() { _state.update { it.copy(muted = !it.muted) } }

    /** One stream: encoder, connection and the sending thread; reconnects up to 10 times. */
    private inner class Session(val url: String, val st: LiveState) {
        @Volatile private var running = true
        private val q = ArrayBlockingQueue<Triple<IntArray, Int, Int>>(3)
        private var thread: Thread? = null
        private var audio: Thread? = null
        @Volatile var liveSince = System.currentTimeMillis()
        private var bytesAt = 0L; private var bytesT = System.currentTimeMillis()
        @Volatile private var rtmp: Rtmp? = null
        private val w = (if (st.vertical) st.quality.height else st.quality.width) and 1.inv()
        private val h = (if (st.vertical) st.quality.width else st.quality.height) and 1.inv()
        private val px = IntArray(w * h)
        private val fps = 30

        fun kbps(): Int {
            val r = rtmp ?: return 0
            val now = System.currentTimeMillis(); val b = r.sentBytes
            val k = ((b - bytesAt) * 8 / maxOf(1L, now - bytesT)).toInt()
            bytesAt = b; bytesT = now
            return k
        }

        fun frame(src: IntArray, sw: Int, sh: Int) {
            if (!running) return
            q.offer(Triple(src.copyOf(sw * sh), sw, sh))
        }

        /** The camera picture fitted to the stream's shape (filled, cut at the sides). */
        private fun fit(src: IntArray, sw: Int, sh: Int) {
            val k = maxOf(w.toFloat() / sw, h.toFloat() / sh)
            val cw = w / k; val ch = h / k
            val x0 = (sw - cw) / 2f; val y0 = (sh - ch) / 2f
            parallel(w * h) { a, b -> for (i in a until b) { val y = i / w; val x = i % w; px[i] = src[((y0 + y / k).toInt().coerceIn(0, sh - 1)) * sw + (x0 + x / k).toInt().coerceIn(0, sw - 1)] } }
        }

        fun start() {
            thread = Thread({ run() }, "live-send").apply { isDaemon = true; start() }
        }

        private fun run() {
            var tries = 0
            while (running) {
                val enc = MfNative.encOpen(w, h, fps, st.quality.bitrate, 128_000)
                if (enc == 0L) { fail("This PC can't encode ${st.quality.label} for streaming; try a lower quality"); return }
                try {
                    val r = Rtmp(url)
                    r.connect()
                    rtmp = r
                    r.metadata(w, h, fps, st.quality.bitrate / 1000, 128)
                    r.audioHeader()
                    tries = 0
                    liveSince = System.currentTimeMillis()
                    _state.update { it.copy(status = LiveStatus.LIVE, seconds = 0) }
                    toast("You're live on ${st.platform.label}")
                    audio = Thread({ sendAudio(enc, r) }, "live-audio").apply { isDaemon = true; start() }
                    var sentHeader = false
                    val t0 = System.nanoTime()
                    var n = 0L
                    while (running && !r.closed) {
                        val (src, sw, sh) = q.poll(200, TimeUnit.MILLISECONDS) ?: continue
                        fit(src, sw, sh)
                        val ts = (System.nanoTime() - t0) / 100
                        val out = MfNative.encVideo(enc, px, ts, 10_000_000L / fps) ?: continue
                        var i = 0
                        while (i + 8 <= out.size) {
                            val key = out[i + 3].toInt() == 0
                            val len = ((out[i + 4].toInt() and 0xFF) shl 24) or ((out[i + 5].toInt() and 0xFF) shl 16) or ((out[i + 6].toInt() and 0xFF) shl 8) or (out[i + 7].toInt() and 0xFF)
                            val data = out.copyOfRange(i + 8, minOf(out.size, i + 8 + len)); i += 8 + len
                            val nals = Rtmp.nals(data)
                            if (!sentHeader) {
                                val hdr = MfNative.encHeader(enc)?.let { Rtmp.nals(it) }.orEmpty() + nals
                                val sps = hdr.firstOrNull { (it[0].toInt() and 0x1F) == 7 }; val pps = hdr.firstOrNull { (it[0].toInt() and 0x1F) == 8 }
                                if (sps == null || pps == null || !key) continue
                                r.videoHeader(sps, pps); sentHeader = true
                            }
                            val body = nals.filter { (it[0].toInt() and 0x1F) !in setOf(7, 8, 9) }
                            if (body.isNotEmpty()) r.video(body, key, (ts / 10_000).toInt())
                            n++
                        }
                    }
                    r.close()
                } catch (e: SecurityException) {
                    fail("The server rejected the stream key — check it and try again", setup = true); return
                } catch (t: Throwable) {
                    if (!running) break
                    if (++tries > 10) { fail("Connection failed: ${t.message ?: "no answer"}"); return }
                    _state.update { it.copy(status = LiveStatus.RECONNECTING) }
                    try { Thread.sleep(5000) } catch (_: InterruptedException) { break }
                } finally {
                    rtmp?.close(); rtmp = null
                    audio?.interrupt(); audio?.join(1000)
                    MfNative.encClose(enc)
                }
            }
        }

        private fun sendAudio(enc: Long, r: Rtmp) {
            val rate = 48_000
            val rec = android.media.AudioRecord(android.media.MediaRecorder.AudioSource.CAMCORDER, rate, android.media.AudioFormat.CHANNEL_IN_STEREO, android.media.AudioFormat.ENCODING_PCM_16BIT, 1 shl 15)
            if (rec.state != android.media.AudioRecord.STATE_INITIALIZED) return
            val ch = rec.channelCount
            val raw = ShortArray(1024 * ch)
            val pcm = ByteArray(1024 * 4)
            var samples = 0L
            try {
                rec.startRecording()
                while (running && !r.closed && !Thread.currentThread().isInterrupted) {
                    val got = rec.read(raw, 0, raw.size); if (got <= 0) continue
                    val frames = got / ch
                    val mute = _state.value.muted
                    for (i in 0 until frames) for (c in 0..1) {
                        val v = if (mute) 0 else raw[i * ch + (if (ch == 2) c else 0)].toInt()
                        pcm[i * 4 + c * 2] = v.toByte(); pcm[i * 4 + c * 2 + 1] = (v shr 8).toByte()
                    }
                    val ts = samples * 10_000_000 / rate
                    val out = MfNative.encAudio(enc, pcm, frames * 4, ts, frames.toLong() * 10_000_000 / rate)
                    samples += frames
                    if (out != null) {
                        var i = 0; var k = 0L
                        while (i + 2 <= out.size) {
                            val len = ((out[i].toInt() and 0xFF) shl 8) or (out[i + 1].toInt() and 0xFF)
                            val ms = ((samples - frames) * 1000 / rate + k * 1024 * 1000 / rate).toInt()
                            r.audio(out.copyOfRange(i + 2, minOf(out.size, i + 2 + len)), ms)
                            i += 2 + len; k++
                        }
                    }
                }
            } catch (_: Throwable) {
            } finally { try { rec.stop() } catch (_: Throwable) {}; rec.release() }
        }

        private fun fail(msg: String, setup: Boolean = false) {
            running = false
            if (session === this) session = null
            _state.update { it.copy(status = LiveStatus.IDLE, kbps = 0, showSetup = setup || it.showSetup) }
            toast(msg)
        }

        fun stop() {
            running = false
            rtmp?.close()
            thread?.join(3000)
        }
    }
}
