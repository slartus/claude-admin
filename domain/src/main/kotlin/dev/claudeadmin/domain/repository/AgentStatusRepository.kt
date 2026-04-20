package dev.claudeadmin.domain.repository

import dev.claudeadmin.domain.model.AgentStatusEntry
import kotlinx.coroutines.flow.Flow

interface AgentStatusRepository {
    fun observe(): Flow<Map<String, AgentStatusEntry>>
}
