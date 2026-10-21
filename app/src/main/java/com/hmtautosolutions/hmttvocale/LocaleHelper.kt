package com.hmtautosolutions.hmttvocale

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {
    val SUPPORTED = listOf("fr", "en", "ar")

    fun uiLocale(lang: String): Locale = Locale(if (lang in SUPPORTED) lang else "fr")

    fun wrap(base: Context, lang: String): Context {
        val locale = uiLocale(lang)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    fun speechLocale(lang: String): Locale = when (lang) {
        "en" -> Locale.US
        "ar" -> Locale("ar")
        else -> Locale.FRANCE
    }

    /** BCP-47 tag for the system speech recognizer. ar-DZ = Algerian Arabic (availability depends on the recognizer). */
    fun recognizerTag(lang: String): String = when (lang) {
        "en" -> "en-US"
        "ar" -> "ar-DZ"
        else -> "fr-FR"
    }
}
