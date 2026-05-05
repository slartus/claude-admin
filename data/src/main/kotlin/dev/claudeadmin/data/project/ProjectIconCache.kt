package dev.claudeadmin.data.project

import dev.claudeadmin.data.util.AppDirs
import dev.claudeadmin.domain.model.Project
import dev.claudeadmin.domain.model.ProjectId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Collections

class ProjectIconCache(
    private val cacheDir: File = AppDirs.iconsCacheDir,
) {

    private val updatesFlow = MutableStateFlow<Map<ProjectId, Long>>(emptyMap())
    val updates: StateFlow<Map<ProjectId, Long>> = updatesFlow.asStateFlow()

    private val inFlight = Collections.synchronizedSet(HashSet<ProjectId>())

    init {
        cacheDir.mkdirs()
    }

    fun cachedFile(id: ProjectId): File? {
        val files = cacheDir.listFiles { file -> isCacheFileFor(file, id) } ?: return null
        return files.firstOrNull()
    }

    suspend fun resolveAndCache(project: Project) {
        if (!inFlight.add(project.id)) return
        try {
            withContext(Dispatchers.IO) {
                val source = ProjectIconResolver.resolve(File(project.path)) ?: return@withContext
                val ext = source.extension.lowercase()
                if (ext !in ALLOWED_EXTENSIONS) return@withContext
                val target = File(cacheDir, "${project.id.value}.$ext")
                val tmp = File(cacheDir, "${project.id.value}.$ext.tmp")
                source.copyTo(tmp, overwrite = true)
                Files.move(
                    tmp.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
                removeStaleSiblings(project.id, keepName = target.name)
                bumpUpdates(project.id)
            }
        } finally {
            inFlight.remove(project.id)
        }
    }

    fun invalidate(id: ProjectId) {
        if (removeCached(id)) bumpUpdates(id)
    }

    private fun removeCached(id: ProjectId): Boolean {
        var deleted = false
        cacheDir.listFiles { file -> isCacheFileFor(file, id) }?.forEach {
            if (it.delete()) deleted = true
        }
        return deleted
    }

    private fun removeStaleSiblings(id: ProjectId, keepName: String) {
        cacheDir.listFiles { file -> isCacheFileFor(file, id) && file.name != keepName }
            ?.forEach { it.delete() }
    }

    private fun isCacheFileFor(file: File, id: ProjectId): Boolean {
        if (!file.isFile) return false
        val ext = file.extension.lowercase()
        if (ext !in ALLOWED_EXTENSIONS) return false
        return file.nameWithoutExtension == id.value
    }

    private fun bumpUpdates(id: ProjectId) {
        updatesFlow.update { current ->
            current + (id to ((current[id] ?: 0L) + 1L))
        }
    }

    private companion object {
        val ALLOWED_EXTENSIONS = setOf("png", "webp", "jpg", "jpeg")
    }
}
