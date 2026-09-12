package com.take2.chainarrator.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SummarizerTest {

    private val steps = """
        Step 1. Open the settings screen.
        Step 2. Tap the narrator row.
        Step 3. Turn the switch on.
        Step 4. Return to the chat.
    """.trimIndent()

    private val longProse = "The project depends on stable funding from the province. " +
        "Funding decisions are made each spring by the ministry. " +
        "Without stable funding the project cannot hire staff. " +
        "Staff are needed to deliver the training sessions. " +
        "Training sessions are the core of the project. " +
        "We also rent a hall, which is a smaller cost. " +
        "Weather is unpredictable. " +
        "Volunteers help on weekends. " +
        "The ministry has asked for a funding report in March. " +
        "Stable funding remains the single largest risk to the project."

    @Test
    fun `recognises numbered steps`() {
        // Regression guard. The pattern used to require whitespace immediately
        // after the digit, so the common "Step 1." form was never detected and
        // instructions were summarised despite the verbatim setting.
        assertTrue(Summarizer.looksLikeInstructions(steps))
    }

    @Test
    fun `recognises a list of imperatives`() {
        assertTrue(Summarizer.looksLikeInstructions("Open the app.\nTap the bubble.\nSelect a voice."))
    }

    @Test
    fun `does not mistake prose for instructions`() {
        assertFalse(
            Summarizer.looksLikeInstructions(
                "The weather turned cold last night. I sat by the window thinking about it."
            )
        )
    }

    @Test
    fun `shortens long prose and announces that it did`() {
        val result = Summarizer.condense(longProse, thresholdChars = 300, keepInstructionsVerbatim = true)

        assertTrue(result.wasShortened)
        assertTrue(result.text.startsWith(Summarizer.LEAD_IN))
        assertTrue(result.text.length < longProse.length)
    }

    @Test
    fun `summary only ever repeats sentences from the source`() {
        val result = Summarizer.condense(longProse, thresholdChars = 300, keepInstructionsVerbatim = true)
        val body = result.text.removePrefix(Summarizer.LEAD_IN).trim()

        assertTrue(SpeechShaper.sentences(body).all { longProse.contains(it.trim()) })
    }

    @Test
    fun `leaves instructions alone when asked to`() {
        val doubled = "$steps\n$steps"
        val result = Summarizer.condense(doubled, thresholdChars = 300, keepInstructionsVerbatim = true)

        assertFalse(result.wasShortened)
        assertEquals(doubled, result.text)
    }

    @Test
    fun `shortens instructions when the verbatim option is off`() {
        // Regression guard. The minimum summary budget used to be able to exceed
        // the user's own threshold, which made every passage near the threshold
        // look "not worth shortening" and silently ignored the setting.
        val doubled = "$steps\n$steps"
        val result = Summarizer.condense(doubled, thresholdChars = 50, keepInstructionsVerbatim = false)

        assertTrue(result.wasShortened)
    }

    @Test
    fun `leaves short text untouched`() {
        val short = "Just a quick note."

        assertEquals(short, Summarizer.condense(short, 300, true).text)
    }
}
