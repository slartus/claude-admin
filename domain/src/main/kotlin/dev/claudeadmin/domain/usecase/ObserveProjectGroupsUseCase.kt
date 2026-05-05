package dev.claudeadmin.domain.usecase

import dev.claudeadmin.domain.model.ProjectGroup
import dev.claudeadmin.domain.repository.ProjectGroupRepository
import kotlinx.coroutines.flow.Flow

class ObserveProjectGroupsUseCase(
    private val groups: ProjectGroupRepository,
) {
    operator fun invoke(): Flow<List<ProjectGroup>> = groups.observeAll()
}
