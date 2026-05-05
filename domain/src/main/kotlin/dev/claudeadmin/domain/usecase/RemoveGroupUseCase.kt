package dev.claudeadmin.domain.usecase

import dev.claudeadmin.domain.model.GroupId
import dev.claudeadmin.domain.repository.ProjectGroupRepository
import dev.claudeadmin.domain.repository.ProjectRepository

class RemoveGroupUseCase(
    private val groups: ProjectGroupRepository,
    private val projects: ProjectRepository,
) {
    /**
     * Order matters for fail-safety: if the process is interrupted between the two writes,
     * an orphaned [Project.groupId] pointing at a deleted group is harmless (UI treats it
     * as ungrouped), while the inverse — a group with no members in projects.json — is
     * recoverable by sanitizing on next read. Removing the group first is the cheaper failure.
     */
    suspend operator fun invoke(id: GroupId) {
        groups.remove(id)
        projects.clearGroup(id)
    }
}
