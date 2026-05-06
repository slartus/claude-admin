package dev.claudeadmin.domain.repository

import dev.claudeadmin.domain.model.SessionSearchHit

interface SessionSearchRepository {
    suspend fun search(query: String): List<SessionSearchHit>
}
