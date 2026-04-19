package dev.claudeadmin.presentation.root

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import dev.claudeadmin.domain.model.Project
import dev.claudeadmin.domain.model.ProjectId
import dev.claudeadmin.domain.model.TerminalSessionId
import dev.claudeadmin.domain.usecase.AddProjectUseCase
import dev.claudeadmin.domain.usecase.CloseTerminalUseCase
import dev.claudeadmin.domain.usecase.LoadProjectDetailsUseCase
import dev.claudeadmin.domain.usecase.ObserveProjectsUseCase
import dev.claudeadmin.domain.usecase.ObserveTerminalsUseCase
import dev.claudeadmin.domain.usecase.OpenTerminalUseCase
import dev.claudeadmin.domain.usecase.RemoveProjectUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) : ComponentContext by componentContext {

    private val scope: CoroutineScope = coroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private val _state = MutableStateFlow(RootState())
    val state: StateFlow<RootState> = _state.asStateFlow()

    private var detailsLoad: Job? = null

    init {
        scope.launch { observeProjects().collect { list -> _state.update { it.copy(projects = list) } } }
        scope.launch { observeTerminals.all().collect { list -> _state.update { it.copy(terminals = list) } } }
    }

    fun selectProject(id: ProjectId) {
        _state.update { it.copy(selection = Selection.Details(id), addProjectError = null) }
        loadDetailsFor(id)
    }

    fun selectTerminal(projectId: ProjectId, terminalId: TerminalSessionId) {
        _state.update { it.copy(selection = Selection.Terminal(projectId, terminalId)) }
    }

    fun addProject(path: String, name: String? = null) {
        scope.launch {
            addProject.invoke(path, name).fold(
                onSuccess = { project: Project ->
                    _state.update { it.copy(addProjectError = null) }
                    selectProject(project.id)
                },
                onFailure = { t -> _state.update { it.copy(addProjectError = t.message) } },
            )
        }
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

    fun closeTerminal(id: TerminalSessionId) {
        scope.launch {
            closeTerminal.invoke(id)
            _state.update { s ->
                val sel = s.selection
                if (sel is Selection.Terminal && sel.terminalId == id) {
                    s.copy(selection = Selection.Details(sel.projectId))
                } else s
            }
        }
    }

    fun dismissAddProjectError() {
        _state.update { it.copy(addProjectError = null) }
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
