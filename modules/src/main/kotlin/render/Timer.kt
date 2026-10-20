package render

// Minimal port of tire Timer (render/timer.h).
class Timer {
    private var mLastTimePoint: Long = System.nanoTime()
    private var mFrameDurationNanos: Long = 0

    // Called every frame.
    fun update() {
        val now = System.nanoTime()
        mFrameDurationNanos = now - mLastTimePoint
        mLastTimePoint = now
    }

    // Last frame duration in seconds.
    fun floatFrameDuration(): Float {
        return mFrameDurationNanos.toFloat() * 1.0e-9f
    }
}
