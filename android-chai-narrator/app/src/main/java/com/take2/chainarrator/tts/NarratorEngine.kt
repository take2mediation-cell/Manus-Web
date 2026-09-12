package com.take2.chainarrator.tts

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.take2.chainarrator.data.NarratorBus
import com.take2.chainarrator.data.Settings
import com.take2.chainarrator.text.SpeechShaper
import com.take2.chainarrator.text.Summarizer
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Owns the single TextToSpeech instance for the whole app.
 *
 * Both the accessibility service and the bubble need to speak and to know when
 * speech is running. Two engines would fight over the audio focus, so there is
 * one, created with the application context and kept alive for the session.
 */
class NarratorEngine(private val appContext: Context) {

    private val settings = Settings.get(appContext)
    private val outstanding = AtomicInteger(0)
    private val utteranceCounter = AtomicInteger(0)

    private var tts: TextToSpeech? = null

    @Volatile
    var isReady: Boolean = false
        private set

    /** Set when the engine failed to start, so the UI can say why nothing speaks. */
    @Volatile
    var initialisationError: String? = null
        private set

    private var pendingUtterance: String? = null
    private var onReadyCallbacks = mutableListOf<() -> Unit>()

    fun initialise() {
        if (tts != null) return

        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                configure()
                isReady = true
                initialisationError = null

                val queued = pendingUtterance
                pendingUtterance = null
                queued?.let { speak(it, interrupt = true) }

                val callbacks = synchronized(onReadyCallbacks) {
                    onReadyCallbacks.toList().also { onReadyCallbacks.clear() }
                }
                callbacks.forEach { it() }
            } else {
                isReady = false
                initialisationError = "Text-to-speech engine failed to start (status $status)"
                Log.w(TAG, "TextToSpeech init failed with status $status")
            }
        }
    }

    fun whenReady(action: () -> Unit) {
        if (isReady) {
            action()
        } else {
            synchronized(onReadyCallbacks) { onReadyCallbacks.add(action) }
            initialise()
        }
    }

    private fun configure() {
        val engine = tts ?: return

        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )

        val localeResult = engine.setLanguage(Locale.getDefault())
        if (localeResult == TextToSpeech.LANG_MISSING_DATA || localeResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.setLanguage(Locale.US)
        }

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                NarratorBus.setSpeaking(true)
            }

            override fun onDone(utteranceId: String?) = settle()

            @Suppress("OVERRIDE_DEPRECATION")
            override fun onError(utteranceId: String?) = settle()

            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.w(TAG, "Utterance $utteranceId failed with code $errorCode")
                settle()
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                outstanding.set(0)
                NarratorBus.setSpeaking(false)
            }
        })

        applyVoiceSettings()
    }

    private fun settle() {
        if (outstanding.decrementAndGet() <= 0) {
            outstanding.set(0)
            NarratorBus.setSpeaking(false)
        }
    }

    /** Re-reads pace, pitch and voice from settings. Safe to call at any time. */
    fun applyVoiceSettings() {
        val engine = tts ?: return

        engine.setSpeechRate(settings.speechRate)
        engine.setPitch(settings.pitch)

        val wanted = settings.voiceName
        if (wanted.isNullOrBlank()) return

        val match = try {
            engine.voices?.firstOrNull { it.name == wanted }
        } catch (error: Exception) {
            null
        }

        if (match != null) {
            engine.setVoice(match)
        } else {
            Log.i(TAG, "Saved voice $wanted is no longer available; keeping engine default")
        }
    }

    /**
     * Shapes, optionally shortens, then reads [raw].
     *
     * Returns the text that was actually spoken, or null if nothing was
     * speakable or the narrator is muted.
     */
    fun narrate(raw: String, interrupt: Boolean = false): String? {
        if (NarratorBus.isMuted) return null

        val shaped = SpeechShaper.shape(raw)
        if (shaped.isEmpty()) return null

        val condensed = if (settings.summariseEnabled) {
            Summarizer.condense(
                text = shaped,
                thresholdChars = settings.summariseThreshold,
                keepInstructionsVerbatim = settings.verbatimInstructions
            ).text
        } else {
            shaped
        }

        speak(condensed, interrupt)
        NarratorBus.publishUtterance(condensed)
        return condensed
    }

    /** Speaks already-shaped text. Used by narrate() and by the voice auditions. */
    fun speak(text: String, interrupt: Boolean) {
        val engine = tts
        if (engine == null || !isReady) {
            pendingUtterance = text
            initialise()
            return
        }

        val sentences = SpeechShaper.sentences(text).ifEmpty { listOf(text) }
        val pauseBetween = settings.sentencePauses

        sentences.forEachIndexed { index, sentence ->
            val mode = if (interrupt && index == 0) {
                TextToSpeech.QUEUE_FLUSH
            } else {
                TextToSpeech.QUEUE_ADD
            }

            outstanding.incrementAndGet()
            val result = engine.speak(sentence, mode, SPEECH_PARAMS, nextUtteranceId())
            if (result != TextToSpeech.SUCCESS) {
                settle()
                return
            }

            if (pauseBetween && index < sentences.lastIndex) {
                engine.playSilentUtterance(
                    pauseFor(sentence),
                    TextToSpeech.QUEUE_ADD,
                    nextUtteranceId()
                )
            }
        }
    }

    /**
     * A full stop earns a longer breath than a comma. This is the difference
     * between a narrator and a queue of clips played back to back.
     */
    private fun pauseFor(sentence: String): Long = when (sentence.trimEnd().lastOrNull()) {
        '.', '!', '?' -> 320L
        ':', ';' -> 240L
        else -> 160L
    }

    fun stopSpeaking() {
        outstanding.set(0)
        tts?.stop()
        NarratorBus.setSpeaking(false)
    }

    fun release() {
        stopSpeaking()
        tts?.shutdown()
        tts = null
        isReady = false
    }

    /** Exposed so the voice picker can rank what this engine actually offers. */
    fun voiceOptions(): List<VoiceCatalog.Option> {
        val engine = tts ?: return emptyList()
        return VoiceCatalog.build(engine)
    }

    /**
     * Auditions a voice. The engine is left switched to that voice until the
     * caller calls [applyVoiceSettings] again, because restoring it immediately
     * would race the utterance that was just queued: speak() is asynchronous and
     * some engines read the voice at synthesis time rather than at enqueue time.
     * The pickers call applyVoiceSettings() on the way out.
     */
    fun preview(voiceName: String?, text: String) {
        val engine = tts ?: return

        val target = voiceName?.let { name ->
            try {
                engine.voices?.firstOrNull { it.name == name }
            } catch (error: Exception) {
                null
            }
        }

        engine.setSpeechRate(settings.speechRate)
        engine.setPitch(settings.pitch)
        if (target != null) engine.setVoice(target)

        outstanding.incrementAndGet()
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, SPEECH_PARAMS, nextUtteranceId())
        if (result != TextToSpeech.SUCCESS) settle()
    }

    private fun nextUtteranceId(): String = "chai-${utteranceCounter.incrementAndGet()}"

    companion object {
        private const val TAG = "NarratorEngine"

        private val SPEECH_PARAMS = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
    }
}
