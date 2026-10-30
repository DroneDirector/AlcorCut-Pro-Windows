package com.dronedirector.veyrocutpro.camera

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI

/**
 * A small RTMP / RTMPS publisher (what YouTube, Twitch, Facebook, TikTok and most servers take):
 * handshake, connect, publish, then H.264 video and AAC audio as FLV tags. Own code.
 */
class Rtmp(url: String) {
    private val uri = URI(url)
    private val secure = uri.scheme.equals("rtmps", true)
    private val host = uri.host ?: throw IllegalArgumentException("No server in the address")
    private val port = if (uri.port > 0) uri.port else if (secure) 443 else 1935
    /** "live2/streamkey" → app "live2", stream "streamkey" (the key may itself contain slashes or a query). */
    private val app: String
    private val stream: String
    init {
        val path = url.substringAfter("://").substringAfter('/', "")
        app = path.substringBefore('/')
        stream = path.substringAfter('/', "")
        if (app.isEmpty()) throw IllegalArgumentException("The address needs the app part (e.g. rtmp://server/live)")
    }
    private val tcUrl = url.substringBefore("://") + "://" + host + (if (uri.port > 0) ":${uri.port}" else "") + "/" + app

    private lateinit var sock: Socket
    private lateinit var out: OutputStream
    private lateinit var inp: DataInputStream
    private var chunk = 128
    private val outChunk = 4096
    private var streamId = 1
    @Volatile var sentBytes = 0L; private set
    @Volatile var closed = false; private set
    private val lock = Any()

    fun connect(timeoutMs: Int = 10_000) {
        val raw = Socket().also { it.connect(InetSocketAddress(host, port), timeoutMs) }
        val s = if (secure) (javax.net.ssl.SSLSocketFactory.getDefault() as javax.net.ssl.SSLSocketFactory).createSocket(raw, host, port, true).also { (it as javax.net.ssl.SSLSocket).startHandshake() } else raw
        s.tcpNoDelay = true; s.soTimeout = timeoutMs
        sock = s
        out = s.getOutputStream().buffered(1 shl 16)
        inp = DataInputStream(BufferedInputStream(s.getInputStream()))
        handshake()
        // Bigger chunks from us.
        send(2, 1, 0, 0, byteArrayOf(0, 0, (outChunk shr 8).toByte(), outChunk.toByte()), chunked = false)
        command("connect", 1.0, mapOf("app" to app, "type" to "nonprivate", "flashVer" to "FMLE/3.0 (compatible; AlcorCut Pro)", "tcUrl" to tcUrl))
        waitFor("_result", 1.0)
        command("releaseStream", 2.0, null, stream)
        command("FCPublish", 3.0, null, stream)
        command("createStream", 4.0, null)
        val r = waitFor("_result", 4.0)
        streamId = (r.getOrNull(3) as? Double)?.toInt() ?: 1
        command("publish", 5.0, null, stream, "live", msgStream = streamId)
        waitStatus("NetStream.Publish.Start")
        s.soTimeout = 0
        // Server messages (acknowledgements, pings) are read and answered in the background.
        Thread({ try { while (!closed) readMessage()?.let { (type, body) -> if (type == 4 && body.size >= 6 && body[1].toInt() == 6) pong(body) } } catch (_: Throwable) {} }, "rtmp-in").apply { isDaemon = true; start() }
    }

    private fun handshake() {
        val c1 = ByteArray(1536).also { java.util.Random().nextBytes(it); for (i in 0 until 8) it[i] = 0 }
        out.write(3); out.write(c1); out.flush()
        val s0 = inp.readUnsignedByte(); if (s0 != 3) throw IllegalStateException("Not an RTMP server")
        val s1 = ByteArray(1536); inp.readFully(s1)
        out.write(s1); out.flush()
        val s2 = ByteArray(1536); inp.readFully(s2)
    }

    // ---------------------------------------------------------------- AMF0

    private fun amf(o: ByteArrayOutputStream, v: Any?) {
        when (v) {
            null -> o.write(5)
            is Double -> { o.write(0); val b = java.lang.Double.doubleToLongBits(v); for (i in 7 downTo 0) o.write((b shr (i * 8)).toInt() and 0xFF) }
            is Int -> amf(o, v.toDouble())
            is Boolean -> { o.write(1); o.write(if (v) 1 else 0) }
            is String -> { val b = v.toByteArray(); o.write(2); o.write(b.size shr 8); o.write(b.size and 0xFF); o.write(b) }
            is Map<*, *> -> {
                o.write(3)
                for ((k, x) in v) { val kb = (k as String).toByteArray(); o.write(kb.size shr 8); o.write(kb.size and 0xFF); o.write(kb); amf(o, x) }
                o.write(0); o.write(0); o.write(9)
            }
        }
    }

