package com.hmtautosolutions.hmttvocale.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.hmtautosolutions.hmttvocale.LocaleHelper
import com.hmtautosolutions.hmttvocale.Prefs
import com.hmtautosolutions.hmttvocale.R
import com.hmtautosolutions.hmttvocale.service.ListeningService
import com.hmtautosolutions.hmttvocale.speech.SpeechInput
import com.hmtautosolutions.hmttvocale.speech.Tts
import com.hmtautosolutions.hmttvocale.vehicle.SupportLevel
import com.hmtautosolutions.hmttvocale.vehicle.VehicleFunction
import com.hmtautosolutions.hmttvocale.vehicle.VehicleProviders

class SettingsActivity : BaseActivity() {

    private lateinit var prefs: Prefs
    private lateinit var tts: Tts
    private var testSpeech: SpeechInput? = null
    private lateinit var testResult: TextView
    private lateinit var diagText: TextView
    private lateinit var serviceStatus: TextView
    private lateinit var vehicleCaps: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        tts = Tts(this)
        setContentView(R.layout.activity_settings)
        testResult = findViewById(R.id.testResult)
        diagText = findViewById(R.id.diagText)
        serviceStatus = findViewById(R.id.serviceStatus)
        vehicleCaps = findViewById(R.id.vehicleCaps)

        findViewById<Button>(R.id.backButton).setOnClickListener { finish() }

        val rg = findViewById<RadioGroup>(R.id.langGroup)
        rg.check(when (prefs.language) { "en" -> R.id.rbEn; "ar" -> R.id.rbAr; else -> R.id.rbFr })
        rg.setOnCheckedChangeListener { _, id ->
            val lang = when (id) { R.id.rbEn -> "en"; R.id.rbAr -> "ar"; else -> "fr" }
            if (lang != prefs.language) {
                prefs.language = lang
                restartServiceIfNeeded()
                recreate()
            }
        }

        bindSwitch(R.id.swTts, prefs.ttsEnabled) { prefs.ttsEnabled = it }
        bindSwitch(R.id.swOffline, prefs.preferOffline) { prefs.preferOffline = it }
        bindSwitch(R.id.swVoiceLang, prefs.allowVoiceLanguageChange) { prefs.allowVoiceLanguageChange = it }
        bindSwitch(R.id.swWake, prefs.wakeWordEnabled) { prefs.wakeWordEnabled = it; restartServiceIfNeeded() }
        bindSwitch(R.id.swBackground, prefs.backgroundListening) {
            prefs.backgroundListening = it
            if (it && hasMic()) ListeningService.start(this) else ListeningService.stop(this)
            refresh()
        }
        bindSwitch(R.id.swBoot, prefs.autoStartOnBoot) { prefs.autoStartOnBoot = it }
        bindSwitch(R.id.swSimulation, prefs.simulationMode) { prefs.simulationMode = it; refresh() }
        bindSwitch(R.id.swAi, prefs.onlineAiEnabled) { prefs.onlineAiEnabled = it }

