package dev.claudeadmin.domain.model

data class GitStatus(
    val branch: String?,
    val headSha: String?,
    val isDetached: Boolean,
)
