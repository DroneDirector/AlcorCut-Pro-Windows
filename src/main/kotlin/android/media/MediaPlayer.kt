package android.media

import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import javax.sound.sampled.AudioFormat as JFormat

/**
 * Plays a sound file (sound effects, library previews) through the chosen speaker / headphones,
 * read with Windows' media engine. Own code.
 */
class MediaPlayer {
    var isLooping = false
    private var file: java.io.File? = null
    @Volatile private var left = 1f
    @Volatile private var right = 1f
    @Volatile private var playing = false
    @Volatile private var startMs = 0L
    @Volatile private var posMs = 0L
    private var thread: Thread? = null
    private var completion: ((MediaPlayer) -> Unit)? = null
    private var prepared: ((MediaPlayer) -> Unit)? = null
    var duration: Int = 0; private set
    val isPlaying: Boolean get() = playing
    val currentPosition: Int get() = posMs.toInt()

    fun interface OnCompletionListener { fun onCompletion(mp: MediaPlayer) }
    fun interface OnPreparedListener { fun onPrepared(mp: MediaPlayer) }

    fun setDataSource(path: String) { file = java.io.File(path) }
    fun setDataSource(c: android.content.Context, u: android.net.Uri) { file = com.dronedirector.veyrocutpro.windows.MediaFiles.local(u) }
    fun setDataSource(afd: android.content.res.AssetFileDescriptor) { file = afd.file }
    fun setOnCompletionListener(l: OnCompletionListener?) { completion = l?.let { x -> { mp: MediaPlayer -> x.onCompletion(mp) } } }
    fun setOnPreparedListener(l: OnPreparedListener?) { prepared = l?.let { x -> { mp: MediaPlayer -> x.onPrepared(mp) } } }
    fun setVolume(l: Float, r: Float) { left = l; right = r }
    fun setAudioAttributes(a: AudioAttributes?) {}
    fun prepare() {
        val f = file ?: throw java.io.IOException("No file")
        duration = ((com.dronedirector.veyrocutpro.windows.VideoFile.info(f)?.durationUs ?: throw java.io.IOException("Can't read ${f.name}")) / 1000).toInt()
        prepared?.invoke(this)
    }
    fun prepareAsync() { Thread { try { prepare() } catch (_: Throwable) {} }.start() }
    fun seekTo(ms: Int) { startMs = ms.toLong(); posMs = startMs; if (playing) { stopThread(); start() } }
    fun start() {
        val f = file ?: return
        if (playing) return
        playing = true
        thread = Thread({
            do {
                var line: SourceDataLine? = null
                try {
                    com.dronedirector.veyrocutpro.windows.AudioFile(f).use { a ->
                        a.seek(startMs * 1000)
                        val fmt = JFormat(48000f, 16, 2, true, false)
                        val inf = DataLine.Info(SourceDataLine::class.java, fmt)
                        val m = AudioTrack.preferredOutput?.let { name -> AudioSystem.getMixerInfo().firstOrNull { it.name == name } }
                        line = ((if (m != null) AudioSystem.getMixer(m).getLine(inf) else AudioSystem.getLine(inf)) as SourceDataLine).also { it.open(fmt, 48000); it.start() }
                        val buf = ByteArray(4096 * 4)
                        while (playing) {
                            val b = a.next() ?: break
                            val skip = (((startMs * 1000) - b.timeUs) * 48 / 1000).toInt().coerceIn(0, b.n)
                            var n = 0
                            for (i in skip until b.n) {
                                if (n * 4 + 4 > buf.size) { line!!.write(buf, 0, n * 4); n = 0 }
                                val l = (b.pcm[i * 2] * left).toInt().coerceIn(-32768, 32767); val r = (b.pcm[i * 2 + 1] * right).toInt().coerceIn(-32768, 32767)
                                buf[n * 4] = l.toByte(); buf[n * 4 + 1] = (l shr 8).toByte(); buf[n * 4 + 2] = r.toByte(); buf[n * 4 + 3] = (r shr 8).toByte(); n++
                            }
                            if (n > 0) line!!.write(buf, 0, n * 4)
                            posMs = b.timeUs / 1000
                        }
                        if (playing) line!!.drain()
                    }
                } catch (_: Throwable) {
                } finally { try { line?.close() } catch (_: Throwable) {} }
                startMs = 0
            } while (playing && isLooping)
            val wasPlaying = playing
            playing = false
            if (wasPlaying) javax.swing.SwingUtilities.invokeLater { completion?.invoke(this) }
        }, "sound").apply { isDaemon = true; start() }
    }
    fun pause() { startMs = posMs; stopThread() }
    fun stop() { stopThread(); startMs = 0 }
    fun reset() { stop(); file = null }
    fun release() { stopThread() }
    private fun stopThread() { playing = false; val t = thread; thread = null; if (t != null && t !== Thread.currentThread()) t.join(500) }
}
