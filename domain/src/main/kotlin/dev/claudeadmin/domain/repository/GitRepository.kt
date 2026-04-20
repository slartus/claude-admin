package dev.claudeadmin.domain.repository

import dev.claudeadmin.domain.model.GitStatus
import kotlinx.coroutines.flow.Flow

interface GitRepository {
    fun observe(path: String): Flow<GitStatus?>
}
