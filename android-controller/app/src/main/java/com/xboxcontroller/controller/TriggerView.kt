package com.xboxcontroller.controller

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.xboxcontroller.R

/**
 * Custom trigger button (LT/RT) with analog pressure support
 */
class TriggerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnTriggerListener {
        fun onValueChanged(triggerId: String, value: Float)
    }

    var listener: OnTriggerListener? = null
    var triggerId: String = ""
    var triggerLabel: String = ""
        set(value) {
            field = value
            invalidate()
        }

    var value: Float = 0f
        private set

    var hapticEnabled: Boolean = true

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private val rect = RectF()
    private val fillRect = RectF()
    private var cornerRadius = 0f
    private var touchId = -1

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    init {
        backgroundPaint.color = context.getColor(R.color.button_face)
        fillPaint.color = context.getColor(R.color.button_pressed)
        borderPaint.color = context.getColor(R.color.button_border)
        textPaint.color = context.getColor(R.color.text_primary)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rect.set(4f, 4f, w - 4f, h - 4f)
        cornerRadius = 12f
        textPaint.textSize = h / 2.5f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw background
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, backgroundPaint)

        // Draw fill based on value
        if (value > 0) {
            fillRect.set(rect.left, rect.top, rect.left + rect.width() * value, rect.bottom)
            canvas.save()
            canvas.clipRect(fillRect)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, fillPaint)
            canvas.restore()
        }

        // Draw border
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)

        // Draw label
        if (triggerLabel.isNotEmpty()) {
            val textY = (height / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
            canvas.drawText(triggerLabel, width / 2f, textY, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchId = event.getPointerId(0)
                setValue(1f)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                setValue(0f)
                touchId = -1
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun setValue(newValue: Float) {
        val oldValue = value
        value = newValue.coerceIn(0f, 1f)

        if (oldValue == 0f && value > 0f && hapticEnabled) {
            performHapticFeedback()
        }

        listener?.onValueChanged(triggerId, value)
        invalidate()
    }

    private fun performHapticFeedback() {
        vibrator?.let { vib ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createOneShot(15, 100))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(15)
            }
        }
    }
}
