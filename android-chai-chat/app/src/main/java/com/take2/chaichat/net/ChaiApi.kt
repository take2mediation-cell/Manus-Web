package com.take2.chaichat.net

import org.json.JSONArray
import org.json.JSONObject

/**
 * The wire format for Chai's chat API, kept in one file on purpose.
 *
 * The API host was unreachable from the machine this was written on, so the
 * request shape below follows the OpenAI chat completions convention that the
 * endpoint path (/v1/chat/completions) and operation name (createChatCompletion)
 * both point to. It has not been checked against a live response.
 *
 * If a field name turns out to be different, this file is the only thing that
 * needs to change. The parser is deliberately forgiving for the same reason: it
 * tries the shapes a chat completions endpoint plausibly returns, and when none
 * of them match it hands back the raw body so the mismatch is visible instead of
 * showing an empty bubble.
 */
object ChaiApi {

    const val DEFAULT_BASE_URL = "https://api.chai-research.com"
    const val CHAT_PATH = "/v1/chat/completions"

    /** Chai authenticates with its own header rather than a bearer token. */
    const val API_KEY_HEADER = "X-API_KEY"

    const val DEFAULT_MODEL = "chai_v1"

    data class Turn(val role: String, val content: String)

    object Role {
        const val USER = "user"
        const val ASSISTANT = "assistant"
        const val SYSTEM = "system"
    }

    fun encodeRequest(
        turns: List<Turn>,
        model: String = DEFAULT_MODEL,
        temperature: Double? = null,
        maxTokens: Int? = null
    ): String {
        val messages = JSONArray()
        for (turn in turns) {
            messages.put(
                JSONObject()
                    .put("role", turn.role)
                    .put("content", turn.content)
            )
        }

        val body = JSONObject()
            .put("model", model)
            .put("messages", messages)

        temperature?.let { body.put("temperature", it) }
        maxTokens?.let { body.put("max_tokens", it) }

        return body.toString()
    }

    sealed interface Reply {
        data class Text(val content: String) : Reply

        /** The server answered, but said no. */
        data class Refused(val message: String) : Reply

        /**
         * The server answered with something this parser does not recognise.
         * [raw] is carried so the shape can be seen and corrected rather than
         * guessed at a second time.
         */
        data class Unrecognised(val raw: String) : Reply
    }

    fun parseResponse(json: String): Reply {
        val root = try {
            JSONObject(json)
        } catch (error: Exception) {
            return Reply.Unrecognised(json)
        }

        errorMessage(root)?.let { return Reply.Refused(it) }

        contentFromChoices(root)?.let { return Reply.Text(it) }

        // Some deployments return the reply at the top level instead of wrapping
        // it in a choices array.
        for (key in TOP_LEVEL_KEYS) {
            val value = root.optString(key, "")
            if (value.isNotBlank()) return Reply.Text(value.trim())
        }

        return Reply.Unrecognised(json)
    }

    private fun contentFromChoices(root: JSONObject): String? {
        val choices = root.optJSONArray("choices") ?: return null
        if (choices.length() == 0) return null

        val first = choices.optJSONObject(0) ?: return null

        // Chat style: choices[0].message.content
        first.optJSONObject("message")?.optString("content", "")
            ?.takeIf { it.isNotBlank() }
            ?.let { return it.trim() }

        // Streaming delta collapsed into a single response.
        first.optJSONObject("delta")?.optString("content", "")
            ?.takeIf { it.isNotBlank() }
            ?.let { return it.trim() }

        // Completion style: choices[0].text
        first.optString("text", "")
            .takeIf { it.isNotBlank() }
            ?.let { return it.trim() }

        return null
    }

    private fun errorMessage(root: JSONObject): String? {
        root.optJSONObject("error")?.let { error ->
            val message = error.optString("message", "")
            if (message.isNotBlank()) return message
            return error.toString()
        }

        val detail = root.optString("detail", "")
        if (detail.isNotBlank()) return detail

        return null
    }

    private val TOP_LEVEL_KEYS = listOf("content", "text", "message", "output", "response")
}
