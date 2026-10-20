package render

import algebra.Matrix4
import algebra.Vector3
import context.Context
import context.ResourceBase

import org.lwjgl.vulkan.VK14.*
import org.lwjgl.vulkan.VkCommandBuffer

// Port of tire TestBox (test_box.cpp).
class TestBox(val context: Context) : ResourceBase() {
    private val mPipeline: PipelineTestBox = PipelineTestBox(context)
    private var mPosition: Vector3 = Vector3()
    private var mAngle: Float = 0.0f

    init {
        // tire's Program is a ctor-local destroyed right after pipeline
        // creation - modules are only referenced at pipeline-creation time.
        val program = Program(context, TextProgramSource("testbox"))
        mPipeline.buildPipeline(program)
        program.free()
    }

    fun draw(cb: VkCommandBuffer, duration: Float) {
        // =================================
        val offset = Matrix4().offset(mPosition)
        offset.transpose()

        val (width, height) = context.viewportSize()
        val proj = Matrix4().perspective(40.0f, width.toFloat() / height.toFloat(), 0.1f, 100.0f)

        // kvk a.multiply(b) on column-major flats is cm(B*A), so
        // offset.multiply(proj) == tire proj * offset.
        val viewMatrix = offset.multiply(proj)

        mAngle += duration * 25.0f

        val ax = Vector3(0.0f, 1.0f, 1.0f)
        ax.normalizeSelf()

        // kvk fromAxisAngle is the transpose of tire axis_angle -
        // transpose afterwards to get the identical flat array.
        val modelMatrix = Matrix4().rotate(ax, mAngle)
        modelMatrix.transpose()
        // =================================

        vkCmdBindPipeline(cb, VK_PIPELINE_BIND_POINT_GRAPHICS, mPipeline.pipeline)

        mStack.push().use { stack ->
            val viewBuf = stack.mallocFloat(16)
            viewBuf.put(viewMatrix.data)
            viewBuf.flip()

            val modelBuf = stack.mallocFloat(16)
            modelBuf.put(modelMatrix.data)
            modelBuf.flip()

            vkCmdPushConstants(cb, mPipeline.layout, VK_SHADER_STAGE_VERTEX_BIT, 0, viewBuf)
            vkCmdPushConstants(cb, mPipeline.layout, VK_SHADER_STAGE_VERTEX_BIT, 64, modelBuf)
        }

        vkCmdDraw(cb, 36, 3, 0, 0)
    }

    fun setPosition(x: Float, y: Float, z: Float) {
        mPosition = Vector3(x, y, z)
    }

    fun free() {
        mPipeline.free()
    }
}