    private fun readAmf(b: ByteArray, at: IntArray): Any? {
        val t = b[at[0]++].toInt()
        return when (t) {
            0 -> { var x = 0L; repeat(8) { x = (x shl 8) or (b[at[0]++].toLong() and 0xFF) }; java.lang.Double.longBitsToDouble(x) }
            1 -> b[at[0]++].toInt() != 0
            2 -> { val n = ((b[at[0]].toInt() and 0xFF) shl 8) or (b[at[0] + 1].toInt() and 0xFF); at[0] += 2; String(b, at[0], n).also { at[0] += n } }
            3, 8 -> {
                if (t == 8) at[0] += 4
                val m = LinkedHashMap<String, Any?>()
                while (at[0] + 3 <= b.size) {
                    val n = ((b[at[0]].toInt() and 0xFF) shl 8) or (b[at[0] + 1].toInt() and 0xFF); at[0] += 2
                    if (n == 0 && b[at[0]].toInt() == 9) { at[0]++; break }
                    val k = String(b, at[0], n); at[0] += n
                    m[k] = readAmf(b, at)
                }
                m
            }
            5, 6 -> null
            else -> throw IllegalStateException("AMF type $t")
        }
    }

    private fun command(name: String, tx: Double, obj: Map<String, Any?>?, vararg args: Any?, msgStream: Int = 0) {
        val o = ByteArrayOutputStream()
        amf(o, name); amf(o, tx); amf(o, obj)
        for (a in args) amf(o, a)
        send(3, 20, 0, msgStream, o.toByteArray())
    }

    // ---------------------------------------------------------------- chunks

    /** Sends one message on chunk stream [csid]. */
    private fun send(csid: Int, type: Int, ts: Int, msgStream: Int, body: ByteArray, chunked: Boolean = true) = synchronized(lock) {
        val size = if (chunked) outChunk else 128
        out.write(csid and 0x3F)                    // fmt 0
        val t = ts.coerceAtMost(0xFFFFFF)
        out.write(t shr 16); out.write(t shr 8); out.write(t)
        out.write(body.size shr 16); out.write(body.size shr 8); out.write(body.size)
        out.write(type)
        out.write(msgStream); out.write(msgStream shr 8); out.write(msgStream shr 16); out.write(msgStream shr 24)
        var i = 0
        while (i < body.size) {
            if (i > 0) out.write(0xC0 or (csid and 0x3F))   // fmt 3
            val n = minOf(size, body.size - i)
            out.write(body, i, n); i += n
        }
        out.flush()
        sentBytes += body.size + 12
        if (type == 1 && !chunked) { /* our chunk size is now outChunk */ }
    }

    private class In(var type: Int = 0, var len: Int = 0, var ts: Int = 0, var buf: ByteArrayOutputStream? = null)
    private val ins = HashMap<Int, In>()

    /** Reads one whole message: (type, body), or null for protocol housekeeping. */
    private fun readMessage(): Pair<Int, ByteArray>? {
        val b0 = inp.readUnsignedByte()
        val fmt = b0 shr 6
        var csid = b0 and 0x3F
        if (csid == 0) csid = 64 + inp.readUnsignedByte() else if (csid == 1) csid = 64 + inp.readUnsignedByte() + inp.readUnsignedByte() * 256
        val st = ins.getOrPut(csid) { In() }
        fun u24() = (inp.readUnsignedByte() shl 16) or (inp.readUnsignedByte() shl 8) or inp.readUnsignedByte()
        if (fmt <= 2) { st.ts = u24() }
        if (fmt <= 1) { st.len = u24(); st.type = inp.readUnsignedByte() }
        if (fmt == 0) inp.readInt()
        if (st.ts == 0xFFFFFF && fmt <= 2) inp.readInt()
        val buf = st.buf ?: ByteArrayOutputStream().also { st.buf = it }
        val n = minOf(chunk, st.len - buf.size())
        val part = ByteArray(n); inp.readFully(part); buf.write(part)
        if (buf.size() < st.len) return null
        st.buf = null
        val body = buf.toByteArray()
        if (st.type == 1 && body.size >= 4) chunk = ((body[0].toInt() and 0x7F) shl 24) or ((body[1].toInt() and 0xFF) shl 16) or ((body[2].toInt() and 0xFF) shl 8) or (body[3].toInt() and 0xFF)
        return st.type to body
    }

    private fun pong(ping: ByteArray) { send(2, 4, 0, 0, byteArrayOf(0, 7) + ping.copyOfRange(2, 6), chunked = false) }

