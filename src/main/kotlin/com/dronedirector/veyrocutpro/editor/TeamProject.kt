package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.io.InputStream

/**
 * Team projects: a video project kept in a shared folder — a folder the phone can reach (SD card,
 * USB drive, a cloud drive's folder) or a folder on a NAS — so several people can open it.
 *
 * In the folder: "<name>.alcorteam" (the project, with a save counter and who saved it),
 * "<name>.alcorlock" (who has it open right now) and "<name> media/" (every file the edit uses,
 * copied up once). Opening copies the media down once into the app. Own code.
 */
interface TeamPlace {
    /** Shown to people: where the folder is. */
    val label: String
    /** Remembered so the folder can be opened again (never holds a password). */
    val key: String
    fun list(): List<String>
    fun read(name: String): ByteArray?
    fun write(name: String, data: ByteArray)
    fun delete(name: String)
    /** Size of [name] ("folder/file" allowed), or -1 when it isn't there. */
    fun size(name: String): Long
    fun upload(name: String, input: InputStream, keepGoing: () -> Boolean, onBytes: (Long) -> Unit): Boolean
    fun download(name: String, dest: File, keepGoing: () -> Boolean, onBytes: (Long) -> Unit): Boolean
    fun close() {}
}

/** A folder picked with the phone's own folder picker. */
class FolderPlace(private val context: Context, private val tree: Uri) : TeamPlace {
    private val cr = context.contentResolver
    private val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    override val label: String = "Folder: " + (DocumentsContract.getTreeDocumentId(tree).substringAfter(':').ifBlank { "top level" })
    override val key: String = "folder|$tree"

    private data class Child(val name: String, val uri: Uri, val dir: Boolean, val size: Long)

    private fun children(dir: Uri): List<Child> {
        val q = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(dir))
        val out = ArrayList<Child>()
        cr.query(q, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                out.add(Child(c.getString(1) ?: "", DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)),
                    c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR, if (c.isNull(3)) 0L else c.getLong(3)))
            }
        }
        return out
    }

    private fun find(path: String, create: Boolean): Child? {
        val parts = path.split('/').filter { it.isNotEmpty() }
        var dir = root
        for ((i, p) in parts.withIndex()) {
            val last = i == parts.lastIndex
            val hit = children(dir).firstOrNull { it.name == p }
            if (hit != null) { if (last) return hit; dir = hit.uri; continue }
            if (!create) return null
            val made = DocumentsContract.createDocument(cr, dir, if (last) "application/octet-stream" else DocumentsContract.Document.MIME_TYPE_DIR, p) ?: return null
            if (last) return Child(p, made, false, 0L)
            dir = made
        }
        return null
    }

    override fun list() = children(root).filter { !it.dir }.map { it.name }
    override fun read(name: String): ByteArray? = try { find(name, false)?.let { c -> cr.openInputStream(c.uri)?.use { it.readBytes() } } } catch (_: Throwable) { null }
    override fun write(name: String, data: ByteArray) { upload(name, data.inputStream(), { true }, {}) }
    override fun delete(name: String) { try { find(name, false)?.let { DocumentsContract.deleteDocument(cr, it.uri) } } catch (_: Throwable) {} }
    override fun size(name: String): Long = try { find(name, false)?.size ?: -1L } catch (_: Throwable) { -1L }

    override fun upload(name: String, input: InputStream, keepGoing: () -> Boolean, onBytes: (Long) -> Unit): Boolean {
        val c = find(name, true) ?: throw java.io.IOException("Couldn't create $name")
        cr.openOutputStream(c.uri, "wt")?.use { out -> return copy(input, out, keepGoing, onBytes) } ?: throw java.io.IOException("Couldn't write $name")
    }

    override fun download(name: String, dest: File, keepGoing: () -> Boolean, onBytes: (Long) -> Unit): Boolean {
        val c = find(name, false) ?: return false
        val part = File(dest.parentFile, dest.name + ".part")
        try {
            val ok = cr.openInputStream(c.uri)?.use { input -> part.outputStream().use { copy(input, it, keepGoing, onBytes) } } ?: false
            if (!ok) return false
            if (dest.exists()) dest.delete()
            return part.renameTo(dest)
        } finally { if (part.exists()) part.delete() }
    }
}

/** A folder on a NAS (SMB). Paths inside use "\" on the NAS. */
class NasPlace(private val link: NasLink, private val share: String, private val folder: String) : TeamPlace {
    override val label: String = "NAS: ${link.host} › $share" + (if (folder.isNotEmpty()) " › " + folder.replace("\\", " › ") else "")
    override val key: String = "nas|${link.host}|$share|$folder"
    private fun p(name: String) = (if (folder.isEmpty()) "" else "$folder\\") + name.replace('/', '\\')

