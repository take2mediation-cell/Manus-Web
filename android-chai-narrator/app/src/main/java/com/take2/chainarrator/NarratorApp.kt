package com.take2.chainarrator

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.take2.chainarrator.tts.NarratorEngine

class NarratorApp : Application() {

    /**
     * One engine for the whole process. The accessibility service and the bubble
     * both reach it through this property rather than creating their own, so
     * they never compete for audio focus.
     */
    val narrator: NarratorEngine by lazy { NarratorEngine(this) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
            enableVibration(false)
        }

        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "narrator_bubble"
    }
}
