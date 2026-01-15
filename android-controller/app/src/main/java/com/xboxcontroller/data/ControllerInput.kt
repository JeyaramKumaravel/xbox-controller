package com.xboxcontroller.data

/**
 * Represents the current state of all controller inputs
 */
data class ControllerInput(
    // Face buttons
    var buttonA: Boolean = false,
    var buttonB: Boolean = false,
    var buttonX: Boolean = false,
    var buttonY: Boolean = false,
    
    // Shoulder buttons
    var buttonLB: Boolean = false,
    var buttonRB: Boolean = false,
    
    // Menu buttons
    var buttonStart: Boolean = false,
    var buttonBack: Boolean = false,
    var buttonGuide: Boolean = false,
    
    // Stick clicks
    var buttonL3: Boolean = false,
    var buttonR3: Boolean = false,
    
    // D-Pad
    var dpadUp: Boolean = false,
    var dpadDown: Boolean = false,
    var dpadLeft: Boolean = false,
    var dpadRight: Boolean = false,
    
    // Triggers (0.0 to 1.0)
    var triggerLT: Float = 0f,
    var triggerRT: Float = 0f,
    
    // Joysticks (-1.0 to 1.0)
    var leftStickX: Float = 0f,
    var leftStickY: Float = 0f,
    var rightStickX: Float = 0f,
    var rightStickY: Float = 0f
) {
    fun copy(): ControllerInput = ControllerInput(
        buttonA, buttonB, buttonX, buttonY,
        buttonLB, buttonRB,
        buttonStart, buttonBack, buttonGuide,
        buttonL3, buttonR3,
        dpadUp, dpadDown, dpadLeft, dpadRight,
        triggerLT, triggerRT,
        leftStickX, leftStickY, rightStickX, rightStickY
    )
    
    fun reset() {
        buttonA = false
        buttonB = false
        buttonX = false
        buttonY = false
        buttonLB = false
        buttonRB = false
        buttonStart = false
        buttonBack = false
        buttonGuide = false
        buttonL3 = false
        buttonR3 = false
        dpadUp = false
        dpadDown = false
        dpadLeft = false
        dpadRight = false
        triggerLT = 0f
        triggerRT = 0f
        leftStickX = 0f
        leftStickY = 0f
        rightStickX = 0f
        rightStickY = 0f
    }
}
