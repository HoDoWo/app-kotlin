package com.hodowo.ui.components

import android.content.Context
import android.speech.SpeechRecognizer
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpeechToTextHelperTest {

    private val context: Context = mockk(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic(SpeechRecognizer::class)
        every { SpeechRecognizer.isRecognitionAvailable(any()) } returns true
    }

    @Test
    fun isAvailableReturnsTrueWhenSpeechRecognizerIsAvailable() {
        var partialResult: String? = null
        var finalResult: String? = null

        val helper = SpeechToTextHelper(
            context = context,
            onPartialResult = { partialResult = it },
            onFinalResult = { finalResult = it }
        )

        assertTrue(helper.isAvailable())
        assertFalse(helper.isListening.value)
    }

    @Test
    fun isAvailableReturnsFalseWhenRecognitionIsNotAvailable() {
        every { SpeechRecognizer.isRecognitionAvailable(any()) } returns false

        val helper = SpeechToTextHelper(
            context = context,
            onPartialResult = {},
            onFinalResult = {}
        )

        assertFalse(helper.isAvailable())
    }

    @Test
    fun startListeningWhenUnavailableReportsError() {
        every { SpeechRecognizer.isRecognitionAvailable(any()) } returns false
        var errorMessage: String? = null

        val helper = SpeechToTextHelper(
            context = context,
            onPartialResult = {},
            onFinalResult = {},
            onError = { errorMessage = it }
        )

        helper.startListening()

        assertEquals("Speech recognition is not available on this device.", errorMessage)
        assertFalse(helper.isListening.value)
    }

    @Test
    fun stopListeningResetsListeningState() = runTest {
        val helper = SpeechToTextHelper(
            context = context,
            onPartialResult = {},
            onFinalResult = {}
        )

        helper.stopListening()
        assertFalse(helper.isListening.value)

        helper.destroy()
        assertFalse(helper.isListening.value)
    }
}
