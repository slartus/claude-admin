package dev.claudeadmin.domain.repository

import dev.claudeadmin.domain.model.Command

interface CommandRepository {
    suspend fun loadForProject(projectPath: String): List<Command>
}
