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
        // Studio voice (df.dll, DeepFilterNet3) is opened by the voice library by name: load it first from the app's folder.
        if (name == "veyrocut_native" && win) dirs.map { File(it, "df.dll") }.firstOrNull { it.exists() }?.let { try { System.load(it.absolutePath) } catch (_: Throwable) {} }
        val f = dirs.map { File(it, file) }.firstOrNull { it.exists() } ?: throw UnsatisfiedLinkError("$file isn't in the app's folder")
        System.load(f.absolutePath)
        loaded += name
    }
}
