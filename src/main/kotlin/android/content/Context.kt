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
        const val CONNECTIVITY_SERVICE = "connectivity"
        const val AUDIO_SERVICE = "audio"
        const val VIBRATOR_SERVICE = "vibrator"
        const val CLIPBOARD_SERVICE = "clipboard"
        const val ACTIVITY_SERVICE = "activity"
        const val NSD_SERVICE = "servicediscovery"
        const val USB_SERVICE = "usb"
        const val BATTERY_SERVICE = "batterymanager"
        const val DISPLAY_SERVICE = "display"
        const val SENSOR_SERVICE = "sensor"
        const val POWER_SERVICE = "power"
        const val LOCATION_SERVICE = "location"
        const val WINDOW_SERVICE = "window"
        const val RECEIVER_NOT_EXPORTED = 4
        const val RECEIVER_EXPORTED = 2

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

    val packageManager: android.content.pm.PackageManager by lazy { android.content.pm.PackageManager() }

    fun getSystemService(name: String): Any? = when (name) {
        CONNECTIVITY_SERVICE -> android.net.ConnectivityManager()
        CLIPBOARD_SERVICE -> ClipboardManager()
        ACTIVITY_SERVICE -> android.app.ActivityManager()
        USB_SERVICE -> android.hardware.usb.UsbManager()
        DISPLAY_SERVICE -> android.hardware.display.DisplayManager()
        SENSOR_SERVICE -> android.hardware.SensorManager()
        POWER_SERVICE -> android.os.PowerManager()
        BATTERY_SERVICE -> android.os.BatteryManager()
        AUDIO_SERVICE -> android.media.AudioManager()
        else -> null
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> getSystemService(c: Class<T>): T = (if (c == android.net.ConnectivityManager::class.java) android.net.ConnectivityManager() else null) as T

    fun registerReceiver(r: BroadcastReceiver, f: IntentFilter): Intent? = null
    fun registerReceiver(r: BroadcastReceiver, f: IntentFilter, flags: Int): Intent? = null
    fun unregisterReceiver(r: BroadcastReceiver) {}

    val resources: android.content.res.Resources get() = android.content.res.Resources.system
    val applicationInfo: android.content.pm.ApplicationInfo get() = android.content.pm.ApplicationInfo(root.path)

    /** The app's own folder, then "AlcorCut Pro" on every other drive (for catalogs). */
    fun getExternalFilesDirs(type: String?): Array<File?> {
        val own = getExternalFilesDir(type)
        val others = File.listRoots().filter { r -> r.path != own.toPath().root?.toString() && r.canWrite() && (r.totalSpace > 0) }
            .map { File(it, "AlcorCut Pro" + (type?.let { t -> File.separator + t } ?: "")) }
        return (listOf(own) + others).toTypedArray()
    }

    /** "Open this" on Windows: web pages in the browser, files in their app; "restart" restarts the app. */
    fun startActivity(i: Intent) {
        try {
            when {
                i.action == "restart" -> AppRestart.request()
                i.action == Intent.ACTION_VIEW && i.data != null -> {
                    val u = i.data!!
                    val f = u.toFile()
                    if (f != null && u.scheme == "file") java.awt.Desktop.getDesktop().open(f)
                    else java.awt.Desktop.getDesktop().browse(java.net.URI(u.toString()))
                }
                i.action == Intent.ACTION_SEND -> {
                    (i.extraMap[Intent.EXTRA_STREAM] as? Uri)?.toFile()?.let { f -> Runtime.getRuntime().exec(arrayOf("explorer.exe", "/select,", f.path)) }
                }
            }
        } catch (_: Throwable) {}
    }
}

/** Restarting the app (after a backup is restored). */
object AppRestart {
    @Volatile var handler: (() -> Unit)? = null
    fun request() { handler?.invoke() }
}

/** Settings kept in a small file per group. */
open class SharedPreferences(private val file: File) {
    private val p = Properties().apply { try { if (file.exists()) file.inputStream().use { load(it) } } catch (_: Throwable) {} }
    private val listeners = ArrayList<OnSharedPreferenceChangeListener>()

