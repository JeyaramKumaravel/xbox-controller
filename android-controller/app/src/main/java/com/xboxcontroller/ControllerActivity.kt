package com.xboxcontroller

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import androidx.lifecycle.lifecycleScope
import com.xboxcontroller.controller.*
import com.xboxcontroller.data.ControllerInput
import com.xboxcontroller.data.SettingsRepository
import com.xboxcontroller.databinding.ActivityControllerBinding
import com.xboxcontroller.network.WebSocketClient
import com.xboxcontroller.network.XboxControllerApp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.abs

/**
 * Fullscreen controller activity with integrated trackpad, keyboard, and edit mode
 */
class ControllerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityControllerBinding
    private lateinit var settingsRepository: SettingsRepository

    private val webSocketClient get() = XboxControllerApp.webSocketClient

    private val controllerInput = ControllerInput()
    private var playerId: Int = 1

    // Trackpad
    private lateinit var trackpadArea: FrameLayout
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var pointerCount = 0
    private lateinit var gestureDetector: GestureDetectorCompat

    // Keyboard
    private lateinit var keyboardLayout: LinearLayout
    private lateinit var keyboardInput: EditText
    private lateinit var keyboardButton: Button
    private lateinit var closeKeyboardButton: Button

    // Edit mode
    private var isEditMode = false
    private lateinit var editModeOverlay: LinearLayout
    private lateinit var rootContainer: FrameLayout
    private val draggableElements = mutableListOf<View>()
    private val originalPositions = mutableMapOf<Int, Pair<Float, Float>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityControllerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsRepository = SettingsRepository(this)
        playerId = intent.getIntExtra("player_id", 1)

        rootContainer = findViewById(R.id.rootContainer)
        trackpadArea = findViewById(R.id.trackpadArea)
        keyboardLayout = findViewById(R.id.keyboardLayout)
        keyboardInput = findViewById(R.id.keyboardInput)
        keyboardButton = findViewById(R.id.keyboardButton)
        closeKeyboardButton = findViewById(R.id.closeKeyboardButton)
        editModeOverlay = findViewById(R.id.editModeOverlay)

        // Collect draggable elements
        draggableElements.addAll(listOf(
            binding.leftJoystick,
            binding.rightJoystick,
            binding.dpad,
            findViewById(R.id.abxyContainer),
            binding.triggerLT,
            binding.triggerRT,
            binding.buttonLB,
            binding.buttonRB,
            binding.buttonL3,
            binding.buttonR3,
            findViewById(R.id.menuButtonsGroup),
            findViewById(R.id.trackpadGroup),
            findViewById(R.id.settingsGroup)
        ))

        hideSystemUI()
        setupButtons()
        setupJoysticks()
        setupTriggers()
        setupDPad()
        setupTrackpad()
        setupKeyboard()
        setupMenuButtons()
        setupEditMode()
        observeConnection()
        loadSettings()
        loadSavedLayout()
        
        // Check if launched in edit mode from settings
        if (intent.getBooleanExtra("edit_mode", false)) {
            // Delay to let layout settle
            rootContainer.post { enterEditMode() }
        }
    }

    private fun setupEditMode() {
        findViewById<Button>(R.id.saveLayoutButton).setOnClickListener {
            saveLayout()
            exitEditMode()
        }

        findViewById<Button>(R.id.resetLayoutButton).setOnClickListener {
            resetLayout()
        }

        findViewById<Button>(R.id.cancelEditButton).setOnClickListener {
            cancelEdit()
            exitEditMode()
        }
    }

    private fun enterEditMode() {
        isEditMode = true
        editModeOverlay.visibility = View.VISIBLE

        // Save original positions
        originalPositions.clear()
        draggableElements.forEach { view ->
            originalPositions[view.id] = Pair(view.x, view.y)
        }

        // Make elements draggable
        draggableElements.forEach { view ->
            makeDraggable(view)
        }

        Toast.makeText(this, "Edit mode: Drag elements to reposition", Toast.LENGTH_SHORT).show()
    }

    private fun exitEditMode() {
        isEditMode = false
        editModeOverlay.visibility = View.GONE

        // Remove drag listeners
        draggableElements.forEach { view ->
            view.setOnTouchListener(null)
        }

        // Re-setup touch listeners for normal operation
        setupTrackpad()
    }

    private fun cancelEdit() {
        // Restore original positions
        originalPositions.forEach { (id, pos) ->
            draggableElements.find { it.id == id }?.let { view ->
                view.x = pos.first
                view.y = pos.second
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun makeDraggable(view: View) {
        var dX = 0f
        var dY = 0f

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = v.x - event.rawX
                    dY = v.y - event.rawY
                    v.alpha = 0.7f
                    v.elevation = 20f
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    v.x = (event.rawX + dX).coerceIn(0f, rootContainer.width - v.width.toFloat())
                    v.y = (event.rawY + dY).coerceIn(0f, rootContainer.height - v.height.toFloat())
                    true
                }
                MotionEvent.ACTION_UP -> {
                    v.alpha = 1f
                    v.elevation = 4f
                    true
                }
                else -> false
            }
        }
    }

    private fun saveLayout() {
        lifecycleScope.launch {
            draggableElements.forEach { view ->
                val name = getElementName(view.id)
                if (name.isNotEmpty()) {
                    settingsRepository.saveElementLayout(name, view.x, view.y, 1.0f)
                }
            }
            settingsRepository.setCustomLayout(true)
            Toast.makeText(this@ControllerActivity, "Layout saved!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resetLayout() {
        lifecycleScope.launch {
            settingsRepository.resetLayout()
            Toast.makeText(this@ControllerActivity, "Layout reset - restart app to apply", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadSavedLayout() {
        // Wait for layout to be ready before applying saved positions
        rootContainer.post {
            lifecycleScope.launch {
                val layout = settingsRepository.layout.first()
                if (layout.isCustom) {
                    // Apply saved positions to all draggable elements
                    applyElementPosition(binding.leftJoystick, layout.leftJoystick.x, layout.leftJoystick.y)
                    applyElementPosition(binding.rightJoystick, layout.rightJoystick.x, layout.rightJoystick.y)
                    applyElementPosition(binding.dpad, layout.dpad.x, layout.dpad.y)
                    applyElementPosition(binding.triggerLT, layout.triggerLT.x, layout.triggerLT.y)
                    applyElementPosition(binding.triggerRT, layout.triggerRT.x, layout.triggerRT.y)
                    applyElementPosition(binding.buttonLB, layout.buttonLB.x, layout.buttonLB.y)
                    applyElementPosition(binding.buttonRB, layout.buttonRB.x, layout.buttonRB.y)
                    applyElementPosition(binding.buttonL3, layout.buttonL3.x, layout.buttonL3.y)
                    applyElementPosition(binding.buttonR3, layout.buttonR3.x, layout.buttonR3.y)
                    
                    // Apply to group containers
                    findViewById<View>(R.id.abxyContainer)?.let { 
                        applyElementPosition(it, layout.abxyContainer.x, layout.abxyContainer.y) 
                    }
                    findViewById<View>(R.id.menuButtonsGroup)?.let { 
                        applyElementPosition(it, layout.menuButtonsGroup.x, layout.menuButtonsGroup.y) 
                    }
                    findViewById<View>(R.id.trackpadGroup)?.let { 
                        applyElementPosition(it, layout.trackpadGroup.x, layout.trackpadGroup.y) 
                    }
                    findViewById<View>(R.id.settingsGroup)?.let { 
                        applyElementPosition(it, layout.settingsGroup.x, layout.settingsGroup.y) 
                    }
                }
            }
        }
    }

    private fun applyElementPosition(view: View, x: Float, y: Float) {
        if (x != 0f || y != 0f) {
            view.x = x
            view.y = y
        }
    }

    private fun getElementName(id: Int): String {
        return when (id) {
            R.id.leftJoystick -> "left_joystick"
            R.id.rightJoystick -> "right_joystick"
            R.id.dpad -> "dpad"
            R.id.abxyContainer -> "abxy"
            R.id.triggerLT -> "trigger_lt"
            R.id.triggerRT -> "trigger_rt"
            R.id.buttonLB -> "button_lb"
            R.id.buttonRB -> "button_rb"
            R.id.buttonL3 -> "button_l3"
            R.id.buttonR3 -> "button_r3"
            R.id.menuButtonsGroup -> "menu_buttons"
            R.id.trackpadGroup -> "trackpad_group"
            R.id.settingsGroup -> "settings_group"
            else -> ""
        }
    }

    private fun setupMenuButtons() {
        findViewById<Button>(R.id.settingsButton).setOnClickListener {
            startActivity(android.content.Intent(this, com.xboxcontroller.settings.SettingsActivity::class.java))
        }

        findViewById<Button>(R.id.disconnectButton).setOnClickListener {
            XboxControllerApp.disconnect()
            finish()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun observeConnection() {
        lifecycleScope.launch {
            webSocketClient.connectionState.collectLatest { state ->
                when (state) {
                    is WebSocketClient.ConnectionState.Connected -> {
                        binding.connectionStatus.text = "Player ${state.playerId}"
                        binding.connectionStatus.setTextColor(getColor(R.color.accent))
                    }
                    is WebSocketClient.ConnectionState.Disconnected -> {
                        binding.connectionStatus.text = "Disconnected"
                        binding.connectionStatus.setTextColor(getColor(R.color.text_secondary))
                    }
                    is WebSocketClient.ConnectionState.Connecting -> {
                        binding.connectionStatus.text = "Connecting..."
                        binding.connectionStatus.setTextColor(getColor(R.color.button_y))
                    }
                    is WebSocketClient.ConnectionState.Error -> {
                        binding.connectionStatus.text = "Error"
                        binding.connectionStatus.setTextColor(getColor(R.color.button_b))
                        Toast.makeText(this@ControllerActivity, state.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun loadSettings() {
        lifecycleScope.launch {
            settingsRepository.settings.collectLatest { settings ->
                applyHapticSettings(settings.hapticFeedback, settings.hapticIntensity)

                binding.leftJoystick.deadzone = settings.deadzone
                binding.rightJoystick.deadzone = settings.deadzone

                trackpadArea.visibility = if (settings.showTrackpad) View.VISIBLE else View.GONE
                keyboardButton.visibility = if (settings.showKeyboard) View.VISIBLE else View.GONE
            }
        }
    }

    private fun applyHapticSettings(enabled: Boolean, intensity: Int) {
        listOf(
            binding.buttonA, binding.buttonB, binding.buttonX, binding.buttonY,
            binding.buttonLB, binding.buttonRB,
            binding.buttonStart, binding.buttonBack, binding.buttonGuide,
            binding.buttonL3, binding.buttonR3
        ).forEach { button ->
            button.hapticEnabled = enabled
            button.hapticIntensity = intensity
        }

        binding.triggerLT.hapticEnabled = enabled
        binding.triggerRT.hapticEnabled = enabled
    }

    private fun setupButtons() {
        configureButton(binding.buttonA, "a", "A", getColor(R.color.button_a))
        configureButton(binding.buttonB, "b", "B", getColor(R.color.button_b))
        configureButton(binding.buttonX, "x", "X", getColor(R.color.button_x))
        configureButton(binding.buttonY, "y", "Y", getColor(R.color.button_y))
        configureButton(binding.buttonLB, "lb", "LB")
        configureButton(binding.buttonRB, "rb", "RB")
        configureButton(binding.buttonStart, "start", "☰")
        configureButton(binding.buttonBack, "back", "⧉")
        configureButton(binding.buttonGuide, "guide", "Ⓧ")
        configureButton(binding.buttonL3, "l3", "L3")
        configureButton(binding.buttonR3, "r3", "R3")

        val buttonListener = object : ButtonView.OnButtonListener {
            override fun onPressed(buttonId: String) {
                if (!isEditMode) updateButtonState(buttonId, true)
            }
            override fun onReleased(buttonId: String) {
                if (!isEditMode) updateButtonState(buttonId, false)
            }
        }

        listOf(
            binding.buttonA, binding.buttonB, binding.buttonX, binding.buttonY,
            binding.buttonLB, binding.buttonRB,
            binding.buttonStart, binding.buttonBack, binding.buttonGuide,
            binding.buttonL3, binding.buttonR3
        ).forEach { it.listener = buttonListener }
    }

    private fun configureButton(button: ButtonView, id: String, label: String, color: Int? = null) {
        button.buttonId = id
        button.buttonLabel = label
        color?.let { button.setButtonColor(it) }
    }

    private fun updateButtonState(buttonId: String, pressed: Boolean) {
        when (buttonId) {
            "a" -> controllerInput.buttonA = pressed
            "b" -> controllerInput.buttonB = pressed
            "x" -> controllerInput.buttonX = pressed
            "y" -> controllerInput.buttonY = pressed
            "lb" -> controllerInput.buttonLB = pressed
            "rb" -> controllerInput.buttonRB = pressed
            "start" -> controllerInput.buttonStart = pressed
            "back" -> controllerInput.buttonBack = pressed
            "guide" -> controllerInput.buttonGuide = pressed
            "l3" -> controllerInput.buttonL3 = pressed
            "r3" -> controllerInput.buttonR3 = pressed
        }
        sendInput()
    }

    private fun setupJoysticks() {
        binding.leftJoystick.listener = object : JoystickView.OnJoystickMoveListener {
            override fun onMove(x: Float, y: Float) {
                if (!isEditMode) {
                    controllerInput.leftStickX = x
                    controllerInput.leftStickY = -y
                    sendInput()
                }
            }
            override fun onRelease() {
                if (!isEditMode) {
                    controllerInput.leftStickX = 0f
                    controllerInput.leftStickY = 0f
                    sendInput()
                }
            }
        }

        binding.rightJoystick.listener = object : JoystickView.OnJoystickMoveListener {
            override fun onMove(x: Float, y: Float) {
                if (!isEditMode) {
                    controllerInput.rightStickX = x
                    controllerInput.rightStickY = -y
                    sendInput()
                }
            }
            override fun onRelease() {
                if (!isEditMode) {
                    controllerInput.rightStickX = 0f
                    controllerInput.rightStickY = 0f
                    sendInput()
                }
            }
        }
    }

    private fun setupTriggers() {
        binding.triggerLT.triggerId = "lt"
        binding.triggerLT.triggerLabel = "LT"
        binding.triggerLT.listener = object : TriggerView.OnTriggerListener {
            override fun onValueChanged(triggerId: String, value: Float) {
                if (!isEditMode) {
                    controllerInput.triggerLT = value
                    sendInput()
                }
            }
        }

        binding.triggerRT.triggerId = "rt"
        binding.triggerRT.triggerLabel = "RT"
        binding.triggerRT.listener = object : TriggerView.OnTriggerListener {
            override fun onValueChanged(triggerId: String, value: Float) {
                if (!isEditMode) {
                    controllerInput.triggerRT = value
                    sendInput()
                }
            }
        }
    }

    private fun setupDPad() {
        binding.dpad.listener = object : DPadView.OnDPadListener {
            override fun onDirectionChanged(up: Boolean, down: Boolean, left: Boolean, right: Boolean) {
                if (!isEditMode) {
                    controllerInput.dpadUp = up
                    controllerInput.dpadDown = down
                    controllerInput.dpadLeft = left
                    controllerInput.dpadRight = right
                    sendInput()
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTrackpad() {
        gestureDetector = GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (!isEditMode) sendMouseClick("left")
                return true
            }
            override fun onLongPress(e: MotionEvent) {
                if (!isEditMode) sendMouseClick("left")
            }
        })

        trackpadArea.setOnTouchListener { _, event ->
            if (isEditMode) return@setOnTouchListener false

            gestureDetector.onTouchEvent(event)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pointerCount = 1
                    lastTouchX = event.x
                    lastTouchY = event.y
                    true
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    pointerCount = event.pointerCount
                    if (pointerCount == 2) sendMouseClick("right")
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (pointerCount == 1) {
                        val deltaX = event.x - lastTouchX
                        val deltaY = event.y - lastTouchY

                        if (abs(deltaX) > 1 || abs(deltaY) > 1) {
                            sendMouseMove(deltaX, deltaY)
                            lastTouchX = event.x
                            lastTouchY = event.y
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    pointerCount = maxOf(0, event.pointerCount - 1)
                    true
                }
                else -> false
            }
        }
    }

    private fun setupKeyboard() {
        keyboardButton.setOnClickListener {
            if (!isEditMode) {
                keyboardLayout.visibility = View.VISIBLE
                keyboardInput.requestFocus()
                keyboardInput.text.clear()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(keyboardInput, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        closeKeyboardButton.setOnClickListener {
            keyboardLayout.visibility = View.GONE
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(keyboardInput.windowToken, 0)
        }

        keyboardInput.addTextChangedListener(object : TextWatcher {
            private var previousText = ""

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                previousText = s?.toString() ?: ""
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val currentText = s?.toString() ?: ""
                if (currentText.length > previousText.length) {
                    val newChar = currentText.substring(previousText.length)
                    if (newChar.isNotEmpty()) sendKeyboardType(newChar)
                } else if (currentText.length < previousText.length) {
                    sendKeyboardKey("backspace")
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        keyboardInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                sendKeyboardKey("enter")
                keyboardInput.text.clear()
                true
            } else false
        }
    }

    private fun sendMouseMove(dx: Float, dy: Float) {
        val json = JSONObject().apply {
            put("type", "mouse")
            put("action", "move")
            put("dx", dx)
            put("dy", dy)
        }
        webSocketClient.sendRaw(json.toString())
    }

    private fun sendMouseClick(button: String) {
        val json = JSONObject().apply {
            put("type", "mouse")
            put("action", "click")
            put("button", button)
        }
        webSocketClient.sendRaw(json.toString())
    }

    private fun sendKeyboardType(text: String) {
        val json = JSONObject().apply {
            put("type", "keyboard")
            put("action", "type")
            put("text", text)
        }
        webSocketClient.sendRaw(json.toString())
    }

    private fun sendKeyboardKey(key: String) {
        val json = JSONObject().apply {
            put("type", "keyboard")
            put("action", "key")
            put("key", key)
        }
        webSocketClient.sendRaw(json.toString())
    }

    private fun sendInput() {
        webSocketClient.sendInput(controllerInput.copy())
    }

    private fun hideSystemUI() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
    }
}
