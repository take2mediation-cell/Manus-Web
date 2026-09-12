package com.take2.chaichat.data

import com.take2.chaichat.net.ChaiApi
import org.json.JSONArray
import org.json.JSONObject

/**
 * One line in the conversation.
 *
 * [Status] exists so a message that failed to send still appears in the thread
 * with a way to retry, rather than vanishing and leaving the user unsure whether
 * it was sent.
 */
data class ChatMessage(
    val id: Long,
    val role: String,
    val text: String,
    val timestamp: Long,
    val status: Status = Status.SENT,
    val errorDetail: String? = null
) {
    enum class Status { SENDING, SENT, FAILED }

    val isUser: Boolean get() = role == ChaiApi.Role.USER

    companion object {
        fun user(text: String, id: Long = System.currentTimeMillis()) = ChatMessage(
            id = id,
            role = ChaiApi.Role.USER,
            text = text,
            timestamp = System.currentTimeMillis(),
            status = Status.SENDING
        )

        fun assistant(text: String, id: Long = System.currentTimeMillis()) = ChatMessage(
            id = id,
            role = ChaiApi.Role.ASSISTANT,
            text = text,
            timestamp = System.currentTimeMillis()
        )
    }
}

/**
 * Turning the thread into JSON and back.
 *
 * Kept as free functions over strings so the round trip can be unit tested
 * without a device, since a corrupted save here means a user loses their
 * conversation with nothing to show for it.
 */
object ConversationCodec {

    fun encode(messages: List<ChatMessage>): String {
        val array = JSONArray()
        for (message in messages) {
            // A message still in flight is saved as failed. If the process died
            // mid-request the reply is never coming, and "sending" would hang
            // forever on the next launch.
            val status = if (message.status == ChatMessage.Status.SENDING) {
                ChatMessage.Status.FAILED
            } else {
                message.status
            }

            array.put(
                JSONObject()
                    .put("id", message.id)
                    .put("role", message.role)
                    .put("text", message.text)
                    .put("timestamp", message.timestamp)
                    .put("status", status.name)
                    .apply { message.errorDetail?.let { put("errorDetail", it) } }
            )
        }
        return JSONObject().put("version", VERSION).put("messages", array).toString()
    }

    fun decode(json: String): List<ChatMessage> {
        if (json.isBlank()) return emptyList()

        val array = try {
            JSONObject(json).optJSONArray("messages") ?: return emptyList()
        } catch (error: Exception) {
            // A half written file should cost the user their history, not crash
            // the app on every launch from here on.
            return emptyList()
        }

        val messages = mutableListOf<ChatMessage>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val text = item.optString("text", "")
            val role = item.optString("role", "")
            if (text.isBlank() || role.isBlank()) continue

            messages += ChatMessage(
                id = item.optLong("id", index.toLong()),
                role = role,
                text = text,
                timestamp = item.optLong("timestamp", 0L),
                status = statusOf(item.optString("status", "")),
                errorDetail = item.optString("errorDetail", "").takeIf { it.isNotBlank() }
            )
        }
        return messages
    }

    private fun statusOf(name: String): ChatMessage.Status = when (name) {
        ChatMessage.Status.FAILED.name -> ChatMessage.Status.FAILED
        ChatMessage.Status.SENDING.name -> ChatMessage.Status.FAILED
        else -> ChatMessage.Status.SENT
    }

    private const val VERSION = 1
}
