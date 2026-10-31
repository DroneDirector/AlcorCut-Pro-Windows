package com.dronedirector.veyrocutpro

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Background work (exports, AI): on the phone a notification; on Windows the progress shows in the
 * window's task bar button and the app's status line.
 */
object ExportService {
    class Task(var title: String, var pct: Int)
    val tasks = mutableStateMapOf<String, Task>()
    var lastResult by mutableStateOf<String?>(null)
    const val EXPORT = "export"
    /** The last export, for the share button (Windows opens the folder instead). */
    @Volatile var shareUri: android.net.Uri? = null
    @Volatile var shareMime: String? = null

    fun begin(context: Context, key: String, title: String, pct: Int = -1) { tasks[key] = Task(title, pct); TaskbarProgress.update() }
    fun update(context: Context, key: String, pct: Int, title: String? = null) {
        tasks[key]?.let { tasks[key] = Task(title ?: it.title, pct) }
        TaskbarProgress.update()
    }
    fun isRunning(key: String) = tasks.containsKey(key)
    fun end(context: Context, key: String, text: String? = null) { tasks.remove(key); if (text != null) lastResult = text; TaskbarProgress.update() }
    fun start(context: Context, what: String) = begin(context, EXPORT, what, 0)
    fun progress(context: Context, p: Int, what: String? = null) = update(context, EXPORT, p, what)
    fun finish(context: Context, text: String?) = end(context, EXPORT, text)
}

/** Progress on the window's task bar button (Windows 7 and newer). */
object TaskbarProgress {
    @Volatile var window: java.awt.Window? = null
    fun update() {
        val w = window ?: return
        try {
            val t = java.awt.Taskbar.getTaskbar()
            if (!t.isSupported(java.awt.Taskbar.Feature.PROGRESS_STATE_WINDOW)) return
            val list = ExportService.tasks.values.toList()
            if (list.isEmpty()) { t.setWindowProgressState(w, java.awt.Taskbar.State.OFF); return }
            val known = list.filter { it.pct >= 0 }
            if (known.isEmpty()) t.setWindowProgressState(w, java.awt.Taskbar.State.INDETERMINATE)
            else { t.setWindowProgressState(w, java.awt.Taskbar.State.NORMAL); t.setWindowProgressValue(w, known.sumOf { it.pct } / known.size) }
        } catch (_: Throwable) {}
    }
}
