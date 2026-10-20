package context

import org.lwjgl.util.vma.Vma.VMA_MEMORY_USAGE_GPU_ONLY
import org.lwjgl.util.vma.Vma.vmaCreateImage
import org.lwjgl.util.vma.Vma.vmaDestroyImage
import org.lwjgl.util.vma.VmaAllocationCreateInfo
import org.lwjgl.vulkan.VK14.*
import org.lwjgl.vulkan.VkCommandBuffer
import org.lwjgl.vulkan.VkFormatProperties
import org.lwjgl.vulkan.VkImageCreateInfo
import org.lwjgl.vulkan.VkImageMemoryBarrier
import org.lwjgl.vulkan.VkImageViewCreateInfo

// Verbatim port of tire DepthImage (depth_image.cpp).
class DepthImage(
    private val mContext: Context,
    width: Int,
    height: Int
) : ResourceBase() {
    private val mDepthFormat: Int
    private var mImage: Long = VK_NULL_HANDLE
    private var mAllocation: Long = VK_NULL_HANDLE
    private var mImageView: Long = VK_NULL_HANDLE

    val view: Long
        get(): Long {
            return mImageView
        }

    init {
        mDepthFormat = findSupportedFormat(
            listOf(VK_FORMAT_D32_SFLOAT, VK_FORMAT_D32_SFLOAT_S8_UINT, VK_FORMAT_D24_UNORM_S8_UINT),
            VK_IMAGE_TILING_OPTIMAL,
            VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT
        )

        initDeviceImage(width, height)
        initImageView()

        mContext.blockingScope { cb ->
            layoutTransitionCmd(cb)
        }
    }

    fun free() {
        vmaDestroyImage(mContext.allocator, mImage, mAllocation)
        vkDestroyImageView(mContext.device, mImageView, null)
    }

    private fun initDeviceImage(width: Int, height: Int) {
        mStack.push().use { stack ->
            val imgCreateInfo = VkImageCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
                .flags(0)
                .imageType(VK_IMAGE_TYPE_2D)
                .format(mDepthFormat)
                .extent { extent ->
                    extent
                        .width(width)
                        .height(height)
                        .depth(1)
                }
                .mipLevels(1)
                .arrayLayers(1)
                .samples(VK_SAMPLE_COUNT_1_BIT)
                .tiling(VK_IMAGE_TILING_OPTIMAL)
                .usage(VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)

            val allocCreateInfo = VmaAllocationCreateInfo.calloc(stack)
                .flags(0)
                .usage(VMA_MEMORY_USAGE_GPU_ONLY)
                .requiredFlags(VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)

            val imageBuf = stack.mallocLong(1)
            val allocationBuf = stack.mallocPointer(1)

            when (val result =
                vmaCreateImage(mContext.allocator, imgCreateInfo, allocCreateInfo, imageBuf, allocationBuf, null)) {
                VK_SUCCESS -> {
                    println("depth image created")
                }

                else -> {
                    throw RuntimeException("error while creating depth image: $result")
                }
            }

            mImage = imageBuf.get(0)
            mAllocation = allocationBuf.get(0)
        }
    }

    private fun initImageView() {
        mStack.push().use { stack ->
            val imageInfo = VkImageViewCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                .image(mImage)
                .viewType(VK_IMAGE_VIEW_TYPE_2D)
                .format(mDepthFormat)
                .subresourceRange { range ->
                    range
                        .aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT)
                        .baseMipLevel(0)
                        .levelCount(1)
                        .baseArrayLayer(0)
                        .layerCount(1)
                }

            val viewBuf = stack.mallocLong(1)
            when (val result = vkCreateImageView(mContext.device, imageInfo, null, viewBuf)) {
                VK_SUCCESS -> {
                    println("depth image view created")
                }

                else -> {
                    throw RuntimeException("error while creating depth image view: $result")
                }
            }

            mImageView = viewBuf.get(0)
        }
    }

    private fun layoutTransitionCmd(cb: VkCommandBuffer) {
        var aspectMask = VK_IMAGE_ASPECT_DEPTH_BIT

        if (mDepthFormat == VK_FORMAT_D32_SFLOAT_S8_UINT || mDepthFormat == VK_FORMAT_D24_UNORM_S8_UINT) {
            aspectMask = aspectMask or VK_IMAGE_ASPECT_STENCIL_BIT
        }

        mStack.push().use { stack ->
            val barrier = VkImageMemoryBarrier.calloc(1, stack)
                .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                .srcAccessMask(0)
                .dstAccessMask(
                    VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT or VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT
                )
                .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                .newLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(mImage)
                .subresourceRange { range ->
                    range
                        .aspectMask(aspectMask)
                        .baseMipLevel(0)
                        .levelCount(1)
                        .baseArrayLayer(0)
                        .layerCount(1)
                }

            vkCmdPipelineBarrier(
                cb,
                VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT,
                0,
                null,
                null,
                barrier
            )
        }
    }

    private fun findSupportedFormat(candidates: List<Int>, tiling: Int, features: Int): Int {
        mStack.push().use { stack ->
            for (format in candidates) {
                val props = VkFormatProperties.calloc(stack)
                vkGetPhysicalDeviceFormatProperties(mContext.physicalDevice, format, props)

                if (tiling == VK_IMAGE_TILING_LINEAR && (props.linearTilingFeatures() and features) == features) {
                    return format
                } else {
                    if (tiling == VK_IMAGE_TILING_OPTIMAL && (props.optimalTilingFeatures() and features) == features) {
                        return format
                    }
                }
            }
        }

        throw RuntimeException("failed to find supported format!")
    }
}
