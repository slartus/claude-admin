package dev.claudeadmin.domain.model

data class Agent(
    val name: String,
    val description: String?,
    val scope: AgentScope,
    val path: String,
    val tools: List<String>,
    val model: String?,
    val permissionMode: String?,
    val body: String,
)

enum class AgentScope { PROJECT, USER }
