package dev.claudeadmin.domain.repository

import dev.claudeadmin.domain.model.ClaudeSession
import kotlinx.coroutines.flow.Flow

interface ClaudeSessionRepository {
    fun observeAll(): Flow<List<ClaudeSession>>
}
