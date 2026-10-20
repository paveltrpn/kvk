package generator

import algebra.Vector2
import algebra.Vector3

enum class Axis(val mIndex: Int) {
    X(0),
    Y(1),
    Z(2)
}

class IntPair(val mX: Int, val mY: Int)

class IntTriple(val mX: Int, val mY: Int, val mZ: Int)

internal fun add(a: Vector2, b: Vector2): Vector2 {
    return Vector2(a.x + b.x, a.y + b.y)
}

internal fun sub(a: Vector2, b: Vector2): Vector2 {
    return Vector2(a.x - b.x, a.y - b.y)
}

internal fun scale(v: Vector2, s: Float): Vector2 {
    return Vector2(v.x * s, v.y * s)
}

internal fun dot(a: Vector2, b: Vector2): Float {
    return a.x * b.x + a.y * b.y
}

internal fun length(v: Vector2): Float {
    return kotlin.math.sqrt(v.x * v.x + v.y * v.y)
}

internal fun normalize(v: Vector2): Vector2 {
    val len = length(v)
    if (len > 0.0f) {
        return Vector2(v.x / len, v.y / len)
    }
    return Vector2()
}

internal fun perp(v: Vector2): Vector2 {
    return Vector2(-v.y, v.x)
}

internal fun add(a: Vector3, b: Vector3): Vector3 {
    return Vector3(a.x + b.x, a.y + b.y, a.z + b.z)
}

internal fun scale(v: Vector3, s: Float): Vector3 {
    return Vector3(v.x * s, v.y * s, v.z * s)
}

internal fun dot(a: Vector3, b: Vector3): Float {
    return a.x * b.x + a.y * b.y + a.z * b.z
}

internal fun cross(a: Vector3, b: Vector3): Vector3 {
    return Vector3(
        a.y * b.z - a.z * b.y,
        a.z * b.x - a.x * b.z,
        a.x * b.y - a.y * b.x
    )
}

internal fun componentAt(v: Vector3, axis: Axis): Float {
    if (axis == Axis.X) {
        return v.x
    }
    if (axis == Axis.Y) {
        return v.y
    }
    return v.z
}

// Right-hand rotation of v around a unit axis (Rodrigues' formula).
// Matches gml::transform(gml::qrotate(angle, axis), v) from the C++ library.
internal fun rotate(v: Vector3, axis: Vector3, angle: Float): Vector3 {
    val c = kotlin.math.cos(angle)
    val s = kotlin.math.sin(angle)
    val term1 = scale(v, c)
    val term2 = scale(cross(axis, v), s)
    val term3 = scale(axis, dot(axis, v) * (1.0f - c))
    return add(add(term1, term2), term3)
}
