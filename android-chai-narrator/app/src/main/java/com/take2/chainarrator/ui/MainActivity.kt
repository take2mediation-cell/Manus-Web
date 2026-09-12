package com.take2.chainarrator.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.take2.chainarrator.NarratorApp
import com.take2.chainarrator.R
import com.take2.chainarrator.data.Settings
import com.take2.chainarrator.databinding.ActivityMainBinding
import com.take2.chainarrator.service.BubbleService
import com.take2.chainarrator.service.ChaiAccessibilityService
import kotlin.math.roundToInt

/**
 * Setup and delivery controls.
 *
 * The screen is written as a checklist on purpose. Three separate system
 * permissions have to line up before a single word is spoken, and if any one of
 * them is missing the app looks broken rather than unconfigured. Each card says
 * what is still needed and opens the exact settings page that fixes it.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: Settings

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            toast("Without notification permission the bubble cannot stay running in the background.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settings = Settings.get(this)
        (application as NarratorApp).narrator.initialise()

        bindSetupButtons()
        bindDeliveryControls()
        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        refresh()
        (application as NarratorApp).narrator.applyVoiceSettings()
    }

    private fun bindSetupButtons() {
        binding.overlayButton.setOnClickListener {
            startActivity(
                Intent(
                    AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }

        binding.accessibilityButton.setOnClickListener {
            startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))
            toast("Find Chai Narrator in the list and switch it on.")
        }

        binding.targetButton.setOnClickListener {
            startActivity(Intent(this, AppPickerActivity::class.java))
        }

        binding.voiceButton.setOnClickListener {
            startActivity(Intent(this, VoicePickerActivity::class.java))
        }

        binding.bubbleToggle.setOnClickListener { toggleBubble() }
    }

    private fun bindDeliveryControls() {
        binding.rateSeek.progress = toProgress(
            settings.speechRate, Settings.MIN_RATE, Settings.MAX_RATE
        )
        binding.pitchSeek.progress = toProgress(
            settings.pitch, Settings.MIN_PITCH, Settings.MAX_PITCH
        )
        binding.thresholdSeek.progress = toProgress(
            settings.summariseThreshold.toFloat(),
            Settings.MIN_THRESHOLD.toFloat(),
            Settings.MAX_THRESHOLD.toFloat()
        )

        binding.rateSeek.onProgress { progress ->
            settings.speechRate = fromProgress(progress, Settings.MIN_RATE, Settings.MAX_RATE)
            updateDeliveryLabels()
        }

        binding.pitchSeek.onProgress { progress ->
            settings.pitch = fromProgress(progress, Settings.MIN_PITCH, Settings.MAX_PITCH)
            updateDeliveryLabels()
        }

        binding.thresholdSeek.onProgress { progress ->
            settings.summariseThreshold = fromProgress(
                progress,
                Settings.MIN_THRESHOLD.toFloat(),
                Settings.MAX_THRESHOLD.toFloat()
            ).roundToInt()
            updateDeliveryLabels()
        }

        binding.testButton.setOnClickListener {
            val narrator = (application as NarratorApp).narrator
            narrator.whenReady {
                narrator.applyVoiceSettings()
                narrator.speak(getString(R.string.test_utterance), interrupt = true)
            }
        }

        binding.autoReadCheck.isChecked = settings.autoRead
        binding.autoReadCheck.setOnCheckedChangeListener { _, checked -> settings.autoRead = checked }

        binding.sentencePauseCheck.isChecked = settings.sentencePauses
        binding.sentencePauseCheck.setOnCheckedChangeListener { _, checked ->
            settings.sentencePauses = checked
        }

        binding.summariseCheck.isChecked = settings.summariseEnabled
        binding.summariseCheck.setOnCheckedChangeListener { _, checked ->
            settings.summariseEnabled = checked
            binding.thresholdSeek.isEnabled = checked
            binding.verbatimCheck.isEnabled = checked
        }

        binding.verbatimCheck.isChecked = settings.verbatimInstructions
        binding.verbatimCheck.setOnCheckedChangeListener { _, checked ->
            settings.verbatimInstructions = checked
        }

        binding.thresholdSeek.isEnabled = settings.summariseEnabled
        binding.verbatimCheck.isEnabled = settings.summariseEnabled

        updateDeliveryLabels()
    }

    private fun updateDeliveryLabels() {
        binding.rateLabel.text = getString(R.string.label_rate) + "  ·  " +
            String.format("%.2f×", settings.speechRate)
        binding.pitchLabel.text = getString(R.string.label_pitch) + "  ·  " +
            String.format("%.2f×", settings.pitch)
        binding.thresholdLabel.text = getString(R.string.label_summarise_threshold) + "  ·  " +
            getString(R.string.status_chars, settings.summariseThreshold)
    }

    private fun refresh() {
        val canOverlay = AndroidSettings.canDrawOverlays(this)
        val serviceOn = ChaiAccessibilityService.isEnabled(this)
        val hasTarget = settings.isConfigured

        binding.overlayTitle.text = tick(canOverlay) + " " + getString(R.string.step_overlay_title)
        binding.overlayButton.isEnabled = !canOverlay

        binding.accessibilityTitle.text =
            tick(serviceOn) + " " + getString(R.string.step_accessibility_title)

        binding.targetTitle.text = tick(hasTarget) + " " + getString(R.string.step_target_title)
        binding.targetValue.text = settings.targetLabel?.let { label ->
            "$label  (${settings.targetPackage})"
        } ?: getString(R.string.target_missing)

        val hasVoice = !settings.voiceName.isNullOrBlank()
        binding.voiceTitle.text = tick(hasVoice) + " " + getString(R.string.step_voice_title)
        binding.voiceValue.text = settings.voiceName ?: getString(R.string.voice_default)

        val ready = canOverlay && serviceOn && hasTarget
        binding.statusLine.text = if (ready) {
            getString(R.string.status_ready)
        } else {
            getString(R.string.status_needs_setup)
        }

        binding.bubbleToggle.text = getString(
            if (BubbleService.isRunning) R.string.action_stop_bubble else R.string.action_start_bubble
        )

        updateDeliveryLabels()
    }

    private fun toggleBubble() {
        if (BubbleService.isRunning) {
            BubbleService.stop(this)
            binding.bubbleToggle.postDelayed({ refresh() }, 200)
            return
        }

        if (!AndroidSettings.canDrawOverlays(this)) {
            toast(getString(R.string.permission_overlay_missing))
            return
        }
        if (!ChaiAccessibilityService.isEnabled(this)) {
            toast(getString(R.string.accessibility_missing))
            return
        }
        if (!settings.isConfigured) {
            toast(getString(R.string.target_missing))
            return
        }

        BubbleService.start(this)
        binding.bubbleToggle.postDelayed({ refresh() }, 200)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun tick(done: Boolean): String = if (done) "✓" else "○"

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun toProgress(value: Float, min: Float, max: Float): Int =
        (((value - min) / (max - min)) * 100f).roundToInt().coerceIn(0, 100)

    private fun fromProgress(progress: Int, min: Float, max: Float): Float =
        min + (max - min) * (progress / 100f)

    private inline fun SeekBar.onProgress(crossinline action: (Int) -> Unit) {
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) action(progress)
            }

            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
        })
    }
}
