package render

import org.lwjgl.vulkan.VK10.vkDeviceWaitIdle

import config.Config
import context.Context
import graph.Scene

import events.*
import graph.FlycamMoveBits
import org.lwjgl.glfw.GLFW.GLFW_KEY_A
import org.lwjgl.glfw.GLFW.GLFW_KEY_D
import org.lwjgl.glfw.GLFW.GLFW_KEY_S
import org.lwjgl.glfw.GLFW.GLFW_KEY_W

// ============================================================================
// ======================= Render =============================================
// ============================================================================

class Render(val context: Context) : WindowEventObserver {
    private var mCurrentFrame: Int = 0
    private val mTimer: Timer = Timer()
    private val mTestBox: TestBox
    private val mFlatshade: FlatshadeProgram
    private var mScene: Scene? = null

    init {
        mTestBox = TestBox(context)
        mTestBox.setPosition(0.0f, 0.0f, -2.5f)

        mFlatshade = FlatshadeProgram(context)
    }

    val flatshade: FlatshadeProgram
        get(): FlatshadeProgram {
            return mFlatshade
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
        mFlatshade.free()
        mTestBox.free()
    }

    // === Window events handler method ===
    override fun onWindowEvent(event: WindowEvent) {
        when (event) {
            is WindowKeyPress -> {
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
                }
            }

            is WindowKeyRelease -> {
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
        }
    }
}
