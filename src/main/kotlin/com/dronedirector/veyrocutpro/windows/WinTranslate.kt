package com.dronedirector.veyrocutpro.windows

import com.google.android.gms.tasks.Task

/**
 * Caption translation on Windows. The offline translation models come with the AI tools update;
 * until they are there, translating says so. Language finding works now (common words per language).
 */
object WinTranslate {
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
    )
    fun identify(text: String): String {
        val t = text.lowercase().split(Regex("[^\\p{L}]+")).filter { it.isNotBlank() }
        if (t.isEmpty()) return "und"
        val best = words.maxByOrNull { (_, w) -> t.count { it in w } } ?: return "und"
        return if (t.count { it in best.value } >= 2) best.key else "und"
    }
    fun prepare(from: String, to: String): Task<Void?> = Task(null, Exception("Caption translation comes to Windows with the AI tools update"))
    fun translate(from: String, to: String, text: String): Task<String> = Task(null, Exception("Caption translation comes to Windows with the AI tools update"))
}
