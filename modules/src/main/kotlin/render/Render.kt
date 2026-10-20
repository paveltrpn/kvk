package render

import config.Config
import context.Context

import org.lwjgl.vulkan.VK10.vkDeviceWaitIdle

// Port of tire RenderVK (mainloop.cpp), reduced to the black screen pass.
class Render(val context: Context) {
    private var mCurrentFrame: Int = 0

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

        // Empty body — black screen only.
        context.renderScope(mCurrentFrame) { }

        mCurrentFrame = (mCurrentFrame + 1) % Config.FRAMES_COUNT
    }

    fun postLoop() {
        println("wait device idle...")

        // Wait for the logical device to finish operations before exiting
        // mainLoop and destroying the window.
        vkDeviceWaitIdle(context.device)
    }

    fun free() {
    }
}
