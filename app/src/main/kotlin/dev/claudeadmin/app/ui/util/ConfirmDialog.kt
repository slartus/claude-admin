package dev.claudeadmin.app.ui.util

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberDialogState

internal val LocalParentWindowState = compositionLocalOf<WindowState?> { null }

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    dismissText: String = "Cancel",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dialogSize = DpSize(420.dp, 180.dp)
    val parent = LocalParentWindowState.current
    val initialPosition = remember {
        val parentPos = parent?.position
        val parentSize = parent?.size
        if (parentPos is WindowPosition.Absolute && parentSize != null) {
            WindowPosition(
                x = parentPos.x + (parentSize.width - dialogSize.width) / 2,
                y = parentPos.y + (parentSize.height - dialogSize.height) / 2,
            )
        } else {
            WindowPosition(Alignment.Center)
        }
    }
    DialogWindow(
        onCloseRequest = onDismiss,
        title = title,
        resizable = false,
        state = rememberDialogState(position = initialPosition, size = dialogSize),
    ) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onDismiss) { Text(dismissText) }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = onConfirm) { Text(confirmText) }
                    }
                }
            }
        }
    }
}
