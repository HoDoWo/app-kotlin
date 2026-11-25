package com.hodowo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Frame time of the AR view (#241). */
class FrameTimeMeterTest {

    private val msNs = 1_000_000L

    @Test
    fun noStatsBeforeTwoFrames() {
        val meter = FrameTimeMeter()
        assertNull(meter.stats())
        meter.onFrame(0)
        assertNull(meter.stats())
    }

    @Test
    fun averageWorstAndFps() {
        val meter = FrameTimeMeter()
        var now = 0L
        meter.onFrame(now)
        repeat(3) {
            now += 16 * msNs
            meter.onFrame(now)
        }
        now += 52 * msNs
        meter.onFrame(now)

        val stats = meter.stats()!!
        assertEquals(25f, stats.averageMs, 0.01f)
        assertEquals(52f, stats.worstMs, 0.01f)
        assertEquals(40, stats.fps)
    }

    @Test
    fun onlyTheLastWindowCounts() {
        val meter = FrameTimeMeter(window = 2)
        listOf(0L, 100L, 110L, 120L).forEach { meter.onFrame(it * msNs) }
        assertEquals(10f, meter.stats()!!.worstMs, 0.01f)
    }

    @Test
    fun aClockGoingBackIsIgnoredAndResetStartsOver() {
        val meter = FrameTimeMeter()
        meter.onFrame(100 * msNs)
        meter.onFrame(50 * msNs)
        assertNull(meter.stats())
        meter.onFrame(66 * msNs)
        assertEquals(16f, meter.stats()!!.averageMs, 0.01f)
        meter.reset()
        assertNull(meter.stats())
    }
}
