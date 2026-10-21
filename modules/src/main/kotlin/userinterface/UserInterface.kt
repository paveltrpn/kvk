package userinterface

import context.*
import events.*

class Userinterface(val context: Context) : WindowEventEmitter() {
    private var _windowWidth: Int = 0
    private var _windowHeight: Int = 0

    private var _mousePosX: Int = 0
    private var _mousePosY: Int = 0

    var windowWidth: Int
        get() = _windowWidth
        set(value) {
            _windowWidth = value
        }

    var windowHeight: Int
        get() = _windowHeight
        set(value) {
            _windowHeight = value
        }

    var mousePosX: Int
        get() = _mousePosX
        set(value) {
            _mousePosX = value
        }

    var mousePosY: Int
        get() = _mousePosY
        set(value) {
            _mousePosY = value
        }

    fun free() {
    }
}