    fun interface OnSharedPreferenceChangeListener { fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) }

    open fun getString(k: String, d: String?): String? = synchronized(p) { p.getProperty("s.$k") ?: d }
    open fun getInt(k: String, d: Int): Int = synchronized(p) { p.getProperty("i.$k")?.toIntOrNull() ?: d }
    open fun getLong(k: String, d: Long): Long = synchronized(p) { p.getProperty("l.$k")?.toLongOrNull() ?: d }
    open fun getFloat(k: String, d: Float): Float = synchronized(p) { p.getProperty("f.$k")?.toFloatOrNull() ?: d }
    open fun getBoolean(k: String, d: Boolean): Boolean = synchronized(p) { p.getProperty("b.$k")?.toBooleanStrictOrNull() ?: d }
    open fun getStringSet(k: String, d: Set<String>?): Set<String>? = synchronized(p) {
        p.getProperty("ss.$k")?.let { v -> if (v.isEmpty()) emptySet() else v.split('\u0001').toSet() } ?: d
    }
    open fun contains(k: String): Boolean = synchronized(p) { p.stringPropertyNames().any { it.substringAfter('.') == k } }
    open val all: Map<String, Any?> get() = synchronized(p) { p.stringPropertyNames().associate { it.substringAfter('.') to p.getProperty(it) } }

    fun registerOnSharedPreferenceChangeListener(l: OnSharedPreferenceChangeListener) { listeners += l }
    fun unregisterOnSharedPreferenceChangeListener(l: OnSharedPreferenceChangeListener) { listeners -= l }

    open fun edit(): Editor = Editor(this)

    internal fun store(put: Map<String, String?>, clear: Boolean): Boolean {
        synchronized(p) {
            if (clear) p.clear()
            for ((k, v) in put) if (v == null) p.remove(k) else p.setProperty(k, v)
            try {
                val tmp = File(file.path + ".tmp")
                tmp.outputStream().use { p.store(it, null) }
                if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
            } catch (_: Throwable) { return false }
        }
        put.keys.map { it.substringAfter('.') }.distinct().forEach { k -> listeners.toList().forEach { it.onSharedPreferenceChanged(this, k) } }
        return true
    }

    open class Editor(private val owner: SharedPreferences?) {
        private val put = LinkedHashMap<String, String?>()
        private var clear = false
        open fun putString(k: String, v: String?): Editor = apply { put["s.$k"] = v }
        open fun putInt(k: String, v: Int): Editor = apply { put["i.$k"] = v.toString() }
        open fun putLong(k: String, v: Long): Editor = apply { put["l.$k"] = v.toString() }
        open fun putFloat(k: String, v: Float): Editor = apply { put["f.$k"] = v.toString() }
        open fun putBoolean(k: String, v: Boolean): Editor = apply { put["b.$k"] = v.toString() }
        open fun putStringSet(k: String, v: Set<String>?): Editor = apply { put["ss.$k"] = v?.joinToString("\u0001") }
        open fun remove(k: String): Editor = apply { for (t in listOf("s", "i", "l", "f", "b", "ss")) put["$t.$k"] = null }
        open fun clear(): Editor = apply { clear = true }
        open fun commit(): Boolean = owner?.store(put, clear) ?: false
        open fun apply() { commit() }
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

    fun openFileDescriptor(u: Uri, mode: String): android.os.ParcelFileDescriptor? = try {
        u.toFile()?.let { android.os.ParcelFileDescriptor.open(it, mode) }
    } catch (_: Throwable) { null }

    fun openAssetFileDescriptor(u: Uri, mode: String): android.content.res.AssetFileDescriptor? =
        u.toFile()?.takeIf { it.exists() }?.let { android.content.res.AssetFileDescriptor(it) }

    /** A small picture of an image file (long side about [size]). */
    fun loadThumbnail(u: Uri, size: android.util.Size, signal: Any?): android.graphics.Bitmap {
        val f = u.toFile() ?: throw java.io.FileNotFoundException(u.toString())
        val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(f.path, o)
        var s = 1
        while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= maxOf(size.width, size.height)) s *= 2
        val b = android.graphics.BitmapFactory.decodeFile(f.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = s })
            ?: throw java.io.IOException("Can't read " + f.name)
        val k = maxOf(size.width, size.height).toFloat() / maxOf(b.width, b.height)
        return if (k < 1f) android.graphics.Bitmap.createScaledBitmap(b, (b.width * k).toInt().coerceAtLeast(1), (b.height * k).toInt().coerceAtLeast(1), true) else b
    }

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
