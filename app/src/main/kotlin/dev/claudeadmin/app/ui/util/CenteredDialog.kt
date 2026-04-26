package dev.claudeadmin.app.ui.util

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState

@Composable
fun CenteredDialog(
    title: String,
    size: DpSize,
    onDismiss: () -> Unit,
    resizable: Boolean = false,
    content: @Composable () -> Unit,
) {
    val parent = LocalParentWindowState.current
    val initialPosition = remember {
        val parentPos = parent?.position
        val parentSize = parent?.size
        if (parentPos is WindowPosition.Absolute && parentSize != null) {
            WindowPosition(
                x = parentPos.x + (parentSize.width - size.width) / 2,
                y = parentPos.y + (parentSize.height - size.height) / 2,
            )
        } else {
            WindowPosition(Alignment.Center)
        }
    }
    DialogWindow(
        onCloseRequest = onDismiss,
        title = title,
        resizable = resizable,
        state = rememberDialogState(position = initialPosition, size = size),
    ) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface,
            ) {
                content()
            }
        }
    }
}
