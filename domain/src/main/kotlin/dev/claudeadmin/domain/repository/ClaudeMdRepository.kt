package dev.claudeadmin.domain.repository

import dev.claudeadmin.domain.model.ClaudeMd

interface ClaudeMdRepository {
    suspend fun load(projectPath: String): ClaudeMd?
    suspend fun loadUser(): ClaudeMd?
}
