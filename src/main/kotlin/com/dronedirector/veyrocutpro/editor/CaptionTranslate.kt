package com.dronedirector.veyrocutpro.editor

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.concurrent.TimeUnit

/**
 * Caption translation on the phone (Google ML Kit on-device translation, free; the language pack
 * for each language, about 30 MB, is downloaded once). Translated text is credited "Translated by
 * Google", as Google's terms ask.
 */
object CaptionTranslate {
    /** Languages offered (ML Kit codes) with their names. */
    val targets = listOf(
        "en" to "English", "da" to "Danish", "no" to "Norwegian", "sv" to "Swedish", "is" to "Icelandic", "de" to "German",
        "nl" to "Dutch", "fr" to "French", "es" to "Spanish", "it" to "Italian", "pt" to "Portuguese", "pl" to "Polish",
        "fi" to "Finnish", "uk" to "Ukrainian", "ar" to "Arabic", "hi" to "Hindi", "ja" to "Japanese", "ko" to "Korean", "zh" to "Chinese",
    )

    class Failed(msg: String) : Exception(msg)

    /** Which language [text] is in (ML Kit code), or null if unsure. Blocking. */
    fun detect(text: String): String? = try {
        val id = LanguageIdentification.getClient()
        val r = Tasks.await(id.identifyLanguage(text.take(2000)), 20, TimeUnit.SECONDS)
        id.close()
        r.takeIf { it != "und" }
    } catch (_: Throwable) { null }

    /**
     * Translates [texts] (blocking; call off the main thread) from [from] (null = detect) into [to].
     * [onStatus] gets short progress notes.
     */
    fun translate(texts: List<String>, from: String?, to: String, onStatus: (String) -> Unit): List<String> {
        val src0 = from ?: detect(texts.joinToString(" ")) ?: throw Failed("Couldn't tell which language the captions are in")
        val src = TranslateLanguage.fromLanguageTag(src0) ?: throw Failed("Captions in this language can't be translated on the phone")
        val dst = TranslateLanguage.fromLanguageTag(to) ?: throw Failed("Can't translate into that language")
        if (src == dst) return texts
        val t = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(src).setTargetLanguage(dst).build())
        try {
            onStatus("Getting the language pack (once, about 30 MB)…")
            Tasks.await(t.downloadModelIfNeeded(DownloadConditions.Builder().build()), 10, TimeUnit.MINUTES)
            return texts.mapIndexed { i, s ->
                onStatus("Translating ${i + 1} / ${texts.size}…")
                if (s.isBlank()) s else Tasks.await(t.translate(s), 60, TimeUnit.SECONDS)
            }
        } catch (e: Failed) { throw e } catch (e: Throwable) {
            throw Failed("Translation failed: ${e.cause?.message ?: e.message ?: "no connection?"}")
        } finally { t.close() }
    }
}
