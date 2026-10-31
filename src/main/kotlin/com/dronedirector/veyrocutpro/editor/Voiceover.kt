package com.dronedirector.veyrocutpro.editor

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * AI voiceover from text, on the phone: Kokoro 82M (Apache 2.0) speaks phonemes; the words are turned
 * into phonemes with misaki's pronunciation dictionaries (Apache 2.0) plus AlcorCut Pro's own rules
 * for numbers, word endings and unknown words (no GPL espeak). English (American and British voices).
 */
object Voiceover {
    data class Voice(val id: String, val label: String, val british: Boolean)
    val voices = listOf(
        Voice("af_heart", "Heart (US, woman)", false), Voice("af_bella", "Bella (US, woman)", false), Voice("af_nicole", "Nicole (US, woman, soft)", false),
        Voice("af_sarah", "Sarah (US, woman)", false), Voice("am_michael", "Michael (US, man)", false), Voice("am_fenrir", "Fenrir (US, man, deep)", false),
        Voice("am_puck", "Puck (US, man, lively)", false), Voice("bf_emma", "Emma (UK, woman)", true), Voice("bf_isabella", "Isabella (UK, woman)", true),
        Voice("bm_george", "George (UK, man)", true), Voice("bm_fable", "Fable (UK, man)", true), Voice("bm_lewis", "Lewis (UK, man)", true),
    )
    const val RATE = 24000

    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    @Volatile var failed: String? = null; private set

    fun voiceFile(context: Context, v: Voice) = ModelStore.file(context, "kokoro_${v.id}.bin")

    // ---- Voice preview: a short sample spoken when a voice is tapped (made once per voice, then instant).
    private val samples = HashMap<String, FloatArray>()
    private var player: android.media.AudioTrack? = null
    var previewing by androidx.compose.runtime.mutableStateOf<String?>(null)

    /** Speaks a short sample in [v] (downloads the voice the first time). Off the main thread. Returns an error or null. */
    fun preview(context: Context, v: Voice, text: String?, stop: () -> Boolean): String? {
        previewing = v.id
        try {
            val name = v.label.substringBefore(' ')
            val line = text?.trim()?.takeIf { it.isNotEmpty() }?.let { t -> splitSentences(t).firstOrNull()?.take(120) } ?: "Hi, I'm $name. This is how I sound in your video."
            val key = v.id + "|" + line
            val a = samples[key] ?: run {
                ensure(context, v, {}, stop)?.let { return it }
                speak(context, line, v, 1f, {}, stop)?.also { if (samples.size > 24) samples.clear(); samples[key] = it }
            } ?: return failed ?: "the voice couldn't be made"
            if (stop()) return null
            playPcm(a)
            return null
        } finally { previewing = null }
    }

    @Synchronized private fun playPcm(a: FloatArray) {
        try { player?.stop(); player?.release() } catch (_: Throwable) {}
        val pcm = ShortArray(a.size) { (a[it].coerceIn(-1f, 1f) * 32767f).toInt().toShort() }
        val t = android.media.AudioTrack.Builder()
            .setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(android.media.AudioFormat.Builder().setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(android.media.AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size * 2).build()
        t.write(pcm, 0, pcm.size); t.play()
        player = t
    }

    fun stopPreview() { try { player?.stop() } catch (_: Throwable) {} }

    /** Downloads the model, dictionaries and [v] (blocking). Null = ready. */
    fun ensure(context: Context, v: Voice, onProgress: (Int) -> Unit, cancelled: () -> Boolean): String? {
        ModelStore.ensure(context, AiModel.VOICE, onProgress, cancelled)?.let { return it }
        return ModelStore.fetch(context, "kokoro_${v.id}.bin", { _, _ -> }, cancelled)
    }

    @Synchronized private fun ready(context: Context): Boolean {
        if (session != null) return true
        return try {
            val e = OrtEnvironment.getEnvironment()
            val o = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(2, 6))
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            session = e.createSession(ModelStore.file(context, AiModel.VOICE.files[0]).path, o)
            env = e
            true
        } catch (t: Throwable) { failed = t.message ?: t.javaClass.simpleName; false }
    }

