package context

import org.lwjgl.vulkan.VK14.*
import org.lwjgl.vulkan.VkCommandPoolCreateInfo
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo
import org.lwjgl.vulkan.VkDescriptorPoolSize

class Pools(val device: Device) : ResourceBase() {
    val mCommandPool: Long
    val mDescriptorPool: Long

    init {
        mStack.push().use { stack ->
            val poolInfo = VkCommandPoolCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
                .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
                .queueFamilyIndex(device.mGraphicsQueueNodeIndex)

            val commandPoolBuf = stack.mallocLong(1)
            when (val result = vkCreateCommandPool(device.mLogicalDevice, poolInfo, null, commandPoolBuf)) {
                VK_SUCCESS -> {
                    println("command pool created")
                }

                else -> {
                    throw RuntimeException("failed to create command pool: $result")
                }
            }
            mCommandPool = commandPoolBuf.get(0)

            val sizes = VkDescriptorPoolSize.calloc(4, stack)
            sizes.get(0).type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER).descriptorCount(10)
            sizes.get(1).type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC).descriptorCount(10)
            sizes.get(2).type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(10)
            sizes.get(3).type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(10)

            val descriptorPoolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                .flags(0)
                .maxSets(10)
                .pPoolSizes(sizes)

            val descriptorPoolBuf = stack.mallocLong(1)
            when (val result =
                vkCreateDescriptorPool(device.mLogicalDevice, descriptorPoolInfo, null, descriptorPoolBuf)) {
                VK_SUCCESS -> {
                    println("descriptor pool created")
                }

                else -> {
                    throw RuntimeException("failed to create descriptor pool: $result")
                }
            }
            mDescriptorPool = descriptorPoolBuf.get(0)
        }
    }

    fun free() {
        vkDestroyDescriptorPool(device.mLogicalDevice, mDescriptorPool, null)
        vkDestroyCommandPool(device.mLogicalDevice, mCommandPool, null)
    }
}
