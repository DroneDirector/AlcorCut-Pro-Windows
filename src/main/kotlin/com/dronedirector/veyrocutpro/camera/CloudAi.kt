package com.dronedirector.veyrocutpro.camera

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Cloud AI (optional): runs Topaz Labs' professional models through the user's own fal.ai
 * account. Photos: Topaz upscale (Standard V2) and Topaz Denoise. Video: Topaz Nyx denoise.
 * The key is stored encrypted on the phone and only ever sent to fal.ai.
 */
object CloudAi {
    enum class PhotoDenoise(val label: String, val api: String) { NORMAL("Normal", "Normal"), STRONG("Strong", "Strong"), EXTREME("Extreme", "Extreme"), MAX("Max", "Denoise Max") }
    enum class VideoModel(val label: String, val api: String) { FAST("Nyx Fast", "Nyx Fast"), NYX("Nyx", "Nyx"), HF("Nyx HF (high fidelity)", "Nyx HF"), XL("Nyx XL", "Nyx XL") }

    private const val PREFS = "procam"
    private var loaded = false

    var photos by mutableStateOf(false)        // run photo AI (upscale + denoise) in the cloud
        private set
    var video by mutableStateOf(false)         // run video AI noise reduction in the cloud
        private set
    var wifiOnly by mutableStateOf(true)
        private set
    var photoDenoise by mutableStateOf(PhotoDenoise.NORMAL)
        private set
    var videoModel by mutableStateOf(VideoModel.FAST)
        private set
    var hasKey by mutableStateOf(false)
        private set

