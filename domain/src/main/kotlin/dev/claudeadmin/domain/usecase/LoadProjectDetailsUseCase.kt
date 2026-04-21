package dev.claudeadmin.domain.usecase

import dev.claudeadmin.domain.model.ProjectDetails
import dev.claudeadmin.domain.model.ProjectId
import dev.claudeadmin.domain.repository.AgentRepository
import dev.claudeadmin.domain.repository.ClaudeMdRepository
import dev.claudeadmin.domain.repository.ClaudeSettingsRepository
import dev.claudeadmin.domain.repository.CommandRepository
import dev.claudeadmin.domain.repository.HookRepository
import dev.claudeadmin.domain.repository.McpServerRepository
import dev.claudeadmin.domain.repository.OutputStyleRepository
import dev.claudeadmin.domain.repository.ProjectRepository
import dev.claudeadmin.domain.repository.SkillRepository

class LoadProjectDetailsUseCase(
    private val projects: ProjectRepository,
    private val claudeMd: ClaudeMdRepository,
    private val settings: ClaudeSettingsRepository,
    private val agents: AgentRepository,
    private val commands: CommandRepository,
    private val skills: SkillRepository,
    private val outputStyles: OutputStyleRepository,
    private val hooks: HookRepository,
    private val mcpServers: McpServerRepository,
) {
    suspend operator fun invoke(id: ProjectId): ProjectDetails? {
        val project = projects.get(id) ?: return null
        return ProjectDetails(
            project = project,
            projectClaudeMd = claudeMd.load(project.path),
            userClaudeMd = claudeMd.loadUser(),
            projectSettings = settings.loadProject(project.path),
            projectSettingsLocal = settings.loadProjectLocal(project.path),
            userSettings = settings.loadUser(),
            agents = agents.loadForProject(project.path),
            commands = commands.loadForProject(project.path),
            skills = skills.loadForProject(project.path),
            outputStyles = outputStyles.loadForProject(project.path),
            hooks = hooks.loadForProject(project.path),
            mcpServers = mcpServers.loadForProject(project.path),
        )
    }
}
