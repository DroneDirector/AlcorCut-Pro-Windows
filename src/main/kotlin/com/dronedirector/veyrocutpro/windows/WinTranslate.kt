package com.dronedirector.veyrocutpro.windows

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.google.android.gms.tasks.Task
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.LongBuffer

/**
 * Caption translation on Windows, offline on the PC: OPUS-MT translation models (University of
 * Helsinki, CC-BY 4.0 / Apache 2.0) run with ONNX Runtime. Every language goes through English, so
 * each language needs its two models (into and out of English), downloaded once from the app's own
 * release "translate-models-v1". Language finding uses common words per language. Own code.
 */
object WinTranslate {
    private const val BASE = "https://github.com/DroneDirector/AlcorCut-Pro-Windows/releases/download/translate-models-v1/"

    private val words = mapOf(
        "en" to setOf("the", "and", "is", "to", "of", "you", "that", "it", "in", "this"),
        "da" to setOf("og", "det", "er", "jeg", "ikke", "en", "til", "på", "med", "har"),
        "de" to setOf("und", "der", "die", "das", "ist", "nicht", "ich", "zu", "mit", "ein"),
        "es" to setOf("el", "la", "de", "que", "y", "en", "es", "los", "por", "un"),
        "fr" to setOf("le", "la", "les", "et", "est", "de", "un", "une", "pas", "que"),
        "it" to setOf("il", "di", "che", "e", "la", "non", "un", "per", "sono", "una"),
        "nl" to setOf("de", "het", "een", "en", "is", "niet", "van", "ik", "dat", "op"),
        "sv" to setOf("och", "det", "att", "är", "jag", "inte", "en", "på", "som", "med"),
        "no" to setOf("og", "det", "er", "jeg", "ikke", "en", "til", "på", "som", "med"),
        "pt" to setOf("o", "a", "de", "que", "e", "não", "um", "uma", "para", "com"),
        "pl" to setOf("i", "w", "nie", "się", "na", "to", "że", "jest", "z", "do"),
        "fi" to setOf("ja", "on", "ei", "se", "että", "oli", "hän", "mutta", "kun", "minä"),
        "is" to setOf("og", "að", "er", "ekki", "ég", "það", "við", "sem", "á", "með"),
    )

    fun identify(text: String): String {
        when {
            text.any { it in '぀'..'ヿ' } -> return "ja"
            text.any { it in '가'..'힯' } -> return "ko"
            text.any { it in '一'..'鿿' } -> return "zh"
            text.any { it in '؀'..'ۿ' } -> return "ar"
            text.any { it in 'ऀ'..'ॿ' } -> return "hi"
            text.any { it in 'Ѐ'..'ӿ' } -> return "uk"
        }
        val t = text.lowercase().split(Regex("[^\\p{L}]+")).filter { it.isNotBlank() }
        if (t.isEmpty()) return "und"
        val best = words.maxByOrNull { (_, w) -> t.count { it in w } } ?: return "und"
        return if (t.count { it in best.value } >= 2) best.key else "und"
    }

    // ---------------------------------------------------------------- models

    private fun dir(): File = File(android.content.Context.app.filesDir, "models/translate").apply { mkdirs() }

    private val loaded = HashMap<String, Marian>()

    private fun steps(from: String, to: String): List<String> = when {
        from == to -> emptyList()
        from == "en" -> listOf("en-$to")
        to == "en" -> listOf("$from-en")
        else -> listOf("$from-en", "en-$to")
    }

    /** Downloads (once) and opens the models for [from] → [to]. */
    fun prepare(from: String, to: String): Task<Void?> = try {
        for (s in steps(from, to)) model(s)
        Task(null, null)
    } catch (e: Exception) { Task(null, e) }

    fun translate(from: String, to: String, text: String): Task<String> = try {
        var t = text
        for (s in steps(from, to)) t = model(s).translate(t)
        Task(t, null)
    } catch (e: Exception) { Task(null, e) }

    @Synchronized private fun model(name: String): Marian {
        loaded[name]?.let { return it }
        val d = File(dir(), name)
        if (!File(d, "model.json").exists()) fetch(name, d)
        return Marian(d).also { loaded[name] = it }
    }

