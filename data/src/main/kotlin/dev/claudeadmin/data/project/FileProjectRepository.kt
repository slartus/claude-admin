package dev.claudeadmin.data.project

import dev.claudeadmin.data.util.AppDirs
import dev.claudeadmin.domain.model.Project
import dev.claudeadmin.domain.model.ProjectId
import dev.claudeadmin.domain.repository.ProjectRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class FileProjectRepository(
    private val file: File = AppDirs.projectsFile,
    scope: CoroutineScope,
) : ProjectRepository {

    private val mutex = Mutex()
    private val state = MutableStateFlow<List<Project>>(emptyList())

    init {
        scope.launch(Dispatchers.IO) { state.value = readFromDisk() }
    }

    override fun observeAll(): StateFlow<List<Project>> = state.asStateFlow()

    override suspend fun add(path: String, name: String?): Project {
        val finalName = name?.takeIf { it.isNotBlank() } ?: File(path).name
        val project = Project(id = ProjectId(UUID.randomUUID().toString()), name = finalName, path = path)
        mutex.withLock {
            state.value = state.value + project
            writeToDisk(state.value)
        }
        return project
    }

    override suspend fun remove(id: ProjectId) {
        mutex.withLock {
            state.value = state.value.filterNot { it.id == id }
            writeToDisk(state.value)
        }
    }

    override suspend fun get(id: ProjectId): Project? = state.value.firstOrNull { it.id == id }

    private fun readFromDisk(): List<Project> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val dtos = json.decodeFromString(listSerializer, file.readText())
            dtos.map { Project(ProjectId(it.id), it.name, it.path) }
        }.getOrElse { emptyList() }
    }

    private suspend fun writeToDisk(items: List<Project>) = withContext(Dispatchers.IO) {
        val dtos = items.map { ProjectDto(it.id.value, it.name, it.path) }
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(listSerializer, dtos))
    }

    @Serializable
    private data class ProjectDto(val id: String, val name: String, val path: String)

    private companion object {
        val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
        val listSerializer = ListSerializer(ProjectDto.serializer())
    }
}
