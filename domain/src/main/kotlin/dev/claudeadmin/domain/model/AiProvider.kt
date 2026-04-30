package dev.claudeadmin.domain.model

enum class AiProvider(val cliCommand: String, val displayName: String) {
    CLAUDE("claude", "Claude"),
    OPENCODE("opencode", "OpenCode"),
}
