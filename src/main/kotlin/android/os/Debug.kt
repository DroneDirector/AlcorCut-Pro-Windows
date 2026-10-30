package android.os

/** Memory use of the app (Java heap; Windows doesn't split it like the phone). Own code. */
object Debug {
    class MemoryInfo {
        @JvmField var totalPss: Int = 0
        fun getMemoryStat(name: String): String? = null
    }
    fun getMemoryInfo(m: MemoryInfo) {
        val rt = Runtime.getRuntime(); m.totalPss = ((rt.totalMemory() - rt.freeMemory()) / 1024).toInt()
    }
    fun getNativeHeapAllocatedSize(): Long = 0
}

object Process {
    fun myPid(): Int = ProcessHandle.current().pid().toInt()
}
