package dev.claudeadmin.domain.repository

import dev.claudeadmin.domain.model.ClaudeSettings

interface ClaudeSettingsRepository {
    suspend fun loadLocal(projectPath: String): ClaudeSettings?
}
