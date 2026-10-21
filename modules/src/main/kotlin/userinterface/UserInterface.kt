package userinterface

import context.*
import events.*
import render.UiDrawable

class Userinterface(val context: Context) : WindowEventEmitter() {
    private val _nuklearBackend: NuklearVulkanBackend = NuklearVulkanBackend(context)
    private val _nuklearInterface: NuklearUi = NuklearUi(context, _nuklearBackend)

    private var _windowWidth: Int = 0
    private var _windowHeight: Int = 0

    private var _windowPosX: Int = 0
    private var _windowPosY: Int = 0

    private var _mousePosX: Int = 0
    private var _mousePosY: Int = 0

    private var _holdMouse: Boolean = false

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

    var holdMouse: Boolean
        get() = _holdMouse
        set(value) {
            _holdMouse = value
        }

    val ui: UiDrawable
        get(): UiDrawable {
            return _nuklearInterface
        }

    init {
        attach(_nuklearInterface)
    }

    fun update(frameIndex: Int) {
        _nuklearInterface.update(frameIndex)
    }

    fun free() {
        _nuklearInterface.free()
        _nuklearBackend.free()
    }
}



