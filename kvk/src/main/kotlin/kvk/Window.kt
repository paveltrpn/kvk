package kvk

import org.lwjgl.glfw.*
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.system.Configuration

import context.*
import graph.*
import render.*
import config.*
import events.*
import userinterface.*

import kvk.testgraph.sparseObjectsGraph

class Window {
    private var mAllocator: GLFWAllocator? = null
    private var mWindow: Long = 0

    private val mContext: Context
    private val mRender: Render
    private val mUserinterface: Userinterface

    private val _flatshadeProgram: FlatshadeProgram

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
        mUserinterface.windowPosX = Config.instance().konfig.window_pos_x
        mUserinterface.windowPosY = Config.instance().konfig.window_pos_y

        // Create render object.
        mRender = Render(mContext)

        mUserinterface.attach(mRender)

        mRender.ui = mUserinterface.ui

        // Main scene init.
        _flatshadeProgram = FlatshadeProgram(mContext)
        val (diamonds, frames, arches) = sparseObjectsGraph(_flatshadeProgram)
        val (width, height) = mContext.viewportSize()
        val mainScene = Scene(width.toFloat() / height.toFloat())

        val locale = Locale()
        locale.addStateGroups(listOf(diamonds, frames, arches))
        mainScene.addLocale(locale)

        mRender.scene = mainScene

//        glfwSetWindowSizeLimits(mWindow, WIDTH, HEIGHT, GLFW_DONT_CARE, GLFW_DONT_CARE)

//        glfwSetWindowAspectRatio(window, 1, 1);

//        val monitor = glfwGetPrimaryMonitor()
//        val vidmode: GLFWVidMode? = glfwGetVideoMode(monitor)
//        glfwSetWindowPos(
//            mWindow,
//            (vidmode!!.width() - mUserinterface.windowWidth) / 2,
//            (vidmode.height() - mUserinterface.windowHeight) / 2
//        )

        glfwSetWindowPos(
            mWindow,
            mUserinterface.windowPosX,
            mUserinterface.windowPosY
        )

        glfwSetKeyCallback(mWindow, { window, key, scancode, action, mods ->
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) glfwSetWindowShouldClose(
                window,
                true
            )

            if (action == GLFW_PRESS) {
                val keyEvent = WindowKeyPressEvent(key)
                mUserinterface.notify(keyEvent)

                when (key) {
                    GLFW_KEY_ESCAPE -> {
                        glfwSetWindowShouldClose(
                            window,
                            true
                        )
                    }

                    GLFW_KEY_G -> {
                        mUserinterface.holdMouse = !mUserinterface.holdMouse

                        if (mUserinterface.holdMouse) {
                            glfwSetInputMode(mWindow, GLFW_CURSOR, GLFW_CURSOR_HIDDEN)
                        } else {
                            glfwSetInputMode(mWindow, GLFW_CURSOR, GLFW_CURSOR_NORMAL)
                        }
                    }
                }
            }

            if (action == GLFW_RELEASE) {
                val keyEvent = WindowKeyReleaseEvent(key)
                mUserinterface.notify(keyEvent)
            }
        })

        glfwSetCursorPosCallback(mWindow, { window, xpos, ypos ->
            if (!mUserinterface.holdMouse) {
                val mouseEvent = WindowMouseMoveEvent(xpos.toInt(), ypos.toInt())
                mUserinterface.notify(mouseEvent)
            } else {
                val offsetX = 400 - xpos.toInt()
                val offsetY = 300 - ypos.toInt()

                val mouseEvent = WindowMouseOffsetEvent(offsetX, offsetY)
                mUserinterface.notify(mouseEvent)
            }
        })

        glfwSetMouseButtonCallback(mWindow, { window, button, action, mods ->
            if (action == GLFW_PRESS) {
                val mouseEvent = WindowMousePress(button)
                mUserinterface.notify(mouseEvent)
            }

            if (action == GLFW_RELEASE) {
                val mouseEvent = WindowMouseRelease(button)
                mUserinterface.notify(mouseEvent)
            }
        })

        glfwShowWindow(mWindow)
    }

    fun loop() {
        mRender.preLoop()

        while (!glfwWindowShouldClose(mWindow)) {
            if (mUserinterface.holdMouse) {
                glfwSetCursorPos(mWindow, 400.0, 300.0)
            }

            glfwPollEvents()

            mUserinterface.update(mRender.currentFrame)

            mRender.frame()
        }

        mRender.postLoop()
    }

    fun destroy() {
        mRender.free()

        mUserinterface.free()

        _flatshadeProgram.free()

        mContext.free()

        if (mWindow != NULL) {
            glfwDestroyWindow(mWindow)
        }

        glfwTerminate()
    }
}
