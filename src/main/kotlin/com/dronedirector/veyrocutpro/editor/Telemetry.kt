package com.dronedirector.veyrocutpro.editor

import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** One reading from a drone's flight log, at [tMs] into the clip. */
data class TelemetrySample(
    val tMs: Long,
    val lat: Double? = null,
    val lon: Double? = null,
    /** Height above the take-off point (m). */
    val alt: Double? = null,
    /** Horizontal speed (m/s), from the log or worked out from the GPS track. */
    val speed: Double? = null,
    val iso: Int? = null,
    val shutter: String? = null,
    val fnum: Double? = null,
)

/** What to show in the telemetry overlay. */
data class TelemetryShow(val alt: Boolean = true, val speed: Boolean = true, val gps: Boolean = true, val camera: Boolean = false, val metric: Boolean = true)

/**
 * Reads the .SRT flight-data file DJI drones save next to each video (DJI Mini, Air, Mavic, Avata,
 * Phantom… both the newer "[latitude: …] [rel_alt: …]" and the older "GPS(lon,lat,alt) H … H.S …"
 * styles).
 */
object Telemetry {
    private val time = Regex("""(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})\s*-->""")
    private val lat = Regex("""latitude\s*[:=]\s*(-?\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
    private val lon = Regex("""longi?tude\s*[:=]\s*(-?\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
    private val gpsOld = Regex("""GPS\s*\(\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)""")
    private val relAlt = Regex("""rel_alt\s*[:=]\s*(-?\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
    private val hOld = Regex("""(?:^|\s)H\s+(-?\d+(?:\.\d+)?)\s*m""")
    private val altPlain = Regex("""(?:^|[\s\[])altitude\s*[:=]\s*(-?\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
    private val hs = Regex("""H\.S\s+(-?\d+(?:\.\d+)?)\s*m/s""")
    private val iso = Regex("""iso\s*[:=]\s*(\d+)""", RegexOption.IGNORE_CASE)
    private val shutter = Regex("""shutter\s*[:=]\s*([\d/.]+)""", RegexOption.IGNORE_CASE)
    private val fnum = Regex("""fnum\s*[:=]\s*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)

    fun parse(text: String): List<TelemetrySample> {
        val out = ArrayList<TelemetrySample>()
        val blocks = text.replace("\r", "").split(Regex("\n\\s*\n"))
        for (b in blocks) {
            val m = time.find(b) ?: continue
            val (h, mi, s, ms) = m.destructured
            val t = h.toLong() * 3_600_000 + mi.toLong() * 60_000 + s.toLong() * 1000 + ms.padEnd(3, '0').take(3).toLong()
            val body = b.substring(m.range.last + 1).replace(Regex("<[^>]+>"), " ")
            var la = lat.find(body)?.groupValues?.get(1)?.toDoubleOrNull()
            var lo = lon.find(body)?.groupValues?.get(1)?.toDoubleOrNull()
            var alt = relAlt.find(body)?.groupValues?.get(1)?.toDoubleOrNull()
            gpsOld.find(body)?.let { g ->
                if (lo == null) lo = g.groupValues[1].toDoubleOrNull()
                if (la == null) la = g.groupValues[2].toDoubleOrNull()
            }
            if (alt == null) alt = hOld.find(body)?.groupValues?.get(1)?.toDoubleOrNull()
            if (alt == null) alt = altPlain.find(body)?.groupValues?.get(1)?.toDoubleOrNull()
            // 0,0 means "no GPS fix yet".
            if (la != null && lo != null && abs(la!!) < 0.0001 && abs(lo!!) < 0.0001) { la = null; lo = null }
            out += TelemetrySample(
                tMs = t, lat = la, lon = lo, alt = alt,
                speed = hs.find(body)?.groupValues?.get(1)?.toDoubleOrNull(),
                iso = iso.find(body)?.groupValues?.get(1)?.toIntOrNull(),
                shutter = shutter.find(body)?.groupValues?.get(1),
                fnum = fnum.find(body)?.groupValues?.get(1)?.toDoubleOrNull(),
            )
        }
        return fillSpeed(out.sortedBy { it.tMs })
    }

    /** Works out speed from the GPS track (over about a second) where the log doesn't give it. */
    private fun fillSpeed(list: List<TelemetrySample>): List<TelemetrySample> {
        if (list.any { it.speed != null }) return list
        val gps = list.filter { it.lat != null && it.lon != null }
        if (gps.size < 2) return list
        var j = 0
        return list.map { s ->
            if (s.lat == null || s.lon == null) return@map s
            while (j < gps.size - 1 && gps[j].tMs < s.tMs - 1000) j++
            val a = gps[j]
            val dt = (s.tMs - a.tMs) / 1000.0
            if (dt < 0.3) s else s.copy(speed = meters(a.lat!!, a.lon!!, s.lat, s.lon) / dt)
        }
    }

    private fun meters(la1: Double, lo1: Double, la2: Double, lo2: Double): Double {
        val r = 6_371_000.0
        val p1 = Math.toRadians(la1); val p2 = Math.toRadians(la2)
        val dp = p2 - p1; val dl = Math.toRadians(lo2 - lo1)
        val h = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * r * atan2(sqrt(h), sqrt(1 - h))
    }

    /** The overlay text for one reading. */
    fun format(s: TelemetrySample, show: TelemetryShow): String {
        val parts = ArrayList<String>()
        if (show.alt) s.alt?.let { a ->
            parts += if (show.metric) String.format(Locale.US, "ALT %.0f m", a) else String.format(Locale.US, "ALT %.0f ft", a * 3.28084)
        }
        if (show.speed) s.speed?.let { v ->
            parts += if (show.metric) String.format(Locale.US, "SPD %.0f km/h", v * 3.6) else String.format(Locale.US, "SPD %.0f mph", v * 2.23694)
        }
        if (show.gps && s.lat != null && s.lon != null) {
            parts += String.format(Locale.US, "%.5f°%s %.5f°%s", abs(s.lat), if (s.lat >= 0) "N" else "S", abs(s.lon), if (s.lon >= 0) "E" else "W")
        }
        if (show.camera) {
            val cam = listOfNotNull(s.iso?.let { "ISO $it" }, s.shutter?.let { if (it.contains('/')) it else "1/$it" }, s.fnum?.let { String.format(Locale.US, "f/%.1f", it) })
            if (cam.isNotEmpty()) parts += cam.joinToString(" ")
        }
        return parts.joinToString("  ·  ")
    }

    /**
     * One overlay text per [stepMs] of source time between [fromMs] and [toMs] (the clip's used
     * part), merging neighbours with the same text. Returns (sourceStart, sourceEnd, text).
     */
    fun segments(samples: List<TelemetrySample>, show: TelemetryShow, fromMs: Long, toMs: Long, stepMs: Long = 1000L): List<Triple<Long, Long, String>> {
        if (samples.isEmpty()) return emptyList()
        val out = ArrayList<Triple<Long, Long, String>>()
        var i = 0
        var t = fromMs
        while (t < toMs) {
            while (i < samples.size - 1 && samples[i + 1].tMs <= t) i++
            val txt = format(samples[i], show)
            val end = (t + stepMs).coerceAtMost(toMs)
            if (txt.isNotBlank()) {
                val last = out.lastOrNull()
                if (last != null && last.third == txt && last.second == t) out[out.size - 1] = Triple(last.first, end, txt)
                else out += Triple(t, end, txt)
            }
            t = end
        }
        return out
    }
}
