package dev.claudeadmin.data.settings

import dev.claudeadmin.domain.model.ClaudeSettings
import dev.claudeadmin.domain.repository.ClaudeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File

class FileClaudeSettingsRepository : ClaudeSettingsRepository {

    override suspend fun loadLocal(projectPath: String): ClaudeSettings? = withContext(Dispatchers.IO) {
        val file = File(projectPath, ".claude/settings.local.json")
        if (!file.isFile) return@withContext null
        val raw = runCatching { file.readText() }.getOrNull() ?: return@withContext null
        val pretty = runCatching {
            val element = json.parseToJsonElement(raw)
            json.encodeToString(JsonElement.serializer(), element)
        }.getOrDefault(raw)
        ClaudeSettings(path = file.absolutePath, content = pretty)
    }

    private companion object {
        val json = Json { prettyPrint = true }
    }
}
