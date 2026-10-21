package kvk

import org.lwjgl.glfw.*
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.system.MemoryUtil.*

import context.*
import graph.*
import render.*
import config.*
import events.*
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

        mWindow = glfwCreateWindow(
            Config.instance().konfig.window_width,
            Config.instance().konfig.window_height,
            "kvk",
            NULL,
            NULL
        )

        if (mWindow == NULL) {
            throw RuntimeException("Failed to create the GLFW window")
        }

        // Create vulkan context object.
        mContext = Context(mWindow, Config.instance().konfig.window_width, Config.instance().konfig.window_height)

        // Create userinterface object.
        mUserinterface = Userinterface(mContext)
        mUserinterface.windowWidth = Config.instance().konfig.window_width
        mUserinterface.windowHeight = Config.instance().konfig.window_height

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

        // glfwSetWindowSizeLimits(mWindow, WIDTH, HEIGHT, GLFW_DONT_CARE, GLFW_DONT_CARE)

        //glfwSetWindowAspectRatio(window, 1, 1);
        val monitor = glfwGetPrimaryMonitor()

        val vidmode: GLFWVidMode? = glfwGetVideoMode(monitor)

        glfwSetWindowPos(
            mWindow,
            (vidmode!!.width() - mUserinterface.windowWidth) / 2,
            (vidmode.height() - mUserinterface.windowHeight) / 2
        )

        glfwSetInputMode(mWindow, GLFW_CURSOR, GLFW_CURSOR_HIDDEN)

        glfwSetKeyCallback(mWindow, { window, key, scancode, action, mods ->
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) glfwSetWindowShouldClose(
                window,
                true
            )

            if (action == GLFW_PRESS) {
                val keyEvent = WindowKeyPressEvent(key)
                mUserinterface.notify(keyEvent)
            }

            if (action == GLFW_RELEASE) {
                val keyEvent = WindowKeyReleaseEvent(key)
                mUserinterface.notify(keyEvent)
            }
        })

        glfwSetCursorPosCallback(mWindow, { window, xpos, ypos ->
            val mouseEvent = WindowMouseMoveEvent(xpos.toInt(), ypos.toInt())
            mUserinterface.notify(mouseEvent)
        })

        glfwShowWindow(mWindow)
    }

    fun loop() {
        mRender.preLoop()

        while (!glfwWindowShouldClose(mWindow)) {
            glfwSetCursorPos(mWindow, 400.0, 300.0)

            glfwPollEvents()

            mRender.frame()
        }

        mRender.postLoop()
    }

    fun destroy() {
        mRender.free()

        mUserinterface.free()

        mContext.free()

        if (mWindow != NULL) {
            glfwDestroyWindow(mWindow)
        }

        glfwTerminate()
    }
}
