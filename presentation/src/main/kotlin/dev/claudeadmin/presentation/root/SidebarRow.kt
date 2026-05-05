package dev.claudeadmin.presentation.root

import dev.claudeadmin.domain.model.GroupId
import dev.claudeadmin.domain.model.Project
import dev.claudeadmin.domain.model.ProjectGroup

sealed interface SidebarRow {
    val key: String

    data class GroupHeader(
        val group: ProjectGroup,
        val depth: Int,
        val projectCount: Int,
        val selfCollapsed: Boolean,
    ) : SidebarRow {
        override val key: String get() = "group:${group.id.value}"
    }

    data class ProjectItem(
        val project: Project,
        val depth: Int,
    ) : SidebarRow {
        override val key: String get() = "project:${project.id.value}"
    }
}

/**
 * Pre-order flattening of the group tree:
 * - root-level groups (in repository order) with their nested projects/subgroups
 * - then ungrouped projects (root depth, no header) at the very bottom
 *
 * Effectively-collapsed: a group is hidden if itself or any ancestor is collapsed.
 * The top-level Ungrouped projects bucket has no header — they render as plain rows.
 */
fun buildSidebarRows(
    groups: List<ProjectGroup>,
    projects: List<Project>,
): List<SidebarRow> {
    if (groups.isEmpty()) {
        return projects.map { SidebarRow.ProjectItem(it, depth = 0) }
    }

    val groupsByParent: Map<GroupId?, List<ProjectGroup>> = groups.groupBy { it.parentId }
    val projectsByGroup: Map<GroupId?, List<Project>> = projects.groupBy { it.groupId }
    val validGroupIds = groups.mapTo(HashSet()) { it.id }

    val rows = mutableListOf<SidebarRow>()

    fun visit(parentId: GroupId?, depth: Int, ancestorCollapsed: Boolean) {
        val children = groupsByParent[parentId].orEmpty()
        for (group in children) {
            val descendantProjectCount = countProjectsRecursive(group.id, groupsByParent, projectsByGroup)
            val collapsed = ancestorCollapsed || group.collapsed
            rows += SidebarRow.GroupHeader(
                group = group,
                depth = depth,
                projectCount = descendantProjectCount,
                selfCollapsed = group.collapsed,
            )
            if (!collapsed) {
                projectsByGroup[group.id].orEmpty().forEach { p ->
                    rows += SidebarRow.ProjectItem(p, depth = depth + 1)
                }
                visit(group.id, depth = depth + 1, ancestorCollapsed = false)
            }
        }
    }

    visit(parentId = null, depth = 0, ancestorCollapsed = false)

    // Ungrouped at root: groupId == null, OR groupId pointing at a deleted group.
    val ungrouped = projects.filter { it.groupId == null || it.groupId !in validGroupIds }
    ungrouped.forEach { rows += SidebarRow.ProjectItem(it, depth = 0) }

    return rows
}

private fun countProjectsRecursive(
    groupId: GroupId,
    groupsByParent: Map<GroupId?, List<ProjectGroup>>,
    projectsByGroup: Map<GroupId?, List<Project>>,
): Int {
    var total = projectsByGroup[groupId]?.size ?: 0
    val children = groupsByParent[groupId].orEmpty()
    for (child in children) {
        total += countProjectsRecursive(child.id, groupsByParent, projectsByGroup)
    }
    return total
}
