package com.dronedirector.veyrocutpro.windows

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import javax.sound.sampled.AudioFormat as JFormat

/**
 * The video editor's player on Windows, with the phone player's behaviour: a list of clips (each
 * trimmed), play / pause / seek, speed, volume, the shared code's sound steps ([processors], e.g. EQ
 * and effects) and pictures shown on a [android.view.TextureView]. Sound is the clock; pictures are
 * shown when their time comes. Pictures and sound are read with Windows' media engine. Own code.
 */
class WinPlayer(private val context: Context, private val processors: List<AudioProcessor>) : ExoPlayer {
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<Player.Listener>()
    private val items = ArrayList<MediaItem>()
    private var index = 0
    @Volatile private var state = Player.STATE_IDLE
    @Volatile private var pwr = false
    @Volatile private var speed = 1f
    @Volatile private var vol = 1f
    @Volatile private var view: android.view.TextureView? = null
    @Volatile private var size = VideoSize.UNKNOWN
    @Volatile private var error: PlaybackException? = null
    private var rep = Player.REPEAT_MODE_OFF
    private val lock = Object()

    // Position within the current item's file, microseconds (the clock while playing).
    @Volatile private var posUs = 0L
    private var startUs = 0L; private var endUs = Long.MAX_VALUE
    private var info: MediaInfo? = null
    private var file: File? = null
    private var engine: Thread? = null
    @Volatile private var gen = 0          // bumps on every seek / item change / pause: stale work stops
    @Volatile private var seekTarget = -1L
    @Volatile private var released = false

    private fun main(block: () -> Unit) = javax.swing.SwingUtilities.invokeLater { if (!released) block() }
    private fun setState(s: Int) { if (state == s) return; state = s; main { listeners.forEach { it.onPlaybackStateChanged(s) } } }

    override fun addListener(l: Player.Listener) { listeners += l }
    override fun removeListener(l: Player.Listener) { listeners -= l }

    override fun setMediaItem(item: MediaItem) { setMediaItems(listOf(item)) }
    override fun setMediaItem(item: MediaItem, startPositionMs: Long) { setMediaItems(listOf(item)); posUs = startUs + startPositionMs * 1000 }
    override fun setMediaItems(items: List<MediaItem>) {
        synchronized(lock) { this.items.clear(); this.items += items; index = 0; gen++ }
        stopEngine(); info = null; file = null
        load()
        if (state != Player.STATE_IDLE) prepare()
    }
    override fun addMediaItem(item: MediaItem) { items += item }
    override fun clearMediaItems() { stopEngine(); synchronized(lock) { items.clear(); gen++ }; info = null; file = null; setState(Player.STATE_IDLE) }

    private fun load() {
        val it = items.getOrNull(index) ?: return
        val clip = it.clippingConfiguration
        startUs = clip.startPositionMs * 1000
        endUs = if (clip.endPositionMs == C.TIME_END_OF_SOURCE) Long.MAX_VALUE else clip.endPositionMs * 1000
        posUs = startUs
        file = it.localConfiguration?.uri?.let { u -> try { MediaFiles.local(u) } catch (_: Throwable) { null } }
    }

    override fun prepare() {
        if (items.isEmpty()) return
        if (file == null) load()
        val f = file
        if (f == null || !f.exists()) { fail("Can't find the video file", PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND); return }
        setState(Player.STATE_BUFFERING)
        val g = gen
        Thread({
            val i = VideoFile.info(f)
            if (g != gen) return@Thread
            if (i == null) { fail("This file can't be played on this PC", PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED); return@Thread }
            info = i
            if (endUs == Long.MAX_VALUE || endUs > i.durationUs) endUs = i.durationUs.coerceAtLeast(startUs + 1)
            if (i.hasVideo) {
                val rot = i.rotation
                size = if (rot == 90 || rot == 270) VideoSize(i.height, i.width) else VideoSize(i.width, i.height)
                main { listeners.forEach { l -> l.onVideoSizeChanged(size) } }
            }
            showAt(posUs, g)
            if (g != gen) return@Thread
            setState(Player.STATE_READY)
            if (pwr) startEngine()
        }, "player-prepare").apply { isDaemon = true; start() }
    }

