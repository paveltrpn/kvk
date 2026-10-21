package kvk

import org.lwjgl.glfw.*
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.system.MemoryUtil.*

import context.*
import graph.*
import render.*
import config.*
import userinterface.*

import kvk.testgraph.sparseObjectsGraph

import org.lwjgl.system.Configuration;

class Window {
    private var mAllocator: GLFWAllocator? = null
    private var mWindow: Long = 0

    private val mContext: Context
    private val mRender: Render
    private val mUserinterface: Userinterface

    init {
        // === Init Config object sngleton ===
        Config.init()

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

        val WIDTH = 1600
        val HEIGHT = 800

        mWindow = glfwCreateWindow(WIDTH, HEIGHT, "kvk", NULL, NULL)
        if (mWindow == NULL) {
            throw RuntimeException("Failed to create the GLFW window")
        }

        // Create vulkan context object.
        mContext = Context(mWindow)

        // Create userinterface object.
        mUserinterface = Userinterface(mContext)

        // Create render object.
        mRender = Render(mContext)

        mUserinterface.attach(mRender)

        // Main scene init.
        val (diamonds, frames, arches) = sparseObjectsGraph(mRender.flatshade)
        val (width, height) = mContext.viewportSize()
        val mainScene = Scene(width.toFloat() / height.toFloat())

        val locale = Locale()
        locale.addStateGroups(listOf(diamonds, frames, arches))
        mainScene.addLocale(locale)

        mRender.scene = mainScene

        glfwSetWindowSizeLimits(mWindow, WIDTH, HEIGHT, GLFW_DONT_CARE, GLFW_DONT_CARE)

        //glfwSetWindowAspectRatio(window, 1, 1);
        val monitor = glfwGetPrimaryMonitor()

        val vidmode: GLFWVidMode? = glfwGetVideoMode(monitor)

        glfwSetWindowPos(
            mWindow,
            (vidmode!!.width() - WIDTH) / 2,
            (vidmode.height() - HEIGHT) / 2
        )

        glfwSetInputMode(mWindow, GLFW_CURSOR, GLFW_CURSOR_HIDDEN)

        glfwSetKeyCallback(mWindow, { window, key, scancode, action, mods ->
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) glfwSetWindowShouldClose(
                window,
                true
            ) // We will detect this in the rendering loop

            if (action == GLFW_PRESS) {
                when (key) {
                    GLFW_KEY_W -> {
                        mRender.scene.camera.setMoveBit(FlycamMoveBits.FORWARD)
                    }

                    GLFW_KEY_A -> {
                        mRender.scene.camera.setMoveBit(FlycamMoveBits.LEFT)
                    }

                    GLFW_KEY_S -> {
                        mRender.scene.camera.setMoveBit(FlycamMoveBits.BACKWARD)
                    }

                    GLFW_KEY_D -> {
                        mRender.scene.camera.setMoveBit(FlycamMoveBits.RIGHT)
                    }
                }
            }

            if (action == GLFW_RELEASE) {
                when (key) {
                    GLFW_KEY_W -> {
                        mRender.scene.camera.unsetMoveBit(FlycamMoveBits.FORWARD)
                    }

                    GLFW_KEY_A -> {
                        mRender.scene.camera.unsetMoveBit(FlycamMoveBits.LEFT)
                    }

                    GLFW_KEY_S -> {
                        mRender.scene.camera.unsetMoveBit(FlycamMoveBits.BACKWARD)
                    }

                    GLFW_KEY_D -> {
                        mRender.scene.camera.unsetMoveBit(FlycamMoveBits.RIGHT)
                    }
                }
            }
        })

        glfwSetCursorPosCallback(mWindow, { window, xpos, ypos ->
            val sensitivity = 0.005f
            mRender.scene.camera.rotate(
                (400.0 - xpos).toFloat() * sensitivity,
                (300.0 - ypos).toFloat() * sensitivity
            )
        })

        glfwShowWindow(mWindow)
    }

    fun loop() {
        mRender.preLoop()

        while (!glfwWindowShouldClose(mWindow)) {
            // Re-center the hidden cursor each frame so the camera rotate
            // offset is always relative to the window center (kogl loop).
            glfwSetCursorPos(mWindow, 400.0, 300.0)

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
