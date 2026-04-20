package dev.claudeadmin.domain.repository

import dev.claudeadmin.domain.model.HookInstallState

interface HookInstallerRepository {
    val currentVersion: String

    suspend fun currentState(): HookInstallState

    suspend fun install(): Result<Unit>

    suspend fun uninstall(): Result<Unit>
}