    override fun list() = link.listAll(share, folder).filter { !it.folder }.map { it.name }
    override fun read(name: String) = link.readBytes(share, p(name))
    override fun write(name: String, data: ByteArray) = link.writeBytes(share, p(name), data)
    override fun delete(name: String) = link.delete(share, p(name))
    override fun size(name: String) = link.size(share, p(name))
    override fun upload(name: String, input: InputStream, keepGoing: () -> Boolean, onBytes: (Long) -> Unit) = link.write(share, p(name), input, keepGoing, onBytes)
    override fun download(name: String, dest: File, keepGoing: () -> Boolean, onBytes: (Long) -> Unit) = link.copy(share, p(name), dest, keepGoing, onBytes)
    override fun close() { link.close() }
}

private fun copy(input: InputStream, out: java.io.OutputStream, keepGoing: () -> Boolean, onBytes: (Long) -> Unit): Boolean {
    val buf = ByteArray(1 shl 20)
    var total = 0L
    while (true) {
        if (!keepGoing()) return false
        val r = input.read(buf)
        if (r < 0) break
        out.write(buf, 0, r); total += r; onBytes(total)
    }
    return true
}

/** An open team project. */
class TeamSession(
    val place: TeamPlace,
    val name: String,
    val readOnly: Boolean,
    /** The save counter this edit is based on. */
    var rev: Int,
    /** Local file (uri text) → its name in the team's media folder. */
    val media: MutableMap<String, String> = HashMap(),
)

/** Who has a team project open. */
data class TeamLock(val user: String, val device: String, val at: Long)

object Team {
    private const val PREFS = "procam"
    const val EXT = ".alcorteam"
    private const val LOCK = ".alcorlock"
    /** A lock not refreshed for this long is left over (app closed, phone off). */
    const val STALE_MS = 3 * 60_000L

    var current by mutableStateOf<TeamSession?>(null)
    /** Handed from "New team project" / "Open team project" to the editor that opens next. */
    var pending: TeamSession? = null
    var busy by mutableStateOf<String?>(null)
    var progress by mutableFloatStateOf(0f)
    @Volatile var stop = false

    fun mediaFolder(name: String) = "$name media"

    fun userName(c: Context): String = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("team_user", "") ?: ""
    fun setUserName(c: Context, n: String) { c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("team_user", n.trim().take(40)).apply() }
    fun deviceId(c: Context): String {
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return p.getString("team_device", null) ?: java.util.UUID.randomUUID().toString().also { p.edit().putString("team_device", it).apply() }
    }

