package dev.claudeadmin.domain.repository

import dev.claudeadmin.domain.model.OpenCodeSettings

interface OpenCodeSettingsRepository {
    suspend fun loadProject(projectPath: String): OpenCodeSettings?
    suspend fun loadUser(): OpenCodeSettings?
}
