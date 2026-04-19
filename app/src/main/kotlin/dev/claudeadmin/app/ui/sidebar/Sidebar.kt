package dev.claudeadmin.app.ui.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.claudeadmin.domain.model.Project
import dev.claudeadmin.domain.model.ProjectId
import dev.claudeadmin.domain.model.TerminalSession
import dev.claudeadmin.domain.model.TerminalSessionId
import dev.claudeadmin.presentation.root.RootState
import dev.claudeadmin.presentation.root.Selection

@Composable
fun Sidebar(
    modifier: Modifier = Modifier,
    state: RootState,
    onAddProject: (path: String, name: String?) -> Unit,
    onSelectProject: (ProjectId) -> Unit,
    onRemoveProject: (ProjectId) -> Unit,
    onOpenTerminal: (ProjectId) -> Unit,
    onSelectTerminal: (ProjectId, TerminalSessionId) -> Unit,
    onCloseTerminal: (TerminalSessionId) -> Unit,
    onDismissError: () -> Unit,
) {
    var pickerOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = "Projects",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { pickerOpen = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add project")
            }
        }
        Divider()

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp)) {
            items(state.projects, key = { it.id.value }) { project ->
                val terminals = state.terminalsByProject[project.id].orEmpty()
                ProjectRow(
                    project = project,
                    selected = state.selection?.projectId == project.id &&
                        state.selection is Selection.Details,
                    onClick = { onSelectProject(project.id) },
                    onRemove = { onRemoveProject(project.id) },
                    onOpenTerminal = { onOpenTerminal(project.id) },
                )
                terminals.forEach { session ->
                    TerminalRow(
                        session = session,
                        selected = (state.selection as? Selection.Terminal)?.terminalId == session.id,
                        onClick = { onSelectTerminal(project.id, session.id) },
                        onClose = { onCloseTerminal(session.id) },
                    )
                }
            }
        }
    }

    state.addProjectError?.let { msg ->
        ErrorDialog(message = msg, onDismiss = onDismissError)
    }

    if (pickerOpen) {
        FolderPickerDialog(
            onResult = { path ->
                pickerOpen = false
                if (path != null) onAddProject(path, null)
            },
        )
    }
}

@Composable
private fun ProjectRow(
    project: Project,
    selected: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onOpenTerminal: () -> Unit,
) {
    val bg = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(project.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                text = project.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        IconButton(onClick = onOpenTerminal) {
            Icon(Icons.Default.Terminal, contentDescription = "Open terminal", modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Close, contentDescription = "Remove project", modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun TerminalRow(
    session: TerminalSession,
    selected: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(onClick = onClick)
            .padding(start = 36.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
    ) {
        Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = session.title,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Close terminal", modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun ErrorDialog(message: String, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        title = { Text("Couldn't add project") },
        text = { Text(message) },
    )
}
