package dev.claudeadmin.domain.usecase

import dev.claudeadmin.domain.model.GroupId
import dev.claudeadmin.domain.model.ProjectGroup
import dev.claudeadmin.domain.repository.ProjectGroupRepository

class CreateGroupUseCase(
    private val groups: ProjectGroupRepository,
) {
    suspend operator fun invoke(name: String, parentId: GroupId? = null): Result<ProjectGroup> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Name must not be empty"))
        return runCatching { groups.create(trimmed, parentId) }
    }
}