    private fun waitFor(name: String, tx: Double): List<Any?> {
        val until = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < until) {
            val (type, body) = readMessage() ?: continue
            if (type == 4 && body.size >= 6 && body[1].toInt() == 6) { pong(body); continue }
            if (type != 20) continue
            val at = intArrayOf(0); val vals = ArrayList<Any?>()
            try { while (at[0] < body.size) vals += readAmf(body, at) } catch (_: Throwable) {}
            if (vals.firstOrNull() == "_error") throw IllegalStateException(errorText(vals))
            if (vals.firstOrNull() == name && (vals.getOrNull(1) as? Double) == tx) return vals
        }
        throw IllegalStateException("The server didn't answer")
    }

    private fun waitStatus(code: String) {
        val until = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < until) {
            val (type, body) = readMessage() ?: continue
            if (type != 20) continue
            val at = intArrayOf(0); val vals = ArrayList<Any?>()
            try { while (at[0] < body.size) vals += readAmf(body, at) } catch (_: Throwable) {}
            if (vals.firstOrNull() == "onStatus") {
                val info = vals.getOrNull(3) as? Map<*, *>
                val c = info?.get("code") as? String ?: continue
                if (c == code) return
                if (c.contains("BadName") || c.contains("Failed") || c.contains("Rejected")) throw SecurityException(info["description"] as? String ?: c)
            }
            if (vals.firstOrNull() == "_error") throw SecurityException(errorText(vals))
        }
        throw IllegalStateException("The server didn't start the stream")
    }

    private fun errorText(vals: List<Any?>): String = ((vals.getOrNull(3) as? Map<*, *>)?.get("description") as? String) ?: "The server refused the stream"

    // ---------------------------------------------------------------- media

    fun metadata(w: Int, h: Int, fps: Int, vkbps: Int, akbps: Int) {
        val o = ByteArrayOutputStream()
        amf(o, "@setDataFrame"); amf(o, "onMetaData")
        // ECMA array.
        o.write(8); o.write(0); o.write(0); o.write(0); o.write(8)
        for ((k, v) in listOf("width" to w.toDouble(), "height" to h.toDouble(), "framerate" to fps.toDouble(), "videocodecid" to 7.0,
            "videodatarate" to vkbps.toDouble(), "audiocodecid" to 10.0, "audiodatarate" to akbps.toDouble(), "audiosamplerate" to 48000.0)) {
            val kb = k.toByteArray(); o.write(kb.size shr 8); o.write(kb.size and 0xFF); o.write(kb); amf(o, v)
        }
        o.write(0); o.write(0); o.write(9)
        send(4, 18, 0, streamId, o.toByteArray())
    }

    /** AVC decoder configuration from the SPS and PPS. */
    fun videoHeader(sps: ByteArray, pps: ByteArray) {
        val o = ByteArrayOutputStream()
        o.write(0x17); o.write(0); o.write(0); o.write(0); o.write(0)
        o.write(1); o.write(sps[1].toInt()); o.write(sps[2].toInt()); o.write(sps[3].toInt()); o.write(0xFF)
        o.write(0xE1); o.write(sps.size shr 8); o.write(sps.size and 0xFF); o.write(sps)
        o.write(1); o.write(pps.size shr 8); o.write(pps.size and 0xFF); o.write(pps)
        send(6, 9, 0, streamId, o.toByteArray())
    }

    /** AAC-LC, 48 kHz, stereo. */
    fun audioHeader() { send(4, 8, 0, streamId, byteArrayOf(0xAF.toByte(), 0, 0x11, 0x90.toByte())) }

    /** One picture's NAL units (without start codes) at [ms]. */
    fun video(nals: List<ByteArray>, key: Boolean, ms: Int) {
        val o = ByteArrayOutputStream()
        o.write(if (key) 0x17 else 0x27); o.write(1); o.write(0); o.write(0); o.write(0)
        for (n in nals) { o.write(n.size ushr 24); o.write(n.size shr 16); o.write(n.size shr 8); o.write(n.size); o.write(n) }
        send(6, 9, ms, streamId, o.toByteArray())
    }

    fun audio(aac: ByteArray, ms: Int) { send(4, 8, ms, streamId, byteArrayOf(0xAF.toByte(), 1) + aac) }

    fun close() {
        if (closed) return
        closed = true
        try { command("FCUnpublish", 6.0, null, stream); command("deleteStream", 7.0, null, streamId.toDouble()) } catch (_: Throwable) {}
        try { sock.close() } catch (_: Throwable) {}
    }

    companion object {
        /** Splits Annex-B data into NAL units (without start codes). */
        fun nals(b: ByteArray): List<ByteArray> {
            val out = ArrayList<ByteArray>()
            var i = 0; var start = -1
            while (i + 3 <= b.size) {
                val sc3 = b[i].toInt() == 0 && b[i + 1].toInt() == 0 && b[i + 2].toInt() == 1
                val sc4 = i + 4 <= b.size && b[i].toInt() == 0 && b[i + 1].toInt() == 0 && b[i + 2].toInt() == 0 && b[i + 3].toInt() == 1
                if (sc3 || sc4) {
                    if (start >= 0) out += b.copyOfRange(start, i)
                    i += if (sc4) 4 else 3; start = i
                } else i++
            }
            if (start >= 0 && start < b.size) out += b.copyOfRange(start, b.size)
            return out.filter { it.isNotEmpty() }
        }
    }
}
