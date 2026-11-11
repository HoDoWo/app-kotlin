package com.hodowo.data

import com.hodowo.viewmodel.ManualGuide
import com.hodowo.viewmodel.ManualStep
import com.hodowo.viewmodel.StepPin
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The pin of a manual step: where it happens on the guide image (review 10-11). */
class StepPinTest {

    @Test
    fun aPinIsReadOnlyInsideTheImage() {
        assertEquals(StepPin(0.25f, 0.5f), ManualJson.parsePin(JSONObject("""{"x": 0.25, "y": 0.5}""")))
        assertNull(ManualJson.parsePin(JSONObject("""{"x": 1.5, "y": 0.5}""")))
        assertNull(ManualJson.parsePin(JSONObject("""{"x": 0.5}""")))
        assertNull(ManualJson.parsePin(null))
    }

    @Test
    fun pinsSurviveTheSavedCopyOfAGuide() {
        val guide = ManualGuide(
            applianceName = "Oven",
            issueTitle = "Set 180 °C",
            overview = "Use the right knob.",
            steps = listOf(
                ManualStep(1, "Turn the knob", "", StepPin(0.8f, 0.2f)),
                ManualStep(2, "Wait", "")
            )
        )

        val back = ManualJson.guideFromJson(JSONObject(ManualJson.guideToJson(guide).toString()))

        assertEquals(guide.steps, back?.steps)
    }

    @Test
    fun pinsSurviveFirestore() {
        val pin = StepPin(0.5f, 0.25f)

        assertEquals(pin, ManualJson.pinFromMap(ManualJson.pinToMap(pin)))
        assertNull(ManualJson.pinToMap(null))
        assertNull(ManualJson.pinFromMap(mapOf("x" to 2.0, "y" to 0.5)))
        assertNull(ManualJson.pinFromMap(mapOf("x" to 0.5)))
        assertNull(ManualJson.pinFromMap("not a map"))
    }
}