    // ---------------------------------------------------------------- phoneme vocabulary (Kokoro's tokenizer)

    private const val VOCAB = "\$;:,.!?—…\"()“” ̃ʣʥʦʨᵝꭧAIOQSTWYᵊabcdefhijklmnopqrstuvwxyzɑɐɒæβɔɕçɖðʤəɚɛɜɟɡɥɨɪʝɯɰŋɳɲɴøɸθœɹɾɻʁɽʂʃʈʧʊʋʌɣɤχʎʒʔˈˌːʰʲ↓→↗↘ᵻ"
    private var ids: Map<Char, Long>? = null

    /** Kokoro's ids, read from its tokenizer (bundled in the app's assets). */
    private fun vocab(context: Context): Map<Char, Long> = ids ?: run {
        val m = HashMap<Char, Long>()
        try {
            val j = JSONObject(context.assets.open("kokoro_tokenizer.json").bufferedReader().use { it.readText() }).getJSONObject("model").getJSONObject("vocab")
            for (k in j.keys()) if (k.length == 1) m[k[0]] = j.getLong(k)
        } catch (_: Throwable) {}
        if (m.isEmpty()) VOCAB.forEachIndexed { i, c -> m[c] = i.toLong() }
        ids = m; m
    }

    // ---------------------------------------------------------------- speech

    /**
     * Speaks [text] with voice [v] at [speed] (blocking; slow on long texts). Returns 24 kHz mono
     * samples, or null. Long texts are spoken sentence by sentence, with a natural pause between.
     */
    fun speak(context: Context, text: String, v: Voice, speed: Float, onProgress: (Int) -> Unit, stop: () -> Boolean): FloatArray? {
        if (!ready(context)) return null
        val style = loadVoice(voiceFile(context, v)) ?: run { failed = "voice missing"; return null }
        val g2p = G2P.get(context, v.british)
        val vocab = vocab(context)
        val sentences = splitSentences(text)
        val out = ArrayList<FloatArray>()
        for ((i, s) in sentences.withIndex()) {
            if (stop()) return null
            val ph = g2p.phonemize(s)
            for (chunk in chunkPhonemes(ph, 500)) {
                val toks = chunk.mapNotNull { vocab[it] }
                if (toks.isEmpty()) continue
                val audio = run(toks, style, speed) ?: return null
                out += trimSilence(audio)
            }
            // A short breath between sentences.
            out += FloatArray((RATE * (if (s.trimEnd().endsWith("?") || s.trimEnd().endsWith("!") || s.trimEnd().endsWith(".")) 0.32f else 0.18f) / speed.coerceIn(0.5f, 2f)).toInt())
            onProgress(((i + 1) * 100 / sentences.size).coerceIn(0, 100))
        }
        val n = out.sumOf { it.size }
        val all = FloatArray(n); var p = 0
        for (a in out) { a.copyInto(all, p); p += a.size }
        return all
    }

    private fun run(toks: List<Long>, voice: FloatArray, speed: Float): FloatArray? {
        val e = env ?: return null; val s = session ?: return null
        val seq = LongArray(toks.size + 2); for (i in toks.indices) seq[i + 1] = toks[i]
        // The voice's style for this length of text.
        val row = toks.size.coerceIn(0, voice.size / 256 - 1)
        val st = FloatArray(256) { voice[row * 256 + it] }
        return try {
            val inputs = HashMap<String, OnnxTensor>()
            val names = s.inputNames.toList()
            inputs[names.firstOrNull { it.contains("input") } ?: names[0]] = OnnxTensor.createTensor(e, LongBuffer.wrap(seq), longArrayOf(1, seq.size.toLong()))
            inputs[names.firstOrNull { it.contains("style") } ?: names[1]] = OnnxTensor.createTensor(e, FloatBuffer.wrap(st), longArrayOf(1, 256))
            inputs[names.firstOrNull { it.contains("speed") } ?: names[2]] = OnnxTensor.createTensor(e, FloatBuffer.wrap(floatArrayOf(speed.coerceIn(0.5f, 2f))), longArrayOf(1))
            try {
                s.run(inputs).use { r ->
                    val fb = (r[0] as OnnxTensor).floatBuffer
                    FloatArray(fb.remaining()).also { fb.get(it) }
                }
            } finally { inputs.values.forEach { it.close() } }
        } catch (t: Throwable) { failed = t.message; null }
    }

