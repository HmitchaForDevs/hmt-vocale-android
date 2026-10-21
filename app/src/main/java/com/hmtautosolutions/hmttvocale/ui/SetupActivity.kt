package com.hmtautosolutions.hmttvocale.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.hmtautosolutions.hmttvocale.Prefs
import com.hmtautosolutions.hmttvocale.R
import com.hmtautosolutions.hmttvocale.service.ListeningService

class SetupActivity : BaseActivity() {

    private lateinit var prefs: Prefs
    private var step = 0
    private lateinit var steps: List<View>
    private lateinit var permStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        setContentView(R.layout.activity_setup)
        step = savedInstanceState?.getInt("step") ?: 0
        steps = listOf(findViewById(R.id.stepWelcome), findViewById(R.id.stepMic), findViewById(R.id.stepAi))
        permStatus = findViewById(R.id.permStatus)

        val langButtons = mapOf(
            "fr" to findViewById<Button>(R.id.langFr),
            "en" to findViewById<Button>(R.id.langEn),
            "ar" to findViewById<Button>(R.id.langAr)
        )
        langButtons.forEach { (lang, b) ->
            b.isSelected = prefs.language == lang
            b.setOnClickListener {
                if (prefs.language != lang) {
                    prefs.language = lang
                    recreate()
                }
            }
        }

        findViewById<Button>(R.id.next1).setOnClickListener { show(1) }
        findViewById<Button>(R.id.back2).setOnClickListener { show(0) }
        findViewById<Button>(R.id.next2).setOnClickListener { show(2) }
        findViewById<Button>(R.id.back3).setOnClickListener { show(1) }
        findViewById<Button>(R.id.grantMic).setOnClickListener {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO, "android.permission.POST_NOTIFICATIONS"), 1)
        }

        val swBg = findViewById<SwitchCompat>(R.id.swBackground)
        swBg.isChecked = prefs.backgroundListening
        swBg.setOnCheckedChangeListener { _, c ->
            prefs.backgroundListening = c
            prefs.wakeWordEnabled = c
        }
        val swBoot = findViewById<SwitchCompat>(R.id.swBoot)
        swBoot.isChecked = prefs.autoStartOnBoot
        swBoot.setOnCheckedChangeListener { _, c -> prefs.autoStartOnBoot = c }

        val swAi = findViewById<SwitchCompat>(R.id.swAi)
        val etEndpoint = findViewById<EditText>(R.id.etEndpoint)
        val etModel = findViewById<EditText>(R.id.etModel)
        val etKey = findViewById<EditText>(R.id.etKey)
        swAi.isChecked = prefs.onlineAiEnabled
        etEndpoint.setText(prefs.aiEndpoint)
        etModel.setText(prefs.aiModel)

        findViewById<Button>(R.id.finish).setOnClickListener {
            prefs.onlineAiEnabled = swAi.isChecked
            prefs.aiEndpoint = etEndpoint.text.toString()
            prefs.aiModel = etModel.text.toString()
            if (etKey.text.isNotBlank()) prefs.aiApiKey = etKey.text.toString()
            complete()
        }
        findViewById<Button>(R.id.skip).setOnClickListener {
            prefs.onlineAiEnabled = false
            complete()
        }
        show(step)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("step", step)
    }

    override fun onResume() {
        super.onResume()
        updatePerm()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updatePerm()
    }

    private fun hasMic() = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

    private fun updatePerm() {
        permStatus.setText(if (hasMic()) R.string.perm_granted else R.string.perm_denied)
    }

    private fun show(i: Int) {
        step = i
        steps.forEachIndexed { idx, v -> v.visibility = if (idx == i) View.VISIBLE else View.GONE }
    }

    private fun complete() {
        prefs.setupDone = true
        if (prefs.backgroundListening && hasMic()) ListeningService.start(this)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
