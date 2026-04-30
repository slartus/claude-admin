package dev.claudeadmin.domain.model

data class Project(
    val id: ProjectId,
    val name: String,
    val path: String,
    val gitRoot: String? = null,
)

@JvmInline
value class ProjectId(val value: String)
