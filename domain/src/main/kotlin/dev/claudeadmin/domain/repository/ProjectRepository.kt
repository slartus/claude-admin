package dev.claudeadmin.domain.repository

import dev.claudeadmin.domain.model.AiProvider
import dev.claudeadmin.domain.model.Project
import dev.claudeadmin.domain.model.ProjectId
import kotlinx.coroutines.flow.Flow

interface ProjectRepository {
    fun observeAll(): Flow<List<Project>>
    suspend fun add(path: String, name: String? = null, aiProvider: AiProvider = AiProvider.CLAUDE): Project
    suspend fun remove(id: ProjectId)
    suspend fun get(id: ProjectId): Project?
    suspend fun setGitRoot(id: ProjectId, gitRoot: String?)
    suspend fun reorder(orderedIds: List<ProjectId>)
}