    private fun loadVoice(f: File): FloatArray? = try {
        val b = f.readBytes()
        val fb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        FloatArray(fb.remaining()).also { fb.get(it) }
    } catch (_: Throwable) { null }

    private fun trimSilence(a: FloatArray): FloatArray {
        var s = 0; var e = a.size
        while (s < e && kotlin.math.abs(a[s]) < 0.004f) s++
        while (e > s && kotlin.math.abs(a[e - 1]) < 0.004f) e--
        s = (s - RATE / 50).coerceAtLeast(0); e = (e + RATE / 25).coerceAtMost(a.size)
        return a.copyOfRange(s, e)
    }

    private fun splitSentences(t: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        for ((i, ch) in t.withIndex()) {
            sb.append(ch)
            val next = t.getOrNull(i + 1)
            if ((ch == '.' || ch == '!' || ch == '?' || ch == '\n') && (next == null || next.isWhitespace())) {
                if (sb.isNotBlank()) out += sb.toString().trim(); sb.clear()
            }
        }
        if (sb.isNotBlank()) out += sb.toString().trim()
        return out
    }

    /** Splits phonemes at spaces so each chunk fits the model (510 tokens). */
    private fun chunkPhonemes(p: String, max: Int): List<String> {
        if (p.length <= max) return listOf(p)
        val out = ArrayList<String>(); var cur = StringBuilder()
        for (w in p.split(' ')) {
            if (cur.length + w.length + 1 > max && cur.isNotEmpty()) { out += cur.toString(); cur = StringBuilder() }
            if (cur.isNotEmpty()) cur.append(' ')
            cur.append(w)
        }
        if (cur.isNotEmpty()) out += cur.toString()
        return out
    }

    /** Writes 24 kHz mono samples as a 16-bit WAV file. */
    fun writeWav(f: File, a: FloatArray) {
        val data = ByteBuffer.allocate(a.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (v in a) data.putShort((v.coerceIn(-1f, 0.99997f) * 32768f).toInt().toShort())
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()); h.putInt(36 + a.size * 2); h.put("WAVE".toByteArray())
        h.put("fmt ".toByteArray()); h.putInt(16); h.putShort(1); h.putShort(1); h.putInt(RATE); h.putInt(RATE * 2); h.putShort(2); h.putShort(16)
        h.put("data".toByteArray()); h.putInt(a.size * 2)
        f.outputStream().use { it.write(h.array()); it.write(data.array()) }
    }
}

/**
 * English text → Kokoro phonemes (misaki's phoneme set). Dictionary first (misaki gold, then silver),
 * then word-ending rules on known stems, then own letter-to-sound rules. Numbers, money, percentages,
 * times, ordinals and common abbreviations are written out first.
 */
