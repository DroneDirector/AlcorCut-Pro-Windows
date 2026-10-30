package android.content

/** Copy to the Windows clipboard. Own code. */
class ClipData private constructor(val text: String) {
    companion object { fun newPlainText(label: CharSequence?, text: CharSequence?): ClipData = ClipData(text?.toString() ?: "") }
}

class ClipboardManager {
    fun setPrimaryClip(c: ClipData) {
        try { java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(c.text), null) } catch (_: Throwable) {}
    }
}

open class BroadcastReceiver { open fun onReceive(ctx: Context, i: Intent) {} }
class IntentFilter(val action: String = "")
