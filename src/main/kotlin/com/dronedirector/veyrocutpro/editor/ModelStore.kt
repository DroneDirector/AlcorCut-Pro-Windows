package com.dronedirector.veyrocutpro.editor

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * AI models that are downloaded the first time a feature needs them (so the app itself stays small),
 * from the app's own GitHub release "ai-models-v1". Each model's licence allows use in a paid app.
 */
enum class AiModel(val label: String, val files: List<String>, val sizeMb: Int) {
    DEPTH("AI depth (Depth Anything V2 Small, Apache 2.0)", listOf("depth_anything_v2_small.onnx"), 99),
    INPAINT("AI object removal (LaMa, Apache 2.0)", listOf("lama_fp32.onnx"), 208),
    FACES("AI face recognition (SFace, Apache 2.0)", listOf("face_sface_2021dec.onnx"), 37),
    VOICE("AI voiceover (Kokoro 82M, Apache 2.0)", listOf("kokoro_v1_q8.onnx", "misaki_us_gold.json", "misaki_us_silver.json", "misaki_gb_gold.json", "misaki_gb_silver.json"), 105),
}

object ModelStore {
    const val BASE = "https://github.com/DroneDirector/Android-Projects/releases/download/ai-models-v1/"

    fun dir(context: Context) = File(context.filesDir, "models").apply { mkdirs() }
    fun file(context: Context, name: String) = File(dir(context), name)
    fun has(context: Context, m: AiModel) = m.files.all { file(context, it).let { f -> f.exists() && f.length() > 1000 } }

    /** Downloads one file (blocking). Returns null when done, else why not. */
    fun fetch(context: Context, name: String, onProgress: (Long, Long) -> Unit, cancelled: () -> Boolean): String? {
        val target = file(context, name)
        if (target.exists() && target.length() > 1000 && !File(target.path + ".part").exists()) return null
        // The first-time download fetches it already: don't download the same file twice at once.
        if (AppData.running) return "The AI models are still downloading (first-time setup, ${AppData.percent()}%) — try again when it's done"
        val part = File(target.parentFile, "$name.part")
        return try {
            var url = URL(BASE + name)
            var conn: HttpURLConnection
            var hops = 0
            while (true) {
                conn = url.openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 20_000; conn.readTimeout = 60_000
                val code = conn.responseCode
                if (code in 300..399 && hops < 8) { url = URL(url, conn.getHeaderField("Location")); conn.disconnect(); hops++; continue }
                if (code != 200) return "Download failed (HTTP $code)"
                break
            }
            val total = conn.contentLengthLong
            var got = 0L
            conn.inputStream.use { ins ->
                part.outputStream().buffered(1 shl 16).use { out ->
                    val buf = ByteArray(1 shl 16)
                    while (true) {
                        if (cancelled()) return "Download cancelled"
                        val n = ins.read(buf); if (n < 0) break
                        out.write(buf, 0, n); got += n
                        onProgress(got, total)
                    }
                }
            }
            if (total > 0 && got != total) return "Download incomplete"
            if (!part.renameTo(target)) return "Couldn't save the model"
            null
        } catch (t: Throwable) { "Download failed: ${t.message ?: "no connection?"}" } finally { if (!target.exists()) part.delete() }
    }

    /** Downloads everything [m] needs (blocking). [onProgress] gets 0..100. Returns null when ready, else why not. */
    fun ensure(context: Context, m: AiModel, onProgress: (Int) -> Unit, cancelled: () -> Boolean): String? {
        if (has(context, m)) return null
        val todo = m.files.filter { !file(context, it).let { f -> f.exists() && f.length() > 1000 } }
        val totalMb = m.sizeMb.coerceAtLeast(1) * 1_048_576L
        var done = m.files.filter { it !in todo }.sumOf { file(context, it).length() }
        for (name in todo) {
            val base = done
            fetch(context, name, { got, _ -> onProgress(((base + got) * 100 / totalMb).toInt().coerceIn(0, 99)) }, cancelled)?.let { return it }
            done += file(context, name).length()
        }
        onProgress(100)
        return null
    }

    /** Frees the space a model takes. */
    fun delete(context: Context, m: AiModel) { m.files.forEach { file(context, it).delete() } }
}

/**
 * Everything heavy the app needs — AI models and the built-in soundtracks — is downloaded once, on
 * first launch (it's not inside the APK, so the APK stays small), and kept in the app's private
 * storage: only AlcorCut Pro can use it, it survives app updates, and it's removed on uninstall.
 * Downloads resume where they stopped.
 */
