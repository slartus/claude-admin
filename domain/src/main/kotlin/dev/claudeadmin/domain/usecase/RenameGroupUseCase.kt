package dev.claudeadmin.domain.usecase

import dev.claudeadmin.domain.model.GroupId
import dev.claudeadmin.domain.repository.ProjectGroupRepository

class RenameGroupUseCase(
    private val groups: ProjectGroupRepository,
) {
    suspend operator fun invoke(id: GroupId, name: String): Result<Unit> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Name must not be empty"))
        return runCatching { groups.rename(id, trimmed) }
    }
}
