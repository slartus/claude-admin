package dev.claudeadmin.app.ui.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.claudeadmin.app.ui.util.CenteredDialog
import dev.claudeadmin.domain.model.GroupId
import dev.claudeadmin.domain.model.ProjectGroup

@Composable
internal fun MoveToGroupDialog(
    target: MoveTarget,
    allGroups: List<ProjectGroup>,
    onConfirm: (newParent: GroupId?) -> Unit,
    onDismiss: () -> Unit,
) {
    val excluded = when (target) {
        is MoveTarget.Group -> descendantsOf(target.groupId, allGroups) + target.groupId
        is MoveTarget.Project -> emptySet()
    }
    val candidates = remember(allGroups, excluded) {
        flattenForPicker(allGroups).filter { it.group.id !in excluded }
    }
    val currentParent = when (target) {
        is MoveTarget.Group -> target.currentParent
        is MoveTarget.Project -> target.currentParent
    }
    var selected by remember { mutableStateOf<GroupId?>(currentParent) }

    val title = when (target) {
        is MoveTarget.Group -> "Move group to…"
        is MoveTarget.Project -> "Move project to group…"
    }

    CenteredDialog(
        title = title,
        size = DpSize(420.dp, 480.dp),
        onDismiss = onDismiss,
        resizable = true,
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                item("__none__") {
                    NoneRow(
                        selected = selected == null,
                        onClick = { selected = null },
                    )
                    HorizontalDivider()
                }
                items(candidates, key = { "g:${it.group.id.value}" }) { node ->
                    GroupPickerRow(
                        node = node,
                        selected = selected == node.group.id,
                        onClick = { selected = node.group.id },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    enabled = selected != currentParent,
                    onClick = { onConfirm(selected) },
                ) { Text("Move") }
            }
        }
    }
}

@Composable
private fun NoneRow(selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(Icons.Default.FolderOff, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = "No group (top level)",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun GroupPickerRow(
    node: PickerNode,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(onClick = onClick)
            .padding(start = (12 + node.depth * 16).dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = node.group.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private data class PickerNode(val group: ProjectGroup, val depth: Int)

private fun flattenForPicker(all: List<ProjectGroup>): List<PickerNode> {
    val byParent = all.groupBy { it.parentId }
    val result = mutableListOf<PickerNode>()
    fun visit(parentId: GroupId?, depth: Int) {
        for (g in byParent[parentId].orEmpty()) {
            result += PickerNode(g, depth)
            visit(g.id, depth + 1)
        }
    }
    visit(null, 0)
    return result
}

private fun descendantsOf(groupId: GroupId, all: List<ProjectGroup>): Set<GroupId> {
    val byParent = all.groupBy { it.parentId }
    val result = HashSet<GroupId>()
    fun visit(id: GroupId) {
        for (child in byParent[id].orEmpty()) {
            if (result.add(child.id)) visit(child.id)
        }
    }
    visit(groupId)
    return result
}
