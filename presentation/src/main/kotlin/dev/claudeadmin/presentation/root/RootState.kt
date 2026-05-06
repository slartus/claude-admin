package dev.claudeadmin.presentation.root

import dev.claudeadmin.domain.model.AiProvider
import dev.claudeadmin.domain.model.AiSession
import dev.claudeadmin.domain.model.GitStatus
import dev.claudeadmin.domain.model.Project
import dev.claudeadmin.domain.model.ProjectDetails
import dev.claudeadmin.domain.model.ProjectGroup
import dev.claudeadmin.domain.model.ProjectId
import dev.claudeadmin.domain.model.SessionSearchHit
import dev.claudeadmin.domain.model.TerminalSession
import dev.claudeadmin.domain.model.TerminalSessionId

data class RootState(
    val projects: List<Project> = emptyList(),
    val groups: List<ProjectGroup> = emptyList(),
    val terminals: List<TerminalSession> = emptyList(),
    val selection: Selection? = null,
    val details: DetailsState = DetailsState.Empty,
    val addProjectError: String? = null,
    val gitByProject: Map<ProjectId, GitStatus?> = emptyMap(),
    val gitRootPrompts: List<ProjectId> = emptyList(),
    val savedSessionsByProject: Map<ProjectId, List<AiSession>> = emptyMap(),
    val orphanSessionsByCwd: Map<String, List<AiSession>> = emptyMap(),
    val sessionPreviewById: Map<String, String> = emptyMap(),
    val pendingTerminalProvider: ProjectId? = null,
    val searchQuery: String = "",
    val searchInProgress: Boolean = false,
    val searchResults: List<SessionSearchHit> = emptyList(),
) {

    val isSearchActive: Boolean get() = searchQuery.isNotBlank()

    val terminalsByProject: Map<ProjectId, List<TerminalSession>>
        get() = terminals.asSequence()
            .mapNotNull { t -> t.projectId?.let { it to t } }
            .groupBy({ it.first }, { it.second })

    val detachedTerminalBySessionId: Map<String, TerminalSession>
        get() = terminals.asSequence()
            .filter { it.projectId == null }
            .mapNotNull { t -> t.aiSessionId?.let { it to t } }
            .toMap()

    val visibleSavedSessionsByProject: Map<ProjectId, List<AiSession>>
        get() {
            val active = activeTrackedSessionIds()
            return savedSessionsByProject.mapValues { (_, list) ->
                list.filterNot { it.id in active }
            }
        }

    private fun activeTrackedSessionIds(): Set<String> =
        terminals.asSequence()
            .filter { it.projectId != null }
            .mapNotNullTo(hashSetOf()) { it.aiSessionId }
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
