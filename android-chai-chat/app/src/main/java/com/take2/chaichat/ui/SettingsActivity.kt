package com.take2.chaichat.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import com.take2.chaichat.R
import com.take2.chaichat.data.Settings
import com.take2.chaichat.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settings: Settings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        settings = Settings.get(this)

        binding.apiKey.setText(settings.apiKey)
        binding.model.setText(settings.model)
        binding.baseUrl.setText(settings.baseUrl)
        binding.systemPrompt.setText(settings.systemPrompt)

        binding.save.setOnClickListener { save() }
        binding.clear.setOnClickListener { confirmClear() }
    }

    private fun save() {
        settings.apiKey = binding.apiKey.text?.toString().orEmpty()
        settings.model = binding.model.text?.toString().orEmpty()
        settings.baseUrl = binding.baseUrl.text?.toString().orEmpty()
        settings.systemPrompt = binding.systemPrompt.text?.toString().orEmpty()

        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_clear_title)
            .setMessage(R.string.confirm_clear_body)
            .setNegativeButton(R.string.confirm_clear_no, null)
            .setPositiveButton(R.string.confirm_clear_yes) { _, _ ->
                // Deleting the file from here would not stick: the chat screen
                // still holds the thread in memory and would save it straight
                // back on return. So the clear is reported to the chat screen and
                // its view model, which owns the conversation, performs it.
                setResult(RESULT_OK, Intent().putExtra(EXTRA_CLEARED, true))
                finish()
            }
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        const val EXTRA_CLEARED = "conversation_cleared"
    }
}
