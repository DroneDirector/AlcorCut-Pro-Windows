package com.dronedirector.veyrocutpro.camera

import com.dronedirector.veyrocutpro.windows.MfNative
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A camera connected by USB that Windows sees as a camera device (PTP). */
data class TetherDevice(val id: String, val name: String, val maker: String) {
    val link: String get() = "ptp:$id"
}

/**
 * Tethered shooting with a pro camera over USB (PTP, the cameras' own picture-transfer protocol,
 * through Windows Portable Devices): shutter from the PC, the camera's ISO / shutter / aperture /
 * white balance / exposure compensation where it offers them as standard settings, live view on
 * Nikon and Canon EOS bodies, and every new photo copied to the PC as soon as it's taken. Own code.
 */
class TetherCamera(val device: TetherDevice) {
    companion object {
        fun list(): List<TetherDevice> = if (!MfNative.ok) emptyList() else try {
            MfNative.ptpList().mapNotNull { s ->
                val p = s.split('\u0001')
                if (p.size < 3) null else TetherDevice(p[0], p[1], p[2])
            }.filter { d ->
                // Cameras only (phones and music players also show up as portable devices).
                listOf("canon", "nikon", "sony", "fujifilm", "panasonic", "olympus", "om digital", "pentax", "ricoh", "leica", "sigma", "hasselblad", "gopro", "dji")
                    .any { d.maker.lowercase().contains(it) || d.name.lowercase().contains(it) }
            }
        } catch (_: Throwable) { emptyList() }

        const val ISO = 0x500F; const val SHUTTER = 0x500D; const val APERTURE = 0x5007; const val WB = 0x5005; const val EV = 0x5010
    }

    private var h = 0L
    private val lock = Any()
    private val canon = device.maker.contains("canon", true) || device.name.contains("canon", true)
    private val nikon = device.maker.contains("nikon", true) || device.name.contains("nikon", true)
    var operations: Set<Int> = emptySet(); private set
    var properties: Set<Int> = emptySet(); private set
    var model = device.name; private set

    /** A camera setting and the values it allows (null = any in range). */
    class Prop(val code: Int, val type: Int, var value: Long, val allowed: List<Long>, val writable: Boolean)

    private class Reply(val code: Int, val params: IntArray, val data: ByteArray) { val ok get() = code == 0x2001 }

    private fun run(op: Int, vararg params: Int, data: ByteArray? = null, read: Boolean = false): Reply? = synchronized(lock) {
        if (h == 0L) return null
        val r = try { MfNative.ptpCommand(h, op, params, data, read) } catch (_: Throwable) { null } ?: return null
        if (r.size < 3) return null
        val code = (r[0].toInt() and 0xFF) or ((r[1].toInt() and 0xFF) shl 8)
        val n = r[2].toInt() and 0xFF
        val ps = IntArray(n) { i -> ByteBuffer.wrap(r, 3 + i * 4, 4).order(ByteOrder.LITTLE_ENDIAN).int }
        Reply(code, ps, r.copyOfRange(minOf(r.size, 3 + n * 4), r.size))
    }

    fun open(): Boolean {
        h = try { MfNative.ptpOpen(device.id) } catch (_: Throwable) { 0L }
        if (h == 0L) return false
        // What the camera can do (GetDeviceInfo).
        run(0x1001, read = true)?.takeIf { it.ok }?.let { parseInfo(it.data) }
        if (canon) {
            run(0x9114, 1)            // remote mode
            run(0x9115, 1)            // event mode
        }
        return true
    }