    // Progress for the AI badge.
    var stage by mutableStateOf<String?>(null)
        private set
    var progress by mutableIntStateOf(0)
        private set
    private val cancel = AtomicBoolean(false)
    @Volatile private var title = "CLOUD AI"
    class Stopped : RuntimeException("stopped")
    class CloudError(msg: String) : RuntimeException(msg)

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun secure(c: Context) = try {
        val alias = androidx.security.crypto.MasterKeys.getOrCreate(androidx.security.crypto.MasterKeys.AES256_GCM_SPEC)
        androidx.security.crypto.EncryptedSharedPreferences.create(
            "acluxpro_secure", alias, c,
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (_: Throwable) { null }

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(c)
        photos = p.getBoolean("cloud_photos", false)
        video = p.getBoolean("cloud_video", false)
        wifiOnly = p.getBoolean("cloud_wifi", true)
        photoDenoise = PhotoDenoise.entries.firstOrNull { it.name == p.getString("cloud_pd", null) } ?: PhotoDenoise.NORMAL
        videoModel = VideoModel.entries.firstOrNull { it.name == p.getString("cloud_vm", null) } ?: VideoModel.FAST
        hasKey = !key(c).isNullOrBlank()
    }

    fun set(c: Context, photos: Boolean = this.photos, video: Boolean = this.video, wifiOnly: Boolean = this.wifiOnly,
            photoDenoise: PhotoDenoise = this.photoDenoise, videoModel: VideoModel = this.videoModel) {
        this.photos = photos; this.video = video; this.wifiOnly = wifiOnly; this.photoDenoise = photoDenoise; this.videoModel = videoModel
        prefs(c).edit().putBoolean("cloud_photos", photos).putBoolean("cloud_video", video).putBoolean("cloud_wifi", wifiOnly)
            .putString("cloud_pd", photoDenoise.name).putString("cloud_vm", videoModel.name).apply()
    }

    fun key(c: Context): String? = secure(c)?.getString("fal_key", null)

    fun saveKey(c: Context, k: String?) {
        secure(c)?.edit()?.apply { if (k.isNullOrBlank()) remove("fal_key") else putString("fal_key", k.trim()) }?.apply()
        hasKey = !k.isNullOrBlank()
    }

    fun usePhotos(c: Context): Boolean { load(c); return photos && hasKey }
    fun useVideo(c: Context): Boolean { load(c); return video && hasKey }

    fun stop() = cancel.set(true)

    private fun onWifi(c: Context): Boolean = try {
        val cm = c.getSystemService(ConnectivityManager::class.java)
        cm.getNetworkCapabilities(cm.activeNetwork)?.let {
            it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } ?: false
    } catch (_: Throwable) { false }

    /** Lets callers show their own step (e.g. trimming) in the cloud badge. */
    fun show(s: String?, p: Int = 0) = setStage(s, p)

    private fun setStage(s: String?, p: Int = 0) { android.os.Handler(android.os.Looper.getMainLooper()).post { stage = s; progress = p } }
    private fun check() { if (cancel.get()) throw Stopped() }

    // ------------------------------------------------------------------ jobs (blocking; call off the main thread)

    /** Topaz upscale of a photo to [factor]×; returns the result JPEG bytes. */
    fun upscalePhoto(c: Context, jpeg: ByteArray, factor: Float): ByteArray = job(c, "CLOUD AI UPSCALE") {
        val url = upload(c, jpeg.inputStream(), jpeg.size.toLong(), "photo.jpg", "image/jpeg")
        val res = run(c, "fal-ai/topaz/upscale/image", JSONObject()
            .put("image_url", url).put("model", "Standard V2").put("upscale_factor", factor.toDouble().coerceIn(1.0, 4.0))
            .put("output_format", "jpeg").put("face_enhancement", false))
        download(res.getJSONObject("image").getString("url"))
    }

    /** Topaz Denoise of a photo; returns the result JPEG bytes. */
    fun denoisePhoto(c: Context, jpeg: ByteArray): ByteArray = job(c, "CLOUD AI NOISE REDUCTION") {
        val url = upload(c, jpeg.inputStream(), jpeg.size.toLong(), "photo.jpg", "image/jpeg")
        val res = run(c, "topaz/denoise/image", JSONObject()
            .put("image_url", url).put("model", photoDenoise.api).put("output_format", "jpeg"))
        download(res.getJSONObject("image").getString("url"))
    }

    /** Topaz Nyx video denoise; writes the result to [out]. [strength] maps to Topaz's noise amount. */
    fun denoiseVideo(c: Context, source: Uri, out: File, strength: Float) = job(c, "CLOUD AI NOISE REDUCTION") {
        val size = c.contentResolver.openAssetFileDescriptor(source, "r")?.use { it.length } ?: -1L
        val url = c.contentResolver.openInputStream(source)!!.use { upload(c, it, size, "clip.mp4", "video/mp4") }
        val res = run(c, "topaz/denoise/video", JSONObject()
            .put("video_url", url).put("model", videoModel.api).put("upscale_factor", 1.0)
            .put("noise", strength.toDouble().coerceIn(0.05, 1.0)).put("H264_output", true))
        val tmp = File(out.parentFile, out.name + ".part")
        tmp.outputStream().use { downloadTo(res.getJSONObject("video").getString("url"), it) }
        if (!tmp.renameTo(out)) throw CloudError("Couldn't save the cloud result")
    }

    /** Speech to text (Whisper): timed segments (start ms, end ms, text) of an audio file. */
    fun transcribe(c: Context, audio: File, language: String?): List<Triple<Long, Long, String>> = job(c, "AI CAPTIONS") {
        val url = audio.inputStream().use { upload(c, it, audio.length(), "audio.m4a", "audio/mp4") }
        val input = JSONObject().put("audio_url", url).put("task", "transcribe").put("chunk_level", "segment")
        if (!language.isNullOrBlank()) input.put("language", language)
        val res = run(c, "fal-ai/whisper", input)
        val out = ArrayList<Triple<Long, Long, String>>()
        val chunks = res.optJSONArray("chunks")
        if (chunks != null) for (i in 0 until chunks.length()) {
            val ch = chunks.getJSONObject(i)
            val text = ch.optString("text").trim()
            if (text.isEmpty()) continue
            val ts = ch.optJSONArray("timestamp")
            val start = ts?.optDouble(0, Double.NaN) ?: ch.optDouble("start", Double.NaN)
            var end = ts?.optDouble(1, Double.NaN) ?: ch.optDouble("end", Double.NaN)
            if (start.isNaN()) continue
            if (end.isNaN() || end <= start) end = start + 2.5
            out += Triple((start * 1000).toLong(), (end * 1000).toLong(), text)
        }
        if (out.isEmpty() && res.optString("text").isNotBlank()) out += Triple(0L, 4000L, res.optString("text").trim())
        out
    }

    /** Selection mask (PNG bytes, white = selected) for [prompt] (e.g. "sky"), via EVF-SAM2. */
    fun segment(c: Context, jpeg: ByteArray, prompt: String): ByteArray = job(c, "AI SELECT") {
        val url = upload(c, jpeg.inputStream(), jpeg.size.toLong(), "photo.jpg", "image/jpeg")
        val res = run(c, "fal-ai/evf-sam", JSONObject().put("image_url", url).put("prompt", prompt).put("mask_only", true).put("fill_holes", true))
        download(res.getJSONObject("image").getString("url"))
    }

    /** Subject cut-out (PNG with transparent background) via BiRefNet. */
    fun cutout(c: Context, jpeg: ByteArray): ByteArray = job(c, "AI SUBJECT") {
        val url = upload(c, jpeg.inputStream(), jpeg.size.toLong(), "photo.jpg", "image/jpeg")
        val res = run(c, "fal-ai/birefnet", JSONObject().put("image_url", url).put("output_format", "png"))
        download(res.getJSONObject("image").getString("url"))
    }

    /** Object removal (Bria Eraser): [maskPng] white where things should disappear. */
    fun erase(c: Context, jpeg: ByteArray, maskPng: ByteArray): ByteArray = job(c, "AI ERASE") {
        val url = upload(c, jpeg.inputStream(), jpeg.size.toLong(), "photo.jpg", "image/jpeg")
        val murl = upload(c, maskPng.inputStream(), maskPng.size.toLong(), "mask.png", "image/png")
        val res = run(c, "fal-ai/bria/eraser", JSONObject().put("image_url", url).put("mask_url", murl).put("mask_type", "manual"))
        download(res.getJSONObject("image").getString("url"))
    }

    /**
     * Generative expand (Bria Expand — trained only on licensed data, made for commercial use):
     * the photo placed at [x], [y] on a [cw] × [ch] canvas, the empty parts filled in.
     */
    fun expand(c: Context, jpeg: ByteArray, cw: Int, ch: Int, w: Int, h: Int, x: Int, y: Int, prompt: String): ByteArray = job(c, "AI EXPAND") {
        val url = upload(c, jpeg.inputStream(), jpeg.size.toLong(), "photo.jpg", "image/jpeg")
        val res = run(c, "fal-ai/bria/expand", JSONObject().put("image_url", url)
            .put("canvas_size", org.json.JSONArray().put(cw).put(ch))
            .put("original_image_size", org.json.JSONArray().put(w).put(h))
            .put("original_image_location", org.json.JSONArray().put(x).put(y))
            .put("prompt", prompt.replace(Regex("[^A-Za-z0-9 ,.'-]"), " ").trim()))
        download(res.getJSONObject("image").getString("url"))
    }

    /** Speech (ElevenLabs Multilingual v2, 29 languages) for one line of text; returns the MP3 bytes. */
    fun speak(c: Context, text: String, voice: String, language: String?, speed: Float): ByteArray = job(c, "AI DUBBING") {
        val input = JSONObject().put("text", text).put("voice", voice).put("speed", speed.toDouble().coerceIn(0.7, 1.2))
        if (language != null) input.put("language_code", language)
        val res = run(c, "fal-ai/elevenlabs/tts/multilingual-v2", input)
        download(res.getJSONObject("audio").getString("url"))
    }

    /** Frame interpolation (RIFE): [frames] new frames between each pair, written to [out]. */
    fun interpolate(c: Context, source: File, out: File, frames: Int) = job(c, "AI SLOW MOTION") {
        val url = source.inputStream().use { upload(c, it, source.length(), "clip.mp4", "video/mp4") }
        val res = run(c, "fal-ai/rife/video", JSONObject().put("video_url", url).put("num_frames", frames.coerceIn(1, 4)).put("use_calculated_fps", true).put("use_scene_detection", true))
        val tmp = File(out.parentFile, out.name + ".part")
        tmp.outputStream().use { downloadTo(res.getJSONObject("video").getString("url"), it) }
        if (!tmp.renameTo(out)) throw CloudError("Couldn't save the cloud result")
    }

    private fun <T> job(c: Context, title: String, block: () -> T): T {
        load(c)
        val k = key(c)
        if (k.isNullOrBlank()) throw CloudError("Add your fal.ai key in Cloud AI settings")
        if (wifiOnly && !onWifi(c)) throw CloudError("Cloud AI is set to Wi-Fi only — connect to Wi-Fi or change the setting")
        cancel.set(false)
        this.title = title
        setStage("$title · UPLOAD", 0)
        try { return block() } finally { setStage(null) }
    }

    private fun conn(url: String, method: String, c: Context): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000; readTimeout = 120_000
            setRequestProperty("Authorization", "Key ${key(c)}")
        }