        val rate = findViewById<SeekBar>(R.id.seekRate)
        rate.progress = ((prefs.speechRate - 0.5f) * 10).toInt().coerceIn(0, 20)
        rate.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) prefs.speechRate = 0.5f + p / 10f
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        val etEndpoint = findViewById<EditText>(R.id.etEndpoint)
        val etModel = findViewById<EditText>(R.id.etModel)
        val etKey = findViewById<EditText>(R.id.etKey)
        etEndpoint.setText(prefs.aiEndpoint)
        etModel.setText(prefs.aiModel)
        findViewById<Button>(R.id.btnSaveAi).setOnClickListener {
            prefs.aiEndpoint = etEndpoint.text.toString()
            prefs.aiModel = etModel.text.toString()
            if (etKey.text.isNotBlank()) prefs.aiApiKey = etKey.text.toString()
            etKey.setText("")
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
            refresh()
        }

        findViewById<Button>(R.id.btnTestMic).setOnClickListener { testMic() }
        findViewById<Button>(R.id.btnTestTts).setOnClickListener { testTts() }
        findViewById<Button>(R.id.btnRefresh).setOnClickListener { refresh() }
    }

    override fun onResume() {
        super.onResume()
        ListeningService.send(this, ListeningService.ACTION_PAUSE)
        refresh()
    }

    override fun onPause() {
        super.onPause()
        testSpeech?.destroy()
        testSpeech = null
        ListeningService.send(this, ListeningService.ACTION_RESUME)
    }

    override fun onDestroy() {
        tts.shutdown()
        super.onDestroy()
    }

    private fun bindSwitch(id: Int, value: Boolean, onChange: (Boolean) -> Unit) {
        val sw = findViewById<SwitchCompat>(id)
        sw.isChecked = value
        sw.setOnCheckedChangeListener { _, c -> onChange(c) }
    }

    private fun hasMic() = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

    private fun restartServiceIfNeeded() {
        if (ListeningService.isRunning) {
            ListeningService.stop(this)
            if (prefs.backgroundListening && hasMic()) ListeningService.start(this)
        }
    }

    private fun testMic() {
        if (!hasMic()) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 3)
            return
        }
        testResult.setText(R.string.test_mic_say)
        testSpeech?.destroy()
        val s = SpeechInput(this, object : SpeechInput.Listener {
            override fun onPartial(text: String) { testResult.text = text }
            override fun onResult(texts: List<String>) {
                testResult.text = getString(R.string.test_mic_heard, texts.firstOrNull().orEmpty())
            }
            override fun onError(code: Int, message: String) {
                prefs.lastError = message
                testResult.text = message
                refresh()
            }
        })
        testSpeech = s
        s.start(LocaleHelper.recognizerTag(prefs.language), prefs.preferOffline)
    }

    private fun testTts() {
        val status = tts.languageStatus(prefs.language)
        testResult.text = getString(ttsStatusRes(status))
        tts.speak(getString(R.string.test_tts_phrase), prefs.language, prefs.speechRate) {}
    }

    private fun ttsStatusRes(s: Tts.LangStatus) = when (s) {
        Tts.LangStatus.READY -> R.string.tts_ok
        Tts.LangStatus.MISSING_DATA -> R.string.tts_lang_missing
        Tts.LangStatus.NOT_SUPPORTED -> R.string.tts_lang_unsupported
        Tts.LangStatus.ENGINE_NOT_READY -> R.string.tts_not_ready
    }

    private fun refresh() {
        serviceStatus.text = getString(when {
            ListeningService.isListening -> R.string.service_listening
            ListeningService.isRunning -> R.string.service_running
            else -> R.string.service_stopped
        })

        val provider = VehicleProviders.create(prefs)
        val caps = provider.capabilities()
        val sb = StringBuilder()
        for (f in VehicleFunction.values()) {
            val label = getString(when (f) {
                VehicleFunction.CLIMATE_POWER -> R.string.vf_climate
                VehicleFunction.TEMPERATURE -> R.string.vf_temperature
                VehicleFunction.FAN_SPEED -> R.string.vf_fan
                VehicleFunction.WINDOWS -> R.string.vf_windows
            })
            val level = getString(when (caps[f]) {
                SupportLevel.VERIFIED -> R.string.support_verified
                SupportLevel.INTERFACE_UNTESTED -> R.string.support_untested
                else -> R.string.support_not_supported
            })
            sb.append("• ").append(label).append(" — ").append(level).append('\n')
        }
        if (provider.isSimulation) sb.append('\n').append(getString(R.string.simulation_active_note))
        vehicleCaps.text = sb.toString().trim()

        val yes = getString(R.string.yes)
        val no = getString(R.string.no)
        val recognizer = SpeechInput(this, object : SpeechInput.Listener {
            override fun onResult(texts: List<String>) {}
            override fun onError(code: Int, message: String) {}
        }).isAvailable()
        diagText.text = buildString {
            append(getString(R.string.diag_android)).append(": ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
            append(getString(R.string.diag_device)).append(": ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
            append(getString(R.string.diag_mic_perm)).append(": ").append(if (hasMic()) yes else no).append('\n')
            append(getString(R.string.diag_recognizer)).append(": ").append(if (recognizer) yes else no).append('\n')
            append(getString(R.string.diag_tts)).append(": ").append(getString(ttsStatusRes(tts.languageStatus(prefs.language)))).append('\n')
            append(getString(R.string.diag_ai)).append(": ").append(if (prefs.isAiConfigured) yes else no).append('\n')
            append(getString(R.string.diag_wake_engine)).append(": ").append(getString(R.string.wake_engine_name)).append('\n')
            append(getString(R.string.diag_last_error)).append(": ").append(prefs.lastError.ifBlank { "—" })
        }
    }
}
