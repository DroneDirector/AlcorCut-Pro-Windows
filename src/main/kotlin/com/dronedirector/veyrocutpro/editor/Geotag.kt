package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.ui.Pro
import com.dronedirector.veyrocutpro.ui.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Geotagging from a GPS track (GPX from a GPS logger, a phone app or a drone's flight log export): each photo
 * gets the position the track had when it was taken. Own code. The photos themselves are not changed —
 * the place is kept in the library, shown on the Map and written into exports.
 */
object Geotag {
    class Pt(val t: Long, val lat: Double, val lon: Double)

    /** The track's points (trkpt, rtept or wpt with a time), oldest first. */
    fun readGpx(c: Context, uri: Uri): List<Pt> {
        val out = ArrayList<Pt>()
        c.contentResolver.openInputStream(uri)?.use { ins ->
            val p = android.util.Xml.newPullParser()
            p.setInput(ins, null)
            var lat = Double.NaN; var lon = Double.NaN; var inPt = false; var inTime = false
            while (true) {
                when (p.next()) {
                    org.xmlpull.v1.XmlPullParser.END_DOCUMENT -> break
                    org.xmlpull.v1.XmlPullParser.START_TAG -> when (p.name.substringAfter(':')) {
                        "trkpt", "rtept", "wpt" -> {
                            inPt = true
                            lat = p.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: Double.NaN
                            lon = p.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: Double.NaN
                        }
                        "time" -> inTime = inPt
                    }
                    org.xmlpull.v1.XmlPullParser.TEXT -> if (inTime && !lat.isNaN() && !lon.isNaN()) {
                        parseIso(p.text.trim())?.let { out += Pt(it, lat, lon) }
                    }
                    org.xmlpull.v1.XmlPullParser.END_TAG -> when (p.name.substringAfter(':')) {
                        "time" -> inTime = false
                        "trkpt", "rtept", "wpt" -> inPt = false
                    }
                }
            }
        }
        return out.sortedBy { it.t }
    }

    private fun parseIso(s: String): Long? = try {
        java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli()
    } catch (_: Throwable) {
        try { java.time.Instant.parse(s).toEpochMilli() } catch (_: Throwable) { null }
    }

    /** When the photo was taken (UTC ms): the camera's time with its own offset, or [zoneMinutes] when it has none. */
    fun photoTime(c: Context, uri: Uri, zoneMinutes: Int): Long? = try {
        c.contentResolver.openInputStream(uri)?.use { ins ->
            val e = androidx.exifinterface.media.ExifInterface(ins)
            val dt = e.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_ORIGINAL) ?: e.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME) ?: return@use null
            val local = java.time.LocalDateTime.parse(dt.trim(), java.time.format.DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss"))
            val off = e.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_OFFSET_TIME_ORIGINAL)?.let { o -> try { java.time.ZoneOffset.of(o.trim()) } catch (_: Throwable) { null } }
                ?: java.time.ZoneOffset.ofTotalSeconds(zoneMinutes * 60)
            local.toInstant(off).toEpochMilli()
        }
    } catch (_: Throwable) { null }

    /** The position at [t], between the nearest points; null when the track is further than [maxGapMs] away. */
    fun at(track: List<Pt>, t: Long, maxGapMs: Long = 5 * 60_000L): Pair<Double, Double>? {
        if (track.isEmpty()) return null
        var lo = 0; var hi = track.size - 1
        while (lo < hi) { val m = (lo + hi) / 2; if (track[m].t < t) lo = m + 1 else hi = m }
        val b = track[lo]; val a = track[(lo - 1).coerceAtLeast(0)]
        if (t <= track.first().t) return if (track.first().t - t <= maxGapMs) track.first().lat to track.first().lon else null
        if (t >= track.last().t) return if (t - track.last().t <= maxGapMs) track.last().lat to track.last().lon else null
        if (b.t - a.t > maxGapMs * 2 && minOf(t - a.t, b.t - t) > maxGapMs) return null
        val k = if (b.t == a.t) 0.0 else (t - a.t).toDouble() / (b.t - a.t)
        return (a.lat + (b.lat - a.lat) * k) to (a.lon + (b.lon - a.lon) * k)
    }

    /** The place written into an export of [uri] (when it was geotagged here), or null. */
    fun placeFor(c: Context, uri: Uri): Pair<Double, Double>? {
        LibraryStore.load(c)
        val m = LibraryStore.meta[uri.toString()] ?: return null
        if (m.geotagged != true) return null
        val p = m.place ?: return null
        return if (p.size == 2) p[0] to p[1] else null
    }
}

/** Library → selected photos → Geotag from GPX: the time difference, how many photos match, then apply. */
@Composable
fun GeotagDialog(gpx: Uri, photos: List<LibPhoto>, onDone: (String?) -> Unit) {
    val context = LocalContext.current
    val phoneZone = remember { java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000 }
    var zoneH by remember { mutableFloatStateOf(phoneZone / 60f) }
    val track by produceState<List<Geotag.Pt>?>(null, gpx) { value = withContext(Dispatchers.IO) { try { Geotag.readGpx(context, gpx) } catch (_: Throwable) { emptyList() } } }
    val matches by produceState<Map<String, Pair<Double, Double>>?>(null, track, zoneH) {
        val tr = track ?: return@produceState
        value = withContext(Dispatchers.IO) {
            photos.mapNotNull { p -> Geotag.photoTime(context, p.uri, (zoneH * 60).toInt())?.let { t -> Geotag.at(tr, t)?.let { p.uri.toString() to it } } }.toMap()
        }
    }
    AlertDialog(
        onDismissRequest = { onDone(null) }, containerColor = Pro.PanelSolid,
        title = { Text("Geotag from GPX", color = Color.White) },
        text = {
            Column {
                val tr = track
                when {
                    tr == null -> Text("Reading the track…", color = Pro.Dim)
                    tr.isEmpty() -> Text("No timed points found in this file — it needs a GPX track with times.", color = Pro.Red, fontSize = 13.sp)
                    else -> {
                        Text("${tr.size} track points · ${photos.size} photo(s) selected", color = Pro.Dim, fontSize = 12.sp)
                        EdSlider("Photos' time zone (UTC ±h)", zoneH, -12f..14f, phoneZone / 60f, { v -> "%+.1f h".format(v) }) { v -> zoneH = kotlin.math.round(v * 4f) / 4f }
                        Text("Used for photos whose camera time has no zone (most cameras). Phones and newer cameras store their own.", color = Pro.Dim, fontSize = 11.sp)
                        val m = matches
                        Text(if (m == null) "Matching…" else "${m.size} of ${photos.size} photos are on the track (within 5 minutes)", color = Pro.Amber, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            val m = matches
            TextButton(enabled = !m.isNullOrEmpty(), onClick = {
                if (m.isNullOrEmpty()) return@TextButton
                for ((k, ll) in m) LibraryStore.meta[k] = LibraryStore.get(k).copy(place = listOf(ll.first, ll.second), geotagged = true)
                LibraryStore.save(context)
                onDone("${m.size} photo(s) geotagged — shown on the Map and written into exports")
            }) { Text("GEOTAG", color = if (m.isNullOrEmpty()) Pro.Dim else Pro.Amber) }
        },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("Cancel", color = Color.White) } },
    )
}
