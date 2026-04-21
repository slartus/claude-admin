package dev.claudeadmin.domain.repository

import dev.claudeadmin.domain.model.McpServer

interface McpServerRepository {
    suspend fun loadForProject(projectPath: String): List<McpServer>
}
