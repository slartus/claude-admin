package dev.claudeadmin.domain.model

data class McpServer(
    val name: String,
    val type: String?,
    val command: String?,
    val args: List<String>,
    val url: String?,
    val scope: McpServerScope,
    val sourcePath: String,
)

enum class McpServerScope { PROJECT, PROJECT_LOCAL, USER }
