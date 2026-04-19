package dev.claudeadmin.domain.usecase

import dev.claudeadmin.domain.model.ProjectId
import dev.claudeadmin.domain.model.TerminalSession
import dev.claudeadmin.domain.repository.TerminalRepository
import kotlinx.coroutines.flow.Flow

class ObserveTerminalsUseCase(
    private val terminals: TerminalRepository,
) {
    fun all(): Flow<List<TerminalSession>> = terminals.observeAll()
    fun byProject(projectId: ProjectId): Flow<List<TerminalSession>> =
        terminals.observeByProject(projectId)
}
