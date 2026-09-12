package com.take2mediation.chaivoiceagent

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale
import java.util.UUID

/**
 * Thin wrapper around Android's on-device TextToSpeech engine.
 * Everything happens locally on the phone: no audio or text ever leaves the device.
 */
class SpeechEngine(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var ready = false
    private val pendingQueue = mutableListOf<String>()

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
            ready = true
            pendingQueue.forEach { enqueue(it) }
            pendingQueue.clear()
        } else {
            Log.e("SpeechEngine", "TextToSpeech failed to initialize, status=$status")
        }
    }

    fun setRate(rate: Float) {
        tts?.setSpeechRate(rate.coerceIn(0.3f, 3.0f))
    }

    fun setPitch(pitch: Float) {
        tts?.setPitch(pitch.coerceIn(0.3f, 3.0f))
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        if (!ready) {
            pendingQueue.add(text)
            return
        }
        enqueue(text)
    }

    private fun enqueue(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, UUID.randomUUID().toString())
    }

    fun stopSpeaking() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
