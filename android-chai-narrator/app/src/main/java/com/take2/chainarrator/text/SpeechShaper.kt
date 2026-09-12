package com.take2.chainarrator.text

import java.text.BreakIterator

/**
 * Turns raw on-screen text into something that sounds like a person reading.
 *
 * Chat UIs are full of things a speech engine handles badly: emoji, markdown
 * asterisks around roleplay actions, bare URLs, doubled punctuation. Left alone
 * the engine either spells them out or stumbles over them, and the illusion of a
 * narrator collapses in the first sentence.
 */
object SpeechShaper {

    private val whitespace = Regex("\\s+")

    /** Markdown emphasis, including the *she smiles softly* convention Chai uses. */
    private val emphasis = Regex("(\\*{1,3}|_{1,3}|~{2})(?=\\S)(.+?)(?<=\\S)\\1", RegexOption.DOT_MATCHES_ALL)

    private val inlineCode = Regex("`{1,3}([^`]+)`{1,3}")

    private val markdownLink = Regex("\\[([^\\]]+)]\\((?:[^)]+)\\)")

    private val bareUrl = Regex("https?://\\S+|www\\.\\S+", RegexOption.IGNORE_CASE)

    private val bulletPrefix = Regex("(?m)^\\s*[-*•·+]\\s+")

    /**
     * Pictographs, dingbats, arrows, variation selectors and zero-width joiners.
     *
     * These are written as \x{...} code point escapes rather than surrogate
     * pairs on purpose. Java's regex engine matches code points, so a character
     * class built out of surrogate code units never matches an emoji at all.
     */
    private val pictographs = Regex(
        "[\\x{1F000}-\\x{1FAFF}]" +
            "|[\\x{2190}-\\x{21FF}]" +
            "|[\\x{2300}-\\x{23FF}]" +
            "|[\\x{2460}-\\x{24FF}]" +
            "|[\\x{25A0}-\\x{27BF}]" +
            "|[\\x{2B00}-\\x{2BFF}]" +
            "|[\\x{FE00}-\\x{FE0F}]" +
            "|\\x{200D}|\\x{20E3}"
    )

    private val repeatedPunctuation = Regex("([!?.,])\\1{1,}")

    private val spaceBeforePunctuation = Regex("\\s+([,.;:!?])")

    /**
     * Read as words rather than letters. Kept deliberately short: every entry is
     * a guess about intent, and a wrong guess is worse than a slightly odd
     * pronunciation.
     */
    private val expansions = listOf(
        Regex("(?i)\\be\\.g\\.") to "for example",
        Regex("(?i)\\bi\\.e\\.") to "that is",
        Regex("(?i)\\betc\\.") to "et cetera",
        Regex("(?i)\\bvs\\.?\\b") to "versus",
        Regex("(?i)\\bapprox\\.") to "approximately",
        Regex("(?i)\\bw/\\b") to "with",
        Regex("(?i)\\bb/c\\b") to "because",
        Regex("&") to " and "
    )

    /**
     * Cleans [raw] into a form the speech engine can read aloud.
     * Returns an empty string when nothing speakable is left.
     */
    fun shape(raw: String): String {
        var text = raw

        text = markdownLink.replace(text) { it.groupValues[1] }
        text = bareUrl.replace(text, " link ")
        text = inlineCode.replace(text) { it.groupValues[1] }

        // Run emphasis stripping twice so nested markers such as ***word*** clear.
        repeat(2) { text = emphasis.replace(text) { m -> m.groupValues[2] } }

        // Any asterisks that survive were unbalanced, so drop them outright
        // rather than letting the engine announce "asterisk".
        text = text.replace("*", " ").replace("_", " ")

        text = bulletPrefix.replace(text, "")
        text = pictographs.replace(text, " ")

        for ((pattern, replacement) in expansions) {
            text = pattern.replace(text, replacement)
        }

        text = repeatedPunctuation.replace(text) { it.groupValues[1] }
        text = spaceBeforePunctuation.replace(text) { it.groupValues[1] }
        text = whitespace.replace(text, " ").trim()

        return if (text.any { it.isLetterOrDigit() }) text else ""
    }

    /**
     * Splits shaped text into sentences. Speaking sentence by sentence lets the
     * caller insert a breath between them and lets the user interrupt part way
     * through a long passage instead of waiting for the whole thing.
     */
    fun sentences(text: String): List<String> {
        if (text.isBlank()) return emptyList()

        val iterator = BreakIterator.getSentenceInstance()
        iterator.setText(text)

        val result = mutableListOf<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            text.substring(start, end).trim().takeIf { it.isNotEmpty() }?.let(result::add)
            start = end
            end = iterator.next()
        }

        // A very long sentence with no terminator still needs breaking up, or the
        // engine's own buffer limit truncates it silently.
        return result.flatMap { splitLongClause(it) }
    }

    private fun splitLongClause(sentence: String): List<String> {
        if (sentence.length <= MAX_CHUNK) return listOf(sentence)

        val parts = mutableListOf<String>()
        var current = StringBuilder()
        for (word in sentence.split(' ')) {
            if (current.length + word.length + 1 > MAX_CHUNK && current.isNotEmpty()) {
                parts += current.toString().trim()
                current = StringBuilder()
            }
            current.append(word).append(' ')
        }
        current.toString().trim().takeIf { it.isNotEmpty() }?.let(parts::add)
        return parts
    }

    /** TextToSpeech.getMaxSpeechInputLength() is 4000; stay well inside it. */
    private const val MAX_CHUNK = 300
}
