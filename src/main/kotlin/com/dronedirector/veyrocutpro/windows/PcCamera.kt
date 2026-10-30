package com.dronedirector.veyrocutpro.camera

import com.dronedirector.veyrocutpro.windows.MfNative

/** A camera on this PC (webcam, capture card, camera in webcam mode). */
data class PcDevice(val link: String, val name: String)

/** One of the camera's own picture formats. */
data class PcFormat(val index: Int, val width: Int, val height: Int, val fps: Float, val fourcc: String)

/** A camera control the camera offers (kind 0 = camera: zoom, exposure, focus…; kind 1 = picture: brightness, white balance, gain…). */
data class PcControl(val kind: Int, val id: Int, val min: Int, val max: Int, val step: Int, val default: Int, var value: Int, var auto: Boolean, val canAuto: Boolean)

/**
 * A PC camera through Media Foundation: its formats, controls, and a reading thread that hands
 * every picture (ARGB, top row first) to [onFrame]. Own code.
 */
class PcCamera(val device: PcDevice) {
    companion object {
        // Camera controls (kind 0).
        const val PAN = 0; const val TILT = 1; const val ROLL = 2; const val ZOOM = 3; const val EXPOSURE = 4; const val IRIS = 5; const val FOCUS = 6
        // Picture controls (kind 1).
        const val BRIGHTNESS = 0; const val CONTRAST = 1; const val HUE = 2; const val SATURATION = 3; const val SHARPNESS = 4; const val GAMMA = 5
        const val COLOR_ENABLE = 6; const val WHITE_BALANCE = 7; const val BACKLIGHT = 8; const val GAIN = 9; const val POWERLINE = 13

        fun list(): List<PcDevice> = if (!MfNative.ok) emptyList() else try {
            MfNative.list().mapNotNull { s -> s.split('\u0001').takeIf { it.size == 2 }?.let { PcDevice(it[0], it[1]) } }
        } catch (_: Throwable) { emptyList() }
    }

    private var h = 0L
    var formats: List<PcFormat> = emptyList(); private set
    var controls: List<PcControl> = emptyList(); private set
    var width = 0; private set
    var height = 0; private set
    var current: PcFormat? = null; private set
    @Volatile private var running = false
    private var thread: Thread? = null
    private val lock = Object()

    fun open(): Boolean {
        if (!MfNative.ok) return false
        h = MfNative.open(device.link)
        if (h == 0L) return false
        formats = MfNative.formats(h).mapNotNull { line ->
            val p = line.split(' ')
            if (p.size < 6) null else {
                val num = p[3].toFloatOrNull() ?: 0f; val den = p[4].toFloatOrNull()?.takeIf { it > 0f } ?: 1f
                PcFormat(p[0].toInt(), p[1].toInt(), p[2].toInt(), num / den, p[5])
            }
        }
        refreshControls()
        return true
    }

    fun refreshControls() {
        if (h == 0L) return
        controls = try {
            MfNative.controls(h).mapNotNull { line ->
                val p = line.split(' ').mapNotNull { it.toIntOrNull() }
                if (p.size < 9) null else PcControl(p[0], p[1], p[2], p[3], p[4].coerceAtLeast(1), p[5], p[6], p[7] == 1, p[8] == 1)
            }
        } catch (_: Throwable) { emptyList() }
    }

    fun control(kind: Int, id: Int): PcControl? = controls.firstOrNull { it.kind == kind && it.id == id }

    fun set(kind: Int, id: Int, value: Int, auto: Boolean = false): Boolean {
        val c = control(kind, id) ?: return false
        val v = value.coerceIn(c.min, c.max)
        if (c.value == v && c.auto == auto) return true
        val ok = try { MfNative.setControl(h, kind, id, v, auto && c.canAuto) } catch (_: Throwable) { false }
        if (ok) { c.value = v; c.auto = auto && c.canAuto }
        return ok
    }

    /** Best native format for [w]×[h] near [fps]: matching size, the frame rate closest above, uncompressed first. */
    fun pick(w: Int, h: Int, fps: Float): PcFormat? {
        val same = formats.filter { it.width == w && it.height == h }.ifEmpty { return null }
        val order = listOf("NV12", "YUY2", "MJPG", "H264")
        return same.sortedWith(compareBy<PcFormat>({ if (it.fps + 0.5f >= fps) it.fps - fps else 1000f + (fps - it.fps) }, { order.indexOf(it.fourcc).let { i -> if (i < 0) 9 else i } })).first()
    }

    /** Starts reading [f]; every picture goes to [onFrame] (on the camera thread). */
    fun start(f: PcFormat, onFrame: (IntArray, Int, Int, Long) -> Unit, onLost: () -> Unit): Boolean {
        stop()
        val wh = try { MfNative.setFormat(h, f.index) } catch (_: Throwable) { 0 }
        if (wh == 0) return false
        width = (wh ushr 16) and 0xFFFF; height = wh and 0xFFFF; current = f
        running = true
        thread = Thread({
            // Two buffers: one being shown / saved while the next is read.
            val bufs = arrayOf(IntArray(width * height), IntArray(width * height))
            var k = 0; var errors = 0
            while (running) {
                val buf = bufs[k]
                val ts = synchronized(lock) { if (!running) -1L else MfNative.read(h, buf) }
                when {
                    ts >= 0 -> { errors = 0; onFrame(buf, width, height, ts); k = 1 - k }
                    ts == -3L -> {}
                    else -> { if (++errors > 30 || ts == -1L) { if (running) { running = false; onLost() }; break } }
                }
            }
        }, "pc-camera").apply { isDaemon = true; priority = Thread.MAX_PRIORITY - 1; start() }
        return true
    }

    fun stop() {
        running = false
        thread?.let { if (it !== Thread.currentThread()) it.join(1500) }
        thread = null
    }

    fun close() {
        stop()
        synchronized(lock) { if (h != 0L) try { MfNative.close(h) } catch (_: Throwable) {}; h = 0L }
    }
}
