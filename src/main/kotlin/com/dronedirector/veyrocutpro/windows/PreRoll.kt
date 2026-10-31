package com.dronedirector.veyrocutpro.camera

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pre-record on Windows: while you wait, the last few seconds of picture (as JPEG, to keep memory
 * small) and sound are kept, and a recording starts with them. Own code.
 */
class PreRoll(private val seconds: Int) {
    /** What a recording starts with: pictures with their times (System.nanoTime) and the sound from [pcmStartNs]. */
    class Take(val frames: List<Pair<ByteArray, Long>>, val pcm: FloatArray, val pcmStartNs: Long, val width: Int, val height: Int) {
        val startNs: Long get() = minOf(frames.firstOrNull()?.second ?: pcmStartNs, pcmStartNs)
    }

    private val frames = ArrayDeque<Pair<ByteArray, Long>>()
    private val busy = AtomicBoolean(false)
    private val enc = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "pre-roll").apply { isDaemon = true } }
    @Volatile private var w = 0; @Volatile private var h = 0
    private val rate = 48_000
    private val ring = FloatArray(rate * 2 * (seconds + 1))
    private var ringPos = 0; private var ringFilled = 0; private var ringEndNs = 0L
    @Volatile private var running = true
    private val audio = Thread({ listen() }, "pre-roll-audio").apply { isDaemon = true; start() }

    /** A picture (ARGB) from the camera; kept when the encoder is free (lower rate on slow PCs). */
    fun frame(px: IntArray, width: Int, height: Int) {
        if (!running || !busy.compareAndSet(false, true)) return
        val copy = px.copyOf(width * height); val t = System.nanoTime()
        enc.execute {
            try {
                val img = java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB)
                img.setRGB(0, 0, width, height, copy, 0, width)
                val o = java.io.ByteArrayOutputStream()
                val wr = javax.imageio.ImageIO.getImageWritersByFormatName("jpeg").next()
                val prm = wr.defaultWriteParam.apply { compressionMode = javax.imageio.ImageWriteParam.MODE_EXPLICIT; compressionQuality = 0.92f }
                javax.imageio.ImageIO.createImageOutputStream(o).use { s -> wr.output = s; wr.write(null, javax.imageio.IIOImage(img, null, null), prm) }
                wr.dispose()
                synchronized(frames) {
                    if (width != w || height != h) { frames.clear(); w = width; h = height }
                    frames.addLast(o.toByteArray() to t)
                    while (frames.isNotEmpty() && t - frames.first().second > seconds * 1_000_000_000L) frames.removeFirst()
                }
            } catch (_: Throwable) {} finally { busy.set(false) }
        }
    }

    private fun listen() {
        val rec = android.media.AudioRecord(android.media.MediaRecorder.AudioSource.CAMCORDER, rate, android.media.AudioFormat.CHANNEL_IN_STEREO, android.media.AudioFormat.ENCODING_PCM_16BIT, 1 shl 15)
        if (rec.state != android.media.AudioRecord.STATE_INITIALIZED) return
        val ch = rec.channelCount
        val raw = ShortArray(960 * ch)
        try {
            rec.startRecording()
            while (running) {
                val got = rec.read(raw, 0, raw.size); if (got <= 0) continue
                val n = got / ch
                synchronized(ring) {
                    for (i in 0 until n) for (c in 0..1) { ring[ringPos] = raw[i * ch + (if (ch == 2) c else 0)] / 32768f; ringPos = (ringPos + 1) % ring.size }
                    ringFilled = minOf(ring.size, ringFilled + n * 2); ringEndNs = System.nanoTime()
                }
            }
        } catch (_: Throwable) {} finally { try { rec.stop() } catch (_: Throwable) {}; rec.release() }
    }

    /** Stops keeping and hands over what was kept. */
    fun take(): Take {
        running = false
        audio.join(500)
        enc.shutdown(); enc.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS)
        val f = synchronized(frames) { frames.toList() }
        val pcm = synchronized(ring) { FloatArray(ringFilled) { i -> ring[(ringPos - ringFilled + i + ring.size) % ring.size] } }
        val start = ringEndNs - pcm.size / 2 * 1_000_000_000L / rate
        val first = f.firstOrNull()?.second ?: System.nanoTime()
        // Sound from the first kept picture on.
        val skip = (((first - start).coerceAtLeast(0) * rate / 1_000_000_000L).toInt() * 2).coerceIn(0, pcm.size)
        return Take(f, pcm.copyOfRange(skip, pcm.size), start + skip / 2 * 1_000_000_000L / rate, w, h)
    }

    fun close() { running = false; enc.shutdownNow() }
}
