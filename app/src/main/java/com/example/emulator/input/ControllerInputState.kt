package com.example.emulator.input

/**
 * Thread-safe Controller Input State representing Nintendo Switch Joy-Con / Pro Controller inputs.
 */
class ControllerInputState {

    @Volatile var stickX: Float = 0f
    @Volatile var stickY: Float = 0f

    @Volatile var isAPressed: Boolean = false
    @Volatile var isBPressed: Boolean = false
    @Volatile var isXPressed: Boolean = false
    @Volatile var isYPressed: Boolean = false

    @Volatile var isLPressed: Boolean = false
    @Volatile var isRPressed: Boolean = false
    @Volatile var isZLPressed: Boolean = false
    @Volatile var isZRPressed: Boolean = false

    @Volatile var isDpadUp: Boolean = false
    @Volatile var isDpadDown: Boolean = false
    @Volatile var isDpadLeft: Boolean = false
    @Volatile var isDpadRight: Boolean = false

    @Volatile var isPlusPressed: Boolean = false
    @Volatile var isMinusPressed: Boolean = false

    fun setJoystick(x: Float, y: Float) {
        stickX = x.coerceIn(-1f, 1f)
        stickY = y.coerceIn(-1f, 1f)
    }

    fun setButton(button: SwitchButton, pressed: Boolean) {
        when (button) {
            SwitchButton.A -> isAPressed = pressed
            SwitchButton.B -> isBPressed = pressed
            SwitchButton.X -> isXPressed = pressed
            SwitchButton.Y -> isYPressed = pressed
            SwitchButton.L -> isLPressed = pressed
            SwitchButton.R -> isRPressed = pressed
            SwitchButton.ZL -> isZLPressed = pressed
            SwitchButton.ZR -> isZRPressed = pressed
            SwitchButton.DPAD_UP -> isDpadUp = pressed
            SwitchButton.DPAD_DOWN -> isDpadDown = pressed
            SwitchButton.DPAD_LEFT -> isDpadLeft = pressed
            SwitchButton.DPAD_RIGHT -> isDpadRight = pressed
            SwitchButton.PLUS -> isPlusPressed = pressed
            SwitchButton.MINUS -> isMinusPressed = pressed
        }
    }

    fun reset() {
        stickX = 0f
        stickY = 0f
        isAPressed = false
        isBPressed = false
        isXPressed = false
        isYPressed = false
        isLPressed = false
        isRPressed = false
        isZLPressed = false
        isZRPressed = false
        isDpadUp = false
        isDpadDown = false
        isDpadLeft = false
        isDpadRight = false
        isPlusPressed = false
        isMinusPressed = false
    }
}

enum class SwitchButton {
    A, B, X, Y,
    L, R, ZL, ZR,
    DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT,
    PLUS, MINUS
}
