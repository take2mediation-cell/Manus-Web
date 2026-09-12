package com.take2mediation.chaivoiceagent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.core.app.NotificationCompat
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

const val ACTION_PAUSE = "com.take2mediation.chaivoiceagent.action.PAUSE"
const val ACTION_RESUME = "com.take2mediation.chaivoiceagent.action.RESUME"
private const val CHANNEL_ID = "chai_voice_agent_status"
private const val NOTIF_ID = 1001
private const val HISTORY_CAP = 300

/**
 * Accessibility service scoped (via accessibility_service_config.xml and the runtime
 * package filter below) to the Chai AI app package(s) only. On every content change in
 * that app it walks the visible node tree, picks out text that hasn't been spoken yet,
 * and hands it to the on-device TextToSpeech engine. Nothing is read from, or sent to,
 * any other app, and nothing leaves the device.
 */
class ChaiScreenReaderService : AccessibilityService() {

    private lateinit var speech: SpeechEngine
    private val spokenHistory = LinkedHashSet<String>()

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        applyRuntimeServiceInfo()
        speech.setRate(Prefs.getRate(this))
        speech.setPitch(Prefs.getPitch(this))
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        speech = SpeechEngine(this)
        speech.setRate(Prefs.getRate(this))
        speech.setPitch(Prefs.getPitch(this))
        applyRuntimeServiceInfo()
        Prefs.registerChangeListener(this, prefsListener)
        postStatusNotification(Prefs.isSpeakEnabled(this))
    }

    private fun applyRuntimeServiceInfo() {
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_SPOKEN
            notificationTimeout = 150
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            packageNames = Prefs.getTargetPackages(this@ChaiScreenReaderService).toTypedArray()
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!Prefs.isSpeakEnabled(this)) return

        val targetPackages = Prefs.getTargetPackages(this)
        val eventPackage = event.packageName?.toString() ?: return
        if (eventPackage !in targetPackages) return

        val root = rootInActiveWindow ?: return
        val minLength = Prefs.getMinLength(this)
        val newTexts = mutableListOf<String>()
        collectNewText(root, minLength, newTexts)

        newTexts.forEach { text ->
            spokenHistory.add(text)
            speech.speak(text)
        }

        while (spokenHistory.size > HISTORY_CAP) {
            val oldest = spokenHistory.firstOrNull() ?: break
            spokenHistory.remove(oldest)
        }
    }

    private fun collectNewText(node: AccessibilityNodeInfo, minLength: Int, out: MutableList<String>) {
        if (!node.isVisibleToUser) return

        if (!node.isEditable) {
            val candidate = (node.text?.toString() ?: node.contentDescription?.toString())?.trim()
            if (!candidate.isNullOrEmpty() && candidate.length >= minLength && !isNoise(candidate) &&
                candidate !in spokenHistory && candidate !in out
            ) {
                out.add(candidate)
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectNewText(child, minLength, out)
        }
    }

    private fun isNoise(text: String): Boolean {
        val timestampLike = Regex("^[0-9:apmAPM\\s.]+$")
        return timestampLike.matches(text)
    }

    override fun onInterrupt() {
        speech.stopSpeaking()
    }

    override fun onDestroy() {
        super.onDestroy()
        Prefs.unregisterChangeListener(this, prefsListener)
        speech.shutdown()
        if (instance === this) instance = null
    }

    fun setPaused(paused: Boolean) {
        Prefs.setSpeakEnabled(this, !paused)
        if (paused) speech.stopSpeaking()
        postStatusNotification(!paused)
    }

    private fun postStatusNotification(running: Boolean) {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }

        val toggleAction = if (running) ACTION_PAUSE else ACTION_RESUME
        val toggleLabel = if (running) getString(R.string.action_pause) else getString(R.string.action_resume)
        val toggleIntent = Intent(this, NotificationActionReceiver::class.java).apply { action = toggleAction }
        val togglePendingIntent = PendingIntent.getBroadcast(
            this, 0, toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(if (running) getString(R.string.notif_text_running) else getString(R.string.notif_text_paused))
            .setOngoing(true)
            .addAction(0, toggleLabel, togglePendingIntent)
            .build()

        manager.notify(NOTIF_ID, notification)
    }

    companion object {
        var instance: ChaiScreenReaderService? = null
    }
}
