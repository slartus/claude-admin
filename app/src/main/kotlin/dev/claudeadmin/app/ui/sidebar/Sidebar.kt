package dev.claudeadmin.app.ui.sidebar

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.claudeadmin.domain.model.AgentStatus
import dev.claudeadmin.domain.model.GitStatus
import dev.claudeadmin.domain.model.HookInstallState
import dev.claudeadmin.domain.model.Project
import dev.claudeadmin.domain.model.ProjectId
import dev.claudeadmin.domain.model.TerminalSession
import dev.claudeadmin.domain.model.TerminalSessionId
import dev.claudeadmin.app.ui.util.ConfirmDialog
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
    onSetGitRoot: (ProjectId, String?) -> Unit,
    onDismissGitRootPrompt: (ProjectId) -> Unit,
    onInstallHooks: () -> Unit,
    onDismissHookBanner: () -> Unit,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    var pendingClose by remember { mutableStateOf<TerminalSession?>(null) }
    var pendingRemove by remember { mutableStateOf<Project?>(null) }
    var gitRootPickerFor by remember { mutableStateOf<ProjectId?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        if (shouldShowHookBanner(state)) {
            HookInstallBanner(
                state = state.hookInstallState,
                inProgress = state.hookInstallInProgress,
                onInstall = onInstallHooks,
                onDismiss = onDismissHookBanner,
            )
        }

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
                val terminalStatuses = terminals.mapNotNull { session ->
                    session.claudeSessionId?.let { state.agentStatusBySessionId[it]?.status }
                }
                ProjectRow(
                    project = project,
                    git = state.gitByProject[project.id],
                    aggregateStatus = aggregateStatus(terminalStatuses),
                    selected = state.selection?.projectId == project.id &&
                        state.selection is Selection.Details,
                    onClick = { onSelectProject(project.id) },
                    onRemove = { pendingRemove = project },
                    onOpenTerminal = { onOpenTerminal(project.id) },
                )
                terminals.forEach { session ->
                    val status = session.claudeSessionId
                        ?.let { state.agentStatusBySessionId[it]?.status }
                    TerminalRow(
                        session = session,
                        status = status,
                        selected = (state.selection as? Selection.Terminal)?.terminalId == session.id,
                        onClick = { onSelectTerminal(project.id, session.id) },
                        onClose = { pendingClose = session },
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

    pendingClose?.let { session ->
        ConfirmDialog(
            title = "Close terminal?",
            message = "Terminal \"${session.title}\" will be terminated.",
            confirmText = "Close",
            onConfirm = {
                onCloseTerminal(session.id)
                pendingClose = null
            },
            onDismiss = { pendingClose = null },
        )
    }

    pendingRemove?.let { project ->
        ConfirmDialog(
            title = "Remove project?",
            message = "\"${project.name}\" will be removed from the list. Files on disk are not affected.",
            confirmText = "Remove",
            onConfirm = {
                onRemoveProject(project.id)
                pendingRemove = null
            },
            onDismiss = { pendingRemove = null },
        )
    }

    val nextPromptId = state.gitRootPrompts.firstOrNull()
    val nextPromptProject = nextPromptId?.let { id -> state.projects.firstOrNull { it.id == id } }
    if (nextPromptProject != null && gitRootPickerFor == null) {
        GitRootPromptDialog(
            projectName = nextPromptProject.name,
            onChoose = { gitRootPickerFor = nextPromptProject.id },
            onSkip = { onDismissGitRootPrompt(nextPromptProject.id) },
        )
    }

    gitRootPickerFor?.let { id ->
        FolderPickerDialog(
            onResult = { path ->
                gitRootPickerFor = null
                if (path != null) onSetGitRoot(id, path) else onDismissGitRootPrompt(id)
            },
        )
    }
}

@Composable
private fun GitRootPromptDialog(
    projectName: String,
    onChoose: () -> Unit,
    onSkip: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onSkip,
        confirmButton = { TextButton(onClick = onChoose) { Text("Choose folder") } },
        dismissButton = { TextButton(onClick = onSkip) { Text("Skip") } },
        title = { Text("No git repository found") },
        text = {
            Text(
                "\"$projectName\" doesn't contain a .git directory. " +
                    "Pick the repository root to track the current branch.",
            )
        },
    )
}

@Composable
private fun ProjectRow(
    project: Project,
    git: GitStatus?,
    aggregateStatus: AgentStatus?,
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
        ProjectBadge(project.name)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(project.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                text = project.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            if (git != null) GitBranchLabel(git)
        }
        Box(
            modifier = Modifier.size(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            StatusDot(aggregateStatus)
        }
        IconButton(onClick = onOpenTerminal) {
            Icon(Icons.Default.Terminal, contentDescription = "Open terminal", modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Close, contentDescription = "Remove project", modifier = Modifier.size(18.dp))
        }
    }
}

private fun aggregateStatus(statuses: List<AgentStatus>): AgentStatus? {
    if (statuses.isEmpty()) return null
    return when {
        AgentStatus.WAITING in statuses -> AgentStatus.WAITING
        AgentStatus.WORKING in statuses -> AgentStatus.WORKING
        else -> AgentStatus.IDLE
    }
}

@Composable
private fun GitBranchLabel(git: GitStatus) {
    val branch = git.branch
    val sha = git.headSha
    val label = when {
        branch != null -> branch
        sha != null -> "(detached) ${sha.take(7)}"
        else -> return
    }
    val color = if (git.isDetached) MaterialTheme.colorScheme.tertiary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
        Icon(
            Icons.AutoMirrored.Filled.AltRoute,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
private fun TerminalRow(
    session: TerminalSession,
    status: AgentStatus?,
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
        Box(
            modifier = Modifier.size(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            StatusDot(status)
        }
        IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Close terminal", modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun StatusDot(status: AgentStatus?) {
    if (status == null) return
    val baseColor = when (status) {
        AgentStatus.WORKING -> MaterialTheme.colorScheme.primary
        AgentStatus.WAITING -> Color(0xFFFFB300)
        AgentStatus.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val alpha by when (status) {
        AgentStatus.WAITING -> {
            val t = rememberInfiniteTransition(label = "waiting-pulse")
            t.animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 700, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "waiting-alpha",
            )
        }
        else -> remember { mutableStateOf(1f) }
    }
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(baseColor.copy(alpha = alpha)),
    )
}

@Composable
private fun ProjectBadge(name: String) {
    val initials = remember(name) { projectInitials(name) }
    val color = remember(name) { projectColor(name) }
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun projectInitials(name: String): String {
    val words = name.split(Regex("[\\s\\-_./]+")).filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(2).uppercase()
        else -> (words[0].first().toString() + words[1].first()).uppercase()
    }
}

private fun projectColor(key: String): Color {
    val hash = key.hashCode()
    val hue = ((hash.toLong() and 0xFFFFFFFFL) % 360L).toFloat()
    return Color.hsv(hue = hue, saturation = 0.55f, value = 0.72f)
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

private fun shouldShowHookBanner(state: RootState): Boolean {
    if (state.hookBannerDismissed) return false
    return when (state.hookInstallState) {
        is HookInstallState.NotInstalled,
        is HookInstallState.OutdatedVersion,
        -> true
        else -> false
    }
}

@Composable
private fun HookInstallBanner(
    state: HookInstallState,
    inProgress: Boolean,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title: String
    val message: String
    val actionLabel: String
    when (state) {
        is HookInstallState.OutdatedVersion -> {
            title = "Update status hooks"
            message = "Installed ${state.installedVersion}, current ${state.currentVersion}."
            actionLabel = "Update"
        }
        else -> {
            title = "Enable status tracking"
            message = "Installs hooks in ~/.claude/settings.json so the app can show live agent status."
            actionLabel = "Install"
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (inProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                TextButton(onClick = onDismiss, enabled = !inProgress) {
                    Text("Dismiss")
                }
                TextButton(onClick = onInstall, enabled = !inProgress) {
                    Text(actionLabel)
                }
            }
        }
    }
}
