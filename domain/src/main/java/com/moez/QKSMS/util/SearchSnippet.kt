package com.moez.QKSMS.util

object SearchSnippet {
    fun create(text: String, query: String, context: Int = 35): String {
        val index = text.indexOf(query, ignoreCase = true).coerceAtLeast(0)
        val lineStart = text.lastIndexOf('\n', (index - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', index + query.length).let { if (it < 0) text.length else it }
        val start = maxOf(lineStart, index - context)
        val end = minOf(lineEnd, index + query.length + context)
        return (if (start > lineStart) "…" else "") + text.substring(start, end).trim() +
            (if (end < lineEnd) "…" else "")
    }
}
