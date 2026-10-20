package render

import config.Config
import context.Context

import org.lwjgl.vulkan.VK10.vkDeviceWaitIdle

// Port of tire RenderVK (mainloop.cpp), reduced to the spinning cube pass.
class Render(val context: Context) {
    private var mCurrentFrame: Int = 0
    private val mTimer: Timer = Timer()
    private val mTestBox: TestBox

    init {
        mTestBox = TestBox(context)
        mTestBox.setPosition(0.0f, 0.0f, -2.5f)
    }

    fun preLoop() {
        println("render loop starts here...")
    }

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
            mTestBox.draw(cb, mTimer.floatFrameDuration())
        }

        mCurrentFrame = (mCurrentFrame + 1) % Config.FRAMES_COUNT
    }

    fun postLoop() {
        println("wait device idle...")

        // Wait for the logical device to finish operations before exiting
        // mainLoop and destroying the window.
        vkDeviceWaitIdle(context.device)
    }

    fun free() {
        mTestBox.free()
    }
}
