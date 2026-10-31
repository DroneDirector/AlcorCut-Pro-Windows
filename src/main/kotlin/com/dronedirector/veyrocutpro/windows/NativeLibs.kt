package com.dronedirector.veyrocutpro.windows

import java.io.File

/** The phone's native libraries, built for Windows and shipped in the app's folder (native/). */
object NativeLibs {
    private val loaded = HashSet<String>()
    @Synchronized fun load(name: String) {
        if (name in loaded) return
        val win = System.getProperty("os.name").lowercase().contains("win")
        val file = if (win) "$name.dll" else "lib$name.so"
        val dirs = listOfNotNull(System.getProperty("compose.application.resources.dir")?.let { File(it, "native") }, File("packaging/resources/windows/native"), File("native-build/Release"))
        val f = dirs.map { File(it, file) }.firstOrNull { it.exists() } ?: throw UnsatisfiedLinkError("$file isn't in the app's folder")
        System.load(f.absolutePath)
        loaded += name
    }
}
