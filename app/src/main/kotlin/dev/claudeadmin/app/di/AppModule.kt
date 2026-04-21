package dev.claudeadmin.app.di

import dev.claudeadmin.data.agent.FileAgentRepository
import dev.claudeadmin.data.claudemd.FileClaudeMdRepository
import dev.claudeadmin.data.command.FileCommandRepository
import dev.claudeadmin.data.git.FileGitRepository
import dev.claudeadmin.data.hooks.FileAgentStatusRepository
import dev.claudeadmin.data.hooks.FileHookInstaller
import dev.claudeadmin.data.hooks.FileHookRepository
import dev.claudeadmin.data.mcp.FileMcpServerRepository
import dev.claudeadmin.data.outputstyle.FileOutputStyleRepository
import dev.claudeadmin.data.project.FileProjectRepository
import dev.claudeadmin.data.session.FileClaudeSessionRepository
import dev.claudeadmin.data.settings.FileClaudeSettingsRepository
import dev.claudeadmin.data.skill.FileSkillRepository
import dev.claudeadmin.data.terminal.PtyTerminalRepository
import dev.claudeadmin.domain.repository.AgentRepository
import dev.claudeadmin.domain.repository.AgentStatusRepository
import dev.claudeadmin.domain.repository.ClaudeMdRepository
import dev.claudeadmin.domain.repository.ClaudeSessionRepository
import dev.claudeadmin.domain.repository.ClaudeSettingsRepository
import dev.claudeadmin.domain.repository.CommandRepository
import dev.claudeadmin.domain.repository.GitRepository
import dev.claudeadmin.domain.repository.HookInstallerRepository
import dev.claudeadmin.domain.repository.HookRepository
import dev.claudeadmin.domain.repository.McpServerRepository
import dev.claudeadmin.domain.repository.OutputStyleRepository
import dev.claudeadmin.domain.repository.ProjectRepository
import dev.claudeadmin.domain.repository.SkillRepository
import dev.claudeadmin.domain.repository.TerminalRepository
import dev.claudeadmin.domain.usecase.AddProjectUseCase
import dev.claudeadmin.domain.usecase.CloseTerminalUseCase
import dev.claudeadmin.domain.usecase.LoadProjectDetailsUseCase
import dev.claudeadmin.domain.usecase.ObserveProjectsUseCase
import dev.claudeadmin.domain.usecase.ObserveTerminalsUseCase
import dev.claudeadmin.domain.usecase.OpenTerminalUseCase
import dev.claudeadmin.domain.usecase.RemoveProjectUseCase
import dev.claudeadmin.domain.usecase.SetProjectGitRootUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

val appModule = module {
    single<CoroutineScope>(qualifier = org.koin.core.qualifier.named("AppScope")) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    single<ProjectRepository> {
        FileProjectRepository(scope = get(qualifier = org.koin.core.qualifier.named("AppScope")))
    }
    single<ClaudeMdRepository> { FileClaudeMdRepository() }
    single<ClaudeSettingsRepository> { FileClaudeSettingsRepository() }
    single<AgentRepository> { FileAgentRepository() }
    single<CommandRepository> { FileCommandRepository() }
    single<SkillRepository> { FileSkillRepository() }
    single<OutputStyleRepository> { FileOutputStyleRepository() }
    single<HookRepository> { FileHookRepository() }
    single<McpServerRepository> { FileMcpServerRepository() }
    single<GitRepository> {
        FileGitRepository(scope = get(qualifier = org.koin.core.qualifier.named("AppScope")))
    }
    single<ClaudeSessionRepository> {
        FileClaudeSessionRepository(scope = get(qualifier = org.koin.core.qualifier.named("AppScope")))
    }
    single<PtyTerminalRepository> { PtyTerminalRepository() }
    single<TerminalRepository> { get<PtyTerminalRepository>() }
    single<HookInstallerRepository> { FileHookInstaller() }
    single<AgentStatusRepository> {
        FileAgentStatusRepository(scope = get(qualifier = org.koin.core.qualifier.named("AppScope")))
    }

    factory { ObserveProjectsUseCase(get()) }
    factory { ObserveTerminalsUseCase(get()) }
    factory { AddProjectUseCase(get()) }
    factory { RemoveProjectUseCase(get(), get()) }
    factory { LoadProjectDetailsUseCase(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { OpenTerminalUseCase(get(), get()) }
    factory { CloseTerminalUseCase(get()) }
    factory { SetProjectGitRootUseCase(get()) }
}
