package dev.claudeadmin.domain.usecase

import dev.claudeadmin.domain.model.SessionSearchHit
import dev.claudeadmin.domain.repository.SessionSearchRepository

class SearchSessionsUseCase(
    private val repository: SessionSearchRepository,
) {
    suspend operator fun invoke(query: String): List<SessionSearchHit> = repository.search(query)
}
