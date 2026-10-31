package com.dronedirector.veyrocutpro.windows

import android.content.Context
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.Transformer

/** Renders an edit ([Composition]) to an MP4 file on Windows. Filled in with the export batch. */
class WinExport(
    private val context: Context, private val transformer: Transformer, private val composition: Composition,
    private val path: String, private val listeners: List<Transformer.Listener>,
) {
    @Volatile var progress = 0; private set
    @Volatile var done = false; private set
    @Volatile private var cancelled = false

    fun start() {
        javax.swing.SwingUtilities.invokeLater {
            done = true
            val e = ExportException("Video export comes in the next Windows version", null, ExportException.ERROR_CODE_UNSPECIFIED)
            listeners.forEach { it.onError(composition, Transformer.result(0, 0, 0, 0, 0, e), e) }
        }
    }
    fun cancel() { cancelled = true }
}
