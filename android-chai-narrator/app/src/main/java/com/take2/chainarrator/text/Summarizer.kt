package com.take2.chainarrator.text

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Shortens a long passage before it is read aloud.
 *
 * This is deliberately an extractive summariser built from word statistics, not a
 * model. Two reasons. It runs instantly on any handset with no download, and it
 * can only ever repeat sentences the app actually displayed, so it cannot invent
 * a line and put it in the narrator's mouth.
 */
object Summarizer {

    /** Spoken before a shortened passage so the listener knows it was condensed. */
    const val LEAD_IN = "Short version."

    private val stopWords = setOf(
        "a", "about", "after", "all", "also", "am", "an", "and", "any", "are", "as", "at",
        "be", "because", "been", "before", "being", "but", "by", "can", "could", "did", "do",
        "does", "doing", "done", "down", "each", "even", "for", "from", "get", "got", "had",
        "has", "have", "he", "her", "here", "hers", "him", "his", "how", "i", "if", "in",
        "into", "is", "it", "its", "just", "like", "me", "more", "most", "my", "no", "not",
        "now", "of", "off", "on", "one", "only", "or", "other", "our", "out", "over", "own",
        "she", "so", "some", "such", "than", "that", "the", "their", "them", "then", "there",
        "these", "they", "this", "those", "through", "to", "too", "under", "up", "very",
        "was", "we", "were", "what", "when", "where", "which", "while", "who", "why", "will",
        "with", "would", "you", "your", "yours"
    )

    private val wordPattern = Regex("[\\p{L}\\p{N}']+")

    /** Lines that read as a procedure: "1.", "2)", "Step 3", "- first do this". */
    private val stepLine = Regex(
        "(?m)^\\s*(?:step\\s+\\d+\\s*[.)\\-:]?|\\d+\\s*[.)\\-:]|[a-z]\\s*[.)])\\s+",
        RegexOption.IGNORE_CASE
    )

    private val instructionVerbs = setOf(
        "open", "tap", "press", "select", "choose", "enter", "type", "go", "click", "scroll",
        "swipe", "install", "download", "copy", "paste", "save", "close", "restart", "turn",
        "set", "add", "remove", "check", "confirm", "send", "reply", "wait", "hold"
    )

    data class Result(
        val text: String,
        val wasShortened: Boolean
    )

    /**
     * Returns [text] unchanged when it is short enough, when it reads as a set of
     * instructions and [keepInstructionsVerbatim] is on, or when shortening would
     * save nothing worth the loss. Otherwise returns the condensed version.
     */
    fun condense(
        text: String,
        thresholdChars: Int,
        keepInstructionsVerbatim: Boolean
    ): Result {
        if (text.length <= thresholdChars) return Result(text, false)
        if (keepInstructionsVerbatim && looksLikeInstructions(text)) return Result(text, false)

        val sentences = SpeechShaper.sentences(text)
        if (sentences.size < 4) return Result(text, false)

        val frequencies = termFrequencies(sentences)
        // The floor stops a tiny threshold producing a one-line stub, but it must
        // never exceed what the user actually asked for, or their setting is
        // silently ignored for every passage near the threshold.
        val budget = max(thresholdChars * 3 / 4, min(MIN_BUDGET, thresholdChars))

        val ranked = sentences.mapIndexed { index, sentence ->
            ScoredSentence(index, sentence, score(sentence, index, sentences.size, frequencies))
        }.sortedByDescending { it.score }

        val chosen = mutableListOf<ScoredSentence>()
        var used = 0
        for (candidate in ranked) {
            if (used + candidate.sentence.length > budget && chosen.isNotEmpty()) continue
            chosen += candidate
            used += candidate.sentence.length
            if (used >= budget) break
        }

        if (chosen.isEmpty()) return Result(text, false)

        val condensed = chosen.sortedBy { it.index }.joinToString(" ") { it.sentence }

        // If the "summary" saved less than a fifth of the length, the passage was
        // evenly weighted and the listener is better served by the whole thing.
        return if (condensed.length > text.length * 0.8) {
            Result(text, false)
        } else {
            Result("$LEAD_IN $condensed", true)
        }
    }

    fun looksLikeInstructions(text: String): Boolean {
        if (stepLine.findAll(text).count() >= 2) return true

        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size < 2) return false

        val imperativeLines = lines.count { line ->
            val firstWord = wordPattern.find(line)?.value?.lowercase()
            firstWord != null && firstWord in instructionVerbs
        }
        return imperativeLines >= 2 && imperativeLines >= (lines.size * 0.4).roundToInt()
    }

    private fun termFrequencies(sentences: List<String>): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        for (sentence in sentences) {
            for (match in wordPattern.findAll(sentence.lowercase())) {
                val word = match.value
                if (word.length < 3 || word in stopWords) continue
                counts[word] = (counts[word] ?: 0) + 1
            }
        }
        return counts
    }

    private fun score(
        sentence: String,
        index: Int,
        total: Int,
        frequencies: Map<String, Int>
    ): Double {
        val words = wordPattern.findAll(sentence.lowercase())
            .map { it.value }
            .filter { it.length >= 3 && it !in stopWords }
            .toList()
        if (words.isEmpty()) return 0.0

        val informationDensity = words.sumOf { (frequencies[it] ?: 0).toDouble() } / words.size

        // Chat replies tend to state the point first and land it last, so both
        // ends of the passage get a modest lift over the middle.
        val positionBoost = when {
            index == 0 -> 1.35
            index == total - 1 -> 1.15
            index < total / 4 -> 1.10
            else -> 1.0
        }

        // Very short fragments ("Right." "Okay.") carry little and read oddly
        // when lifted out of context.
        val lengthPenalty = if (sentence.length < 40) 0.5 else 1.0

        return informationDensity * positionBoost * lengthPenalty
    }

    private data class ScoredSentence(val index: Int, val sentence: String, val score: Double)

    private const val MIN_BUDGET = 240
}
