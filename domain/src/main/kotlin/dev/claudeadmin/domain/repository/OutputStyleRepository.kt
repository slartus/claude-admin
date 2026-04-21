package dev.claudeadmin.domain.repository

import dev.claudeadmin.domain.model.OutputStyle

interface OutputStyleRepository {
    suspend fun loadForProject(projectPath: String): List<OutputStyle>
}
