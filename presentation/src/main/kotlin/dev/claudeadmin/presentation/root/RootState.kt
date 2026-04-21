package dev.claudeadmin.presentation.root

import dev.claudeadmin.domain.model.AgentStatusEntry
import dev.claudeadmin.domain.model.ClaudeSession
import dev.claudeadmin.domain.model.GitStatus
import dev.claudeadmin.domain.model.HookInstallState
import dev.claudeadmin.domain.model.Project
import dev.claudeadmin.domain.model.ProjectDetails
import dev.claudeadmin.domain.model.ProjectId
import dev.claudeadmin.domain.model.TerminalSession
import dev.claudeadmin.domain.model.TerminalSessionId

data class RootState(
    val projects: List<Project> = emptyList(),
    val terminals: List<TerminalSession> = emptyList(),
    val selection: Selection? = null,
    val details: DetailsState = DetailsState.Empty,
    val addProjectError: String? = null,
    val gitByProject: Map<ProjectId, GitStatus?> = emptyMap(),
    val gitRootPrompts: List<ProjectId> = emptyList(),
    val hookInstallState: HookInstallState = HookInstallState.Unknown,
    val hookBannerDismissed: Boolean = false,
    val hookInstallInProgress: Boolean = false,
    val agentStatusBySessionId: Map<String, AgentStatusEntry> = emptyMap(),
    val savedSessionsByProject: Map<ProjectId, List<ClaudeSession>> = emptyMap(),
    val orphanSessionsByCwd: Map<String, List<ClaudeSession>> = emptyMap(),
) {
    val terminalsByProject: Map<ProjectId, List<TerminalSession>>
        get() = terminals.groupBy { it.projectId }

    val visibleSavedSessionsByProject: Map<ProjectId, List<ClaudeSession>>
        get() {
            val active = activeSessionIds()
            return savedSessionsByProject.mapValues { (_, list) ->
                list.filterNot { it.id in active }
            }
        }

    val visibleOrphanSessionsByCwd: Map<String, List<ClaudeSession>>
        get() {
            val active = activeSessionIds()
            return orphanSessionsByCwd
                .mapValues { (_, list) -> list.filterNot { it.id in active } }
                .filterValues { it.isNotEmpty() }
        }

    private fun activeSessionIds(): Set<String> =
        terminals.mapNotNullTo(hashSetOf()) { it.claudeSessionId }
}

sealed interface Selection {
    val projectId: ProjectId

    data class Details(override val projectId: ProjectId) : Selection
    data class Terminal(
        override val projectId: ProjectId,
        val terminalId: TerminalSessionId,
    ) : Selection
}

sealed interface DetailsState {
    data object Empty : DetailsState
    data object Loading : DetailsState
    data class Loaded(val details: ProjectDetails) : DetailsState
    data class Error(val message: String) : DetailsState
}
