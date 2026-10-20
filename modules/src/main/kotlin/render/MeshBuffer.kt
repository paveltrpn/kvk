package render

import context.Context
import context.ResourceBase
import mesh.InterleavedMesh
import mesh.SeparatedArraysMesh

import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.vma.Vma.*
import org.lwjgl.util.vma.VmaAllocationCreateInfo
import org.lwjgl.vulkan.VK14.*
import org.lwjgl.vulkan.VkBufferCopy
import org.lwjgl.vulkan.VkBufferCreateInfo
import org.lwjgl.vulkan.VkCommandBuffer

// ============================================================================
// ======================= MeshBuffer =========================================
// ============================================================================

abstract class MeshBuffer(val context: Context) : ResourceBase() {
    abstract fun recordDraw(cb: VkCommandBuffer): Unit

    abstract fun free(): Unit

    // Upload helper: staging CPU_TO_GPU buffer -> blocking copy command ->
    // device-local GPU_ONLY vertex buffer. Returns (buffer, allocation).
    protected fun uploadToDeviceBuffer(data: FloatArray): Pair<Long, Long> {
        val size = data.size.toLong() * Float.SIZE_BYTES

        var stagingBuffer = VK_NULL_HANDLE
        var stagingAllocation = VK_NULL_HANDLE
        var deviceBuffer = VK_NULL_HANDLE
        var deviceAllocation = VK_NULL_HANDLE

        mStack.push().use { stack ->
            val bufferBuf = stack.mallocLong(1)
            val allocationBuf = stack.mallocPointer(1)

            // Staging buffer.
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
                    throw RuntimeException("failed to create staging buffer: $result")
                }
            }

            stagingBuffer = bufferBuf.get(0)
            stagingAllocation = allocationBuf.get(0)

            // Copy data into the staging buffer.
            val mappedPtr = stack.mallocPointer(1)
            vmaMapMemory(context.allocator, stagingAllocation, mappedPtr)

            val mapped = MemoryUtil.memFloatBuffer(mappedPtr.get(0), data.size)
            mapped.put(data)

            vmaUnmapMemory(context.allocator, stagingAllocation)

            // Device-local vertex buffer.
            val deviceInfo = VkBufferCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                .size(size)
                .usage(VK_BUFFER_USAGE_TRANSFER_DST_BIT or VK_BUFFER_USAGE_VERTEX_BUFFER_BIT)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE)

            val deviceAllocInfo = VmaAllocationCreateInfo.calloc(stack)
                .usage(VMA_MEMORY_USAGE_GPU_ONLY)
                .requiredFlags(VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)

            when (val result =
                vmaCreateBuffer(context.allocator, deviceInfo, deviceAllocInfo, bufferBuf, allocationBuf, null)) {
                VK_SUCCESS -> {
                }

                else -> {
                    throw RuntimeException("failed to create device buffer: $result")
                }
            }

            deviceBuffer = bufferBuf.get(0)
            deviceAllocation = allocationBuf.get(0)
        }

        context.blockingScope { cb ->
            mStack.push().use { stack ->
                val region = VkBufferCopy.calloc(1, stack)
                    .srcOffset(0)
                    .dstOffset(0)
                    .size(size)

                vkCmdCopyBuffer(cb, stagingBuffer, deviceBuffer, region)
            }
        }

        vmaDestroyBuffer(context.allocator, stagingBuffer, stagingAllocation)

        return Pair(deviceBuffer, deviceAllocation)
    }
}

// ============================================================================
// ======================= ArrayMeshBuffer ====================================
// ============================================================================

class ArrayMeshBuffer(context: Context, mesh: SeparatedArraysMesh) : MeshBuffer(context) {
    // NOTE: deliberate fix over kogl - draw count is the vertex count, not
    // the float count (kogl's GL buffer passed vertices.size, 3x too many).
    private val mVertexCount: Int = mesh.vertices.size / 3

    private val mVerticesBuffer: Long
    private val mVerticesAllocation: Long
    private val mNormalsBuffer: Long
    private val mNormalsAllocation: Long

    init {
        val vertices = uploadToDeviceBuffer(mesh.vertices)
        mVerticesBuffer = vertices.first
        mVerticesAllocation = vertices.second

        val normals = uploadToDeviceBuffer(mesh.vnormals)
        mNormalsBuffer = normals.first
        mNormalsAllocation = normals.second
    }

    override fun recordDraw(cb: VkCommandBuffer): Unit {
        mStack.push().use { stack ->
            vkCmdBindVertexBuffers(
                cb, 0,
                stack.longs(mVerticesBuffer, mNormalsBuffer),
                stack.longs(0, 0)
            )
        }

        vkCmdDraw(cb, mVertexCount, 1, 0, 0)
    }

    override fun free(): Unit {
        vmaDestroyBuffer(context.allocator, mVerticesBuffer, mVerticesAllocation)
        vmaDestroyBuffer(context.allocator, mNormalsBuffer, mNormalsAllocation)
    }
}

// ============================================================================
// ======================= InterleavedMeshBuffer ==============================
// ============================================================================

class InterleavedMeshBuffer(context: Context, mesh: InterleavedMesh) : MeshBuffer(context) {
    // NOTE: deliberate fix over kogl - draw count is the vertex count
    // (8 floats per vertex: pos3 + nrm3 + uv2).
    private val mVertexCount: Int = mesh.mesh.size / 8

    private val mBuffer: Long
    private val mAllocation: Long

    init {
        val buffer = uploadToDeviceBuffer(mesh.mesh)
        mBuffer = buffer.first
        mAllocation = buffer.second
    }

    override fun recordDraw(cb: VkCommandBuffer): Unit {
        mStack.push().use { stack ->
            vkCmdBindVertexBuffers(cb, 0, stack.longs(mBuffer), stack.longs(0))
        }

        vkCmdDraw(cb, mVertexCount, 1, 0, 0)
    }

    override fun free(): Unit {
        vmaDestroyBuffer(context.allocator, mBuffer, mAllocation)
    }
}
