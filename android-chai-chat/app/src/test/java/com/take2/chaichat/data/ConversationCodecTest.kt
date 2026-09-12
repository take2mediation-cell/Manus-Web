package com.take2.chaichat.data

import com.take2.chaichat.net.ChaiApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationCodecTest {

    private val thread = listOf(
        ChatMessage(1, ChaiApi.Role.USER, "Hello", 1000L, ChatMessage.Status.SENT),
        ChatMessage(2, ChaiApi.Role.ASSISTANT, "Hi, how are you?", 2000L, ChatMessage.Status.SENT)
    )

    @Test
    fun `round trips a conversation`() {
        assertEquals(thread, ConversationCodec.decode(ConversationCodec.encode(thread)))
    }

    @Test
    fun `round trips text that would break a naive format`() {
        val awkward = listOf(
            ChatMessage(1, ChaiApi.Role.USER, "line one\nline \"two\"\ttabbed \\ slash", 1L)
        )

        assertEquals(awkward, ConversationCodec.decode(ConversationCodec.encode(awkward)))
    }

    @Test
    fun `round trips emoji and non latin text`() {
        val mixed = listOf(
            ChatMessage(1, ChaiApi.Role.USER, "Bonjour 🌙 مرحبا こんにちは", 1L)
        )

        assertEquals(mixed, ConversationCodec.decode(ConversationCodec.encode(mixed)))
    }

    @Test
    fun `a message still in flight is restored as failed`() {
        // Otherwise a process death mid-request leaves a message stuck showing
        // as sending forever, with no way to retry it.
        val inFlight = listOf(ChatMessage(1, ChaiApi.Role.USER, "Did this send?", 1L, ChatMessage.Status.SENDING))

        val restored = ConversationCodec.decode(ConversationCodec.encode(inFlight))

        assertEquals(ChatMessage.Status.FAILED, restored.single().status)
    }

    @Test
    fun `keeps the error detail on a failed message`() {
        val failed = listOf(
            ChatMessage(1, ChaiApi.Role.USER, "Hello", 1L, ChatMessage.Status.FAILED, "HTTP 401")
        )

        assertEquals("HTTP 401", ConversationCodec.decode(ConversationCodec.encode(failed)).single().errorDetail)
    }

    @Test
    fun `a truncated file loses the history instead of crashing`() {
        assertTrue(ConversationCodec.decode("""{"version":1,"messa""").isEmpty())
    }

    @Test
    fun `empty input decodes to an empty thread`() {
        assertTrue(ConversationCodec.decode("").isEmpty())
    }

    @Test
    fun `skips entries with no usable content`() {
        val json = """{"version":1,"messages":[{"id":1,"role":"user","text":""},{"id":2,"role":"user","text":"real"}]}"""

        assertEquals(listOf("real"), ConversationCodec.decode(json).map { it.text })
    }
}
