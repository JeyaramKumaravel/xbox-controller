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
 * Custom view for a controller button
 */
class ButtonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnButtonListener {
        fun onPressed(buttonId: String)
        fun onReleased(buttonId: String)
    }

    var listener: OnButtonListener? = null
    var buttonId: String = ""
    var buttonLabel: String = ""
        set(value) {
            field = value
            invalidate()
        }

    var buttonPressed: Boolean = false
        private set

    var hapticEnabled: Boolean = true
    var hapticIntensity: Int = 50

    private val normalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val pressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
    private var cornerRadius = 0f

    private var buttonColor: Int = 0
    private var pressedColor: Int = 0

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
        buttonColor = context.getColor(R.color.button_face)
        pressedColor = context.getColor(R.color.button_pressed)

        normalPaint.color = buttonColor
        pressedPaint.color = pressedColor
        borderPaint.color = context.getColor(R.color.button_border)
        textPaint.color = context.getColor(R.color.text_primary)
    }

    fun setButtonColor(color: Int) {
        buttonColor = color
        normalPaint.color = color
        invalidate()
    }

    fun setPressedColor(color: Int) {
        pressedColor = color
        pressedPaint.color = color
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rect.set(4f, 4f, w - 4f, h - 4f)
        cornerRadius = minOf(w, h) / 4f
        textPaint.textSize = minOf(w, h) / 2.5f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val paint = if (buttonPressed) pressedPaint else normalPaint
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)

        if (buttonLabel.isNotEmpty()) {
            val textY = (height / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
            canvas.drawText(buttonLabel, width / 2f, textY, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                updatePressedState(true)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                updatePressedState(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updatePressedState(pressed: Boolean) {
        if (buttonPressed == pressed) return

        buttonPressed = pressed
        invalidate()

        if (pressed) {
            performHapticFeedback()
            listener?.onPressed(buttonId)
        } else {
            listener?.onReleased(buttonId)
        }
    }

    private fun performHapticFeedback() {
        if (!hapticEnabled) return

        vibrator?.let { vib ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitude = (hapticIntensity * 2.55).toInt().coerceIn(1, 255)
                vib.vibrate(VibrationEffect.createOneShot(20, amplitude))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(20)
            }
        }
    }
}
