package dev.claudeadmin.data.terminal

import com.jediterm.terminal.TtyConnector
import dev.claudeadmin.domain.model.AiProvider
import dev.claudeadmin.domain.model.Project
import dev.claudeadmin.domain.model.ProjectId
import dev.claudeadmin.domain.model.TerminalSession
import dev.claudeadmin.domain.model.TerminalSessionId
import dev.claudeadmin.domain.repository.TerminalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

class PtyTerminalRepository : TerminalRepository {

    private data class Entry(val session: TerminalSession, val backend: PtyBackend)

    private val mutex = Mutex()
    private val entries = MutableStateFlow<Map<TerminalSessionId, Entry>>(emptyMap())

    override fun observeAll(): Flow<List<TerminalSession>> =
        entries.map { map -> map.values.map { it.session }.sortedBy { it.createdAt } }

    override fun observeByProject(projectId: ProjectId): Flow<List<TerminalSession>> =
        entries.map { map ->
            map.values
                .map { it.session }
                .filter { it.projectId == projectId }
                .sortedBy { it.createdAt }
        }

    override suspend fun open(
        project: Project,
        title: String,
        resumeSessionId: String?,
        provider: AiProvider,
    ): TerminalSession = spawn(
        projectId = project.id,
        cwd = project.path,
        title = title,
        resumeSessionId = resumeSessionId,
        provider = provider,
    )

    override suspend fun openDetached(
        cwd: String,
        title: String,
        resumeSessionId: String?,
        provider: AiProvider,
    ): TerminalSession = spawn(
        projectId = null,
        cwd = cwd,
        title = title,
        resumeSessionId = resumeSessionId,
        provider = provider,
    )

    private suspend fun spawn(
        projectId: ProjectId?,
        cwd: String,
        title: String,
        resumeSessionId: String?,
        provider: AiProvider,
    ): TerminalSession = withContext(Dispatchers.IO) {
        val aiSessionId = resumeSessionId ?: UUID.randomUUID().toString()
        val fullCommand = buildCommand(provider, resumeSessionId, aiSessionId)
        val backend = PtyFactory.spawn(cwd, fullCommand)
        val session = TerminalSession(
            id = TerminalSessionId(UUID.randomUUID().toString()),
            projectId = projectId,
            cwd = cwd,
            title = title,
            createdAt = System.currentTimeMillis(),
            aiSessionId = aiSessionId,
            aiProvider = provider,
        )
        mutex.withLock {
            entries.value = entries.value + (session.id to Entry(session, backend))
        }
        session
    }

    private fun buildCommand(
        provider: AiProvider,
        resumeSessionId: String?,
        aiSessionId: String,
    ): String = when (provider) {
        AiProvider.CLAUDE -> {
            if (resumeSessionId != null) {
                "${provider.cliCommand} --resume $resumeSessionId"
            } else {
                "${provider.cliCommand} --session-id=$aiSessionId"
            }
        }
        AiProvider.OPENCODE -> {
            if (resumeSessionId != null) {
                "${provider.cliCommand} --session $resumeSessionId"
            } else {
                provider.cliCommand
            }
        }
    }

    override suspend fun close(id: TerminalSessionId) {
        val entry = mutex.withLock {
            val e = entries.value[id] ?: return@withLock null
            entries.value = entries.value - id
            e
        }
        entry?.backend?.dispose()
    }

    fun connectorFor(id: TerminalSessionId): TtyConnector? = entries.value[id]?.backend?.connector

    fun isAlive(id: TerminalSessionId): Boolean = entries.value[id]?.backend?.isAlive == true

    fun livePids(): Map<TerminalSessionId, Long> =
        entries.value.mapNotNull { (id, entry) ->
            if (entry.backend.isAlive) id to entry.backend.process.pid() else null
        }.toMap()
}
