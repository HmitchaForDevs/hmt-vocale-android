package com.hmtautosolutions.hmttvocale.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.view.View
import androidx.core.content.ContextCompat
import com.hmtautosolutions.hmttvocale.LocaleHelper
import com.hmtautosolutions.hmttvocale.Prefs
import com.hmtautosolutions.hmttvocale.R
import com.hmtautosolutions.hmttvocale.ai.OpenAiCompatibleProvider
import com.hmtautosolutions.hmttvocale.commands.CommandExecutor
import com.hmtautosolutions.hmttvocale.commands.Outcome
import com.hmtautosolutions.hmttvocale.commands.OutcomeStatus
import com.hmtautosolutions.hmttvocale.nlu.CommandIntent
import com.hmtautosolutions.hmttvocale.nlu.IntentParser
import com.hmtautosolutions.hmttvocale.nlu.IntentType
import com.hmtautosolutions.hmttvocale.nlu.TextNormalizer
import com.hmtautosolutions.hmttvocale.service.ListeningService
import com.hmtautosolutions.hmttvocale.speech.SpeechInput
import com.hmtautosolutions.hmttvocale.speech.Tts
import com.hmtautosolutions.hmttvocale.vehicle.VehicleProviders
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : BaseActivity(), SpeechInput.Listener {

    companion object {
        const val EXTRA_FROM_WAKE = "from_wake"
        const val EXTRA_TRANSCRIPT = "transcript"
        private const val REQ_MIC = 7
    }

    private lateinit var prefs: Prefs
    private lateinit var waveform: WaveformView
    private lateinit var stateLabel: TextView
    private lateinit var recognizedText: TextView
    private lateinit var responseText: TextView
    private lateinit var micStatus: TextView
    private lateinit var micDot: View
    private lateinit var micButton: Button
    private lateinit var simBadge: TextView

    private var speech: SpeechInput? = null
    private var tts: Tts? = null
    private lateinit var executor: CommandExecutor
    private val main = Handler(Looper.getMainLooper())
    private val io: ExecutorService = Executors.newSingleThreadExecutor()

    private var listening = false
    private var pending: CommandIntent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        if (!prefs.setupDone) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_main)
        waveform = findViewById(R.id.waveform)
        stateLabel = findViewById(R.id.stateLabel)
        recognizedText = findViewById(R.id.recognizedText)
        responseText = findViewById(R.id.responseText)
        micStatus = findViewById(R.id.micStatus)
        micDot = findViewById(R.id.micDot)
        micButton = findViewById(R.id.micButton)
        simBadge = findViewById(R.id.simBadge)

        speech = SpeechInput(this, this)
        tts = Tts(this)

        micButton.setOnClickListener { toggleListening() }
        waveform.setOnClickListener { toggleListening() }
        findViewById<ImageButton>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        setState(AssistantState.IDLE)
        if (prefs.backgroundListening && hasMic()) ListeningService.start(this)
        handleLaunchIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    private fun handleLaunchIntent(i: Intent?) {
        if (i == null || !i.getBooleanExtra(EXTRA_FROM_WAKE, false)) return
        val transcript = i.getStringExtra(EXTRA_TRANSCRIPT).orEmpty()
        i.removeExtra(EXTRA_FROM_WAKE)
        i.removeExtra(EXTRA_TRANSCRIPT)
        val command = TextNormalizer.stripWakeWord(transcript).second
        if (command.isNotBlank()) main.post { handleText(transcript) }
        else main.postDelayed({ startListening() }, 300)
    }

    override fun onResume() {
        super.onResume()
        executor = CommandExecutor(this, VehicleProviders.create(prefs))
        simBadge.visibility = if (prefs.simulationMode) View.VISIBLE else View.GONE
        ListeningService.send(this, ListeningService.ACTION_PAUSE)
        updateMicStatus()
    }

    override fun onPause() {
        super.onPause()
        main.removeCallbacksAndMessages(null)
        speech?.cancel()
        tts?.stop()
        listening = false
        pending = null
        if (::waveform.isInitialized) setState(AssistantState.IDLE)
        ListeningService.send(this, ListeningService.ACTION_RESUME)
    }

    override fun onDestroy() {
        speech?.destroy()
        tts?.shutdown()
        io.shutdownNow()
        super.onDestroy()
    }

    private fun hasMic() = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

    private fun toggleListening() {
        if (listening) {
            speech?.stop()
        } else {
            startListening()
        }
    }

    private fun startListening() {
        if (!hasMic()) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
            return
        }
        val s = speech ?: return
        if (!s.isAvailable()) {
            showError(getString(R.string.err_no_recognizer))
            return
        }
        tts?.stop()
        listening = true
        recognizedText.text = ""
        responseText.text = ""
        setState(AssistantState.LISTENING)
        s.start(LocaleHelper.recognizerTag(prefs.language), prefs.preferOffline)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_MIC) return
        updateMicStatus()
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startListening()
        else showError(getString(R.string.mic_no_permission))
    }

    // SpeechInput.Listener
    override fun onRms(db: Float) = waveform.setLevel(db)

    override fun onPartial(text: String) {
        recognizedText.text = text
    }

    override fun onResult(texts: List<String>) {
        listening = false
        val best = texts.firstOrNull().orEmpty()
        if (best.isBlank()) showError(getString(R.string.err_no_match)) else handleText(best)
    }

    override fun onError(code: Int, message: String) {
        listening = false
        prefs.lastError = message
        showError(message)
    }

    private fun handleText(raw: String) {
        recognizedText.text = raw
        setState(AssistantState.PROCESSING)

        pending?.let { p ->
            pending = null
            if (IntentParser.isYes(raw)) { deliver(executor.execute(p, confirmed = true)); return }
            if (IntentParser.isNo(raw)) { deliver(Outcome(OutcomeStatus.INFO, getString(R.string.resp_cancelled))); return }
        }

        val intent = IntentParser.parse(raw)
        when {
            intent.type == IntentType.CHANGE_LANGUAGE -> changeLanguage(intent.params["lang"] ?: prefs.language)
            intent.type == IntentType.UNKNOWN && prefs.onlineAiEnabled && prefs.isAiConfigured -> askAi(raw)
            else -> {
                val outcome = executor.execute(intent, confirmed = false)
                if (outcome.status == OutcomeStatus.NEEDS_CONFIRMATION) pending = intent
                deliver(outcome)
            }
        }
    }

    private fun askAi(raw: String) {
        responseText.text = getString(R.string.resp_ai_thinking)
        val lang = prefs.language
        io.execute {
            val res = OpenAiCompatibleProvider(prefs).ask(raw, lang)
            main.post {
                if (isFinishing || isDestroyed) return@post
                val outcome = res.fold(
                    onSuccess = { Outcome(OutcomeStatus.INFO, it) },
                    onFailure = { Outcome(OutcomeStatus.FAILED, getString(R.string.resp_ai_error, it.message ?: "")) }
                )
                deliver(outcome)
            }
        }
    }

    private fun changeLanguage(lang: String) {
        if (!prefs.allowVoiceLanguageChange) {
            deliver(Outcome(OutcomeStatus.REFUSED, getString(R.string.resp_lang_locked)))
            return
        }
        prefs.language = lang
        val msg = LocaleHelper.wrap(applicationContext, lang).getString(R.string.resp_lang_changed)
        responseText.text = msg
        setState(AssistantState.SPEAKING)
        val t = tts
        if (prefs.ttsEnabled && t != null) t.speak(msg, lang, prefs.speechRate) { recreate() }
        else main.postDelayed({ recreate() }, 1200)
    }

    private fun deliver(outcome: Outcome) {
        responseText.text = outcome.message
        val finalState = if (outcome.isPositive) AssistantState.SUCCESS else AssistantState.ERROR
        val t = tts
        if (prefs.ttsEnabled && t != null) {
            setState(AssistantState.SPEAKING)
            t.speak(outcome.message, prefs.language, prefs.speechRate) { afterResponse(finalState, outcome) }
        } else {
            afterResponse(finalState, outcome)
        }
    }

    private fun afterResponse(state: AssistantState, outcome: Outcome) {
        if (isFinishing || isDestroyed) return
        setState(state)
        if (outcome.status == OutcomeStatus.NEEDS_CONFIRMATION) {
            main.postDelayed({ startListening() }, 400)
        } else {
            main.postDelayed({ if (!listening) setState(AssistantState.IDLE) }, 2200)
        }
    }

    private fun showError(msg: String) {
        responseText.text = msg
        setState(AssistantState.ERROR)
        main.postDelayed({ if (!listening) setState(AssistantState.IDLE) }, 2500)
    }

    private fun setState(s: AssistantState) {
        waveform.state = s
        stateLabel.text = getString(when (s) {
            AssistantState.IDLE -> R.string.state_idle
            AssistantState.LISTENING -> R.string.state_listening
            AssistantState.PROCESSING -> R.string.state_processing
            AssistantState.SPEAKING -> R.string.state_speaking
            AssistantState.SUCCESS -> R.string.state_success
            AssistantState.ERROR -> R.string.state_error
        })
        micButton.setText(if (s == AssistantState.LISTENING) R.string.main_stop else R.string.main_push_to_talk)
        updateMicStatus()
    }

    private fun updateMicStatus() {
        val (dot, text) = when {
            !hasMic() -> R.drawable.bg_dot_off to R.string.mic_no_permission
            listening -> R.drawable.bg_dot_on to R.string.mic_active
            ListeningService.isListening -> R.drawable.bg_dot_on to R.string.mic_wake_active
            else -> R.drawable.bg_dot_idle to R.string.mic_ready
        }
        micDot.setBackgroundResource(dot)
        micStatus.setText(text)
    }
}
