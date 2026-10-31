package com.google.mlkit.nl.languageid

import com.google.android.gms.tasks.Task

/** Which language a text is in (by its letters and common words). */
class LanguageIdentifier internal constructor() : AutoCloseable {
    fun identifyLanguage(text: String): Task<String> = Task(com.dronedirector.veyrocutpro.windows.WinTranslate.identify(text), null)
    override fun close() {}
}

object LanguageIdentification {
    @JvmStatic fun getClient() = LanguageIdentifier()
    const val UNDETERMINED_LANGUAGE_TAG = "und"
}
