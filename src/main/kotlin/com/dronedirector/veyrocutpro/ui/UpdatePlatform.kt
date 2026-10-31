package com.dronedirector.veyrocutpro.ui

import android.content.Context
import java.io.File

/** PC side of in-app updates: downloads the Windows installer, closes the app, installs and opens it again. Own code. */
object UpdatePlatform {
    const val repo = "DroneDirector/AlcorCut-Pro-Windows"
    const val tagPrefix = "v"
    /** Only the installed app updates itself (not a copy started from the project folder). */
    val supported: Boolean get() = System.getProperty("os.name").lowercase().contains("win") && System.getProperty("jpackage.app-path") != null
    const val installNote = "The app closes, installs the update and opens again — Windows may ask you to allow the install. Save your work first."
    const val installingText = "Installing — the app closes and opens again when it's done"

    fun currentVersion(c: Context): String = android.content.pm.PackageManager.version

    /** The .msi installer (it upgrades the installed app in place). */
    fun pickAsset(assets: List<Triple<String, String, Long>>): Triple<String, String, Long>? =
        assets.firstOrNull { it.first.endsWith(".msi", ignoreCase = true) }

    /** Starts the installer after the app has closed, then opens the app again. Returns a problem to show, or null. */
    fun install(c: Context, msi: File): String? {
        if (!supported) return I18n.tr("Updates install only in the installed app.")
        return try {
            val exe = System.getProperty("jpackage.app-path") ?: ""
            val pid = ProcessHandle.current().pid()
            val log = File(msi.parentFile, "install-log.txt")
            val script = File(msi.parentFile, "install-update.cmd")
            script.writeText(
                "@echo off\r\n" +
                ":wait\r\n" +
                "tasklist /FI \"PID eq $pid\" 2>nul | find \"$pid\" >nul && (timeout /t 1 /nobreak >nul & goto wait)\r\n" +
                "msiexec /i \"${msi.absolutePath}\" /passive /norestart /l*v \"${log.absolutePath}\"\r\n" +
                "start \"\" \"$exe\"\r\n"
            )
            ProcessBuilder("cmd.exe", "/c", "start", "\"\"", "/min", script.absolutePath).start()
            Thread {
                Thread.sleep(1200)
                kotlin.system.exitProcess(0)
            }.apply { isDaemon = true }.start()
            null
        } catch (t: Throwable) { t.message ?: t.toString() }
    }
}
