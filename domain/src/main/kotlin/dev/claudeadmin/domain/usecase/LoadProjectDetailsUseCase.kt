package dev.claudeadmin.domain.usecase

import dev.claudeadmin.domain.model.ProjectDetails
import dev.claudeadmin.domain.model.ProjectId
import dev.claudeadmin.domain.repository.AgentRepository
import dev.claudeadmin.domain.repository.ClaudeMdRepository
import dev.claudeadmin.domain.repository.ProjectRepository

class LoadProjectDetailsUseCase(
    private val projects: ProjectRepository,
    private val claudeMd: ClaudeMdRepository,
    private val agents: AgentRepository,
) {
    suspend operator fun invoke(id: ProjectId): ProjectDetails? {
        val project = projects.get(id) ?: return null
        return ProjectDetails(
            project = project,
            claudeMd = claudeMd.load(project.path),
            agents = agents.loadForProject(project.path),
        )
    }
}