    // ---- Folders used before (opened again from the list).
    fun recent(c: Context): List<String> =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("team_places", "")!!.split('\n').filter { it.isNotBlank() }
    fun remember(c: Context, key: String) {
        val l = (listOf(key) + recent(c).filter { it != key }).take(6)
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("team_places", l.joinToString("\n")).apply()
    }
    fun forget(c: Context, key: String) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("team_places", recent(c).filter { it != key }.joinToString("\n")).apply()
    }
    /** A remembered folder in words. */
    fun describe(key: String): String = when {
        key.startsWith("folder|") -> "Folder: " + try { DocumentsContract.getTreeDocumentId(Uri.parse(key.removePrefix("folder|"))).substringAfter(':').ifBlank { "top level" } } catch (_: Throwable) { "?" }
        key.startsWith("nas|") -> key.removePrefix("nas|").split('|').let { "NAS: " + it.filter { s -> s.isNotEmpty() }.joinToString(" › ") }
        else -> key
    }

    fun projects(place: TeamPlace): List<String> = place.list().filter { it.endsWith(EXT) }.map { it.removeSuffix(EXT) }.sortedBy { it.lowercase() }

    // ---- Lock.
    fun lock(place: TeamPlace, name: String): TeamLock? = try {
        place.read(name + LOCK)?.let { b ->
            val o = JsonParser.parseString(String(b, Charsets.UTF_8)).asJsonObject
            TeamLock(o["user"]?.asString ?: "Someone", o["device"]?.asString ?: "", o["at"]?.asLong ?: 0L)
        }
    } catch (_: Throwable) { null }

    /** Someone else has it open right now (their lock is fresh)? */
    fun otherHolder(c: Context, place: TeamPlace, name: String): TeamLock? =
        lock(place, name)?.takeIf { it.device != deviceId(c) && System.currentTimeMillis() - it.at < STALE_MS }

    fun writeLock(c: Context, s: TeamSession) {
        if (s.readOnly) return
        val o = JsonObject().apply {
            addProperty("user", userName(c).ifBlank { android.os.Build.MODEL }); addProperty("device", deviceId(c)); addProperty("at", System.currentTimeMillis())
        }
        try { s.place.write(s.name + LOCK, o.toString().toByteArray()) } catch (_: Throwable) {}
    }

    /** Lets go of the project (only our own lock is removed). */
    fun release(c: Context, s: TeamSession) {
        if (!s.readOnly && lock(s.place, s.name)?.device == deviceId(c)) s.place.delete(s.name + LOCK)
    }

    // ---- Saving.
    sealed class Saved {
        object Ok : Saved()
        /** Someone saved after this edit was opened. */
        class Newer(val by: String, val at: Long) : Saved()
        class Failed(val why: String) : Saved()
    }

    private fun header(place: TeamPlace, name: String): JsonObject? = try {
        place.read(name + EXT)?.let { JsonParser.parseString(String(it, Charsets.UTF_8)).asJsonObject }
    } catch (_: Throwable) { null }

    /** Copies media the team folder doesn't have yet, then writes the project. Blocking. */
    fun save(c: Context, s: TeamSession, p: VideoProjectData, force: Boolean): Saved {
        if (s.readOnly) return Saved.Failed("This team project is open read-only")
        try {
            val head = header(s.place, s.name)
            val theirRev = head?.get("rev")?.asInt ?: 0
            if (!force && theirRev > s.rev) return Saved.Newer(head?.get("savedBy")?.asString ?: "Someone", head?.get("savedAt")?.asLong ?: 0L)
            // Media first, so the project never points at a file that isn't there yet.
            val uris = LinkedHashSet<String>()
            ProjectArchive.mapUris(p) { u -> if (u != Uri.EMPTY && u.scheme != "team") uris += u.toString(); u }
            val todo = uris.filter { it !in s.media }
            val used = s.media.values.toHashSet()
            todo.forEachIndexed { i, us ->
                if (stop) return Saved.Failed("Stopped")
                val u = Uri.parse(us)
                val base = ProjectArchive.displayName(c, u).replace(Regex("[\\\\/:*?\"<>|]"), "_")
                var n = base; var k = 2
                while (n in used) { n = base.substringBeforeLast('.') + " ($k)" + (base.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }); k++ }
                val total = ProjectArchive.sizeOf(c, u).coerceAtLeast(1)
                val dest = mediaFolder(s.name) + "/" + n
                busy = "Copying media to the team folder ${i + 1} of ${todo.size}…"
                if (s.place.size(dest) != total) {
                    val ok = c.contentResolver.openInputStream(u)?.use { input -> s.place.upload(dest, input, { !stop }) { b -> progress = (i + b.toFloat() / total) / todo.size } } ?: false
                    if (!ok) return Saved.Failed(if (stop) "Stopped" else "Couldn't read $base")
                }
                used += n; s.media[us] = n
            }
            busy = "Saving the team project…"
            val shared = ProjectArchive.mapUris(p) { u -> s.media[u.toString()]?.let { Uri.parse("team:" + Uri.encode(it)) } ?: u }
            val o = JsonObject().apply {
                addProperty("rev", theirRev.coerceAtLeast(s.rev) + 1)
                addProperty("savedBy", userName(c).ifBlank { android.os.Build.MODEL })
                addProperty("savedAt", System.currentTimeMillis())
                add("project", ProjectStore.gson.toJsonTree(shared))
            }
            s.place.write(s.name + EXT, o.toString().toByteArray())
            s.rev = theirRev.coerceAtLeast(s.rev) + 1
            writeLock(c, s)
            return Saved.Ok
        } catch (t: Throwable) {
            return Saved.Failed(t.message ?: t.javaClass.simpleName)
        } finally { busy = null; progress = 0f }
    }

    /** Reads a team project and copies its media down (once) into the app. Blocking. */
    fun open(c: Context, place: TeamPlace, name: String, readOnly: Boolean): Pair<VideoProjectData?, TeamSession?> {
        try {
            val head = header(place, name) ?: return null to null
            val rev = head["rev"]?.asInt ?: 0
            val raw = ProjectStore.gson.fromJson(head["project"], VideoProjectData::class.java) ?: return null to null
            val s = TeamSession(place, name, readOnly, rev)
            val dir = File(c.filesDir, "team/" + name.replace(Regex("[^A-Za-z0-9._ -]"), "_")).apply { mkdirs() }
            val names = LinkedHashSet<String>()
            ProjectArchive.mapUris(raw) { u -> if (u.scheme == "team") names += Uri.decode(u.encodedSchemeSpecificPart); u }
            val local = HashMap<String, Uri>()
            names.forEachIndexed { i, n ->
                if (stop) return null to null
                val f = File(dir, n)
                val remote = place.size(mediaFolder(name) + "/" + n)
                busy = "Copying media from the team folder ${i + 1} of ${names.size}…"
                if (!f.exists() || (remote >= 0 && f.length() != remote)) {
                    place.download(mediaFolder(name) + "/" + n, f, { !stop }) { b -> progress = (i + if (remote > 0) b.toFloat() / remote else 0f) / names.size }
                }
                val u = Uri.fromFile(f)
                local[n] = u; s.media[u.toString()] = n
            }
            val p = ProjectArchive.mapUris(raw) { u -> if (u.scheme == "team") local[Uri.decode(u.encodedSchemeSpecificPart)] ?: u else u }
            return p.copy(name = name) to s
        } catch (_: Throwable) {
            return null to null
        } finally { busy = null; progress = 0f }
    }
}
