package dev.claudeadmin.data.settings

import dev.claudeadmin.data.util.AppDirs
import dev.claudeadmin.domain.model.ClaudeSettings
import dev.claudeadmin.domain.model.ClaudeSettingsScope
import dev.claudeadmin.domain.repository.ClaudeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File

class FileClaudeSettingsRepository : ClaudeSettingsRepository {

    override suspend fun loadProject(projectPath: String): ClaudeSettings? = withContext(Dispatchers.IO) {
        read(File(projectPath, ".claude/settings.json"), ClaudeSettingsScope.PROJECT)
    }

    override suspend fun loadProjectLocal(projectPath: String): ClaudeSettings? = withContext(Dispatchers.IO) {
        read(File(projectPath, ".claude/settings.local.json"), ClaudeSettingsScope.PROJECT_LOCAL)
    }

    override suspend fun loadUser(): ClaudeSettings? = withContext(Dispatchers.IO) {
        read(File(AppDirs.userClaudeDir, "settings.json"), ClaudeSettingsScope.USER)
    }

    private fun read(file: File, scope: ClaudeSettingsScope): ClaudeSettings? {
        if (!file.isFile) return null
        val raw = runCatching { file.readText() }.getOrNull() ?: return null
        val pretty = runCatching {
            val element = json.parseToJsonElement(raw)
            json.encodeToString(JsonElement.serializer(), element)
        }.getOrDefault(raw)
        return ClaudeSettings(path = file.absolutePath, content = pretty, scope = scope)
    }

    private companion object {
        val json = Json { prettyPrint = true }
    }
}
