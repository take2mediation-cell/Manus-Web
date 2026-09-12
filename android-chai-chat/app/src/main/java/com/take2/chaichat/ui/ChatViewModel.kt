package com.take2.chaichat.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.take2.chaichat.data.ChatMessage
import com.take2.chaichat.data.ConversationStore
import com.take2.chaichat.data.Settings
import com.take2.chaichat.data.TurnBuilder
import com.take2.chaichat.net.ChaiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val store = ConversationStore(application)
    private val settings = Settings.get(application)

    private val _messages = MutableStateFlow(store.load())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _awaitingReply = MutableStateFlow(false)
    val awaitingReply: StateFlow<Boolean> = _awaitingReply.asStateFlow()

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _awaitingReply.value) return

        val outgoing = ChatMessage.user(trimmed)
        update(_messages.value + outgoing)
        deliver(outgoing)
    }

    /** Re-sends a message that failed, rather than making the user retype it. */
    fun retry(message: ChatMessage) {
        if (_awaitingReply.value) return

        val revived = message.copy(status = ChatMessage.Status.SENDING, errorDetail = null)
        update(_messages.value.map { if (it.id == message.id) revived else it })
        deliver(revived)
    }

    private fun deliver(outgoing: ChatMessage) {
        _awaitingReply.value = true

        viewModelScope.launch {
            val client = ChaiClient(baseUrl = settings.baseUrl)

            // The history is built from the thread with this message already in
            // it, so the turn being answered is the last one the model sees.
            val turns = TurnBuilder.build(
                messages = _messages.value,
                systemPrompt = settings.systemPrompt
            )

            when (val outcome = client.send(turns, settings.apiKey, settings.model)) {
                is ChaiClient.Outcome.Ok -> {
                    val delivered = _messages.value.map {
                        if (it.id == outgoing.id) it.copy(status = ChatMessage.Status.SENT) else it
                    }
                    update(delivered + ChatMessage.assistant(outcome.text))
                }

                is ChaiClient.Outcome.Failed -> {
                    update(
                        _messages.value.map {
                            if (it.id == outgoing.id) {
                                it.copy(
                                    status = ChatMessage.Status.FAILED,
                                    errorDetail = listOfNotNull(outcome.message, outcome.detail)
                                        .joinToString("\n\n")
                                )
                            } else {
                                it
                            }
                        }
                    )
                }
            }

            _awaitingReply.value = false
        }
    }

    fun clearConversation() {
        store.clear()
        _messages.value = emptyList()
    }

    private fun update(messages: List<ChatMessage>) {
        _messages.value = messages
        store.save(messages)
    }
}
