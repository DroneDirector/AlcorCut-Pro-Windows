package android.content

import android.database.Cursor
import android.net.Uri
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.Properties

/**
 * The app's place on the PC: its folders under %LOCALAPPDATA%\AlcorCut Pro, its saved settings and
 * file access. Lets the shared editor code run unchanged on Windows. Own code.
 */
open class Context {
    companion object {
        const val MODE_PRIVATE = 0

        /** The one app instance. */
        val app: Context by lazy { Context() }

        val root: File by lazy {
            val base = System.getenv("LOCALAPPDATA")?.let { File(it) } ?: File(System.getProperty("user.home"), ".local/share")
            File(base, "AlcorCut Pro").apply { mkdirs() }
        }

        /** Where saved photos and videos go (the Windows Pictures / Videos folders). */
        fun picturesDir(): File = File(System.getProperty("user.home"), "Pictures")
        fun videosDir(): File = File(System.getProperty("user.home"), "Videos")
    }

    open val applicationContext: Context get() = app
    open val filesDir: File get() = File(root, "files").apply { mkdirs() }
    open val cacheDir: File get() = File(root, "cache").apply { mkdirs() }
    open val packageName: String get() = "com.dronedirector.alcorcutpro"
    fun getExternalFilesDir(type: String?): File = File(filesDir, type ?: "external").apply { mkdirs() }
    val externalCacheDir: File get() = cacheDir
    fun getDir(name: String, mode: Int): File = File(root, "app_$name").apply { mkdirs() }
    val contentResolver: ContentResolver by lazy { ContentResolver() }
    val assets: android.content.res.AssetManager by lazy { android.content.res.AssetManager() }

    private val prefs = HashMap<String, SharedPreferences>()
    fun getSharedPreferences(name: String, mode: Int): SharedPreferences = synchronized(prefs) {
        prefs.getOrPut(name) { SharedPreferences(File(File(root, "settings").apply { mkdirs() }, "$name.properties")) }
    }

    fun getString(id: Int): String = ""
}

/** Settings kept in a small file per group. */
class SharedPreferences(private val file: File) {
    private val p = Properties().apply { try { if (file.exists()) file.inputStream().use { load(it) } } catch (_: Throwable) {} }
    private val listeners = ArrayList<OnSharedPreferenceChangeListener>()

    fun interface OnSharedPreferenceChangeListener { fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) }

    fun getString(k: String, d: String?): String? = synchronized(p) { p.getProperty("s.$k") ?: d }
    fun getInt(k: String, d: Int): Int = synchronized(p) { p.getProperty("i.$k")?.toIntOrNull() ?: d }
    fun getLong(k: String, d: Long): Long = synchronized(p) { p.getProperty("l.$k")?.toLongOrNull() ?: d }
    fun getFloat(k: String, d: Float): Float = synchronized(p) { p.getProperty("f.$k")?.toFloatOrNull() ?: d }
    fun getBoolean(k: String, d: Boolean): Boolean = synchronized(p) { p.getProperty("b.$k")?.toBooleanStrictOrNull() ?: d }
    fun getStringSet(k: String, d: Set<String>?): Set<String>? = synchronized(p) {
        p.getProperty("ss.$k")?.let { v -> if (v.isEmpty()) emptySet() else v.split('\u0001').toSet() } ?: d
    }
    fun contains(k: String): Boolean = synchronized(p) { p.stringPropertyNames().any { it.substringAfter('.') == k } }
    val all: Map<String, Any?> get() = synchronized(p) { p.stringPropertyNames().associate { it.substringAfter('.') to p.getProperty(it) } }

    fun registerOnSharedPreferenceChangeListener(l: OnSharedPreferenceChangeListener) { listeners += l }
    fun unregisterOnSharedPreferenceChangeListener(l: OnSharedPreferenceChangeListener) { listeners -= l }

    fun edit() = Editor()

    inner class Editor {
        private val put = LinkedHashMap<String, String?>()
        private var clear = false
        fun putString(k: String, v: String?) = apply { put["s.$k"] = v }
        fun putInt(k: String, v: Int) = apply { put["i.$k"] = v.toString() }
        fun putLong(k: String, v: Long) = apply { put["l.$k"] = v.toString() }
        fun putFloat(k: String, v: Float) = apply { put["f.$k"] = v.toString() }
        fun putBoolean(k: String, v: Boolean) = apply { put["b.$k"] = v.toString() }
        fun putStringSet(k: String, v: Set<String>?) = apply { put["ss.$k"] = v?.joinToString("\u0001") }
        fun remove(k: String) = apply { for (t in listOf("s", "i", "l", "f", "b", "ss")) put["$t.$k"] = null }
        fun clear() = apply { clear = true }
        fun commit(): Boolean {
            synchronized(p) {
                if (clear) p.clear()
                for ((k, v) in put) if (v == null) p.remove(k) else p.setProperty(k, v)
                try {
                    val tmp = File(file.path + ".tmp")
                    tmp.outputStream().use { p.store(it, null) }
                    if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
                } catch (_: Throwable) { return false }
            }
            put.keys.map { it.substringAfter('.') }.distinct().forEach { k -> listeners.toList().forEach { it.onSharedPreferenceChanged(this@SharedPreferences, k) } }
            return true
        }
        fun apply() { commit() }
    }
}

