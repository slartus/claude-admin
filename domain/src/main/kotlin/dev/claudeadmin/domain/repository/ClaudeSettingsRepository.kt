package dev.claudeadmin.domain.repository

import dev.claudeadmin.domain.model.ClaudeSettings

interface ClaudeSettingsRepository {
    suspend fun loadProject(projectPath: String): ClaudeSettings?
    suspend fun loadProjectLocal(projectPath: String): ClaudeSettings?
    suspend fun loadUser(): ClaudeSettings?
}
