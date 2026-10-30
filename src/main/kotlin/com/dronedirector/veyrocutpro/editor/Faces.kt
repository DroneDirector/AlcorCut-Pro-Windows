package com.dronedirector.veyrocutpro.editor

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.camera.GradeIO
import com.dronedirector.veyrocutpro.ui.Pro
import com.dronedirector.veyrocutpro.ui.Text
import com.dronedirector.veyrocutpro.ui.hintedScroll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.sqrt

/**
 * Name people: faces in photos and videos are found (ML Kit) and turned into a "face print" (SFace, an
 * Apache 2.0 face-recognition model, on this phone — nothing leaves it). Similar prints are grouped into
 * people; give a person a name and every photo / video with them can be found by that name.
 */
object Faces {
    /** One face in a photo or video: where it is (0..1 of the picture) and its print. */
    class Face(val key: String, val x: Float, val y: Float, val w: Float, val h: Float, val emb: FloatArray, var person: Int, val t: Long = 0L)
    /** A person: an id, a name (empty = not named yet) and the average print. */
    class Person(val id: Int, var name: String, var centroid: FloatArray, var count: Int)

    private val faces = ArrayList<Face>()
    private val people = LinkedHashMap<Int, Person>()
    /** Photos / videos already looked at (also those without faces). */
    private val done = HashSet<String>()
    private var nextId = 1
    private var loaded = false
    /** Changes when people or names change (the screens redraw). */
    var version by mutableStateOf(0)
    var progress by mutableStateOf<Pair<Int, Int>?>(null)
    var failed by mutableStateOf<String?>(null)
    /** Shown while the model downloads (or why it couldn't). */
    var status by mutableStateOf<String?>(null)

    private const val SAME = 0.42f   // prints this close (cosine) are the same person

    private fun dir(c: Context) = File(c.filesDir, "people").apply { mkdirs() }

    @Synchronized fun load(c: Context) {
        if (loaded) return
        loaded = true
        try {
            val f = File(dir(c), "faces.bin"); if (!f.exists()) return
            DataInputStream(f.inputStream().buffered()).use { d ->
                d.readInt(); nextId = d.readInt()
                val np = d.readInt()
                people.clear()
                repeat(np) { val id = d.readInt(); val name = d.readUTF(); val cnt = d.readInt(); val cen = FloatArray(d.readInt()) { d.readFloat() }; people[id] = Person(id, name, cen, cnt) }
                val nd = d.readInt(); done.clear(); repeat(nd) { done += d.readUTF() }
                val nf = d.readInt(); faces.clear()
                repeat(nf) {
                    val key = d.readUTF(); val x = d.readFloat(); val y = d.readFloat(); val w = d.readFloat(); val h = d.readFloat(); val p = d.readInt(); val t = d.readLong()
                    faces += Face(key, x, y, w, h, FloatArray(d.readInt()) { d.readFloat() }, p, t)
                }
            }
        } catch (_: Throwable) { faces.clear(); people.clear(); done.clear() }
    }

    @Synchronized fun save(c: Context) {
        try {
            val f = File(dir(c), "faces.tmp")
            DataOutputStream(f.outputStream().buffered()).use { d ->
                d.writeInt(1); d.writeInt(nextId)
                d.writeInt(people.size)
                for (p in people.values) { d.writeInt(p.id); d.writeUTF(p.name); d.writeInt(p.count); d.writeInt(p.centroid.size); p.centroid.forEach { d.writeFloat(it) } }
                d.writeInt(done.size); done.forEach { d.writeUTF(it) }
                d.writeInt(faces.size)
                for (x in faces) { d.writeUTF(x.key); d.writeFloat(x.x); d.writeFloat(x.y); d.writeFloat(x.w); d.writeFloat(x.h); d.writeInt(x.person); d.writeLong(x.t); d.writeInt(x.emb.size); x.emb.forEach { d.writeFloat(it) } }
            }
            f.renameTo(File(dir(c), "faces.bin"))
        } catch (_: Throwable) {}
    }

