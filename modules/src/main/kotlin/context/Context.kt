package context

import config.Config

import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK14.*
import org.lwjgl.vulkan.KHRSurface.VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR
import org.lwjgl.vulkan.KHRSwapchain.*

class Context(
    val window: Long,
    private var mWidth: Int = 800,
    private var mHeight: Int = 600
) : ResourceBase() {

    class Frame {
        var image: Long = VK_NULL_HANDLE
        var view: Long = VK_NULL_HANDLE
        var imageAvailableSemaphore: Long = VK_NULL_HANDLE
        var renderFinishedSemaphore: Long = VK_NULL_HANDLE
        var inFlightFence: Long = VK_NULL_HANDLE
        var cbPrimary: VkCommandBuffer? = null
    }

    private var mInstance: Instance = Instance()
    private var mSurface: Surface
    private var mDevice: Device
    private var mPools: Pools
    private var mAllocator: Allocator

    private var mBlockingCommandFence: Long = VK_NULL_HANDLE
    private lateinit var mBlockingCommandBuffer: VkCommandBuffer

    private var mSwapchain: Long = VK_NULL_HANDLE
    private var mDepthImage: DepthImage? = null
    private var mFramesCount: Int = 0
    private var mFrames: MutableList<Frame> = mutableListOf()

    private var mAcquiredImageIndex: Int = 0

    @Volatile
    var swapchainOutdated: Boolean = false
        private set

    // Here is all vulkan related objects are created!
    init {
        mSurface = Surface(instance, window)

        mDevice = Device(instance, surface)

        mSurface.queryCapabilities(mDevice)

        mPools = Pools(mDevice)

        mAllocator = Allocator(mDevice.mPhysDevice, device, instance)

        println("picked physical device name: $deviceName")

        initBlockingCommandBuffer()

        makeSwapchain()
        makeFrames()
    }

    val instance: VkInstance
        get(): VkInstance {
            return mInstance.mInstance
        }

    val surface: Long
        get(): Long {
            return mSurface.mSurface
        }

    val deviceName: String
        get(): String {
            return mDevice.mPhysDeviceName
        }

    val device: VkDevice
        get(): VkDevice {
            return mDevice.mLogicalDevice
        }

    val physicalDevice: VkPhysicalDevice
        get(): VkPhysicalDevice {
            return mDevice.mPhysDevice
        }

    val allocator: Long
        get(): Long {
            return mAllocator.allocator
        }

    val surfaceFormat: Int
        get(): Int {
            return mSurface.mSurfaceFormat
        }

    fun viewportSize(): Pair<Int, Int> {
        return Pair(mWidth, mHeight)
    }

    fun markSwapchainOutdated() {
        swapchainOutdated = true
    }

    // Port of tire Context::initBlockingCommandBuffer() (init.cpp:46).
    private fun initBlockingCommandBuffer() {
        mStack.push().use { stack ->
            val fenceInfo = VkFenceCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO)

            val fenceBuf = stack.mallocLong(1)
            when (val result = vkCreateFence(device, fenceInfo, null, fenceBuf)) {
                VK_SUCCESS -> {
                }

                else -> {
                    throw RuntimeException("failed to create blocking command fence: $result")
                }
            }
            mBlockingCommandFence = fenceBuf.get(0)

            val allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                .commandPool(mPools.mCommandPool)
                .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                .commandBufferCount(1)

            val cbBuf = stack.mallocPointer(1)
            when (val result = vkAllocateCommandBuffers(device, allocInfo, cbBuf)) {
                VK_SUCCESS -> {
                }

                else -> {
                    throw RuntimeException("failed to allocate blocking command buffer: $result")
                }
            }
            mBlockingCommandBuffer = VkCommandBuffer(cbBuf.get(0), device)
        }
    }

    // Kotlin-lambda version of tire Context::blockingCommandScope()
    // (commands.cpp:17-74). Records commands and submits them to the
    // graphics queue, then blocks until completion.
    fun blockingScope(block: (VkCommandBuffer) -> Unit) {
        mStack.push().use { stack ->
            vkResetFences(device, stack.longs(mBlockingCommandFence))

            vkResetCommandBuffer(mBlockingCommandBuffer, 0)

            val beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT)

            vkBeginCommandBuffer(mBlockingCommandBuffer, beginInfo)

            // Record commands from the caller.
            block(mBlockingCommandBuffer)

            vkEndCommandBuffer(mBlockingCommandBuffer)

            val submitInfo = VkSubmitInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                .pCommandBuffers(stack.pointers(mBlockingCommandBuffer))

            vkQueueSubmit(mDevice.mGraphicsQueue, submitInfo, mBlockingCommandFence)

            vkWaitForFences(device, stack.longs(mBlockingCommandFence), true, -1L)
        }
    }

    private fun clamp(value: Int, min: Int, max: Int): Int {
        if (value < min) {
            return min
        }
        if (value > max) {
            return max
        }
        return value
    }

    // Port of tire Context::makeSwapchain() (swapchain.cpp:17-117), minus depth image.
    private fun makeSwapchain() {
        mStack.push().use { stack ->
            val caps = mSurface.mSurfaceCapabilities

            println(
                "surface capabilities minImageCount: ${caps.minImageCount()}, " +
                        "maxImageCount: ${caps.maxImageCount()}"
            )

            // Resolve swapchain extent against surface capabilities.
            // If currentExtent is fixed (not UINT32_MAX, seen as -1 here) it must
            // be used verbatim, otherwise clamp the desired size to
            // [minImageExtent, maxImageExtent].
            val swapExtent = VkExtent2D.calloc(stack)
            if (caps.currentExtent().width() != -1) {
                swapExtent.set(caps.currentExtent())
            } else {
                swapExtent.width(
                    clamp(mWidth, caps.minImageExtent().width(), caps.maxImageExtent().width())
                )
                swapExtent.height(
                    clamp(mHeight, caps.minImageExtent().height(), caps.maxImageExtent().height())
                )
            }

            // Keep context size in sync with the real swapchain extent so
            // viewport/scissor/renderArea all match it.
            mWidth = swapExtent.width()
            mHeight = swapExtent.height()

            val createInfo = VkSwapchainCreateInfoKHR.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR)
                .surface(surface)
                .minImageCount(Config.instance().konfig.frame_count)
                .imageFormat(mSurface.mSurfaceFormat)
                .imageColorSpace(mSurface.mSurfaceColorSpace)
                .imageExtent(swapExtent)
                .imageArrayLayers(1)
                .imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
                .preTransform(caps.currentTransform())
                .compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
                .presentMode(mSurface.mPresentMode)
                .clipped(true)
                .oldSwapchain(VK_NULL_HANDLE)
                // NOTE: Device guarantees graphics == present queue family,
                // so EXCLUSIVE sharing mode is always valid here.
                .imageSharingMode(VK_SHARING_MODE_EXCLUSIVE)

            val swapchainBuf = stack.mallocLong(1)
            when (val result = vkCreateSwapchainKHR(device, createInfo, null, swapchainBuf)) {
                VK_SUCCESS -> {
                    println("swapchain created")
                }

                else -> {
                    throw RuntimeException("failed to create swapchain: $result")
                }
            }
            mSwapchain = swapchainBuf.get(0)

            val countBuf = stack.mallocInt(1)
            when (val result = vkGetSwapchainImagesKHR(device, mSwapchain, countBuf, null)) {
                VK_SUCCESS -> {
                    println("swapchain images count: ${countBuf.get(0)}")
                }

                else -> {
                    throw RuntimeException("failed to get swapchain images count: $result")
                }
            }
            mFramesCount = countBuf.get(0)
        }

        // Depth attachment image for the new extent (swapchain.cpp:116).
        mDepthImage = DepthImage(this, mWidth, mHeight)
    }

    // Port of tire Context::makeFramesImages() (swapchain.cpp:119-159).
    private fun makeFramesImages() {
        mStack.push().use { stack ->
            val countBuf = stack.mallocInt(1)
            countBuf.put(0, mFramesCount)
            val imagesBuf = stack.mallocLong(mFramesCount)

            when (val result = vkGetSwapchainImagesKHR(device, mSwapchain, countBuf, imagesBuf)) {
                VK_SUCCESS -> {
                    println("swapchain images acquired")
                }

                else -> {
                    throw RuntimeException("failed to get swapchain images: $result")
                }
            }

            for (i in 0..<mFramesCount) {
                val frame = mFrames[i]
                frame.image = imagesBuf.get(i)

                val createInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                    .image(imagesBuf.get(i))
                    .viewType(VK_IMAGE_VIEW_TYPE_2D)
                    .format(mSurface.mSurfaceFormat)
                    .components { components ->
                        components
                            .r(VK_COMPONENT_SWIZZLE_IDENTITY)
                            .g(VK_COMPONENT_SWIZZLE_IDENTITY)
                            .b(VK_COMPONENT_SWIZZLE_IDENTITY)
                            .a(VK_COMPONENT_SWIZZLE_IDENTITY)
                    }
                    .subresourceRange { range ->
                        range
                            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                            .baseMipLevel(0)
                            .levelCount(1)
                            .baseArrayLayer(0)
                            .layerCount(1)
                    }

                val viewBuf = stack.mallocLong(1)

                when (val result = vkCreateImageView(device, createInfo, null, viewBuf)) {
                    VK_SUCCESS -> {
                    }

                    else -> {
                        throw RuntimeException("failed to create swapchain image view: $result")
                    }
                }

                frame.view = viewBuf.get(0)
            }
        }
    }

    // Port of tire Context::makeFrames() (swapchain.cpp:161-208),
    // minus upload semaphore and upload command buffer.
    private fun makeFrames() {
        for (i in 0..<mFramesCount) {
            mFrames.add(Frame())
        }

        makeFramesImages()

        mStack.push().use { stack ->
            val semaphoreInfo = VkSemaphoreCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO)

            // Fence starts signaled so the first frame's wait passes.
            val fenceInfo = VkFenceCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO)
                .flags(VK_FENCE_CREATE_SIGNALED_BIT)

            for (frame in mFrames) {
                val handleBuf = stack.mallocLong(1)

                when (val result = vkCreateSemaphore(device, semaphoreInfo, null, handleBuf)) {
                    VK_SUCCESS -> {
                    }

                    else -> {
                        throw RuntimeException("failed to create semaphore: $result")
                    }
                }

                frame.imageAvailableSemaphore = handleBuf.get(0)

                when (val result = vkCreateSemaphore(device, semaphoreInfo, null, handleBuf)) {
                    VK_SUCCESS -> {
                    }

                    else -> {
                        throw RuntimeException("failed to create semaphore: $result")
                    }
                }

                frame.renderFinishedSemaphore = handleBuf.get(0)

                when (val result = vkCreateFence(device, fenceInfo, null, handleBuf)) {
                    VK_SUCCESS -> {
                    }

                    else -> {
                        throw RuntimeException("failed to create fence: $result")
                    }
                }

                frame.inFlightFence = handleBuf.get(0)

                val allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                    .commandPool(mPools.mCommandPool)
                    .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(1)

                val cbBuf = stack.mallocPointer(1)

                when (val result = vkAllocateCommandBuffers(device, allocInfo, cbBuf)) {
                    VK_SUCCESS -> {
                    }

                    else -> {
                        throw RuntimeException("failed to allocate command buffer: $result")
                    }
                }

                frame.cbPrimary = VkCommandBuffer(cbBuf.get(0), device)
            }
        }
    }

    // Port of tire Context::acquireImage() (commands.cpp:126-159).
    fun acquireImage(frameId: Int): Boolean {
        val frame = mFrames[frameId]

        mStack.push().use { stack ->
            // Wait for previous use of this frame slot to complete. Fence is NOT
            // reset here: on acquire failure nothing is submitted this frame, and
            // an unsignaled fence would deadlock the next frame. Reset happens in
            // renderScope before the submit that re-signals the fence.
            vkWaitForFences(device, stack.longs(frame.inFlightFence), true, -1L)

            val indexBuf = stack.mallocInt(1)

            val result = vkAcquireNextImageKHR(
                device, mSwapchain, -1L, frame.imageAvailableSemaphore, VK_NULL_HANDLE, indexBuf
            )

            mAcquiredImageIndex = indexBuf.get(0)

            when (result) {
                VK_ERROR_OUT_OF_DATE_KHR -> {
                    // Nothing acquired, imageAvailableSemaphore will never be
                    // signaled — caller must skip the whole frame.
                    markSwapchainOutdated()
                    return false
                }

                VK_SUBOPTIMAL_KHR -> {
                    // Image acquired and must be presented, but recreate swapchain
                    // at the next frame start.
                    markSwapchainOutdated()
                }

                else -> {
                    if (result != VK_SUCCESS) {
                        throw RuntimeException("failed to acquire swapchain image: $result")
                    }
                }
            }


        }

        return true
    }

    // Kotlin-lambda version of tire Context::renderCommandScope()
    // (commands.cpp:161-323), minus upload semaphore.
    fun renderScope(frameId: Int, block: (VkCommandBuffer) -> Unit) {
        val frame = mFrames[frameId]
        val imageIndex = mAcquiredImageIndex
        val target = mFrames[imageIndex]
        val cb = frame.cbPrimary!!

        mStack.push().use { stack ->
            // Fence was already waited in acquireImage() at frame start.
            // Reset it here — it is re-signaled by the render submit at frame
            // end. tire does this in the upload scope, which is skipped here.
            vkResetFences(device, stack.longs(frame.inFlightFence))

            vkResetCommandBuffer(cb, 0)

            val beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                .flags(0)

            vkBeginCommandBuffer(cb, beginInfo)

            val toAttachment = VkImageMemoryBarrier.calloc(1, stack)
                .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                .srcAccessMask(0)
                .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                .newLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(target.image)
                .subresourceRange { range ->
                    range
                        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .baseMipLevel(0)
                        .levelCount(1)
                        .baseArrayLayer(0)
                        .layerCount(1)
                }

            // CMDCALL
            vkCmdPipelineBarrier(
                cb,
                VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                0,
                null,
                null,
                toAttachment
            )

            val clearColor = VkClearValue.calloc(stack)
            val color = clearColor.color()
            color.float32(0, 0.2f)
            color.float32(1, 0.1f)
            color.float32(2, 0.1f)

            // Dynamic rendering, core since Vulkan 1.3.
            val colorAttachmentInfo = VkRenderingAttachmentInfo.calloc(1, stack)
                .sType(VK_STRUCTURE_TYPE_RENDERING_ATTACHMENT_INFO)
                .imageView(target.view)
                .imageLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                .clearValue(clearColor)

            val clearDepth = VkClearValue.calloc(stack)
            clearDepth.depthStencil { depthStencil ->
                depthStencil.depth(1.0f).stencil(0)
            }

            val depthAttachmentInfo = VkRenderingAttachmentInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_RENDERING_ATTACHMENT_INFO)
                .imageView(mDepthImage!!.view)
                .imageLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
                .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                .storeOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                .clearValue(clearDepth)

            val renderingInfo = VkRenderingInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_RENDERING_INFO)
                .renderArea { area ->
                    area.extent { extent ->
                        extent.width(mWidth).height(mHeight)
                    }
                }
                .layerCount(1)
                // NOTE: colorAttachmentCount is derived from the buffer.
                .pColorAttachments(colorAttachmentInfo)
                .pDepthAttachment(depthAttachmentInfo)

            // CMDCALL
            vkCmdBeginRendering(cb, renderingInfo)

            // Dynamic viewport. No performance penalty.
            // NOTE: negative viewport height to use the same projection
            // matrix convention as an OpenGL pipeline (mirrors tire).
            val viewport = VkViewport.calloc(1, stack)
                .x(0.0f)
                .y(mHeight.toFloat())
                .width(mWidth.toFloat())
                .height(-mHeight.toFloat())
                .minDepth(0.0f)
                .maxDepth(1.0f)

            // CMDCALL
            vkCmdSetViewport(cb, 0, viewport)

            val scissor = VkRect2D.calloc(1, stack)
                .offset { offset ->
                    offset.x(0).y(0)
                }
                .extent { extent ->
                    extent.width(mWidth).height(mHeight)
                }

            // CMDCALL
            vkCmdSetScissor(cb, 0, scissor)

            // Record commands from the caller (empty for the black screen).
            block(cb)

            // CMDCALL
            vkCmdEndRendering(cb)

            val toPresent = VkImageMemoryBarrier.calloc(1, stack)
                .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                .srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                .dstAccessMask(0)
                .oldLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                .newLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(target.image)
                .subresourceRange { range ->
                    range
                        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .baseMipLevel(0)
                        .levelCount(1)
                        .baseArrayLayer(0)
                        .layerCount(1)
                }

            // CMDCALL
            vkCmdPipelineBarrier(
                cb,
                VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,
                0,
                null,
                null,
                toPresent
            )

            // CMDCALL
            vkEndCommandBuffer(cb)

            val submitInfo = VkSubmitInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                .waitSemaphoreCount(1)
                .pWaitSemaphores(stack.longs(frame.imageAvailableSemaphore))
                .pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT))
                .pCommandBuffers(stack.pointers(cb))
                .pSignalSemaphores(stack.longs(frame.renderFinishedSemaphore))

            when (val result = vkQueueSubmit(mDevice.mGraphicsQueue, submitInfo, frame.inFlightFence)) {
                VK_SUCCESS -> {
                }

                else -> {
                    throw RuntimeException("failed to submit render command buffer: $result")
                }
            }

            val presentInfo = VkPresentInfoKHR.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
                .pWaitSemaphores(stack.longs(frame.renderFinishedSemaphore))
                .swapchainCount(1)
                .pSwapchains(stack.longs(mSwapchain))
                .pImageIndices(stack.ints(imageIndex))

            val result = vkQueuePresentKHR(mDevice.mPresentQueue, presentInfo)

            if (result != VK_SUCCESS) {
                println("queue present result $result, is it normal?")
                if (result == VK_ERROR_OUT_OF_DATE_KHR || result == VK_SUBOPTIMAL_KHR) {
                    markSwapchainOutdated()
                }
            }
        }
    }

    // Port of tire Context::recreateSwapchain() (context.cpp:72-102).
    fun recreateSwapchain(width: Int = 0, height: Int = 0): Boolean {
        vkDeviceWaitIdle(device)

        mSurface.updateCapabilities()

        var newWidth = width
        var newHeight = height
        if (newWidth == 0 || newHeight == 0) {
            newWidth = mSurface.mSurfaceCapabilities.currentExtent().width()
            newHeight = mSurface.mSurfaceCapabilities.currentExtent().height()
        }

        if (newWidth == 0 || newHeight == 0) {
            return false
        }

        mWidth = newWidth
        mHeight = newHeight

        for (frame in mFrames) {
            vkDestroyImageView(device, frame.view, null)
        }

        mDepthImage?.free()
        mDepthImage = null

        vkDestroySwapchainKHR(device, mSwapchain, null)

        makeSwapchain()
        makeFramesImages()

        swapchainOutdated = false
        return true
    }

    // Port of tire Context::releaseContext() (context.cpp:104-128),
    // minus upload semaphore. Runs after Render.postLoop()'s vkDeviceWaitIdle.
    fun free() {
        println("Release vulkan context.")

        vkDestroyFence(device, mBlockingCommandFence, null)

        for (frame in mFrames) {
            vkDestroySemaphore(device, frame.imageAvailableSemaphore, null)
            vkDestroySemaphore(device, frame.renderFinishedSemaphore, null)
            vkDestroyFence(device, frame.inFlightFence, null)
            vkDestroyImageView(device, frame.view, null)
        }

        mDepthImage?.free()
        mDepthImage = null

        vkDestroySwapchainKHR(device, mSwapchain, null)

        mPools.free()
        mAllocator.free()
        mSurface.free()
        mDevice.free()
        mInstance.free()
    }
}
