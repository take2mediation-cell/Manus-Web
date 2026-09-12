package com.take2.chainarrator.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.take2.chainarrator.NarratorApp
import com.take2.chainarrator.R
import com.take2.chainarrator.data.Settings
import com.take2.chainarrator.databinding.ActivityListBinding
import com.take2.chainarrator.databinding.ItemVoiceBinding
import com.take2.chainarrator.tts.NarratorEngine
import com.take2.chainarrator.tts.VoiceCatalog

/**
 * Voice selection, ordered best-guess first.
 *
 * Android has no notion of a voice's age, and its gender labelling is a naming
 * convention rather than an API, so this screen does not claim to find "a forty
 * year old woman". It ranks the candidates most likely to read that way and puts
 * a Test button next to every one, because the only real test is hearing it.
 */
class VoicePickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityListBinding
    private lateinit var settings: Settings
    private lateinit var narrator: NarratorEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        settings = Settings.get(this)
        narrator = (application as NarratorApp).narrator

        binding.listHint.text =
            "Ranked with the voices most likely to sound calm, female and mid-range first. " +
            "Tap Test to hear one, tap the row to keep it. Pace and pitch on the main screen " +
            "do more for how old a voice sounds than the voice choice itself."

        binding.list.layoutManager = LinearLayoutManager(this)

        narrator.whenReady { showVoices() }
    }

    private fun showVoices() {
        val options = narrator.voiceOptions()

        if (options.size <= 1) {
            binding.listHint.text = getString(R.string.no_tts_engine)
        }

        binding.list.adapter = VoiceAdapter(options)
    }

    override fun onPause() {
        super.onPause()
        // Auditions switch the live engine voice. Put it back to the saved one.
        narrator.applyVoiceSettings()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private inner class VoiceAdapter(
        private val items: List<VoiceCatalog.Option>
    ) : RecyclerView.Adapter<VoiceAdapter.Holder>() {

        inner class Holder(val binding: ItemVoiceBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(ItemVoiceBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val option = items[position]

            holder.binding.voiceTitle.text = option.displayName
            holder.binding.voiceSubtitle.text = if (option.requiresNetwork) {
                getString(R.string.voice_network_warning)
            } else {
                option.subtitle
            }

            val isSelected = option.voiceName == settings.voiceName
            holder.binding.voiceSelected.visibility = if (isSelected) View.VISIBLE else View.GONE

            holder.binding.voiceTest.setOnClickListener {
                narrator.preview(option.voiceName, getString(R.string.test_utterance))
            }

            holder.binding.root.setOnClickListener {
                settings.voiceName = option.voiceName
                narrator.applyVoiceSettings()
                notifyDataSetChanged()
                finish()
            }
        }
    }
}
