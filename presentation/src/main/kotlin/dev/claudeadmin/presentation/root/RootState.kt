package dev.claudeadmin.presentation.root

import dev.claudeadmin.domain.model.AiProvider
import dev.claudeadmin.domain.model.AiSession
import dev.claudeadmin.domain.model.GitStatus
import dev.claudeadmin.domain.model.GroupId
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
    val pendingResume: PendingResume? = null,
    val searchQuery: String = "",
    val searchInProgress: Boolean = false,
    val searchResults: List<SessionSearchHit> = emptyList(),
    val showOnlyActive: Boolean = false,
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

    val sidebarTerminalsByProject: Map<ProjectId, List<TerminalSession>>
        get() {
            val detached = terminals.filter { it.projectId == null }
            if (detached.isEmpty()) return terminalsByProject
            val projectsByPathLength = projects.sortedByDescending { it.path.length }
            val result = terminalsByProject.toMutableMap()
            detached.forEach { terminal ->
                val owner = projectsByPathLength.firstOrNull { project ->
                    terminal.cwd == project.path || terminal.cwd.startsWith(project.path + "/")
                } ?: return@forEach
                result[owner.id] = result[owner.id].orEmpty() + terminal
            }
            return result
        }

    val visibleSavedSessionsByProject: Map<ProjectId, List<AiSession>>
        get() {
            val active = activeTrackedSessionIds()
            return savedSessionsByProject.mapValues { (_, list) ->
                list.filterNot { it.id in active }
            }
        }

    val sidebarProjects: List<Project>
        get() {
            if (!showOnlyActive) return projects
            val withTerminals = sidebarTerminalsByProject.keys
            return projects.filter { it.id in withTerminals }
        }

    val sidebarGroups: List<ProjectGroup>
        get() {
            if (!showOnlyActive) return groups
            val validIds = groups.mapTo(HashSet()) { it.id }
            val parentById = groups.associate { it.id to it.parentId }
            val keep = HashSet<GroupId>()
            sidebarProjects.forEach { project ->
                var current = project.groupId?.takeIf { it in validIds }
                while (current != null && keep.add(current)) {
                    current = parentById[current]
                }
            }
            return groups.filter { it.id in keep }
        }

    val sidebarSavedSessionsByProject: Map<ProjectId, List<AiSession>>
        get() = if (showOnlyActive) emptyMap() else visibleSavedSessionsByProject

    val sidebarOrphanSessionsByCwd: Map<String, List<AiSession>>
        get() {
            if (!showOnlyActive) return orphanSessionsByCwd
            val running = detachedTerminalBySessionId.keys
            return orphanSessionsByCwd
                .mapValues { (_, sessions) -> sessions.filter { it.id in running } }
                .filterValues { it.isNotEmpty() }
        }

    private fun activeTrackedSessionIds(): Set<String> =
        terminals.mapNotNullTo(hashSetOf()) { it.aiSessionId }
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

sealed interface PendingResume {
    val sessionId: String
    val provider: AiProvider

    data class ProjectSession(
        val projectId: ProjectId,
        override val sessionId: String,
        override val provider: AiProvider,
    ) : PendingResume

    data class OrphanSession(
        val cwd: String,
        override val sessionId: String,
        override val provider: AiProvider,
    ) : PendingResume
}
