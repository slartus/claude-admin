package dev.claudeadmin.presentation.root

import dev.claudeadmin.domain.model.GitStatus
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
) {
    val terminalsByProject: Map<ProjectId, List<TerminalSession>>
        get() = terminals.groupBy { it.projectId }
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
