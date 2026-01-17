package com.xboxcontroller.controller

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.xboxcontroller.R
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Custom view for analog joystick input
 */
class JoystickView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnJoystickMoveListener {
        fun onMove(x: Float, y: Float)
        fun onRelease()
    }

    var listener: OnJoystickMoveListener? = null
    var deadzone: Float = 0.15f
    var sensitivity: Float = 1.0f

    // Position values (-1.0 to 1.0)
    var positionX: Float = 0f
        private set
    var positionY: Float = 0f
        private set

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private var centerX = 0f
    private var centerY = 0f
    private var baseRadius = 0f
    private var thumbRadius = 0f

    private val thumbPosition = PointF()
    private var touchId: Int = -1

    init {
        basePaint.color = context.getColor(R.color.joystick_bg)
        thumbPaint.color = context.getColor(R.color.joystick_thumb)
        borderPaint.color = context.getColor(R.color.button_border)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        baseRadius = min(w, h) / 2f - 8f
        thumbRadius = baseRadius * 0.4f
        thumbPosition.set(centerX, centerY)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw base circle
        canvas.drawCircle(centerX, centerY, baseRadius, basePaint)
        canvas.drawCircle(centerX, centerY, baseRadius, borderPaint)

        // Draw thumb
        canvas.drawCircle(thumbPosition.x, thumbPosition.y, thumbRadius, thumbPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchId = event.getPointerId(0)
                updateThumbPosition(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = event.findPointerIndex(touchId)
                if (pointerIndex != -1) {
                    updateThumbPosition(event.getX(pointerIndex), event.getY(pointerIndex))
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                resetThumbPosition()
                touchId = -1
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                if (event.getPointerId(pointerIndex) == touchId) {
                    resetThumbPosition()
                    touchId = -1
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateThumbPosition(touchX: Float, touchY: Float) {
        val deltaX = touchX - centerX
        val deltaY = touchY - centerY
        val distance = sqrt(deltaX.pow(2) + deltaY.pow(2))
        val maxDistance = baseRadius - thumbRadius / 2

        if (distance > maxDistance) {
            val angle = atan2(deltaY, deltaX)
            thumbPosition.x = centerX + cos(angle) * maxDistance
            thumbPosition.y = centerY + sin(angle) * maxDistance
        } else {
            thumbPosition.x = touchX
            thumbPosition.y = touchY
        }

        // Calculate normalized position (-1 to 1)
        val normalizedX = (thumbPosition.x - centerX) / maxDistance
        val normalizedY = (thumbPosition.y - centerY) / maxDistance

        // Apply deadzone
        val magnitude = sqrt(normalizedX.pow(2) + normalizedY.pow(2))
        if (magnitude < deadzone) {
            positionX = 0f
            positionY = 0f
        } else {
            // Scale to use full range after deadzone
            val scaledMagnitude = (magnitude - deadzone) / (1f - deadzone)
            val angle = atan2(normalizedY, normalizedX)
            // Apply sensitivity multiplier and clamp to valid range
            positionX = (cos(angle) * scaledMagnitude * sensitivity).coerceIn(-1f, 1f)
            positionY = (sin(angle) * scaledMagnitude * sensitivity).coerceIn(-1f, 1f)
        }

        listener?.onMove(positionX, positionY)
        invalidate()
    }

    private fun resetThumbPosition() {
        thumbPosition.set(centerX, centerY)
        positionX = 0f
        positionY = 0f
        listener?.onRelease()
        invalidate()
    }
}
