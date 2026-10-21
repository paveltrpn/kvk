package render

import config.Config
import context.Context
import context.ResourceBase

import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.shaderc.Shaderc.*
import org.lwjgl.vulkan.VK14.*
import org.lwjgl.vulkan.VkShaderModuleCreateInfo

import java.nio.ByteBuffer
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.pathString
import kotlin.io.path.readText

// Trimmed port of tire program definitions (definitions.cpp).
enum class ShaderStageType {
    UNKNOWN,
    VERTEX,
    FRAGMENT
}

val SuffixToStageTypeMap = mapOf(
    "VERTEX" to ShaderStageType.VERTEX,
    "FRAGMENT" to ShaderStageType.FRAGMENT
)

// Port of tire TextProgramSource (textsource.cpp).
// Looks up shader sources named "vk_<programName>_<SUFFIX>.glsl"
// in the "<workPath>/shaders" directory.
class TextProgramSource(programName: String) {
    val sources: List<Pair<ShaderStageType, String>>

    init {
        val shadersPath = Path(Config.instance().workPath, "shaders")

        if (!shadersPath.exists()) {
            throw RuntimeException("path to shaders ${shadersPath.pathString} not exist")
        }

        val found = mutableListOf<Pair<ShaderStageType, String>>()

        for (file in shadersPath.listDirectoryEntries()) {
            if (!file.isRegularFile() || file.extension != "glsl") {
                continue
            }

            val parts = file.nameWithoutExtension.split("_")
            if (parts.size != 3 || parts[0] != "vk" || parts[1] != programName) {
                continue
            }

            val stage = SuffixToStageTypeMap[parts[2]] ?: ShaderStageType.UNKNOWN
            if (stage == ShaderStageType.UNKNOWN) {
                throw RuntimeException("unknown shader stage for file: ${file.pathString}")
            }

            found.add(Pair(stage, file.readText()))
        }

        // Each program must contain at least two shader stages -
        // VERTEX and FRAGMENT.
        if (found.size < 2) {
            throw RuntimeException("not enough source files for program: $programName")
        }

        sources = found
    }
}

// Port of tire Program (program.cpp). Compiles GLSL text sources to
// SPIR-V with shaderc (tire uses glslang - no LWJGL binding exists) and
// creates one VkShaderModule per stage.
class Program(val context: Context, source: TextProgramSource) : ResourceBase() {
    private val mModules: MutableMap<ShaderStageType, Long> = mutableMapOf()

    init {
        for (sourcePair in source.sources) {
            val bytecode = compile(sourcePair.first, sourcePair.second)
            push(sourcePair.first, bytecode)
            MemoryUtil.memFree(bytecode)
        }
    }

    fun get(stage: ShaderStageType): Long {
        return mModules[stage] ?: VK_NULL_HANDLE
    }

    fun free() {
        for (module in mModules.values) {
            vkDestroyShaderModule(context.device, module, null)
        }
        mModules.clear()
    }

    private fun shadercKind(stage: ShaderStageType): Int {
        return when (stage) {
            ShaderStageType.VERTEX -> shaderc_glsl_vertex_shader
            ShaderStageType.FRAGMENT -> shaderc_glsl_fragment_shader
            ShaderStageType.UNKNOWN -> throw RuntimeException("cannot compile UNKNOWN shader stage")
        }
    }

    // Compile GLSL text to SPIR-V. Returns a freshly allocated native
    // ByteBuffer the caller must free with MemoryUtil.memFree.
    private fun compile(stage: ShaderStageType, text: String): ByteBuffer {
        val compiler = shaderc_compiler_initialize()

        if (compiler == MemoryUtil.NULL) {
            throw RuntimeException("failed to initialize shaderc compiler")
        }

        val options = shaderc_compile_options_initialize()

        shaderc_compile_options_set_target_env(options, shaderc_target_env_vulkan, shaderc_env_version_vulkan_1_3)

        val result = shaderc_compile_into_spv(compiler, text, shadercKind(stage), "source", "main", options)

        val status = shaderc_result_get_compilation_status(result)

        if (status != shaderc_compilation_status_success) {
            val message = shaderc_result_get_error_message(result)
            shaderc_result_release(result)
            shaderc_compile_options_release(options)
            shaderc_compiler_release(compiler)
            throw RuntimeException("shader compilation failed for stage $stage: $message")
        }

        val bytes = shaderc_result_get_bytes(result)!!
        val bytecode = MemoryUtil.memAlloc(bytes.remaining())

        bytecode.put(bytes)
        bytecode.flip()

        shaderc_result_release(result)
        shaderc_compile_options_release(options)
        shaderc_compiler_release(compiler)

        return bytecode
    }

    // Create vulkan shader module.
    private fun push(stage: ShaderStageType, bytecode: ByteBuffer) {
        mStack.push().use { stack ->
            val createInfo = VkShaderModuleCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
                .pCode(bytecode)

            val moduleBuf = stack.mallocLong(1)

            when (val result = vkCreateShaderModule(context.device, createInfo, null, moduleBuf)) {
                VK_SUCCESS -> {
                    println("shader module for stage $stage created")
                }

                else -> {
                    throw RuntimeException("failed to create shader module for stage $stage: $result")
                }
            }

            mModules[stage] = moduleBuf.get(0)
        }
    }
}