    private fun readJson(h: HttpURLConnection): JSONObject {
        val code = h.responseCode
        val body = (if (code in 200..299) h.inputStream else h.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) {
            val msg = try { JSONObject(body).optString("detail").ifBlank { JSONObject(body).optString("message") } } catch (_: Throwable) { "" }
            throw CloudError(when (code) {
                401, 403 -> "fal.ai refused the key — check it in Cloud AI settings"
                402 -> "Your fal.ai account needs credit"
                429 -> "fal.ai is busy (rate limit) — try again shortly"
                else -> "Cloud AI error $code${if (msg.isNotBlank()) ": $msg" else ""}"
            })
        }
        return if (body.isBlank()) JSONObject() else JSONObject(body)
    }

    /** Upload to fal storage; returns a URL the models can read. */
    private fun upload(c: Context, data: InputStream, size: Long, name: String, mime: String): String {
        val init = conn("https://rest.fal.ai/storage/upload/initiate?storage_type=fal-cdn-v3", "POST", c).apply {
            doOutput = true; setRequestProperty("Content-Type", "application/json")
        }
        init.outputStream.use { it.write(JSONObject().put("file_name", name).put("content_type", mime).toString().toByteArray()) }
        val j = readJson(init)
        val put = (URL(j.getString("upload_url")).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"; doOutput = true; connectTimeout = 20_000; readTimeout = 300_000
            setRequestProperty("Content-Type", mime)
            if (size > 0) setFixedLengthStreamingMode(size) else setChunkedStreamingMode(256 * 1024)
        }
        put.outputStream.use { os ->
            val buf = ByteArray(256 * 1024); var sent = 0L
            while (true) {
                check()
                val n = data.read(buf); if (n < 0) break
                os.write(buf, 0, n); sent += n
                if (size > 0) setStage("$title · UPLOAD", (sent * 100 / size).toInt())
            }
        }
        if (put.responseCode !in 200..299) throw CloudError("Upload failed (${put.responseCode})")
        return j.getString("file_url")
    }

