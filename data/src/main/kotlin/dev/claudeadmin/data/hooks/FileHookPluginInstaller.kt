package dev.claudeadmin.data.hooks

import java.io.File

/**
 * Installs a Claude Code plugin that emits agent status events into a JSONL log
 * ({AppDirs.statusLogFile}). The plugin is injected per-launch via `--plugin-dir`
 * instead of editing the global ~/.claude/settings.json, so it does not interfere
 * with user/project settings or other plugins.
 */
class FileHookPluginInstaller(
    val pluginDir: File,
) {

    fun ensureInstalled(): Boolean {
        if (isCurrent()) return true
        writePluginJson()
        writeHooksJson()
        writeStatusScript()
        return true
    }

    fun currentVersion(): String? {
        if (!pluginJson.isFile) return null
        return VERSION_PATTERN.find(pluginJson.readText())?.groupValues?.get(1)
    }

    private fun isCurrent(): Boolean =
        pluginJson.isFile &&
            hooksJson.isFile &&
            statusScript.isFile &&
            currentVersion() == VERSION &&
            statusScript.canExecute()

    private fun writePluginJson() {
        pluginJson.parentFile.mkdirs()
        pluginJson.writeText(PLUGIN_JSON)
    }

    private fun writeHooksJson() {
        hooksJson.parentFile.mkdirs()
        hooksJson.writeText(buildHooksJson())
    }

    private fun writeStatusScript() {
        statusScript.parentFile.mkdirs()
        statusScript.writeText(STATUS_SCRIPT)
        statusScript.setExecutable(true, false)
    }

    private fun buildHooksJson(): String = buildString {
        appendLine("{")
        appendLine("  \"hooks\": {")
        HOOKS.forEachIndexed { index, (event, status) ->
            val isLast = index == HOOKS.size - 1
            val needsMatcher = event in MATCHED_EVENTS
            val command = "sh \\\"\$CLAUDE_PLUGIN_ROOT/hooks/status.sh\\\" $status"
            appendLine("    \"$event\": [")
            appendLine("      {")
            if (needsMatcher) appendLine("        \"matcher\": \"*\",")
            appendLine("        \"hooks\": [")
            appendLine("          { \"type\": \"command\", \"command\": \"$command\", \"async\": true }")
            appendLine("        ]")
            appendLine("      }")
            appendLine("    ]${if (isLast) "" else ","}")
        }
        appendLine("  }")
        append("}")
    }

    private val pluginJson: File get() = File(pluginDir, ".claude-plugin/plugin.json")
    private val hooksJson: File get() = File(pluginDir, "hooks/hooks.json")
    private val statusScript: File get() = File(pluginDir, "hooks/status.sh")

    private companion object {
        const val VERSION = "1.0.0"

        const val PLUGIN_JSON = """{
  "name": "claude-admin-status",
  "version": "1.0.0",
  "description": "Emits agent status events for Claude Admin"
}
"""

        // Event -> status written to the log. The event name is the Claude Code hook
        // event key; the value is the status label consumed by FileAgentStatusRepository.
        val HOOKS: List<Pair<String, String>> = listOf(
            "SessionStart" to "idle",
            "SessionEnd" to "ended",
            "UserPromptSubmit" to "working",
            "PreToolUse" to "working",
            "PostToolUse" to "working",
            "PostToolUseFailure" to "working",
            "Stop" to "idle",
            "Notification" to "waiting",
        )

        // Tool-related events require a matcher; "*" matches all tools.
        val MATCHED_EVENTS: Set<String> = setOf("PreToolUse", "PostToolUse", "PostToolUseFailure")

        val VERSION_PATTERN = Regex("\"version\"\\s*:\\s*\"([^\"]+)\"")

        val STATUS_SCRIPT = """#!/bin/sh
# claude-admin-status v1
status="${'$'}1"
LOG="${'$'}HOME/.claude-admin/status.jsonl"
[ -z "${'$'}status" ] && status="unknown"
mkdir -p "${'$'}(dirname "${'$'}LOG")"
jq -c \
  --arg status "${'$'}status" \
  '{timestamp: (now|floor), session_id: (.session_id // null), cwd: (.cwd // null), transcript_path: (.transcript_path // null), status: ${'$'}status, event: (.hook_event_name // null), tool_name: (.tool_name // null), message: (.message // null)}' \
  >> "${'$'}LOG" 2>/dev/null || true
exit 0
"""
    }
}
