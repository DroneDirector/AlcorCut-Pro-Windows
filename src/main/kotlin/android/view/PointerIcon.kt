package android.view

/** Mouse pointers: the system ones, as Windows cursors. */
object PointerIcon {
    const val TYPE_NULL = 0
    const val TYPE_ARROW = 1000
    const val TYPE_HAND = 1002
    const val TYPE_CROSSHAIR = 1007
    const val TYPE_TEXT = 1008
    const val TYPE_GRAB = 1020
    const val TYPE_GRABBING = 1021
    fun getSystemIcon(c: android.content.Context, type: Int): java.awt.Cursor = when (type) {
        TYPE_NULL -> java.awt.Toolkit.getDefaultToolkit().createCustomCursor(java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB), java.awt.Point(0, 0), "none")
        TYPE_HAND -> java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
        TYPE_CROSSHAIR -> java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.CROSSHAIR_CURSOR)
        TYPE_TEXT -> java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.TEXT_CURSOR)
        TYPE_GRAB, TYPE_GRABBING -> java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.MOVE_CURSOR)
        else -> java.awt.Cursor.getDefaultCursor()
    }
}
