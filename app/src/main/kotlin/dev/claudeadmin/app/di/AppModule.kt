package dev.claudeadmin.app.di

import dev.claudeadmin.data.agent.FileAgentRepository
import dev.claudeadmin.data.claudemd.FileClaudeMdRepository
import dev.claudeadmin.data.command.FileCommandRepository
import dev.claudeadmin.data.git.FileGitRepository
import dev.claudeadmin.data.mcp.FileMcpServerRepository
import dev.claudeadmin.data.opencode.FileOpenCodeMdRepository
import dev.claudeadmin.data.opencode.FileOpenCodeSettingsRepository
import dev.claudeadmin.data.opencode.OpenCodeSessionRepository
import dev.claudeadmin.data.outputstyle.FileOutputStyleRepository
import dev.claudeadmin.data.project.FileProjectGroupRepository
import dev.claudeadmin.data.project.FileProjectRepository
import dev.claudeadmin.data.project.ProjectIconCache
import dev.claudeadmin.data.search.AggregateSessionSearchRepository
import dev.claudeadmin.data.search.ClaudeSessionSearchRepository
import dev.claudeadmin.data.search.OpenCodeSessionSearchRepository
import dev.claudeadmin.data.session.FileClaudeSessionRepository
import dev.claudeadmin.data.settings.FileClaudeSettingsRepository
import dev.claudeadmin.data.skill.FileSkillRepository
import dev.claudeadmin.data.terminal.PtyTerminalRepository
import dev.claudeadmin.domain.repository.AgentRepository
import dev.claudeadmin.domain.repository.AiSessionRepository
import dev.claudeadmin.domain.repository.ClaudeMdRepository
import dev.claudeadmin.domain.repository.ClaudeSessionRepository
import dev.claudeadmin.domain.repository.ClaudeSettingsRepository
import dev.claudeadmin.domain.repository.CommandRepository
import dev.claudeadmin.domain.repository.GitRepository
import dev.claudeadmin.domain.repository.McpServerRepository
import dev.claudeadmin.domain.repository.OpenCodeMdRepository
import dev.claudeadmin.domain.repository.OpenCodeSettingsRepository
import dev.claudeadmin.domain.repository.OutputStyleRepository
import dev.claudeadmin.domain.repository.ProjectGroupRepository
import dev.claudeadmin.domain.repository.ProjectRepository
import dev.claudeadmin.domain.repository.SessionSearchRepository
import dev.claudeadmin.domain.repository.SkillRepository
import dev.claudeadmin.domain.repository.TerminalRepository
import dev.claudeadmin.domain.usecase.AddProjectUseCase
import dev.claudeadmin.domain.usecase.CloseTerminalUseCase
import dev.claudeadmin.domain.usecase.CreateGroupUseCase
import dev.claudeadmin.domain.usecase.LoadProjectDetailsUseCase
import dev.claudeadmin.domain.usecase.MoveGroupUseCase
import dev.claudeadmin.domain.usecase.MoveProjectToGroupUseCase
import dev.claudeadmin.domain.usecase.ObserveProjectGroupsUseCase
import dev.claudeadmin.domain.usecase.ObserveProjectsUseCase
import dev.claudeadmin.domain.usecase.ObserveTerminalsUseCase
import dev.claudeadmin.domain.usecase.OpenTerminalUseCase
import dev.claudeadmin.domain.usecase.RemoveGroupUseCase
import dev.claudeadmin.domain.usecase.RemoveProjectUseCase
import dev.claudeadmin.domain.usecase.RenameGroupUseCase
import dev.claudeadmin.domain.usecase.ReorderProjectsUseCase
import dev.claudeadmin.domain.usecase.SearchSessionsUseCase
import dev.claudeadmin.domain.usecase.SetProjectGitRootUseCase
import dev.claudeadmin.domain.usecase.ToggleGroupCollapsedUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

val appModule = module {
    single<CoroutineScope>(qualifier = org.koin.core.qualifier.named("AppScope")) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    single<ProjectIconCache> { ProjectIconCache() }
    single<ProjectRepository> {
        FileProjectRepository(
            iconCache = get(),
            scope = get(qualifier = org.koin.core.qualifier.named("AppScope")),
        )
    }
    single<ProjectGroupRepository> {
        FileProjectGroupRepository(scope = get(qualifier = org.koin.core.qualifier.named("AppScope")))
    }
    single<ClaudeMdRepository> { FileClaudeMdRepository() }
    single<ClaudeSettingsRepository> { FileClaudeSettingsRepository() }
    single<AgentRepository> { FileAgentRepository() }
    single<CommandRepository> { FileCommandRepository() }
    single<SkillRepository> { FileSkillRepository() }
    single<OutputStyleRepository> { FileOutputStyleRepository() }
    single<McpServerRepository> { FileMcpServerRepository() }
    single<GitRepository> {
        FileGitRepository(scope = get(qualifier = org.koin.core.qualifier.named("AppScope")))
    }

    single<ClaudeSessionRepository> {
        FileClaudeSessionRepository(scope = get(qualifier = org.koin.core.qualifier.named("AppScope")))
    }
    single<AiSessionRepository>(qualifier = org.koin.core.qualifier.named("Claude")) {
        get<ClaudeSessionRepository>()
    }
    single<AiSessionRepository>(qualifier = org.koin.core.qualifier.named("OpenCode")) {
        OpenCodeSessionRepository(scope = get(qualifier = org.koin.core.qualifier.named("AppScope")))
    }
    single<List<AiSessionRepository>> {
        listOf(
            get<AiSessionRepository>(qualifier = org.koin.core.qualifier.named("Claude")),
            get<AiSessionRepository>(qualifier = org.koin.core.qualifier.named("OpenCode")),
        )
    }

    single<SessionSearchRepository> {
        AggregateSessionSearchRepository(
            sources = listOf(
                ClaudeSessionSearchRepository(),
                OpenCodeSessionSearchRepository(),
            ),
        )
    }

    single<OpenCodeMdRepository> { FileOpenCodeMdRepository() }
    single<OpenCodeSettingsRepository> { FileOpenCodeSettingsRepository() }

    single<PtyTerminalRepository> { PtyTerminalRepository() }
    single<TerminalRepository> { get<PtyTerminalRepository>() }

    factory { ObserveProjectsUseCase(get()) }
    factory { ObserveTerminalsUseCase(get()) }
    factory { AddProjectUseCase(get()) }
    factory { RemoveProjectUseCase(get(), get()) }
    factory {
        LoadProjectDetailsUseCase(
            get(), get(), get(), get(), get(), get(), get(), get(),
            get<OpenCodeMdRepository>(), get<OpenCodeSettingsRepository>(),
        )
    }
    factory { OpenTerminalUseCase(get(), get()) }
    factory { CloseTerminalUseCase(get()) }
    factory { SetProjectGitRootUseCase(get()) }
    factory { ReorderProjectsUseCase(get()) }
    factory { ObserveProjectGroupsUseCase(get()) }
    factory { CreateGroupUseCase(get()) }
    factory { RenameGroupUseCase(get()) }
    factory { MoveGroupUseCase(get()) }
    factory { RemoveGroupUseCase(get(), get()) }
    factory { ToggleGroupCollapsedUseCase(get()) }
    factory { MoveProjectToGroupUseCase(get()) }
    factory { SearchSessionsUseCase(get()) }
}
