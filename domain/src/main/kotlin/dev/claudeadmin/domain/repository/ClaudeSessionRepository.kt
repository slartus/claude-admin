package dev.claudeadmin.domain.repository

import dev.claudeadmin.domain.model.AiProvider
import dev.claudeadmin.domain.model.AiSession

interface ClaudeSessionRepository : AiSessionRepository {
    override val provider: AiProvider get() = AiProvider.CLAUDE
    override fun observeAll(): kotlinx.coroutines.flow.Flow<List<AiSession>>
}