    private fun fail(msg: String, code: Int) {
        val e = PlaybackException(msg, null, code)
        error = e
        setState(Player.STATE_IDLE)
        main { listeners.forEach { it.onPlayerError(e) } }
    }

    // ---------------------------------------------------------------- pictures

    private var pic: VideoFile? = null
    private var picLimit = 0
    /** Pictures are read at most at this size (the preview is never bigger than the screen). */
    private fun limit(): Pair<Int, Int> {
        val v = view
        val w = (v?.width ?: 0).coerceAtLeast(640); val h = (v?.height ?: 0).coerceAtLeast(360)
        val k = maxOf(w, h).coerceAtMost(1920)
        return k to k
    }
    private fun picReader(): VideoFile? {
        val f = file ?: return null
        if (info?.hasVideo != true) return null
        val (w, h) = limit()
        if (pic == null || picLimit != w) { pic?.close(); pic = try { VideoFile(f, w, h) } catch (_: Throwable) { null }; picLimit = w }
        return pic
    }
    private var firstShown = false
    private fun present(fr: VideoFile.Frame) {
        val v = view ?: return
        val rot = info?.rotation ?: 0
        if (rot == 0) v.show(fr.px, fr.width, fr.height)
        else { val (px, w, h) = rotate(fr.px, fr.width, fr.height, rot); v.show(px, w, h) }
        if (!firstShown) { firstShown = true; main { listeners.forEach { it.onRenderedFirstFrame() } } }
    }
    private fun rotate(px: IntArray, w: Int, h: Int, deg: Int): Triple<IntArray, Int, Int> {
        val out = IntArray(w * h)
        when (deg) {
            90 -> { for (y in 0 until h) for (x in 0 until w) out[x * h + (h - 1 - y)] = px[y * w + x]; return Triple(out, h, w) }
            270 -> { for (y in 0 until h) for (x in 0 until w) out[(w - 1 - x) * h + y] = px[y * w + x]; return Triple(out, h, w) }
            else -> { for (i in px.indices) out[px.size - 1 - i] = px[i]; return Triple(out, w, h) }
        }
    }

    /** Shows the picture at [us] (paused seeking). */
    private fun showAt(us: Long, g: Int) {
        synchronized(this) {
            val r = picReader() ?: return
            val fr = try { r.frameAt(us) } catch (_: Throwable) { null } ?: return
            if (g == gen) present(fr)
        }
    }

    // ---------------------------------------------------------------- playing

    private fun startEngine() {
        if (engine?.isAlive == true) return
        val g = gen
        engine = Thread({ playLoop(g) }, "player").apply { isDaemon = true; priority = Thread.MAX_PRIORITY - 1; start() }
        main { listeners.forEach { it.onIsPlayingChanged(true) } }
    }
    private fun stopEngine() {
        gen++
        val e = engine; engine = null
        if (e != null && e !== Thread.currentThread()) { e.interrupt(); try { e.join(1500) } catch (_: InterruptedException) {} }
    }

    private fun openLine(): SourceDataLine? = try {
        val f = JFormat(48000f, 16, 2, true, false)
        val inf = DataLine.Info(SourceDataLine::class.java, f)
        val m = android.media.AudioTrack.preferredOutput?.let { name -> AudioSystem.getMixerInfo().firstOrNull { it.name == name } }
        ((if (m != null) AudioSystem.getMixer(m).getLine(inf) else AudioSystem.getLine(inf)) as SourceDataLine).also { it.open(f, 48000 * 4 / 8); it.start() }
    } catch (_: Throwable) { null }

