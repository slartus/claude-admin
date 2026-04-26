package dev.claudeadmin.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.extensions.compose.lifecycle.LifecycleController
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import dev.claudeadmin.app.di.appModule
import dev.claudeadmin.app.ui.RootScreen
import dev.claudeadmin.app.ui.util.ConfirmDialog
import dev.claudeadmin.app.ui.util.LocalParentWindowState
import dev.claudeadmin.app.ui.util.loadWindowState
import dev.claudeadmin.app.ui.util.openInDefaultApp
import dev.claudeadmin.app.ui.util.saveWindowState
import dev.claudeadmin.data.terminal.PtyTerminalRepository
import dev.claudeadmin.data.util.CrashReporter
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
import dev.claudeadmin.presentation.root.RootComponent
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.getKoin
import java.io.File

@OptIn(FlowPreview::class)
fun main() {
    val priorCrashes = CrashReporter.snapshotUnseenCrashes()
    CrashReporter.install()
    application {
        AppContent(priorCrashes = priorCrashes)
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun ApplicationScope.AppContent(priorCrashes: List<File>) {
    var crashesToShow by remember { mutableStateOf(priorCrashes) }

    remember {
        startKoin { modules(appModule) }
    }
    val koin = remember { getKoin() }

    val lifecycle = remember { LifecycleRegistry() }
    val rootContext = remember { DefaultComponentContext(lifecycle = lifecycle) }

    val ptyRepo = remember { koin.get<PtyTerminalRepository>() }

    val root = remember {
        RootComponent(
            componentContext = rootContext,
            observeProjects = koin.get<ObserveProjectsUseCase>(),
            observeTerminals = koin.get<ObserveTerminalsUseCase>(),
            loadDetails = koin.get<LoadProjectDetailsUseCase>(),
            addProject = koin.get<AddProjectUseCase>(),
            removeProject = koin.get<RemoveProjectUseCase>(),
            openTerminal = koin.get<OpenTerminalUseCase>(),
            closeTerminal = koin.get<CloseTerminalUseCase>(),
            gitRepository = koin.get<GitRepository>(),
            setProjectGitRoot = koin.get<SetProjectGitRootUseCase>(),
            reorderProjects = koin.get<ReorderProjectsUseCase>(),
            claudeSessionRepository = koin.get<ClaudeSessionRepository>(),
        )
    }

    val windowState = remember { loadWindowState(defaultSize = DpSize(1280.dp, 800.dp)) }
    LifecycleController(lifecycle, windowState)

    remember(windowState) {
        Runtime.getRuntime().addShutdownHook(Thread { saveWindowState(windowState) })
    }

    LaunchedEffect(windowState) {
        snapshotFlow { listOf(windowState.position, windowState.size, windowState.placement) }
            .drop(1)
            .debounce(500)
            .collect { saveWindowState(windowState) }
    }

    val rootState by root.state.collectAsState()
    var confirmExit by remember { mutableStateOf(false) }

    val exit = {
        saveWindowState(windowState)
        exitApplication()
    }

    CompositionLocalProvider(LocalParentWindowState provides windowState) {
        Window(
            onCloseRequest = {
                if (rootState.terminals.isEmpty()) exit() else confirmExit = true
            },
            state = windowState,
            title = "Claude Admin",
        ) {
            MaterialTheme(colorScheme = darkColorScheme()) {
                RootScreen(component = root, ptyRepo = ptyRepo)
            }
        }

        if (confirmExit) {
            val openCount = rootState.terminals.size
            val noun = if (openCount == 1) "terminal" else "terminals"
            ConfirmDialog(
                title = "Quit Claude Admin?",
                message = "$openCount open $noun will be terminated.",
                confirmText = "Quit",
                onConfirm = {
                    confirmExit = false
                    exit()
                },
                onDismiss = { confirmExit = false },
            )
        }

        if (crashesToShow.isNotEmpty()) {
            val count = crashesToShow.size
            val noun = if (count == 1) "crash" else "crashes"
            ConfirmDialog(
                title = "Previous session crashed",
                message = "Found $count $noun from a previous run. Open the crash log folder?",
                confirmText = "Open Folder",
                dismissText = "Dismiss",
                onConfirm = {
                    openInDefaultApp(CrashReporter.directory.absolutePath)
                    CrashReporter.markSeen(crashesToShow)
                    crashesToShow = emptyList()
                },
                onDismiss = {
                    CrashReporter.markSeen(crashesToShow)
                    crashesToShow = emptyList()
                },
            )
        }
    }
}
