package dev.claudeadmin.domain.model

data class OpenCodeSettings(
    val path: String,
    val content: String,
    val scope: OpenCodeSettingsScope,
)

enum class OpenCodeSettingsScope { PROJECT, USER }
