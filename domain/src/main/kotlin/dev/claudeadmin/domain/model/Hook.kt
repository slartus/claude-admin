package dev.claudeadmin.domain.model

data class Hook(
    val event: String,
    val matcher: String?,
    val type: String,
    val command: String?,
    val async: Boolean,
    val scope: HookScope,
    val sourcePath: String,
)

enum class HookScope { PROJECT, PROJECT_LOCAL, USER }
