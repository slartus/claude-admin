package dev.claudeadmin.domain.usecase

import dev.claudeadmin.domain.model.Project
import dev.claudeadmin.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow

class ObserveProjectsUseCase(
    private val projects: ProjectRepository,
) {
    operator fun invoke(): Flow<List<Project>> = projects.observeAll()
}
