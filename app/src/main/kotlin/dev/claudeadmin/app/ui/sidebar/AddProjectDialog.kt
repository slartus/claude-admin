package dev.claudeadmin.app.ui.sidebar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.claudeadmin.domain.model.AiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.io.File
import javax.swing.JFileChooser

data class AddProjectResult(
    val path: String?,
    val provider: AiProvider,
)

@Composable
fun AddProjectDialog(onResult: (AddProjectResult) -> Unit) {
    var pickedPath by mutableStateOf<String?>(null)
    var pickedError by mutableStateOf(false)
    var selectedProvider by mutableStateOf(AiProvider.CLAUDE)

    LaunchedEffect(Unit) {
        val path = withContext(Dispatchers.Swing) { pickFolderDialog() }
        if (path != null) {
            pickedPath = path
        } else {
            pickedError = true
        }
    }

    if (pickedError) {
        LaunchedEffect(Unit) { onResult(AddProjectResult(null, AiProvider.CLAUDE)) }
        return
    }

    if (pickedPath != null) {
        Dialog(onDismissRequest = { onResult(AddProjectResult(null, AiProvider.CLAUDE)) }) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                ) {
                    Text(
                        text = "Select AI Provider",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    Text(
                        text = pickedPath!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                    AiProvider.entries.forEach { provider ->
                        ProviderOption(
                            provider = provider,
                            selected = selectedProvider == provider,
                            onClick = { selectedProvider = provider },
                        )
                    }
                    Spacer(modifier = Modifier.padding(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = { onResult(AddProjectResult(null, AiProvider.CLAUDE)) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Cancel")
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { onResult(AddProjectResult(pickedPath, selectedProvider)) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Add")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderOption(
    provider: AiProvider,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = provider.displayName,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = provider.cliCommand,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp),
            )
            if (selected) {
                androidx.compose.material3.Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

private fun pickFolderDialog(): String? {
    val os = System.getProperty("os.name").lowercase()
    return if ("mac" in os) pickMac() else pickSwing()
}

private fun pickMac(): String? {
    val previous = System.getProperty("apple.awt.fileDialogForDirectories")
    System.setProperty("apple.awt.fileDialogForDirectories", "true")
    try {
        val dialog = FileDialog(null as java.awt.Frame?, "Select project folder", FileDialog.LOAD)
        dialog.isVisible = true
        val dir = dialog.directory ?: return null
        val file = dialog.file ?: return null
        return File(dir, file).absolutePath
    } finally {
        if (previous == null) System.clearProperty("apple.awt.fileDialogForDirectories")
        else System.setProperty("apple.awt.fileDialogForDirectories", previous)
    }
}

private fun pickSwing(): String? {
    val chooser = JFileChooser().apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogTitle = "Select project folder"
        isAcceptAllFileFilterUsed = false
    }
    val code = chooser.showOpenDialog(null)
    return if (code == JFileChooser.APPROVE_OPTION) chooser.selectedFile?.absolutePath else null
}
