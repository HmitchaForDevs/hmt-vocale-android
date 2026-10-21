package com.hmtautosolutions.hmttvocale.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.hmtautosolutions.hmttvocale.LocaleHelper
import java.util.UUID

/** Android TextToSpeech wrapper with completion callbacks delivered on the main thread. */
class Tts(context: Context) : TextToSpeech.OnInitListener {

    enum class LangStatus { READY, MISSING_DATA, NOT_SUPPORTED, ENGINE_NOT_READY }

    private val main = Handler(Looper.getMainLooper())
    private val callbacks = HashMap<String, () -> Unit>()
    private val tts = TextToSpeech(context.applicationContext, this)
    @Volatile var ready = false
        private set
    @Volatile var initFailed = false
        private set

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        initFailed = !ready
        if (ready) {
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) = finish(utteranceId)
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = finish(utteranceId)
                override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId)
            })
        }
    }

    private fun finish(id: String?) {
        main.post { callbacks.remove(id ?: "")?.invoke() }
    }

    fun languageStatus(lang: String): LangStatus {
        if (!ready) return LangStatus.ENGINE_NOT_READY
        return when (tts.isLanguageAvailable(LocaleHelper.speechLocale(lang))) {
            TextToSpeech.LANG_MISSING_DATA -> LangStatus.MISSING_DATA
            TextToSpeech.LANG_NOT_SUPPORTED -> LangStatus.NOT_SUPPORTED
            else -> LangStatus.READY
        }
    }

    /** Returns false (and calls onDone immediately) if the voice could not be played. */
    fun speak(text: String, lang: String, rate: Float, onDone: () -> Unit): Boolean {
        if (!ready || text.isBlank()) { onDone(); return false }
        val r = tts.setLanguage(LocaleHelper.speechLocale(lang))
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) { onDone(); return false }
        tts.setSpeechRate(rate)
        val id = UUID.randomUUID().toString()
        callbacks[id] = onDone
        val res = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        if (res != TextToSpeech.SUCCESS) {
            callbacks.remove(id)
            onDone()
            return false
        }
        return true
    }

    fun stop() {
        if (ready) tts.stop()
        callbacks.clear()
    }

    fun shutdown() {
        callbacks.clear()
        try { tts.stop(); tts.shutdown() } catch (_: Exception) {}
    }
}
