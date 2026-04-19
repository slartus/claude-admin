package dev.claudeadmin.domain.usecase

import dev.claudeadmin.domain.model.TerminalSessionId
import dev.claudeadmin.domain.repository.TerminalRepository

class CloseTerminalUseCase(
    private val terminals: TerminalRepository,
) {
    suspend operator fun invoke(id: TerminalSessionId) = terminals.close(id)
}
