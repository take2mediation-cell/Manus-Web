package com.take2.chainarrator.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechShaperTest {

    @Test
    fun `strips roleplay asterisks but keeps the words`() {
        val shaped = SpeechShaper.shape("*She leans back, smiling.* \"You're **early**,\" she says.")

        assertFalse(shaped.contains("*"))
        assertTrue(shaped.contains("She leans back"))
        assertTrue(shaped.contains("early"))
    }

    @Test
    fun `removes emoji`() {
        // Regression guard. The original pattern was built from UTF-16 surrogate
        // ranges, which Java's code point based regex engine never matches, so
        // every emoji survived and was read out as a stray character.
        val shaped = SpeechShaper.shape("Good morning 😊🎉 to you")

        assertFalse(shaped.contains("😊"))
        assertFalse(shaped.contains("🎉"))
        assertTrue(shaped.contains("Good morning"))
    }

    @Test
    fun `emoji only input produces nothing speakable`() {
        assertEquals("", SpeechShaper.shape("🎉🎉🎉"))
    }

    @Test
    fun `keeps link text and does not read out the url`() {
        val shaped = SpeechShaper.shape("See [the docs](https://example.com/a/b) or https://example.com/raw")

        assertTrue(shaped.contains("the docs"))
        assertFalse(shaped.contains("http"))
    }

    @Test
    fun `expands abbreviations and tidies punctuation`() {
        val shaped = SpeechShaper.shape("Bring water, snacks, etc. e.g. a flask & a map!!!")

        assertTrue(shaped.contains("et cetera"))
        assertTrue(shaped.contains("for example"))
        assertTrue(shaped.contains(" and "))
        assertFalse(shaped.contains("!!"))
    }

    @Test
    fun `splits on sentence boundaries`() {
        assertEquals(4, SpeechShaper.sentences("First one. Second one! Third one? Fourth.").size)
    }

    @Test
    fun `breaks up a long run with no terminator`() {
        val chunks = SpeechShaper.sentences("word ".repeat(200).trim())

        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 300 })
    }
}
