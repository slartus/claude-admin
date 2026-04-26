package dev.claudeadmin.presentation.root

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import dev.claudeadmin.domain.model.ClaudeSession
import dev.claudeadmin.domain.model.Project
import dev.claudeadmin.domain.model.ProjectId
import dev.claudeadmin.domain.model.TerminalSessionId
import dev.claudeadmin.domain.repository.ClaudeSessionRepository
import dev.claudeadmin.domain.repository.GitRepository
import dev.claudeadmin.domain.usecase.AddProjectUseCase
import dev.claudeadmin.domain.usecase.CloseTerminalUseCase
import dev.claudeadmin.domain.usecase.LoadProjectDetailsUseCase
import dev.claudeadmin.domain.usecase.ObserveProjectsUseCase
import dev.claudeadmin.domain.usecase.ObserveTerminalsUseCase
import dev.claudeadmin.domain.usecase.OpenTerminalUseCase
import dev.claudeadmin.domain.usecase.RemoveProjectUseCase
import dev.claudeadmin.domain.usecase.ReorderProjectsUseCase
import dev.claudeadmin.domain.usecase.SetProjectGitRootUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    private val claudeSessionRepository: ClaudeSessionRepository,
) : ComponentContext by componentContext {

    private val scope: CoroutineScope = coroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private val _state = MutableStateFlow(RootState())
    val state: StateFlow<RootState> = _state.asStateFlow()

    private var detailsLoad: Job? = null
    private val gitJobs = mutableMapOf<ProjectId, GitSubscription>()

    private data class GitSubscription(val path: String, val job: Job)

    @Volatile
    private var allSessions: List<ClaudeSession> = emptyList()

    init {
        scope.launch {
            observeProjects().collect { list ->
                applyProjectsAndGrouping(list, allSessions)
                syncGitSubscriptions(list)
            }
        }
        scope.launch { observeTerminals.all().collect { list -> _state.update { it.copy(terminals = list) } } }
        scope.launch {
            claudeSessionRepository.observeAll().collect { sessions ->
                allSessions = sessions
                applyProjectsAndGrouping(_state.value.projects, sessions)
            }
        }
    }

    private fun applyProjectsAndGrouping(projects: List<Project>, sessions: List<ClaudeSession>) {
        val sortedProjects = projects.sortedByDescending { it.path.length }
        val tracked = HashMap<ProjectId, MutableList<ClaudeSession>>()
        val orphans = LinkedHashMap<String, MutableList<ClaudeSession>>()
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
        val fromIdx = current.indexOfFirst { it.id == movingId }
        val targetIdx = current.indexOfFirst { it.id == targetId }
        if (fromIdx < 0 || targetIdx < 0) return
        val ids = current.map { it.id }.toMutableList()
        ids.removeAt(fromIdx)
        ids.add(targetIdx, movingId)
        scope.launch { reorderProjects.invoke(ids) }
    }

    fun removeProject(id: ProjectId) {
        scope.launch {
            removeProject.invoke(id)
            _state.update { s ->
                if (s.selection?.projectId == id) s.copy(selection = null, details = DetailsState.Empty) else s
            }
        }
    }

    fun openTerminal(id: ProjectId) {
        scope.launch {
            openTerminal.invoke(id).onSuccess { session ->
                _state.update { it.copy(selection = Selection.Terminal(id, session.id)) }
            }
        }
    }

    fun resumeClaudeSession(projectId: ProjectId, sessionId: String) {
        scope.launch {
            openTerminal.invoke(projectId, resumeSessionId = sessionId).onSuccess { session ->
                _state.update { it.copy(selection = Selection.Terminal(projectId, session.id)) }
            }
        }
    }

    fun resumeOrphanSession(cwd: String, sessionId: String) {
        scope.launch {
            openTerminal.openDetached(cwd, resumeSessionId = sessionId).onSuccess { session ->
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

    private companion object {
        const val SESSIONS_PER_BUCKET = 50
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
