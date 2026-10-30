package android.net

import java.io.File

/** A file or web address. On Windows almost everything is a file:// address. Own code. */
class Uri private constructor(private val s: String) : Comparable<Uri> {
    val scheme: String? get() = s.substringBefore(':', "").takeIf { it.isNotEmpty() && s.contains(':') && it.length > 1 }
    val path: String? get() = when (scheme) {
        "file" -> try { File(java.net.URI(s)).path } catch (_: Throwable) { s.removePrefix("file://") }
        null -> s
        else -> try { java.net.URI(s).path } catch (_: Throwable) { null }
    }
    val encodedSchemeSpecificPart: String get() = if (scheme != null) s.substringAfter(':', "") else s
    val schemeSpecificPart: String get() = decode(encodedSchemeSpecificPart)
    val lastPathSegment: String? get() = path?.replace('\\', '/')?.substringAfterLast('/')?.takeIf { it.isNotEmpty() }
    val authority: String? get() = try { java.net.URI(s).authority } catch (_: Throwable) { null }
    val host: String? get() = try { java.net.URI(s).host } catch (_: Throwable) { null }
    val pathSegments: List<String> get() = path?.replace('\\', '/')?.split('/')?.filter { it.isNotEmpty() } ?: emptyList()

    fun getQueryParameter(key: String): String? = s.substringAfter('?', "").split('&')
        .map { it.split('=', limit = 2) }.firstOrNull { it[0] == key }?.getOrNull(1)?.let { java.net.URLDecoder.decode(it, "UTF-8") }

    /** The file behind this address, or null. */
    fun toFile(): File? = path?.let { File(it) }?.takeIf { scheme == "file" || scheme == null }

    override fun toString() = s
    override fun equals(other: Any?) = other is Uri && other.s == s
    override fun hashCode() = s.hashCode()
    override fun compareTo(other: Uri) = s.compareTo(other.s)

    fun buildUpon() = Builder(s)
    class Builder(private var b: String = "") {
        fun appendPath(p: String) = apply { b = b.trimEnd('/') + "/" + p }
        fun appendQueryParameter(k: String, v: String) = apply { b += (if ('?' in b) "&" else "?") + k + "=" + java.net.URLEncoder.encode(v, "UTF-8") }
        fun build() = Uri(b)
    }

    companion object {
        @JvmField val EMPTY = Uri("")
        fun parse(s: String): Uri = Uri(s)
        fun fromFile(f: File): Uri = Uri(f.absoluteFile.toURI().toString())
        fun encode(s: String): String = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
        fun decode(s: String): String = java.net.URLDecoder.decode(s, "UTF-8")
    }
}
