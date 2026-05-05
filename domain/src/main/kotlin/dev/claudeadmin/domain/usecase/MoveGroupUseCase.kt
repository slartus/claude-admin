package dev.claudeadmin.domain.usecase

import dev.claudeadmin.domain.model.GroupId
import dev.claudeadmin.domain.repository.ProjectGroupRepository

class MoveGroupUseCase(
    private val groups: ProjectGroupRepository,
) {
    suspend operator fun invoke(id: GroupId, newParentId: GroupId?): Result<Unit> {
        if (newParentId == id) return Result.failure(IllegalArgumentException("A group cannot be its own parent"))
        return runCatching { groups.setParent(id, newParentId) }
    }
}
