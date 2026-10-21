package com.hmtautosolutions.hmttvocale.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.hmtautosolutions.hmttvocale.R

/** Thin wrapper over the Android system SpeechRecognizer. Must be used from the main thread. */
class SpeechInput(private val context: Context, private val listener: Listener) {

    interface Listener {
        fun onReady() {}
        fun onRms(db: Float) {}
        fun onPartial(text: String) {}
        fun onResult(texts: List<String>)
        fun onError(code: Int, message: String)
    }

    private var recognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(languageTag: String, preferOffline: Boolean) {
        if (!isAvailable()) {
            listener.onError(-1, context.getString(R.string.err_no_recognizer))
            return
        }
        destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = listener.onReady()
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) = listener.onRms(rmsdB)
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) = listener.onError(error, errorMessage(context, error))
            override fun onResults(results: Bundle?) {
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: arrayListOf()
                listener.onResult(list)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                list?.firstOrNull()?.let { listener.onPartial(it) }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        try {
            r.startListening(intent)
        } catch (e: Exception) {
            listener.onError(SpeechRecognizer.ERROR_CLIENT, errorMessage(context, SpeechRecognizer.ERROR_CLIENT))
        }
    }

    fun stop() { recognizer?.stopListening() }

    fun cancel() { recognizer?.cancel() }

    fun destroy() {
        recognizer?.let {
            try { it.cancel(); it.destroy() } catch (_: Exception) {}
        }
        recognizer = null
    }

    companion object {
        const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        const val ERROR_LANGUAGE_UNAVAILABLE = 13

        fun errorMessage(ctx: Context, code: Int): String = ctx.getString(when (code) {
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> R.string.err_network
            SpeechRecognizer.ERROR_AUDIO -> R.string.err_audio
            SpeechRecognizer.ERROR_SERVER -> R.string.err_server
            SpeechRecognizer.ERROR_CLIENT -> R.string.err_client
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> R.string.err_timeout
            SpeechRecognizer.ERROR_NO_MATCH -> R.string.err_no_match
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> R.string.err_busy
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> R.string.err_permission
            ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE -> R.string.err_language
            else -> R.string.err_unknown
        })
    }
}