    /** Queue a model run, wait for it (cancellable), return its result JSON. */
    private fun run(c: Context, model: String, input: JSONObject): JSONObject {
        setStage("$title · PROCESSING", 0)
        val sub = conn("https://queue.fal.run/$model", "POST", c).apply { doOutput = true; setRequestProperty("Content-Type", "application/json") }
        sub.outputStream.use { it.write(input.toString().toByteArray()) }
        val s = readJson(sub)
        val id = s.getString("request_id")
        val app = model.split("/").take(2).joinToString("/")
        val statusUrl = s.optString("status_url").ifBlank { "https://queue.fal.run/$app/requests/$id/status" }
        val responseUrl = s.optString("response_url").ifBlank { "https://queue.fal.run/$app/requests/$id" }
        val cancelUrl = s.optString("cancel_url").ifBlank { "https://queue.fal.run/$app/requests/$id/cancel" }
        val t0 = System.currentTimeMillis()
        while (true) {
            if (cancel.get()) {
                try { conn(cancelUrl, "PUT", c).responseCode } catch (_: Throwable) {}
                throw Stopped()
            }
            val st = readJson(conn(statusUrl, "GET", c)).optString("status")
            if (st == "COMPLETED") break
            if (st == "FAILED" || st == "ERROR") throw CloudError("The cloud job failed")
            // No real progress from the queue: show a gently rising estimate.
            val secs = (System.currentTimeMillis() - t0) / 1000.0
            setStage("$title · PROCESSING", (95 * (1 - Math.exp(-secs / 40.0))).toInt())
            Thread.sleep(1500)
        }
        return readJson(conn(responseUrl, "GET", c))
    }

    private fun download(url: String): ByteArray {
        val out = java.io.ByteArrayOutputStream(1 shl 20)
        downloadTo(url, out)
        return out.toByteArray()
    }

    private fun downloadTo(url: String, out: java.io.OutputStream) {
        setStage("$title · DOWNLOAD", 0)
        val h = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 20_000; readTimeout = 300_000 }
        if (h.responseCode !in 200..299) throw CloudError("Download failed (${h.responseCode})")
        val total = h.contentLengthLong
        h.inputStream.use { ins ->
            val buf = ByteArray(256 * 1024); var got = 0L
            while (true) {
                check()
                val n = ins.read(buf); if (n < 0) break
                out.write(buf, 0, n); got += n
                if (total > 0) setStage("$title · DOWNLOAD", (got * 100 / total).toInt())
            }
        }
    }
}
