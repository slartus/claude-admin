package dev.claudeadmin.domain.usecase

import dev.claudeadmin.domain.model.ProjectDetails
import dev.claudeadmin.domain.model.ProjectId
import dev.claudeadmin.domain.repository.AgentRepository
import dev.claudeadmin.domain.repository.ClaudeMdRepository
import dev.claudeadmin.domain.repository.ClaudeSettingsRepository
import dev.claudeadmin.domain.repository.CommandRepository
import dev.claudeadmin.domain.repository.ProjectRepository

class LoadProjectDetailsUseCase(
    private val projects: ProjectRepository,
    private val claudeMd: ClaudeMdRepository,
    private val settings: ClaudeSettingsRepository,
    private val agents: AgentRepository,
    private val commands: CommandRepository,
) {
    suspend operator fun invoke(id: ProjectId): ProjectDetails? {
        val project = projects.get(id) ?: return null
        return ProjectDetails(
            project = project,
            claudeMd = claudeMd.load(project.path),
            settingsLocal = settings.loadLocal(project.path),
            agents = agents.loadForProject(project.path),
            commands = commands.loadForProject(project.path),
        )
    }
}
