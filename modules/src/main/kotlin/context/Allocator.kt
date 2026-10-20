package context

import org.lwjgl.util.vma.Vma.vmaCreateAllocator
import org.lwjgl.util.vma.Vma.vmaDestroyAllocator
import org.lwjgl.util.vma.VmaAllocatorCreateInfo
import org.lwjgl.util.vma.VmaVulkanFunctions
import org.lwjgl.vulkan.VK13.VK_API_VERSION_1_3
import org.lwjgl.vulkan.VK14.VK_SUCCESS
import org.lwjgl.vulkan.VkDevice
import org.lwjgl.vulkan.VkInstance
import org.lwjgl.vulkan.VkPhysicalDevice

// Port of tire VMAllocator (allocator.cpp).
class Allocator(
    physicalDevice: VkPhysicalDevice,
    device: VkDevice,
    instance: VkInstance
) : ResourceBase() {
    val allocator: Long

    init {
        mStack.push().use { stack ->
            // Populate the function pointers - valid for both static and
            // dynamic Vulkan function loading VMA builds.
            val vulkanFunctions = VmaVulkanFunctions.calloc(stack)
                .set(instance, device)

            val createInfo = VmaAllocatorCreateInfo.calloc(stack)
                .vulkanApiVersion(VK_API_VERSION_1_3)
                .physicalDevice(physicalDevice)
                .device(device)
                .instance(instance)
                .pVulkanFunctions(vulkanFunctions)

            val allocatorBuf = stack.mallocPointer(1)
            when (val result = vmaCreateAllocator(createInfo, allocatorBuf)) {
                VK_SUCCESS -> {
                    println("vma allocator created")
                }

                else -> {
                    throw RuntimeException("failed to create vma allocator: $result")
                }
            }
            allocator = allocatorBuf.get(0)
        }
    }

    fun free() {
        vmaDestroyAllocator(allocator)
    }
}
