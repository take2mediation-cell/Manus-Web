package com.take2.chainarrator.text

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpokenHistoryTest {

    @Test
    fun `reads new text once and never again`() {
        val history = SpokenHistory()
        val message = "Hello there, how have you been?"

        assertTrue(history.evaluate(message) is SpokenHistory.Decision.Speak)
        assertTrue(history.evaluate(message) is SpokenHistory.Decision.Skip)
    }

    @Test
    fun `treats a re-laid-out message as already read`() {
        val history = SpokenHistory()
        history.evaluate("Hello there, how have you been?")

        assertTrue(
            history.evaluate("Hello  there,   how have you been?") is SpokenHistory.Decision.Skip
        )
    }

    @Test
    fun `ignores timestamps and badge counts`() {
        assertTrue(SpokenHistory().evaluate("12:04") is SpokenHistory.Decision.Skip)
    }

    @Test
    fun `reads only the new tail of a message that is still arriving`() {
        val history = SpokenHistory()
        history.evaluate("I have been thinking about")

        val decision = history.evaluate("I have been thinking about what you said yesterday.")

        assertTrue(decision is SpokenHistory.Decision.Continue)
        decision as SpokenHistory.Decision.Continue
        assertFalse(decision.addition.contains("thinking about"))
        assertTrue(decision.addition.contains("what you said"))
    }

    @Test
    fun `forgets the oldest entry once it is full`() {
        val history = SpokenHistory(capacity = 3)
        repeat(5) { history.evaluate("message number $it goes here") }

        assertTrue(history.evaluate("message number 0 goes here") is SpokenHistory.Decision.Speak)
    }

    @Test
    fun `reset clears everything`() {
        val history = SpokenHistory()
        val message = "Something memorable happened."
        history.evaluate(message)
        history.reset()

        assertTrue(history.evaluate(message) is SpokenHistory.Decision.Speak)
    }
}
