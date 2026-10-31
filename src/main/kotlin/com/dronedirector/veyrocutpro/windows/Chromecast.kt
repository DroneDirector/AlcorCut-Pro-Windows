package com.dronedirector.veyrocutpro.camera

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/** A Chromecast or Google TV found on the network. */
data class CastDevice(val name: String, val address: String, val port: Int)

/**
 * Talking to a Chromecast from the PC: finds TVs on the network (multicast DNS, as phones do) and
 * opens Google's standard media player on the TV, which then plays the PC's live stream ([CastFeed]).
 * Own code: the discovery, the Cast messages and their encoding are written here.
 */
class Chromecast(val device: CastDevice, private val onEnded: (String?) -> Unit) {
    companion object {
        private const val SERVICE = "_googlecast._tcp.local"
        private const val APP = "CC1AD845"       // the Default Media Receiver on every Chromecast
        private const val NS_CONN = "urn:x-cast:com.google.cast.tp.connection"
        private const val NS_BEAT = "urn:x-cast:com.google.cast.tp.heartbeat"
        private const val NS_RECV = "urn:x-cast:com.google.cast.receiver"
        private const val NS_MEDIA = "urn:x-cast:com.google.cast.media"

        /** Chromecasts answering on the local networks (about two seconds). */
        fun discover(): List<CastDevice> {
            val found = LinkedHashMap<String, CastDevice>()
            val query = dnsQuery(SERVICE)
            val group = InetAddress.getByName("224.0.0.251")
            val sockets = ArrayList<MulticastSocket>()
            try {
                for (ni in NetworkInterface.getNetworkInterfaces()) {
                    if (!ni.isUp || ni.isLoopback || !ni.supportsMulticast()) continue
                    if (ni.inetAddresses.toList().none { it is Inet4Address }) continue
                    try {
                        val s = MulticastSocket(0); s.networkInterface = ni; s.soTimeout = 200; s.timeToLive = 255
                        sockets += s
                    } catch (_: Throwable) {}
                }
                if (sockets.isEmpty()) sockets += MulticastSocket(0).apply { soTimeout = 200 }
                repeat(2) { for (s in sockets) try { s.send(DatagramPacket(query, query.size, group, 5353)) } catch (_: Throwable) {} }
                val until = System.currentTimeMillis() + 2000
                val buf = ByteArray(9000)
                while (System.currentTimeMillis() < until) {
                    for (s in sockets) {
                        val p = DatagramPacket(buf, buf.size)
                        try { s.receive(p) } catch (_: Throwable) { continue }
                        parseAnswer(p.data.copyOf(p.length), p.address)?.let { d -> found[d.address] = d }
                    }
                }
            } catch (_: Throwable) {
            } finally { sockets.forEach { try { it.close() } catch (_: Throwable) {} } }
            return found.values.sortedBy { it.name.lowercase() }
        }

        private fun dnsQuery(name: String): ByteArray {
            val o = ByteArrayOutputStream()
            o.write(byteArrayOf(0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0))
            for (label in name.split('.')) { o.write(label.length); o.write(label.toByteArray()) }
            o.write(0)
            o.write(byteArrayOf(0, 12, 0, 1))           // PTR, IN
            return o.toByteArray()
        }

        /** A device from one multicast DNS answer: its name (TXT fn=), port (SRV) and address. */
        private fun parseAnswer(d: ByteArray, from: InetAddress): CastDevice? {
            if (d.size < 12) return null
            fun u16(i: Int) = ((d[i].toInt() and 0xFF) shl 8) or (d[i + 1].toInt() and 0xFF)
            fun skipName(start: Int): Int {
                var i = start
                while (i < d.size) {
                    val n = d[i].toInt() and 0xFF
                    if (n == 0) return i + 1
                    if (n and 0xC0 == 0xC0) return i + 2
                    i += n + 1
                }
                return d.size
            }
            fun readName(start: Int): String {
                val sb = StringBuilder(); var i = start; var jumps = 0
                while (i < d.size && jumps < 20) {
                    val n = d[i].toInt() and 0xFF
                    if (n == 0) break
                    if (n and 0xC0 == 0xC0) { i = ((n and 0x3F) shl 8) or (d[i + 1].toInt() and 0xFF); jumps++; continue }
                    if (sb.isNotEmpty()) sb.append('.')
                    sb.append(String(d, i + 1, n.coerceAtMost(d.size - i - 1))); i += n + 1
                }
                return sb.toString()
            }
            val qd = u16(4); val records = u16(6) + u16(8) + u16(10)
            var i = 12
            repeat(qd) { i = skipName(i) + 4 }
            var isCast = false; var name: String? = null; var port = 8009; var addr: String? = null
            repeat(records) {
                if (i >= d.size) return@repeat
                val owner = readName(i)
                i = skipName(i)
                if (i + 10 > d.size) return@repeat
                val type = u16(i); val len = u16(i + 8); val data = i + 10
                when (type) {
                    12 -> if (owner.equals(SERVICE, true)) isCast = true
                    16 -> {
                        var k = data
                        while (k < data + len && k < d.size) {
                            val n = d[k].toInt() and 0xFF
                            val kv = String(d, k + 1, n.coerceAtMost(d.size - k - 1), Charsets.UTF_8)
                            if (kv.startsWith("fn=")) name = kv.substring(3)
                            k += n + 1
                        }
                        if (owner.endsWith(SERVICE, true)) isCast = true
                    }
                    33 -> if (owner.endsWith(SERVICE, true)) port = u16(data + 4)
                    1 -> if (len == 4) addr = (0 until 4).joinToString(".") { (d[data + it].toInt() and 0xFF).toString() }
                }
                i = data + len
            }
            if (!isCast) return null
            val a = addr ?: from.hostAddress
            return CastDevice(name ?: "Chromecast", a, port)
        }

        // ---- the Cast message (a small protocol-buffer record)

        private fun varint(o: ByteArrayOutputStream, v: Int) { var x = v; while (x and 0x7F.inv() != 0) { o.write((x and 0x7F) or 0x80); x = x ushr 7 }; o.write(x) }
        private fun str(o: ByteArrayOutputStream, field: Int, s: String) { val b = s.toByteArray(); varint(o, (field shl 3) or 2); varint(o, b.size); o.write(b) }

        fun encode(source: String, dest: String, ns: String, payload: String): ByteArray {
            val o = ByteArrayOutputStream()
            varint(o, (1 shl 3) or 0); varint(o, 0)          // protocol version
            str(o, 2, source); str(o, 3, dest); str(o, 4, ns)
            varint(o, (5 shl 3) or 0); varint(o, 0)          // text payload
            str(o, 6, payload)
            return o.toByteArray()
        }

        /** Namespace, source and text of a Cast message. */
        fun decode(b: ByteArray): Triple<String, String, String> {
            var i = 0; var ns = ""; var src = ""; var text = ""
            fun rv(): Int { var r = 0; var s = 0; while (i < b.size) { val c = b[i++].toInt() and 0xFF; r = r or ((c and 0x7F) shl s); if (c and 0x80 == 0) break; s += 7 }; return r }
            while (i < b.size) {
                val key = rv(); val f = key ushr 3
                when (key and 7) {
                    0 -> rv()
                    2 -> { val n = rv(); val s = String(b, i, n.coerceAtMost(b.size - i), Charsets.UTF_8); i += n
                        when (f) { 2 -> src = s; 4 -> ns = s; 6 -> text = s } }
                    else -> return Triple(ns, src, text)
                }
            }
            return Triple(ns, src, text)
        }
    }

