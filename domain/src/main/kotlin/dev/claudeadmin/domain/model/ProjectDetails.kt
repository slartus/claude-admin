package dev.claudeadmin.domain.model

data class ProjectDetails(
    val project: Project,
    val claudeMd: ClaudeMd?,
    val settingsLocal: ClaudeSettings?,
    val agents: List<Agent>,
    val commands: List<Command>,
)