    // ---- the model
    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    @Synchronized private fun ready(c: Context): Boolean {
        if (session != null) return true
        if (!ModelStore.has(c, AiModel.FACES)) return false
        return try {
            val e = OrtEnvironment.getEnvironment()
            session = e.createSession(ModelStore.file(c, AiModel.FACES.files[0]).path, OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) })
            env = e; true
        } catch (t: Throwable) { failed = t.message ?: t.javaClass.simpleName; false }
    }

    /** The standard 112 × 112 face layout: eyes, nose tip, mouth corners (as seen in the picture). */
    private val TEMPLATE = floatArrayOf(38.2946f, 51.6963f, 73.5318f, 51.5014f, 56.0252f, 71.7366f, 41.5493f, 92.3655f, 70.7299f, 92.2041f)

    /** Best rotation + scale + shift that puts the 5 points [src] on the template (least squares). */
    private fun similarity(src: FloatArray): Matrix {
        var mx = 0f; var my = 0f; var tx = 0f; var ty = 0f
        for (i in 0 until 5) { mx += src[2 * i]; my += src[2 * i + 1]; tx += TEMPLATE[2 * i]; ty += TEMPLATE[2 * i + 1] }
        mx /= 5; my /= 5; tx /= 5; ty /= 5
        var a = 0f; var b = 0f; var n = 0f
        for (i in 0 until 5) {
            val sx = src[2 * i] - mx; val sy = src[2 * i + 1] - my; val dx = TEMPLATE[2 * i] - tx; val dy = TEMPLATE[2 * i + 1] - ty
            a += sx * dx + sy * dy; b += sx * dy - sy * dx; n += sx * sx + sy * sy
        }
        val ca = a / n; val sb = b / n   // scale·cos, scale·sin
        val m = Matrix()
        m.setValues(floatArrayOf(ca, -sb, tx - (ca * mx - sb * my), sb, ca, ty - (sb * mx + ca * my), 0f, 0f, 1f))
        return m
    }

    private fun embed(c: Context, face112: Bitmap): FloatArray? {
        if (!ready(c)) return null
        val px = IntArray(112 * 112); face112.getPixels(px, 0, 112, 0, 0, 112, 112)
        val n = 112 * 112; val chw = FloatArray(3 * n)
        for (i in 0 until n) { val p = px[i]; chw[i] = ((p shr 16) and 255).toFloat(); chw[n + i] = ((p shr 8) and 255).toFloat(); chw[2 * n + i] = (p and 255).toFloat() }
        val e = env ?: return null; val s = session ?: return null
        return try {
            OnnxTensor.createTensor(e, FloatBuffer.wrap(chw), longArrayOf(1, 3, 112, 112)).use { t ->
                // This model also lists some of its weights as inputs: the picture goes to "data".
                s.run(mapOf((if ("data" in s.inputNames) "data" else s.inputNames.first()) to t)).use { r ->
                    val fb = (r[0] as OnnxTensor).floatBuffer; val v = FloatArray(fb.remaining()); fb.get(v)
                    var q = 0f; for (x in v) q += x * x; q = sqrt(q).coerceAtLeast(1e-6f)
                    for (i in v.indices) v[i] /= q
                    v
                }
            }
        } catch (t: Throwable) { failed = t.message; null }
    }

    private fun cos(a: FloatArray, b: FloatArray): Float { var s = 0f; for (i in a.indices) s += a[i] * b[i]; return s }

    /** Faces in one picture → prints and people. */
    private fun addPicture(c: Context, key: String, b: Bitmap, det: com.google.mlkit.vision.face.FaceDetector, t: Long = 0L) {
        val found = try { com.google.android.gms.tasks.Tasks.await(det.process(InputImage.fromBitmap(b, 0)), 20, java.util.concurrent.TimeUnit.SECONDS) } catch (_: Throwable) { return }
        for (f in found.take(12)) {
            val bb = f.boundingBox
            if (bb.width() < 40 || bb.height() < 40) continue
            val le = f.getLandmark(FaceLandmark.LEFT_EYE)?.position; val re = f.getLandmark(FaceLandmark.RIGHT_EYE)?.position
            val no = f.getLandmark(FaceLandmark.NOSE_BASE)?.position
            val ml = f.getLandmark(FaceLandmark.MOUTH_LEFT)?.position; val mr = f.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position
            if (le == null || re == null || no == null || ml == null || mr == null) continue
            // As seen in the picture: the left-most eye / mouth corner first.
            val (e1, e2) = if (le.x <= re.x) le to re else re to le
            val (m1, m2) = if (ml.x <= mr.x) ml to mr else mr to ml
            val m = similarity(floatArrayOf(e1.x, e1.y, e2.x, e2.y, no.x, no.y, m1.x, m1.y, m2.x, m2.y))
            val out = Bitmap.createBitmap(112, 112, Bitmap.Config.ARGB_8888)
            Canvas(out).apply { setMatrix(m); drawBitmap(b, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG)) }
            val emb = embed(c, out); out.recycle()
            if (emb == null) continue
            // The nearest person, or a new one.
            var best: Person? = null; var bs = SAME
            for (p in people.values) { val s = cos(emb, p.centroid); if (s > bs) { bs = s; best = p } }
            val person = best ?: Person(nextId++, "", emb.copyOf(), 0).also { people[it.id] = it }
            val k = person.count.toFloat()
            for (i in emb.indices) person.centroid[i] = (person.centroid[i] * k + emb[i]) / (k + 1)
            var q = 0f; for (x in person.centroid) q += x * x; q = sqrt(q).coerceAtLeast(1e-6f); for (i in person.centroid.indices) person.centroid[i] /= q
            person.count++
            faces += Face(key, bb.left / b.width.toFloat(), bb.top / b.height.toFloat(), bb.width() / b.width.toFloat(), bb.height() / b.height.toFloat(), emb, person.id, t)
        }
    }

    private fun detector() = FaceDetection.getClient(FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL).setMinFaceSize(0.05f).build())

    /** Looks at photos not looked at yet. [photos] are (key, uri). Returns how many were looked at. */
    fun indexPhotos(c: Context, photos: List<Pair<String, Uri>>, stop: () -> Boolean): Int {
        load(c)
        val todo = synchronized(this) { photos.filter { it.first !in done } }
        if (todo.isEmpty()) return 0
        if (!ready(c)) return 0
        val det = detector()
        var n = 0
        try {
            for ((key, u) in todo) {
                if (stop()) break
                progress = n to todo.size
                val b = try { com.dronedirector.veyrocutpro.camera.GradeIO.decode(c, u, 1280) } catch (_: Throwable) { null }
                synchronized(this) { if (b != null) addPicture(c, key, b, det); done += key }
                b?.recycle(); n++
                if (n % 25 == 0) { save(c); version++ }
            }
        } finally { det.close(); progress = null; save(c); version++ }
        return n
    }

    /** Looks at videos not looked at yet: a frame every 3 s (at most 20 per video). */
    fun indexVideos(c: Context, videos: List<Triple<String, Uri, Long>>, stop: () -> Boolean): Int {
        load(c)
        val todo = synchronized(this) { videos.filter { it.first !in done } }
        if (todo.isEmpty() || !ready(c)) return 0
        val det = detector()
        var n = 0
        try {
            for ((key, u, dur) in todo) {
                if (stop()) break
                progress = n to todo.size
                val steps = (dur / 3000L).toInt().coerceIn(1, 20)
                val before = synchronized(this) { faces.size }
                for (s in 0 until steps) {
                    if (stop()) break
                    val t = (dur * (s + 0.5) / steps).toLong()
                    val b = try { com.dronedirector.veyrocutpro.camera.GradeIO.videoFrame(c, u, t * 1000, 1280) } catch (_: Throwable) { null } ?: continue
                    synchronized(this) { addPicture(c, key, b, det, t) }
                    b.recycle()
                }
                // The same person seen in several frames counts once per video.
                synchronized(this) {
                    val added = faces.subList(before, faces.size)
                    val seen = HashSet<Int>(); val keep = added.filter { seen.add(it.person) }
                    val drop = added.size - keep.size
                    if (drop > 0) { val k = ArrayList(keep); added.clear(); faces.addAll(k) }
                    done += key
                }
                n++
                if (n % 5 == 0) { save(c); version++ }
            }
        } finally { det.close(); progress = null; save(c); version++ }
        return n
    }

    /** People with at least [min] faces, most seen first. */
    @Synchronized fun peopleList(min: Int = 2, keys: Set<String>? = null): List<Person> {
        val counts = HashMap<Int, Int>()
        for (f in faces) if (keys == null || f.key in keys) counts[f.person] = (counts[f.person] ?: 0) + 1
        return people.values.filter { (counts[it.id] ?: 0) >= min || it.name.isNotBlank() && (counts[it.id] ?: 0) > 0 }
            .sortedWith(compareBy<Person>({ it.name.isBlank() }, { -(counts[it.id] ?: 0) }))
    }
    @Synchronized fun countOf(id: Int, keys: Set<String>? = null) = faces.count { it.person == id && (keys == null || it.key in keys) }
    /** Photos / videos with this person. */
    @Synchronized fun keysOf(id: Int): Set<String> = faces.filter { it.person == id }.map { it.key }.toSet()
    /** Names of the people in this photo / video. */
    @Synchronized fun namesIn(key: String): List<String> = faces.filter { it.key == key }.mapNotNull { people[it.person]?.name?.takeIf { n -> n.isNotBlank() } }.distinct()
    /** A face of this person to show: (key, box). */
    @Synchronized fun sample(id: Int): Face? = faces.filter { it.person == id }.maxByOrNull { it.w * it.h }

    /** Names a person; a name already used merges the two. */
    @Synchronized fun name(c: Context, id: Int, name: String) {
        val p = people[id] ?: return
        val n = name.trim()
        val other = people.values.firstOrNull { it.id != id && n.isNotBlank() && it.name.equals(n, ignoreCase = true) }
        if (other != null) {
            faces.filter { it.person == id }.forEach { it.person = other.id }
            val k = other.count.toFloat(); val k2 = p.count.toFloat()
            for (i in other.centroid.indices) other.centroid[i] = (other.centroid[i] * k + p.centroid[i] * k2) / (k + k2).coerceAtLeast(1f)
            other.count += p.count
            people.remove(id)
        } else p.name = n
        save(c); version++
    }

    /** "Not this person": the face goes to a new person of its own. */
    @Synchronized fun split(c: Context, key: String, id: Int) {
        faces.filter { it.key == key && it.person == id }.forEach { f ->
            val p = Person(nextId++, "", f.emb.copyOf(), 1); people[p.id] = p; f.person = p.id
        }
        save(c); version++
    }

    /** Self-test (emulator): download the model if needed and make one face print. */
    fun selfTest(c: Context): String = try {
        val err = ModelStore.ensure(c, AiModel.FACES, {}, { false })
        if (err != null) "model: $err" else {
            val b = Bitmap.createBitmap(112, 112, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFFB08070.toInt()) }
            val e = embed(c, b); b.recycle()
            if (e == null) "FAILED: ${failed ?: "no print"}" else "ok, print of ${e.size} numbers"
        }
    } catch (t: Throwable) { "FAILED: ${t.message}" }

    /** Does the name of someone in [key] match [text]? (for searching) */
    fun nameMatches(key: String, text: String): Boolean {
        if (text.isBlank()) return false
        val t = text.trim().lowercase()
        return namesIn(key).any { it.lowercase().contains(t) }
    }
}

