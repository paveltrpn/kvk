package userinterface

import context.*
import events.*

class Userinterface(val context: Context) : WindowEventEmitter() {
    private var _windowWidth: Int = 0
    private var _windowHeight: Int = 0

    private var _windowPosX: Int = 0
    private var _windowPosY: Int = 0

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

    var windowPosX: Int
        get() = _windowWidth
        set(value) {
            _windowWidth = value
        }

    var windowPosY: Int
        get() = _windowPosX
        set(value) {
            _windowPosX = value
        }

    var mousePosX: Int
        get() = _windowPosY
        set(value) {
            _windowPosY = value
        }

    var mousePosY: Int
        get() = _mousePosY
        set(value) {
            _mousePosY = value
        }

    fun free() {
    }
}



