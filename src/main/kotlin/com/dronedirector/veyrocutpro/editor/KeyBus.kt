package com.dronedirector.veyrocutpro.editor

import android.view.KeyEvent

/**
 * Hardware keyboard shortcuts (Bluetooth / USB keyboard): the open editor registers a handler;
 * the activity passes it every key no text field has used.
 */
object KeyBus {
    @Volatile var handler: ((KeyEvent) -> Boolean)? = null
    /** While a shortcut key is being chosen, every key goes here first. */
    @Volatile var capture: ((KeyEvent) -> Boolean)? = null
    fun dispatch(e: KeyEvent): Boolean = try { capture?.invoke(e) == true || handler?.invoke(e) == true } catch (_: Throwable) { false }
}
