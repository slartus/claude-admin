package dev.claudeadmin.domain.model

data class Command(
    val name: String,
    val description: String?,
    val scope: CommandScope,
    val path: String,
    val argumentHint: String?,
    val model: String?,
    val body: String,
)

enum class CommandScope { PROJECT, USER }
