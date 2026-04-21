package dev.claudeadmin.data.hooks

import dev.claudeadmin.data.util.AppDirs
import dev.claudeadmin.domain.model.Hook
import dev.claudeadmin.domain.model.HookScope
import dev.claudeadmin.domain.repository.HookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.io.File

class FileHookRepository : HookRepository {

    override suspend fun loadForProject(projectPath: String): List<Hook> = withContext(Dispatchers.IO) {
        buildList {
            addAll(parseFile(File(projectPath, ".claude/settings.json"), HookScope.PROJECT))
            addAll(parseFile(File(projectPath, ".claude/settings.local.json"), HookScope.PROJECT_LOCAL))
            addAll(parseFile(File(AppDirs.userClaudeDir, "settings.json"), HookScope.USER))
        }
    }

    private fun parseFile(file: File, scope: HookScope): List<Hook> {
        if (!file.isFile) return emptyList()
        val root = runCatching {
            val text = file.readText().ifBlank { "{}" }
            json.parseToJsonElement(text) as? JsonObject
        }.getOrNull() ?: return emptyList()
        val hooks = root["hooks"] as? JsonObject ?: return emptyList()
        val result = mutableListOf<Hook>()
        for ((event, value) in hooks) {
            val entries = value as? JsonArray ?: continue
            for (entry in entries) {
                val obj = entry as? JsonObject ?: continue
                val matcher = (obj["matcher"] as? JsonPrimitive)?.contentOrNull
                val nested = obj["hooks"] as? JsonArray ?: continue
                for (hook in nested) {
                    val hookObj = hook as? JsonObject ?: continue
                    result += Hook(
                        event = event,
                        matcher = matcher?.takeIf { it.isNotEmpty() },
                        type = (hookObj["type"] as? JsonPrimitive)?.contentOrNull ?: "",
                        command = (hookObj["command"] as? JsonPrimitive)?.contentOrNull,
                        async = (hookObj["async"] as? JsonPrimitive)?.booleanOrNull ?: false,
                        scope = scope,
                        sourcePath = file.absolutePath,
                    )
                }
            }
        }
        return result
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
