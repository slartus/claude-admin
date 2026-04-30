package dev.claudeadmin.app.ui.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import dev.claudeadmin.domain.model.AiProvider
import dev.claudeadmin.domain.model.ClaudeSession
import dev.claudeadmin.domain.model.GitStatus
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
    onAddProject: (path: String, provider: AiProvider) -> Unit,
    onSelectProject: (ProjectId) -> Unit,
    onRemoveProject: (ProjectId) -> Unit,
    onReorderProjects: (movingId: ProjectId, targetId: ProjectId) -> Unit,
    onOpenTerminal: (ProjectId) -> Unit,
    onSelectTerminal: (ProjectId?, TerminalSessionId) -> Unit,
    onCloseTerminal: (TerminalSessionId) -> Unit,
    onResumeSession: (ProjectId, String) -> Unit,
    onResumeOrphanSession: (cwd: String, sessionId: String) -> Unit,
    onAddProjectFromOrphan: (cwd: String) -> Unit,
    onDismissError: () -> Unit,
    onSetGitRoot: (ProjectId, String?) -> Unit,
    onDismissGitRootPrompt: (ProjectId) -> Unit,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    var pendingClose by remember { mutableStateOf<TerminalSession?>(null) }
    var pendingRemove by remember { mutableStateOf<Project?>(null) }
    var gitRootPickerFor by remember { mutableStateOf<ProjectId?>(null) }
    val sessionsExpanded = remember { mutableStateMapOf<ProjectId, Boolean>() }
    var orphanExpanded by remember { mutableStateOf(false) }
    val lazyState = rememberLazyListState()
    var drag by remember { mutableStateOf<ProjectDragInfo?>(null) }
    var pendingAdd by remember { mutableStateOf<AddProjectResult?>(null) }

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

        LazyColumn(
            state = lazyState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            items(state.projects, key = { it.id.value }) { project ->
                val isDragging by remember(project.id) {
                    derivedStateOf { drag?.id == project.id }
                }
                Column(
                    modifier = Modifier
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = drag?.takeIf { it.id == project.id }?.offsetY ?: 0f
                        },
                ) {
                    val terminals = state.terminalsByProject[project.id].orEmpty()
                    ProjectRow(
                        project = project,
                        git = state.gitByProject[project.id],
                        selected = state.selection?.projectId == project.id &&
                            state.selection is Selection.Details,
                        dragging = isDragging,
                        onClick = { onSelectProject(project.id) },
                        onRemove = { pendingRemove = project },
                        onOpenTerminal = { onOpenTerminal(project.id) },
                        dragHandleModifier = Modifier.pointerInput(project.id) {
                            detectDragGestures(
                                onDragStart = {
                                    drag = ProjectDragInfo(project.id, 0f)
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    drag = drag?.let { it.copy(offsetY = it.offsetY + amount.y) }
                                },
                                onDragEnd = {
                                    val d = drag
                                    if (d != null) {
                                        val targetId = computeTargetId(d, lazyState, state.projects)
                                        if (targetId != null && targetId != d.id) {
                                            onReorderProjects(d.id, targetId)
                                        }
                                    }
                                    drag = null
                                },
                                onDragCancel = { drag = null },
                            )
                        },
                    )
                    terminals.forEach { session ->
                        val displayTitle = session.aiSessionId
                            ?.let { state.sessionPreviewById[it] }
                            ?: session.title
                        TerminalRow(
                            title = displayTitle,
                            selected = (state.selection as? Selection.Terminal)?.terminalId == session.id,
                            onClick = { onSelectTerminal(project.id, session.id) },
                            onClose = { pendingClose = session },
                        )
                    }
                    val savedSessions = state.visibleSavedSessionsByProject[project.id].orEmpty()
                    if (savedSessions.isNotEmpty()) {
                        val expanded = sessionsExpanded[project.id] == true
                        SessionsGroupHeader(
                            count = savedSessions.size,
                            expanded = expanded,
                            onToggle = { sessionsExpanded[project.id] = !expanded },
                        )
                        if (expanded) {
                            savedSessions.forEach { session ->
                                SavedSessionRow(
                                    session = session,
                                    onClick = { onResumeSession(project.id, session.id) },
                                )
                            }
                        }
                    }
                }
            }

            val orphanGroups = state.orphanSessionsByCwd
            val orphanTotal = orphanGroups.values.sumOf { it.size }
            val detachedBySession = state.detachedTerminalBySessionId
            if (orphanTotal > 0) {
                item(key = "orphan-divider") {
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                }
                item(key = "orphan-header") {
                    OrphanGroupHeader(
                        count = orphanTotal,
                        expanded = orphanExpanded,
                        onToggle = { orphanExpanded = !orphanExpanded },
                    )
                }
                if (orphanExpanded) {
                    val flat = buildList<OrphanRow> {
                        orphanGroups.forEach { (cwd, sessions) ->
                            add(OrphanRow.Cwd(cwd, sessions.size))
                            sessions.forEach { session -> add(OrphanRow.Session(cwd, session)) }
                        }
                    }
                    val selectedTerminalId = (state.selection as? Selection.Terminal)?.terminalId
                    items(flat, key = { it.key }) { row ->
                        when (row) {
                            is OrphanRow.Cwd -> OrphanCwdRow(
                                cwd = row.cwd,
                                count = row.count,
                                onAddAsProject = { onAddProjectFromOrphan(row.cwd) },
                            )
                            is OrphanRow.Session -> {
                                val runningTerminal = detachedBySession[row.session.id]
                                OrphanSessionRow(
                                    session = row.session,
                                    running = runningTerminal != null,
                                    selected = runningTerminal != null && selectedTerminalId == runningTerminal.id,
                                    displayText = if (runningTerminal != null) {
                                        state.sessionPreviewById[row.session.id] ?: row.session.preview
                                    } else {
                                        row.session.preview
                                    },
                                    onClick = {
                                        if (runningTerminal != null) {
                                            onSelectTerminal(null, runningTerminal.id)
                                        } else {
                                            onResumeOrphanSession(row.cwd, row.session.id)
                                        }
                                    },
                                    onClose = runningTerminal?.let { term -> { pendingClose = term } },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    state.addProjectError?.let { msg ->
        ErrorDialog(message = msg, onDismiss = onDismissError)
    }

    if (pickerOpen) {
        AddProjectDialog(
            onResult = { result ->
                pickerOpen = false
                if (result.path != null) {
                    pendingAdd = result
                }
            },
        )
    }

    pendingAdd?.let { result ->
        ConfirmDialog(
            title = "Add project as ${result.provider.displayName}?",
            message = "\"${result.path}\" will be added as a ${result.provider.displayName} project.",
            confirmText = "Add",
            onConfirm = {
                onAddProject(result.path!!, result.provider)
                pendingAdd = null
            },
            onDismiss = { pendingAdd = null },
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
    selected: Boolean,
    dragging: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onOpenTerminal: () -> Unit,
    dragHandleModifier: Modifier,
) {
    val bg = when {
        dragging -> MaterialTheme.colorScheme.secondaryContainer
        selected -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(onClick = onClick)
            .padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
    ) {
        DragHandle(dragHandleModifier = dragHandleModifier)
        Spacer(Modifier.width(4.dp))
        ProjectBadge(project.name)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(project.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(6.dp))
                ProviderBadge(project.aiProvider)
            }
            Text(
                text = project.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            if (git != null) GitBranchLabel(git)
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
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TerminalRow(
    title: String,
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
            text = title,
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
private fun OrphanGroupHeader(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "Other sessions · $count",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OrphanCwdRow(
    cwd: String,
    count: Int,
    onAddAsProject: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 2.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = cwd,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Text(
                text = "$count session${if (count == 1) "" else "s"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onAddAsProject, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Add, contentDescription = "Add as project", modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun OrphanSessionRow(
    session: ClaudeSession,
    running: Boolean,
    selected: Boolean,
    displayText: String,
    onClick: () -> Unit,
    onClose: (() -> Unit)?,
) {
    val bg = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val textColor = if (running) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(onClick = onClick)
            .padding(start = 36.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
    ) {
        Icon(
            imageVector = if (running) Icons.Default.Terminal else Icons.Default.History,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = displayText,
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        Spacer(Modifier.width(6.dp))
        if (running) {
            if (onClose != null) {
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close terminal", modifier = Modifier.size(14.dp))
                }
            }
        } else {
            Text(
                text = formatRelative(session.lastModified),
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SessionsGroupHeader(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = 20.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
    ) {
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.Default.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "Sessions · $count",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SavedSessionRow(
    session: ClaudeSession,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 52.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
    ) {
        Icon(
            Icons.Default.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = session.preview,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = formatRelative(session.lastModified),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

private fun formatRelative(timestampMs: Long): String {
    val diff = (System.currentTimeMillis() - timestampMs).coerceAtLeast(0)
    val minutes = diff / 60_000L
    val hours = minutes / 60L
    val days = hours / 24L
    return when {
        minutes < 1L -> "now"
        minutes < 60L -> "${minutes}m"
        hours < 24L -> "${hours}h"
        days < 30L -> "${days}d"
        else -> "${days / 30L}mo"
    }
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

private data class ProjectDragInfo(
    val id: ProjectId,
    val offsetY: Float,
)

@Composable
private fun DragHandle(dragHandleModifier: Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(24.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {},
            )
            .then(dragHandleModifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.DragIndicator,
            contentDescription = "Reorder project",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

private fun computeTargetId(
    drag: ProjectDragInfo,
    lazyState: LazyListState,
    projects: List<Project>,
): ProjectId? {
    val idsByKey = HashMap<String, ProjectId>(projects.size)
    projects.forEach { p -> idsByKey[p.id.value] = p.id }
    val projectItems = lazyState.layoutInfo.visibleItemsInfo.filter { info ->
        val k = info.key
        k is String && idsByKey.containsKey(k)
    }
    val dragged = projectItems.firstOrNull { it.key == drag.id.value } ?: return null
    val draggedCenter = dragged.offset + dragged.size / 2f + drag.offsetY
    val first = projectItems.first()
    val last = projectItems.last()
    val targetKey = when {
        draggedCenter <= first.offset + first.size / 2f -> first.key as String
        draggedCenter >= last.offset + last.size / 2f -> last.key as String
        else -> projectItems.minByOrNull { info ->
            abs((info.offset + info.size / 2f) - draggedCenter)
        }?.key as? String ?: return null
    }
    return idsByKey[targetKey]
}

private sealed interface OrphanRow {
    val key: String
    data class Cwd(val cwd: String, val count: Int) : OrphanRow {
        override val key: String get() = "orphan-cwd:$cwd"
    }
    data class Session(val cwd: String, val session: ClaudeSession) : OrphanRow {
        override val key: String get() = "orphan-session:$cwd:${session.id}"
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

@Composable
private fun ProviderBadge(provider: AiProvider) {
    val color = when (provider) {
        AiProvider.CLAUDE -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        AiProvider.OPENCODE -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
    }
    val textColor = when (provider) {
        AiProvider.CLAUDE -> MaterialTheme.colorScheme.primary
        AiProvider.OPENCODE -> MaterialTheme.colorScheme.tertiary
    }
    Surface(
        color = color,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = provider.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}
