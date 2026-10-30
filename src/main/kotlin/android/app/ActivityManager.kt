package android.app

/** Memory facts for the app log (from Windows / the Java runtime). Own code. */
class ActivityManager {
    class MemoryInfo {
        @JvmField var availMem: Long = 0
        @JvmField var totalMem: Long = 0
        @JvmField var lowMemory: Boolean = false
    }
    /** Earlier closes of the app (Windows keeps no such list; crashes are caught by the app itself). */
    fun getHistoricalProcessExitReasons(pkg: String?, pid: Int, max: Int): List<ApplicationExitInfo> = emptyList()

    class RunningAppProcessInfo {
        companion object { const val IMPORTANCE_FOREGROUND = 100; const val IMPORTANCE_VISIBLE = 200 }
    }

    fun getMemoryInfo(m: MemoryInfo) {
        try {
            val os = java.lang.management.ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean
            m.totalMem = os.totalMemorySize; m.availMem = os.freeMemorySize; m.lowMemory = m.availMem < 512L * 1024 * 1024
        } catch (_: Throwable) {}
    }
}

object Application {
    fun getProcessName(): String = "AlcorCut Pro"
}

class ApplicationExitInfo {
    val timestamp: Long = 0
    val reason: Int = 0
    val importance: Int = 0
    val processName: String = ""
    val description: String? = null
    val traceInputStream: java.io.InputStream? = null
    companion object {
        const val REASON_CRASH = 4
        const val REASON_CRASH_NATIVE = 5
        const val REASON_LOW_MEMORY = 3
    }
}
