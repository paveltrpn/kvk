package render

import org.lwjgl.vulkan.VK10.vkDeviceWaitIdle

import config.Config
import context.Context
import graph.Scene

import events.*
import graph.FlycamMoveBits

// ============================================================================
// ======================= Render =============================================
// ============================================================================

class Render(val context: Context) : WindowEventObserver {
    private var mCurrentFrame: Int = 0
    private val mTimer: Timer = Timer()
    private var mScene: Scene? = null

    private var _holdMouse: Boolean

    private val _mouseSensitivity = 0.005f

    init {
        _holdMouse = true
    }

    var holdMouse: Boolean
        get() = _holdMouse
        set(value) {
            _holdMouse = value
        }

    var scene: Scene
        get(): Scene {
            return mScene ?: throw RuntimeException("Scene is null! Nothing to render!")
        }
        set(value) {
            // All VMA uploads and blocking submits happen here, before the
            // render loop starts - no mid-frame submits.
            value.upload(context)
            mScene = value
        }

    fun preLoop() {
        if (mScene == null) {
            throw RuntimeException("Scene is null! Nothing to render!")
        }

        println("Render loop starts here...")
    }

    // === Frame render main method ===
    fun frame() {
        if (context.swapchainOutdated) {
            if (!context.recreateSwapchain()) {
                return
            }
        }

        // Acquire swapchain image before any submit. On failure nothing is
        // acquired and no semaphore will be signaled — skip the whole frame.
        if (!context.acquireImage(mCurrentFrame)) {
            return
        }

        mTimer.update()

        context.renderScope(mCurrentFrame) { cb ->
            // mTestBox.draw(cb, mTimer.floatFrameDuration())
            scene.walk(cb)
        }

        mCurrentFrame = (mCurrentFrame + 1) % Config.instance().konfig.frame_count
    }

    fun postLoop() {
        println("Wait device idle...")

        // Wait for the logical device to finish operations before exiting
        // mainLoop and destroying the window.
        vkDeviceWaitIdle(context.device)
    }

    fun free() {
        mScene?.free()
        mScene = null
    }

    // === Window events handler method ===
    override fun onWindowEvent(event: WindowEvent) {
        when (event) {
            is WindowKeyPressEvent -> {
                when (event.keyCode) {
                    // GLFW_KEY_W
                    87 -> {
                        scene.camera.setMoveBit(FlycamMoveBits.FORWARD)
                    }

                    // GLFW_KEY_A
                    65 -> {
                        scene.camera.setMoveBit(FlycamMoveBits.LEFT)
                    }

                    // GLFW_KEY_S
                    83 -> {
                        scene.camera.setMoveBit(FlycamMoveBits.BACKWARD)
                    }

                    // GLFW_KEY_D
                    68 -> {
                        scene.camera.setMoveBit(FlycamMoveBits.RIGHT)
                    }

                    71 -> {
                        holdMouse = !holdMouse
                    }
                }
            }

            is WindowKeyReleaseEvent -> {
                when (event.keyCode) {
                    87 -> {
                        scene.camera.unsetMoveBit(FlycamMoveBits.FORWARD)
                    }

                    65 -> {
                        scene.camera.unsetMoveBit(FlycamMoveBits.LEFT)
                    }

                    83 -> {
                        scene.camera.unsetMoveBit(FlycamMoveBits.BACKWARD)
                    }

                    68 -> {
                        scene.camera.unsetMoveBit(FlycamMoveBits.RIGHT)
                    }
                }
            }

            is WindowMouseMoveEvent -> {
                if (holdMouse) {
                    scene.camera.rotate(
                        (400.0 - event.posX).toFloat() * _mouseSensitivity,
                        (300.0 - event.posY).toFloat() * _mouseSensitivity
                    )
                }
            }
        }
    }
}
