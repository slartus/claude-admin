package dev.claudeadmin.domain.repository

import dev.claudeadmin.domain.model.ProjectApiKeys

interface ProjectApiKeysRepository {
    suspend fun load(projectPath: String): ProjectApiKeys
    suspend fun save(projectPath: String, keys: ProjectApiKeys)
}
