package dev.claudeadmin.app.di

import dev.claudeadmin.data.agent.FileAgentRepository
import dev.claudeadmin.data.claudemd.FileClaudeMdRepository
import dev.claudeadmin.data.command.FileCommandRepository
import dev.claudeadmin.data.project.FileProjectRepository
import dev.claudeadmin.data.settings.FileClaudeSettingsRepository
import dev.claudeadmin.data.terminal.PtyTerminalRepository
import dev.claudeadmin.domain.repository.AgentRepository
import dev.claudeadmin.domain.repository.ClaudeMdRepository
import dev.claudeadmin.domain.repository.ClaudeSettingsRepository
import dev.claudeadmin.domain.repository.CommandRepository
import dev.claudeadmin.domain.repository.ProjectRepository
import dev.claudeadmin.domain.repository.TerminalRepository
import dev.claudeadmin.domain.usecase.AddProjectUseCase
import dev.claudeadmin.domain.usecase.CloseTerminalUseCase
import dev.claudeadmin.domain.usecase.LoadProjectDetailsUseCase
import dev.claudeadmin.domain.usecase.ObserveProjectsUseCase
import dev.claudeadmin.domain.usecase.ObserveTerminalsUseCase
import dev.claudeadmin.domain.usecase.OpenTerminalUseCase
import dev.claudeadmin.domain.usecase.RemoveProjectUseCase
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
    single<PtyTerminalRepository> { PtyTerminalRepository() }
    single<TerminalRepository> { get<PtyTerminalRepository>() }

    factory { ObserveProjectsUseCase(get()) }
    factory { ObserveTerminalsUseCase(get()) }
    factory { AddProjectUseCase(get()) }
    factory { RemoveProjectUseCase(get(), get()) }
    factory { LoadProjectDetailsUseCase(get(), get(), get(), get(), get()) }
    factory { OpenTerminalUseCase(get(), get()) }
    factory { CloseTerminalUseCase(get()) }
}
