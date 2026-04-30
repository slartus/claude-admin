package dev.claudeadmin.domain.repository

import dev.claudeadmin.domain.model.OpenCodeMd

interface OpenCodeMdRepository {
    suspend fun load(projectPath: String): List<OpenCodeMd>
    suspend fun loadUser(): List<OpenCodeMd>
}
