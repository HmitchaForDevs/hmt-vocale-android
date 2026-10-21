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
    val WAKE_WORDS = listOf("geely", "jili", "gili", "djili", "jeely", "geeli", "jilly", "جيلي")
        .map { normalize(it) }

    /** Returns (found, text after the wake word). */
    fun stripWakeWord(input: String): Pair<Boolean, String> {
        val words = normalize(input).split(' ').filter { it.isNotEmpty() }
        val idx = words.indexOfFirst { it in WAKE_WORDS }
        if (idx < 0) return Pair(false, words.joinToString(" "))
        return Pair(true, words.drop(idx + 1).joinToString(" "))
    }
}
