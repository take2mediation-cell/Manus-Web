package com.take2.chainarrator.data

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The accessibility service, the bubble overlay and the settings screen are three
 * separate Android components that cannot hold references to each other safely.
 * This is the single place they meet: the service publishes, the others observe.
 * All callbacks are delivered on the main thread.
 */
object NarratorBus {

    interface Observer {
        /** Called when speech starts or stops. */
        fun onSpeakingChanged(speaking: Boolean) {}

        /** Called with the text that is about to be read, already shaped for speech. */
        fun onUtteranceStarted(preview: String) {}

        /** Called when the user mutes or unmutes from the bubble. */
        fun onMutedChanged(muted: Boolean) {}
    }

    private val handler = Handler(Looper.getMainLooper())
    private val observers = CopyOnWriteArrayList<Observer>()

    @Volatile
    var isSpeaking: Boolean = false
        private set

    /**
     * Muting stops new text being read but leaves the service listening, so the
     * user can silence the narrator mid-sentence without losing their setup.
     */
    @Volatile
    var isMuted: Boolean = false
        private set

    /** The last thing that was read, kept only so the bubble can offer a replay. */
    @Volatile
    var lastUtterance: String? = null
        private set

    fun addObserver(observer: Observer) {
        observers.addIfAbsent(observer)
    }

    fun removeObserver(observer: Observer) {
        observers.remove(observer)
    }

    fun setSpeaking(speaking: Boolean) {
        if (isSpeaking == speaking) return
        isSpeaking = speaking
        dispatch { it.onSpeakingChanged(speaking) }
    }

    fun setMuted(muted: Boolean) {
        if (isMuted == muted) return
        isMuted = muted
        dispatch { it.onMutedChanged(muted) }
    }

    fun toggleMuted(): Boolean {
        setMuted(!isMuted)
        return isMuted
    }

    fun publishUtterance(text: String) {
        lastUtterance = text
        dispatch { it.onUtteranceStarted(text) }
    }

    private inline fun dispatch(crossinline action: (Observer) -> Unit) {
        handler.post { observers.forEach { action(it) } }
    }
}
