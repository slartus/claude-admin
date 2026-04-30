package dev.claudeadmin.domain.model

data class OpenCodeMd(
    val path: String,
    val content: String,
    val name: String,
    val imports: List<String>,
)
