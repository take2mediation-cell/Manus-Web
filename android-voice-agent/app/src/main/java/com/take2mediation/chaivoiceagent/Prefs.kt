package com.take2mediation.chaivoiceagent

import android.content.Context
import android.content.SharedPreferences

/**
 * Default set of known Chai AI Android package IDs.
 * "Chai: Chat AI Platform" ships as com.Beauchamp.Messenger.external on the Play Store;
 * com.freechat.android.chaix (Chai.X) is a related companion app from the same publisher.
 * Editable from the app's Settings screen if Chai ships under a different package later.
 */
const val DEFAULT_TARGET_PACKAGES = "com.Beauchamp.Messenger.external,com.freechat.android.chaix"

object Prefs {
    private const val FILE = "chai_voice_agent_prefs"
    private const val KEY_ENABLED = "speak_enabled"
    private const val KEY_PACKAGES = "target_packages"
    private const val KEY_RATE = "speech_rate"
    private const val KEY_PITCH = "speech_pitch"
    private const val KEY_MIN_LENGTH = "min_text_length"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isSpeakEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, true)

    fun setSpeakEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun getTargetPackages(context: Context): List<String> =
        prefs(context).getString(KEY_PACKAGES, DEFAULT_TARGET_PACKAGES)
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: DEFAULT_TARGET_PACKAGES.split(",")

    fun setTargetPackages(context: Context, raw: String) {
        prefs(context).edit().putString(KEY_PACKAGES, raw).apply()
    }

    fun getRate(context: Context): Float =
        prefs(context).getFloat(KEY_RATE, 1.0f)

    fun setRate(context: Context, rate: Float) {
        prefs(context).edit().putFloat(KEY_RATE, rate).apply()
    }

    fun getPitch(context: Context): Float =
        prefs(context).getFloat(KEY_PITCH, 1.0f)

    fun setPitch(context: Context, pitch: Float) {
        prefs(context).edit().putFloat(KEY_PITCH, pitch).apply()
    }

    fun getMinLength(context: Context): Int =
        prefs(context).getInt(KEY_MIN_LENGTH, 2)

    fun registerChangeListener(context: Context, listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs(context).registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterChangeListener(context: Context, listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs(context).unregisterOnSharedPreferenceChangeListener(listener)
    }
}
