package com.hmtautosolutions.hmttvocale.wake

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.SpeechRecognizer
import com.hmtautosolutions.hmttvocale.LocaleHelper
import com.hmtautosolutions.hmttvocale.Prefs
import com.hmtautosolutions.hmttvocale.nlu.TextNormalizer
import com.hmtautosolutions.hmttvocale.speech.SpeechInput
import com.hmtautosolutions.hmttvocale.speech.VoskMic
import com.hmtautosolutions.hmttvocale.speech.VoskModels
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.Recognizer

/**
 * Wake-word engine abstraction. Swap the implementation (e.g. Porcupine with a custom
 * "Geely" keyword, or Vosk keyword spotting) without touching the service or UI.
 */
interface WakeWordEngine {
    interface Listener {
        /** @param transcript full utterance, which may already contain the command ("Geely, open YouTube"). */
        fun onWakeWord(transcript: String)
        fun onEngineError(message: String, fatal: Boolean)
    }

    val id: String
    /** true only if detection runs fully on-device without a speech service. */
    val isFullyLocal: Boolean
    fun start()
    fun stop()
    fun destroy()
}

/**
 * EXPERIMENTAL engine: repeatedly runs the system SpeechRecognizer and genuinely checks each
 * transcript for "Geely". It is not a dedicated low-power keyword spotter: depending on the
 * recognizer installed it may use the network, may play a start beep, and uses more battery/CPU.
 * Replace with a dedicated on-device engine for production (see README).
 */
class SystemRecognizerWakeWordEngine(
    private val context: Context,
    private val prefs: Prefs,
    private val listener: WakeWordEngine.Listener
) : WakeWordEngine, SpeechInput.Listener {

    override val id = "system-recognizer-experimental"
    override val isFullyLocal = false

    private val main = Handler(Looper.getMainLooper())
    private val speech = SpeechInput(context, this)
    private var active = false
    private var consecutiveErrors = 0

    override fun start() {
        if (active) return
        active = true
        consecutiveErrors = 0
        listen()
    }

    private fun listen() {
        if (!active) return
        speech.start(LocaleHelper.recognizerTag(prefs.language), preferOffline = true)
    }

    private fun restart(delayMs: Long) {
        main.removeCallbacksAndMessages(null)
        main.postDelayed({ listen() }, delayMs)
    }

    override fun onResult(texts: List<String>) {
        if (!active) return
        consecutiveErrors = 0
        val hit = texts.firstOrNull { TextNormalizer.stripWakeWord(it).first }
        if (hit != null) {
            active = false
            speech.destroy()
            listener.onWakeWord(hit)
        } else {
            restart(250)
        }
    }

    override fun onError(code: Int, message: String) {
        if (!active) return
        when (code) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS, -1 -> {
                active = false
                listener.onEngineError(message, fatal = true)
            }
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> restart(250)
            else -> {
                consecutiveErrors++
                listener.onEngineError(message, fatal = false)
                // back off: 1s, 2s, 4s ... max 30s
                val delay = (1000L shl (consecutiveErrors - 1).coerceAtMost(5)).coerceAtMost(30_000L)
                restart(delay)
            }
        }
    }

    override fun stop() {
        active = false
        main.removeCallbacksAndMessages(null)
        speech.destroy()
    }

    override fun destroy() = stop()
}

/**
 * Fully on-device "Geely" detection with the bundled Vosk models: no Google, no internet,
 * no beeps. Two recognizers listen to the same audio: a tiny grammar recognizer that only
 * knows the wake word (low false-alarm rate) and a free-form one that captures the command
 * said in the same breath ("Geely, ouvre YouTube").
 */
class VoskWakeWordEngine(
    private val context: Context,
    private val prefs: Prefs,
    private val listener: WakeWordEngine.Listener
) : WakeWordEngine {

    override val id = "vosk-offline"
    override val isFullyLocal = true

    private val main = Handler(Looper.getMainLooper())
    private var mic: VoskMic? = null
    private var wakeRec: Recognizer? = null
    private var cmdRec: Recognizer? = null
    private var session = 0

    override fun start() {
        if (mic != null) return
        val my = ++session
        val lang = if (prefs.language == "en") "en" else "fr"
        VoskModels.load(context, VoskModels.assetFor(lang)) { model, err ->
            if (my != session) return@load
            if (model == null) { listener.onEngineError(err ?: "model", fatal = true); return@load }
            val grammar = JSONArray(TextNormalizer.OFFLINE_WAKE_GRAMMAR[lang].orEmpty() + "[unk]").toString()
            val w = try { Recognizer(model, VoskModels.SAMPLE_RATE, grammar) } catch (e: Exception) {
                listener.onEngineError("Wake recognizer: ${e.message}", fatal = true); return@load
            }
            val c = Recognizer(model, VoskModels.SAMPLE_RATE)
            wakeRec = w; cmdRec = c
            var lastCmd = ""
            var fired = false
            val onChunk: (ShortArray, Int) -> Unit = chunk@{ buf, n ->
                if (fired) return@chunk
                val cmdFinal = c.acceptWaveForm(buf, n)
                if (cmdFinal) lastCmd = JSONObject(c.result).optString("text")
                if (w.acceptWaveForm(buf, n)) {
                    val wt = JSONObject(w.result).optString("text")
                    if (TextNormalizer.grammarHit(wt, lang)) {
                        fired = true
                        val free = if (cmdFinal) lastCmd else JSONObject(c.finalResult).optString("text")
                        val command = TextNormalizer.stripOfflineWake(free)
                        main.post {
                            if (my != session) return@post
                            release()
                            listener.onWakeWord("geely $command".trim())
                        }
                    }
                }
            }
            val m = VoskMic(onChunk) { msg ->
                if (my == session) {
                    release()
                    listener.onEngineError(msg, fatal = false)
                    main.postDelayed({ if (my == session) start() }, 3000)
                }
            }
            mic = m
            m.start()
        }
    }

    private fun release() {
        mic?.stop(); mic = null
        wakeRec?.let { try { it.close() } catch (_: Exception) {} }
        cmdRec?.let { try { it.close() } catch (_: Exception) {} }
        wakeRec = null; cmdRec = null
    }

    override fun stop() {
        session++
        main.removeCallbacksAndMessages(null)
        release()
    }

    override fun destroy() = stop()
}

object WakeWordEngineFactory {
    fun create(context: Context, prefs: Prefs, listener: WakeWordEngine.Listener): WakeWordEngine {
        val lang = if (prefs.language == "en") "en" else "fr"
        return if (VoskModels.isBundled(context, VoskModels.assetFor(lang))) VoskWakeWordEngine(context, prefs, listener)
        else SystemRecognizerWakeWordEngine(context, prefs, listener)
    }
}
