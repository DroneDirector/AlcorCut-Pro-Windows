package com.dronedirector.veyrocutpro.editor

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.ui.Pro
import java.io.DataInputStream
import kotlin.math.max
import kotlin.math.min

/**
 * The Library's Map view: the world (Natural Earth land outlines, public domain — drawn by the app
 * itself, no map service) with a pin for every photo that has a location. Pinch to zoom, drag to
 * move, tap a pin to see those photos.
 */
object WorldShapes {
    @Volatile private var rings: List<FloatArray>? = null
    /** Land outlines as lon, lat pairs. */
    fun get(c: Context): List<FloatArray> = rings ?: try {
        DataInputStream(c.assets.open("world_land.bin").buffered()).use { s ->
            val n = s.readInt()
            List(n) { val m = s.readInt(); FloatArray(m * 2) { s.readShort() / 100f } }
        }.also { rings = it }
    } catch (_: Throwable) { emptyList() }
}

@Composable
fun LibraryMap(photos: List<Pair<String, List<Double>>>, onPick: (List<String>) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val land = remember { WorldShapes.get(context) }
    var zoom by remember { mutableStateOf(1f) }
    var cx by remember { mutableStateOf(0f) }    // map centre, longitude
    var cy by remember { mutableStateOf(20f) }   // latitude
    // Start on the photos.
    androidx.compose.runtime.LaunchedEffect(photos.isNotEmpty()) {
        if (photos.isNotEmpty()) {
            val lons = photos.map { it.second[1] }; val lats = photos.map { it.second[0] }
            cx = ((lons.min() + lons.max()) / 2).toFloat(); cy = ((lats.min() + lats.max()) / 2).toFloat()
            val span = max(lons.max() - lons.min(), (lats.max() - lats.min()) * 2).toFloat().coerceAtLeast(2f)
            zoom = (360f / (span * 1.6f)).coerceIn(1f, 400f)
        }
    }
    val clusters = remember { ArrayList<Pair<Offset, List<String>>>() }
    Box(modifier.background(Color(0xFF0D1B2A))) {
        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, z, _ ->
                        val pxPerDeg = size.width / 360f * zoom
                        zoom = (zoom * z).coerceIn(1f, 400f)
                        cx = (cx - pan.x / pxPerDeg).coerceIn(-180f, 180f)
                        cy = (cy + pan.y / pxPerDeg).coerceIn(-85f, 85f)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { zoom = (zoom * 2f).coerceAtMost(400f) },
                        onTap = { o -> clusters.minByOrNull { (it.first - o).getDistance() }?.takeIf { (it.first - o).getDistance() < 40.dp.toPx() }?.let { onPick(it.second) } },
                    )
                },
        ) {
            val k = size.width / 360f * zoom
            fun proj(lon: Float, lat: Float) = Offset(size.width / 2 + (lon - cx) * k, size.height / 2 - (lat - cy) * k)
            val fill = Color(0xFF2A3A4C); val edge = Color(0xFF4F6C88)
            for (r in land) {
                if (r.size < 6) continue
                val path = Path()
                var o = proj(r[0], r[1]); path.moveTo(o.x, o.y)
                var i = 2
                while (i < r.size) { o = proj(r[i], r[i + 1]); path.lineTo(o.x, o.y); i += 2 }
                path.close()
                drawPath(path, fill); drawPath(path, edge, style = Stroke(1f))
            }
            // Pins, grouped when they would overlap on screen.
            val cell = 34.dp.toPx()
            val groups = LinkedHashMap<Long, MutableList<Pair<Offset, String>>>()
            for ((key, ll) in photos) {
                val p = proj(ll[1].toFloat(), ll[0].toFloat())
                if (p.x < -cell || p.y < -cell || p.x > size.width + cell || p.y > size.height + cell) continue
                val id = (p.x / cell).toLong() * 100_000 + (p.y / cell).toLong()
                groups.getOrPut(id) { ArrayList() } += p to key
            }
            val out = clusters; out.clear()
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; textAlign = android.graphics.Paint.Align.CENTER; textSize = 11.sp.toPx(); isFakeBoldText = true }
            for (g in groups.values) {
                val c = Offset(g.sumOf { it.first.x.toDouble() }.toFloat() / g.size, g.sumOf { it.first.y.toDouble() }.toFloat() / g.size)
                val rad = (9.dp.toPx() + min(10f, g.size.toFloat()) * 0.9f.dp.toPx())
                drawCircle(Color.Black.copy(alpha = 0.4f), rad + 2.dp.toPx(), c)
                drawCircle(Pro.Amber, rad, c)
                drawContext.canvas.nativeCanvas.drawText(if (g.size > 999) "999+" else g.size.toString(), c.x, c.y + paint.textSize / 3, paint)
                out += c to g.map { it.second }
            }
        }
        if (photos.isEmpty()) Text("No photos with a location yet — the ones taken with location on show up here.", color = Pro.Dim, fontSize = 12.sp, modifier = Modifier.align(Alignment.Center).padding(24.dp))
        Text("Map: Natural Earth", color = Color.White.copy(alpha = 0.4f), fontSize = 9.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp))
    }
}
