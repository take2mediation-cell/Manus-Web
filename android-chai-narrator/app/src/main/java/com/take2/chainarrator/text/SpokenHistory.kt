package com.take2.chainarrator.text

/**
 * Decides whether a block of on-screen text is actually new.
 *
 * Accessibility events fire constantly. A chat screen re-reports every visible
 * message on almost every scroll, and a reply that is still being typed out
 * arrives as a dozen events, each one a little longer than the last. Without this
 * the narrator would read the same message over and over, and would stutter
 * through a streaming reply one fragment at a time.
 */
class SpokenHistory(private val capacity: Int = 60) {

    sealed interface Decision {
        /** Already read, or not worth reading. */
        data object Skip : Decision

        /** Read this text. */
        data class Speak(val text: String) : Decision

        /**
         * A previously read message grew. Read only what was added, so a reply
         * that streams in does not get restarted from the top.
         */
        data class Continue(val addition: String, val full: String) : Decision
    }

    private val spoken = ArrayDeque<String>()
    private val spokenSet = HashSet<String>()

    fun evaluate(text: String): Decision {
        val candidate = text.trim()
        if (candidate.length < MIN_LENGTH) return Decision.Skip

        val key = normalize(candidate)
        if (key in spokenSet) return Decision.Skip

        // Look for a previously read message that this one extends.
        val extended = spoken.lastOrNull { key.startsWith(it) && key.length > it.length }
        if (extended != null) {
            val addition = candidate.substring(
                findOriginalOffset(candidate, extended.length)
            ).trim()
            if (addition.length < MIN_LENGTH) {
                // The tail is a stray character or two from a still-arriving
                // message. Record it so we do not re-read the whole thing later.
                remember(key)
                return Decision.Skip
            }
            remember(key)
            return Decision.Continue(addition, candidate)
        }

        remember(key)
        return Decision.Speak(candidate)
    }

    /** Called when the user switches target app or clears the queue. */
    fun reset() {
        spoken.clear()
        spokenSet.clear()
    }

    private fun remember(key: String) {
        if (spokenSet.add(key)) {
            spoken.addLast(key)
            while (spoken.size > capacity) {
                spokenSet.remove(spoken.removeFirst())
            }
        }
    }

    /**
     * Maps an offset in the normalized string back to the original, which may
     * have different whitespace. Walks the original counting normalized
     * characters rather than assuming the two line up.
     */
    private fun findOriginalOffset(original: String, normalizedOffset: Int): Int {
        var counted = 0
        var lastWasSpace = false
        for (index in original.indices) {
            val char = original[index]
            if (char.isWhitespace()) {
                if (lastWasSpace) continue
                lastWasSpace = true
            } else {
                lastWasSpace = false
            }
            counted++
            if (counted > normalizedOffset) return index
        }
        return original.length
    }

    private fun normalize(text: String): String =
        text.lowercase().replace(WHITESPACE, " ").trim()

    companion object {
        /** Below this a "message" is a timestamp, a badge count or a stray glyph. */
        const val MIN_LENGTH = 8

        private val WHITESPACE = Regex("\\s+")
    }
}
