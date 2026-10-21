package com.hmtautosolutions.hmttvocale

import android.content.Context
import android.content.SharedPreferences

/** Persistent user preferences (stored privately on the device). */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("hmt_prefs", Context.MODE_PRIVATE)

    var language: String
        get() = sp.getString("language", "fr") ?: "fr"
        set(v) = sp.edit().putString("language", v).apply()

    var setupDone: Boolean
        get() = sp.getBoolean("setup_done", false)
        set(v) = sp.edit().putBoolean("setup_done", v).apply()

    var backgroundListening: Boolean
        get() = sp.getBoolean("background_listening", false)
        set(v) = sp.edit().putBoolean("background_listening", v).apply()

    var wakeWordEnabled: Boolean
        get() = sp.getBoolean("wake_word_enabled", false)
        set(v) = sp.edit().putBoolean("wake_word_enabled", v).apply()

    var autoStartOnBoot: Boolean
        get() = sp.getBoolean("auto_start_boot", true)
        set(v) = sp.edit().putBoolean("auto_start_boot", v).apply()

    var ttsEnabled: Boolean
        get() = sp.getBoolean("tts_enabled", true)
        set(v) = sp.edit().putBoolean("tts_enabled", v).apply()

    var speechRate: Float
        get() = sp.getFloat("speech_rate", 1.0f)
        set(v) = sp.edit().putFloat("speech_rate", v).apply()

    var preferOffline: Boolean
        get() = sp.getBoolean("prefer_offline", true)
        set(v) = sp.edit().putBoolean("prefer_offline", v).apply()

    var allowVoiceLanguageChange: Boolean
        get() = sp.getBoolean("voice_lang_change", true)
        set(v) = sp.edit().putBoolean("voice_lang_change", v).apply()

    var simulationMode: Boolean
        get() = sp.getBoolean("simulation_mode", true)
        set(v) = sp.edit().putBoolean("simulation_mode", v).apply()

    var onlineAiEnabled: Boolean
        get() = sp.getBoolean("online_ai", false)
        set(v) = sp.edit().putBoolean("online_ai", v).apply()

    var aiEndpoint: String
        get() = sp.getString("ai_endpoint", "") ?: ""
        set(v) = sp.edit().putString("ai_endpoint", v.trim()).apply()

    var aiModel: String
        get() = sp.getString("ai_model", "") ?: ""
        set(v) = sp.edit().putString("ai_model", v.trim()).apply()

    /** Entered by the user on the device. Never bundled in the APK or repository. */
    var aiApiKey: String
        get() = sp.getString("ai_key", "") ?: ""
        set(v) = sp.edit().putString("ai_key", v.trim()).apply()

    var lastError: String
        get() = sp.getString("last_error", "") ?: ""
        set(v) = sp.edit().putString("last_error", v).apply()

    val isAiConfigured: Boolean
        get() = aiEndpoint.startsWith("https://") && aiApiKey.isNotBlank() && aiModel.isNotBlank()
}
