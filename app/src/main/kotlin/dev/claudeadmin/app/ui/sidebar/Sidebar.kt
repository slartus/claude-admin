package dev.claudeadmin.app.ui.sidebar

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage
import org.koin.compose.koinInject
import dev.claudeadmin.domain.model.AiProvider
import dev.claudeadmin.domain.model.AiSession
import dev.claudeadmin.domain.model.GitStatus
import dev.claudeadmin.domain.model.GroupId
import dev.claudeadmin.domain.model.Project
import dev.claudeadmin.domain.model.ProjectGroup
import dev.claudeadmin.domain.model.ProjectId
import dev.claudeadmin.domain.model.TerminalSession
import dev.claudeadmin.domain.model.TerminalSessionId
import dev.claudeadmin.app.ui.util.ConfirmDialog
import dev.claudeadmin.data.project.ProjectIconCache
import dev.claudeadmin.presentation.root.RootState
import dev.claudeadmin.presentation.root.Selection
import dev.claudeadmin.presentation.root.SidebarRow
import dev.claudeadmin.presentation.root.buildSidebarRows

@Composable
fun Sidebar(
    modifier: Modifier = Modifier,
    state: RootState,
    onAddProject: (path: String, name: String?) -> Unit,
    onSelectProject: (ProjectId) -> Unit,
    onRemoveProject: (ProjectId) -> Unit,
    onReorderProjects: (movingId: ProjectId, targetId: ProjectId) -> Unit,
    onRequestOpenTerminal: (ProjectId) -> Unit,
    onSelectTerminal: (ProjectId?, TerminalSessionId) -> Unit,
    onCloseTerminal: (TerminalSessionId) -> Unit,
    onResumeSession: (ProjectId, String, AiProvider) -> Unit,
    onResumeOrphanSession: (cwd: String, sessionId: String, provider: AiProvider) -> Unit,
    onAddProjectFromOrphan: (cwd: String) -> Unit,
    onDismissError: () -> Unit,
    onSetGitRoot: (ProjectId, String?) -> Unit,
    onDismissGitRootPrompt: (ProjectId) -> Unit,
    onCreateGroup: (name: String, parentId: GroupId?) -> Unit,
    onRenameGroup: (GroupId, String) -> Unit,
    onMoveGroup: (GroupId, GroupId?) -> Unit,
    onRemoveGroup: (GroupId) -> Unit,
    onToggleGroupCollapsed: (GroupId, Boolean) -> Unit,
    onMoveProjectToGroup: (ProjectId, GroupId?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    var pendingClose by remember { mutableStateOf<TerminalSession?>(null) }
    var pendingRemove by remember { mutableStateOf<Project?>(null) }
    var pendingGroupRemove by remember { mutableStateOf<ProjectGroup?>(null) }
    var gitRootPickerFor by remember { mutableStateOf<ProjectId?>(null) }
    val sessionsExpanded = remember { mutableStateMapOf<ProjectId, Boolean>() }
    var orphanExpanded by remember { mutableStateOf(false) }
    var createGroupParent by remember { mutableStateOf<GroupParentChoice?>(null) }
    var renamingGroup by remember { mutableStateOf<ProjectGroup?>(null) }
    var movePicker by remember { mutableStateOf<MoveTarget?>(null) }
    val lazyState = rememberLazyListState()
    var drag by remember { mutableStateOf<ProjectDragInfo?>(null) }

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
            IconButton(onClick = { createGroupParent = GroupParentChoice(null) }) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = "New group")
            }
            IconButton(onClick = { pickerOpen = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add project")
            }
        }

        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (state.searchQuery.isNotEmpty()) {
                    IconButton(onClick = onClearSearch) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )

        Divider()

        if (state.isSearchActive) {
            SidebarSearchResults(
                state = state,
                onResumeSession = onResumeSession,
                onResumeOrphanSession = onResumeOrphanSession,
            )
            return@Column
        }

        val rows = remember(state.groups, state.projects) {
            buildSidebarRows(state.groups, state.projects)
        }
        LazyColumn(
            state = lazyState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            items(rows, key = { it.key }) { row ->
                when (row) {
                    is SidebarRow.GroupHeader -> GroupHeaderRow(
                        header = row,
                        onToggleCollapsed = {
                            onToggleGroupCollapsed(row.group.id, !row.group.collapsed)
                        },
                        onRename = { renamingGroup = row.group },
                        onCreateSubgroup = { createGroupParent = GroupParentChoice(row.group.id) },
                        onMove = {
                            movePicker = MoveTarget.Group(row.group.id, currentParent = row.group.parentId)
                        },
                        onDelete = { pendingGroupRemove = row.group },
                    )

                    is SidebarRow.ProjectItem -> {
                        val project = row.project
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
                                depth = row.depth,
                                git = state.gitByProject[project.id],
                                selected = state.selection?.projectId == project.id &&
                                    state.selection is Selection.Details,
                                dragging = isDragging,
                                onClick = { onSelectProject(project.id) },
                                onRemove = { pendingRemove = project },
                                onRequestOpenTerminal = { onRequestOpenTerminal(project.id) },
                                onMove = {
                                    movePicker = MoveTarget.Project(project.id, currentParent = project.groupId)
                                },
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
                                    provider = session.aiProvider,
                                    depth = row.depth,
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
                                    depth = row.depth,
                                    onToggle = { sessionsExpanded[project.id] = !expanded },
                                )
                                if (expanded) {
                                    savedSessions.forEach { session ->
                                        SavedSessionRow(
                                            session = session,
                                            depth = row.depth,
                                            onClick = { onResumeSession(project.id, session.id, session.provider) },
                                        )
                                    }
                                }
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
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
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
                                            onResumeOrphanSession(row.cwd, row.session.id, row.session.provider)
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

    pendingGroupRemove?.let { group ->
        val childCount = state.groups.count { it.parentId == group.id }
        val projectCount = state.projects.count { it.groupId == group.id }
        val msg = buildString {
            append("\"${group.name}\" will be removed.")
            if (projectCount > 0) append(" $projectCount project${if (projectCount == 1) "" else "s"} will move out of this group.")
            if (childCount > 0) append(" $childCount subgroup${if (childCount == 1) "" else "s"} will move up.")
        }
        ConfirmDialog(
            title = "Delete group?",
            message = msg,
            confirmText = "Delete",
            onConfirm = {
                onRemoveGroup(group.id)
                pendingGroupRemove = null
            },
            onDismiss = { pendingGroupRemove = null },
        )
    }

    createGroupParent?.let { choice ->
        GroupNameDialog(
            title = if (choice.parentId == null) "New group" else "New subgroup",
            confirmText = "Create",
            initialName = "",
            onConfirm = { name ->
                onCreateGroup(name, choice.parentId)
                createGroupParent = null
            },
            onDismiss = { createGroupParent = null },
        )
    }

    renamingGroup?.let { group ->
        GroupNameDialog(
            title = "Rename group",
            confirmText = "Save",
            initialName = group.name,
            onConfirm = { name ->
                onRenameGroup(group.id, name)
                renamingGroup = null
            },
            onDismiss = { renamingGroup = null },
        )
    }

    movePicker?.let { target ->
        MoveToGroupDialog(
            target = target,
            allGroups = state.groups,
            onConfirm = { newParent ->
                when (target) {
                    is MoveTarget.Project -> onMoveProjectToGroup(target.projectId, newParent)
                    is MoveTarget.Group -> onMoveGroup(target.groupId, newParent)
                }
                movePicker = null
            },
            onDismiss = { movePicker = null },
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
private fun GroupHeaderRow(
    header: SidebarRow.GroupHeader,
    onToggleCollapsed: () -> Unit,
    onRename: () -> Unit,
    onCreateSubgroup: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleCollapsed)
            .padding(start = (4 + header.depth * 16).dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Icon(
            imageVector = if (header.selfCollapsed) Icons.Default.ChevronRight else Icons.Default.ExpandMore,
            contentDescription = if (header.selfCollapsed) "Expand" else "Collapse",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(2.dp))
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = header.group.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (header.projectCount > 0) {
            Text(
                text = header.projectCount.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 6.dp),
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = "Group menu", modifier = Modifier.size(16.dp))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = { menuOpen = false; onRename() },
                )
                DropdownMenuItem(
                    text = { Text("New subgroup") },
                    leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                    onClick = { menuOpen = false; onCreateSubgroup() },
                )
                DropdownMenuItem(
                    text = { Text("Move to…") },
                    leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    onClick = { menuOpen = false; onMove() },
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
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
    depth: Int,
    git: GitStatus?,
    selected: Boolean,
    dragging: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onRequestOpenTerminal: () -> Unit,
    onMove: () -> Unit,
    dragHandleModifier: Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
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
            .padding(
                start = (4 + depth * 16).dp,
                end = 8.dp,
                top = 6.dp,
                bottom = 6.dp,
            ),
    ) {
        DragHandle(dragHandleModifier = dragHandleModifier)
        Spacer(Modifier.width(4.dp))
        ProjectBadge(project = project)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(project.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
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
        IconButton(onClick = onRequestOpenTerminal) {
            Icon(Icons.Default.Terminal, contentDescription = "Open terminal", modifier = Modifier.size(18.dp))
        }
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = "Project menu", modifier = Modifier.size(16.dp))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Move to group…") },
                    leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    onClick = { menuOpen = false; onMove() },
                )
                DropdownMenuItem(
                    text = { Text("Remove project") },
                    leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                    onClick = { menuOpen = false; onRemove() },
                )
            }
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
    provider: AiProvider,
    depth: Int,
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
            .padding(start = (36 + depth * 16).dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
    ) {
        ProviderLabel(provider)
        Spacer(Modifier.width(4.dp))
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
private fun ProviderLabel(provider: AiProvider) {
    Surface(
        color = when (provider) {
            AiProvider.CLAUDE -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            AiProvider.OPENCODE -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
        },
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text = provider.terminalLabel,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = when (provider) {
                    AiProvider.CLAUDE -> MaterialTheme.colorScheme.primary
                    AiProvider.OPENCODE -> MaterialTheme.colorScheme.tertiary
                },
            ),
            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
        )
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
    session: AiSession,
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
    depth: Int,
    onToggle: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = (20 + depth * 16).dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
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
    session: AiSession,
    depth: Int,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = (52 + depth * 16).dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
    ) {
        ProviderLabel(session.provider)
        Spacer(Modifier.width(4.dp))
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
private fun ProjectBadge(project: Project) {
    val iconCache = koinInject<ProjectIconCache>()
    val updates by iconCache.updates.collectAsState()
    val tick = updates[project.id] ?: 0L
    val bitmap by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = project.id.value,
        key2 = tick,
    ) {
        value = withContext(Dispatchers.IO) {
            val file = iconCache.cachedFile(project.id) ?: return@withContext null
            runCatching {
                SkiaImage.makeFromEncoded(file.readBytes()).use { it.toComposeImageBitmap() }
            }.getOrNull()
        }
    }

    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            ProjectInitialsBadge(name = project.name)
        }
    }
}

@Composable
private fun ProjectInitialsBadge(name: String) {
    val initials = remember(name) { projectInitials(name) }
    val color = remember(name) { projectColor(name) }
    Box(
        modifier = Modifier
            .fillMaxSize()
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

private data class GroupParentChoice(val parentId: GroupId?)

internal sealed interface MoveTarget {
    data class Project(val projectId: ProjectId, val currentParent: GroupId?) : MoveTarget
    data class Group(val groupId: GroupId, val currentParent: GroupId?) : MoveTarget
}

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
    val draggedProject = projects.firstOrNull { it.id == drag.id } ?: return null
    val sameGroupKeys = HashMap<String, ProjectId>()
    projects.forEach { p ->
        if (p.groupId == draggedProject.groupId) {
            sameGroupKeys["project:${p.id.value}"] = p.id
        }
    }
    val projectItems = lazyState.layoutInfo.visibleItemsInfo.filter { info ->
        val k = info.key
        k is String && sameGroupKeys.containsKey(k)
    }
    val dragged = projectItems.firstOrNull { it.key == "project:${drag.id.value}" } ?: return null
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
    return sameGroupKeys[targetKey]
}

private sealed interface OrphanRow {
    val key: String
    data class Cwd(val cwd: String, val count: Int) : OrphanRow {
        override val key: String get() = "orphan-cwd:$cwd"
    }
    data class Session(val cwd: String, val session: AiSession) : OrphanRow {
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
