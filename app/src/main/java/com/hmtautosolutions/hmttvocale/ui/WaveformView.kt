package com.hmtautosolutions.hmttvocale.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.hmtautosolutions.hmttvocale.R
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Lightweight circular waveform. No allocations in onDraw; slower refresh when idle. */
class WaveformView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var state: AssistantState = AssistantState.IDLE
        set(value) { field = value; invalidate() }

    private val red = ContextCompat.getColor(context, R.color.hmt_red)
    private val redDim = ContextCompat.getColor(context, R.color.hmt_red_dim)
    private val white = ContextCompat.getColor(context, R.color.hmt_white)
    private val grey = ContextCompat.getColor(context, R.color.hmt_grey)
    private val errorColor = ContextCompat.getColor(context, R.color.hmt_error)

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }

    private val bars = 72
    private var phase = 0f
    private var level = 0f
    private var targetLevel = 0f

    /** RMS in dB from the recognizer (roughly -2..10). */
    fun setLevel(rmsDb: Float) {
        targetLevel = ((rmsDb + 2f) / 12f).coerceIn(0f, 1f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val base = min(width, height) * 0.27f
        val density = resources.displayMetrics.density

        level += (targetLevel - level) * 0.25f
        if (state != AssistantState.LISTENING) targetLevel *= 0.9f

        val color = when (state) {
            AssistantState.IDLE -> redDim
            AssistantState.LISTENING, AssistantState.SPEAKING -> red
            AssistantState.PROCESSING -> white
            AssistantState.SUCCESS -> white
            AssistantState.ERROR -> errorColor
        }
        val speed = when (state) {
            AssistantState.IDLE -> 0.02f
            AssistantState.PROCESSING -> 0.14f
            else -> 0.08f
        }
        phase += speed

        val breathe = 0.5f + 0.5f * sin(phase)
        val energy = when (state) {
            AssistantState.IDLE -> 0.08f + 0.04f * breathe
            AssistantState.LISTENING -> 0.15f + level * 0.85f
            AssistantState.SPEAKING -> 0.35f + 0.3f * breathe
            AssistantState.PROCESSING -> 0.3f
            AssistantState.SUCCESS -> 0.25f
            AssistantState.ERROR -> 0.2f
        }

        glowPaint.color = color
        glowPaint.alpha = (30 + 50 * energy).toInt()
        canvas.drawCircle(cx, cy, base * (0.92f + 0.08f * energy), glowPaint)

        ringPaint.color = color
        ringPaint.strokeWidth = 3f * density
        ringPaint.alpha = 255
        canvas.drawCircle(cx, cy, base, ringPaint)

        barPaint.color = color
        barPaint.strokeWidth = 3.5f * density
        val maxLen = base * 0.55f
        for (i in 0 until bars) {
            val a = (i.toFloat() / bars) * 2f * Math.PI.toFloat()
            val wave = if (state == AssistantState.PROCESSING) {
                0.5f + 0.5f * sin(a * 2f - phase * 3f)
            } else {
                0.5f + 0.25f * sin(a * 5f + phase * 2f) + 0.25f * sin(a * 3f - phase * 1.3f)
            }
            val len = maxLen * (0.12f + energy * wave)
            val r0 = base + 8f * density
            val ca = cos(a); val sa = sin(a)
            barPaint.alpha = (110 + 145 * wave).toInt().coerceAtMost(255)
            canvas.drawLine(cx + ca * r0, cy + sa * r0, cx + ca * (r0 + len), cy + sa * (r0 + len), barPaint)
        }

        if (state == AssistantState.IDLE) postInvalidateDelayed(50) else postInvalidateOnAnimation()
    }

    @Suppress("unused")
    private fun unusedGrey() = grey
}
