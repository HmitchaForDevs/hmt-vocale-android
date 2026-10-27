package com.hmtautosolutions.hmttvocale.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.hmtautosolutions.hmttvocale.R
import org.json.JSONObject
import org.vosk.Recognizer
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Speech input. Uses the bundled offline engine (Vosk) for French and English, so it works
 * without Google or internet. Falls back to the system SpeechRecognizer (e.g. Arabic).
 * Must be used from the main thread.
 */
class SpeechInput(private val context: Context, private val listener: Listener) {

    interface Listener {
        fun onReady() {}
        fun onRms(db: Float) {}
        fun onPartial(text: String) {}
        fun onResult(texts: List<String>)
        fun onError(code: Int, message: String)
    }

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var vRec: Recognizer? = null
    private var vMic: VoskMic? = null
    private var session = 0

    private fun systemAvailable() = SpeechRecognizer.isRecognitionAvailable(context)

    fun isAvailable(): Boolean = systemAvailable() ||
        VoskModels.isBundled(context, "model-fr") || VoskModels.isBundled(context, "model-en") ||
        VoskModels.isBundled(context, "model-ar")

    fun start(languageTag: String, preferOffline: Boolean) {
        destroy()
        val lang = languageTag.substringBefore('-')
        if (VoskModels.commandsOffline(lang) && VoskModels.isBundled(context, VoskModels.commandAssetFor(lang))) {
            startVosk(lang)
        } else {
            startSystem(languageTag, preferOffline)
        }
    }

    private fun startVosk(lang: String) {
        val my = ++session
        VoskModels.load(context, VoskModels.commandAssetFor(lang)) { model, err ->
            if (my != session) return@load
            if (model == null) { listener.onError(SpeechRecognizer.ERROR_CLIENT, err ?: ""); return@load }
            val rec = try { Recognizer(model, VoskModels.SAMPLE_RATE) } catch (e: Exception) {
                listener.onError(SpeechRecognizer.ERROR_CLIENT, errorMessage(context, SpeechRecognizer.ERROR_CLIENT)); return@load
            }
            vRec = rec
            val started = System.currentTimeMillis()
            var heard = false
            var lastPartial = ""
            var done = false
            fun finishWith(text: String?, code: Int) {
                if (my != session) return
                session++
                stopVosk()
                if (!text.isNullOrBlank()) listener.onResult(listOf(text))
                else listener.onError(code, errorMessage(context, code))
            }
            val onChunk: (ShortArray, Int) -> Unit = chunk@{ buf, n ->
                if (done) return@chunk
                var sum = 0.0
                for (i in 0 until n) sum += buf[i].toDouble() * buf[i]
                val db = (20 * log10(sqrt(sum / n) + 1) - 40).toFloat() / 4f
                val final = rec.acceptWaveForm(buf, n)
                val elapsed = System.currentTimeMillis() - started
                if (final) {
                    val t = JSONObject(rec.result).optString("text")
                    if (t.isNotBlank()) { done = true; main.post { finishWith(t, 0) } }
                } else {
                    val p = JSONObject(rec.partialResult).optString("partial")
                    if (p.isNotBlank()) heard = true
                    main.post {
                        if (my != session) return@post
                        listener.onRms(db)
                        if (p.isNotBlank() && p != lastPartial) { lastPartial = p; listener.onPartial(p) }
                    }
                }
                if (!done && ((!heard && elapsed > 7000) || elapsed > 15000)) {
                    done = true
                    val t = JSONObject(rec.finalResult).optString("text")
                    main.post { finishWith(t, SpeechRecognizer.ERROR_SPEECH_TIMEOUT) }
                }
            }
            val mic = VoskMic(onChunk) { msg -> if (my == session) { session++; stopVosk(); listener.onError(SpeechRecognizer.ERROR_AUDIO, msg) } }
            vMic = mic
            mic.start()
            listener.onReady()
        }
    }

    private fun stopVosk() {
        vMic?.stop(); vMic = null
        vRec?.let { try { it.close() } catch (_: Exception) {} }
        vRec = null
    }

    private fun startSystem(languageTag: String, preferOffline: Boolean) {
        if (!systemAvailable()) {
            listener.onError(-1, context.getString(R.string.err_no_recognizer))
            return
        }
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

    fun stop() {
        recognizer?.stopListening()
        val rec = vRec ?: return
        val my = session
        vMic?.stop(); vMic = null
        val t = try { JSONObject(rec.finalResult).optString("text") } catch (_: Exception) { "" }
        stopVosk()
        if (my == session) {
            if (t.isNotBlank()) listener.onResult(listOf(t))
            else listener.onError(SpeechRecognizer.ERROR_NO_MATCH, errorMessage(context, SpeechRecognizer.ERROR_NO_MATCH))
        }
    }

    fun cancel() { session++; recognizer?.cancel(); stopVosk() }

    fun destroy() {
        session++
        stopVosk()
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
