package com.hmtautosolutions.hmttvocale.service

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
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
import com.hmtautosolutions.hmttvocale.speech.SpeechInput
import com.hmtautosolutions.hmttvocale.speech.Tts
import com.hmtautosolutions.hmttvocale.vehicle.VehicleProviders
import java.util.concurrent.Executors

/**
 * Floating assistant bubble shown above any app (maps, music...) after the wake word.
 * Listens for the command, runs it, speaks the answer, then disappears — HMT Vocale
 * never comes to the foreground. Requires the "display over other apps" permission.
 */
class OverlayAssistant(
    private val ctx: Context,
    private val prefs: Prefs,
    private val tts: Tts,
    private val onFinished: () -> Unit
) : SpeechInput.Listener {

    companion object {
        fun canShow(ctx: Context) = Settings.canDrawOverlays(ctx)
    }

    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val speech = SpeechInput(ctx, this)
    private var root: View? = null
    private lateinit var text: TextView
    private lateinit var dot: View
    private var pulse: ObjectAnimator? = null
    private var pending: CommandIntent? = null
    private var active = false

    private fun lc(): Context = LocaleHelper.wrap(ctx, prefs.language)

    private fun t(fr: String, en: String, ar: String) = when (prefs.language) {
        "ar" -> ar; "en" -> en; else -> fr
    }

    /** @param transcript the utterance that contained the wake word (may already hold the command). */
    fun show(transcript: String) {
        if (active) return
        active = true
        addView()
        val command = TextNormalizer.stripWakeWord(transcript).second
        if (command.isNotBlank()) { handle(transcript); return }
        // Wake reply: show "J'écoute", say "Oui", then open the mic (after the voice, so it doesn't hear itself).
        setText(listeningText())
        val yes = t("Oui", "Yes", "نعم")
        if (prefs.ttsEnabled) tts.speak(yes, prefs.language, prefs.speechRate) { main.post { if (active) listen() } }
        else listen()
    }

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    private fun addView() {
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            background = GradientDrawable().apply {
                cornerRadius = dp(28).toFloat()
                setColor(ContextCompat.getColor(ctx, R.color.hmt_surface))
                setStroke(dp(3), ContextCompat.getColor(ctx, R.color.hmt_red))
            }
            elevation = dp(16).toFloat()
        }
        val logo = ImageView(ctx).apply { setImageResource(R.drawable.ic_logo) }
        card.addView(logo, LinearLayout.LayoutParams(dp(72), dp(72)))
        dot = View(ctx).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ContextCompat.getColor(ctx, R.color.hmt_red))
            }
        }
        card.addView(dot, LinearLayout.LayoutParams(dp(22), dp(22)).apply {
            topMargin = dp(18); bottomMargin = dp(18)
        })
        text = TextView(ctx).apply {
            setTextColor(ContextCompat.getColor(ctx, R.color.hmt_white))
            textSize = 24f
            gravity = Gravity.CENTER
            maxLines = 4
        }
        card.addView(text, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        card.setOnClickListener { finish() } // tap to dismiss

        val side = dp(340)
        val lp = WindowManager.LayoutParams(
            side, side,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }
        try {
            wm.addView(card, lp)
            root = card
            card.alpha = 0f; card.scaleX = 0.85f; card.scaleY = 0.85f
            card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).start()
        } catch (e: Exception) {
            prefs.lastError = "Overlay failed: ${e.message}"
        }
        pulse = ObjectAnimator.ofFloat(dot, View.ALPHA, 1f, 0.25f).apply {
            duration = 600; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE
        }
    }

    private fun setText(s: String) { if (::text.isInitialized) text.text = s }

    private fun listen() {
        setText(listeningText())
        pulse?.start()
        speech.start(LocaleHelper.recognizerTag(prefs.language), preferOffline = true)
    }

    private fun listeningText() = t("J'écoute…", "I'm listening…", "أنا أستمع…")

    override fun onPartial(text: String) { setText(text) }

    override fun onResult(texts: List<String>) {
        pulse?.cancel(); dot.alpha = 1f
        val raw = texts.firstOrNull().orEmpty()
        if (raw.isBlank()) { finish(); return }
        handle(raw)
    }

    override fun onError(code: Int, message: String) {
        pulse?.cancel()
        setText(message)
        main.postDelayed({ finish() }, 1800)
    }

    private fun handle(raw: String) {
        setText(raw)
        val executor = CommandExecutor(lc(), VehicleProviders.create(prefs))
        pending?.let { p ->
            pending = null
            if (IntentParser.isYes(raw)) { deliver(executor.execute(p, confirmed = true)); return }
            if (IntentParser.isNo(raw)) { deliver(Outcome(OutcomeStatus.INFO, lc().getString(R.string.resp_cancelled))); return }
        }
        val intent = IntentParser.parse(raw)
        if (intent.type == IntentType.UNKNOWN && prefs.onlineAiEnabled && prefs.isAiConfigured) {
            setText(lc().getString(R.string.resp_ai_thinking))
            val lang = prefs.language
            io.execute {
                val res = OpenAiCompatibleProvider(prefs).ask(raw, lang)
                main.post {
                    deliver(res.fold(
                        onSuccess = { Outcome(OutcomeStatus.INFO, it) },
                        onFailure = { Outcome(OutcomeStatus.FAILED, lc().getString(R.string.resp_ai_error, it.message ?: "")) }
                    ))
                }
            }
            return
        }
        val outcome = executor.execute(intent, confirmed = false)
        if (outcome.status == OutcomeStatus.NEEDS_CONFIRMATION) pending = intent
        deliver(outcome)
    }

    private fun deliver(outcome: Outcome) {
        if (!active) return
        setText(outcome.message)
        // Only a verified success is announced as "done"; unverified/failed outcomes say what really happened.
        val spoken = if (outcome.status == OutcomeStatus.SUCCESS)
            t("Commande effectuée.", "Done.", "تم تنفيذ الأمر.") + " " + outcome.message
        else outcome.message
        val next = {
            if (outcome.status == OutcomeStatus.NEEDS_CONFIRMATION) main.postDelayed({ listen() }, 300)
            else main.postDelayed({ finish() }, 1500)
        }
        if (prefs.ttsEnabled) tts.speak(spoken, prefs.language, prefs.speechRate) { main.post { next() } }
        else next()
    }

    /** Close without resuming the wake engine (the main screen took over the mic). */
    fun finishSilently() = close(notify = false)

    fun finish() = close(notify = true)

    private fun close(notify: Boolean) {
        if (!active) return
        active = false
        main.removeCallbacksAndMessages(null)
        pulse?.cancel()
        speech.destroy()
        tts.stop()
        root?.let { v -> try { wm.removeView(v) } catch (_: Exception) {} }
        root = null
        if (notify) onFinished()
    }

    fun destroy() {
        finish()
        io.shutdownNow()
    }
}