object AppData {
    private val BUILT_IN = linkedMapOf(
        "DeepFilterNet3_onnx.tar.gz" to 7983136L,
        "deeplab.tflite" to 2779264L,
        "depth_anything_v2_small.onnx" to 99060839L,
        "esrgan.tflite" to 4993712L,
        "kokoro_af_bella.bin" to 522240L,
        "kokoro_af_heart.bin" to 522240L,
        "kokoro_af_nicole.bin" to 522240L,
        "kokoro_af_sarah.bin" to 522240L,
        "kokoro_am_fenrir.bin" to 522240L,
        "kokoro_am_michael.bin" to 522240L,
        "kokoro_am_puck.bin" to 522240L,
        "kokoro_bf_emma.bin" to 522240L,
        "kokoro_bf_isabella.bin" to 522240L,
        "kokoro_bm_fable.bin" to 522240L,
        "kokoro_bm_george.bin" to 522240L,
        "kokoro_bm_lewis.bin" to 522240L,
        "kokoro_v1_q8.onnx" to 92361116L,
        "lama_fp32.onnx" to 208044816L,
        "misaki_gb_gold.json" to 2838552L,
        "misaki_gb_silver.json" to 3663898L,
        "misaki_us_gold.json" to 3000469L,
        "misaki_us_silver.json" to 3099517L,
        "nafnet.tflite" to 62454048L,
        "sam_decoder.onnx" to 16492708L,
        "sam_encoder.onnx" to 27976484L,
        "sound_soundtracks_advisory.m4a" to 6330844L,
        "sound_soundtracks_and_we_run.m4a" to 4868896L,
        "sound_soundtracks_beyond_the_horizon.m4a" to 4817508L,
        "sound_soundtracks_black_eagle.m4a" to 5357258L,
        "sound_soundtracks_colorful_noise.m4a" to 5413774L,
        "sound_soundtracks_ethernal.m4a" to 3664162L,
        "sound_soundtracks_faithful_end_credits.m4a" to 3542068L,
        "sound_soundtracks_feather_fall.m4a" to 3520218L,
        "sound_soundtracks_heaven_encounters.m4a" to 1870206L,
        "sound_soundtracks_higher_again.m4a" to 4903137L,
        "sound_soundtracks_journey.m4a" to 2843652L,
        "sound_soundtracks_let_there_be.m4a" to 4170180L,
        "sound_soundtracks_lethal_weapons.m4a" to 2758098L,
        "sound_soundtracks_natures_roar.m4a" to 5047761L,
        "sound_soundtracks_natures_voice.m4a" to 2578142L,
        "sound_soundtracks_radioactive.m4a" to 2931646L,
        "sound_soundtracks_rings_of_saturn.m4a" to 2771133L,
        "sound_soundtracks_rise_like_the_dawn.m4a" to 3314955L,
        "sound_soundtracks_row.m4a" to 5210395L,
        "sound_soundtracks_skybound_dreams.m4a" to 3458018L,
        "sound_soundtracks_spring.m4a" to 3496524L,
        "sound_soundtracks_tales_of_the_brave.m4a" to 4005687L,
        "sound_soundtracks_the_flood.m4a" to 3193909L,
        "sound_soundtracks_the_haze.m4a" to 3219490L,
        "sound_soundtracks_there_back_again.m4a" to 3347868L,
        "sound_soundtracks_uprising.m4a" to 5313695L,
        "whisper_ggml-base-q5_1.bin" to 59707625L,
    )

    /** Release file name → size (bytes): what this version of the app needs (from the server's list when known). */
    @Volatile private var FILES: Map<String, Long> = BUILT_IN

    // ---- after an app update: a quick look at the server's list (one small file) for changed or new files

    private const val MANIFEST = "manifest.json"
    var checking by androidx.compose.runtime.mutableStateOf(false); private set

    private fun prefs(context: Context) = context.getSharedPreferences("procam", Context.MODE_PRIVATE)
    private fun appVersion(context: Context): Long = try {
        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
    } catch (_: Throwable) { 0L }

    /** The server's list as saved on the phone: name → (size, checksum). */
    private fun parse(text: String): Map<String, Pair<Long, String>> = try {
        val j = org.json.JSONObject(text)
        j.keys().asSequence().associateWith { k -> val o = j.getJSONObject(k); o.optLong("size") to o.optString("sha256") }
    } catch (_: Throwable) { emptyMap() }

    /** Loads the saved list (if any) so the files it names are the ones checked. Call at start-up. */
    fun init(context: Context) {
        val f = File(ModelStore.dir(context), MANIFEST)
        if (f.exists()) applyManifest(parse(f.readText()))
    }

