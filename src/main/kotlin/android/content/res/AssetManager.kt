package android.content.res

import java.io.FileNotFoundException
import java.io.InputStream

/** The app's bundled files (translations, licences, models), read from inside the app. Own code. */
class AssetManager {
    fun open(name: String): InputStream =
        AssetManager::class.java.getResourceAsStream("/" + name.trimStart('/')) ?: throw FileNotFoundException(name)

    fun list(path: String): Array<String> {
        val p = path.trim('/')
        // Bundled folders carry an index file listing their contents.
        val idx = AssetManager::class.java.getResourceAsStream("/$p/index.txt") ?: return emptyArray()
        return idx.bufferedReader().use { r -> r.readLines().map { it.trim() }.filter { it.isNotEmpty() }.toTypedArray() }
    }
}
