package com.hmtautosolutions.hmttvocale.nlu

import java.text.Normalizer
import java.util.Locale

object TextNormalizer {
    private val marks = Regex("\\p{Mn}+")
    private val nonWord = Regex("[^\\p{L}\\p{N}]+")

    /** Lowercase, strip accents / Arabic diacritics, unify digits, collapse punctuation. */
    fun normalize(input: String): String {
        var t = Normalizer.normalize(input.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        t = t.replace(marks, "")
        val sb = StringBuilder(t.length)
        for (c in t) {
            when (c) {
                in '\u0660'..'\u0669' -> sb.append('0' + (c - '\u0660'))
                in '\u06F0'..'\u06F9' -> sb.append('0' + (c - '\u06F0'))
                '\u0640' -> {}
                '\u0649' -> sb.append('\u064A')
                else -> sb.append(c)
            }
        }
        return sb.toString().replace(nonWord, " ").trim()
    }

    /** Spellings the speech recognizer may return for the wake word "Geely". */
    val WAKE_WORDS = listOf("geely", "jili", "gili", "djili", "jeely", "geeli", "jilly", "jeeli", "جيلي", "جلي", "جيلى")
        .map { normalize(it) }

    /** Returns (found, text after the wake word). */
    fun stripWakeWord(input: String): Pair<Boolean, String> {
        val words = normalize(input).split(' ').filter { it.isNotEmpty() }
        val idx = words.indexOfFirst { it in WAKE_WORDS }
        if (idx < 0) return Pair(false, words.joinToString(" "))
        return Pair(true, words.drop(idx + 1).joinToString(" "))
    }

    /** Offline-engine grammar words that sound like "Geely" (all exist in the bundled model vocabularies). */
    val OFFLINE_WAKE_GRAMMAR = mapOf(
        "fr" to listOf("gili", "gilly", "jimmy", "jill"),
        "en" to listOf("geely", "gilly", "jelly", "gilley", "neely")
    )

    /** How the offline free-form recognizer tends to spell "Geely" at the start of a command. */
    private val OFFLINE_ALIASES = (WAKE_WORDS + listOf("jimmy", "gilly", "jill", "gilley", "gailey", "neely", "jelly", "gimme", "jenny", "julie", "li")).toSet()

    /** True if the grammar transcript (e.g. "gili [unk]" or "[unk] geely") starts with the wake word. */
    fun grammarHit(grammarText: String, lang: String): Boolean {
        val words = grammarText.trim().split(' ').filter { it.isNotEmpty() }
        val wake = OFFLINE_WAKE_GRAMMAR[if (lang == "en") "en" else "fr"].orEmpty()
        val idx = words.indexOfFirst { it in wake }
        return idx in 0..1
    }

    /** Removes the (mis-heard) wake word at the start of an offline transcript; returns the command. */
    fun stripOfflineWake(freeText: String): String {
        val words = normalize(freeText).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return ""
        val idx = words.take(3).indexOfFirst { it in OFFLINE_ALIASES }
        return words.drop(if (idx >= 0) idx + 1 else 1).joinToString(" ")
    }
}