    private fun fetch(name: String, d: File) {
        val zip = File(dir(), "opus-mt-$name.zip")
        val part = File(zip.path + ".part")
        var url = URL(BASE + "opus-mt-$name.zip")
        var conn: HttpURLConnection
        var hops = 0
        while (true) {
            conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 20_000; conn.readTimeout = 60_000
            val code = conn.responseCode
            if (code in 300..399 && hops++ < 8) { url = URL(url, conn.getHeaderField("Location")); conn.disconnect(); continue }
            if (code == 404) throw Exception("Captions can't be translated between these languages on the PC yet")
            if (code != 200) throw Exception("The language pack didn't download (HTTP $code)")
            break
        }
        conn.inputStream.use { ins -> part.outputStream().buffered(1 shl 16).use { ins.copyTo(it, 1 shl 16) } }
        val tmp = File(dir(), "$name.tmp"); tmp.deleteRecursively(); tmp.mkdirs()
        java.util.zip.ZipFile(part).use { z ->
            for (e in z.entries()) {
                if (e.isDirectory || e.name.contains("..") || e.name.contains('/')) continue
                z.getInputStream(e).use { i -> File(tmp, e.name).outputStream().use { o -> i.copyTo(o) } }
            }
        }
        part.delete()
        d.deleteRecursively()
        if (!tmp.renameTo(d)) throw Exception("Couldn't keep the language pack")
    }

    // ---------------------------------------------------------------- one model

    /** One OPUS-MT model: SentencePiece pieces in, greedy decoding out. */
    private class Marian(d: File) {
        private val env = OrtEnvironment.getEnvironment()
        private val opts = OrtSession.SessionOptions().apply { setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT) }
        private val enc = env.createSession(File(d, "encoder_model.onnx").path, opts)
        private val dec = env.createSession(File(d, "decoder_model.onnx").path, opts)
        private val src = SentencePiece(File(d, "source.spm"))
        private val vocab: Map<String, Int>
        private val back: Array<String>
        private val cfg = com.google.gson.JsonParser.parseString(File(d, "model.json").readText()).asJsonObject
        private val eos = cfg.get("eos").asInt
        private val pad = cfg.get("pad").asInt
        private val start = cfg.get("decoder_start").asInt
        private val unk = cfg.get("unk").asInt
        private val prefix = cfg.get("prefix")?.asString.orEmpty()

        init {
            val o = com.google.gson.JsonParser.parseString(File(d, "vocab.json").readText()).asJsonObject
            vocab = o.entrySet().associate { it.key to it.value.asInt }
            back = Array((vocab.values.maxOrNull() ?: 0) + 1) { "" }
            for ((k, v) in vocab) back[v] = k
        }

        fun translate(text: String): String {
            val s = text.trim()
            if (s.isEmpty()) return text
            // Long captions: by sentence, so nothing is cut off.
            val parts = s.split(Regex("(?<=[.!?。！？])\\s+")).filter { it.isNotBlank() }
            return parts.joinToString(" ") { one(it) }
        }

