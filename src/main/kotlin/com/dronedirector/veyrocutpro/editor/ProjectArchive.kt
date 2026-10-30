package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Project archive (Settings → PROJECT ARCHIVE): one .zip with the edit and every file it uses,
 * to move a project to another phone or keep it safe; and opening such a .zip again. Media that
 * can't be found on the phone (moved or deleted) can be relinked to another file.
 */
object ProjectArchive {
    private const val SCHEME = "veyroarchive"

    /** [p] with every media link passed through [f] (clips, nested clips, layers, music, audio). */
    fun mapUris(p: VideoProjectData, f: (Uri) -> Uri): VideoProjectData {
        fun clip(c: Clip): Clip = c.copy(uri = f(c.uri), nested = c.nested?.map { clip(it) })
        return p.copy(
            clips = p.clips.map { clip(it) },
            layers = p.layers.map { l -> l.copy(imageUri = l.imageUri?.let(f), videoUri = l.videoUri?.let(f)) },
            music = p.music?.let { it.copy(uri = f(it.uri)) },
            extraAudio = p.extraAudio.map { it.copy(uri = f(it.uri)) },
            audioPool = p.audioPool.map { it.copy(uri = f(it.uri)) },
            library = p.library?.map { clip(it) },
            timelines = p.timelines?.map { t ->
                t.copy(
                    clips = t.clips.map { clip(it) },
                    layers = t.layers.map { l -> l.copy(imageUri = l.imageUri?.let(f), videoUri = l.videoUri?.let(f)) },
                    music = t.music?.let { it.copy(uri = f(it.uri)) },
                    extraAudio = t.extraAudio.map { it.copy(uri = f(it.uri)) },
                )
            },
        )
    }

    private fun allUris(p: VideoProjectData): List<Uri> {
        val out = LinkedHashSet<String>()
        mapUris(p) { u -> if (u != Uri.EMPTY) out += u.toString(); u }
        return out.map { Uri.parse(it) }
    }

    internal fun displayName(context: Context, u: Uri): String = try {
        context.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (_: Throwable) { null } ?: u.lastPathSegment?.substringAfterLast('/') ?: "media"

    internal fun sizeOf(context: Context, u: Uri): Long = try {
        context.contentResolver.openFileDescriptor(u, "r")?.use { it.statSize } ?: 0L
    } catch (_: Throwable) { 0L }

    /** True when [u] can be opened right now. */
    fun readable(context: Context, u: Uri): Boolean = try {
        context.contentResolver.openFileDescriptor(u, "r")?.use { true } ?: false
    } catch (_: Throwable) { false }

    /** Writes the archive to [out]. Returns null when done, else what went wrong. Blocking. */
    fun write(context: Context, p: VideoProjectData, out: OutputStream, onProgress: (Int) -> Unit, cancelled: () -> Boolean): String? {
        val uris = allUris(p)
        val names = HashMap<String, String>()
        uris.forEachIndexed { i, u ->
            val n = displayName(context, u).replace(Regex("[^A-Za-z0-9._ -]"), "_").take(60)
            names[u.toString()] = "media/%03d_%s".format(i + 1, n)
        }
        val total = uris.sumOf { sizeOf(context, it) }.coerceAtLeast(1)
        var done = 0L
        val missing = ArrayList<String>()
        try {
            ZipOutputStream(out.buffered(1 shl 16)).use { zip ->
                // Video is already compressed: light, fast compression only.
                zip.setLevel(1)
                val packed = mapUris(p) { u -> names[u.toString()]?.let { Uri.parse("$SCHEME:$it") } ?: u }
                zip.putNextEntry(ZipEntry("project.json"))
                zip.write(ProjectStore.gson.toJson(packed).toByteArray())
                zip.closeEntry()
                val buf = ByteArray(1 shl 16)
                for (u in uris) {
                    if (cancelled()) return "Archive cancelled"
                    val ins = try { context.contentResolver.openInputStream(u) } catch (_: Throwable) { null }
                    if (ins == null) { missing += displayName(context, u); continue }
                    zip.putNextEntry(ZipEntry(names[u.toString()]!!))
                    ins.use { s ->
                        while (true) {
                            if (cancelled()) return "Archive cancelled"
                            val n = s.read(buf); if (n < 0) break
                            zip.write(buf, 0, n); done += n
                            onProgress((done * 100 / total).toInt().coerceIn(0, 99))
                        }
                    }
                    zip.closeEntry()
                }
            }
        } catch (t: Throwable) { return "Couldn't write the archive: ${t.message}" }
        onProgress(100)
        return if (missing.isEmpty()) null else "Archive saved, but ${missing.size} file(s) couldn't be read: ${missing.take(3).joinToString()}"
    }

    /** Unpacks an archive into the app's files and returns the project (a new copy) or an error. Blocking. */
    fun read(context: Context, src: Uri, onProgress: (Int) -> Unit): Pair<VideoProjectData?, String?> {
        val dir = File(context.filesDir, "archives/${System.currentTimeMillis()}").apply { mkdirs() }
        val root = dir.canonicalPath + File.separator
        var json: String? = null
        val total = sizeOf(context, src).coerceAtLeast(1)
        var done = 0L
        try {
            val ins = context.contentResolver.openInputStream(src) ?: return null to "Couldn't open the archive"
            ZipInputStream(ins.buffered(1 shl 16)).use { zip ->
                val buf = ByteArray(1 shl 16)
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (e.isDirectory) continue
                    if (e.name == "project.json") { json = zip.readBytes().toString(Charsets.UTF_8); continue }
                    val f = File(dir, e.name)
                    // Never write outside the archive folder.
                    if (!f.canonicalPath.startsWith(root)) continue
                    f.parentFile?.mkdirs()
                    f.outputStream().buffered(1 shl 16).use { o ->
                        while (true) { val n = zip.read(buf); if (n < 0) break; o.write(buf, 0, n); done += n; onProgress((done * 100 / total).toInt().coerceIn(0, 99)) }
                    }
                }
            }
        } catch (t: Throwable) { dir.deleteRecursively(); return null to "Couldn't read the archive: ${t.message}" }
        val j = json ?: run { dir.deleteRecursively(); return null to "This isn't a AlcorCut Pro project archive" }
        val p0 = ProjectStore.parseVideo(j) ?: run { dir.deleteRecursively(); return null to "The project in the archive couldn't be read" }
        val p = mapUris(p0) { u -> if (u.scheme == SCHEME) Uri.fromFile(File(dir, u.schemeSpecificPart)) else u }
            .copy(id = UUID.randomUUID().toString(), name = p0.name.ifBlank { "Archived project" } + " (archive)")
        onProgress(100)
        return p to null
    }
}
