package dev.claudeadmin.domain.repository

import dev.claudeadmin.domain.model.Hook

interface HookRepository {
    suspend fun loadForProject(projectPath: String): List<Hook>
}
