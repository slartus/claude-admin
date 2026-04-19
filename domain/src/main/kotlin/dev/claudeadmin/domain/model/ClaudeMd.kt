package dev.claudeadmin.domain.model

data class ClaudeMd(
    val path: String,
    val content: String,
    val imports: List<String>,
)
