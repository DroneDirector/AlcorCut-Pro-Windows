package android.content

import android.net.Uri

/** The phone's "open this / share this" requests. On Windows: open in the browser or the file's app. Own code. */
open class Intent(var action: String? = null, var data: Uri? = null) {
    constructor(context: Context?, cls: Class<*>?) : this(null as String?, null as Uri?)
    var flags = 0
    var type: String? = null
    private val extras = HashMap<String, Any?>()
    fun addFlags(f: Int) = apply { flags = flags or f }
    fun setFlags(f: Int) = apply { flags = f }
    fun setDataAndType(u: Uri?, t: String?) = apply { data = u; type = t }
    fun setType(t: String?) = apply { type = t }
    fun setPackage(p: String?) = apply {}
    fun putExtra(k: String, v: Any?) = apply { extras[k] = v }
    fun getStringExtra(k: String): String? = extras[k] as? String
    fun getBooleanExtra(k: String, d: Boolean): Boolean = extras[k] as? Boolean ?: d
    fun getIntExtra(k: String, d: Int): Int = extras[k] as? Int ?: d
    fun getLongExtra(k: String, d: Long): Long = extras[k] as? Long ?: d
    fun hasExtra(k: String) = k in extras
    @Suppress("UNCHECKED_CAST") fun <T> getParcelableExtra(k: String): T? = extras[k] as? T
    @Suppress("UNCHECKED_CAST") fun <T> getParcelableExtra(k: String, c: Class<T>): T? = extras[k] as? T
    internal val extraMap: Map<String, Any?> get() = extras

    companion object {
        const val ACTION_VIEW = "android.intent.action.VIEW"
        const val ACTION_SEND = "android.intent.action.SEND"
        const val ACTION_SEND_MULTIPLE = "android.intent.action.SEND_MULTIPLE"
        const val ACTION_MAIN = "android.intent.action.MAIN"
        const val EXTRA_STREAM = "android.intent.extra.STREAM"
        const val EXTRA_TEXT = "android.intent.extra.TEXT"
        const val EXTRA_SUBJECT = "android.intent.extra.SUBJECT"
        const val EXTRA_EMAIL = "android.intent.extra.EMAIL"
        const val FLAG_GRANT_READ_URI_PERMISSION = 1
        const val FLAG_GRANT_WRITE_URI_PERMISSION = 2
        const val FLAG_GRANT_PERSISTABLE_URI_PERMISSION = 64
        const val FLAG_ACTIVITY_NEW_TASK = 0x10000000
        const val FLAG_ACTIVITY_CLEAR_TASK = 0x00008000
        const val FLAG_ACTIVITY_CLEAR_TOP = 0x04000000
        fun createChooser(target: Intent, title: CharSequence?): Intent = target
    }
}
