package render

import context.Context
import context.ResourceBase

import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK14.*
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo
import org.lwjgl.vulkan.VkPipelineCacheCreateInfo
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo
import org.lwjgl.vulkan.VkPipelineDepthStencilStateCreateInfo
import org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo
import org.lwjgl.vulkan.VkPipelineRenderingCreateInfo
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo
import org.lwjgl.vulkan.VkVertexInputAttributeDescription
import org.lwjgl.vulkan.VkVertexInputBindingDescription

// Fixed state copied from PipelineTestBox. Subclasses provide only the
// vertex input state - the vulkan pipeline must match the mesh buffer
// layout (separated 2xvec3 buffers vs interleaved stride-32), while the
// pipeline layout (with the 144-byte push-constant range) is shared and
// owned by FlatshadeProgram.
abstract class PipelineFlatshadeBase(val context: Context, val layout: Long) : ResourceBase() {
    var mPipeline: Long = VK_NULL_HANDLE
        protected set
    private var mPipelineCache: Long = VK_NULL_HANDLE

    protected abstract fun vertexInputState(stack: MemoryStack): VkPipelineVertexInputStateCreateInfo

    fun buildPipeline(program: Program) {
        // Shader stage flag + module pairs for stages that exist.
        val stages = mutableListOf<Pair<Int, Long>>()

        // Add VERTEX stage
        val vertexModule = program.get(ShaderStageType.VERTEX)
        if (vertexModule != VK_NULL_HANDLE) {
            stages.add(Pair(VK_SHADER_STAGE_VERTEX_BIT, vertexModule))
        }

        // Add FRAGMENT stage
        val fragmentModule = program.get(ShaderStageType.FRAGMENT)
        if (fragmentModule != VK_NULL_HANDLE) {
            stages.add(Pair(VK_SHADER_STAGE_FRAGMENT_BIT, fragmentModule))
        }

        // NOTE: one stack block for the whole pipeline create info - shader
        // stage structs hold stack memory and pName pointers that must stay
        // valid until vkCreateGraphicsPipelines returns.
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

            val vertexInput = vertexInputState(stack)

            val inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
                .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
                .primitiveRestartEnable(false)

            val rasterizer = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
                .depthClampEnable(true)
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
                .blendEnable(false)
                .srcColorBlendFactor(VK_BLEND_FACTOR_ONE)
                .dstColorBlendFactor(VK_BLEND_FACTOR_ZERO)
                .colorBlendOp(VK_BLEND_OP_ADD)
                .srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE)
                .dstAlphaBlendFactor(VK_BLEND_FACTOR_ZERO)
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

            val viewportState = VkPipelineViewportStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
                .viewportCount(1)
                .scissorCount(1)

            val depthStencil = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO)
                .depthTestEnable(true)
                .depthWriteEnable(true)
                .depthCompareOp(VK_COMPARE_OP_LESS)
                .depthBoundsTestEnable(false)
                .minDepthBounds(0.0f)
                .maxDepthBounds(1.0f)
                .stencilTestEnable(false)

            // NOTE: pipeline cache!
            val pipelineCacheInfo = VkPipelineCacheCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_CACHE_CREATE_INFO)

            val cacheBuf = stack.mallocLong(1)
            when (val result = vkCreatePipelineCache(context.device, pipelineCacheInfo, null, cacheBuf)) {
                VK_SUCCESS -> {
                    println("pipeline cache created")
                }

                else -> {
                    throw RuntimeException("failed to create pipeline cache: $result")
                }
            }
            mPipelineCache = cacheBuf.get(0)

            val renderingInfo = VkPipelineRenderingCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_RENDERING_CREATE_INFO)
                .colorAttachmentCount(1)
                .pColorAttachmentFormats(stack.ints(context.surfaceFormat))
                .depthAttachmentFormat(VK_FORMAT_D32_SFLOAT)
                .stencilAttachmentFormat(VK_FORMAT_UNDEFINED)

            // Create pipeline
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
                .layout(layout)
                .renderPass(VK_NULL_HANDLE)
                .subpass(0)
                .basePipelineHandle(VK_NULL_HANDLE)
                .basePipelineIndex(-1)

            val pipelineBuf = stack.mallocLong(1)
            when (val result = vkCreateGraphicsPipelines(context.device, mPipelineCache, pipelineInfo, null, pipelineBuf)) {
                VK_SUCCESS -> {
                    println("graphics pipeline created")
                }

                else -> {
                    throw RuntimeException("failed to create graphics pipeline: $result")
                }
            }
            mPipeline = pipelineBuf.get(0)
        }
    }

    // NOTE: layout is owned by FlatshadeProgram and destroyed there.
    open fun free() {
        vkDestroyPipelineCache(context.device, mPipelineCache, null)
        vkDestroyPipeline(context.device, mPipeline, null)
    }
}

// Vertex input for SeparatedArraysMesh buffers: binding 0 = positions
// (vec3), binding 1 = normals (vec3).
class PipelineFlatshadeSeparated(context: Context, layout: Long) : PipelineFlatshadeBase(context, layout) {
    override fun vertexInputState(stack: MemoryStack): VkPipelineVertexInputStateCreateInfo {
        val bindings = VkVertexInputBindingDescription.calloc(2, stack)
        bindings.get(0)
            .binding(0)
            .stride(12)
            .inputRate(VK_VERTEX_INPUT_RATE_VERTEX)
        bindings.get(1)
            .binding(1)
            .stride(12)
            .inputRate(VK_VERTEX_INPUT_RATE_VERTEX)

        val attributes = VkVertexInputAttributeDescription.calloc(2, stack)
        attributes.get(0)
            .location(0)
            .binding(0)
            .format(VK_FORMAT_R32G32B32_SFLOAT)
            .offset(0)
        attributes.get(1)
            .location(1)
            .binding(1)
            .format(VK_FORMAT_R32G32B32_SFLOAT)
            .offset(0)

        return VkPipelineVertexInputStateCreateInfo.calloc(stack)
            .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
            .pVertexBindingDescriptions(bindings)
            .pVertexAttributeDescriptions(attributes)
    }
}

// Vertex input for InterleavedMesh buffers: single binding, stride 32 -
// pos @0 (vec3), nrm @12 (vec3), uv @24 not declared (shader has no uv
// input).
class PipelineFlatshadeInterleaved(context: Context, layout: Long) : PipelineFlatshadeBase(context, layout) {
    override fun vertexInputState(stack: MemoryStack): VkPipelineVertexInputStateCreateInfo {
        val bindings = VkVertexInputBindingDescription.calloc(1, stack)
        bindings.get(0)
            .binding(0)
            .stride(32)
            .inputRate(VK_VERTEX_INPUT_RATE_VERTEX)

        val attributes = VkVertexInputAttributeDescription.calloc(2, stack)
        attributes.get(0)
            .location(0)
            .binding(0)
            .format(VK_FORMAT_R32G32B32_SFLOAT)
            .offset(0)
        attributes.get(1)
            .location(1)
            .binding(0)
            .format(VK_FORMAT_R32G32B32_SFLOAT)
            .offset(12)

        return VkPipelineVertexInputStateCreateInfo.calloc(stack)
            .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
            .pVertexBindingDescriptions(bindings)
            .pVertexAttributeDescriptions(attributes)
    }
}
