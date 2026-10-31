package android.os

/** Key–value data passed along with messages. */
open class Bundle {
    private val m = HashMap<String, Any?>()
    fun putString(k: String, v: String?) { m[k] = v }
    fun getString(k: String): String? = m[k] as? String
    fun putInt(k: String, v: Int) { m[k] = v }
    fun getInt(k: String, d: Int = 0): Int = m[k] as? Int ?: d
    fun putLong(k: String, v: Long) { m[k] = v }
    fun getLong(k: String, d: Long = 0): Long = m[k] as? Long ?: d
    fun putBoolean(k: String, v: Boolean) { m[k] = v }
    fun getBoolean(k: String, d: Boolean = false): Boolean = m[k] as? Boolean ?: d
    fun putFloat(k: String, v: Float) { m[k] = v }
    fun getFloat(k: String, d: Float = 0f): Float = m[k] as? Float ?: d
    fun containsKey(k: String) = k in m
    fun keySet(): Set<String> = m.keys
}
