package dev.claudeadmin.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import dev.claudeadmin.app.ui.terminal.TerminalView
import dev.claudeadmin.app.ui.util.openInDefaultApp
import dev.claudeadmin.app.ui.util.revealInFinder
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import dev.claudeadmin.app.ui.terminal.TerminalWidgetCache
import dev.claudeadmin.data.terminal.PtyTerminalRepository
import dev.claudeadmin.presentation.root.RootComponent
import dev.claudeadmin.presentation.root.Selection

@Composable
fun RootScreen(
    component: RootComponent,
    ptyRepo: PtyTerminalRepository,
) {
    val state by component.state.collectAsState()

    LaunchedEffect(state.terminals) {
        TerminalWidgetCache.retainOnly(state.terminals.map { it.id }.toSet())
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Row(modifier = Modifier.fillMaxSize()) {
            Sidebar(
                modifier = Modifier.width(300.dp),
                state = state,
                onAddProject = component::addProject,
                onSelectProject = component::selectProject,
                onRemoveProject = component::removeProject,
                onOpenTerminal = component::openTerminal,
                onSelectTerminal = component::selectTerminal,
                onCloseTerminal = component::closeTerminal,
                onDismissError = component::dismissAddProjectError,
                onSetGitRoot = component::setGitRoot,
                onDismissGitRootPrompt = component::dismissGitRootPrompt,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
            ) {
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
}
