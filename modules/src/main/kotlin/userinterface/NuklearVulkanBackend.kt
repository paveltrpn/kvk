package userinterface

import org.lwjgl.system.MemoryUtil
import org.lwjgl.nuklear.NkBuffer
import org.lwjgl.nuklear.NkContext
import org.lwjgl.nuklear.NkConvertConfig
import org.lwjgl.nuklear.NkDrawCommand
import org.lwjgl.nuklear.Nuklear.*
import org.lwjgl.util.vma.Vma.*
import org.lwjgl.util.vma.VmaAllocationCreateInfo
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK14.*

import config.Config
import context.Context
import context.ResourceBase
import render.Program
import render.ShaderStageType
import render.TextProgramSource

import java.nio.ByteBuffer
import kotlin.math.ceil
import kotlin.math.floor

class NuklearVulkanBackend(val context: Context) : ResourceBase() {
    private class FrameBuffers {
        var mVertexBuffer: Long = VK_NULL_HANDLE
        var mVertexAllocation: Long = VK_NULL_HANDLE
        var mVertexMapped: Long = 0
        var mVertexCapacity: Long = 0

        var mIndexBuffer: Long = VK_NULL_HANDLE
        var mIndexAllocation: Long = VK_NULL_HANDLE
        var mIndexMapped: Long = 0
        var mIndexCapacity: Long = 0
    }

    private val _framesCount: Int = Config.instance().konfig.frame_count
    private val _frames: MutableList<FrameBuffers> = mutableListOf()

    private var _descriptorSetLayout: Long = VK_NULL_HANDLE
    private var _descriptorSet: Long = VK_NULL_HANDLE

    private var _fontImage: Long = VK_NULL_HANDLE
    private var _fontAllocation: Long = VK_NULL_HANDLE
    private var _fontView: Long = VK_NULL_HANDLE
    private var _fontSampler: Long = VK_NULL_HANDLE

    private var _pipelineLayout: Long = VK_NULL_HANDLE
    private var _pipeline: Long = VK_NULL_HANDLE
    private var _pipelineCache: Long = VK_NULL_HANDLE

    init {
        initFrameBuffers()
        initDescriptorSetLayout()

        val program = Program(context, TextProgramSource("nuklear"))
        buildPipeline(program)
        program.free()
    }

    fun uploadFontTexture(pixels: ByteBuffer, width: Int, height: Int) {
        val size = width.toLong() * height.toLong() * 4L

        var stagingBuffer = VK_NULL_HANDLE
        var stagingAllocation = VK_NULL_HANDLE

        mStack.push().use { stack ->
            val bufferBuf = stack.mallocLong(1)
            val allocationBuf = stack.mallocPointer(1)

            val stagingInfo = VkBufferCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                .size(size)
                .usage(VK_BUFFER_USAGE_TRANSFER_SRC_BIT)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE)

            val stagingAllocInfo = VmaAllocationCreateInfo.calloc(stack)
                .usage(VMA_MEMORY_USAGE_CPU_TO_GPU)

            when (val result =
                vmaCreateBuffer(context.allocator, stagingInfo, stagingAllocInfo, bufferBuf, allocationBuf, null)) {
                VK_SUCCESS -> {
                }

                else -> {
                    throw RuntimeException("failed to create nuklear font staging buffer: $result")
                }
            }

            stagingBuffer = bufferBuf.get(0)
            stagingAllocation = allocationBuf.get(0)

            val mappedPtr = stack.mallocPointer(1)
            vmaMapMemory(context.allocator, stagingAllocation, mappedPtr)

            val mapped = MemoryUtil.memByteBuffer(mappedPtr.get(0), size.toInt())
            mapped.put(pixels)

            vmaUnmapMemory(context.allocator, stagingAllocation)

            val imageInfo = VkImageCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
                .flags(0)
                .imageType(VK_IMAGE_TYPE_2D)
                .format(VK_FORMAT_R8G8B8A8_UNORM)
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
                .usage(VK_IMAGE_USAGE_TRANSFER_DST_BIT or VK_IMAGE_USAGE_SAMPLED_BIT)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)

            val imageAllocInfo = VmaAllocationCreateInfo.calloc(stack)
                .usage(VMA_MEMORY_USAGE_GPU_ONLY)
                .requiredFlags(VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)