    private var sock: SSLSocket? = null
    private var out: DataOutputStream? = null
    private val me = "sender-alcorcut"
    @Volatile private var transport: String? = null
    @Volatile private var session: String? = null
    @Volatile var closed = false; private set
    private var req = 1
    @Volatile private var tries = 0
    private var mediaUrl: String? = null

    /** The PC's own address on the connection to the TV (where the TV fetches the stream from). */
    var localAddress: String? = null; private set

    /** Connects and opens the media player on the TV; returns null when connected, else why not. Blocking. */
    fun connect(): String? = try {
        val ctx = SSLContext.getInstance("TLS")
        // A Chromecast presents its own device certificate, not one a browser would know.
        ctx.init(null, arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(c: Array<out X509Certificate>?, a: String?) {}
            override fun checkServerTrusted(c: Array<out X509Certificate>?, a: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }), null)
        val s = ctx.socketFactory.createSocket() as SSLSocket
        s.connect(InetSocketAddress(device.address, device.port), 5000)
        s.startHandshake()
        sock = s; out = DataOutputStream(s.outputStream)
        localAddress = s.localAddress.hostAddress
        send("receiver-0", NS_CONN, "{\"type\":\"CONNECT\"}")
        send("receiver-0", NS_RECV, "{\"type\":\"LAUNCH\",\"appId\":\"$APP\",\"requestId\":${req++}}")
        Thread({ read(DataInputStream(s.inputStream)) }, "cast-read").apply { isDaemon = true; start() }
        Thread({ beat() }, "cast-beat").apply { isDaemon = true; start() }
        null
    } catch (t: Throwable) { close(); "Couldn't reach ${device.name}: ${t.message ?: "no answer"}" }

    /** Plays [url] (the live stream) once the player is open on the TV. */
    fun play(url: String) {
        mediaUrl = url
        val t = transport ?: return
        send(t, NS_MEDIA, "{\"type\":\"LOAD\",\"requestId\":${req++},\"autoplay\":true,\"media\":{\"contentId\":\"$url\"," +
            "\"contentType\":\"application/x-mpegURL\",\"streamType\":\"LIVE\",\"hlsSegmentFormat\":\"ts\",\"hlsVideoSegmentFormat\":\"mpeg2_ts\"," +
            "\"metadata\":{\"metadataType\":0,\"title\":\"AlcorCut Pro\"}}}")
    }

    /** Whether the TV's player is open (ready for [play]). */
    val ready: Boolean get() = transport != null

    @Synchronized private fun send(dest: String, ns: String, payload: String) {
        val o = out ?: return
        try { val b = encode(me, dest, ns, payload); o.writeInt(b.size); o.write(b); o.flush() } catch (_: Throwable) { end("The connection to the TV was lost") }
    }

    private fun read(i: DataInputStream) {
        try {
            while (!closed) {
                val n = i.readInt()
                if (n < 0 || n > (1 shl 20)) break
                val b = ByteArray(n); i.readFully(b)
                val (ns, src, text) = decode(b)
                when (ns) {
                    NS_BEAT -> if (text.contains("\"PING\"")) send(src, NS_BEAT, "{\"type\":\"PONG\"}")
                    NS_RECV -> onReceiver(text)
                    NS_MEDIA -> onMedia(text)
                    NS_CONN -> if (text.contains("\"CLOSE\"") && src == transport) end("The TV closed the player")
                }
            }
        } catch (_: Throwable) {}
        end(if (closed) null else "The connection to the TV was lost")
    }

    private fun onReceiver(text: String) {
        // Our player among the TV's open apps: its transport for the media messages.
        val apps = text.substringAfter("\"applications\"", "")
        if (apps.isEmpty()) { if (transport != null && text.contains("RECEIVER_STATUS")) end("Another app took over the TV"); return }
        if (!apps.contains(APP)) { if (transport != null) end("Another app took over the TV"); return }
        val t = Regex("\"transportId\"\\s*:\\s*\"([^\"]+)\"").find(apps)?.groupValues?.get(1) ?: return
        val sid = Regex("\"sessionId\"\\s*:\\s*\"([^\"]+)\"").find(apps)?.groupValues?.get(1)
        if (t == transport) return
        transport = t; session = sid
        send(t, NS_CONN, "{\"type\":\"CONNECT\"}")
        mediaUrl?.let { play(it) }
    }

    private fun onMedia(text: String) {
        // The TV stopped with an error: try again a little later (a few times).
        if (text.contains("\"playerState\":\"IDLE\"") && text.contains("\"idleReason\":\"ERROR\"") && tries < 5) {
            tries++
            Thread({ Thread.sleep(2000); mediaUrl?.let { if (!closed) play(it) } }, "cast-retry").apply { isDaemon = true; start() }
        }
        if (text.contains("\"playerState\":\"PLAYING\"")) tries = 0
    }

    private fun beat() {
        while (!closed) {
            try { Thread.sleep(5000) } catch (_: InterruptedException) { break }
            if (!closed) send("receiver-0", NS_BEAT, "{\"type\":\"PING\"}")
        }
    }

    private fun end(why: String?) {
        if (closed) return
        closed = true
        try { sock?.close() } catch (_: Throwable) {}
        onEnded(why)
    }

    /** Stops the player on the TV and disconnects. */
    fun stop() {
        session?.let { send("receiver-0", NS_RECV, "{\"type\":\"STOP\",\"sessionId\":\"$it\",\"requestId\":${req++}}") }
        closed = true
        try { sock?.close() } catch (_: Throwable) {}
    }

    fun close() { closed = true; try { sock?.close() } catch (_: Throwable) {} }
}
