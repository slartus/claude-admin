package dev.claudeadmin.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.claudeadmin.app.ui.details.DetailsView
import dev.claudeadmin.app.ui.details.WelcomeView
import dev.claudeadmin.app.ui.sidebar.Sidebar
import dev.claudeadmin.app.ui.sidebar.TerminalProviderDialog
import dev.claudeadmin.app.ui.terminal.TerminalView
import dev.claudeadmin.app.ui.util.openInDefaultApp
import dev.claudeadmin.app.ui.util.revealInFinder
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import dev.claudeadmin.app.ui.terminal.TerminalWidgetCache
import dev.claudeadmin.data.terminal.PtyTerminalRepository
import dev.claudeadmin.domain.model.AiProvider
import dev.claudeadmin.domain.repository.ClaudeUserSettingsRepository
import dev.claudeadmin.presentation.root.PendingResume
import dev.claudeadmin.presentation.root.RootComponent
import dev.claudeadmin.presentation.root.Selection
import org.koin.compose.koinInject

@Composable
fun RootScreen(
    component: RootComponent,
    ptyRepo: PtyTerminalRepository,
) {
    val state by component.state.collectAsState()
    val settingsRepo = koinInject<ClaudeUserSettingsRepository>()
    val claudeUserSettings = settingsRepo.list()

    LaunchedEffect(state.terminals) {
        TerminalWidgetCache.retainOnly(state.terminals.map { it.id }.toSet())
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Sidebar(
                    modifier = Modifier.width(300.dp),
                    state = state,
                    onAddProject = component::addProject,
                    onSelectProject = component::selectProject,
                    onRemoveProject = component::removeProject,
                    onReorderProjects = component::reorderProjects,
                    onRequestOpenTerminal = component::requestOpenTerminal,
                    onSelectTerminal = component::selectTerminal,
                    onCloseTerminal = component::closeTerminal,
                    onRequestResumeSession = component::requestResumeSession,
                    onAddProjectFromOrphan = component::addProjectFromOrphan,
                    onDismissError = component::dismissAddProjectError,
                    onSetGitRoot = component::setGitRoot,
                    onDismissGitRootPrompt = component::dismissGitRootPrompt,
                    onCreateGroup = component::createGroup,
                    onRenameGroup = component::renameGroup,
                    onMoveGroup = component::moveGroup,
                    onRemoveGroup = component::removeGroup,
                    onToggleGroupCollapsed = component::toggleGroupCollapsed,
                    onMoveProjectToGroup = component::moveProjectToGroup,
                    onSearchQueryChange = component::setSearchQuery,
                    onClearSearch = component::clearSearch,
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    if (state.pendingTerminalProvider == null && state.pendingResume == null) {
                        when (val sel = state.selection) {
                            null -> WelcomeView()
                            is Selection.Details -> DetailsView(
                                state = state.details,
                                onOpenFile = ::openInDefaultApp,
                                onRevealInFinder = ::revealInFinder,
                            )
                            is Selection.Terminal -> TerminalView(sessionId = sel.terminalId, ptyRepo = ptyRepo)
                        }
                    }
                }
            }
            StatusBar(
                terminals = state.terminals,
                projects = state.projects,
                ptyRepo = ptyRepo,
            )
        }
    }

    state.pendingTerminalProvider?.let { projectId ->
        TerminalProviderDialog(
            claudeUserSettings = claudeUserSettings,
            onResult = { provider, settingsPath ->
                component.openTerminal(projectId, provider, settingsPath)
            },
            onDismiss = { component.cancelOpenTerminal() },
        )
    }

    state.pendingResume?.let { resume ->
        when (resume.provider) {
            AiProvider.CLAUDE -> {
                TerminalProviderDialog(
                    claudeUserSettings = claudeUserSettings,
                    showOpenCode = false,
                    onResult = { _, settingsPath ->
                        when (resume) {
                            is PendingResume.ProjectSession ->
                                component.resumeAiSession(resume.projectId, resume.sessionId, AiProvider.CLAUDE, settingsPath)
                            is PendingResume.OrphanSession ->
                                component.resumeOrphanSession(resume.cwd, resume.sessionId, AiProvider.CLAUDE, settingsPath)
                        }
                    },
                    onDismiss = { component.cancelResume() },
                )
            }
            AiProvider.OPENCODE -> {
                LaunchedEffect(resume) {
                    when (resume) {
                        is PendingResume.ProjectSession ->
                            component.resumeAiSession(resume.projectId, resume.sessionId, AiProvider.OPENCODE)
                        is PendingResume.OrphanSession ->
                            component.resumeOrphanSession(resume.cwd, resume.sessionId, AiProvider.OPENCODE)
                    }
                }
            }
        }
    }
}
