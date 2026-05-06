package dev.claudeadmin.data.search

private const val SNIPPET_BEFORE = 50
private const val SNIPPET_AFTER = 100

internal data class Snippet(val text: String, val matchOffset: Int)

internal fun buildSnippet(text: String, matchIndex: Int, matchLength: Int): Snippet {
    val from = (matchIndex - SNIPPET_BEFORE).coerceAtLeast(0)
    val to = (matchIndex + matchLength + SNIPPET_AFTER).coerceAtMost(text.length)
    val window = text.substring(from, to).replace('\n', ' ').replace('\r', ' ').replace('\t', ' ')
    val prefix = if (from > 0) "…" else ""
    val suffix = if (to < text.length) "…" else ""
    return Snippet(
        text = prefix + window + suffix,
        matchOffset = prefix.length + (matchIndex - from),
    )
}
