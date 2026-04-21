package dev.claudeadmin.domain.model

data class ClaudeSession(
    val id: String,
    val cwd: String,
    val preview: String,
    val lastModified: Long,
)
