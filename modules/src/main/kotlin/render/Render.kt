package render

import config.Config
import context.Context
import graph.Scene

import org.lwjgl.vulkan.VK10.vkDeviceWaitIdle

// Port of tire RenderVK (mainloop.cpp) - spinning cube pass plus the
// scenegraph walk that renders all Drawable objects via the flatshade
// program. The scene graph itself is built by the application (Window),
// mirroring kogl's Render.scene assignment.
class Render(val context: Context) {
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
            // mTestBox.draw(cb, mTimer.floatFrameDuration())
            scene.walk(cb)
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
        mScene?.free()
        mScene = null
        mFlatshade.free()
        mTestBox.free()
    }
}