    private fun applyManifest(m: Map<String, Pair<Long, String>>) {
        if (m.isEmpty()) return
        // Everything this app version knows about, with the server's sizes; plus new files the server lists for these kinds.
        val known = BUILT_IN.keys
        val kinds = listOf("sound_", "kokoro_", "misaki_", "sam_", "whisper_ggml-base")
        val out = LinkedHashMap<String, Long>()
        for (k in known) out[k] = m[k]?.first ?: BUILT_IN[k]!!
        for ((k, v) in m) if (k !in out && kinds.any { k.startsWith(it) } && (k.endsWith(".m4a") || k.endsWith(".bin") || k.endsWith(".json") || k.endsWith(".onnx"))) out[k] = v.first
        FILES = out
    }

    /**
     * Only on the first start after an app update (blocking, a second or two): fetches the server's
     * small file list and compares it with the one saved last time. Changed files are removed so they
     * download again; nothing else is downloaded. Returns true if something needs downloading.
     */
    fun checkAfterUpdate(context: Context): Boolean {
        val ver = appVersion(context)
        if (prefs(context).getLong("data_checked_version", -1L) == ver) return needed(context)
        main.post { checking = true }
        try {
            val text = try {
                var url = URL(ModelStore.BASE + MANIFEST)
                var hops = 0
                var body: String? = null
                while (body == null) {
                    val c = url.openConnection() as HttpURLConnection
                    c.instanceFollowRedirects = false; c.connectTimeout = 4000; c.readTimeout = 4000
                    val code = c.responseCode
                    if (code in 300..399 && hops < 8) { url = URL(url, c.getHeaderField("Location")); c.disconnect(); hops++; continue }
                    if (code != 200) return needed(context)
                    body = c.inputStream.bufferedReader().use { it.readText() }
                }
                body
            } catch (_: Throwable) { return needed(context) }   // offline: check again next start
            val fresh = parse(text)
            if (fresh.isEmpty()) return needed(context)
            val saved = File(ModelStore.dir(context), MANIFEST)
            val old = if (saved.exists()) parse(saved.readText()) else emptyMap()
            applyManifest(fresh)
            // A file whose checksum (or size) changed on the server is removed, so it downloads again.
            for ((k, v) in fresh) {
                val o = old[k]
                val changed = if (o != null) (o.second.isNotEmpty() && v.second.isNotEmpty() && o.second != v.second) || o.first != v.first
                else { val f = local(context, k); f.exists() && f.length() != v.first }
                if (changed && k in FILES) { local(context, k).delete(); File(local(context, k).path + ".part").delete() }
            }
            saved.writeText(text)
            prefs(context).edit().putLong("data_checked_version", ver).apply()
            return needed(context)
        } finally { main.post { checking = false } }
    }

    /** Where a downloaded file lives on the phone. */
    fun local(context: Context, remote: String): File = when {
        remote.startsWith("sound_soundtracks_") -> File(File(context.filesDir, "sound_library/soundtracks").apply { mkdirs() }, remote.removePrefix("sound_soundtracks_"))
        remote.startsWith("whisper_") -> File(File(context.filesDir, "whisper").apply { mkdirs() }, remote.removePrefix("whisper_"))
        else -> ModelStore.file(context, remote)
    }

    /** A model in the models folder (by its plain name, e.g. "nafnet.tflite"). */
    fun file(context: Context, name: String) = ModelStore.file(context, name)
    fun has(context: Context, name: String): Boolean = complete(context, name)

    private fun complete(context: Context, remote: String): Boolean {
        val f = local(context, remote)
        val want = FILES[remote] ?: return f.exists() && f.length() > 0
        return f.exists() && f.length() == want
    }

    fun missing(context: Context) = FILES.keys.filter { !complete(context, it) }
    fun percent(): Int = (doneBytes * 100 / totalBytes().coerceAtLeast(1)).toInt().coerceIn(0, 100)
    fun totalBytes(): Long = FILES.values.sum()
    fun needed(context: Context) = missing(context).isNotEmpty()

    // ---- state for the first-launch screen
    var running by androidx.compose.runtime.mutableStateOf(false); private set
    var doneBytes by androidx.compose.runtime.mutableLongStateOf(0L); private set
    var current by androidx.compose.runtime.mutableStateOf(""); private set
    var error by androidx.compose.runtime.mutableStateOf<String?>(null); private set
    var finished by androidx.compose.runtime.mutableStateOf(false); private set
    /** The person chose to use the app while it downloads (the small badge shows instead of the screen). */
    var background by androidx.compose.runtime.mutableStateOf(false)
    /** Waiting for Wi-Fi (on mobile data and the person chose to wait). */
    var waitingForWifi by androidx.compose.runtime.mutableStateOf(false)

    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    @Volatile private var thread: Thread? = null

