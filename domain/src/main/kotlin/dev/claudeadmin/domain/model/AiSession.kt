package dev.claudeadmin.domain.model

data class AiSession(
    val id: String,
    val cwd: String,
    val preview: String,
    val lastModified: Long,
    val provider: AiProvider,
)

@Deprecated("Use AiSession instead", ReplaceWith("AiSession"))
typealias ClaudeSession = AiSession
