package dev.claudeadmin.domain.model

data class ClaudeSettings(
    val path: String,
    val content: String,
    val scope: ClaudeSettingsScope,
)

enum class ClaudeSettingsScope { PROJECT, PROJECT_LOCAL, USER }
