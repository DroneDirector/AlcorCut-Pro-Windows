package android.content.pm

/** The app's own version (the installer sets it). Own code. */
class PackageInfo(val versionName: String?, val longVersionCode: Long) {
    @Suppress("DEPRECATION") val versionCode: Int get() = longVersionCode.toInt()
}

class PackageManager {
    class NameNotFoundException : Exception()
    companion object {
        const val PERMISSION_GRANTED = 0
        const val PERMISSION_DENIED = -1
        const val FEATURE_CAMERA_ANY = "android.hardware.camera.any"
        val version: String by lazy {
            System.getProperty("jpackage.app-version")
                ?: PackageManager::class.java.getResourceAsStream("/version.txt")?.bufferedReader()?.readText()?.trim()
                ?: "1.0.0"
        }
    }
    fun getPackageInfo(name: String, flags: Int): PackageInfo = PackageInfo(version, version.substringAfterLast('.').toLongOrNull() ?: 0L)
    fun getLaunchIntentForPackage(name: String): android.content.Intent? = android.content.Intent("restart")
    fun hasSystemFeature(f: String): Boolean = false
}

class ApplicationInfo(@JvmField val dataDir: String)
