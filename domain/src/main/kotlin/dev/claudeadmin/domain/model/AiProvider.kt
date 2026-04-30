package dev.claudeadmin.domain.model

enum class AiProvider(val cliCommand: String, val displayName: String, val terminalLabel: String) {
    CLAUDE("claude", "Claude", "C"),
    OPENCODE("opencode", "OpenCode", "O"),
}