    private fun playLoop(g: Int) {
        val f = file ?: return
        val i = info ?: return
        val from = posUs
        val audio = if (i.hasAudio) try { AudioFile(f).also { it.seek(from) } } catch (_: Throwable) { null } else null
        val line = if (audio != null) openLine() else null
        // The sound steps, set up for this run.
        val chain = processors.filter { p -> try { p.configure(AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)); p.flush(); p.isActive() } catch (_: Throwable) { false } }
        val vr = synchronized(this) { picReader() }
        try { vr?.seek(from) } catch (_: Throwable) {}
        var nextPic: VideoFile.Frame? = null
        var picEnd = false
        val t0 = System.nanoTime()
        var written = 0L                         // output frames given to the speakers
        var srcPos = 0.0                         // fractional read position for speed changes
        var aDone = audio == null
        var pending: ShortArray? = null; var pendingAt = 0; var pendingUs = from
        val outBuf = ByteArray(4096 * 4)
        try {
            while (g == gen && !released) {
                // 1. Sound: keep about 120 ms ahead in the speaker buffer.
                if (!aDone && line != null) {
                    val queuedFrames = written - line.longFramePosition
                    if (queuedFrames < 48000 * 12 / 100) {
                        if (pending == null || pendingAt >= pending.size / 2) {
                            val b = audio!!.next()
                            if (b == null) aDone = true else {
                                // Sound before the starting point (reading starts a little earlier): left out.
                                val skip = ((from - b.timeUs) * 48000 / 1_000_000L).toInt().coerceIn(0, b.n)
                                if (skip >= b.n && b.timeUs < from) { pending = null; continue }
                                var pcm = b.pcm.copyOfRange(skip * 2, b.n * 2)
                                if (b.timeUs >= endUs) aDone = true
                                if (chain.isNotEmpty()) pcm = runChain(chain, pcm)
                                pending = pcm; pendingAt = 0; pendingUs = b.timeUs
                            }
                        }
                        val p = pending
                        if (p != null && pendingAt < p.size / 2) {
                            val sp = speed.toDouble()
                            var n = 0
                            val v = vol
                            while (n < outBuf.size / 4 && pendingAt < p.size / 2) {
                                val l = (p[pendingAt * 2] * v).toInt().coerceIn(-32768, 32767); val r = (p[pendingAt * 2 + 1] * v).toInt().coerceIn(-32768, 32767)
                                outBuf[n * 4] = l.toByte(); outBuf[n * 4 + 1] = (l shr 8).toByte(); outBuf[n * 4 + 2] = r.toByte(); outBuf[n * 4 + 3] = (r shr 8).toByte()
                                n++
                                srcPos += sp
                                while (srcPos >= 1.0) { srcPos -= 1.0; pendingAt++ }
                            }
                            line.write(outBuf, 0, n * 4)
                            written += n
                        }
                    }
                }
                // 2. The clock: the sound being heard now (or wall time when there is no sound).
                val nowUs = if (line != null && !aDone) from + (line.longFramePosition * 1_000_000L / 48000 * speed).toLong()
                            else from + ((System.nanoTime() - t0) / 1000 * speed).toLong()
                posUs = nowUs.coerceAtMost(endUs)
                // 3. Pictures whose time has come.
                if (vr != null && !picEnd) {
                    while (true) {
                        if (nextPic == null) { val fr = try { vr.next() } catch (_: Throwable) { null }; if (fr == null) { picEnd = true; break }; nextPic = VideoFile.Frame(fr.px.copyOf(fr.width * fr.height), fr.width, fr.height, fr.timeUs) }
                        val np = nextPic!!
                        if (np.timeUs < startUs - 20_000) { nextPic = null; continue }
                        if (np.timeUs <= nowUs + 8_000) {
                            // Late by more than a frame: skip to the next unless it's the last one due.
                            present(np); nextPic = null
                        } else break
                    }
                }
                // 4. The end of the clip.
                val soundOver = aDone || line == null
                if (nowUs >= endUs || (soundOver && (vr == null || picEnd) && (line == null || line.available() >= line.bufferSize - 64))) {
                    posUs = endUs
                    if (rep == Player.REPEAT_MODE_ONE || (rep == Player.REPEAT_MODE_ALL && items.size == 1)) { posUs = startUs; main { if (pwr) { stopEngine(); startEngine() } }; return }
                    if (index + 1 < items.size) { main { advanceItem() }; return }
                    pwr = false
                    setState(Player.STATE_ENDED)
                    main { listeners.forEach { it.onIsPlayingChanged(false) } }
                    return
                }
                Thread.sleep(4)
            }
        } catch (_: InterruptedException) {
        } catch (t: Throwable) {
            fail(t.message ?: "Playback stopped", PlaybackException.ERROR_CODE_DECODING_FAILED)
        } finally {
            try { line?.stop(); line?.flush(); line?.close() } catch (_: Throwable) {}
            audio?.close()
        }
    }

    private fun runChain(chain: List<AudioProcessor>, pcm: ShortArray): ShortArray {
        var buf = ByteBuffer.allocateDirect(pcm.size * 2).order(ByteOrder.nativeOrder()); buf.asShortBuffer().put(pcm); buf.limit(pcm.size * 2)
        for (p in chain) {
            p.queueInput(buf)
            val o = p.getOutput()
            buf = if (o.hasRemaining()) o else buf
        }
        val out = ShortArray(buf.remaining() / 2)
        buf.order(ByteOrder.nativeOrder()).asShortBuffer().get(out)
        return out
    }

    private fun advanceItem() {
        stopEngine()
        index++
        load(); info = null
        main { listeners.forEach { it.onMediaItemTransition(items.getOrNull(index), Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) } }
        prepare()
    }

    // ---------------------------------------------------------------- controls

    override fun play() { pwr = true; if (state == Player.STATE_ENDED) { posUs = startUs; setState(Player.STATE_READY) }; if (state == Player.STATE_READY) startEngine() }
    override fun pause() {
        val was = pwr && engine != null
        pwr = false; stopEngine()
        if (was) main { listeners.forEach { it.onIsPlayingChanged(false) } }
    }
    override fun stop() { pause(); setState(Player.STATE_IDLE) }
    override fun release() { released = true; pause(); synchronized(this) { pic?.close(); pic = null } }

    override fun seekTo(positionMs: Long) {
        val playing = pwr && engine != null
        stopEngine()
        posUs = (startUs + positionMs * 1000).coerceIn(startUs, if (endUs == Long.MAX_VALUE) Long.MAX_VALUE else endUs)
        if (state == Player.STATE_ENDED) setState(Player.STATE_READY)
        val g = gen; val at = posUs
        if (state == Player.STATE_READY) {
            if (playing) startEngine()
            else seeker.execute { if (g == gen) showAt(at, g) }
        }
    }
    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        if (mediaItemIndex != index) { stopEngine(); index = mediaItemIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)); load(); info = null; posUs = startUs + positionMs * 1000; prepare() }
        else seekTo(positionMs)
    }
    private val seeker = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "player-seek").apply { isDaemon = true } }

    override var playWhenReady: Boolean
        get() = pwr
        set(v) { if (v) play() else pause() }
    override val playbackState: Int get() = state
    override val isPlaying: Boolean get() = pwr && state == Player.STATE_READY && engine != null
    override val currentPosition: Long get() = ((posUs - startUs) / 1000).coerceAtLeast(0)
    override val duration: Long get() = info?.let { ((endUs.coerceAtMost(it.durationUs) - startUs) / 1000).coerceAtLeast(0) } ?: C.TIME_UNSET
    override var volume: Float
        get() = vol
        set(v) { vol = v.coerceIn(0f, 4f); main { listeners.forEach { it.onVolumeChanged(vol) } } }
    override var repeatMode: Int
        get() = rep
        set(v) { rep = v }
    override var playbackParameters: PlaybackParameters
        get() = PlaybackParameters(speed)
        set(v) { speed = v.speed.coerceIn(0.05f, 16f); if (pwr && engine != null) { stopEngine(); startEngine() } }
    override val currentMediaItem: MediaItem? get() = items.getOrNull(index)
    override val currentMediaItemIndex: Int get() = index
    override val mediaItemCount: Int get() = items.size
    override val videoSize: VideoSize get() = size
    override val playerError: PlaybackException? get() = error

    override fun setVideoTextureView(v: android.view.TextureView?) { view = v; firstShown = false; if (v != null && state == Player.STATE_READY && !isPlaying) { val g = gen; seeker.execute { showAt(posUs, g) } } }
    override fun clearVideoTextureView(v: android.view.TextureView?) { if (view === v) view = null }
    /** Picture effects in the player (deflicker); the Windows preview leaves them to the export. */
    override fun setVideoEffects(effects: List<Effect>) {}
    override fun setSeekParameters(p: SeekParameters?) {}
}
