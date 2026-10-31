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
        fun discover(): List<CastDevice> =
            Mdns.browse(SERVICE).map { CastDevice(it.txt["fn"] ?: "Chromecast", it.address, if (it.port > 0) it.port else 8009) }.sortedBy { it.name.lowercase() }

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
