package com.take2.chaichat.net

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChaiApiTest {

    @Test
    fun `encodes turns in order with roles intact`() {
        val body = ChaiApi.encodeRequest(
            listOf(
                ChaiApi.Turn(ChaiApi.Role.SYSTEM, "Be brief."),
                ChaiApi.Turn(ChaiApi.Role.USER, "Hello"),
                ChaiApi.Turn(ChaiApi.Role.ASSISTANT, "Hi there"),
                ChaiApi.Turn(ChaiApi.Role.USER, "How are you?")
            ),
            model = "chai_v1"
        )

        val root = JSONObject(body)
        assertEquals("chai_v1", root.getString("model"))

        val messages: JSONArray = root.getJSONArray("messages")
        assertEquals(4, messages.length())
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertEquals("Be brief.", messages.getJSONObject(0).getString("content"))
        assertEquals("How are you?", messages.getJSONObject(3).getString("content"))
    }

    @Test
    fun `omits optional tuning fields when they are not set`() {
        val root = JSONObject(ChaiApi.encodeRequest(listOf(ChaiApi.Turn("user", "hi"))))

        assertTrue(!root.has("temperature"))
        assertTrue(!root.has("max_tokens"))
    }

    @Test
    fun `includes optional tuning fields when they are set`() {
        val root = JSONObject(
            ChaiApi.encodeRequest(
                listOf(ChaiApi.Turn("user", "hi")),
                temperature = 0.8,
                maxTokens = 256
            )
        )

        assertEquals(0.8, root.getDouble("temperature"), 0.0001)
        assertEquals(256, root.getInt("max_tokens"))
    }

    @Test
    fun `escapes characters that would otherwise break the payload`() {
        val awkward = "She said \"hi\"\nthen left \\ quietly"
        val root = JSONObject(ChaiApi.encodeRequest(listOf(ChaiApi.Turn("user", awkward))))

        assertEquals(awkward, root.getJSONArray("messages").getJSONObject(0).getString("content"))
    }

    @Test
    fun `reads a chat style reply`() {
        val json = """
            {"choices":[{"message":{"role":"assistant","content":"  Hello there  "}}]}
        """.trimIndent()

        assertEquals(ChaiApi.Reply.Text("Hello there"), ChaiApi.parseResponse(json))
    }

    @Test
    fun `reads a completion style reply`() {
        assertEquals(
            ChaiApi.Reply.Text("Hello there"),
            ChaiApi.parseResponse("""{"choices":[{"text":"Hello there"}]}""")
        )
    }

    @Test
    fun `reads a reply delivered at the top level`() {
        assertEquals(
            ChaiApi.Reply.Text("Hello there"),
            ChaiApi.parseResponse("""{"content":"Hello there"}""")
        )
    }

    @Test
    fun `surfaces a structured error`() {
        val reply = ChaiApi.parseResponse("""{"error":{"message":"Invalid API key"}}""")

        assertEquals(ChaiApi.Reply.Refused("Invalid API key"), reply)
    }

    @Test
    fun `surfaces a detail style error`() {
        assertEquals(
            ChaiApi.Reply.Refused("Not authenticated"),
            ChaiApi.parseResponse("""{"detail":"Not authenticated"}""")
        )
    }

    @Test
    fun `hands back the raw body when the shape is unknown`() {
        // The API host was unreachable when this was written, so the response
        // shape is an inference. This is the case that makes a wrong inference
        // visible on screen instead of showing an empty bubble.
        val json = """{"unexpected":{"nested":"value"}}"""

        assertEquals(ChaiApi.Reply.Unrecognised(json), ChaiApi.parseResponse(json))
    }

    @Test
    fun `hands back the raw body when it is not json at all`() {
        val html = "<html><body>502 Bad Gateway</body></html>"

        assertEquals(ChaiApi.Reply.Unrecognised(html), ChaiApi.parseResponse(html))
    }

    @Test
    fun `treats an empty choices array as unrecognised rather than empty text`() {
        val json = """{"choices":[]}"""

        assertEquals(ChaiApi.Reply.Unrecognised(json), ChaiApi.parseResponse(json))
    }
}
