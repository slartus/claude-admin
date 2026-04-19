package dev.claudeadmin.domain.repository

import dev.claudeadmin.domain.model.Agent

interface AgentRepository {
    suspend fun loadForProject(projectPath: String): List<Agent>
}
