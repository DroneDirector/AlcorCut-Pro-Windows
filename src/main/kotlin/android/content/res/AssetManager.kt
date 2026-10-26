package android.content.res

import java.io.FileNotFoundException
import java.io.InputStream

/** The app's bundled files (translations, licences, models), read from inside the app. Own code. */
class AssetManager {
    fun open(name: String): InputStream {
        val n = name.trimStart('/')
        val base = AssetManager::class.java.getResourceAsStream("/$n") ?: throw FileNotFoundException(name)
        // Translations: the Windows-only texts are added to the app's own language files.
        if (n.startsWith("i18n/") && n.endsWith(".json")) {
            val extra = AssetManager::class.java.getResourceAsStream("/i18n-windows/" + n.removePrefix("i18n/")) ?: return base
            val a = com.google.gson.JsonParser.parseString(base.bufferedReader(Charsets.UTF_8).use { it.readText() }).asJsonObject
            val b = com.google.gson.JsonParser.parseString(extra.bufferedReader(Charsets.UTF_8).use { it.readText() }).asJsonObject
            for ((k, v) in b.entrySet()) a.add(k, v)
            return a.toString().byteInputStream(Charsets.UTF_8)
        }
        return base
    }

    fun list(path: String): Array<String> {
        val p = path.trim('/')
        // Bundled folders carry an index file listing their contents.
        val idx = AssetManager::class.java.getResourceAsStream("/$p/index.txt") ?: return emptyArray()
        return idx.bufferedReader().use { r -> r.readLines().map { it.trim() }.filter { it.isNotEmpty() }.toTypedArray() }
    }
}
