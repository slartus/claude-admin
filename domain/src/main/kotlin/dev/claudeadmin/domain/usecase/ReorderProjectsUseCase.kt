package dev.claudeadmin.domain.usecase

import dev.claudeadmin.domain.model.ProjectId
import dev.claudeadmin.domain.repository.ProjectRepository

class ReorderProjectsUseCase(
    private val projects: ProjectRepository,
) {
    suspend operator fun invoke(orderedIds: List<ProjectId>) {
        projects.reorder(orderedIds)
    }
}
