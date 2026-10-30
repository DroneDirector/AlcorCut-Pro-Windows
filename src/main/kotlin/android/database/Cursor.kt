package android.database

/** One row of file facts (name, size, date). Own code. */
class Cursor(private val row: Map<String, Any?>) : java.io.Closeable {
    private val keys = row.keys.toList()
    private var pos = -1
    val count: Int get() = 1
    fun moveToFirst(): Boolean { pos = 0; return true }
    fun moveToNext(): Boolean { pos++; return pos == 0 }
    fun getColumnIndex(name: String): Int = keys.indexOf(name)
    fun getColumnIndexOrThrow(name: String): Int = keys.indexOf(name).also { require(it >= 0) }
    fun getString(i: Int): String = keys.getOrNull(i)?.let { row[it]?.toString() } ?: ""
    fun getLong(i: Int): Long = (keys.getOrNull(i)?.let { row[it] } as? Number)?.toLong() ?: 0L
    fun getInt(i: Int): Int = getLong(i).toInt()
    fun isNull(i: Int): Boolean = keys.getOrNull(i)?.let { row[it] } == null
    override fun close() {}
}
