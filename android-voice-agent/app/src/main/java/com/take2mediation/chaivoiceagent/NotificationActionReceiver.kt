package com.take2mediation.chaivoiceagent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val service = ChaiScreenReaderService.instance
        when (intent.action) {
            ACTION_PAUSE -> service?.setPaused(true) ?: Prefs.setSpeakEnabled(context, false)
            ACTION_RESUME -> service?.setPaused(false) ?: Prefs.setSpeakEnabled(context, true)
        }
    }
}
