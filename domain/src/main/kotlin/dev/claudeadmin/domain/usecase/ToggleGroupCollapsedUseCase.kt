package dev.claudeadmin.domain.usecase

import dev.claudeadmin.domain.model.GroupId
import dev.claudeadmin.domain.repository.ProjectGroupRepository

class ToggleGroupCollapsedUseCase(
    private val groups: ProjectGroupRepository,
) {
    suspend operator fun invoke(id: GroupId, collapsed: Boolean) {
        groups.setCollapsed(id, collapsed)
    }
}
