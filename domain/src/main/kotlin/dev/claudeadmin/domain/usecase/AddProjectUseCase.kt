package dev.claudeadmin.domain.usecase

import dev.claudeadmin.domain.model.Project
import dev.claudeadmin.domain.repository.ProjectRepository
import java.io.File

class AddProjectUseCase(private val projects: ProjectRepository) {
    suspend operator fun invoke(path: String, name: String? = null): Result<Project> {
        val dir = File(path)
        if (!dir.exists()) return Result.failure(IllegalArgumentException("Folder does not exist: $path"))
        if (!dir.isDirectory) return Result.failure(IllegalArgumentException("Not a directory: $path"))
        return runCatching { projects.add(dir.absolutePath, name) }
    }
}