/** Saving with the phone's "add to gallery" code lands in the PC's Pictures / Videos folders. */
class ContentValues {
    private val m = LinkedHashMap<String, Any?>()
    fun put(k: String, v: String?) { m[k] = v }
    fun put(k: String, v: Int?) { m[k] = v }
    fun put(k: String, v: Long?) { m[k] = v }
    fun put(k: String, v: Boolean?) { m[k] = v }
    fun put(k: String, v: Float?) { m[k] = v }
    fun put(k: String, v: Double?) { m[k] = v }
    fun getAsString(k: String): String? = m[k]?.toString()
    fun clear() = m.clear()
    fun containsKey(k: String) = k in m
}

class ContentResolver {
    companion object {
        const val SCHEME_CONTENT = "content"
        const val SCHEME_FILE = "file"
    }

    fun openInputStream(u: Uri): InputStream? = try {
        when (u.scheme) {
            "http", "https" -> java.net.URI(u.toString()).toURL().openStream()
            else -> u.toFile()?.inputStream()
        }
    } catch (_: Throwable) { null }

    fun openOutputStream(u: Uri): OutputStream? = openOutputStream(u, "w")
    fun openOutputStream(u: Uri, mode: String): OutputStream? = try {
        u.toFile()?.let { f -> f.parentFile?.mkdirs(); java.io.FileOutputStream(f, mode.contains('a')) }
    } catch (_: Throwable) { null }

    fun getType(u: Uri): String? = u.lastPathSegment?.substringAfterLast('.', "")?.lowercase()?.let { mimeOf(it) }

    /** "Add to the gallery": a new file in Pictures\<folder> or Videos\<folder>, never overwriting. */
    fun insert(collection: Uri, v: ContentValues): Uri? {
        val name = v.getAsString("_display_name") ?: "AlcorCut_${System.currentTimeMillis()}"
        val rel = v.getAsString("relative_path")?.replace('/', File.separatorChar)?.trimEnd(File.separatorChar)
        val mime = v.getAsString("mime_type") ?: ""
        val video = collection.toString().contains("video") || mime.startsWith("video") || mime.startsWith("audio")
        val base = if (video) Context.videosDir() else Context.picturesDir()
        val sub = rel?.substringAfter(File.separatorChar, "")?.takeIf { it.isNotEmpty() } ?: "AlcorCut Pro"
        val dir = File(base, sub).apply { mkdirs() }
        var f = File(dir, name)
        var i = 1
        while (f.exists()) { f = File(dir, name.substringBeforeLast('.') + " ($i)" + (name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" })); i++ }
        f.createNewFile()
        return Uri.fromFile(f)
    }

    fun update(u: Uri, v: ContentValues?, where: String?, args: Array<String>?): Int = 1
    fun delete(u: Uri, where: String?, args: Array<String>?): Int = if (u.toFile()?.delete() == true) 1 else 0

    fun query(u: Uri, projection: Array<String>?, sel: String?, args: Array<String>?, sort: String?): Cursor? {
        val f = u.toFile() ?: return null
        if (!f.exists()) return null
        return Cursor(mapOf("_display_name" to f.name, "_size" to f.length(), "date_modified" to f.lastModified() / 1000, "_data" to f.path))
    }

    fun takePersistableUriPermission(u: Uri, flags: Int) {}
    fun releasePersistableUriPermission(u: Uri, flags: Int) {}

    private fun mimeOf(ext: String) = when (ext) {
        "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"; "webp" -> "image/webp"; "heic", "heif" -> "image/heic"
        "tif", "tiff" -> "image/tiff"; "dng" -> "image/x-adobe-dng"; "gif" -> "image/gif"; "bmp" -> "image/bmp"; "avif" -> "image/avif"
        "mp4", "m4v" -> "video/mp4"; "mov" -> "video/quicktime"; "mkv" -> "video/x-matroska"; "webm" -> "video/webm"
        "mp3" -> "audio/mpeg"; "m4a", "aac" -> "audio/mp4"; "wav" -> "audio/wav"; "flac" -> "audio/flac"
        "cube" -> "text/plain"; "json" -> "application/json"
        else -> null
    }
}
