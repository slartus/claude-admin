package dev.claudeadmin.data.settings

import dev.claudeadmin.data.util.AppDirs
import dev.claudeadmin.domain.model.ClaudeUserSettings
import dev.claudeadmin.domain.repository.ClaudeUserSettingsRepository
import java.io.File

class FileClaudeUserSettingsRepository : ClaudeUserSettingsRepository {

    override fun list(): List<ClaudeUserSettings> {
        val dir = AppDirs.userClaudeDir
        return dir.listFiles()
            ?.mapNotNull { file ->
                PATTERN.matchEntire(file.name)?.let { match ->
                    ClaudeUserSettings(
                        path = file.absolutePath,
                        name = match.groupValues[1],
                    )
                }
            }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    private companion object {
        val PATTERN = Regex("^settings--(.+)\\.json$")
    }
}
