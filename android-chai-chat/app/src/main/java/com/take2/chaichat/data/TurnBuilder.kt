package com.take2.chaichat.data

import com.take2.chaichat.net.ChaiApi

/**
 * Turns the on-screen thread into the turns sent to the API.
 *
 * Three rules, and each one is a bug if it is missed. Failed messages are left
 * out, or a message that never reached Chai would be presented as though it had.
 * The history is capped, or a long conversation grows the request until it is
 * rejected. And the system prompt always leads, because a persona placed
 * anywhere else stops steering the reply.
 */
object TurnBuilder {

    /** Roughly a dozen exchanges. Enough to hold context, bounded enough to send. */
    const val DEFAULT_HISTORY_LIMIT = 24

    fun build(
        messages: List<ChatMessage>,
        systemPrompt: String = "",
        historyLimit: Int = DEFAULT_HISTORY_LIMIT
    ): List<ChaiApi.Turn> {
        val usable = messages
            .filter { it.status != ChatMessage.Status.FAILED }
            .filter { it.text.isNotBlank() }
            .takeLast(historyLimit)

        val turns = mutableListOf<ChaiApi.Turn>()

        if (systemPrompt.isNotBlank()) {
            turns += ChaiApi.Turn(ChaiApi.Role.SYSTEM, systemPrompt.trim())
        }

        for (message in usable) {
            turns += ChaiApi.Turn(message.role, message.text)
        }

        return turns
    }
}
