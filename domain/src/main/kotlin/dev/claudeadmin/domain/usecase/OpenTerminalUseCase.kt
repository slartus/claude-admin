package dev.claudeadmin.domain.usecase

import dev.claudeadmin.domain.model.AiProvider
import dev.claudeadmin.domain.model.ProjectId
import dev.claudeadmin.domain.model.TerminalSession
import dev.claudeadmin.domain.repository.ProjectRepository
import dev.claudeadmin.domain.repository.TerminalRepository

class OpenTerminalUseCase(
    private val projects: ProjectRepository,
    private val terminals: TerminalRepository,
) {
    suspend operator fun invoke(
        projectId: ProjectId,
        title: String = "terminal",
        resumeSessionId: String? = null,
        provider: AiProvider,
    ): Result<TerminalSession> {
        val project = projects.get(projectId)
            ?: return Result.failure(IllegalStateException("Project $projectId not found"))
        return runCatching { terminals.open(project, title, resumeSessionId, provider) }
    }

    suspend fun openDetached(
        cwd: String,
        title: String = "terminal",
        resumeSessionId: String? = null,
        provider: AiProvider,
    ): Result<TerminalSession> = runCatching { terminals.openDetached(cwd, title, resumeSessionId, provider) }
}
