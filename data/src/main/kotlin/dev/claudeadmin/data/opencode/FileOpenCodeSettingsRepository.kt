package dev.claudeadmin.data.opencode

import dev.claudeadmin.data.util.AppDirs
import dev.claudeadmin.domain.model.OpenCodeSettings
import dev.claudeadmin.domain.model.OpenCodeSettingsScope
import dev.claudeadmin.domain.repository.OpenCodeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File

class FileOpenCodeSettingsRepository : OpenCodeSettingsRepository {

    override suspend fun loadProject(projectPath: String): OpenCodeSettings? = withContext(Dispatchers.IO) {
        read(File(projectPath, ".opencode.json"), OpenCodeSettingsScope.PROJECT)
            ?: read(File(projectPath, "opencode.json"), OpenCodeSettingsScope.PROJECT)
    }

    override suspend fun loadUser(): OpenCodeSettings? = withContext(Dispatchers.IO) {
        read(File(AppDirs.userOpenCodeDir, "opencode.json"), OpenCodeSettingsScope.USER)
    }

    private fun read(file: File, scope: OpenCodeSettingsScope): OpenCodeSettings? {
        if (!file.isFile) return null
        val raw = runCatching { file.readText() }.getOrNull() ?: return null
        val pretty = runCatching {
            val element = json.parseToJsonElement(raw)
            json.encodeToString(JsonElement.serializer(), element)
        }.getOrDefault(raw)
        return OpenCodeSettings(path = file.absolutePath, content = pretty, scope = scope)
    }

    private companion object {
        val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    }
}
