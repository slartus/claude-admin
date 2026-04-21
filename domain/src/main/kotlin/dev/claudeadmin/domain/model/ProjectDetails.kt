package dev.claudeadmin.domain.model

data class ProjectDetails(
    val project: Project,
    val projectClaudeMd: ClaudeMd?,
    val userClaudeMd: ClaudeMd?,
    val projectSettings: ClaudeSettings?,
    val projectSettingsLocal: ClaudeSettings?,
    val userSettings: ClaudeSettings?,
    val agents: List<Agent>,
    val commands: List<Command>,
    val skills: List<Skill>,
    val outputStyles: List<OutputStyle>,
    val hooks: List<Hook>,
    val mcpServers: List<McpServer>,
)
