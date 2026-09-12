package com.take2.chaichat.data

import android.content.Context

/**
 * API key, model and base URL.
 *
 * The key is held in the app's private SharedPreferences and nothing else reads
 * it. Backup is disabled in the manifest so it is not carried off the device in
 * a cloud backup or a phone migration.
 */
class Settings private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("chai_chat", Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_API_KEY, value.trim()).apply()

    var model: String
        get() = prefs.getString(KEY_MODEL, com.take2.chaichat.net.ChaiApi.DEFAULT_MODEL)
            .orEmpty()
            .ifBlank { com.take2.chaichat.net.ChaiApi.DEFAULT_MODEL }
        set(value) = prefs.edit().putString(KEY_MODEL, value.trim()).apply()

    /**
     * Editable because the endpoint could not be verified against live docs when
     * this was written. If Chai moves it, this is a settings change rather than
     * a new build.
     */
    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, com.take2.chaichat.net.ChaiApi.DEFAULT_BASE_URL)
            .orEmpty()
            .ifBlank { com.take2.chaichat.net.ChaiApi.DEFAULT_BASE_URL }
        set(value) = prefs.edit().putString(KEY_BASE_URL, value.trim()).apply()

    /** Sent as the first turn when set, so the bot keeps a consistent persona. */
    var systemPrompt: String
        get() = prefs.getString(KEY_SYSTEM_PROMPT, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_SYSTEM_PROMPT, value.trim()).apply()

    val isConfigured: Boolean get() = apiKey.isNotBlank()

    companion object {
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_SYSTEM_PROMPT = "system_prompt"

        @Volatile
        private var instance: Settings? = null

        fun get(context: Context): Settings = instance ?: synchronized(this) {
            instance ?: Settings(context).also { instance = it }
        }
    }
}
