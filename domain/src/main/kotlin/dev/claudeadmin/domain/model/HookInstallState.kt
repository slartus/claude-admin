package dev.claudeadmin.domain.model

sealed interface HookInstallState {
    data object Unknown : HookInstallState
    data object NotInstalled : HookInstallState
    data class Installed(val version: String) : HookInstallState
    data class OutdatedVersion(val installedVersion: String, val currentVersion: String) : HookInstallState
    data class Error(val message: String) : HookInstallState
}
