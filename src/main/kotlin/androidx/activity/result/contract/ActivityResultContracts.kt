package androidx.activity.result.contract

import android.net.Uri

/**
 * The phone's "pick a file / folder" requests. On Windows each opens the Windows file dialog;
 * the result comes back the same way as on the phone.
 */
abstract class ActivityResultContract<I, O> {
    internal abstract fun run(input: I, done: (O) -> Unit)
}

object ActivityResultContracts {
    private val imageExt = listOf("jpg", "jpeg", "png", "tif", "tiff", "bmp", "gif", "webp", "heic", "heif", "dng", "avif", "jxl",
        "cr2", "cr3", "nef", "arw", "raf", "orf", "rw2", "pef", "srw", "x3f", "3fr", "iiq", "erf", "kdc", "mrw", "nrw", "sr2", "srf")
    private val videoExt = listOf("mp4", "mov", "m4v", "mkv", "webm", "avi", "mts", "m2ts", "3gp")
    private val audioExt = listOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus")

    /** Extensions for a MIME filter such as "image/*". Empty = everything. */
    internal fun extsFor(mimes: List<String>): List<String> {
        val out = ArrayList<String>()
        for (m in mimes) when {
            m == "*/*" -> return emptyList()
            m.startsWith("image/") -> out += imageExt
            m.startsWith("video/") -> out += videoExt
            m.startsWith("audio/") -> out += audioExt
            m.contains("json") -> out += "json"
            m.contains("zip") -> out += "zip"
            m.startsWith("text/") -> out += listOf("txt", "cube", "xmp", "csv", "srt", "vtt")
            m.contains("xml") -> out += "xmp"
            else -> return emptyList()
        }
        return out.distinct()
    }

    internal fun extsFor(req: androidx.activity.result.PickVisualMediaRequest): List<String> = when (req.type) {
        PickVisualMedia.ImageOnly -> imageExt
        PickVisualMedia.VideoOnly -> videoExt
        else -> imageExt + videoExt
    }

    class PickVisualMedia : ActivityResultContract<androidx.activity.result.PickVisualMediaRequest, Uri?>() {
        sealed interface VisualMediaType
        object ImageOnly : VisualMediaType
        object VideoOnly : VisualMediaType
        object ImageAndVideo : VisualMediaType
        override fun run(input: androidx.activity.result.PickVisualMediaRequest, done: (Uri?) -> Unit) =
            FileDialogs.open(extsFor(input), multiple = false) { done(it.firstOrNull()) }
    }

    class PickMultipleVisualMedia(val max: Int = 100) : ActivityResultContract<androidx.activity.result.PickVisualMediaRequest, List<Uri>>() {
        override fun run(input: androidx.activity.result.PickVisualMediaRequest, done: (List<Uri>) -> Unit) =
            FileDialogs.open(extsFor(input), multiple = true) { done(it.take(max)) }
    }

    class OpenDocument : ActivityResultContract<Array<String>, Uri?>() {
        override fun run(input: Array<String>, done: (Uri?) -> Unit) = FileDialogs.open(extsFor(input.toList()), multiple = false) { done(it.firstOrNull()) }
    }

    class OpenMultipleDocuments : ActivityResultContract<Array<String>, List<Uri>>() {
        override fun run(input: Array<String>, done: (List<Uri>) -> Unit) = FileDialogs.open(extsFor(input.toList()), multiple = true, done)
    }

    class GetContent : ActivityResultContract<String, Uri?>() {
        override fun run(input: String, done: (Uri?) -> Unit) = FileDialogs.open(extsFor(listOf(input)), multiple = false) { done(it.firstOrNull()) }
    }

    class GetMultipleContents : ActivityResultContract<String, List<Uri>>() {
        override fun run(input: String, done: (List<Uri>) -> Unit) = FileDialogs.open(extsFor(listOf(input)), multiple = true, done)
    }

    class CreateDocument(val mime: String = "*/*") : ActivityResultContract<String, Uri?>() {
        override fun run(input: String, done: (Uri?) -> Unit) = FileDialogs.save(input, done)
    }

    class OpenDocumentTree : ActivityResultContract<Uri?, Uri?>() {
        override fun run(input: Uri?, done: (Uri?) -> Unit) = FileDialogs.folder(done)
    }

    class TakePicture : ActivityResultContract<Uri, Boolean>() {
        override fun run(input: Uri, done: (Boolean) -> Unit) = done(false)
    }

    class RequestPermission : ActivityResultContract<String, Boolean>() {
        override fun run(input: String, done: (Boolean) -> Unit) = done(true)
    }

    class RequestMultiplePermissions : ActivityResultContract<Array<String>, Map<String, Boolean>>() {
        override fun run(input: Array<String>, done: (Map<String, Boolean>) -> Unit) = done(input.associateWith { true })
    }
}

/** Windows file dialogs (the system's own Open / Save / folder pickers). */
object FileDialogs {
    private var lastDir: java.io.File? = null

    private fun later(block: () -> Unit) = javax.swing.SwingUtilities.invokeLater(block)

    fun open(exts: List<String>, multiple: Boolean, done: (List<Uri>) -> Unit) = later {
        val d = java.awt.FileDialog(null as java.awt.Frame?, "", java.awt.FileDialog.LOAD)
        d.isMultipleMode = multiple
        lastDir?.let { d.directory = it.path }
        if (exts.isNotEmpty()) {
            d.setFilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in exts }
            // Windows uses the pattern in the file name box as its filter.
            d.file = exts.joinToString(";") { "*.$it" }
        }
        d.isVisible = true
        val files = d.files.toList()
        files.firstOrNull()?.parentFile?.let { lastDir = it }
        done(files.map { Uri.fromFile(it) })
    }

    fun save(suggested: String, done: (Uri?) -> Unit) = later {
        val d = java.awt.FileDialog(null as java.awt.Frame?, "", java.awt.FileDialog.SAVE)
        lastDir?.let { d.directory = it.path }
        d.file = suggested
        d.isVisible = true
        val f = d.file?.let { java.io.File(d.directory, it) }
        f?.parentFile?.let { lastDir = it }
        done(f?.let { Uri.fromFile(it) })
    }

    fun folder(done: (Uri?) -> Unit) = later {
        val c = javax.swing.JFileChooser(lastDir)
        c.fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
        val ok = c.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION
        val f = if (ok) c.selectedFile else null
        f?.let { lastDir = it }
        done(f?.let { Uri.fromFile(it) })
    }
}
