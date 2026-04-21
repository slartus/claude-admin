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
    val sessionPreviewById: Map<String, String> = emptyMap(),
) {
    val terminalsByProject: Map<ProjectId, List<TerminalSession>>
        get() = terminals.asSequence()
            .mapNotNull { t -> t.projectId?.let { it to t } }
            .groupBy({ it.first }, { it.second })

    val detachedTerminalBySessionId: Map<String, TerminalSession>
        get() = terminals.asSequence()
            .filter { it.projectId == null }
            .mapNotNull { t -> t.claudeSessionId?.let { it to t } }
            .toMap()

    val visibleSavedSessionsByProject: Map<ProjectId, List<ClaudeSession>>
        get() {
            val active = activeTrackedSessionIds()
            return savedSessionsByProject.mapValues { (_, list) ->
                list.filterNot { it.id in active }
            }
        }

    private fun activeTrackedSessionIds(): Set<String> =
        terminals.asSequence()
            .filter { it.projectId != null }
            .mapNotNullTo(hashSetOf()) { it.claudeSessionId }
}

sealed interface Selection {
    val projectId: ProjectId?

    data class Details(override val projectId: ProjectId) : Selection
    data class Terminal(
        override val projectId: ProjectId?,
        val terminalId: TerminalSessionId,
    ) : Selection
}

sealed interface DetailsState {
    data object Empty : DetailsState
    data object Loading : DetailsState
    data class Loaded(val details: ProjectDetails) : DetailsState
    data class Error(val message: String) : DetailsState
}
