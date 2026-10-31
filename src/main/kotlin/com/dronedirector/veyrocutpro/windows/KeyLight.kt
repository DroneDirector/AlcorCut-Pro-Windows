package com.dronedirector.veyrocutpro.camera

import java.net.HttpURLConnection
import java.net.URI

/**
 * Network video lights as the PC camera's torch and flash: Elgato Key Light, Key Light Air, Ring Light
 * and other lights that answer the same local light control (found by multicast DNS, "_elg._tcp").
 * Own code; talks to the light's own small web interface on the local network.
 */
object KeyLight {
    class Light(val name: String, val address: String, val port: Int)

    @Volatile var lights: List<Light> = emptyList(); private set
    @Volatile private var searched = 0L
    /** What each light was set to before the flash or torch, to put it back afterwards. */
    private val before = HashMap<String, String>()

    /** Looks for lights (at most every 30 s; blocking, about 1.5 s). */
    fun find(force: Boolean = false): List<Light> {
        if (!force && System.currentTimeMillis() - searched < 30_000) return lights
        searched = System.currentTimeMillis()
        lights = Mdns.browse("_elg._tcp.local", 1500).map { Light(it.instance.ifBlank { "Key Light" }, it.address, if (it.port > 0) it.port else 9123) }
        return lights
    }

    private fun http(l: Light, method: String, body: String? = null): String? = try {
        val c = URI("http://${l.address}:${l.port}/elgato/lights").toURL().openConnection() as HttpURLConnection
        c.connectTimeout = 1500; c.readTimeout = 1500; c.requestMethod = method
        if (body != null) { c.doOutput = true; c.setRequestProperty("Content-Type", "application/json"); c.outputStream.use { it.write(body.toByteArray()) } }
        val r = c.inputStream.use { String(it.readBytes()) }
        c.disconnect(); r
    } catch (_: Throwable) { null }

    /** Every light on at [brightness] (1–100), remembering how they were. Returns how many answered. */
    fun on(brightness: Int = 100): Int {
        var n = 0
        for (l in lights) {
            if (l.address !in before) http(l, "GET")?.let { before[l.address] = it }
            if (http(l, "PUT", "{\"numberOfLights\":1,\"lights\":[{\"on\":1,\"brightness\":${brightness.coerceIn(1, 100)}}]}") != null) n++
        }
        return n
    }

    /** Puts every light back as it was. */
    fun restore() {
        for (l in lights) {
            val was = before.remove(l.address) ?: continue
            val on = Regex("\"on\"\\s*:\\s*(\\d)").find(was)?.groupValues?.get(1) ?: "0"
            val br = Regex("\"brightness\"\\s*:\\s*(\\d+)").find(was)?.groupValues?.get(1) ?: "50"
            http(l, "PUT", "{\"numberOfLights\":1,\"lights\":[{\"on\":$on,\"brightness\":$br}]}")
        }
    }
}
