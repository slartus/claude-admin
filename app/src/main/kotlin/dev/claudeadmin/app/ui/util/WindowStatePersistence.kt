package dev.claudeadmin.app.ui.util

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import dev.claudeadmin.data.util.AppDirs
import java.io.File
import java.util.Properties

private val stateFile: File get() = File(AppDirs.root, "window-state.properties")

private const val KEY_X = "x"
private const val KEY_Y = "y"
private const val KEY_WIDTH = "width"
private const val KEY_HEIGHT = "height"
private const val KEY_PLACEMENT = "placement"

internal fun loadWindowState(defaultSize: DpSize): WindowState {
    val props = runCatching {
        if (stateFile.exists()) Properties().apply { stateFile.inputStream().use { load(it) } } else null
    }.getOrNull() ?: return WindowState(size = defaultSize)

    val width = props.getProperty(KEY_WIDTH)?.toFloatOrNull()?.dp ?: defaultSize.width
    val height = props.getProperty(KEY_HEIGHT)?.toFloatOrNull()?.dp ?: defaultSize.height
    val x = props.getProperty(KEY_X)?.toFloatOrNull()
    val y = props.getProperty(KEY_Y)?.toFloatOrNull()
    val position = if (x != null && y != null) WindowPosition(x.dp, y.dp) else WindowPosition.PlatformDefault
    val placement = runCatching { WindowPlacement.valueOf(props.getProperty(KEY_PLACEMENT) ?: "") }
        .getOrDefault(WindowPlacement.Floating)

    return WindowState(position = position, size = DpSize(width, height), placement = placement)
}

internal fun saveWindowState(state: WindowState) {
    val props = Properties()
    val pos = state.position
    if (pos is WindowPosition.Absolute) {
        props.setProperty(KEY_X, pos.x.value.toString())
        props.setProperty(KEY_Y, pos.y.value.toString())
    }
    props.setProperty(KEY_WIDTH, state.size.width.value.toString())
    props.setProperty(KEY_HEIGHT, state.size.height.value.toString())
    props.setProperty(KEY_PLACEMENT, state.placement.name)

    runCatching {
        stateFile.parentFile?.mkdirs()
        stateFile.outputStream().use { props.store(it, null) }
    }
}
