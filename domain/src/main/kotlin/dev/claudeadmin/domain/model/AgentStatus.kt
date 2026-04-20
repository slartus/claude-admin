package dev.claudeadmin.domain.model

enum class AgentStatus { IDLE, WORKING, WAITING }

data class AgentStatusEntry(
    val status: AgentStatus,
    val event: String,
    val updatedAt: Long,
)