            when (val result =
                vmaCreateImage(context.allocator, imageInfo, imageAllocInfo, bufferBuf, allocationBuf, null)) {
                VK_SUCCESS -> {
                }

                else -> {
                    throw RuntimeException("failed to create nuklear font image: $result")
                }
            }

            _fontImage = bufferBuf.get(0)
            _fontAllocation = allocationBuf.get(0)

            val viewInfo = VkImageViewCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                .image(_fontImage)
                .viewType(VK_IMAGE_VIEW_TYPE_2D)
                .format(VK_FORMAT_R8G8B8A8_UNORM)
                .subresourceRange { range ->
                    range
                        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .baseMipLevel(0)
                        .levelCount(1)
                        .baseArrayLayer(0)
                        .layerCount(1)
                }

            when (val result = vkCreateImageView(context.device, viewInfo, null, bufferBuf)) {
                VK_SUCCESS -> {
                }

                else -> {
                    throw RuntimeException("failed to create nuklear font image view: $result")
                }
            }

            _fontView = bufferBuf.get(0)

            val samplerInfo = VkSamplerCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)
                .magFilter(VK_FILTER_LINEAR)
                .minFilter(VK_FILTER_LINEAR)
                .mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST)
                .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                .mipLodBias(0.0f)
                .anisotropyEnable(false)
                .maxAnisotropy(1.0f)
                .compareEnable(false)
                .compareOp(VK_COMPARE_OP_NEVER)
                .minLod(0.0f)
                .maxLod(0.0f)
                .borderColor(VK_BORDER_COLOR_FLOAT_OPAQUE_WHITE)
                .unnormalizedCoordinates(false)

            when (val result = vkCreateSampler(context.device, samplerInfo, null, bufferBuf)) {
                VK_SUCCESS -> {
                }

                else -> {
                    throw RuntimeException("failed to create nuklear font sampler: $result")
                }
            }

            _fontSampler = bufferBuf.get(0)
        }

        context.blockingScope { cb ->
            mStack.push().use { stack ->
                val toTransfer = VkImageMemoryBarrier.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                    .srcAccessMask(0)
                    .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .newLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(_fontImage)
                    .subresourceRange { range ->
                        range
                            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                            .baseMipLevel(0)
                            .levelCount(1)
                            .baseArrayLayer(0)
                            .layerCount(1)
                    }

                vkCmdPipelineBarrier(
                    cb,
                    VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                    VK_PIPELINE_STAGE_TRANSFER_BIT,
                    0,
                    null,
                    null,
                    toTransfer
                )

                val region = VkBufferImageCopy.calloc(1, stack)
                    .bufferOffset(0)
                    .bufferRowLength(0)
                    .bufferImageHeight(0)
                    .imageSubresource { subresource ->
                        subresource
                            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                            .mipLevel(0)
                            .baseArrayLayer(0)
                            .layerCount(1)
                    }
                    .imageOffset { offset ->
                        offset
                            .x(0)
                            .y(0)
                            .z(0)
                    }
                    .imageExtent { extent ->
                        extent
                            .width(width)
                            .height(height)
                            .depth(1)
                    }

                vkCmdCopyBufferToImage(cb, stagingBuffer, _fontImage, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region)

                val toShaderRead = VkImageMemoryBarrier.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                    .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                    .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                    .newLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(_fontImage)
                    .subresourceRange { range ->
                        range
                            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                            .baseMipLevel(0)
                            .levelCount(1)
                            .baseArrayLayer(0)
                            .layerCount(1)
                    }

                vkCmdPipelineBarrier(
                    cb,
                    VK_PIPELINE_STAGE_TRANSFER_BIT,
                    VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                    0,
                    null,
                    null,
                    toShaderRead
                )
            }
        }

        vmaDestroyBuffer(context.allocator, stagingBuffer, stagingAllocation)

        writeDescriptorSet()
    }

    fun convert(
        ctx: NkContext,
        cmds: NkBuffer,
        vbuf: NkBuffer,
        ebuf: NkBuffer,
        config: NkConvertConfig,
        frameIndex: Int
    ) {
        nk_convert(ctx, cmds, vbuf, ebuf, config)

        val vertexSize = nk_buffer_total(vbuf)
        val indexSize = nk_buffer_total(ebuf)

        if (vertexSize == 0L || indexSize == 0L) {
            return
        }

        val frame = _frames[frameIndex]

        if (vertexSize > frame.mVertexCapacity || indexSize > frame.mIndexCapacity) {
            var newVertexCapacity = frame.mVertexCapacity
            if (vertexSize > newVertexCapacity) {
                newVertexCapacity = vertexSize * 2L
            }

            var newIndexCapacity = frame.mIndexCapacity
            if (indexSize > newIndexCapacity) {
                newIndexCapacity = indexSize * 2L
            }

            freeFrameBuffers(frame)
            allocateFrameBuffers(frame, newVertexCapacity, newIndexCapacity)
        }

        MemoryUtil.memCopy(nk_buffer_memory_const(vbuf), frame.mVertexMapped, vertexSize)
        MemoryUtil.memCopy(nk_buffer_memory_const(ebuf), frame.mIndexMapped, indexSize)
    }

    fun draw(cb: VkCommandBuffer, ctx: NkContext, cmds: NkBuffer, frameIndex: Int) {
        val frame = _frames[frameIndex]
        val viewportSize = context.viewportSize()
        val width = viewportSize.first
        val height = viewportSize.second

        mStack.push().use { stack ->
            vkCmdBindPipeline(cb, VK_PIPELINE_BIND_POINT_GRAPHICS, _pipeline)

            vkCmdBindDescriptorSets(
                cb,
                VK_PIPELINE_BIND_POINT_GRAPHICS,
                _pipelineLayout,
                0,
                stack.longs(_descriptorSet),
                null
            )

            val pushConstants = stack.floats(
                2.0f / width.toFloat(),
                -2.0f / height.toFloat(),
                -1.0f,
                1.0f
            )

            vkCmdPushConstants(cb, _pipelineLayout, VK_SHADER_STAGE_VERTEX_BIT, 0, pushConstants)

            vkCmdBindVertexBuffers(cb, 0, stack.longs(frame.mVertexBuffer), stack.longs(0L))

            vkCmdBindIndexBuffer(cb, frame.mIndexBuffer, 0L, VK_INDEX_TYPE_UINT16)
        }

        var indexOffset = 0
        var command: NkDrawCommand? = nk__draw_begin(ctx, cmds)

        while (command != null) {
            val elemCount = command.elem_count()

            if (elemCount > 0) {

                // === Record draw command === //

                val recordDrawCommand: (
                    VkCommandBuffer,
                    NkDrawCommand,
                    Int,
                    Int,
                    Int
                ) -> Unit = { cb: VkCommandBuffer,
                              command: NkDrawCommand,
                              indexOffset: Int,
                              width: Int,
                              height: Int ->
                    val clip = command.clip_rect()

                    val x0 = maxOf(0, floor(clip.x().toDouble()).toInt())
                    val y0 = maxOf(0, floor(clip.y().toDouble()).toInt())
                    val x1 = minOf(width, ceil((clip.x() + clip.w()).toDouble()).toInt())
                    val y1 = minOf(height, ceil((clip.y() + clip.h()).toDouble()).toInt())

                    if (!(x1 <= x0 || y1 <= y0)) {
                        mStack.push().use { stack ->
                            val scissor = VkRect2D.calloc(1, stack)
                                .offset { offset ->
                                    offset
                                        .x(x0)
                                        .y(y0)
                                }
                                .extent { extent ->
                                    extent
                                        .width(x1 - x0)
                                        .height(y1 - y0)
                                }

                            vkCmdSetScissor(cb, 0, scissor)
                        }

                        vkCmdDrawIndexed(cb, command.elem_count(), 1, indexOffset, 0, 0)
                    }
                }

                recordDrawCommand(cb, command, indexOffset, width, height)
            }

            indexOffset += elemCount
            command = nk__draw_next(command, cmds, ctx)
        }
    }

    fun free() {
        for (frame in _frames) {
            freeFrameBuffers(frame)
        }
        
        _frames.clear()

        vkDestroyPipeline(context.device, _pipeline, null)
        vkDestroyPipelineCache(context.device, _pipelineCache, null)
        vkDestroyPipelineLayout(context.device, _pipelineLayout, null)

        vkDestroyDescriptorSetLayout(context.device, _descriptorSetLayout, null)

        vkDestroySampler(context.device, _fontSampler, null)
        vkDestroyImageView(context.device, _fontView, null)
        vmaDestroyImage(context.allocator, _fontImage, _fontAllocation)
    }

    private fun initFrameBuffers() {
        for (i in 0..<_framesCount) {
            val frame = FrameBuffers()

            val _INITIAL_VERTEX_SIZE = 512L * 1024L
            val _INITIAL_INDEX_SIZE = 128L * 1024L
            allocateFrameBuffers(frame, _INITIAL_VERTEX_SIZE, _INITIAL_INDEX_SIZE)

            _frames.add(frame)
        }
    }

    private fun allocateFrameBuffers(frame: FrameBuffers, vertexSize: Long, indexSize: Long) {
        mStack.push().use { stack ->
            val bufferBuf = stack.mallocLong(1)
            val allocationBuf = stack.mallocPointer(1)

            val vertexInfo = VkBufferCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                .size(vertexSize)
                .usage(VK_BUFFER_USAGE_VERTEX_BUFFER_BIT)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE)

            val vertexAllocInfo = VmaAllocationCreateInfo.calloc(stack)
                .usage(VMA_MEMORY_USAGE_CPU_TO_GPU)

            when (val result =
                vmaCreateBuffer(context.allocator, vertexInfo, vertexAllocInfo, bufferBuf, allocationBuf, null)) {
                VK_SUCCESS -> {
                }

                else -> {
                    throw RuntimeException("failed to create nuklear vertex buffer: $result")
                }
            }

            frame.mVertexBuffer = bufferBuf.get(0)
            frame.mVertexAllocation = allocationBuf.get(0)
            frame.mVertexCapacity = vertexSize

            val mappedVertexPtr = stack.mallocPointer(1)
            vmaMapMemory(context.allocator, frame.mVertexAllocation, mappedVertexPtr)
            frame.mVertexMapped = mappedVertexPtr.get(0)

            val indexInfo = VkBufferCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                .size(indexSize)
                .usage(VK_BUFFER_USAGE_INDEX_BUFFER_BIT)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE)

            val indexAllocInfo = VmaAllocationCreateInfo.calloc(stack)
                .usage(VMA_MEMORY_USAGE_CPU_TO_GPU)

            when (val result =
                vmaCreateBuffer(context.allocator, indexInfo, indexAllocInfo, bufferBuf, allocationBuf, null)) {
                VK_SUCCESS -> {
                }

                else -> {
                    throw RuntimeException("failed to create nuklear index buffer: $result")
                }
            }

            frame.mIndexBuffer = bufferBuf.get(0)
            frame.mIndexAllocation = allocationBuf.get(0)
            frame.mIndexCapacity = indexSize

            val mappedIndexPtr = stack.mallocPointer(1)
            vmaMapMemory(context.allocator, frame.mIndexAllocation, mappedIndexPtr)
            frame.mIndexMapped = mappedIndexPtr.get(0)
        }
    }

    private fun freeFrameBuffers(frame: FrameBuffers) {
        vmaUnmapMemory(context.allocator, frame.mVertexAllocation)
        vmaUnmapMemory(context.allocator, frame.mIndexAllocation)

        vmaDestroyBuffer(context.allocator, frame.mVertexBuffer, frame.mVertexAllocation)
        vmaDestroyBuffer(context.allocator, frame.mIndexBuffer, frame.mIndexAllocation)

        frame.mVertexBuffer = VK_NULL_HANDLE
        frame.mVertexAllocation = VK_NULL_HANDLE
        frame.mVertexMapped = 0
        frame.mVertexCapacity = 0

        frame.mIndexBuffer = VK_NULL_HANDLE
        frame.mIndexAllocation = VK_NULL_HANDLE
        frame.mIndexMapped = 0
        frame.mIndexCapacity = 0
    }

    private fun initDescriptorSetLayout() {
        mStack.push().use { stack ->
            val bindings = VkDescriptorSetLayoutBinding.calloc(1, stack)
                .binding(0)
                .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1)
                .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT)

            val layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                .pBindings(bindings)

            val layoutBuf = stack.mallocLong(1)
            when (val result = vkCreateDescriptorSetLayout(context.device, layoutInfo, null, layoutBuf)) {
                VK_SUCCESS -> {
                }

                else -> {
                    throw RuntimeException("failed to create nuklear descriptor set layout: $result")
                }
            }

            _descriptorSetLayout = layoutBuf.get(0)
        }
    }

    private fun writeDescriptorSet() {
        mStack.push().use { stack ->
            val allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                .descriptorPool(context.descriptorPool)
                .pSetLayouts(stack.longs(_descriptorSetLayout))

            val setBuf = stack.mallocLong(1)
            when (val result = vkAllocateDescriptorSets(context.device, allocInfo, setBuf)) {
                VK_SUCCESS -> {
                }

                else -> {
                    throw RuntimeException("failed to allocate nuklear descriptor set: $result")
                }
            }

            _descriptorSet = setBuf.get(0)

            val imageInfo = VkDescriptorImageInfo.calloc(1, stack)
                .sampler(_fontSampler)
                .imageView(_fontView)
                .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)

            val write = VkWriteDescriptorSet.calloc(1, stack)
                .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                .dstSet(_descriptorSet)
                .dstBinding(0)
                .dstArrayElement(0)
                .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1)
                .pImageInfo(imageInfo)

            vkUpdateDescriptorSets(context.device, write, null)
        }
    }

    private fun buildPipeline(program: Program) {
        val stages = mutableListOf<Pair<Int, Long>>()

        val vertexModule = program.get(ShaderStageType.VERTEX)
        if (vertexModule != VK_NULL_HANDLE) {
            stages.add(Pair(VK_SHADER_STAGE_VERTEX_BIT, vertexModule))
        }

        val fragmentModule = program.get(ShaderStageType.FRAGMENT)
        if (fragmentModule != VK_NULL_HANDLE) {
            stages.add(Pair(VK_SHADER_STAGE_FRAGMENT_BIT, fragmentModule))
        }

        mStack.push().use { stack ->
            val shaderStages = VkPipelineShaderStageCreateInfo.calloc(stages.size, stack)
            for (index in stages.indices) {
                val stagePair = stages[index]
                shaderStages.get(index)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(stagePair.first)
                    .module(stagePair.second)
                    .pName(stack.UTF8("main"))
            }

            val bindingDescription = VkVertexInputBindingDescription.calloc(1, stack)
                .binding(0)
                .stride(20)
                .inputRate(VK_VERTEX_INPUT_RATE_VERTEX)

            val attributeDescriptions = VkVertexInputAttributeDescription.calloc(3, stack)
            attributeDescriptions.get(0)
                .binding(0)
                .location(0)
                .format(VK_FORMAT_R32G32_SFLOAT)
                .offset(0)
            attributeDescriptions.get(1)
                .binding(0)
                .location(1)
                .format(VK_FORMAT_R32G32_SFLOAT)
                .offset(8)
            attributeDescriptions.get(2)
                .binding(0)
                .location(2)
                .format(VK_FORMAT_R8G8B8A8_UNORM)
                .offset(16)

            val vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
                .pVertexBindingDescriptions(bindingDescription)
                .pVertexAttributeDescriptions(attributeDescriptions)

            val inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
                .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
                .primitiveRestartEnable(false)

            val rasterizer = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
                .depthClampEnable(false)
                .rasterizerDiscardEnable(false)
                .polygonMode(VK_POLYGON_MODE_FILL)
                .cullMode(VK_CULL_MODE_NONE)
                .frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE)
                .depthBiasEnable(false)
                .depthBiasConstantFactor(0.0f)
                .depthBiasClamp(0.0f)
                .depthBiasSlopeFactor(0.0f)
                .lineWidth(1.0f)

            val multisampling = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
                .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT)
                .sampleShadingEnable(false)
                .minSampleShading(1.0f)
                .alphaToCoverageEnable(false)
                .alphaToOneEnable(false)

            val colorBlendAttachment = VkPipelineColorBlendAttachmentState.calloc(1, stack)
                .blendEnable(true)
                .srcColorBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA)
                .dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                .colorBlendOp(VK_BLEND_OP_ADD)
                .srcAlphaBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA)
                .dstAlphaBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                .alphaBlendOp(VK_BLEND_OP_ADD)
                .colorWriteMask(
                    VK_COLOR_COMPONENT_R_BIT or VK_COLOR_COMPONENT_G_BIT or
                            VK_COLOR_COMPONENT_B_BIT or VK_COLOR_COMPONENT_A_BIT
                )

            val colorBlending = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
                .logicOpEnable(false)
                .logicOp(VK_LOGIC_OP_COPY)
                .pAttachments(colorBlendAttachment)
                .blendConstants(stack.floats(0.0f, 0.0f, 0.0f, 0.0f))

            val dynamicState = VkPipelineDynamicStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO)
                .pDynamicStates(
                    stack.ints(
                        VK_DYNAMIC_STATE_VIEWPORT,
                        VK_DYNAMIC_STATE_SCISSOR,
                        VK_DYNAMIC_STATE_LINE_WIDTH
                    )
                )

            val pushConstantRanges = VkPushConstantRange.calloc(1, stack)
                .stageFlags(VK_SHADER_STAGE_VERTEX_BIT)
                .offset(0)
                .size(16)

            val pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                .pSetLayouts(stack.longs(_descriptorSetLayout))
                .pPushConstantRanges(pushConstantRanges)

            val layoutBuf = stack.mallocLong(1)
            when (val result = vkCreatePipelineLayout(context.device, pipelineLayoutInfo, null, layoutBuf)) {
                VK_SUCCESS -> {
                    println("nuklear pipeline layout created")
                }

                else -> {
                    throw RuntimeException("failed to create nuklear pipeline layout: $result")
                }
            }
            _pipelineLayout = layoutBuf.get(0)

            val viewportState = VkPipelineViewportStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
                .viewportCount(1)
                .scissorCount(1)

            val depthStencil = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO)
                .depthTestEnable(false)
                .depthWriteEnable(false)
                .depthCompareOp(VK_COMPARE_OP_LESS_OR_EQUAL)
                .depthBoundsTestEnable(false)
                .minDepthBounds(0.0f)
                .maxDepthBounds(1.0f)
                .stencilTestEnable(false)

            val pipelineCacheInfo = VkPipelineCacheCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_CACHE_CREATE_INFO)

            val cacheBuf = stack.mallocLong(1)
            when (val result = vkCreatePipelineCache(context.device, pipelineCacheInfo, null, cacheBuf)) {
                VK_SUCCESS -> {
                    println("nuklear pipeline cache created")
                }

                else -> {
                    throw RuntimeException("failed to create nuklear pipeline cache: $result")
                }
            }
            _pipelineCache = cacheBuf.get(0)

            val renderingInfo = VkPipelineRenderingCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_RENDERING_CREATE_INFO)
                .colorAttachmentCount(1)
                .pColorAttachmentFormats(stack.ints(context.surfaceFormat))
                .depthAttachmentFormat(VK_FORMAT_D32_SFLOAT)
                .stencilAttachmentFormat(VK_FORMAT_UNDEFINED)

            val pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                .pNext(renderingInfo)
                .pStages(shaderStages)
                .pVertexInputState(vertexInput)
                .pInputAssemblyState(inputAssembly)
                .pViewportState(viewportState)
                .pRasterizationState(rasterizer)
                .pMultisampleState(multisampling)
                .pDepthStencilState(depthStencil)
                .pColorBlendState(colorBlending)
                .pDynamicState(dynamicState)
                .layout(_pipelineLayout)
                .renderPass(VK_NULL_HANDLE)
                .subpass(0)
                .basePipelineHandle(VK_NULL_HANDLE)
                .basePipelineIndex(-1)

            val pipelineBuf = stack.mallocLong(1)
            when (val result =
                vkCreateGraphicsPipelines(context.device, _pipelineCache, pipelineInfo, null, pipelineBuf)) {
                VK_SUCCESS -> {
                    println("nuklear graphics pipeline created")
                }

                else -> {
                    throw RuntimeException("failed to create nuklear graphics pipeline: $result")
                }
            }
            _pipeline = pipelineBuf.get(0)
        }
    }
}
