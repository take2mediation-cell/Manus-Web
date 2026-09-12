package com.take2.chaichat.ui

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.take2.chaichat.R
import com.take2.chaichat.data.ChatMessage
import com.take2.chaichat.databinding.ItemMessageBinding

/**
 * Renders the thread.
 *
 * One layout for both sides, with the row's gravity and the bubble's background
 * swapped per message. Two near-identical layouts would drift apart the first
 * time one of them was edited.
 */
class MessageAdapter(
    private val onRetry: (ChatMessage) -> Unit
) : ListAdapter<ChatMessage, MessageAdapter.Holder>(DIFF) {

    class Holder(val binding: ItemMessageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val message = getItem(position)
        val binding = holder.binding
        val context = binding.root.context

        binding.bubble.text = message.text
        binding.messageRow.gravity = if (message.isUser) Gravity.END else Gravity.START

        val failed = message.status == ChatMessage.Status.FAILED

        binding.bubble.setBackgroundResource(
            when {
                failed -> R.drawable.bubble_failed
                message.isUser -> R.drawable.bubble_user
                else -> R.drawable.bubble_bot
            }
        )

        binding.bubble.setTextColor(
            context.getColor(
                when {
                    failed -> R.color.ink
                    message.isUser -> R.color.bubble_user_text
                    else -> R.color.bubble_bot_text
                }
            )
        )

        // A sending message is dimmed rather than replaced, so the text the user
        // typed stays visible while it is in flight.
        binding.bubble.alpha = if (message.status == ChatMessage.Status.SENDING) 0.55f else 1f

        if (failed) {
            binding.errorLine.visibility = View.VISIBLE
            binding.errorLine.text = message.errorDetail
                ?: context.getString(R.string.failed_prefix)
            binding.retry.visibility = View.VISIBLE
            binding.retry.setOnClickListener { onRetry(message) }
        } else {
            binding.errorLine.visibility = View.GONE
            binding.retry.visibility = View.GONE
            binding.retry.setOnClickListener(null)
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<ChatMessage>() {
            override fun areItemsTheSame(old: ChatMessage, new: ChatMessage) = old.id == new.id

            override fun areContentsTheSame(old: ChatMessage, new: ChatMessage) = old == new
        }
    }
}
