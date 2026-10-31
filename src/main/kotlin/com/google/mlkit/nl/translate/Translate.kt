package com.google.mlkit.nl.translate

import com.google.android.gms.tasks.Task

/** Caption translation on Windows (an offline translation model, set up by the AI tools). */
object TranslateLanguage {
    private val all = listOf("af", "ar", "be", "bg", "bn", "ca", "cs", "cy", "da", "de", "el", "en", "eo", "es", "et", "fa", "fi", "fr", "ga", "gl", "gu", "he", "hi", "hr", "ht", "hu", "id", "is", "it", "ja", "ka", "kn", "ko", "lt", "lv", "mk", "mr", "ms", "mt", "nl", "no", "pl", "pt", "ro", "ru", "sk", "sl", "sq", "sv", "sw", "ta", "te", "th", "tl", "tr", "uk", "ur", "vi", "zh")
    @JvmStatic fun fromLanguageTag(tag: String): String? = tag.substringBefore('-').lowercase().takeIf { it in all }
    @JvmStatic fun getAllLanguages(): List<String> = all
    const val ENGLISH = "en"; const val DANISH = "da"
}

class TranslatorOptions private constructor(val source: String, val target: String) {
    class Builder {
        private var s = "en"; private var t = "en"
        fun setSourceLanguage(v: String) = apply { s = v }
        fun setTargetLanguage(v: String) = apply { t = v }
        fun build() = TranslatorOptions(s, t)
    }
}

class Translator internal constructor(private val o: TranslatorOptions) : AutoCloseable {
    fun downloadModelIfNeeded(c: com.google.mlkit.common.model.DownloadConditions): Task<Void?> =
        com.dronedirector.veyrocutpro.windows.WinTranslate.prepare(o.source, o.target)
    fun downloadModelIfNeeded(): Task<Void?> = downloadModelIfNeeded(com.google.mlkit.common.model.DownloadConditions.Builder().build())
    fun translate(text: String): Task<String> = com.dronedirector.veyrocutpro.windows.WinTranslate.translate(o.source, o.target, text)
    override fun close() {}
}

object Translation {
    @JvmStatic fun getClient(o: TranslatorOptions) = Translator(o)
}
