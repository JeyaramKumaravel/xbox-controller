package com.xboxcontroller.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Controller settings
 */
data class ControllerSettings(
    val hapticFeedback: Boolean = true,
    val hapticIntensity: Int = 50,
    val deadzone: Float = 0.15f,
    val lastServerHost: String = "",
    val lastServerPort: Int = 8765,
    val showTrackpad: Boolean = true,
    val showKeyboard: Boolean = true
)

/**
 * Position and scale for a controller element
 */
data class ElementLayout(
    val x: Float = 0f,
    val y: Float = 0f,
    val scale: Float = 1.0f
)

/**
 * Custom layout for all controller elements
 */
data class ControllerLayout(
    val leftJoystick: ElementLayout = ElementLayout(),
    val rightJoystick: ElementLayout = ElementLayout(),
    val dpad: ElementLayout = ElementLayout(),
    val abxyContainer: ElementLayout = ElementLayout(),
    val buttonLB: ElementLayout = ElementLayout(),
    val buttonRB: ElementLayout = ElementLayout(),
    val triggerLT: ElementLayout = ElementLayout(),
    val triggerRT: ElementLayout = ElementLayout(),
    val buttonL3: ElementLayout = ElementLayout(),
    val buttonR3: ElementLayout = ElementLayout(),
    val menuButtonsGroup: ElementLayout = ElementLayout(),
    val trackpadGroup: ElementLayout = ElementLayout(),
    val settingsGroup: ElementLayout = ElementLayout(),
    val isCustom: Boolean = false
)

class SettingsRepository(private val context: Context) {
    
    private object Keys {
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        val HAPTIC_INTENSITY = intPreferencesKey("haptic_intensity")
        val DEADZONE = floatPreferencesKey("deadzone")
        val LAST_SERVER_HOST = stringPreferencesKey("last_server_host")
        val LAST_SERVER_PORT = intPreferencesKey("last_server_port")
        val SHOW_TRACKPAD = booleanPreferencesKey("show_trackpad")
        val SHOW_KEYBOARD = booleanPreferencesKey("show_keyboard")
        val USE_CUSTOM_LAYOUT = booleanPreferencesKey("use_custom_layout")
        
        // Layout positions (stored as JSON-like strings)
        fun elementX(name: String) = floatPreferencesKey("${name}_x")
        fun elementY(name: String) = floatPreferencesKey("${name}_y")
        fun elementScale(name: String) = floatPreferencesKey("${name}_scale")
    }
    
    val settings: Flow<ControllerSettings> = context.dataStore.data.map { prefs ->
        ControllerSettings(
            hapticFeedback = prefs[Keys.HAPTIC_FEEDBACK] ?: true,
            hapticIntensity = prefs[Keys.HAPTIC_INTENSITY] ?: 50,
            deadzone = prefs[Keys.DEADZONE] ?: 0.15f,
            lastServerHost = prefs[Keys.LAST_SERVER_HOST] ?: "",
            lastServerPort = prefs[Keys.LAST_SERVER_PORT] ?: 8765,
            showTrackpad = prefs[Keys.SHOW_TRACKPAD] ?: true,
            showKeyboard = prefs[Keys.SHOW_KEYBOARD] ?: true
        )
    }
    
    val layout: Flow<ControllerLayout> = context.dataStore.data.map { prefs ->
        ControllerLayout(
            leftJoystick = getElementLayout(prefs, "left_joystick"),
            rightJoystick = getElementLayout(prefs, "right_joystick"),
            dpad = getElementLayout(prefs, "dpad"),
            abxyContainer = getElementLayout(prefs, "abxy"),
            buttonLB = getElementLayout(prefs, "button_lb"),
            buttonRB = getElementLayout(prefs, "button_rb"),
            triggerLT = getElementLayout(prefs, "trigger_lt"),
            triggerRT = getElementLayout(prefs, "trigger_rt"),
            buttonL3 = getElementLayout(prefs, "button_l3"),
            buttonR3 = getElementLayout(prefs, "button_r3"),
            menuButtonsGroup = getElementLayout(prefs, "menu_buttons"),
            trackpadGroup = getElementLayout(prefs, "trackpad_group"),
            settingsGroup = getElementLayout(prefs, "settings_group"),
            isCustom = prefs[Keys.USE_CUSTOM_LAYOUT] ?: false
        )
    }
    
    private fun getElementLayout(prefs: Preferences, name: String): ElementLayout {
        return ElementLayout(
            x = prefs[Keys.elementX(name)] ?: 0f,
            y = prefs[Keys.elementY(name)] ?: 0f,
            scale = prefs[Keys.elementScale(name)] ?: 1.0f
        )
    }
    
    suspend fun saveElementLayout(name: String, x: Float, y: Float, scale: Float) {
        context.dataStore.edit { prefs ->
            prefs[Keys.elementX(name)] = x
            prefs[Keys.elementY(name)] = y
            prefs[Keys.elementScale(name)] = scale
        }
    }
    
    suspend fun setCustomLayout(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.USE_CUSTOM_LAYOUT] = enabled
        }
    }
    
    suspend fun resetLayout() {
        val elements = listOf(
            "left_joystick", "right_joystick", "dpad", "abxy",
            "button_lb", "button_rb", "trigger_lt", "trigger_rt",
            "button_l3", "button_r3",
            "menu_buttons", "trackpad_group", "settings_group"
        )
        context.dataStore.edit { prefs ->
            elements.forEach { name ->
                prefs[Keys.elementX(name)] = 0f
                prefs[Keys.elementY(name)] = 0f
                prefs[Keys.elementScale(name)] = 1.0f
            }
            prefs[Keys.USE_CUSTOM_LAYOUT] = false
        }
    }
    
    suspend fun saveLastServer(host: String, port: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_SERVER_HOST] = host
            prefs[Keys.LAST_SERVER_PORT] = port
        }
    }
    
    suspend fun updateHapticFeedback(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HAPTIC_FEEDBACK] = enabled
        }
    }
    
    suspend fun updateHapticIntensity(intensity: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HAPTIC_INTENSITY] = intensity.coerceIn(0, 100)
        }
    }
    
    suspend fun updateDeadzone(deadzone: Float) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DEADZONE] = deadzone.coerceIn(0f, 0.5f)
        }
    }
    
    suspend fun updateShowTrackpad(show: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SHOW_TRACKPAD] = show
        }
    }
    
    suspend fun updateShowKeyboard(show: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SHOW_KEYBOARD] = show
        }
    }
}
