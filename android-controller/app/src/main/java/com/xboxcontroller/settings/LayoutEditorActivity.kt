package com.xboxcontroller.settings

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.xboxcontroller.R
import com.xboxcontroller.data.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Layout editor for customizing controller element positions
 */
class LayoutEditorActivity : AppCompatActivity() {
    
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var container: FrameLayout
    
    private var selectedElement: View? = null
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    
    // Map of element names to views
    private val elementViews = mutableMapOf<String, View>()
    private val elementPositions = mutableMapOf<String, Pair<Float, Float>>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_layout_editor)
        
        settingsRepository = SettingsRepository(this)
        container = findViewById(R.id.editorContainer)
        
        setupElements()
        setupButtons()
        loadSavedLayout()
    }
    
    private fun setupElements() {
        // Create draggable elements for each controller part
        createDraggableElement("left_joystick", "Left\nJoystick", 140, 140, 50f, 200f)
        createDraggableElement("right_joystick", "Right\nJoystick", 140, 140, 600f, 350f)
        createDraggableElement("dpad", "D-Pad", 120, 120, 50f, 380f)
        createDraggableElement("abxy", "ABXY", 140, 140, 600f, 150f)
        createDraggableElement("trigger_lt", "LT", 80, 50, 50f, 50f)
        createDraggableElement("trigger_rt", "RT", 80, 50, 680f, 50f)
        createDraggableElement("button_lb", "LB", 80, 40, 50f, 110f)
        createDraggableElement("button_rb", "RB", 80, 40, 680f, 110f)
        createDraggableElement("button_l3", "L3", 48, 48, 130f, 160f)
        createDraggableElement("button_r3", "R3", 48, 48, 610f, 310f)
        createDraggableElement("menu_buttons", "Menu", 150, 50, 300f, 50f)
        createDraggableElement("trackpad_group", "Trackpad\n+Keyboard", 180, 120, 300f, 180f)
        createDraggableElement("settings_group", "Settings", 100, 40, 340f, 320f)
    }
    
    @SuppressLint("ClickableViewAccessibility")
    private fun createDraggableElement(name: String, label: String, width: Int, height: Int, defaultX: Float, defaultY: Float) {
        val element = TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(resources.getColor(R.color.text_primary, null))
            setBackgroundResource(R.drawable.editor_element_background)
            gravity = android.view.Gravity.CENTER
            setPadding(8, 8, 8, 8)
            
            layoutParams = FrameLayout.LayoutParams(
                (width * resources.displayMetrics.density).toInt(),
                (height * resources.displayMetrics.density).toInt()
            )
            
            x = defaultX * resources.displayMetrics.density
            y = defaultY * resources.displayMetrics.density
        }
        
        element.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    selectedElement = view
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                    view.alpha = 0.7f
                    view.elevation = 10f
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - lastTouchX
                    val dy = event.rawY - lastTouchY
                    
                    view.x = (view.x + dx).coerceIn(0f, container.width - view.width.toFloat())
                    view.y = (view.y + dy).coerceIn(0f, container.height - view.height.toFloat())
                    
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                    
                    elementPositions[name] = Pair(view.x / resources.displayMetrics.density, view.y / resources.displayMetrics.density)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    view.alpha = 1f
                    view.elevation = 4f
                    selectedElement = null
                    true
                }
                else -> false
            }
        }
        
        container.addView(element)
        elementViews[name] = element
        elementPositions[name] = Pair(defaultX, defaultY)
    }
    
    private fun setupButtons() {
        findViewById<Button>(R.id.saveButton).setOnClickListener {
            saveLayout()
        }
        
        findViewById<Button>(R.id.resetButton).setOnClickListener {
            resetLayout()
        }
        
        findViewById<Button>(R.id.cancelButton).setOnClickListener {
            finish()
        }
    }
    
    private fun loadSavedLayout() {
        lifecycleScope.launch {
            val layout = settingsRepository.layout.first()
            if (layout.isCustom) {
                // Apply saved positions
                applyPosition("left_joystick", layout.leftJoystick.x, layout.leftJoystick.y)
                applyPosition("right_joystick", layout.rightJoystick.x, layout.rightJoystick.y)
                applyPosition("dpad", layout.dpad.x, layout.dpad.y)
                applyPosition("abxy", layout.abxyContainer.x, layout.abxyContainer.y)
                applyPosition("menu_buttons", layout.menuButtonsGroup.x, layout.menuButtonsGroup.y)
                applyPosition("trackpad_group", layout.trackpadGroup.x, layout.trackpadGroup.y)
            }
        }
    }
    
    private fun applyPosition(name: String, x: Float, y: Float) {
        elementViews[name]?.let { view ->
            if (x != 0f || y != 0f) {
                view.x = x * resources.displayMetrics.density
                view.y = y * resources.displayMetrics.density
                elementPositions[name] = Pair(x, y)
            }
        }
    }
    
    private fun saveLayout() {
        lifecycleScope.launch {
            // Save all element positions
            elementPositions.forEach { (name, pos) ->
                settingsRepository.saveElementLayout(name, pos.first, pos.second, 1.0f)
            }
            settingsRepository.setCustomLayout(true)
            
            Toast.makeText(this@LayoutEditorActivity, "Layout saved!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
    private fun resetLayout() {
        lifecycleScope.launch {
            settingsRepository.resetLayout()
            Toast.makeText(this@LayoutEditorActivity, "Layout reset to default", Toast.LENGTH_SHORT).show()
            
            // Reset visual positions
            container.removeAllViews()
            elementViews.clear()
            elementPositions.clear()
            setupElements()
        }
    }
}
