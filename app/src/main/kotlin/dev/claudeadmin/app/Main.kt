package dev.claudeadmin.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.remember
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
import dev.claudeadmin.data.terminal.PtyTerminalRepository
import dev.claudeadmin.domain.usecase.AddProjectUseCase
import dev.claudeadmin.domain.usecase.CloseTerminalUseCase
import dev.claudeadmin.domain.usecase.LoadProjectDetailsUseCase
import dev.claudeadmin.domain.usecase.ObserveProjectsUseCase
import dev.claudeadmin.domain.usecase.ObserveTerminalsUseCase
import dev.claudeadmin.domain.usecase.OpenTerminalUseCase
import dev.claudeadmin.domain.usecase.RemoveProjectUseCase
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
        )
    }

    val windowState = rememberWindowState(size = DpSize(1280.dp, 800.dp))
    LifecycleController(lifecycle, windowState)

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "Claude Admin",
    ) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            RootScreen(component = root, ptyRepo = ptyRepo)
        }
    }
}
