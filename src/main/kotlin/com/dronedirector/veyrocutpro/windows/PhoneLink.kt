package com.dronedirector.veyrocutpro.camera

import com.dronedirector.veyrocutpro.windows.MfNative
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

/** A phone running AlcorCut Pro with Connect to PC on. */
data class PhoneDevice(val address: String, val port: Int, val name: String) {
    val link: String get() = "phone:$address:$port"
}

/**
 * The phone as the PC's camera (Capture → camera list): finds phones on the network (Wi-Fi or USB
 * tethering), connects with the phone's PIN, shows its live picture, works its camera and receives the
 * photos and videos it takes. Own code; the phone side is Connect to PC in the phone app.
 */
class PhoneLink(
    val device: PhoneDevice,
    private val onFrame: (IntArray, Int, Int, Long) -> Unit,
    private val onState: (Map<String, String>) -> Unit,
    private val onFile: (File) -> Unit,
    private val onClosed: (String?) -> Unit,
) {
    companion object {
        private const val DISCOVERY = 8767

        /** Phones answering on the local networks (about a second). */
        fun discover(): List<PhoneDevice> {
            val found = LinkedHashMap<String, PhoneDevice>()
            try {
                DatagramSocket().use { s ->
                    s.broadcast = true; s.soTimeout = 300
                    val ask = "ALCORCUT?".toByteArray()
                    val targets = HashSet<InetAddress>().apply {
                        add(InetAddress.getByName("255.255.255.255"))
                        for (ni in NetworkInterface.getNetworkInterfaces()) {
                            if (!ni.isUp || ni.isLoopback) continue
                            for (ia in ni.interfaceAddresses) ia.broadcast?.let { add(it) }
                        }
                    }
                    repeat(2) { for (t in targets) try { s.send(DatagramPacket(ask, ask.size, t, DISCOVERY)) } catch (_: Throwable) {} }
                    val until = System.currentTimeMillis() + 1000
                    val buf = ByteArray(512)
                    while (System.currentTimeMillis() < until) {
                        val p = DatagramPacket(buf, buf.size)
                        try { s.receive(p) } catch (_: java.net.SocketTimeoutException) { continue }
                        val parts = String(p.data, 0, p.length).split(' ', limit = 3)
                        if (parts.size == 3 && parts[0] == "ALCORCUT") {
                            val d = PhoneDevice(p.address.hostAddress, parts[1].toIntOrNull() ?: 8768, parts[2])
                            found[d.address] = d
                        }
                    }
                }
            } catch (_: Throwable) {}
            return found.values.toList()
        }
    }

    private val sender = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "phone-send").apply { isDaemon = true } }
    @Volatile private var sock: Socket? = null
    @Volatile private var out: DataOutputStream? = null
    @Volatile var connected = false; private set
    private var dec = 0L
    private var px = IntArray(1280 * 960)

    /** Connects with [pin]; returns null when connected, else why not. Blocking. */
    fun connect(pin: String): String? {
        return try {
            val s = Socket(); s.connect(InetSocketAddress(device.address, device.port), 5000); s.tcpNoDelay = true; s.soTimeout = 8000
            val o = DataOutputStream(s.getOutputStream().buffered(1 shl 16)); val i = DataInputStream(s.getInputStream().buffered(1 shl 16))
            o.writeUTF("ALCORCUT $pin"); o.flush()
            when (i.readUTF()) {
                "OK" -> {}
                "PIN" -> { s.close(); return "Wrong PIN — type the 4 digits shown on the phone" }
                else -> { s.close(); return "The phone didn't answer" }
            }
            s.soTimeout = 0
            sock = s; out = o; connected = true
            dec = if (MfNative.ok) MfNative.decOpen() else 0L
            Thread({ read(i) }, "phone-link").apply { isDaemon = true; start() }
            null
        } catch (t: Throwable) { "Couldn't reach ${device.name}: ${t.message ?: "no answer"}" }
    }

    private fun read(i: DataInputStream) {
        var file: File? = null; var fileOut: java.io.OutputStream? = null
        var why: String? = null
        try {
            while (true) {
                val type = i.readByte().toInt().toChar()
                val n = i.readInt() - 8
                val extra = i.readLong()
                if (n < 0 || n > (64 shl 20)) { why = "The connection was garbled"; break }
                val body = ByteArray(n); i.readFully(body)
                when (type) {
                    'V' -> if (dec != 0L) {
                        var r = MfNative.decFrame(dec, body, (extra shr 2) * 10, px)
                        if (r < 0) { px = IntArray((-r).toInt()); r = MfNative.decFrame(dec, ByteArray(0), (extra shr 2) * 10, px) }
                        if (r > 0) onFrame(px, ((r shr 16) and 0xFFFF).toInt(), (r and 0xFFFF).toInt(), (extra shr 2) * 10)
                    }
                    'S' -> onState(parse(String(body)))
                    'F' -> {
                        val name = String(body).substringAfterLast('/').substringAfterLast('\\').ifBlank { "phone_${System.currentTimeMillis()}" }
                        val video = name.substringAfterLast('.').lowercase() in setOf("mp4", "mov", "m4v", "3gp", "wav")
                        val dir = Storage.folder(android.content.Context.app, StorageArea.CAPTURE, if (video) Storage.Kind.VIDEO else Storage.Kind.IMAGE)
                        file = Storage.newFile(android.content.Context.app, StorageArea.CAPTURE, if (video) Storage.Kind.VIDEO else Storage.Kind.IMAGE, name).also { it.parentFile?.mkdirs() }
                        fileOut = file!!.outputStream().buffered(1 shl 18)
                        dir.mkdirs()
                    }
                    'D' -> fileOut?.write(body)
                    'E' -> { fileOut?.close(); fileOut = null; file?.let(onFile); file = null }
                }
            }
        } catch (_: java.io.EOFException) {
            why = "The phone ended the connection"
        } catch (t: Throwable) {
            if (connected) why = "The connection to the phone was lost"
        } finally {
            try { fileOut?.close() } catch (_: Throwable) {}
            if (file != null) file!!.delete()
            val was = connected
            connected = false
            if (dec != 0L) { MfNative.decClose(dec); dec = 0L }
            if (was) onClosed(why)
        }
    }

    private fun parse(json: String): Map<String, String> {
        val m = HashMap<String, String>()
        Regex("\"(\\w+)\"\\s*:\\s*(\"(?:[^\"\\\\]|\\\\.)*\"|\\[[^]]*]|[-\\d.eE]+|true|false)").findAll(json).forEach { m[it.groupValues[1]] = it.groupValues[2].trim('"') }
        return m
    }

    /** Sends a command to the phone ({"cmd": …, "value": …}). */
    fun command(cmd: String, value: Any? = null, extra: Map<String, Any> = emptyMap()) {
        val o = out ?: return
        fun q(v: Any?) = if (v is Number || v is Boolean) v.toString() else "\"" + v.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        val body = buildString {
            append("{\"cmd\":").append(q(cmd))
            if (value != null) append(",\"value\":").append(q(value))
            for ((k, v) in extra) append(",\"").append(k).append("\":").append(q(v))
            append('}')
        }.toByteArray()
        sender.execute { try { o.writeByte('C'.code); o.writeInt(body.size); o.write(body); o.flush() } catch (_: Throwable) {} }
    }

    fun close() {
        connected = false
        try { sock?.close() } catch (_: Throwable) {}
        sock = null; out = null
        sender.shutdown()
    }
}
