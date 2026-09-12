package com.take2.chainarrator.tts

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

/**
 * Ranks the voices an engine offers so the best candidates for a calm, mature
 * female narrator sit at the top of the picker.
 *
 * Android exposes no age attribute and no reliable gender attribute, so nothing
 * here can promise "a forty year old woman". What it can do is put the voices
 * that are most likely to fit first and let the user audition them. Google's
 * engine encodes gender in the voice name (en-us-x-sfg#female_1-local), which is
 * the strongest signal available; other engines fall back to locale and quality.
 */
object VoiceCatalog {

    data class Option(
        val voiceName: String?,
        val displayName: String,
        val subtitle: String,
        val requiresNetwork: Boolean,
        val score: Int
    ) {
        val isEngineDefault: Boolean get() = voiceName == null
    }

    private val femalePattern = Regex("(?i)female|\\bfem\\b|_f_|-f\\d")

    /** Matches "male" only when it is not the tail of "female". */
    private val malePattern = Regex("(?i)(?<!fe)male")

    fun build(tts: TextToSpeech, preferred: Locale = Locale.getDefault()): List<Option> {
        val voices = try {
            tts.voices ?: emptySet()
        } catch (error: Exception) {
            // Some OEM engines throw here before they are fully warmed up.
            emptySet()
        }

        val options = voices
            // features can come back null from some OEM engines, so it is read
            // defensively. A voice the engine reports as not installed cannot
            // speak, so it is dropped rather than offered and then failing.
            .filterNot { voice ->
                voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true
            }
            .map { voice -> toOption(voice, preferred) }
            .sortedWith(compareByDescending<Option> { it.score }.thenBy { it.displayName })

        val default = Option(
            voiceName = null,
            displayName = "Engine default",
            subtitle = "Whatever your text-to-speech engine picks for ${preferred.displayLanguage}",
            requiresNetwork = false,
            score = Int.MIN_VALUE
        )

        return options + default
    }

    private fun toOption(voice: Voice, preferred: Locale): Option {
        var score = 0
        val name = voice.name ?: ""

        val isFemale = femalePattern.containsMatchIn(name)
        val isMale = !isFemale && malePattern.containsMatchIn(name)
        when {
            isFemale -> score += 120
            isMale -> score -= 120
        }

        val locale = voice.locale
        if (locale != null) {
            if (locale.language == preferred.language) {
                score += 40
                if (locale.country == preferred.country) score += 25
            } else {
                score -= 60
            }
        }

        score += when (voice.quality) {
            Voice.QUALITY_VERY_HIGH -> 40
            Voice.QUALITY_HIGH -> 28
            Voice.QUALITY_NORMAL -> 12
            else -> 0
        }

        score += when (voice.latency) {
            Voice.LATENCY_VERY_LOW -> 12
            Voice.LATENCY_LOW -> 8
            Voice.LATENCY_NORMAL -> 4
            else -> 0
        }

        // The app has no internet permission, so a network voice cannot speak at
        // all. It stays in the list only so the user is not left wondering where
        // a voice they expected went.
        if (voice.isNetworkConnectionRequired) score -= 500

        return Option(
            voiceName = name,
            displayName = prettyName(voice, isFemale, isMale),
            subtitle = buildSubtitle(voice, isFemale, isMale),
            requiresNetwork = voice.isNetworkConnectionRequired,
            score = score
        )
    }

    private fun prettyName(voice: Voice, isFemale: Boolean, isMale: Boolean): String {
        val locale = voice.locale
        val place = when {
            locale == null -> "Unknown"
            locale.country.isNullOrBlank() -> locale.displayLanguage
            else -> "${locale.displayLanguage} (${locale.displayCountry})"
        }
        val timbre = when {
            isFemale -> "female"
            isMale -> "male"
            else -> "unlabelled"
        }
        val variant = voice.name?.substringAfterLast('-')?.take(12).orEmpty()
        return "$place, $timbre $variant".trim().trimEnd(',')
    }

    private fun buildSubtitle(voice: Voice, isFemale: Boolean, isMale: Boolean): String {
        val quality = when (voice.quality) {
            Voice.QUALITY_VERY_HIGH -> "very high quality"
            Voice.QUALITY_HIGH -> "high quality"
            Voice.QUALITY_NORMAL -> "normal quality"
            else -> "low quality"
        }
        val gender = when {
            isFemale -> "named as female"
            isMale -> "named as male"
            else -> "gender not stated by the engine"
        }
        return listOf(voice.name.orEmpty(), quality, gender)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
    }
}
