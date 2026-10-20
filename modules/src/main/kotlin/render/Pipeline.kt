package render

import context.Context
import context.ResourceBase

import org.lwjgl.vulkan.VK14.*
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo
import org.lwjgl.vulkan.VkPipelineCacheCreateInfo
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo
import org.lwjgl.vulkan.VkPipelineDepthStencilStateCreateInfo
import org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo
import org.lwjgl.vulkan.VkPipelineRenderingCreateInfo
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo
import org.lwjgl.vulkan.VkPushConstantRange

// Port of tire Pipeline (pipeline.cpp), minus VkRenderPass
// (dynamic rendering only here).
abstract class Pipeline(val context: Context) : ResourceBase() {
    var mPipeline: Long = VK_NULL_HANDLE
        protected set
    var mLayout: Long = VK_NULL_HANDLE
        protected set

    val pipeline: Long
        get(): Long {
            return mPipeline
        }

    val layout: Long
        get(): Long {
            return mLayout
        }

    abstract fun buildPipeline(program: Program)

    open fun free() {
        vkDestroyPipeline(context.device, mPipeline, null)
        vkDestroyPipelineLayout(context.device, mLayout, null)
    }
}

// Verbatim port of tire PiplineTestBox (pipeline_test_box.cpp),
// reduced to the VERTEX + FRAGMENT stages that exist for this program.
class PipelineTestBox(context: Context) : Pipeline(context) {
    private var mPipelineCache: Long = VK_NULL_HANDLE

    override fun buildPipeline(program: Program) {
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

            // Init fixed stages (empty vertex input - positions are
            // hardcoded in the shader).
            val vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)

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

            // This pipeline layout initialization
            // setup push constants
            val pushConstantRanges = VkPushConstantRange.calloc(1, stack)
                .stageFlags(VK_SHADER_STAGE_VERTEX_BIT)
                .offset(0)
                .size(4 * 16 * 2)

            val pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                .setLayoutCount(0)
                .pPushConstantRanges(pushConstantRanges)

            val layoutBuf = stack.mallocLong(1)
            when (val result = vkCreatePipelineLayout(context.device, pipelineLayoutInfo, null, layoutBuf)) {
                VK_SUCCESS -> {
                    println("pipeline layout created")
                }

                else -> {
                    throw RuntimeException("failed to create pipeline layout: $result")
                }
            }
            mLayout = layoutBuf.get(0)

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
                .layout(mLayout)
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

    override fun free() {
        vkDestroyPipelineCache(context.device, mPipelineCache, null)
        super.free()
    }
}
