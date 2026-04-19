package dev.claudeadmin.domain.model

data class ProjectDetails(
    val project: Project,
    val claudeMd: ClaudeMd?,
    val agents: List<Agent>,
)
