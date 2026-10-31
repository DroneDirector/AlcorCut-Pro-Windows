package com.dronedirector.veyrocutpro.editor

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.ui.Pro
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/**
 * The AI models the photo editor uses, downloaded once in the background on first start (and
 * resumed if the PC was offline): noise reduction, upscaling, subject / sky masks, faces and Magic
 * Mask, and the video editor's studio voice. Kept in the app's own folder. Own code.
 */
object WinData {
    private const val WIN = "https://github.com/DroneDirector/AlcorCut-Pro-Windows/releases/download/ai-models-win-v1/"
    private const val PHONE = ModelStore.BASE

    /** (where from, file name in the models folder) */
    private val FILES = listOf(
        WIN to "face_detection_yunet_2023mar.onnx",
        WIN to "deeplab.onnx",
        WIN to "esrgan.onnx",
        PHONE to "sam_decoder.onnx",
        PHONE to "sam_encoder.onnx",
        PHONE to "face_sface_2021dec.onnx",
        WIN to "nafnet.onnx",
        PHONE to "DeepFilterNet3_onnx.tar.gz",
    )

    var running by mutableStateOf(false); private set
    var percent by mutableIntStateOf(0); private set
    var current by mutableStateOf(""); private set
    @Volatile private var thread: Thread? = null

    fun has(c: Context, name: String) = ModelStore.file(c, name).let { it.exists() && it.length() > 1000 && !File(it.path + ".part").exists() }

    @Synchronized fun start(c: Context) {
        if (thread?.isAlive == true) return
        val todo = FILES.filter { !has(c, it.second) }
        if (todo.isEmpty()) return
        running = true
        thread = Thread({
            var tries = 0
            var left = todo
            while (left.isNotEmpty() && tries < 30) {
                for ((i, f) in left.withIndex()) {
                    current = AppData.label(f.second.removeSuffix(".onnx")).takeIf { it != f.second.removeSuffix(".onnx") } ?: when {
                        f.second.startsWith("face_") -> "Face recognition"
                        f.second.startsWith("DeepFilterNet") -> "Studio voice"
                        else -> f.second
                    }
                    fetch(c, f.first + f.second, ModelStore.file(c, f.second)) { p -> percent = ((i * 100 + p) / left.size).coerceIn(0, 99) }
                }
                left = left.filter { !has(c, it.second) }
                if (left.isNotEmpty()) { tries++; Thread.sleep(20_000) }
            }
            percent = 100; running = false
        }, "ai-models").apply { isDaemon = true; start() }
    }

    /** Downloads [url] into [target], continuing a part-done download. */
    private fun fetch(c: Context, url0: String, target: File, progress: (Int) -> Unit) {
        val part = File(target.path + ".part")
        try {
            if (target.exists() && !part.exists()) return
            var url = URL(url0); var hops = 0
            val have = if (part.exists()) part.length() else 0L
            var conn: HttpURLConnection
            while (true) {
                conn = url.openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false; conn.connectTimeout = 20_000; conn.readTimeout = 60_000
                if (have > 0) conn.setRequestProperty("Range", "bytes=$have-")
                val code = conn.responseCode
                if (code in 300..399 && hops < 8) { url = URL(url, conn.getHeaderField("Location")); conn.disconnect(); hops++; continue }
                if (code != 200 && code != 206) { conn.disconnect(); return }
                break
            }
            val resume = conn.responseCode == 206
            val total = (if (resume) have else 0L) + conn.contentLengthLong.coerceAtLeast(0)
            RandomAccessFile(part, "rw").use { out ->
                if (!resume) out.setLength(0)
                out.seek(if (resume) have else 0L)
                conn.inputStream.use { ins ->
                    val buf = ByteArray(1 shl 16)
                    while (true) {
                        val n = ins.read(buf); if (n < 0) break
                        out.write(buf, 0, n)
                        if (total > 0) progress((out.filePointer * 100 / total).toInt())
                    }
                }
            }
            target.delete()
            if (!part.renameTo(target)) { part.copyTo(target, overwrite = true); part.delete() }
        } catch (_: Throwable) {}
    }
}

/** Small note while the AI models download (bottom left of the window). */
@Composable
fun WinDataBadge(modifier: Modifier = Modifier) {
    if (!WinData.running) return
    Text(
        "AI models (${WinData.current}) downloading… ${WinData.percent} %", color = Color.White, fontSize = 12.sp,
        modifier = modifier.padding(10.dp).clip(RoundedCornerShape(50)).background(Color(0xE6202226)).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
