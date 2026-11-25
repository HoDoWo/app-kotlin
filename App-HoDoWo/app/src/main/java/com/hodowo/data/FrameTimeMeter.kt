package com.hodowo.data

import kotlin.math.roundToInt

/** Frame time of the AR view, for the performance check of #241. */
data class FrameStats(val averageMs: Float, val worstMs: Float, val fps: Int)

/**
 * Average and worst time between frames over the last [window] frames.
 * The AR view calls [onFrame] once per rendered frame with a monotonic clock.
 */
class FrameTimeMeter(private val window: Int = DEFAULT_WINDOW) {

    private val intervalsNs = ArrayDeque<Long>()
    private var lastNs: Long? = null

    fun onFrame(nowNs: Long) {
        val previous = lastNs
        lastNs = nowNs
        if (previous == null || nowNs <= previous) return
        intervalsNs.addLast(nowNs - previous)
        if (intervalsNs.size > window) intervalsNs.removeFirst()
    }

    /** Null until two frames were seen. */
    fun stats(): FrameStats? {
        if (intervalsNs.isEmpty()) return null
        val averageMs = intervalsNs.average().toFloat() / NS_PER_MS
        val worstMs = intervalsNs.max().toFloat() / NS_PER_MS
        return FrameStats(averageMs, worstMs, (MS_PER_S / averageMs).roundToInt())
    }

    fun reset() {
        intervalsNs.clear()
        lastNs = null
    }

    companion object {
        /** About two seconds at 30 fps. */
        const val DEFAULT_WINDOW = 60
        private const val NS_PER_MS = 1_000_000f
        private const val MS_PER_S = 1000f
    }
}
