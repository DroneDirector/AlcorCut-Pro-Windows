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
 * through Windows Portable Devices): shutter from the PC, the camera's ISO / shutter / white balance /
 * exposure compensation (standard settings, Canon EOS and Sony Alpha settings), live view on Nikon,
 * Canon EOS and Sony Alpha bodies, and every new photo copied to the PC as soon as it's taken. Own code.
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
    private val sony = device.maker.contains("sony", true) || device.name.contains("sony", true)
    private var sonyReady = false
    var operations: Set<Int> = emptySet(); private set
    var properties: Set<Int> = emptySet(); private set
    var model = device.name; private set

    /**
     * A camera setting and the values it allows (empty = any). Values are in standard units (ISO number,
     * 1/10000 s, white balance code, 1/1000 EV); [raw] gives the camera's own value for each where the
     * maker uses its own codes, and [vendor] the maker's setting code.
     */
    class Prop(val code: Int, val type: Int, var value: Long, val allowed: List<Long>, val writable: Boolean,
               val vendor: Int = 0, val raw: Map<Long, Long> = emptyMap())

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
            repeat(3) { canonEvents() }   // the first events list every setting and its choices
        }
        if (sony && 0x9201 in operations) {
            // Sony's PC remote handshake, then all its settings at once.
            run(0x9201, 1, 0, 0); run(0x9201, 2, 0, 0)
            run(0x9202, 0xC8, read = true)
            run(0x9201, 3, 0, 0)
            sonyReady = true
            sonyRead()
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
        if (canon && 0x9116 in operations) canonProp(code)?.let { return it }
        if (sonyReady) sonyProp(code)?.let { return it }
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
        if (p.vendor != 0) {
            val raw = p.raw[value] ?: value
            val ok = if (canon) { setCanon(p.vendor, raw.toInt()); true } else sonySet(p.vendor, p.type, raw)
            if (ok) p.value = value
            return ok
        }
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
        if (sonyReady) {
            // Half press, full press, release (Sony's shutter-button settings).
            sonyButton(0xD2C1, 2); Thread.sleep(200); sonyButton(0xD2C2, 2); Thread.sleep(150)
            sonyButton(0xD2C2, 1); sonyButton(0xD2C1, 1)
            return true
        }
        return run(0x100E, 0, 0)?.ok == true
    }

    // ---------------------------------------------------------------- live view

    private var liveStarted = false
    val hasLiveView: Boolean get() = (nikon && 0x9203 in operations) || (canon && 0x9153 in operations) || sonyReady

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
            sonyReady -> run(0x1009, 0xFFFFC002.toInt(), read = true)
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

    // ---------------------------------------------------------------- Canon EOS settings

    private val canonVal = HashMap<Int, Long>()
    private val canonChoices = HashMap<Int, List<Long>>()

    /** Reads Canon's event list (GetEvent): changed settings and the choices each one offers now. */
    private fun canonEvents() {
        val d = run(0x9116, read = true)?.takeIf { it.ok }?.data ?: return
        val b = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN)
        try {
            while (b.remaining() >= 8) {
                val start = b.position()
                val size = b.int; val type = b.int
                if (size < 8 || type == 0) break
                when (type) {
                    0xC189 -> { val code = b.int; canonVal[code] = b.int.toLong() and 0xFFFFFFFFL }
                    0xC18A -> { val code = b.int; b.int; val n = b.int; if (n in 0..512) canonChoices[code] = (0 until n).map { b.int.toLong() and 0xFFFFFFFFL } }
                }
                b.position(start + size)
            }
        } catch (_: Throwable) {}
    }

    private fun canonProp(code: Int): Prop? {
        val (vendor, toStd) = when (code) {
            ISO -> 0xD103 to { r: Long -> if (r < 0x28) null else nominalIso(100.0 * Math.pow(2.0, (r - 0x48) / 8.0)) }
            SHUTTER -> 0xD102 to { r: Long -> if (r < 0x10) null else Math.round(Math.pow(2.0, (0x38 - r) / 8.0) * 10000).coerceAtLeast(1) }
            EV -> 0xD104 to { r: Long -> eighthsToMilli(r.toInt().toByte().toInt()) }
            WB -> 0xD109 to { r: Long -> when (r) { 0L -> 2L; 1L -> 4L; 3L -> 6L; 4L -> 5L; 5L -> 7L; else -> null } }
            else -> return null
        }
        val cur = canonVal[vendor] ?: return null
        val raw = LinkedHashMap<Long, Long>()
        for (r in canonChoices[vendor].orEmpty()) toStd(r)?.let { if (it !in raw) raw[it] = r }
        if (raw.isEmpty()) return null
        return Prop(code, 6, toStd(cur) ?: raw.keys.first(), raw.keys.toList(), true, vendor, raw)
    }

    /** 1/8-stop steps (Canon's) as 1/1000 EV, with thirds kept as thirds. */
    private fun eighthsToMilli(e: Int): Long {
        val a = kotlin.math.abs(e); val frac = when (a % 8) { 3 -> 333; 4 -> 500; 5 -> 667; else -> (a % 8) * 125 }
        return ((a / 8) * 1000L + frac) * (if (e < 0) -1 else 1)
    }

    private fun nominalIso(v: Double): Long {
        val list = longArrayOf(50, 64, 80, 100, 125, 160, 200, 250, 320, 400, 500, 640, 800, 1000, 1250, 1600, 2000, 2500, 3200, 4000, 5000, 6400,
            8000, 10000, 12800, 16000, 20000, 25600, 32000, 40000, 51200, 64000, 80000, 102400, 128000, 160000, 204800, 409600, 819200)
        return list.minByOrNull { kotlin.math.abs(Math.log(it / v)) }!!
    }

    // ---------------------------------------------------------------- Sony Alpha settings

    private val sonyProps = HashMap<Int, Prop>()

    /** Reads every Sony setting with its choices (GetAllExtDevicePropInfo). */
    private fun sonyRead() {
        val d = run(0x9209, read = true)?.takeIf { it.ok }?.data ?: return
        val b = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN)
        try {
            val n = b.long.toInt()
            repeat(n.coerceIn(0, 1000)) {
                val code = b.short.toInt() and 0xFFFF; val type = b.short.toInt() and 0xFFFF
                b.get(); b.get()                         // writable, enabled
                fun v(): Long = when (type) { 1 -> b.get().toLong(); 2 -> (b.get().toInt() and 0xFF).toLong(); 3 -> b.short.toLong(); 4 -> (b.short.toInt() and 0xFFFF).toLong(); 5 -> b.int.toLong(); 6 -> b.int.toLong() and 0xFFFFFFFFL; 7, 8 -> b.long; 0xFFFF -> { val c = b.get().toInt() and 0xFF; b.position(b.position() + c * 2); 0L }; else -> throw IllegalStateException() }
                v(); val cur = v()
                val allowed = when (b.get().toInt()) {
                    1 -> { val mn = v(); val mx = v(); val st = v().coerceAtLeast(1); if ((mx - mn) / st <= 400) (mn..mx step st).toList() else emptyList() }
                    2 -> { val c = b.short.toInt() and 0xFFFF; (0 until c).map { v() } }
                    else -> emptyList()
                }
                sonyProps[code] = Prop(code, type, cur, allowed, true, code)
            }
        } catch (_: Throwable) {}
    }

    private fun sonyProp(code: Int): Prop? {
        val vendor = when (code) { ISO -> 0xD21E; SHUTTER -> 0xD20D; EV -> 0x5010; WB -> 0x5005; else -> return null }
        val p = sonyProps[vendor] ?: return null
        val toStd: (Long) -> Long? = when (code) {
            ISO -> { r -> (r and 0xFFFFFF).takeIf { it in 25..819200 } }
            SHUTTER -> { r -> val num = (r shr 16) and 0xFFFF; val den = r and 0xFFFF; if (num == 0L || den == 0L) null else Math.round(num * 10000.0 / den).coerceAtLeast(1) }
            else -> { r -> r }
        }
        val raw = LinkedHashMap<Long, Long>()
        for (r in p.allowed) toStd(r)?.let { if (it !in raw) raw[it] = r }
        return Prop(code, p.type, toStd(p.value) ?: raw.keys.firstOrNull() ?: return null, raw.keys.toList(), true, vendor, raw)
    }

    private fun sonySet(code: Int, type: Int, value: Long): Boolean {
        val size = when (type) { 1, 2 -> 1; 3, 4 -> 2; 7, 8 -> 8; else -> 4 }
        val b = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        when (size) { 1 -> b.put(value.toByte()); 2 -> b.putShort(value.toShort()); 8 -> b.putLong(value); else -> b.putInt(value.toInt()) }
        return run(0x9205, code, data = b.array())?.ok == true
    }

    private fun sonyButton(code: Int, value: Int) {
        run(0x9207, code, data = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array())
    }

    // ---------------------------------------------------------------- new photos

    private var known: Set<Int>? = null

    /** Copies photos added on the camera since the last call into [dir]; returns the new files. */
    fun fetchNew(dir: (String) -> File): List<File> {
        if (sonyReady) return sonyFetch(dir)
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

    /** Sony keeps each new photo in the camera's memory for the PC to take (pending count in 0xD215). */
    private fun sonyFetch(dir: (String) -> File): List<File> {
        val out = ArrayList<File>()
        repeat(4) {
            sonyRead()
            val pending = sonyProps[0xD215]?.value ?: 0L
            if (pending and 0x8000L == 0L) return out
            val info = run(0x1008, 0xFFFFC001.toInt(), read = true)?.takeIf { it.ok }?.data
            val name = info?.let { inf ->
                try { val ib = ByteBuffer.wrap(inf).order(ByteOrder.LITTLE_ENDIAN); ib.position(52); val n = ib.get().toInt() and 0xFF; val sb = StringBuilder(); repeat(n) { val c = ib.short.toInt().toChar(); if (c != '\u0000') sb.append(c) }; sb.toString() } catch (_: Throwable) { null }
            }?.ifBlank { null } ?: "DSC_${System.currentTimeMillis()}.JPG"
            val data = run(0x1009, 0xFFFFC001.toInt(), read = true)?.takeIf { it.ok }?.data ?: return out
            val f = dir(name)
            try { f.writeBytes(data); out += f } catch (_: Throwable) {}
        }
        return out
    }

    fun close() {
        try { stopLive() } catch (_: Throwable) {}
        synchronized(lock) { if (h != 0L) try { MfNative.ptpClose(h) } catch (_: Throwable) {}; h = 0L }
    }
}
