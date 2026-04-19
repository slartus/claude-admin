package dev.claudeadmin.domain.usecase

import dev.claudeadmin.domain.model.ProjectId
import dev.claudeadmin.domain.model.TerminalSession
import dev.claudeadmin.domain.repository.ProjectRepository
import dev.claudeadmin.domain.repository.TerminalRepository
import kotlinx.coroutines.flow.first

class RemoveProjectUseCase(
    private val projects: ProjectRepository,
    private val terminals: TerminalRepository,
) {
    suspend operator fun invoke(id: ProjectId) {
        terminals.observeByProject(id).first().forEach { session: TerminalSession ->
            terminals.close(session.id)
        }
        projects.remove(id)
    }
}