    fun label(remote: String): String = when {
        remote.startsWith("sound_soundtracks_") -> "Soundtracks"
        remote.startsWith("whisper_") -> "Speech to text"
        remote.startsWith("sam_") -> "Magic Mask"
        remote.startsWith("nafnet") -> "AI noise reduction"
        remote.startsWith("esrgan") -> "AI upscale"
        remote.startsWith("deeplab") -> "AI subject masks"
        remote.startsWith("DeepFilterNet") -> "Studio voice"
        remote.startsWith("depth") -> "AI depth"
        remote.startsWith("lama") -> "Object removal"
        remote.startsWith("kokoro") || remote.startsWith("misaki") -> "AI voiceover"
        else -> remote
    }

    /** True on mobile data (or another network that may cost money). */
    fun metered(context: Context): Boolean = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        cm.isActiveNetworkMetered
    } catch (_: Throwable) { false }

    fun online(context: Context): Boolean = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val n = cm.activeNetwork; val c = cm.getNetworkCapabilities(n)
        c != null && c.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (_: Throwable) { true }

    /** Starts (or resumes) the download in the background. */
    @Synchronized fun start(context: Context) {
        if (thread?.isAlive == true) return
        val app = context.applicationContext
        error = null; running = true; waitingForWifi = false
        thread = Thread({ run(app) }, "app-data").apply { isDaemon = true; start() }
    }

    private fun post(f: () -> Unit) = main.post(f)

    private fun run(context: Context) {
        // Small things first (soundtracks and most tools are ready quickly), the biggest models last.
        val todo = missing(context).sortedBy { FILES[it] ?: 0L }
        var done = FILES.filterKeys { it !in todo }.values.sum()
        // Partly downloaded files count too.
        for (r in todo) { val p = File(local(context, r).path + ".part"); if (p.exists()) done += p.length() }
        post { doneBytes = done }
        for (r in todo) {
            post { current = label(r) }
            var tries = 0
            while (true) {
                val err = fetch(context, r) { n -> done += n; val d = done; post { doneBytes = d } }
                if (err == null) break
                tries++
                // Recount from the files themselves after a failure.
                done = FILES.keys.sumOf { k -> if (complete(context, k)) FILES[k]!! else File(local(context, k).path + ".part").let { if (it.exists()) it.length() else 0L } }
                if (tries >= 4) { post { error = err; running = false }; return }
                Thread.sleep(2000L * tries)
            }
        }
        VoiceIsolator.prepare(context)
        post { running = false; finished = true; doneBytes = totalBytes(); current = "" }
    }

    /** Downloads one file, carrying on from a partial download. Null = done. */
    private fun fetch(context: Context, remote: String, onBytes: (Long) -> Unit): String? {
        val target = local(context, remote)
        val part = File(target.path + ".part")
        val want = FILES[remote] ?: -1L
        return try {
            var have = if (part.exists()) part.length() else 0L
            if (want > 0 && have > want) { part.delete(); have = 0L }
            var url = java.net.URL(ModelStore.BASE + remote)
            var conn: java.net.HttpURLConnection
            var hops = 0
            while (true) {
                conn = url.openConnection() as java.net.HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 20_000; conn.readTimeout = 60_000
                if (have > 0) conn.setRequestProperty("Range", "bytes=$have-")
                val code = conn.responseCode
                if (code in 300..399 && hops < 8) { url = java.net.URL(url, conn.getHeaderField("Location")); conn.disconnect(); hops++; continue }
                if (code == 416 && want > 0 && have == want) break
                if (code != 200 && code != 206) return "Download failed (HTTP $code)"
                if (code == 200 && have > 0) { onBytes(-have); have = 0L; part.delete() }   // the server sent it all again
                break
            }
            if (!(want > 0 && have == want)) {
                conn.inputStream.use { ins ->
                    java.io.FileOutputStream(part, have > 0).buffered(1 shl 16).use { out ->
                        val buf = ByteArray(1 shl 16)
                        while (true) {
                            val n = ins.read(buf); if (n < 0) break
                            out.write(buf, 0, n); onBytes(n.toLong())
                        }
                    }
                }
            }
            if (want > 0 && part.length() != want) return "Download incomplete"
            target.delete()
            if (!part.renameTo(target)) return "Couldn't save $remote"
            null
        } catch (t: Throwable) { "Download failed: ${t.message ?: "no connection?"}" }
    }
}
