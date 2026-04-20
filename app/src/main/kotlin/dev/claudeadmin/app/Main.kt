package dev.claudeadmin.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.extensions.compose.lifecycle.LifecycleController
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import dev.claudeadmin.app.di.appModule
import dev.claudeadmin.app.ui.RootScreen
import dev.claudeadmin.app.ui.util.ConfirmDialog
import dev.claudeadmin.data.terminal.PtyTerminalRepository
import dev.claudeadmin.domain.repository.GitRepository
import dev.claudeadmin.domain.usecase.AddProjectUseCase
import dev.claudeadmin.domain.usecase.CloseTerminalUseCase
import dev.claudeadmin.domain.usecase.LoadProjectDetailsUseCase
import dev.claudeadmin.domain.usecase.ObserveProjectsUseCase
import dev.claudeadmin.domain.usecase.ObserveTerminalsUseCase
import dev.claudeadmin.domain.usecase.OpenTerminalUseCase
import dev.claudeadmin.domain.usecase.RemoveProjectUseCase
import dev.claudeadmin.domain.usecase.SetProjectGitRootUseCase
import dev.claudeadmin.presentation.root.RootComponent
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.getKoin

fun main() = application {
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
        )
    }

    val windowState = rememberWindowState(size = DpSize(1280.dp, 800.dp))
    LifecycleController(lifecycle, windowState)

    val rootState by root.state.collectAsState()
    var confirmExit by remember { mutableStateOf(false) }

    Window(
        onCloseRequest = {
            if (rootState.terminals.isEmpty()) exitApplication() else confirmExit = true
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
                exitApplication()
            },
            onDismiss = { confirmExit = false },
        )
    }
}
