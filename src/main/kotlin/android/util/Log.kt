package android.util

/** Messages to the app's log file. Own code. */
object Log {
    private fun w(level: String, tag: String?, msg: String?, t: Throwable? = null): Int {
        System.err.println("$level/$tag: $msg"); t?.printStackTrace(); return 0
    }
    fun d(tag: String?, msg: String): Int = w("D", tag, msg)
    fun i(tag: String?, msg: String): Int = w("I", tag, msg)
    fun w(tag: String?, msg: String): Int = w("W", tag, msg)
    fun w(tag: String?, msg: String?, t: Throwable?): Int = w("W", tag, msg, t)
    fun e(tag: String?, msg: String): Int = w("E", tag, msg)
    fun e(tag: String?, msg: String?, t: Throwable?): Int = w("E", tag, msg, t)
}

class Size(private val w: Int, private val h: Int) {
    val width: Int get() = w
    val height: Int get() = h
    override fun toString() = "${w}x$h"
}

open class LruCache<K : Any, V : Any>(private val max: Int) {
    private val map = LinkedHashMap<K, V>(16, 0.75f, true)
    private var size = 0
    protected open fun sizeOf(key: K, value: V): Int = 1
    @Synchronized fun get(key: K): V? = map[key]
    @Synchronized fun put(key: K, value: V): V? {
        val old = map.put(key, value)
        if (old != null) size -= sizeOf(key, old)
        size += sizeOf(key, value)
        while (size > max && map.isNotEmpty()) {
            val e = map.entries.iterator().next()
            map.remove(e.key); size -= sizeOf(e.key, e.value)
        }
        return old
    }
    @Synchronized fun remove(key: K): V? = map.remove(key)?.also { size -= sizeOf(key, it) }
    @Synchronized fun evictAll() { map.clear(); size = 0 }
    @Synchronized fun size() = size
}
