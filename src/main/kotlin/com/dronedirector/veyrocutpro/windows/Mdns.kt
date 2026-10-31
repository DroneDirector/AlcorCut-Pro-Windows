package com.dronedirector.veyrocutpro.camera

import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.NetworkInterface

/**
 * Finding devices on the local network by multicast DNS (what phones and PCs use for Chromecasts,
 * network lights and printers). Own code.
 */
object Mdns {
    class Found(val instance: String, val address: String, val port: Int, val txt: Map<String, String>)

    /** Devices offering [service] (e.g. "_googlecast._tcp.local"), answering within [ms]. */
    fun browse(service: String, ms: Long = 2000): List<Found> {
        val found = LinkedHashMap<String, Found>()
        val query = query(service)
        val group = InetAddress.getByName("224.0.0.251")
        val sockets = ArrayList<MulticastSocket>()
        try {
            for (ni in NetworkInterface.getNetworkInterfaces()) {
                if (!ni.isUp || ni.isLoopback || !ni.supportsMulticast()) continue
                if (ni.inetAddresses.toList().none { it is Inet4Address }) continue
                try { sockets += MulticastSocket(0).apply { networkInterface = ni; soTimeout = 150; timeToLive = 255 } } catch (_: Throwable) {}
            }
            if (sockets.isEmpty()) sockets += MulticastSocket(0).apply { soTimeout = 150 }
            repeat(2) { for (s in sockets) try { s.send(DatagramPacket(query, query.size, group, 5353)) } catch (_: Throwable) {} }
            val until = System.currentTimeMillis() + ms
            val buf = ByteArray(9000)
            while (System.currentTimeMillis() < until) {
                for (s in sockets) {
                    val p = DatagramPacket(buf, buf.size)
                    try { s.receive(p) } catch (_: Throwable) { continue }
                    parse(p.data.copyOf(p.length), p.address, service)?.let { f -> found[f.address + ":" + f.port] = f }
                }
            }
        } catch (_: Throwable) {
        } finally { sockets.forEach { try { it.close() } catch (_: Throwable) {} } }
        return found.values.toList()
    }

    private fun query(name: String): ByteArray {
        val o = ByteArrayOutputStream()
        o.write(byteArrayOf(0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0))
        for (label in name.split('.')) { o.write(label.length); o.write(label.toByteArray()) }
        o.write(0)
        o.write(byteArrayOf(0, 12, 0, 1))           // PTR, IN
        return o.toByteArray()
    }

    private fun parse(d: ByteArray, from: InetAddress, service: String): Found? {
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
        var match = false; var instance = ""; var port = 0; var addr: String? = null
        val txt = HashMap<String, String>()
        repeat(records) {
            if (i >= d.size) return@repeat
            val owner = readName(i)
            i = skipName(i)
            if (i + 10 > d.size) return@repeat
            val type = u16(i); val len = u16(i + 8); val data = i + 10
            when (type) {
                12 -> if (owner.equals(service, true)) { match = true; instance = readName(data) }
                16 -> if (owner.endsWith(service, true)) {
                    match = true
                    var k = data
                    while (k < data + len && k < d.size) {
                        val n = d[k].toInt() and 0xFF
                        val kv = String(d, k + 1, n.coerceAtMost(d.size - k - 1), Charsets.UTF_8)
                        if ('=' in kv) txt[kv.substringBefore('=')] = kv.substringAfter('=')
                        k += n + 1
                    }
                }
                33 -> if (owner.endsWith(service, true)) port = u16(data + 4)
                1 -> if (len == 4) addr = (0 until 4).joinToString(".") { (d[data + it].toInt() and 0xFF).toString() }
            }
            i = data + len
        }
        if (!match) return null
        return Found(instance.substringBefore("." + service), addr ?: from.hostAddress, port, txt)
    }
}
