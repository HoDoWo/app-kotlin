package com.hodowo.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guide pins on the real surface (#241): plan, mapping and grouping. */
class ArPinAlignmentTest {

    private val eps = 1e-4f

    private fun step(number: Int, pin: StepPin?) = ManualStep(number, "Step $number", "", pin)

    private fun assertVec(expected: Vec3, actual: Vec3) {
        assertEquals(expected.x, actual.x, eps)
        assertEquals(expected.y, actual.y, eps)
        assertEquals(expected.z, actual.z, eps)
    }

    @Test
    fun noPinsIsFree() {
        assertEquals(AlignPlan.Free, AlignPlan.of(listOf(step(1, null), step(2, null))))
        assertEquals(AlignPlan.Free, AlignPlan.of(emptyList()))
    }

    @Test
    fun pinsOnOneControlNeedOnePoint() {
        val steps = listOf(step(1, null), step(2, StepPin(0.5f, 0.5f)), step(3, StepPin(0.505f, 0.5f)))
        assertEquals(AlignPlan.OnePoint(1), AlignPlan.of(steps))
    }

    @Test
    fun twoPointsUseTheFirstPinAndTheFarthestOne() {
        val steps = listOf(
            step(1, StepPin(0.2f, 0.3f)),
            step(2, StepPin(0.4f, 0.3f)),
            step(3, null),
            step(4, StepPin(0.8f, 0.9f))
        )
        assertEquals(AlignPlan.TwoPoints(0, 3), AlignPlan.of(steps))
    }

    @Test
    fun alignedPinsKeepTheirPhotoLayoutAtRealScale() {
        // The two controls are 0.6 photo widths apart and 36 cm apart on the appliance.
        val mapper = PinMapper.align(
            first = StepPin(0.2f, 0.3f),
            second = StepPin(0.8f, 0.3f),
            secondLocal = Vec3(0.36f, 0f, 0f),
            cameraLocal = Vec3(0f, 0.5f, 0.2f),
            aspect = 4f / 3f
        )
        assertNotNull(mapper)
        mapper!!
        assertEquals(0.6f, mapper.metersPerPhotoWidth, eps)
        assertVec(Vec3(0f, 0f, 0f), mapper.localPosition(StepPin(0.2f, 0.3f)))
        assertVec(Vec3(0.36f, 0f, 0f), mapper.localPosition(StepPin(0.8f, 0.3f)))
        // 0.3 widths right and 0.3 heights (= 0.4 widths) down: 18 cm right, 24 cm down.
        assertVec(Vec3(0.18f, 0f, 0.24f), mapper.localPosition(StepPin(0.5f, 0.6f)))
    }

    @Test
    fun liftGoesTowardsTheCamera() {
        val mapper = PinMapper.align(
            StepPin(0.2f, 0.3f), StepPin(0.8f, 0.3f), Vec3(0.36f, 0f, 0f), Vec3(0f, 0.5f, 0f)
        )!!
        assertEquals(0.04f, mapper.localPosition(StepPin(0.2f, 0.3f), lift = 0.04f).y, eps)
        assertEquals(1f, mapper.side, eps)
    }

    @Test
    fun cameraOnTheOtherSideMirrorsDownAndLift() {
        val mapper = PinMapper.align(
            StepPin(0.2f, 0.3f), StepPin(0.8f, 0.3f), Vec3(0.36f, 0f, 0f), Vec3(0f, -0.5f, 0f), aspect = 4f / 3f
        )!!
        assertEquals(-1f, mapper.side, eps)
        assertVec(Vec3(0.18f, -0.04f, -0.24f), mapper.localPosition(StepPin(0.5f, 0.6f), lift = 0.04f))
    }

    @Test
    fun aTurnedPhotoIsTurnedOnTheSurface() {
        // Photo "right" lies along the surface "down" axis: a 90 degree turn.
        val mapper = PinMapper.align(
            StepPin(0.2f, 0.3f), StepPin(0.8f, 0.3f), Vec3(0f, 0f, 0.36f), Vec3(0f, 0.5f, 0f), aspect = 4f / 3f
        )!!
        assertVec(Vec3(0f, 0f, 0.36f), mapper.localPosition(StepPin(0.8f, 0.3f)))
        assertVec(Vec3(-0.24f, 0f, 0.18f), mapper.localPosition(StepPin(0.5f, 0.6f)))
    }

    @Test
    fun pointsThatCannotBeTheTwoControlsAreRejected() {
        val first = StepPin(0.2f, 0.3f)
        val second = StepPin(0.8f, 0.3f)
        val camera = Vec3(0f, 0.5f, 0f)
        // Too close in the photo.
        assertNull(PinMapper.align(first, StepPin(0.25f, 0.3f), Vec3(0.03f, 0f, 0f), camera))
        // 1 mm apart: a panel 1.7 mm wide.
        assertNull(PinMapper.align(first, second, Vec3(0.001f, 0f, 0f), camera))
        // 6 m apart: a panel 10 m wide.
        assertNull(PinMapper.align(first, second, Vec3(6f, 0f, 0f), camera))
        // On another surface, 30 cm in front of the first point.
        assertNull(PinMapper.align(first, second, Vec3(0.36f, 0.3f, 0f), camera))
    }

    @Test
    fun singlePointPutsEveryPinOnTheAnchor() {
        val mapper = PinMapper.single(StepPin(0.5f, 0.5f), Vec3(0f, 1f, 0f))
        assertVec(Vec3(0f, 0f, 0f), mapper.localPosition(StepPin(0.51f, 0.5f)))
    }

    @Test
    fun stepsOnTheSameControlShareOneMarker() {
        val steps = listOf(
            step(1, StepPin(0.5f, 0.5f)),
            step(2, null),
            step(3, StepPin(0.2f, 0.8f)),
            step(4, StepPin(0.505f, 0.5f))
        )
        val groups = pinGroups(steps)
        assertEquals(2, groups.size)
        assertEquals(listOf(0, 3), groups[0].stepIndices)
        assertEquals("1, 4", groups[0].label(steps))
        assertEquals("3", groups[1].label(steps))
        assertTrue(samePoint(StepPin(0.5f, 0.5f), StepPin(0.505f, 0.5f)))
    }
}
