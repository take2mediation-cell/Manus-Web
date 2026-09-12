package com.take2mediation.chaivoiceagent

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.accessibility.AccessibilityManager
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.take2mediation.chaivoiceagent.databinding.ActivityMainBinding
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var previewTts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnOpenAccessibilitySettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.switchSpeakEnabled.isChecked = Prefs.isSpeakEnabled(this)
        binding.switchSpeakEnabled.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setSpeakEnabled(this, isChecked)
        }

        binding.seekRate.progress = (Prefs.getRate(this) * 100).toInt()
        binding.seekPitch.progress = (Prefs.getPitch(this) * 100).toInt()

        binding.seekRate.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) Prefs.setRate(this@MainActivity, (progress.coerceAtLeast(30)) / 100f)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.seekPitch.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) Prefs.setPitch(this@MainActivity, (progress.coerceAtLeast(30)) / 100f)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.editTargetPackages.setText(Prefs.getTargetPackages(this).joinToString(","))
        binding.btnSaveSettings.setOnClickListener {
            Prefs.setTargetPackages(this, binding.editTargetPackages.text.toString())
        }

        binding.btnTestSpeak.setOnClickListener { playTestSpeech() }
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatusLabel()
    }

    private fun updateServiceStatusLabel() {
        binding.serviceStatus.text = if (isAccessibilityServiceEnabled()) {
            getString(R.string.status_service_on)
        } else {
            getString(R.string.status_service_off)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfoCompatMask
        )
        return enabledServices.any { it.resolveInfo.serviceInfo.packageName == packageName }
    }

    private fun playTestSpeech() {
        if (previewTts == null) {
            previewTts = TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    previewTts?.language = Locale.getDefault()
                    previewTts?.setSpeechRate(Prefs.getRate(this))
                    previewTts?.setPitch(Prefs.getPitch(this))
                    previewTts?.speak(getString(R.string.test_speak_text), TextToSpeech.QUEUE_FLUSH, null, "preview")
                }
            }
        } else {
            previewTts?.speak(getString(R.string.test_speak_text), TextToSpeech.QUEUE_FLUSH, null, "preview")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        previewTts?.stop()
        previewTts?.shutdown()
    }
}

private const val AccessibilityServiceInfoCompatMask = android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
