package com.take2.chaichat.data

import com.take2.chaichat.net.ChaiApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnBuilderTest {

    private fun user(text: String, status: ChatMessage.Status = ChatMessage.Status.SENT) =
        ChatMessage(text.hashCode().toLong(), ChaiApi.Role.USER, text, 0L, status)

    private fun bot(text: String) =
        ChatMessage(text.hashCode().toLong(), ChaiApi.Role.ASSISTANT, text, 0L)

    @Test
    fun `keeps the conversation in order`() {
        val turns = TurnBuilder.build(listOf(user("one"), bot("two"), user("three")))

        assertEquals(listOf("one", "two", "three"), turns.map { it.content })
        assertEquals(listOf("user", "assistant", "user"), turns.map { it.role })
    }

    @Test
    fun `leaves out messages that never reached chai`() {
        // Sending a failed message would present it to the model as though it had
        // been delivered, and the reply would answer something the user never
        // actually sent.
        val turns = TurnBuilder.build(
            listOf(user("delivered"), user("never sent", ChatMessage.Status.FAILED), user("latest"))
        )

        assertEquals(listOf("delivered", "latest"), turns.map { it.content })
    }

    @Test
    fun `puts the system prompt first`() {
        val turns = TurnBuilder.build(listOf(user("hello")), systemPrompt = "Be warm and brief.")

        assertEquals(ChaiApi.Role.SYSTEM, turns.first().role)
        assertEquals("Be warm and brief.", turns.first().content)
        assertEquals(2, turns.size)
    }

    @Test
    fun `omits a blank system prompt entirely`() {
        val turns = TurnBuilder.build(listOf(user("hello")), systemPrompt = "   ")

        assertEquals(1, turns.size)
        assertEquals(ChaiApi.Role.USER, turns.single().role)
    }

    @Test
    fun `caps the history and keeps the most recent turns`() {
        val long = (1..40).map { user("message $it") }

        val turns = TurnBuilder.build(long, historyLimit = 10)

        assertEquals(10, turns.size)
        assertEquals("message 31", turns.first().content)
        assertEquals("message 40", turns.last().content)
    }

    @Test
    fun `the cap does not push out the system prompt`() {
        val long = (1..40).map { user("message $it") }

        val turns = TurnBuilder.build(long, systemPrompt = "Stay in character.", historyLimit = 5)

        assertEquals(ChaiApi.Role.SYSTEM, turns.first().role)
        assertEquals(6, turns.size)
    }

    @Test
    fun `an empty thread with no system prompt sends nothing`() {
        assertTrue(TurnBuilder.build(emptyList()).isEmpty())
    }
}
