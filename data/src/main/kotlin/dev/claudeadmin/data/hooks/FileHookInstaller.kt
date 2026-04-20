package dev.claudeadmin.data.hooks

import dev.claudeadmin.data.util.AppDirs
import dev.claudeadmin.domain.model.HookInstallState
import dev.claudeadmin.domain.repository.HookInstallerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class FileHookInstaller(
    private val settingsFile: File = File(AppDirs.userClaudeDir, "settings.json"),
) : HookInstallerRepository {

    override val currentVersion: String = HOOKS_VERSION

    override suspend fun currentState(): HookInstallState = withContext(Dispatchers.IO) {
        if (!settingsFile.exists()) return@withContext HookInstallState.NotInstalled
        val root = runCatching { parseRoot() }.getOrElse { t ->
            return@withContext HookInstallState.Error("settings.json is malformed: ${t.message}")
        }
        val versions = scanInstalledVersions(root)
        when {
            versions.isEmpty() -> HookInstallState.NotInstalled
            versions.all { it == HOOKS_VERSION } -> HookInstallState.Installed(HOOKS_VERSION)
            else -> HookInstallState.OutdatedVersion(versions.first(), HOOKS_VERSION)
        }
    }

    override suspend fun install(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            settingsFile.parentFile?.mkdirs()
            makeBackupIfFirstInstall()
            val root = if (settingsFile.exists()) parseRoot() else JsonObject(emptyMap())
            val updated = installHooks(root)
            writeAtomically(updated)
        }
    }

    override suspend fun uninstall(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!settingsFile.exists()) return@runCatching
            val root = parseRoot()
            val cleaned = stripOurHooks(root)
            writeAtomically(cleaned)
        }
    }

    private fun parseRoot(): JsonObject {
        val text = settingsFile.readText().ifBlank { "{}" }
        val element = json.parseToJsonElement(text)
        return element as? JsonObject
            ?: error("Expected JSON object at root")
    }

    private fun scanInstalledVersions(root: JsonObject): List<String> {
        val hooks = root["hooks"] as? JsonObject ?: return emptyList()
        val versions = mutableListOf<String>()
        for ((_, value) in hooks) {
            val list = value as? JsonArray ?: continue
            for (entry in list) {
                val obj = entry as? JsonObject ?: continue
                val nested = obj["hooks"] as? JsonArray ?: continue
                for (hook in nested) {
                    val command = extractCommand(hook) ?: continue
                    val match = markerRegex.find(command) ?: continue
                    versions += match.groupValues[1]
                }
            }
        }
        return versions
    }

    private fun installHooks(root: JsonObject): JsonObject {
        val cleaned = stripOurHooks(root)
        val existingHooks = (cleaned["hooks"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        for ((event, status) in OUR_HOOKS) {
            val list = (existingHooks[event] as? JsonArray)?.toMutableList() ?: mutableListOf()
            list += buildEntry(event, status)
            existingHooks[event] = JsonArray(list)
        }
        val newRoot = cleaned.toMutableMap().apply { put("hooks", JsonObject(existingHooks)) }
        return JsonObject(newRoot)
    }

    private fun stripOurHooks(root: JsonObject): JsonObject {
        val hooks = root["hooks"] as? JsonObject ?: return root
        val newHooks = mutableMapOf<String, JsonElement>()
        for ((event, value) in hooks) {
            val list = value as? JsonArray
            if (list == null) {
                newHooks[event] = value
                continue
            }
            val filtered = list.filterNot { isOurEntry(it) }
            if (filtered.isNotEmpty()) newHooks[event] = JsonArray(filtered)
        }
        val newRoot = root.toMutableMap()
        if (newHooks.isEmpty()) newRoot.remove("hooks") else newRoot["hooks"] = JsonObject(newHooks)
        return JsonObject(newRoot)
    }

    private fun isOurEntry(entry: JsonElement): Boolean {
        val obj = entry as? JsonObject ?: return false
        val nested = obj["hooks"] as? JsonArray ?: return false
        if (nested.isEmpty()) return false
        return nested.all { hook ->
            val cmd = extractCommand(hook) ?: return@all false
            markerRegex.containsMatchIn(cmd)
        }
    }

    private fun extractCommand(hook: JsonElement): String? {
        val obj = hook as? JsonObject ?: return null
        val cmd = obj["command"] as? JsonPrimitive ?: return null
        return cmd.contentOrNull
    }

    private fun buildEntry(event: String, status: String): JsonObject {
        val command = buildCommand(event, status)
        val hookObj = buildJsonObject {
            put("type", JsonPrimitive("command"))
            put("command", JsonPrimitive(command))
            put("async", JsonPrimitive(true))
        }
        return buildJsonObject {
            if (event == "PreToolUse") put("matcher", JsonPrimitive(""))
            put("hooks", JsonArray(listOf(hookObj)))
        }
    }

    private fun buildCommand(event: String, status: String): String {
        val extraFields = if (event == "Notification") ", message:(.message // null)" else ""
        return "jq -c --arg status \"$status\" --arg ts \"\$(date +%s)\" " +
            "'{timestamp:(\$ts|tonumber), session_id, cwd, transcript_path, status:\$status, " +
            "event:.hook_event_name, tool_name:(.tool_name // null)$extraFields}' " +
            ">> $STATUS_LOG_PATH # $MARKER"
    }

    private fun makeBackupIfFirstInstall() {
        if (!settingsFile.exists()) return
        val bak = File(settingsFile.parentFile, "${settingsFile.name}.claude-admin.bak")
        if (!bak.exists()) settingsFile.copyTo(bak, overwrite = false)
    }

    private fun writeAtomically(root: JsonObject) {
        val tmp = File(settingsFile.parentFile, "${settingsFile.name}.tmp")
        tmp.writeText(json.encodeToString(JsonObject.serializer(), root))
        Files.move(
            tmp.toPath(),
            settingsFile.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    private companion object {
        const val HOOKS_VERSION = "v1"
        const val MARKER = "claude-admin-status $HOOKS_VERSION"
        const val STATUS_LOG_PATH = "~/.claude/claude-admin-status.jsonl"
        val markerRegex = Regex("""claude-admin-status (v\d+)""")
        val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
        val OUR_HOOKS = listOf(
            "UserPromptSubmit" to "working",
            "PreToolUse" to "working",
            "Notification" to "waiting",
            "Stop" to "idle",
            "SubagentStop" to "idle",
        )
    }
}