class G2P private constructor(private val gold: Map<String, String>, private val silver: Map<String, String>, private val british: Boolean) {
    companion object {
        private var us: G2P? = null
        private var gb: G2P? = null
        @Synchronized fun get(context: Context, british: Boolean): G2P {
            (if (british) gb else us)?.let { return it }
            val p = if (british) "gb" else "us"
            val g = G2P(load(ModelStore.file(context, "misaki_${p}_gold.json")), load(ModelStore.file(context, "misaki_${p}_silver.json")), british)
            if (british) gb = g else us = g
            return g
        }

        private fun load(f: File): Map<String, String> {
            val m = HashMap<String, String>(200_000)
            try {
                val j = JSONObject(f.readText())
                for (k in j.keys()) {
                    val v = j.get(k)
                    val s = when (v) {
                        is String -> v
                        is JSONObject -> v.optString("DEFAULT").ifEmpty { v.optString("None").ifEmpty { v.keys().asSequence().firstOrNull()?.let { v.optString(it) } ?: "" } }
                        else -> ""
                    }
                    if (s.isNotEmpty()) m[k] = s
                }
            } catch (_: Throwable) {}
            return m
        }

        private val ONES = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen")
        private val TENS = listOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")
        private val ORD = mapOf("one" to "first", "two" to "second", "three" to "third", "five" to "fifth", "eight" to "eighth", "nine" to "ninth", "twelve" to "twelfth")
        private val ABBR = mapOf("mr" to "mister", "mrs" to "missus", "ms" to "miz", "dr" to "doctor", "st" to "saint", "jr" to "junior", "sr" to "senior", "vs" to "versus", "etc" to "et cetera", "approx" to "approximately", "no" to "number")

        fun numberWords(n: Long): String {
            if (n < 0) return "minus " + numberWords(-n)
            if (n < 20) return ONES[n.toInt()]
            if (n < 100) return TENS[(n / 10).toInt()] + if (n % 10 != 0L) " " + ONES[(n % 10).toInt()] else ""
            if (n < 1000) return ONES[(n / 100).toInt()] + " hundred" + if (n % 100 != 0L) " and " + numberWords(n % 100) else ""
            for ((v, w) in listOf(1_000_000_000_000L to "trillion", 1_000_000_000L to "billion", 1_000_000L to "million", 1000L to "thousand")) {
                if (n >= v) return numberWords(n / v) + " $w" + if (n % v != 0L) (if (n % v < 100) " and " else " ") + numberWords(n % v) else ""
            }
            return n.toString()
        }

        fun ordinalWords(n: Long): String {
            val w = numberWords(n)
            val parts = w.split(' ').toMutableList()
            val last = parts.last()
            parts[parts.lastIndex] = ORD[last] ?: if (last.endsWith("y")) last.dropLast(1) + "ieth" else last + "th"
            return parts.joinToString(" ")
        }

        /** "1999" as a year: "nineteen ninety-nine". */
        fun yearWords(n: Int): String {
            if (n in 2000..2009) return numberWords(n.toLong())
            val a = n / 100; val b = n % 100
            return numberWords(a.toLong()) + " " + when { b == 0 -> "hundred"; b < 10 -> "oh " + numberWords(b.toLong()); else -> numberWords(b.toLong()) }
        }
    }

    /** Writes out numbers, money, %, times, ordinals and abbreviations. */
    fun normalize(t0: String): String {
        var t = t0.replace('’', '\'').replace('‘', '\'').replace("&", " and ")
        t = Regex("""\$(\d+(?:[.,]\d{1,2})?)""").replace(t) { m ->
            val p = m.groupValues[1].replace(',', '.').split('.')
            val d = p[0].toLong(); val c = p.getOrNull(1)?.padEnd(2, '0')?.toLong() ?: 0
            numberWords(d) + (if (d == 1L) " dollar" else " dollars") + if (c > 0) " and " + numberWords(c) + (if (c == 1L) " cent" else " cents") else ""
        }
        t = Regex("""(\d+)%""").replace(t) { numberWords(it.groupValues[1].toLong()) + " percent" }
        t = Regex("""\b(\d{1,2}):(\d{2})\b""").replace(t) { m ->
            val h = m.groupValues[1].toLong(); val mi = m.groupValues[2].toLong()
            numberWords(h) + " " + when { mi == 0L -> "o'clock"; mi < 10 -> "oh " + numberWords(mi); else -> numberWords(mi) }
        }
        t = Regex("""\b(\d+)(st|nd|rd|th)\b""").replace(t) { ordinalWords(it.groupValues[1].toLong()) }
        t = Regex("""\b(1[1-9]\d\d|20\d\d)\b""").replace(t) { yearWords(it.value.toInt()) }
        t = Regex("""\b(\d{1,3}(?:,\d{3})+|\d+)(?:\.(\d+))?\b""").replace(t) { m ->
            val whole = m.groupValues[1].replace(",", "").toLongOrNull() ?: return@replace m.value
            numberWords(whole) + (m.groupValues[2].takeIf { it.isNotEmpty() }?.let { " point " + it.map { d -> ONES[d - '0'] }.joinToString(" ") } ?: "")
        }
        t = Regex("""\b([A-Za-z]{1,6})\.(?=\s)""").replace(t) { m -> ABBR[m.groupValues[1].lowercase()] ?: m.value }
        return t
    }

