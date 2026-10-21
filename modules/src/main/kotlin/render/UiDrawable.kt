package render

import org.lwjgl.vulkan.VkCommandBuffer

interface UiDrawable {
    fun update(frameIndex: Int)
    fun draw(cb: VkCommandBuffer)
    fun free()
}
