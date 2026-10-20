package context

import org.lwjgl.vulkan.VkInstance
import org.lwjgl.glfw.GLFWVulkan.glfwCreateWindowSurface
import org.lwjgl.vulkan.KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR
import org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_FIFO_KHR
import org.lwjgl.vulkan.KHRSurface.vkDestroySurfaceKHR
import org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR
import org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR
import org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR
import org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR
import org.lwjgl.vulkan.VK14.*
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR
import org.lwjgl.vulkan.VkSurfaceFormatKHR
import kotlin.use

class Surface(val instance: VkInstance, window: Long) : ResourceBase() {
    val mSurface: Long

    // Persistent struct storage, filled once a Device is available.
    val mSurfaceCapabilities: VkSurfaceCapabilitiesKHR = VkSurfaceCapabilitiesKHR.calloc(mStack)
    var mSurfaceFormat: Int = VK_FORMAT_UNDEFINED
        private set
    var mSurfaceColorSpace: Int = VK_COLOR_SPACE_SRGB_NONLINEAR_KHR
        private set
    var mPresentMode: Int = VK_PRESENT_MODE_FIFO_KHR
        private set

    private lateinit var mDevice: Device

    init {
        mStack.push().use { stack ->
            // Create a WSI surface for the window.
            val surfaceBuf = stack.mallocLong(1)
            glfwCreateWindowSurface(instance, window, null, surfaceBuf)
            mSurface = surfaceBuf.get(0)
        }
    }

    // Port of tire VKSurface::physicalDeviceSurfaceCapabilities().
    // Called from Context after the Device exists.
    fun queryCapabilities(device: Device) {
        mDevice = device

        mStack.push().use { stack ->
            // Check if picked queue family (with VK_QUEUE_GRAPHICS_BIT) supports present.
            val presentSupport = stack.mallocInt(1)
            when (val result = vkGetPhysicalDeviceSurfaceSupportKHR(
                device.mPhysDevice,
                device.mGraphicsQueueNodeIndex,
                mSurface,
                presentSupport
            )) {
                VK_SUCCESS -> {
                }

                else -> {
                    throw RuntimeException("failed to get device surface support for presentation: $result")
                }
            }

            if (presentSupport.get(0) != VK_TRUE) {
                throw RuntimeException("queue with VK_QUEUE_GRAPHICS_BIT does not support present")
            }

            updateCapabilities()

            // Physical device surface formats.
            val formatCount = stack.mallocInt(1)
            when (val result = vkGetPhysicalDeviceSurfaceFormatsKHR(device.mPhysDevice, mSurface, formatCount, null)) {
                VK_SUCCESS -> {
                    println("physical device surface formats count: ${formatCount.get(0)}")
                }

                else -> {
                    throw RuntimeException("failed to obtain physical device surface formats count: $result")
                }
            }

            val surfaceFormats = VkSurfaceFormatKHR.calloc(formatCount.get(0), stack)
            when (val result =
                vkGetPhysicalDeviceSurfaceFormatsKHR(device.mPhysDevice, mSurface, formatCount, surfaceFormats)) {
                VK_SUCCESS -> {
                    println("physical device surface formats acquired")
                }

                else -> {
                    throw RuntimeException("failed to acquire physical device surface formats: $result")
                }
            }

            for (i in 0..<formatCount.get(0)) {
                println("format: ${surfaceFormats.get(i).format()}")
            }

            // This format will be used across application, in swapchain in particular.
            // Mirrors tire CHOSEN_SURFACE_FORMAT 0.
            mSurfaceFormat = surfaceFormats.get(0).format()
            mSurfaceColorSpace = surfaceFormats.get(0).colorSpace()

            // Physical device present modes.
            val presentModeCount = stack.mallocInt(1)
            when (val result =
                vkGetPhysicalDeviceSurfacePresentModesKHR(device.mPhysDevice, mSurface, presentModeCount, null)) {
                VK_SUCCESS -> {
                    println("physical device present modes count: ${presentModeCount.get(0)}")
                }

                else -> {
                    throw RuntimeException("failed to obtain physical device present modes count: $result")
                }
            }

            val presentModes = stack.mallocInt(presentModeCount.get(0))
            when (val result =
                vkGetPhysicalDeviceSurfacePresentModesKHR(
                    device.mPhysDevice,
                    mSurface,
                    presentModeCount,
                    presentModes
                )) {
                VK_SUCCESS -> {
                    println("physical device present modes acquired")
                }

                else -> {
                    throw RuntimeException("failed to acquire physical device present modes: $result")
                }
            }

            for (i in 0..<presentModeCount.get(0)) {
                println("mode: ${presentModes.get(i)}")
            }

            // This present mode will be used across application, in swapchain
            // in particular. Mirrors tire CHOSEN_PRESENT_MODE VK_PRESENT_MODE_FIFO_KHR.
            mPresentMode = VK_PRESENT_MODE_FIFO_KHR
        }
    }

    fun updateCapabilities() {
        when (val result =
            vkGetPhysicalDeviceSurfaceCapabilitiesKHR(mDevice.mPhysDevice, mSurface, mSurfaceCapabilities)) {
            VK_SUCCESS -> {
            }

            else -> {
                throw RuntimeException("failed to obtain surface capabilities: $result")
            }
        }
    }

    fun free() {
        vkDestroySurfaceKHR(instance, mSurface, null)
    }
}
