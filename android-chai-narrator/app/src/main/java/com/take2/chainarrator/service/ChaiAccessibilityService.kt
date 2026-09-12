package com.take2.chainarrator.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.provider.Settings as AndroidSettings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.take2.chainarrator.NarratorApp
import com.take2.chainarrator.data.NarratorBus
import com.take2.chainarrator.data.Settings
import com.take2.chainarrator.text.SpokenHistory
import com.take2.chainarrator.text.TextExtractor

/**
 * Watches the chosen app and reads whatever new text appears.
 *
 * The hard part is not reading, it is knowing when to read. Accessibility events
 * arrive many times a second while a reply streams in, and the same screen is
 * re-reported on every scroll. So the service waits for the screen to hold still
 * (see [SETTLE_DELAY_MS]) and then hands what it found to [SpokenHistory], which
 * decides whether it is genuinely new.
 */
class ChaiAccessibilityService : AccessibilityService(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private val handler = Handler(Looper.getMainLooper())
    private val history = SpokenHistory()

    private lateinit var settings: Settings

    private var lastTargetPackage: String? = null

    /**
     * True when the next read should be recorded but not spoken. Opening the app
     * shows a screen full of messages the user has already seen; without this the
     * narrator would read the entire visible backlog before getting to anything
     * new. Priming banks what is on screen so only what arrives after is read.
     */
    private var needsPriming = true

    private val settleRunnable = Runnable { readWhatChanged() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true

        settings = Settings.get(this)
        settings.registerListener(this)

        (application as? NarratorApp)?.narrator?.initialise()

        applyTargetFilter()
        Log.i(TAG, "Narrator service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !this::settings.isInitialized) return
        if (!settings.autoRead || NarratorBus.isMuted) return

        val target = settings.targetPackage ?: return
        if (event.packageName?.toString() != target) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // A new screen inside the target app. Bank what it shows rather than
            // reading it, so arriving at a conversation is silent.
            needsPriming = true
        }

        handler.removeCallbacks(settleRunnable)
        handler.postDelayed(settleRunnable, SETTLE_DELAY_MS)
    }

    private fun readWhatChanged() {
        val target = settings.targetPackage ?: return

        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != target) return

        val text = try {
            TextExtractor.extract(root)
        } catch (error: Exception) {
            Log.w(TAG, "Could not read the window contents", error)
            return
        }

        if (text.isBlank()) return

        val narrator = (application as? NarratorApp)?.narrator ?: return

        val priming = needsPriming
        needsPriming = false

        // Blocks are walked in display order, so when several messages land at
        // once they are queued in the order they appear rather than reversed.
        for (block in text.split('\n')) {
            val decision = history.evaluate(block)
            if (priming) continue

            when (decision) {
                is SpokenHistory.Decision.Skip -> Unit
                is SpokenHistory.Decision.Speak -> narrator.narrate(decision.text)
                is SpokenHistory.Decision.Continue -> narrator.narrate(decision.addition)
            }
        }
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        if (!this::settings.isInitialized) return

        val target = settings.targetPackage
        if (target != lastTargetPackage) {
            // A different app means a different conversation. Anything remembered
            // about the old one would only cause false "already read" matches.
            history.reset()
            needsPriming = true
            applyTargetFilter()
        }
        (application as? NarratorApp)?.narrator?.applyVoiceSettings()
    }

    /**
     * Narrows the service to the one package the user picked. Doing this at
     * runtime rather than in the XML config means the service never receives
     * events from any other app, which is both the privacy posture and a large
     * reduction in wasted work.
     */
    private fun applyTargetFilter() {
        val target = settings.targetPackage
        lastTargetPackage = target

        val info = (serviceInfo ?: AccessibilityServiceInfo()).apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_SPOKEN
            flags = AccessibilityServiceInfo.DEFAULT or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 200
            packageNames = target?.let { arrayOf(it) }
        }

        try {
            serviceInfo = info
        } catch (error: IllegalStateException) {
            Log.w(TAG, "Could not narrow the service to $target", error)
        }
    }

    override fun onInterrupt() {
        handler.removeCallbacks(settleRunnable)
        (application as? NarratorApp)?.narrator?.stopSpeaking()
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacks(settleRunnable)
        if (this::settings.isInitialized) settings.unregisterListener(this)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ChaiNarratorService"

        /**
         * How long the screen must hold still before anything is read. Short
         * enough to feel immediate, long enough that a reply typing itself out
         * character by character is read once, whole, rather than in fragments.
         */
        private const val SETTLE_DELAY_MS = 900L

        @Volatile
        var isRunning: Boolean = false
            private set

        /**
         * The authoritative check. [isRunning] only knows about this process, and
         * the system can have the service enabled while the process is not yet
         * started, so the settings screen asks the platform instead.
         */
        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, ChaiAccessibilityService::class.java)
            val enabled = AndroidSettings.Secure.getString(
                context.contentResolver,
                AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabled)
            for (entry in splitter) {
                if (ComponentName.unflattenFromString(entry) == expected) return true
            }
            return false
        }
    }
}
