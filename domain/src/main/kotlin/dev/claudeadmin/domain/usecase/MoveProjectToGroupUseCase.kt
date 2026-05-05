package dev.claudeadmin.domain.usecase

import dev.claudeadmin.domain.model.GroupId
import dev.claudeadmin.domain.model.ProjectId
import dev.claudeadmin.domain.repository.ProjectRepository

class MoveProjectToGroupUseCase(
    private val projects: ProjectRepository,
) {
    suspend operator fun invoke(projectId: ProjectId, groupId: GroupId?) {
        projects.setGroup(projectId, groupId)
    }
}
