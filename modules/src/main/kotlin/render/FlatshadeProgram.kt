package render

import context.Context
import context.ResourceBase

import org.lwjgl.vulkan.VK14.*
import org.lwjgl.vulkan.VkPhysicalDeviceProperties
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo
import org.lwjgl.vulkan.VkPushConstantRange

// Owns the flatshade shader program and both vulkan pipelines (separated
// and interleaved vertex inputs), sharing one pipeline layout with the
// 144-byte push-constant range:
// view_matrix @0 (64B), model_matrix @64 (64B), color @128 (16B).
class FlatshadeProgram(val context: Context) : ResourceBase() {
    private val mProgram: Program
    val layout: Long

    private val mPipelineSeparated: PipelineFlatshadeSeparated
    private val mPipelineInterleaved: PipelineFlatshadeInterleaved

    init {
        // The push-constant block is 144 bytes - check the device limit.
        mStack.push().use { stack ->
            val props = VkPhysicalDeviceProperties.calloc(stack)
            vkGetPhysicalDeviceProperties(context.physicalDevice, props)

            val maxPushConstantsSize = props.limits().maxPushConstantsSize()
            if (maxPushConstantsSize < 144) {
                throw RuntimeException(
                    "flatshade program requires 144 bytes of push constants, device supports $maxPushConstantsSize"
                )
            }
        }

        // Shared pipeline layout, both pipelines are created with it.
        mStack.push().use { stack ->
            val pushConstantRanges = VkPushConstantRange.calloc(1, stack)
                .stageFlags(VK_SHADER_STAGE_VERTEX_BIT)
                .offset(0)
                .size(144)

            val pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                .setLayoutCount(0)
                .pPushConstantRanges(pushConstantRanges)

            val layoutBuf = stack.mallocLong(1)
            when (val result = vkCreatePipelineLayout(context.device, pipelineLayoutInfo, null, layoutBuf)) {
                VK_SUCCESS -> {
                    println("flatshade pipeline layout created")
                }

                else -> {
                    throw RuntimeException("failed to create flatshade pipeline layout: $result")
                }
            }
            layout = layoutBuf.get(0)
        }

        mProgram = Program(context, TextProgramSource("flatshade"))

        mPipelineSeparated = PipelineFlatshadeSeparated(context, layout)
        mPipelineSeparated.buildPipeline(mProgram)

        mPipelineInterleaved = PipelineFlatshadeInterleaved(context, layout)
        mPipelineInterleaved.buildPipeline(mProgram)
    }

    fun getPipeline(buffer: MeshBuffer): Long {
        return when (buffer) {
            is ArrayMeshBuffer -> {
                mPipelineSeparated.mPipeline
            }

            is InterleavedMeshBuffer -> {
                mPipelineInterleaved.mPipeline
            }

            else -> {
                throw RuntimeException("unknown mesh buffer type: ${buffer::class}")
            }
        }
    }

    fun free() {
        mPipelineSeparated.free()
        mPipelineInterleaved.free()
        mProgram.free()
        vkDestroyPipelineLayout(context.device, layout, null)
    }
}