    private fun parseInfo(d: ByteArray) {
        val b = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN)
        fun str(): String { val n = b.get().toInt() and 0xFF; val sb = StringBuilder(); repeat(n) { val c = b.short.toInt().toChar(); if (c != '\u0000') sb.append(c) }; return sb.toString() }
        fun arr16(): Set<Int> { val n = b.int; return (0 until n).map { b.short.toInt() and 0xFFFF }.toSet() }
        try {
            b.short; b.int; b.short; str(); b.short
            operations = arr16(); arr16(); properties = arr16(); arr16(); arr16()
            str(); model = str().ifBlank { device.name }
        } catch (_: Throwable) {}
    }

    /** A standard setting's value and choices (GetDevicePropDesc), or null when the camera doesn't offer it. */
    fun prop(code: Int): Prop? {
        if (code !in properties) return null
        val r = run(0x1014, code, read = true)?.takeIf { it.ok } ?: return null
        val b = ByteBuffer.wrap(r.data).order(ByteOrder.LITTLE_ENDIAN)
        return try {
            b.short; val type = b.short.toInt(); val rw = b.get().toInt() == 1
            fun v(): Long = when (type) { 1 -> b.get().toLong(); 2 -> (b.get().toInt() and 0xFF).toLong(); 3 -> b.short.toLong(); 4 -> (b.short.toInt() and 0xFFFF).toLong(); 5 -> b.int.toLong(); 6 -> b.int.toLong() and 0xFFFFFFFFL; else -> b.int.toLong() }
            v(); val cur = v()
            val form = b.get().toInt()
            val allowed = when (form) {
                2 -> { val n = b.short.toInt() and 0xFFFF; (0 until n).map { v() } }
                1 -> { val mn = v(); val mx = v(); val st = v().coerceAtLeast(1); if ((mx - mn) / st <= 400) (mn..mx step st).toList() else emptyList() }
                else -> emptyList()
            }
            Prop(code, type, cur, allowed, rw)
        } catch (_: Throwable) { null }
    }

    /** Sets a standard setting (SetDevicePropValue). */
    fun set(p: Prop, value: Long): Boolean {
        val size = when (p.type) { 1, 2 -> 1; 3, 4 -> 2; else -> 4 }
        val b = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        when (size) { 1 -> b.put(value.toByte()); 2 -> b.putShort(value.toShort()); else -> b.putInt(value.toInt()) }
        val ok = run(0x1016, p.code, data = b.array())?.ok == true
        if (ok) p.value = value
        return ok
    }

    /** Takes a photo (the camera saves it to its card as usual). */
    fun shoot(): Boolean {
        if (canon && 0x9128 in operations) {
            val a = run(0x9128, 3, 0)          // half and full press
            Thread.sleep(150)
            run(0x9129, 3)
            if (a?.ok == true) return true
        }
        if (nikon && 0x90C0 in operations && run(0x90C0)?.ok == true) return true
        return run(0x100E, 0, 0)?.ok == true
    }

    // ---------------------------------------------------------------- live view

    private var liveStarted = false
    val hasLiveView: Boolean get() = (nikon && 0x9203 in operations) || (canon && 0x9153 in operations)

    /** The camera's live picture as JPEG, or null. */
    fun liveFrame(): ByteArray? {
        if (!liveStarted) {
            liveStarted = true
            if (nikon) run(0x9201)
            if (canon) { setCanon(0xD1B0, 1); setCanon(0xD1B1, 2) }
            Thread.sleep(300)
        }
        val r = when {
            nikon -> run(0x9203, read = true)
            canon -> run(0x9153, 0x00100000, read = true)
            else -> null
        }?.takeIf { it.ok } ?: return null
        val d = r.data
        var a = -1
        for (i in 0 until d.size - 1) if (d[i] == 0xFF.toByte() && d[i + 1] == 0xD8.toByte()) { a = i; break }
        if (a < 0) return null
        var e = d.size
        for (i in d.size - 2 downTo a) if (d[i] == 0xFF.toByte() && d[i + 1] == 0xD9.toByte()) { e = i + 2; break }
        return d.copyOfRange(a, e)
    }

    fun stopLive() {
        if (!liveStarted) return
        liveStarted = false
        if (nikon) run(0x9202)
        if (canon) setCanon(0xD1B1, 1)
    }

    /** Canon EOS settings (SetDevicePropValueEx: size, code, value). */
    private fun setCanon(code: Int, value: Int) {
        val b = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).putInt(12).putInt(code).putInt(value)
        run(0x9110, data = b.array())
    }

    // ---------------------------------------------------------------- new photos

    private var known: Set<Int>? = null

    /** Copies photos added on the camera since the last call into [dir]; returns the new files. */
    fun fetchNew(dir: (String) -> File): List<File> {
        val r = run(0x1007, -1, 0, 0, read = true)?.takeIf { it.ok } ?: return emptyList()
        val b = ByteBuffer.wrap(r.data).order(ByteOrder.LITTLE_ENDIAN)
        val handles = try { (0 until b.int).map { b.int }.toSet() } catch (_: Throwable) { return emptyList() }
        val before = known
        known = handles
        if (before == null) return emptyList()
        val out = ArrayList<File>()
        for (hd in handles - before) {
            val info = run(0x1008, hd, read = true)?.takeIf { it.ok }?.data ?: continue
            val ib = ByteBuffer.wrap(info).order(ByteOrder.LITTLE_ENDIAN)
            val name = try {
                ib.position(4); val fmt = ib.short.toInt() and 0xFFFF
                if (fmt == 0x3001) continue                     // a folder
                ib.position(52)
                val n = ib.get().toInt() and 0xFF; val sb = StringBuilder(); repeat(n) { val c = ib.short.toInt().toChar(); if (c != '\u0000') sb.append(c) }; sb.toString()
            } catch (_: Throwable) { "IMG_$hd.JPG" }
            val data = run(0x1009, hd, read = true)?.takeIf { it.ok }?.data ?: continue
            val f = dir(name.ifBlank { "IMG_$hd.JPG" })
            try { f.writeBytes(data); out += f } catch (_: Throwable) {}
        }
        return out
    }

    fun close() {
        try { stopLive() } catch (_: Throwable) {}
        synchronized(lock) { if (h != 0L) try { MfNative.ptpClose(h) } catch (_: Throwable) {}; h = 0L }
    }
}