    private fun look(w: String): String? {
        gold[w]?.let { return it }
        val lw = w.lowercase()
        gold[lw]?.let { return it }
        val cap = lw.replaceFirstChar { it.uppercase() }
        gold[cap]?.let { return it }
        silver[w]?.let { return it }
        silver[lw]?.let { return it }
        silver[cap]?.let { return it }
        return null
    }

    /** Known stem + ending (plural, past tense, -ing, -'s, -ly, -er …). */
    private fun stem(w: String): String? {
        val lw = w.lowercase()
        fun voicedEnd(p: String) = p.lastOrNull()?.let { it !in "ptkfθsʃʧ" } ?: true
        if (lw.endsWith("'s")) look(lw.dropLast(2))?.let { p -> return p + if (p.last() in "szʃʒʧʤ") "ɪz" else if (voicedEnd(p)) "z" else "s" }
        if (lw.endsWith("s") && lw.length > 3) {
            (look(lw.dropLast(1)))?.let { p -> return p + if (p.last() in "szʃʒʧʤ") "ɪz" else if (voicedEnd(p)) "z" else "s" }
            if (lw.endsWith("es")) look(lw.dropLast(2))?.let { p -> return p + "ɪz" }
            if (lw.endsWith("ies")) look(lw.dropLast(3) + "y")?.let { p -> return p.dropLast(1) + "iz" }
        }
        if (lw.endsWith("ed") && lw.length > 4) {
            val base = look(lw.dropLast(2)) ?: look(lw.dropLast(1)) ?: (if (lw.length > 5 && lw[lw.length - 3] == lw[lw.length - 4]) look(lw.dropLast(3)) else null)
            if (base != null) return base + when { base.last() in "td" -> "ɪd"; voicedEnd(base) -> "d"; else -> "t" }
        }
        if (lw.endsWith("ing") && lw.length > 5) {
            val base = look(lw.dropLast(3)) ?: look(lw.dropLast(3) + "e") ?: (if (lw[lw.length - 4] == lw[lw.length - 5]) look(lw.dropLast(4)) else null)
            if (base != null) return base + "ɪŋ"
        }
        if (lw.endsWith("ly") && lw.length > 4) look(lw.dropLast(2))?.let { return it + "li" }
        if (lw.endsWith("er") && lw.length > 4) look(lw.dropLast(2))?.let { return it + if (british) "ə" else "əɹ" }
        if (lw.endsWith("ness") && lw.length > 6) look(lw.dropLast(4))?.let { return it + "nəs" }
        return null
    }

