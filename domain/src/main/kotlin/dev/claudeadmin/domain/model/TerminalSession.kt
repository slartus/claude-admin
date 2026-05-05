package dev.claudeadmin.domain.model

data class TerminalSession(
    val id: TerminalSessionId,
    val projectId: ProjectId?,
    val cwd: String,
    val title: String,
    val createdAt: Long,
    val aiSessionId: String? = null,
    val aiProvider: AiProvider,
)

@JvmInline
value class TerminalSessionId(val value: String)
