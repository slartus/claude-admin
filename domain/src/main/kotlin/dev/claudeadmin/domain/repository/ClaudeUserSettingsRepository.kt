package dev.claudeadmin.domain.repository

import dev.claudeadmin.domain.model.ClaudeUserSettings

interface ClaudeUserSettingsRepository {
    fun list(): List<ClaudeUserSettings>
}
