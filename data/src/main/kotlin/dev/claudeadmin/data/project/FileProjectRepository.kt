package dev.claudeadmin.data.project

import dev.claudeadmin.data.util.AppDirs
import dev.claudeadmin.domain.model.GroupId
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
        mutex.withLock {
            state.value.firstOrNull { it.path == path }?.let { return it }
            val project = Project(id = ProjectId(UUID.randomUUID().toString()), name = finalName, path = path)
            state.value = state.value + project
            writeToDisk(state.value)
            return project
        }
    }

    override suspend fun remove(id: ProjectId) {
        mutex.withLock {
            state.value = state.value.filterNot { it.id == id }
            writeToDisk(state.value)
        }
    }

    override suspend fun get(id: ProjectId): Project? = state.value.firstOrNull { it.id == id }

    override suspend fun setGitRoot(id: ProjectId, gitRoot: String?) {
        mutex.withLock {
            val updated = state.value.map { p ->
                if (p.id == id) p.copy(gitRoot = gitRoot?.takeIf { it.isNotBlank() }) else p
            }
            if (updated == state.value) return@withLock
            state.value = updated
            writeToDisk(state.value)
        }
    }

    override suspend fun reorder(orderedIds: List<ProjectId>) {
        mutex.withLock {
            val current = state.value
            val byId = current.associateBy { it.id }
            val requested = orderedIds.mapNotNull { byId[it] }
            val requestedIds = requested.mapTo(HashSet(requested.size)) { it.id }
            val tail = current.filter { it.id !in requestedIds }
            val reordered = requested + tail
            if (reordered == current) return@withLock
            state.value = reordered
            writeToDisk(state.value)
        }
    }

    override suspend fun setGroup(id: ProjectId, groupId: GroupId?) {
        mutex.withLock {
            val updated = state.value.map { p ->
                if (p.id == id) p.copy(groupId = groupId) else p
            }
            if (updated == state.value) return@withLock
            state.value = updated
            writeToDisk(state.value)
        }
    }

    override suspend fun clearGroup(groupId: GroupId) {
        mutex.withLock {
            val updated = state.value.map { p ->
                if (p.groupId == groupId) p.copy(groupId = null) else p
            }
            if (updated == state.value) return@withLock
            state.value = updated
            writeToDisk(state.value)
        }
    }

    private fun readFromDisk(): List<Project> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val dtos = json.decodeFromString(listSerializer, file.readText())
            dtos.map {
                Project(
                    id = ProjectId(it.id),
                    name = it.name,
                    path = it.path,
                    gitRoot = it.gitRoot,
                    groupId = it.groupId?.let(::GroupId),
                )
            }
        }.getOrElse { emptyList() }
    }

    private suspend fun writeToDisk(items: List<Project>) = withContext(Dispatchers.IO) {
        val dtos = items.map {
            ProjectDto(it.id.value, it.name, it.path, it.gitRoot, it.groupId?.value)
        }
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(listSerializer, dtos))
    }

    @Serializable
    private data class ProjectDto(
        val id: String,
        val name: String,
        val path: String,
        val gitRoot: String? = null,
        val groupId: String? = null,
    )

    private companion object {
        val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
        val listSerializer = ListSerializer(ProjectDto.serializer())
    }
}