        @Synchronized private fun one(text: String): String {
            val ids = ArrayList<Long>()
            if (prefix.isNotEmpty()) vocab[prefix]?.let { ids += it.toLong() }
            for (p in src.encode(text)) ids += (vocab[p] ?: unk).toLong()
            ids += eos.toLong()
            val n = ids.size
            val mask = LongArray(n) { 1L }
            OnnxTensor.createTensor(env, LongBuffer.wrap(ids.toLongArray()), longArrayOf(1, n.toLong())).use { inIds ->
            OnnxTensor.createTensor(env, LongBuffer.wrap(mask), longArrayOf(1, n.toLong())).use { inMask ->
                enc.run(mapOf("input_ids" to inIds, "attention_mask" to inMask)).use { er ->
                    val hidden = er[0] as OnnxTensor
                    val out = arrayListOf(start.toLong())
                    val limit = minOf(cfg.get("max_length")?.asInt ?: 512, n * 3 + 16)
                    while (out.size < limit) {
                        val t = OnnxTensor.createTensor(env, LongBuffer.wrap(out.toLongArray()), longArrayOf(1, out.size.toLong()))
                        val next = t.use {
                            dec.run(mapOf("input_ids" to t, "encoder_hidden_states" to hidden, "encoder_attention_mask" to inMask)).use { dr ->
                                val logits = (dr[0] as OnnxTensor).floatBuffer
                                val v = logits.capacity() / out.size
                                val base = (out.size - 1) * v
                                var best = -1; var bv = Float.NEGATIVE_INFINITY
                                for (k in 0 until v) {
                                    if (k == pad) continue
                                    val x = logits.get(base + k)
                                    if (x > bv) { bv = x; best = k }
                                }
                                best
                            }
                        }
                        if (next == eos || next < 0) break
                        out += next.toLong()
                    }
                    val sb = StringBuilder()
                    for (i in 1 until out.size) {
                        val id = out[i].toInt()
                        if (id == unk || id == pad || id == eos) continue
                        val p = back.getOrElse(id) { "" }
                        if (p.startsWith(">>") && p.endsWith("<<")) continue
                        sb.append(p)
                    }
                    return sb.toString().replace('▁', ' ').trim().replace(Regex(" +"), " ")
                }
            }
            }
        }
    }

    // ---------------------------------------------------------------- SentencePiece (unigram)

    /** Reads a SentencePiece model file and splits text into its pieces (the most likely split). */
    private class SentencePiece(f: File) {
        private val score = HashMap<String, Float>()
        private var maxLen = 1
        private var unkScore = -10f

        init {
            val b = f.readBytes()
            var i = 0
            var stop = false
            fun varint(): Long { var r = 0L; var s = 0; while (true) { val x = b[i++].toInt() and 0xFF; r = r or ((x and 0x7F).toLong() shl s); if (x < 0x80) return r; s += 7 } }
            while (i < b.size && !stop) {
                val key = varint().toInt(); val field = key ushr 3; val wire = key and 7
                when (wire) {
                    0 -> varint()
                    1 -> i += 8
                    5 -> i += 4
                    2 -> {
                        val len = varint().toInt(); val end = i + len
                        if (field == 1) {                       // one piece: text, score, type
                            var piece = ""; var sc = 0f; var type = 1
                            while (i < end) {
                                val k = varint().toInt(); val fl = k ushr 3; val w = k and 7
                                when {
                                    fl == 1 && w == 2 -> { val l = varint().toInt(); piece = String(b, i, l, Charsets.UTF_8); i += l }
                                    fl == 2 && w == 5 -> { sc = java.lang.Float.intBitsToFloat((b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8) or ((b[i + 2].toInt() and 0xFF) shl 16) or ((b[i + 3].toInt() and 0xFF) shl 24)); i += 4 }
                                    fl == 3 && w == 0 -> type = varint().toInt()
                                    w == 0 -> varint()
                                    w == 2 -> { val l = varint().toInt(); i += l }
                                    w == 5 -> i += 4
                                    w == 1 -> i += 8
                                }
                            }
                            // 1 normal, 4 user defined; unknown / control pieces aren't matched in text.
                            if (type == 1 || type == 4) { score[piece] = if (type == 4) 0f else sc; maxLen = maxOf(maxLen, piece.length) }
                            i = end
                        } else i = end
                    }
                    else -> stop = true
                }
            }
            unkScore = (score.values.minOrNull() ?: 0f) - 10f
        }

        fun encode(text: String): List<String> {
            val norm = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKC).trim().replace(Regex("\\s+"), " ")
            val s = "▁" + norm.replace(' ', '▁')
            val n = s.length
            val best = FloatArray(n + 1) { Float.NEGATIVE_INFINITY }; best[0] = 0f
            val from = IntArray(n + 1)
            for (e in 1..n) {
                for (l in 1..minOf(maxLen, e)) {
                    val st = e - l
                    if (best[st] == Float.NEGATIVE_INFINITY) continue
                    val p = s.substring(st, e)
                    val sc = score[p] ?: if (l == 1) unkScore else continue
                    val v = best[st] + sc
                    if (v > best[e]) { best[e] = v; from[e] = st }
                }
            }
            val out = ArrayList<String>()
            var e = n
            while (e > 0) { val st = from[e]; out += s.substring(st, e); e = st }
            out.reverse()
            return out
        }
    }
}
