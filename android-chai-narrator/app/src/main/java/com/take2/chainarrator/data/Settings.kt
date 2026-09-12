package com.take2.chainarrator.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Every user choice lives here, in one plain SharedPreferences file.
 *
 * Nothing in this file is ever transmitted. The app has no INTERNET permission,
 * and backup is disabled in the manifest, so these values stay on the handset.
 */
class Settings private constructor(private val prefs: SharedPreferences) {

    var targetPackage: String?
        get() = prefs.getString(KEY_TARGET_PACKAGE, null)
        set(value) = prefs.edit().putString(KEY_TARGET_PACKAGE, value).apply()

    var targetLabel: String?
        get() = prefs.getString(KEY_TARGET_LABEL, null)
        set(value) = prefs.edit().putString(KEY_TARGET_LABEL, value).apply()

    /** Null means "whatever the engine picked for the system locale". */
    var voiceName: String?
        get() = prefs.getString(KEY_VOICE_NAME, null)
        set(value) = prefs.edit().putString(KEY_VOICE_NAME, value).apply()

    /**
     * Slightly under 1.0 by default. A composed narrator reads a shade slower
     * than the engine default, which otherwise clips the ends of clauses.
     */
    var speechRate: Float
        get() = prefs.getFloat(KEY_RATE, DEFAULT_RATE)
        set(value) = prefs.edit().putFloat(KEY_RATE, value.coerceIn(MIN_RATE, MAX_RATE)).apply()

    /** Just under 1.0 keeps the register low and settled rather than bright. */
    var pitch: Float
        get() = prefs.getFloat(KEY_PITCH, DEFAULT_PITCH)
        set(value) = prefs.edit().putFloat(KEY_PITCH, value.coerceIn(MIN_PITCH, MAX_PITCH)).apply()

    var autoRead: Boolean
        get() = prefs.getBoolean(KEY_AUTO_READ, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_READ, value).apply()

    var sentencePauses: Boolean
        get() = prefs.getBoolean(KEY_SENTENCE_PAUSES, true)
        set(value) = prefs.edit().putBoolean(KEY_SENTENCE_PAUSES, value).apply()

    var summariseEnabled: Boolean
        get() = prefs.getBoolean(KEY_SUMMARISE, true)
        set(value) = prefs.edit().putBoolean(KEY_SUMMARISE, value).apply()

    var summariseThreshold: Int
        get() = prefs.getInt(KEY_SUMMARISE_THRESHOLD, DEFAULT_THRESHOLD)
        set(value) = prefs.edit()
            .putInt(KEY_SUMMARISE_THRESHOLD, value.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD))
            .apply()

    /** Step lists and numbered instructions are read in full even when long. */
    var verbatimInstructions: Boolean
        get() = prefs.getBoolean(KEY_VERBATIM, true)
        set(value) = prefs.edit().putBoolean(KEY_VERBATIM, value).apply()

    var bubbleX: Int
        get() = prefs.getInt(KEY_BUBBLE_X, Int.MIN_VALUE)
        set(value) = prefs.edit().putInt(KEY_BUBBLE_X, value).apply()

    var bubbleY: Int
        get() = prefs.getInt(KEY_BUBBLE_Y, Int.MIN_VALUE)
        set(value) = prefs.edit().putInt(KEY_BUBBLE_Y, value).apply()

    val isConfigured: Boolean get() = !targetPackage.isNullOrBlank()

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        const val MIN_RATE = 0.6f
        const val MAX_RATE = 1.4f
        const val DEFAULT_RATE = 0.94f

        const val MIN_PITCH = 0.7f
        const val MAX_PITCH = 1.3f
        const val DEFAULT_PITCH = 0.96f

        const val MIN_THRESHOLD = 300
        const val MAX_THRESHOLD = 2500
        const val DEFAULT_THRESHOLD = 700

        private const val KEY_TARGET_PACKAGE = "target_package"
        private const val KEY_TARGET_LABEL = "target_label"
        private const val KEY_VOICE_NAME = "voice_name"
        private const val KEY_RATE = "speech_rate"
        private const val KEY_PITCH = "pitch"
        private const val KEY_AUTO_READ = "auto_read"
        private const val KEY_SENTENCE_PAUSES = "sentence_pauses"
        private const val KEY_SUMMARISE = "summarise"
        private const val KEY_SUMMARISE_THRESHOLD = "summarise_threshold"
        private const val KEY_VERBATIM = "verbatim_instructions"
        private const val KEY_BUBBLE_X = "bubble_x"
        private const val KEY_BUBBLE_Y = "bubble_y"

        @Volatile
        private var instance: Settings? = null

        fun get(context: Context): Settings = instance ?: synchronized(this) {
            instance ?: Settings(
                context.applicationContext
                    .getSharedPreferences("chai_narrator", Context.MODE_PRIVATE)
            ).also { instance = it }
        }
    }
}
