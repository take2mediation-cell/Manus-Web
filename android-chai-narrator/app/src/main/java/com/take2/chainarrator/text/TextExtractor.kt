package com.take2.chainarrator.text

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Pulls readable text out of another app's view hierarchy.
 *
 * Two rules shape this. It reads leaf nodes only, because a container repeats
 * everything its children hold and would double every line. And it never reads
 * an editable field, so whatever the user is part way through typing stays
 * private even though the service technically has access to it.
 */
object TextExtractor {

    private const val MAX_NODES = 600
    private const val MAX_DEPTH = 40

    private val skippedClassNames = listOf(
        "EditText",
        "AutoCompleteTextView",
        "SearchView"
    )

    fun extract(root: AccessibilityNodeInfo?): String {
        if (root == null) return ""

        val collected = mutableListOf<String>()
        val budget = intArrayOf(MAX_NODES)
        walk(root, 0, budget, collected)

        return collected.joinToString("\n")
    }

    private fun walk(
        node: AccessibilityNodeInfo,
        depth: Int,
        budget: IntArray,
        out: MutableList<String>
    ) {
        if (depth > MAX_DEPTH || budget[0] <= 0) return
        budget[0]--

        // The window root can report itself as not visible on some devices, which
        // would discard the whole tree, so the visibility test starts below it.
        if (isExcluded(node, applyVisibilityTest = depth > 0)) return

        val childCount = node.childCount
        if (childCount == 0) {
            textOf(node)?.let(out::add)
            return
        }

        for (index in 0 until childCount) {
            val child = node.getChild(index) ?: continue
            try {
                walk(child, depth + 1, budget, out)
            } finally {
                // Recycling is a no-op from API 33 on, but older devices leak
                // hard without it and this service runs for hours at a time.
                @Suppress("DEPRECATION")
                child.recycle()
            }
        }
    }

    private fun isExcluded(node: AccessibilityNodeInfo, applyVisibilityTest: Boolean): Boolean {
        if (node.isPassword || node.isEditable) return true
        if (applyVisibilityTest && !node.isVisibleToUser) return true

        val className = node.className?.toString() ?: return false
        return skippedClassNames.any { className.endsWith(it) }
    }

    private fun textOf(node: AccessibilityNodeInfo): String? {
        val text = node.text?.toString()?.trim()
        if (!text.isNullOrEmpty()) return text

        // Some chat bubbles carry their content only as a content description,
        // particularly when the message is rendered rather than laid out as text.
        val description = node.contentDescription?.toString()?.trim()
        return description?.takeIf { it.isNotEmpty() }
    }
}
