package dev.claudeadmin.domain.repository

import dev.claudeadmin.domain.model.AiSession
import dev.claudeadmin.domain.model.AiProvider
import kotlinx.coroutines.flow.Flow

interface AiSessionRepository {
    val provider: AiProvider
    fun observeAll(): Flow<List<AiSession>>
}
