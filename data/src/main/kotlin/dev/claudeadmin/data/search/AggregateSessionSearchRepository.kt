package dev.claudeadmin.data.search

import dev.claudeadmin.domain.model.SessionSearchHit
import dev.claudeadmin.domain.repository.SessionSearchRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class AggregateSessionSearchRepository(
    private val sources: List<SessionSearchRepository>,
) : SessionSearchRepository {

    override suspend fun search(query: String): List<SessionSearchHit> = coroutineScope {
        if (query.isBlank()) return@coroutineScope emptyList()
        sources.map { source -> async { source.search(query) } }
            .flatMap { it.await() }
    }
}
