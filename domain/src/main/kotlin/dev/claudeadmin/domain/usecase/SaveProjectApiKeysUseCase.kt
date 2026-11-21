package dev.claudeadmin.domain.usecase

import dev.claudeadmin.domain.model.ProjectApiKeys
import dev.claudeadmin.domain.repository.ProjectApiKeysRepository
import kotlinx.coroutines.CancellationException

class SaveProjectApiKeysUseCase(
    private val repository: ProjectApiKeysRepository,
) {
    suspend operator fun invoke(projectPath: String, keys: ProjectApiKeys): Result<Unit> = try {
        Result.success(repository.save(projectPath, keys))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
}
