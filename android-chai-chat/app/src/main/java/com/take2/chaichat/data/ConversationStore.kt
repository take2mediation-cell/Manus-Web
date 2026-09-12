package com.take2.chaichat.data

import android.content.Context
import java.io.File

/**
 * Keeps the thread on disk so the conversation survives the app being closed.
 *
 * One file, rewritten whole on each change. The thread is capped at
 * [MAX_MESSAGES], which keeps both the file and the request payload bounded
 * without the user ever having to think about it.
 */
class ConversationStore(context: Context) {

    private val file = File(context.filesDir, "conversation.json")

    fun load(): List<ChatMessage> = try {
        if (file.exists()) ConversationCodec.decode(file.readText()) else emptyList()
    } catch (error: Exception) {
        emptyList()
    }

    fun save(messages: List<ChatMessage>) {
        try {
            file.writeText(ConversationCodec.encode(messages.takeLast(MAX_MESSAGES)))
        } catch (error: Exception) {
            // Losing a save is survivable; crashing the send that triggered it
            // is not.
        }
    }

    fun clear() {
        try {
            file.delete()
        } catch (error: Exception) {
            // Nothing useful to do; the next save overwrites it anyway.
        }
    }

    companion object {
        const val MAX_MESSAGES = 300
    }
}
