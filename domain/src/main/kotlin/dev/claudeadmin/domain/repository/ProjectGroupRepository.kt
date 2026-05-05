package dev.claudeadmin.domain.repository

import dev.claudeadmin.domain.model.GroupId
import dev.claudeadmin.domain.model.ProjectGroup
import kotlinx.coroutines.flow.Flow

interface ProjectGroupRepository {
    fun observeAll(): Flow<List<ProjectGroup>>
    suspend fun create(name: String, parentId: GroupId? = null): ProjectGroup
    suspend fun rename(id: GroupId, name: String)
    suspend fun setParent(id: GroupId, parentId: GroupId?)
    suspend fun setCollapsed(id: GroupId, collapsed: Boolean)
    suspend fun remove(id: GroupId)
}
