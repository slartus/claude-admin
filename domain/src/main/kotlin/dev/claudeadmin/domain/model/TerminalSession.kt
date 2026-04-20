package dev.claudeadmin.domain.model

data class TerminalSession(
    val id: TerminalSessionId,
    val projectId: ProjectId,
    val title: String,
    val createdAt: Long,
    val claudeSessionId: String? = null,
)

@JvmInline
value class TerminalSessionId(val value: String)
