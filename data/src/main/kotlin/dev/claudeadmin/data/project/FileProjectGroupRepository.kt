package dev.claudeadmin.data.project

import dev.claudeadmin.data.util.AppDirs
import dev.claudeadmin.domain.model.GroupId
import dev.claudeadmin.domain.model.ProjectGroup
import dev.claudeadmin.domain.repository.ProjectGroupRepository
import kotlinx.coroutines.CompletableDeferred
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

class FileProjectGroupRepository(
    private val file: File = AppDirs.projectGroupsFile,
    scope: CoroutineScope,
) : ProjectGroupRepository {

    private val mutex = Mutex()
    private val state = MutableStateFlow<List<ProjectGroup>>(emptyList())
    private val ready = CompletableDeferred<Unit>()

    init {
        scope.launch(Dispatchers.IO) {
            try {
                state.value = readFromDisk()
            } finally {
                ready.complete(Unit)
            }
        }
    }

    override fun observeAll(): StateFlow<List<ProjectGroup>> = state.asStateFlow()

    override suspend fun create(name: String, parentId: GroupId?): ProjectGroup {
        ready.await()
        mutex.withLock {
            val current = state.value
            val parentExists = parentId == null || current.any { it.id == parentId }
            val safeParent = if (parentExists) parentId else null
            val group = ProjectGroup(
                id = GroupId(UUID.randomUUID().toString()),
                name = name,
                parentId = safeParent,
            )
            state.value = current + group
            writeToDisk(state.value)
            return group
        }
    }

    override suspend fun rename(id: GroupId, name: String) {
        ready.await()
        mutex.withLock {
            val updated = state.value.map { g -> if (g.id == id) g.copy(name = name) else g }
            if (updated == state.value) return@withLock
            state.value = updated
            writeToDisk(state.value)
        }
    }

    override suspend fun setParent(id: GroupId, parentId: GroupId?) {
        ready.await()
        mutex.withLock {
            val current = state.value
            if (current.none { it.id == id }) {
                throw IllegalArgumentException("Group not found: ${id.value}")
            }
            if (parentId != null && current.none { it.id == parentId }) {
                throw IllegalArgumentException("Parent group not found: ${parentId.value}")
            }
            if (parentId != null && wouldCreateCycle(current, id, parentId)) {
                throw IllegalStateException("Cycle detected: cannot move group under its own descendant")
            }
            val updated = current.map { g -> if (g.id == id) g.copy(parentId = parentId) else g }
            if (updated == current) return@withLock
            state.value = updated
            writeToDisk(state.value)
        }
    }

    override suspend fun setCollapsed(id: GroupId, collapsed: Boolean) {
        ready.await()
        mutex.withLock {
            val updated = state.value.map { g -> if (g.id == id) g.copy(collapsed = collapsed) else g }
            if (updated == state.value) return@withLock
            state.value = updated
            writeToDisk(state.value)
        }
    }

    override suspend fun remove(id: GroupId) {
        ready.await()
        mutex.withLock {
            val current = state.value
            val target = current.firstOrNull { it.id == id } ?: return@withLock
            val newParent = target.parentId
            val updated = current
                .filterNot { it.id == id }
                .map { g -> if (g.parentId == id) g.copy(parentId = newParent) else g }
            state.value = updated
            writeToDisk(state.value)
        }
    }

    private fun wouldCreateCycle(
        all: List<ProjectGroup>,
        movingId: GroupId,
        candidateParentId: GroupId,
    ): Boolean {
        val byId = all.associateBy { it.id }
        var cursor: GroupId? = candidateParentId
        val seen = HashSet<GroupId>()
        while (cursor != null) {
            if (cursor == movingId) return true
            if (!seen.add(cursor)) return true
            cursor = byId[cursor]?.parentId
        }
        return false
    }

    private fun readFromDisk(): List<ProjectGroup> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val dtos = json.decodeFromString(listSerializer, file.readText())
            val byId = dtos.associateBy { it.id }
            dtos.map { dto ->
                val parent = dto.parentId
                val safeParent = if (parent != null && parent !in byId.keys) null else parent
                ProjectGroup(
                    id = GroupId(dto.id),
                    name = dto.name,
                    parentId = safeParent?.let(::GroupId),
                    collapsed = dto.collapsed,
                )
            }.let { sanitizeCycles(it) }
        }.getOrElse { emptyList() }
    }

    private fun sanitizeCycles(groups: List<ProjectGroup>): List<ProjectGroup> {
        val byId = groups.associateBy { it.id }
        return groups.map { g ->
            val parent = g.parentId
            if (parent == null) {
                g
            } else {
                var cursor: GroupId? = parent
                val seen = HashSet<GroupId>()
                var bad = false
                while (cursor != null) {
                    if (cursor == g.id || !seen.add(cursor)) {
                        bad = true
                        break
                    }
                    cursor = byId[cursor]?.parentId
                }
                if (bad) g.copy(parentId = null) else g
            }
        }
    }

    private suspend fun writeToDisk(items: List<ProjectGroup>) = withContext(Dispatchers.IO) {
        val dtos = items.map { ProjectGroupDto(it.id.value, it.name, it.parentId?.value, it.collapsed) }
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(listSerializer, dtos))
    }

    @Serializable
    private data class ProjectGroupDto(
        val id: String,
        val name: String,
        val parentId: String? = null,
        val collapsed: Boolean = false,
    )

    private companion object {
        val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
        val listSerializer = ListSerializer(ProjectGroupDto.serializer())
    }
}
