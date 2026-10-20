package graph

import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK14.*
import org.lwjgl.vulkan.VkCommandBuffer

import algebra.*
import context.Context
import render.*

// ============================================================================
// ======================= DrawCallVisitorBase ================================
// ============================================================================

abstract class DrawCallVisitorBase(delta: Float, viewMatrix: Matrix4) : Visitor() {
    protected val _delta: Float
    protected var _viewMatrix: Matrix4

    protected lateinit var _program: FlatshadeProgram
    protected var _modelMatrixStack = MatrixStack()

    protected var _drawCallsCount = 0

    init {
        _delta = delta
        _viewMatrix = viewMatrix
    }

    val drawCallsCount: Int
        get(): Int {
            return _drawCallsCount
        }

    override fun apply(node: Switch): Unit {
        node.traverse(this)
    }

    override fun apply(node: StateGroup): Unit {
        // NOTE: no pipeline bind here (unlike kogl's GL program bind) -
        // vulkan needs a pipeline per vertex-input layout, picked per
        // drawable in DrawableVisitor.
        _program = node.program

        node.traverse(this)
    }

    override fun apply(node: Group): Unit {
        node.traverse(this)
    }

    override fun apply(node: TransformGroup): Unit {
        with(_modelMatrixStack) {
            push(node)
            node.traverse(this@DrawCallVisitorBase)
            pop()
        }
    }
}

// ============================================================================
// ======================= DrawableVisitor ====================================
// ============================================================================

class DrawableVisitor(delta: Float, viewMatrix: Matrix4, val cb: VkCommandBuffer) :
    DrawCallVisitorBase(delta, viewMatrix) {
    override fun apply(node: Leaf): Unit {
        when (node) {
            is Drawable -> {
                when (node) {
                    is SpinableDrawable -> {
                        val local = node.updateLocal(_delta)

                        val top = _modelMatrixStack.top.copy()
                        top.transpose()

                        node.applyTransform(local.multiply(top))
                    }

                    is FlyaroundDrawable -> {
                        val local = node.updateLocal(_delta)

                        val top = _modelMatrixStack.top.copy()
                        top.transpose()

                        node.applyTransform(local.multiply(top))
                    }
                }

                vkCmdBindPipeline(cb, VK_PIPELINE_BIND_POINT_GRAPHICS, _program.getPipeline(node.buffer))

                // One 144-byte push-constant block per draw call:
                // view_matrix @0 (64B), model_matrix @64 (64B), color @128 (16B).
                MemoryStack.stackPush().use { stack ->
                    val buf = stack.mallocFloat(36)
                    buf.put(_viewMatrix.data)
                    buf.put(node.modelMatrix.data)
                    buf.put(node.color.x)
                    buf.put(node.color.y)
                    buf.put(node.color.z)
                    buf.put(1.0f)
                    buf.flip()

                    vkCmdPushConstants(cb, _program.layout, VK_SHADER_STAGE_VERTEX_BIT, 0, buf)
                }

                node.draw(cb)

                _drawCallsCount++
            }
        }
    }
}

// ============================================================================
// ======================= UploadVisitor ======================================
// ============================================================================

private class UploadVisitor(val context: Context) : Visitor() {
    override fun apply(node: Switch): Unit {
        node.traverse(this)
    }

    override fun apply(node: StateGroup): Unit {
        node.traverse(this)
    }

    override fun apply(node: Group): Unit {
        node.traverse(this)
    }

    override fun apply(node: TransformGroup): Unit {
        node.traverse(this)
    }

    override fun apply(node: Leaf): Unit {
        if (node is Drawable) {
            node.upload(context)
        }
    }
}

// ============================================================================
// ======================= FreeVisitor ========================================
// ============================================================================

private class FreeVisitor : Visitor() {
    override fun apply(node: Switch): Unit {
        node.traverse(this)
    }

    override fun apply(node: StateGroup): Unit {
        node.traverse(this)
    }

    override fun apply(node: Group): Unit {
        node.traverse(this)
    }

    override fun apply(node: TransformGroup): Unit {
        node.traverse(this)
    }

    override fun apply(node: Leaf): Unit {
        if (node is Drawable) {
            node.free()
        }
    }
}

// ============================================================================
// ======================= Scene ==============================================
// ============================================================================

/**
 * This object serve as the largest unit of scenegraph representation,
 * and can also be thought of as database. Scene can be very large, both
 * in physical space units and in content.
 */
class Scene(aspect: Float) {
    private var _locales: MutableList<Locale> = mutableListOf()

    private var _camera = Flycam()

    private var _drawCallsCount = 0

    val drawCallsCount: Int
        get(): Int {
            return _drawCallsCount
        }

    init {
        with(_camera) {
            fov = 45.0f
            this.aspect = aspect
            ncp = 0.1f
            fcp = 100.0f

            eye = Vector3(-18.0f, -13.5f, -14.5f)
            azimuth = 54.0f
            elevation = -31.0f
        }
    }

    var camera: Flycam
        get(): Flycam {
            return _camera
        }
        set(value) {
            _camera = value
        }

    fun walk(cb: VkCommandBuffer): Unit {
        _camera.traverse()

        val r = DrawableVisitor(1.0f, _camera.viewMatrix, cb)
        for (locale in _locales) {
            locale.root.accept(r)
        }

        _drawCallsCount = r.drawCallsCount
    }

    fun upload(context: Context): Unit {
        val u = UploadVisitor(context)
        for (locale in _locales) {
            locale.root.accept(u)
        }
    }

    fun free(): Unit {
        val f = FreeVisitor()
        for (locale in _locales) {
            locale.root.accept(f)
        }
    }

    fun addLocale(locale: Locale): Unit {
        _locales.addLast(locale)
    }

    fun addLocales(locales: List<Locale>): Unit {
        for (locale in locales) {
            _locales.addLast(locale)
        }
    }
}
