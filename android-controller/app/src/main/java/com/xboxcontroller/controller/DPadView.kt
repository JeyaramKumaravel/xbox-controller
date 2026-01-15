package com.xboxcontroller.controller

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.xboxcontroller.R

/**
 * Custom D-Pad view with 8-direction support
 */
class DPadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnDPadListener {
        fun onDirectionChanged(up: Boolean, down: Boolean, left: Boolean, right: Boolean)
    }

    var listener: OnDPadListener? = null

    var upPressed = false
        private set
    var downPressed = false
        private set
    var leftPressed = false
        private set
    var rightPressed = false
        private set

    private val normalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val pressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private var buttonWidth = 0f
    private var buttonHeight = 0f
    private var centerX = 0f
    private var centerY = 0f
    private var touchId = -1

    init {
        normalPaint.color = context.getColor(R.color.button_face)
        pressedPaint.color = context.getColor(R.color.button_pressed)
        borderPaint.color = context.getColor(R.color.button_border)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buttonWidth = w / 3f
        buttonHeight = h / 3f
        centerX = w / 2f
        centerY = h / 2f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw Up button
        drawButton(canvas, centerX - buttonWidth / 2, 0f, buttonWidth, buttonHeight, upPressed)

        // Draw Down button
        drawButton(canvas, centerX - buttonWidth / 2, height - buttonHeight, buttonWidth, buttonHeight, downPressed)

        // Draw Left button
        drawButton(canvas, 0f, centerY - buttonHeight / 2, buttonWidth, buttonHeight, leftPressed)

        // Draw Right button
        drawButton(canvas, width - buttonWidth, centerY - buttonHeight / 2, buttonWidth, buttonHeight, rightPressed)

        // Draw center (decorative)
        val centerPaint = Paint(normalPaint)
        centerPaint.color = context.getColor(R.color.surface)
        canvas.drawRect(
            centerX - buttonWidth / 2,
            centerY - buttonHeight / 2,
            centerX + buttonWidth / 2,
            centerY + buttonHeight / 2,
            centerPaint
        )
    }

    private fun drawButton(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, pressed: Boolean) {
        val paint = if (pressed) pressedPaint else normalPaint
        canvas.drawRect(x, y, x + w, y + h, paint)
        canvas.drawRect(x, y, x + w, y + h, borderPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchId = event.getPointerId(0)
                updateDirection(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = event.findPointerIndex(touchId)
                if (pointerIndex != -1) {
                    updateDirection(event.getX(pointerIndex), event.getY(pointerIndex))
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                resetDirection()
                touchId = -1
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateDirection(x: Float, y: Float) {
        val oldUp = upPressed
        val oldDown = downPressed
        val oldLeft = leftPressed
        val oldRight = rightPressed

        // Calculate which direction is pressed based on position
        val relX = x - centerX
        val relY = y - centerY

        // Threshold for activation
        val threshold = buttonWidth / 2

        upPressed = relY < -threshold
        downPressed = relY > threshold
        leftPressed = relX < -threshold
        rightPressed = relX > threshold

        if (upPressed != oldUp || downPressed != oldDown || 
            leftPressed != oldLeft || rightPressed != oldRight) {
            listener?.onDirectionChanged(upPressed, downPressed, leftPressed, rightPressed)
            invalidate()
        }
    }

    private fun resetDirection() {
        if (upPressed || downPressed || leftPressed || rightPressed) {
            upPressed = false
            downPressed = false
            leftPressed = false
            rightPressed = false
            listener?.onDirectionChanged(false, false, false, false)
            invalidate()
        }
    }
}