    /** Letter-to-sound for words in no dictionary (names, new words). */
    private fun rules(w: String): String {
        val lw = w.lowercase().filter { it.isLetter() }
        if (lw.isEmpty()) return ""
        // Short all-capital words are spelt out (acronyms: "NASA" excepted when it has vowels and 4+ letters).
        if (w.length in 2..4 && w.all { it.isUpperCase() } && !(w.length == 4 && w.any { it in "AEIOU" })) {
            return w.map { look(it.toString()) ?: "" }.joinToString("")
        }
        val r = listOf(
            "tion" to "ʃən", "sion" to "ʒən", "ough" to "O", "augh" to "ɔ", "eigh" to "A", "igh" to "I", "tch" to "ʧ", "sch" to "sk",
            "ch" to "ʧ", "sh" to "ʃ", "th" to "θ", "ph" to "f", "wh" to "w", "ck" to "k", "ng" to "ŋ", "qu" to "kw", "gh" to "",
            "ey" to "A", "oe" to "O", "ee" to "i", "ea" to "i", "oo" to "u", "ou" to "W", "ow" to "O", "oi" to "Y", "oy" to "Y", "ai" to "A", "ay" to "A",
            "au" to "ɔ", "aw" to "ɔ", "ie" to "i", "ei" to "A", "ue" to "u", "ew" to "u", "oa" to "O", "ar" to "ɑɹ", "or" to "ɔɹ",
            "er" to "əɹ", "ir" to "ɜɹ", "ur" to "ɜɹ",
            "a" to "æ", "b" to "b", "c" to "k", "d" to "d", "e" to "ɛ", "f" to "f", "g" to "ɡ", "h" to "h", "i" to "ɪ", "j" to "ʤ",
            "k" to "k", "l" to "l", "m" to "m", "n" to "n", "o" to "ɑ", "p" to "p", "q" to "k", "r" to "ɹ", "s" to "s", "t" to "t",
            "u" to "ʌ", "v" to "v", "w" to "w", "x" to "ks", "y" to "i", "z" to "z",
        )
        val sb = StringBuilder()
        var i = 0
        // Silent final e makes the vowel before it long (make, note, time).
        val magicE = lw.length >= 4 && lw.endsWith("e") && lw[lw.length - 2] !in "aeiou" && lw[lw.length - 3] in "aeiou"
        val word = if (magicE) lw.dropLast(1) else lw
        while (i < word.length) {
            if (magicE && i == word.length - 2 && word[i] in "aeiou") {
                sb.append(mapOf('a' to "A", 'e' to "i", 'i' to "I", 'o' to "O", 'u' to "u")[word[i]]); i++; continue
            }
            if (i == word.length - 1 && word[i] == 'y' && word.length > 2) { sb.append("i"); i++; continue }
            val m = r.firstOrNull { word.startsWith(it.first, i) }
            if (m == null) { i++; continue }
            // A double consonant is one sound.
            if (m.first.length == 1 && i > 0 && word[i - 1] == word[i] && word[i] !in "aeiou") { i++; continue }
            sb.append(m.second); i += m.first.length
        }
        // Stress on the first vowel; British English drops the r after a vowel.
        var p = sb.toString()
        if (british) p = p.replace("əɹ", "ə").replace("ɑɹ", "ɑː").replace("ɔɹ", "ɔː").replace("ɜɹ", "ɜː").replace("O", "Q")
        val v = p.indexOfFirst { it in "æɛɪɑʌəaeiouAIOWYɜɔ" }
        return if (v >= 0 && p.count { it in "æɛɪɑʌəaeiouAIOWYɜɔ" } > 1) p.substring(0, v) + "ˈ" + p.substring(v) else p
    }

    fun word(w: String): String {
        look(w)?.let { return it }
        stem(w)?.let { return it }
        if ('-' in w) return w.split('-').filter { it.isNotEmpty() }.joinToString(" ") { word(it) }
        return rules(w)
    }

    /** Text → phonemes, keeping punctuation the model knows (it shapes the voice's rhythm). */
    fun phonemize(text: String): String {
        val t = normalize(text)
        val sb = StringBuilder()
        val tok = Regex("""[A-Za-z][A-Za-z'\-]*|[;:,.!?—…"()“”]""")
        for (m in tok.findAll(t)) {
            val s = m.value
            if (s[0].isLetter()) {
                val p = word(s.trim('\'', '-'))
                if (p.isEmpty()) continue
                if (sb.isNotEmpty() && sb.last() != ' ' && sb.last() !in "(“\"") sb.append(' ')
                sb.append(p)
            } else {
                // Punctuation sticks to the word before it.
                while (sb.isNotEmpty() && sb.last() == ' ') sb.setLength(sb.length - 1)
                sb.append(s)
                if (s[0] !in "(“") sb.append(' ')
            }
        }
        return sb.toString().trim()
    }
}
