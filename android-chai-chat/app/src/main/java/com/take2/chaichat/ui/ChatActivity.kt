package com.take2.chaichat.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.take2.chaichat.R
import com.take2.chaichat.databinding.ActivityChatBinding
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private val viewModel: ChatViewModel by viewModels()

    private lateinit var adapter: MessageAdapter

    private val settings = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val cleared = result.data
            ?.getBooleanExtra(SettingsActivity.EXTRA_CLEARED, false) == true
        if (result.resultCode == RESULT_OK && cleared) {
            viewModel.clearConversation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.app_name)

        adapter = MessageAdapter(onRetry = viewModel::retry)

        binding.messages.layoutManager = LinearLayoutManager(this).apply {
            // New messages arrive at the bottom, which is where the user is
            // looking, so the list grows from there.
            stackFromEnd = true
        }
        binding.messages.adapter = adapter

        binding.send.setOnClickListener { submit() }

        observe()
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.messages.collect { messages ->
                        adapter.submitList(messages) {
                            if (messages.isNotEmpty()) {
                                binding.messages.scrollToPosition(messages.size - 1)
                            }
                        }
                        binding.emptyState.visibility =
                            if (messages.isEmpty()) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    viewModel.awaitingReply.collect { waiting ->
                        binding.thinking.visibility = if (waiting) View.VISIBLE else View.GONE
                        binding.send.isEnabled = !waiting
                    }
                }
            }
        }
    }

    private fun submit() {
        val text = binding.input.text?.toString().orEmpty()
        if (text.isBlank()) return

        viewModel.send(text)
        binding.input.setText("")
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_SETTINGS, 0, R.string.action_settings)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == MENU_SETTINGS) {
            settings.launch(Intent(this, SettingsActivity::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private companion object {
        const val MENU_SETTINGS = 1
    }
}
