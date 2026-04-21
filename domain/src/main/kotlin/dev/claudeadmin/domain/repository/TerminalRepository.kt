package dev.claudeadmin.domain.repository

import dev.claudeadmin.domain.model.Project
import dev.claudeadmin.domain.model.ProjectId
import dev.claudeadmin.domain.model.TerminalSession
import dev.claudeadmin.domain.model.TerminalSessionId
import kotlinx.coroutines.flow.Flow

interface TerminalRepository {
    fun observeByProject(projectId: ProjectId): Flow<List<TerminalSession>>
    fun observeAll(): Flow<List<TerminalSession>>
    suspend fun open(
        project: Project,
        title: String = "claude",
        resumeSessionId: String? = null,
    ): TerminalSession
    suspend fun close(id: TerminalSessionId)
}
