package dev.claudeadmin.domain.usecase

import dev.claudeadmin.domain.model.ProjectId
import dev.claudeadmin.domain.repository.ProjectRepository

class SetProjectGitRootUseCase(
    private val projects: ProjectRepository,
) {
    suspend operator fun invoke(id: ProjectId, gitRoot: String?) {
        projects.setGitRoot(id, gitRoot)
    }
}
