package dev.claudeadmin.presentation.root

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import dev.claudeadmin.domain.model.AiProvider
import dev.claudeadmin.domain.model.AiSession
import dev.claudeadmin.domain.model.GroupId
import dev.claudeadmin.domain.model.Project
import dev.claudeadmin.domain.model.ProjectId
import dev.claudeadmin.domain.model.TerminalSessionId
import dev.claudeadmin.domain.repository.AiSessionRepository
import dev.claudeadmin.domain.repository.GitRepository
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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class RootComponent(
    componentContext: ComponentContext,
    private val observeProjects: ObserveProjectsUseCase,
    private val observeTerminals: ObserveTerminalsUseCase,
    private val loadDetails: LoadProjectDetailsUseCase,
    private val addProject: AddProjectUseCase,
    private val removeProject: RemoveProjectUseCase,
    private val openTerminal: OpenTerminalUseCase,
    private val closeTerminal: CloseTerminalUseCase,
    private val gitRepository: GitRepository,
    private val setProjectGitRoot: SetProjectGitRootUseCase,
    private val reorderProjects: ReorderProjectsUseCase,
    private val sessionRepositories: List<AiSessionRepository>,
    private val observeGroupsUseCase: ObserveProjectGroupsUseCase,
    private val createGroupUseCase: CreateGroupUseCase,
    private val renameGroupUseCase: RenameGroupUseCase,
    private val moveGroupUseCase: MoveGroupUseCase,
    private val removeGroupUseCase: RemoveGroupUseCase,
    private val toggleGroupCollapsedUseCase: ToggleGroupCollapsedUseCase,
    private val moveProjectToGroupUseCase: MoveProjectToGroupUseCase,
    private val searchSessionsUseCase: SearchSessionsUseCase,
) : ComponentContext by componentContext {

    private val scope: CoroutineScope = coroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private val _state = MutableStateFlow(RootState())
    val state: StateFlow<RootState> = _state.asStateFlow()

    private val searchQueryFlow = MutableStateFlow("")

    private var detailsLoad: Job? = null
    private val gitJobs = mutableMapOf<ProjectId, GitSubscription>()

    private data class GitSubscription(val path: String, val job: Job)

    @Volatile
    private var allSessions: List<AiSession> = emptyList()

    init {
        scope.launch {
            observeProjects().collect { list ->
                applyProjectsAndGrouping(list, allSessions)
                syncGitSubscriptions(list)
            }
        }
        scope.launch { observeTerminals.all().collect { list -> _state.update { it.copy(terminals = list) } } }
        scope.launch { observeGroupsUseCase().collect { list -> _state.update { it.copy(groups = list) } } }

        val sessionFlows = sessionRepositories.map { it.observeAll() }
        scope.launch {
            if (sessionFlows.isEmpty()) return@launch
            combine(sessionFlows) { arrays ->
                arrays.flatMap { it.toList() }
            }.collect { sessions ->
                allSessions = sessions.sortedByDescending { it.lastModified }
                applyProjectsAndGrouping(_state.value.projects, allSessions)
            }
        }

        scope.launch {
            searchQueryFlow
                .debounce(SEARCH_DEBOUNCE_MS)
                .collectLatest { query -> runSearch(query) }
        }
    }

    private suspend fun runSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _state.update { it.copy(searchInProgress = false, searchResults = emptyList()) }
            return
        }
        _state.update { it.copy(searchInProgress = true) }
        val hits = runCatching { searchSessionsUseCase(trimmed) }.getOrDefault(emptyList())
        if (searchQueryFlow.value.trim() != trimmed) return
        _state.update { it.copy(searchInProgress = false, searchResults = hits) }
    }

    private fun applyProjectsAndGrouping(projects: List<Project>, sessions: List<AiSession>) {
        val sortedProjects = projects.sortedByDescending { it.path.length }
        val tracked = HashMap<ProjectId, MutableList<AiSession>>()
        val orphans = LinkedHashMap<String, MutableList<AiSession>>()
        for (session in sessions) {
            val matched = sortedProjects.firstOrNull { project ->
                session.cwd == project.path || session.cwd.startsWith(project.path + "/")
            }
            if (matched != null) {
                tracked.getOrPut(matched.id) { mutableListOf() }.add(session)
            } else {
                orphans.getOrPut(session.cwd) { mutableListOf() }.add(session)
            }
        }
        val cappedTracked = tracked.mapValues { (_, v) -> v.take(SESSIONS_PER_BUCKET) }
        val cappedOrphans = orphans.mapValues { (_, v) -> v.take(SESSIONS_PER_BUCKET) }
        val previewById = sessions.associate { it.id to it.preview }
        _state.update {
            it.copy(
                projects = projects,
                savedSessionsByProject = cappedTracked,
                orphanSessionsByCwd = cappedOrphans,
                sessionPreviewById = previewById,
            )
        }
    }

    private fun syncGitSubscriptions(projects: List<Project>) {
        val active = projects.associateBy { it.id }
        val toRemove = gitJobs.keys - active.keys
        toRemove.forEach { id ->
            gitJobs.remove(id)?.job?.cancel()
            _state.update {
                it.copy(
                    gitByProject = it.gitByProject - id,
                    gitRootPrompts = it.gitRootPrompts - id,
                )
            }
        }
        active.values.forEach { project ->
            val effectivePath = project.gitRoot ?: project.path
            val existing = gitJobs[project.id]
            if (existing != null && existing.path == effectivePath) return@forEach
            existing?.job?.cancel()
            val job = scope.launch {
                gitRepository.observe(effectivePath).collect { status ->
                    _state.update { it.copy(gitByProject = it.gitByProject + (project.id to status)) }
                }
            }
            gitJobs[project.id] = GitSubscription(effectivePath, job)
        }
    }

    fun selectProject(id: ProjectId) {
        _state.update { it.copy(selection = Selection.Details(id), addProjectError = null) }
        loadDetailsFor(id)
    }

    fun selectTerminal(projectId: ProjectId?, terminalId: TerminalSessionId) {
        _state.update { it.copy(selection = Selection.Terminal(projectId, terminalId)) }
    }

    fun addProject(path: String, name: String? = null) {
        scope.launch {
            addProject.invoke(path, name).fold(
                onSuccess = { project: Project ->
                    _state.update { it.copy(addProjectError = null) }
                    selectProject(project.id)
                    detectGitRootMissing(project)
                },
                onFailure = { t -> _state.update { it.copy(addProjectError = t.message) } },
            )
        }
    }

    private fun detectGitRootMissing(project: Project) {
        scope.launch {
            val status = gitRepository.observe(project.path).first()
            if (status == null) {
                _state.update { it.copy(gitRootPrompts = it.gitRootPrompts + project.id) }
            }
        }
    }

    fun setGitRoot(id: ProjectId, gitRoot: String?) {
        scope.launch {
            setProjectGitRoot.invoke(id, gitRoot)
            _state.update { it.copy(gitRootPrompts = it.gitRootPrompts - id) }
        }
    }

    fun dismissGitRootPrompt(id: ProjectId) {
        _state.update { it.copy(gitRootPrompts = it.gitRootPrompts - id) }
    }

    fun reorderProjects(movingId: ProjectId, targetId: ProjectId) {
        if (movingId == targetId) return
        val current = _state.value.projects
        val moving = current.firstOrNull { it.id == movingId } ?: return
        val target = current.firstOrNull { it.id == targetId } ?: return
        if (moving.groupId != target.groupId) return

        val siblings = current.filter { it.groupId == moving.groupId }
        val siblingFromIdx = siblings.indexOfFirst { it.id == movingId }
        val siblingTargetIdx = siblings.indexOfFirst { it.id == targetId }
        if (siblingFromIdx < 0 || siblingTargetIdx < 0) return

        val newSiblingOrder = siblings.toMutableList()
        val moved = newSiblingOrder.removeAt(siblingFromIdx)
        newSiblingOrder.add(siblingTargetIdx, moved)

        // Re-stitch: keep foreign-group projects on their absolute positions,
        // replace sibling slots with the new sibling order in sequence.
        val siblingQueue = ArrayDeque(newSiblingOrder)
        val finalOrder = current.map { p ->
            if (p.groupId == moving.groupId) siblingQueue.removeFirst().id else p.id
        }
        scope.launch { reorderProjects.invoke(finalOrder) }
    }

    fun removeProject(id: ProjectId) {
        scope.launch {
            removeProject.invoke(id)
            _state.update { s ->
                if (s.selection?.projectId == id) s.copy(selection = null, details = DetailsState.Empty) else s
            }
        }
    }

    fun requestOpenTerminal(id: ProjectId) {
        _state.update { it.copy(pendingTerminalProvider = id) }
    }

    fun openTerminal(id: ProjectId, provider: AiProvider) {
        scope.launch {
            openTerminal.invoke(id, provider = provider).onSuccess { session ->
                _state.update { it.copy(selection = Selection.Terminal(id, session.id), pendingTerminalProvider = null) }
            }
        }
    }

    fun cancelOpenTerminal() {
        _state.update { it.copy(pendingTerminalProvider = null) }
    }

    fun resumeAiSession(projectId: ProjectId, sessionId: String, provider: AiProvider) {
        scope.launch {
            openTerminal.invoke(projectId, resumeSessionId = sessionId, provider = provider).onSuccess { session ->
                _state.update { it.copy(selection = Selection.Terminal(projectId, session.id)) }
            }
        }
    }

    fun resumeOrphanSession(cwd: String, sessionId: String, provider: AiProvider) {
        scope.launch {
            openTerminal.openDetached(cwd, resumeSessionId = sessionId, provider = provider).onSuccess { session ->
                _state.update { it.copy(selection = Selection.Terminal(null, session.id)) }
            }
        }
    }

    fun addProjectFromOrphan(cwd: String) {
        scope.launch { addProject.invoke(cwd, null) }
    }

    fun closeTerminal(id: TerminalSessionId) {
        scope.launch {
            closeTerminal.invoke(id)
            _state.update { s ->
                val sel = s.selection
                if (sel is Selection.Terminal && sel.terminalId == id) {
                    val projectId = sel.projectId
                    s.copy(selection = projectId?.let { Selection.Details(it) })
                } else s
            }
        }
    }

    fun dismissAddProjectError() {
        _state.update { it.copy(addProjectError = null) }
    }

    fun createGroup(name: String, parentId: GroupId? = null) {
        scope.launch { createGroupUseCase(name, parentId) }
    }

    fun renameGroup(id: GroupId, name: String) {
        scope.launch { renameGroupUseCase(id, name) }
    }

    fun moveGroup(id: GroupId, newParentId: GroupId?) {
        scope.launch { moveGroupUseCase(id, newParentId) }
    }

    fun removeGroup(id: GroupId) {
        scope.launch { removeGroupUseCase(id) }
    }

    fun toggleGroupCollapsed(id: GroupId, collapsed: Boolean) {
        scope.launch { toggleGroupCollapsedUseCase(id, collapsed) }
    }

    fun moveProjectToGroup(projectId: ProjectId, groupId: GroupId?) {
        scope.launch { moveProjectToGroupUseCase(projectId, groupId) }
    }

    fun setSearchQuery(query: String) {
        _state.update {
            it.copy(
                searchQuery = query,
                searchInProgress = query.isNotBlank(),
            )
        }
        searchQueryFlow.value = query
    }

    fun clearSearch() {
        setSearchQuery("")
    }

    private companion object {
        const val SESSIONS_PER_BUCKET = 50
        const val SEARCH_DEBOUNCE_MS = 200L
    }

    private fun loadDetailsFor(id: ProjectId) {
        detailsLoad?.cancel()
        detailsLoad = scope.launch {
            _state.update { it.copy(details = DetailsState.Loading) }
            val result = runCatching { loadDetails.invoke(id) }
            _state.update {
                it.copy(
                    details = result.fold(
                        onSuccess = { details ->
                            if (details == null) DetailsState.Empty else DetailsState.Loaded(details)
                        },
                        onFailure = { t -> DetailsState.Error(t.message ?: "Failed to load") },
                    ),
                )
            }
        }
    }
}
