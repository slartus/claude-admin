package dev.claudeadmin.domain.model

data class ProjectGroup(
    val id: GroupId,
    val name: String,
    val parentId: GroupId? = null,
    val collapsed: Boolean = false,
)

@JvmInline
value class GroupId(val value: String)
