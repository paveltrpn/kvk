package kvk

import org.lwjgl.glfw.*
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.system.MemoryUtil.*

import context.*
import render.*

import org.lwjgl.system.Configuration;

class Window {
    private var mAllocator: GLFWAllocator? = null
    private var mWindow: Long = 0

    private val mContext: Context
    private val mRender: Render

    init {
        // NOTE: set default stack size - 128 kb!!!
        Configuration.STACK_SIZE.set(128 * 1024)

        mAllocator = GLFWAllocator.calloc()
            .allocate(GLFWAllocateCallbackI { size: Long, user: Long -> nmemAllocChecked(size) })
            .reallocate(GLFWReallocateCallbackI { block: Long, size: Long, user: Long ->
                nmemReallocChecked(
                    block,
                    size
                )
            })
            .deallocate(GLFWDeallocateCallbackI { block: Long, user: Long -> nmemFree(block) })

        glfwInitAllocator(mAllocator)

        GLFWErrorCallback.createPrint().set()

        if (!glfwInit()) {
            println("Unable to initialize glfw")
        }

        // glfwDefaultWindowHints()
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE)
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)

        val WIDTH = 800
        val HEIGHT = 600

        mWindow = glfwCreateWindow(WIDTH, HEIGHT, "another tire", NULL, NULL)
        if (mWindow == NULL) {
            throw RuntimeException("Failed to create the GLFW window")
        }

        // Create vulkan context object.
        mContext = Context(mWindow)

        // Create global render object.
        mRender = Render(mContext)

        glfwSetWindowSizeLimits(mWindow, WIDTH, HEIGHT, GLFW_DONT_CARE, GLFW_DONT_CARE)

        //glfwSetWindowAspectRatio(window, 1, 1);
        val monitor = glfwGetPrimaryMonitor()

        val vidmode: GLFWVidMode? = glfwGetVideoMode(monitor)

        glfwSetWindowPos(
            mWindow,
            (vidmode!!.width() - WIDTH) / 2,
            (vidmode.height() - HEIGHT) / 2
        )

        glfwSetKeyCallback(mWindow, { window, key, scancode, action, mods ->
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) glfwSetWindowShouldClose(
                window,
                true
            ) // We will detect this in the rendering loop
        })

        glfwShowWindow(mWindow)
    }

    fun loop() {
        mRender.preLoop()

        while (!glfwWindowShouldClose(mWindow)) {
            glfwPollEvents()
            mRender.frame()
        }

        mRender.postLoop()
    }

    fun destroy() {
        // Free structures, allocated in render.
        mRender.free()

        // Free structures, allocated in context.
        mContext.free()

        if (mWindow != NULL) {
            glfwDestroyWindow(mWindow)
        }

        glfwTerminate()

//        Objects.requireNonNull<T?>(glfwSetErrorCallback(null)).free()

//        allocator!!.deallocate().free()
//        allocator!!.reallocate().free()
//        allocator!!.allocate().free()
//        allocator!!.free()
    }
}
