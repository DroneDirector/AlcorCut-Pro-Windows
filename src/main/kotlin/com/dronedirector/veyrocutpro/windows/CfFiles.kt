package com.dronedirector.veyrocutpro.windows

/** The CineForm export writes through a C runtime file number; on Windows it is opened here. Own code. */
object CfFiles {
    private val open = java.util.WeakHashMap<android.os.ParcelFileDescriptor, Int>()
    @JvmStatic private external fun nOpen(path: String): Int
    @JvmStatic private external fun nClose(fd: Int)

    @Synchronized fun fd(p: android.os.ParcelFileDescriptor): Int = open.getOrPut(p) {
        NativeLibs.load("veyrocut_cineform")
        nOpen(p.file.absolutePath)
    }

    @Synchronized fun close(p: android.os.ParcelFileDescriptor) { open.remove(p)?.let { nClose(it) } }
}
