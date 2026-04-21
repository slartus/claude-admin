package dev.claudeadmin.data.mcp

import dev.claudeadmin.data.util.AppDirs
import dev.claudeadmin.domain.model.McpServer
import dev.claudeadmin.domain.model.McpServerScope
import dev.claudeadmin.domain.repository.McpServerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.File

class FileMcpServerRepository : McpServerRepository {

    override suspend fun loadForProject(projectPath: String): List<McpServer> = withContext(Dispatchers.IO) {
        buildList {
            addAll(parseMcpJson(File(projectPath, ".mcp.json"), McpServerScope.PROJECT))
            addAll(parseSettings(File(projectPath, ".claude/settings.json"), McpServerScope.PROJECT))
            addAll(parseSettings(File(projectPath, ".claude/settings.local.json"), McpServerScope.PROJECT_LOCAL))
            addAll(parseSettings(File(AppDirs.userClaudeDir, "settings.json"), McpServerScope.USER))
        }.sortedBy { it.name.lowercase() }
    }

    private fun parseMcpJson(file: File, scope: McpServerScope): List<McpServer> {
        val root = readJson(file) ?: return emptyList()
        return extractServers(root, file.absolutePath, scope)
    }

    private fun parseSettings(file: File, scope: McpServerScope): List<McpServer> {
        val root = readJson(file) ?: return emptyList()
        val servers = root["mcpServers"] as? JsonObject ?: return emptyList()
        return extractServersFromMap(servers, file.absolutePath, scope)
    }

    private fun extractServers(root: JsonObject, path: String, scope: McpServerScope): List<McpServer> {
        val servers = root["mcpServers"] as? JsonObject ?: return emptyList()
        return extractServersFromMap(servers, path, scope)
    }

    private fun extractServersFromMap(
        map: JsonObject,
        path: String,
        scope: McpServerScope,
    ): List<McpServer> {
        val result = mutableListOf<McpServer>()
        for ((name, value) in map) {
            val obj = value as? JsonObject ?: continue
            val type = (obj["type"] as? JsonPrimitive)?.contentOrNull
            val command = (obj["command"] as? JsonPrimitive)?.contentOrNull
            val url = (obj["url"] as? JsonPrimitive)?.contentOrNull
            val args = (obj["args"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                ?: emptyList()
            result += McpServer(
                name = name,
                type = type,
                command = command,
                args = args,
                url = url,
                scope = scope,
                sourcePath = path,
            )
        }
        return result
    }

    private fun readJson(file: File): JsonObject? {
        if (!file.isFile) return null
        return runCatching {
            val text = file.readText().ifBlank { "{}" }
            json.parseToJsonElement(text) as? JsonObject
        }.getOrNull()
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
