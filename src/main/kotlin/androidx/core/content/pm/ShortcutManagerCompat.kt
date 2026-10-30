package androidx.core.content.pm

import android.content.Context
import android.content.Intent

/**
 * "Home-screen shortcuts" on Windows: icons on the desktop that open Capture, the Photo editor or the
 * Video editor directly (the installed app with --screen=…). Own code.
 */
class ShortcutInfoCompat private constructor(val id: String, val label: String, val screen: String?) {
    class Builder(c: Context, private val id: String) {
        private var label = id; private var screen: String? = null
        fun setShortLabel(s: CharSequence) = apply { label = s.toString() }
        fun setLongLabel(s: CharSequence) = this
        fun setIcon(i: androidx.core.graphics.drawable.IconCompat) = this
        fun setIntent(i: Intent) = apply { screen = i.getStringExtra("screen") }
        fun build() = ShortcutInfoCompat(id, label, screen)
    }
}

object ShortcutManagerCompat {
    private fun desktop() = java.io.File(System.getProperty("user.home"), "Desktop")
    private fun exe(): String? = ProcessHandle.current().info().command().orElse(null)?.takeIf { it.endsWith(".exe", true) && !it.endsWith("java.exe", true) && !it.endsWith("javaw.exe", true) }
        ?: System.getProperty("compose.application.resources.dir")?.let { java.io.File(it).parentFile?.parentFile?.listFiles()?.firstOrNull { f -> f.name.endsWith(".exe", true) }?.path }

    private fun link(s: ShortcutInfoCompat) = java.io.File(desktop(), s.label.replace(Regex("[\\\\/:*?\"<>|]"), "") + ".lnk")

    private fun make(s: ShortcutInfoCompat): Boolean {
        val target = exe() ?: return false
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) return false
        fun q(v: String) = "'" + v.replace("'", "''") + "'"
        val script = "\$s = (New-Object -ComObject WScript.Shell).CreateShortcut(${q(link(s).path)}); \$s.TargetPath = ${q(target)}; " +
            "\$s.Arguments = ${q("--screen=" + (s.screen ?: ""))}; \$s.IconLocation = ${q(target)}; \$s.WorkingDirectory = ${q(java.io.File(target).parent)}; \$s.Save()"
        return try {
            val p = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script).redirectErrorStream(true).start()
            p.inputStream.readAllBytes(); p.waitFor() == 0 && link(s).exists()
        } catch (_: Throwable) { false }
    }

    fun enableShortcuts(c: Context, list: List<ShortcutInfoCompat>) {}
    fun disableShortcuts(c: Context, ids: List<String>, msg: CharSequence?) {}
    fun updateShortcuts(c: Context, list: List<ShortcutInfoCompat>): Boolean = true
    fun isRequestPinShortcutSupported(c: Context): Boolean = exe() != null
    fun requestPinShortcut(c: Context, s: ShortcutInfoCompat, cb: Any?): Boolean = make(s)
}
