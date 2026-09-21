package com.example.expensetracker.sharedimport

internal data class OcrTextFragment(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

internal fun reconstructOcrRows(fragments: List<OcrTextFragment>): String {
    val validFragments = fragments
        .filter { it.text.isNotBlank() && it.right > it.left && it.bottom > it.top }

    val parents = IntArray(validFragments.size) { it }
    fun root(index: Int): Int {
        var current = index
        while (parents[current] != current) {
            parents[current] = parents[parents[current]]
            current = parents[current]
        }
        return current
    }

    fun merge(first: Int, second: Int) {
        val firstRoot = root(first)
        val secondRoot = root(second)
        if (firstRoot != secondRoot) parents[secondRoot] = firstRoot
    }

    validFragments.indices.forEach { firstIndex ->
        ((firstIndex + 1) until validFragments.size).forEach { secondIndex ->
            val first = validFragments[firstIndex]
            val second = validFragments[secondIndex]
            val overlap = minOf(first.bottom, second.bottom) - maxOf(first.top, second.top)
            val minimumHeight = minOf(first.bottom - first.top, second.bottom - second.top).toFloat()
            val centerDistance = kotlin.math.abs(
                (first.top + first.bottom) / 2f - (second.top + second.bottom) / 2f
            )
            if (overlap >= minimumHeight * 0.35f || centerDistance <= minimumHeight * 0.6f) {
                merge(firstIndex, secondIndex)
            }
        }
    }

    return validFragments.indices
        .groupBy(::root)
        .values
        .sortedBy { row -> row.minOf { validFragments[it].top } }
        .joinToString("\n") { row ->
            row.map(validFragments::get)
                .sortedBy(OcrTextFragment::left)
                .joinToString(" ") { it.text.trim() }
        }
}