/**
 * The people row (photo and video library): a face for each person with their name; tap to show only
 * them, hold to name them. "Find people" looks at what hasn't been looked at yet.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PeopleRow(keys: Set<String>, selected: Int?, onPick: (Int?) -> Unit, uriOf: (String) -> Uri?, video: Boolean, onFind: () -> Unit) {
    val c = LocalContext.current
    Faces.load(c)
    val v = Faces.version
    val list = remember(v, keys) { Faces.peopleList(2, keys) }
    var naming by remember { mutableStateOf<Faces.Person?>(null) }
    Row(Modifier.fillMaxWidth().hintedScroll(horizontal = true).padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        val pr = Faces.progress
        val st = Faces.status
        if (pr != null) Text("Finding people… ${pr.first} of ${pr.second}", color = Pro.Amber, fontSize = 11.sp)
        else if (st != null) Text(st, color = Pro.Amber, fontSize = 11.sp)
        else PillChip(if (list.isEmpty()) "Find people (AI)" else "Look for more", false, accentText = true) { onFind() }
        if (selected != null) PillChip("Everyone ✕", true) { onPick(null) }
        list.forEach { p ->
            val face = remember(v, p.id) { Faces.sample(p.id) }
            val img by produceState<ImageBitmap?>(null, face?.key, face?.t) {
                val f = face ?: return@produceState
                val u = uriOf(f.key) ?: return@produceState
                value = withContext(Dispatchers.IO) {
                    try {
                        val b = (if (video) GradeIO.videoFrame(c, u, f.t * 1000, 1280) else GradeIO.decode(c, u, 1280)) ?: return@withContext null
                        val pad = 0.25f
                        val x0 = ((f.x - f.w * pad) * b.width).toInt().coerceIn(0, b.width - 1); val y0 = ((f.y - f.h * pad) * b.height).toInt().coerceIn(0, b.height - 1)
                        val x1 = ((f.x + f.w * (1 + pad)) * b.width).toInt().coerceIn(x0 + 1, b.width); val y1 = ((f.y + f.h * (1 + pad)) * b.height).toInt().coerceIn(y0 + 1, b.height)
                        Bitmap.createScaledBitmap(Bitmap.createBitmap(b, x0, y0, x1 - x0, y1 - y0), 96, 96, true).asImageBitmap()
                    } catch (_: Throwable) { null }
                }
            }
            Column(Modifier.clip(RoundedCornerShape(8.dp)).combinedClickable(onClick = { onPick(if (selected == p.id) null else p.id) }, onLongClick = { naming = p }).padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(Ed.Raised).border(2.dp, if (selected == p.id) Pro.Amber else Color.Transparent, CircleShape)) {
                    img?.let { Image(it, null, Modifier.size(48.dp), contentScale = ContentScale.Crop) }
                }
                Text(p.name.ifBlank { "Name?" }, color = if (p.name.isBlank()) Pro.Dim else Color.White, fontSize = 10.sp, maxLines = 1)
                Text("${Faces.countOf(p.id, keys)}", color = Pro.Dim, fontSize = 9.sp)
            }
        }
    }
    naming?.let { p ->
        var t by remember(p.id) { mutableStateOf(p.name) }
        AlertDialog(onDismissRequest = { naming = null }, containerColor = Ed.Panel,
            title = { Text("Who is this?", color = Pro.Fg) },
            text = {
                Column {
                    Text("Type their name. Giving two groups the same name puts them together.", color = Pro.Dim, fontSize = 12.sp)
                    OutlinedTextField(t, { t = it.take(60) }, singleLine = true, label = { Text("Name") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Pro.Fg, unfocusedTextColor = Pro.Fg, focusedBorderColor = Pro.Amber, cursorColor = Pro.Amber))
                }
            },
            confirmButton = { TextButton(onClick = { Faces.name(c, p.id, t); naming = null }) { Text("SAVE", color = Pro.Amber) } },
            dismissButton = { TextButton(onClick = { naming = null }) { Text("CANCEL", color = Pro.Dim) } },
        )
    }
}
