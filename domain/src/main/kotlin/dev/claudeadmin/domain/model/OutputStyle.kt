package dev.claudeadmin.domain.model

data class OutputStyle(
    val name: String,
    val description: String?,
    val scope: OutputStyleScope,
    val path: String,
    val body: String,
)

enum class OutputStyleScope { PROJECT, USER }
