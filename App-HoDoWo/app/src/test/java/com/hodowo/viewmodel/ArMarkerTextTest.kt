package com.hodowo.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class ArMarkerTextTest {

    private fun step(title: String, description: String = "Turn the timer knob") =
        ManualStep(number = 2, title = title, description = description)

    @Test
    fun aRealTitleIsShown() {
        assertEquals("Set the timer", step("Set the timer").markerText())
    }

    @Test
    fun aGenericTitleGivesWayToTheDescription() {
        listOf("Step 2", "Passo 2", "2.", "", "  step 12 ")
            .forEach { assertEquals("Turn the timer knob", step(it).markerText()) }
    }
}
