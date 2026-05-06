package dev.claudeadmin.domain.model

data class SessionSearchHit(
    val sessionId: String,
    val provider: AiProvider,
    val snippet: String,
    val matchOffset: Int,
    val matchLength: Int,
)
